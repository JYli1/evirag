package com.evirag.common.api;

/**
 * API 错误码枚举。
 *
 * <p>错误码面向前端和调用方保持稳定，后端内部异常类型可以调整，但响应中的 code/message 应保持可预期。</p>
 */
public enum ApiErrorCode {

    /**
     * 请求成功。
     */
    OK("OK", "请求成功"),

    /**
     * 请求参数格式正确但业务校验失败。
     */
    VALIDATION_FAILED("VALIDATION_FAILED", "请求参数校验失败"),

    /**
     * 请求体 JSON 格式错误或字段类型无法反序列化。
     */
    BAD_REQUEST("BAD_REQUEST", "请求格式错误"),

    /**
     * 当前请求未登录或登录态无效。
     */
    UNAUTHORIZED("UNAUTHORIZED", "未认证或登录已失效"),

    /**
     * 当前用户已认证但没有访问目标资源的权限。
     */
    FORBIDDEN("FORBIDDEN", "没有权限访问该资源"),

    /**
     * 后端未识别或未显式处理的异常。
     */
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;

    /**
     * 默认中文提示。具体业务异常可以覆盖 message，但 code 应保持稳定。
     */
    private final String message;

    ApiErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
