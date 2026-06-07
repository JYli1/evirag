-- 为文档切片表补充来源标题和来源位置。
-- V1 已创建 document_chunks 基础表，本迁移补齐前端引用证据展示所需的结构化来源字段。
ALTER TABLE document_chunks
    ADD COLUMN source_title VARCHAR(255) NULL COMMENT '来源标题' AFTER content,
    ADD COLUMN source_location VARCHAR(128) NULL COMMENT '页码或段落位置' AFTER source_title;
