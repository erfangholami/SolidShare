package com.erfangholami.solidshare.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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

    const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_ID_UPLOAD_PROGRESS = 1002

    const val NOTIFICATION_ID_DOWNLOAD_COMPLETE = 1003
    const val NOTIFICATION_ID_UPLOAD_COMPLETE = 1004

    const val NOTIFICATION_ID_SHARING = 2001

    private const val SHARING_CONTENT_REQUEST_CODE = 2100

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
    }

    fun buildSharingNotification(
        context: Context,
        title: String,
        text: String,
        account: String,
        highPriority: Boolean,
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
            .setSmallIcon(R.drawable.ic_solid)
            .setLargeIcon(appLogo(context))
            .setColor(BRAND_COLOR)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(account))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()
    }

    /** The colored app logo as a bitmap for the notification's large icon. */
    private fun appLogo(context: Context): Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.drawable.logo)?.toBitmap(width = 128, height = 128)
    }.getOrNull()

    fun buildProgressNotification(
        context: Context,
        title: String,
        progress: Int,
    ): Notification {
        val indeterminate = progress < 0
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun buildUploadProgressNotification(
        context: Context,
        fileName: String,
        progress: Int,
    ): Notification {
        val indeterminate = progress < 0
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Uploading…")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun buildDownloadCompleteNotification(
        context: Context,
        fileName: String,
        fileUri: Uri,
        mimeType: String,
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
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
    }

    fun buildUploadCompleteNotification(
        context: Context,
        fileName: String,
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Upload complete")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
    }

    fun buildErrorNotification(
        context: Context,
        title: String,
        message: String,
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
    }
}
