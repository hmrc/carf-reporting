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

import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT, OK}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.config.Constants.{clientIdHeader, sdesKeyHeader}
import uk.gov.hmrc.carfreporting.models.errors.ApiError.{BadRequestError, InternalServerError, JsonValidationError}
import uk.gov.hmrc.carfreporting.models.requests.sdes.FileTransferNotification
import uk.gov.hmrc.carfreporting.models.responses.FileListing
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class SDESConnector @Inject() (val config: AppConfig, val http: HttpClientV2) extends Logging {

  def sendFileReadyNotification(
      fileTransferNotification: FileTransferNotification
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): ResultT[Unit] = {

    val baseUrl = url"${config.sdesUrl}"

    logger.debug(
      s"[SDESConnector][sendFileReadyNotification] Sending File Ready Notification for uploadId/correlationId: " +
        s"${fileTransferNotification.audit.correlationID}"
    )

    ResultT.fromFuture(
      http
        .post(baseUrl)
        .setHeader(clientIdHeader -> config.sdesClientId)
        .withBody(Json.toJson(fileTransferNotification))
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case NO_CONTENT  => Right(())
            case BAD_REQUEST =>
              logger.warn(
                s"[SDESConnector][sendFileReadyNotification] Request Body was invalid: ${httpResponse.status} with " +
                  s"message: ${httpResponse.body}"
              )
              Left(BadRequestError)
            case _           =>
              logger.warn(
                s"[SDESConnector][sendFileReadyNotification] Unexpected response. Status code: ${httpResponse.status}, from endpoint: ${baseUrl.toURI}"
              )
              Left(InternalServerError)
          }
        }
        .recover { case NonFatal(e) =>
          logger.error(
            s"[SDESConnector][sendFileReadyNotification] Asynchronous call failed with message " +
              s"${e.getMessage}"
          )
          Left(InternalServerError)
        }
    )
  }

  def getBusinessRulesFileListing()(implicit hc: HeaderCarrier, ec: ExecutionContext): ResultT[Seq[FileListing]] = {
    val requestUrl = url"${config.sdesFileListingBaseUrl}/${config.sdesInformationType}"

    logger.debug("[SDESConnector][getBusinessRulesFileListing] Getting list of business rules response XML files")

    ResultT.fromFuture(
      http
        .get(requestUrl)
        .setHeader(clientIdHeader -> config.sdesClientId, sdesKeyHeader -> config.sdesSrn)
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case OK          =>
              Try(httpResponse.json.as[Seq[FileListing]]) match {
                case Success(fileList) => Right(fileList)
                case Failure(_)        =>
                  logger.warn(
                    s"[SDESConnector][getBusinessRulesFileListing] Error parsing response body from ${requestUrl.toURI}"
                  )
                  Left(JsonValidationError)
              }
            case otherStatus =>
              logger.warn(
                s"[SDESConnector][getBusinessRulesFileListing] Unexpected response. Status code: $otherStatus, from endpoint: ${requestUrl.toURI}"
              )
              Left(InternalServerError)
          }
        }
        .recover { case NonFatal(e) =>
          logger.error(
            s"[SDESConnector][getBusinessRulesFileListing] Asynchronous call failed with message ${e.getMessage}"
          )
          Left(InternalServerError)
        }
    )
  }
}
