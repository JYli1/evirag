# 课程设计报告大纲

## 1. 实验题目

基于 RAG 的智能文档问答系统：EviRAG 证据增强文档问答系统。

## 2. 实验目的

- 掌握前后端分离系统的设计与实现。
- 掌握文档解析、文本切分、embedding、向量检索和 LLM 生成的 RAG 流程。
- 掌握邮箱验证码认证、JWT 鉴权、用户隔离和管理员面板设计。

## 3. 项目背景与 RAG 原理

说明传统大模型直接问答容易缺少依据，RAG 通过检索知识库片段增强回答，并展示引用来源。

## 4. 需求分析

- 邮箱注册、登录和找回密码。
- 多知识库、多文档上传。
- 支持 PDF、TXT、DOCX、MD。
- 文档解析、切片、embedding 和 Chroma 入库。
- ChatGPT 式流式问答。
- 引用证据、相似度和低相关性标记。
- 管理员统计、配置状态和用户启停用。

## 5. 总体设计

说明 Vue3 前端、Spring Boot 后端、MySQL、Chroma、OpenAI 兼容 LLM、硅基流动 embedding 的关系。

## 6. 数据库设计

重点说明 `users`、`email_verification_codes`、`knowledge_bases`、`documents`、`document_chunks`、`chat_sessions`、`chat_messages`、`admin_audit_logs`。

## 7. 系统详细设计

- 认证模块
- 知识库模块
- 文档解析模块
- 文本切片模块
- Embedding 模块
- Chroma 检索模块
- LLM 调用模块
- RAG 编排模块
- SSE 聊天模块
- 管理员模块

## 8. RAG 流程设计

1. 读取历史消息。
2. 判断是否需要 query rewrite。
3. 将检索问题转为 embedding。
4. 通过 Chroma 按用户和知识库过滤 Top-K。
5. 组装 prompt。
6. 调用 LLM 流式生成。
7. 保存回答和引用证据。

## 9. 关键代码说明

建议截图或摘录：

- `OpenAiCompatibleEmbeddingClient`
- `ChromaClient`
- `QueryRewriteService`
- `RagService`
- `ChatService`
- `AdminDashboardService`
- 前端 `WorkbenchPage.vue`
- 前端 `EvidencePanel.vue`

## 10. 系统测试

引用 `docs/test-cases.md` 中的注册登录、文档上传、RAG 问答、管理员面板测试。

## 11. 运行截图清单

- 登录页和 EviRAG Logo
- 注册页验证码发送
- 工作台三栏布局
- 知识库创建
- 文档上传和解析状态
- RAG 流式回答
- 右侧引用证据
- 失败详情浅色展示
- 管理员统计面板
- 配置状态列表

## 12. 小结与心得

总结 RAG 系统工程化重点：数据隔离、错误可观测性、配置安全、检索结果解释性和前后端接口契约。
