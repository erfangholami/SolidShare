package com.erfangholami.solidshare.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.notification.NotificationHelper
import com.pondersource.shared.domain.network.HTTPAcceptType.OCTET_STREAM
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileRepository: FileRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_WEB_ID = "webId"
        const val KEY_CONTAINER_URL = "containerUrl"
        const val KEY_FILE_URI = "fileUri"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_MIME_TYPE = "mimeType"
    }

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val webId = inputData.getString(KEY_WEB_ID) ?: return Result.failure()
        val containerUrl = inputData.getString(KEY_CONTAINER_URL) ?: return Result.failure()
        val fileUriStr = inputData.getString(KEY_FILE_URI) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: OCTET_STREAM

        setForeground(buildForegroundInfo(fileName, 0))

        return try {
            updateProgress(fileName, 10)
            applicationContext.contentResolver
                .openInputStream(fileUriStr.toUri())
                ?.use { stream ->
                    updateProgress(fileName, 50)
                    fileRepository.uploadFile(
                        webId = webId,
                        containerUrl = containerUrl,
                        fileName = fileName,
                        mimeType = mimeType,
                        inputStream = stream,
                        onProgress = { pct ->
                            updateProgress(fileName, 50 + pct / 2)
                        },
                    )
                } ?: return Result.failure(workDataOf("error" to "Cannot open file"))

            nm.notify(
                NotificationHelper.NOTIFICATION_ID_UPLOAD_COMPLETE,
                NotificationHelper.buildUploadCompleteNotification(applicationContext, fileName),
            )

            Result.success()
        } catch (e: Exception) {
            nm.notify(
                NotificationHelper.NOTIFICATION_ID_UPLOAD_COMPLETE,
                NotificationHelper.buildErrorNotification(
                    applicationContext, "Upload failed", e.message ?: "Unknown error",
                ),
            )
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    private fun updateProgress(fileName: String, pct: Int) {
        nm.notify(
            NotificationHelper.NOTIFICATION_ID_UPLOAD_PROGRESS,
            NotificationHelper.buildUploadProgressNotification(applicationContext, fileName, pct),
        )
    }

    private fun buildForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val notification = NotificationHelper.buildUploadProgressNotification(
            applicationContext, fileName, progress,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_UPLOAD_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_UPLOAD_PROGRESS, notification)
        }
    }
}
