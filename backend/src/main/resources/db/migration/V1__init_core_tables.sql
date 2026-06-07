-- EviRAG 核心业务表初始化脚本。
-- 该脚本保持 MySQL 8 兼容，所有外键字段都配套索引，便于按用户、知识库、文档和会话快速查询。

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    email VARCHAR(255) NOT NULL COMMENT '邮箱地址',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '用户角色：USER 或 ADMIN',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE email_verification_codes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证码主键',
    email VARCHAR(255) NOT NULL COMMENT '接收验证码的邮箱',
    code_hash VARCHAR(255) NOT NULL COMMENT '验证码哈希',
    purpose VARCHAR(32) NOT NULL COMMENT '验证码用途',
    consumed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用',
    expires_at DATETIME(6) NOT NULL COMMENT '过期时间',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_email_codes_email_purpose_consumed (email, purpose, consumed),
    KEY idx_email_codes_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码表';

CREATE TABLE knowledge_bases (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识库主键',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description TEXT NULL COMMENT '知识库描述',
    chroma_collection VARCHAR(191) NOT NULL COMMENT 'Chroma 集合名称',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '知识库状态',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_bases_collection (chroma_collection),
    UNIQUE KEY uk_knowledge_bases_id_user (id, user_id),
    UNIQUE KEY uk_knowledge_bases_user_name (user_id, name),
    KEY idx_knowledge_bases_user_status (user_id, status),
    CONSTRAINT fk_knowledge_bases_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

CREATE TABLE documents (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文档主键',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库 ID',
    user_id BIGINT NOT NULL COMMENT '上传用户 ID',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_path VARCHAR(512) NOT NULL COMMENT '后端存储路径',
    content_type VARCHAR(128) NULL COMMENT '文件 MIME 类型',
    file_size_bytes BIGINT NOT NULL COMMENT '文件大小，单位字节',
    sha256 VARCHAR(64) NOT NULL COMMENT '文件 SHA-256 摘要',
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态',
    error_message TEXT NULL COMMENT '解析失败原因',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_documents_id_kb (id, knowledge_base_id),
    KEY idx_documents_user_created (user_id, created_at),
    KEY idx_documents_kb_user_status (knowledge_base_id, user_id, parse_status),
    KEY idx_documents_sha256 (sha256),
    CONSTRAINT fk_documents_knowledge_base_owner FOREIGN KEY (knowledge_base_id, user_id) REFERENCES knowledge_bases (id, user_id),
    CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

CREATE TABLE document_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文本块主键',
    document_id BIGINT NOT NULL COMMENT '所属文档 ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库 ID',
    chunk_index INT NOT NULL COMMENT '文档内文本块顺序',
    content TEXT NOT NULL COMMENT '文本块内容',
    token_count INT NULL COMMENT '估算 token 数',
    chroma_embedding_id VARCHAR(191) NOT NULL COMMENT 'Chroma 向量记录 ID',
    metadata JSON NULL COMMENT '文本块元数据',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_chunks_document_index (document_id, chunk_index),
    UNIQUE KEY uk_document_chunks_embedding_id (chroma_embedding_id),
    KEY idx_document_chunks_document_kb (document_id, knowledge_base_id),
    KEY idx_document_chunks_kb (knowledge_base_id),
    CONSTRAINT fk_document_chunks_document_kb FOREIGN KEY (document_id, knowledge_base_id) REFERENCES documents (id, knowledge_base_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档文本块表';

CREATE TABLE chat_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    knowledge_base_id BIGINT NULL COMMENT '关联知识库 ID',
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_sessions_id_user (id, user_id),
    KEY idx_chat_sessions_user_updated (user_id, updated_at),
    KEY idx_chat_sessions_kb (knowledge_base_id),
    KEY idx_chat_sessions_kb_user (knowledge_base_id, user_id),
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_sessions_knowledge_base_owner FOREIGN KEY (knowledge_base_id, user_id) REFERENCES knowledge_bases (id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    session_id BIGINT NOT NULL COMMENT '所属会话 ID',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    role VARCHAR(32) NOT NULL COMMENT '消息角色：USER 或 ASSISTANT',
    content LONGTEXT NOT NULL COMMENT '消息内容',
    citations JSON NULL COMMENT '引用来源',
    low_confidence TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否低置信度回答',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_chat_messages_session_created (session_id, created_at),
    KEY idx_chat_messages_session_user (session_id, user_id),
    KEY idx_chat_messages_user_created (user_id, created_at),
    CONSTRAINT fk_chat_messages_session_user FOREIGN KEY (session_id, user_id) REFERENCES chat_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计日志主键',
    admin_user_id BIGINT NOT NULL COMMENT '管理员用户 ID',
    action VARCHAR(128) NOT NULL COMMENT '操作动作',
    target_type VARCHAR(64) NOT NULL COMMENT '操作对象类型',
    target_id BIGINT NULL COMMENT '操作对象 ID',
    detail JSON NULL COMMENT '审计详情',
    ip_address VARCHAR(64) NULL COMMENT '请求 IP',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_admin_audit_logs_admin_created (admin_user_id, created_at),
    KEY idx_admin_audit_logs_target (target_type, target_id),
    CONSTRAINT fk_admin_audit_logs_admin FOREIGN KEY (admin_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员审计日志表';
