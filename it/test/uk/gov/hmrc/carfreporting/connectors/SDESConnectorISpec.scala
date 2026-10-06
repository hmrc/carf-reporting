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
import play.api.http.Status.*
import uk.gov.hmrc.carfreporting.itutil.ApplicationWithWiremock
import uk.gov.hmrc.carfreporting.models.errors.ApiError.*
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class SDESConnectorISpec extends ApplicationWithWiremock {

  lazy val connector: SDESConnector = app.injector.instanceOf[SDESConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val testNotificationJson: String =
    """
      |{
      |  "informationType": "carf-reporting",
      |  "file": {
      |    "name": "test-file.xml",
      |    "location": "http://localhost:8080/download",
      |    "checksum": {
      |      "algorithm": "SHA-256",
      |      "value": "checksum12345"
      |    },
      |    "size": 1024,
      |    "recipientOrSender": "Sender",
      |    "properties": [
      |      {
      |        "name": "name",
      |        "value": "value"
      |      }
      |    ]
      |  },
      |  "audit": {
      |    "correlationID": "correlation-id-123456789"
      |  }
      |}
      |""".stripMargin

  "sendFileReadyNotification" - {

    val baseUrlPattern = "/sdes-stub/notification/fileready"

    "must return Right(()) when backend returns NO_CONTENT (204)" in {
      stubFor(
        post(urlPathMatching(baseUrlPattern))
          .withRequestBody(equalToJson(testNotificationJson))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val result = connector.sendFileReadyNotification(testNotification).value.futureValue
      result mustBe Right(())
    }

    "must return Left(BadRequestError) when backend returns BAD_REQUEST (400)" in {
      stubFor(
        post(urlPathMatching(baseUrlPattern))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody("Invalid notification structure")
          )
      )

      val result = connector.sendFileReadyNotification(testNotification).value.futureValue
      result mustBe Left(BadRequestError)
    }

    "must return Left(InternalServerError) when backend returns INTERNAL_SERVER_ERROR (500)" in {
      stubFor(
        post(urlPathMatching(baseUrlPattern))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody("Internal server error")
          )
      )

      val result = connector.sendFileReadyNotification(testNotification).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left(InternalServerError) for any unexpected response status (e.g. 200 OK)" in {
      stubFor(
        post(urlPathMatching(baseUrlPattern))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = connector.sendFileReadyNotification(testNotification).value.futureValue
      result mustBe Left(InternalServerError)
    }
  }

  "getBusinessRulesFileListing" - {
    val testUrl = "/files-available/list/carf-submission"

    val testFileListingJson: String =
      s"""[
        |    {
        |        "filename": "$testBusinessRulesFileName",
        |        "fileSize": 100,
        |        "downloadURL": "$testDownloadUrl",
        |        "metadata": [
        |            {
        |                "metadata": "FileType",
        |                "value": "XML"
        |            }
        |        ]
        |    },
        |    {
        |        "filename": "br-file-2.xml",
        |        "fileSize": 345,
        |        "downloadURL": "$testDownloadUrl",
        |        "metadata": []
        |    }
        |]
        |""".stripMargin

    "must return the list of files given a 200 response" in {
      stubFor(
        get(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(testFileListingJson)
          )
      )

      val result = connector.getBusinessRulesFileListing().value.futureValue
      result mustBe Right(testSdesFileListing)
    }

    "must return JsonValidationError when the response body cannot be parsed" in {
      stubFor(
        get(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""{"incorrect": "structure"}""")
          )
      )

      val result = connector.getBusinessRulesFileListing().value.futureValue
      result mustBe Left(JsonValidationError)
    }

    "must return InternalServerError given a 400 response" in {
      stubFor(
        get(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody("Bad request")
          )
      )

      val result = connector.getBusinessRulesFileListing().value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return InternalServerError given a 500 response" in {
      stubFor(
        get(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody("Internal server error")
          )
      )

      val result = connector.getBusinessRulesFileListing().value.futureValue
      result mustBe Left(InternalServerError)
    }
  }
}
