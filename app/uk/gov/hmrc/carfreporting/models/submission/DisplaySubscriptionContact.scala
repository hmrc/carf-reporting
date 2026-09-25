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

package uk.gov.hmrc.carfreporting.models.submission

import play.api.libs.json.{Json, OFormat}

case class DisplaySubscriptionDetails(
    carfReference: CarfId,
    gbUser: Boolean,
    primaryContact: DisplaySubscriptionContact,
    secondaryContact: Option[DisplaySubscriptionContact]
)

extension (displaySubscriptionDetails: DisplaySubscriptionDetails) {
  def getEmails: List[String] = List(
    Some(displaySubscriptionDetails.primaryContact.email),
    displaySubscriptionDetails.secondaryContact.map(_.email)
  ).flatten
}

case class DisplaySubscriptionContact(
    individual: Option[DisplaySubscriptionIndividual],
    organisation: Option[DisplaySubscriptionOrganisation],
    email: String
)

case class DisplaySubscriptionIndividual(firstName: String, lastName: String) {
  val fullName: String = s"$firstName $lastName"
}

case class DisplaySubscriptionOrganisation(name: String)

object DisplaySubscriptionDetails {
  implicit val format: OFormat[DisplaySubscriptionDetails] = Json.format[DisplaySubscriptionDetails]
}

object DisplaySubscriptionContact {
  implicit val format: OFormat[DisplaySubscriptionContact] = Json.format[DisplaySubscriptionContact]
}

object DisplaySubscriptionIndividual {
  implicit val format: OFormat[DisplaySubscriptionIndividual] = Json.format[DisplaySubscriptionIndividual]
}

object DisplaySubscriptionOrganisation {
  implicit val format: OFormat[DisplaySubscriptionOrganisation] = Json.format[DisplaySubscriptionOrganisation]
}
