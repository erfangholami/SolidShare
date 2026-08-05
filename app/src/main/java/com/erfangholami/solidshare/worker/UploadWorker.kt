package com.erfangholami.solidshare.worker

import android.app.Notification
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
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType.OCTET_STREAM
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileRepository: FileRepository,
    private val errors: ErrorPresenter,
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

        setForeground(buildForegroundInfo(webId, fileName, 0))

        return try {
            updateProgress(webId, fileName, 10)
            applicationContext.contentResolver
                .openInputStream(fileUriStr.toUri())
                ?.use { stream ->
                    updateProgress(webId, fileName, 50)
                    fileRepository.uploadFile(
                        webId = webId,
                        containerUrl = containerUrl,
                        fileName = fileName,
                        mimeType = mimeType,
                        inputStream = stream,
                        onProgress = { pct ->
                            updateProgress(webId, fileName, 50 + pct / 2)
                        },
                    )
                } ?: return Result.failure(workDataOf("error" to "Cannot open file"))

            post(
                NotificationHelper.idFor(NotificationHelper.NOTIFICATION_ID_UPLOAD_COMPLETE, webId),
                NotificationHelper.buildUploadCompleteNotification(applicationContext, fileName, webId),
            )

            Result.success()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val failure = errors.present(e, AppOperation.UPLOAD_FILE, fileName)
            post(
                NotificationHelper.idFor(NotificationHelper.NOTIFICATION_ID_UPLOAD_COMPLETE, webId),
                NotificationHelper.buildErrorNotification(
                    applicationContext, failure.title, failure.message, webId,
                ),
            )
            Result.failure(workDataOf("error" to failure.summary))
        }
    }

    private fun updateProgress(webId: String, fileName: String, pct: Int) {
        post(
            NotificationHelper.idFor(NotificationHelper.NOTIFICATION_ID_UPLOAD_PROGRESS, webId),
            NotificationHelper.buildUploadProgressNotification(
                applicationContext, fileName, pct, webId,
            ),
        )
    }

    private fun post(id: Int, notification: Notification) {
        if (NotificationHelper.canPost(applicationContext)) nm.notify(id, notification)
    }

    private fun buildForegroundInfo(webId: String, fileName: String, progress: Int): ForegroundInfo {
        val notification = NotificationHelper.buildUploadProgressNotification(
            applicationContext, fileName, progress, webId,
        )
        val id = NotificationHelper.idFor(NotificationHelper.NOTIFICATION_ID_UPLOAD_PROGRESS, webId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }
}
