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

package uk.gov.hmrc.carfreporting.services.upscan

import org.mockito.ArgumentMatchers.{any, argThat, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.repositories.upscan.UpscanSessionRepository
import uk.gov.hmrc.carfreporting.types.ResultT

class UploadProgressTrackerSpec extends SpecBase {

  val mockUpscanSessionRepository: UpscanSessionRepository = mock[UpscanSessionRepository]

  val uploadProgressTracker = new UploadProgressTracker(mockUpscanSessionRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanSessionRepository)
  }

  "UploadProgressTracker" - {
    ".requestUpload" - {
      "must insert an UploadSessionDetails" in {
        when(mockUpscanSessionRepository.insert(any[UploadSessionDetails]())).thenReturn(ResultT.fromValue(true))

        val result = uploadProgressTracker.requestUpload(testUploadId, testReference)
        result.value.futureValue mustBe Right(true)

        verify(mockUpscanSessionRepository, times(1)).insert(
          argThat { details =>
            details.uploadId == testUploadId &&
            details.reference == testReference &&
            details.status == InProgress
          }
        )
      }

      "must return an error if UpscanSessionRepository returns an error" in {
        when(mockUpscanSessionRepository.insert(any[UploadSessionDetails]()))
          .thenReturn(ResultT.fromError(MongoError("error")))

        val result = uploadProgressTracker.requestUpload(testUploadId, testReference)
        result.value.futureValue mustBe Left(MongoError("error"))

        verify(mockUpscanSessionRepository, times(1)).insert(
          argThat { details =>
            details.uploadId == testUploadId &&
            details.reference == testReference &&
            details.status == InProgress
          }
        )
      }
    }

    ".registerUploadResult" - {
      "must update status" in {
        when(mockUpscanSessionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result = uploadProgressTracker.registerUploadResult(testReference, uploadedSuccessfully)
        result.value.futureValue mustBe Right(true)

        verify(mockUpscanSessionRepository, times(1)).updateStatus(eqTo(testReference), eqTo(uploadedSuccessfully))
      }

      "must return an error if UpscanSessionRepository returns an error" in {
        when(mockUpscanSessionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromError(MongoError("error")))

        val result = uploadProgressTracker.registerUploadResult(testReference, uploadedSuccessfully)
        result.value.futureValue mustBe Left(MongoError("error"))

        verify(mockUpscanSessionRepository, times(1)).updateStatus(eqTo(testReference), eqTo(uploadedSuccessfully))
      }
    }

    ".getUploadResult" - {
      "must return None when no record is found" in {
        when(mockUpscanSessionRepository.findByUploadId(any())).thenReturn(ResultT.fromValue(None))

        val result = uploadProgressTracker.getUploadResult(testUploadId)
        result.value.futureValue mustBe Right(None)

        verify(mockUpscanSessionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return the status when a record is found" - {
        "with status UploadedSuccessfully" in {
          when(mockUpscanSessionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Some(uploadSessionDetails.copy(status = uploadedSuccessfully))))

          val result = uploadProgressTracker.getUploadResult(testUploadId)
          result.value.futureValue mustBe Right(Some(uploadedSuccessfully))

          verify(mockUpscanSessionRepository, times(1)).findByUploadId(eqTo(testUploadId))
        }

        "with status UploadRejected" in {
          when(mockUpscanSessionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Some(uploadSessionDetails.copy(status = uploadRejected))))

          val result = uploadProgressTracker.getUploadResult(testUploadId)
          result.value.futureValue mustBe Right(Some(uploadRejected))

          verify(mockUpscanSessionRepository, times(1)).findByUploadId(eqTo(testUploadId))
        }

        "with status InProgress" in {
          when(mockUpscanSessionRepository.findByUploadId(any()))
            .thenReturn(ResultT.fromValue(Some(uploadSessionDetails.copy(status = InProgress))))

          val result = uploadProgressTracker.getUploadResult(testUploadId)
          result.value.futureValue mustBe Right(Some(InProgress))

          verify(mockUpscanSessionRepository, times(1)).findByUploadId(eqTo(testUploadId))
        }
      }

      "must return return an error if UpscanSessionRepository returns an error" in {
        when(mockUpscanSessionRepository.findByUploadId(any())).thenReturn(ResultT.fromError(MongoError("error")))

        val result = uploadProgressTracker.getUploadResult(testUploadId)
        result.value.futureValue mustBe Left(MongoError("error"))

        verify(mockUpscanSessionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }
    }
  }
}
