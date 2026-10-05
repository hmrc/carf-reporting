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

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.connectors.SDESConnector
import uk.gov.hmrc.carfreporting.helpers.SDESFileMetadataHelper
import uk.gov.hmrc.carfreporting.models.errors.ApiError.InternalServerError
import uk.gov.hmrc.carfreporting.models.errors.{InvalidXmlError, XmlErrors}
import uk.gov.hmrc.carfreporting.models.requests.sdes.*
import uk.gov.hmrc.carfreporting.models.responses.FileListing
import uk.gov.hmrc.carfreporting.models.submission.FileStatus
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.services.XmlParserService
import uk.gov.hmrc.carfreporting.types.ResultT

import java.time.Instant
import scala.concurrent.Future

class SDESServiceSpec extends NoGuiceSpecBase with TestData {

  val mockSdesConnector: SDESConnector               = mock[SDESConnector]
  val mockXmlParserService: XmlParserService         = mock[XmlParserService]
  val mockSubmissionRepository: SubmissionRepository = mock[SubmissionRepository]
  val mockAppConfig: AppConfig                       = mock[AppConfig]

  val service = new SDESService(mockSdesConnector, mockXmlParserService, mockSubmissionRepository, mockAppConfig)

  val testSubmissionTime: Instant = Instant.now()
  val testInformationType         = "carf-submission"

  val expectedNotification = FileTransferNotification(
    informationType = testInformationType,
    file = File(
      name = testSubmissionRequest.fileName,
      location = testSubmissionRequest.documentUrl,
      checksum = Checksum(Algorithm.SHA256, testSubmissionRequest.checksum),
      size = testSubmissionRequest.fileSize,
      recipientOrSender = Some("carf-reporting"),
      properties = SDESFileMetadataHelper.generatePropertiesMetadata(testSubmissionRequest, testSubmissionTime)
    ),
    audit = Audit(testUploadId.value)
  )

  when(mockAppConfig.sdesInformationType).thenReturn(testInformationType)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSdesConnector, mockXmlParserService, mockSubmissionRepository)
  }

  "SDESService" - {
    "sendNotification" - {
      "construct the payload and send it to the connector successfully" in {

        when(mockSdesConnector.sendFileReadyNotification(eqTo(expectedNotification))(any(), any()))
          .thenReturn(EitherT.rightT[Future, uk.gov.hmrc.carfreporting.models.errors.ApiError](()))

        val result = service.sendNotification(testSubmissionRequest, testSubmissionTime).value.futureValue

        result mustBe Right(())
        verify(mockSdesConnector, times(1)).sendFileReadyNotification(eqTo(expectedNotification))(any(), any())
      }

      "return a Left error when the connector fails" in {
        when(mockSdesConnector.sendFileReadyNotification(any())(any(), any()))
          .thenReturn(EitherT.leftT[Future, Unit](InternalServerError))

        val result = service.sendNotification(testSubmissionRequest, testSubmissionTime).value.futureValue

        result mustBe Left(InternalServerError)
        verify(mockSdesConnector, times(1)).sendFileReadyNotification(eqTo(expectedNotification))(any(), any())
      }
    }

    ".getAndProcessBusinessRulesResponseFile" - {
      "must return a Right when the file is found and processed successfully" in {
        when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
          .thenReturn(ResultT.fromValue(testSdesFileListing))
        when(mockXmlParserService.validateAndExtractAEOI(any(), any()))
          .thenReturn(ResultT.fromValue(validExtractedAEOIFileDetails))

        val result =
          service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

        result mustBe Right(())

        verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
        verify(mockXmlParserService, times(1)).validateAndExtractAEOI(eqTo(testDownloadUrl), eqTo(testUploadId))
        verify(mockSubmissionRepository, times(0)).updateStatus(any(), any())
      }

      "must return an error when the file is found but XmlParserService returns an error" - {
        "when XmlParserService returns XmlErrors" in {
          when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
            .thenReturn(ResultT.fromValue(testSdesFileListing))
          when(mockXmlParserService.validateAndExtractAEOI(any(), any()))
            .thenReturn(ResultT.fromError(xmlErrors))

          val result =
            service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

          result mustBe Left(xmlErrors)

          verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
          verify(mockXmlParserService, times(1)).validateAndExtractAEOI(eqTo(testDownloadUrl), eqTo(testUploadId))
          verify(mockSubmissionRepository, times(0)).updateStatus(any(), any())
        }

        "when XmlParserService returns InvalidXmlError" in {
          when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
            .thenReturn(ResultT.fromValue(testSdesFileListing))
          when(mockXmlParserService.validateAndExtractAEOI(any(), any()))
            .thenReturn(ResultT.fromError(InvalidXmlError))

          val result =
            service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

          result mustBe Left(InvalidXmlError)

          verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
          verify(mockXmlParserService, times(1)).validateAndExtractAEOI(eqTo(testDownloadUrl), eqTo(testUploadId))
          verify(mockSubmissionRepository, times(0)).updateStatus(any(), any())
        }

        "when XmlParserService returns InternalServerError" in {
          when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
            .thenReturn(ResultT.fromValue(testSdesFileListing))
          when(mockXmlParserService.validateAndExtractAEOI(any(), any()))
            .thenReturn(ResultT.fromError(InternalServerError))

          val result =
            service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

          result mustBe Left(InternalServerError)

          verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
          verify(mockXmlParserService, times(1)).validateAndExtractAEOI(eqTo(testDownloadUrl), eqTo(testUploadId))
          verify(mockSubmissionRepository, times(0)).updateStatus(any(), any())
        }
      }

      "must return InternalServerError and update file status to UnexpectedError when the file is not found" in {
        when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
          .thenReturn(ResultT.fromValue(Seq.empty[FileListing]))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result =
          service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(eqTo(testUploadId), eqTo(FileStatus.UnexpectedError))
        verify(mockXmlParserService, times(0)).validateAndExtractAEOI(any(), any())
      }

      "must return the error and update file status to UnexpectedError when the connector returns an error" in {
        when(mockSdesConnector.getBusinessRulesFileListing()(any(), any()))
          .thenReturn(ResultT.fromError(InternalServerError))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result =
          service.getAndProcessBusinessRulesResponseFile(testUploadId, testBusinessRulesFileName).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSdesConnector, times(1)).getBusinessRulesFileListing()(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(eqTo(testUploadId), eqTo(FileStatus.UnexpectedError))
        verify(mockXmlParserService, times(0)).validateAndExtractAEOI(any(), any())
      }
    }
  }
}
