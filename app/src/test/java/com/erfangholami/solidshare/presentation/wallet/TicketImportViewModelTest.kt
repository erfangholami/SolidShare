package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.ParsedTicketFile
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.presentation.wallet.TicketImportViewModel.ImportState
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.coEvery
import io.mockk.coVerify
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
    )

    @Test
    fun `nothing stashed shows not found`() {
        val vm = viewModel()
        vm.load()
        assertEquals(ImportState.NotFound, vm.state.value)
    }

    @Test
    fun `a bundle shows every pass and add-all saves each one`() = runTest(dispatcher) {
        val first = TicketDraft(title = "Leg 1")
        val second = TicketDraft(title = "Leg 2")
        holder.stashImport(byteArrayOf(7), "trip.pkpasses")
        coEvery { ticketsRepository.parseTicketFile(any(), any()) } returns listOf(
            ParsedTicketFile(first, TicketFile("application/vnd.apple.pkpass", byteArrayOf(1))),
            ParsedTicketFile(second, TicketFile("application/vnd.apple.pkpass", byteArrayOf(2))),
        )
        coEvery { authRepository.getActiveWebId() } returns "https://erfan.example/#me"
        coEvery { ticketsRepository.queueCreate(any(), any(), any()) } returns "urn:solidshare:pending:1"

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        val found = vm.state.value as ImportState.Found
        assertEquals(2, found.items.size)

        var closed = false
        vm.addAll { closed = true }
        advanceUntilIdle()

        assertEquals(setOf(0, 1), vm.added.value)
        assertEquals(true, closed)
        coVerify(exactly = 2) { ticketsRepository.queueCreate(any(), any(), any()) }
    }

    @Test
    fun `a stashed pkpass file is parsed and shown`() = runTest(dispatcher) {
        val bytes = byteArrayOf(1, 2)
        val draft = TicketDraft(title = "Bus to Paris")
        holder.stashImport(bytes, "pass.pkpass")
        coEvery { ticketsRepository.parseTicketFile(bytes, "pass.pkpass") } returns
            listOf(ParsedTicketFile(draft, TicketFile("application/vnd.apple.pkpass", bytes)))

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        val found = vm.state.value as ImportState.Found
        assertEquals(listOf(draft), found.items.map { it.draft })
    }

    @Test
    fun `content that is not a pass shows not found`() = runTest(dispatcher) {
        holder.stashImport(byteArrayOf(9), "junk.bin")
        coEvery { ticketsRepository.parseTicketFile(any(), any()) } returns emptyList()

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.NotFound, vm.state.value)
    }
}
