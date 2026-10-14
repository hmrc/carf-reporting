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

package uk.gov.hmrc.carfreporting

import uk.gov.hmrc.carfreporting.config.AppConfig
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HeaderNames}

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID}

package object connectors {

  private lazy val stripSession: String => String = (input: String) => input.replace("session-", "")

  private[connectors] def additionalHeaders(
      config: AppConfig,
      serviceName: String
  )(implicit headerCarrier: HeaderCarrier): Seq[(String, String)] = {
    val authorizationHeaderCarrier = headerCarrier
      .copy(authorization = Some(Authorization(s"Bearer ${config.bearerToken(serviceName)}")))

    authorizationHeaderCarrier.headers(Seq(HeaderNames.authorisation)) ++ addHeaders(
      config.environment(serviceName)
    )
  }

  private def addHeaders(
      eisEnvironment: String
  )(implicit headerCarrier: HeaderCarrier): Seq[(String, String)] = {

    // HTTP-date format defined by RFC 7231 e.g. Fri, 01 Aug 2020 15:51:38 GMT
    val formatter = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
      .withZone(ZoneOffset.UTC)

    Seq(
      "x-forwarded-host"  -> "mdtp",
      "date"              -> ZonedDateTime.now().format(formatter),
      "x-correlation-id"  -> UUID.randomUUID().toString,
      "x-conversation-id" -> {
        headerCarrier.sessionId
          .map(s => stripSession(s.value))
          .getOrElse(UUID.randomUUID().toString)
      },
      "content-type"      -> "application/json",
      "accept"            -> "application/json",
      "Environment"       -> eisEnvironment
    )
  }
}
