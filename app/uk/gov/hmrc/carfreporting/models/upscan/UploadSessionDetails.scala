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

import org.bson.types.ObjectId
import play.api.libs.json.*
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.crypto.CryptoType.CryptoT
import uk.gov.hmrc.carfreporting.models.crypto.SensitiveStatus
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.mongo.play.json.formats.{MongoFormats, MongoJavatimeFormats}

import java.time.Instant

case class UploadSessionDetails(
    _id: ObjectId,
    uploadId: UploadId,
    reference: Reference,
    status: UploadStatus,
    lastUpdated: Instant = Instant.now
)

object UploadSessionDetails {

  import play.api.libs.functional.syntax.*

  val reads: Reads[UploadSessionDetails] =
    (
      (__ \ "_id").read(MongoFormats.objectIdFormat) and
        (__ \ "uploadId").read[UploadId] and
        (__ \ "reference").read[Reference] and
        (__ \ "status").read[UploadStatus] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
    )(UploadSessionDetails.apply _)

  private val writes: OWrites[UploadSessionDetails] =
    (
      (__ \ "_id").write(MongoFormats.objectIdFormat) and
        (__ \ "uploadId").write[UploadId] and
        (__ \ "reference").write[Reference] and
        (__ \ "status").write[UploadStatus] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
    )(o => Tuple.fromProductTyped(o))

  implicit val format: OFormat[UploadSessionDetails] = OFormat(reads, writes)

  def mongoFormat(encryptionEnabled: Boolean)(implicit crypto: CryptoT): OFormat[UploadSessionDetails] = {
    implicit val sensitiveStatusFormat: Format[SensitiveStatus] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveStatus.apply)

    val mongoReads: Reads[UploadSessionDetails] =
      if (encryptionEnabled)
        (
          (__ \ "_id").read(MongoFormats.objectIdFormat) and
            (__ \ "uploadId").read[UploadId] and
            (__ \ "reference").read[Reference] and
            (__ \ "status").read[SensitiveStatus].map(_.decryptedValue) and
            (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
        )(UploadSessionDetails.apply _)
      else reads

    val mongoWrites: OWrites[UploadSessionDetails] =
      if (encryptionEnabled)
        (
          (__ \ "_id").write(MongoFormats.objectIdFormat) and
            (__ \ "uploadId").write[UploadId] and
            (__ \ "reference").write[Reference] and
            (__ \ "status").write[SensitiveStatus] and
            (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
        )(o => Tuple.fromProductTyped(o._id, o.uploadId, o.reference, SensitiveStatus(o.status), o.lastUpdated))
      else writes

    OFormat(mongoReads, mongoWrites)
  }
}
