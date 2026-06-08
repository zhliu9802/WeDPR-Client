<template>
  <div class="blockchain-config" style="height: 100%; overflow: auto">
    <div class="warning">
      <p><i class="el-icon-warning"></i> 区块链配置将持久化写入服务端 <code>conf/wedpr.properties</code> 与 <code>conf/config.toml</code>。区块链连接在 JVM 启动时加载，保存后需<strong>重启 wedpr-site</strong> 才能生效；点击「保存配置」确认后将自动重启。</p>
      <p class="next">多机构部署时，各站点端与管理端须连接同一 FISCO BCOS 群组，且合约地址保持一致。</p>
    </div>

    <el-form
      v-loading="loading"
      ref="blockchainForm"
      :model="form"
      :rules="rules"
      label-width="220px"
      label-position="right"
      size="small"
      class="config-form"
    >
      <el-card shadow="never" class="config-card">
        <div slot="header" class="card-header">链连接配置（config.toml）</div>
        <el-form-item label="区块链群组 ID：" prop="chainGroupId">
          <el-input v-model="form.chainGroupId" placeholder="如 group0" style="width: 360px" />
          <span class="form-tip">对应 wedpr.chain.group_id 与 config.toml defaultGroup</span>
        </el-form-item>
        <el-form-item label="节点 RPC 地址：" prop="networkPeersText">
          <el-input
            v-model="form.networkPeersText"
            type="textarea"
            :rows="3"
            placeholder="每行一个节点，格式 host:port&#10;127.0.0.1:20200"
            style="width: 480px"
          />
          <span class="form-tip">对应 config.toml [network] peers</span>
        </el-form-item>
        <el-form-item label="消息超时（ms）：" prop="networkMessageTimeout">
          <el-input v-model="form.networkMessageTimeout" placeholder="10000" style="width: 200px" />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="config-card">
        <div slot="header" class="card-header">智能合约配置（wedpr.properties）</div>
        <el-form-item label="Factory 合约地址：" prop="recorderFactoryContractAddress">
          <el-input
            v-model="form.recorderFactoryContractAddress"
            placeholder="ResourceLogRecordFactory 部署地址"
            style="width: 480px"
          />
        </el-form-item>
        <el-form-item label="Sequencer 合约地址：" prop="sequencerContractAddress">
          <el-input
            v-model="form.sequencerContractAddress"
            placeholder="ResourceSequencer 部署地址"
            style="width: 480px"
          />
        </el-form-item>
        <el-form-item label="合约版本号：" prop="recorderContractVersion">
          <el-input-number v-model="form.recorderContractVersion" :min="1" :max="99" />
          <span class="form-tip">对应 wedpr.sync.recorder.contract_version</span>
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="config-card">
        <div slot="header" class="card-header">SDK 证书配置（config.toml）</div>
        <el-form-item label="证书目录 certPath：" prop="cryptoCertPath">
          <el-input v-model="form.cryptoCertPath" placeholder="conf" style="width: 360px" />
        </el-form-item>
        <el-form-item label="禁用 SSL：" prop="cryptoDisableSsl">
          <el-select v-model="form.cryptoDisableSsl" style="width: 160px">
            <el-option label="false（启用 SSL）" value="false" />
            <el-option label="true（禁用 SSL）" value="true" />
          </el-select>
        </el-form-item>
        <el-form-item label="国密加密：" prop="cryptoUseSmCrypto">
          <el-select v-model="form.cryptoUseSmCrypto" style="width: 160px">
            <el-option label="false（ECDSA）" value="false" />
            <el-option label="true（国密）" value="true" />
          </el-select>
        </el-form-item>
      </el-card>

      <el-collapse v-model="advancedActive" class="advanced-collapse">
        <el-collapse-item title="高级同步配置（可选）" name="advanced">
          <el-form-item label="同步队列上限：" prop="syncQueueLimit">
            <el-input-number v-model="form.syncQueueLimit" :min="1000" :max="1000000" :step="1000" />
          </el-form-item>
          <el-form-item label="Worker 空闲间隔（ms）：" prop="syncWorkerIdleMs">
            <el-input-number v-model="form.syncWorkerIdleMs" :min="1" :max="60000" />
          </el-form-item>
          <el-form-item label="Leader 保活（秒）：" prop="leaderElectionKeepAliveSeconds">
            <el-input-number v-model="form.leaderElectionKeepAliveSeconds" :min="5" :max="600" />
          </el-form-item>
          <el-form-item label="Leader 过期（秒）：" prop="leaderElectionExpireSeconds">
            <el-input-number v-model="form.leaderElectionExpireSeconds" :min="10" :max="3600" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>

      <div class="file-info" v-if="form.configDir">
        <p>配置文件目录：<code>{{ form.configDir }}</code></p>
        <p>properties：<code>{{ form.propertiesFile }}</code></p>
        <p>config.toml：<code>{{ form.tomlFile }}</code></p>
      </div>

      <div class="form-actions">
        <el-button size="small" @click="loadConfig" :loading="loading">重新加载</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
      </div>
    </el-form>
  </div>
</template>

<script>
import blockchainConfigServer from '@/apis/blockchainConfig'

const defaultForm = () => ({
  chainGroupId: 'group0',
  recorderFactoryContractAddress: '',
  sequencerContractAddress: '',
  recorderContractVersion: 1,
  syncQueueLimit: 100000,
  syncWorkerIdleMs: 10,
  leaderElectionKeepAliveSeconds: 30,
  leaderElectionExpireSeconds: 60,
  networkDefaultGroup: 'group0',
  networkPeersText: '127.0.0.1:20200',
  networkMessageTimeout: '10000',
  cryptoCertPath: 'conf',
  cryptoDisableSsl: 'false',
  cryptoUseSmCrypto: 'false',
  configDir: '',
  propertiesFile: '',
  tomlFile: ''
})

export default {
  name: 'blockchainConfig',
  data() {
    return {
      loading: false,
      saving: false,
      advancedActive: [],
      form: defaultForm(),
      rules: {
        chainGroupId: [{ required: true, message: '请输入区块链群组 ID', trigger: 'blur' }],
        networkPeersText: [{ required: true, message: '请输入至少一个节点 RPC 地址', trigger: 'blur' }],
        recorderFactoryContractAddress: [
          { required: true, message: '请输入 Factory 合约地址', trigger: 'blur' }
        ],
        sequencerContractAddress: [
          { required: true, message: '请输入 Sequencer 合约地址', trigger: 'blur' }
        ],
        recorderContractVersion: [{ required: true, message: '请输入合约版本号', trigger: 'change' }]
      }
    }
  },
  created() {
    this.loadConfig()
  },
  methods: {
    fillForm(data) {
      const peers = data.networkPeers && data.networkPeers.length ? data.networkPeers : ['127.0.0.1:20200']
      this.form = {
        ...defaultForm(),
        chainGroupId: data.chainGroupId || 'group0',
        recorderFactoryContractAddress: data.recorderFactoryContractAddress || '',
        sequencerContractAddress: data.sequencerContractAddress || '',
        recorderContractVersion: data.recorderContractVersion != null ? data.recorderContractVersion : 1,
        syncQueueLimit: data.syncQueueLimit != null ? data.syncQueueLimit : 100000,
        syncWorkerIdleMs: data.syncWorkerIdleMs != null ? data.syncWorkerIdleMs : 10,
        leaderElectionKeepAliveSeconds:
          data.leaderElectionKeepAliveSeconds != null ? data.leaderElectionKeepAliveSeconds : 30,
        leaderElectionExpireSeconds:
          data.leaderElectionExpireSeconds != null ? data.leaderElectionExpireSeconds : 60,
        networkDefaultGroup: data.networkDefaultGroup || data.chainGroupId || 'group0',
        networkPeersText: peers.join('\n'),
        networkMessageTimeout: data.networkMessageTimeout || '10000',
        cryptoCertPath: data.cryptoCertPath || 'conf',
        cryptoDisableSsl: data.cryptoDisableSsl || 'false',
        cryptoUseSmCrypto: data.cryptoUseSmCrypto || 'false',
        configDir: data.configDir || '',
        propertiesFile: data.propertiesFile || '',
        tomlFile: data.tomlFile || ''
      }
    },
    buildSavePayload() {
      const peers = this.form.networkPeersText
        .split(/[\n,;]+/)
        .map((v) => v.trim())
        .filter((v) => v)
      return {
        chainGroupId: this.form.chainGroupId,
        recorderFactoryContractAddress: this.form.recorderFactoryContractAddress,
        sequencerContractAddress: this.form.sequencerContractAddress,
        recorderContractVersion: this.form.recorderContractVersion,
        syncQueueLimit: this.form.syncQueueLimit,
        syncWorkerIdleMs: this.form.syncWorkerIdleMs,
        leaderElectionKeepAliveSeconds: this.form.leaderElectionKeepAliveSeconds,
        leaderElectionExpireSeconds: this.form.leaderElectionExpireSeconds,
        networkDefaultGroup: this.form.chainGroupId,
        networkPeers: peers,
        networkMessageTimeout: this.form.networkMessageTimeout,
        cryptoCertPath: this.form.cryptoCertPath,
        cryptoDisableSsl: this.form.cryptoDisableSsl,
        cryptoUseSmCrypto: this.form.cryptoUseSmCrypto
      }
    },
    async loadConfig() {
      this.loading = true
      try {
        const res = await blockchainConfigServer.getBlockchainConfig()
        if (res.code === 0 && res.data) {
          this.fillForm(res.data)
        }
      } finally {
        this.loading = false
      }
    },
    handleSave() {
      this.$refs.blockchainForm.validate(async (valid) => {
        if (!valid) {
          return false
        }
        try {
          await this.$confirm(
            '配置将写入服务端并自动重启 wedpr-site，重启期间服务将短暂不可用（约 30～60 秒），是否继续？',
            '保存并重启',
            {
              confirmButtonText: '确认保存并重启',
              cancelButtonText: '取消',
              type: 'warning'
            }
          )
        } catch {
          return
        }
        this.saving = true
        try {
          const payload = Object.assign({}, this.buildSavePayload(), { restartSite: true })
          const res = await blockchainConfigServer.saveBlockchainConfig(payload)
          if (res.code === 0) {
            if (res.data) {
              this.fillForm(res.data)
            }
            this.$alert(
              '配置已保存，wedpr-site 正在后台重启。当前页面可能在重启过程中断开连接，请等待约 30～60 秒后刷新浏览器重新登录。',
              '重启中',
              { confirmButtonText: '我知道了', type: 'info' }
            )
          }
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.blockchain-config {
  padding: 0 16px 24px;
  .warning {
    background: #fff7e6;
    border: 1px solid #ffd591;
    border-radius: 4px;
    padding: 12px 16px;
    margin-bottom: 16px;
    color: #614700;
    font-size: 13px;
    line-height: 1.6;
    p {
      margin: 0;
    }
    .next {
      margin-top: 6px;
      color: #8c6d1f;
    }
    code {
      background: rgba(0, 0, 0, 0.06);
      padding: 0 4px;
      border-radius: 2px;
    }
  }
  .config-card {
    margin-bottom: 16px;
    .card-header {
      font-weight: 600;
      font-size: 14px;
    }
  }
  .form-tip {
    display: block;
    color: #909399;
    font-size: 12px;
    margin-top: 4px;
  }
  .advanced-collapse {
    margin-bottom: 16px;
    border: none;
  }
  .file-info {
    background: #f5f7fa;
    border-radius: 4px;
    padding: 12px 16px;
    margin-bottom: 16px;
    font-size: 12px;
    color: #606266;
    p {
      margin: 4px 0;
      word-break: break-all;
    }
    code {
      color: #303133;
    }
  }
  .form-actions {
    padding: 8px 0 16px;
  }
}
</style>
