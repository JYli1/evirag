package com.evirag.admin;

/**
 * 管理员接口资源不存在异常。
 *
 * <p>例如管理员尝试修改不存在的用户时抛出该异常，统一由全局异常处理器转换为 404 风格响应。</p>
 */
public class AdminNotFoundException extends RuntimeException {

    public AdminNotFoundException(String message) {
        super(message);
    }
}
