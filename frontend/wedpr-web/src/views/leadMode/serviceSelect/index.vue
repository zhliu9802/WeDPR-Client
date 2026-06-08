<template>
  <div class="select-data">
    <div class="form-search">
      <el-form :inline="true" @submit="queryHandle" :model="searchForm" ref="searchForm" size="small">
        <el-form-item prop="serviceName" label="服务名称：">
          <el-input clearable style="width: 180px" v-model="searchForm.serviceName" placeholder="请输入"> </el-input>
        </el-form-item>
        <el-form-item prop="serviceId" label="服务ID：">
          <el-input clearable style="width: 180px" v-model="searchForm.serviceId" placeholder="请输入"> </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="queryFlag" @click="queryHandle">
            {{ queryFlag ? '查询中...' : '查询' }}
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="default" @click="reset"> 重置 </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="card-container">
      <serviceCard
        @selected="(checked) => selected(checked, item)"
        :selected="serviceId === item.serviceId"
        v-for="item in tableData"
        :serviceInfo="item"
        :key="item.serviceId"
        :showEdit="false"
      />
    </div>
    <el-empty v-if="!total" :image-size="120" description="暂无数据">
      <img slot="image" src="~Assets/images/pic_empty_news.png" alt="" />
    </el-empty>
    <we-pagination
      :pageSizesOption="[8, 12, 16, 24, 32]"
      :total="total"
      :page_offset="pageData.page_offset"
      :page_size="pageData.page_size"
      @paginationChange="paginationHandle"
    ></we-pagination>
  </div>
</template>
<script>
import { mapGetters } from 'vuex'
import serviceCard from '@/components/serviceCard.vue'
import { serviceManageServer } from 'Api'
import { serviceAuthStatus } from 'Utils/constant.js'
import { handleParamsValid } from 'Utils/index.js'
export default {
  name: 'participateSelect',
  model: {
    prop: 'value'
  },
  props: {
    showTagsModal: {
      type: Boolean,
      default: false
    },
    serviceType: {
      type: String,
      default: ''
    },
    value: {
      type: String,
      default: ''
    }
  },
  components: {
    serviceCard
  },
  data() {
    return {
      formLabelWidth: '112px',
      loadingFlag: false,
      groupId: '',
      pageData: { page_offset: 1, page_size: 4 },
      total: -1,
      dataList: [],
      selectedData: {},
      fieldList: [],
      tableData: [],
      serviceId: '',
      searchForm: {
        serviceName: '',
        serviceId: ''
      },
      searchQuery: {
        serviceName: '',
        serviceId: ''
      }
    }
  },
  created() {
    // 编辑的回显
    this.init()
  },
  computed: {
    ...mapGetters(['userId', 'agencyId'])
  },
  watch: {
    value(val) {
      if (val !== this.serviceId) {
        this.init()
      }
    }
  },
  methods: {
    init() {
      // 回显初始化
      if (this.value) {
        this.serviceId = this.value
        this.searchForm.serviceId = this.value
      }
      this.queryHandle()
    },
    // 查询
    queryHandle() {
      this.searchQuery = { ...this.searchForm }
      this.pageData.page_offset = 1
      this.getPublishList()
    },
    reset() {
      this.$refs.searchForm.resetFields()
    },
    // 单选 选中后更新标签字段选项下拉
    selected(checked, row) {
      const { serviceId } = row
      if (checked) {
        this.serviceId = serviceId
        this.$emit('input', serviceId)
      } else {
        this.serviceId = ''
        this.$emit('input', '')
      }
    },
    // 获取服务列表
    async getPublishList(cb) {
      const { page_offset, page_size } = this.pageData
      const { serviceName = '', serviceId = '' } = this.searchQuery
      const searchParams = handleParamsValid({ serviceName, serviceId })
      const { serviceType } = this
      const params = {
        condition: { serviceId: '', ...searchParams, serviceType, authStatus: serviceAuthStatus.Authorized, status: 'PublishSuccess' },
        serviceIdList: [],
        pageNum: page_offset,
        pageSize: page_size
      }
      this.loadingFlag = true
      const res = await serviceManageServer.getPublishList(params)
      this.loadingFlag = false
      console.log(res)
      if (res.code === 0 && res.data) {
        const { wedprPublishedServiceList = [], total } = res.data
        this.tableData = wedprPublishedServiceList.map((v) => {
          return {
            ...v,
            isOnwer: v.owner === this.userId && v.agency === this.agencyId,
            showSelect: true
          }
        })
        this.total = total
        cb && cb()
      } else {
        this.tableData = []
        this.total = 0
      }
    },
    paginationHandle(pageData) {
      this.pageData = { ...pageData }
      this.queryHandle()
    }
  }
}
</script>
<style lang="less" scoped>
.select-data {
  border: 1px solid #e0e4ed;
  border-radius: 4px;
  padding: 20px;
  height: auto;
  margin-bottom: 42px;
  .el-empty {
    margin-top: 0;
  }
}
.card-container {
  margin: -10px;
  height: auto;
  overflow: auto;
}

::v-deep div.data-card {
  margin: 10px;
  ul li:first-child {
    line-height: 26px;
    margin-bottom: 8px;
  }
  ul li span.data-size {
    i {
      font-size: 18px;
      line-height: 26px;
    }
  }
}
</style>
