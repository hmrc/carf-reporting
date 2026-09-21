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

package uk.gov.hmrc.carfreporting.services

import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.{NoGuiceSpecBase, TestData}
import uk.gov.hmrc.carfreporting.repositories.SubmissionRepository
import uk.gov.hmrc.carfreporting.services.submission.{SDESService, SubmissionService}
import uk.gov.hmrc.carfreporting.types.ResultT

class SubmissionServiceSpec extends NoGuiceSpecBase with TestData {

  private val mockSDESService          = mock[SDESService]
  private val mockSubmissionRepository = mock[SubmissionRepository]
  private val submissionService        = new SubmissionService(mockSDESService, mockSubmissionRepository)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSDESService)
    reset(mockSubmissionRepository)
  }

  "SubmissionService" - {
    "saveAndSubmit" - {
      "must save and submit a submission request" in {
        when(mockSubmissionRepository.insert(any())).thenReturn(ResultT.fromValue(true))
        when(mockSDESService.sendNotification(any(), any())(any())).thenReturn(ResultT.fromValue(()))

        val result = submissionService.saveAndSubmit(testSubmissionRequest).value.futureValue

        result mustBe Right(())
      }
    }
  }
}
