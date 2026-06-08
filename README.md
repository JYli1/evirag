# EviRAG

EviRAG 是一个中文全栈 RAG 文档问答系统，后端使用 Spring Boot，前端使用 Vue3 + Vite。系统支持邮箱验证码注册登录、多知识库、多文档上传、PDF/TXT/DOCX/MD 解析、embedding 入库、Chroma 检索、OpenAI 兼容 LLM、SSE 流式问答、引用证据展示和管理员面板。

## 目录结构

```text
backend/   Spring Boot 后端工程
frontend/  Vue3 + TypeScript 前端工程
docs/      设计文档与实现计划
scripts/   本地运行和配置检查脚本
```

## 完整本地启动流程

本项目是前后端分离的 RAG 系统，完整运行需要同时准备 MySQL、Chroma、后端服务和前端服务。推荐启动顺序为：

```text
1. 启动 MySQL，并创建业务数据库
2. 启动 Chroma 向量库服务
3. 在 IDEA 中启动 Spring Boot 后端
4. 在 PowerShell 或 IDEA Terminal 中启动 Vue 前端
5. 访问 http://127.0.0.1:3000
```

### 1. 创建 MySQL 数据库

先确认 MySQL 已经启动，然后在 MySQL 客户端中执行：

```sql
CREATE DATABASE rag_doc_qa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

只需要手动创建数据库，不需要手动建表。后端启动时会通过 Flyway 自动执行 `backend/src/main/resources/db/migration` 下的建表脚本。

### 2. 安装并启动 Chroma

Chroma 用于保存 embedding 向量并执行相似度检索。第一次使用时执行：

```powershell
py -m pip install chromadb
```

然后启动本地 Chroma 服务：

```powershell
New-Item -ItemType Directory -Force -Path D:\github_project\evirag\chroma-data
chroma run --host 127.0.0.1 --port 8000 --path D:\github_project\evirag\chroma-data
```

该 PowerShell 窗口需要保持打开。向量数据会持久化到 `D:\github_project\evirag\chroma-data`。

### 3. 配置后端 .env

后端所有本地运行配置都放在 `backend/.env`。首次运行可复制配置样例：

```powershell
Copy-Item backend\.env.example backend\.env
```

然后按本机情况修改 `backend/.env` 中的数据库密码、QQ 邮箱授权码、LLM API Key 和 embedding API Key。

### 4. 用 IDEA 启动后端

用 IDEA 打开项目根目录：

```text
D:\github_project\evirag\.worktrees\feat-evirag-implementation
```

IDEA 中需要确认：

```text
Project SDK: JDK 17
backend/pom.xml 已作为 Maven 项目导入
```

如果 Maven 没有自动导入，右键 `backend/pom.xml`，选择 `Add as Maven Project`。

启动后端的最简单方式是打开：

```text
backend/src/main/java/com/evirag/EviRagApplication.java
```

点击 `main` 方法左侧绿色三角形，选择 `Run 'EviRagApplication'`。如果手动创建 Run Configuration，使用以下配置：

```text
Name: EviRAG Backend
JDK/JRE: 17
Main class: com.evirag.EviRagApplication
Module/classpath: backend
Working directory: D:\github_project\evirag\.worktrees\feat-evirag-implementation\backend
```

后端启动成功后，Swagger 地址为：

```text
http://127.0.0.1:8080/swagger-ui/index.html
```

### 5. 启动前端

打开 PowerShell 或 IDEA Terminal：

```powershell
Set-Location D:\github_project\evirag\.worktrees\feat-evirag-implementation\frontend
npm.cmd install
npm.cmd run dev -- --host 127.0.0.1 --port 3000
```

前端访问地址：

```text
http://127.0.0.1:3000
```

### 6. 使用流程

系统启动后，按以下顺序使用：

```text
1. 注册账号并登录
2. 创建知识库
3. 上传 PDF、DOCX、MD 或 TXT 文档
4. 等待文档解析、分块、embedding 入库
5. 在问答页面提问并查看引用证据
```

## 后端

后端启动时会自动读取 `backend/.env`，并在 Spring Boot 解析 `application.yml` 占位符前把缺失项写入 JVM 系统属性。配置优先级为 JVM `-D` 参数 > 宿主机环境变量 > 本地 `.env` 回退值；CI 和生产环境应优先使用宿主机环境变量或 `-D` 注入配置，`.env` 只作为本地开发回退。开发时可参考 `backend/.env.example` 创建 `backend/.env`，不要提交真实密钥、数据库密码或邮箱授权码。

```powershell
Copy-Item backend\.env.example backend\.env
.\scripts\check-env.ps1
Set-Location backend
mvn -q -DskipTests package
mvn -q test
```

启动后端：

```powershell
.\scripts\run-backend.ps1
```

Swagger 地址：`http://127.0.0.1:8080/swagger-ui/index.html`

## 前端

PowerShell 如果禁止加载 `npm.ps1`，请使用 `npm.cmd` 执行同等命令。

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run test -- --run
npm.cmd run build
```

启动前端：

```powershell
.\scripts\run-frontend.ps1
```

默认访问地址：`http://127.0.0.1:3000`

## 主要页面

- `/login`：登录页
- `/register`：邮箱验证码注册
- `/reset-password`：找回密码
- `/workbench`：三栏 RAG 工作台
- `/admin`：管理员面板

## 文档

- 运行手册：[docs/runbook.md](docs/runbook.md)
- 测试用例：[docs/test-cases.md](docs/test-cases.md)
- 报告大纲：[docs/report-outline.md](docs/report-outline.md)
