package com.erfangholami.solidshare.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val NOTIFICATION_CHANNEL_ID = "channel_file_transfer"
    private const val NOTIFICATION_CHANNEL_NAME = "File Transfers"

    const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_ID_UPLOAD_PROGRESS = 1002

    const val NOTIFICATION_ID_DOWNLOAD_COMPLETE = 1003
    const val NOTIFICATION_ID_UPLOAD_COMPLETE = 1004

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
    }

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
