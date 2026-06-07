# EviRAG

EviRAG 是一个中文全栈 RAG 文档问答系统，后端使用 Spring Boot，前端使用 Vue3 + Vite。系统支持邮箱验证码注册登录、多知识库、多文档上传、PDF/TXT/DOCX/MD 解析、embedding 入库、Chroma 检索、OpenAI 兼容 LLM、SSE 流式问答、引用证据展示和管理员面板。

## 目录结构

```text
backend/   Spring Boot 后端工程
frontend/  Vue3 + TypeScript 前端工程
docs/      设计文档与实现计划
scripts/   本地运行和配置检查脚本
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

默认访问地址：`http://127.0.0.1:5173`

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
