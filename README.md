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

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").