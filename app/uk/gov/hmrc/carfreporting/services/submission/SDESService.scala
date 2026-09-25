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
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.models.requests.sdes.*
import uk.gov.hmrc.carfreporting.models.requests.sdes.Algorithm.SHA256
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SDESService @Inject() (sdesConnector: SDESConnector, appConfig: AppConfig)(implicit ec: ExecutionContext)
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
}
