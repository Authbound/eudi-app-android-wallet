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

package eu.europa.ec.assemblylogic.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.businesslogic.controller.session.PresentationSessionController
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.uilogic.di.LogicUiModule
import eu.europa.ec.uilogic.di.module as logicUiModule
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module as koinModule
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = TestMainActivityApplication::class,
)
class TestMainActivity {

    private lateinit var localUnlockTracker: FakeLocalUnlockTracker

    @Before
    fun before() {
        localUnlockTracker = FakeLocalUnlockTracker()
        startKoin {
            modules(
                LogicUiModule().logicUiModule(),
                koinModule {
                    single<LocalUnlockTracker> { localUnlockTracker }
                    single<PresentationSessionController> { FakePresentationSessionController() }
                    single<UuidProvider> { FakeUuidProvider() }
                }
            )
        }
    }

    @After
    fun after() {
        stopKoin()
    }

    @Test
    fun `Given activity is not backgrounded, When it starts locked, Then lock state is not rechecked`() {
        val controller = buildActivity()

        controller.create().start()

        assertThat(localUnlockTracker.isUnlockedChecks).isEqualTo(0)
        assertThat(Shadows.shadowOf(controller.get()).peekNextStartedActivityForResult()).isNull()
        assertThat(controller.get().isFinishing).isFalse()
    }

    @Test
    fun `Given activity was backgrounded and remains unlocked, When it starts again, Then it does not restart`() {
        localUnlockTracker.unlocked = true
        val controller = buildActivity()

        controller.create().start()
        controller.stop()
        controller.start()

        assertThat(localUnlockTracker.isUnlockedChecks).isEqualTo(1)
        assertThat(Shadows.shadowOf(controller.get()).peekNextStartedActivityForResult()).isNull()
        assertThat(controller.get().isFinishing).isFalse()
    }

    @Test
    fun `Given activity was backgrounded and is locked, When it starts again, Then it restarts and preserves credential offer deep link`() {
        localUnlockTracker.unlocked = false
        val expectedDeepLink = Uri.parse("openid-credential-offer://test-offer")
        val controller = buildActivity()

        controller.create().start()
        controller.get().setPendingDeepLink(expectedDeepLink)
        controller.stop()
        controller.start()

        val startedIntent = Shadows.shadowOf(controller.get())
            .getNextStartedActivityForResult()
            .intent

        assertThat(localUnlockTracker.isUnlockedChecks).isEqualTo(1)
        assertThat(startedIntent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(startedIntent.data).isEqualTo(expectedDeepLink)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK).isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertThat(controller.get().isFinishing).isTrue()
    }

    @Test
    fun `Given activity was backgrounded and is locked, When it starts again, Then it restarts and preserves DC API intent`() {
        localUnlockTracker.unlocked = false
        val expectedAction: String = "androidx.identitycredentials.action.get_credentials"
        val expectedExtra: String = "provider-request"
        val controller: ActivityController<TestableMainActivity> = buildActivity()

        controller.create().start()
        controller.get().setPendingIntent(
            Intent(expectedAction).putExtra("request_id", expectedExtra)
        )
        controller.stop()
        controller.start()

        val startedIntent: Intent = Shadows.shadowOf(controller.get())
            .getNextStartedActivityForResult()
            .intent

        assertThat(localUnlockTracker.isUnlockedChecks).isEqualTo(1)
        assertThat(startedIntent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(startedIntent.action).isEqualTo(expectedAction)
        assertThat(startedIntent.getStringExtra("request_id")).isEqualTo(expectedExtra)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK).isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertThat(controller.get().isFinishing).isTrue()
    }

    private fun buildActivity(): ActivityController<TestableMainActivity> {
        return Robolectric.buildActivity(TestableMainActivity::class.java)
    }
}

private class FakeLocalUnlockTracker(
    var unlocked: Boolean = true,
) : LocalUnlockTracker {

    var isUnlockedChecks: Int = 0

    override suspend fun markUnlocked(ttlMillis: Long) = Unit

    override suspend fun lockNow() = Unit

    override fun isUnlocked(): Boolean {
        isUnlockedChecks += 1
        return unlocked
    }
}

private class FakePresentationSessionController : PresentationSessionController {
    private var sessionId: String = ""

    override fun setSessionId(value: String) {
        sessionId = value
    }

    override fun getSessionId(): String {
        return sessionId
    }

    override fun clearSessionId(value: String) {
        if (sessionId == value) {
            sessionId = ""
        }
    }
}

private class FakeUuidProvider : UuidProvider {
    override fun provideUuid(): String {
        return "test-session-id"
    }
}

private class TestableMainActivity : MainActivity() {
    override fun initializeActivityUi(startIntent: Intent?) = Unit

    fun setPendingDeepLink(uri: Uri?) {
        cacheDeepLink(uri)
    }

    fun setPendingIntent(intent: Intent?) {
        cacheIntent(intent)
    }
}

class TestMainActivityApplication : Application()
