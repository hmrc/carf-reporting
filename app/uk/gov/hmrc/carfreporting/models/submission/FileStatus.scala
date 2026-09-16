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

package uk.gov.hmrc.carfreporting.models.submission

import play.api.libs.json.*

enum FileStatus {
  case Pending // default, initially set to this
  case Accepted // from BR response XML file
  case Rejected // from BR response XML file
  case VirusFound // from initial FTS callback
  case UnprocessableErrorFile // from BR response XML file
  case UnexpectedError // from initial FTS callback
}

object FileStatus {

  given Format[FileStatus] = Format(
    Reads {
      case JsString("Pending")                => JsSuccess(Pending)
      case JsString("Passed")                 => JsSuccess(Accepted)
      case JsString("Failed")                 => JsSuccess(Rejected)
      case JsString("VirusFound")             => JsSuccess(VirusFound)
      case JsString("UnprocessableErrorFile") => JsSuccess(UnprocessableErrorFile)
      case JsString("UnexpectedError")        => JsSuccess(UnexpectedError)
      case other                              => JsError(s"Invalid FileStatus JSON: $other")
    },
    Writes {
      case Pending                => JsString("Pending")
      case Accepted               => JsString("Passed")
      case Rejected               => JsString("Failed")
      case VirusFound             => JsString("VirusFound")
      case UnprocessableErrorFile => JsString("UnprocessableErrorFile")
      case UnexpectedError        => JsString("UnexpectedError")
    }
  )
}
