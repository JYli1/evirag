package com.evirag.document;

/**
 * 文档解析状态。
 *
 * <p>Task 4 上传后进入 PROCESSING；READY/FAILED 供同步解析结果或 Task 5 索引流程复用。</p>
 */
public enum DocumentStatus {
    PROCESSING,
    READY,
    FAILED
}
