package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.passimport.FetchedLink
import com.erfangholami.solidshare.data.passimport.TicketLinkFetcher
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.presentation.wallet.TicketImportViewModel.ImportState
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TicketImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val holder = TicketImportHolder()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val ticketsRepository = mockk<TicketsRepository>()
    private val linkFetcher = mockk<TicketLinkFetcher>()
    private val stringProvider = mockk<StringProvider>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TicketImportViewModel(
        authRepository = authRepository,
        ticketsRepository = ticketsRepository,
        importHolder = holder,
        stringProvider = stringProvider,
        linkFetcher = linkFetcher,
    )

    @Test
    fun `nothing stashed shows not found`() {
        val vm = viewModel()
        vm.load()
        assertEquals(ImportState.NotFound(), vm.state.value)
    }

    @Test
    fun `a stashed link is fetched and parsed like a file`() = runTest(dispatcher) {
        holder.stashLink("https://vendor.example/pass")
        val bytes = byteArrayOf(1, 2)
        val draft = TicketDraft(title = "Bus to Paris")
        coEvery { linkFetcher.fetch("https://vendor.example/pass") } returns
            FetchedLink(bytes, "pass.pkpass")
        coEvery { ticketsRepository.parseTicketFile(bytes, "pass.pkpass") } returns
            (draft to TicketFile("application/vnd.apple.pkpass", bytes))

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.Found(draft), vm.state.value)
    }

    @Test
    fun `a link that cannot be downloaded shows the link-failed state`() = runTest(dispatcher) {
        holder.stashLink("https://vendor.example/gone")
        coEvery { linkFetcher.fetch(any()) } returns null

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.NotFound(linkFailed = true), vm.state.value)
    }

    @Test
    fun `fetched content that is not a ticket shows plain not found`() = runTest(dispatcher) {
        holder.stashLink("https://vendor.example/page")
        coEvery { linkFetcher.fetch(any()) } returns FetchedLink(byteArrayOf(1), null)
        coEvery { ticketsRepository.parseTicketFile(any(), any()) } returns null

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.NotFound(linkFailed = false), vm.state.value)
    }
}
