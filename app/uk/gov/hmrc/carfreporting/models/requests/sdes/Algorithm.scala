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

package uk.gov.hmrc.carfreporting.models.requests.sdes

import play.api.libs.json.*

enum Algorithm:
  case MD5, SHA256, SHA512

object Algorithm {

  def apply(algorithm: String): Algorithm = algorithm.toUpperCase match {
    case "MD5"     => MD5
    case "SHA-256" => SHA256
    case "SHA-512" => SHA512
    case _         => throw new IllegalArgumentException(s"Unsupported algorithm $algorithm")
  }

  given Writes[Algorithm] = Writes[Algorithm] {
    case MD5    => JsString("MD5")
    case SHA256 => JsString("SHA-256")
    case SHA512 => JsString("SHA-512")
  }

  given Reads[Algorithm] = Reads[Algorithm] {
    case JsString(s) =>
      s.toUpperCase match {
        case "MD5"     => JsSuccess(MD5)
        case "SHA-256" => JsSuccess(SHA256)
        case "SHA-512" => JsSuccess(SHA512)
        case _         => JsError(s"Unexpected Algorithm: $s")
      }
    case other       =>
      JsError(
        "Expected JSON string for Algorithm from the following options\n" +
          s"${Algorithm.values.mkString(", ")}, but got ${other.getClass.getSimpleName}"
      )
  }
}
