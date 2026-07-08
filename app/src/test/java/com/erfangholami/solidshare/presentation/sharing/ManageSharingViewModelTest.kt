package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import com.erfangholami.solidshare.data.local.cache.TEST_CONTAINER
import com.erfangholami.solidshare.data.local.cache.TEST_WEB_ID
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "who has access" panel needs the network. When offline it must show a friendly offline state,
 * not surface a raw connection error to the user.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManageSharingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var sharingRepository: SharingRepository
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.activeProfileFlow } returns MutableStateFlow(null)
        coEvery { authRepository.getActiveWebId() } returns TEST_WEB_ID
        sharingRepository = mockk(relaxed = true)
        networkMonitor = mockk(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build() = ManageSharingViewModel(
        stringProvider = mockk<StringProvider>(relaxed = true),
        savedStateHandle = SavedStateHandle(
            mapOf("resourceUri" to TEST_CONTAINER + "f.txt", "canManage" to true, "resourceSubtitle" to null),
        ),
        authRepository = authRepository,
        sharingRepository = sharingRepository,
        networkMonitor = networkMonitor,
    )

    @Test
    fun load_whenOffline_showsOfflineStateNotError() {
        every { networkMonitor.currentlyOnline() } returns false

        // init { load() } — the offline guard resolves synchronously.
        val vm = build()

        assertEquals(ManageSharingViewModel.UiState.Offline, vm.uiState.value)
    }

    @Test
    fun load_whenOnline_loadsSharesInsteadOfOffline() = runTest(dispatcher) {
        every { networkMonitor.currentlyOnline() } returns true
        coEvery { sharingRepository.getGivenSharesForResource(any(), any()) } returns emptyList()

        val vm = build()
        advanceUntilIdle()

        assertTrue(
            "online must reach the loaded state, not the offline one",
            vm.uiState.value is ManageSharingViewModel.UiState.Loaded,
        )
    }
}
