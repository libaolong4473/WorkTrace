package com.worktrace.collector;

import java.util.Map;

/**
 * 活动类别分类器。
 * 根据文件扩展名和路径特征判断活动类别。
 *
 * 职责：
 *   - 根据文件扩展名判定类别
 *   - 支持自定义分类规则
 *
 * 分类规则：
 *   CODE     → java, py, js, ts, c, cpp, go, rs, kt, swift, sql ...
 *   DOCUMENT → doc, docx, pdf, md, txt, xlsx, pptx ...
 *   CONFIG   → xml, json, yaml, yml, properties, toml, ini, env ...
 *   MEDIA    → png, jpg, mp3, mp4, gif, svg, webp ...
 *   OTHER    → 其余所有
 */
public class CategoryClassifier {

    private static final Map<String, String> CATEGORY_RULES = Map.ofEntries(
        // CODE
        Map.entry("java",     "CODE"),
        Map.entry("py",       "CODE"),
        Map.entry("js",       "CODE"),
        Map.entry("ts",       "CODE"),
        Map.entry("c",        "CODE"),
        Map.entry("cpp",      "CODE"),
        Map.entry("h",        "CODE"),
        Map.entry("go",       "CODE"),
        Map.entry("rs",       "CODE"),
        Map.entry("kt",       "CODE"),
        Map.entry("swift",    "CODE"),
        Map.entry("sql",      "CODE"),
        Map.entry("sh",       "CODE"),
        Map.entry("bat",      "CODE"),
        Map.entry("vue",      "CODE"),
        Map.entry("jsx",      "CODE"),
        Map.entry("tsx",      "CODE"),
        // DOCUMENT
        Map.entry("doc",      "DOCUMENT"),
        Map.entry("docx",     "DOCUMENT"),
        Map.entry("pdf",      "DOCUMENT"),
        Map.entry("md",       "DOCUMENT"),
        Map.entry("txt",      "DOCUMENT"),
        Map.entry("xlsx",     "DOCUMENT"),
        Map.entry("pptx",     "DOCUMENT"),
        Map.entry("csv",      "DOCUMENT"),
        // CONFIG
        Map.entry("xml",      "CONFIG"),
        Map.entry("json",     "CONFIG"),
        Map.entry("yaml",     "CONFIG"),
        Map.entry("yml",      "CONFIG"),
        Map.entry("properties","CONFIG"),
        Map.entry("toml",     "CONFIG"),
        Map.entry("ini",      "CONFIG"),
        Map.entry("env",      "CONFIG"),
        // MEDIA
        Map.entry("png",      "MEDIA"),
        Map.entry("jpg",      "MEDIA"),
        Map.entry("jpeg",     "MEDIA"),
        Map.entry("gif",      "MEDIA"),
        Map.entry("svg",      "MEDIA"),
        Map.entry("mp3",      "MEDIA"),
        Map.entry("mp4",      "MEDIA"),
        Map.entry("webp",     "MEDIA")
    );

    /**
     * 根据文件扩展名判定活动类别。
     *
     * @param extension 文件扩展名(不含点)，可为 null 或空
     * @return 类别字符串，保证非 null
     */
    public String classify(String extension) {
        if (extension == null || extension.isBlank()) {
            return "OTHER";
        }
        return CATEGORY_RULES.getOrDefault(extension.toLowerCase(), "OTHER");
    }
}
