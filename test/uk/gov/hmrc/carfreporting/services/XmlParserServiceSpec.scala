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

package uk.gov.hmrc.carfreporting.services

import org.mockito.ArgumentMatchers.{any, argThat, eq as eqTo}
import org.mockito.Mockito.*
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.connectors.FtsConnector
import uk.gov.hmrc.carfreporting.dispatchers.{MainDispatcherName, XmlDispatcher}
import uk.gov.hmrc.carfreporting.models.ExtractedCarfFileDetails
import uk.gov.hmrc.carfreporting.models.errors.ApiError.ApiInternalServerError
import uk.gov.hmrc.carfreporting.models.errors.{InternalServerError, InvalidXmlError, XmlErrors}
import uk.gov.hmrc.carfreporting.models.submission.FileStatus
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.http.StringContextOps

import java.io.{ByteArrayInputStream, FileNotFoundException, IOException, InputStream}
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class XmlParserServiceSpec extends NoGuiceSpecBase with TestData {

  val mockXmlDataHandlerService: XmlDataHandlerService = mock[XmlDataHandlerService]
  val mockSubmissionRepository: SubmissionRepository   = mock[SubmissionRepository]
  val mockFtsConnector: FtsConnector                   = mock[FtsConnector]

  val mainDispatcherName = new MainDispatcherName()
  val xmlDispatcher      = new XmlDispatcher(actorSystem, mainDispatcherName)
  val service            =
    new XmlParserService(mockXmlDataHandlerService, mockSubmissionRepository, mockFtsConnector)(testEnv)(xmlDispatcher)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockXmlDataHandlerService, mockSubmissionRepository, mockFtsConnector, mockEnv)
  }

  val testInputStreamSuccess: ResultT[InputStream] =
    ResultT.fromValue(new ByteArrayInputStream("test123".getBytes(StandardCharsets.UTF_8)))

  val testUrl: String                    = "http://example.com"
  val testFileLocationThatExists: String =
    "data/businessRuleResponseExamples/BusinessRuleCheckSampleRequest_ValidFile_v0.3.xml"
  val testUrlAsURL: URL                  = url"$testUrl"

  def serviceWhereOpenFails(resourceName: String, e: Throwable): XmlParserService =
    new XmlParserService(mockXmlDataHandlerService, mockSubmissionRepository, mockFtsConnector)(testEnv)(
      xmlDispatcher
    ) {
      override protected def openStream(url: URL): InputStream =
        if url.toString.endsWith(resourceName) then throw e
        else super.openStream(url)
    }

  "XmlParserService" - {
    "validateAndExtractCarf" - {
      "must return InternalServerError when the XML file does not exist (given a url)" in {
        val fileName = "invalid/path/nonexistent.xml"
        val path     = Paths.get(fileName).toUri.toString

        val result = service.validateAndExtractCarf(path).value.futureValue

        result mustBe Left(InternalServerError("XML file cannot be found with path provided"))

        verify(mockXmlDataHandlerService, times(0)).carfValidationAndExtraction(any(), any())
      }

      "must return InternalServerError when the XML file does not exist (given a path to a file in this repository)" in {
        val path = "invalid/path/nonexistent.xml"

        val result = service.validateAndExtractCarf(path).value.futureValue

        result mustBe Left(InternalServerError("XML file cannot be found with path provided"))

        verify(mockXmlDataHandlerService, times(0)).carfValidationAndExtraction(any(), any())
      }

      "must return an ExtractedFileDetails when returned by XmlDataHandlerService (given a url)" in {
        when(mockXmlDataHandlerService.carfValidationAndExtraction(any(), any()))
          .thenReturn(Right(extractedFileDetailsCarf))

        val result = service.validateAndExtractCarf(testUrl).value.futureValue

        result mustBe Right(extractedFileDetailsCarf)

        verify(mockXmlDataHandlerService, times(1)).carfValidationAndExtraction(any(), any())
      }

      "must return an ExtractedFileDetails when returned by XmlDataHandlerService (given a path to a file in this repository)" in {
        when(mockXmlDataHandlerService.carfValidationAndExtraction(any(), any()))
          .thenReturn(Right(extractedFileDetailsCarf))

        val path = "resources/data/valid-carf.xml"

        val result = service.validateAndExtractCarf(path).value.futureValue

        result mustBe Right(extractedFileDetailsCarf)

        verify(mockXmlDataHandlerService, times(1)).carfValidationAndExtraction(any(), any())
      }

      "must return XmlErrors when XmlDataHandlerService returns schema errors (the XML is well-formed but fails schema validation)" in {
        when(mockXmlDataHandlerService.carfValidationAndExtraction(any(), any())).thenReturn(Left(xmlErrors))

        val fileName = "test/resources/data/invalid-carf.xml"
        val path     = Paths.get(fileName).toUri.toString

        val result = service.validateAndExtractCarf(path).value.futureValue

        result match {
          case Left(e: XmlErrors) =>
            val errorMessages = e.errors.map(_.errorMessage)
            e.errors.length  mustBe 4
            errorMessages.head must include("\"MessageTypeIndic\" is not allowed.")
            errorMessages(1)   must include("\"ReportingPeriod\" is not allowed.")
            errorMessages(2)   must include("\"Timestamp\" is not allowed.")
            errorMessages(3)   must include("uncompleted content model.")
          case _                  => fail()
        }

        verify(mockXmlDataHandlerService, times(1)).carfValidationAndExtraction(any(), any())
      }

      "must return an InvalidXmlError when XmlDataHandlerService returns InvalidXmlError (the XML is completely malformed)" in {
        when(mockXmlDataHandlerService.carfValidationAndExtraction(any(), any())).thenReturn(Left(InvalidXmlError))

        val fileName = "test/resources/data/malformed-xml.xml"
        val path     = Paths.get(fileName).toUri.toString

        val result = service.validateAndExtractCarf(path).value.futureValue

        result mustBe Left(InvalidXmlError)

        verify(mockXmlDataHandlerService, times(1)).carfValidationAndExtraction(any(), any())
      }
    }

    "validateAndExtractBusinessRules" - {
      "must return a success when path is a url and XmlDataHandlerService + connector are called successfully" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
          .thenReturn(Right(validExtractedBusinessRulesFileDetails))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(eqTo(testUrlAsURL))(using any()))
          .thenReturn(testInputStreamSuccess)

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result mustBe Right(validExtractedBusinessRulesFileDetails)

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockFtsConnector, times(1)).downloadBusinessRulesFile(eqTo(testUrlAsURL))(using any())
        verify(mockSubmissionRepository, times(1)).updateStatus(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.Accepted)
        )
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return an error when path is a url but the connector call returns an error" in {
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(eqTo(testUrlAsURL))(using any()))
          .thenReturn(ResultT.fromError(ApiInternalServerError("unexpected error")))

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result mustBe Left(ApiInternalServerError("unexpected error"))

        verify(mockXmlDataHandlerService, never()).businessRulesValidationAndExtraction(any(), any())
        verify(mockFtsConnector, times(1)).downloadBusinessRulesFile(eqTo(testUrlAsURL))(using any())
        verify(mockSubmissionRepository, times(1)).updateStatus(any(), any())
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return a success when path is NOT a url and XmlDataHandlerService + connector are called successfully" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
          .thenReturn(Right(validExtractedBusinessRulesFileDetails))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result = service.validateAndExtractBusinessRules(testFileLocationThatExists, testUploadId).value.futureValue

        result mustBe Right(validExtractedBusinessRulesFileDetails)

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.Accepted)
        )
        verify(mockFtsConnector, never()).downloadBusinessRulesFile(any())(using any())
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return an internal server error when path is NOT a url but doesn't exist" in {
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val result = service.validateAndExtractBusinessRules("/any", testUploadId).value.futureValue

        result mustBe Left(InternalServerError("Could not find file"))

        verify(mockXmlDataHandlerService, never()).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(any(), any())
        verify(mockFtsConnector, never()).downloadBusinessRulesFile(any())(using any())
      }

      "must return an internal server error when path is NOT a url but file isn't found when opening stream" in {
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val serviceWithFileNotFound =
          serviceWhereOpenFails(testFileLocationThatExists, new FileNotFoundException("missing"))

        val result = serviceWithFileNotFound
          .validateAndExtractBusinessRules(testFileLocationThatExists, testUploadId)
          .value
          .futureValue

        result mustBe Left(InternalServerError("File not found exception"))

        verify(mockXmlDataHandlerService, never()).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(any(), any())
        verify(mockFtsConnector, never()).downloadBusinessRulesFile(any())(using any())
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return an internal server error when path is NOT a url but there is an unexpected error when opening stream" in {
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))

        val serviceWithFileNotFound = serviceWhereOpenFails(testFileLocationThatExists, new IOException("boom"))

        val result = serviceWithFileNotFound
          .validateAndExtractBusinessRules(testFileLocationThatExists, testUploadId)
          .value
          .futureValue

        result mustBe Left(InternalServerError("Unexpected error opening file"))

        verify(mockXmlDataHandlerService, never()).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(any(), any())
        verify(mockFtsConnector, never()).downloadBusinessRulesFile(any())(using any())
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return ExtractedBusinessRulesFileDetails when returned by XmlDataHandlerService" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
          .thenReturn(Right(validExtractedBusinessRulesFileDetails))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(any())(using any())).thenReturn(testInputStreamSuccess)

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result mustBe Right(validExtractedBusinessRulesFileDetails)

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.Accepted)
        )
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return ExtractedBusinessRulesFileDetails with errors when valid file with errors are returned by XmlDataHandlerService" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
          .thenReturn(Right(validExtractedBusinessRulesFileDetailsWithErrors))
        when(mockSubmissionRepository.updateStatusWithErrors(any(), any(), any())).thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(any())(using any())).thenReturn(testInputStreamSuccess)

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result mustBe Right(validExtractedBusinessRulesFileDetailsWithErrors)

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, never()).updateStatus(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatusWithErrors(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.Rejected),
          argThat(_ == validExtractedBusinessRulesFileDetailsWithErrors.validationErrors)
        )
      }

      "must return XmlErrors when XmlDataHandlerService returns schema errors (the XML is well-formed but fails schema validation)" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any())).thenReturn(Left(xmlErrors))
        when(mockSubmissionRepository.updateStatus(testUploadId, FileStatus.UnprocessableErrorFile))
          .thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(any())(using any())).thenReturn(testInputStreamSuccess)

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result match {
          case Left(e: XmlErrors) =>
            e.errors.length                 mustBe 4
            e.errors.map(_.errorMessage).head must include("\"MessageTypeIndic\" is not allowed.")
          case _                  => fail()
        }

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.UnprocessableErrorFile)
        )
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }

      "must return an InvalidXmlError when XmlDataHandlerService returns InvalidXmlError (the XML is completely " +
        "malformed)" in {
          when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
            .thenReturn(Left(InvalidXmlError))
          when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))
          when(mockFtsConnector.downloadBusinessRulesFile(any())(using any())).thenReturn(testInputStreamSuccess)

          val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

          result mustBe Left(InvalidXmlError)

          verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
          verify(mockSubmissionRepository, times(1)).updateStatus(
            argThat(_ == testUploadId),
            argThat(_ == FileStatus.UnprocessableErrorFile)
          )
          verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
        }

      "must return an InternalServerError when businessRulesValidationAndExtraction throws an exception" in {
        when(mockXmlDataHandlerService.businessRulesValidationAndExtraction(any(), any()))
          .thenThrow(new RuntimeException("unexpected explosion"))
        when(mockSubmissionRepository.updateStatus(any(), any())).thenReturn(ResultT.fromValue(true))
        when(mockFtsConnector.downloadBusinessRulesFile(any())(using any())).thenReturn(testInputStreamSuccess)

        val result = service.validateAndExtractBusinessRules(testUrl, testUploadId).value.futureValue

        result match {
          case Left(e: InternalServerError) =>
            e.message must include("unexpected explosion")
          case _                            => fail()
        }

        verify(mockXmlDataHandlerService, times(1)).businessRulesValidationAndExtraction(any(), any())
        verify(mockSubmissionRepository, times(1)).updateStatus(
          argThat(_ == testUploadId),
          argThat(_ == FileStatus.UnprocessableErrorFile)
        )
        verify(mockSubmissionRepository, never()).updateStatusWithErrors(any(), any(), any())
      }
    }
  }
}
