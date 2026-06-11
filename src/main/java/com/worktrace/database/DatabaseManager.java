package com.worktrace.database;

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
     * 初始化数据库：创建目录 → 打开连接 → 执行 schema.sql。
     */
    public void initialize() throws SQLException, java.io.IOException {
        Files.createDirectories(Path.of(DB_DIR));
        connection = DriverManager.getConnection(DB_URL);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        runSchemaScript();
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void runSchemaScript() throws SQLException, java.io.IOException {
        var stream = ClassLoader.getSystemResourceAsStream("sql/schema.sql");
        if (stream == null) {
            // 备选：从模块资源读取
            stream = getClass().getModule().getResourceAsStream("sql/schema.sql");
        }
        if (stream == null) {
            throw new java.io.IOException("找不到资源 sql/schema.sql，请检查 src/main/resources/sql/schema.sql 是否存在");
        }
        String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        // 去掉 BOM 头(UTF-8 编辑器可能写入)
        if (sql.startsWith("﻿")) {
            sql = sql.substring(1);
        }
        try (Statement stmt = connection.createStatement()) {
            for (String s : sql.split(";")) {
                // 去除行注释和空白
                String cleaned = s.replaceAll("--.*", "").trim();
                if (!cleaned.isEmpty()) {
                    stmt.execute(cleaned);
                }
            }
        }
    }
}
