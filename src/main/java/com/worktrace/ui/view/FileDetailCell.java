package com.worktrace.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * 右侧详情面板中的文件列表单元格。
 *
 * 功能：
 *   - 显示文件名 + 路径 + 图标
 *   - 双击使用系统默认应用打开文件
 *   - 悬停高亮 + 手型光标
 *   - 打开失败时弹出错误对话框
 *
 * 渲染效果：
 * ┌────────────────────────────────────┐
 * │  📄 MainController.java            │
 * │     src/main/java/.../controller/  │  ← 双击打开
 * └────────────────────────────────────┘
 */
public class FileDetailCell extends ListCell<String> {

    private static final String HOVER_STYLE =
        "-fx-background-color: #3C3F41; -fx-border-color: #555555;";
    private static final String NORMAL_STYLE =
        "-fx-background-color: #35383A; -fx-border-color: #3C3F41;";

    private final HBox root = new HBox(8);
    private final Label icon = new Label();
    private final VBox textBox = new VBox(2);
    private final Label fileName = new Label();
    private final Label filePath = new Label();

    /** 当前单元格对应的文件路径 */
    private String currentPath;

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

        // 悬停效果 + 手型光标
        root.setOnMouseEntered(e -> {
            if (currentPath != null) {
                root.setStyle(HOVER_STYLE);
                root.setCursor(Cursor.HAND);
            }
        });
        root.setOnMouseExited(e -> {
            root.setStyle(NORMAL_STYLE);
            root.setCursor(Cursor.DEFAULT);
        });

        // 双击打开文件
        root.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && currentPath != null) {
                openFile(currentPath);
            }
        });

        HBox.setHgrow(textBox, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            currentPath = null;
            return;
        }

        currentPath = item;

        // 分离文件名和路径
        int lastSep = Math.max(item.lastIndexOf('/'), item.lastIndexOf('\\'));
        if (lastSep > 0 && lastSep < item.length() - 1) {
            fileName.setText(item.substring(lastSep + 1));
            filePath.setText(item.substring(0, lastSep));
        } else {
            fileName.setText(item);
            filePath.setText("");
        }

        icon.setText(getFileIcon(item));
        root.setStyle(NORMAL_STYLE);
        setGraphic(root);
    }

    /**
     * 使用系统默认应用打开文件。
     */
    private void openFile(String path) {
        File file = new File(path);

        if (!file.exists()) {
            showError("文件不存在", "文件已被移动或删除：\n" + path);
            return;
        }

        if (!file.canRead()) {
            showError("权限不足", "无法读取文件：\n" + path);
            return;
        }

        try {
            Desktop.getDesktop().open(file);
        } catch (UnsupportedOperationException ex) {
            showError("不支持的操作", "系统不支持打开此类型的文件：\n" + path);
        } catch (IOException ex) {
            showError("打开失败", "无法打开文件：" + ex.getMessage() + "\n" + path);
        } catch (SecurityException ex) {
            showError("安全限制", "没有权限打开此文件：\n" + path);
        } catch (Exception ex) {
            showError("未知错误", "打开文件时发生错误：" + ex.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getFileIcon(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java"))   return "☕";
        if (lower.endsWith(".py"))     return "🐍";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "📜";
        if (lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yaml")) return "⚙";
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx")) return "📝";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg") || lower.endsWith(".gif")) return "🖼";
        if (lower.endsWith(".pdf"))    return "📕";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "📊";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📽";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) return "🎵";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")) return "🎬";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "📦";
        if (lower.endsWith(".sql"))    return "🗃";
        if (lower.endsWith(".fxml") || lower.endsWith(".css") || lower.endsWith(".html")) return "🎨";
        if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".sh")) return "⚙";
        return "📄";
    }
}
