package com.worktrace.ui.view;

import com.worktrace.model.ActivityBlock;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * 时间线 ActivityBlock 的自定义单元格。
 *
 * 渲染效果：
 * ┌──────────────────────────────────────────────────┐
 * │  09:01 - 09:35                    34分钟  5文件   │
 * │  代码开发 · App.java 等5个文件    [CODE]          │
 * └──────────────────────────────────────────────────┘
 */
public class ActivityBlockCell extends ListCell<ActivityBlock> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HBox root = new HBox(12);
    private final VBox leftBox  = new VBox(4);
    private final VBox rightBox = new VBox(4);
    private final Label lblTime      = new Label();
    private final Label lblSummary   = new Label();
    private final Label lblCategory  = new Label();
    private final Label lblMeta      = new Label();

    public ActivityBlockCell() {
        // 左侧：时间 + 摘要
        leftBox.getChildren().addAll(lblTime, lblSummary);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // 右侧：类别标签 + 元信息
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.getChildren().addAll(lblCategory, lblMeta);

        root.getChildren().addAll(leftBox, rightBox);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(10, 14, 10, 14));
        root.getStyleClass().add("timeline-card");

        // 固定样式
        lblTime.getStyleClass().add("card-time");
        lblSummary.getStyleClass().add("card-summary");
        lblCategory.getStyleClass().add("card-category");
        lblMeta.getStyleClass().add("card-file-count");
    }

    @Override
    protected void updateItem(ActivityBlock item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        // 时间范围
        lblTime.setText(item.getStartTime().format(TIME_FMT) + " - " +
                        item.getEndTime().format(TIME_FMT));

        // 摘要
        lblSummary.setText(item.getSummary());

        // 类别标签
        lblCategory.setText(categoryLabel(item.getCategory()));
        lblCategory.getStyleClass().removeIf(s -> s.startsWith("card-category-"));
        lblCategory.getStyleClass().add("card-category-" + item.getCategory().toLowerCase());

        // 元信息：时长
        long minutes = Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
        lblMeta.setText(Math.max(minutes, 1) + "分钟");

        // 选中状态样式
        root.getStyleClass().remove("timeline-card-selected");

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
