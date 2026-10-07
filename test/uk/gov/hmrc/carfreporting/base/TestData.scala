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

package uk.gov.hmrc.carfreporting.base

import org.bson.types.ObjectId
import uk.gov.hmrc.carfreporting.config.Constants.ukZoneId
import uk.gov.hmrc.carfreporting.models.*
import uk.gov.hmrc.carfreporting.models.errors.{XmlError, XmlErrors}
import uk.gov.hmrc.carfreporting.models.requests.sdes.*
import uk.gov.hmrc.carfreporting.models.requests.sdes.Algorithm.SHA256
import uk.gov.hmrc.carfreporting.models.requests.*
import uk.gov.hmrc.carfreporting.models.responses.*
import uk.gov.hmrc.carfreporting.models.submission.*
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.{Pending, Rejected}
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.models.upscan.UploadStatus.*

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

trait TestData {

  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1718118467838L), ukZoneId)

  val uuid: String = UUID.randomUUID().toString

  val testUploadId           = UploadId(uuid)
  val testReference          = Reference("11370e18-6e24-453e-b45a-76d3e32ea33d")
  inline val testDownloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676"

  val uploadSessionDetails = UploadSessionDetails(
    ObjectId.get(),
    testUploadId,
    testReference,
    Quarantined,
    Instant.ofEpochSecond(1)
  )

  val uploadDetails = UploadDetails(
    uploadTimestamp = Instant.now(clock),
    checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    fileMimeType = "application/xml",
    fileName = "test.xml",
    size = 987L
  )

  val readyCallbackBody = ReadyCallbackBody(
    testReference,
    testDownloadUrl,
    uploadDetails
  )

  def errorDetails(failureReason: String) =
    ErrorDetails(
      failureReason = failureReason,
      message = "Error message"
    )

  def failedCallbackBody(failureReason: String) =
    FailedCallbackBody(
      testReference,
      errorDetails(failureReason)
    )

  val uploadedSuccessfully: UploadStatus.UploadedSuccessfully =
    UploadedSuccessfully(
      name = "test.xml",
      mimeType = "application/xml",
      downloadUrl = testDownloadUrl,
      size = Some(987L),
      checksum = Some("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    )

  val uploadRejected: UploadStatus.UploadRejected = UploadRejected(errorDetails("REJECTED"))

  val extractedFileDetailsCarf = ExtractedCarfFileDetails(
    messageRefId = "MSG-2024-0001",
    sendingEntityIn = "SENDER-001",
    rcaspName = Some("Acme Crypto Exchange Ltd"),
    messageTypeIndic = "CARF701",
    hasOtherNexus = false,
    hasCryptoUsers = true,
    docTypeIndic = Some("OECD1"),
    isTestData = false,
    allCryptoUsersAreCorrections = false,
    allCryptoUsersAreDeletions = false
  )

  val xmlErrors = XmlErrors(
    errors = Vector(
      XmlError(
        15,
        null,
        "tag name \"MessageTypeIndic\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<Warning>"
      ),
      XmlError(
        17,
        null,
        "tag name \"ReportingPeriod\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<MessageTypeIndic>,<Warning>"
      ),
      XmlError(
        18,
        null,
        "tag name \"Timestamp\" is not allowed. Possible tag names are: <Contact>,<MessageRefId>,<MessageTypeIndic>,<ReportingPeriod>,<Warning>"
      ),
      XmlError(
        19,
        null,
        "uncompleted content model. expecting: <Contact>,<MessageRefId>,<MessageTypeIndic>,<ReportingPeriod>,<Timestamp>,<Warning>"
      )
    )
  )

  val validExtractedAEOIFileDetails = ExtractedAEOIFileDetails(
    UploadId("3ada9236-21a6-4ad2-9f0c-f01shdt40c5"),
    ValidationErrors(
      Seq.empty,
      Seq.empty
    ),
    ValidationResult(ValidationStatus.fromString("Accepted"))
  )

  lazy val businessRuleValidationErrors = ValidationErrors(
    fileError = Seq(
      FileError(
        code = "50009",
        details = Some("Duplicate message ref IDs")
      )
    ),
    recordError = Seq(
      RecordError(
        code = "80000",
        details = Some("Duplicate doc ref IDs"),
        docRefIDInError = Seq(
          "CBCUSER001DHSJEURUT20001",
          "CBCUSER001DHSJEURUT20002"
        )
      )
    )
  )

  val validExtractedAEOIFileDetailsWithErrors = ExtractedAEOIFileDetails(
    UploadId("3ada9236-21a6-4ad2-9f0c-f01shdt40c5"),
    businessRuleValidationErrors,
    ValidationResult(ValidationStatus.fromString("Rejected"))
  )

  lazy val testSavedAEOIFileDetails: SavedAEOIFileDetails = SavedAEOIFileDetails(
    ObjectId.get(),
    validExtractedAEOIFileDetails,
    Instant.ofEpochSecond(1)
  )

  val testNotification: FileTransferNotification = FileTransferNotification(
    informationType = "carf-reporting",
    file = File(
      name = FileName("test-file.xml"),
      location = "http://localhost:8080/download",
      checksum = Checksum(SHA256, "checksum12345"),
      size = 1024,
      recipientOrSender = Some("Sender"),
      properties = List(Property("name", "value"))
    ),
    audit = Audit("correlation-id-123456789")
  )

  inline val testCarfRef = "XACARF000001234"

  lazy val displaySubscriptionDetails: DisplaySubscriptionDetails = DisplaySubscriptionDetails(
    carfReference = CarfId(testCarfRef),
    gbUser = true,
    primaryContact = DisplaySubscriptionContact(
      individual = Some(DisplaySubscriptionIndividual("Jane", "Smith")),
      organisation = None,
      email = "jane.smith@example.com"
    ),
    secondaryContact = None
  )

  val individualRcaspDetails =
    IndividualRcaspDetails(
      RCASPID = "ZMCAR0123456788",
      IsRCASPUser = false,
      FirstName = "testFirstName",
      LastName = "testLastName",
      PrimaryContactDetails = RcaspContactDetails(ContactName = "testContactName", EmailAddress = "test@example.com")
    )

  val testSubmissionRequest: SubmissionRequest = SubmissionRequest(
    fileName = FileName("test-file.xml"),
    uploadId = testUploadId,
    fileSize = 1024L,
    documentUrl = "http://localhost:8080/file",
    checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    rcaspDetails = individualRcaspDetails,
    subscriptionDetails = displaySubscriptionDetails,
    extractedFileDetails = extractedFileDetailsCarf
  )

  lazy val testSubmissionDetailsCache: SubmissionDetailsCache = SubmissionDetailsCache(
    testSubmissionRequest.uploadId,
    testSubmissionRequest.subscriptionDetails.carfReference,
    Pending,
    testSubmissionRequest.fileName,
    extractedFileDetailsCarf,
    rcaspDetails = individualRcaspDetails,
    subscriptionDetails = displaySubscriptionDetails,
    submissionTime = Instant.ofEpochSecond(1),
    lastStatusUpdateTime = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    businessRuleErrors = ValidationErrors.apply()
  )

  val submissionDetailsList = Seq(
    testSubmissionDetailsCache,
    testSubmissionDetailsCache.copy(_id = UploadId("987654"), fileStatus = Rejected)
  )

  val testBusinessRulesFileName = "br-file-1.xml"

  val testSdesFileListing: Seq[FileListing] = Seq(
    FileListing(
      filename = testBusinessRulesFileName,
      fileSize = 100L,
      downloadURL = testDownloadUrl,
      metadata = Seq(
        FileListingMetadata(metadata = "FileType", value = "XML")
      )
    ),
    FileListing(
      filename = "br-file-2.xml",
      fileSize = 345L,
      downloadURL = testDownloadUrl,
      metadata = Seq.empty
    )
  )

  val submissionHistoryRequest = SubmissionHistoryRequest(
    submissionsListRequest = SubmissionsListRequest(
      requestCommon = SubmissionHistoryRequestCommon(
        originatingSystem = "MDTP",
        transmittingSystem = "CADX",
        regime = "CARF",
        requestParameters = None
      ),
      requestDetails = SubmissionHistoryRequestDetails(
        subscriptionId = testCarfRef,
        rcaspId = None
      )
    )
  )

  val submissionHistoryResponse: SubmissionHistoryResponse =
    SubmissionHistoryResponse(
      submissionsListResponse = SubmissionsListResponse(
        responseCommon = SubmissionHistoryResponseCommon(
          regime = "CARF",
          responseParameters = None
        ),
        responseDetails = SubmissionHistoryResponseDetails(
          submissionsList = submissionHistoryRecordList(2)
        )
      )
    )

  val submissionHistoryRecord: SubmissionHistoryRecord =
    SubmissionHistoryRecord(
      rcaspId = "ZMCAR0123456789",
      rcaspName = "Nemona Champion",
      filename = "filename1.xml",
      submissionStatus = SubmissionHistoryStatus.Passed,
      uploadDateTime = Instant.now(clock).minus(30, ChronoUnit.DAYS),
      messageRefId = "MSG-2024-0001",
      submissionFileType = "CARF-701",
      reportingYear = "2024",
      submissionCaseId = "CARF-SUB-001"
    )

  val submissionHistoryPassed: SubmissionHistoryPassed =
    SubmissionHistoryPassed(
      messageRefId = "MSG-2024-0001",
      rcaspName = "Nemona Champion",
      submissionTime = Instant.now(clock)
    )

  def submissionHistoryRecordList(numRecords: Int): Seq[SubmissionHistoryRecord] =
    (1 to numRecords).map(_ + 28).map { daysAgo =>
      SubmissionHistoryRecord(
        rcaspId = "ZMCAR0123456780",
        rcaspName = "Other RCASP Ltd",
        filename = s"filename$daysAgo.xml",
        submissionStatus = SubmissionHistoryStatus.Passed,
        uploadDateTime = Instant.now(clock).minus(daysAgo, ChronoUnit.DAYS),
        messageRefId = s"MSG-2024-00$daysAgo",
        submissionFileType = "CARF-701",
        reportingYear = "2024",
        submissionCaseId = s"CARF-SUB-0$daysAgo"
      )
    }

  def submissionHistoryPassedList(numRecords: Int): Seq[SubmissionHistoryPassed] =
    (1 to numRecords).map(_ + 28).map { daysAgo =>
      SubmissionHistoryPassed(
        messageRefId = s"MSG-2024-00$daysAgo",
        rcaspName = "Other RCASP Ltd",
        submissionTime = Instant.now(clock).minus(daysAgo, ChronoUnit.DAYS)
      )
    }
}
