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
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
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

      "must return OK (200) when notification is FileProcessingFailure and the service successfully updates the status" in {
        val failureReason = "Virus scan failed"

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessingFailure",
             |  "filename": "test-file.xml",
             |  "checksumAlgorithm": "SHA-256",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "correlationID": "${testUploadId.value}",
             |  "failureReason": "$failureReason"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        when(mockSubmissionService.updateFileStatusAsFailure(any(), any()))
          .thenReturn(ResultT.fromValue(()))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual OK
        verify(mockSubmissionService).updateFileStatusAsFailure(eqTo(testUploadId), eqTo(Some(failureReason)))
      }

      "must return INTERNAL_SERVER_ERROR (500) when notification is FileProcessingFailure and the service returns an error" in {

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessingFailure",
             |  "checksumAlgorithm": "SHA-256",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "filename": "test-file.xml",
             |  "correlationID": "${testUploadId.value}"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        when(mockSubmissionService.updateFileStatusAsFailure(any(), any()))
          .thenReturn(ResultT.fromError(InternalServerError("Database timeout")))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result)          mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) mustEqual "Unexpected error"

        verify(mockSubmissionService).updateFileStatusAsFailure(eqTo(testUploadId), eqTo(None))
      }

      "must return OK (200) without calling the service when notification is NOT FileProcessingFailure (e.g., FileProcessed)" in {

        val requestBodyJson =
          s"""
             |{
             |  "notification": "FileProcessed",
             |  "filename": "test-file.xml",
             |  "checksumAlgorithm": "SHA-256",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "correlationID": "correlation-12345"
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJson)

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual OK

        verify(mockSubmissionService, times(0)).updateFileStatusAsFailure(any(), any())
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

        verify(mockSubmissionService, times(0)).updateFileStatusAsFailure(any(), any())
      }
    }
  }
}
