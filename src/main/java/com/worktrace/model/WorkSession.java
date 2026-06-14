package com.worktrace.model;

import java.time.LocalDateTime;

/**
 * 工作会话实体 —— 对应 work_session 表。
 * 由 WorkSessionGenerator 将多个 ActivityBlock 聚合为更高层的工作会话。
 *
 * 设计理念：
 *   ActivityBlock = 文件级活动（5-15分钟）
 *   WorkSession   = 工作级活动（30分钟+）
 *
 *   用户关心的是"我今天做了什么工作"，而不是"我改了哪些文件"。
 *   WorkSession 将文件活动抽象为有意义的工作段落。
 */
public class WorkSession {

    private long id;
    private LocalDateTime startTime;    // 会话开始时间
    private LocalDateTime endTime;      // 会话结束时间
    private String projectName;         // 项目名称
    private String category;            // 主类别 (CODE/DOCUMENT/IMAGE/VIDEO/CONFIG/OTHER)
    private String title;               // 会话标题 (如 "WorkTrace 开发")
    private int blockCount;             // 包含的 ActivityBlock 数量
    private int fileCount;              // 涉及的文件总数
    private long durationMinutes;       // 总时长(分钟)

    public WorkSession() {}

    // ---------- getters / setters ----------

    public long getId()                         { return id; }
    public void setId(long id)                  { this.id = id; }

    public LocalDateTime getStartTime()                     { return startTime; }
    public void setStartTime(LocalDateTime startTime)       { this.startTime = startTime; }

    public LocalDateTime getEndTime()                       { return endTime; }
    public void setEndTime(LocalDateTime endTime)           { this.endTime = endTime; }

    public String getProjectName()                          { return projectName; }
    public void setProjectName(String projectName)          { this.projectName = projectName; }

    public String getCategory()                             { return category; }
    public void setCategory(String category)                { this.category = category; }

    public String getTitle()                                { return title; }
    public void setTitle(String title)                      { this.title = title; }

    public int getBlockCount()                              { return blockCount; }
    public void setBlockCount(int blockCount)               { this.blockCount = blockCount; }

    public int getFileCount()                               { return fileCount; }
    public void setFileCount(int fileCount)                 { this.fileCount = fileCount; }

    public long getDurationMinutes()                        { return durationMinutes; }
    public void setDurationMinutes(long durationMinutes)    { this.durationMinutes = durationMinutes; }
}
