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

import play.api.libs.json.*

import java.time.Instant

sealed trait CallbackBody {
  def reference: Reference
}

object CallbackBody {

  implicit val reads: Reads[CallbackBody] = (json: JsValue) =>
    json \ "fileStatus" match {
      case JsDefined(JsString("READY"))  =>
        implicitly[Reads[ReadyCallbackBody]].reads(json)
      case JsDefined(JsString("FAILED")) =>
        implicitly[Reads[FailedCallbackBody]].reads(json)
      case JsDefined(value)              => JsError(s"Invalid type distriminator: $value")
      case _                             => JsError("Missing type distriminator")
    }
}

case class UploadDetails(
    uploadTimestamp: Instant,
    checksum: String,
    fileMimeType: String,
    fileName: String,
    size: Long
)

object UploadDetails {
  implicit val format: OFormat[UploadDetails] = Json.format[UploadDetails]
}

case class ReadyCallbackBody(
    reference: Reference,
    downloadUrl: String,
    uploadDetails: UploadDetails
) extends CallbackBody

object ReadyCallbackBody {
  implicit val format: OFormat[ReadyCallbackBody] = Json.format[ReadyCallbackBody]
}

case class FailedCallbackBody(
    reference: Reference,
    failureDetails: ErrorDetails
) extends CallbackBody

object FailedCallbackBody {
  implicit val format: OFormat[FailedCallbackBody] = Json.format[FailedCallbackBody]
}
