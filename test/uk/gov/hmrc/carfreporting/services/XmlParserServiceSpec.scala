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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.dispatchers.{MainDispatcherName, XmlDispatcher}
import uk.gov.hmrc.carfreporting.models.ExtractedFileDetails
import uk.gov.hmrc.carfreporting.models.errors.{InternalServerError, XmlErrors}

class XmlParserServiceSpec extends NoGuiceSpecBase with TestData {

  val mockXmlDataHandlerService: XmlDataHandlerService = mock[XmlDataHandlerService]

  val mainDispatcherName = new MainDispatcherName()
  val xmlDispatcher      = new XmlDispatcher(actorSystem, mainDispatcherName)
  val service            = new XmlParserService(mockXmlDataHandlerService)(testEnv)(xmlDispatcher)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockXmlDataHandlerService)
  }

  "XmlParserService" - {

    "must return file_not_found error when the XML file does not exist" in {
      val result = service.validateAndExtract("invalid/path/nonexistent.xml").value.futureValue

      result match {
        case Left(e: XmlErrors) => e.errors.head.errorCode mustBe "file_not_found"
        case _                  => fail()
      }

      verify(mockXmlDataHandlerService, times(0)).validationAndExtraction(any(), any())
    }

    "must return an ExtractedFileDetails when returned by XmlDataHandlerService" in {
      when(mockXmlDataHandlerService.validationAndExtraction(any(), any()))
        .thenReturn(Right(extractedFileDetailsValidCarf))

      val path = "data/examples/valid-carf.xml"

      val result = service.validateAndExtract(path).value.futureValue

      result mustBe Right(extractedFileDetailsValidCarf)

      verify(mockXmlDataHandlerService, times(1)).validationAndExtraction(any(), any())
    }

    "must return XmlErrors when XmlDataHandlerService returns schema errors (the XML is well-formed but fails schema validation)" in {
      when(mockXmlDataHandlerService.validationAndExtraction(any(), any())).thenReturn(Left(xmlErrors))

      val path = "data/examples/invalid-carf.xml"

      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: XmlErrors) =>
          val errorMessages = e.errors.map(_.errorMessage)
          e.errors.length  mustBe 4
          errorMessages.head must include("\"MessageTypeIndic\" is not allowed.")
          errorMessages(1)   must include("\"ReportingPeriod\" is not allowed.")
          errorMessages(2)   must include("\"Timestamp\" is not allowed.")
          errorMessages(3)   must include("uncompleted content model.")
        case _                  => fail()
      }

      verify(mockXmlDataHandlerService, times(1)).validationAndExtraction(any(), any())
    }

    "must return an InternalServerError when XmlDataHandlerService returns InternalServerError (the XML is completely malformed)" in {
      when(mockXmlDataHandlerService.validationAndExtraction(any(), any()))
        .thenReturn(Left(InternalServerError("Unexpected EOF; was expecting a close tag for element <Root>")))

      val path   = "data/examples/malformed-xml.xml"
      val result = service.validateAndExtract(path).value.futureValue

      result match {
        case Left(e: InternalServerError) =>
          e.message must include("Unexpected EOF; was expecting a close tag for element <Root>")
        case _                            => fail()
      }

      verify(mockXmlDataHandlerService, times(1)).validationAndExtraction(any(), any())
    }
  }
}
