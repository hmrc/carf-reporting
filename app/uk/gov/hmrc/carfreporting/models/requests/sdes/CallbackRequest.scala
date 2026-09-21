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

package uk.gov.hmrc.carfreporting.models.requests.sdes

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.submission.NotificationType

import java.time.LocalDateTime

case class CallbackRequest(
    notification: NotificationType,
    filename: String,
    checksumAlgorithm: Algorithm,
    checksum: String,
    correlationID: UploadId,
    dateTime: Option[LocalDateTime],
    failureReason: Option[String] = None
)

object CallbackRequest {
  implicit val format: OFormat[CallbackRequest] = Json.format[CallbackRequest]
}
