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

package uk.gov.hmrc.carfreporting.models.responses

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryStatus.Passed

import java.time.Instant
import java.time.temporal.ChronoUnit

case class SubmissionHistoryRecord(
    rcaspId: String,
    rcaspName: String,
    fileName: String,
    submissionStatus: SubmissionHistoryStatus,
    uploadDateTime: Instant,
    messageRefId: String,
    submissionFileType: String,
    reportingYear: String,
    submissionCaseId: String
) {
  def shouldBeDisplayed(now: Instant, submissionTtlDays: Long): Boolean =
    submissionStatus == Passed && uploadDateTime.isBefore(now.minus(submissionTtlDays, ChronoUnit.DAYS))
}

object SubmissionHistoryRecord {
  implicit val format: OFormat[SubmissionHistoryRecord] = Json.format[SubmissionHistoryRecord]
}
