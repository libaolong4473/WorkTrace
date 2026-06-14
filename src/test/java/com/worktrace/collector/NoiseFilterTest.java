package com.worktrace.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoiseFilter - 文件级噪声过滤")
class NoiseFilterTest {

    private NoiseFilter filter;

    @BeforeEach
    void setUp() {
        filter = new NoiseFilter();
    }

    // ==================== 系统文件 ====================

    @Nested
    @DisplayName("系统文件过滤")
    class SystemFiles {

        @Test
        @DisplayName("Thumbs.db → 噪声")
        void thumbsDb() {
            assertTrue(filter.isNoise("Thumbs.db"));
            assertTrue(filter.isNoise("thumbs.db"));
        }

        @Test
        @DisplayName("desktop.ini → 噪声")
        void desktopIni() {
            assertTrue(filter.isNoise("desktop.ini"));
        }

        @Test
        @DisplayName(".DS_Store → 噪声")
        void dsStore() {
            assertTrue(filter.isNoise(".DS_Store"));
        }

        @Test
        @DisplayName("正常文件名 → 不是噪声")
        void normalFile() {
            assertFalse(filter.isNoise("README.md"));
            assertFalse(filter.isNoise("Main.java"));
        }
    }

    // ==================== 扩展名过滤 ====================

    @Nested
    @DisplayName("扩展名过滤")
    class Extensions {

        @Test
        @DisplayName(".log → 噪声")
        void logFile() {
            assertTrue(filter.isNoise("app.log"));
            assertTrue(filter.isNoise("000003.log"));
        }

        @Test
        @DisplayName(".tmp → 噪声")
        void tmpFile() {
            assertTrue(filter.isNoise("data.tmp"));
        }

        @Test
        @DisplayName(".wal → 噪声")
        void walFile() {
            assertTrue(filter.isNoise("kv.db-wal"));
        }

        @Test
        @DisplayName(".journal → 噪声")
        void journalFile() {
            assertTrue(filter.isNoise("QuotaManager-journal"));
        }

        @Test
        @DisplayName(".lock → 噪声")
        void lockFile() {
            assertTrue(filter.isNoise("session.lock"));
        }

        @Test
        @DisplayName(".swp → 噪声 (Vim临时文件)")
        void swpFile() {
            assertTrue(filter.isNoise(".main.java.swp"));
        }

        @Test
        @DisplayName(".bak → 噪声")
        void bakFile() {
            assertTrue(filter.isNoise("config.bak"));
        }

        @Test
        @DisplayName(".download → 噪声")
        void downloadFile() {
            assertTrue(filter.isNoise("file.download"));
        }

        @Test
        @DisplayName("正常扩展名 → 不是噪声")
        void normalExtensions() {
            assertFalse(filter.isNoise("Main.java"));
            assertFalse(filter.isNoise("report.docx"));
            assertFalse(filter.isNoise("photo.png"));
            assertFalse(filter.isNoise("style.css"));
        }

        @Test
        @DisplayName("无扩展名 → 不是噪声")
        void noExtension() {
            assertFalse(filter.isNoise("Makefile"));
            assertFalse(filter.isNoise("Dockerfile"));
        }
    }

    // ==================== 随机文件名检测 ====================

    @Nested
    @DisplayName("随机文件名检测")
    class RandomNames {

        @Test
        @DisplayName("32位十六进制哈希 → 噪声")
        void hexHash32() {
            assertTrue(filter.isNoise("3c5ea061b4bf1ea23c5ea061b4bf1ea2"));
        }

        @Test
        @DisplayName("16位十六进制 + 数字后缀 → 噪声")
        void hexHashWithSuffix() {
            assertTrue(filter.isNoise("3c5ea061b4bf1ea2_0"));
            assertTrue(filter.isNoise("aabbccdd11223344_1"));
        }

        @Test
        @DisplayName("UUID → 噪声")
        void uuid() {
            assertTrue(filter.isNoise("550e8400-e29b-41d4-a716-446655440000"));
        }

        @Test
        @DisplayName("纯数字 ≥4位 → 噪声")
        void numericName() {
            assertTrue(filter.isNoise("000574"));
            assertTrue(filter.isNoise("12345678"));
            assertTrue(filter.isNoise("00001"));
        }

        @Test
        @DisplayName("Chrome缓存文件 → 噪声")
        void chromeCache() {
            assertTrue(filter.isNoise("f_000001"));
            assertTrue(filter.isNoise("data_0"));
        }

        @Test
        @DisplayName("短正常文件名 → 不是噪声")
        void shortName() {
            assertFalse(filter.isNoise("a"));
            assertFalse(filter.isNoise("ab"));
            assertFalse(filter.isNoise("abc"));
        }
    }

    // ==================== 临时文件前缀 ====================

    @Nested
    @DisplayName("临时文件前缀")
    class TempPrefix {

        @Test
        @DisplayName("Word临时文件 ~$xxx.docx → 噪声")
        void wordTemp() {
            assertTrue(filter.isNoise("~$report.docx"));
            assertTrue(filter.isNoise("~$会议纪要.docx"));
        }

        @Test
        @DisplayName("LibreOffice临时文件 .~lock.xxx → 噪声")
        void libreOfficeTemp() {
            assertTrue(filter.isNoise(".~lock.file.odt"));
        }
    }

    // ==================== Path API ====================

    @Nested
    @DisplayName("Path API")
    class PathApi {

        @Test
        @DisplayName("Path.getFileName() 正确传递")
        void pathFileName() {
            Path p = Path.of("C:", "Users", "alan", "Documents", "app.log");
            assertTrue(filter.isNoise(p));
        }

        @Test
        @DisplayName("Path 正常文件 → 不是噪声")
        void pathNormal() {
            Path p = Path.of("C:", "Users", "alan", "Documents", "report.docx");
            assertFalse(filter.isNoise(p));
        }

        @Test
        @DisplayName("null Path → 不是噪声")
        void nullPath() {
            assertFalse(filter.isNoise((Path) null));
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("null 文件名 → 不是噪声")
        void nullFileName() {
            assertFalse(filter.isNoise((String) null));
        }

        @Test
        @DisplayName("空字符串 → 不是噪声")
        void emptyFileName() {
            assertFalse(filter.isNoise(""));
            assertFalse(filter.isNoise("   "));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertTrue(filter.isNoise("THUMBS.DB"));
            assertTrue(filter.isNoise("Thumbs.DB"));
            assertTrue(filter.isNoise("APP.LOG"));
        }

        @Test
        @DisplayName("带扩展名的随机文件名")
        void randomNameWithExt() {
            assertTrue(filter.isNoise("3c5ea061b4bf1ea2.dat"));
            assertTrue(filter.isNoise("550e8400-e29b-41d4-a716-446655440000.tmp"));
        }
    }
}
