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

import play.api.libs.json.*

enum DocTypeIndic {
  case OECD0, OECD1, OECD2, OECD3, OECD10, OECD11, OECD12, OECD13
}

object DocTypeIndic {
  implicit val reads: Reads[DocTypeIndic] = Reads[DocTypeIndic] {
    case JsString("OECD0")  => JsSuccess(OECD0)
    case JsString("OECD1")  => JsSuccess(OECD1)
    case JsString("OECD2")  => JsSuccess(OECD2)
    case JsString("OECD3")  => JsSuccess(OECD3)
    case JsString("OECD10") => JsSuccess(OECD10)
    case JsString("OECD11") => JsSuccess(OECD11)
    case JsString("OECD12") => JsSuccess(OECD12)
    case JsString("OECD13") => JsSuccess(OECD13)
    case value              => JsError(s"Unexpected value of DocTypeIndic: $value")
  }

  implicit val writes: Writes[DocTypeIndic] = Writes[DocTypeIndic] {
    case OECD0  => JsString("OECD0")
    case OECD1  => JsString("OECD1")
    case OECD2  => JsString("OECD2")
    case OECD3  => JsString("OECD3")
    case OECD10 => JsString("OECD10")
    case OECD11 => JsString("OECD11")
    case OECD12 => JsString("OECD12")
    case OECD13 => JsString("OECD13")
  }
}
