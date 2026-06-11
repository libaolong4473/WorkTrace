package com.worktrace.collector;

import com.worktrace.util.LogUtil;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * FileWatcherService 的完整实现。
 *
 * 线程模型：
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  [WatchThread]  (单守护线程，阻塞式轮询 WatchService)           │
 * │       │                                                         │
 * │       │  take() → 获取 WatchKey                                  │
 * │       │  遍历事件 → 过滤 → 构建 FileEvent → 回调 listener       │
 * │       │  若新目录 → 递归注册                                     │
 * │       ▼                                                         │
 * │  [Listener]  (在 WatchThread 上同步执行)                        │
 * │       │                                                         │
 * │       ▼                                                         │
 * │  [FileEventDao.insert()]  (SQLite WAL 模式，写操作自动串行化)   │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 关键设计：
 *   1. watchDirMap: WatchKey → Path 双向映射，用于事件发生时反查目录
 *   2. rootDirs: 记录用户注册的根目录，用于 getWatchedDirectories()
 *   3. 新目录创建时自动递归注册，实现深度监听
 *   4. 忽略 .git / node_modules / target / __pycache__ 等噪声目录
 *   5. 程序退出时优雅关闭 WatchService
 */
public class FileWatcherServiceImpl implements FileWatcherService {

    /** 需要忽略的目录名集合 */
    private static final Set<String> IGNORED_DIRS = Set.of(
        ".git", "node_modules", "target", "__pycache__",
        ".idea", ".vscode", ".gradle", "build", "dist", ".worktrace"
    );

    /** 文件大小上限：超过此值的文件不记录大小(避免读取超大文件) */
    private static final long MAX_SIZE_READ_BYTES = 100 * 1024 * 1024; // 100MB

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    /** WatchKey → 所属目录路径 */
    private final Map<WatchKey, Path> watchKeyToPath = new ConcurrentHashMap<>();

    /** 目录路径 → WatchKey (用于 unwatch) */
    private final Map<Path, WatchKey> pathToWatchKey = new ConcurrentHashMap<>();

    /** 用户注册的根目录集合 */
    private final Set<Path> rootDirs = ConcurrentHashMap.newKeySet();

    /** 事件监听器列表(线程安全) */
    private final List<FileEventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void start() {
        if (running) {
            LogUtil.warn("FileWatcherService 已在运行，忽略重复启动");
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            LogUtil.error("创建 WatchService 失败: " + e.getMessage());
            return;
        }

        running = true;
        watchThread = new Thread(this::pollLoop, "worktrace-watcher");
        watchThread.setDaemon(true);
        watchThread.start();

        // 重新注册所有已配置的目录(支持 stop → start 重启场景)
        for (Path dir : rootDirs) {
            registerTree(dir);
        }

        LogUtil.info("FileWatcherService 已启动，监听 " + pathToWatchKey.size() + " 个目录");
    }

    @Override
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LogUtil.error("关闭 WatchService 失败: " + e.getMessage());
            }
        }
        // 只清空 WatchKey 映射，保留 rootDirs 以便 start() 时重新注册
        watchKeyToPath.clear();
        pathToWatchKey.clear();
        LogUtil.info("FileWatcherService 已停止");
    }

    @Override
    public void watchDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            LogUtil.warn("路径不是目录，跳过: " + dir);
            return;
        }
        rootDirs.add(dir.toAbsolutePath());
        if (running && watchService != null) {
            registerTree(dir);
        }
    }

    @Override
    public void watchDirectories(List<Path> dirs) {
        for (Path dir : dirs) {
            watchDirectory(dir);
        }
    }

    @Override
    public void unwatchDirectory(Path dir) {
        Path absDir = dir.toAbsolutePath();
        rootDirs.remove(absDir);
        // 取消该目录及其所有子目录的 WatchKey
        var iterator = watchKeyToPath.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().startsWith(absDir)) {
                entry.getKey().cancel();
                pathToWatchKey.remove(entry.getValue());
                iterator.remove();
            }
        }
        LogUtil.info("已取消监听: " + dir);
    }

    @Override
    public List<Path> getWatchedDirectories() {
        return List.copyOf(rootDirs);
    }

    @Override
    public void addEventListener(FileEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // ========== 核心轮询循环 ==========

    private void pollLoop() {
        LogUtil.info("监听线程已启动，等待事件... (已注册 " + watchKeyToPath.size() + " 个 WatchKey)");
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // 阻塞直到有事件
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path dir = watchKeyToPath.get(key);
            if (dir == null) {
                key.cancel();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                handleEvent(ev.kind(), dir.resolve(ev.context()));
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyToPath.remove(key);
                pathToWatchKey.remove(dir);
            }
        }
        LogUtil.info("监听线程已退出");
    }

    // ========== 事件处理 ==========

    private void handleEvent(WatchEvent.Kind<?> kind, Path filePath) {
        LogUtil.info("收到事件: " + kind + " → " + filePath);

        // 过滤被忽略的目录
        if (isUnderIgnoredDir(filePath)) {
            LogUtil.info("  → 已过滤(忽略目录)");
            return;
        }

        String eventType = mapKind(kind);
        if (eventType == null) {
            return;
        }

        // 如果是新创建的目录，自动注册递归监听
        if (kind == ENTRY_CREATE && Files.isDirectory(filePath)) {
            registerTree(filePath);
            // 目录创建事件本身不记录(没有文件大小意义)
            return;
        }

        // 只处理文件事件，不处理目录事件
        if (Files.isDirectory(filePath)) {
            return;
        }

        long size = safeGetSize(filePath);

        // 通知所有监听器
        for (FileEventListener listener : listeners) {
            try {
                listener.onFileEvent(eventType, filePath, size);
            } catch (Exception e) {
                LogUtil.error("事件回调异常: " + e.getMessage());
            }
        }
    }

    private String mapKind(WatchEvent.Kind<?> kind) {
        if (kind == ENTRY_CREATE)  return "CREATE";
        if (kind == ENTRY_MODIFY) return "MODIFY";
        if (kind == ENTRY_DELETE) return "DELETE";
        return null;
    }

    // ========== 目录注册 ==========

    /**
     * 递归注册目录树。遍历所有子目录，逐个注册到 WatchService。
     */
    private void registerTree(Path root) {
        if (!Files.isDirectory(root) || isUnderIgnoredDir(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isUnderIgnoredDir(dir) && !dir.equals(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    registerSingle(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LogUtil.error("递归注册目录失败: " + root + " - " + e.getMessage());
        }
    }

    /**
     * 将单个目录注册到 WatchService。
     */
    private void registerSingle(Path dir) {
        if (pathToWatchKey.containsKey(dir)) {
            return; // 已注册
        }
        try {
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            watchKeyToPath.put(key, dir);
            pathToWatchKey.put(dir, key);
            LogUtil.info("已注册监听: " + dir);
        } catch (IOException e) {
            LogUtil.warn("注册目录失败(可能无权限): " + dir + " - " + e.getMessage());
        }
    }

    // ========== 工具方法 ==========

    private boolean isUnderIgnoredDir(Path path) {
        for (Path part : path) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private long safeGetSize(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                long size = Files.size(filePath);
                return size <= MAX_SIZE_READ_BYTES ? size : -1;
            }
        } catch (IOException ignored) {
        }
        return 0;
    }
}
