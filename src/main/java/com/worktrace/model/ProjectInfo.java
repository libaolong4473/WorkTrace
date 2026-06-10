package com.worktrace.model;

/**
 * 项目信息实体 —— 对应 project_info 表。
 * 系统根据文件事件路径自动识别项目根目录，也可由用户手动配置。
 */
public class ProjectInfo {

    private long id;
    private String projectName;
    private String rootPath;        // 绝对路径，唯一约束

    public ProjectInfo() {}

    public ProjectInfo(String projectName, String rootPath) {
        this.projectName = projectName;
        this.rootPath    = rootPath;
    }

    // ---------- getters / setters ----------

    public long getId()                    { return id; }
    public void setId(long id)             { this.id = id; }

    public String getProjectName()                     { return projectName; }
    public void setProjectName(String projectName)     { this.projectName = projectName; }

    public String getRootPath()                        { return rootPath; }
    public void setRootPath(String rootPath)           { this.rootPath = rootPath; }
}
