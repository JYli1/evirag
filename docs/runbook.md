# EviRAG 运行手册

## 1. 本地依赖

- Java 17
- Maven 3.9+
- Node.js 20+
- MySQL 8
- Chroma HTTP 服务

## 2. 配置文件

复制配置示例：

```powershell
Copy-Item backend\.env.example backend\.env
```

然后填写 `backend/.env`。LLM、embedding、数据库、邮箱、Chroma、上传目录和 RAG 参数都放在这个文件中。前端不保存任何 API Key。

检查配置：

```powershell
.\scripts\check-env.ps1
```

## 3. 启动顺序

1. 启动 MySQL，并创建 `DB_NAME` 对应数据库。
2. 启动 Chroma HTTP 服务，确认 `CHROMA_HOST`、`CHROMA_PORT`、`CHROMA_TENANT`、`CHROMA_DATABASE` 与 `.env` 一致。
3. 启动后端：

```powershell
.\scripts\run-backend.ps1
```

4. 启动前端：

```powershell
.\scripts\run-frontend.ps1
```

默认访问地址：

- 前端：`http://127.0.0.1:3000`
- 后端：`http://127.0.0.1:8080`
- Swagger：`http://127.0.0.1:8080/swagger-ui/index.html`

## 4. 核心流程

1. 注册页发送邮箱验证码并完成注册。
2. 登录后进入工作台。
3. 创建知识库。
4. 上传 PDF、TXT、DOCX 或 MD 文档。
5. 等待文档状态变为“已就绪”。
6. 在会话中提问，系统通过 SSE 流式输出回答。
7. 右侧查看引用片段、相似度和低相关性标记。
8. 管理员账号访问 `/admin` 查看统计、配置状态和用户状态。

## 5. 常见问题

- 文档状态为 `FAILED`：查看前端浅色错误详情，重点检查 `errorStage` 和 `rawErrorSummary`。
- Embedding 失败：检查 `EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL`。
- LLM 失败：检查 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`。
- Chroma 无法检索：检查 Chroma v2 地址、tenant、database 和 collection 是否可访问。
- 邮箱验证码发送失败：检查 SMTP 主机、端口、用户名、授权码和 `MAIL_FROM`。
