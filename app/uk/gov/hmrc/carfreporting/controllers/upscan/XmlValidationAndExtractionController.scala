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

package uk.gov.hmrc.carfreporting.controllers.upscan

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.carfreporting.services.XmlParserService
import uk.gov.hmrc.carfreporting.models.requests.XmlValidationRequest
import uk.gov.hmrc.carfreporting.models.responses.XmlValidationAndExtractionResponse
import uk.gov.hmrc.carfreporting.models.errors.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject

class XmlValidationAndExtractionController @Inject() (cc: ControllerComponents, service: XmlParserService)(implicit
    e: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def processXml: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[XmlValidationRequest]
      .fold(
        invalid =>
          logger.error("[XmlValidationAndExtractionController][validateXml] Failed to parse request body")
          Future.successful(BadRequest(s"Request body provided is invalid"))
        ,
        valid =>
          service.validateAndExtract(valid.path).value.map {
            case Right(value)               =>
              Ok(
                Json.toJson(
                  XmlValidationAndExtractionResponse(
                    OK,
                    valid.path,
                    None,
                    Vector.empty
                  )
                )
              )
            case Left(xmlErrors: XmlErrors) =>
              logger.warn(s"Failed to validate XML with (${xmlErrors.errors.size}) error(s)")
              BadRequest(
                Json.toJson(
                  XmlValidationAndExtractionResponse(
                    BAD_REQUEST,
                    valid.path,
                    Some("The submitted XML failed schema validation."),
                    xmlErrors.errors
                  )
                )
              )
            case Left(error)                =>
              logger.error(s"Failed to validate XML with unexpected error with message: ${error.message}")
              InternalServerError(
                Json.toJson(
                  XmlValidationAndExtractionResponse(
                    INTERNAL_SERVER_ERROR,
                    valid.path,
                    Some("Unexpected error"),
                    Vector.empty
                  )
                )
              )
          }
      )
  }
}
