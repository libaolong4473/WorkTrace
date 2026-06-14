package com.worktrace.collector;

import com.worktrace.database.ProjectRepository;
import com.worktrace.model.ProjectInfo;
import com.worktrace.util.LogUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目识别引擎 —— 自动检测文件所属项目。
 *
 * 算法：
 *   给定文件路径 D:\WorkTrace\src\Main.java
 *   向上遍历目录，查找项目标志文件：
 *     D:\WorkTrace\src\          → 无标志文件
 *     D:\WorkTrace\              → 发现 pom.xml → 项目根目录
 *   项目名称 = 根目录名 = "WorkTrace"
 *
 * 项目标志文件：
 *   .git / pom.xml / build.gradle / settings.gradle
 *   package.json / Cargo.toml / requirements.txt
 *   Makefile / CMakeLists.txt / .project
 *
 * 缓存：
 *   已识别的路径前缀缓存在内存中，避免重复向上遍历。
 *   缓存命中时直接返回，耗时 ~0.001ms。
 */
public class ProjectDetector {

    /** 项目根目录标志文件 */
    private static final Set<String> MARKER_FILES = Set.of(
        ".git",
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "package.json",
        "Cargo.toml",
        "requirements.txt",
        "Makefile",
        "CMakeLists.txt",
        ".project",
        "go.mod",
        "Gemfile",
        "composer.json"
    );

    /** 路径前缀 → 项目信息 缓存 */
    private final ConcurrentHashMap<String, Optional<ProjectInfo>> cache = new ConcurrentHashMap<>();

    /** 项目仓库（用于持久化） */
    private final ProjectRepository projectRepo;

    /** 最大向上遍历层数（防止遍历到根目录） */
    private static final int MAX_DEPTH = 10;

    public ProjectDetector(ProjectRepository projectRepo) {
        this.projectRepo = projectRepo;
    }

    /**
     * 识别文件所属项目。
     *
     * @param filePath 文件绝对路径
     * @return 项目信息（含名称和根路径），无法识别时返回 Optional.empty()
     */
    public Optional<ProjectInfo> detect(String filePath) {
        if (filePath == null || filePath.isBlank()) return Optional.empty();

        // 标准化路径
        String normalized = filePath.replace('\\', '/');

        // 1. 缓存命中
        Optional<ProjectInfo> cached = cacheGet(normalized);
        if (cached != null) return cached;

        // 2. 向上遍历查找项目根目录
        Optional<ProjectInfo> result = walkUpToFindRoot(filePath);

        // 3. 写入缓存
        cachePut(normalized, result);

        // 4. 持久化到 project_info 表
        result.ifPresent(this::persistProject);

        return result;
    }

    /**
     * 从缓存中查找。
     * 如果文件路径以某个已缓存的项目根路径为前缀，直接返回。
     */
    private Optional<ProjectInfo> cacheGet(String normalizedPath) {
        for (var entry : cache.entrySet()) {
            String projectRoot = entry.getKey();
            if (normalizedPath.startsWith(projectRoot)) {
                return entry.getValue();
            }
        }
        return null; // 缓存未命中
    }

    /**
     * 写入缓存。key = 项目根路径。
     */
    private void cachePut(String normalizedPath, Optional<ProjectInfo> project) {
        project.ifPresent(info -> {
            String root = info.getRootPath().replace('\\', '/');
            cache.put(root, project);
        });
    }

    /**
     * 向上遍历目录树，查找项目根目录。
     */
    private Optional<ProjectInfo> walkUpToFindRoot(String filePath) {
        Path current = Path.of(filePath).getParent();
        if (current == null) return Optional.empty();

        int depth = 0;
        while (current != null && depth < MAX_DEPTH) {
            if (isProjectRoot(current)) {
                String projectName = current.getFileName().toString();
                String rootPath = current.toAbsolutePath().toString();
                ProjectInfo info = new ProjectInfo(projectName, rootPath);
                LogUtil.info("识别项目: " + projectName + " → " + rootPath);
                return Optional.of(info);
            }
            current = current.getParent();
            depth++;
        }
        return Optional.empty();
    }

    /**
     * 判断目录是否为项目根目录（存在任一标志文件）。
     */
    private boolean isProjectRoot(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        for (String marker : MARKER_FILES) {
            if (Files.exists(dir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 持久化项目信息到数据库（幂等，已存在则忽略）。
     */
    private void persistProject(ProjectInfo project) {
        try {
            projectRepo.insert(project);
        } catch (Exception e) {
            // 静默处理，不影响主流程
        }
    }

    /**
     * 清空缓存（用于测试或配置变更后）。
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 获取缓存大小（用于监控）。
     */
    public int cacheSize() {
        return cache.size();
    }
}
