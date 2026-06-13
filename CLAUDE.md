# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

WeDPR 站点端 — WeBank's multi-party privacy-computing platform (站点端 / institutional site). It is a layered system: a Vue2 admin console talks over `/api` to a Spring Boot Java backend, which persists business data in MySQL, syncs cross-institution resources via FISCO BCOS blockchain, and drives C++ privacy-compute nodes (PSI/PIR/MPC/modeling) through a JNI Gateway SDK.

Top-level layout:
- `frontend/` — Java backend (Gradle multi-module) + Vue2 frontend + Solidity contracts + build scripts. Despite the name, this holds **both** the Java services and the web UI.
- `backend/` — C++ privacy-compute core (PSI/PIR/MPC/modeling), built with CMake + vcpkg. Also a Python ML toolkit under `backend/python`.
- `docs/` — architecture notes (`docs/architecture/`) and the authoritative build/deploy guide `docs/站点端本地构建与服务器部署指南.md`.
- `deploy.sh` + `deploy.conf.example` — one-click deploy orchestrator (see below).

## Common commands

Deployment and lifecycle are driven by `./deploy.sh` (reads `deploy.conf`, copied from `deploy.conf.example` on first run):

```bash
cp deploy.conf.example deploy.conf   # then edit SITE_IP, ADMIN_IP, MySQL creds, etc.
./deploy.sh all        # config + build + init-db + start + nginx
./deploy.sh build      # compile backend + frontend, sync config into dist
./deploy.sh config     # regenerate config files from deploy.conf only
./deploy.sh init-db    # create MySQL db/tables + seed data
./deploy.sh start|stop|restart|status
./deploy.sh dev        # build + start site, then run web dev server
```

### Java backend (wedpr-site, Spring Boot, port 8005)

Build from `frontend/` (Gradle multi-module, JDK 8 expected):

```bash
cd frontend
./gradlew build -x test                  # full build, skip tests
./gradlew :wedpr-site:build -x test       # single module
./gradlew :wedpr-site:test                # run a module's tests
./gradlew :wedpr-site:test --tests "com.webank.wedpr.site.SomeTest"   # single test
```

Run the site service (after build, with config staged into `wedpr-site/dist/conf/`):

```bash
cd frontend/wedpr-site/dist
java -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -DserviceName=WEDPR-SITE \
  -DserviceConfigPath=$(pwd)/conf -Xmx512m \
  -cp "conf/:apps/*:lib/*" com.webank.wedpr.site.main.SiteServiceApplication
```

### Web frontend (wedpr-web, Vue2, port 3000)

```bash
cd frontend/wedpr-web
npm install
npm run serve     # dev server on 0.0.0.0:3000, proxies /api → 127.0.0.1:8005
npm run lint
npm run build:pro # production build
```

Default login: `admin` / `123456`. devServer proxies `/api` to the site service at `127.0.0.1:8005` (see `wedpr-web/vue.config.js`).

### C++ core (backend)

Built with CMake + vcpkg (`backend/cpp/`, `backend/vcpkg.json`). This is a vendored copy of WeDPR-Component; it is consumed at runtime by the Java side as the gateway/pro-node binaries plus the `wedpr-gateway-sdk` JAR, not usually rebuilt for site-end work.

## Architecture

Read `docs/architecture/WeDPR系统架构说明.md` for the full picture; the deploy/build guide in `docs/` is the most detailed operational reference.

Key structural facts that span multiple files:

- **Modular monolith.** `frontend/settings.gradle` wires ~40 Gradle modules. The shared business logic lives in `wedpr-components/*` (dataset, project, authorization, scheduler, transport, sync, blockchain, user, etc.). Deployable services — `wedpr-site`, `wedpr-worker`, `wedpr-pir` — assemble those components and start independently.
- **Thin controllers, thick components.** `wedpr-site/src/main/java/.../site/controller/*` only exposes REST endpoints; real logic is delegated into `wedpr-components`. When changing behavior, the logic usually lives in a component module, not in the site controller.
- **Cross-institution sync.** `wedpr-components/sync` + `wedpr-components/blockchain` replicate resource metadata across agencies via FISCO BCOS. The Solidity contracts are in `frontend/wedpr-sol/` (`ResourceLogRecord`, `ResourceSequencer`, and factories); their deployed addresses are configured in `deploy.conf` (`RECORDER_FACTORY_CONTRACT`, `SEQUENCER_CONTRACT`).
- **Gateway / compute path.** `wedpr-components/transport` loads the `wedpr-gateway-sdk` (JNI) to reach the C++ `ppc-gateway-service` (gRPC, default port 40600). The site dispatches privacy-compute tasks (PSI/PIR/MPC/modeling) to C++ nodes over HTTP RPC and routes cross-agency traffic through the gateway. Each agency has a unique `AGENCY_NAME` and transport `nodeID` (`wedpr-site-node-${AGENCY_NAME}`).
- **Config flow.** `deploy.sh config` renders `deploy.conf` values into the site's `.properties` / `config.toml` and the web build, then copies them into `wedpr-site/dist/conf/`. Treat `deploy.conf` as the single source of truth for environment wiring (IPs, MySQL, ports, contract addresses, gateway token); editing dist configs by hand will be overwritten on the next `config`/`build`.

## Conventions

- The build/deploy guide and `deploy.conf.example` are heavily commented in Chinese and document exactly which config key writes to which file — consult them before changing ports, IPs, or DB settings.
- `deploy.conf` may contain DB passwords and the gateway API token; it is git-ignored and must not be committed.
