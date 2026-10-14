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

package uk.gov.hmrc.carfreporting.services.submission

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.carfreporting.connectors.SubmissionHistoryConnector
import uk.gov.hmrc.carfreporting.models.errors.ApiError.InternalServerError
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryRecord
import uk.gov.hmrc.carfreporting.models.responses.SubmissionHistoryStatus.{Failed, Pending}
import uk.gov.hmrc.carfreporting.models.submission.SubmissionHistoryPassed
import uk.gov.hmrc.carfreporting.types.ResultT

import java.time.Instant
import java.time.temporal.ChronoUnit

class SubmissionHistoryServiceSpec extends SpecBase {

  val mockSubmissionHistoryConnector: SubmissionHistoryConnector = mock[SubmissionHistoryConnector]
  val mockAppConfig: AppConfig                                   = mock[AppConfig]

  val service = new SubmissionHistoryService(mockSubmissionHistoryConnector, clock, mockAppConfig)

  when(mockAppConfig.submissionTtlDays).thenReturn(28L)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionHistoryConnector)
  }

  "SubmissionHistoryService" - {
    ".getSubmissionHistory" - {
      "must return an empty list" - {
        "when the connector returns no records" in {
          when(mockSubmissionHistoryConnector.getSubmissionHistory(any())(any()))
            .thenReturn(ResultT.fromValue(Seq.empty[SubmissionHistoryRecord]))

          val result = service.getSubmissionHistory(testCarfRef).value.futureValue

          result mustBe Right(Seq.empty[SubmissionHistoryPassed])

          verify(mockSubmissionHistoryConnector, times(1)).getSubmissionHistory(eqTo(submissionHistoryRequest))(any())
        }

        "when there are only records for recent files and files not passed" in {
          val submissionHistoryRecords = Seq(
            submissionHistoryRecord.copy(uploadDateTime = Instant.now(clock).minus(1, ChronoUnit.DAYS)),
            submissionHistoryRecord
              .copy(submissionStatus = Failed, uploadDateTime = Instant.now(clock).minus(2, ChronoUnit.DAYS)),
            submissionHistoryRecord.copy(submissionStatus = Pending)
          )

          when(mockSubmissionHistoryConnector.getSubmissionHistory(any())(any()))
            .thenReturn(ResultT.fromValue(submissionHistoryRecords))

          val result = service.getSubmissionHistory(testCarfRef).value.futureValue

          result mustBe Right(Seq.empty[SubmissionHistoryPassed])

          verify(mockSubmissionHistoryConnector, times(1)).getSubmissionHistory(eqTo(submissionHistoryRequest))(any())
        }
      }

      "must return a sorted list of SubmissionHistoryPassed, filtering away records for recent files and files not passed" in {
        val submissionHistoryRecords = Seq(
          submissionHistoryRecord.copy(uploadDateTime = Instant.now(clock).minus(1, ChronoUnit.DAYS)),
          submissionHistoryRecord.copy(uploadDateTime = Instant.now(clock).minus(2, ChronoUnit.DAYS))
        ) ++ submissionHistoryRecordList(5).reverse ++ Seq(
          submissionHistoryRecord.copy(submissionStatus = Pending),
          submissionHistoryRecord.copy(submissionStatus = Failed)
        )

        when(mockSubmissionHistoryConnector.getSubmissionHistory(any())(any()))
          .thenReturn(ResultT.fromValue(submissionHistoryRecords))

        val result = service.getSubmissionHistory(testCarfRef).value.futureValue

        result mustBe Right(submissionHistoryPassedList(5))

        verify(mockSubmissionHistoryConnector, times(1)).getSubmissionHistory(eqTo(submissionHistoryRequest))(any())
      }

      "must return an error when the connector returns an error" in {
        when(mockSubmissionHistoryConnector.getSubmissionHistory(any())(any()))
          .thenReturn(ResultT.fromError(InternalServerError))

        val result = service.getSubmissionHistory(testCarfRef).value.futureValue

        result mustBe Left(InternalServerError)

        verify(mockSubmissionHistoryConnector, times(1)).getSubmissionHistory(eqTo(submissionHistoryRequest))(any())
      }
    }
  }
}
