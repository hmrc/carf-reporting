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

package uk.gov.hmrc.carfreporting.models.upscan

import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.carfreporting.base.SpecBase

import java.time.Instant

class CallbackBodySpec extends SpecBase {

  "CallbackBody" - {
    "must marshall correctly when upload is finished" in {
      val json =
        """
          |{
          |   "fileStatus": "READY",
          |   "reference": "ref",
          |   "downloadUrl": "http://test.com",
          |   "uploadDetails": {
          |     "uploadTimestamp": 1591464117,
          |     "checksum": "396f1",
          |     "fileMimeType": "application/xml",
          |     "fileName": "test.xml",
          |     "size": 987
          |   }
          |}""".stripMargin

      val expectedResult = ReadyCallbackBody(
        Reference("ref"),
        "http://test.com",
        UploadDetails(
          uploadTimestamp = Instant.ofEpochMilli(1591464117),
          checksum = "396f1",
          fileMimeType = "application/xml",
          fileName = "test.xml",
          size = 987L
        )
      )

      Json.parse(json).as[CallbackBody] mustBe expectedResult
    }

    "must marshall correctly when upload has failed" in {
      val json =
        """
          |{
          |   "fileStatus": "FAILED",
          |   "reference": "ref",
          |   "failureDetails": {
          |     "failureReason": "REJECTED",
          |     "message": "Error message"
          |   }
          |}""".stripMargin

      val expectedResult = FailedCallbackBody(
        Reference("ref"),
        errorDetails("REJECTED")
      )

      Json.parse(json).as[CallbackBody] mustBe expectedResult
    }

    "must return JsError" - {
      "when fileStatus is unexpected value" in {
        val unexpectedValue = "RandomValue"
        val json            = s"""{"fileStatus": "$unexpectedValue"}"""
        Json.parse(json).validate[CallbackBody] mustBe JsError(s"""Invalid type distriminator: "$unexpectedValue"""")
      }

      "when fileStatus is missing from JSON" in {
        val json = """{"_type": "RandomValue"}"""
        Json.parse(json).validate[CallbackBody] mustBe JsError("Missing type distriminator")
      }
    }
  }

}
