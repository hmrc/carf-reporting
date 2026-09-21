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

import play.api.Logging
import uk.gov.hmrc.carfreporting.models.requests.SubmissionRequest
import uk.gov.hmrc.carfreporting.models.requests.sdes.Property
import uk.gov.hmrc.carfreporting.models.submission.{DisplaySubscriptionContact, DisplaySubscriptionOrganisation}

import java.time.Instant
import java.time.format.DateTimeFormatter

object SDESFileMetadataHelper extends Logging {

  private final val formatter = DateTimeFormatter.ISO_INSTANT

  def generatePropertiesMetadata(submissionRequest: SubmissionRequest, submissionTime: Instant): List[Property] =
    val conversationId = submissionRequest.uploadId.value
    List(
      Property("requestCommon/conversationID", conversationId),
      Property("requestCommon/receiptDate", formatter.format(submissionTime)),
      Property("requestCommon/regime", "CARF"),
      Property("requestCommon/schemaVersion", "1.0.0"),
      Property("requestAdditionalDetail/subscriptionID", submissionRequest.subscriptionDetails.carfReference.value),
      Property("requestAdditionalDetail/isGBUser", submissionRequest.subscriptionDetails.gbUser.toString)
    ) ++ individualOrOrganisationDetails(submissionRequest.subscriptionDetails.primaryContact, "primaryContact") ++
      submissionRequest.subscriptionDetails.secondaryContact.fold(List.empty)(secondaryContact =>
        individualOrOrganisationDetails(secondaryContact, "secondaryContact")
      )

  private def individualOrOrganisationDetails(subscriptionContact: DisplaySubscriptionContact, contactType: String) = {
    lazy val orgProperties: DisplaySubscriptionOrganisation => List[Property] = displaySubscriptionOrg =>
      List(
        Property(
          s"requestAdditionalDetail/$contactType/organisationDetails/organisationName",
          displaySubscriptionOrg.name
        )
      )

    (subscriptionContact.individual, subscriptionContact.organisation) match {
      case (Some(ind), None)    =>
        List(
          Property(
            s"requestAdditionalDetail/$contactType/individualDetails/firstName",
            subscriptionContact.individual.get.firstName
          ),
          Property(
            s"requestAdditionalDetail/$contactType/individualDetails/lastName",
            subscriptionContact.individual.get.lastName
          )
        )
      case (None, Some(org))    => orgProperties(org)
      case (Some(_), Some(org)) =>
        logger.warn(
          "[SDESFileMetadataHelper][individualOrOrganisationDetails](Individual and organisation) " +
            "contact details provided defaulting to organisation"
        )
        orgProperties(org)
      case (_, _)               =>
        logger.error(
          "[SDESFileMetadataHelper][individualOrOrganisationDetails]No" +
            "contact details provided submission will fail in transit"
        )
        List.empty
    }
  }.appended(
    Property(
      s"requestAdditionalDetail/$contactType/emailAddress",
      subscriptionContact.email
    )
  )
}
