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
import uk.gov.hmrc.carfreporting.models.submission.FileName

final case class FileTransferNotification(
    informationType: String,
    file: File,
    audit: Audit
)

final case class File(
    name: FileName,
    location: String,
    checksum: Checksum,
    size: Long,
    recipientOrSender: Option[String],
    properties: List[Property]
)

final case class Audit(correlationID: String)

final case class Checksum(algorithm: Algorithm, value: String)

final case class Property(name: String, value: String)

object FileTransferNotification {
  given OFormat[FileTransferNotification] = Json.format[FileTransferNotification]
}

object File {
  given OFormat[File] = Json.format[File]
}

object Audit {
  given OFormat[Audit] = Json.format[Audit]
}

object Checksum {
  given OFormat[Checksum] = Json.format[Checksum]
}

object Property {
  given OFormat[Property] = Json.format[Property]
}
