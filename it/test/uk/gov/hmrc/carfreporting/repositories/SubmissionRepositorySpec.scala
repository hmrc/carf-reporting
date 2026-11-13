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
import org.scalactic.Prettifier.default
import org.scalatest.freespec.AnyFreeSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.{AppConfig, CryptoProvider}
import uk.gov.hmrc.carfreporting.models.crypto.CryptoType.{randomAesKey, CryptoT}
import uk.gov.hmrc.carfreporting.models.errors.BusinessError
import uk.gov.hmrc.carfreporting.models.submission.FileStatus.{Accepted, Rejected, VirusFound}
import uk.gov.hmrc.carfreporting.models.submission.SubmissionDetailsCache
import uk.gov.hmrc.carfreporting.models.{UploadId, ValidationErrors}
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant

class SubmissionRepositorySpec extends SpecBase with DefaultPlayMongoRepositorySupport[SubmissionDetailsCache] {

  val config: AppConfig = mock[AppConfig]
  when(config.cacheTtl) thenReturn 1L
  when(config.mongoEncryptionEnabled) thenReturn false

  private implicit val crypto: CryptoT = new CryptoProvider(Configuration("crypto.key" -> randomAesKey)).get()

  override protected val repository: SubmissionRepository = new SubmissionRepository(
    mongoComponent = mongoComponent,
    appConfig = config,
    clock = clock
  )(ec, crypto)

  "SubmissionRepository" - {
    ".insert" - {
      "must insert a SubmissionDetailsCache" in {
        val setResult = repository.insert(testSubmissionDetailsCache).value.futureValue
        val record    = find(Filters.equal("_id", testSubmissionDetailsCache._id.value)).futureValue.head

        setResult mustBe Right(true)
        record    mustBe testSubmissionDetailsCache
      }

      "must return a MongoError if there is already a record with the same uploadId" in {
        val setResult1 = repository.insert(testSubmissionDetailsCache).value.futureValue
        val setResult2 = repository.insert(testSubmissionDetailsCache).value.futureValue

        setResult1 mustBe Right(true)
        setResult2 match {
          case Left(value) => value.message.contains("duplicate key error collection") mustBe true
          case _           => fail()
        }
      }

      "must return a BusinessError if inserting without a Pending file status" in {
        val setResult = repository.insert(testSubmissionDetailsCache.copy(fileStatus = Accepted)).value.futureValue

        setResult match {
          case Left(BusinessError(message)) =>
            message mustBe "[SubmissionRepository][insert] Tried to insert file with a status that is not Pending in SubmissionRepository .insert"
          case _                            => fail()
        }
      }

      "must persist the data in encrypted format when encryption is enabled" in {
        val mockConfigWithEncryption = mock[AppConfig]
        when(mockConfigWithEncryption.cacheTtl) thenReturn 1L
        when(mockConfigWithEncryption.mongoEncryptionEnabled) thenReturn true

        val repositoryWithEncryption = new SubmissionRepository(
          mongoComponent = mongoComponent,
          appConfig = mockConfigWithEncryption,
          clock = clock
        )

        val setResult = repositoryWithEncryption.insert(testSubmissionDetailsCache).value.futureValue

        setResult mustBe Right(true)

        val record = repositoryWithEncryption.collection
          .find[BsonDocument](Filters.equal("_id", testSubmissionDetailsCache._id.value))
          .headOption()
          .futureValue
          .value

        val rawCarfId               = record.get("carfId").asString().getValue
        val rawFileName             = record.get("fileName").asString().getValue
        val rawExtractedFileDetails = record.get("extractedFileDetails").asString().getValue
        val rawRcaspDetails         = record.get("rcaspDetails").asString().getValue
        val rawSubscriptionDetails  = record.get("subscriptionDetails").asString().getValue
        val rawValidationErrors     = record.get("businessRuleErrors").asString().getValue

        val decryptedCarfId               = crypto.decrypt(Crypted(rawCarfId)).value
        val decryptedFileName             = crypto.decrypt(Crypted(rawFileName)).value
        val decryptedExtractedFileDetails = crypto.decrypt(Crypted(rawExtractedFileDetails)).value
        val decryptedRcaspDetails         = crypto.decrypt(Crypted(rawRcaspDetails)).value
        val decryptedSubscriptionDetails  = crypto.decrypt(Crypted(rawSubscriptionDetails)).value
        val decryptedValidationErrors     = crypto.decrypt(Crypted(rawValidationErrors)).value

        Json.parse(decryptedCarfId)               mustBe Json.toJson(testSubmissionDetailsCache.carfId)
        Json.parse(decryptedFileName)             mustBe Json.toJson(testSubmissionDetailsCache.fileName)
        Json.parse(decryptedExtractedFileDetails) mustBe Json.toJson(testSubmissionDetailsCache.extractedFileDetails)
        Json.parse(decryptedRcaspDetails)         mustBe Json.toJson(testSubmissionDetailsCache.rcaspDetails)
        Json.parse(decryptedSubscriptionDetails)  mustBe Json.toJson(testSubmissionDetailsCache.subscriptionDetails)
        Json.parse(decryptedValidationErrors)     mustBe Json.toJson(testSubmissionDetailsCache.businessRuleErrors)
      }

      "must persist the data in plain format when encryption is disabled" in {
        val setResult = repository.insert(testSubmissionDetailsCache).value.futureValue

        setResult mustBe Right(true)

        val record = repository.collection
          .find[BsonDocument](Filters.equal("_id", testSubmissionDetailsCache._id.value))
          .headOption()
          .futureValue
          .value

        val rawCarfId               = record.get("carfId").asString().getValue
        val rawFileName             = record.get("fileName").asString().getValue
        val rawExtractedFileDetails = record.get("extractedFileDetails").asDocument()
        val rawRcaspDetails         = record.get("rcaspDetails").asDocument()
        val rawSubscriptionDetails  = record.get("subscriptionDetails").asDocument()
        val rawValidationErrors     = record.get("businessRuleErrors").asDocument()

        rawCarfId                                  mustBe testSubmissionDetailsCache.carfId.value
        rawFileName                                mustBe testSubmissionDetailsCache.fileName.value
        Json.parse(rawExtractedFileDetails.toJson) mustBe Json.toJson(testSubmissionDetailsCache.extractedFileDetails)
        Json.parse(rawRcaspDetails.toJson)         mustBe Json.toJson(testSubmissionDetailsCache.rcaspDetails)
        Json.parse(rawSubscriptionDetails.toJson)  mustBe Json.toJson(testSubmissionDetailsCache.subscriptionDetails)
        Json.parse(rawValidationErrors.toJson)     mustBe Json.toJson(testSubmissionDetailsCache.businessRuleErrors)
      }
    }

    ".updateStatus" - {
      "must successfully update the fileStatus and lastStatusUpdateTime when new status is NOT Rejected" in {
        repository.insert(testSubmissionDetailsCache).value.futureValue mustBe Right(true)

        val updateResult = repository.updateStatus(testUploadId, Accepted).value.futureValue
        updateResult mustBe Right(true)

        val record = find(Filters.equal("_id", testSubmissionDetailsCache._id.value)).futureValue.head

        record.fileStatus           mustBe Accepted
        record.lastStatusUpdateTime mustBe clock.instant()
      }

      "must return a BusinessError when attempting to update with a Rejected status" in {
        val uploadId     = testSubmissionDetailsCache._id
        val updateResult = repository.updateStatus(uploadId, Rejected).value.futureValue

        updateResult match {
          case Left(BusinessError(message)) =>
            message mustBe "[SubmissionRepository][updateStatus] Error updateStatus called with rejected status in SubmissionRepository .updateStatus"
          case _                            => fail("Expected a BusinessError")
        }
      }
    }

    ".updateStatusWithErrors" - {
      "must successfully update fileStatus, lastStatusUpdateTime, and businessRuleErrors when new status is Rejected" in {
        repository.insert(testSubmissionDetailsCache).value.futureValue mustBe Right(true)

        val uploadId = testSubmissionDetailsCache._id

        val updateResult = repository
          .updateStatusWithErrors(
            uploadId,
            Rejected,
            businessRuleValidationErrors
          )
          .value
          .futureValue

        updateResult mustBe Right(true)

        val record = find(Filters.equal("_id", uploadId.value)).futureValue.head

        record.fileStatus           mustBe Rejected
        record.businessRuleErrors   mustBe businessRuleValidationErrors
        record.lastStatusUpdateTime mustBe clock.instant()
      }

      "must persist BusinessErrors in encrypted format when encryption is enabled" in {
        val mockConfigWithEncryption = mock[AppConfig]
        when(mockConfigWithEncryption.cacheTtl) thenReturn 1L
        when(mockConfigWithEncryption.mongoEncryptionEnabled) thenReturn true

        val repositoryWithEncryption = new SubmissionRepository(
          mongoComponent = mongoComponent,
          appConfig = mockConfigWithEncryption,
          clock = clock
        )

        repositoryWithEncryption.insert(testSubmissionDetailsCache).value.futureValue mustBe Right(true)

        val uploadId = testSubmissionDetailsCache._id

        val updateResult = repositoryWithEncryption
          .updateStatusWithErrors(
            uploadId,
            Rejected,
            businessRuleValidationErrors
          )
          .value
          .futureValue

        updateResult mustBe Right(true)

        val record = repositoryWithEncryption.collection
          .find[BsonDocument](Filters.equal("_id", uploadId.value))
          .headOption()
          .futureValue
          .value

        val rawValidationErrors = record.get("businessRuleErrors").asString().getValue

        val decryptedValidationErrors = crypto.decrypt(Crypted(rawValidationErrors)).value

        Json.parse(decryptedValidationErrors) mustBe Json.toJson(businessRuleValidationErrors)
      }

      "must persist BusinessErrors in plain format when encryption is disabled" in {
        repository.insert(testSubmissionDetailsCache).value.futureValue mustBe Right(true)

        val uploadId = testSubmissionDetailsCache._id

        val updateResult = repository
          .updateStatusWithErrors(
            uploadId,
            Rejected,
            businessRuleValidationErrors
          )
          .value
          .futureValue

        updateResult mustBe Right(true)

        val record = repository.collection
          .find[BsonDocument](Filters.equal("_id", uploadId.value))
          .headOption()
          .futureValue
          .value

        val rawValidationErrors = record.get("businessRuleErrors").asDocument()

        Json.parse(rawValidationErrors.toJson) mustBe Json.toJson(businessRuleValidationErrors)
      }

      "must return a BusinessError when attempting to update with a status other than Rejected" in {
        val uploadId = testSubmissionDetailsCache._id

        val updateResult =
          repository.updateStatusWithErrors(uploadId, VirusFound, businessRuleValidationErrors).value.futureValue

        updateResult match {
          case Left(BusinessError(message)) =>
            message mustBe "[SubmissionRepository][updateStatusWithErrors] Error updateStatusWithErrors called without rejected status in SubmissionRepository .updateStatusWithErrors"
          case _                            => fail("Expected a BusinessError")
        }
      }
    }

    ".findByUploadId" - {
      "when there is a record for the uploadId" in {
        insert(testSubmissionDetailsCache).futureValue

        val result = repository.findByUploadId(testUploadId).value.futureValue

        result mustBe Right(Some(testSubmissionDetailsCache))
      }

      "when there is no record for the uploadId" in {
        repository.findByUploadId(UploadId("abc")).value.futureValue mustBe Right(None)
      }
    }

    ".findByCarfId" - {
      "when there are records for the carfId, returns the records in order of descending submissionTime" in {
        val testSubmissionDetailsCache2 =
          testSubmissionDetailsCache.copy(
            _id = UploadId("987654"),
            fileStatus = Rejected,
            submissionTime = Instant.ofEpochSecond(3)
          )

        val testSubmissionDetailsCache3 =
          testSubmissionDetailsCache.copy(
            _id = UploadId("222222"),
            fileStatus = Accepted,
            submissionTime = Instant.ofEpochSecond(2)
          )

        insert(testSubmissionDetailsCache).futureValue
        insert(testSubmissionDetailsCache2).futureValue
        insert(testSubmissionDetailsCache3).futureValue

        val result = repository.findByCarfId(testCarfRef).value.futureValue

        result mustBe Right(Seq(testSubmissionDetailsCache2, testSubmissionDetailsCache3, testSubmissionDetailsCache))
      }

      "when there are no records for the carfId" in {
        repository.findByCarfId(testCarfRef).value.futureValue mustBe Right(Seq.empty)
      }
    }
  }
}
