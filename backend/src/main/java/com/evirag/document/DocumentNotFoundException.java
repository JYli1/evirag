package com.evirag.document;

/**
 * 文档不存在或当前用户无权访问。
 *
 * <p>错误文案不区分不存在和无权限，避免暴露其他用户文档 ID。</p>
 */
public class DocumentNotFoundException extends RuntimeException {

    /**
     * 不接收外部自定义 message，保证对外文案始终不泄露资源归属。
     */
    public DocumentNotFoundException() {
        super("文档不存在或无权访问");
    }
}
