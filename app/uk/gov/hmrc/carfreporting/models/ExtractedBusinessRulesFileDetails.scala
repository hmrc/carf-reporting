package uk.gov.hmrc.carfreporting.models
import uk.gov.hmrc.carfreporting.models.errors.ValidationErrors

case class ExtractedBusinessRulesFileDetails(
    validationErrors: ValidationErrors,
    validationResult: ValidationResult
)

/** @param status
  *   \- can be 'Accepted' or 'Rejected' from an AEOI XML file. Furthermore, It can also be 'UnprocessableErrorFile'
  *   when an XML file fails schema validation or 'UnexpectedError' for any other unexpected errors.
  */

case class ValidationResult(status: String) //TODO Create enum for possible values, no benefit for now
