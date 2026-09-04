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

object xmlElements {

  object MessageSpec {
    val MESSAGE_SPEC       = "MessageSpec"
    val SENDING_ENTITY_IN  = "SendingEntityIN"
    val MESSAGE_TYPE       = "MessageType"
    val MESSAGE_REF_ID     = "MessageRefId"
    val MESSAGE_TYPE_INDIC = "MessageTypeIndic"
  }

  object CarfBody {
    val CARF_BODY      = "CARFBody"
    val RCASP          = "RCASP"
    val DOC_SPEC       = "DocSpec"
    val DOC_TYPE_INDIC = "DocTypeIndic"
    val CRYPTO_USERS   = "CryptoUsers"
    val OTHER_NEXUS    = "OtherNexus"

    object RcaspName {
      val RCASP_ID   = "RCASP_ID"
      val INDIVIDUAL = "Individual"
      val ENTITY     = "Entity"
      val NAME       = "Name"
      val FIRST_NAME = "FirstName"
      val LAST_NAME  = "LastName"
    }
  }

  object AEOIRequestDetail {
    val REQUEST_DETAIL = "requestDetail"
    val GENERIC_STATUS_MESSAGE = "GenericStatusMessage"
    val VALIDATION_ERRORS = "ValidationErrors"
    val FILE_ERROR = "FileError"
    val RECORD_ERROR = "RecordError"
    val CODE = "Code"
    val DETAILS = "Details"
    val DOC_REF_ID_IN_ERROR = "DocRefIDInError"
    val VALIDATION_RESULT = "ValidationResult"
    val STATUS = "Status"
  }
}
