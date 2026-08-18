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

package uk.gov.hmrc.carfreporting.models

import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.MessageTypeIndic.*

class MessageTypeIndicSpec extends SpecBase {

  "MessageTypeIndic" - {
    "json reads" - {
      "must parse to expected MessageTypeIndic" in {
        Json.parse("\"CARF701\"").as[MessageTypeIndic] mustBe CARF701
        Json.parse("\"CARF702\"").as[MessageTypeIndic] mustBe CARF702
        Json.parse("\"CARF703\"").as[MessageTypeIndic] mustBe CARF703
      }

      "must return a JsError when parsing an unexpected value" in {
        Json.parse("\"Unknown\"").validate[MessageTypeIndic] mustBe JsError(
          """Unexpected value of MessageTypeIndic: "Unknown""""
        )
      }
    }

    "json writes" - {
      "must write to json as expected" in {
        Json.toJson(CARF701).toString mustBe "\"CARF701\""
        Json.toJson(CARF702).toString mustBe "\"CARF702\""
        Json.toJson(CARF703).toString mustBe "\"CARF703\""
      }
    }
  }
}
