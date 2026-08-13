/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.carfreporting.base

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsValue
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import play.api.test.{DefaultAwaitTimeout, FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HeaderCarrier

import java.io.File
import scala.concurrent.ExecutionContext

trait NoGuiceSpecBase
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with DefaultAwaitTimeout
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MockitoSugar {

  private val playConfig = ConfigFactory
    .parseFile(new File("conf/application.test.conf"))
    .withFallback(ConfigFactory.load())

  val actorSystem: ActorSystem = ActorSystem(
    "TestSystem",
    playConfig.resolve()
  )

  val cc: ControllerComponents = stubControllerComponents()

  def fakeRequestWithJsonBody(json: JsValue): FakeRequest[JsValue] = FakeRequest("", "/", FakeHeaders(), json)

  implicit val hc: HeaderCarrier    = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

}
