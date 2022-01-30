package vn.hunghd.flutterdownloader;

public class DownloadTask {
    int primaryId;
    String taskId;
    int status;
    int progress;
    int totalByte;
    int currentByte;
    int priority;
    int contentId;
    String url;
    String filename;
    String savedDir;
    String headers;
    String mimeType;
    boolean resumable;
    boolean forcePause;
    boolean userPause;
    boolean showNotification;
    boolean openFileFromNotification;
    long timeCreated;
    long timeUpdated;
    boolean saveInPublicStorage;

    DownloadTask(int primaryId, String taskId, int status, int progress, int currentByte, int totalByte, String url, String filename, String savedDir,
                 String headers, String mimeType, boolean resumable, boolean showNotification, boolean openFileFromNotification, long timeCreated, boolean saveInPublicStorage, long timeUpdated, int priority, int contentId, boolean forcePause, boolean userPause) {
        this.primaryId = primaryId;
        this.taskId = taskId;
        this.status = status;
        this.progress = progress;
        this.currentByte = currentByte;
        this.totalByte = totalByte;
        this.url = url;
        this.filename = filename;
        this.savedDir = savedDir;
        this.headers = headers;
        this.mimeType = mimeType;
        this.resumable = resumable;
        this.showNotification = showNotification;
        this.openFileFromNotification = openFileFromNotification;
        this.timeCreated = timeCreated;
        this.timeUpdated = timeUpdated;
        this.priority = priority;
        this.contentId = contentId;
        this.forcePause = forcePause;
        this.userPause = userPause;
        this.saveInPublicStorage = saveInPublicStorage;
    }

    @Override
    public String toString() {
        return "DownloadTask{taskId=" + taskId + ",status=" + status + ",progress=" + progress + ",currentByte=" + currentByte + ",totalByte=" + totalByte + ",url=" + url + ",filename=" + filename + ",savedDir=" + savedDir + ",headers=" + headers + ", saveInPublicStorage= " + saveInPublicStorage + ", time_updated= " + timeUpdated + ", priority= " + priority + ", contentId= " + contentId + ", forcePause= " + forcePause + ", userPause= " + userPause + "}";
    }
}
