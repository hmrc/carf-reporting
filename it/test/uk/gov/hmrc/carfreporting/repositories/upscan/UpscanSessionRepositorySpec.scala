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

import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.must.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.carfreporting.base.TestData
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class UpscanSessionRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UploadSessionDetails]
    with IntegrationPatience
    with OptionValues
    with MockitoSugar
    with TestData {

  private val instant = Instant.now(clock)

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.cacheTtl) thenReturn 1L

  override protected val repository: UpscanSessionRepository = new UpscanSessionRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = clock
  )

  "UpscanSessionRepository" - {
    ".insert" - {
      "must insert an UploadSessionDetails" in {
        val setResult = repository.insert(uploadSessionDetails).value.futureValue
        val record    = find(Filters.equal("_id", uploadSessionDetails._id)).futureValue.headOption.value

        setResult mustBe Right(true)
        record    mustBe uploadSessionDetails
      }

      "must return a MongoError if there is already a record with the same uploadId" in {
        val setResult1 = repository.insert(uploadSessionDetails).value.futureValue
        val setResult2 = repository.insert(uploadSessionDetails.copy(reference = Reference("new"))).value.futureValue

        setResult1 mustBe Right(true)
        setResult2 mustBe Left(
          MongoError(
            "MongoWriteException from UpscanSessionRepository .insert - ensure no duplicate uploadId or reference"
          )
        )
      }

      "must return a MongoError if there is already a record with the same reference" in {
        val setResult1 = repository.insert(uploadSessionDetails).value.futureValue
        val setResult2 = repository.insert(uploadSessionDetails.copy(uploadId = UploadId("111111"))).value.futureValue

        setResult1 mustBe Right(true)
        setResult2 mustBe Left(
          MongoError(
            "MongoWriteException from UpscanSessionRepository .insert - ensure no duplicate uploadId or reference"
          )
        )
      }
    }

    ".findByUploadId" - {
      "when there is a record for the uploadId" in {
        insert(uploadSessionDetails).futureValue

        val result = repository.findByUploadId(testUploadId).value.futureValue

        result mustBe Right(Some(uploadSessionDetails))
      }

      "when there is no record for the uploadId" in {
        repository.findByUploadId(UploadId("abc")).value.futureValue mustBe Right(None)
      }
    }

    ".updateStatus" - {
      "must update the status and lastUpdated time" in {
        insert(uploadSessionDetails).futureValue

        val updateResult = repository.updateStatus(testReference, Failed).value.futureValue
        val record       = find(Filters.equal("_id", uploadSessionDetails._id)).futureValue.headOption.value

        updateResult mustBe Right(true)
        record       mustBe uploadSessionDetails.copy(status = Failed, lastUpdated = instant)
      }
    }
  }
}
