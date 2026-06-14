package com.worktrace.timeline;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.collector.ProjectDetector;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.model.ProjectInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聚合上下文 —— 表示"正在构建中的活动块"。
 *
 * 生命周期：
 *   newContext(firstEvent)  →  add(event) × N  →  toActivityBlock()
 *
 * 核心职责：
 *   1. 追踪当前块的时间范围(start / end)
 *   2. 维护文件列表(去重：同一文件多次修改只记一次)
 *   3. 统计各类别文件数量 → 确定主类别
 *   4. 收集涉及的项目路径 → 用于项目归属
 *   5. 判断新事件是否应归入当前块
 */
public class AggregationContext {

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private final List<FileEntry> files = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new HashMap<>();
    private final Set<String> projectPaths = new LinkedHashSet<>();
    /** 路径 → 项目名称 映射 */
    private final Map<String, String> pathToProjectName = new HashMap<>();

    private final CategoryClassifier classifier;
    private final ProjectDetector projectDetector;

    public AggregationContext(CategoryClassifier classifier, FileEvent firstEvent) {
        this(classifier, null, firstEvent);
    }

    public AggregationContext(CategoryClassifier classifier, ProjectDetector projectDetector, FileEvent firstEvent) {
        this.classifier       = classifier;
        this.projectDetector  = projectDetector;
        this.startTime        = firstEvent.getEventTime();
        this.endTime          = firstEvent.getEventTime();
        add(firstEvent);
    }

    // ==================== 状态查询 ====================

    public LocalDateTime getStartTime()   { return startTime; }
    public LocalDateTime getEndTime()     { return endTime; }
    public int getFileCount()             { return files.size(); }
    public Set<String> getProjectPaths()  { return projectPaths; }

    /**
     * 当前块的主类别(文件数最多的类别)。
     */
    public String getPrimaryCategory() {
        return categoryCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("OTHER");
    }

    /**
     * 获取主项目路径(出现次数最多的目录前缀)。
     */
    public String getPrimaryProjectPath() {
        if (projectPaths.isEmpty()) return "";
        return projectPaths.stream()
            .max(Comparator.comparingInt(String::length))
            .orElse("");
    }

    /**
     * 获取主项目名称(识别到的项目中出现次数最多的)。
     */
    public String getPrimaryProjectName() {
        if (pathToProjectName.isEmpty()) return "";
        // 统计各项目名称出现次数
        Map<String, Long> nameCounts = pathToProjectName.values().stream()
            .filter(n -> n != null && !n.isEmpty())
            .collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        return nameCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
    }

    // ==================== 合并判断 ====================

    /**
     * 判断新事件是否应归入当前块。
     *
     * 核心原则：gap ≤ maxGapMinutes 时，尽可能合并，减少碎片化。
     *
     * 规则：
     *   1. 时间间隔 > maxGapMinutes → 强制分裂
     *   2. 时间间隔 ≤ maxGapMinutes → 合并(无论项目、类别是否相同)
     *
     * 设计理由：
     *   用户在 15 分钟内连续工作，即使写了代码又改了配置，
     *   本质上是同一次工作会话，不应被切割成多个碎片块。
     *   类别混合时，主类别由 categoryCounts 中权重最高的决定。
     */
    public boolean shouldMerge(FileEvent event, MergeConfig config) {
        long gapMinutes = java.time.Duration.between(endTime, event.getEventTime()).toMinutes();
        return gapMinutes <= config.maxGapMinutes();
    }

    /**
     * 判断事件是否属于当前块已涉及的项目。
     * 匹配逻辑：事件路径以某个已知项目路径为前缀。
     */
    public boolean containsProject(FileEvent event) {
        if (projectPaths.isEmpty()) return false;
        String eventDir = extractDirectory(event.getPath());
        for (String projectPath : projectPaths) {
            if (eventDir.startsWith(projectPath) || projectPath.startsWith(eventDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断事件的文件类别是否与当前块的主类别相同。
     */
    public boolean isSameCategory(FileEvent event) {
        String eventCategory = classifier.classify(event.getExtension());
        return eventCategory.equals(getPrimaryCategory());
    }

    // ==================== 状态变更 ====================

    /**
     * 将一个事件归入当前块。
     */
    public void add(FileEvent event) {
        // 更新时间范围
        if (event.getEventTime().isBefore(startTime)) {
            startTime = event.getEventTime();
        }
        if (event.getEventTime().isAfter(endTime)) {
            endTime = event.getEventTime();
        }

        // 添加文件(按路径去重：同一文件多次修改只保留最后一条)
        String normalizedPath = event.getPath().replace('\\', '/');
        boolean replaced = false;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).path().equals(normalizedPath)) {
                files.set(i, new FileEntry(
                    normalizedPath, event.getFileName(),
                    event.getExtension(), event.getSize(), event.getEventType()
                ));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            files.add(new FileEntry(
                normalizedPath, event.getFileName(),
                event.getExtension(), event.getSize(), event.getEventType()
            ));
        }

        // 更新类别计数
        String category = classifier.classify(event.getExtension());
        categoryCounts.merge(category, 1, Integer::sum);

        // 收集项目路径并识别项目名称
        String dir = extractDirectory(normalizedPath);
        if (!dir.isEmpty()) {
            projectPaths.add(dir);
            // 使用 ProjectDetector 识别项目
            if (projectDetector != null && !pathToProjectName.containsKey(dir)) {
                projectDetector.detect(event.getPath())
                    .ifPresent(project -> pathToProjectName.put(dir, project.getProjectName()));
            }
        }
    }

    // ==================== 输出 ====================

    /**
     * 将当前上下文转换为最终的 ActivityBlock 实体。
     */
    public ActivityBlock toActivityBlock() {
        ActivityBlock block = new ActivityBlock();
        block.setStartTime(startTime);
        block.setEndTime(endTime);
        block.setCategory(getPrimaryCategory());
        block.setProjectName(getPrimaryProjectName());
        block.setSummary(buildSummary());
        return block;
    }

    /**
     * 生成人类可读的摘要。
     *
     * 示例输出：
     *   "Java开发 · 涉及4个文件"
     *   "文档编辑 · README.md 等2个文件"
     *   "图片处理 · 设计稿.psd"
     *   "配置修改 · package.json 等3个文件"
     */
    private String buildSummary() {
        String categoryLabel = categoryLabel(getPrimaryCategory());
        int fileCount = files.size();

        if (fileCount == 1) {
            return categoryLabel + " · " + files.get(0).fileName();
        }

        // 取主文件名(出现次数最多的扩展名对应的文件)
        String mainFileName = files.stream()
            .collect(Collectors.groupingBy(FileEntry::extension, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .flatMap(e -> files.stream()
                .filter(f -> f.extension().equals(e.getKey()))
                .findFirst())
            .map(FileEntry::fileName)
            .orElse("");

        if (fileCount <= 3) {
            String names = files.stream()
                .map(FileEntry::fileName)
                .collect(Collectors.joining("、"));
            return categoryLabel + " · " + names;
        }

        return categoryLabel + " · " + mainFileName + " 等" + fileCount + "个文件";
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "CODE"     -> "代码开发";
            case "DOCUMENT" -> "文档编辑";
            case "IMAGE"    -> "图片处理";
            case "VIDEO"    -> "音视频处理";
            case "CONFIG"   -> "配置修改";
            default         -> "其他活动";
        };
    }

    /**
     * 从完整路径中提取目录部分。
     */
    private String extractDirectory(String path) {
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep > 0 ? path.substring(0, lastSep) : "";
    }

    // ==================== 内部值对象 ====================

    /**
     * 文件条目 —— 记录参与当前活动块的单个文件信息。
     */
    public record FileEntry(
        String path,
        String fileName,
        String extension,
        long size,
        String lastEventType    // 最后一次事件类型(CREATE/MODIFY/DELETE)
    ) {}
}
