package com.evirag.common.api;

/**
 * 统一 API 响应体。
 *
 * <p>所有 REST 接口都应返回该结构，前端只需要根据 success、code、message 和 data 四个固定字段处理成功与失败状态。
 * 错误响应必须保留 code 和 message，避免前端只能解析 HTTP 状态码或异常文本。</p>
 *
 * @param <T> data 字段的业务数据类型
 */
public class ApiResponse<T> {

    /**
     * 请求是否成功；业务失败和系统异常都应为 false。
     */
    private boolean success;

    /**
     * 稳定响应码；成功时为 OK，失败时为 ApiErrorCode 中定义的错误码。
     */
    private String code;

    /**
     * 可展示给用户或前端统一弹窗的简短消息。
     */
    private String message;

    /**
     * 成功响应的业务数据；失败响应通常为 null。
     */
    private T data;

    public ApiResponse() {
        // Jackson 反序列化/序列化需要无参构造器；业务代码通常使用下面的静态工厂方法。
    }

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        // 成功响应统一带 OK，前端不需要为不同接口单独判断成功码。
        return new ApiResponse<>(true, ApiErrorCode.OK.getCode(), ApiErrorCode.OK.getMessage(), data);
    }

    public static ApiResponse<Void> success() {
        // 适合“发送验证码成功”“删除成功”这类没有 data 的接口。
        return success(null);
    }

    public static ApiResponse<Void> error(ApiErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> error(ApiErrorCode errorCode, String message) {
        // message 允许业务异常传入更具体的提示；为空时回退到错误码默认文案。
        String responseMessage = message == null || message.isBlank() ? errorCode.getMessage() : message;
        return new ApiResponse<>(false, errorCode.getCode(), responseMessage, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
