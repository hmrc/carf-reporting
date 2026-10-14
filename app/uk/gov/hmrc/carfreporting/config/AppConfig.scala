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

package uk.gov.hmrc.carfreporting.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  final val appName: String = config.get[String]("appName")

  val bearerToken: String => String =
    (serviceName: String) => config.get[String](s"microservice.services.$serviceName.bearer-token")

  val environment: String => String =
    (serviceName: String) => config.get[String](s"microservice.services.$serviceName.environment")

  val cacheTtl: Long = config.get[Long]("mongodb.upscanTimeToLiveInSeconds")

  private val sdesBaseUrl: String = servicesConfig.baseUrl("sdes")
  val sdesUrl: String             = s"$sdesBaseUrl/${config.get[String]("sdes.url")}"

  val sdesInformationType: String = s"${config.get[String]("sdes.informationType")}"
  val sdesClientId: String        = s"${config.get[String]("sdes.client-id")}"
  val sdesSrn: String             = s"${config.get[String]("sdes.srn")}"

  private val sdesFileListingHost: String = servicesConfig.baseUrl("sdes-file-listing")
  val sdesFileListingBaseUrl: String      =
    s"$sdesFileListingHost${config.get[String]("microservice.services.sdes-file-listing.uri")}"

  val submissionTtlDays: Long = config.get[Long]("mongodb.submissionTimeToLiveInDays")

  private val submissionHistoryHost: String = servicesConfig.baseUrl("submission-history")
  val submissionHistoryBaseUrl: String      =
    s"$submissionHistoryHost${config.get[String]("microservice.services.submission-history.uri")}"

  val submittedFilesPageSize: Int = config.get[Int]("submitted-files.page-size")
}
