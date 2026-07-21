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

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackDispatcher @Inject() (
    uploadProgressTracker: UploadProgressTracker
)(implicit val ec: ExecutionContext)
    extends Logging {

  def handleCallback(callback: CallbackBody): Future[Boolean] = {
    val uploadStatus: UploadStatus = callback match {

      case s: ReadyCallbackBody =>
        UploadedSuccessfully(
          s.uploadDetails.fileName,
          s.uploadDetails.fileMimeType,
          s.downloadUrl,
          Some(s.uploadDetails.size),
          Some(s.uploadDetails.checksum)
        )

      case q: FailedCallbackBody if q.failureDetails.failureReason == "QUARANTINE" =>
        logger.warn(s"FailedCallbackBody, QUARANTINE: ${q.reference.value}")
        Quarantined

      case r: FailedCallbackBody if r.failureDetails.failureReason == "REJECTED" =>
        logger.warn(s"FailedCallbackBody, REJECTED: ${r.reference.value}")
        UploadRejected(r.failureDetails)

      case f: FailedCallbackBody =>
        logger.warn(s"FailedCallbackBody: ${f.reference.value}")
        Failed
    }

    uploadProgressTracker.registerUploadResult(callback.reference, uploadStatus)
  }
}
