package com.evirag.document;

/**
 * 文档解析状态。
 *
 * <p>Task 4 上传后进入 PROCESSING；READY/FAILED 供同步解析结果或 Task 5 索引流程复用。</p>
 */
public enum DocumentStatus {
    // 文件已保存，后台索引任务还在解析、切片、embedding 或写 Chroma。
    PROCESSING,
    // 文档已成功生成切片并写入向量库，可以参与问答检索。
    READY,
    // 文档处理失败，错误阶段和摘要保存在 Document 中。
    FAILED
}
