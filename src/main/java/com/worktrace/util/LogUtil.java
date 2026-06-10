package com.worktrace.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日志工具类。
 * 轻量级控制台日志输出，后续可替换为 SLF4J / Logback。
 *
 * 职责：
 *   - 提供 info / warn / error 三个日志级别
 *   - 统一时间戳格式
 *   - 输出到控制台(开发阶段)和文件(后续扩展)
 */
public class LogUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String msg)  { log("INFO",  msg); }
    public static void warn(String msg)  { log("WARN",  msg); }
    public static void error(String msg) { log("ERROR", msg); }

    private static void log(String level, String msg) {
        System.out.printf("[%s] [%s] %s%n",
            LocalDateTime.now().format(FMT), level, msg);
    }
}
