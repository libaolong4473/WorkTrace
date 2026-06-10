package com.worktrace.ui.view;

import com.worktrace.model.ActivityBlock;

import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 时间线视图组件。
 * 将 ActivityBlock 列表渲染为可视化时间线。
 *
 * 职责：
 *   - 以条形图/色块形式展示各活动时段
 *   - 标注活动类别和时间段
 *   - 支持鼠标悬停查看详情
 *
 * 扩展点：
 *   后续可替换为 Canvas 自绘或引入图表库。
 */
public class TimelineView extends VBox {

    public TimelineView() {
        setSpacing(2);
        setPrefWidth(280);
    }

    /**
     * 用给定的活动块列表刷新时间线。
     */
    public void updateTimeline(List<ActivityBlock> blocks) {
        getChildren().clear();
        for (ActivityBlock block : blocks) {
            getChildren().add(createBlockNode(block));
        }
    }

    private javafx.scene.Node createBlockNode(ActivityBlock block) {
        // TODO: 实现活动块的可视化节点
        var label = new javafx.scene.control.Label(
            block.getCategory() + " " + block.getSummary()
        );
        label.setPrefWidth(260);
        label.setStyle("-fx-background-color: #E3F2FD; -fx-padding: 4 8; -fx-background-radius: 4;");
        return label;
    }
}
