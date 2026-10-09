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
import uk.gov.hmrc.carfreporting.connectors.SubmissionHistoryConnector
import uk.gov.hmrc.carfreporting.models.requests.{SubmissionHistoryRequest, SubmissionHistoryRequestCommon, SubmissionHistoryRequestDetails, SubmissionsListRequest}
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryStatus.Passed
import uk.gov.hmrc.carfreporting.models.submission.*
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.HeaderCarrier

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SubmissionHistoryService @Inject() (
    submissionHistoryConnector: SubmissionHistoryConnector,
    clock: Clock,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def getSubmissionHistory(carfId: String)(implicit hc: HeaderCarrier): ResultT[Seq[SubmissionHistoryPassed]] = {
    val submissionHistoryRequest = SubmissionHistoryRequest(
      submissionsListRequest = SubmissionsListRequest(
        requestCommon = SubmissionHistoryRequestCommon(
          originatingSystem = "MDTP",
          transmittingSystem = "CADX",
          regime = "CARF",
          requestParameters = None
        ),
        requestDetails = SubmissionHistoryRequestDetails(subscriptionId = carfId, rcaspId = None)
      )
    )

    submissionHistoryConnector
      .getSubmissionHistory(submissionHistoryRequest)
      .leftMap { error =>
        logger.warn(s"[SubmissionHistoryService][getSubmissionHistory] Error getting submission history: $error")
        error
      }
      .map { submissionRecordList =>
        submissionRecordList
          .filter { submissionRecord =>
            submissionRecord.submissionStatus == Passed && submissionRecord.uploadDateTime
              .isBefore(Instant.now(clock).minus(appConfig.submissionTtlDays, ChronoUnit.DAYS))
          }
          .map { submissionRecord =>
            SubmissionHistoryPassed(
              submissionRecord.messageRefId,
              submissionRecord.rcaspName,
              submissionRecord.uploadDateTime
            )
          }
          .sortBy(_.submissionTime)(Ordering[Instant].reverse)
      }
  }

}
