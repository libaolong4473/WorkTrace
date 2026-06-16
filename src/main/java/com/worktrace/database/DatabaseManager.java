package com.worktrace.database;

import com.worktrace.util.LogUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据库连接管理器 —— 单例。
 * 负责 SQLite 连接的创建、Schema 初始化、WAL 模式设置。
 */
public class DatabaseManager {

    private static final String DB_DIR  = System.getProperty("user.home")
                                        + "/.worktrace";
    private static final String DB_FILE = DB_DIR + "/worktrace.db";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_FILE;

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * 初始化数据库：创建目录 → 打开连接 → 设置 PRAGMA。
     * 建表由 DatabaseMigration 统一管理，不再依赖 schema.sql 解析。
     */
    public void initialize() throws SQLException, java.io.IOException {
        Files.createDirectories(Path.of(DB_DIR));
        connection = DriverManager.getConnection(DB_URL);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        LogUtil.info("数据库连接已建立: " + DB_URL);
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

}
