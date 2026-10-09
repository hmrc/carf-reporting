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

import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryStatus.*

class SubmissionHistoryStatusSpec extends SpecBase {

  "SubmissionHistoryStatus" - {
    "json reads" - {
      "must parse to expected SubmissionHistoryStatus" in {
        Json.parse("\"PASSED\"").as[SubmissionHistoryStatus]  mustBe Passed
        Json.parse("\"FAILED\"").as[SubmissionHistoryStatus]  mustBe Failed
        Json.parse("\"PENDING\"").as[SubmissionHistoryStatus] mustBe Pending
      }

      "must return a JsError when parsing an unexpected value" in {
        Json.parse("\"Unknown\"").validate[SubmissionHistoryStatus] mustBe JsError(
          """Unexpected value of SubmissionHistoryStatus: "Unknown""""
        )
      }
    }

    "json writes" - {
      "must write to json as expected" in {
        Json.toJson(Passed).toString  mustBe "\"PASSED\""
        Json.toJson(Failed).toString  mustBe "\"FAILED\""
        Json.toJson(Pending).toString mustBe "\"PENDING\""
      }
    }
  }
}
