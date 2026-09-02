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

import org.codehaus.stax2.validation.*
import play.api.{Environment, Logging}
import uk.gov.hmrc.carfreporting.dispatchers.XmlDispatcher
import uk.gov.hmrc.carfreporting.models.ExtractedFileDetails
import uk.gov.hmrc.carfreporting.models.errors.*
import uk.gov.hmrc.carfreporting.types.ResultT

import java.io.{FileNotFoundException, InputStream}
import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class XmlParserService @Inject (
    dataHandlerService: XmlDataHandlerService
)(env: Environment)(implicit xmlDispatcher: XmlDispatcher)
    extends Logging {

  def validateAndExtract(path: String): ResultT[ExtractedFileDetails] =
    for {
      schema      <- loadSchema
      inputStream <- openInputStream(path)
      result      <- initiate(schema, inputStream)
    } yield result

  private def initiate(schema: XMLValidationSchema, inputStream: InputStream): ResultT[ExtractedFileDetails] =
    ResultT.fromFuture {
      Future {
        dataHandlerService.validationAndExtraction(schema, inputStream)
      } andThen { _ =>
        inputStream.close()
      } recover { e =>
        logger.error(s"Fatal Error XML Stream Failed with message: ${e.getMessage}")
        Left(InternalServerError(e.getMessage))
      }
    }

  private def openInputStream(path: String): ResultT[InputStream] =
    Try {
      new java.io.BufferedInputStream(new URI(path).toURL.openStream())
    } match {
      case Success(in)                       => ResultT.fromValue(in)
      case Failure(_: FileNotFoundException) =>
        ResultT.fromError(InternalServerError("XML file cannot be found with path provided"))
      case Failure(e)                        =>
        ResultT.fromError(
          InternalServerError(s"Unexpected error when creating Buffered Input Stream: ${e.getMessage}")
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
        // Would have to delete schema for test coverage but would rely on test to be guaranteed last, not recommended
        ResultT.fromError(InternalServerError("Schema file cannot be found"))
      case Failure(e)                      =>
        ResultT.fromError(
          InternalServerError(s"Unexpected error when creating Buffered Input Stream: ${e.getMessage}")
        )
    }
  }
}
