package com.erfangholami.solidshare.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.MainActivity

object NotificationHelper {

    /** SolidShare brand accent (Primary40) used to tint the small icon + accent. */
    private const val BRAND_COLOR = 0xFF4D65FF.toInt()

    private const val NOTIFICATION_CHANNEL_ID = "channel_file_transfer"
    private const val NOTIFICATION_CHANNEL_NAME = "File Transfers"

    private const val CHANNEL_SHARING_REQUESTS = "channel_sharing_requests"
    private const val CHANNEL_SHARING_ACTIVITY = "channel_sharing_activity"

    private const val CHANNEL_CONTACTS_PROGRESS = "channel_contacts_progress"
    private const val CHANNEL_CONTACTS = "channel_contacts"

    const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_ID_UPLOAD_PROGRESS = 1002

    const val NOTIFICATION_ID_DOWNLOAD_COMPLETE = 1003
    const val NOTIFICATION_ID_UPLOAD_COMPLETE = 1004

    const val NOTIFICATION_ID_SHARING = 2001

    const val NOTIFICATION_ID_CONTACTS_IMPORT_PROGRESS = 3001
    const val NOTIFICATION_ID_CONTACTS_IMPORT_COMPLETE = 3002
    const val NOTIFICATION_ID_CONTACTS_EXPORT_PROGRESS = 3003
    const val NOTIFICATION_ID_CONTACTS_EXPORT_COMPLETE = 3004

    private const val ID_ACCOUNT_STRIDE = 100_000

    /**
     * A notification id unique to [base] and [webId].
     *
     * Ids used to be plain constants, so two accounts running the same job — importing contacts
     * from two pods, say — posted to the same id and each replaced the other's notification, with
     * their progress bars interleaving. Every base gets its own block of [ID_ACCOUNT_STRIDE] ids
     * and the account picks a slot inside it, so the two coexist.
     */
    fun idFor(base: Int, webId: String?): Int =
        if (webId == null) base else base * ID_ACCOUNT_STRIDE + webId.hashCode().mod(ID_ACCOUNT_STRIDE)

    /**
     * Short, recognisable form of [webId] for the notification's sub-text.
     *
     * A WebID is a URL, and the part that identifies the person sits in the host for some pod
     * servers (`alice.solidcommunity.net`) and in the path for others (`pod.example/alice`), so
     * both are kept and the boilerplate `/profile/card#me` tail is dropped.
     */
    fun accountLabel(webId: String?): String? {
        if (webId.isNullOrBlank()) return null
        val withoutScheme = webId.substringAfter("://", webId).substringBefore('#')
        val parts = withoutScheme.trim('/').split('/')
        val host = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return webId
        val segment = parts.drop(1).firstOrNull { it.isNotBlank() && it != "profile" }
        return if (segment == null) host else "$host/$segment"
    }

    /**
     * Names the account a notification belongs to and bundles it with that account's others.
     *
     * The sub-text is the only place the account is visible once the notification is collapsed in
     * the shade, and it is what tells two concurrent jobs apart at a glance.
     */
    private fun NotificationCompat.Builder.attributeToAccount(
        webId: String?,
    ): NotificationCompat.Builder = apply {
        accountLabel(webId)?.let { setSubText(it) }
        webId?.takeIf { it.isNotBlank() }?.let { setGroup(it) }
    }

    private const val CONTACTS_CONTENT_REQUEST_CODE = 3100

    private const val SHARING_CONTENT_REQUEST_CODE = 2100

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress for downloads and uploads"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val requestsChannel = NotificationChannel(
            CHANNEL_SHARING_REQUESTS,
            "Share requests",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Someone is asking to access your data"
        }
        manager.createNotificationChannel(requestsChannel)

        val activityChannel = NotificationChannel(
            CHANNEL_SHARING_ACTIVITY,
            "Sharing activity",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Updates about resources shared with you"
        }
        manager.createNotificationChannel(activityChannel)

        val contactsProgressChannel = NotificationChannel(
            CHANNEL_CONTACTS_PROGRESS,
            "Contacts sync progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress for contact import and export"
            setShowBadge(false)
        }
        manager.createNotificationChannel(contactsProgressChannel)

        val contactsChannel = NotificationChannel(
            CHANNEL_CONTACTS,
            "Contacts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Contact import, export and duplicate updates"
        }
        manager.createNotificationChannel(contactsChannel)
    }

    fun buildContactsProgressNotification(
        context: Context,
        title: String,
        text: String,
        current: Int,
        total: Int,
        webId: String? = null,
    ): Notification {
        val indeterminate = total <= 0
        return NotificationCompat.Builder(context, CHANNEL_CONTACTS_PROGRESS)
            .setContentTitle(title)
            .setContentText(text)
            .attributeToAccount(webId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setProgress(if (indeterminate) 0 else total, if (indeterminate) 0 else current, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun buildContactsCompleteNotification(
        context: Context,
        title: String,
        text: String,
        openContacts: Boolean,
        webId: String? = null,
    ): Notification {
        val account = accountLabel(webId)
        val style = NotificationCompat.BigTextStyle().bigText(text)
        if (account != null) style.setSummaryText(account)
        val builder = NotificationCompat.Builder(context, CHANNEL_CONTACTS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .attributeToAccount(webId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
        if (openContacts) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_CONTACTS, true)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    CONTACTS_CONTENT_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    fun buildSharingNotification(
        context: Context,
        title: String,
        text: String,
        account: String,
        highPriority: Boolean,
        webId: String? = null,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            SHARING_CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (highPriority) CHANNEL_SHARING_REQUESTS else CHANNEL_SHARING_ACTIVITY
        return NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(account)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLogo(context))
            .setColor(BRAND_COLOR)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(account))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .apply { webId?.takeIf { it.isNotBlank() }?.let { setGroup(it) } }
            .build()
    }

    /** The colored app logo as a bitmap for the notification's large icon. */
    private fun appLogo(context: Context): Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.drawable.logo)?.toBitmap(width = 128, height = 128)
    }.getOrNull()

    fun buildDownloadProgressNotification(
        context: Context,
        fileName: String,
        progress: Int,
        webId: String? = null,
    ): Notification {
        val indeterminate = progress < 0
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.transfer_downloading, fileName))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .attributeToAccount(webId)
            .build()
    }

    fun buildUploadProgressNotification(
        context: Context,
        fileName: String,
        progress: Int,
        webId: String? = null,
    ): Notification {
        val indeterminate = progress < 0
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.transfer_uploading))
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .attributeToAccount(webId)
            .build()
    }

    fun buildDownloadCompleteNotification(
        context: Context,
        fileName: String,
        fileUri: Uri,
        mimeType: String,
        webId: String? = null,
    ): Notification {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pending = PendingIntent.getActivity(
            context,
            fileUri.hashCode(),
            Intent.createChooser(openIntent, null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.transfer_download_complete))
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .attributeToAccount(webId)
            .build()
    }

    fun buildUploadCompleteNotification(
        context: Context,
        fileName: String,
        webId: String? = null,
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.transfer_upload_complete))
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .attributeToAccount(webId)
            .build()
    }

    fun buildErrorNotification(
        context: Context,
        title: String,
        message: String,
        webId: String? = null,
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .attributeToAccount(webId)
            .build()
    }
}
