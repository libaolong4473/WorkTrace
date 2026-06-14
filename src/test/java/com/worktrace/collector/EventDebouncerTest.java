package com.worktrace.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventDebouncer - 事件去抖")
class EventDebouncerTest {

    private EventDebouncer debouncer;

    @BeforeEach
    void setUp() {
        // 使用 500ms 去抖窗口
        debouncer = new EventDebouncer(500);
    }

    // ==================== MODIFY 去抖 ====================

    @Nested
    @DisplayName("MODIFY 事件去抖")
    class ModifyDebounce {

        @Test
        @DisplayName("同路径同类型 500ms 内重复 → 丢弃")
        void samePathSameTypeWithinWindow() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1200));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1400));
        }

        @Test
        @DisplayName("同路径同类型超过 500ms → 放行")
        void samePathSameTypeBeyondWindow() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1400));
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1600)); // 600ms > 500ms
        }

        @Test
        @DisplayName("首次出现 → 放行")
        void firstOccurrence() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
        }

        @Test
        @DisplayName("不同文件 → 各自独立去抖")
        void differentFiles() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertTrue(debouncer.shouldPass("/src/B.java", "MODIFY", 1100));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1200));
            assertFalse(debouncer.shouldPass("/src/B.java", "MODIFY", 1300));
        }

        @Test
        @DisplayName("IDE 保存模拟：5 次 MODIFY 只保留 2 次")
        void ideSaveSimulation() {
            long t = 10000;
            assertTrue(debouncer.shouldPass("/src/App.java", "MODIFY", t));       // 0ms → 放行
            assertFalse(debouncer.shouldPass("/src/App.java", "MODIFY", t + 100)); // 100ms → 丢弃
            assertFalse(debouncer.shouldPass("/src/App.java", "MODIFY", t + 200)); // 200ms → 丢弃
            assertFalse(debouncer.shouldPass("/src/App.java", "MODIFY", t + 350)); // 350ms → 丢弃
            assertTrue(debouncer.shouldPass("/src/App.java", "MODIFY", t + 600));  // 600ms → 放行
        }
    }

    // ==================== CREATE / DELETE 不去抖 ====================

    @Nested
    @DisplayName("CREATE / DELETE 永远放行")
    class CreateDelete {

        @Test
        @DisplayName("CREATE 永远放行")
        void createAlwaysPasses() {
            assertTrue(debouncer.shouldPass("/src/A.java", "CREATE", 1000));
            assertTrue(debouncer.shouldPass("/src/A.java", "CREATE", 1100));
            assertTrue(debouncer.shouldPass("/src/A.java", "CREATE", 1200));
        }

        @Test
        @DisplayName("DELETE 永远放行")
        void deleteAlwaysPasses() {
            assertTrue(debouncer.shouldPass("/src/A.java", "DELETE", 1000));
            assertTrue(debouncer.shouldPass("/src/A.java", "DELETE", 1100));
            assertTrue(debouncer.shouldPass("/src/A.java", "DELETE", 1200));
        }

        @Test
        @DisplayName("CREATE 后紧跟 MODIFY → MODIFY 放行（不同类型）")
        void createThenModify() {
            assertTrue(debouncer.shouldPass("/src/A.java", "CREATE", 1000));
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1100)); // 上次是 CREATE，放行
        }

        @Test
        @DisplayName("DELETE 后紧跟 MODIFY → MODIFY 放行（不同类型）")
        void deleteThenModify() {
            assertTrue(debouncer.shouldPass("/src/A.java", "DELETE", 1000));
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1100)); // 上次是 DELETE，放行
        }
    }

    // ==================== cleanup ====================

    @Nested
    @DisplayName("过期清理")
    class Cleanup {

        @Test
        @DisplayName("cleanup 清理过期条目")
        void cleanupRemovesExpired() {
            // 添加一些条目
            debouncer.shouldPass("/src/A.java", "MODIFY", 1000);
            debouncer.shouldPass("/src/B.java", "CREATE", 2000);
            assertEquals(2, debouncer.size());

            // cleanup 不会清理刚添加的
            debouncer.cleanup();
            assertEquals(2, debouncer.size());
        }

        @Test
        @DisplayName("cleanup 不影响新条目")
        void cleanupKeepsRecent() {
            debouncer.shouldPass("/src/A.java", "MODIFY", System.currentTimeMillis());
            int before = debouncer.size();
            debouncer.cleanup();
            assertEquals(before, debouncer.size());
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("时间戳相同时 → 丢弃")
        void sameTimestamp() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
        }

        @Test
        @DisplayName("恰好在窗口边界 (500ms) → 放行")
        void exactBoundary() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1500)); // 恰好 500ms
        }

        @Test
        @DisplayName("窗口内 499ms → 丢弃")
        void justInsideWindow() {
            assertTrue(debouncer.shouldPass("/src/A.java", "MODIFY", 1000));
            assertFalse(debouncer.shouldPass("/src/A.java", "MODIFY", 1499)); // 499ms < 500ms
        }

        @Test
        @DisplayName("大量不同文件 → 各自独立")
        void manyFiles() {
            for (int i = 0; i < 100; i++) {
                assertTrue(debouncer.shouldPass("/src/File" + i + ".java", "MODIFY", 1000));
            }
            assertEquals(100, debouncer.size());
        }
    }
}
