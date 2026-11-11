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

package uk.gov.hmrc.carfreporting.controllers.actions

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.requests.AuthenticatedRequest
import uk.gov.hmrc.http.SessionId

import scala.concurrent.Future

class CheckCarfIdActionSpec extends SpecBase {

  class Harness(carfId: String) extends CheckCarfIdActionImpl(carfId) {
    def callFilter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = filter(request)
  }

  private def buildRequest(carfId: Option[String]): AuthenticatedRequest[_] =
    AuthenticatedRequest(FakeRequest(), "internalId", SessionId("sessionId"), Organisation, carfId)

  "CheckCarfIdAction" - {

    val expectedCarfId = "test-carf-id"

    "must return None to let the request continue" - {
      "when the CARF ID in the request matches the expected CARF ID" in {
        val action  = new Harness(expectedCarfId)
        val request = buildRequest(Some(expectedCarfId))

        val result = action.callFilter(request).futureValue

        result mustBe empty
      }
    }

    "must block the request and return Unauthorized" - {
      "when the CARF ID is missing from the request" in {
        val action  = new Harness(expectedCarfId)
        val request = buildRequest(None)

        val result = action.callFilter(request).futureValue

        result mustBe defined

        val unauthorizedResult = Future.successful(result.get)

        status(unauthorizedResult)          mustEqual UNAUTHORIZED
        contentAsString(unauthorizedResult) mustEqual "CARF ID missing or does not match or missing"
      }

      "when the CARF ID in the request does not match the expected CARF ID" in {
        val action  = new Harness(expectedCarfId)
        val request = buildRequest(Some("different-carf-id"))

        val result = action.callFilter(request).futureValue

        result mustBe defined

        val unauthorizedResult = Future.successful(result.get)

        status(unauthorizedResult)          mustEqual UNAUTHORIZED
        contentAsString(unauthorizedResult) mustEqual "CARF ID missing or does not match or missing"
      }
    }
  }
}
