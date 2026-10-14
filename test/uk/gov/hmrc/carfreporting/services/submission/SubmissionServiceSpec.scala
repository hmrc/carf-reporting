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

package uk.gov.hmrc.carfreporting.services.submission

import org.mockito.ArgumentMatchers.{any, argThat, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.ApiError.{InternalServerError, NotFoundError}
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.submission.{DetailsOfFilesSent, FileStatus, SubmissionDetailsCache}
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionServiceSpec extends NoGuiceSpecBase with TestData {

  private val mockSDESService              = mock[SDESService]
  private val mockSubmissionRepository     = mock[SubmissionRepository]
  private val mockSubmissionHistoryService = mock[SubmissionHistoryService]
  private val mockAppConfig                = mock[AppConfig]
  private val submissionService            =
    new SubmissionService(mockSDESService, mockSubmissionRepository, mockSubmissionHistoryService, mockAppConfig)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSDESService, mockSubmissionRepository, mockSubmissionHistoryService)
  }

  "SubmissionService" - {
    "saveAndSubmit" - {
      "must save and submit a submission request" in {
        when(mockSubmissionRepository.insert(any())).thenReturn(ResultT.fromValue(true))
        when(mockSDESService.sendNotification(any(), any())(any())).thenReturn(ResultT.fromValue(()))

        val result = submissionService.saveAndSubmit(testSubmissionRequest).value.futureValue

        result mustBe Right(())

        verify(mockSubmissionRepository).insert(
          argThat(cache =>
            cache._id == testSubmissionDetailsCache._id &&
              cache.carfId == testSubmissionDetailsCache.carfId &&
              cache.fileStatus == testSubmissionDetailsCache.fileStatus &&
              cache.fileName == testSubmissionDetailsCache.fileName &&
              cache.extractedFileDetails == testSubmissionDetailsCache.extractedFileDetails &&
              cache.rcaspDetails == testSubmissionDetailsCache.rcaspDetails &&
              cache.subscriptionDetails == testSubmissionDetailsCache.subscriptionDetails &&
              cache.businessRuleErrors == testSubmissionDetailsCache.businessRuleErrors
          )
        )
        verify(mockSDESService).sendNotification(eqTo(testSubmissionRequest), any())(any())
      }

      "must pass on failure if the repository fails during submission request" in {
        when(mockSubmissionRepository.insert(any())).thenReturn(ResultT.fromError(InternalServerError))

        val result = submissionService.saveAndSubmit(testSubmissionRequest).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSubmissionRepository).insert(
          argThat(cache =>
            cache._id == testSubmissionDetailsCache._id &&
              cache.carfId == testSubmissionDetailsCache.carfId &&
              cache.fileStatus == testSubmissionDetailsCache.fileStatus &&
              cache.fileName == testSubmissionDetailsCache.fileName &&
              cache.extractedFileDetails == testSubmissionDetailsCache.extractedFileDetails &&
              cache.rcaspDetails == testSubmissionDetailsCache.rcaspDetails &&
              cache.subscriptionDetails == testSubmissionDetailsCache.subscriptionDetails &&
              cache.businessRuleErrors == testSubmissionDetailsCache.businessRuleErrors
          )
        )
      }

      "must pass on failure if SDESService fails when sending a notification" in {
        when(mockSubmissionRepository.insert(any())).thenReturn(ResultT.fromValue(true))
        when(mockSDESService.sendNotification(any(), any())(any())).thenReturn(ResultT.fromError(InternalServerError))

        val result = submissionService.saveAndSubmit(testSubmissionRequest).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSubmissionRepository).insert(
          argThat(cache =>
            cache._id == testSubmissionDetailsCache._id &&
              cache.carfId == testSubmissionDetailsCache.carfId &&
              cache.fileStatus == testSubmissionDetailsCache.fileStatus &&
              cache.fileName == testSubmissionDetailsCache.fileName &&
              cache.extractedFileDetails == testSubmissionDetailsCache.extractedFileDetails &&
              cache.rcaspDetails == testSubmissionDetailsCache.rcaspDetails &&
              cache.subscriptionDetails == testSubmissionDetailsCache.subscriptionDetails &&
              cache.businessRuleErrors == testSubmissionDetailsCache.businessRuleErrors
          )
        )
        verify(mockSDESService).sendNotification(eqTo(testSubmissionRequest), any())(any())
      }
    }

    "updateFileStatusAsFailure" - {
      "must update file status to [VirusFound] when virus is a failure Reason" in {
        val failureReason = "Virus scan failed"

        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result = submissionService.updateFileStatusAsFailure(testUploadId, Some(failureReason)).value.futureValue

        result mustBe Right(())

        verify(mockSubmissionRepository).updateStatus(eqTo(testUploadId), eqTo(FileStatus.VirusFound))
      }

      "must update file status to [UnexpectedError] when there is no failure Reason" in {

        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result = submissionService.updateFileStatusAsFailure(testUploadId, None).value.futureValue

        result mustBe Right(())

        verify(mockSubmissionRepository).updateStatus(eqTo(testUploadId), eqTo(FileStatus.UnexpectedError))
      }

      "must update file status to [UnexpectedError] when there is a failure Reason other than virus" in {

        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result =
          submissionService.updateFileStatusAsFailure(testUploadId, Some("Unexpected error")).value.futureValue

        result mustBe Right(())

        verify(mockSubmissionRepository).updateStatus(eqTo(testUploadId), eqTo(FileStatus.UnexpectedError))
      }

      "must return Left when updating the file status is failed by the repository" in {

        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromError(MongoError()))

        val result =
          submissionService.updateFileStatusAsFailure(testUploadId, Some("Unexpected error")).value.futureValue

        result mustBe Left(MongoError())

        verify(mockSubmissionRepository).updateStatus(eqTo(testUploadId), eqTo(FileStatus.UnexpectedError))
      }
    }

    ".getSubmissionDetailsByUploadId" - {
      "must return the submission details when a record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(testSubmissionDetailsCache)))

        val result = submissionService.getSubmissionDetailsByUploadId(testUploadId).value.futureValue

        result mustBe Right(testSubmissionDetailsCache)

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return NotFoundError when no record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId))).thenReturn(ResultT.fromValue(None))

        val result = submissionService.getSubmissionDetailsByUploadId(testUploadId).value.futureValue

        result mustBe Left(NotFoundError)

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return the error when the repository returns an error" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = submissionService.getSubmissionDetailsByUploadId(testUploadId).value.futureValue

        result mustBe Left(MongoError("Error message"))

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }
    }

    ".getSubmissionDetailsByCarfId" - {
      when(mockAppConfig.submittedFilesPageSize).thenReturn(50)

      "must return a DetailsOfFilesSent" - {
        "when there are no records from the repository or SubmissionHistoryService" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(Seq.empty))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(Seq.empty))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(Seq.empty, totalPages = 0)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when there are records from the repository but not SubmissionHistoryService (2 records, page 1)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(submissionDetailsList))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(Seq.empty))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(submissionDetailsList, totalPages = 1)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when there are records from SubmissionHistoryService but not the repository (70 records, page 1)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(Seq.empty))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryPassedList(70)))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(submissionHistoryPassedList(50), totalPages = 2)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when there are records from both the repository and SubmissionHistoryService (72 records total, page 1)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(submissionDetailsList))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryPassedList(70)))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(submissionDetailsList ++ submissionHistoryPassedList(48), totalPages = 2)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when there are records from both the repository and SubmissionHistoryService (102 records total, page 2)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(submissionDetailsList))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryPassedList(100)))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 2).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(submissionHistoryPassedList(100).slice(48, 98), totalPages = 3)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when there are records from both the repository and SubmissionHistoryService (102 records total, page 3)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(submissionDetailsList))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryPassedList(100)))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 3).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(submissionHistoryPassedList(100).drop(98), totalPages = 3)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }

        "when page number is too large for the number of records (72 records total, page 3)" in {
          when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
            .thenReturn(ResultT.fromValue(submissionDetailsList))

          when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryPassedList(70)))

          val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 3).value.futureValue

          result mustBe Right(
            DetailsOfFilesSent(Seq.empty, totalPages = 2)
          )

          verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
          verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
        }
      }

      "must return an error when the repository returns an error" in {
        when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

        result mustBe Left(MongoError("Error message"))

        verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
        verify(mockSubmissionHistoryService, times(0)).getSubmissionHistory(any())(any())
      }

      "must return an error when SubmissionHistoryService returns an error" in {
        when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
          .thenReturn(ResultT.fromValue(submissionDetailsList))

        when(mockSubmissionHistoryService.getSubmissionHistory(eqTo(testCarfRef))(any()))
          .thenReturn(ResultT.fromError(InternalServerError))

        val result = submissionService.getSubmissionDetailsByCarfId(testCarfRef, 1).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
        verify(mockSubmissionHistoryService, times(1)).getSubmissionHistory(eqTo(testCarfRef))(any())
      }
    }
  }
}
