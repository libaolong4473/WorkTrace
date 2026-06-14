package com.worktrace.collector;

import com.worktrace.model.FileEvent;
import com.worktrace.util.Config;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件级噪声过滤器。
 *
 * 过滤策略（按优先级）：
 *   1. 文件名黑名单（Thumbs.db、desktop.ini 等系统文件）
 *   2. 扩展名黑名单（.wal、.journal、.tmp 等系统/临时文件）
 *   3. 噪声关键词匹配（QuotaManager、CachedData 等）
 *   4. 随机文件名识别（UUID、32位哈希、纯数字等）
 *   5. 临时文件前缀（~$、.~ 开头）
 *
 * 使用方式：
 *   NoiseFilter filter = new NoiseFilter();
 *   if (filter.isNoise(path)) return;   // Path 版本
 *   if (filter.isNoise(event)) return;   // FileEvent 版本
 *
 * 配置扩展：
 *   ~/.worktrace/config.properties
 *   watch.exclude.files=.log;.tmp;.bak;.custom
 */
public class NoiseFilter {

    // ==================== 默认排除扩展名 ====================

    private static final Set<String> DEFAULT_NOISE_EXTENSIONS = Set.of(
        // 临时文件
        "tmp", "temp", "bak", "swp", "swo", "orig", "rej", "saving",
        // 数据库/缓存
        "wal", "journal", "ldb", "lock", "db-wal", "db-shm", "db-journal",
        // 下载/传输
        "part", "download", "crdownload", "partial",
        // 日志
        "log", "logs",
        // 缓存
        "cache", "crx"
    );

    // ==================== 默认排除文件名 ====================

    private static final Set<String> DEFAULT_NOISE_FILENAMES = Set.of(
        "thumbs.db",
        "desktop.ini",
        ".ds_store",
        ".gitkeep",
        ".gitattributes"
    );

    // ==================== 噪声关键词 ====================

    private static final Set<String> NOISE_KEYWORDS = Set.of(
        "quotamanager",
        "cacheddata",
        "lockfile",
        "pidlock",
        "index-journal",
        "cookies-journal",
        "storage-journal"
    );

    // ==================== 随机文件名正则 ====================

    /** 32位十六进制哈希：3c5ea061b4bf1ea23c5ea061b4bf1ea2 */
    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-f]{32}$", Pattern.CASE_INSENSITIVE);

    /** 16-64位十六进制 + 可选数字后缀：3c5ea061b4bf1ea2_0 */
    private static final Pattern HEX_HASH = Pattern.compile("^[0-9a-f]{16,64}(_\\d+)?$", Pattern.CASE_INSENSITIVE);

    /** UUID：550e8400-e29b-41d4-a716-446655440000 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );

    /** 纯数字文件名 ≥4 位：000574、12345678 */
    private static final Pattern NUMERIC_NAME = Pattern.compile("^\\d{4,}$");

    /** Chrome 缓存：f_000001、data_0、000003.log */
    private static final Pattern CHROME_CACHE = Pattern.compile(
        "^[a-z]_\\d{6}$|^[a-z]+_\\d+$|^\\d{6}\\.log$",
        Pattern.CASE_INSENSITIVE
    );

    /** 临时文件前缀：~$report.docx、.~lock.file */
    private static final Pattern TEMP_PREFIX = Pattern.compile("^(~\\$|\\.~)");

    // ==================== 实例状态 ====================

    private final Set<String> noiseExtensions;

    public NoiseFilter() {
        // 合并默认扩展名 + 用户配置
        this.noiseExtensions = new HashSet<>(DEFAULT_NOISE_EXTENSIONS);

        String userExts = Config.getInstance().getString("watch.exclude.files", "");
        if (userExts != null && !userExts.isBlank()) {
            for (String ext : userExts.split(";")) {
                String trimmed = ext.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith(".")) {
                        trimmed = trimmed.substring(1);
                    }
                    noiseExtensions.add(trimmed);
                }
            }
        }
    }

    // ==================== 公共 API ====================

    /**
     * 判断文件路径是否为噪声。
     */
    public boolean isNoise(Path filePath) {
        if (filePath == null) return false;
        return isNoise(filePath.getFileName().toString());
    }

    /**
     * 判断文件事件是否为噪声。
     */
    public boolean isNoise(FileEvent event) {
        if (event == null) return false;
        String fileName = event.getFileName();
        if (fileName == null || fileName.isBlank()) return false;
        return isNoise(fileName);
    }

    /**
     * 判断文件名是否为噪声。
     * 按优先级检查：系统文件 → 扩展名 → 关键词 → 随机名 → 临时前缀。
     */
    public boolean isNoise(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;

        String lower = fileName.toLowerCase();

        // 1. 系统文件黑名单
        if (DEFAULT_NOISE_FILENAMES.contains(lower)) return true;

        // 2. 扩展名黑名单
        if (hasNoiseExtension(lower)) return true;

        // 3. 噪声关键词
        if (containsNoiseKeyword(lower)) return true;

        // 4. 随机文件名
        if (isRandomName(lower)) return true;

        // 5. 临时文件前缀 (~$、.~)
        if (TEMP_PREFIX.matcher(fileName).matches()) return true;

        return false;
    }

    // ==================== 内部实现 ====================

    private boolean hasNoiseExtension(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        if (dot < 0 || dot == lowerName.length() - 1) return false;
        String ext = lowerName.substring(dot + 1);

        // 直接匹配
        if (noiseExtensions.contains(ext)) return true;

        // 模式匹配：*.tmp_xxx、*.db-wal、*-journal
        if (ext.startsWith("tmp_") || ext.startsWith("temp_")) return true;
        if (ext.startsWith("db-") || ext.endsWith("-journal")) return true;

        return false;
    }

    private boolean containsNoiseKeyword(String lowerName) {
        for (String keyword : NOISE_KEYWORDS) {
            if (lowerName.contains(keyword)) return true;
        }
        return false;
    }

    private boolean isRandomName(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        String base = (dot > 0) ? lowerName.substring(0, dot) : lowerName;

        if (base.length() < 4) return false;

        if (HEX_32.matcher(base).matches()) return true;
        if (HEX_HASH.matcher(base).matches()) return true;
        if (UUID_PATTERN.matcher(base).matches()) return true;
        if (NUMERIC_NAME.matcher(base).matches()) return true;
        if (CHROME_CACHE.matcher(base).matches()) return true;

        return false;
    }
}
