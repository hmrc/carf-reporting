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

import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.BusinessError
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.Accepted
import uk.gov.hmrc.carfreporting.models.submission.SubmissionDetailsCache
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class SubmissionRepositorySpec
  extends SpecBase with IntegrationPatience with DefaultPlayMongoRepositorySupport[SubmissionDetailsCache]{
  
  override protected val checkTtlIndex: Boolean = false // TODO remove when CARF-611 is implemented
  
  val config: AppConfig = mock[AppConfig]
  
  override protected val repository: SubmissionRepository = new SubmissionRepository(
    mongoComponent = mongoComponent,
    appConfig = config,
    clock = clock
  )(ec)

  "SubmissionRepository" - {
    ".insert" - {
      "must insert a SubmissionDetailsCache" in {
        val setResult = repository.insert(testSubmissionDetailsCache).value.futureValue
        val record = find(Filters.equal("_id", testSubmissionDetailsCache._id.value)).futureValue.head

        setResult mustBe Right(true)
        record mustBe testSubmissionDetailsCache
      }

      "must return a MongoError if there is already a record with the same uploadId" in {
        val setResult1 = repository.insert(testSubmissionDetailsCache).value.futureValue
        val setResult2 =
          repository.insert(testSubmissionDetailsCache).value.futureValue

        setResult1 mustBe Right(true)
        setResult2 match {
          case Left(value) => value.message.contains("duplicate key error collection") mustBe true
          case _ => fail()
        }
      }

      "must return a MongoError if inserting without a Pending file status" in {
        val setResult = repository.insert(testSubmissionDetailsCache.copy(fileStatus = Accepted)).value.futureValue

        setResult match {
          case Left(BusinessError(message)) => message mustBe "Tried to insert file with a status that is not Pending in SubmissionRepository .insert"
          case _ => fail()
        }
      }
    }
    
    /*".update" - {
      "must update a SavedAEOIFileDetails" in {
        val setResult = repository.update(testSavedAEOIFileDetails).value.futureValue
        val record    = find(Filters.equal("_id", testSavedAEOIFileDetails._id)).futureValue.headOption.value

        setResult mustBe Right(true)
        record    mustBe testSavedAEOIFileDetails
      }

      "must return a MongoError if there is already a record with the same uploadId" in {
        val setResult1 = repository.update(testSavedAEOIFileDetails).value.futureValue
        val Left(setResult2) =
          repository.update(testSavedAEOIFileDetails.copy(_id = org.bson.types.ObjectId.get())).value.futureValue

        setResult1 mustBe Right(true)
        setResult2.message.contains("duplicate key error collection") mustBe true
      }
    }*/
  }
}