package com.evirag.admin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理员审计日志仓储。
 *
 * <p>面板默认只展示最近 50 条操作，避免 demo 页面一次性拉取过多历史数据。</p>
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    // 管理员面板只展示最近 50 条，避免一次性加载全部日志。
    List<AdminAuditLog> findTop50ByOrderByCreatedAtDesc();
}
