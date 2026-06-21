/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.controller.session

interface PresentationSessionController {
    fun setSessionId(value: String)
    fun getSessionId(): String
    fun clearSessionId(value: String)
}

class PresentationSessionControllerImpl : PresentationSessionController {

    private val lock = Any()
    private var sessionId: String = ""

    override fun setSessionId(value: String) {
        synchronized(lock) {
            sessionId = value
        }
    }

    override fun getSessionId(): String {
        return synchronized(lock) {
            sessionId
        }
    }

    override fun clearSessionId(value: String) {
        synchronized(lock) {
            if (sessionId == value) {
                sessionId = ""
            }
        }
    }
}
