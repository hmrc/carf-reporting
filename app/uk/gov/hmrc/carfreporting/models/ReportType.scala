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

import uk.gov.hmrc.carfreporting.config.Constants

enum ReportType(val description: String) {
  case TestData extends ReportType("Test data")
  case NilReport extends ReportType("No reportable information")
  case NotificationOfReportingOutsideUk
      extends ReportType(
        "Notification that this RCASP elects to report in another country outside the UK"
      )
  case NewInformation extends ReportType("New information")
  case AdditionalInformationForExistingReport extends ReportType("Additional information for an existing report")
  case DeletionOfExistingReport extends ReportType("Deletion of the whole of an existing report")
  case CorrectedInformationForExistingReport extends ReportType("Corrected information for an existing report")
  case DeletedInformationForExistingReport
      extends ReportType(
        "Deletion of selected information from an existing report"
      )
  case CorrectedAndDeletedInformationForExistingReport
      extends ReportType(
        "Corrected and deleted information for an existing report"
      )
  case ReportableInformationFallback extends ReportType("Reportable information")
}

extension (details: ExtractedCarfFileDetails) {

  def getReportType: ReportType = {
    import ReportType.*

    if (details.isTestData) {
      TestData
    } else {
      details.messageTypeIndic match {
        case Constants.carf701MessageTypeIndic =>
          if (details.hasOtherNexus && !details.hasCryptoUsers)
            NotificationOfReportingOutsideUk
          else if (details.docTypeIndic.contains(Constants.oecd0DocTypeIndic))
            AdditionalInformationForExistingReport
          else
            NewInformation

        case Constants.carf702MessageTypeIndic =>
          details.docTypeIndic match {
            case Some(Constants.deletionDocTypeIndic) =>
              DeletionOfExistingReport

            case Some(Constants.oecd0DocTypeIndic) | Some(Constants.correctionDocTypeIndic) =>
              if (details.allCryptoUsersAreCorrections || !details.hasCryptoUsers)
                CorrectedInformationForExistingReport
              else if (
                details.docTypeIndic.contains(Constants.oecd0DocTypeIndic) &&
                details.allCryptoUsersAreDeletions
              )
                DeletedInformationForExistingReport
              else
                CorrectedAndDeletedInformationForExistingReport

            case _ =>
              ReportableInformationFallback
          }

        case Constants.nilReportMessageTypeIndic =>
          NilReport

        case _ =>
          ReportableInformationFallback
      }
    }
  }
}
