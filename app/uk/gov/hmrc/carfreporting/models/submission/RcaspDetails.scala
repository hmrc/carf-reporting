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

import play.api.libs.json.{Json, OFormat, Reads, Writes}

sealed trait RcaspDetails {
  val RCASPID: String
  val IsRCASPUser: Boolean
}

extension (rcaspDetails: RcaspDetails) {
  def getName: String =
    rcaspDetails match {
      case individual: IndividualRcaspDetails     => s"${individual.FirstName} ${individual.LastName}"
      case organisation: OrganisationRcaspDetails => organisation.RCASPName
    }

  def getEmails: List[String] =
    rcaspDetails match {
      case individual: IndividualRcaspDetails            => List(individual.PrimaryContactDetails.EmailAddress)
      case rcaspUser: OrganisationRcaspDetailsRcaspUser  => List.empty
      case standardOrg: OrganisationRcaspDetailsStandard =>
        List(
          Some(standardOrg.PrimaryContactDetails.EmailAddress),
          standardOrg.SecondaryContactDetails.map(_.EmailAddress)
        ).flatten
    }
}

case class IndividualRcaspDetails(
    RCASPID: String,
    IsRCASPUser: Boolean,
    FirstName: String,
    LastName: String,
    PrimaryContactDetails: RcaspContactDetails
) extends RcaspDetails

sealed trait OrganisationRcaspDetails extends RcaspDetails {
  val RCASPName: String
}

case class OrganisationRcaspDetailsRcaspUser(
    RCASPID: String,
    IsRCASPUser: Boolean,
    RCASPName: String
) extends OrganisationRcaspDetails

case class OrganisationRcaspDetailsStandard(
    RCASPID: String,
    IsRCASPUser: Boolean,
    RCASPName: String,
    PrimaryContactDetails: RcaspContactDetails,
    SecondaryContactDetails: Option[RcaspContactDetails]
) extends OrganisationRcaspDetails

object RcaspDetails {

  implicit val reads: Reads[RcaspDetails] = Reads { json =>
    (json \ "RCASPName").validateOpt[String].flatMap {
      case Some(_) =>
        (json \ "IsRCASPUser").validate[Boolean].flatMap {
          case true  => json.validate[OrganisationRcaspDetailsRcaspUser]
          case false => json.validate[OrganisationRcaspDetailsStandard]
        }
      case None    => json.validate[IndividualRcaspDetails]
    }
  }

  implicit val writes: Writes[RcaspDetails] = {
    case i: IndividualRcaspDetails   => IndividualRcaspDetails.format.writes(i)
    case o: OrganisationRcaspDetails =>
      o match {
        case rcaspUser: OrganisationRcaspDetailsRcaspUser => OrganisationRcaspDetailsRcaspUser.format.writes(rcaspUser)
        case standard: OrganisationRcaspDetailsStandard   => OrganisationRcaspDetailsStandard.format.writes(standard)
      }
  }
}

object IndividualRcaspDetails {
  implicit val format: OFormat[IndividualRcaspDetails] = Json.format[IndividualRcaspDetails]
}

object OrganisationRcaspDetailsRcaspUser {
  implicit val format: OFormat[OrganisationRcaspDetailsRcaspUser] = Json.format[OrganisationRcaspDetailsRcaspUser]
}

object OrganisationRcaspDetailsStandard {
  implicit val format: OFormat[OrganisationRcaspDetailsStandard] = Json.format[OrganisationRcaspDetailsStandard]
}

case class RcaspContactDetails(ContactName: String, EmailAddress: String)

object RcaspContactDetails {
  implicit val format: OFormat[RcaspContactDetails] = Json.format[RcaspContactDetails]
}
