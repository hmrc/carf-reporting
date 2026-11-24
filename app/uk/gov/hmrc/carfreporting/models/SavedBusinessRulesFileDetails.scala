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

package uk.gov.hmrc.carfreporting.models

import org.bson.types.ObjectId
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.{MongoFormats, MongoJavatimeFormats}

import java.time.Instant

case class SavedBusinessRulesFileDetails(
    _id: ObjectId,
    extractedBusinessRulesFileDetails: ExtractedBusinessRulesFileDetails,
    lastUpdated: Instant = Instant.now
)

object SavedBusinessRulesFileDetails {

  import play.api.libs.functional.syntax.*

  val reads: Reads[SavedBusinessRulesFileDetails] =
    (
      (__ \ "_id").read(MongoFormats.objectIdFormat) and
        (__ \ "extractedBusinessRulesFileDetails").read[ExtractedBusinessRulesFileDetails] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
    )(SavedBusinessRulesFileDetails.apply _)

  private val writes: OWrites[SavedBusinessRulesFileDetails] =
    (
      (__ \ "_id").write(MongoFormats.objectIdFormat) and
        (__ \ "extractedBusinessRulesFileDetails").write[ExtractedBusinessRulesFileDetails] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
    )(o => Tuple.fromProductTyped(o))

  val mongoFormat: OFormat[SavedBusinessRulesFileDetails] = OFormat(reads, writes)
}
