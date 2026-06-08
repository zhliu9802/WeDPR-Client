<template>
  <div class="record">
    <div class="form-search">
      <el-form :inline="true" @submit="queryHandle" :model="searchForm" ref="searchForm" size="small">
        <el-form-item prop="name" label="模型名称：">
          <el-input clearable style="width: 180px" v-model="searchForm.name" placeholder="请输入"> </el-input>
        </el-form-item>
        <el-form-item prop="id" label="模型ID：">
          <el-input style="width: 180px" v-model="searchForm.id" placeholder="请输入" clearable> </el-input>
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
    <div class="card-container" v-if="modelTableData.length" v-loading="queryFlag">
      <modelCard
        v-for="item in modelTableData"
        :selected="selectedId === item.id"
        @selected="(data) => hanleSelectedModel(data, item)"
        :modelInfo="item"
        :key="item.id"
      ></modelCard>
    </div>
    <el-empty v-else :image-size="120" description="暂无数据">
      <img slot="image" src="~Assets/images/pic_empty_news.png" alt="" />
    </el-empty>
    <we-pagination
      :pageSizesOption="pageSizesOption"
      :total="total"
      :page_offset="pageData.page_offset"
      :page_size="pageData.page_size"
      @paginationChange="paginationHandle"
    ></we-pagination>
  </div>
</template>
<script>
import { settingManageServer } from 'Api'
import { mapGetters } from 'vuex'
import modelCard from '@/components/modelCard.vue'
import { jobModelSettingMap } from 'Utils/constant.js'
import { handleParamsValid } from 'Utils/index.js'
export default {
  name: 'modelSelect',
  model: {
    prop: 'value'
  },
  props: {
    value: {
      type: String,
      default: ''
    },
    pageSizesOption: {
      type: Array,
      default: () => {
        return [8, 12, 16, 24, 32]
      }
    },
    jobType: {
      type: String,
      default: ''
    },
    queriedTypes: {
      type: Array,
      default: () => {
        return []
      }
    }
  },
  components: {
    modelCard
  },
  data() {
    return {
      queryFlag: false,
      pageData: { page_offset: 1, page_size: 8 },
      modelTableData: [],
      searchForm: {
        name: '',
        id: ''
      },
      searchQuery: {
        name: '',
        id: ''
      },
      selectedId: ''
    }
  },
  created() {
    this.pageData.page_size = this.pageSizesOption[0]
    if (this.value) {
      const { id } = this.value
      this.searchForm.id = id
      this.selectedId = id
    }
    this.queryHandle()
  },
  computed: {
    ...mapGetters(['userId', 'agencyId'])
  },
  methods: {
    reset() {
      this.$refs.searchForm.resetFields()
    },
    hanleSelectedModel(selected, item) {
      this.selectedId = selected ? item.id : ''
      if (selected) {
        this.$emit('input', item)
        this.$emit('modelSelectedChange', item)
      } else {
        this.$emit('input', null)
        this.$emit('modelSelectedChange', null)
      }
    },
    queryHandle() {
      this.searchQuery = { ...this.searchForm }
      this.pageData.page_offset = 1
      this.getModelData()
    },
    async getModelData() {
      const { page_offset, page_size } = this.pageData
      const { jobType, queriedTypes } = this
      const { id = '', name = '' } = this.searchQuery
      const searchParams = handleParamsValid({ id, name })
      const params = { pageNum: page_offset, pageSize: page_size }
      this.queryFlag = true
      const condition = {
        id: '',
        name: '',
        owner: '',
        ...searchParams
      }
      if (jobType) {
        condition.type = jobModelSettingMap[jobType]
      }
      if (queriedTypes && queriedTypes.length) {
        condition.queriedTypes = queriedTypes
      }
      const res = await settingManageServer.querySettings({
        onlyMeta: false,
        ...params,
        condition
      })
      this.queryFlag = false
      console.log(res)
      if (res.code === 0 && res.data) {
        const { dataList, total } = res.data
        this.modelTableData = dataList.map((v) => {
          const setting = JSON.parse(v.setting)
          const { label_provider, label_column, participant_agency_list } = setting
          const participant_agency_list_filtered = participant_agency_list.map((v) => {
            return {
              ...v,
              isLabelProvider: v.agency === label_provider
            }
          })
          return {
            ...v,
            label_column,
            participant_agency_list: participant_agency_list_filtered
          }
        })
        this.total = total
      } else {
        this.modelTableData = []
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
