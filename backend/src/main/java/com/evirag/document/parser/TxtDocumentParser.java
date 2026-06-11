package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TXT 文档解析器。
 *
 * <p>按 UTF-8 读取纯文本，并以非空段落生成 paragraph-N 位置，供前端展示解析细节。</p>
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String originalFilename) {
        // 简单文本文件只按 .txt 扩展名识别。
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".txt");
    }

    @Override
    public ParsedDocument parse(Path path, String originalFilename) {
        try {
            // 当前约定文本文件使用 UTF-8；若用户上传其他编码，解析失败会进入 FAILED 状态。
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return ParsedDocument.success(text, List.of(), paragraphPositions(text));
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_TXT", ex);
        }
    }

    static List<ParsedDocument.Position> paragraphPositions(String text) {
        List<ParsedDocument.Position> positions = new ArrayList<>();
        int cursor = 0;
        int index = 1;
        for (String paragraph : text.split("\\R\\s*\\R|\\R")) {
            // 用 cursor 限制搜索范围，避免重复段落总是匹配到第一次出现的位置。
            int start = text.indexOf(paragraph, cursor);
            if (!paragraph.isBlank() && start >= 0) {
                int end = start + paragraph.length();
                positions.add(new ParsedDocument.Position("paragraph-" + index, start, end));
                index++;
                cursor = end;
            }
        }
        return positions;
    }
}
