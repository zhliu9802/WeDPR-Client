#!/usr/bin/env bash
# WeDPR 一键部署脚本
# 用法: ./deploy.sh [命令]
# 详见: ./deploy.sh help

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
SITE_DIR="${FRONTEND_DIR}/wedpr-site"
WEB_DIR="${FRONTEND_DIR}/wedpr-web"
DB_DIR="${FRONTEND_DIR}/wedpr-builder/db"
CONF_FILE="${PROJECT_ROOT}/deploy.conf"
CONF_EXAMPLE="${PROJECT_ROOT}/deploy.conf.example"

# 颜色输出
info()  { echo -e "\033[32m[INFO]\033[0m $*"; }
warn()  { echo -e "\033[33m[WARN]\033[0m $*"; }
error() { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }
die()   { error "$@"; exit 1; }

load_conf() {
    if [[ ! -f "${CONF_FILE}" ]]; then
        if [[ -f "${CONF_EXAMPLE}" ]]; then
            cp "${CONF_EXAMPLE}" "${CONF_FILE}"
            warn "已生成 ${CONF_FILE}，请按需修改后重新执行"
        else
            die "缺少配置文件 ${CONF_FILE}"
        fi
    fi
    # shellcheck disable=SC1090
    source "${CONF_FILE}"

    DEPLOY_ROLE="${DEPLOY_ROLE:-site}"
    SITE_IP="${SITE_IP:-127.0.0.1}"
    ADMIN_IP="${ADMIN_IP:-127.0.0.1}"
    AGENCY_NAME="${AGENCY_NAME:-agency0}"
    MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
    MYSQL_PORT="${MYSQL_PORT:-3306}"
    MYSQL_DB="${MYSQL_DB:-wedpr}"
    MYSQL_USER="${MYSQL_USER:-wedpr}"
    MYSQL_PASSWORD="${MYSQL_PASSWORD:-wedpr1234}"
    MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
    MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
    GATEWAY_GRPC_PORT="${GATEWAY_GRPC_PORT:-40600}"
    GATEWAY_IP="${GATEWAY_IP:-${SITE_IP}}"
    GATEWAY_API_TOKEN="${GATEWAY_API_TOKEN:-wedpr_api_token_agency0}"
    SITE_TRANSPORT_PORT="${SITE_TRANSPORT_PORT:-6001}"
    BLOCKCHAIN_GROUP="${BLOCKCHAIN_GROUP:-group0}"
    BLOCKCHAIN_PEER="${BLOCKCHAIN_PEER:-${ADMIN_IP}:20200}"
    RECORDER_FACTORY_CONTRACT="${RECORDER_FACTORY_CONTRACT:-0x4721d1a77e0e76851d460073e64ea06d9c104194}"
    SEQUENCER_CONTRACT="${SEQUENCER_CONTRACT:-0x6849f21d1e455e9f0712b1e99fa4fcd23758e8f1}"
    DEPLOY_DIR="${DEPLOY_DIR:-${SITE_DIR}/dist}"
    ENABLE_NGINX="${ENABLE_NGINX:-true}"
    NGINX_PORT="${NGINX_PORT:-80}"
    SKIP_TESTS="${SKIP_TESTS:-true}"
    SKIP_NPM_INSTALL="${SKIP_NPM_INSTALL:-false}"
    JAVA_XMX="${JAVA_XMX:-512m}"
    TIMEZONE="${TIMEZONE:-Asia/Shanghai}"

    SITE_DIST="$(cd "${DEPLOY_DIR}" 2>/dev/null && pwd || echo "${SITE_DIR}/dist")"
    GATEWAY_TARGET="ipv4:${GATEWAY_IP}:${GATEWAY_GRPC_PORT}"
    JDBC_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?characterEncoding=UTF-8&allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8"
}

require_cmd() {
    local cmd=$1
    command -v "${cmd}" >/dev/null 2>&1 || die "未找到命令: ${cmd}，请先安装依赖或执行: ./deploy.sh install-deps"
}

check_java() {
    require_cmd java
    local ver
    ver=$(java -version 2>&1 | head -1)
    if ! java -version 2>&1 | grep -qE 'version "1\.8'; then
        warn "建议使用 JDK 8，当前: ${ver}"
    fi
}

check_node() {
    require_cmd node
    require_cmd npm
    local major
    major=$(node -v | sed 's/v//' | cut -d. -f1)
    if [[ "${major}" -lt 14 ]]; then
        warn "建议使用 Node.js 14+，当前: $(node -v)"
    fi
}

set_property() {
    local file=$1 key=$2 value=$3
    [[ -f "${file}" ]] || die "配置文件不存在: ${file}"
    local escaped
    escaped=$(printf '%s' "${value}" | sed 's/[&/\]/\\&/g')
    if grep -q "^${key}=" "${file}"; then
        sed -i "s|^${key}=.*|${key}=${escaped}|" "${file}"
    else
        echo "${key}=${value}" >> "${file}"
    fi
}

set_toml_peer() {
    local file=$1 peer=$2
    [[ -f "${file}" ]] || die "配置文件不存在: ${file}"
    sed -i "s|^peers=.*|peers=[\"${peer}\"]|" "${file}"
}

setup_timezone() {
    if command -v timedatectl >/dev/null 2>&1; then
        if timedatectl 2>/dev/null | grep -q "${TIMEZONE}"; then
            info "系统时区已是 ${TIMEZONE}"
        else
            if [[ "$(id -u)" -eq 0 ]]; then
                timedatectl set-timezone "${TIMEZONE}" && info "系统时区已设为 ${TIMEZONE}"
            else
                warn "非 root 用户，请手动执行: sudo timedatectl set-timezone ${TIMEZONE}"
            fi
        fi
    fi
}

cmd_config() {
    info "根据 deploy.conf 生成站点端配置..."
    local conf_dir="${SITE_DIR}/conf"
    local props="${conf_dir}/wedpr.properties"
    local app_props="${conf_dir}/application-wedpr.properties"
    local toml="${conf_dir}/config.toml"

    [[ -f "${props}" ]] || die "缺少 ${props}"

    local bak_dir="${conf_dir}.bak.$(date +%Y%m%d%H%M%S)"
    mkdir -p "${bak_dir}"
    cp -a "${conf_dir}/." "${bak_dir}/"
    info "已备份原配置到 ${bak_dir}"

    set_property "${props}" "wedpr.agency" "${AGENCY_NAME}"
    set_property "${props}" "wedpr.mybatis.url" "${JDBC_URL}"
    set_property "${props}" "wedpr.mybatis.username" "${MYSQL_USER}"
    set_property "${props}" "wedpr.mybatis.password" "${MYSQL_PASSWORD}"
    set_property "${props}" "wedpr.chain.group_id" "${BLOCKCHAIN_GROUP}"
    set_property "${props}" "wedpr.sync.recorder.factory.contract_address" "${RECORDER_FACTORY_CONTRACT}"
    set_property "${props}" "wedpr.sync.sequencer.contract_address" "${SEQUENCER_CONTRACT}"
    set_property "${props}" "wedpr.transport.gateway_targets" "${GATEWAY_TARGET}"
    set_property "${props}" "wedpr.transport.host_ip" "${SITE_IP}"
    set_property "${props}" "wedpr.transport.listen_port" "${SITE_TRANSPORT_PORT}"
    set_property "${props}" "wedpr.transport.nodeID" "wedpr-site-node-${AGENCY_NAME}"
    set_property "${props}" "wedpr.executor.psi.token" "${GATEWAY_API_TOKEN}"
    set_property "${props}" "wedpr.executor.mpc.token" "${GATEWAY_API_TOKEN}"

    if [[ -f "${toml}" ]]; then
        set_toml_peer "${toml}" "${BLOCKCHAIN_PEER}"
        sed -i "s|^defaultGroup=.*|defaultGroup=\"${BLOCKCHAIN_GROUP}\"|" "${toml}"
    fi

    if [[ -f "${app_props}" ]]; then
        set_property "${app_props}" "server.port" "8005"
        set_property "${app_props}" "server.type" "site_end"
    fi

    info "配置已写入 ${conf_dir}"
    info "  机构: ${AGENCY_NAME}"
    info "  站点 IP: ${SITE_IP}"
    info "  Gateway: ${GATEWAY_TARGET}"
    info "  区块链: ${BLOCKCHAIN_PEER}"
}

sync_dist_conf() {
    info "同步配置到运行目录 ${SITE_DIST}/conf ..."
    mkdir -p "${SITE_DIST}/conf"
    cp -f "${SITE_DIR}/conf/"*.properties "${SITE_DIST}/conf/" 2>/dev/null || true
    [[ -f "${SITE_DIR}/conf/config.toml" ]] && cp -f "${SITE_DIR}/conf/config.toml" "${SITE_DIST}/conf/"
    for f in ca.crt sdk.crt sdk.key sdk.nodeid; do
        [[ -f "${SITE_DIR}/conf/${f}" ]] && cp -f "${SITE_DIR}/conf/${f}" "${SITE_DIST}/conf/"
    done
    cp -f "${SITE_DIR}/bin/start.sh" "${SITE_DIST}/start.sh"
    cp -f "${SITE_DIR}/bin/stop.sh" "${SITE_DIST}/stop.sh"
    chmod +x "${SITE_DIST}/start.sh" "${SITE_DIST}/stop.sh"
}

cmd_build() {
    info "构建 Java 后端 (wedpr-site)..."
    check_java
    cd "${FRONTEND_DIR}"
    chmod +x gradlew
    # 需构建全部子模块 jar，否则 dist/lib 缺少 wedpr-components 等依赖
    local gradle_args=("jar")
    [[ "${SKIP_TESTS}" == "true" ]] && gradle_args+=("-x" "test")
    export GRADLE_OPTS="${GRADLE_OPTS:--Xmx4096m}"
    ./gradlew "${gradle_args[@]}"

    cmd_config
    sync_dist_conf

    info "构建 Vue 前端 (wedpr-web)..."
    check_node
    cd "${WEB_DIR}"
    if [[ "${SKIP_NPM_INSTALL}" != "true" ]]; then
        npm install --legacy-peer-deps
    fi
    npm run build:pro

    mkdir -p "${SITE_DIST}/web"
    rm -rf "${SITE_DIST}/web/"*
    cp -r "${WEB_DIR}/dist/"* "${SITE_DIST}/web/"
    info "前端静态文件已复制到 ${SITE_DIST}/web/"
    info "构建完成"
}

mysql_exec() {
    local sql=$1
    local user pass
    if [[ -n "${MYSQL_ROOT_PASSWORD}" ]]; then
        user="${MYSQL_ROOT_USER}"
        pass="${MYSQL_ROOT_PASSWORD}"
    else
        user="${MYSQL_USER}"
        pass="${MYSQL_PASSWORD}"
    fi
    if [[ -n "${pass}" ]]; then
        mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${user}" -p"${pass}" -e "${sql}"
    else
        mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${user}" -e "${sql}"
    fi
}

cmd_init_db() {
    require_cmd mysql
    info "初始化 MySQL 数据库 ${MYSQL_DB} ..."

    mysql_exec "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
    mysql_exec "CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';" 2>/dev/null || true
    mysql_exec "CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'localhost' IDENTIFIED BY '${MYSQL_PASSWORD}';" 2>/dev/null || true
    mysql_exec "GRANT ALL PRIVILEGES ON \`${MYSQL_DB}\`.* TO '${MYSQL_USER}'@'%';"
    mysql_exec "GRANT ALL PRIVILEGES ON \`${MYSQL_DB}\`.* TO '${MYSQL_USER}'@'localhost';"
    mysql_exec "FLUSH PRIVILEGES;"

    local auth=()
    if [[ -n "${MYSQL_PASSWORD}" ]]; then
        auth=(-p"${MYSQL_PASSWORD}")
    fi
    mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${auth[@]}" "${MYSQL_DB}" < "${DB_DIR}/wedpr_ddl.sql"
    mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${auth[@]}" "${MYSQL_DB}" < "${DB_DIR}/wedpr_dml.sql"
    if [[ -f "${DB_DIR}/tables_quartz.sql" ]]; then
        mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${auth[@]}" "${MYSQL_DB}" < "${DB_DIR}/tables_quartz.sql"
    fi
    if mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${auth[@]}" "${MYSQL_DB}" \
        -e "SHOW COLUMNS FROM wedpr_dataset LIKE 'differential_privacy_meta';" 2>/dev/null | grep -q differential_privacy_meta; then
        info "差分隐私字段已存在，跳过迁移"
    else
        mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${auth[@]}" "${MYSQL_DB}" \
            < "${DB_DIR}/wedpr_dml_differential_privacy.sql" 2>/dev/null || warn "差分隐私迁移脚本执行跳过（可能已在 ddl 中）"
    fi
    info "数据库初始化完成，默认账号: admin / 123456"
}

cmd_start() {
    check_java
    setup_timezone
    sync_dist_conf
    cd "${SITE_DIST}"
    if [[ -x ./start.sh ]]; then
        ./start.sh
    else
        info "使用 java 命令启动..."
        nohup java -Dfile.encoding=UTF-8 \
            -Duser.timezone="${TIMEZONE}" \
            -DserviceName=WEDPR-SITE \
            -DserviceConfigPath="$(pwd)/conf" \
            -Xmx"${JAVA_XMX}" \
            -cp "conf/:apps/*:lib/*" \
            com.webank.wedpr.site.main.SiteServiceApplication > start.out 2>&1 &
        sleep 3
    fi
    if ss -tlnp 2>/dev/null | grep -q ':8005 '; then
        info "wedpr-site 已启动，端口 8005"
        info "访问: http://${SITE_IP}:${NGINX_PORT}/ （需 Nginx）或开发模式 http://${SITE_IP}:3000"
    else
        warn "8005 端口未监听，请查看 ${SITE_DIST}/start.out"
    fi
}

cmd_stop() {
    info "停止 wedpr-site ..."
    if [[ -x "${SITE_DIST}/stop.sh" ]]; then
        cd "${SITE_DIST}" && ./stop.sh || true
    fi
    pkill -f 'com.webank.wedpr.site.main.SiteServiceApplication' 2>/dev/null || true

    if pgrep -f ppc-gateway-service >/dev/null 2>&1; then
        info "停止 ppc-gateway-service ..."
        pkill -f ppc-gateway-service || true
    fi
    if pgrep -f ppc-pro-node >/dev/null 2>&1; then
        info "停止 ppc-pro-node ..."
        pkill -f ppc-pro-node || true
    fi
    info "已发送停止信号"
}

cmd_dev() {
    cmd_build
    cmd_start
    info "启动前端开发服务 (npm run serve)..."
    cd "${WEB_DIR}"
    info "前端开发地址: http://${SITE_IP}:3000  （API 代理到 127.0.0.1:8005）"
    npm run serve
}

cmd_nginx() {
    require_cmd nginx
    local nginx_conf="/etc/nginx/sites-available/wedpr-site"
    local web_root="${SITE_DIST}/web"

    if [[ ! -d "${web_root}" ]]; then
        die "前端未构建，请先执行: ./deploy.sh build"
    fi

  if [[ "$(id -u)" -ne 0 ]]; then
        die "配置 Nginx 需要 root 权限: sudo ./deploy.sh nginx"
    fi

    cat > "${nginx_conf}" <<EOF
upstream wedpr_site_backend {
    server 127.0.0.1:8005;
}

server {
    listen ${NGINX_PORT};
    server_name ${SITE_IP};

    client_max_body_size 100M;

    location / {
        root ${web_root};
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }

    location /api {
        proxy_pass http://wedpr_site_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 600s;
    }
}
EOF

    ln -sf "${nginx_conf}" /etc/nginx/sites-enabled/wedpr-site
    rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
    nginx -t
    systemctl reload nginx
    # CentOS/RHEL 开启 SELinux 时，需允许 Nginx 反向代理连接后端
    if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" == "Enforcing" ]]; then
        setsebool -P httpd_can_network_connect 1 2>/dev/null && info "已开启 SELinux: httpd_can_network_connect"
    fi
    info "Nginx 已配置，访问: http://${SITE_IP}:${NGINX_PORT}/"
}

cmd_status() {
    echo "========== WeDPR 部署状态 =========="
    echo "项目根目录: ${PROJECT_ROOT}"
    echo "运行目录:   ${SITE_DIST}"
    echo "机构:       ${AGENCY_NAME} @ ${SITE_IP}"
    echo "管理端:     ${ADMIN_IP}"
    echo ""
    if ss -tlnp 2>/dev/null | grep -q ':8005 '; then
        echo "[运行中] wedpr-site :8005"
    else
        echo "[未运行] wedpr-site :8005"
    fi
    if ss -tlnp 2>/dev/null | grep -q ":${NGINX_PORT} "; then
        echo "[运行中] nginx :${NGINX_PORT}"
    else
        echo "[未运行] nginx :${NGINX_PORT}"
    fi
    if ss -tlnp 2>/dev/null | grep -q ":${GATEWAY_GRPC_PORT} "; then
        echo "[运行中] ppc-gateway :${GATEWAY_GRPC_PORT}"
    else
        echo "[未运行] ppc-gateway :${GATEWAY_GRPC_PORT} （隐私计算任务需要）"
    fi
    if pgrep -f 'npm run serve' >/dev/null 2>&1; then
        echo "[运行中] wedpr-web 开发服务 :3000"
    fi
    echo ""
    echo "管理端登记提醒:"
    echo "  在 http://${ADMIN_IP}/ 机构管理中新建:"
    echo "    agencyName=${AGENCY_NAME}"
    echo "    gatewayEndpoint=${GATEWAY_IP}:${GATEWAY_GRPC_PORT}"
}

cmd_install_deps() {
    if [[ "$(id -u)" -ne 0 ]]; then
        die "安装系统依赖需要 root: sudo ./deploy.sh install-deps"
    fi
    info "安装基础依赖 (Ubuntu/Debian)..."
    apt-get update
    apt-get install -y curl wget git unzip vim net-tools nginx mysql-server openjdk-8-jdk
    setup_timezone
    info "Node.js 请使用 nvm 安装 16.x: https://github.com/nvm-sh/nvm"
    info "C++ 网关请参见 backend/README.md 单独编译"
}

cmd_all() {
    setup_timezone
    cmd_build
    if mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" ${MYSQL_PASSWORD:+-p"${MYSQL_PASSWORD}"} \
        -e "USE ${MYSQL_DB};" 2>/dev/null; then
        info "数据库 ${MYSQL_DB} 已存在，跳过 init-db（如需重建请手动执行 ./deploy.sh init-db）"
    else
        cmd_init_db
    fi
    cmd_stop
    cmd_start
    if [[ "${ENABLE_NGINX}" == "true" ]] && command -v nginx >/dev/null 2>&1; then
        if [[ "$(id -u)" -eq 0 ]]; then
            cmd_nginx
        else
            warn "未以 root 运行，跳过 Nginx 配置。请执行: sudo ./deploy.sh nginx"
        fi
    fi
    cmd_status
    echo ""
    info "一键部署完成！"
    info "站点访问: http://${SITE_IP}:${NGINX_PORT}/  账号 admin / 123456"
    warn "请在管理端 http://${ADMIN_IP}/ 登记机构 ${AGENCY_NAME}，Gateway ${GATEWAY_IP}:${GATEWAY_GRPC_PORT}"
}

print_help() {
    cat <<'EOF'
WeDPR 一键部署脚本

用法:
  ./deploy.sh <命令>

命令:
  all           一键部署（配置 + 构建 + 建库 + 启动 + Nginx）
  build         仅编译后端与前端，并同步配置
  config        仅根据 deploy.conf 刷新配置文件
  init-db       初始化 MySQL（建库、建表、导入初始数据）
  start         启动 wedpr-site
  stop          停止 wedpr-site 及 C++ 组件
  restart       重启 wedpr-site
  dev           构建并启动后端，再启动前端开发服务 (npm run serve)
  nginx         配置 Nginx 反向代理（需 root）
  status        查看服务状态
  install-deps  安装系统依赖（Ubuntu，需 root）

首次使用:
  1. cp deploy.conf.example deploy.conf
  2. 编辑 deploy.conf（SITE_IP、ADMIN_IP、MySQL 密码等）
  3. ./deploy.sh all

配置文件: deploy.conf
详细文档: docs/站点端本地构建与服务器部署指南.md
EOF
}

main() {
    local cmd="${1:-help}"
    load_conf

    case "${cmd}" in
        all)          cmd_all ;;
        build)        cmd_build ;;
        config)       cmd_config; sync_dist_conf ;;
        init-db)      cmd_init_db ;;
        start)        cmd_start ;;
        stop)         cmd_stop ;;
        restart)      cmd_stop; sleep 2; cmd_start ;;
        dev)          cmd_dev ;;
        nginx)        cmd_nginx ;;
        status)       cmd_status ;;
        install-deps) cmd_install_deps ;;
        help|-h|--help) print_help ;;
        *) die "未知命令: ${cmd}，执行 ./deploy.sh help 查看帮助" ;;
    esac
}

main "$@"
