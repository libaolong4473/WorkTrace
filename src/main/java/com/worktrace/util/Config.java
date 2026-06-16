package com.worktrace.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 应用配置管理器。
 * 从 ~/.worktrace/config.properties 加载配置，提供默认值兜底。
 *
 * 职责：
 *   - 加载配置文件
 *   - 提供类型安全的配置读取方法
 *   - 管理默认监听目录、聚合阈值等运行参数
 */
public class Config {

    private static final Path CONFIG_DIR  = Path.of(System.getProperty("user.home"), ".worktrace");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    private final Properties props = new Properties();

    private static Config instance;

    private Config() {}

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
            instance.load();
        }
        return instance;
    }

    private void load() {
        // 加载默认值
        props.setProperty("watch.dirs",           System.getProperty("user.home") + "\\Desktop;" +
                                                  System.getProperty("user.home") + "\\Documents;" +
                                                  "E:\\opc\\WorkTrace;" +
                                                  "E:\\luolingbanyu_shopping");
        props.setProperty("watch.exclude.dirs",   ".git;.idea;node_modules;target;build;dist;out;.cache;__pycache__;ShaderCache;.vscode;.gradle;.worktrace;Thumbs.db;" +
                                                  "LarkCache;WeChat Files;WXWork;DingTalk;OneDrive");
        props.setProperty("watch.exclude.files",  ".log;.tmp;.wal;.journal;.ldb;.lock;.part;.download;.swp;.bak;.cache;.temp;.db-wal;.db-shm");
        props.setProperty("aggregate.gap.minutes", "5");
        props.setProperty("db.path",              CONFIG_DIR.resolve("worktrace.db").toString());
        props.setProperty("retention.file_event.days", "90");

        // 尝试覆盖用户配置
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                LogUtil.warn("加载配置文件失败，使用默认值: " + e.getMessage());
            }
        }
    }

    public String getString(String key)               { return props.getProperty(key); }
    public String getString(String key, String def)    { return props.getProperty(key, def); }
    public int getInt(String key, int def)             {
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
}
