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

package uk.gov.hmrc.carfreporting.services.submission

import play.api.Logging
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.connectors.SDESConnector
import uk.gov.hmrc.carfreporting.helpers.SDESFileMetadataHelper
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.errors.ApiError.InternalServerError
import uk.gov.hmrc.carfreporting.models.errors.{InvalidXmlError, XmlErrors}
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.models.requests.sdes.*
import uk.gov.hmrc.carfreporting.models.requests.sdes.Algorithm.SHA256
import uk.gov.hmrc.carfreporting.models.submission.FileStatus
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.services.XmlParserService
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SDESService @Inject() (
    sdesConnector: SDESConnector,
    xmlParserService: XmlParserService,
    repository: SubmissionRepository,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def sendNotification(submissionRequest: SubmissionRequest, submissionTime: Instant)(implicit
      hc: HeaderCarrier
  ): ResultT[Unit] = {

    val conversationId = submissionRequest.uploadId.value

    sdesConnector.sendFileReadyNotification(
      FileTransferNotification(
        informationType = appConfig.sdesInformationType,
        file = File(
          name = submissionRequest.fileName,
          location = submissionRequest.documentUrl,
          checksum = Checksum(SHA256, submissionRequest.checksum),
          size = submissionRequest.fileSize,
          recipientOrSender = Some("carf-reporting"),
          properties = SDESFileMetadataHelper.generatePropertiesMetadata(submissionRequest, submissionTime)
        ),
        audit = Audit(conversationId)
      )
    )
  }

  def getAndProcessBusinessRulesResponseFile(
      uploadId: UploadId,
      fileName: String
  )(implicit hc: HeaderCarrier): ResultT[Unit] =
    ResultT.fromFuture {
      sdesConnector.getBusinessRulesFileListing().value.flatMap {
        case Right(fileList) =>
          fileList
            .find(_.filename == fileName)
            .fold {
              logger.warn("[SDESService][getAndProcessBusinessRulesResponseFile] No file with matching file name found")
              repository.updateStatus(uploadId, FileStatus.UnexpectedError).value.map(_ => Left(InternalServerError))
            } { fileListingRecord =>
              xmlParserService.validateAndExtractAEOI(fileListingRecord.downloadURL, uploadId).value.map {
                case Right(_)                   => Right(())
                case Left(xmlErrors: XmlErrors) =>
                  logger.warn(
                    s"[SDESService][getAndProcessBusinessRulesResponseFile] Failed to validate XML with (${xmlErrors.errors.size}) schema error(s)"
                  )
                  Left(xmlErrors)
                case Left(InvalidXmlError)      =>
                  logger.warn("[SDESService][getAndProcessBusinessRulesResponseFile] Error parsing XML file")
                  Left(InvalidXmlError)
                case Left(error)                =>
                  logger.warn(
                    s"[SDESService][getAndProcessBusinessRulesResponseFile] Unexpected error with message: ${error.message}"
                  )
                  Left(InternalServerError)
              }
            }
        case Left(error)     =>
          logger.warn(
            s"[SDESService][getAndProcessBusinessRulesResponseFile] Error getting business rules file listing: $error"
          )
          repository.updateStatus(uploadId, FileStatus.UnexpectedError).value.map(_ => Left(error))
      }
    }
}
