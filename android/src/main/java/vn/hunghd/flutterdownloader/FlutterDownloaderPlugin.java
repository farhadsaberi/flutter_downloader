package vn.hunghd.flutterdownloader;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterDownloaderPlugin implements MethodCallHandler, FlutterPlugin {
    private static final String CHANNEL = "vn.hunghd/downloader";
    private static final String TAG = "flutter_download_task";

    public static final String SHARED_PREFERENCES_KEY = "vn.hunghd.downloader.pref";
    public static final String CALLBACK_DISPATCHER_HANDLE_KEY = "callback_dispatcher_handle_key";

    private static FlutterDownloaderPlugin instance;
    private MethodChannel flutterChannel;
    private TaskDbHelper dbHelper;
    private TaskDao taskDao;
    private Context context;
    private long callbackHandle;
    private int debugMode;
    private final Object initializationLock = new Object();

    @SuppressLint("NewApi")
    public static void registerWith(PluginRegistry.Registrar registrar) {
        if (instance == null) {
            instance = new FlutterDownloaderPlugin();
        }
        instance.onAttachedToEngine(registrar.context(), registrar.messenger());
    }

    public void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        synchronized (initializationLock) {
            if (flutterChannel != null) {
                return;
            }
            this.context = applicationContext;
            flutterChannel = new MethodChannel(messenger, CHANNEL);
            flutterChannel.setMethodCallHandler(this);
            dbHelper = TaskDbHelper.getInstance(context);
            taskDao = new TaskDao(dbHelper);
        }
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (call.method.equals("initialize")) {
            initialize(call, result);
        } else if (call.method.equals("registerCallback")) {
            registerCallback(call, result);
        } else if (call.method.equals("enqueue")) {
            enqueue(call, result);
        } else if (call.method.equals("loadTasks")) {
            loadTasks(call, result);
        } else if (call.method.equals("loadTasksWithRawQuery")) {
            loadTasksWithRawQuery(call, result);
        } else if (call.method.equals("loadTasksWithTaskId")) {
            loadTasksWithTaskId(call, result);
        } else if (call.method.equals("cancel")) {
            cancel(call, result);
        } else if (call.method.equals("cancelAll")) {
            cancelAll(call, result);
        } else if (call.method.equals("pause")) {
            pause(call, result);
        } else if (call.method.equals("resume")) {
            resume(call, result, call.argument("contentId"));
        } else if (call.method.equals("retry")) {
            retry(call, result, call.argument("contentId"));
        } else if (call.method.equals("open")) {
            open(call, result);
        } else if (call.method.equals("remove")) {
            remove(call, result);
        } else if (call.method.equals("pauseAll")) {
            pauseAll(call, result);
        } else if (call.method.equals("resumeAll")) {
            resumeAll(call, result);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        context = null;
        if (flutterChannel != null) {
            flutterChannel.setMethodCallHandler(null);
            flutterChannel = null;
        }
    }

    private WorkRequest buildRequest(String url, String savedDir, String filename, String headers,
                                     boolean showNotification, boolean openFileFromNotification,
                                     boolean isResume, boolean requiresStorageNotLow, boolean saveInPublicStorage, String contentId) {
        WorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(requiresStorageNotLow)
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .setInputData(new Data.Builder()
                        .putString(DownloadWorker.ARG_URL, url)
                        .putString(DownloadWorker.ARG_CONTENT_ID, contentId)
                        .putString(DownloadWorker.ARG_SAVED_DIR, savedDir)
                        .putString(DownloadWorker.ARG_FILE_NAME, filename)
                        .putString(DownloadWorker.ARG_HEADERS, headers)
                        .putBoolean(DownloadWorker.ARG_SHOW_NOTIFICATION, showNotification)
                        .putBoolean(DownloadWorker.ARG_OPEN_FILE_FROM_NOTIFICATION, openFileFromNotification)
                        .putBoolean(DownloadWorker.ARG_IS_RESUME, isResume)
                        .putLong(DownloadWorker.ARG_CALLBACK_HANDLE, callbackHandle)
                        .putBoolean(DownloadWorker.ARG_DEBUG, debugMode == 1)
                        .putBoolean(DownloadWorker.ARG_SAVE_IN_PUBLIC_STORAGE, saveInPublicStorage)
                        .build()
                )
                .build();
        return request;
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    private void sendUpdateProgress(String id, int status, int progress, int currentByte, int totalByte, int contentId) {
        Map<String, Object> args = new HashMap<>();
        args.put("task_id", id);
        args.put("status", status);
        args.put("progress", progress);
        args.put("currentByte", currentByte);
        args.put("totalByte", totalByte);
        args.put("contentId", contentId);
        flutterChannel.invokeMethod("updateProgress", args);
    }

    private void initialize(MethodCall call, MethodChannel.Result result) {
        List args = (List) call.arguments;
        long callbackHandle = Long.parseLong(args.get(0).toString());
        debugMode = Integer.parseInt(args.get(1).toString());

        SharedPreferences pref = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        pref.edit().putLong(CALLBACK_DISPATCHER_HANDLE_KEY, callbackHandle).apply();

        result.success(null);
    }

    private void registerCallback(MethodCall call, MethodChannel.Result result) {
        List args = (List) call.arguments;
        callbackHandle = Long.parseLong(args.get(0).toString());
        result.success(null);
    }

    private void enqueue(MethodCall call, MethodChannel.Result result) {
        String url = call.argument("url");
        String savedDir = call.argument("saved_dir");
        String filename = call.argument("file_name");
        String headers = call.argument("headers");
        String priority = call.argument("priority");
        String contentId = call.argument("contentId");

        boolean showNotification = call.argument("show_notification");
        boolean openFileFromNotification = call.argument("open_file_from_notification");
        boolean requiresStorageNotLow = call.argument("requires_storage_not_low");
        boolean saveInPublicStorage = call.argument("save_in_public_storage");

        if (url.contains(".srt")) {
            WorkRequest request = buildRequest(url, savedDir, filename, headers, showNotification,
                    openFileFromNotification, false, requiresStorageNotLow, saveInPublicStorage, contentId);
            WorkManager.getInstance(context).enqueue(request);
            String taskId = request.getId().toString();
            result.success(taskId);
            sendUpdateProgress(taskId, DownloadStatus.ENQUEUED, 0, 0, 0, Integer.parseInt(contentId));
            taskDao.insertOrUpdateNewTask(taskId, url, DownloadStatus.ENQUEUED, 0, 0, 0, filename,
                    savedDir, headers, showNotification, openFileFromNotification, saveInPublicStorage, PriorityStatus.WAITING, Integer.parseInt(contentId));
            return;
        }

        DownloadTask task = taskDao.loadContent(contentId);
        if (task != null) {
            WorkRequest request = buildRequest(url, savedDir, filename, headers, showNotification,
                    openFileFromNotification, false, requiresStorageNotLow, saveInPublicStorage, contentId);
            WorkManager.getInstance(context).enqueue(request);
            String taskId = request.getId().toString();
            result.success(taskId);
            sendUpdateProgress(taskId, DownloadStatus.ENQUEUED, task.progress, task.currentByte, task.totalByte, Integer.parseInt(contentId));
            taskDao.updateTaskId(taskId, contentId);
            return;
        }

        if (taskDao.hasDownloaded()) {
            String taskId = UUID.randomUUID().toString();
            sendUpdateProgress(taskId, DownloadStatus.UNDEFINED, 0, 0, 0, Integer.parseInt(contentId));
            taskDao.insertOrUpdateNewTask(taskId, url, DownloadStatus.UNDEFINED, 0, 0, 0, filename,
                    savedDir, headers, showNotification, openFileFromNotification, saveInPublicStorage, Integer.parseInt(priority), Integer.parseInt(contentId));
            result.success(taskId);
        } else {
            WorkRequest request = buildRequest(url, savedDir, filename, headers, showNotification,
                    openFileFromNotification, false, requiresStorageNotLow, saveInPublicStorage, contentId);
            WorkManager.getInstance(context).enqueue(request);
            String taskId = request.getId().toString();
            result.success(taskId);
            sendUpdateProgress(taskId, DownloadStatus.ENQUEUED, 0, 0, 0, Integer.parseInt(contentId));
            taskDao.insertOrUpdateNewTask(taskId, url, DownloadStatus.ENQUEUED, 0, 0, 0, filename,
                    savedDir, headers, showNotification, openFileFromNotification, saveInPublicStorage, PriorityStatus.DOWNLOADING, Integer.parseInt(contentId));
        }
    }

    private void loadTasks(MethodCall call, MethodChannel.Result result) {
        List<DownloadTask> tasks = taskDao.loadAllTasks();
        List<Map> array = new ArrayList<>();
        for (DownloadTask task : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("task_id", task.taskId);
            item.put("contentId", task.contentId);
            item.put("status", task.status);
            item.put("progress", task.progress);
            item.put("currentByte", task.currentByte);
            item.put("totalByte", task.totalByte);
            item.put("url", task.url);
            item.put("file_name", task.filename);
            item.put("saved_dir", task.savedDir);
            item.put("time_created", task.timeCreated);
            item.put("priorityStatus", task.priority);
            array.add(item);
        }
        result.success(array);
    }

    private void loadTasksWithRawQuery(MethodCall call, MethodChannel.Result result) {
        String query = call.argument("query");
        List<DownloadTask> tasks = taskDao.loadTasksWithRawQuery(query);
        List<Map> array = new ArrayList<>();
        for (DownloadTask task : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("task_id", task.taskId);
            item.put("status", task.status);
            item.put("contentId", task.contentId);
            item.put("priorityStatus", task.priority);
            item.put("progress", task.progress);
            item.put("currentByte", task.currentByte);
            item.put("totalByte", task.totalByte);
            item.put("url", task.url);
            item.put("file_name", task.filename);
            item.put("saved_dir", task.savedDir);
            item.put("time_created", task.timeCreated);
            array.add(item);
        }
        result.success(array);
    }

    private void loadTasksWithTaskId(MethodCall call, MethodChannel.Result result) {
        String taskId = call.argument("contentId");
        DownloadTask task = taskDao.loadContent(taskId);

        List<Map> array = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("task_id", task.taskId);
        item.put("status", task.status);
        item.put("priorityStatus", task.priority);
        item.put("contentId", task.contentId);
        item.put("progress", task.progress);
        item.put("currentByte", task.currentByte);
        item.put("totalByte", task.totalByte);
        item.put("url", task.url);
        item.put("file_name", task.filename);
        item.put("saved_dir", task.savedDir);
        item.put("time_created", task.timeCreated);
        array.add(item);

        result.success(array);
    }

    private void cancel(MethodCall call, MethodChannel.Result result) {
        String taskId = call.argument("task_id");
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(taskId));
        result.success(null);
    }

    private void cancelAll(MethodCall call, MethodChannel.Result result) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG);
        result.success(null);
    }

    private void pauseAll(MethodCall call, MethodChannel.Result result) {
        taskDao.pauseAllDownloading();
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG);
        result.success(null);
    }

    private void resumeAll(MethodCall call, MethodChannel.Result result) {
        if (!taskDao.hasDownloaded()) {
            DownloadTask downloadTask = taskDao.resumeAllDownload();
            if (downloadTask != null) {
                checkStatus(call, result, downloadTask);
            } else {
                result.success(null);
            }
        } else {
            result.success(null);
        }
    }

    private void pause(MethodCall call, MethodChannel.Result result) {
        String contentId = call.argument("contentId");
        String taskId = call.argument("task_id");
        // mark the current task is cancelled to process pause request
        // the worker will depends on this flag to prepare data for resume request
        taskDao.updateTask(contentId, true, true);
        // cancel running task, this method causes WorkManager.isStopped() turning true and the download loop will be stopped
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(taskId));
        result.success(null);
    }

    private void resume(MethodCall call, MethodChannel.Result result, String contentId) {
        DownloadTask task = taskDao.loadContent(contentId);
        boolean requiresStorageNotLow = call.argument("requires_storage_not_low");
        if (task != null) {
            if (task.status == DownloadStatus.PAUSED) {
                String filename = task.filename;
                if (filename == null) {
                    filename = task.url.substring(task.url.lastIndexOf("/") + 1, task.url.length());
                }
                String partialFilePath = task.savedDir + File.separator + filename;
                File partialFile = new File(partialFilePath);
                if (partialFile.exists()) {
                    WorkRequest request = buildRequest(task.url, task.savedDir, task.filename,
                            task.headers, task.showNotification, task.openFileFromNotification,
                            true, requiresStorageNotLow, task.saveInPublicStorage, contentId);
                    String newTaskId = request.getId().toString();
                    result.success(newTaskId);
                    sendUpdateProgress(newTaskId, DownloadStatus.RUNNING, task.progress, task.currentByte, task.totalByte, Integer.parseInt(contentId));
                    taskDao.updateTask(newTaskId, DownloadStatus.RUNNING, task.progress, task.currentByte, task.totalByte, false, contentId);
                    WorkManager.getInstance(context).enqueue(request);
                } else {
                    taskDao.updateTask(contentId, false, false);
                    result.error("invalid_data", "not found partial downloaded data, this task cannot be resumed", null);
                }
            } else {
                checkStatus(call, result, task);
                result.error("invalid_status", "only paused task can be resumed", null);
            }
        } else {
            result.error("invalid_task_id", "not found task corresponding to given task id", null);
        }
    }

    private void retry(MethodCall call, MethodChannel.Result result, String contentId) {
        DownloadTask task = taskDao.loadContent(contentId);
        boolean requiresStorageNotLow = call.argument("requires_storage_not_low");
        if (task != null) {
            if (task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELED) {
                WorkRequest request = buildRequest(task.url, task.savedDir, task.filename,
                        task.headers, task.showNotification, task.openFileFromNotification,
                        false, requiresStorageNotLow, task.saveInPublicStorage, contentId);
                String newTaskId = request.getId().toString();
                result.success(newTaskId);
                sendUpdateProgress(newTaskId, DownloadStatus.ENQUEUED, task.progress, task.currentByte, task.totalByte, Integer.parseInt(contentId));
                taskDao.updateTask(newTaskId, DownloadStatus.ENQUEUED, task.progress, task.currentByte, task.totalByte, false, contentId);
                WorkManager.getInstance(context).enqueue(request);
            } else {
                checkStatus(call, result, task);
                result.error("invalid_status", "only failed and canceled task can be retried", null);
            }
        } else {
            result.error("invalid_task_id", "not found task corresponding to given task id", null);
        }
    }

    private void open(MethodCall call, MethodChannel.Result result) {
        String taskId = call.argument("contentId");
        DownloadTask task = taskDao.loadContent(taskId);
        if (task != null) {
            if (task.status == DownloadStatus.COMPLETE) {
                String fileURL = task.url;
                String savedDir = task.savedDir;
                String filename = task.filename;
                if (filename == null) {
                    filename = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
                }
                String saveFilePath = savedDir + File.separator + filename;
                Intent intent = IntentUtils.validatedFileIntent(context, saveFilePath, task.mimeType);
                if (intent != null) {
                    context.startActivity(intent);
                    result.success(true);
                } else {
                    result.success(false);
                }
            } else {
                result.error("invalid_status", "only success task can be opened", null);
            }
        } else {
            result.error("invalid_task_id", "not found task corresponding to given task id", null);
        }
    }

    private void remove(MethodCall call, MethodChannel.Result result) {
        String taskId = call.argument("task_id");
        String contentId = call.argument("contentId");
        boolean shouldDeleteContent = call.argument("should_delete_content");
        DownloadTask task = taskDao.loadContent(contentId);
        if (task != null) {
            if (task.status == DownloadStatus.ENQUEUED || task.status == DownloadStatus.RUNNING) {
                WorkManager.getInstance(context).cancelWorkById(UUID.fromString(taskId));
            }
            if (shouldDeleteContent) {
                String filename = task.filename;
                if (filename == null) {
                    filename = task.url.substring(task.url.lastIndexOf("/") + 1, task.url.length());
                }

                String saveFilePath = task.savedDir + File.separator + filename;
                File tempFile = new File(saveFilePath);
                if (tempFile.exists()) {
                    deleteFileInMediaStore(tempFile);
                    tempFile.delete();
                }
            }
            taskDao.deleteContentId(contentId);

            NotificationManagerCompat.from(context).cancel(task.primaryId);

            result.success(null);
        } else {
            result.error("invalid_task_id", "not found task corresponding to given task id", null);
        }
    }

    private void deleteFileInMediaStore(File file) {
        // Set up the projection (we only need the ID)
        String[] projection = {MediaStore.Images.Media._ID};

        // Match on the file path
        String imageSelection = MediaStore.Images.Media.DATA + " = ?";
        String videoSelection = MediaStore.Video.Media.DATA + " = ?";
        String[] selectionArgs = new String[]{file.getAbsolutePath()};

        // Query for the ID of the media matching the file path
        Uri imageQueryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri videoQueryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        ContentResolver contentResolver = context.getContentResolver();

        // search the file in image store first
        Cursor imageCursor = contentResolver.query(imageQueryUri, projection, imageSelection, selectionArgs, null);
        if (imageCursor != null && imageCursor.moveToFirst()) {
            // We found the ID. Deleting the item via the content provider will also remove the file
            long id = imageCursor.getLong(imageCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
            Uri deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            contentResolver.delete(deleteUri, null, null);
        } else {
            // File not found in image store DB, try to search in video store
            Cursor videoCursor = contentResolver.query(imageQueryUri, projection, imageSelection, selectionArgs, null);
            if (videoCursor != null && videoCursor.moveToFirst()) {
                // We found the ID. Deleting the item via the content provider will also remove the file
                long id = videoCursor.getLong(videoCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                contentResolver.delete(deleteUri, null, null);
            } else {
                // can not find the file in media store DB at all
            }
            if (videoCursor != null) videoCursor.close();
        }
        if (imageCursor != null) imageCursor.close();
    }

    private void checkStatus(MethodCall call, MethodChannel.Result result, DownloadTask task) {
        if (task.status == DownloadStatus.PAUSED) {
            resume(call, result, String.valueOf(task.contentId));
        } else if (task.status == DownloadStatus.UNDEFINED) {
            WorkRequest request = buildRequest(task.url, task.savedDir, task.filename, task.headers, task.showNotification,
                    task.openFileFromNotification, false, true, task.saveInPublicStorage, String.valueOf(task.contentId));
            WorkManager.getInstance(context).enqueue(request);
            String taskId = request.getId().toString();
            sendUpdateProgress(taskId, DownloadStatus.ENQUEUED, 0, 0, 0, Integer.parseInt(task.taskId));
            taskDao.updateTask(String.valueOf(task.contentId), PriorityStatus.DOWNLOADING);
            taskDao.updateTaskId(taskId, String.valueOf(task.contentId));
            result.success(taskId);
        } else if (task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELED) {
            retry(call, result, String.valueOf(task.contentId));
        } else {
            result.error("invalid_status_download", "Fail", null);
        }
    }
}
