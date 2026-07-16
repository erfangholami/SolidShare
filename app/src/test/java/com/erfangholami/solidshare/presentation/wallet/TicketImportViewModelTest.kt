package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.ParsedTicketFile
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
    fun `a stashed pkpass file is parsed and shown`() = runTest(dispatcher) {
        val bytes = byteArrayOf(1, 2)
        val draft = TicketDraft(title = "Bus to Paris")
        holder.stashImport(bytes, "pass.pkpass")
        coEvery { ticketsRepository.parseTicketFile(bytes, "pass.pkpass") } returns
            ParsedTicketFile(draft, TicketFile("application/vnd.apple.pkpass", bytes))

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.Found(draft), vm.state.value)
    }

    @Test
    fun `content that is not a pass shows not found`() = runTest(dispatcher) {
        holder.stashImport(byteArrayOf(9), "junk.bin")
        coEvery { ticketsRepository.parseTicketFile(any(), any()) } returns null

        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertEquals(ImportState.NotFound, vm.state.value)
    }
}
