package com.worktrace.timeline;

import com.worktrace.model.ActivityBlock;
import com.worktrace.model.WorkSession;
import com.worktrace.util.LogUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作会话聚合引擎。
 * 将多个 ActivityBlock 聚合为更高层的 WorkSession。
 *
 * 聚合规则：
 *   1. 时间间隔 ≤ 30 分钟 → 合并
 *   2. 相同项目 → 优先合并
 *   3. 不同项目或间隔 > 30 分钟 → 新会话
 *
 * 设计理念：
 *   ActivityBlock = 文件级活动（5-15分钟，"改了 Main.java"）
 *   WorkSession   = 工作级活动（30分钟+，"在做 WorkTrace 开发"）
 *
 * 使用示例：
 *   WorkSessionGenerator generator = new WorkSessionGenerator();
 *   List<WorkSession> sessions = generator.generate(activityBlocks);
 */
public class WorkSessionGenerator {

    /** 默认会话间隔阈值：30 分钟 */
    private static final int DEFAULT_SESSION_GAP_MINUTES = 30;

    private final int sessionGapMinutes;

    public WorkSessionGenerator() {
        this.sessionGapMinutes = DEFAULT_SESSION_GAP_MINUTES;
    }

    public WorkSessionGenerator(int sessionGapMinutes) {
        this.sessionGapMinutes = sessionGapMinutes;
    }

    /**
     * 将 ActivityBlock 列表聚合为 WorkSession 列表。
     *
     * @param blocks ActivityBlock 列表（应按时间排序）
     * @return WorkSession 列表（按时间排序）
     */
    public List<WorkSession> generate(List<ActivityBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        // 确保按时间排序
        List<ActivityBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(ActivityBlock::getStartTime));

        List<WorkSession> sessions = new ArrayList<>();
        SessionContext ctx = null;

        for (ActivityBlock block : sorted) {
            if (ctx == null) {
                ctx = new SessionContext(block);
            } else if (ctx.shouldMerge(block)) {
                ctx.add(block);
            } else {
                sessions.add(ctx.toWorkSession());
                ctx = new SessionContext(block);
            }
        }

        if (ctx != null) {
            sessions.add(ctx.toWorkSession());
        }

        LogUtil.info("会话聚合: " + sorted.size() + " 个活动块 → " + sessions.size() + " 个工作会话");
        return sessions;
    }

    // ==================== 内部上下文 ====================

    /**
     * 会话构建上下文。
     * 负责追踪当前会话的状态，判断是否应合并新块。
     */
    private class SessionContext {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String projectName;
        private final Map<String, Integer> categoryCounts = new HashMap<>();
        private final List<ActivityBlock> blocks = new ArrayList<>();

        SessionContext(ActivityBlock firstBlock) {
            this.startTime   = firstBlock.getStartTime();
            this.endTime     = firstBlock.getEndTime();
            this.projectName = firstBlock.getProjectName() != null ? firstBlock.getProjectName() : "";
            add(firstBlock);
        }

        /**
         * 判断是否应合并新块。
         * 规则：间隔 ≤ 30 分钟 AND 同项目
         */
        boolean shouldMerge(ActivityBlock block) {
            // 时间间隔检查
            long gap = Duration.between(endTime, block.getStartTime()).toMinutes();
            if (gap > sessionGapMinutes) return false;

            // 项目检查：空项目名视为"未知"，可以和任何项目合并
            String blockProject = block.getProjectName() != null ? block.getProjectName() : "";
            if (projectName.isEmpty() || blockProject.isEmpty()) return true;
            return projectName.equals(blockProject);
        }

        void add(ActivityBlock block) {
            // 更新时间范围
            if (block.getStartTime().isBefore(startTime)) {
                startTime = block.getStartTime();
            }
            if (block.getEndTime().isAfter(endTime)) {
                endTime = block.getEndTime();
            }

            // 更新项目名（取第一个非空的）
            if (projectName.isEmpty() && block.getProjectName() != null && !block.getProjectName().isEmpty()) {
                projectName = block.getProjectName();
            }

            // 统计类别
            String cat = block.getCategory() != null ? block.getCategory() : "OTHER";
            categoryCounts.merge(cat, 1, Integer::sum);

            blocks.add(block);
        }

        WorkSession toWorkSession() {
            WorkSession session = new WorkSession();
            session.setStartTime(startTime);
            session.setEndTime(endTime);
            session.setProjectName(projectName);
            session.setCategory(getPrimaryCategory());
            session.setTitle(buildTitle());
            session.setBlockCount(blocks.size());
            session.setFileCount(blocks.size()); // 每个 Block 平均对应 1 个文件活动
            session.setDurationMinutes(Math.max(1, Duration.between(startTime, endTime).toMinutes()));
            return session;
        }

        String getPrimaryCategory() {
            return categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
        }

        /**
         * 生成会话标题。
         * 有项目名: "WorkTrace 开发" / "WorkTrace 文档编辑"
         * 无项目名: "代码开发" / "文档编辑"
         */
        private String buildTitle() {
            String catLabel = categoryLabel(getPrimaryCategory());
            if (projectName != null && !projectName.isEmpty()) {
                return projectName + " " + catLabel;
            }
            return catLabel;
        }

        private String categoryLabel(String category) {
            return switch (category) {
                case "CODE"     -> "开发";
                case "DOCUMENT" -> "文档编辑";
                case "IMAGE"    -> "图片处理";
                case "VIDEO"    -> "音视频处理";
                case "CONFIG"   -> "配置调整";
                default         -> "其他工作";
            };
        }
    }
}
