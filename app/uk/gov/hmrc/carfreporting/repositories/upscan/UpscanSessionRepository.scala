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

package uk.gov.hmrc.carfreporting.repositories.upscan

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanSessionRepository @Inject() (
    mongoComponent: MongoComponent,
    clock: Clock,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UploadSessionDetails](
      mongoComponent = mongoComponent,
      collectionName = "upscanSessionRepository",
      domainFormat = UploadSessionDetails.format,
      indexes = Seq(
        IndexModel(
          ascending("lastUpdated"),
          IndexOptions()
            .name("upscan-last-updated-index")
            .expireAfter(appConfig.cacheTtl, TimeUnit.SECONDS)
        ),
        IndexModel(
          ascending("uploadId"),
          IndexOptions()
            .name("uploadId-index")
            .unique(false)
        ),
        IndexModel(
          ascending("reference"),
          IndexOptions()
            .name("reference-index")
            .unique(false)
        )
      ),
      replaceIndexes = true
    ) {

  def findByUploadId(uploadId: UploadId): Future[Option[UploadSessionDetails]] =
    collection
      .find(equal("uploadId", Codecs.toBson(uploadId.value)))
      .first()
      .toFutureOption()

  def updateStatus(
      reference: Reference,
      newStatus: UploadStatus
  ): Future[Boolean] = {
    val filter: Bson                     = equal("reference", Codecs.toBson(reference.value))
    val modifier: Bson                   = Updates.combine(
      set("status", Codecs.toBson(newStatus)),
      set("lastUpdated", Instant.now(clock))
    )
    val options: FindOneAndUpdateOptions = FindOneAndUpdateOptions().upsert(true)

    collection
      .findOneAndUpdate(filter, modifier, options)
      .toFuture()
      .map(_ => true)
  }

  def insert(uploadDetails: UploadSessionDetails): Future[Boolean] =
    collection
      .insertOne(uploadDetails)
      .toFuture()
      .map(_ => true)

}
