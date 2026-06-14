package com.worktrace.ui.view;

import com.worktrace.model.WorkSession;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/**
 * WorkSession 时间线卡片单元格。
 *
 * 渲染效果：
 * ┌──────────────────────────────────────────────────┐
 * │  09:00 - 10:00                    CODE · 60分钟  │
 * │  WorkTrace 开发                                   │
 * │  WorkTrace · 5 个活动块 · 12 个文件              │
 * └──────────────────────────────────────────────────┘
 */
public class WorkSessionCell extends ListCell<WorkSession> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HBox root = new HBox(12);
    private final VBox leftBox  = new VBox(4);
    private final VBox rightBox = new VBox(4);
    private final Label lblTime      = new Label();
    private final Label lblTitle     = new Label();
    private final Label lblMeta      = new Label();
    private final Label lblCategory  = new Label();
    private final Label lblDuration  = new Label();

    public WorkSessionCell() {
        // 左侧：时间 + 标题 + 元信息
        leftBox.getChildren().addAll(lblTime, lblTitle, lblMeta);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // 右侧：类别 + 时长
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.getChildren().addAll(lblCategory, lblDuration);

        root.getChildren().addAll(leftBox, rightBox);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(12, 16, 12, 16));
        root.getStyleClass().add("timeline-card");

        // 样式
        lblTime.getStyleClass().add("card-time");
        lblTitle.getStyleClass().add("card-summary");
        lblMeta.getStyleClass().add("card-file-count");
        lblCategory.getStyleClass().add("card-category");
        lblDuration.getStyleClass().add("card-file-count");
    }

    @Override
    protected void updateItem(WorkSession item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        // 时间范围
        lblTime.setText(item.getStartTime().format(TIME_FMT) + " - " +
                        item.getEndTime().format(TIME_FMT));

        // 标题
        lblTitle.setText(item.getTitle());

        // 元信息：项目 · 活动块数 · 文件数
        String project = item.getProjectName() != null && !item.getProjectName().isEmpty()
            ? item.getProjectName() : "未分类";
        lblMeta.setText(project + " · " + item.getBlockCount() + " 个活动块 · " +
                        item.getFileCount() + " 个文件");

        // 类别标签
        lblCategory.setText(categoryLabel(item.getCategory()));
        lblCategory.getStyleClass().removeIf(s -> s.startsWith("card-category-"));
        lblCategory.getStyleClass().add("card-category-" + item.getCategory().toLowerCase());

        // 时长
        lblDuration.setText(item.getDurationMinutes() + " 分钟");

        setGraphic(root);
    }

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        if (root.getStyleClass().contains("timeline-card-selected") != selected) {
            if (selected) {
                root.getStyleClass().add("timeline-card-selected");
            } else {
                root.getStyleClass().remove("timeline-card-selected");
            }
        }
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "CODE"     -> "代码";
            case "DOCUMENT" -> "文档";
            case "IMAGE"    -> "图片";
            case "VIDEO"    -> "音视频";
            case "CONFIG"   -> "配置";
            default         -> "其他";
        };
    }
}
