package com.evirag.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。
 *
 * <p>认证流程只通过邮箱查找用户，用户名暂时沿用邮箱，后续个人资料功能再扩展独立用户名。</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
