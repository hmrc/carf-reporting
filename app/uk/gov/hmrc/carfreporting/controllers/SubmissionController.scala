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

package uk.gov.hmrc.carfreporting.controllers

import play.api.Logging
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.services.submission.SubmissionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionController @Inject() (cc: ControllerComponents, service: SubmissionService)(implicit
    e: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def submit: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[SubmissionRequest]
      .fold(
        invalid =>
          logger.error(
            s"[SubmissionController][submit] Failed to parse request body with message: ${invalid.mkString(",\n")}"
          )
          Future.successful(BadRequest(s"Request body provided is invalid with message: ${invalid.mkString(",\n")}"))
        ,
        valid =>
          service.saveAndSubmit(valid).value.map {
            case Right(_)    =>
              NoContent
            case Left(error) =>
              logger.error(
                s"[SubmissionController][submit] Unexpected error with message: ${error.message}"
              )
              InternalServerError("Unexpected error")
          }
      )
  }
}
