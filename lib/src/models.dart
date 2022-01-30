///
/// A class defines a set of possible statuses of a download task
///
class DownloadTaskStatus {
  final int _value;

  const DownloadTaskStatus(int value) : _value = value;

  int get value => _value;

  static DownloadTaskStatus from(int value) => DownloadTaskStatus(value);

  static const undefined = const DownloadTaskStatus(0);
  static const enqueued = const DownloadTaskStatus(1);
  static const running = const DownloadTaskStatus(2);
  static const complete = const DownloadTaskStatus(3);
  static const failed = const DownloadTaskStatus(4);
  static const canceled = const DownloadTaskStatus(5);
  static const paused = const DownloadTaskStatus(6);

  @override
  bool operator ==(Object o) {
    if (identical(this, o)) return true;

    return o is DownloadTaskStatus && o._value == _value;
  }

  @override
  int get hashCode => _value.hashCode;

  @override
  String toString() => 'DownloadTaskStatus($_value)';
}

class PriorityTaskStatus {
  final int _value;

  const PriorityTaskStatus(int value) : _value = value;

  int get value => _value;

  static PriorityTaskStatus from(int value) => PriorityTaskStatus(value);

  static const completed = const PriorityTaskStatus(0);
  static const downloading = const PriorityTaskStatus(1);
  static const waiting = const PriorityTaskStatus(2);
  static const queue = const PriorityTaskStatus(3);

  @override
  bool operator ==(Object o) {
    if (identical(this, o)) return true;

    return o is PriorityTaskStatus && o._value == _value;
  }

  @override
  int get hashCode => _value.hashCode;

  @override
  String toString() => 'PriorityTaskStatus($_value)';
}

///
/// A model class encapsulates all task information according to data in Sqlite
/// database.
///
/// * [taskId] the unique identifier of a download task
/// * [status] the latest status of a download task
/// * [progress] the latest progress value of a download task
/// * [url] the download link
/// * [filename] the local file name of a downloaded file
/// * [savedDir] the absolute path of the directory where the downloaded file is saved
/// * [timeCreated] milliseconds since the Unix epoch (midnight, January 1, 1970 UTC)
///
class DownloadTask {
  final String taskId;
  final DownloadTaskStatus status;
  final PriorityTaskStatus priorityStatus;
  final int progress;
  final int contentId;
  final String url;
  final String? filename;
  final String savedDir;
  final int timeCreated;
  final int currentByte;
  final int totalByte;

  DownloadTask(
      {required this.taskId,
      required this.status,
      required this.priorityStatus,
      required this.contentId,
      required this.progress,
      required this.url,
      required this.filename,
      required this.savedDir,
      required this.currentByte,
      required this.totalByte,
      required this.timeCreated});

  @override
  String toString() =>
      "DownloadTask(taskId: $taskId,contentId: $contentId, status: $status, priorityStatus: $priorityStatus, progress: $progress, url: $url, filename: $filename, savedDir: $savedDir, timeCreated: $timeCreated,currentByte:$currentByte,totalByte:$totalByte)";
}
