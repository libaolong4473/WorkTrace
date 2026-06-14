package com.worktrace.model;

import java.time.LocalDateTime;

/**
 * 活动块实体 —— 对应 activity_block 表。
 * 由 EventAggregator 将短时间内的多个 FileEvent 聚合为一个有意义的活动时段。
 */
public class ActivityBlock {

    private long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String category;     // CODE / DOCUMENT / IMAGE / VIDEO / CONFIG / OTHER
    private String summary;      // 摘要(可由 AI 生成)
    private String projectName;  // 项目名称(由 ProjectDetector 自动识别)

    public ActivityBlock() {}

    public ActivityBlock(LocalDateTime startTime, LocalDateTime endTime,
                         String category, String summary) {
        this.startTime = startTime;
        this.endTime   = endTime;
        this.category  = category;
        this.summary   = summary;
    }

    // ---------- getters / setters ----------

    public long getId()               { return id; }
    public void setId(long id)        { this.id = id; }

    public LocalDateTime getStartTime()                    { return startTime; }
    public void setStartTime(LocalDateTime startTime)      { this.startTime = startTime; }

    public LocalDateTime getEndTime()                      { return endTime; }
    public void setEndTime(LocalDateTime endTime)          { this.endTime = endTime; }

    public String getCategory()                            { return category; }
    public void setCategory(String category)               { this.category = category; }

    public String getSummary()                             { return summary; }
    public void setSummary(String summary)                 { this.summary = summary; }

    public String getProjectName()                         { return projectName; }
    public void setProjectName(String projectName)         { this.projectName = projectName; }
}
