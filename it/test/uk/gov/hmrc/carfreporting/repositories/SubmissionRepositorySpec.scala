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
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import uk.gov.hmrc.carfreporting.base.TestData
import uk.gov.hmrc.carfreporting.models.SavedAEOIFileDetails
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.*

class SubmissionRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[SavedAEOIFileDetails]
    with IntegrationPatience
    with OptionValues
    with TestData {
  
  override protected val checkTtlIndex: Boolean = false // TODO remove when CARF-611 is implemented
  
  override protected val repository: SubmissionRepository = new SubmissionRepository(
    mongoComponent = mongoComponent
  )

  "SubmissionRepository" - {
    ".insert" - {
      "must insert a SavedAEOIFileDetails" in {
        val setResult = repository.insert(testSavedAEOIFileDetails).value.futureValue
        val record    = find(Filters.equal("_id", testSavedAEOIFileDetails._id)).futureValue.headOption.value

        setResult mustBe Right(true)
        record    mustBe testSavedAEOIFileDetails
      }

      "must return a MongoError if there is already a record with the same uploadId" in {
        val setResult1 = repository.insert(testSavedAEOIFileDetails).value.futureValue
        val Left(setResult2) =
          repository.insert(testSavedAEOIFileDetails.copy(_id = org.bson.types.ObjectId.get())).value.futureValue

        setResult1 mustBe Right(true)
        setResult2.message.contains("duplicate key error collection") mustBe true
      }
    }
  }
}