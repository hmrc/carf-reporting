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

import play.api.libs.json.*

case class ExtractedAEOIFileDetails(
    uploadId: UploadId,
    validationErrors: ValidationErrors,
    validationResult: ValidationResult
)

case class ValidationErrors(
    fileError: Seq[FileError] = Seq.empty,
    recordError: Seq[RecordError] = Seq.empty
)

/** @param status
  *   \- can be 'Accepted' or '1' from an AEOI XML file. Furthermore, It can also be 'SchemaValidationError' when an XML
  *   file fails schema validation or 'UnexpectedFailure' for any other unexpected errors.
  */

case class ValidationResult(status: ValidationStatus)

enum ValidationStatus:
  case Accepted
  case Rejected

object ValidationStatus {

  def fromString(value: String): ValidationStatus =
    value match {
      case "Accepted" => Accepted
      case "Rejected" => Rejected
      case _          => throw new RuntimeException("No match found for provided Validation status string")
    }

  given Writes[ValidationStatus] = Writes[ValidationStatus] {
    case Accepted => JsString("Accepted")
    case Rejected => JsString("Rejected")
  }

  given Reads[ValidationStatus] = Reads[ValidationStatus] {
    case JsString(s) =>
      s.toUpperCase match {
        case "Accepted" => JsSuccess(Accepted)
        case "Rejected" => JsSuccess(Rejected)
        case _          => JsError(s"Unexpected ValidationStatus: $s")
      }
    case other       =>
      JsError(
        "Expected JSON string for ValidationStatus from the following options\n" +
          s"${ValidationStatus.values.mkString(", ")}, but got ${other.toString}"
      )
  }
}

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
