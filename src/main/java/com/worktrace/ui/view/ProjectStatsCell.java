package com.worktrace.ui.view;

import com.worktrace.database.ProjectRepository.ProjectStats;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 项目统计列表单元格。
 *
 * 渲染效果：
 * ┌──────────────────────────────────────────────────────────┐
 * │  📁 WorkTrace                                            │
 * │  D:\projects\WorkTrace                                   │
 * │                                                          │
 * │  5 活动    120 分钟    18 文件    最后: 16:40             │
 * └──────────────────────────────────────────────────────────┘
 */
public class ProjectStatsCell extends ListCell<ProjectStats> {

    private final VBox root = new VBox(6);
    private final Label lblName = new Label();
    private final Label lblPath = new Label();
    private final HBox statsRow = new HBox(16);
    private final Label lblActivities = new Label();
    private final Label lblDuration = new Label();
    private final Label lblFiles = new Label();
    private final Label lblLastActive = new Label();

    public ProjectStatsCell() {
        lblName.setStyle("-fx-text-fill: #DDDDDD; -fx-font-size: 14px; -fx-font-weight: bold;");
        lblPath.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        String statStyle = "-fx-text-fill: #BBBBBB; -fx-font-size: 12px;";
        lblActivities.setStyle(statStyle);
        lblDuration.setStyle(statStyle);
        lblFiles.setStyle(statStyle);
        lblLastActive.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");

        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.getChildren().addAll(lblActivities, lblDuration, lblFiles, lblLastActive);

        root.getChildren().addAll(lblName, lblPath, statsRow);
        root.setPadding(new Insets(12, 16, 12, 16));
        root.setStyle("-fx-background-color: #35383A; -fx-background-radius: 8; "
            + "-fx-border-color: #3C3F41; -fx-border-radius: 8;");
    }

    @Override
    protected void updateItem(ProjectStats item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        lblName.setText("📁 " + item.projectName());
        lblPath.setText(item.rootPath());
        lblActivities.setText(item.activityCount() + " 活动");
        lblDuration.setText(item.totalMinutes() + " 分钟");
        lblFiles.setText(item.filesModified() + " 文件");

        String lastActive = item.lastActiveTime();
        if (lastActive != null && lastActive.length() >= 16) {
            lblLastActive.setText("最后: " + lastActive.substring(11, 16));
        } else {
            lblLastActive.setText("最后: --:--");
        }

        setGraphic(root);
    }
}
