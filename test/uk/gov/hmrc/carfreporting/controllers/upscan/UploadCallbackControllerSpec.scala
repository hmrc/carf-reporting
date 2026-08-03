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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsString, status}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.services.upscan.UpscanCallbackDispatcher
import uk.gov.hmrc.carfreporting.types.ResultT

class UploadCallbackControllerSpec extends SpecBase {

  val mockUpscanCallbackDispatcher: UpscanCallbackDispatcher = mock[UpscanCallbackDispatcher]

  val controller = new UploadCallbackController(mockUpscanCallbackDispatcher, cc)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanCallbackDispatcher)
  }

  "UploadCallbackController" - {
    "must return OK when a valid request is passed with a ReadyCallbackBody" in {
      val requestBody = Json.parse(
        """{
            "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            "uploadDetails" : {
              "fileName" : "test.xml",
              "fileMimeType" : "application/xml",
              "uploadTimestamp" : "2018-04-24T09:30:00Z",
              "checksum" : "396f1",
              "size" : 987
              },
            "fileStatus" : "READY"
          }"""
      )

      when(mockUpscanCallbackDispatcher.handleCallback(any())).thenReturn(ResultT.fromValue(true))

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result) mustEqual OK

      verify(mockUpscanCallbackDispatcher, times(1)).handleCallback(any())
    }

    "must return OK when a valid request is passed with a FailedCallbackBody (QUARANTINE)" in {
      val requestBody = Json.parse(
        """{
            "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "fileStatus" : "FAILED",
            "failureDetails": {
                "failureReason": "QUARANTINE",
                "message": "e.g. This file has a virus"
              }
            }"""
      )

      when(mockUpscanCallbackDispatcher.handleCallback(any())).thenReturn(ResultT.fromValue(true))

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result) mustEqual OK

      verify(mockUpscanCallbackDispatcher, times(1)).handleCallback(any())
    }

    "must return OK when a valid request is passed with a FailedCallbackBody (REJECTED)" in {
      val requestBody = Json.parse(
        """{
            "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "fileStatus" : "FAILED",
            "failureDetails": {
                "failureReason": "REJECTED",
                "message": "MIME type $mime is not allowed for service $service-name"
              }
            }"""
      )

      when(mockUpscanCallbackDispatcher.handleCallback(any())).thenReturn(ResultT.fromValue(true))

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result) mustEqual OK

      verify(mockUpscanCallbackDispatcher, times(1)).handleCallback(any())
    }

    "must return OK when a valid request is passed with a FailedCallbackBody (UNKNOWN)" in {
      val requestBody = Json.parse(
        """{
            "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            "fileStatus" : "FAILED",
            "failureDetails": {
                "failureReason": "UNKNOWN",
                "message": "Something unknown happened"
              }
            }"""
      )

      when(mockUpscanCallbackDispatcher.handleCallback(any())).thenReturn(ResultT.fromValue(true))

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result) mustEqual OK

      verify(mockUpscanCallbackDispatcher, times(1)).handleCallback(any())
    }

    "must return InternalServerError when UpscanCallbackDispatcher returns an error" in {
      val requestBody = Json.parse(
        """{
          "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
          "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          "uploadDetails" : {
            "fileName" : "test.xml",
            "fileMimeType" : "application/xml",
            "uploadTimestamp" : "2018-04-24T09:30:00Z",
            "checksum" : "396f1",
            "size" : 987
            },
          "fileStatus" : "READY"
        }"""
      )

      when(mockUpscanCallbackDispatcher.handleCallback(any()))
        .thenReturn(ResultT.fromError(MongoError("Error message")))

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result)     mustEqual INTERNAL_SERVER_ERROR
      contentAsString(result) must include("Error message")

      verify(mockUpscanCallbackDispatcher, times(1)).handleCallback(any())
    }

    "must return BadRequest when an invalid request is passed" in {
      val requestBody = Json.parse(
        """{"reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d"}"""
      )

      val result = controller.callback(
        fakeRequestWithJsonBody(Json.toJson(requestBody))
      )

      status(result) mustEqual BAD_REQUEST

      verify(mockUpscanCallbackDispatcher, times(0)).handleCallback(any())
    }
  }

}
