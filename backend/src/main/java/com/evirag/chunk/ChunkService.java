package com.evirag.chunk;

import com.evirag.config.AppProperties;
import com.evirag.document.ParsedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 文本切分服务。
 *
 * <p>切分顺序为：先识别 Markdown 风格标题，再按段落聚合；当单段超过最大长度时，再使用带重叠的字符窗口切分。
 * 这样可以尽量保留文档结构，同时避免过长文本直接进入 embedding 接口。</p>
 */
@Service
public class ChunkService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\S(?:.|\\R)*?(?=(\\R\\s*\\R)|\\z)");

    // 单个切片最大字符数，来自 evirag.chunk.max-chars。
    private final int maxChars;
    // 超长段落窗口切分时的重叠字符数。
    private final int overlapChars;

    @Autowired
    public ChunkService(AppProperties appProperties) {
        // 生产环境从强类型配置中读取切片参数，避免在代码中写死。
        this(appProperties.getChunk().getMaxChars(), appProperties.getChunk().getOverlapChars());
    }

    /**
     * 测试专用构造器，便于用较小窗口验证切片长度和重叠逻辑。
     */
    public ChunkService(int maxChars, int overlapChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("切片最大长度必须大于 0");
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("切片重叠长度必须大于等于 0 且小于最大长度");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    /**
     * 将解析结果切成可索引文本块。
     *
     * <p>如果解析结果为空文本，直接返回空列表，由索引服务把文档标记为 CHUNK 阶段失败。</p>
     */
    public List<TextChunk> split(ParsedDocument parsedDocument) {
        if (parsedDocument == null || parsedDocument.text() == null || parsedDocument.text().isBlank()) {
            return List.of();
        }

        List<TextBlock> blocks = extractBlocks(parsedDocument);
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentTitle = null;
        String currentLocation = null;

        for (TextBlock block : blocks) {
            if (block.heading()) {
                // 标题本身也会进入当前切片，同时更新后续段落的 sourceTitle。
                currentTitle = block.text();
                currentLocation = appendToCurrent(chunks, currentText, block.text(), currentTitle, block.location(), currentLocation);
                continue;
            }
            if (block.text().length() > maxChars) {
                // 单段已经超过上限时，先把前面积累内容落盘，再对该段单独做窗口切分。
                flushCurrent(chunks, currentText, currentTitle, currentLocation);
                addWindowChunks(chunks, block, currentTitle);
                currentLocation = null;
                continue;
            }
            currentLocation = appendToCurrent(chunks, currentText, block.text(), currentTitle, block.location(), currentLocation);
        }

        flushCurrent(chunks, currentText, currentTitle, currentLocation);
        return chunks;
    }

    private String appendToCurrent(
            List<TextChunk> chunks,
            StringBuilder currentText,
            String text,
            String currentTitle,
            String blockLocation,
            String currentLocation
    ) {
        if (!currentText.isEmpty() && currentText.length() + 2 + text.length() > maxChars) {
            // 再追加会超长时，先形成一个切片，再从当前段落开始新的切片。
            flushCurrent(chunks, currentText, currentTitle, currentLocation);
            currentLocation = null;
        }
        if (currentText.isEmpty()) {
            // 记录这个切片首个段落的位置，作为整个切片的来源位置。
            currentLocation = blockLocation;
        }
        if (!currentText.isEmpty()) {
            // 段落之间保留空行，让送入 embedding 的文本更接近原文结构。
            currentText.append(System.lineSeparator()).append(System.lineSeparator());
        }
        currentText.append(text);
        return currentLocation;
    }

    /**
     * 抽取标题和段落块，同时尽量把解析器给出的 page-N / paragraph-N 位置映射到段落。
     */
    private List<TextBlock> extractBlocks(ParsedDocument parsedDocument) {
        List<TextBlock> blocks = new ArrayList<>();
        Matcher matcher = PARAGRAPH_PATTERN.matcher(parsedDocument.text());
        while (matcher.find()) {
            String raw = matcher.group().strip();
            if (raw.isBlank()) {
                continue;
            }
            Matcher headingMatcher = HEADING_PATTERN.matcher(raw);
            if (headingMatcher.matches()) {
                blocks.add(new TextBlock(true, headingMatcher.group(1).trim(), locationFor(parsedDocument, matcher.start())));
            } else {
                blocks.add(new TextBlock(false, raw, locationFor(parsedDocument, matcher.start())));
            }
        }
        return blocks;
    }

    /**
     * 根据字符偏移找到解析器给出的来源位置；找不到时返回 null，让后续元数据保持精简。
     */
    private String locationFor(ParsedDocument parsedDocument, int offset) {
        for (ParsedDocument.Position position : parsedDocument.positions()) {
            if (offset >= position.startOffset() && offset <= position.endOffset()) {
                return position.label();
            }
        }
        return null;
    }

    private void flushCurrent(List<TextChunk> chunks, StringBuilder currentText, String currentTitle, String currentLocation) {
        if (currentText.isEmpty()) {
            return;
        }
        // chunks.size() 正好是下一个切片序号，避免额外维护计数器。
        chunks.add(new TextChunk(chunks.size(), currentTitle, currentLocation, currentText.toString()));
        currentText.setLength(0);
    }

    /**
     * 对超长段落使用字符窗口切分，窗口之间保留 overlapChars，减少边界处上下文断裂。
     */
    private void addWindowChunks(List<TextChunk> chunks, TextBlock block, String currentTitle) {
        int start = 0;
        while (start < block.text().length()) {
            int end = Math.min(start + maxChars, block.text().length());
            String piece = block.text().substring(start, end);
            chunks.add(new TextChunk(chunks.size(), currentTitle, block.location(), piece));
            if (end == block.text().length()) {
                break;
            }
            // 下一段从 end - overlap 开始，让两个切片共享一小段上下文。
            start = Math.max(0, end - overlapChars);
        }
    }

    private record TextBlock(
            // true 表示该块是标题，false 表示普通段落。
            boolean heading,
            // 块文本。
            String text,
            // page-N / paragraph-N 等来源位置。
            String location
    ) {
    }
}
