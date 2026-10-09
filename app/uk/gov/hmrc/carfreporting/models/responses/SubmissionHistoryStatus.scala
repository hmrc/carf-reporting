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

import play.api.libs.json.*

enum SubmissionHistoryStatus {
  case Passed, Failed, Pending
}

object SubmissionHistoryStatus {

  implicit val reads: Reads[SubmissionHistoryStatus] = Reads[SubmissionHistoryStatus] {
    case JsString("PASSED")  => JsSuccess(Passed)
    case JsString("FAILED")  => JsSuccess(Failed)
    case JsString("PENDING") => JsSuccess(Pending)
    case value               => JsError(s"Unexpected value of SubmissionHistoryStatus: $value")
  }

  implicit val writes: Writes[SubmissionHistoryStatus] = Writes[SubmissionHistoryStatus] {
    case Passed  => JsString("PASSED")
    case Failed  => JsString("FAILED")
    case Pending => JsString("PENDING")
  }
}
