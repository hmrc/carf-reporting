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
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.{BusinessError, MongoError, ValidationErrors}
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.{Pending, Rejected}
import uk.gov.hmrc.carfreporting.models.submission.{CarfId, FileDetails, FileStatus}
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.types.ResultT
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SubmissionRepository @Inject() (
    mongoComponent: MongoComponent,
    clock: Clock,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[FileDetails](
      mongoComponent = mongoComponent,
      collectionName = "submissionRepository",
      domainFormat = FileDetails.format,
      indexes = Seq(
        IndexModel(
          ascending("submissionTime"),
          IndexOptions()
            .name("submission-time-index")
            .expireAfter(appConfig.submissionTtlDays, TimeUnit.DAYS)
        ),
        IndexModel(
          ascending("uploadId"),
          IndexOptions()
            .name("uploadId-index")
            .unique(true)
        ),
        IndexModel(
          ascending("carfId"),
          IndexOptions()
            .name("carfId-index")
        )
      ),
      replaceIndexes = true
    ) {

  def findByUploadId(uploadId: UploadId): ResultT[Option[FileDetails]] =
    ResultT.fromFuture {
      collection
        .find(equal("uploadId", Codecs.toBson(uploadId.value)))
        .headOption()
        .map(Right(_))
        .recover { case _ =>
          Left(MongoError("Failed to call SubmissionRepository .findByUploadId"))
        }
    }

  def findByCarfId(carfId: CarfId): ResultT[Seq[FileDetails]] =
    ResultT.fromFuture {
      collection
        .find(equal("carfId", Codecs.toBson(carfId.value)))
        .toFuture()
        .map(Right(_))
        .recover { case _ =>
          Left(MongoError("Failed to call SubmissionRepository .findByCarfId"))
        }
    }

  def updateStatus(
      uploadId: UploadId,
      newStatus: FileStatus
  ): ResultT[Boolean] = {
    val filter: Bson                     = equal("uploadId", Codecs.toBson(uploadId.value))
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

  }

  def updateStatusWithErrors(
      uploadId: UploadId,
      newStatus: FileStatus,
      businessRuleErrors: ValidationErrors
  ): ResultT[Boolean] =
    if (newStatus == Rejected) {
      val filter: Bson                     = equal("uploadId", Codecs.toBson(uploadId.value))
      val modifier: Bson                   = Updates.combine(
        set("fileStatus", Codecs.toBson(newStatus)),
        set("lastStatusUpdateTime", Instant.now(clock)),
        set("businessRuleErrors", businessRuleErrors)
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
          "Error status update called without rejected status in SubmissionRepository .updateStatusWithErrors"
        )
      )

  def insert(fileDetails: FileDetails): ResultT[Boolean] =
    if (fileDetails.fileStatus == Pending) {
      ResultT.fromFuture {
        collection
          .insertOne(fileDetails)
          .toFuture()
          .map(_ => Right(true))
          .recover {
            case e: MongoWriteException =>
              Left(
                MongoError(
                  "MongoWriteException from SubmissionRepository .insert - ensure no duplicate uploadId"
                )
              )
            case _                      => Left(MongoError("Failed to call SubmissionRepository .insert"))
          }
      }
    } else
      ResultT.fromError(
        BusinessError("Tried to insert file with a status that is not Pending in SubmissionRepository .insert")
      )

}
