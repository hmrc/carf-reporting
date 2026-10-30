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

package uk.gov.hmrc.carfreporting.services.submission

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, reset, times, verify, verifyNoInteractions, when}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.Constants.*
import uk.gov.hmrc.carfreporting.connectors.{EmailConnector, EmailNotSent, EmailSent}
import uk.gov.hmrc.carfreporting.models.ReportType
import uk.gov.hmrc.carfreporting.models.submission.*

import java.time.Instant
import scala.concurrent.Future

class EmailServiceSpec extends SpecBase {

  private val mockEmailConnector = mock[EmailConnector]
  private val service            = new EmailService(mockEmailConnector)

  private val statusTime       = Instant.parse("2027-03-01T11:14:00Z")
  private val expectedDateTime = "11:14am on 1 March 2027"

  private val userEmail          = "jane.smith@example.com"
  private val rcaspEmail         = "test@example.com"
  private val secondUserEmail    = "second.user@example.com"
  private val secondRcaspEmail   = "second.rcasp@example.com"
  private val secondUserOrgName  = "Second Org Ltd"
  private val secondRcaspContact = "secondRcaspContact"

  private val rcaspUser = OrganisationRcaspDetailsRcaspUser("ZMCAR0123456788", true, "Crypto Co")

  private def submission(
      status: FileStatus = FileStatus.Accepted,
      subscription: DisplaySubscriptionDetails = displaySubscriptionDetailsOrg,
      rcasp: RcaspDetails = individualRcaspDetails,
      details: uk.gov.hmrc.carfreporting.models.ExtractedCarfFileDetails = extractedFileDetailsCarf
  ): SubmissionDetailsCache =
    testSubmissionDetailsCache.copy(
      fileStatus = status,
      subscriptionDetails = subscription,
      rcaspDetails = rcasp,
      extractedFileDetails = details,
      lastStatusUpdateTime = statusTime
    )

  private val nilReportDetails = extractedFileDetailsCarf.copy(
    messageTypeIndic = nilReportMessageTypeIndic,
    rcaspName = None,
    docTypeIndic = None,
    reportType = None
  )

  private val passedCommon = Map(
    "dateTime"   -> expectedDateTime,
    "fileId"     -> "MSG-2024-0001",
    "rcaspId"    -> "SENDER-001",
    "rcaspName"  -> "Acme Crypto Exchange Ltd",
    "reportType" -> ReportType.NewInformation.description
  )

  private val failedCommon = Map(
    "dateTime"         -> expectedDateTime,
    "fileId"           -> "MSG-2024-0001",
    "rcaspId"          -> "SENDER-001",
    "rcaspLabel"       -> "RCASP (RCASP Name)",
    "rcaspName"        -> "Acme Crypto Exchange Ltd",
    "userBusinessName" -> testOrgName
  )

  private val secondUserContact =
    DisplaySubscriptionContact(None, Some(DisplaySubscriptionOrganisation(secondUserOrgName)), secondUserEmail)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEmailConnector)
    when(mockEmailConnector.sendEmail(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(EmailSent))
  }

  private def verifySent(address: String, template: String, params: Map[String, String]): Unit =
    verify(mockEmailConnector, times(1)).sendEmail(eqTo(address), eqTo(template), eqTo(params))(any(), any())

  private def verifyTotalSent(count: Int): Unit =
    verify(mockEmailConnector, times(count)).sendEmail(any(), any(), any())(any(), any())

  "EmailService" - {
    "sendStatusEmails" - {

      "when the file status is Accepted" - {

        "must email the user and the RCASP contact when the user is not the RCASP" in {
          service.sendStatusEmails(submission()).futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> testOrgName))
          verifySent(
            rcaspEmail,
            carfFilePassedRcaspTemplateId,
            passedCommon + ("contactName"                                                   -> "testContactName", "userBusinessName" -> testOrgName)
          )
          verifyTotalSent(2)
        }

        "must email every user contact and every RCASP contact" in {
          val subscription = displaySubscriptionDetailsOrg.copy(secondaryContact = Some(secondUserContact))
          val rcasp        = organisationRcaspDetails.copy(
            SecondaryContactDetails = Some(RcaspContactDetails(secondRcaspContact, secondRcaspEmail))
          )

          service.sendStatusEmails(submission(subscription = subscription, rcasp = rcasp)).futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> testOrgName))
          verifySent(
            secondUserEmail,
            carfFilePassedUserTemplateId,
            passedCommon + ("contactName"                                                   -> secondUserOrgName)
          )
          verifySent(
            rcaspEmail,
            carfFilePassedRcaspTemplateId,
            passedCommon + ("contactName"                                                   -> "testContactName", "userBusinessName"  -> testOrgName)
          )
          verifySent(
            secondRcaspEmail,
            carfFilePassedRcaspTemplateId,
            passedCommon + ("contactName"                                                   -> secondRcaspContact, "userBusinessName" -> testOrgName)
          )
          verifyTotalSent(4)
        }

        "must email only the user when the user is the RCASP" in {
          service.sendStatusEmails(submission(rcasp = rcaspUser)).futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> testOrgName))
          verify(mockEmailConnector, never())
            .sendEmail(any(), eqTo(carfFilePassedRcaspTemplateId), any())(any(), any())
          verifyTotalSent(1)
        }

        "must email only the user when an individual RCASP is also the user" in {
          service
            .sendStatusEmails(submission(rcasp = individualRcaspDetails.copy(IsRCASPUser = true)))
            .futureValue

          verifyTotalSent(1)
          verify(mockEmailConnector, never()).sendEmail(eqTo(rcaspEmail), any(), any())(any(), any())
        }

        "must use the individual's full name when the primary contact is an individual" in {
          service
            .sendStatusEmails(submission(subscription = displaySubscriptionDetailsInd, rcasp = rcaspUser))
            .futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> "Jane Smith"))
        }

        "must put the business name from the primary contact, not the trading name, in the RCASP email" in {
          service.sendStatusEmails(submission()).futureValue

          verify(mockEmailConnector, never())
            .sendEmail(any(), any(), eqTo(passedCommon + ("userBusinessName" -> testTradingName)))(any(), any())
          verifySent(
            rcaspEmail,
            carfFilePassedRcaspTemplateId,
            passedCommon + ("contactName" -> "testContactName", "userBusinessName" -> testOrgName)
          )
        }

        "must use the reportType already stored on the file details when there is one" in {
          val details = extractedFileDetailsCarf.copy(reportType = Some(ReportType.DeletionOfExistingReport))

          service.sendStatusEmails(submission(rcasp = rcaspUser, details = details)).futureValue

          verifySent(
            userEmail,
            carfFilePassedUserTemplateId,
            passedCommon + ("contactName" -> testOrgName, "reportType" -> "Deletion of the whole of an existing report")
          )
        }

        "must work out the reportType from the file details when none is stored" in {
          val details = extractedFileDetailsCarf.copy(reportType = None)

          service.sendStatusEmails(submission(rcasp = rcaspUser, details = details)).futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> testOrgName))
        }

        "must use the RCASP name from management data for a nil report (individual RCASP)" in {
          service.sendStatusEmails(submission(details = nilReportDetails)).futureValue

          val expected = passedCommon ++ Map(
            "rcaspName"  -> "testFirstName testLastName",
            "reportType" -> ReportType.NilReport.description
          )

          verifySent(userEmail, carfFilePassedUserTemplateId, expected + ("contactName" -> testOrgName))
        }

        "must use the RCASP name from management data for a nil report (organisation RCASP)" in {
          service
            .sendStatusEmails(submission(rcasp = organisationRcaspDetails, details = nilReportDetails))
            .futureValue

          verifySent(
            userEmail,
            carfFilePassedUserTemplateId,
            passedCommon ++ Map(
              "rcaspName"   -> "testFirstName",
              "reportType"  -> ReportType.NilReport.description,
              "contactName" -> testOrgName
            )
          )
        }

        "must send no email when the primary contact has neither an organisation nor an individual" in {
          val invalidPrimary = DisplaySubscriptionContact(None, None, userEmail)
          val subscription   = displaySubscriptionDetailsOrg.copy(primaryContact = invalidPrimary)

          service.sendStatusEmails(submission(subscription = subscription)).futureValue

          verifyNoInteractions(mockEmailConnector)
        }

        "must skip a user contact with no name but still email the other contact" in {
          val noNameContact = DisplaySubscriptionContact(None, None, "noname@example.com")
          val subscription  = displaySubscriptionDetailsOrg.copy(secondaryContact = Some(noNameContact))

          service.sendStatusEmails(submission(subscription = subscription, rcasp = rcaspUser)).futureValue

          verifySent(userEmail, carfFilePassedUserTemplateId, passedCommon + ("contactName" -> testOrgName))
          verify(mockEmailConnector, never()).sendEmail(eqTo("noname@example.com"), any(), any())(any(), any())
          verifyTotalSent(1)
        }

        "must still send the other emails when one fails to send" in {
          when(mockEmailConnector.sendEmail(eqTo(userEmail), any(), any())(any(), any()))
            .thenReturn(Future.successful(EmailNotSent))

          service.sendStatusEmails(submission()).futureValue

          verifySent(
            rcaspEmail,
            carfFilePassedRcaspTemplateId,
            passedCommon + ("contactName" -> "testContactName", "userBusinessName" -> testOrgName)
          )
          verifyTotalSent(2)
        }

        "must complete successfully when every email fails to send" in {
          when(mockEmailConnector.sendEmail(any(), any(), any())(any(), any()))
            .thenReturn(Future.successful(EmailNotSent))

          service.sendStatusEmails(submission()).futureValue mustBe ((): Unit)
        }
      }

      "when the file status is Rejected" - {

        "must email the user with the failed template" in {
          service.sendStatusEmails(submission(status = FileStatus.Rejected)).futureValue

          verifySent(userEmail, carfFileFailedUserTemplateId, failedCommon + ("contactName" -> testOrgName))
          verifyTotalSent(1)
        }

        "must email every user contact" in {
          val subscription = displaySubscriptionDetailsOrg.copy(secondaryContact = Some(secondUserContact))

          service.sendStatusEmails(submission(status = FileStatus.Rejected, subscription = subscription)).futureValue

          verifySent(userEmail, carfFileFailedUserTemplateId, failedCommon + ("contactName" -> testOrgName))
          verifySent(
            secondUserEmail,
            carfFileFailedUserTemplateId,
            failedCommon + ("contactName"                                                   -> secondUserOrgName)
          )
          verifyTotalSent(2)
        }

        "must not email the RCASP, even when the user is not the RCASP" in {
          service.sendStatusEmails(submission(status = FileStatus.Rejected)).futureValue

          verify(mockEmailConnector, never()).sendEmail(eqTo(rcaspEmail), any(), any())(any(), any())
          verify(mockEmailConnector, never())
            .sendEmail(any(), eqTo(carfFilePassedRcaspTemplateId), any())(any(), any())
        }

        "must use the individual's full name for an individual user" in {
          service
            .sendStatusEmails(submission(status = FileStatus.Rejected, subscription = displaySubscriptionDetailsInd))
            .futureValue

          verifySent(
            userEmail,
            carfFileFailedUserTemplateId,
            failedCommon + ("contactName" -> "Jane Smith", "userBusinessName" -> "Jane Smith")
          )
        }

        "must use the label RCASP and the management data name for a nil report" in {
          service
            .sendStatusEmails(submission(status = FileStatus.Rejected, details = nilReportDetails))
            .futureValue

          verifySent(
            userEmail,
            carfFileFailedUserTemplateId,
            failedCommon ++ Map(
              "rcaspLabel"  -> "RCASP",
              "rcaspName"   -> "testFirstName testLastName",
              "contactName" -> testOrgName
            )
          )
        }

        "must send no email when the primary contact has neither an organisation nor an individual" in {
          val subscription =
            displaySubscriptionDetailsOrg.copy(primaryContact = DisplaySubscriptionContact(None, None, userEmail))

          service.sendStatusEmails(submission(status = FileStatus.Rejected, subscription = subscription)).futureValue

          verifyNoInteractions(mockEmailConnector)
        }

        "must complete successfully when the email fails to send" in {
          when(mockEmailConnector.sendEmail(any(), any(), any())(any(), any()))
            .thenReturn(Future.successful(EmailNotSent))

          service.sendStatusEmails(submission(status = FileStatus.Rejected)).futureValue mustBe ((): Unit)
        }
      }

      "when the file status is not Accepted or Rejected" - {
        "must send no email" in {
          List(
            FileStatus.Pending,
            FileStatus.VirusFound,
            FileStatus.UnprocessableErrorFile,
            FileStatus.UnexpectedError
          ).foreach { status =>
            service.sendStatusEmails(submission(status = status)).futureValue

            verifyNoInteractions(mockEmailConnector)
          }
        }
      }
    }
  }
}
