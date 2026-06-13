# WeDPR 站点端（WeDPR-Client）

WeDPR 是微众银行开源的多方大数据隐私计算平台。本仓库为 **站点端（机构侧）** 的可部署工程，整合了 Java 业务服务、Vue 管理台、智能合约与一键部署脚本，实现「原始数据不出域、数据可用不可见」的跨机构隐私计算。

> 站点端提供机构内的用户管理、数据集管理与授权、项目/任务管理、日志审计等能力，并通过统一网关与区块链与其他机构、管理端协作完成 PSI / MPC / 联邦建模 / 匿踪查询等隐私计算任务。

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│  前端层    wedpr-web (Vue2 管理台, :3000)                       │
└───────────────┬─────────────────────────────────────────────┘
                │ /api
┌───────────────▼─────────────────────────────────────────────┐
│  Java 业务层 (Spring Boot)                                     │
│  wedpr-site (:8005)  ← 薄 Controller                          │
│  wedpr-components/*  ← 厚组件库 (dataset/project/scheduler...) │
└───┬──────────┬──────────────┬───────────────┬────────────────┘
    │ MySQL    │ FISCO BCOS   │ JNI Gateway   │ HTTP RPC
    │ 业务数据  │ 跨机构元数据  │ SDK           │ 任务调度
    ▼          ▼              ▼               ▼
  数据落盘   资源同步       ppc-gateway    C++ 计算节点
  LOCAL/HDFS （只传元数据）  （只传密文）   PSI/MPC/PIR/建模
```

要点：

- **模块化单体**：`frontend/settings.gradle` 组织约 40 个 Gradle 模块，业务逻辑集中在 `wedpr-components/*`，`wedpr-site` / `wedpr-worker` / `wedpr-pir` 按需组合后独立启动。
- **原始数据不出域**：上传的数据始终落在本站点存储（LOCAL 磁盘或本机构 HDFS），不上传管理端、不跨机构复制。
- **跨机构两条通道**：元数据经 FISCO BCOS 区块链同步；隐私计算经统一网关交换密文，二者均不传原始文件。

完整架构见 [`docs/architecture/WeDPR系统架构说明.md`](docs/architecture/WeDPR系统架构说明.md)。

---

## 目录结构

| 目录 | 说明 |
|------|------|
| `frontend/` | Java 后端（Gradle 多模块）+ Vue2 前端 + Solidity 合约 + 构建脚本 |
| `frontend/wedpr-site/` | 站点端 Spring Boot 服务（:8005） |
| `frontend/wedpr-web/` | 站点管理台（Vue2，:3000） |
| `frontend/wedpr-components/` | 共享业务组件库（数据集、项目、调度、传输、同步等） |
| `frontend/wedpr-sol/` | 跨机构资源同步的 Solidity 合约 |
| `backend/` | C++ 隐私计算核心（PSI/PIR/MPC/建模，CMake + vcpkg） |
| `docs/` | 架构说明与部署指南 |
| `deploy.sh` / `deploy.conf.example` | 一键部署脚本与配置模板 |

---

## 快速开始

### 前置依赖

- JDK 8、Node.js 14+、MySQL 5.7+/8.0
- （可选）FISCO BCOS 区块链、HDFS、Nginx
- C++ 计算节点 / 网关（`ppc-gateway-service`、`ppc-pro-node` 等），未部署时站点可启动但无法执行隐私计算任务

### 一键部署

```bash
cp deploy.conf.example deploy.conf
# 编辑 deploy.conf：SITE_IP、ADMIN_IP、MySQL 账号密码、AGENCY_NAME 等
./deploy.sh all
```

`deploy.sh` 子命令：

| 命令 | 作用 |
|------|------|
| `all` | 一键部署（config + build + init-db + start + nginx） |
| `build` | 编译后端与前端，并同步配置 |
| `config` | 仅按 `deploy.conf` 刷新配置文件 |
| `init-db` | 初始化 MySQL（建库、建表、导入初始数据） |
| `start` / `stop` / `restart` | 启停站点服务 |
| `dev` | 构建并启动后端，再启动前端开发服务 |
| `nginx` | 配置 Nginx 反向代理（需 root） |
| `status` | 查看服务状态 |
| `install-deps` | 安装系统依赖（Ubuntu，需 root） |

默认登录账号：`admin` / `123456`。

> `deploy.conf` 可能含数据库密码、网关 Token 等敏感信息，已被 `.gitignore` 忽略，请勿提交。

### 本地开发模式

```bash
# 后端（在 frontend/ 下）
cd frontend && ./gradlew :wedpr-site:build -x test

# 前端（在 frontend/wedpr-web/ 下）
cd frontend/wedpr-web && npm install && npm run serve
```

前端 devServer 监听 `0.0.0.0:3000`，`/api` 代理至 `127.0.0.1:8005`。

---

## 文档

| 文档 | 内容 |
|------|------|
| [站点端本地构建与服务器部署指南](docs/站点端本地构建与服务器部署指南.md) | 最详细的构建与部署操作手册 |
| [WeDPR 系统架构说明](docs/architecture/WeDPR系统架构说明.md) | 分层架构与组件关系 |
| [Phase1：管理端与站点端接入](docs/architecture/phase1_admin_site_integration.md) | 元数据同步通道、管理端 API |
| [Phase2：站点端运行机制](docs/architecture/phase2_site_runtime.md) | 启动、调度、API 分层 |
| [Phase3：数据上传全流程解析](docs/architecture/Phase3_数据上传全流程解析.md) | 数据上传、落盘、存储后端、链上同步、差分隐私 |
| [Phase4：隐私计算任务详解](docs/architecture/Phase4_隐私计算任务详解.md) | PSI/MPC/ML/PIR 的数据 I/O 规范 |
| [Phase4：隐私计算任务调度全流程](docs/architecture/phase4_privacy_compute_task_flow_and_io.md) | 任务创建、调度、跨机构执行 |
| [Phase5：区块链合约部署与链上同步](docs/architecture/phase5_blockchain_contract_deploy_and_onchain_data_sync.md) | 合约部署、资源链上同步 |

---

## 支持的数据形态

WeDPR 当前为**表格型（结构化）隐私计算平台**：支持 CSV、Excel、关系型数据库（MySQL/PostgreSQL/达梦等）、Hive、HDFS 上的 CSV 文件；所有来源最终归一化为 CSV 参与计算。**不支持** 图片、视频、音频等非结构化媒体（接入思路见 Phase3 §8.4）。

---

## 许可证

本项目基于 [WeDPR](https://github.com/WeBankBlockchain/WeDPR) 与 [WeDPR-Component](https://github.com/WeBankBlockchain/WeDPR-Component) 构建，遵循 **Apache License 2.0**。
