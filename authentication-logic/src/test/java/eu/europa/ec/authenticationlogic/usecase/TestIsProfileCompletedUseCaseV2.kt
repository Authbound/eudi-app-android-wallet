/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.authenticationlogic.repository.ProfileApiException
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [IsProfileCompletedUseCaseV2Impl].
 *
 * Tests cache logic, backend verification, retry behavior, and error handling.
 * These tests validate the resilient profile completion check used during app startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestIsProfileCompletedUseCaseV2 {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var profileRepository: ProfileRepository

    @Mock
    private lateinit var logController: LogController

    private lateinit var useCase: IsProfileCompletedUseCaseV2Impl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = IsProfileCompletedUseCaseV2Impl(
            prefKeys = prefKeys,
            profileRepository = profileRepository,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    //region Cache Logic Tests

    // Case 1:
    // Cache says profile is completed.
    // Expected: Return true immediately, no backend call.
    @Test
    fun `Given cache is true, When invoke is called, Then returns true without backend call`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(true)

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should return true when cache is true", result)
            verify(profileRepository, never()).getMyProfile()
        }

    // Case 2:
    // Cache miss, backend returns complete profile.
    // Expected: Caches result and returns true.
    @Test
    fun `Given cache miss and backend returns complete profile, When invoke is called, Then caches and returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(Result.success(COMPLETE_PROFILE))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should return true when backend confirms complete profile", result)
            verify(prefKeys, times(1)).setProfileCompleted(true)
        }

    // Case 3:
    // Cache miss, backend returns null/failure.
    // Expected: Returns false (profile not completed).
    @Test
    fun `Given cache miss and backend returns null, When invoke is called, Then returns false`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(
                Result.failure(Exception("Network error"))
            )

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false when backend fails", result)
        }

    //endregion

    //region Profile Validation Tests

    // Case 4:
    // Profile missing handle (null or blank).
    // Expected: Retries 3x, returns false (incomplete profile).
    @Test
    fun `Given profile has blank handle, When invoke is called, Then retries and returns false`() =
        coroutineRule.runTest {
            // Given
            val incompleteProfile = Profile(
                id = "user-123",
                handle = "", // Blank handle
                displayName = "John Doe"
            )
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(Result.success(incompleteProfile))

            // When
            val result = useCase.invoke()

            // Then - Should retry 3 times then return false
            assertFalse("Should return false for incomplete profile", result)
            verify(profileRepository, times(3)).getMyProfile()
        }

    // Case 5:
    // Profile has blank displayName.
    // Expected: Retries 3x, returns false.
    @Test
    fun `Given profile has blank displayName, When invoke is called, Then retries and returns false`() =
        coroutineRule.runTest {
            // Given
            val incompleteProfile = Profile(
                id = "user-123",
                handle = "john_doe",
                displayName = "" // Blank display name
            )
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(Result.success(incompleteProfile))

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false for profile with blank displayName", result)
            verify(profileRepository, times(3)).getMyProfile()
        }

    //endregion

    //region Retry Logic Tests

    // Case 6:
    // Backend fails twice then succeeds on third attempt.
    // Expected: Succeeds after retries.
    @Test
    fun `Given backend fails twice then succeeds, When invoke is called, Then succeeds on 3rd attempt`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile())
                .thenReturn(Result.failure(Exception("Attempt 1 failed")))
                .thenReturn(Result.failure(Exception("Attempt 2 failed")))
                .thenReturn(Result.success(COMPLETE_PROFILE))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should succeed after retries", result)
            verify(profileRepository, times(3)).getMyProfile()
        }

    // Case 7:
    // All 3 retry attempts fail.
    // Expected: Returns false, logs warning.
    @Test
    fun `Given all 3 retries fail, When invoke is called, Then returns false`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile())
                .thenReturn(Result.failure(Exception("Network error")))

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false after all retries fail", result)
            verify(profileRepository, times(3)).getMyProfile()
        }

    // Case 8:
    // Cache write fails after successful backend verification.
    // Expected: Still returns true (cache failure doesn't affect result).
    @Test
    fun `Given cache write fails, When invoke is called, Then still returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(Result.success(COMPLETE_PROFILE))
            whenever(prefKeys.setProfileCompleted(any())).thenThrow(RuntimeException("Cache write failed"))

            // When
            val result = useCase.invoke()

            // Then - Should still return true despite cache failure
            assertTrue("Should return true even if cache write fails", result)
        }

    //endregion

    //region Network Failure Fallback Tests

    // Case 9:
    // Network fails but cache indicates profile was completed.
    // This case is already covered by Case 1 (cache true returns immediately).
    // However, let's test the explicit NetworkError path for completeness.
    // Note: With current implementation, cache=true returns early before network call.
    // This test verifies that behavior is correct.
    @Test
    fun `Given cache is true, When network would fail, Then returns true from cache before network call`() =
        coroutineRule.runTest {
            // Given - Cache says complete, so we never hit network
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(true)
            // Even if we configured network to fail, it should not be called
            whenever(profileRepository.getMyProfile())
                .thenReturn(Result.failure(Exception("Should not be called")))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should return true from cache", result)
            verify(profileRepository, never()).getMyProfile()
        }

    // Case 10:
    // Network fails and cache is false.
    // Expected: Returns false (network error with cached value false).
    @Test
    fun `Given network fails and cache is false, When invoke is called, Then returns false`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile())
                .thenReturn(Result.failure(Exception("Network unavailable")))

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false when network fails and cache is false", result)
        }

    //endregion

    //region Edge Cases

    // Case 11:
    // Complete profile returned on first attempt.
    // Expected: Returns true, only 1 backend call.
    @Test
    fun `Given complete profile on first attempt, When invoke is called, Then returns true with single call`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(Result.success(COMPLETE_PROFILE))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should return true", result)
            verify(profileRepository, times(1)).getMyProfile()
        }

    //endregion

    //region Server Error Tests (ProfileApiException)

    // Case 12:
    // Backend returns HTTP 400 (e.g., race condition duplicate key error).
    // Expected: Returns false (cannot verify profile), no retries for 4xx.
    @Test
    fun `Given backend returns HTTP 400, When invoke is called, Then returns false without retry`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(
                Result.failure(ProfileApiException(400, "Bad Request"))
            )

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false on HTTP 400 — cannot verify profile", result)
            verify(profileRepository, times(1)).getMyProfile() // No retries for 4xx
        }

    // Case 13:
    // Backend returns HTTP 500 on all attempts.
    // Expected: Retries 3x, then returns false (cannot verify profile).
    @Test
    fun `Given backend returns HTTP 500, When invoke is called, Then retries and returns false`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile()).thenReturn(
                Result.failure(ProfileApiException(500, "Internal Server Error"))
            )

            // When
            val result = useCase.invoke()

            // Then
            assertFalse("Should return false on HTTP 500 — cannot verify profile", result)
            verify(profileRepository, times(3)).getMyProfile() // Retries for 5xx
        }

    // Case 14:
    // Backend returns HTTP 500 twice, then succeeds on third attempt.
    // Expected: Returns true, caches result.
    @Test
    fun `Given HTTP 500 twice then success, When invoke is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isProfileCompletedSafe()).thenReturn(false)
            whenever(profileRepository.getMyProfile())
                .thenReturn(Result.failure(ProfileApiException(500, "Server Error")))
                .thenReturn(Result.failure(ProfileApiException(500, "Server Error")))
                .thenReturn(Result.success(COMPLETE_PROFILE))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should succeed after retries", result)
            verify(prefKeys).setProfileCompleted(true) // Caches on success
        }

    //endregion

    //region Test Constants

    companion object {
        /**
         * A complete profile with all required fields populated.
         */
        private val COMPLETE_PROFILE = Profile(
            id = "user-123",
            handle = "john_doe",
            displayName = "John Doe"
        )
    }

    //endregion
}
