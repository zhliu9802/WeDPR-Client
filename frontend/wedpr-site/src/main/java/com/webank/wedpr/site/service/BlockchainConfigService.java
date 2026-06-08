/*
 * Copyright 2017-2025  [webank-wedpr]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.webank.wedpr.site.service;

import com.moandjiezana.toml.Toml;
import com.webank.wedpr.common.config.WeDPRConfig;
import com.webank.wedpr.common.utils.WeDPRException;
import com.webank.wedpr.site.service.model.BlockchainConfigDTO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 区块链配置文件读写服务，持久化到 conf/wedpr.properties 与 conf/config.toml */
@Service
public class BlockchainConfigService {
    private static final Logger logger = LoggerFactory.getLogger(BlockchainConfigService.class);

    private static final String WEDPR_PROPERTIES = "wedpr.properties";
    private static final String CONFIG_TOML = "config.toml";

    private static final Map<String, String> PROPERTY_KEY_DEFAULTS = new LinkedHashMap<>();

    static {
        PROPERTY_KEY_DEFAULTS.put("wedpr.chain.group_id", "group0");
        PROPERTY_KEY_DEFAULTS.put("wedpr.chain.config_path", "config.toml");
        PROPERTY_KEY_DEFAULTS.put("wedpr.sync.recorder.factory.contract_address", "");
        PROPERTY_KEY_DEFAULTS.put("wedpr.sync.sequencer.contract_address", "");
        PROPERTY_KEY_DEFAULTS.put("wedpr.sync.recorder.contract_version", "1");
        PROPERTY_KEY_DEFAULTS.put("wedpr.sync.queue_limit", "100000");
        PROPERTY_KEY_DEFAULTS.put("wedpr.sync.worker_idle_ms", "10");
        PROPERTY_KEY_DEFAULTS.put("wedpr.leader.election.keep.alive.seconds", "30");
        PROPERTY_KEY_DEFAULTS.put("wedpr.leader.election.expire.seconds", "60");
    }

    /**
     * 异步调度 wedpr-site 重启。通过独立 nohup 子进程执行 stop.sh + start.sh，
     * 避免当前 JVM 被 stop 后无法继续执行 start。
     */
    public void scheduleSiteRestart() throws WeDPRException {
        Path distDir = getDistDir();
        Path stopScript = distDir.resolve("stop.sh").normalize();
        Path startScript = distDir.resolve("start.sh").normalize();
        if (!stopScript.startsWith(distDir) || !startScript.startsWith(distDir)) {
            throw new WeDPRException("重启脚本路径非法");
        }
        if (!Files.isRegularFile(stopScript) || !Files.isRegularFile(startScript)) {
            throw new WeDPRException(
                    "未找到 start.sh 或 stop.sh，请确认 wedpr-site 以 dist 目录方式部署");
        }
        Path restartLog = distDir.resolve("restart.log");
        String restartCmd =
                String.format(
                        "nohup bash -c 'sleep 2; bash \"%s\"; sleep 1; bash \"%s\"' >> \"%s\" 2>&1 &",
                        stopScript.toAbsolutePath(),
                        startScript.toAbsolutePath(),
                        restartLog.toAbsolutePath());
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", restartCmd);
            processBuilder.directory(distDir.toFile());
            processBuilder.start();
            logger.info("已调度 wedpr-site 自动重启，部署目录: {}", distDir.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("调度 wedpr-site 重启失败, distDir: {}, error: ", distDir, e);
            throw new WeDPRException("调度 wedpr-site 重启失败: " + e.getMessage());
        }
    }

    /** 获取 wedpr-site 部署根目录（conf 的父目录，含 start.sh / stop.sh） */
    public Path getDistDir() throws WeDPRException {
        Path configDir = getConfigDir().toAbsolutePath().normalize();
        Path distDir = configDir.getParent();
        if (distDir == null || !Files.isDirectory(distDir)) {
            throw new WeDPRException(
                    "无法定位 wedpr-site 部署目录，serviceConfigPath=" + configDir);
        }
        return distDir;
    }

    /** 获取配置目录，优先使用 start.sh 注入的 serviceConfigPath */
    public Path getConfigDir() {
        String serviceConfigPath = System.getProperty("serviceConfigPath");
        if (StringUtils.isNotBlank(serviceConfigPath)) {
            return Paths.get(serviceConfigPath);
        }
        return Paths.get("conf");
    }

    public BlockchainConfigDTO loadConfig() throws Exception {
        BlockchainConfigDTO dto = new BlockchainConfigDTO();
        Path configDir = getConfigDir();
        dto.setConfigDir(configDir.toAbsolutePath().toString());
        dto.setPropertiesFile(configDir.resolve(WEDPR_PROPERTIES).toAbsolutePath().toString());
        dto.setTomlFile(configDir.resolve(CONFIG_TOML).toAbsolutePath().toString());

        loadFromPropertiesFile(configDir, dto);
        loadFromTomlFile(configDir, dto);
        // 以 JVM 已加载的运行时配置作为补充（文件不存在时仍可展示默认值）
        fillFromRuntimeConfig(dto);
        return dto;
    }

    public void saveConfig(BlockchainConfigDTO request) throws Exception {
        validateConfig(request);
        Path configDir = getConfigDir();
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }
        Map<String, String> propertyUpdates = buildPropertyUpdates(request);
        updatePropertiesFile(configDir.resolve(WEDPR_PROPERTIES), propertyUpdates);
        updateTomlFile(configDir.resolve(CONFIG_TOML), request);
        logger.info(
                "区块链配置已持久化到目录: {}, properties={}, toml={}",
                configDir.toAbsolutePath(),
                WEDPR_PROPERTIES,
                CONFIG_TOML);
    }

    private void validateConfig(BlockchainConfigDTO request) throws WeDPRException {
        if (StringUtils.isBlank(request.getChainGroupId())) {
            throw new WeDPRException("区块链群组 ID 不能为空");
        }
        if (StringUtils.isBlank(request.getRecorderFactoryContractAddress())) {
            throw new WeDPRException("ResourceLogRecordFactory 合约地址不能为空");
        }
        if (StringUtils.isBlank(request.getSequencerContractAddress())) {
            throw new WeDPRException("ResourceSequencer 合约地址不能为空");
        }
        if (request.getNetworkPeers() == null || request.getNetworkPeers().isEmpty()) {
            throw new WeDPRException("区块链节点 peers 不能为空");
        }
        for (String peer : request.getNetworkPeers()) {
            if (StringUtils.isBlank(peer) || !peer.contains(":")) {
                throw new WeDPRException("节点地址格式不正确，应为 host:port，当前: " + peer);
            }
        }
    }

    private Map<String, String> buildPropertyUpdates(BlockchainConfigDTO request) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("wedpr.chain.group_id", request.getChainGroupId().trim());
        updates.put("wedpr.chain.config_path", CONFIG_TOML);
        updates.put(
                "wedpr.sync.recorder.factory.contract_address",
                request.getRecorderFactoryContractAddress().trim());
        updates.put(
                "wedpr.sync.sequencer.contract_address",
                request.getSequencerContractAddress().trim());
        updates.put(
                "wedpr.sync.recorder.contract_version",
                String.valueOf(
                        request.getRecorderContractVersion() == null
                                ? 1
                                : request.getRecorderContractVersion()));
        updates.put(
                "wedpr.sync.queue_limit",
                String.valueOf(
                        request.getSyncQueueLimit() == null ? 100000 : request.getSyncQueueLimit()));
        updates.put(
                "wedpr.sync.worker_idle_ms",
                String.valueOf(
                        request.getSyncWorkerIdleMs() == null ? 10 : request.getSyncWorkerIdleMs()));
        updates.put(
                "wedpr.leader.election.keep.alive.seconds",
                String.valueOf(
                        request.getLeaderElectionKeepAliveSeconds() == null
                                ? 30
                                : request.getLeaderElectionKeepAliveSeconds()));
        updates.put(
                "wedpr.leader.election.expire.seconds",
                String.valueOf(
                        request.getLeaderElectionExpireSeconds() == null
                                ? 60
                                : request.getLeaderElectionExpireSeconds()));
        return updates;
    }

    private void loadFromPropertiesFile(Path configDir, BlockchainConfigDTO dto) throws Exception {
        Path propertiesPath = configDir.resolve(WEDPR_PROPERTIES);
        if (!Files.exists(propertiesPath)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        dto.setChainGroupId(properties.getProperty("wedpr.chain.group_id", dto.getChainGroupId()));
        dto.setRecorderFactoryContractAddress(
                properties.getProperty(
                        "wedpr.sync.recorder.factory.contract_address",
                        dto.getRecorderFactoryContractAddress()));
        dto.setSequencerContractAddress(
                properties.getProperty(
                        "wedpr.sync.sequencer.contract_address", dto.getSequencerContractAddress()));
        dto.setRecorderContractVersion(
                parseInteger(
                        properties.getProperty("wedpr.sync.recorder.contract_version"),
                        dto.getRecorderContractVersion()));
        dto.setSyncQueueLimit(
                parseInteger(
                        properties.getProperty("wedpr.sync.queue_limit"), dto.getSyncQueueLimit()));
        dto.setSyncWorkerIdleMs(
                parseInteger(
                        properties.getProperty("wedpr.sync.worker_idle_ms"),
                        dto.getSyncWorkerIdleMs()));
        dto.setLeaderElectionKeepAliveSeconds(
                parseInteger(
                        properties.getProperty("wedpr.leader.election.keep.alive.seconds"),
                        dto.getLeaderElectionKeepAliveSeconds()));
        dto.setLeaderElectionExpireSeconds(
                parseInteger(
                        properties.getProperty("wedpr.leader.election.expire.seconds"),
                        dto.getLeaderElectionExpireSeconds()));
    }

    private void loadFromTomlFile(Path configDir, BlockchainConfigDTO dto) throws Exception {
        Path tomlPath = configDir.resolve(CONFIG_TOML);
        if (!Files.exists(tomlPath)) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(tomlPath)) {
            Toml toml = new Toml().read(inputStream);
            Toml network = toml.getTable("network");
            if (network != null) {
                dto.setNetworkDefaultGroup(
                        network.getString("defaultGroup", dto.getNetworkDefaultGroup()));
                dto.setNetworkMessageTimeout(
                        network.getString("messageTimeout", dto.getNetworkMessageTimeout()));
                List<String> peers = network.getList("peers");
                if (peers != null && !peers.isEmpty()) {
                    dto.setNetworkPeers(new ArrayList<>(peers));
                }
            }
            Toml cryptoMaterial = toml.getTable("cryptoMaterial");
            if (cryptoMaterial != null) {
                dto.setCryptoCertPath(cryptoMaterial.getString("certPath", dto.getCryptoCertPath()));
                dto.setCryptoDisableSsl(
                        cryptoMaterial.getString("disableSsl", dto.getCryptoDisableSsl()));
                dto.setCryptoUseSmCrypto(
                        cryptoMaterial.getString("useSMCrypto", dto.getCryptoUseSmCrypto()));
            }
        }
    }

    private void fillFromRuntimeConfig(BlockchainConfigDTO dto) {
        dto.setChainGroupId(
                WeDPRConfig.apply("wedpr.chain.group_id", dto.getChainGroupId(), Boolean.FALSE));
        dto.setRecorderFactoryContractAddress(
                WeDPRConfig.apply(
                        "wedpr.sync.recorder.factory.contract_address",
                        dto.getRecorderFactoryContractAddress(),
                        Boolean.FALSE));
        dto.setSequencerContractAddress(
                WeDPRConfig.apply(
                        "wedpr.sync.sequencer.contract_address",
                        dto.getSequencerContractAddress(),
                        Boolean.FALSE));
        dto.setRecorderContractVersion(
                WeDPRConfig.apply(
                        "wedpr.sync.recorder.contract_version", dto.getRecorderContractVersion()));
        dto.setSyncQueueLimit(WeDPRConfig.apply("wedpr.sync.queue_limit", dto.getSyncQueueLimit()));
        dto.setSyncWorkerIdleMs(
                WeDPRConfig.apply("wedpr.sync.worker_idle_ms", dto.getSyncWorkerIdleMs()));
        dto.setLeaderElectionKeepAliveSeconds(
                WeDPRConfig.apply(
                        "wedpr.leader.election.keep.alive.seconds",
                        dto.getLeaderElectionKeepAliveSeconds()));
        dto.setLeaderElectionExpireSeconds(
                WeDPRConfig.apply(
                        "wedpr.leader.election.expire.seconds",
                        dto.getLeaderElectionExpireSeconds()));
        if (StringUtils.isBlank(dto.getNetworkDefaultGroup())) {
            dto.setNetworkDefaultGroup(dto.getChainGroupId());
        }
    }

    private void updatePropertiesFile(Path propertiesPath, Map<String, String> updates)
            throws Exception {
        List<String> lines = new ArrayList<>();
        if (Files.exists(propertiesPath)) {
            lines = Files.readAllLines(propertiesPath, StandardCharsets.UTF_8);
        }
        Set<String> updatedKeys = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                result.add(line);
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx > 0) {
                String key = line.substring(0, eqIdx).trim();
                if (updates.containsKey(key)) {
                    result.add(key + "=" + updates.get(key));
                    updatedKeys.add(key);
                    continue;
                }
            }
            result.add(line);
        }
        boolean appendedSection = false;
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (updatedKeys.contains(entry.getKey())) {
                continue;
            }
            if (!appendedSection) {
                result.add("");
                result.add("### the blockchain configuration (updated by WebUI)");
                appendedSection = true;
            }
            result.add(entry.getKey() + "=" + entry.getValue());
        }
        if (!Files.exists(propertiesPath)) {
            result.clear();
            result.add("### the blockchain configuration");
            for (Map.Entry<String, String> entry : updates.entrySet()) {
                result.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        Files.write(propertiesPath, result, StandardCharsets.UTF_8);
    }

    private void updateTomlFile(Path tomlPath, BlockchainConfigDTO request) throws Exception {
        List<String> lines = new ArrayList<>();
        if (Files.exists(tomlPath)) {
            lines = Files.readAllLines(tomlPath, StandardCharsets.UTF_8);
        } else {
            lines = buildDefaultTomlLines(request);
            Files.write(tomlPath, lines, StandardCharsets.UTF_8);
            return;
        }

        String peersValue = formatPeersTomlValue(request.getNetworkPeers());
        String defaultGroup =
                StringUtils.defaultIfBlank(request.getNetworkDefaultGroup(), request.getChainGroupId());
        Map<String, String> replacements = new HashMap<>();
        replacements.put("defaultGroup", defaultGroup);
        replacements.put("messageTimeout", request.getNetworkMessageTimeout());
        replacements.put("peers", peersValue);
        replacements.put("certPath", request.getCryptoCertPath());
        replacements.put("disableSsl", request.getCryptoDisableSsl());
        replacements.put("useSMCrypto", request.getCryptoUseSmCrypto());

        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String updatedLine = line;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                Pattern pattern =
                        Pattern.compile(
                                "^\\s*"
                                        + Pattern.quote(entry.getKey())
                                        + "\\s*=\\s*.*$",
                                Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    if ("peers".equals(entry.getKey())) {
                        updatedLine = "peers=" + entry.getValue();
                    } else if ("defaultGroup".equals(entry.getKey())) {
                        updatedLine = "defaultGroup=\"" + entry.getValue() + "\"";
                    } else if ("messageTimeout".equals(entry.getKey())) {
                        updatedLine = "messageTimeout = \"" + entry.getValue() + "\"";
                    } else if ("certPath".equals(entry.getKey())) {
                        updatedLine =
                                "certPath = \""
                                        + entry.getValue()
                                        + "\"                           # The certification path";
                    } else if ("disableSsl".equals(entry.getKey())) {
                        updatedLine =
                                "disableSsl = \""
                                        + entry.getValue()
                                        + "\"                        # Communication with nodes without SSL";
                    } else if ("useSMCrypto".equals(entry.getKey())) {
                        updatedLine =
                                "useSMCrypto = \""
                                        + entry.getValue()
                                        + "\"                       # RPC SM crypto type";
                    }
                    break;
                }
            }
            result.add(updatedLine);
        }
        Files.write(tomlPath, result, StandardCharsets.UTF_8);
    }

    private List<String> buildDefaultTomlLines(BlockchainConfigDTO request) {
        List<String> lines = new ArrayList<>();
        lines.add("[cryptoMaterial]");
        lines.add("");
        lines.add("certPath = \"" + request.getCryptoCertPath() + "\"");
        lines.add("disableSsl = \"" + request.getCryptoDisableSsl() + "\"");
        lines.add("useSMCrypto = \"" + request.getCryptoUseSmCrypto() + "\"");
        lines.add("");
        lines.add("[network]");
        lines.add("messageTimeout = \"" + request.getNetworkMessageTimeout() + "\"");
        lines.add(
                "defaultGroup=\""
                        + StringUtils.defaultIfBlank(
                                request.getNetworkDefaultGroup(), request.getChainGroupId())
                        + "\"");
        lines.add("peers=" + formatPeersTomlValue(request.getNetworkPeers()));
        lines.add("");
        lines.add("[account]");
        lines.add("keyStoreDir = \"account\"");
        lines.add("accountFileFormat = \"pem\"");
        lines.add("");
        lines.add("[threadPool]");
        return lines;
    }

    private String formatPeersTomlValue(List<String> peers) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < peers.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("\"").append(peers.get(i).trim()).append("\"");
        }
        builder.append("]");
        return builder.toString();
    }

    private Integer parseInteger(String value, Integer defaultValue) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
