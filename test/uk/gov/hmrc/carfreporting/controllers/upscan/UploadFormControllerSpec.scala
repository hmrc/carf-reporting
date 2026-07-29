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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.services.upscan.UploadProgressTracker
import uk.gov.hmrc.carfreporting.types.ResultT

class UploadFormControllerSpec extends SpecBase {

  val mockUploadProgressTracker: UploadProgressTracker = mock[UploadProgressTracker]

  val controller = new UploadFormController(mockUploadProgressTracker, cc)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUploadProgressTracker)
  }

  "UploadFormController" - {
    ".requestUpload" - {
      "must return OK when passed a valid request body" in {
        val upscanIdentifiers = UpscanIdentifiers(testUploadId, testReference)

        when(mockUploadProgressTracker.requestUpload(eqTo(testUploadId), eqTo(testReference)))
          .thenReturn(ResultT.fromValue(true))

        val result = controller.requestUpload(
          fakeRequestWithJsonBody(Json.toJson(upscanIdentifiers))
        )

        status(result) mustEqual OK

        verify(mockUploadProgressTracker, times(1)).requestUpload(eqTo(testUploadId), eqTo(testReference))
      }

      "must return InternalServerError when UploadProgressTracker returns an error" in {
        val upscanIdentifiers = UpscanIdentifiers(testUploadId, testReference)

        when(mockUploadProgressTracker.requestUpload(eqTo(testUploadId), eqTo(testReference)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.requestUpload(
          fakeRequestWithJsonBody(Json.toJson(upscanIdentifiers))
        )

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Error message")

        verify(mockUploadProgressTracker, times(1)).requestUpload(eqTo(testUploadId), eqTo(testReference))
      }

      "must return BadRequest when passed an invalid request body" in {
        val requestBody = """{ "fileReference" : "test" }"""

        val result = controller.requestUpload(
          fakeRequestWithJsonBody(Json.toJson(requestBody))
        )

        status(result) mustEqual BAD_REQUEST

        verify(mockUploadProgressTracker, times(0)).requestUpload(any(), any())
      }
    }

    ".getStatus" - {
      "must return NOT_FOUND when no upload status is retrieved" in {
        when(mockUploadProgressTracker.getUploadResult(eqTo(testUploadId))).thenReturn(ResultT.fromValue(None))

        val result = controller.getStatus(testUploadId.value)(fakeRequest)

        status(result) mustEqual NOT_FOUND

        verify(mockUploadProgressTracker, times(1)).getUploadResult(eqTo(testUploadId))
      }

      "must return OK when an upload status with no fields is retrieved" in {
        when(mockUploadProgressTracker.getUploadResult(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(Quarantined)))

        val result = controller.getStatus(testUploadId.value)(fakeRequest)

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.parse("""{"_type":"Quarantined"}""")

        verify(mockUploadProgressTracker, times(1)).getUploadResult(eqTo(testUploadId))
      }

      "must return OK when an UploadedSuccessfully is retrieved" in {
        when(mockUploadProgressTracker.getUploadResult(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(uploadedSuccessfully)))

        val result = controller.getStatus(testUploadId.value)(fakeRequest)

        val expectedJson: String =
          """{
            |  "name": "test.xml",
            |  "mimeType": "application/xml",
            |  "downloadUrl": "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            |  "size": 987,
            |  "checksum": "396f1",
            |  "_type": "UploadedSuccessfully"
            |}""".stripMargin

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.parse(expectedJson)

        verify(mockUploadProgressTracker, times(1)).getUploadResult(eqTo(testUploadId))
      }

      "must return OK when an UploadRejected is retrieved" in {
        when(mockUploadProgressTracker.getUploadResult(eqTo(testUploadId)))
          .thenReturn(ResultT.fromValue(Some(uploadRejected)))

        val result = controller.getStatus(testUploadId.value)(fakeRequest)

        val expectedJson: String =
          """{
            |  "details": {
            |    "failureReason": "REJECTED",
            |    "message": "Error message"
            |  },
            |  "_type": "UploadRejected"
            |}""".stripMargin

        status(result)        mustEqual OK
        contentAsJson(result) mustEqual Json.parse(expectedJson)

        verify(mockUploadProgressTracker, times(1)).getUploadResult(eqTo(testUploadId))
      }

      "must return INTERNAL_SERVER_ERROR when UploadProgressTracker returns an error" in {
        when(mockUploadProgressTracker.getUploadResult(eqTo(testUploadId)))
          .thenReturn(ResultT.fromError(MongoError("Error message")))

        val result = controller.getStatus(testUploadId.value)(fakeRequest)

        status(result)     mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Error message")

        verify(mockUploadProgressTracker, times(1)).getUploadResult(eqTo(testUploadId))
      }
    }
  }
}
