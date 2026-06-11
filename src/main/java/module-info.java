module com.worktrace {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;

    // JDBC
    requires java.sql;

    // AWT (Desktop.open())
    requires java.desktop;

    // 日志
    requires org.slf4j;

    // FXML 反射需要访问 controller 包
    opens com.worktrace.ui.controller to javafx.fxml;
    opens com.worktrace.ui.view     to javafx.fxml;

    // 模块导出
    exports com.worktrace.app;
    exports com.worktrace.model;
    exports com.worktrace.service;
    exports com.worktrace.service.impl;
    exports com.worktrace.collector;
    exports com.worktrace.database;
    exports com.worktrace.timeline;
    exports com.worktrace.util;
}
