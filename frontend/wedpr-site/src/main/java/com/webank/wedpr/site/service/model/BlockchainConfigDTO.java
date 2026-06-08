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
package com.webank.wedpr.site.service.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 区块链在线配置 DTO，对应 wedpr.properties 与 config.toml 中的链相关字段 */
@Data
public class BlockchainConfigDTO {
    // wedpr.properties
    private String chainGroupId = "group0";
    private String recorderFactoryContractAddress = "";
    private String sequencerContractAddress = "";
    private Integer recorderContractVersion = 1;
    private Integer syncQueueLimit = 100000;
    private Integer syncWorkerIdleMs = 10;
    private Integer leaderElectionKeepAliveSeconds = 30;
    private Integer leaderElectionExpireSeconds = 60;

    // config.toml [network]
    private String networkDefaultGroup = "group0";
    private List<String> networkPeers = new ArrayList<>();
    private String networkMessageTimeout = "10000";

    // config.toml [cryptoMaterial]
    private String cryptoCertPath = "conf";
    private String cryptoDisableSsl = "false";
    private String cryptoUseSmCrypto = "false";

    // 配置文件路径（只读，供前端展示）
    private String configDir = "";
    private String propertiesFile = "";
    private String tomlFile = "";

    /** 保存后是否自动重启 wedpr-site（仅请求参数，不写入配置文件） */
    private Boolean restartSite = false;
}
