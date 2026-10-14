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

package uk.gov.hmrc.carfreporting.connectors

import com.google.inject.Inject
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.connectors.additionalHeaders
import uk.gov.hmrc.carfreporting.models.*
import uk.gov.hmrc.carfreporting.models.errors.ApiError
import uk.gov.hmrc.carfreporting.models.requests.SubmissionHistoryRequest
import uk.gov.hmrc.carfreporting.models.responses.{SubmissionHistoryRecord, SubmissionHistoryResponse}
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.carfreporting.utils.ErrorDetailsHandler
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class SubmissionHistoryConnector @Inject() (
    config: AppConfig,
    http: HttpClientV2
)(implicit ec: ExecutionContext)
    extends Logging {

  def getSubmissionHistory(
      submissionHistoryRequest: SubmissionHistoryRequest
  )(implicit hc: HeaderCarrier): ResultT[Seq[SubmissionHistoryRecord]] = {
    val url = url"${config.submissionHistoryBaseUrl}"
    logger.info(
      s"[SubmissionHistoryConnector][getSubmissionHistory] Getting submission history for carfId: ${submissionHistoryRequest.submissionsListRequest.requestDetails.subscriptionId}"
    )

    ResultT.fromFuture {
      http
        .post(url)
        .setHeader(additionalHeaders(config, "submission-history"): _*)
        .withBody(Json.toJson(submissionHistoryRequest))
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case OK                                                                                         =>
              Try(httpResponse.json.as[SubmissionHistoryResponse]) match {
                case Success(submissionHistoryResponse) =>
                  logger.info("[SubmissionHistoryConnector][getSubmissionHistory] Success getting submission history")
                  Right(submissionHistoryResponse.submissionsListResponse.responseDetails.submissionsList)
                case Failure(exception)                 =>
                  logger.warn(
                    s"[SubmissionHistoryConnector][getSubmissionHistory] Error parsing response as SubmissionHistoryResponse. Endpoint: <${url.toURI}> Exception: <${exception.getMessage}>"
                  )
                  Left(ApiError.JsonValidationError)
              }
            case UNPROCESSABLE_ENTITY                                                                       =>
              ErrorDetailsHandler.errorParseForSubmissionHistory422(httpResponse)
            case BAD_REQUEST | INTERNAL_SERVER_ERROR | SERVICE_UNAVAILABLE | FORBIDDEN | METHOD_NOT_ALLOWED =>
              Left(ErrorDetailsHandler.errorParse(httpResponse, url))
            case _                                                                                          =>
              logger.warn(s"Unexpected response: status code: ${httpResponse.status}, from endpoint: ${url.toURI}")
              Left(ApiError.InternalServerError)
          }
        }
    }
  }
}
