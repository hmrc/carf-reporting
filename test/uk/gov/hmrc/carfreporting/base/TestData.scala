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
import uk.gov.hmrc.carfreporting.models.ExtractedFileDetails
import uk.gov.hmrc.carfreporting.models.errors.{XmlError, XmlErrors}
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.models.upscan.UploadStatus.*

import java.time.*
import java.util.UUID

trait TestData {

  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1718118467838L), ukZoneId)

  val uuid: String = UUID.randomUUID().toString

  val testUploadId    = UploadId(uuid)
  val testReference   = Reference("11370e18-6e24-453e-b45a-76d3e32ea33d")
  val testDownloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676"

  val uploadSessionDetails = UploadSessionDetails(
    ObjectId.get(),
    testUploadId,
    testReference,
    Quarantined,
    Instant.ofEpochSecond(1)
  )

  val uploadDetails = UploadDetails(
    uploadTimestamp = Instant.now(clock),
    checksum = "396f1",
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
      checksum = Some("396f1")
    )

  val uploadRejected: UploadStatus.UploadRejected = UploadRejected(errorDetails("REJECTED"))

  val extractedFileDetailsValidCarf = ExtractedFileDetails(
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
}
