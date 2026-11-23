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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT, OK}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.config.Constants.{clientIdHeader, sdesKeyHeader}
import uk.gov.hmrc.carfreporting.models.errors.ApiError.*
import uk.gov.hmrc.carfreporting.models.errors.CarfError
import uk.gov.hmrc.carfreporting.models.requests.sdes.FileTransferNotification
import uk.gov.hmrc.carfreporting.models.responses.FileListing
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.client.{HttpClientV2, given}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FtsConnector @Inject() (val config: AppConfig, val http: HttpClientV2)(using Materializer) extends Logging {

  def sendFileReadyNotification(
      fileTransferNotification: FileTransferNotification
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): ResultT[Unit] = {

    val baseUrl = url"${config.sdesUrl}"

    logger.info(
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
              logger.error(
                s"[SDESConnector][sendFileReadyNotification] Request Body was invalid: ${httpResponse.status} with " +
                  s"message: ${httpResponse.body}"
              )
              Left(BadRequestError)
            case _           =>
              logger.error(
                s"[SDESConnector][sendFileReadyNotification] Unexpected response from file ready call. Status code: ${httpResponse.status}"
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
    val requestUrl = url"${config.sdesFileListingBaseUrl}/${config.sdesInformationTypeListing}"

    logger.info("[SDESConnector][getBusinessRulesFileListing] Getting list of business rules response XML files")

    ResultT.fromFuture(
      http
        .get(requestUrl)
        .setHeader(clientIdHeader -> config.sdesClientIdListing, sdesKeyHeader -> config.sdesSrnListing)
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case OK          =>
              Try(httpResponse.json.as[Seq[FileListing]]) match {
                case Success(fileList) => Right(fileList)
                case Failure(_)        =>
                  logger.error(
                    s"[SDESConnector][getBusinessRulesFileListing] Error parsing response body from business rules lookup"
                  )
                  Left(JsonValidationError)
              }
            case otherStatus =>
              logger.error(
                s"[SDESConnector][getBusinessRulesFileListing] Unexpected response from business rules lookup. Status code: $otherStatus"
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

  def downloadBusinessRulesFile(
      url: URL
  )(using ec: ExecutionContext): ResultT[InputStream] =
    ResultT.fromFuture(
      http
        .get(url)(using HeaderCarrier())
        .withProxy
        .stream[Source[ByteString, ?]]
        .map[Either[CarfError, InputStream]](src =>
          Right(
            src.runWith(
              StreamConverters.asInputStream(readTimeout = config.downloadBusinessRulesFileTimeoutInSeconds.seconds)
            )
          )
        )
        .recover {
          case e: UpstreamErrorResponse if e.statusCode == 404 =>
            Left(NotFoundError)
          case e: UpstreamErrorResponse if e.statusCode == 410 =>
            Left(FileGoneError)
          case NonFatal(e)                                     =>
            logger.error(s"Failed to download from ${url.getHost}${url.getPath}", e)
            Left(InternalServerError)
        }
    )
}
