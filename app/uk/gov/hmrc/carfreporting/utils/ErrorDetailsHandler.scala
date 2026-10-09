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

package uk.gov.hmrc.carfreporting.utils

import play.api.Logging
import uk.gov.hmrc.carfreporting.models.errors.{ApiError, ErrorDetail}
import uk.gov.hmrc.carfreporting.models.errors.ApiError.*
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryRecord
import uk.gov.hmrc.http.HttpResponse

import java.net.URL
import scala.util.{Failure, Success, Try}

object ErrorDetailsHandler extends Logging {

  def errorParse(response: HttpResponse, endpoint: URL): ApiError =
    logger.warn(s"[ErrorDetailsHandler][errorParse] Status code: ${response.status} from endpoint: ${endpoint.toURI}")
    Try(response.json.as[ErrorDetail]) match {
      case Success(error)     =>
        logger.warn(
          s"[ErrorDetailsHandler][errorParse] Error code: ${error.errorDetail.errorCode}. Error message: ${error.errorDetail.errorMessage}. Source fault detail: ${error.errorDetail.sourceFaultDetail}"
        )
        InternalServerError
      case Failure(exception) =>
        logger.warn(
          s"[ErrorDetailsHandler][errorParse] Error parsing response as ErrorDetails. Exception: <${exception.getMessage}>"
        )
        JsonValidationError
    }

  def errorParseForSubmissionHistory422(
      response: HttpResponse
  ): Either[ApiError, Seq[SubmissionHistoryRecord]] =
    Try(response.json.as[ErrorDetail]) match {
      case Success(error)
          if error.errorDetail.sourceFaultDetail.flatMap(_.detail.headOption).forall(_.trim.startsWith("001")) =>
        logger.info(
          "[ErrorDetailsHandler][errorParseForSubmissionHistory422] UnprocessableEntity from submission history API with detail 001. No records found."
        )
        Right(Seq.empty[SubmissionHistoryRecord])
      case Success(error)     =>
        logger.warn(
          s"[ErrorDetailsHandler][errorParseForSubmissionHistory422] Error code: ${error.errorDetail.errorCode}. Error message: ${error.errorDetail.errorMessage}. Source fault detail: ${error.errorDetail.sourceFaultDetail}"
        )
        Left(InternalServerError)
      case Failure(exception) =>
        logger.warn(
          s"[ErrorDetailsHandler][errorParseForSubmissionHistory422] Error parsing response as ErrorDetails. Exception: <${exception.getMessage}>"
        )
        Left(JsonValidationError)
    }
}
