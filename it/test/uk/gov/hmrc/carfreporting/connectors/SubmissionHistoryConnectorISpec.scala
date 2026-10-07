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
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryRecord
import uk.gov.hmrc.http.HeaderCarrier

class SubmissionHistoryConnectorISpec extends ApplicationWithWiremock {

  lazy val connector: SubmissionHistoryConnector = app.injector.instanceOf[SubmissionHistoryConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val testApiErrorDetailResponseJson: String =
    """{
      |  "errorDetail": {
      |    "errorCode": "400",
      |    "errorMessage": "Test Error Message",
      |    "source": "Test",
      |    "sourceFaultDetail": {
      |      "detail": [
      |        "Test Error Detail"
      |      ]
      |    },
      |    "timestamp": "2020-09-25T21:54:12.015Z",
      |    "correlationId": "1ae81b45-41b4-4642-ae1c-db1126900001"
      |  }
      |}""".stripMargin

  "getSubmissionHistory" - {

    val testRequestJson: String =
      s"""
         |{
         |  "submissionsListRequest": {
         |    "requestCommon": {
         |      "originatingSystem": "MDTP",
         |      "transmittingSystem": "CADX",
         |      "regime": "CARF"
         |    },
         |    "requestDetails": {
         |      "subscriptionId": "$testCarfRef"
         |    }
         |  }
         |}
         |""".stripMargin

    val testResponseJson: String =
      """
        |{
        |  "submissionsListResponse" : {
        |    "responseCommon" : {
        |      "regime" : "CARF"
        |    },
        |    "responseDetails" : {
        |      "submissionsList" : [ {
        |        "rcaspId" : "ZMCAR0123456780",
        |        "rcaspName" : "Other RCASP Ltd",
        |        "filename" : "filename29.xml",
        |        "submissionStatus" : "PASSED",
        |        "uploadDateTime" : "2024-05-13T15:07:47.838Z",
        |        "messageRefId" : "MSG-2024-0029",
        |        "submissionFileType" : "CARF-701",
        |        "reportingYear" : "2024",
        |        "submissionCaseId" : "CARF-SUB-029"
        |      }, {
        |        "rcaspId" : "ZMCAR0123456780",
        |        "rcaspName" : "Other RCASP Ltd",
        |        "filename" : "filename30.xml",
        |        "submissionStatus" : "PASSED",
        |        "uploadDateTime" : "2024-05-12T15:07:47.838Z",
        |        "messageRefId" : "MSG-2024-0030",
        |        "submissionFileType" : "CARF-701",
        |        "reportingYear" : "2024",
        |        "submissionCaseId" : "CARF-SUB-030"
        |      } ]
        |    }
        |  }
        |}
        |""".stripMargin

    val testUrl = "/dac6/carf/SubmissionsList/v1"

    "must return a list of SubmissionHistoryRecord given a 200 response" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .withRequestBody(equalToJson(testRequestJson))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(testResponseJson)
          )
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Right(submissionHistoryRecordList(2))
    }

    "must return Left JsonValidationError if json is incorrectly formatted given a 200 response" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .withRequestBody(equalToJson(testRequestJson))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("")
          )
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(JsonValidationError)
    }

    "must return an empty list given a 422 response with detail 001" in {
      val errorDetailResponseJson: String =
        """{
          |  "errorDetail": {
          |    "errorCode": "422",
          |    "errorMessage": "No matching records found for the request",
          |    "source": "Backend",
          |    "sourceFaultDetail": {
          |      "detail": [
          |        "001 - No matching records found for the request"
          |      ]
          |    },
          |    "timestamp": "2020-09-25T21:54:12.015Z",
          |    "correlationId": "1ae81b45-41b4-4642-ae1c-db1126900001"
          |  }
          |}""".stripMargin

      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(UNPROCESSABLE_ENTITY).withBody(errorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Right(Seq.empty[SubmissionHistoryRecord])
    }

    "must return Left InternalServerError given some other 422 response" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(UNPROCESSABLE_ENTITY).withBody(testApiErrorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left JsonValidationError given a 422 response when error response cannot be parsed" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(UNPROCESSABLE_ENTITY)
              .withBody("""{
                  |  "errorDetail": {
                  |    "errorMessage": "Invalid json",
                  |  }
                  |}""".stripMargin)
          )
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(JsonValidationError)
    }

    "must return Left InternalServerError if 400 status response is returned from backend" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(BAD_REQUEST).withBody(testApiErrorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left JsonValidationError given an error response (e.g. with status 400) that cannot be parsed" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody("""{
                  |  "errorDetail": {
                  |    "errorMessage": "Invalid json",
                  |  }
                  |}""".stripMargin)
          )
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(JsonValidationError)
    }

    "must return Left InternalServerError if 503 status response is returned from backend" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(testApiErrorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left InternalServerError if 403 status response is returned from backend" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(FORBIDDEN).withBody(testApiErrorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left InternalServerError if 500 status response is returned from backend" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(testApiErrorDetailResponseJson))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return Left InternalServerError if unexpected status code is returned from backend" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(aResponse().withStatus(GATEWAY_TIMEOUT))
      )

      val result = connector.getSubmissionHistory(submissionHistoryRequest).value.futureValue
      result mustBe Left(InternalServerError)
    }
  }
}
