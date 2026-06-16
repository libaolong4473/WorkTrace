package com.worktrace.service.impl;

import com.worktrace.collector.ProjectDetector;
import com.worktrace.database.ProjectRepository;
import com.worktrace.model.ProjectInfo;
import com.worktrace.service.ProjectService;
import com.worktrace.util.LogUtil;

import java.util.List;
import java.util.Optional;

/**
 * ProjectService 实现。
 * 委托 ProjectDetector 进行项目识别，ProjectRepository 进行持久化。
 */
public class ProjectServiceImpl implements ProjectService {

    private final ProjectDetector detector;
    private final ProjectRepository repository;

    public ProjectServiceImpl(ProjectDetector detector, ProjectRepository repository) {
        this.detector  = detector;
        this.repository = repository;
    }

    @Override
    public long registerProject(String name, String rootPath) {
        try {
            ProjectInfo info = new ProjectInfo(name, rootPath);
            long id = repository.upsert(info);
            LogUtil.info("注册项目: " + name + " → " + rootPath);
            return id;
        } catch (Exception e) {
            LogUtil.error("注册项目失败: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public Optional<ProjectInfo> identifyProject(String filePath) {
        return detector.detect(filePath);
    }

    @Override
    public List<ProjectInfo> listAll() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            LogUtil.error("查询项目列表失败: " + e.getMessage());
            return List.of();
        }
    }
}
