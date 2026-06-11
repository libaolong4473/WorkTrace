package com.worktrace.collector;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件防抖器 —— 同一文件短时间内多次 MODIFY 只保留最后一次。
 *
 * 原理：
 *   维护一个 Map<filePath, lastEventTimestamp>，
 *   当同一文件在 debounceWindowMs 内再次触发时，丢弃前一次。
 *
 * 线程安全：使用 ConcurrentHashMap，支持多线程并发调用。
 *
 * 使用场景：
 *   IDE 保存文件时可能触发 3-5 次 MODIFY 事件（buffer write, flush, metadata update），
 *   防抖后只保留最后一次，减少 80% 的重复事件。
 */
public class EventDebouncer {

    /** 默认防抖窗口：2 秒 */
    private static final long DEFAULT_DEBOUNCE_MS = 2000;

    /** filePath → 最后一次放行事件的时间戳 */
    private final ConcurrentHashMap<String, Long> lastEventTime = new ConcurrentHashMap<>();

    /** filePath → 最后一次放行的事件类型 */
    private final ConcurrentHashMap<String, String> lastEventType = new ConcurrentHashMap<>();

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
     * @param filePath  文件路径
     * @param eventType 事件类型 (CREATE / MODIFY / DELETE)
     * @param timestamp 事件时间戳(毫秒)
     * @return true = 放行，false = 被防抖吞掉
     */
    public boolean shouldPass(Path filePath, String eventType, long timestamp) {
        String key = filePath.toAbsolutePath().toString();

        // CREATE 和 DELETE 永远放行(不做防抖)
        if (!"MODIFY".equals(eventType)) {
            lastEventTime.put(key, timestamp);
            lastEventType.put(key, eventType);
            return true;
        }

        Long lastTime = lastEventTime.get(key);
        String lastType = lastEventType.get(key);

        // 首次出现 → 放行
        if (lastTime == null) {
            lastEventTime.put(key, timestamp);
            lastEventType.put(key, eventType);
            return true;
        }

        // 同一文件在防抖窗口内的 MODIFY → 丢弃
        if (timestamp - lastTime < debounceWindowMs && "MODIFY".equals(lastType)) {
            return false;
        }

        // 超过防抖窗口 → 放行
        lastEventTime.put(key, timestamp);
        lastEventType.put(key, eventType);
        return true;
    }

    /**
     * 清理过期条目(防止内存泄漏)。
     * 建议每隔 5 分钟调用一次。
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        lastEventTime.entrySet().removeIf(e -> now - e.getValue() > 60_000);
        lastEventType.entrySet().removeIf(e -> !lastEventTime.containsKey(e.getKey()));
    }
}
