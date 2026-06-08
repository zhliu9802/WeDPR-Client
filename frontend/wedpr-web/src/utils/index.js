import { Message } from 'element-ui'

function numMulti(num1, num2) {
  let baseNum = 0
  try {
    baseNum += num1.toString().split('.')[1].length
  } catch (e) {}
  try {
    baseNum += num2.toString().split('.')[1].length
  } catch (e) {}
  return (Number(num1.toString().replace('.', '')) * Number(num2.toString().replace('.', ''))) / Math.pow(10, baseNum)
}
export { numMulti }

export function toDynamicTableData(input) {
  if (typeof input === 'undefined' || input === null) {
    return null
  }

  const dynamicTableData = {
    columns: [],
    columnsOrigin: [],
    data: []
  }

  dynamicTableData.data = Array(input.data.length)

  for (let i = 0; i < input.columns.length; i++) {
    dynamicTableData.columns.push({
      dataItem: 'col' + i,
      dataName: input.columns[i]
    })

    dynamicTableData.columnsOrigin.push({
      dataItem: 'col' + i,
      dataName: input.columns[i]
    })

    for (let j = 0; j < input.data.length; j++) {
      if (!dynamicTableData.data[j]) {
        dynamicTableData.data[j] = {}
      }

      dynamicTableData.data[j]['col' + i] = input.data[j][i]
    }
  }

  return dynamicTableData
}

export function handleParamsValid(params) {
  const validParams = {}
  Object.keys(params).forEach((key) => {
    if (!(params[key] === undefined || params[key] === null || params[key] === '')) {
      validParams[key] = params[key]
    }
  })
  return validParams
}

export function maskString(str) {
  if (str.length <= 8) return str.replace(/./g, '*')
  const start = str.slice(0, 4)
  const end = str.slice(-4)
  const masked = str.slice(4, -4)
  return start + masked.replace(/./g, '*') + end
}

export function copy(val, message) {
  // 模拟 输入框
  const cInput = document.createElement('input')
  cInput.value = val
  document.body.appendChild(cInput)
  cInput.select() // 选取文本框内容
  // 执行浏览器复制命令
  // 复制命令会将当前选中的内容复制到剪切板中（这里就是创建的input标签）
  // Input要在正常的编辑状态下原生复制方法才会生效
  document.execCommand('copy')
  Message.success(message)
  // 复制成功后再将构造的标签 移除
  document.body.removeChild(cInput)
}

export function checkLabelProvider(str) {
  if (!str || str === 'False') {
    return false
  } else {
    return true
  }
}
