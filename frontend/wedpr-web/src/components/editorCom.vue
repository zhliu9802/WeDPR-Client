<template>
  <div class="editor-container">
    <div v-if="false">
      设置主题:
      <el-select size="small" @change="handleChangeTheme" v-model="theme" placeholder="设置主题">
        <el-option label="Visual Studio Dark" value="vs-dark"></el-option>
        <el-option label="Visual Studio" value="vs"></el-option>
        <el-option label="High Contrast Dark" value="hc-black"></el-option>
      </el-select>
      选择语言:
      <el-select size="small" v-model="language" @change="handleChangeLanguage" placeholder="选择语言">
        <el-option v-for="(languageItem, key) in languageOptions" :key="key" :label="languageItem" :value="languageItem"> </el-option>
      </el-select>
    </div>
    <div ref="container" class="monaco-editor"></div>
  </div>
</template>

<script>
import * as monaco from 'monaco-editor'
import { language as pythonLanguage } from 'monaco-editor/esm/vs/basic-languages/python/python.js'
import { language as sqlLanguage } from 'monaco-editor/esm/vs/basic-languages/sql/sql.js'
import { format } from 'sql-formatter'
export default {
  name: 'editorCom',
  props: {
    value: {
      type: String,
      default: () => ''
    },
    title: {
      type: String
    },
    lang: {
      type: String
    },
    readOnly: {
      type: Boolean,
      default: false
    }
  },
  model: {
    prop: 'value'
  },
  data() {
    return {
      languageOptions: ['python', 'sql'],
      // theme: 'vs-dark',
      theme: 'vs',
      language: 'sql',
      // 主要配置
      defaultOpts: {
        // // 编辑器的值
        value: '',
        // // 右侧不显示编辑器预览框
        // roundedSelection: true,
        // // 语言类型java,c,php更多选择详见官网
        language: 'sql',
        // toolbar: ['undo', 'redo', '|', 'fontzoom', '|']
        acceptSuggestionOnCommitCharacter: true, // 接受关于提交字符的建议
        acceptSuggestionOnEnter: 'on', // 接受输入建议 "on" | "off" | "smart"
        accessibilityPageSize: 10, // 辅助功能页面大小 Number 说明：控制编辑器中可由屏幕阅读器读出的行数。警告：这对大于默认值的数字具有性能含义。
        accessibilitySupport: 'on', // 辅助功能支持 控制编辑器是否应在为屏幕阅读器优化的模式下运行。
        autoClosingBrackets: 'always', // 是否自动添加结束括号(包括中括号) "always" | "languageDefined" | "beforeWhitespace" | "never"
        autoClosingDelete: 'always', // 是否自动删除结束括号(包括中括号) "always" | "never" | "auto"
        autoClosingOvertype: 'always', // 是否关闭改写 即使用insert模式时是覆盖后面的文字还是不覆盖后面的文字 "always" | "never" | "auto"
        autoClosingQuotes: 'always', // 是否自动添加结束的单引号 双引号 "always" | "languageDefined" | "beforeWhitespace" | "never"
        autoIndent: true, // 控制编辑器在用户键入、粘贴、移动或缩进行时是否应自动调整缩进
        automaticLayout: true, // 自动布局
        codeLens: false, // 是否显示codeLens 通过 CodeLens，你可以在专注于工作的同时了解代码所发生的情况 – 而无需离开编辑器。 可以查找代码引用、代码更改、关联的 Bug、工作项、代码评审和单元测试。
        codeLensFontFamily: '', // codeLens的字体样式
        codeLensFontSize: 14, // codeLens的字体大小
        colorDecorators: false, // 呈现内联色彩装饰器和颜色选择器
        comments: {
          ignoreEmptyLines: true, // 插入行注释时忽略空行。默认为真。
          insertSpace: true // 在行注释标记之后和块注释标记内插入一个空格。默认为真。
        }, // 注释配置
        contextmenu: true, // 启用上下文菜单
        columnSelection: false, // 启用列编辑 按下shift键位然后按↑↓键位可以实现列选择 然后实现列编辑
        autoSurround: 'never', // 是否应自动环绕选择
        copyWithSyntaxHighlighting: true, // 是否应将语法突出显示复制到剪贴板中 即 当你复制到word中是否保持文字高亮颜色
        cursorBlinking: 'Solid', // 光标动画样式
        cursorSmoothCaretAnimation: true, // 是否启用光标平滑插入动画  当你在快速输入文字的时候 光标是直接平滑的移动还是直接"闪现"到当前文字所处位置
        cursorStyle: 'UnderlineThin', // "Block"|"BlockOutline"|"Line"|"LineThin"|"Underline"|"UnderlineThin" 光标样式
        cursorSurroundingLines: 0, // 光标环绕行数 当文字输入超过屏幕时 可以看见右侧滚动条中光标所处位置是在滚动条中间还是顶部还是底部 即光标环绕行数 环绕行数越大 光标在滚动条中位置越居中
        cursorSurroundingLinesStyle: 'all', // "default" | "all" 光标环绕样式
        cursorWidth: 2, // <=25 光标宽度
        minimap: {
          enabled: false // 是否启用预览图
        }, // 预览图设置
        folding: true, // 是否启用代码折叠
        links: true, // 是否点击链接
        overviewRulerBorder: false, // 是否应围绕概览标尺绘制边框
        renderLineHighlight: 'gutter', // 当前行突出显示方式
        roundedSelection: false, // 选区是否有圆角
        scrollBeyondLastLine: false, // 设置编辑器是否可以滚动到最后一行之后
        readOnly: false, // 是否为只读模式
        theme: 'GreyTheme' // vs, hc-black, or vs-dark
      },
      editor: null
    }
  },
  mounted() {
    this.defaultOpts.language = this.lang
    this.init()
  },
  watch: {
    lang(nv) {
      nv && this.init()
    }
  },
  methods: {
    init() {
      console.log(this.value, 'this.value')
      monaco.editor.defineTheme('GreyTheme', {
        base: 'vs',
        inherit: true,
        rules: [{ background: '#F5F7FB' }],
        colors: {
          // 相关颜色属性配置
          'editor.background': '#F5F7FB' // 背景色
        }
      })
      monaco.editor.setTheme('GreyTheme')
      // 初始化container的内容，销毁之前生成的编辑器
      this.$refs.container.innerHTML = ''
      // 生成编辑器配置
      const editorOptions = Object.assign(this.defaultOpts, { readOnly: this.readOnly })
      // 生成编辑器对象
      this.editor = monaco.editor.create(this.$refs.container, editorOptions)
      // 编辑器内容发生改变时触发
      this.editor.onDidChangeModelContent(() => {
        // this.$emit('change', this.editor.getValue())
        this.$emit('input', this.editor.getValue())
      })
      if (this.lang === 'sql') {
        this.addSqlFormatter()
        this.addSqlTips()
      } else {
        this.addPythonTips()
      }
      this.value && this.editor.setValue(this.value)
      // this.addFormatter()
    },
    addPythonTips() {
      monaco.languages.registerCompletionItemProvider('python', {
        provideCompletionItems: function () {
          const suggestions = []
          // 这个keywords就是python.js文件中有的
          pythonLanguage.keywords.forEach((item) => {
            suggestions.push({
              label: item,
              kind: monaco.languages.CompletionItemKind.Keyword,
              insertText: item
            })
          })
          return {
            // 最后要返回一个数组
            suggestions: suggestions
          }
        }
      })
    },
    addSqlTips() {
      monaco.languages.registerCompletionItemProvider('sql', {
        provideCompletionItems: function () {
          const suggestions = []
          sqlLanguage.keywords.forEach((item) => {
            suggestions.push({
              label: item,
              kind: monaco.languages.CompletionItemKind.Keyword,
              insertText: item
            })
          })
          // sqlLanguage.operators.forEach((item) => {
          //   suggestions.push({
          //     label: item,
          //     kind: monaco.languages.CompletionItemKind.Operator,
          //     insertText: item
          //   })
          // })
          // sqlLanguage.builtinFunctions.forEach((item) => {
          //   suggestions.push({
          //     label: item,
          //     kind: monaco.languages.CompletionItemKind.Function,
          //     insertText: item
          //   })
          // })
          // sqlLanguage.builtinVariables.forEach((item) => {
          //   suggestions.push({
          //     label: item,
          //     kind: monaco.languages.CompletionItemKind.Variable,
          //     insertText: item
          //   })
          // })
          return {
            suggestions: suggestions
          }
        }
      })
    },
    addSqlFormatter() {
      // 监听右键事件
      this.editor.addAction({
        id: 'format.sql',
        label: 'Formart SQL',
        precondition: null,
        contextMenuGroupId: 'navigation',
        contextMenuOrder: 1,
        run: () => {
          try {
            this.editor.setValue(format(this.editor.getValue()))
          } catch (e) {
            console.log('error')
          }
        }
      })
    },
    // 手动编辑器中的内容
    getValue() {
      this.$message.info(this.$refs.monaco.getVal())
    }
  }
}
</script>

<style scoped lang="less">
div.editor-container {
  height: 100%;
  width: 100%;
  border: 1px solid #e0e4ed;
  padding: 10px;
  border-radius: 10px;
  background-color: #f5f7fb;
  div.monaco-editor {
    width: 100%;
    height: calc(100% - 30px);
    margin-top: 10px;
  }
}
</style>
