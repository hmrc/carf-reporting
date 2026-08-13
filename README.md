# carf-reporting

This is the Backend repository for the Crypto Asset Reporting Framework (CARF) team's file upload journey.

## What this service does

- REST API endpoints for file upload data
- Data retrieval from MongoDB
- Integration with HMRC downstream services (ETMP, DES) and audit integration
- Processes file upload submission

### Running the service locally

Prerequisites:

- Java 21
- SBT
- MongoDB
- Service Manager

Commands:

Start CARF services in service manager. (frontend, backend, any other services needed to run locally)

```
sm2 --start CARF_ALL
```

Stop this service from service manager.

```
sm2 --stop CARF_REPORTING
```

Run CARF_REPORTING locally using sbt to test dev changes.

```
sbt run
```

### Running the service in test only mode

```
sm2 --start CARF_ALL
```

```
sm2 --stop CARF_REPORTING
```

Starts service locally with test-only routes enabled.

```
sbt "run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
```

### Service manager and port info

Service manager: CARF_ALL

Port: 17005

### How to sign in locally and on staging

Local:
http://localhost:9949/auth-login-stub/gg-sign-in?continue=http://localhost:17004/send-a-cryptoasset-report

Staging:
https://www.staging.tax.service.gov.uk/send-a-cryptoasset-report

### Auth wizard setup

- Redirect URL: http://localhost:17004/send-a-cryptoasset-report/report/upload-file
- Credential Strength: Strong Confidence Level: 50 Affinity Group: Organisation / Individual Credential role: User
- Enrolments:
    - Enrolment Key: HMRC-CARF-ORG
    - Identifier Name: CARFID
    - Identifier Value: <Enter carfId> - see stubs repository for cases
- For auto-matched Organisation with CT UTR:
    - Add preset CT to Enrolments and enter UTR (e.g. 1234568945)

### Running tests

Run unit tests:

```
sbt test
```

Run Integration Tests:

```
sbt it/test
```

Run Unit and Integration Tests with coverage report:

```
sbt clean compile scalafmtAll coverage test it/test coverageReport 
```

# XML Parser for validation and extraction

## How to run locally & staging etc

### Locally

1. Open your Restful Api Client of your choosing
2. Formulate your JSON Body with the following request body example:
    ```json
      {
        "path": "data/examples/valid-carf.xml"
      }
    ```
   Note: Other XML examples such as an invalid xml are available in `data/examples`

3. Call the API with the url: http://localhost:17005/carf-reporting/upscan/validate

### Staging etc

???

## XML Parser Design Decisions and overview

### StAX

We chose the StAX (Streaming API for XML) over SAX (Simple API for XML) primarily for scalability and maintainability.

- SAX is a "Push" parser: It reads the file and blindly pushes events to a handler (e.g., startElement, endElement). This would force us to build a more complex, mutable implementation making it harder maintain. Furthermore, it would also overload the stream as applying back pressure is impossible when using SAX.

- StAX is a "Pull" parser: The application controls the flow using a standard cursor loop (while(reader.hasNext())). This allows for clean, procedural code where state is managed locally and intuitively. It drastically reduces bugs and cognitive load when extracting specific data from complex XML structures. It also allows the stream to pull elements as it pleases, making it easy to handle large loads.

#### Single-Pass Validation and Extraction with Woodstox
Processing massive XML payloads efficiently requires strict memory management. We implemented Woodstox (a high-performance, fully compliant StAX2 implementation) because it enables simultaneous streaming validation and extraction without memory bloat.

On-the-Fly XSD Validation: By attaching an XMLValidationSchema directly to the Woodstox XMLStreamReader2, the parser validates the document against our schema simultaneously as our loop pulls data from the stream.

Fail-Fast Efficiency: If the XML violates the schema, Woodstox triggers a validation event immediately. This allows us to abort processing instantly (in our case 100+ errors) and return accumulated errors, rather than wasting CPU cycles parsing the remainder of an invalid megabyte-sized file.

Flat Memory Profile: Because the data is validated and extracted in a single sequential pass, the file is never mapped into a DOM tree or fully loaded into memory. This guarantees a flat, predictable memory footprint regardless of whether the XML payload is 2 MB or 250 MB.

### Apache Pekko

This streaming API was selected as it is already part of the Play Framework infrastructure and was easy to get going out of the box.
It was also a good choice as Apache Pekko was forked from Akka Streams 2.6 and Akka is a proven fast, scalable asynchronous system.

#### XmlParserService

As you can see in the `XmlParserService` you can see there is a CustomExecutionContext called `XmlDispatcher`, this is to prevent the XML parser starving the rest of the application of threads
where it can parse XML on it's own execution context keeping the application reactive to all incoming requests.

## API Design

The API (`carf-reporting/upscan/validate`) was used to test and simulate how the XML parser will be used by future consumers.
So when implementing [CARF-596] be sure to maintain the structure of the API and add any additional components/logic on top of the current implementation unless specified otherwise.

### Request Body:
- path:
  - Provide a path that points to the existing file within the repository normally within `data/examples`.
  - This was a design decision for ease of use and not to parse a whole file here defeating the purpose of the StAX parser

### Response Body

success example:
```json
{
    "status": 200,
    "sourcePath": "data/examples/valid-carf.xml",
    "xmlErrors": []
}
```

invalid xml example:
```json
{
    "status": 400,
    "sourcePath": "data/examples/invalid-carf.xml",
    "errorMessage": "The submitted XML failed schema validation.",
    "xmlErrors": [
        {
            "lineNumber": 15,
            "errorCode": null,
            "errorMessage": "tag name \"MessageTypeIndic\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<Warning>"
        },
        {
            "lineNumber": 17,
            "errorCode": null,
            "errorMessage": "tag name \"ReportingPeriod\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<MessageTypeIndic>,<Warning>"
        },
        {
            "lineNumber": 18,
            "errorCode": null,
            "errorMessage": "tag name \"Timestamp\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<MessageTypeIndic>,<ReportingPeriod>,<Warning>"
        },
        {
            "lineNumber": 19,
            "errorCode": null,
            "errorMessage": "uncompleted content model. expecting: <Contact>,<MessageRefId>,<MessageTypeIndic>,<ReportingPeriod>,<Timestamp>,<Warning>"
        }
    ]
}
```

fatal example:
```json
{
    "status": 500,
    "sourcePath": "data/examples/valid-carf.xml",
    "errorMessage": "Something unexpected happened. This can include a completely Malformed XML which is classed a fatal to the parser",
    "xmlErrors": []
}
```
## XML Performance tests

How to run perf tests:

1. Navigate to the directory for the performance tests in it/test/uk/gov/hmrc/carfreporting/performance.
        There is one for API and one for the Service, both tests call the real XmlParserService.
2. Run the Generator found here: https://github.com/simondrugan16/carf-xml-parser/blob/main/src/main/scala/apps/XmlGenerator.scala
3. copy over the file named `generated/carf-250mb.xml` to the `data/sized` folder.
4. Rename `carf-250mb.xml` to `carf-262mb.xml` as that is the real size of the file on disk.
5. Run the tests as you would normally via IntelliJ or command line
6. View the results as they output time taken, memory used and other metrics that may be useful.

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").