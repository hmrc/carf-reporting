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

class UploadStatusSpec extends SpecBase {

  val statuses: List[UploadStatus] = List(NotStarted, Failed, InProgress, Quarantined)

  "UploadStatus" - {
    "json reads" - {
      statuses.foreach { status =>
        s"must return $status when _type is $status" in {
          val json = s"""{"_type": "$status"}"""
          Json.parse(json).as[UploadStatus] mustBe status
        }
      }

      "must return UploadedSuccessfully when _type is UploadedSuccessfully" in {
        val json =
          s"""{
            |"_type": "UploadedSuccessfully",
            |"name": "${uploadedSuccessfully.name}",
            |"mimeType": "${uploadedSuccessfully.mimeType}",
            |"downloadUrl": "$testDownloadUrl",
            |"size": ${uploadedSuccessfully.size.get},
            |"checksum": "${uploadedSuccessfully.checksum.get}"
            |}""".stripMargin

        Json.parse(json).as[UploadStatus] mustBe uploadedSuccessfully
      }

      "must return UploadRejected when _type is UploadRejected" in {
        val json             = """{"_type":"UploadRejected","details":{"failureReason":"REJECTED","message":"message"}}"""
        val expectedResponse = UploadRejected(ErrorDetails("REJECTED", "message"))

        Json.parse(json).as[UploadStatus] mustBe expectedResponse
      }

      "must return JsError" - {
        "when _type is unexpected value" in {
          val unexpectedValue = "RandomValue"
          val json            = s"""{"_type": "$unexpectedValue"}"""
          Json.parse(json).validate[UploadStatus] mustBe JsError(s"""Unexpected value of _type: "$unexpectedValue"""")
        }

        "when _type is missing from JSON" in {
          val json = """{"type": "RandomValue"}"""
          Json.parse(json).validate[UploadStatus] mustBe JsError("Missing _type field")
        }
      }
    }

    "json writes" - {
      statuses.foreach { status =>
        s"must set _type as $status when status is $status" in {
          val expectedJson = s"""{"_type":"$status"}"""
          Json.toJson(status).toString() mustBe expectedJson
        }
      }

      "must set _type as UploadedSuccessfully with file details in json when status is UploadedSuccessfully" in {
        val expectedJson =
          s"""{
            |  "name" : "${uploadedSuccessfully.name}",
            |  "mimeType" : "${uploadedSuccessfully.mimeType}",
            |  "downloadUrl" : "$testDownloadUrl",
            |  "size" : ${uploadedSuccessfully.size.get},
            |  "checksum" : "${uploadedSuccessfully.checksum.get}",
            |  "_type" : "UploadedSuccessfully"
            |}""".stripMargin

        Json.prettyPrint(Json.toJson(uploadedSuccessfully: UploadStatus)) mustBe expectedJson
      }

      "must set _type as UploadRejected with error details in json when status is UploadRejected" in {
        val expectedJson =
          """{"details":{"failureReason":"REJECTED","message":"Error message"},"_type":"UploadRejected"}"""

        Json.toJson(uploadRejected: UploadStatus).toString() mustBe expectedJson
      }
    }
  }
}
