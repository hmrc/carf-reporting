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
import cats.syntax.all.*
import com.ctc.wstx.stax.WstxInputFactory
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.validation.*
import play.api.{Environment, Logging}
import uk.gov.hmrc.carfreporting.dispatchers.XmlDispatcher
import uk.gov.hmrc.carfreporting.models.errors.*
import uk.gov.hmrc.carfreporting.types.ResultT

import java.io.{BufferedInputStream, InputStream}
import javax.inject.{Inject, Singleton}
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class XmlParserService @Inject() (env: Environment)(implicit xmlDispatcher: XmlDispatcher) extends Logging {

  inline private val maxErrors = 101

  def validateAndExtract(path: String): ResultT[Unit] =
    for {
      schema      <- loadSchema
      inputStream <- openInputStream(path)
      result      <- initiate(schema, inputStream)
    } yield result

  private def initiate(schema: XMLValidationSchema, inputStream: InputStream): ResultT[Unit] = ResultT.fromFuture {
    Future {
      validationAndExtraction(schema, inputStream)
    } andThen { _ =>
      inputStream.close()
    } recover { e =>
      logger.error(s"Fatal Error XML Stream Failed with message: ${e.getMessage}")
      Left(InternalServerError(e.getMessage))
    }
  }

  private def validationAndExtraction(
      schema: XMLValidationSchema,
      inputStream: InputStream
  ): Either[CarfError, Unit] = {
    val factory = new WstxInputFactory()

    // Security hardening: block XML External Entity (XXE) attacks.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)

    val reader: XMLStreamReader2 = factory.createXMLStreamReader(inputStream).asInstanceOf[XMLStreamReader2]

    reader.validateAgainst(schema)

    val errors  = ListBuffer.empty[XmlError]
    val docRefs = ListBuffer.empty[String]

    reader.setValidationProblemHandler { (problem: XMLValidationProblem) =>
      val loc  = problem.getLocation
      val line = if (loc != null) loc.getLineNumber else 0
      if (errors.size < maxErrors) {
        errors += XmlError(line, problem.getType, problem.getMessage)
      } else {
        logger.warn("Truncated: more than $maxErrors schema errors in this file; further errors dropped.")
        throw new XmlStreamFailSafeException
      }
    }

    Try {
      while (reader.hasNext) {
        val event = reader.next()
        event match {
          case XMLStreamConstants.START_ELEMENT =>
            val localName = reader.getLocalName

            if (localName == "DocRefId") {
              val docRefValue = reader.getElementText.trim
              if (docRefValue.nonEmpty) {
                docRefs += docRefValue
              }
            }
          // TODO [CARF-593] Data Extraction - extract data here
          case _                                => // Nothing
        }
      }
      docRefs
    } match {
      case Success(value)                         =>
        logger.debug(s"Extracted DocRefs:\n${value.mkString(",\n")}")
        reader.close()
        resolveErrors(errors)
      case Failure(e: XmlStreamFailSafeException) =>
        reader.close()
        resolveErrors(errors)
      case Failure(e)                             =>
        reader.close()
        Left(InternalServerError(e.getMessage))
    }
  }

  private def resolveErrors(errors: ListBuffer[XmlError]): Either[CarfError, Unit] =
    NonEmptyChain.fromSeq(errors.toSeq) match {
      case Some(nec) => Left(XmlErrors(nec.toNonEmptyVector.toVector))
      case None      => ().asRight
    }

  private def openInputStream(path: String): ResultT[InputStream] =
    Try {
      env.resource(path).map { url =>
        new java.io.BufferedInputStream(url.openStream())
      }
    } match {
      case Success(Some(in)) => ResultT.fromValue(in)
      case Success(None)     =>
        ResultT.fromError(
          XmlErrors(
            Vector(
              XmlError(
                0,
                "file_not_found",
                "File cannot be found with path provided"
              )
            )
          )
        )
      case Failure(e)        =>
        ResultT.fromError(
          XmlErrors(
            Vector(
              XmlError(
                0,
                "unexpected_error",
                s"Unexpected error when creating Buffered Input Stream with message: ${e.getMessage}"
              )
            )
          )
        )
    }

  private def loadSchema: ResultT[XMLValidationSchema] = {
    val defaultSchemaPath = "data/schemas/CARFXML_v1.5.xsd"

    Try {
      val schemaFactory = XMLValidationSchemaFactory
        .newInstance(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA)

      env.resource(defaultSchemaPath).map { url =>
        schemaFactory.createSchema(url)
      }
    } match {
      case Success(Some(validationSchema)) => ResultT.fromValue(validationSchema)
      case Success(None)                   =>
        resolveError(
          "file_not_found",
          s"Schema file cannot be found"
        ) // Would have to delete schema for test coverage but would rely on test to be guaranteed last, not recommended
      case Failure(e)                      =>
        resolveError(
          "unexpected_error",
          s"Unexpected error when creating Buffered Input Stream with message: ${e.getMessage}"
        )
    }
  }

  private def resolveError(code: String, message: String): ResultT[XMLValidationSchema] =
    ResultT.fromError[XMLValidationSchema](XmlErrors(Vector(XmlError(0, code, message))))
}
