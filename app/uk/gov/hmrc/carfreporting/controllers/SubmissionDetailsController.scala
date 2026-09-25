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

package uk.gov.hmrc.carfreporting.controllers

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.carfreporting.controllers.actions.AuthAction
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.submission.FileStatus
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SubmissionDetailsController @Inject() (
    authorise: AuthAction,
    cc: ControllerComponents,
    submissionRepository: SubmissionRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getFileStatus(uploadId: String): Action[AnyContent] = authorise.async { implicit request =>
    submissionRepository.findByUploadId(UploadId(uploadId)).value.map {
      case Right(Some(submissionDetails)) =>
        Ok(Json.toJson(submissionDetails.fileStatus))
      case Right(None)                    =>
        logger.warn(s"[SubmissionDetailsController][getFileStatus] Submission details not found for uploadId $uploadId")
        NotFound
      case Left(error)                    =>
        logger.warn(
          s"[SubmissionDetailsController][getFileStatus] Error getting submission details for uploadId $uploadId"
        )
        InternalServerError(s"Unexpected error: $error")
    }
  }

  def getSubmissionDetailsByUploadId(uploadId: String): Action[AnyContent] = authorise.async { implicit request =>
    submissionRepository.findByUploadId(UploadId(uploadId)).value.map {
      case Right(Some(submissionDetails)) =>
        Ok(Json.toJson(submissionDetails))
      case Right(None)                    =>
        logger.warn(
          s"[SubmissionDetailsController][getSubmissionDetailsByUploadId] Submission details not found for uploadId $uploadId"
        )
        NotFound
      case Left(error)                    =>
        logger.warn(
          s"[SubmissionDetailsController][getSubmissionDetailsByUploadId] Error getting submission details for uploadId $uploadId"
        )
        InternalServerError(s"Unexpected error: $error")
    }
  }

  def getSubmissionDetailsByCarfId(carfId: String): Action[AnyContent] = authorise.async { implicit request =>
    submissionRepository.findByCarfId(carfId).value.map {
      case Right(submissionDetailsList) =>
        Ok(Json.toJson(submissionDetailsList))
      case Left(error)                  =>
        logger.warn(
          s"[SubmissionDetailsController][getSubmissionDetailsByCarfId] Error getting submission details for carfId $carfId"
        )
        InternalServerError(s"Unexpected error: $error")
    }
  }
}
