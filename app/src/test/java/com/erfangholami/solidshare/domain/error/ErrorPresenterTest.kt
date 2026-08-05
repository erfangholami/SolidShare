package com.erfangholami.solidshare.domain.error

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorPresenterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val presenter: ErrorPresenter = run {
        val monitor = mockk<NetworkMonitor>()
        every { monitor.currentlyOnline() } returns true
        ErrorPresenter(AppErrorMapper(monitor), StringProvider(context))
    }

    @Test
    fun `the headline says what was attempted and the message says why it failed`() {
        val error = presenter.present(
            AppError.ServerUnreachable("solidcommunity.net"),
            AppOperation.CREATE_FOLDER,
        )

        assertEquals("Couldn't create the folder", error.title)
        assertTrue(error.message.contains("solidcommunity.net"))
        assertEquals("Couldn't create the folder. ${error.message}", error.summary)
    }

    @Test
    fun `an unknown host still reads as a sentence`() {
        val error = presenter.present(AppError.Timeout(null), AppOperation.LOAD_FILES)

        assertTrue(error.message, error.message.startsWith("your pod took too long"))
    }

    @Test
    fun `refusing to share points at ownership, not at requesting read access`() {
        val error = presenter.present(
            AppError.PermissionDenied(RESOURCE, OWNER),
            AppOperation.CREATE_SHARE,
        )

        assertEquals("Only the owner of this item can change who has access to it.", error.message)
        assertNull(error.action)
    }

    @Test
    fun `being refused a shared item offers to ask its owner`() {
        val error = presenter.present(
            AppError.PermissionDenied(RESOURCE, OWNER),
            AppOperation.ADD_RECEIVED_SHARE,
        )

        assertEquals(ErrorAction.RequestAccess(RESOURCE, OWNER), error.action)
        assertTrue(error.message.contains(OWNER))
    }

    @Test
    fun `a denial with no known owner never offers a request that cannot be sent`() {
        val error = presenter.present(AppError.PermissionDenied(), AppOperation.OPEN_FOLDER)

        assertNull(error.action)
    }

    @Test
    fun `retryable causes offer a retry and non-retryable ones do not`() {
        assertEquals(
            ErrorAction.Retry,
            presenter.present(AppError.RateLimited("pod.example"), AppOperation.LOAD_FILES).action,
        )
        assertNull(
            presenter.present(AppError.Conflict("pod.example"), AppOperation.CREATE_FOLDER).action,
        )
    }

    @Test
    fun `a screen with nothing to retry with is not given a dead button`() {
        val error = presenter.present(
            AppError.Offline,
            AppOperation.LOAD_FILES,
            allowRetry = false,
        )

        assertNull(error.action)
    }

    @Test
    fun `a dead session sends the user to sign in`() {
        val error = presenter.present(AppError.SessionExpired("inrupt.net"), AppOperation.LOAD_FILES)

        assertEquals(ErrorAction.SignIn, error.action)
        assertTrue(error.message.contains("inrupt.net"))
    }

    @Test
    fun `sign-in failures do not tell the user to sign in`() {
        val error = presenter.present(AppError.SignInRequired, AppOperation.SIGN_IN)

        assertEquals("Sign-in didn't complete. Try again.", error.message)
        assertEquals(ErrorAction.Retry, error.action)
    }

    @Test
    fun `a mistyped provider is named as such rather than as a parse failure`() {
        val error = presenter.present(AppError.UnreadableData("exmaple.com"), AppOperation.SIGN_IN)

        assertTrue(error.message, error.message.contains("Solid identity provider"))
    }

    @Test
    fun `a missing subject falls back to a phrase rather than a blank`() {
        val named = presenter.present(AppError.NotFound(), AppOperation.OPEN_FILE, "budget.pdf")
        val unnamed = presenter.present(AppError.NotFound(), AppOperation.OPEN_FILE)

        assertTrue(named.message.startsWith("budget.pdf isn't on the pod"))
        assertTrue(unnamed.message.startsWith("This item isn't on the pod"))
    }

    @Test
    fun `a throwable never reaches the user as its own message`() {
        val error = presenter.present(
            IllegalStateException("Caused by: retrofit2.HttpException 500"),
            AppOperation.UPLOAD_FILE,
        )

        assertFalse(error.message.contains("retrofit2"))
        assertFalse(error.summary.contains("HttpException"))
        assertEquals("IllegalStateException: Caused by: retrofit2.HttpException 500", error.diagnostic)
    }

    @Test
    fun `every cause has a finished sentence for every operation`() {
        val causes = listOf(
            AppError.Offline,
            AppError.ServerUnreachable("pod.example"),
            AppError.Timeout("pod.example"),
            AppError.InsecureConnection("pod.example"),
            AppError.RequiresConnection,
            AppError.SignInRequired,
            AppError.SessionExpired("pod.example"),
            AppError.NoActiveAccount,
            AppError.NoPodStorage("pod.example"),
            AppError.PermissionDenied(RESOURCE, OWNER),
            AppError.PermissionDenied(),
            AppError.AccessUnverified(RESOURCE),
            AppError.NotFound(RESOURCE),
            AppError.Conflict("pod.example"),
            AppError.ChangedElsewhere("pod.example"),
            AppError.SharingChangedElsewhere("pod.example"),
            AppError.OperationNotAllowed("pod.example"),
            AppError.UnsupportedAccessControl("pod.example", "acp"),
            AppError.RateLimited("pod.example"),
            AppError.ServerProblem("pod.example", 503),
            AppError.ServerProblem("pod.example", null),
            AppError.UnexpectedResponse("pod.example", 418),
            AppError.UnexpectedResponse("pod.example", null),
            AppError.TooLarge("pod.example"),
            AppError.PodStorageFull("pod.example"),
            AppError.DeviceStorageFull,
            AppError.UnreadableData("pod.example"),
            AppError.RecipientUnreachable(OWNER),
            AppError.RecipientUnreachable(),
            AppError.RecipientInboxRefused(OWNER),
            AppError.NotificationNotDelivered(OWNER),
            AppError.AuthenticityCheckFailed,
            AppError.Cancelled,
            AppError.LocalFileUnavailable(),
            AppError.InvalidInput(),
            AppError.Unexpected("boom"),
        )

        for (operation in AppOperation.entries) {
            for (cause in causes) {
                val error = presenter.present(cause, operation)
                val where = "$operation / ${cause::class.simpleName}"

                assertTrue("$where has a blank headline", error.title.isNotBlank())
                assertTrue("$where has a blank message", error.message.isNotBlank())
                assertFalse("$where leaked a format specifier", error.message.contains("%"))
                assertFalse("$where leaked a class name", error.message.contains("AppError"))
                assertTrue("$where does not end a sentence", error.message.trimEnd().endsWith("."))
            }
        }
    }

    private companion object {
        const val RESOURCE = "https://pod.example/private/notes.ttl"
        const val OWNER = "https://owner.example/card#me"
    }
}
