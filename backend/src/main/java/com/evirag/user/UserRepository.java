package com.evirag.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。
 *
 * <p>认证流程只通过邮箱查找用户，用户名暂时沿用邮箱，后续个人资料功能再扩展独立用户名。</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA 会根据方法名自动生成 SQL：where email = ?
    Optional<User> findByEmail(String email);

    // 注册前检查邮箱是否已被使用。
    boolean existsByEmail(String email);

    // 管理员首页按 ACTIVE/DISABLED 统计用户数量。
    long countByStatus(String status);
}
