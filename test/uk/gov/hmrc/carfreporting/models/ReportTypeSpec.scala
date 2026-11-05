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

package uk.gov.hmrc.carfreporting.models

import play.api.libs.json.*
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.Constants

class ReportTypeSpec extends SpecBase {

  private val carf701 = Constants.carf701MessageTypeIndic
  private val carf702 = Constants.carf702MessageTypeIndic
  private val carf703 = Constants.nilReportMessageTypeIndic
  private val oecd0   = Constants.oecd0DocTypeIndic
  private val oecd2   = Constants.correctionDocTypeIndic
  private val oecd3   = Constants.deletionDocTypeIndic

  private def details(
      messageTypeIndic: String = carf701,
      hasOtherNexus: Boolean = false,
      hasCryptoUsers: Boolean = true,
      docTypeIndic: Option[String] = Some("OECD1"),
      isTestData: Boolean = false,
      allCryptoUsersAreCorrections: Boolean = false,
      allCryptoUsersAreDeletions: Boolean = false
  ): ExtractedCarfFileDetails =
    ExtractedCarfFileDetails(
      messageRefId = "CARFFILEID",
      sendingEntityIn = "1234567890",
      rcaspName = Some("RCASPName"),
      messageTypeIndic = messageTypeIndic,
      hasOtherNexus = hasOtherNexus,
      hasCryptoUsers = hasCryptoUsers,
      docTypeIndic = docTypeIndic,
      isTestData = isTestData,
      allCryptoUsersAreCorrections = allCryptoUsersAreCorrections,
      allCryptoUsersAreDeletions = allCryptoUsersAreDeletions
    )

  "ReportType" - {

    ".getReportType" - {

      "when isTestData is true" - {
        "must return TestData whatever the message type" in {
          List(carf701, carf702, carf703).foreach { messageTypeIndic =>
            details(messageTypeIndic = messageTypeIndic, isTestData = true).getReportType mustBe ReportType.TestData
          }
        }
      }

      "when isTestData is false" - {

        "and messageTypeIndic is CARF701" - {
          "must return NotificationOfReportingOutsideUk when hasOtherNexus is true and hasCryptoUsers is false" in {
            List(Some(oecd0), Some("OECD1"), None).foreach { docTypeIndic =>
              details(
                messageTypeIndic = carf701,
                hasOtherNexus = true,
                hasCryptoUsers = false,
                docTypeIndic = docTypeIndic
              ).getReportType mustBe ReportType.NotificationOfReportingOutsideUk
            }
          }

          "must return AdditionalInformationForExistingReport when the nexus condition is not met and docTypeIndic is OECD0" in {
            List((false, false), (true, true), (false, true)).foreach { case (hasOtherNexus, hasCryptoUsers) =>
              details(
                messageTypeIndic = carf701,
                hasOtherNexus = hasOtherNexus,
                hasCryptoUsers = hasCryptoUsers,
                docTypeIndic = Some(oecd0)
              ).getReportType mustBe ReportType.AdditionalInformationForExistingReport
            }
          }

          "must return NewInformation when the nexus condition is not met and docTypeIndic is not OECD0" in {
            for {
              (hasOtherNexus, hasCryptoUsers) <- List((false, false), (true, true), (false, true))
              docTypeIndic                    <- List(Some("OECD1"), Some(oecd2), Some(oecd3), Some("OECD10"), Some("OECD11"), None)
            } details(
              messageTypeIndic = carf701,
              hasOtherNexus = hasOtherNexus,
              hasCryptoUsers = hasCryptoUsers,
              docTypeIndic = docTypeIndic
            ).getReportType mustBe ReportType.NewInformation
          }
        }

        "and messageTypeIndic is CARF702" - {
          "must return DeletionOfExistingReport when docTypeIndic is OECD3" in {
            details(messageTypeIndic = carf702, docTypeIndic = Some(oecd3)).getReportType mustBe
              ReportType.DeletionOfExistingReport
          }

          "and docTypeIndic is OECD0 or OECD2" - {
            "must return CorrectedInformationForExistingReport when all crypto users are corrections" in {
              List(oecd0, oecd2).foreach { docTypeIndic =>
                details(
                  messageTypeIndic = carf702,
                  hasCryptoUsers = true,
                  docTypeIndic = Some(docTypeIndic),
                  allCryptoUsersAreCorrections = true
                ).getReportType mustBe ReportType.CorrectedInformationForExistingReport
              }
            }

            "must return CorrectedInformationForExistingReport when there are no crypto users" in {
              List(oecd0, oecd2).foreach { docTypeIndic =>
                details(
                  messageTypeIndic = carf702,
                  hasCryptoUsers = false,
                  docTypeIndic = Some(docTypeIndic),
                  allCryptoUsersAreCorrections = false
                ).getReportType mustBe ReportType.CorrectedInformationForExistingReport
              }
            }

            "must return DeletedInformationForExistingReport when docTypeIndic is OECD0 and all crypto users are deletions" in {
              details(
                messageTypeIndic = carf702,
                hasCryptoUsers = true,
                docTypeIndic = Some(oecd0),
                allCryptoUsersAreCorrections = false,
                allCryptoUsersAreDeletions = true
              ).getReportType mustBe ReportType.DeletedInformationForExistingReport
            }

            "must return CorrectedAndDeletedInformationForExistingReport when docTypeIndic is OECD0 and not all crypto users are deletions" in {
              details(
                messageTypeIndic = carf702,
                hasCryptoUsers = true,
                docTypeIndic = Some(oecd0),
                allCryptoUsersAreCorrections = false,
                allCryptoUsersAreDeletions = false
              ).getReportType mustBe ReportType.CorrectedAndDeletedInformationForExistingReport
            }

            "must return CorrectedAndDeletedInformationForExistingReport when docTypeIndic is OECD2, even if all crypto users are deletions" in {
              List(true, false).foreach { allCryptoUsersAreDeletions =>
                details(
                  messageTypeIndic = carf702,
                  hasCryptoUsers = true,
                  docTypeIndic = Some(oecd2),
                  allCryptoUsersAreCorrections = false,
                  allCryptoUsersAreDeletions = allCryptoUsersAreDeletions
                ).getReportType mustBe ReportType.CorrectedAndDeletedInformationForExistingReport
              }
            }
          }

          "must return ReportableInformationFallback when docTypeIndic is not OECD0, OECD2 or OECD3" in {
            List(Some("OECD1"), Some("OECD10"), Some("OECD11"), Some("OECD12"), Some("OECD13"), None).foreach {
              docTypeIndic =>
                details(messageTypeIndic = carf702, docTypeIndic = docTypeIndic).getReportType mustBe
                  ReportType.ReportableInformationFallback
            }
          }
        }

        "and messageTypeIndic is CARF703" - {
          "must return NilReport whatever the other fields are" in {
            List(true, false).foreach { flag =>
              List(Some(oecd0), Some(oecd3), None).foreach { docTypeIndic =>
                details(
                  messageTypeIndic = carf703,
                  hasOtherNexus = flag,
                  hasCryptoUsers = flag,
                  docTypeIndic = docTypeIndic,
                  allCryptoUsersAreCorrections = flag,
                  allCryptoUsersAreDeletions = flag
                ).getReportType mustBe ReportType.NilReport
              }
            }
          }
        }

        "and messageTypeIndic is not recognised" - {
          "must return ReportableInformationFallback rather than throwing" in {
            details(messageTypeIndic = "CARF999").getReportType mustBe ReportType.ReportableInformationFallback
            details(messageTypeIndic = "").getReportType        mustBe ReportType.ReportableInformationFallback
          }
        }
      }

      "must work out the type from the raw fields, ignoring any reportType already set" in {
        val nilReportFields = details(messageTypeIndic = carf703)

        nilReportFields.copy(reportType = Some(ReportType.NewInformation)).getReportType mustBe ReportType.NilReport
      }
    }

    ".description" - {
      "must give the wording used in the emails and on the check your file details page" in {
        val expected = List(
          ReportType.TestData                                        -> "Test data",
          ReportType.NilReport                                       -> "No reportable information",
          ReportType.NotificationOfReportingOutsideUk                ->
            "Notification that this RCASP elects to report in another country outside the UK",
          ReportType.NewInformation                                  -> "New information",
          ReportType.AdditionalInformationForExistingReport          -> "Additional information for an existing report",
          ReportType.DeletionOfExistingReport                        -> "Deletion of the whole of an existing report",
          ReportType.CorrectedInformationForExistingReport           -> "Corrected information for an existing report",
          ReportType.DeletedInformationForExistingReport             ->
            "Deletion of selected information from an existing report",
          ReportType.CorrectedAndDeletedInformationForExistingReport ->
            "Corrected and deleted information for an existing report",
          ReportType.ReportableInformationFallback                   -> "Reportable information"
        )

        expected.foreach { case (reportType, description) =>
          reportType.description mustBe description
        }

        expected.map(_._1).toSet mustBe ReportType.values.toSet
      }
    }

    "JSON format" - {
      "must write each case as its name" in {
        ReportType.values.foreach { reportType =>
          Json.toJson(reportType) mustBe JsString(reportType.toString)
        }
      }

      "must write NewInformation as the string \"NewInformation\"" in {
        Json.toJson(ReportType.NewInformation) mustBe JsString("NewInformation")
      }

      "must round trip every case" in {
        ReportType.values.foreach { reportType =>
          Json.toJson(reportType).as[ReportType] mustBe reportType
        }
      }

      "must fail to read an unknown name" in {
        JsString("NotARealReportType").validate[ReportType] mustBe a[JsError]
      }

      "must be case sensitive" in {
        JsString("newinformation").validate[ReportType] mustBe a[JsError]
      }

      "must fail to read a value that is not a string" in {
        List(JsNumber(1), JsBoolean(true), JsNull, Json.obj(), Json.arr("NewInformation")).foreach { json =>
          json.validate[ReportType] mustBe a[JsError]
        }
      }
    }
  }
}
