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
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.ApiError.{BadRequestError, InternalServerError}
import uk.gov.hmrc.carfreporting.models.requests.sdes.FileTransferNotification
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class SDESConnector @Inject() (val config: AppConfig, val http: HttpClientV2) extends Logging {

  def sendFileReadyNotification(
      fileTransferNotification: FileTransferNotification
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): ResultT[Unit] = {

    val baseUrl = url"${config.sdesBaseUrl}/notification/fileready" // TODO add /carf-stubs to url and toggle in config

    logger.debug(
      s"[SDESConnector][sendFileReadyNotification] Sending File Ready Notification for uploadId/correlationId: " +
        s"${fileTransferNotification.audit.correlationID}"
    )

    ResultT.fromFuture(
      http
        .post(baseUrl)
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
}
