package com.worktrace.model;

import java.time.LocalDateTime;

/**
 * 文件事件实体 —— 对应 file_event 表。
 * 每一次 WatchService 检测到的文件系统变化都映射为一条 FileEvent。
 */
public class FileEvent {

    private long id;
    private String eventType;       // CREATE / MODIFY / DELETE
    private String path;            // 完整路径
    private String fileName;        // 文件名
    private String extension;       // 扩展名(不含点)
    private long size;              // 字节
    private LocalDateTime eventTime;

    public FileEvent() {}

    public FileEvent(String eventType, String path, String fileName,
                     String extension, long size, LocalDateTime eventTime) {
        this.eventType  = eventType;
        this.path       = path;
        this.fileName   = fileName;
        this.extension  = extension;
        this.size       = size;
        this.eventTime  = eventTime;
    }

    // ---------- getters / setters ----------

    public long getId()                  { return id; }
    public void setId(long id)           { this.id = id; }

    public String getEventType()             { return eventType; }
    public void setEventType(String eventType){ this.eventType = eventType; }

    public String getPath()              { return path; }
    public void setPath(String path)     { this.path = path; }

    public String getFileName()              { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExtension()               { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public long getSize()                { return size; }
    public void setSize(long size)       { this.size = size; }

    public LocalDateTime getEventTime()               { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
