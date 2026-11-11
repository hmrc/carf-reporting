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

import org.mockito.Mockito.when
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters
import org.scalatest.freespec.AnyFreeSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.{AppConfig, CryptoProvider}
import uk.gov.hmrc.carfreporting.models.UploadId
import uk.gov.hmrc.carfreporting.models.crypto.CryptoType.{randomAesKey, CryptoT}
import uk.gov.hmrc.carfreporting.models.errors.MongoError
import uk.gov.hmrc.carfreporting.models.upscan.*
import uk.gov.hmrc.carfreporting.models.upscan.UploadStatus.*
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant

class UpscanSessionRepositorySpec extends SpecBase with DefaultPlayMongoRepositorySupport[UploadSessionDetails] {

  private val instant = Instant.now(clock)

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.cacheTtl) thenReturn 1L
  when(mockAppConfig.mongoEncryptionEnabled) thenReturn false

  private implicit val crypto: CryptoT = new CryptoProvider(Configuration("crypto.key" -> randomAesKey)).get()

  override protected val repository: UpscanSessionRepository = new UpscanSessionRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = clock
  )(ec, crypto)

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

      "must persist status in encrypted format when encryption is enabled" in {
        val mockConfigWithEncryption = mock[AppConfig]
        when(mockConfigWithEncryption.cacheTtl) thenReturn 1L
        when(mockConfigWithEncryption.mongoEncryptionEnabled) thenReturn true

        val repositoryWithEncryption = new UpscanSessionRepository(
          mongoComponent = mongoComponent,
          appConfig = mockConfigWithEncryption,
          clock = clock
        )

        repositoryWithEncryption.insert(uploadSessionDetails).value.futureValue mustBe Right(true)

        val updateResult = repositoryWithEncryption.updateStatus(testReference, Failed).value.futureValue

        updateResult mustBe Right(true)

        val record = repositoryWithEncryption.collection
          .find[BsonDocument](Filters.equal("reference", testReference.value))
          .headOption()
          .futureValue
          .value

        val rawStatus = record.get("status").asString().getValue

        val decryptedStatus = crypto.decrypt(Crypted(rawStatus)).value

        Json.parse(decryptedStatus) mustBe Json.toJson(Failed)
      }

      "must persist status in plain format when encryption is disabled" in {
        insert(uploadSessionDetails).futureValue

        val updateResult = repository.updateStatus(testReference, Failed).value.futureValue

        updateResult mustBe Right(true)

        val record = repository.collection
          .find[BsonDocument](Filters.equal("reference", testReference.value))
          .headOption()
          .futureValue
          .value

        val rawStatus = record.get("status").asDocument()

        Json.parse(rawStatus.toJson) mustBe Json.toJson(Failed)
      }
    }
  }
}
