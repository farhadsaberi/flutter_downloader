package vn.hunghd.flutterdownloader;

import android.provider.BaseColumns;

public class TaskContract {

    private TaskContract() {
    }

    public static class TaskEntry implements BaseColumns {
        public static final String TABLE_NAME = "task";
        public static final String COLUMN_NAME_TASK_ID = "task_id";
        public static final String COLUMN_NAME_CONTENT_ID = "content_id";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_PROGRESS = "progress";
        public static final String COLUMN_NAME_CURRENT_BYTE = "current_byte";
        public static final String COLUMN_NAME_TOTAL_BYTE = "total_byte";
        public static final String COLUMN_NAME_URL = "url";
        public static final String COLUMN_NAME_SAVED_DIR = "saved_dir";
        public static final String COLUMN_NAME_FILE_NAME = "file_name";
        public static final String COLUMN_NAME_MIME_TYPE = "mime_type";
        public static final String COLUMN_NAME_RESUMABLE = "resumable";
        public static final String COLUMN_NAME_HEADERS = "headers";
        public static final String COLUMN_NAME_SHOW_NOTIFICATION = "show_notification";
        public static final String COLUMN_NAME_OPEN_FILE_FROM_NOTIFICATION = "open_file_from_notification";
        public static final String COLUMN_NAME_TIME_CREATED = "time_created";
        public static final String COLUMN_SAVE_IN_PUBLIC_STORAGE = "save_in_public_storage";
        public static final String COLUMN_NAME_TIME_UPDATED = "time_updated";
        public static final String COLUMN_NAME_PRIORITY = "priority";
        public static final String COLUMN_NAME_FORCE_TO_PAUSE = "force_pause";
        public static final String COLUMN_NAME_USER_PAUSE = "user_pause";
    }

}
