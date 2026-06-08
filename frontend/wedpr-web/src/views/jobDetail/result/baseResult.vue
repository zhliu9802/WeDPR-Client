<template>
  <div class="info-container" v-loading="loading">
    <ul>
      <li>
        <span class="label">任务耗时：</span> <span class="info"> {{ jobStatusInfo.timeCostMs / 1000 }}s </span>
      </li>
      <li>
        <span class="label">结果文件：</span>
        <el-button type="text" v-if="jobType === jobEnum.PSI" @click="getLink('psi_result.csv')">{{ 'psi_result.csv' }}</el-button>
        <el-button type="text" v-if="jobType === jobEnum.PIR" @click="getLink('pir_result.csv')">{{ 'pir_result.csv' }}</el-button>
        <el-button type="text" v-if="jobType === jobEnum.MPC" @click="getLink('result.csv')">{{ 'result.csv' }}</el-button>
        <el-button type="text" v-if="jobType === jobEnum.SQL" @click="getLink('result.csv')">{{ 'result.csv' }}</el-button>
      </li>
    </ul>
  </div>
</template>

<script>
import { downloadLargeFile } from 'Mixin/downloadLargeFile.js'
import { jobEnum } from 'Utils/constant.js'
export default {
  name: 'AiResultNew',
  mixins: [downloadLargeFile],
  props: {
    jobID: {
      type: String,
      default: () => {
        return ''
      }
    },
    jobType: {
      type: String,
      default: () => {
        return ''
      }
    },
    resultFileInfo: {
      type: Object,
      default: () => {
        return {}
      }
    },
    jobStatusInfo: {
      type: Object,
      default: () => {
        return {}
      }
    }
  },
  data() {
    return {
      jobResult: {},
      activeName: '任务结果',
      jobEnum
    }
  },
  methods: {
    getLink(fileName) {
      const { path } = this.resultFileInfo
      path && this.downloadLargeFile({ filePath: path }, fileName)
    }
  }
}
</script>

<style lang="scss" scoped>
ul {
  padding-bottom: 10px;
}
li {
  padding: 4px;
}
span.label {
  margin-right: 6px;
  font-size: 14px;
  line-height: 22px;
}
</style>
