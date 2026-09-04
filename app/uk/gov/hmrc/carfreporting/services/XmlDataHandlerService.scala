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

import cats.data.NonEmptyChain
import com.ctc.wstx.stax.WstxInputFactory
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.validation.{XMLValidationProblem, XMLValidationSchema}
import play.api.Logging
import uk.gov.hmrc.carfreporting.config.Constants.*
import uk.gov.hmrc.carfreporting.services.xmlElements.CarfBody.*
import uk.gov.hmrc.carfreporting.services.xmlElements.CarfBody.RcaspName.*
import uk.gov.hmrc.carfreporting.services.xmlElements.MessageSpec.*
import uk.gov.hmrc.carfreporting.models.{ExtractedAEOIFileDetails, ExtractedCarfFileDetails, FileError, RecordError, ValidationErrors, ValidationResult}
import uk.gov.hmrc.carfreporting.models.errors.*

import java.io.InputStream
import javax.inject.{Inject, Singleton}
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

@Singleton
class XmlDataHandlerService @Inject() extends Logging {

  inline private val maxErrors = 101

  private case class CarfBodyRcaspName(
      var firstName: String = "",
      var lastName: String = "",
      var entityName: String = ""
  ) {
    def individualName: String = s"$firstName $lastName"
  }

  def carfValidationAndExtraction(
      schema: XMLValidationSchema,
      inputStream: InputStream
  ): Either[CarfError, ExtractedCarfFileDetails] =
    validateAndExtract(schema, inputStream)(readCarfXml)

  def aeoiValidationAndExtraction(
                                   schema: XMLValidationSchema,
                                   inputStream: InputStream
                                 ): Either[CarfError, ExtractedAEOIFileDetails] =
    validateAndExtract(schema, inputStream)(readAEOIXml)
  
  private def validateAndExtract[A](schema: XMLValidationSchema,
                                    inputStream: InputStream)(readXml: XMLStreamReader2 => A) = {
    val errors = ListBuffer.empty[XmlError]

    val factory = new WstxInputFactory()

    // Security hardening: block XML External Entity (XXE) attacks.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)

    val reader: XMLStreamReader2 = factory.createXMLStreamReader(inputStream).asInstanceOf[XMLStreamReader2]

    reader.validateAgainst(schema)

    reader.setValidationProblemHandler { (problem: XMLValidationProblem) =>
      val loc = problem.getLocation
      val line = if (loc != null) loc.getLineNumber else 0
      if (errors.size < maxErrors) {
        errors += XmlError(line, problem.getType, problem.getMessage)
      } else {
        logger.warn(s"Truncated: more than $maxErrors schema errors in this file; further errors dropped.")
        throw new XmlStreamFailSafeException
      }
    }

    Try {
      readXml(reader)
    } match {
      case Success(extractedFileDetails) =>
        reader.close()
        resolveErrors(errors, Right(extractedFileDetails))
      case Failure(e: XmlStreamFailSafeException) =>
        reader.close()
        resolveErrors(errors, Left(XmlErrors(Vector.empty)))
      case Failure(e) =>
        reader.close()
        Left(InternalServerError(e.getMessage))
    }
  }

  private def readCarfXml(reader: XMLStreamReader2): ExtractedCarfFileDetails = {
    val path = ListBuffer.empty[String]

    val carfBodyRcaspName = CarfBodyRcaspName()

    var sendingEntityIn: String  = ""
    var messageType: String      = ""
    var messageRefId: String     = ""
    var messageTypeIndic: String = ""

    var rcaspName: Option[String]         = None
    var rcaspDocTypeIndic: Option[String] = None

    val cryptoUserDocTypeIndics = ListBuffer.empty[String]

    var hasOtherNexus: Boolean  = false
    var hasCryptoUsers: Boolean = false

    var carfBodyCompleted: Boolean = false

    def currentPath: List[String] = path.reverse.toList

    def pathEndsWith(expected: String*): Boolean = currentPath.startsWith(expected.toList)

    def readElement(): String = {
      val value = reader.getElementText.trim
      if (path.nonEmpty) path.remove(path.size - 1)
      value
    }

    while (reader.hasNext)
      reader.next() match {
        case XMLStreamConstants.START_ELEMENT if !carfBodyCompleted =>
          val localName = reader.getLocalName
          path += localName

          localName match {
            case SENDING_ENTITY_IN if pathEndsWith(SENDING_ENTITY_IN, MESSAGE_SPEC)   =>
              sendingEntityIn = readElement()
            case MESSAGE_TYPE if pathEndsWith(MESSAGE_TYPE, MESSAGE_SPEC)             =>
              messageType = readElement()
            case MESSAGE_REF_ID if pathEndsWith(MESSAGE_REF_ID, MESSAGE_SPEC)         =>
              messageRefId = readElement()
            case MESSAGE_TYPE_INDIC if pathEndsWith(MESSAGE_TYPE_INDIC, MESSAGE_SPEC) =>
              messageTypeIndic = readElement()

            case DOC_TYPE_INDIC if pathEndsWith(DOC_TYPE_INDIC, DOC_SPEC, RCASP, CARF_BODY)        =>
              rcaspDocTypeIndic = Some(readElement())
            case DOC_TYPE_INDIC if pathEndsWith(DOC_TYPE_INDIC, DOC_SPEC, CRYPTO_USERS, CARF_BODY) =>
              cryptoUserDocTypeIndics += readElement()

            case FIRST_NAME if pathEndsWith(FIRST_NAME, NAME, INDIVIDUAL, RCASP_ID, RCASP, CARF_BODY) =>
              carfBodyRcaspName.firstName = readElement()
            case LAST_NAME if pathEndsWith(LAST_NAME, NAME, INDIVIDUAL, RCASP_ID, RCASP, CARF_BODY)   =>
              carfBodyRcaspName.lastName = readElement()
            case NAME if pathEndsWith(NAME, ENTITY, RCASP_ID, RCASP, CARF_BODY)                       =>
              carfBodyRcaspName.entityName = readElement()

            case CRYPTO_USERS if pathEndsWith(CRYPTO_USERS, CARF_BODY)      =>
              hasCryptoUsers = true
            case OTHER_NEXUS if pathEndsWith(OTHER_NEXUS, RCASP, CARF_BODY) =>
              hasOtherNexus = true

            case _ => // Nothing
          }

        case XMLStreamConstants.END_ELEMENT if !carfBodyCompleted =>
          val localName = reader.getLocalName
          if (localName == CARF_BODY) {
            val name =
              if (carfBodyRcaspName.entityName.nonEmpty) carfBodyRcaspName.entityName
              else carfBodyRcaspName.individualName
            rcaspName = Some(name)
            carfBodyCompleted = true
          }
          if (path.nonEmpty) path.remove(path.size - 1)

        case _ => // Nothing
      }

    ExtractedCarfFileDetails(
      messageRefId = messageRefId,
      sendingEntityIn = if sendingEntityIn.isEmpty then "missing" else sendingEntityIn,
      rcaspName = if messageTypeIndic == nilReportMessageTypeIndic then None else rcaspName,
      messageTypeIndic = messageTypeIndic, // TODO: Will be changed to an enum later (CARF-611)
      hasOtherNexus = hasOtherNexus,
      hasCryptoUsers = hasCryptoUsers,
      docTypeIndic = rcaspDocTypeIndic, // TODO: Will be changed to an enum later (CARF-611)
      isTestData = {
        val docTypeIndics = rcaspDocTypeIndic.fold(List.empty)(List(_)) ++ cryptoUserDocTypeIndics
        docTypeIndics.exists(docTypeIndic => testDataDocTypeIndics.contains(docTypeIndic))
      },
      allCryptoUsersAreCorrections =
        cryptoUserDocTypeIndics.nonEmpty && cryptoUserDocTypeIndics.forall(_ == correctionDocTypeIndic),
      allCryptoUsersAreDeletions =
        cryptoUserDocTypeIndics.nonEmpty && cryptoUserDocTypeIndics.forall(_ == deletionDocTypeIndic)
    )
  }
  
  private def readAEOIXml(reader: XMLStreamReader2): ExtractedAEOIFileDetails = {
    import xmlElements.AEOIRequestDetail._

    val path = ListBuffer.empty[String]

    val fileErrors = ListBuffer.empty[FileError]
    val recordErrors = ListBuffer.empty[RecordError]

    var currentFileErrorCode: String = ""
    var currentFileErrorDetails: Option[String] = None

    var currentRecordErrorCode: String = ""
    var currentRecordErrorDetails: Option[String] = None
    val currentDocRefIDs = ListBuffer.empty[String]

    var status: String = ""

    var requestDetailCompleted: Boolean = false

    def currentPath: List[String] = path.reverse.toList

    def pathEndsWith(expected: String*): Boolean = currentPath.startsWith(expected.toList)

    def readElement(): String = {
      val value = reader.getElementText.trim
      if (path.nonEmpty) path.remove(path.size - 1)
      value
    }

    while (reader.hasNext)
      reader.next() match {
        case XMLStreamConstants.START_ELEMENT if !requestDetailCompleted =>
          val localName = reader.getLocalName
          path += localName

          localName match {
            case CODE if pathEndsWith(CODE, FILE_ERROR, VALIDATION_ERRORS, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              currentFileErrorCode = readElement()
            case DETAILS if pathEndsWith(DETAILS, FILE_ERROR, VALIDATION_ERRORS, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              currentFileErrorDetails = Some(readElement())
            case CODE if pathEndsWith(CODE, RECORD_ERROR, VALIDATION_ERRORS, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              currentRecordErrorCode = readElement()
            case DETAILS if pathEndsWith(DETAILS, RECORD_ERROR, VALIDATION_ERRORS, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              currentRecordErrorDetails = Some(readElement())
            case DOC_REF_ID_IN_ERROR if pathEndsWith(DOC_REF_ID_IN_ERROR, RECORD_ERROR, VALIDATION_ERRORS, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              currentDocRefIDs += readElement()
            case STATUS if pathEndsWith(STATUS, VALIDATION_RESULT, GENERIC_STATUS_MESSAGE, REQUEST_DETAIL) =>
              status = readElement()
            case _ => // Nothing
          }

        case XMLStreamConstants.END_ELEMENT if !requestDetailCompleted =>
          val localName = reader.getLocalName

          localName match {
            case FILE_ERROR =>
              fileErrors += FileError(currentFileErrorCode, currentFileErrorDetails)
              currentFileErrorCode = ""
              currentFileErrorDetails = None
            case RECORD_ERROR =>
              recordErrors += RecordError(currentRecordErrorCode, currentRecordErrorDetails, currentDocRefIDs.toSeq)
              currentRecordErrorCode = ""
              currentRecordErrorDetails = None
              currentDocRefIDs.clear()
            case REQUEST_DETAIL =>
              requestDetailCompleted = true

            case _ => // Nothing
          }

          if (path.nonEmpty) path.remove(path.size - 1)

        case _ => // Nothing
      }

    ExtractedAEOIFileDetails(
      validationErrors = ValidationErrors(
        fileError = fileErrors.toSeq,
        recordError = recordErrors.toSeq
      ),
      validationResult = ValidationResult(
        status = status //TODO convert to enum here when implemented
      )
    )
  }

  private def resolveErrors[A](
      errors: ListBuffer[XmlError],
      responseWithoutErrors: => Either[XmlErrors, A]
  ): Either[XmlErrors, A] =
    NonEmptyChain
      .fromSeq(errors.toSeq)
      .fold(responseWithoutErrors)(nec => Left(XmlErrors(nec.toNonEmptyVector.toVector)))

}
