package com.worktrace.collector;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件噪声过滤器 —— 事件清洗第一道防线。
 *
 * 过滤规则（按优先级）：
 *   1. 噪声目录 → 整个子树跳过
 *   2. 噪声扩展名 → 直接丢弃
 *   3. 噪声关键词 → 文件名包含特定关键词
 *   4. 随机文件名 → hex hash / UUID / 纯数字
 *   5. 系统文件 → Thumbs.db / desktop.ini 等
 */
public class NoiseFilter {

    // ==================== 1. 噪声扩展名 ====================

    private static final Set<String> NOISE_EXTENSIONS = Set.of(
        // 系统临时文件
        "tmp", "temp",
        // 数据库日志/锁
        "wal", "journal", "ldb", "lock", "db-shm", "db-wal",
        // 缓存
        "cache",
        // 日志
        "log",
        // 编辑器临时文件
        "swp", "swo", "bak", "orig", "rej", "saving",
        // 系统隐藏文件
        "thumbs.db", "desktop.ini", "ds_store",
        // Chrome/Edge 缓存
        "crx"
    );

    // ==================== 2. 噪声目录 ====================

    private static final Set<String> NOISE_DIRS = Set.of(
        // 版本控制
        ".git", ".svn", ".hg",
        // IDE
        ".idea", ".vscode", ".eclipse", ".settings",
        // 构建产物
        "node_modules", "target", "build", "dist", "out", "bin", "obj",
        // 缓存目录
        "cache", "temp", "tmp", "__pycache__", ".cache",
        "shadercache", "gpucache", "code cache", "service worker",
        "blob_storage", "session storage",
        // 浏览器
        "browser", "crashpad", "crash_reports",
        // 系统
        ".worktrace", "logs", "$recycle.bin", "system volume information",
        // 微信/企业微信
        "wxwork", "wechat", "tencent"
    );

    // ==================== 3. 噪声关键词 ====================

    /** 文件名包含这些关键词 → 噪声 */
    private static final Set<String> NOISE_KEYWORDS = Set.of(
        "quotamanager",
        "cacheddata",
        "lockfile",
        "pidlock",
        "index-journal",
        "cookies-journal",
        "storage-journal"
    );

    // ==================== 4. 随机文件名模式 ====================

    /** 16-64 位十六进制 (如 3c5ea061b4bf1ea2, 3c5ea061b4bf1ea2_0) */
    private static final Pattern HEX_HASH = Pattern.compile("^[0-9a-f]{16,64}(_\\d+)?$");

    /** UUID (如 3c5ea061-b4bf-1ea2-a0b0-123456789abc) */
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    /** 纯数字文件名 ≥4 位 (如 000574, 12345678) */
    private static final Pattern NUMERIC_NAME = Pattern.compile("^\\d{4,}$");

    /** Chrome 缓存文件 (如 f_000001, data_0, 000003.log) */
    private static final Pattern CHROME_CACHE = Pattern.compile("^[a-z]_\\d{6}$|^[a-z]+_\\d+$|^\\d{6}\\.log$");

    /** 以 ~ 或 . 开头的临时文件 (如 ~$report.docx, .~lock.file) */
    private static final Pattern TEMP_PREFIX = Pattern.compile("^(~\\$|\\.~|\\.).+");

    // ==================== 公共 API ====================

    /**
     * 判断文件路径是否为噪声。
     *
     * @param filePath 文件完整路径
     * @return true = 噪声文件，应跳过
     */
    public static boolean isNoise(Path filePath) {
        if (filePath == null) return false;

        // 1. 噪声目录
        if (isUnderNoiseDir(filePath)) return true;

        String fileName = filePath.getFileName().toString();
        String lowerName = fileName.toLowerCase();

        // 2. 系统隐藏文件
        if (lowerName.equals("thumbs.db") || lowerName.equals("desktop.ini")
            || lowerName.equals(".ds_store")) return true;

        // 3. 噪声扩展名
        if (hasNoiseExtension(lowerName)) return true;

        // 4. 噪声关键词
        if (containsNoiseKeyword(lowerName)) return true;

        // 5. 随机文件名
        if (isRandomName(lowerName)) return true;

        // 6. Office 临时文件 (~$xxx.docx)
        if (TEMP_PREFIX.matcher(fileName).matches()) return true;

        return false;
    }

    // ==================== 内部实现 ====================

    private static boolean isUnderNoiseDir(Path path) {
        for (Path part : path) {
            if (NOISE_DIRS.contains(part.toString().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNoiseExtension(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        if (dot < 0 || dot == lowerName.length() - 1) return false;
        String ext = lowerName.substring(dot + 1);

        // 直接匹配
        if (NOISE_EXTENSIONS.contains(ext)) return true;

        // 模式匹配：*.tmp_xxx (如 xxx.tmp_20260611)
        if (ext.startsWith("tmp_") || ext.startsWith("temp_")) return true;

        // 模式匹配：*.db-wal, *.db-journal, *.db-shm
        if (ext.startsWith("db-") || ext.endsWith("-journal")) return true;

        return false;
    }

    private static boolean containsNoiseKeyword(String lowerName) {
        for (String keyword : NOISE_KEYWORDS) {
            if (lowerName.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean isRandomName(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        String base = (dot > 0) ? lowerName.substring(0, dot) : lowerName;

        if (HEX_HASH.matcher(base).matches()) return true;
        if (UUID_PATTERN.matcher(base).matches()) return true;
        if (NUMERIC_NAME.matcher(base).matches()) return true;
        if (CHROME_CACHE.matcher(base).matches()) return true;

        return false;
    }
}
