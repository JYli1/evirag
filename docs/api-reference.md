# EviRAG 接口文档

版本基准：`f7e9bc4 feat: add document cleanup and admin user insights`  
后端默认地址：`http://127.0.0.1:8080`  
前端开发代理：`http://127.0.0.1:3000/api -> http://127.0.0.1:8080/api`

本文档给前端重做页面时使用。接口以当前 Spring Boot 后端代码为准。

## 1. 通用规则

### 1.1 路径与格式

- REST API 前缀：`/api`
- 普通 JSON 请求：`Content-Type: application/json`
- 文件上传：`multipart/form-data`
- 流式问答：`text/event-stream`
- 时间字段：ISO-8601 字符串，例如 `2026-06-08T04:20:00Z`
- 当前列表接口均未分页，前端需要自行处理空状态和长列表滚动。

### 1.2 统一响应结构

除流式问答接口外，所有 REST 接口都返回：

```json
{
  "success": true,
  "code": "OK",
  "message": "请求成功",
  "data": {}
}
```

失败响应：

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "data": null
}
```

稳定错误码：

| code | 含义 | 常见 HTTP 状态 |
| --- | --- | --- |
| `OK` | 成功 | 200 |
| `VALIDATION_FAILED` | 参数格式正确但业务校验失败 | 400 |
| `BAD_REQUEST` | JSON 格式错误、资源不存在等通用错误 | 400 / 404 |
| `UNAUTHORIZED` | 未登录、Token 无效或过期 | 401 |
| `FORBIDDEN` | 已登录但没有权限 | 403 |
| `INTERNAL_ERROR` | 未处理的后端异常 | 500 |

### 1.3 鉴权

登录成功后保存 `data.token`，后续受保护接口带：

```http
Authorization: Bearer <token>
```

开放接口：

- `POST /api/auth/register/send-code`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/password/send-code`
- `POST /api/auth/password/reset`
- `/v3/api-docs/**`
- `/swagger-ui/**`

管理员接口：

- `/api/admin/**` 要求当前用户 `role=ADMIN`

普通接口：

- 除开放接口和管理员接口外，均要求登录。
- 后端从 JWT 中读取当前用户，不允许前端传 `userId` 越权访问数据。

## 2. 数据模型速查

### 2.1 User

```ts
interface UserResponse {
  id: number;
  email: string;
  role: 'USER' | 'ADMIN' | string;
}
```

### 2.2 KnowledgeBase

```ts
interface KnowledgeBase {
  id: number;
  name: string;
  description: string | null;
  chromaCollection: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}
```

### 2.3 Document

```ts
type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';

interface KnowledgeDocument {
  id: number;
  knowledgeBaseId: number;
  originalFilename: string;
  storedPath: string;
  contentType: string | null;
  fileSizeBytes: number;
  sha256: string;
  parseStatus: DocumentStatus;
  errorStage: string | null;
  errorMessage: string | null;
  rawErrorSummary: string | null;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
}
```

说明：

- `storedPath` 当前是后端本地路径，建议新前端不要当成下载地址展示。
- `PROCESSING` 表示上传成功、正在解析/切片/索引。
- `READY` 表示可用于 RAG 检索。
- `FAILED` 表示处理失败，失败原因看 `errorStage`、`errorMessage`、`rawErrorSummary`。

### 2.4 DocumentChunk

```ts
interface DocumentChunk {
  id: number;
  documentId: number;
  knowledgeBaseId: number;
  chunkIndex: number;
  content: string;
  sourceTitle: string | null;
  sourceLocation: string | null;
  tokenCount: number | null;
  metadata: string | null;
  createdAt: string;
}
```

### 2.5 ChatSession

```ts
interface ChatSession {
  id: number;
  knowledgeBaseId: number | null;
  title: string;
  createdAt: string;
  updatedAt: string;
}
```

### 2.6 ChatMessage

```ts
interface ChatMessage {
  id: number;
  role: 'USER' | 'ASSISTANT' | string;
  content: string;
  citations: string | null;
  lowConfidence: boolean;
  createdAt: string;
}
```

说明：

- 历史消息里的 `citations` 是 JSON 字符串，不是数组；前端展示引用时需要 `JSON.parse`。
- `lowConfidence=true` 表示回答低置信，可在 UI 上显示提醒。

### 2.7 RagCitation

```ts
interface RagCitation {
  vectorId: string;
  content: string;
  score: number;
  lowScore: boolean;
  documentId: number | null;
  chunkId: number | null;
  chunkIndex: number | null;
  sourceTitle: string | null;
  sourceLocation: string | null;
  metadata: Record<string, unknown>;
}
```

## 3. 认证接口

### 3.1 发送注册验证码

```http
POST /api/auth/register/send-code
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱 |

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "请求成功",
  "data": null
}
```

### 3.2 注册

```http
POST /api/auth/register
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com",
  "password": "password123",
  "code": "123456"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱 |
| `password` | string | 是 | 8-128 位 |
| `code` | string | 是 | 6 位数字 |

成功 `data`：

```json
{
  "token": "jwt-token",
  "expiresAt": 1780900000000,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "role": "USER"
  }
}
```

### 3.3 登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

成功 `data` 同注册接口。

### 3.4 发送重置密码验证码

```http
POST /api/auth/password/send-code
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com"
}
```

成功 `data=null`。

### 3.5 重置密码

```http
POST /api/auth/password/reset
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com",
  "newPassword": "newPassword123",
  "code": "123456"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱 |
| `newPassword` | string | 是 | 8-128 位 |
| `code` | string | 是 | 6 位数字 |

成功 `data=null`。

## 4. 知识库接口

所有知识库接口都需要登录。

### 4.1 创建知识库

```http
POST /api/knowledge-bases
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "name": "合同知识库",
  "description": "公司合同和法务资料"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `name` | string | 是 | 1-128 字符 |
| `description` | string | 否 | 最多 2000 字符 |

成功 `data`：`KnowledgeBase`

### 4.2 查询当前用户知识库列表

```http
GET /api/knowledge-bases
Authorization: Bearer <token>
```

成功 `data`：

```json
[
  {
    "id": 1,
    "name": "合同知识库",
    "description": "公司合同和法务资料",
    "chromaCollection": "rag_kb_1_xxx",
    "status": "ACTIVE",
    "createdAt": "2026-06-08T04:20:00Z",
    "updatedAt": "2026-06-08T04:20:00Z"
  }
]
```

### 4.3 查询知识库详情

```http
GET /api/knowledge-bases/{knowledgeBaseId}
Authorization: Bearer <token>
```

成功 `data`：`KnowledgeBase`

## 5. 文档接口

所有文档接口都需要登录。当前支持上传格式：`.pdf`、`.txt`、`.docx`、`.md`。最大文件大小由后端 `.env` 的 `APP_MAX_FILE_SIZE_MB` 控制。

### 5.1 上传文档

```http
POST /api/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

FormData：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | PDF/TXT/DOCX/MD |

成功 `data`：`KnowledgeDocument`

前端建议：

- 上传成功后立即把文档显示为 `PROCESSING`。
- 每 2 秒轮询一次 `GET /api/knowledge-bases/{knowledgeBaseId}/documents`，直到没有 `PROCESSING` 或超时。
- 如果状态变成 `FAILED`，用弹窗/toast 告知，并允许用户删除失败文档。

### 5.2 查询知识库文档列表

```http
GET /api/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <token>
```

成功 `data`：`KnowledgeDocument[]`

### 5.3 查询文档详情

```http
GET /api/documents/{documentId}
Authorization: Bearer <token>
```

成功 `data`：`KnowledgeDocument`

### 5.4 查询文档切片

```http
GET /api/documents/{documentId}/chunks
Authorization: Bearer <token>
```

成功 `data`：`DocumentChunk[]`

前端用途：

- 做切片可视化、悬停预览、文档详情页。
- 建议只在用户展开/悬停时请求，避免文档列表首次加载时请求过多内容。

### 5.5 删除文档

```http
DELETE /api/documents/{documentId}
Authorization: Bearer <token>
```

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "请求成功",
  "data": null
}
```

后端行为：

- 删除 MySQL 中的文档切片。
- 删除 MySQL 中的文档记录。
- 事务提交后尝试删除 Chroma 向量。
- 事务提交后尝试删除本地上传文件。
- Chroma 或本地文件清理失败会记录日志，不阻塞业务删除。

## 6. 聊天与 RAG 接口

所有聊天接口都需要登录。

### 6.1 查询知识库会话列表

```http
GET /api/kbs/{knowledgeBaseId}/sessions
Authorization: Bearer <token>
```

成功 `data`：`ChatSession[]`

### 6.2 查询自由对话会话列表

```http
GET /api/sessions
Authorization: Bearer <token>
```

成功 `data`：`ChatSession[]`，其中 `knowledgeBaseId=null`。

### 6.3 创建知识库会话

```http
POST /api/kbs/{knowledgeBaseId}/sessions
Authorization: Bearer <token>
Content-Type: application/json
```

请求体可为空，也可传：

```json
{
  "title": "合同问答"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `title` | string | 否 | 最多 255 字符 |

成功 `data`：`ChatSession`

### 6.4 创建自由对话会话

```http
POST /api/sessions
Authorization: Bearer <token>
Content-Type: application/json
```

请求体同上，成功 `data`：`ChatSession`，其中 `knowledgeBaseId=null`。

### 6.5 查询会话消息

```http
GET /api/sessions/{sessionId}/messages
Authorization: Bearer <token>
```

成功 `data`：`ChatMessage[]`

### 6.6 流式发送消息

```http
POST /api/sessions/{sessionId}/messages/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream
```

请求体：

```json
{
  "content": "这份合同什么时候到期？"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `content` | string | 是 | 1-8000 字符 |

响应不是统一 `ApiResponse`，而是 SSE 事件流。因为这是 `POST + Body + Authorization`，原生 `EventSource` 不适合，建议用 `fetch` 读取 `ReadableStream`。

事件顺序通常为：

1. `retrieval_start`
2. `retrieval_done`
3. `answer_delta`，可能出现多次
4. `answer_done`

失败时会收到 `error` 事件，然后连接结束。

#### retrieval_start

```text
event: retrieval_start
data: {"query":"改写后的检索问题"}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `query` | string | 实际用于检索/回答的问题 |

#### retrieval_done

```text
event: retrieval_done
data: {"citations":[]}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `citations` | RagCitation[] | 检索到的引用证据；自由对话或无文档时为空数组 |

#### answer_delta

```text
event: answer_delta
data: {"delta":"回答片段"}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `delta` | string | 增量文本，前端追加到当前 assistant 消息 |

#### answer_done

```text
event: answer_done
data: {"answer":"完整回答","rewrittenQuery":"检索问题","citations":[],"lowConfidence":false}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `answer` | string | 完整回答文本 |
| `rewrittenQuery` | string | 改写后的问题 |
| `citations` | RagCitation[] | 最终引用证据 |
| `lowConfidence` | boolean | 是否低置信 |

#### error

```text
event: error
data: {"stage":"LLM","message":"问答生成失败","rawSummary":"HTTP 401: invalid api key"}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `stage` | string | 出错阶段，例如 `LLM`、`CHROMA`、`RAG`、`INDEX` |
| `message` | string | 用户可读错误 |
| `rawSummary` | string | 脱敏后的原始摘要，用于调试展示 |

前端建议：

- 发送前先本地插入用户消息和一个 pending assistant 消息。
- 收到 `answer_delta` 时追加文本。
- 收到 `answer_done` 时用完整 `answer` 覆盖最终内容，保存引用证据。
- 收到 `error` 时停止 pending，并显示错误条/toast。
- 即使知识库没有文档，后端也会走自由回答逻辑，不要禁用发送按钮。

## 7. 管理员接口

所有管理员接口都需要登录且 `role=ADMIN`。

### 7.1 管理员总览

```http
GET /api/admin/dashboard
Authorization: Bearer <admin-token>
```

成功 `data`：

```ts
interface AdminDashboard {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  totalKnowledgeBases: number;
  totalDocuments: number;
  readyDocuments: number;
  failedDocuments: number;
  questionCount: number;
  todayUploadCount: number;
  estimatedTotalTokens: number;
  missingConfigCount: number;
}
```

说明：

- `questionCount` 按用户消息数统计。
- `estimatedTotalTokens` 是估算值，不是真实模型账单。

### 7.2 用户列表

```http
GET /api/admin/users
Authorization: Bearer <admin-token>
```

成功 `data`：

```ts
interface AdminUser {
  id: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'DISABLED' | string;
  createdAt: string;
  updatedAt: string;
}
```

### 7.3 用户详情

```http
GET /api/admin/users/{userId}
Authorization: Bearer <admin-token>
```

成功 `data`：

```ts
interface AdminUserDetail {
  user: AdminUser;
  knowledgeBaseCount: number;
  documentCount: number;
  readyDocumentCount: number;
  failedDocumentCount: number;
  chunkCount: number;
  questionCount: number;
  assistantMessageCount: number;
  estimatedDocumentTokens: number;
  estimatedChatTokens: number;
  estimatedTotalTokens: number;
  recentDocuments: Array<{
    id: number;
    knowledgeBaseId: number;
    originalFilename: string;
    parseStatus: DocumentStatus;
    chunkCount: number;
    createdAt: string;
  }>;
  recentMessages: Array<{
    id: number;
    sessionId: number;
    role: string;
    preview: string;
    createdAt: string;
  }>;
}
```

说明：

- `estimatedDocumentTokens` 来自 `document_chunks.token_count` 求和。
- `estimatedChatTokens` 约等于消息内容长度 / 4。
- `recentDocuments` 和 `recentMessages` 当前最多返回 5 条。

### 7.4 更新用户状态

```http
PUT /api/admin/users/{userId}/status
Authorization: Bearer <admin-token>
Content-Type: application/json
```

请求体：

```json
{
  "status": "DISABLED"
}
```

字段约束：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `status` | string | 是 | 只能是 `ACTIVE` 或 `DISABLED` |

成功 `data`：`AdminUser`

后端会写入管理员审计日志。

### 7.5 系统配置状态

```http
GET /api/admin/system/config-status
Authorization: Bearer <admin-token>
```

成功 `data`：

```ts
interface AdminConfigStatus {
  missingCount: number;
  items: Array<{
    key: string;
    name: string;
    group: string;
    required: boolean;
    secret: boolean;
    configured: boolean;
    message: string;
  }>;
}
```

说明：

- 只返回是否配置，不返回真实 URL、账号、密码或 API Key。
- `secret=true` 的项前端也不要尝试展示具体值。

### 7.6 审计日志

```http
GET /api/admin/audit-logs
Authorization: Bearer <admin-token>
```

成功 `data`：

```ts
interface AdminAuditLog {
  id: number;
  adminUserId: number;
  action: string;
  targetType: string;
  targetId: number | null;
  detail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}
```

当前返回最近 50 条管理员操作。

## 8. 前端页面建议

### 8.1 基础页面

建议至少拆成：

- 登录页
- 注册页
- 重置密码页
- 工作台页
- 管理员面板页

### 8.2 工作台建议

工作台核心状态：

- 当前知识库 `activeKnowledgeBaseId`
- 当前会话 `activeSessionId`
- 知识库列表
- 文档列表
- 会话列表
- 消息列表
- 当前引用证据
- 当前 SSE 状态：检索中、生成中、失败、完成

交互建议：

- 左侧：知识库、上传文档、文档状态、会话列表。
- 中间：聊天流。
- 右侧：引用证据/切片详情。
- 文档 `FAILED` 状态要允许删除。
- 文档 `READY` 后可以展示切片预览。
- 发送消息不要依赖“是否有文档”，无知识库或无文档也允许自由对话。

### 8.3 管理员面板建议

管理员面板核心状态：

- 总览指标：用户、知识库、文档、问答、token、配置缺失。
- 用户表：点击选择用户。
- 用户详情：单用户文档/切片/问答/token/近期活动。
- 配置状态：只读。
- 审计日志：只读。

### 8.4 错误提示建议

- 401：清 token，跳登录。
- 403：提示没有权限。
- 上传失败：弹窗或 toast，显示后端 `message`。
- 文档处理失败：显示 `errorStage + rawErrorSummary`，并保留删除按钮。
- SSE `error`：显示 `stage + rawSummary`，assistant 消息改为失败状态。

## 9. 调试入口

后端暴露 OpenAPI：

- `GET /v3/api-docs`
- `GET /swagger-ui.html`
- `GET /swagger-ui/index.html`

如果文档和 Swagger 不一致，以当前代码和 Swagger 实际返回为准。
