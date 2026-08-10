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

package uk.gov.hmrc.carfreporting.controllers.upscan

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.controllers.base.SpecBase
import uk.gov.hmrc.carfreporting.controllers.upscan.XmlValidationAndExtractionController
import uk.gov.hmrc.carfreporting.models.errors.*
import uk.gov.hmrc.carfreporting.models.responses.XmlValidationAndExtractionResponse
import uk.gov.hmrc.carfreporting.services.XmlParserService
import uk.gov.hmrc.carfreporting.types.ResultT

class XmlValidationAndExtractionControllerSpec extends SpecBase {

  private val mockXmlParserService                         = mock[XmlParserService]
  val testController: XmlValidationAndExtractionController =
    new XmlValidationAndExtractionController(cc, mockXmlParserService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockXmlParserService)
  }

  "XmlValidationAndExtractionController" - {
    "processXml" - {
      "must return OK (200) when the XML parser is successful" in {
        val path        = "data/examples/valid-carf.xml"
        val requestBody = Json.parse(
          s"""
             |{
             |  "path": "$path"
             |}
             |""".stripMargin
        )

        when(mockXmlParserService.validateAndExtract(path)).thenReturn(ResultT.fromValue(()))

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual OK

        verify(mockXmlParserService).validateAndExtract(ArgumentMatchers.eq(path))
      }

      "must return Bad Request (400) when the XML parser fails with an XML error" in {

        val invalidPath     = "data/invalid-carf.xml"
        val requestBody     = Json.parse(
          s"""
             |{
             |  "path": "$invalidPath"
             |}
             |""".stripMargin
        )
        val validationError = XmlError(1, "cvc-complex-type.2.4.a", "Invalid element found at line 1")

        when(mockXmlParserService.validateAndExtract(ArgumentMatchers.eq(invalidPath)))
          .thenReturn(ResultT.fromError(XmlErrors(Vector(validationError))))

        val expectedResponse = XmlValidationAndExtractionResponse(
          BAD_REQUEST,
          invalidPath,
          Some("The submitted XML failed schema validation."),
          Vector(validationError)
        )

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual BAD_REQUEST

        contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      }

      "must return Internal Server Error (500) when the XML parser fails for unknown reasons" in {

        val path        = "data/examples/valid-carf.xml"
        val requestBody = Json.parse(
          s"""
             |{
             |  "path": "$path"
             |}
             |""".stripMargin
        )

        when(mockXmlParserService.validateAndExtract(ArgumentMatchers.eq(path)))
          .thenReturn(ResultT.fromError(InternalServerError("message")))

        val expectedResponse = XmlValidationAndExtractionResponse(
          INTERNAL_SERVER_ERROR,
          path,
          Some("Unexpected error"),
          Vector.empty
        )

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual INTERNAL_SERVER_ERROR

        contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      }
    }
  }
}
