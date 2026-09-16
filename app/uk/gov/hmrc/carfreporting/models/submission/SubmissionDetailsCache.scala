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

package uk.gov.hmrc.carfreporting.models.submission

import play.api.libs.json.*
import uk.gov.hmrc.carfreporting.models.{ExtractedCarfFileDetails, UploadId, ValidationErrors}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class SubmissionDetailsCache(
    _id: UploadId,
    carfId: CarfId,
    fileStatus: FileStatus,
    fileName: FileName,
    extractedFileDetails: ExtractedCarfFileDetails,
    rcaspDetails: RcaspDetails,
    subscriptionDetails: DisplaySubscriptionDetails,
    submissionTime: Instant,
    lastStatusUpdateTime: Instant,
    businessRuleErrors: ValidationErrors
)

case class CarfId(value: String) extends AnyVal

object CarfId {
  implicit val carfIdFormat: Format[CarfId] = Json.valueFormat[CarfId]
}

case class FileName(value: String) extends AnyVal

object FileName {
  implicit val fileNameFormat: Format[FileName] = Json.valueFormat[FileName]
}

object SubmissionDetailsCache {

  import play.api.libs.functional.syntax.*

  // (__ \ "_id").read(MongoFormats.objectIdFormat) and
  val reads: Reads[SubmissionDetailsCache] =
    (
      (__ \ "_id").read[UploadId] and
        (__ \ "carfId").read[CarfId] and
        (__ \ "fileStatus").read[FileStatus] and
        (__ \ "fileName").read[FileName] and
        (__ \ "extractedFileDetails").read[ExtractedCarfFileDetails] and
        (__ \ "rcaspDetails").read[RcaspDetails] and
        (__ \ "subscriptionDetails").read[DisplaySubscriptionDetails] and
        (__ \ "submissionTime").read(MongoJavatimeFormats.instantFormat) and
        (__ \ "lastStatusUpdateTime").read[Instant] and
        (__ \ "businessRuleErrors").read[ValidationErrors]
    )(SubmissionDetailsCache.apply _)

  private val writes: OWrites[SubmissionDetailsCache] =
    (
      (__ \ "_id").write[UploadId] and
        (__ \ "carfId").write[CarfId] and
        (__ \ "fileStatus").write[FileStatus] and
        (__ \ "fileName").write[FileName] and
        (__ \ "extractedFileDetails").write[ExtractedCarfFileDetails] and
        (__ \ "rcaspDetails").write[RcaspDetails] and
        (__ \ "subscriptionDetails").write[DisplaySubscriptionDetails] and
        (__ \ "submissionTime").write(MongoJavatimeFormats.instantFormat) and
        (__ \ "lastStatusUpdateTime").write[Instant] and
        (__ \ "businessRuleErrors").write[ValidationErrors]
    )(o => Tuple.fromProductTyped(o))

  implicit val format: OFormat[SubmissionDetailsCache] = OFormat(reads, writes)

}
