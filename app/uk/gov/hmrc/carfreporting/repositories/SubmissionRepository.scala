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

package uk.gov.hmrc.carfreporting.repositories

import com.mongodb.MongoWriteException
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import uk.gov.hmrc.carfreporting.models.SavedAEOIFileDetails
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SubmissionRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SavedAEOIFileDetails](
      mongoComponent = mongoComponent,
      collectionName = "submissionRepository",
      domainFormat = SavedAEOIFileDetails.mongoFormat,
      indexes = Seq(
        IndexModel(
          ascending("extractedAEOIFileDetails.uploadId"),
          IndexOptions()
            .name("uploadId-index")
            .unique(true)
        )
      ),
      replaceIndexes = true
    )
    with Logging {

  def insert(fileDetails: SavedAEOIFileDetails): ResultT[Boolean] =
    ResultT.fromFuture {
      collection
        .insertOne(fileDetails)
        .toFuture()
        .map(_ => Right(true))
        .recover {
          case e: MongoWriteException =>
            val errorMessage =
              s"Exception from SubmissionRepository.insert with message: ${e.getMessage}"
            logger.error(errorMessage)
            Left(
              MongoError(errorMessage)
            )
          case e                      =>
            val errorMessage = s"Failed to call SubmissionRepository .insert with message: ${e.getMessage}"
            logger.error(errorMessage)
            Left(MongoError(errorMessage))
        }
    }

}
