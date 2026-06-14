package com.worktrace.collector;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件去抖器 —— 合并短时间内同一文件的重复事件。
 *
 * 去抖规则：
 *   同一路径 + 同一事件类型 + 500ms 内重复 → 只保留第一次。
 *
 * 典型场景：
 *   IDE 保存文件时触发 3-5 次 MODIFY（buffer write, flush, metadata update），
 *   去抖后只保留第一次，减少 80% 重复事件。
 *
 * 时序图：
 *   10:00:01.000 MODIFY A.java  → 放行（首次）
 *   10:00:01.200 MODIFY A.java  → 丢弃（500ms 内）
 *   10:00:01.400 MODIFY A.java  → 丢弃（500ms 内）
 *   10:00:01.600 MODIFY A.java  → 放行（超过 500ms）
 *   10:00:02.000 CREATE B.java  → 放行（不同文件）
 *   10:00:02.100 DELETE A.java  → 放行（不同事件类型）
 *
 * 线程安全：使用 ConcurrentHashMap，支持多线程并发调用。
 */
public class EventDebouncer {

    /** 默认去抖窗口：500 毫秒 */
    private static final long DEFAULT_DEBOUNCE_MS = 500;

    /** 条目过期时间：60 秒未更新则清理 */
    private static final long ENTRY_EXPIRE_MS = 60_000;

    /**
     * 去抖条目：记录每个文件最后一次放行事件的时间戳和类型。
     */
    private static class DebounceEntry {
        final long timestamp;
        final String eventType;

        DebounceEntry(long timestamp, String eventType) {
            this.timestamp = timestamp;
            this.eventType = eventType;
        }
    }

    /** 绝对路径 → 最后一次放行的事件条目 */
    private final ConcurrentHashMap<String, DebounceEntry> entries = new ConcurrentHashMap<>();

    /** 去抖窗口（毫秒） */
    private final long debounceWindowMs;

    public EventDebouncer() {
        this(DEFAULT_DEBOUNCE_MS);
    }

    public EventDebouncer(long debounceWindowMs) {
        this.debounceWindowMs = debounceWindowMs;
    }

    /**
     * 判断事件是否应该放行。
     *
     * 规则：
     *   - CREATE / DELETE：永远放行（不做去抖）
     *   - MODIFY：同路径同类型在窗口内 → 丢弃
     *
     * @param filePath  文件路径
     * @param eventType 事件类型 (CREATE / MODIFY / DELETE)
     * @param timestamp 事件时间戳（毫秒，System.currentTimeMillis()）
     * @return true = 放行，false = 被去抖吞掉
     */
    public boolean shouldPass(Path filePath, String eventType, long timestamp) {
        String key = filePath.toAbsolutePath().toString();
        return shouldPassInternal(key, eventType, timestamp);
    }

    /**
     * 判断事件是否应该放行（字符串路径版本，用于测试）。
     */
    public boolean shouldPass(String absolutePath, String eventType, long timestamp) {
        return shouldPassInternal(absolutePath, eventType, timestamp);
    }

    private boolean shouldPassInternal(String key, String eventType, long timestamp) {
        // CREATE 和 DELETE 永远放行
        if (!"MODIFY".equals(eventType)) {
            entries.put(key, new DebounceEntry(timestamp, eventType));
            return true;
        }

        DebounceEntry last = entries.get(key);

        // 首次出现 → 放行
        if (last == null) {
            entries.put(key, new DebounceEntry(timestamp, eventType));
            return true;
        }

        // 同一文件在去抖窗口内的 MODIFY → 丢弃
        if ("MODIFY".equals(last.eventType) && timestamp - last.timestamp < debounceWindowMs) {
            return false;
        }

        // 超过去抖窗口或上次是不同类型 → 放行
        entries.put(key, new DebounceEntry(timestamp, eventType));
        return true;
    }

    /**
     * 清理过期条目，防止内存泄漏。
     * 建议每隔 1-5 分钟调用一次。
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> now - e.getValue().timestamp > ENTRY_EXPIRE_MS);
    }

    /**
     * 获取当前条目数（用于监控和测试）。
     */
    public int size() {
        return entries.size();
    }
}
