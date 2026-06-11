package com.evirag.config;

import com.evirag.auth.AuthException;
import com.evirag.auth.JwtService;
import com.evirag.auth.JwtService.JwtPrincipal;
import com.evirag.common.security.JsonAccessDeniedHandler;
import com.evirag.common.security.JsonAuthenticationEntryPoint;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 配置。
 *
 * <p>禁用表单登录和 Session，所有受保护接口使用 Bearer JWT；未认证和无权限响应接入统一 JSON 处理器。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository userRepository,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // 前后端分离 + JWT 模式下，后端不保存登录 Session。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 不使用 Spring Security 默认登录页。
                .formLogin(form -> form.disable())
                // 不使用浏览器弹窗式 Basic 认证。
                .httpBasic(basic -> basic.disable())
                // 认证失败和权限不足都输出统一 JSON，而不是跳转 HTML 页面。
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步派发和错误页必须放行，否则流式响应中途可能被 Security 拦截。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 这些接口是登录前必须访问的公开接口。
                        .requestMatchers(
                                "/api/auth/register/send-code",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/password/send-code",
                                "/api/auth/password/reset",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 管理端统一要求 ADMIN 角色。
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其他业务接口都要求已经通过 JWT 认证。
                        .anyRequest().authenticated())
                // 自定义 JWT 过滤器必须放在用户名密码认证过滤器之前，先把 Bearer token 转成认证态。
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 会自动加盐，适合保存用户密码哈希。
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 应用只接受 AuthController 登录和 JWT 鉴权，不启用 Spring Boot 默认内存用户。
        return username -> {
            throw new UsernameNotFoundException("应用未启用默认用户名密码登录");
        };
    }

    @Bean
    public Clock clock() {
        // 抽出 Clock 方便测试固定时间，也方便 JWT 过期判断可测。
        return Clock.systemUTC();
    }

    /**
     * Bearer Token 认证过滤器。
     *
     * <p>无 token 时放行给后续授权规则决定是否需要登录；有 token 但无效或用户已禁用时清空认证态，最终由 EntryPoint 输出统一 401。
     * 权限以数据库中的当前角色为准，不信任 token 中可能已经过期的角色快照。</p>
     */
    private static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtService jwtService;
        private final UserRepository userRepository;

        private JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
            this.jwtService = jwtService;
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    // 去掉 "Bearer " 前缀后解析 JWT。
                    JwtPrincipal principal = jwtService.parseToken(header.substring(7));
                    // token 只证明签名有效，仍要查数据库确认用户未被删除或禁用。
                    User user = userRepository.findById(principal.userId())
                            .filter(User::isActive)
                            .orElseThrow(() -> new AuthException("无效令牌"));
                    // 使用数据库当前角色，避免旧 token 中的角色快照继续生效。
                    JwtPrincipal currentPrincipal = new JwtPrincipal(user.getId(), user.getEmail(), user.getRole());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            currentPrincipal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                    );
                    // 写入 SecurityContext 后，Controller 才能通过 @AuthenticationPrincipal 取到当前用户。
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (AuthException ignored) {
                    // token 无效时清空认证态，让后续授权规则返回统一 401。
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }
            // 继续执行后面的过滤器和 Controller。
            filterChain.doFilter(request, response);
        }
    }
}
