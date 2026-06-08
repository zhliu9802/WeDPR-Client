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
package com.webank.wedpr.site.controller;

import com.webank.wedpr.common.utils.Constant;
import com.webank.wedpr.common.utils.WeDPRResponse;
import com.webank.wedpr.site.service.BlockchainConfigService;
import com.webank.wedpr.site.service.model.BlockchainConfigDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 区块链配置在线管理接口，读写 conf/wedpr.properties 与 conf/config.toml */
@RestController
@RequestMapping(
        path = Constant.WEDPR_API_PREFIX + "/blockchain/",
        produces = {"application/json"})
public class BlockchainConfigController {
    private static final Logger logger = LoggerFactory.getLogger(BlockchainConfigController.class);

    @Autowired private BlockchainConfigService blockchainConfigService;

    @GetMapping("/getConfig")
    public WeDPRResponse getConfig() {
        try {
            BlockchainConfigDTO config = blockchainConfigService.loadConfig();
            return new WeDPRResponse(Constant.WEDPR_SUCCESS, Constant.WEDPR_SUCCESS_MSG, config);
        } catch (Exception e) {
            logger.warn("getBlockchainConfig exception, error: ", e);
            return new WeDPRResponse(
                    Constant.WEDPR_FAILED, "读取区块链配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/saveConfig")
    public WeDPRResponse saveConfig(@RequestBody BlockchainConfigDTO request) {
        try {
            boolean restartSite = Boolean.TRUE.equals(request.getRestartSite());
            blockchainConfigService.saveConfig(request);
            String message;
            if (restartSite) {
                blockchainConfigService.scheduleSiteRestart();
                message = "区块链配置已保存，wedpr-site 正在后台重启，请稍候刷新页面";
            } else {
                message = "区块链配置已保存，请重启 wedpr-site 使配置生效";
            }
            BlockchainConfigDTO savedConfig = blockchainConfigService.loadConfig();
            return new WeDPRResponse(Constant.WEDPR_SUCCESS, message, savedConfig);
        } catch (Exception e) {
            logger.warn("saveBlockchainConfig exception, request: {}, error: ", request, e);
            return new WeDPRResponse(
                    Constant.WEDPR_FAILED, "保存区块链配置失败: " + e.getMessage());
        }
    }
}
