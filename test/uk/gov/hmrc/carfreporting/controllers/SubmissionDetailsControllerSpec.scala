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

import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.{Pending, Rejected}
import uk.gov.hmrc.carfreporting.models.submission.SubmissionDetailsCache
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionDetailsControllerSpec extends SpecBase {

  val mockSubmissionRepository: SubmissionRepository = mock[SubmissionRepository]

  val controller = new SubmissionDetailsController(fakeAuthAction, cc, mockSubmissionRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionRepository)
  }

  "SubmissionDetailsController" - {
    ".getFileStatus" - {
      "must return OK with the file status when a record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(testSubmissionDetailsCache)))

        val result = controller.getFileStatus(testUploadId.value)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Pending)

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return NotFound when no record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId))).thenReturn(ResultT.fromValue(None))

        val result = controller.getFileStatus(testUploadId.value)(fakeRequest)

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return InternalServerError when the repository returns an error" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getFileStatus(testUploadId.value)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Unexpected error")

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }
    }

    ".getSubmissionDetailsByUploadId" - {
      "must return OK with the file status when a record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(testSubmissionDetailsCache)))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(testSubmissionDetailsCache)

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return NotFound when no record is found" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId))).thenReturn(ResultT.fromValue(None))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }

      "must return InternalServerError when the repository returns an error" in {
        when(mockSubmissionRepository.findByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Unexpected error")

        verify(mockSubmissionRepository, times(1)).findByUploadId(eqTo(testUploadId))
      }
    }

    ".getSubmissionDetailsByCarfId" - {
      "must return OK with a list of submission details" in {
        val submissionDetailsList = Seq(
          testSubmissionDetailsCache,
          testSubmissionDetailsCache.copy(_id = UploadId("987654"), fileStatus = Rejected)
        )

        when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
          .thenReturn(ResultT.fromValue(submissionDetailsList))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(submissionDetailsList)

        verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
      }

      "must return OK with an empty list of submission details" in {
        when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
          .thenReturn(ResultT.fromValue(Seq.empty))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Seq.empty[SubmissionDetailsCache])

        verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
      }

      "must return InternalServerError when the repository returns an error" in {
        when(mockSubmissionRepository.findByCarfId(eqTo(testCarfRef)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Unexpected error")

        verify(mockSubmissionRepository, times(1)).findByCarfId(eqTo(testCarfRef))
      }
    }
  }
}
