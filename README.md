# EviRAG

EviRAG 是一个中文全栈 RAG 文档问答系统，后端使用 Spring Boot，前端使用 Vue3 + Vite。当前提交只包含仓库基础脚手架，后续任务会逐步加入认证、知识库、文档索引、RAG 问答和管理员面板。

## 目录结构

```text
backend/   Spring Boot 后端工程
frontend/  Vue3 + TypeScript 前端工程
docs/      设计文档与实现计划
```

## 后端

后端配置从环境变量读取。开发时可参考 `backend/.env.example` 创建 `backend/.env`，不要提交真实密钥、数据库密码或邮箱授权码。

```powershell
Set-Location D:\github_project\evirag\.worktrees\feat-evirag-implementation\backend
mvn -q -DskipTests package
mvn -q test
```

## 前端

PowerShell 如果禁止加载 `npm.ps1`，请使用 `npm.cmd` 执行同等命令。

```powershell
Set-Location D:\github_project\evirag\.worktrees\feat-evirag-implementation\frontend
npm.cmd install
npm.cmd run build
```
