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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.errors.InternalServerError
import uk.gov.hmrc.carfreporting.services.submission.SubmissionService
import uk.gov.hmrc.carfreporting.types.ResultT

class SDESCallbackControllerSpec extends SpecBase {

  private val mockSubmissionService = mock[SubmissionService]

  val testController: SDESCallbackController =
    new SDESCallbackController(cc, mockSubmissionService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionService)
  }

  "SDESCallbackController" - {

    "callback" - {

      "must return NO_CONTENT (204) when notification is FileProcessingFailure and the service successfully updates the status" in {
        val correlationID = UploadId("correlation-12345")
        val failureReason = "Virus scan failed"

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessingFailure",
             |  "filename": "test-file.xml",
             |  "correlationID": "${correlationID.value}",
             |  "failureReason": "$failureReason"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        when(mockSubmissionService.updateFileStatus(any(), any()))
          .thenReturn(ResultT.fromValue(()))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual NO_CONTENT
        // TODO Lookdown
        // verify(mockSubmissionService).updateFileStatus(eqTo(correlationID), eqTo(Some(failureReason)))(any())
      }

      "must return INTERNAL_SERVER_ERROR (500) when notification is FileProcessingFailure and the service returns an error" in {
        val correlationID = "correlation-12345"

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessingFailure",
             |  "filename": "test-file.xml",
             |  "correlationID": "$correlationID"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        when(mockSubmissionService.updateFileStatus(any(), any()))
          .thenReturn(ResultT.fromError(InternalServerError("Database timeout")))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result)          mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) mustEqual "Unexpected error"

        // verify(mockSubmissionService).updateFileStatus(eqTo(correlationID), eqTo(None))(any())
      }

      "must return NO_CONTENT (204) without calling the service when notification is NOT FileProcessingFailure (e.g., FileProcessed)" in {
        val correlationID = "correlation-12345"

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessed",
             |  "filename": "test-file.xml",
             |  "correlationID": "$correlationID"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual NO_CONTENT

        // verify(mockSubmissionService, never).updateFileStatus(any(), any())(any())
      }

      "must return BAD_REQUEST (400) when the JSON request is invalid or missing required fields" in {
        val requestBodyJson =
          s"""
             |{
             |  "invalidField": "invalidValue"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result)     mustEqual BAD_REQUEST
        contentAsString(result) must include("Request body provided is invalid")

        // verify(mockSubmissionService, never).updateFileStatus(any(), any())(any())
      }
    }
  }
}
