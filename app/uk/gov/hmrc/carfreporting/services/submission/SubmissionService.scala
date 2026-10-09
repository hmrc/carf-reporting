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
import uk.gov.hmrc.carfreporting.models.errors.ApiError.NotFoundError
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.models.submission.*
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.Pending
import uk.gov.hmrc.carfreporting.models.{UploadId, ValidationErrors}
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionService @Inject() (
    sdesService: SDESService,
    repository: SubmissionRepository,
    submissionHistoryService: SubmissionHistoryService,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def saveAndSubmit(submissionRequest: SubmissionRequest)(implicit headerCarrier: HeaderCarrier): ResultT[Unit] = {
    val submissionTime  = Instant.now
    val submissionCache = SubmissionDetailsCache(
      submissionRequest.uploadId,
      submissionRequest.subscriptionDetails.carfReference,
      Pending,
      submissionRequest.fileName,
      submissionRequest.extractedFileDetails,
      submissionRequest.rcaspDetails,
      submissionRequest.subscriptionDetails,
      submissionTime,
      Instant.now,
      ValidationErrors.apply()
    )
    for {
      _      <- repository.insert(submissionCache)
      result <- sdesService.sendNotification(submissionRequest, submissionTime)
    } yield result
  }

  def updateFileStatusAsFailure(uploadId: UploadId, maybeFailureReason: Option[String]): ResultT[Unit] =
    maybeFailureReason
      .fold {
        repository.updateStatus(uploadId, FileStatus.UnexpectedError)
      } { failureReason =>
        if (failureReason.toLowerCase.contains("virus")) {
          repository.updateStatus(uploadId, FileStatus.VirusFound)
        } else {
          repository.updateStatus(uploadId, FileStatus.UnexpectedError)
        }
      }
      .map(_ => ())

  def getSubmissionDetailsByUploadId(uploadId: UploadId): ResultT[SubmissionDetailsCache] =
    repository
      .findByUploadId(uploadId)
      .leftMap { error =>
        logger.warn(
          s"[SubmissionService][getSubmissionDetailsByUploadId] Error getting submission details for uploadId $uploadId"
        )
        error
      }
      .subflatMap { maybeSubmissionDetails =>
        maybeSubmissionDetails.fold {
          logger.warn(
            s"[SubmissionService][getSubmissionDetailsByUploadId] Submission details not found for uploadId $uploadId"
          )
          Left(NotFoundError)
        }(Right(_))
      }

  def getSubmissionDetailsByCarfId(carfId: String, page: Int)(implicit hc: HeaderCarrier): ResultT[DetailsOfFilesSent] =
    ResultT.fromFuture {
      repository.findByCarfId(carfId).value.flatMap {
        case Left(error)                       =>
          logger.warn(
            s"[SubmissionService][getSubmissionDetailsByCarfId] Error getting submission details from repository for carfId $carfId"
          )
          Future.successful(Left(error))
        case Right(submissionDetailsCacheList) =>
          submissionHistoryService.getSubmissionHistory(carfId).value.map {
            case Left(error)                        =>
              logger.warn(
                s"[SubmissionService][getSubmissionDetailsByCarfId] Error getting submission history for carfId $carfId"
              )
              Left(error)
            case Right(submissionHistoryPassedList) =>
              val submissionRecordList: Seq[SubmissionRecord] =
                submissionDetailsCacheList ++ submissionHistoryPassedList

              val pageSize                   = appConfig.submittedFilesPageSize
              val totalPages                 = (submissionRecordList.size + pageSize - 1) / pageSize
              val submissionRecordSlicedList =
                submissionRecordList.slice((page - 1) * pageSize, (page - 1) * pageSize + pageSize)

              Right(DetailsOfFilesSent(submissionRecordSlicedList, totalPages))
          }
      }
    }
}
