package com.worktrace.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectDetector - 项目识别")
class ProjectDetectorTest {

    private ProjectDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new ProjectDetector(null); // 测试中不需要真实 DB
    }

    // ==================== 标志文件检测 ====================

    @Nested
    @DisplayName("标志文件检测")
    class MarkerDetection {

        @Test
        @DisplayName("发现 .git → 识别为项目根目录")
        void gitMarker() throws IOException {
            Path projectRoot = tempDir.resolve("MyProject");
            Files.createDirectories(projectRoot);
            Files.createDirectories(projectRoot.resolve(".git"));

            Path sourceFile = projectRoot.resolve("src").resolve("Main.java");
            Files.createDirectories(sourceFile.getParent());
            Files.createFile(sourceFile);

            var result = detector.detect(sourceFile.toString());
            assertTrue(result.isPresent());
            assertEquals("MyProject", result.get().getProjectName());
        }

        @Test
        @DisplayName("发现 pom.xml → 识别为 Maven 项目")
        void pomMarker() throws IOException {
            Path projectRoot = tempDir.resolve("WorkTrace");
            Files.createDirectories(projectRoot);
            Files.createFile(projectRoot.resolve("pom.xml"));

            Path sourceFile = projectRoot.resolve("src").resolve("App.java");
            Files.createDirectories(sourceFile.getParent());
            Files.createFile(sourceFile);

            var result = detector.detect(sourceFile.toString());
            assertTrue(result.isPresent());
            assertEquals("WorkTrace", result.get().getProjectName());
        }

        @Test
        @DisplayName("发现 package.json → 识别为 Node 项目")
        void packageJsonMarker() throws IOException {
            Path projectRoot = tempDir.resolve("MyApp");
            Files.createDirectories(projectRoot);
            Files.createFile(projectRoot.resolve("package.json"));

            Path sourceFile = projectRoot.resolve("index.js");
            Files.createFile(sourceFile);

            var result = detector.detect(sourceFile.toString());
            assertTrue(result.isPresent());
            assertEquals("MyApp", result.get().getProjectName());
        }

        @Test
        @DisplayName("发现 Cargo.toml → 识别为 Rust 项目")
        void cargoMarker() throws IOException {
            Path projectRoot = tempDir.resolve("my-rust-app");
            Files.createDirectories(projectRoot);
            Files.createFile(projectRoot.resolve("Cargo.toml"));

            Path sourceFile = projectRoot.resolve("src").resolve("main.rs");
            Files.createDirectories(sourceFile.getParent());
            Files.createFile(sourceFile);

            var result = detector.detect(sourceFile.toString());
            assertTrue(result.isPresent());
            assertEquals("my-rust-app", result.get().getProjectName());
        }
    }

    // ==================== 无标志文件 ====================

    @Nested
    @DisplayName("无标志文件")
    class NoMarker {

        @Test
        @DisplayName("普通目录无标志文件 → 返回 empty")
        void noMarkerFile() throws IOException {
            Path dir = tempDir.resolve("random");
            Files.createDirectories(dir);
            Path file = dir.resolve("file.txt");
            Files.createFile(file);

            var result = detector.detect(file.toString());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 路径 → 返回 empty")
        void nullPath() {
            assertTrue(detector.detect(null).isEmpty());
        }

        @Test
        @DisplayName("空路径 → 返回 empty")
        void emptyPath() {
            assertTrue(detector.detect("").isEmpty());
        }
    }

    // ==================== 缓存 ====================

    @Nested
    @DisplayName("缓存行为")
    class Caching {

        @Test
        @DisplayName("第二次查询同项目文件 → 缓存命中")
        void cacheHit() throws IOException {
            Path projectRoot = tempDir.resolve("CachedProject");
            Files.createDirectories(projectRoot);
            Files.createFile(projectRoot.resolve("pom.xml"));

            Path file1 = projectRoot.resolve("A.java");
            Path file2 = projectRoot.resolve("B.java");
            Files.createFile(file1);
            Files.createFile(file2);

            var result1 = detector.detect(file1.toString());
            var result2 = detector.detect(file2.toString());

            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
            assertEquals(result1.get().getRootPath(), result2.get().getRootPath());
        }

        @Test
        @DisplayName("clearCache 后重新检测")
        void clearCache() throws IOException {
            Path projectRoot = tempDir.resolve("ClearCacheProject");
            Files.createDirectories(projectRoot);
            Files.createFile(projectRoot.resolve(".git"));

            Path file = projectRoot.resolve("X.java");
            Files.createFile(file);

            detector.detect(file.toString());
            assertTrue(detector.cacheSize() > 0);

            detector.clearCache();
            assertEquals(0, detector.cacheSize());
        }
    }
}
