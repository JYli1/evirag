package com.evirag.admin;

import com.evirag.admin.dto.AdminAuditLogResponse;
import com.evirag.admin.dto.AdminConfigStatusResponse;
import com.evirag.admin.dto.AdminDashboardResponse;
import com.evirag.admin.dto.AdminUserResponse;
import com.evirag.admin.dto.UpdateUserStatusRequest;
import com.evirag.auth.JwtService.JwtPrincipal;
import com.evirag.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员面板 REST 接口。
 *
 * <p>安全边界由 {@code SecurityConfig} 对 {@code /api/admin/**} 统一要求 ADMIN 角色；控制器只处理管理端业务参数。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    public AdminController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * 管理员首页统计卡片。
     */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.success(adminDashboardService.dashboard());
    }

    /**
     * 用户管理列表。
     */
    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> users() {
        return ApiResponse.success(adminDashboardService.listUsers());
    }

    /**
     * 启用或禁用指定用户，并记录管理员审计日志。
     */
    @PutMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminDashboardService.updateUserStatus(
                principal.userId(),
                userId,
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        ));
    }

    /**
     * 系统配置状态，只展示是否配置，不展示配置值。
     */
    @GetMapping("/system/config-status")
    public ApiResponse<AdminConfigStatusResponse> configStatus() {
        return ApiResponse.success(adminDashboardService.configStatus());
    }

    /**
     * 最近管理员操作日志。
     */
    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogResponse>> auditLogs() {
        return ApiResponse.success(adminDashboardService.auditLogs());
    }
}
