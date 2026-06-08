<template>
  <el-dialog :width="accessKeyList.length ? '652px' : '420px'" title="申请调用" @close="handleClose" :visible="showApplyModal">
    <div v-if="accessKeyList.length" class="form-con">
      <div class="key-info" v-if="selectedAccessKey && selectedAccessKey.accessKeyID">
        <ul>
          <li>AccessID：</li>
          <li>{{ selectedAccessKey.accessKeyID }}</li>
        </ul>
        <ul>
          <li>Access Secret：</li>
          <li>
            {{ showSercret ? selectedAccessKey.accessKeySecret : maskString(selectedAccessKey.accessKeySecret) }}
            <img v-if="showSercret" @click="hideSecretStr" src="~Assets/images/hide.png" alt="" />
            <img @click="openSecretStr" v-else src="~Assets/images/show.png" alt="" />
          </li>
        </ul>
      </div>
      <el-form v-else label-position="right" size="small" :model="dataForm" :rules="dataFormRules" ref="dataForm" :label-width="formLabelWidth">
        <el-form-item label="绑定AccessKey：" prop="accessKey">
          <el-select value-key="accessKeyID" style="width: 450px" v-model="dataForm.accessKey" placeholder="请选择该服务所绑定的AccessKey" clearable>
            <el-option :label="item.label" :value="item.value" :key="item.label" v-for="item in accessKeyList"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <div class="noak" v-else><i class="el-icon-warning" />暂无启用中的Accesskey，去创建?</div>
    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取 消</el-button>
      <el-button v-if="selectedAccessKey && selectedAccessKey.accessKeyID" :loading="loading" type="primary" @click="copy">复制</el-button>
      <el-button v-else :loading="loading" type="primary" @click="handleOk">确 定</el-button>
    </div>
  </el-dialog>
</template>
<script>
import { accountManageServer, accessKeyManageServer } from 'Api'
import { maskString } from 'Utils/index.js'
export default {
  name: 'applyService',
  props: {
    showApplyModal: {
      type: Boolean,
      default: false
    },
    serviceId: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      dataForm: {
        accessKey: {}
      },
      dataFormRules: {
        accessKey: [{ required: true, message: '绑定AccessKey不能为空', trigger: 'blur' }]
      },
      formLabelWidth: '142px',
      loading: false,
      accessKeyList: [],
      accessInfo: {},
      selectedAccessKey: null,
      pageData: {
        page_offset: 1,
        page_size: 999
      },
      showSercret: false,
      maskString
    }
  },
  created() {
    this.queryAccessKeyList()
  },
  methods: {
    handleClose() {
      this.$emit('closeModal')
    },
    hideSecretStr() {
      this.showSercret = false
    },
    openSecretStr() {
      this.showSercret = true
    },
    copy() {
      const { accessKeyID, accessKeySecret } = this.selectedAccessKey
      // 模拟 输入框
      const val = `accessKeyID：${accessKeyID}\naccessKeySecret：${accessKeySecret}`
      const cInput = document.createElement('textarea')
      cInput.value = val
      document.body.appendChild(cInput)
      cInput.select() // 选取文本框内容
      // 执行浏览器复制命令
      // 复制命令会将当前选中的内容复制到剪切板中（这里就是创建的input标签）
      // Input要在正常的编辑状态下原生复制方法才会生效
      document.execCommand('copy')
      this.$message.success('内容已复制到剪贴板')
      // 复制成功后再将构造的标签 移除
      document.body.removeChild(cInput)
      this.selectedAccessKey = null
      this.$emit('closeModal')
    },
    // 获取账户列表
    async queryAccessKeyList() {
      const { page_offset, page_size } = this.pageData
      const params = { condition: { status: 'Enable', id: '' }, pageNum: page_offset, pageSize: page_size }
      const res = await accessKeyManageServer.queryAccessKeyList(params)
      console.log(res)
      if (res.code === 0 && res.data) {
        const { credentials = [] } = res.data
        this.accessKeyList = credentials.map((v) => {
          return {
            label: v.accessKeyID,
            value: v
          }
        })
      }
    },
    // 创建账号
    async addUser(params) {
      const res = await accountManageServer.addUser(params)
      this.loading = false
      if (res.code === 0) {
        this.userNameSelectList = []
        this.$message({ type: 'success', message: '用户添加成功' })
        this.$emit('handlOK')
      }
    },
    handleOk() {
      if (this.accessKeyList.length) {
        this.$refs.dataForm.validate((valid) => {
          if (valid) {
            this.selectedAccessKey = this.dataForm.accessKey
          }
        })
      } else {
        this.$router.push({ path: 'accessKeyManage' })
      }
    }
  }
}
</script>
<style lang="less" scoped>
div.form-con {
  .el-upload-list__item-name {
    text-align: left;
  }
  div.type-con {
    margin-bottom: 18px;
    .type-label {
      width: 110px;
      display: inline-block;
      text-align: right;
      margin-right: 12px;
    }
  }
  .key-info {
    background-color: rgb(246, 247, 251);
    padding: 20px 10px;
    ul {
      display: flex;

      li:first-child {
        text-align: right;
        width: 120px;
      }
      li:last-child {
        flex: 1;
        img {
          vertical-align: middle;
          width: 20px;
          height: auto;
          margin-left: 10px;
          cursor: pointer;
          transform: translateY(-1px);
        }
      }
    }
    ul:first-child {
      margin-bottom: 10px;
    }
  }
}
div.noak {
  text-align: center;
  i {
    font-size: 16px;
    margin-right: 4px;
    transform: translateY(1px);
  }
}
</style>
