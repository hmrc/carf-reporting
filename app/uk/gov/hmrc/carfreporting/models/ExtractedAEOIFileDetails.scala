package uk.gov.hmrc.carfreporting.models

import play.api.libs.json.{Json, OFormat}

case class ExtractedAEOIFileDetails(
                                     validationErrors: ValidationErrors,
                                     validationResult: ValidationResult
                                   )

case class ValidationErrors(
                             fileError: Seq[FileError],
                             recordError: Seq[RecordError]
                           )

/**
 * 
 * @param status - can be 'Accepted' or 'Rejected' from an AEOI XML file. Furthermore, It can also be 'SchemaValidationError' 
 *               when an XML file fails schema validation or 'UnexpectedFailure' for any other unexpected errors.
 */

case class ValidationResult(status: String) //TODO Create enum for possible values, no benefit for now

case class FileError(
                      code: String,
                      details: Option[String]
                    )

case class RecordError(
                        code: String,
                        details: Option[String],
                        docRefIDInError: Seq[String]
                      )

object ExtractedAEOIFileDetails {
  implicit val format: OFormat[ExtractedAEOIFileDetails] = Json.format[ExtractedAEOIFileDetails]
}

object ValidationErrors {
  implicit val format: OFormat[ValidationErrors] = Json.format[ValidationErrors]
}

object ValidationResult {
  implicit val format: OFormat[ValidationResult] = Json.format[ValidationResult]
}

object FileError {
  implicit val format: OFormat[FileError] = Json.format[FileError]
}

object RecordError {
  implicit val format: OFormat[RecordError] = Json.format[RecordError]
}