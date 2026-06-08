<template>
  <div class="data-preview">
    <div class="title-radius">原始数据预览</div>
    <div class="meta-row" v-if="datasetTitle">
      <span class="label">资源名称：</span>
      <span class="value">{{ datasetTitle }}</span>
      <span class="label">数据集ID：</span>
      <span class="value">{{ datasetId }}</span>
      <span class="label">总记录数：</span>
      <span class="value">{{ totalCount }}</span>
    </div>
    <div class="tableContent autoTableWrap">
      <el-table
        :max-height="tableHeight"
        size="small"
        v-loading="loadingFlag"
        :data="tableRows"
        :border="true"
        class="table-wrap"
        empty-text="暂无数据"
      >
        <el-table-column
          v-for="col in columns"
          :key="col"
          :label="col"
          :prop="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <we-pagination
        :total="totalCount"
        :page_offset="pageData.page_offset"
        :page_size="pageData.page_size"
        :pageSizesOption="[10, 20, 50, 100]"
        @paginationChange="paginationHandle"
      />
    </div>
    <div class="footer-actions">
      <el-button size="medium" @click="goBack">返回</el-button>
    </div>
  </div>
</template>

<script>
import { dataManageServer } from 'Api'
import { tableHeightHandle } from 'Mixin/tableHeightHandle.js'

export default {
  name: 'dataPreview',
  mixins: [tableHeightHandle],
  data() {
    return {
      datasetId: '',
      datasetTitle: '',
      columns: [],
      tableRows: [],
      totalCount: 0,
      loadingFlag: false,
      pageData: {
        page_offset: 1,
        page_size: 20
      }
    }
  },
  created() {
    const { datasetId } = this.$route.query
    this.datasetId = datasetId
    if (datasetId) {
      this.loadPreview()
    } else {
      this.$message.error('缺少数据集 ID')
    }
  },
  methods: {
    goBack() {
      this.$router.back()
    },
    paginationHandle(pageData) {
      this.pageData = { ...pageData }
      this.loadPreview()
    },
    async loadPreview() {
      if (!this.datasetId) {
        return
      }
      this.loadingFlag = true
      const { page_offset, page_size } = this.pageData
      const res = await dataManageServer.previewDatasetData({
        datasetId: this.datasetId,
        pageNum: page_offset,
        pageSize: page_size
      })
      this.loadingFlag = false
      if (res.code === 0 && res.data) {
        const { columns = [], rows = [], totalCount = 0, datasetTitle = '' } = res.data
        this.columns = columns
        this.datasetTitle = datasetTitle
        this.totalCount = totalCount
        this.tableRows = rows.map((row) => {
          const record = {}
          columns.forEach((col, index) => {
            record[col] = row[index] != null ? row[index] : ''
          })
          return record
        })
      } else {
        this.columns = []
        this.tableRows = []
        this.totalCount = 0
        this.$message.error(res.msg || '加载原始数据失败')
      }
    }
  }
}
</script>

<style lang="less" scoped>
.data-preview {
  .meta-row {
    margin-bottom: 16px;
    font-size: 14px;
    color: #525660;
    .label {
      margin-right: 4px;
    }
    .value {
      color: #262a32;
      margin-right: 24px;
    }
  }
  .footer-actions {
    margin-top: 24px;
  }
}
</style>
