package com.worktrace.service;

import com.worktrace.model.ProjectInfo;

import java.util.List;
import java.util.Optional;

/**
 * 项目服务接口。
 * 管理项目信息的识别、注册和查询。
 *
 * 职责：
 *   - 根据文件路径自动识别所属项目
 *   - 注册新项目(手动或自动)
 *   - 查询项目列表及其活动统计
 */
public interface ProjectService {

    /** 注册一个新项目，返回分配的 ID。 */
    long registerProject(String name, String rootPath);

    /** 根据文件路径识别所属项目。 */
    Optional<ProjectInfo> identifyProject(String filePath);

    /** 获取全部已注册项目。 */
    List<ProjectInfo> listAll();
}
