# EviRAG：基于 RAG 的智能文档问答系统设计文档

## 1. 项目目标

EviRAG 是一个前后端分离的课程设计系统，目标是实现一个完整的多知识库 RAG 智能文档问答平台。用户可以注册登录、创建知识库、上传多个文档，并基于知识库内容进行类似 ChatGPT 的流式问答。系统会展示回答引用的原文片段、相似度和来源文档，便于说明答案依据。

项目采用 demo 级可部署方案，不引入 Redis、消息队列和复杂权限系统，但保留必要的安全控制、错误可观测性和管理员面板。

## 2. 已确认约束

- 项目名称：EviRAG
- 中文名：证据增强文档问答系统
- 开源仓库名：`evirag`
- 项目目录：`D:\github_project\evirag`
- 后端：SpringBoot
- 前端：Vue3
- 数据库：MySQL
- 向量库：Chroma
- 大模型接口：OpenAI 兼容接口
- embedding 模型：硅基流动 embedding 接口
- 配置文件：所有运行配置集中放在后端 `.env`
- 文档格式：PDF、TXT、DOCX、MARKDOWN
- 单文件大小：最大 20MB
- 用户系统：邮箱验证码注册、邮箱/密码登录、找回密码
- 安全策略：不用 Redis，通过 MySQL 记录验证码、发送频控、失败次数和过期时间
- 前端布局：三栏 RAG 工作台
- 管理端：需要轻量管理员面板
- 设计文档、代码注释、主要界面文案均使用中文
- 前端需要包含清晰的品牌 Logo，并在登录页、侧边栏、浏览器标题和管理员面板中保持一致

## 3. 总体架构

系统采用前后端分离架构。

前端 Vue3 只负责用户交互、页面状态和接口调用，不直接访问数据库、Chroma、LLM 或 embedding 接口，也不读取 API Key。后端 SpringBoot 暴露 REST API 和 SSE API，前端完全通过接口文档对接。

后端负责用户认证、邮箱验证码、知识库管理、文档上传解析、文本切分、embedding 调用、Chroma 入库、RAG 检索、LLM 调用、SSE 流式输出、聊天记录保存和管理员统计。

数据分三层：

- MySQL：保存用户、知识库、文档元数据、切片元数据、会话、消息、验证码、管理员审计日志。
- Chroma：保存文档切片向量和检索元数据。
- 本地文件目录：保存用户上传的原始文件。

## 4. RAG 业务模型

系统不以单文档问答为主线，而是以知识库为单位。

一个用户可以创建多个知识库。一个知识库可以包含多个 PDF、TXT、DOCX、MARKDOWN 文档。用户在某个知识库下提问时，系统从该知识库的所有已索引文档切片中检索 Top-K 相关片段，再结合问题、历史上下文和引用片段调用 LLM 生成答案。

单文档问答只作为过滤条件存在，例如用户可以在某个知识库中选择“仅检索某一个或多个文档”来提高准确率，此处要给出提示告知此功能作用。但默认问答范围是整个知识库。

## 5. 后端模块设计

### 5.1 `auth` 模块

负责邮箱验证码注册、登录、找回密码、JWT 生成与校验、密码 BCrypt 加密、登录失败限制。

### 5.2 `user` 模块

负责普通用户资料、账号状态、用户角色和用户数据隔离。

### 5.3 `admin` 模块

负责管理员面板，包括用户统计、知识库统计、文档统计、问答统计、今日上传量、系统配置检查、用户启停用和管理员操作日志。

### 5.4 `knowledge` 模块

负责知识库创建、重命名、删除、列表查询和用户隔离。

### 5.5 `document` 模块

负责文件上传、格式校验、大小校验、原始文件保存、解析状态管理、解析失败原因展示和重新索引。

### 5.6 `chunk` 模块

负责文本切分。切分时参考标题、段落和长度，保留切片顺序、来源文档、来源标题、页码或段落位置。

### 5.7 `embedding` 模块

负责调用硅基流动 embedding 接口。该模块只处理 embedding，不处理聊天模型调用。

### 5.8 `retrieval` 模块

负责从 Chroma 中按知识库过滤检索 Top-K 片段，并返回文本、来源、相似度和元数据。

### 5.9 `llm` 模块

负责封装 OpenAI 兼容聊天接口，读取 `.env` 中的 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`、超时时间等配置，支持普通调用和流式调用。

### 5.10 `rag` 模块

负责 RAG 编排。该模块不直接读取数据库。它接收 `chat` 模块传入的当前问题、知识库 ID、最近几轮历史消息和检索参数，必要时进行 query rewrite，然后调用 `embedding`、`retrieval` 和 `llm` 完成一次 RAG 问答。

### 5.11 `chat` 模块

负责会话、消息、SSE 连接、历史消息读取、完整回答保存和引用片段保存。`chat` 模块读取历史消息后组装 `RagRequest` 交给 `rag` 模块。

### 5.12 `config` 模块

负责统一读取 `.env` 配置并提供启动时配置检查。管理员面板可以查看配置是否缺失，但不能显示 API Key 明文。

## 6. 数据库设计

### 6.1 `users`

保存用户账号信息，包括邮箱、密码哈希、昵称、角色、账号状态、创建时间、更新时间。

### 6.2 `email_verification_codes`

保存邮箱验证码、验证码用途、过期时间、发送 IP、发送次数、失败次数和是否已使用。该表用于防止邮箱轰炸和验证码爆破。

### 6.3 `knowledge_bases`

保存知识库名称、描述、所属用户、文档数量、创建时间、更新时间。

### 6.4 `documents`

保存文档元数据，包括所属知识库、上传用户、原始文件名、存储路径、文件类型、文件大小、解析状态、错误阶段、原始错误摘要、切片数量、创建时间、更新时间。

### 6.5 `document_chunks`

保存文档切片元数据，包括所属文档、所属知识库、切片序号、切片文本、来源标题、页码或段落位置、Chroma 向量 ID。

### 6.6 `chat_sessions`

保存会话信息，包括所属知识库、所属用户、会话标题、创建时间、更新时间。

### 6.7 `chat_messages`

保存聊天消息，包括会话 ID、角色、消息内容、引用片段 JSON、相似度信息、耗时信息、错误摘要、创建时间。

### 6.8 `admin_audit_logs`

保存管理员操作日志，包括操作人、操作类型、操作对象、操作前后状态、操作时间。

## 7. Chroma 元数据设计

Chroma 中保存向量和检索必要元数据：

- `chunk_id`
- `document_id`
- `knowledge_base_id`
- `user_id`
- `chunk_index`
- `source_filename`
- `source_title`
- `text`

业务主数据以 MySQL 为准，Chroma 只作为检索索引。删除知识库或文档时，需要同步删除 Chroma 中对应向量。

## 8. 文档入库流程

1. 用户选择知识库并上传文件。
2. 后端校验文件格式和大小。
3. 原始文件保存到本地上传目录。
4. MySQL 中创建 `documents` 记录，状态设为 `PROCESSING`。
5. Spring `TaskExecutor` 异步解析文件。
6. 按文件类型调用不同解析器：
   - PDF：PDFBox
   - TXT：文本读取
   - DOCX：Apache POI
   - MD：文本读取并保留标题层级
7. `chunk` 模块按标题、段落和长度切分文本。
8. `embedding` 模块调用硅基流动 embedding 接口。
9. `retrieval` 相关基础设施将向量和元数据写入 Chroma。
10. MySQL 保存切片元数据，文档状态更新为 `READY`。
11. 任意阶段失败时，文档状态更新为 `FAILED`，保存失败阶段和原始错误摘要。

## 9. 问答流程

1. 用户在某个知识库会话中输入问题。
2. `chat` 模块读取最近 `RAG_HISTORY_TURNS` 轮历史消息。
3. `chat` 模块创建 `RagRequest`，包含当前问题、知识库 ID、用户 ID、历史消息和 Top-K 参数。
4. `rag` 模块判断是否需要 query rewrite。如果问题依赖上下文，例如“那它有什么缺点”，则结合历史消息改写成独立检索问题。
5. `embedding` 模块将检索问题转换成向量。
6. `retrieval` 模块从 Chroma 检索当前知识库的 Top-K 切片。
7. 系统始终展示 Top-K 片段和相似度。低于阈值的片段不丢弃，只标记为“相关性较低”。
8. `rag` 模块组装 prompt，包含系统规则、用户问题、历史摘要和引用片段。
9. `llm` 模块调用 OpenAI 兼容聊天接口并启用流式输出。
10. `chat` 模块通过 SSE 将回答增量推送给前端。
11. 回答完成后，保存完整 AI 回复、引用片段、相似度和耗时信息。

## 10. 检索结果策略

如果知识库中存在已索引切片，Top-K 检索理论上应返回结果。系统不把低相似度误判为“无结果”。

异常情况分开处理：

- 知识库为空：提示当前知识库没有可检索文档。
- 文档未完成索引：提示文档尚未完成解析或向量化。
- Chroma 过滤条件无命中：展示过滤条件和原始返回摘要。
- Top-K 返回但相似度低：展示片段并标记“相关性较低”。

LLM 回答时应被提示只能基于引用片段谨慎回答。如果引用片段无法支撑结论，需要说明“当前知识库中没有找到强相关依据”。

## 11. 错误可观测性

失败信息必须可见，不能只显示“失败”。

前端展示：

- 主提示：显示失败阶段，例如“Embedding 调用失败”。
- 浅色详情：显示原始错误摘要，例如 `HTTP 401 Unauthorized: invalid api key`。
- 操作入口：支持重新解析、重新索引或重新生成。

后端日志：

- 记录完整异常栈。
- 记录失败阶段、知识库 ID、文档 ID、会话 ID。
- 日志中不得输出 API Key、JWT、邮箱授权码等敏感信息。

## 12. 安全设计

### 12.1 邮箱验证码安全

- 同一邮箱发送验证码有冷却时间。
- 同一邮箱每天限制发送次数。
- 同一 IP 每小时和每天限制发送次数。
- 验证码 5 分钟过期。
- 验证码错误达到限制后作废。
- 注册、找回密码提示避免暴露账号是否存在。

### 12.2 登录安全

- 密码使用 BCrypt 哈希存储。
- 连续登录失败后限制账号或 IP。
- JWT 使用 `.env` 中的强随机密钥。
- 账号禁用后不能登录。

### 12.3 数据隔离

- 普通用户只能访问自己的知识库、文档、会话和消息。
- 管理员接口需要管理员角色。
- 前端不能接触 LLM API Key、embedding API Key、数据库密码和邮箱授权码。

## 13. 前端设计

前端采用三栏 RAG 工作台。

### 13.1 左侧栏

- 知识库列表
- 创建、重命名、删除知识库
- 文档列表
- 文档上传入口
- 文档解析状态
- 历史会话

### 13.2 中间聊天区

- ChatGPT 式消息流
- SSE 流式输出
- 发送问题
- 新建会话
- 重新生成回答
- 回答中展示生成状态，例如“正在检索知识库”“正在生成回答”

### 13.3 右侧引用区

- Top-K 引用片段
- 来源文档
- 切片序号
- 相似度分数
- 低相关性标记
- 可展开原文
- 检索耗时和生成耗时

### 13.4 其他页面

- 登录页
- 注册页
- 找回密码页
- 管理员面板

视觉风格应偏文档工作台，干净、专业、信息密度适中，不做营销 landing page。第一屏就是可用系统。

### 13.5 品牌与 Logo

前端需要具备明确的 EviRAG 品牌识别。

Logo 设计方向：

- 主标识使用 `EviRAG` 字样。
- 图标可以使用字母 `E`、文档页、引用标记和检索线索组合，表达“证据增强”和“引用依据”。
- 主色建议使用沉稳绿色，搭配纸张白和深墨色，避免过度花哨。
- Logo 至少需要适配登录页大尺寸、侧边栏小尺寸、浏览器 favicon 和管理员面板标题。
- 实现时可以使用 SVG 组件，保证缩放清晰，并支持后续替换为正式品牌资产。

## 14. 前后端分离与 API Contract

后端提供 Swagger/OpenAPI 文档。前端只依赖接口文档，不依赖后端内部实现。

所有接口返回统一 JSON 格式。SSE 接口返回事件流。后端统一定义 DTO，避免前端猜字段。

如果后续更换前端，例如从 Vue3 改成 React，只要遵守 API 文档即可，不影响后端。

## 15. 核心 API

### 15.1 认证接口

- `POST /api/auth/register/send-code`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/password/send-code`
- `POST /api/auth/password/reset`

### 15.2 知识库接口

- `GET /api/kbs`
- `POST /api/kbs`
- `PUT /api/kbs/{id}`
- `DELETE /api/kbs/{id}`

### 15.3 文档接口

- `POST /api/kbs/{kbId}/documents`
- `GET /api/kbs/{kbId}/documents`
- `GET /api/documents/{id}`
- `POST /api/documents/{id}/reindex`
- `DELETE /api/documents/{id}`

### 15.4 聊天接口

- `GET /api/kbs/{kbId}/sessions`
- `POST /api/kbs/{kbId}/sessions`
- `GET /api/sessions/{id}/messages`
- `POST /api/sessions/{id}/messages/stream`
- `POST /api/sessions/{id}/messages/{messageId}/regenerate`

### 15.5 管理员接口

- `GET /api/admin/dashboard`
- `GET /api/admin/users`
- `PUT /api/admin/users/{id}/status`
- `GET /api/admin/system/config-status`
- `GET /api/admin/audit-logs`

## 16. `.env` 配置

```env
APP_PORT=8080
APP_UPLOAD_DIR=./uploads
APP_MAX_FILE_SIZE_MB=20

DB_HOST=localhost
DB_PORT=3306
DB_NAME=rag_doc_qa
DB_USERNAME=root
DB_PASSWORD=123456

JWT_SECRET=change-me
JWT_EXPIRE_MINUTES=1440

MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MAIL_SSL=true

LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=
LLM_MODEL=gpt-4o-mini
LLM_TIMEOUT_SECONDS=60

EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_API_KEY=
EMBEDDING_MODEL=
EMBEDDING_TIMEOUT_SECONDS=60

CHROMA_HOST=localhost
CHROMA_PORT=8000
CHROMA_TENANT=default_tenant
CHROMA_DATABASE=default_database
CHROMA_TOKEN=
CHROMA_COLLECTION_PREFIX=rag_kb_

CHUNK_MAX_CHARS=1200
CHUNK_OVERLAP_CHARS=120

RAG_TOP_K=5
RAG_LOW_SCORE_THRESHOLD=0.35
RAG_HISTORY_TURNS=4
```

`RAG_LOW_SCORE_THRESHOLD` 只用于前端标记低相关性，不用于丢弃检索结果。

## 17. 中文注释规范

所有核心代码必须有详细中文注释。

- 每个类说明职责。
- 每个 Controller 方法说明接口用途、参数和返回值。
- 每个 Service 方法说明业务流程。
- RAG、embedding、LLM、Chroma、SSE 等复杂逻辑需要步骤级中文注释。
- 配置类说明每个配置项用途。
- 关键实体字段需要中文说明。
- 异常处理说明失败阶段、前端展示方式和日志记录方式。
- 测试代码说明测试目标和断言含义。

不要求对每一行做机械翻译式注释，但关键业务和易错流程必须能让阅读者快速理解。

## 18. 测试设计

后端测试：

- 注册验证码发送频控。
- 验证码过期和错误次数限制。
- 登录成功、密码错误、账号禁用。
- 文档格式限制、大小限制、空文件。
- PDF、TXT、DOCX、MD 解析。
- embedding 成功和失败。
- Chroma Top-K 检索和低相似度标记。
- SSE 流式问答。
- 用户数据隔离。
- 管理员接口鉴权。

前端验证：

- 登录注册流程可走通。
- 上传文档后能看到解析状态变化。
- 多文档知识库可被统一检索。
- 问答回答流式显示。
- 右侧引用证据可展开。
- 错误信息以浅色详情展示。
- 管理员面板能展示统计数据。

## 19. 课程设计报告结构

1. 实验题目
2. 实验目的
3. 项目背景与 RAG 原理
4. 需求分析
5. 总体设计
6. 数据库设计
7. 系统详细设计
8. RAG 流程设计
9. 关键代码说明
10. 系统测试
11. 运行截图
12. 小结与心得体会

运行截图重点包括登录注册、知识库管理、文档上传、解析状态、聊天流式回答、引用证据和管理员面板。

## 20. 验收标准

- 用户可以通过邮箱验证码注册并登录。
- 用户可以创建多个知识库。
- 每个知识库可以上传多个 PDF、TXT、DOCX、MD 文档。
- 文档可以完成解析、切分、embedding 和 Chroma 入库。
- 用户可以对整个知识库发起问答。
- 回答通过 SSE 流式显示。
- 每次回答展示 Top-K 引用片段、来源文档和相似度。
- 低相似度结果不会被隐藏，只会被标记。
- 失败状态能展示失败阶段和原始错误摘要。
- 普通用户不能访问他人数据。
- 管理员可以查看统计面板并启用或禁用用户。
- 所有运行配置集中在后端 `.env` 文件。
- 后端提供 Swagger/OpenAPI 接口文档。
- 核心代码包含详细中文注释。
