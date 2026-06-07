package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.nio.file.Path;

/**
 * 文档解析器统一接口。
 *
 * <p>不同格式解析器只负责把原始文件转成纯文本和位置元数据；解析失败时返回 ParsedDocument.failed，
 * 不向外抛出包含本地路径或敏感配置的原始异常。</p>
 */
public interface DocumentParser {

    boolean supports(String originalFilename);

    ParsedDocument parse(Path path, String originalFilename);
}
