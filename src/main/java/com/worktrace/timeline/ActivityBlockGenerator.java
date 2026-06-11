package com.worktrace.timeline;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.util.LogUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间线聚合引擎 —— 系统核心模块。
 *
 * 将大量原始 FileEvent 聚合为少量有意义的 ActivityBlock。
 *
 * 算法: 单遍扫描 + 滑动窗口
 * ─────────────────────────────────────────────────────────
 *   输入: events[] (按 eventTime 排序)
 *   输出: blocks[]
 *
 *   block = null
 *   for each event in events:
 *       if block == null:
 *           block = newContext(event)
 *       else if shouldMerge(block, event):
 *           block.add(event)
 *       else:
 *           emit(block)
 *           block = newContext(event)
 *   emit(block)
 * ─────────────────────────────────────────────────────────
 *
 * 合并规则(按优先级):
 *   1. 时间间隔 > maxGapMinutes → 强制分裂
 *   2. 同项目(路径前缀匹配)     → 优先合并
 *   3. 同文件类别               → 优先合并
 *   4. 以上都不满足             → 分裂
 *
 * 分类体系:
 *   CODE     → 代码文件(java, py, js, ts, go, rs, ...)
 *   DOCUMENT → 文档文件(doc, pdf, md, txt, xlsx, ...)
 *   IMAGE    → 图片文件(png, jpg, svg, psd, ...)
 *   VIDEO    → 音视频文件(mp4, mp3, avi, ...)
 *   CONFIG   → 配置文件(json, yaml, xml, properties, ...)
 *   OTHER    → 其余
 *
 * 使用示例:
 *   ActivityBlockGenerator generator = new ActivityBlockGenerator();
 *   List<ActivityBlock> blocks = generator.generate(fileEvents);
 */
public class ActivityBlockGenerator {

    private final CategoryClassifier classifier;
    private final MergeConfig config;

    public ActivityBlockGenerator() {
        this.classifier = new CategoryClassifier();
        this.config     = MergeConfig.DEFAULT;
    }

    public ActivityBlockGenerator(MergeConfig config) {
        this.classifier = new CategoryClassifier();
        this.config     = config;
    }

    public ActivityBlockGenerator(CategoryClassifier classifier, MergeConfig config) {
        this.classifier = classifier;
        this.config     = config;
    }

    /**
     * 将文件事件列表聚合为活动块列表。
     *
     * 处理流程:
     *   1. 按时间排序
     *   2. 单遍扫描，滑动窗口聚合
     *   3. 输出 ActivityBlock 列表
     *
     * @param events 原始文件事件列表(可无序，内部会排序)
     * @return 聚合后的活动块列表(按时间正序)
     */
    public List<ActivityBlock> generate(List<FileEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // 1. 按时间排序
        List<FileEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(FileEvent::getEventTime));

        // 2. 单遍扫描聚合
        List<ActivityBlock> allBlocks = new ArrayList<>();
        AggregationContext ctx = null;

        for (FileEvent event : sorted) {
            if (ctx == null) {
                ctx = new AggregationContext(classifier, event);
            } else if (ctx.shouldMerge(event, config)) {
                ctx.add(event);
            } else {
                allBlocks.add(ctx.toActivityBlock());
                ctx = new AggregationContext(classifier, event);
            }
        }
        if (ctx != null) {
            allBlocks.add(ctx.toActivityBlock());
        }

        // 3. 过滤噪声块：丢弃持续时间 < minBlockMinutes 的碎片块
        List<ActivityBlock> blocks = allBlocks.stream()
            .filter(b -> {
                long minutes = java.time.Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
                return Math.max(minutes, 1) >= config.minBlockMinutes();
            })
            .toList();

        int filtered = allBlocks.size() - blocks.size();
        if (filtered > 0) {
            LogUtil.info("过滤噪声块: " + filtered + " 个(持续时间 < " + config.minBlockMinutes() + " 分钟)");
        }
        LogUtil.info("聚合完成: " + sorted.size() + " 个事件 → " + blocks.size() + " 个活动块");
        return blocks;
    }

    /**
     * 增量聚合 —— 将新事件追加到已有的活动块列表中。
     *
     * 适用于实时场景：监听器产生新事件后，不重新处理全部历史，
     * 只判断最后一个块是否可以合并。
     *
     * @param existingBlocks 已有的活动块列表(不可为 null)
     * @param newEvents      新产生的事件列表
     * @return 更新后的活动块列表
     */
    public List<ActivityBlock> generateIncremental(
            List<ActivityBlock> existingBlocks, List<FileEvent> newEvents) {

        if (newEvents == null || newEvents.isEmpty()) {
            return existingBlocks;
        }

        List<FileEvent> sorted = new ArrayList<>(newEvents);
        sorted.sort(Comparator.comparing(FileEvent::getEventTime));

        List<ActivityBlock> result = new ArrayList<>(existingBlocks);

        for (FileEvent event : sorted) {
            if (result.isEmpty()) {
                // 没有已有块，直接全量生成
                return generate(
                    new ArrayList<>() {{ addAll(existingBlocks.stream()
                        .flatMap(b -> toEvents(b).stream()).toList());
                        addAll(sorted);
                    }}
                );
            }

            // 尝试追加到最后一个块
            ActivityBlock lastBlock = result.get(result.size() - 1);
            AggregationContext ctx = fromBlock(lastBlock);

            if (ctx.shouldMerge(event, config)) {
                ctx.add(event);
                result.set(result.size() - 1, ctx.toActivityBlock());
            } else {
                AggregationContext newCtx = new AggregationContext(classifier, event);
                result.add(newCtx.toActivityBlock());
            }
        }

        return result;
    }

    /**
     * 从已有的 ActivityBlock 还原出 AggregationContext。
     * 用于增量聚合场景。
     */
    private AggregationContext fromBlock(ActivityBlock block) {
        // 创建一个伪事件来初始化上下文
        FileEvent pseudo = new FileEvent();
        pseudo.setEventTime(block.getStartTime());
        pseudo.setEventType("MODIFY");
        pseudo.setPath("");
        pseudo.setFileName("");
        pseudo.setExtension("");
        pseudo.setSize(0);
        AggregationContext ctx = new AggregationContext(classifier, pseudo);
        return ctx;
    }

    /**
     * 将 ActivityBlock 转回 FileEvent 列表(近似还原)。
     * 注意：转换会有信息损失，仅用于增量聚合的上下文恢复。
     */
    private List<FileEvent> toEvents(ActivityBlock block) {
        FileEvent e = new FileEvent();
        e.setEventTime(block.getStartTime());
        e.setEventType("MODIFY");
        e.setPath("");
        e.setFileName("");
        e.setExtension("");
        e.setSize(0);
        return List.of(e);
    }
}
