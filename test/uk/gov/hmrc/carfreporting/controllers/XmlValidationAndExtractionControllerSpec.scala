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

package uk.gov.hmrc.carfreporting.controllers

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, reset, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.ExtractedFileDetails
import uk.gov.hmrc.carfreporting.models.errors.*
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
      "must return OK (200) with ExtractedFileDetails when the XML parser is successful" in {
        val path        = "resources/data/examples/valid-carf.xml"
        val requestBody = Json.parse(
          s"""
             |{
             |  "path": "$path"
             |}
             |""".stripMargin
        )

        when(mockXmlParserService.validateAndExtract(path)).thenReturn(ResultT.fromValue(extractedFileDetailsValidCarf))

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(extractedFileDetailsValidCarf)

        verify(mockXmlParserService).validateAndExtract(eqTo(path))
      }

      "must return Unprocessable Entity (422) with XML errors when the XML fails with a schema error" in {
        val invalidPath     = "resources/data/invalid-carf.xml"
        val requestBody     = Json.parse(
          s"""
             |{
             |  "path": "$invalidPath"
             |}
             |""".stripMargin
        )
        val validationError = XmlError(1, "cvc-complex-type.2.4.a", "Invalid element found at line 1")

        when(mockXmlParserService.validateAndExtract(eqTo(invalidPath)))
          .thenReturn(ResultT.fromError(XmlErrors(Vector(validationError))))

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result)        mustEqual UNPROCESSABLE_ENTITY
        contentAsJson(result) mustEqual Json.toJson(XmlErrors(Vector(validationError)): XmlValidationError)

        verify(mockXmlParserService).validateAndExtract(eqTo(invalidPath))
      }

      "must return Unprocessable Entity (422) when there is an error parsing the XML file" in {
        val invalidPath = "resources/data/invalid-carf.xml"
        val requestBody = Json.parse(
          s"""
             |{
             |  "path": "$invalidPath"
             |}
             |""".stripMargin
        )

        when(mockXmlParserService.validateAndExtract(eqTo(invalidPath)))
          .thenReturn(ResultT.fromError(InvalidXmlError))

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result)        mustEqual UNPROCESSABLE_ENTITY
        contentAsJson(result) mustEqual Json.toJson(InvalidXmlError: XmlValidationError)

        verify(mockXmlParserService).validateAndExtract(eqTo(invalidPath))
      }

      "must return Bad Request (400) when the Json request is malformed" in {
        val requestBody = Json.parse(
          s"""
             |{
             |  "bad": "invalid"
             |}
             |""".stripMargin
        )

        val expectedResponse = "Request body provided is invalid"

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result)          mustEqual BAD_REQUEST
        contentAsString(result) mustEqual expectedResponse

        verify(mockXmlParserService, never).validateAndExtract(any())
      }

      "must return Internal Server Error (500) when the XML parser fails for another reason" in {
        val path        = "resources/data/examples/valid-carf.xml"
        val requestBody = Json.parse(
          s"""
             |{
             |  "path": "$path"
             |}
             |""".stripMargin
        )

        when(mockXmlParserService.validateAndExtract(eqTo(path)))
          .thenReturn(ResultT.fromError(InternalServerError("message")))

        val result = testController.processXml(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual INTERNAL_SERVER_ERROR

        verify(mockXmlParserService).validateAndExtract(eqTo(path))
      }
    }
  }
}
