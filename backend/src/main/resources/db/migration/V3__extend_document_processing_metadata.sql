-- 扩展文档处理状态字段。
-- V1 已经随基础工程提交，不能再直接修改历史迁移；这里通过增量迁移补齐解析失败阶段、原始错误摘要和切片数量。
ALTER TABLE documents
    MODIFY COLUMN parse_status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '解析状态',
    ADD COLUMN error_stage VARCHAR(64) NULL COMMENT '失败阶段' AFTER parse_status,
    MODIFY COLUMN error_message TEXT NULL COMMENT '面向用户展示的失败原因',
    ADD COLUMN raw_error_summary TEXT NULL COMMENT '原始错误摘要，前端用浅色调试文本展示' AFTER error_message,
    ADD COLUMN chunk_count INT NOT NULL DEFAULT 0 COMMENT '已写入的文本切片数量' AFTER raw_error_summary;

UPDATE documents
SET parse_status = 'PROCESSING'
WHERE parse_status = 'PENDING';
