package com.erfangholami.solidshare.domain.error

import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.solidshare.util.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorMapperTest {

    private fun mapper(online: Boolean = true): AppErrorMapper {
        val monitor = mockk<NetworkMonitor>()
        every { monitor.currentlyOnline() } returns online
        return AppErrorMapper(monitor)
    }

    @Test
    fun `network failure while the device is offline is reported as offline`() {
        val error = mapper(online = false).map(UnknownHostException("pod.example"))

        assertEquals(AppError.Offline, error)
    }

    @Test
    fun `network failure while the device is online blames the pod, not the connection`() {
        val error = mapper(online = true).map(IOException("connection reset"), RESOURCE)

        assertTrue(error is AppError.ServerUnreachable)
        assertEquals("pod.example", (error as AppError.ServerUnreachable).host)
    }

    @Test
    fun `a library network error is re-decided against live connectivity`() {
        val offline = mapper(online = false).map(SolidError.Network().asException())
        val online = mapper(online = true).map(SolidError.Network().asException(), RESOURCE)

        assertEquals(AppError.Offline, offline)
        assertTrue(online is AppError.ServerUnreachable)
    }

    @Test
    fun `401 is a dead session, not a denial`() {
        val error = mapper().map(SolidError.Unauthorized().asException(), RESOURCE)

        assertEquals(AppError.SessionExpired("pod.example"), error)
    }

    @Test
    fun `access denied carries the resource and its owner so access can be requested`() {
        val error = mapper().map(
            SolidError.AccessDenied(RESOURCE, OWNER).asException(),
        )

        assertEquals(AppError.PermissionDenied(RESOURCE, OWNER, "pod.example"), error)
    }

    @Test
    fun `statuses the library leaves generic are refined into actionable causes`() {
        val m = mapper()

        assertTrue(m.map(SolidError.UnexpectedResponse(413).asException()) is AppError.TooLarge)
        assertTrue(m.map(SolidError.UnexpectedResponse(507).asException()) is AppError.PodStorageFull)
        assertTrue(m.map(SolidError.UnexpectedResponse(408).asException()) is AppError.Timeout)
        assertTrue(m.map(SolidError.UnexpectedResponse(418).asException()) is AppError.UnexpectedResponse)
    }

    @Test
    fun `a stale ACL is distinct from a stale resource`() {
        val acl = mapper().map(SolidError.StaleAcl("https://pod.example/f.acl").asException())
        val resource = mapper().map(SolidError.PreconditionFailed().asException())

        assertTrue(acl is AppError.SharingChangedElsewhere)
        assertTrue(resource is AppError.ChangedElsewhere)
    }

    @Test
    fun `sharing exceptions map without going through the library result type`() {
        val error = mapper().map(SharingException.NoInbox(OWNER))

        assertEquals(AppError.RecipientUnreachable(OWNER), error)
    }

    @Test
    fun `a wrapped cause is still recognised`() {
        val wrapped = IllegalStateException("wrapper", SolidError.Forbidden().asException())

        assertTrue(mapper().map(wrapped) is AppError.PermissionDenied)
    }

    @Test
    fun `an app-raised precondition survives the round trip`() {
        val error = mapper().map(AppError.NoActiveAccount.asException())

        assertEquals(AppError.NoActiveAccount, error)
    }

    @Test
    fun `cancellation is never a failure to report`() {
        assertEquals(AppError.Cancelled, mapper().map(CancellationException("gone")))
    }

    @Test
    fun `local file work is not mistaken for a network problem`() {
        val error = mapper().map(FileNotFoundException("missing"), "content://media/42")

        assertTrue(error is AppError.LocalFileUnavailable)
    }

    @Test
    fun `a full disk is called out rather than buried in an IO error`() {
        val error = mapper().map(IOException("write failed: ENOSPC (No space left on device)"))

        assertEquals(AppError.DeviceStorageFull, error)
    }

    @Test
    fun `timeouts and TLS failures keep their own identity`() {
        val m = mapper()

        assertTrue(m.map(SocketTimeoutException("slow")) is AppError.Timeout)
        assertTrue(m.map(SSLHandshakeException("bad cert")) is AppError.InsecureConnection)
    }

    @Test
    fun `an unrecognised throwable keeps its diagnostic for the log, not the screen`() {
        val error = mapper().map(IllegalStateException("boom"))

        assertTrue(error is AppError.Unexpected)
        assertEquals("IllegalStateException: boom", error.diagnostic)
    }

    @Test
    fun `host extraction survives the shapes pod URLs actually take`() {
        assertEquals("pod.example", hostOf("https://pod.example/private/a b.ttl"))
        assertEquals("pod.example", hostOf("https://www.pod.example/card#me"))
        assertEquals("pod.example", hostOf("https://user@pod.example:8443/x"))
        assertEquals("pod.example", hostOf("https://pod.example"))
        assertEquals("::1", hostOf("http://[::1]:3000/x"))
        assertNull(hostOf("not a url"))
        assertNull(hostOf(null))
    }

    private companion object {
        const val RESOURCE = "https://pod.example/private/notes.ttl"
        const val OWNER = "https://owner.example/card#me"
    }
}
