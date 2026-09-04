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

package uk.gov.hmrc.carfreporting.models.errors

import play.api.libs.json.*

sealed trait CarfError {
  val message: String
}

case class MongoError(value: String = "") extends CarfError {
  override val message: String = value
}

case class BusinessError(override val message: String = "") extends CarfError

sealed trait ApiError extends CarfError

object ApiError {

  case object BadRequestError extends ApiError {
    override val message: String = "Bad Request"
  }

  case object NotFoundError extends ApiError {
    override val message: String = "Not Found"
  }

  case class ApiInternalServerError(override val message: String) extends ApiError

  case object JsonValidationError extends ApiError {
    override val message: String = "Json Validation Error"
  }
}

case class InternalServerError(override val message: String) extends CarfError

sealed trait XmlValidationError extends CarfError

object XmlValidationError {

  private val xmlErrorsFormat: OFormat[XmlErrors] = Json.format[XmlErrors]

  implicit val writes: Writes[XmlValidationError] = {
    case InvalidXmlError   => JsObject(Map("_type" -> JsString("InvalidXmlError")))
    case errors: XmlErrors =>
      Json.toJson(errors)(xmlErrorsFormat).as[JsObject] + ("_type" -> JsString("XmlErrors"))
  }

}

case object InvalidXmlError extends XmlValidationError {
  override val message: String = "Invalid XML"
}

case class XmlErrors(errors: Vector[XmlError]) extends XmlValidationError {
  override val message: String = s"Xml error(s) have occurred: \n ${errors.mkString(",\n")}"
}
