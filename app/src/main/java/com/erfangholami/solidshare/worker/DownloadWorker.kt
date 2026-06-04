package com.erfangholami.solidshare.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType.OCTET_STREAM
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileRepository: FileRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_WEB_ID = "webId"
        const val KEY_FILE_URL = "fileUrl"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_RESULT_URI = "resultUri"
    }

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val webId = inputData.getString(KEY_WEB_ID) ?: return Result.failure()
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: OCTET_STREAM

        setForeground(buildForegroundInfo(fileName, -1))

        return try {
            val uri = fileRepository.downloadToDevice(
                webId = webId,
                fileUrl = fileUrl,
                fileName = fileName,
                mimeType = mimeType,
                onProgress = { pct ->
                    nm.notify(
                        NotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS,
                        NotificationHelper.buildProgressNotification(
                            applicationContext, "Downloading $fileName", pct,
                        ),
                    )
                },
            )

            nm.notify(
                NotificationHelper.NOTIFICATION_ID_DOWNLOAD_COMPLETE,
                NotificationHelper.buildDownloadCompleteNotification(
                    applicationContext, fileName, uri, mimeType,
                ),
            )

            Result.success(workDataOf(KEY_RESULT_URI to uri.toString()))
        } catch (e: Exception) {
            nm.notify(
                NotificationHelper.NOTIFICATION_ID_DOWNLOAD_COMPLETE,
                NotificationHelper.buildErrorNotification(
                    applicationContext, "Download failed", e.message ?: "Unknown error",
                ),
            )
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    private fun buildForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext, "Downloading $fileName", progress,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS, notification)
        }
    }
}
