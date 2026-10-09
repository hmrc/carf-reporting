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

package uk.gov.hmrc.carfreporting.models.requests

import play.api.libs.json.{Json, OFormat}

case class SubmissionHistoryRequest(submissionsListRequest: SubmissionsListRequest)

object SubmissionHistoryRequest {
  implicit val format: OFormat[SubmissionHistoryRequest] = Json.format[SubmissionHistoryRequest]
}

case class SubmissionsListRequest(
    requestCommon: SubmissionHistoryRequestCommon,
    requestDetails: SubmissionHistoryRequestDetails
)

object SubmissionsListRequest {
  implicit val format: OFormat[SubmissionsListRequest] = Json.format[SubmissionsListRequest]
}

case class SubmissionHistoryRequestCommon(
    originatingSystem: String,
    transmittingSystem: String,
    regime: String,
    requestParameters: Option[List[SubmissionHistoryRequestParameter]]
)

object SubmissionHistoryRequestCommon {
  implicit val format: OFormat[SubmissionHistoryRequestCommon] = Json.format[SubmissionHistoryRequestCommon]
}

case class SubmissionHistoryRequestParameter(paramName: String, paramValue: String)

object SubmissionHistoryRequestParameter {
  implicit val format: OFormat[SubmissionHistoryRequestParameter] = Json.format[SubmissionHistoryRequestParameter]
}

case class SubmissionHistoryRequestDetails(subscriptionId: String, rcaspId: Option[String])

object SubmissionHistoryRequestDetails {
  implicit val format: OFormat[SubmissionHistoryRequestDetails] = Json.format[SubmissionHistoryRequestDetails]
}
