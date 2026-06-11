package com.evirag.knowledge;

import com.evirag.config.AppProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库业务服务。
 *
 * <p>该服务只服务普通用户路径，所有查询都以当前 JWT 用户 ID 为边界；管理员统计和跨用户查询
 * 后续应放到 admin 模块，避免普通列表接口被扩展成高权限入口。</p>
 */
@Service
public class KnowledgeBaseService {

    private final AppProperties appProperties;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseService(AppProperties appProperties, KnowledgeBaseRepository knowledgeBaseRepository) {
        this.appProperties = appProperties;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Transactional
    public KnowledgeBaseResponse create(Long userId, KnowledgeBaseRequest request) {
        // collection 名中带 userId 和随机 UUID，既方便排查归属，又避免不同知识库重名。
        String collection = appProperties.getChroma().getCollectionPrefix()
                + userId
                + "_"
                + UUID.randomUUID().toString().replace("-", "");
        KnowledgeBase knowledgeBase = KnowledgeBase.create(userId, request.name(), request.description(), collection);
        return KnowledgeBaseResponse.from(knowledgeBaseRepository.save(knowledgeBase));
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseResponse getById(Long userId, Long knowledgeBaseId) {
        // 用 findByIdAndUserId 保证普通用户只能读取自己的知识库。
        return knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .map(KnowledgeBaseResponse::from)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseResponse> listByCurrentUser(Long userId) {
        // 列表接口只返回当前用户数据，不提供全局列表。
        return knowledgeBaseRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }
}
