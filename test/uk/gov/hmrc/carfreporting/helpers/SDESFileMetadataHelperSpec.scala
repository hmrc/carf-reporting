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

package uk.gov.hmrc.carfreporting.helpers

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.carfreporting.base.TestData
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.models.requests.sdes.Property
import uk.gov.hmrc.carfreporting.models.submission.*

import java.time.Instant

class SDESFileMetadataHelperSpec extends AnyFreeSpec with Matchers with TestData {

  private inline val expectedReceiptDate = "2026-09-17T12:00:00Z"
  val testSubmissionTime: Instant        = Instant.parse(expectedReceiptDate)

  val expectedBaseProperties: List[Property] = List(
    Property("requestCommon/conversationID", testUploadId.value),
    Property("requestCommon/receiptDate", expectedReceiptDate),
    Property("requestCommon/regime", "CARF"),
    Property("requestCommon/schemaVersion", "1.0.0"),
    Property("requestAdditionalDetail/subscriptionID", testCarfRef),
    Property("requestAdditionalDetail/isGBUser", "true")
  )

  def buildSubmissionRequest(
      primaryContact: DisplaySubscriptionContact,
      secondaryContact: Option[DisplaySubscriptionContact] = None
  ): SubmissionRequest = {
    val modifiedSubscriptionDetails = testSubmissionRequest.subscriptionDetails.copy(
      primaryContact = primaryContact,
      secondaryContact = secondaryContact
    )
    testSubmissionRequest.copy(
      subscriptionDetails = modifiedSubscriptionDetails
    )
  }

  "SDESFileMetadataHelper" - {

    "generatePropertiesMetadata" - {
      "must generate correct properties for an Individual Primary Contact and no Secondary Contact" in {
        val primaryContact = DisplaySubscriptionContact(
          individual = Some(DisplaySubscriptionIndividual("Jane", "Doe")),
          organisation = None,
          email = "jane.doe@example.com"
        )

        val request = buildSubmissionRequest(primaryContact)
        val result  = SDESFileMetadataHelper.generatePropertiesMetadata(request, testSubmissionTime)

        val expectedContactProperties = List(
          Property("requestAdditionalDetail/primaryContact/individualDetails/firstName", "Jane"),
          Property("requestAdditionalDetail/primaryContact/individualDetails/lastName", "Doe"),
          Property("requestAdditionalDetail/primaryContact/emailAddress", "jane.doe@example.com")
        )

        result mustBe (expectedBaseProperties ++ expectedContactProperties)
      }

      "must generate correct properties for an Organisation Primary Contact and an Individual Secondary Contact" in {
        val primaryContact = DisplaySubscriptionContact(
          individual = None,
          organisation = Some(DisplaySubscriptionOrganisation("Acme Corp")),
          email = "admin@acmecorp.com"
        )

        val secondaryContact = DisplaySubscriptionContact(
          individual = Some(DisplaySubscriptionIndividual("John", "Smith")),
          organisation = None,
          email = "john.smith@acmecorp.com"
        )

        val request = buildSubmissionRequest(primaryContact, Some(secondaryContact))
        val result  = SDESFileMetadataHelper.generatePropertiesMetadata(request, testSubmissionTime)

        val expectedPrimaryContactProperties = List(
          Property("requestAdditionalDetail/primaryContact/organisationDetails/organisationName", "Acme Corp"),
          Property("requestAdditionalDetail/primaryContact/emailAddress", "admin@acmecorp.com")
        )

        val expectedSecondaryContactProperties = List(
          Property("requestAdditionalDetail/secondaryContact/individualDetails/firstName", "John"),
          Property("requestAdditionalDetail/secondaryContact/individualDetails/lastName", "Smith"),
          Property("requestAdditionalDetail/secondaryContact/emailAddress", "john.smith@acmecorp.com")
        )

        result mustBe (expectedBaseProperties ++ expectedPrimaryContactProperties ++ expectedSecondaryContactProperties)
      }

      "must default to Organisation and log a warning when both individual and organisation are provided for " +
        "Primary contact" in {
          val mixedContact = DisplaySubscriptionContact(
            individual = Some(DisplaySubscriptionIndividual("Jane", "Doe")),
            organisation = Some(DisplaySubscriptionOrganisation("Acme Corp")),
            email = "mixed@example.com"
          )

          val request = buildSubmissionRequest(mixedContact)
          val result  = SDESFileMetadataHelper.generatePropertiesMetadata(request, testSubmissionTime)

          val expectedContactProperties = List(
            Property("requestAdditionalDetail/primaryContact/organisationDetails/organisationName", "Acme Corp"),
            Property("requestAdditionalDetail/primaryContact/emailAddress", "mixed@example.com")
          )

          result mustBe (expectedBaseProperties ++ expectedContactProperties)
        }

      "must append only the emailAddress and log an error when neither individual nor organisation is provided" in {
        val emptyContact = DisplaySubscriptionContact(
          individual = None,
          organisation = None,
          email = "nobody@example.com"
        )

        val request = buildSubmissionRequest(emptyContact)
        val result  = SDESFileMetadataHelper.generatePropertiesMetadata(request, testSubmissionTime)

        val expectedContactProperties = List(
          Property("requestAdditionalDetail/primaryContact/emailAddress", "nobody@example.com")
        )

        result mustBe (expectedBaseProperties ++ expectedContactProperties)
      }
    }
  }
}
