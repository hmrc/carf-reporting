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

package uk.gov.hmrc.carfreporting.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import play.api.http.Status.*
import uk.gov.hmrc.carfreporting.itutil.ApplicationWithWiremock
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorISpec extends ApplicationWithWiremock {

  lazy val connector: EmailConnector = app.injector.instanceOf[EmailConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val emailPath    = "/hmrc/email"
  private val testAddress  = "ashley.smith@example.com"
  private val testTemplate = "carf_file_passed_user"
  private val testParams   = Map("contactName" -> "Ashley Smith", "fileId" -> "CARFFILEID")

  private val expectedRequestJson: String =
    s"""
       |{
       |  "to": ["$testAddress"],
       |  "templateId": "$testTemplate",
       |  "parameters": {
       |    "contactName": "Ashley Smith",
       |    "fileId": "CARFFILEID"
       |  },
       |  "force": false
       |}
       |""".stripMargin

  "sendEmail" - {

    "must return EmailSent when the email service responds with 202 (ACCEPTED)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(ACCEPTED))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailSent
    }

    "must send the address, template id, parameters and force flag in the request body" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .withRequestBody(equalToJson(expectedRequestJson))
          .willReturn(aResponse().withStatus(ACCEPTED))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailSent

      verify(1, postRequestedFor(urlPathEqualTo(emailPath)).withRequestBody(equalToJson(expectedRequestJson)))
    }

    "must return EmailNotSent when the email service responds with 400 (BAD_REQUEST)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(BAD_REQUEST).withBody("Bad request"))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }

    "must return EmailNotSent when the email service responds with 404 (template not found)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }

    "must return EmailNotSent when the email service responds with 415 (UNSUPPORTED_MEDIA_TYPE)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(UNSUPPORTED_MEDIA_TYPE))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }

    "must return EmailNotSent when the email service responds with 500 (INTERNAL_SERVER_ERROR)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody("Internal server error"))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }

    "must return EmailNotSent for any other success status (e.g. 200 OK)" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withStatus(OK))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }

    "must return EmailNotSent, not a failed future, when the connection fails" in {
      stubFor(
        post(urlPathEqualTo(emailPath))
          .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
      )

      connector.sendEmail(testAddress, testTemplate, testParams).futureValue mustBe EmailNotSent
    }
  }
}