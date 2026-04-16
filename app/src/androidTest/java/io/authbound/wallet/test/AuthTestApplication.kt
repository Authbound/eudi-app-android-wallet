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

package io.authbound.wallet.test

import org.koin.core.module.Module

class AuthTestApplication : eu.europa.ec.assemblylogic.Application() {

    override fun additionalKoinModules(): List<Module> = listOf(authTestModule)

    override fun allowKoinOverride(): Boolean = true

    override fun shouldInitializeRqes(): Boolean = false

    override fun shouldInitializeReporting(): Boolean = false

    override fun shouldInitializeRevocationWorkManager(): Boolean = false

    override fun shouldInitializeAppLockLifecycleObserver(): Boolean = false
}
