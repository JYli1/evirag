package com.evirag.admin;

/**
 * 管理员接口资源不存在异常。
 *
 * <p>例如管理员尝试修改不存在的用户时抛出该异常，统一由全局异常处理器转换为 404 风格响应。</p>
 */
public class AdminNotFoundException extends RuntimeException {

    /**
     * 管理端资源不存在时抛出，最终由 GlobalExceptionHandler 转换为统一 JSON。
     */
    public AdminNotFoundException(String message) {
        super(message);
    }
}
