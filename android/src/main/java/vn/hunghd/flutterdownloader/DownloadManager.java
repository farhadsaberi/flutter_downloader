package vn.hunghd.flutterdownloader;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.concurrent.TimeUnit;

public class DownloadManager {

    private static final String TAG = "flutter_download_task";

    public void startDownloadManger(Context context, TaskDao taskDao, DownloadTask downloadTask, long callbackHandle, boolean debugMode) {
        WorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .setInputData(new Data.Builder()
                        .putString(DownloadWorker.ARG_URL, downloadTask.url)
                        .putString(DownloadWorker.ARG_SAVED_DIR, downloadTask.savedDir)
                        .putString(DownloadWorker.ARG_FILE_NAME, downloadTask.filename)
                        .putString(DownloadWorker.ARG_HEADERS, downloadTask.headers)
                        .putBoolean(DownloadWorker.ARG_SHOW_NOTIFICATION, downloadTask.showNotification)
                        .putBoolean(DownloadWorker.ARG_OPEN_FILE_FROM_NOTIFICATION, downloadTask.openFileFromNotification)
                        .putBoolean(DownloadWorker.ARG_IS_RESUME, downloadTask.resumable)
                        .putLong(DownloadWorker.ARG_CALLBACK_HANDLE, callbackHandle)
                        .putBoolean(DownloadWorker.ARG_DEBUG, debugMode)
                        .putBoolean(DownloadWorker.ARG_SAVE_IN_PUBLIC_STORAGE, downloadTask.saveInPublicStorage)
                        .build()
                )
                .build();
        WorkManager.getInstance(context).enqueue(request);
        String taskId = request.getId().toString();
        taskDao.updateTask(taskId, DownloadStatus.ENQUEUED, 0, 0, 0, false, String.valueOf(downloadTask.contentId));
    }

}
