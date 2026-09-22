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

import org.mockito.ArgumentMatchers.{any, argThat, eq as eqTo}
import org.mockito.Mockito.{reset, verify, when}
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.submission.FileStatus
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.services.submission.{SDESService, SubmissionService}
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionServiceSpec extends NoGuiceSpecBase with TestData {

  private val mockSDESService          = mock[SDESService]
  private val mockSubmissionRepository = mock[SubmissionRepository]
  private val submissionService        = new SubmissionService(mockSDESService, mockSubmissionRepository)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSDESService)
    reset(mockSubmissionRepository)
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
  }
}
