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

package uk.gov.hmrc.carfreporting.services.upscan

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import uk.gov.hmrc.carfreporting.base.SpecBase
import uk.gov.hmrc.carfreporting.models.upscan.*

import scala.concurrent.Future

class UpscanCallbackDispatcherSpec extends SpecBase {

  val mockUploadProgressTracker: UploadProgressTracker = mock[UploadProgressTracker]

  val upscanCallbackDispatcher = new UpscanCallbackDispatcher(mockUploadProgressTracker)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUploadProgressTracker)
  }

  "UpscanCallbackDispatcher" - {
    ".handleCallback" - {
      "must call UploadProgressTracker with UploadedSuccessfully for the input ReadyCallbackBody" in {
        when(mockUploadProgressTracker.registerUploadResult(any(), any())).thenReturn(Future.successful(true))

        val result: Future[Boolean] = upscanCallbackDispatcher.handleCallback(readyCallbackBody)
        result.futureValue mustBe true

        verify(mockUploadProgressTracker, times(1))
          .registerUploadResult(eqTo(testReference), eqTo(uploadedSuccessfully))
      }

      "must call UploadProgressTracker with UploadRejected for the input FailedCallbackBody with REJECTED" in {
        when(mockUploadProgressTracker.registerUploadResult(any(), any())).thenReturn(Future.successful(true))

        val result: Future[Boolean] = upscanCallbackDispatcher.handleCallback(failedCallbackBody("REJECTED"))
        result.futureValue mustBe true

        verify(mockUploadProgressTracker, times(1)).registerUploadResult(eqTo(testReference), eqTo(uploadRejected))
      }

      "must call UploadProgressTracker with UploadRejected for the input FailedCallbackBody with QUARANTINE" in {
        when(mockUploadProgressTracker.registerUploadResult(any(), any())).thenReturn(Future.successful(true))

        val result: Future[Boolean] = upscanCallbackDispatcher.handleCallback(failedCallbackBody("QUARANTINE"))
        result.futureValue mustBe true

        verify(mockUploadProgressTracker, times(1)).registerUploadResult(eqTo(testReference), eqTo(Quarantined))
      }

      "must call UploadProgressTracker with UploadRejected for the input FailedCallbackBody with another failure" in {
        when(mockUploadProgressTracker.registerUploadResult(any(), any())).thenReturn(Future.successful(true))

        val result: Future[Boolean] = upscanCallbackDispatcher.handleCallback(failedCallbackBody("UNKNOWN"))
        result.futureValue mustBe true

        verify(mockUploadProgressTracker, times(1)).registerUploadResult(eqTo(testReference), eqTo(Failed))
      }
    }
  }
}
