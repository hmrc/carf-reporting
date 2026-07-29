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

package uk.gov.hmrc.carfreporting.services.upscan

import play.api.Logging
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.models.upscan.UploadStatus.*
import uk.gov.hmrc.carfreporting.types.ResultT

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class UpscanCallbackDispatcher @Inject() (
    uploadProgressTracker: UploadProgressTracker
)(implicit val ec: ExecutionContext)
    extends Logging {

  def handleCallback(callback: CallbackBody): ResultT[Boolean] = {
    val uploadStatus: UploadStatus = callback match {

      case body: ReadyCallbackBody =>
        UploadedSuccessfully(
          body.uploadDetails.fileName,
          body.uploadDetails.fileMimeType,
          body.downloadUrl,
          Some(body.uploadDetails.size),
          Some(body.uploadDetails.checksum)
        )

      case body: FailedCallbackBody if body.failureDetails.failureReason == "QUARANTINE" =>
        logger.warn(s"FailedCallbackBody, QUARANTINE: ${body.reference.value}")
        Quarantined

      case body: FailedCallbackBody if body.failureDetails.failureReason == "REJECTED" =>
        logger.warn(s"FailedCallbackBody, REJECTED: ${body.reference.value}")
        UploadRejected(body.failureDetails)

      case body: FailedCallbackBody =>
        logger.warn(s"FailedCallbackBody: ${body.reference.value}")
        Failed
    }

    uploadProgressTracker.registerUploadResult(callback.reference, uploadStatus)
  }
}
