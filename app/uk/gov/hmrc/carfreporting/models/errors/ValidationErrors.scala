package uk.gov.hmrc.carfreporting.models.errors

import play.api.libs.json.{Json, OFormat}

case class ValidationErrors(
    fileError: Seq[FileError],
    recordError: Seq[RecordError]
)

object ValidationErrors {
  implicit val format: OFormat[ValidationErrors] = Json.format[ValidationErrors]
}

case class FileError(
    code: String,
    details: Option[String]
)

object FileError {
  implicit val format: OFormat[FileError] = Json.format[FileError]
}

case class RecordError(
    code: String,
    details: Option[String],
    docRefIDInError: Seq[String]
)

object RecordError {
  implicit val format: OFormat[RecordError] = Json.format[RecordError]
}
