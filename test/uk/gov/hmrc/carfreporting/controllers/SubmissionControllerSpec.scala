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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, reset, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.*
import uk.gov.hmrc.carfreporting.models.errors.InternalServerError
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.services.submission.SubmissionService
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionControllerSpec extends SpecBase {

  private val mockSubmissionService = mock[SubmissionService]

  val testController: SubmissionController =
    new SubmissionController(cc, mockSubmissionService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionService)
  }

  "SubmissionController" - {
    "submit" - {

      "must return NO_CONTENT (204) when the payload is valid and submission is successful" in {
        val requestBodyJsonString: String =
          s"""
             |{
             |  "fileName": "test-file.xml",
             |  "uploadId": "${testUploadId.value}",
             |  "fileSize": 1024,
             |  "documentUrl": "http://localhost:8080/file",
             |  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
             |  "rcaspDetails": {
             |    "RCASPID": "ZMCAR0123456788",
             |    "IsRCASPUser": false,
             |    "FirstName": "testFirstName",
             |    "LastName": "testLastName",
             |    "PrimaryContactDetails": {
             |      "ContactName": "testContactName",
             |      "EmailAddress": "test@example.com"
             |    }
             |  },
             |  "subscriptionDetails": {
             |    "carfReference": "XACARF000001234",
             |    "gbUser": true,
             |    "primaryContact": {
             |      "individual": {
             |        "firstName": "Jane",
             |        "lastName": "Smith"
             |      },
             |      "email": "jane.smith@example.com"
             |    }
             |  },
             |  "extractedFileDetails": {
             |    "messageRefId": "${extractedFileDetailsCarf.messageRefId}",
             |    "sendingEntityIn": "${extractedFileDetailsCarf.sendingEntityIn}",
             |    "rcaspName": "${extractedFileDetailsCarf.rcaspName.get}",
             |    "messageTypeIndic": "${extractedFileDetailsCarf.messageTypeIndic}",
             |    "hasOtherNexus": ${extractedFileDetailsCarf.hasOtherNexus.toString},
             |    "hasCryptoUsers": ${extractedFileDetailsCarf.hasCryptoUsers.toString},
             |    "docTypeIndic": "${extractedFileDetailsCarf.docTypeIndic.get}",
             |    "isTestData": ${extractedFileDetailsCarf.isTestData},
             |    "allCryptoUsersAreCorrections": ${extractedFileDetailsCarf.allCryptoUsersAreCorrections},
             |    "allCryptoUsersAreDeletions": ${extractedFileDetailsCarf.allCryptoUsersAreDeletions}
             |  }
             |}
             |""".stripMargin

        val requestBody = Json.parse(requestBodyJsonString)

        when(mockSubmissionService.saveAndSubmit(any())(any()))
          .thenReturn(ResultT.fromValue(()))

        val result = testController.submit(fakeRequestWithJsonBody(requestBody))

        status(result) mustEqual NO_CONTENT

        verify(mockSubmissionService).saveAndSubmit(eqTo(testSubmissionRequest))(any())
      }

      "must return BAD_REQUEST (400) when the Json request is malformed or missing fields" in {
        val requestBody = Json.parse(
          s"""
             |{
             |  "bad": "invalid"
             |}
             |""".stripMargin
        )

        val expectedResponse = "Request body provided is invalid"

        val result = testController.submit(fakeRequestWithJsonBody(requestBody))

        status(result)                                     mustEqual BAD_REQUEST
        contentAsString(result).contains(expectedResponse) mustEqual true

        verify(mockSubmissionService, never).saveAndSubmit(any())(any())
      }

      "must return INTERNAL_SERVER_ERROR (500) when the submission service fails" in {
        val requestBody = Json.toJson(testSubmissionRequest)

        when(mockSubmissionService.saveAndSubmit(any())(any()))
          .thenReturn(ResultT.fromError(InternalServerError("Database connection failed")))

        val result = testController.submit(fakeRequestWithJsonBody(requestBody))

        status(result)          mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) mustEqual "Unexpected error"

        verify(mockSubmissionService).saveAndSubmit(eqTo(testSubmissionRequest))(any())
      }
    }
  }
}
