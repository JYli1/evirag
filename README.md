# EviRAG

EviRAG 是一个面向中文文档问答场景的全栈 RAG 系统。项目采用 Spring Boot 3 + Vue 3 前后端分离架构，支持用户登录、知识库管理、文档解析切片、向量检索、OpenAI 兼容大模型问答、引用证据展示、请求过程日志和管理员面板。

系统目标不是只做一个聊天框，而是把 RAG 的完整链路做成可观察、可管理、可调试的工作台：用户可以上传资料、查看文档处理状态、预览切片、发起问答、查看引用证据；管理员可以查看用户、文档、token 估算和配置状态。

## 项目结构

```text
backend/    Spring Boot 后端工程
frontend/   Vue 3 + TypeScript 前端工程
docs/       运行手册、测试用例、报告资料
scripts/    本地检查和启动脚本
chroma-data/ 本地 Chroma 向量库数据目录，运行后生成
```

## 技术栈

后端：

- Java 17
- Spring Boot 3.3
- Spring Security + JWT
- Spring Data JPA
- Flyway
- MySQL 8
- Chroma 向量库
- OpenAI-compatible LLM / Embedding API
- SSE 流式输出

前端：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Axios
- marked + DOMPurify Markdown 渲染

## 主要功能

- 邮箱验证码注册、登录、找回密码
- JWT 鉴权和管理员角色
- 多知识库管理
- PDF、DOCX、TXT、Markdown 文档上传
- 文档解析、切片、embedding、Chroma 入库
- 文档删除和失败文档清理
- 文档切片预览
- 支持无知识库的自由问答
- 支持有知识库的 RAG 问答
- SSE 流式问答
- 最新回复打字机效果
- 动态 Markdown 渲染
- 引用证据和相似度展示
- 用户可见过程日志，包含前端请求、后端响应、LLM 请求摘要和 LLM 响应摘要
- 管理员面板，包含用户信息、文档数量、token 估算、配置状态、审计日志等
- 环境配置检查脚本

## 项目优点

- 链路完整：从文档上传、解析、切片、向量化、检索到 LLM 回答都有实现。
- 可观察性强：前端能看到请求后端、检索、请求 LLM、收到响应等关键过程。
- 便于调试：LLM 和 Embedding 的 HTTP 错误会返回较完整的脱敏摘要，方便定位模型名、baseUrl、网络和服务商问题。
- 安全边界清楚：真实密钥统一放入 `backend/.env`，不提交到 Git；JWT 密钥禁止使用默认弱值。
- 前端体验完整：三栏工作台、证据面板、账号悬浮卡、过程日志、Markdown 和打字机效果都已集成。
- 适合课程展示和二次开发：技术栈常见，默认使用 IDEA 启动后端，配置和排错路径明确。

## 本地启动总览

推荐使用 Windows + IntelliJ IDEA 启动后端，PowerShell 或 IDEA Terminal 启动前端。

启动顺序：

```text
1. 启动 MySQL，并创建数据库
2. 启动 Chroma
3. 配置 backend/.env
4. 用 IDEA 启动 Spring Boot 后端
5. 启动 Vue 前端
6. 访问 http://127.0.0.1:3000
```

## 环境要求

- JDK 17
- IntelliJ IDEA
- Maven 3.9+
- Node.js 20+
- MySQL 8
- Python 3.10+，用于安装和运行 Chroma

## 1. 创建 MySQL 数据库

先启动 MySQL，然后执行：

```sql
CREATE DATABASE rag_doc_qa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

只需要手动创建数据库。表结构由 Flyway 在后端启动时自动执行：

```text
backend/src/main/resources/db/migration
```

## 2. 启动 Chroma

第一次使用先安装 Chroma：

```powershell
py -m pip install chromadb
```

在项目根目录下创建本地向量数据目录：

```powershell
New-Item -ItemType Directory -Force -Path D:\github_project\evirag\chroma-data
```

启动 Chroma：

```powershell
chroma run --host 127.0.0.1 --port 8000 --path D:\github_project\evirag\chroma-data
```

这个 PowerShell 窗口需要保持打开。

如果希望局域网其他机器访问 Chroma，可以把 `--host` 改成 `0.0.0.0`，同时注意防火墙和安全风险。

## 3. 配置 backend/.env

复制样例文件：

```powershell
Copy-Item D:\github_project\evirag\backend\.env.example D:\github_project\evirag\backend\.env
```

编辑：

```text
D:\github_project\evirag\backend\.env
```

至少需要配置：

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=rag_doc_qa
DB_USERNAME=root
DB_PASSWORD=你的 MySQL 密码

JWT_SECRET=换成足够长的随机字符串

MAIL_USERNAME=你的发件邮箱
MAIL_PASSWORD=邮箱 SMTP 授权码
MAIL_FROM=你的发件邮箱

LLM_BASE_URL=你的 LLM OpenAI-compatible 地址
LLM_API_KEY=你的 LLM API Key
LLM_MODEL=服务商支持的聊天模型名

EMBEDDING_BASE_URL=你的 embedding OpenAI-compatible 地址
EMBEDDING_API_KEY=你的 embedding API Key
EMBEDDING_MODEL=服务商支持的 embedding 模型名

CHROMA_HOST=127.0.0.1
CHROMA_PORT=8000
```

注意：

- `LLM_MODEL` 必须是聊天模型。
- `EMBEDDING_MODEL` 必须是 embedding 模型。
- 不要把聊天模型名填到 `EMBEDDING_MODEL`。
- 使用 OpenAI 官方接口时，`LLM_BASE_URL=https://api.openai.com/v1`。
- 使用第三方兼容接口时，`LLM_BASE_URL` 必须改成对应服务商地址。
- 修改 `.env` 后需要重启后端。

检查配置：

```powershell
Set-Location D:\github_project\evirag
.\scripts\check-env.ps1
```

## 4. 用 IDEA 启动后端

用 IDEA 打开项目根目录：

```text
D:\github_project\evirag
```

确认 IDEA 配置：

```text
Project SDK: JDK 17
Maven 项目: backend/pom.xml
```

如果 IDEA 没有自动识别 Maven：

```text
右键 backend/pom.xml -> Add as Maven Project
```

推荐 Run Configuration：

```text
Type: Spring Boot
Name: EviRAG Backend
Module: backend
Main class: com.evirag.EviRagApplication
JRE: Java 17
Working directory: D:\github_project\evirag
```

也可以直接打开：

```text
backend/src/main/java/com/evirag/EviRagApplication.java
```

点击 `main` 方法左侧绿色运行按钮。

后端启动成功后：

```text
API Base URL: http://127.0.0.1:8080
Swagger:      http://127.0.0.1:8080/swagger-ui/index.html
```

## 5. 启动前端

进入前端目录：

```powershell
Set-Location D:\github_project\evirag\frontend
```

安装依赖：

```powershell
npm.cmd install
```

本机访问启动：

```powershell
npm.cmd run dev -- --host 127.0.0.1 --port 3000
```

局域网访问启动：

```powershell
npm.cmd run dev -- --host 0.0.0.0 --port 3000
```

访问：

```text
http://127.0.0.1:3000
```

如果使用 `0.0.0.0` 启动，局域网其他机器访问：

```text
http://你的电脑局域网 IP:3000
```

## 6. 使用流程

1. 注册账号并登录。
2. 创建知识库。
3. 上传 PDF、DOCX、TXT 或 Markdown 文档。
4. 等待文档状态变为“已就绪”。
5. 可点击文档的“预览”查看切片内容。
6. 在聊天框提问。
7. 查看回答、Markdown 渲染、引用证据和过程日志。

没有上传文档时，也可以创建自由会话进行普通 LLM 问答。

## 主要页面

```text
/login           登录
/register        注册
/reset-password  找回密码
/workbench       RAG 工作台
/admin           管理员面板
```

## 常见问题

### JWT 密钥错误

如果后端启动时报：

```text
JWT 密钥不能使用空值、默认值或过短值
```

说明 `backend/.env` 中的 `JWT_SECRET` 没有配置，或仍是示例弱密钥。换成足够长的随机字符串后重启后端。

### Embedding 模型不存在

如果文档索引时报：

```text
EmbeddingException: HTTP 400
Model does not exist
```

说明 `EMBEDDING_MODEL` 填错，或该 embedding 服务商不支持这个模型。需要改成服务商支持的 embedding 模型名。

### LLM 连接失败

如果过程日志里看到：

```text
LLM ConnectException
```

通常是：

- `LLM_BASE_URL` 不可达
- 网络、代理、DNS 或防火墙问题
- 使用了错误的服务商地址
- `LLM_BASE_URL` 和 `LLM_MODEL` 不属于同一个服务商

例如 `LLM_BASE_URL=https://api.openai.com/v1` 时，不应该填写 Gemini 或其他服务商的模型名。

### Chroma collection 不存在

如果看到：

```text
Collection does not exist
```

通常是文档还没成功完成 embedding 入库，或 Chroma 数据目录被清空。重新上传文档并等待状态变成“已就绪”。

## 测试和构建

后端测试：

```powershell
Set-Location D:\github_project\evirag\backend
mvn test
```

前端测试：

```powershell
Set-Location D:\github_project\evirag\frontend
npm.cmd test -- --run
```

前端生产构建：

```powershell
npm.cmd run build
```

## 安全说明

- `backend/.env` 保存数据库密码、邮箱授权码、JWT 密钥、LLM Key 和 Embedding Key，禁止提交到 Git。
- `.gitignore` 已忽略 `backend/.env`。
- 管理员接口由后端角色控制，普通用户不能访问 `/api/admin/**`。
- 前端 Markdown 使用 DOMPurify 清洗后渲染，降低 `v-html` 风险。

## 相关文档

- 运行手册：[docs/runbook.md](docs/runbook.md)
- 测试用例：[docs/test-cases.md](docs/test-cases.md)
- 报告大纲：[docs/report-outline.md](docs/report-outline.md)
