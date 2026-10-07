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
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.errors.ApiError.NotFoundError
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.Rejected
import uk.gov.hmrc.carfreporting.models.submission.{DetailsOfFilesSent, SubmissionDetailsCache, SubmissionRecord}
import uk.gov.hmrc.carfreporting.services.submission.SubmissionService
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionDetailsControllerSpec extends SpecBase {

  val mockSubmissionService: SubmissionService = mock[SubmissionService]

  val controller = new SubmissionDetailsController(fakeAuthAction, cc, mockSubmissionService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionService)
  }

  "SubmissionDetailsController" - {
    ".getSubmissionDetailsByUploadId" - {
      "must return OK with the file status when a record is found" in {
        when(mockSubmissionService.getSubmissionDetailsByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(testSubmissionDetailsCache))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(testSubmissionDetailsCache)

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByUploadId(eqTo(testUploadId))
      }

      "must return NotFound when no record is found" in {
        when(mockSubmissionService.getSubmissionDetailsByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(NotFoundError))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByUploadId(eqTo(testUploadId))
      }

      "must return InternalServerError when the service returns another error" in {
        when(mockSubmissionService.getSubmissionDetailsByUploadId(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getSubmissionDetailsByUploadId(testUploadId.value)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Unexpected error")

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByUploadId(eqTo(testUploadId))
      }
    }

    ".getSubmissionDetailsByCarfId" - {
      "must return OK with a list of submission details" in {
        val submissionRecordsList: Seq[SubmissionRecord] = Seq(
          testSubmissionDetailsCache,
          testSubmissionDetailsCache.copy(_id = UploadId("987654"), fileStatus = Rejected),
          submissionHistoryPassed
        )

        val detailsOfFilesSent = DetailsOfFilesSent(submissionRecordsList, totalPages = 1)

        when(mockSubmissionService.getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any()))
          .thenReturn(ResultT.fromValue(detailsOfFilesSent))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef, 1)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(detailsOfFilesSent)

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any())
      }

      "must return OK with an empty list of submission records" in {
        val detailsOfFilesSent = DetailsOfFilesSent(Seq.empty, totalPages = 0)

        when(mockSubmissionService.getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any()))
          .thenReturn(ResultT.fromValue(detailsOfFilesSent))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef, 1)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(detailsOfFilesSent)

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any())
      }

      "must return InternalServerError when SubmissionService returns an error" in {
        when(mockSubmissionService.getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any()))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getSubmissionDetailsByCarfId(testCarfRef, 1)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Unexpected error")

        verify(mockSubmissionService, times(1)).getSubmissionDetailsByCarfId(eqTo(testCarfRef), eqTo(1))(any())
      }
    }
  }
}
