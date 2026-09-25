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
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import play.api.Logging
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.*
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.{Pending, Rejected}
import uk.gov.hmrc.carfreporting.models.submission.{FileStatus, SubmissionDetailsCache}
import uk.gov.hmrc.carfreporting.models.{UploadId, ValidationErrors}
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SubmissionRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig, clock: Clock)(implicit
    ec: ExecutionContext
) extends PlayMongoRepository[SubmissionDetailsCache](
      mongoComponent = mongoComponent,
      collectionName = "submissionRepository",
      domainFormat = SubmissionDetailsCache.format,
      indexes = Seq(
        IndexModel(
          ascending("submissionTime"),
          IndexOptions()
            .name("submission-time-index")
            .expireAfter(appConfig.submissionTtlDays, TimeUnit.DAYS)
        ),
        IndexModel(
          ascending("carfId"),
          IndexOptions()
            .name("carfId-index")
        )
      ),
      replaceIndexes = true
    )
    with Logging {

  def insert(submissionDetailsCache: SubmissionDetailsCache): ResultT[Boolean] =
    if (submissionDetailsCache.fileStatus == Pending) {
      ResultT.fromFuture {
        collection
          .insertOne(submissionDetailsCache)
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
    } else {
      ResultT.fromError(
        BusinessError("Tried to insert file with a status that is not Pending in SubmissionRepository .insert")
      )
    }

  def updateStatus(
      uploadId: UploadId,
      newStatus: FileStatus
  ): ResultT[Boolean] =
    if (newStatus != Rejected) {
      val filter: Bson                     = equal("_id", Codecs.toBson(uploadId.value))
      val modifier: Bson                   = Updates.combine(
        set("fileStatus", Codecs.toBson(newStatus)),
        set("lastStatusUpdateTime", Instant.now(clock))
      )
      val options: FindOneAndUpdateOptions = FindOneAndUpdateOptions().upsert(true)

      ResultT.fromFuture {
        collection
          .findOneAndUpdate(filter, modifier, options)
          .toFuture()
          .map(_ => Right(true))
          .recover { case _ =>
            Left(MongoError("Failed to call SubmissionRepository .updateStatus"))
          }
      }
    } else {
      ResultT.fromError(
        BusinessError(
          "Error updateStatus called with rejected status in SubmissionRepository .updateStatus"
        )
      )
    }

  def updateStatusWithErrors(
      uploadId: UploadId,
      newStatus: FileStatus,
      businessRuleErrors: ValidationErrors
  ): ResultT[Boolean] =
    if (newStatus == Rejected) {
      val filter: Bson                     = equal("_id", Codecs.toBson(uploadId.value))
      val modifier: Bson                   = Updates.combine(
        set("fileStatus", Codecs.toBson(newStatus)),
        set("lastStatusUpdateTime", Instant.now(clock)),
        set("businessRuleErrors", Codecs.toBson(businessRuleErrors))
      )
      val options: FindOneAndUpdateOptions = FindOneAndUpdateOptions().upsert(true)

      ResultT.fromFuture {
        collection
          .findOneAndUpdate(filter, modifier, options)
          .toFuture()
          .map(_ => Right(true))
          .recover { case _ =>
            Left(MongoError("Failed to call SubmissionRepository .updateStatus"))
          }
      }
    } else
      ResultT.fromError(
        BusinessError(
          "Error updateStatusWithErrors called without rejected status in SubmissionRepository .updateStatusWithErrors"
        )
      )

}
