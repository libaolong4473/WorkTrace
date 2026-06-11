package com.worktrace.collector;

import java.util.Map;

/**
 * 活动类别分类器。
 * 根据文件扩展名和路径特征判断活动类别。
 *
 * 分类规则：
 *   CODE     → java, py, js, ts, c, cpp, go, rs, kt, swift, sql ...
 *   DOCUMENT → doc, docx, pdf, md, txt, xlsx, pptx, csv ...
 *   IMAGE    → png, jpg, jpeg, gif, svg, webp, bmp, ico ...
 *   VIDEO    → mp4, avi, mkv, mov, wmv, flv ...
 *   CONFIG   → xml, json, yaml, yml, properties, toml, ini, env ...
 *   OTHER    → 其余所有
 *
 * 聚合引擎使用此类判断文件应归入哪个 ActivityBlock。
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
        Map.entry("hpp",      "CODE"),
        Map.entry("cs",       "CODE"),
        Map.entry("go",       "CODE"),
        Map.entry("rs",       "CODE"),
        Map.entry("kt",       "CODE"),
        Map.entry("swift",    "CODE"),
        Map.entry("sql",      "CODE"),
        Map.entry("sh",       "CODE"),
        Map.entry("bat",      "CODE"),
        Map.entry("ps1",      "CODE"),
        Map.entry("vue",      "CODE"),
        Map.entry("jsx",      "CODE"),
        Map.entry("tsx",      "CODE"),
        Map.entry("html",     "CODE"),
        Map.entry("css",      "CODE"),
        Map.entry("scss",     "CODE"),
        Map.entry("less",     "CODE"),
        // DOCUMENT
        Map.entry("doc",      "DOCUMENT"),
        Map.entry("docx",     "DOCUMENT"),
        Map.entry("pdf",      "DOCUMENT"),
        Map.entry("md",       "DOCUMENT"),
        Map.entry("txt",      "DOCUMENT"),
        Map.entry("xlsx",     "DOCUMENT"),
        Map.entry("xls",      "DOCUMENT"),
        Map.entry("pptx",     "DOCUMENT"),
        Map.entry("ppt",      "DOCUMENT"),
        Map.entry("csv",      "DOCUMENT"),
        Map.entry("rtf",      "DOCUMENT"),
        Map.entry("odt",      "DOCUMENT"),
        // IMAGE
        Map.entry("png",      "IMAGE"),
        Map.entry("jpg",      "IMAGE"),
        Map.entry("jpeg",     "IMAGE"),
        Map.entry("gif",      "IMAGE"),
        Map.entry("svg",      "IMAGE"),
        Map.entry("webp",     "IMAGE"),
        Map.entry("bmp",      "IMAGE"),
        Map.entry("ico",      "IMAGE"),
        Map.entry("tiff",     "IMAGE"),
        Map.entry("psd",      "IMAGE"),
        Map.entry("ai",       "IMAGE"),
        // VIDEO
        Map.entry("mp4",      "VIDEO"),
        Map.entry("avi",      "VIDEO"),
        Map.entry("mkv",      "VIDEO"),
        Map.entry("mov",      "VIDEO"),
        Map.entry("wmv",      "VIDEO"),
        Map.entry("flv",      "VIDEO"),
        Map.entry("webm",     "VIDEO"),
        Map.entry("mp3",      "VIDEO"),
        Map.entry("wav",      "VIDEO"),
        Map.entry("flac",     "VIDEO"),
        Map.entry("aac",      "VIDEO"),
        // CONFIG
        Map.entry("xml",      "CONFIG"),
        Map.entry("json",     "CONFIG"),
        Map.entry("yaml",     "CONFIG"),
        Map.entry("yml",      "CONFIG"),
        Map.entry("properties","CONFIG"),
        Map.entry("toml",     "CONFIG"),
        Map.entry("ini",      "CONFIG"),
        Map.entry("env",      "CONFIG"),
        Map.entry("cfg",      "CONFIG"),
        Map.entry("conf",     "CONFIG")
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

    /**
     * 判断两个类别是否相同。
     */
    public boolean isSameCategory(String ext1, String ext2) {
        return classify(ext1).equals(classify(ext2));
    }
}
