package com.evirag.document;

/**
 * 文档上传业务异常。
 *
 * <p>用于向前端返回可展示的校验失败原因，例如类型不支持、文件过大或保存失败。</p>
 */
public class DocumentUploadException extends RuntimeException {

    public DocumentUploadException(String message) {
        super(message);
    }

    public DocumentUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
