/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.carfreporting.services

import uk.gov.hmrc.carfreporting.base.NoGuiceSpecBase
import uk.gov.hmrc.carfreporting.dispatchers.{MainDispatcherName, XmlDispatcher}
import uk.gov.hmrc.carfreporting.models.errors.{InternalServerError, XmlErrors}

import java.io.PrintWriter
import java.nio.file.{Files, Path}

class XmlParserServiceSpec extends NoGuiceSpecBase {

  val mainDispatcherName = new MainDispatcherName()
  val xmlDispatcher      = new XmlDispatcher(actorSystem, mainDispatcherName)
  val service            = new XmlParserService(testEnv)(xmlDispatcher)

  val tempDir: Path = Files.createTempDirectory("carf-xml-tests")

  override def beforeAll(): Unit = super.beforeAll()

  override def afterAll(): Unit = {
    tempDir.toFile.listFiles().foreach(_.delete())
    tempDir.toFile.delete()
  }

  private def createTempXml(fileName: String, content: String): String = {
    val file   = tempDir.resolve(fileName).toFile
    val writer = new PrintWriter(file)
    writer.write(content)
    writer.close()
    file.getAbsolutePath
  }

  "XmlParserService" - {

    "must return file_not_found error when the XML file does not exist" in {
      val result = service.validateAndExtract("invalid/path/nonexistent.xml").value.futureValue

      result match {
        case Left(e: XmlErrors) => e.errors.head.errorCode mustBe "file_not_found"
        case _                  => fail()
      }
    }

    "must successfully validate and extract a well-formed XML that matches the schema" in {
      val path = "data/examples/valid-carf.xml"

      val result = service.validateAndExtract(path).value.futureValue

      result mustBe Right(())
    }

    "must return XmlErrors when the XML is well-formed but has invalid root" in {
      val invalidXml = """<?xml version="1.0" encoding="UTF-8"?><InvalidRoot>Data</InvalidRoot>"""
      val path       = createTempXml("schema_invalid.xml", invalidXml)

      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: XmlErrors) =>
          e.errors.length          mustBe 3
          e.errors.head.errorMessage must include("InvalidRoot")
        case _                  => fail()
      }
    }

    "must return XmlErrors when the XML is well-formed but fails schema validation (under 101 errors)" in {
      val path = "data/examples/invalid-carf.xml"

      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: XmlErrors) =>
          val errorMessages = e.errors.map(_.errorMessage)
          println(errorMessages.mkString(",\n"))
          e.errors.length  mustBe 4
          errorMessages.head must include("\"MessageTypeIndic\" is not allowed.")
          errorMessages(1)   must include("\"ReportingPeriod\" is not allowed.")
          errorMessages(2)   must include("\"Timestamp\" is not allowed.")
          errorMessages(3)   must include("uncompleted content model.")
        case _                  => fail()
      }
    }

    "must truncate errors and exit cleanly when schema errors exceed max errors of (101) and xml contains 150 errors" in {
      val path = "data/examples/too-many-schema-errors.xml"

      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: XmlErrors) =>
          e.errors.length            mustBe 101
          e.errors.map(_.errorMessage) must contain only "the value is not a member of the enumeration."
        case _                  => fail()
      }
    }

    "must return an InternalServerError when the XML is completely malformed (Fatal XML Stream Error)" in {
      val malformedXml = """<?xml version="1.0" encoding="UTF-8"?><Root>Unclosed tag"""
      val path         = createTempXml("malformed.xml", malformedXml)

      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: InternalServerError) =>
          println(e.message)
          e.message must include("Unexpected EOF; was expecting a close tag for element <Root>")
        case _                            => fail()
      }
    }
  }
}
