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
import org.mockito.Mockito.{never, reset, times, verify, verifyNoInteractions, when}
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.ValidationStatus
import uk.gov.hmrc.carfreporting.models.errors.InternalServerError
import uk.gov.hmrc.carfreporting.models.submission.SubmissionDetailsCache
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.services.submission.{EmailService, SDESService, SubmissionService}
import uk.gov.hmrc.carfreporting.types.ResultT

import scala.concurrent.Future

class SDESCallbackControllerSpec extends SpecBase with Eventually {

  private val mockSubmissionService    = mock[SubmissionService]
  private val mockSdesService          = mock[SDESService]
  private val mockSubmissionRepository = mock[SubmissionRepository]
  private val mockEmailService         = mock[EmailService]

  val testController: SDESCallbackController =
    new SDESCallbackController(
      cc,
      mockSubmissionService,
      mockSdesService,
      mockSubmissionRepository,
      mockEmailService
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionService, mockSdesService, mockSubmissionRepository, mockEmailService)

    when(mockEmailService.sendFilePassedEmails(any())(any())).thenReturn(Future.successful(()))
    when(mockEmailService.sendFileFailedEmails(any())(any())).thenReturn(Future.successful(()))
  }

  private val fileReadyBody = Json.parse(
    s"""
       |{
       |  "notification": "FileReady",
       |  "filename": "br-file.xml",
       |  "checksumAlgorithm": "SHA-256",
       |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
       |  "correlationID": "${testUploadId.value}"
       |}
       |""".stripMargin
  )

  "SDESCallbackController" - {

    "callback" - {

      "must return OK (200) when notification is FileProcessingFailure and the service successfully updates the status" in {
        val failureReason = "Virus scan failed"

        val requestBody = Json.parse(
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
        )

        when(mockSubmissionService.updateFileStatusAsFailure(any(), any()))
          .thenReturn(ResultT.fromValue(()))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual OK
        verify(mockSubmissionService).updateFileStatusAsFailure(eqTo(testUploadId), eqTo(Some(failureReason)))
        verifyNoInteractions(mockEmailService)
      }

      "must return INTERNAL_SERVER_ERROR (500) when notification is FileProcessingFailure and the service returns an error" in {
        val requestBody = Json.parse(
          s"""
             |{
             |  "notification": "FileProcessingFailure",
             |  "checksumAlgorithm": "SHA-256",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "filename": "test-file.xml",
             |  "correlationID": "${testUploadId.value}"
             |}
             |""".stripMargin
        )

        when(mockSubmissionService.updateFileStatusAsFailure(any(), any()))
          .thenReturn(ResultT.fromError(InternalServerError("Database timeout")))

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result)          mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) mustEqual "Unexpected error"

        verify(mockSubmissionService).updateFileStatusAsFailure(eqTo(testUploadId), eqTo(None))
        verifyNoInteractions(mockEmailService)
      }

      "when notification is FileReady" - {

        "must return OK (200) and send the passed emails when the business rules result is Accepted" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromValue(ValidationStatus.Accepted))
          when(mockSubmissionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Option(testSubmissionDetailsCache)))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result) mustEqual OK
          verify(mockSdesService).getAndProcessBusinessRulesResponseFile(eqTo(testUploadId), eqTo("br-file.xml"))(any())

          eventually {
            verify(mockEmailService, times(1)).sendFilePassedEmails(eqTo(testSubmissionDetailsCache))(any())
            ()
          }
          verify(mockEmailService, never()).sendFileFailedEmails(any())(any())
        }

        "must return OK (200) and send the failed emails when the business rules result is Rejected" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromValue(ValidationStatus.Rejected))
          when(mockSubmissionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Option(testSubmissionDetailsCache)))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result) mustEqual OK

          eventually {
            verify(mockEmailService, times(1)).sendFileFailedEmails(eqTo(testSubmissionDetailsCache))(any())
            ()
          }
          verify(mockEmailService, never()).sendFilePassedEmails(any())(any())
        }

        "must return OK (200) and send no emails when the submission is not found" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromValue(ValidationStatus.Accepted))
          when(mockSubmissionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Option.empty[SubmissionDetailsCache]))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result) mustEqual OK
          eventually {
            verify(mockSubmissionRepository).findByUploadId(eqTo(testUploadId))
          }
          verifyNoInteractions(mockEmailService)
        }

        "must return OK (200) and send no emails when the repository read fails" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromValue(ValidationStatus.Accepted))
          when(mockSubmissionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromError(InternalServerError("Mongo down")))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result) mustEqual OK
          eventually {
            verify(mockSubmissionRepository).findByUploadId(eqTo(testUploadId))
          }
          verifyNoInteractions(mockEmailService)
        }

        "must still return OK (200) when sending the emails fails" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromValue(ValidationStatus.Accepted))
          when(mockSubmissionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Option(testSubmissionDetailsCache)))
          when(mockEmailService.sendFilePassedEmails(any())(any()))
            .thenReturn(Future.failed(new RuntimeException("Email service down")))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result) mustEqual OK
          eventually {
            verify(mockEmailService, times(1)).sendFilePassedEmails(any())(any())
            ()
          }
        }

        "must return INTERNAL_SERVER_ERROR (500) and send no emails when SdesService returns an error" in {
          when(mockSdesService.getAndProcessBusinessRulesResponseFile(any(), any())(any()))
            .thenReturn(ResultT.fromError(InternalServerError("Unexpected error")))

          val result = testController.callback(fakeRequestWithJsonBody(fileReadyBody))

          status(result)          mustEqual INTERNAL_SERVER_ERROR
          contentAsString(result) mustEqual "Unexpected error"

          verify(mockSdesService).getAndProcessBusinessRulesResponseFile(eqTo(testUploadId), eqTo("br-file.xml"))(any())
          verifyNoInteractions(mockSubmissionRepository)
          verifyNoInteractions(mockEmailService)
        }
      }

      "must return OK (200) without calling the service when notification is NOT FileProcessingFailure or FileReady (e.g., FileProcessed)" in {
        val requestBody = Json.parse(
          s"""
             |{
             |  "notification": "FileProcessed",
             |  "filename": "test-file.xml",
             |  "checksumAlgorithm": "SHA-256",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "correlationID": "correlation-12345"
             |}
             |""".stripMargin
        )

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual OK
        verify(mockSubmissionService, times(0)).updateFileStatusAsFailure(any(), any())
        verifyNoInteractions(mockEmailService)
      }

      "must return BAD_REQUEST (400) when the JSON request is invalid or missing required fields" in {
        val requestBody = Json.parse(
          s"""
             |{
             |  "invalidField": "invalidValue"
             |}
             |""".stripMargin
        )

        val result = testController.callback(fakeRequestWithJsonBody(requestBody))

        status(result)     mustEqual BAD_REQUEST
        contentAsString(result) must include("Request body provided is invalid")

        verify(mockSubmissionService, times(0)).updateFileStatusAsFailure(any(), any())
      }
    }
  }
}
