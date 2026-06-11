package com.worktrace.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 右侧详情面板中的文件列表单元格。
 *
 * 渲染效果：
 * ┌────────────────────────────────────┐
 * │  📄 MainController.java            │
 * │     src/main/java/.../controller/  │
 * └────────────────────────────────────┘
 */
public class FileDetailCell extends ListCell<String> {

    private final HBox root = new HBox(8);
    private final Label icon = new Label();
    private final VBox textBox = new VBox(2);
    private final Label fileName = new Label();
    private final Label filePath = new Label();

    public FileDetailCell() {
        icon.setText("📄");
        icon.setStyle("-fx-font-size: 14px;");

        fileName.getStyleClass().add("detail-file-name");
        filePath.getStyleClass().add("detail-file-path");

        textBox.getChildren().addAll(fileName, filePath);
        textBox.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(icon, textBox);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6, 8, 6, 8));
        root.getStyleClass().add("detail-file-item");

        HBox.setHgrow(textBox, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        // 分离文件名和路径
        int lastSep = Math.max(item.lastIndexOf('/'), item.lastIndexOf('\\'));
        if (lastSep > 0 && lastSep < item.length() - 1) {
            fileName.setText(item.substring(lastSep + 1));
            filePath.setText(item.substring(0, lastSep));
        } else {
            fileName.setText(item);
            filePath.setText("");
        }

        // 根据扩展名设置图标
        icon.setText(getFileIcon(item));

        setGraphic(root);
    }

    private String getFileIcon(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java"))   return "☕";
        if (lower.endsWith(".py"))     return "🐍";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "📜";
        if (lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yaml")) return "⚙";
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".doc")) return "📝";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".svg")) return "🖼";
        if (lower.endsWith(".sql"))    return "🗃";
        if (lower.endsWith(".fxml") || lower.endsWith(".css")) return "🎨";
        return "📄";
    }
}
