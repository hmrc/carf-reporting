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

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.carfreporting.models.upscan.{UploadId, UpscanIdentifiers}
import uk.gov.hmrc.carfreporting.services.upscan.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadFormController @Inject() (
    uploadProgressTracker: UploadProgressTracker,
    cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  // TODO: Try to use AuthAction when linked to frontend (CARF-578)
  def requestUpload: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[UpscanIdentifiers]
      .fold(
        invalid = _ => Future.successful(BadRequest("Could not parse request body as UpscanIdentifiers")),
        valid = identifiers =>
          uploadProgressTracker
            .requestUpload(identifiers.uploadId, identifiers.fileReference)
            .map(_ => Ok)
      )
  }

  // TODO: Try to use AuthAction when linked to frontend (CARF-578)
  def getStatus(uploadId: String): Action[AnyContent] = Action.async {
    uploadProgressTracker.getUploadResult(UploadId(uploadId)).map {
      case Some(uploadStatus) => Ok(Json.toJson(uploadStatus))
      case None               => NotFound
    }
  }
}
