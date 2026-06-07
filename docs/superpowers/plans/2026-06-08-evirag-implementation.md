# EviRAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 EviRAG，一个支持多知识库、多文档、引用证据、SSE 流式问答、邮箱注册和管理员面板的中文 RAG 文档问答系统。

**Architecture:** 前端 Vue3 与后端 SpringBoot 完全分离。SpringBoot 负责认证、文档入库、RAG 编排、LLM/embedding 调用、Chroma 检索和 SSE 输出；Vue3 负责三栏工作台、认证页面、管理员面板和 Logo 品牌呈现。

**Tech Stack:** SpringBoot 3、Java 17、MySQL、Flyway、Chroma、OpenAI-compatible Chat Completions API、SiliconFlow-compatible Embeddings API、Vue3、Vite、TypeScript、Pinia、Vue Router、Axios、Vitest。

---

## Scope Check

EviRAG 覆盖认证、邮箱验证码、文档处理、向量检索、LLM、SSE、前端工作台和管理员面板，属于多个子系统组合。本计划按可运行检查点拆分，执行时每个 Task 单独完成、测试、提交。不要把多个 Task 混在一次大改里。

## File Structure

```text
evirag/
  backend/
    pom.xml
    .env.example
    src/main/java/com/evirag/
      EviRagApplication.java
      common/
      config/
      auth/
      user/
      admin/
      knowledge/
      document/
      chunk/
      embedding/
      retrieval/
      llm/
      rag/
      chat/
    src/main/resources/
      application.yml
      db/migration/
    src/test/java/com/evirag/
  frontend/
    package.json
    vite.config.ts
    index.html
    src/
      main.ts
      App.vue
      assets/
      components/
      layouts/
      pages/
      router/
      stores/
      api/
      styles/
  docs/
    superpowers/
      specs/
      plans/
```

## Implementation Rules

- 所有核心代码写详细中文注释。
- 前端所有可见主文案使用中文。
- 前端不得读取 `.env` 中的密钥。
- 所有 LLM、embedding、邮件、数据库、Chroma、上传配置集中在 `backend/.env`。
- 每个任务完成后执行指定测试命令并提交。
- 失败信息要显示失败阶段和原始错误摘要，但不得泄露 API Key、JWT、邮箱授权码。

---

### Task 1: 仓库与基础脚手架

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `backend/pom.xml`
- Create: `backend/.env.example`
- Create: `backend/src/main/java/com/evirag/EviRagApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`

- [ ] **Step 1: 创建后端 Maven 工程**

`backend/pom.xml` 必须包含 Spring Web、Validation、Security、Data JPA、MySQL Driver、Flyway、Spring Mail、Springdoc OpenAPI、Lombok、JUnit、Mockito、Testcontainers、OkHttp MockWebServer。

Run:

```powershell
Set-Location D:\github_project\evirag\backend
mvn -q -DskipTests package
```

Expected: `target` 目录生成，且没有编译错误。

- [ ] **Step 2: 创建 SpringBoot 启动类**

`EviRagApplication.java`：

```java
package com.evirag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EviRAG 后端启动入口。
 * 负责启动认证、知识库、文档索引、RAG 问答和管理员面板等后端能力。
 */
@SpringBootApplication
public class EviRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(EviRagApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建后端配置文件**

`application.yml` 使用环境变量占位读取 `.env` 注入后的配置，不在仓库里放真实密钥。

Run:

```powershell
mvn -q test
```

Expected: 测试阶段正常启动 Spring 上下文。

- [ ] **Step 4: 创建前端 Vite 工程**

`frontend/package.json` 包含 Vue3、TypeScript、Vite、Pinia、Vue Router、Axios、Vitest。

Run:

```powershell
Set-Location D:\github_project\evirag\frontend
npm install
npm run build
```

Expected: `dist` 目录生成，且没有 TypeScript 或构建错误。

- [ ] **Step 5: 提交**

```powershell
git add .gitignore README.md backend frontend
git commit -m "chore: scaffold EviRAG full-stack project"
```

---

### Task 2: 配置加载、统一响应与数据库迁移

**Files:**
- Create: `backend/src/main/java/com/evirag/config/EnvConfig.java`
- Create: `backend/src/main/java/com/evirag/config/AppProperties.java`
- Create: `backend/src/main/java/com/evirag/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/evirag/common/api/ApiErrorCode.java`
- Create: `backend/src/main/java/com/evirag/common/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/resources/db/migration/V1__init_core_tables.sql`
- Test: `backend/src/test/java/com/evirag/config/AppPropertiesTest.java`

- [ ] **Step 1: 写配置测试**

`AppPropertiesTest` 验证上传大小、RAG Top-K、低相似度阈值、历史轮数能从配置读取。

Expected first run:

```powershell
mvn -q -Dtest=AppPropertiesTest test
```

Expected: FAIL，因为配置类还不存在。

- [ ] **Step 2: 实现配置类**

`AppProperties` 使用 `@ConfigurationProperties` 映射应用、LLM、embedding、Chroma、RAG、邮件配置。每个字段加中文注释。

- [ ] **Step 3: 实现统一 API 响应**

`ApiResponse<T>` 字段固定为：

```java
private boolean success;
private String code;
private String message;
private T data;
```

错误响应必须保留 `code` 和 `message`，方便前端统一展示。

- [ ] **Step 4: 写 Flyway 初始化 SQL**

`V1__init_core_tables.sql` 创建 `users`、`email_verification_codes`、`knowledge_bases`、`documents`、`document_chunks`、`chat_sessions`、`chat_messages`、`admin_audit_logs`。

- [ ] **Step 5: 运行测试并提交**

```powershell
mvn -q test
git add backend
git commit -m "feat: add backend config and core schema"
```

Expected: `AppPropertiesTest` PASS。

---

### Task 3: 邮箱验证码注册、登录与安全限制

**Files:**
- Create: `backend/src/main/java/com/evirag/auth/AuthController.java`
- Create: `backend/src/main/java/com/evirag/auth/AuthService.java`
- Create: `backend/src/main/java/com/evirag/auth/EmailVerificationService.java`
- Create: `backend/src/main/java/com/evirag/auth/JwtService.java`
- Create: `backend/src/main/java/com/evirag/auth/dto/*.java`
- Create: `backend/src/main/java/com/evirag/user/User.java`
- Create: `backend/src/main/java/com/evirag/user/UserRepository.java`
- Test: `backend/src/test/java/com/evirag/auth/EmailVerificationServiceTest.java`
- Test: `backend/src/test/java/com/evirag/auth/AuthServiceTest.java`

- [ ] **Step 1: 写验证码安全测试**

测试同一邮箱 60 秒内重复发送会失败、验证码 5 分钟过期、错误 5 次后作废。

Run:

```powershell
mvn -q -Dtest=EmailVerificationServiceTest test
```

Expected: FAIL，因为验证码服务还不存在。

- [ ] **Step 2: 实现验证码服务**

`EmailVerificationService` 使用 MySQL 表记录邮箱、用途、验证码哈希、过期时间、发送 IP、发送次数、失败次数。验证码原文只通过邮件发送，不在数据库明文保存。

- [ ] **Step 3: 写注册登录测试**

测试邮箱验证码注册、BCrypt 密码存储、邮箱密码登录、账号禁用后拒绝登录。

Run:

```powershell
mvn -q -Dtest=AuthServiceTest test
```

Expected: FAIL。

- [ ] **Step 4: 实现 AuthService 与 JwtService**

JWT 中包含用户 ID、邮箱、角色和过期时间。所有认证接口返回统一 `ApiResponse`。

- [ ] **Step 5: 运行测试并提交**

```powershell
mvn -q -Dtest=EmailVerificationServiceTest,AuthServiceTest test
git add backend
git commit -m "feat: add email auth and security limits"
```

---

### Task 4: 知识库、文档上传与文本解析

**Files:**
- Create: `backend/src/main/java/com/evirag/knowledge/*.java`
- Create: `backend/src/main/java/com/evirag/document/*.java`
- Create: `backend/src/main/java/com/evirag/document/parser/DocumentParser.java`
- Create: `backend/src/main/java/com/evirag/document/parser/PdfDocumentParser.java`
- Create: `backend/src/main/java/com/evirag/document/parser/TxtDocumentParser.java`
- Create: `backend/src/main/java/com/evirag/document/parser/DocxDocumentParser.java`
- Create: `backend/src/main/java/com/evirag/document/parser/MarkdownDocumentParser.java`
- Test: `backend/src/test/java/com/evirag/document/DocumentParserTest.java`
- Test: `backend/src/test/java/com/evirag/knowledge/KnowledgeBaseServiceTest.java`

- [ ] **Step 1: 写知识库隔离测试**

测试用户 A 不能读取用户 B 的知识库。

Run:

```powershell
mvn -q -Dtest=KnowledgeBaseServiceTest test
```

Expected: FAIL。

- [ ] **Step 2: 实现知识库服务**

知识库服务只返回当前用户的数据。管理员查询统计走 `admin` 模块，不复用普通用户列表接口。

- [ ] **Step 3: 写文档解析测试**

测试 TXT、MD 直接解析，DOCX 使用 Apache POI，PDF 使用 PDFBox。测试文件放在 `backend/src/test/resources/samples/`。

Run:

```powershell
mvn -q -Dtest=DocumentParserTest test
```

Expected: FAIL。

- [ ] **Step 4: 实现文档解析器**

每个解析器返回统一 `ParsedDocument`，字段包括纯文本、标题列表、页码或段落位置。解析失败时返回失败阶段和原始错误摘要。

- [ ] **Step 5: 实现上传接口**

上传接口校验 PDF/TXT/DOCX/MD 和 20MB 限制，保存原始文件，创建 `documents` 记录并标记 `PROCESSING`。

- [ ] **Step 6: 运行测试并提交**

```powershell
mvn -q test
git add backend
git commit -m "feat: add knowledge bases and document parsing"
```

---

### Task 5: 文本切分、embedding 与 Chroma 入库

**Files:**
- Create: `backend/src/main/java/com/evirag/chunk/ChunkService.java`
- Create: `backend/src/main/java/com/evirag/chunk/TextChunk.java`
- Create: `backend/src/main/java/com/evirag/embedding/EmbeddingClient.java`
- Create: `backend/src/main/java/com/evirag/embedding/OpenAiCompatibleEmbeddingClient.java`
- Create: `backend/src/main/java/com/evirag/retrieval/ChromaClient.java`
- Create: `backend/src/main/java/com/evirag/retrieval/VectorIndexService.java`
- Test: `backend/src/test/java/com/evirag/chunk/ChunkServiceTest.java`
- Test: `backend/src/test/java/com/evirag/embedding/EmbeddingClientTest.java`

- [ ] **Step 1: 写切片测试**

测试标题保留、切片顺序、切片长度和重叠窗口。

Run:

```powershell
mvn -q -Dtest=ChunkServiceTest test
```

Expected: FAIL。

- [ ] **Step 2: 实现切片服务**

切片服务按标题和段落优先切分，超过长度后再按字符窗口切分。每个切片保留 `chunkIndex`、`sourceTitle`、`text`。

- [ ] **Step 3: 写 embedding 客户端测试**

使用 MockWebServer 模拟硅基流动兼容 `/v1/embeddings` 响应，验证请求体包含 `model` 和 `input`。

Run:

```powershell
mvn -q -Dtest=EmbeddingClientTest test
```

Expected: FAIL。

- [ ] **Step 4: 实现 embedding 客户端**

客户端读取 `EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL`。失败时返回阶段 `EMBEDDING` 和原始错误摘要。

- [ ] **Step 5: 实现 Chroma 客户端**

Chroma 客户端负责 collection 创建、upsert、query、delete。元数据必须包含 `user_id`、`knowledge_base_id`、`document_id`、`chunk_id`。

- [ ] **Step 6: 实现 VectorIndexService**

异步流程：解析文本 -> 切片 -> embedding -> 写 Chroma -> 写 MySQL 切片元数据 -> 更新文档状态。

- [ ] **Step 7: 运行测试并提交**

```powershell
mvn -q test
git add backend
git commit -m "feat: add chunking embeddings and vector indexing"
```

---

### Task 6: LLM、Query Rewrite、RAG 编排与 SSE

**Files:**
- Create: `backend/src/main/java/com/evirag/llm/LlmClient.java`
- Create: `backend/src/main/java/com/evirag/llm/OpenAiCompatibleLlmClient.java`
- Create: `backend/src/main/java/com/evirag/rag/RagRequest.java`
- Create: `backend/src/main/java/com/evirag/rag/RagResponse.java`
- Create: `backend/src/main/java/com/evirag/rag/QueryRewriteService.java`
- Create: `backend/src/main/java/com/evirag/rag/RagService.java`
- Create: `backend/src/main/java/com/evirag/chat/ChatController.java`
- Create: `backend/src/main/java/com/evirag/chat/ChatService.java`
- Test: `backend/src/test/java/com/evirag/rag/QueryRewriteServiceTest.java`
- Test: `backend/src/test/java/com/evirag/rag/RagServiceTest.java`

- [ ] **Step 1: 写 query rewrite 测试**

测试“那它有什么缺点？”结合历史改写为完整检索问题；测试 rewrite 失败时回退原问题。

Run:

```powershell
mvn -q -Dtest=QueryRewriteServiceTest test
```

Expected: FAIL。

- [ ] **Step 2: 实现 QueryRewriteService**

先用规则判断是否需要改写。包含指代词、短问题、依赖上文的问题才调用 `llm`。rewrite 结果只用于检索，不替换用户原始问题。

- [ ] **Step 3: 写 RagService 测试**

测试 `chat` 传入历史消息，`rag` 不直接查数据库；测试 Top-K 低相似度结果不会被丢弃。

Run:

```powershell
mvn -q -Dtest=RagServiceTest test
```

Expected: FAIL。

- [ ] **Step 4: 实现 LLM 客户端**

使用 OpenAI 兼容 `/v1/chat/completions`，支持 stream 和非 stream。失败时返回阶段 `LLM` 和原始错误摘要。

- [ ] **Step 5: 实现 RagService**

流程：接收 `RagRequest` -> query rewrite -> embedding -> Chroma Top-K -> prompt 组装 -> 调用 `llm` -> 返回回答片段和引用证据。

- [ ] **Step 6: 实现 SSE 接口**

`POST /api/sessions/{id}/messages/stream` 返回 SSE 事件：

```text
retrieval_start
retrieval_done
answer_delta
answer_done
error
```

- [ ] **Step 7: 运行测试并提交**

```powershell
mvn -q test
git add backend
git commit -m "feat: add RAG orchestration and streaming chat"
```

---

### Task 7: 管理员面板 API 与 OpenAPI 文档

**Files:**
- Create: `backend/src/main/java/com/evirag/admin/AdminController.java`
- Create: `backend/src/main/java/com/evirag/admin/AdminDashboardService.java`
- Create: `backend/src/main/java/com/evirag/admin/AdminAuditLog.java`
- Create: `backend/src/main/java/com/evirag/config/OpenApiConfig.java`
- Test: `backend/src/test/java/com/evirag/admin/AdminControllerTest.java`

- [ ] **Step 1: 写管理员权限测试**

测试普通用户访问管理员接口返回 403，管理员可以访问统计接口。

Run:

```powershell
mvn -q -Dtest=AdminControllerTest test
```

Expected: FAIL。

- [ ] **Step 2: 实现管理员统计**

统计用户数、知识库数、文档数、问答次数、今日上传量和配置缺失项数量。

- [ ] **Step 3: 实现配置状态接口**

返回配置项是否存在，不返回密钥明文。密钥类配置只显示 `configured: true/false`。

- [ ] **Step 4: 启用 Swagger/OpenAPI**

Swagger 地址固定为 `/swagger-ui/index.html`，文档标题为 `EviRAG API`。

- [ ] **Step 5: 运行测试并提交**

```powershell
mvn -q test
git add backend
git commit -m "feat: add admin panel APIs and OpenAPI docs"
```

---

### Task 8: 前端基础、品牌 Logo 与认证页面

**Files:**
- Create: `frontend/src/assets/logo/EviRagLogo.vue`
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/stores/authStore.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/pages/auth/LoginPage.vue`
- Create: `frontend/src/pages/auth/RegisterPage.vue`
- Create: `frontend/src/pages/auth/ResetPasswordPage.vue`
- Test: `frontend/src/pages/auth/LoginPage.spec.ts`

- [ ] **Step 1: 写登录页测试**

测试登录页显示 EviRAG Logo、邮箱输入框、密码输入框和登录按钮。

Run:

```powershell
npm run test -- LoginPage
```

Expected: FAIL。

- [ ] **Step 2: 实现品牌 Logo**

`EviRagLogo.vue` 使用 SVG 组件，包含文档页、引用标记和 `E` 字母抽象形态。支持 `compact` 属性，用于侧边栏小尺寸。

- [ ] **Step 3: 实现认证 API**

Axios 实例统一注入 JWT，401 时清理本地登录状态并跳转登录页。

- [ ] **Step 4: 实现登录、注册、找回密码页面**

页面文案使用中文。注册页包含发送验证码倒计时和错误详情浅色展示。

- [ ] **Step 5: 构建并提交**

```powershell
npm run build
npm run test
git add frontend
git commit -m "feat: add frontend brand and auth pages"
```

---

### Task 9: 前端三栏 RAG 工作台

**Files:**
- Create: `frontend/src/layouts/WorkbenchLayout.vue`
- Create: `frontend/src/pages/workbench/WorkbenchPage.vue`
- Create: `frontend/src/components/kb/KnowledgeBaseSidebar.vue`
- Create: `frontend/src/components/document/DocumentUploader.vue`
- Create: `frontend/src/components/chat/ChatPanel.vue`
- Create: `frontend/src/components/chat/MessageList.vue`
- Create: `frontend/src/components/chat/ChatComposer.vue`
- Create: `frontend/src/components/evidence/EvidencePanel.vue`
- Create: `frontend/src/api/knowledge.ts`
- Create: `frontend/src/api/document.ts`
- Create: `frontend/src/api/chat.ts`
- Test: `frontend/src/pages/workbench/WorkbenchPage.spec.ts`

- [ ] **Step 1: 写工作台测试**

测试页面包含左侧知识库、中间聊天区、右侧引用证据区。

Run:

```powershell
npm run test -- WorkbenchPage
```

Expected: FAIL。

- [ ] **Step 2: 实现三栏布局**

左侧显示知识库、文档和会话；中间显示 ChatGPT 式消息流；右侧显示 Top-K 引用证据。

- [ ] **Step 3: 实现文档上传状态**

展示 `PROCESSING`、`READY`、`FAILED`。失败时用浅色详情显示原始错误摘要。

- [ ] **Step 4: 实现 SSE 聊天**

监听 `retrieval_start`、`retrieval_done`、`answer_delta`、`answer_done`、`error`。回答流式追加到当前 AI 消息。

- [ ] **Step 5: 实现引用证据面板**

展示来源文档、切片序号、相似度、低相关性标记和可展开原文。

- [ ] **Step 6: 构建并提交**

```powershell
npm run build
npm run test
git add frontend
git commit -m "feat: add RAG workbench frontend"
```

---

### Task 10: 前端管理员面板与系统状态

**Files:**
- Create: `frontend/src/pages/admin/AdminDashboardPage.vue`
- Create: `frontend/src/components/admin/AdminMetricGrid.vue`
- Create: `frontend/src/components/admin/AdminUserTable.vue`
- Create: `frontend/src/components/admin/ConfigStatusList.vue`
- Create: `frontend/src/api/admin.ts`
- Test: `frontend/src/pages/admin/AdminDashboardPage.spec.ts`

- [ ] **Step 1: 写管理员面板测试**

测试管理员面板显示用户数、知识库数、文档数、问答次数和配置状态。

Run:

```powershell
npm run test -- AdminDashboardPage
```

Expected: FAIL。

- [ ] **Step 2: 实现管理员 API 客户端**

封装 `/api/admin/dashboard`、`/api/admin/users`、`/api/admin/system/config-status`。

- [ ] **Step 3: 实现管理员页面**

页面包含 EviRAG Logo、统计卡片、用户启停用、配置检查结果和错误详情。

- [ ] **Step 4: 构建并提交**

```powershell
npm run build
npm run test
git add frontend
git commit -m "feat: add admin dashboard frontend"
```

---

### Task 11: 集成验证、运行脚本与报告素材

**Files:**
- Create: `scripts/run-backend.ps1`
- Create: `scripts/run-frontend.ps1`
- Create: `scripts/check-env.ps1`
- Create: `docs/runbook.md`
- Create: `docs/report-outline.md`
- Create: `docs/test-cases.md`
- Modify: `README.md`

- [ ] **Step 1: 创建运行脚本**

`run-backend.ps1` 进入 `backend` 并运行 SpringBoot。`run-frontend.ps1` 进入 `frontend` 并运行 Vite。`check-env.ps1` 检查 `.env` 中必填配置是否存在。

- [ ] **Step 2: 写运行手册**

`docs/runbook.md` 包含 MySQL、Chroma、后端、前端启动顺序和默认访问地址。

- [ ] **Step 3: 写测试用例文档**

`docs/test-cases.md` 列出注册登录、知识库、文档上传、RAG 问答、引用证据、管理员面板和错误展示测试步骤。

- [ ] **Step 4: 写报告大纲**

`docs/report-outline.md` 按课程报告结构写中文大纲，包含截图清单。

- [ ] **Step 5: 全量验证**

```powershell
Set-Location D:\github_project\evirag\backend
mvn -q test
Set-Location D:\github_project\evirag\frontend
npm run build
npm run test
```

Expected: 后端测试通过，前端构建和测试通过。

- [ ] **Step 6: 提交**

```powershell
git add README.md scripts docs
git commit -m "docs: add runbook tests and report outline"
```

---

## Execution Notes

- 执行 Task 1 前先确认本机有 Java 17、Maven、Node.js、MySQL 和 Chroma。
- 如果 Chroma 不在本机运行，先在 `.env` 中配置 `CHROMA_HOST` 和 `CHROMA_PORT`。
- 硅基流动 embedding 和 LLM 配置都在 `backend/.env`，实现过程中不能把真实 key 提交进 Git。
- 每个任务完成后必须运行对应测试命令，不允许只看页面就提交。
- 如果依赖安装因网络失败，按当前 Codex 权限规则申请网络/沙箱外执行。
