package com.evirag.document;

/**
 * 文档上传业务异常。
 *
 * <p>用于向前端返回可展示的校验失败原因，例如类型不支持、文件过大或保存失败。</p>
 */
public class DocumentUploadException extends RuntimeException {

    /**
     * 用于文件类型、大小等可预期业务校验失败。
     */
    public DocumentUploadException(String message) {
        super(message);
    }

    /**
     * 用于保存文件等底层 IO 失败，保留 cause 方便后端日志排查。
     */
    public DocumentUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
