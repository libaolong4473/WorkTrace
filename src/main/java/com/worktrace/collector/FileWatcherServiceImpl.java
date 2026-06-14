package com.worktrace.collector;

import com.worktrace.util.Config;
import com.worktrace.util.LogUtil;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * FileWatcherService 的完整实现。
 *
 * 启动优化：
 *   - 目录注册在后台线程异步执行，不阻塞 UI
 *   - 可配置排除目录列表 (watch.exclude.dirs)
 *   - 默认排除 .git / node_modules / target / build / ShaderCache 等
 *
 * 线程模型：
 *   ┌───────────────────────────────────────────────────────┐
 *   │  [RegisterThread]  启动时异步遍历目录树并注册          │
 *   │       │                                               │
 *   │       ▼                                               │
 *   │  [WatchThread]     阻塞式轮询 WatchService            │
 *   │       │                                               │
 *   │       ▼                                               │
 *   │  [Listener]        回调 → FileEventRepo + Aggregator  │
 *   └───────────────────────────────────────────────────────┘
 */
public class FileWatcherServiceImpl implements FileWatcherService {

    /** 默认排除目录（硬编码保底） */
    private static final Set<String> DEFAULT_EXCLUDED = Set.of(
        ".git", ".idea", ".vscode", ".gradle",
        "node_modules", "target", "build", "dist", "out",
        ".cache", "__pycache__", "ShaderCache",
        ".worktrace", "Thumbs.db"
    );

    /** 文件大小上限 */
    private static final long MAX_SIZE_READ_BYTES = 100 * 1024 * 1024;

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    /** 合并后的排除目录集合（默认 + 用户配置） */
    private final Set<String> excludedDirs = new HashSet<>();

    /** 文件级噪声过滤器 */
    private final NoiseFilter noiseFilter = new NoiseFilter();

    private final Map<WatchKey, Path> watchKeyToPath = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> pathToWatchKey = new ConcurrentHashMap<>();
    private final Set<Path> rootDirs = ConcurrentHashMap.newKeySet();
    private final List<FileEventListener> listeners = new CopyOnWriteArrayList<>();

    /** 事件去抖器：500ms 内同文件同类型重复事件只保留第一次 */
    private final EventDebouncer debouncer = new EventDebouncer(500);

    /** 异步注册完成信号 */
    private volatile CountDownLatch registrationDone = new CountDownLatch(0);

    public FileWatcherServiceImpl() {
        // 合并默认排除 + 用户配置排除
        excludedDirs.addAll(DEFAULT_EXCLUDED);
        String userExcludes = Config.getInstance().getString("watch.exclude.dirs", "");
        if (userExcludes != null && !userExcludes.isBlank()) {
            for (String s : userExcludes.split(";")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    excludedDirs.add(trimmed);
                }
            }
        }
        LogUtil.info("排除目录列表: " + excludedDirs);
    }

    @Override
    public void start() {
        if (running) {
            LogUtil.warn("FileWatcherService 已在运行");
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            LogUtil.error("创建 WatchService 失败: " + e.getMessage());
            return;
        }

        running = true;

        // 启动事件轮询线程
        watchThread = new Thread(this::pollLoop, "worktrace-watcher");
        watchThread.setDaemon(true);
        watchThread.start();

        // 异步注册目录（不阻塞调用线程）
        if (!rootDirs.isEmpty()) {
            List<Path> dirsToRegister = List.copyOf(rootDirs);
            registrationDone = new CountDownLatch(1);
            Thread registerThread = new Thread(() -> {
                registerDirectoriesAsync(dirsToRegister);
                registrationDone.countDown();
            }, "worktrace-register");
            registerThread.setDaemon(true);
            registerThread.start();
        }

        LogUtil.info("FileWatcherService 已启动 (目录注册异步进行中)");
    }

    /**
     * 异步注册目录树。
     * 先注册根目录（立即开始接收事件），再递归注册子目录。
     */
    private void registerDirectoriesAsync(List<Path> dirs) {
        AtomicInteger totalRegistered = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            // 先注册根目录本身（立即生效）
            registerSingle(dir);
            totalRegistered.incrementAndGet();
            // 再递归注册子目录
            registerSubdirectories(dir, totalRegistered);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LogUtil.info("目录注册完成: " + totalRegistered.get() + " 个目录, 耗时 " + elapsed + "ms");
    }

    /**
     * 递归注册子目录（跳过排除目录）。
     */
    private void registerSubdirectories(Path root, AtomicInteger counter) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!running) return FileVisitResult.TERMINATE;
                    if (isExcluded(dir) && !dir.equals(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(root)) {
                        registerSingle(dir);
                        counter.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LogUtil.error("递归注册失败: " + root + " - " + e.getMessage());
        }
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
            registerSingle(dir);
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

    // ========== 事件轮询 ==========

    private void pollLoop() {
        LogUtil.info("监听线程已启动，等待事件...");
        long lastCleanup = System.currentTimeMillis();
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
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
                if (event.kind() == OVERFLOW) continue;
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                handleEvent(ev.kind(), dir.resolve(ev.context()));
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyToPath.remove(key);
                pathToWatchKey.remove(dir);
            }

            // 每 60 秒清理一次去抖器过期条目
            long now = System.currentTimeMillis();
            if (now - lastCleanup > 60_000) {
                debouncer.cleanup();
                lastCleanup = now;
            }
        }
        LogUtil.info("监听线程已退出");
    }

    // ========== 事件处理 ==========

    private void handleEvent(WatchEvent.Kind<?> kind, Path filePath) {
        // 1. 目录级排除
        if (isExcluded(filePath)) return;

        // 2. 噪声文件过滤（支持配置扩展）
        if (noiseFilter.isNoise(filePath)) return;

        String eventType = mapKind(kind);
        if (eventType == null) return;

        // 3. 新目录 → 注册监听(不记录事件)
        if (kind == ENTRY_CREATE && Files.isDirectory(filePath)) {
            registerSingle(filePath);
            return;
        }

        if (Files.isDirectory(filePath)) return;

        // 4. 事件防抖：2 秒内同文件重复 MODIFY 只保留最后一次
        if (!debouncer.shouldPass(filePath, eventType, System.currentTimeMillis())) {
            return;
        }

        long size = safeGetSize(filePath);

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

    private void registerSingle(Path dir) {
        if (pathToWatchKey.containsKey(dir)) return;
        try {
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            watchKeyToPath.put(key, dir);
            pathToWatchKey.put(dir, key);
        } catch (IOException e) {
            // 静默跳过无权限目录
        }
    }

    // ========== 工具方法 ==========

    /**
     * 判断路径是否在排除目录下。
     */
    private boolean isExcluded(Path path) {
        for (Path part : path) {
            if (excludedDirs.contains(part.toString())) {
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
