package uk.gov.hmrc.carfreporting.models.submission

import org.bson.types.ObjectId
import play.api.libs.json.*
import uk.gov.hmrc.carfreporting.models.ExtractedFileDetails
import uk.gov.hmrc.carfreporting.models.errors.ValidationErrors
import uk.gov.hmrc.carfreporting.models.upscan.UploadId
import uk.gov.hmrc.mongo.play.json.formats.{MongoFormats, MongoJavatimeFormats}

import java.time.Instant

case class FileDetails(
    _id: ObjectId,
    uploadId: UploadId,
    carfId: String,
    fileStatus: FileStatus,
    fileName: String,
    extractedFileDetails: ExtractedFileDetails,
    rcaspDetails: RcaspDetails,
    subscriptionDetails: SubscriptionDetails,
    submissionTime: Instant,
    lastStatusUpdateTime: Instant,
    businessRuleErrors: Option[ValidationErrors] = None
)

object UploadSessionDetails {

  import play.api.libs.functional.syntax.*

  val reads: Reads[FileDetails] =
    (
      (__ \ "_id").read(MongoFormats.objectIdFormat) and
        (__ \ "uploadId").read[UploadId] and
        (__ \ "carfId").read[String] and
        (__ \ "fileStatus").read[FileStatus] and
        (__ \ "fileName").read[String] and
        (__ \ "extractedFileDetails").read[ExtractedFileDetails] and
        (__ \ "rcaspDetails").read[RcaspDetails] and
        (__ \ "subscriptionDetails").read[SubscriptionDetails] and
        (__ \ "submissionTime").read(MongoJavatimeFormats.instantFormat) and
        (__ \ "lastStatusUpdateTime").read[Instant] and
        (__ \ "businessRuleErrors").readNullable[ValidationErrors]
    )(FileDetails.apply _)

  private val writes: OWrites[FileDetails] =
    (
      (__ \ "_id").write(MongoFormats.objectIdFormat) and
        (__ \ "uploadId").write[UploadId] and
        (__ \ "carfId").write[String] and
        (__ \ "fileStatus").write[FileStatus] and
        (__ \ "fileName").write[String] and
        (__ \ "extractedFileDetails").write[ExtractedFileDetails] and
        (__ \ "rcaspDetails").write[RcaspDetails] and
        (__ \ "subscriptionDetails").write[SubscriptionDetails] and
        (__ \ "submissionTime").write(MongoJavatimeFormats.instantFormat) and
        (__ \ "lastStatusUpdateTime").write[Instant] and
        (__ \ "businessRuleErrors").writeNullable[ValidationErrors]
    )(o => Tuple.fromProductTyped(o))

  implicit val format: OFormat[FileDetails] = OFormat(reads, writes)

}
