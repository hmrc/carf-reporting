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
import uk.gov.hmrc.carfreporting.models.DocTypeIndic.*

class DocTypeIndicSpec extends SpecBase {

  "DocTypeIndic" - {
    "json reads" - {
      "must parse to expected DocTypeIndic" in {
        Json.parse("\"OECD0\"").as[DocTypeIndic]  mustBe OECD0
        Json.parse("\"OECD1\"").as[DocTypeIndic]  mustBe OECD1
        Json.parse("\"OECD2\"").as[DocTypeIndic]  mustBe OECD2
        Json.parse("\"OECD3\"").as[DocTypeIndic]  mustBe OECD3
        Json.parse("\"OECD10\"").as[DocTypeIndic] mustBe OECD10
        Json.parse("\"OECD11\"").as[DocTypeIndic] mustBe OECD11
        Json.parse("\"OECD12\"").as[DocTypeIndic] mustBe OECD12
        Json.parse("\"OECD13\"").as[DocTypeIndic] mustBe OECD13
      }

      "must return a JsError when parsing an unexpected value" in {
        Json.parse("\"Unknown\"").validate[DocTypeIndic] mustBe JsError(
          """Unexpected value of DocTypeIndic: "Unknown""""
        )
      }
    }

    "json writes" - {
      "must write to json as expected" in {
        Json.toJson(OECD0).toString  mustBe "\"OECD0\""
        Json.toJson(OECD1).toString  mustBe "\"OECD1\""
        Json.toJson(OECD2).toString  mustBe "\"OECD2\""
        Json.toJson(OECD3).toString  mustBe "\"OECD3\""
        Json.toJson(OECD10).toString mustBe "\"OECD10\""
        Json.toJson(OECD11).toString mustBe "\"OECD11\""
        Json.toJson(OECD12).toString mustBe "\"OECD12\""
        Json.toJson(OECD13).toString mustBe "\"OECD13\""
      }
    }
  }
}
