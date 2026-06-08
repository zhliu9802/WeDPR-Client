<template>
  <div class="info-container" v-loading="loading">
    <div class="log-container">
      日志下载： <el-button type="text" @click="downloadLog(logPath)">{{ 'job.log' }}</el-button>
    </div>
    <div class="log-container" v-if="logContent">
      <div class="log" v-html="logContent"></div>
    </div>
  </div>
</template>

<script>
import { downloadLargeFile } from 'Mixin/downloadLargeFile.js'
import { jobManageServer } from 'Api'
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
    }
  },
  data() {
    return {
      logSize: 0,
      logContent: '',
      logPath: '',
      loading: false
    }
  },
  watch: {
    jobID() {
      this.getJobLog()
    }
  },
  created() {
    this.getJobLog()
  },
  methods: {
    handleResult() {},
    async getJobLog() {
      const { jobID } = this
      this.loading = true
      const res = await jobManageServer.queryJobDetail({ jobID, fetchLog: true, fetchJobDetail: false })
      if (res.code === 0 && res.data) {
        const { log } = res.data
        if (log) {
          const { logContent, logPath, logSize } = log
          this.logSize = logSize
          // 高亮运行日志
          if (logContent) {
            this.logContent = this.highLightLog(logContent)
          } else {
            this.logPath = logPath
          }
        }
      }
      this.loading = false
    },
    highLightLog(logContent) {
      const content = logContent.replace(/\n/g, '<br>')
      const dataArray = content.split('<br>')
      const dataDrawedList = dataArray.map((v) => {
        if (v.toLowerCase().indexOf('error') > -1) {
          return `<span class='error'>${v}</span>`
        }
        if (v.toLowerCase().indexOf('warn') > -1) {
          return `<span class='warn'>${v}</span>`
        }
        return v
      })
      return dataDrawedList.join('<br>')
    },
    downloadLog(path) {
      this.downloadLargeFile({ filePath: path }, 'job.log')
    }
  }
}
</script>

<style lang="scss" scoped>
ul {
  padding: 10px 0;
}
li {
  padding: 8px;
}
span.label {
  margin-right: 6px;
  font-size: 14px;
  line-height: 22px;
}
</style>
