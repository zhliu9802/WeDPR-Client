# Phase4：隐私计算任务详解（数据 I/O 规范）

> 本文档从 [`Phase3_数据上传全流程解析.md`](Phase3_数据上传全流程解析.md) 抽取，专门说明**数据集入库后如何被隐私计算任务消费**：统一数据模型（`FileMeta` / `DatasetInfo`）、PSI / MPC / ML / PIR 四类任务的输入输出规范、各数据源格式到任务输入的对照、任务缓存与文件命名约定。
> 前置阅读：[`Phase3_数据上传全流程解析.md`](Phase3_数据上传全流程解析.md)（数据上传、落盘、链上同步）、[`phase2_site_runtime.md`](phase2_site_runtime.md)（站点端运行机制）、[`phase4_privacy_compute_task_flow_and_io.md`](phase4_privacy_compute_task_flow_and_io.md)（任务调度全流程）。

---

## 1. 隐私计算任务的统一数据模型

### 1.1 关键结论：任务只消费「已落盘的 CSV」

无论用户上传时是 CSV、Excel、DB 还是 Hive，**入库完成后**存储层文件均为 **CSV 格式**（HDFS 引用型除外，但内容也必须是 CSV 语义）。

任务参数中通过 **`FileMeta`** 引用数据集：

```java
public class FileMeta {
    private String datasetID;      // 推荐：只填 datasetID，运行时解析
    private String storageTypeStr; // LOCAL / HDFS
    private String path;           // 绝对或相对存储路径
    private String owner;
    private String ownerAgency;
}
```
运行时解析（`FileMeta.obtainDatasetInfo()`）：

```java
this.dataset = datasetMapper.getDatasetByDatasetId(this.datasetID, false);
setStorageTypeStr(this.dataset.getDatasetStorageType());
setPath(this.dataset.getStoragePathMeta().getFilePath());
setOwner(this.dataset.getOwnerUserName());
setOwnerAgency(this.dataset.getOwnerAgencyName());
```
读取文件（各 Executor Hook 统一模式）：

```java
storage.download(partyInfo.getDataset().getStoragePath(), localCachePath);
```

### 1.2 任务参数公共结构：`DatasetInfo`

```java
public class DatasetInfo {
    protected FileMeta dataset;           // 输入数据集
    protected FileMeta output;            // 输出（部分任务）
    protected Boolean labelProvider;      // ML：是否标签方
    protected String labelField;          // ML：标签列，默认 "y"
    protected Boolean receiveResult;      // 是否接收 PSI 结果
    protected List<String> idFields;      // PSI/ML：关联键列，默认 ["id"]
}
```
**任务创建时推荐写法**：`dataset.datasetID = "d-xxx"`，其余字段由服务端 `obtainDatasetInfo()` 填充。

### 1.3 动态数据源在任务时的刷新

若创建 DB/Hive 数据集时 `dynamicDataSource=true`：

- 创建时不生成 storagePath
- 任务执行前 `DatasetStoragePathRetriever.getDatasetStoragePath()` 会 **重新执行** `processData()`（JDBC 查库 → 临时 CSV → upload → 返回新 StoragePath）

适用场景：每次任务使用数据库最新快照。

---

## 2. 各任务类型的输入/输出规范

### 2.1 总览表

| 任务类型 | JobParam 类 | 输入 | 中间产物 | 输出 | 数据格式要求 |
|---------|------------|------|---------|------|-------------|
| PSI | `PSIJobParam` | 各方 `FileMeta`(datasetID) + `idFields` | `psi_prepare.csv` | `psi_result.csv` | CSV，含 idFields 列 |
| MPC | `MPCJobParam` | `dataSetList` + mpcContent/sql | `mpc_prepare.csv`、`.mpc` | `mpc_result.csv`、`mpc_output.txt` | CSV；可选先跑 PSI |
| ML 训练/预测 | `ModelJobParam` | `dataSetList` + modelSetting | 可选 PSI 流程 | 模型文件 / 预测结果 | CSV；需 labelProvider |
| PIR 服务构建 | `PirServiceSetting` | datasetId + idField | 本地缓存 CSV | MySQL 表 `pir_*` | CSV；发布时导入 DB |

### 2.2 PSI（Private Set Intersection）

**JobParam**：`PSIJobParam`

```json
{
  "jobID": "job-xxx",
  "taskID": "task-xxx",
  "user": "admin",
  "dataSetList": [
    {
      "dataset": { "datasetID": "d-aaa" },
      "idFields": ["id"],
      "receiveResult": true,
      "output": null
    },
    {
      "dataset": { "datasetID": "d-bbb" },
      "idFields": ["user_id"],
      "receiveResult": false
    }
  ]
}
```
**本机构 prepare 流程**（`PSIJobParam.prepare()`，源码 `psi/model/PSIJobParam.java:236`）：

> **前提**：`prepare()` 默认操作 `selfDatasetInfo`，`storage` 是**本站点自己注入**的 `FileStorageInterface`。即每方只在自己机器上准备自己的数据集，无跨机构文件读取。

```
① storage.download(dataset.getStoragePath() → ./.cache/jobs/{jobId}/原文件名)
     └ storagePath 由 FileMeta.obtainDatasetInfo() 查本站点 MySQL 得到 → 必指向本域存储
② CSVFileParser.extractFields(全量CSV, idFields → psi_prepare.csv)
     └ 只抽 idFields 列，其余业务列不进入后续 PSI 流程（数据最小化）
③ storage.upload(psi_prepare.csv → {owner}/PSI/{jobId}/psi_prepare.csv)
     └ 同一个本站点 storage，写回本域（LOCAL/HDFS 由 storage.type() 决定）
④ partyInfo.setDataset(updatedInput)
     └ 把下发给 C++ 引擎的输入从「原始全量CSV」替换为「仅含 idFields 的 psi_prepare.csv」
⑤ finally: 删除本地 downloadedFilePath 与 extractFilePath 两份临时副本
```

链路图：

```
本域存储(原始全量CSV)
   │ ① download         ← 连本站点自己的存储
   ▼
本地 ./.cache/jobs/{jobId}/原文件名(全量,仅本机)
   │ ② extractFields(idFields)
   ▼
本地 psi_prepare.csv(只剩 idFields)
   │ ③ upload           ← 写回本站点存储
   ▼
本域存储 {owner}/PSI/{jobId}/psi_prepare.csv  ──→ 下发本侧 C++ PSI 引擎
   │ ⑤ finally 删除本地两份临时文件
```

> **隐私结论**：原始全量数据始终留在本站点存储与本机；跨机构经网关传出的只有 PSI 协议密文。这是「数据可用不可见」在代码层的落地。

**输出路径**（本机构自动生成）：

```
WeDPRCommonConfig.getUserJobCachePath(user, "PSI", jobId, "psi_result.csv")
```
**下发 C++ 层的 Party 结构**（`PartyInfo.PartyData`）：

| 字段 | 含义 |
|------|------|
| `input` | prepare 后的 `psi_prepare.csv` 路径 |
| `output` | 本机构 `psi_result.csv` 路径（仅 SERVER 方） |

**约束**：

- 至少 **2 方**参与
- 本机构必须在 `dataSetList` 中
- `idFields` 不可为空

### 2.3 MPC（Secure Multi-Party Computation）

**JobParam**：`MPCJobParam`

```json
{
  "sql": "SELECT ...",
  "mpcContent": "...",
  "dataSetList": [
    { "dataset": { "datasetID": "d-aaa" }, "idFields": ["id"] },
    { "dataset": { "datasetID": "d-bbb" }, "idFields": ["id"] }
  ]
}
```
- `sql` 与 `mpcContent` 二选一；`sql` 会经 `MpcCodeTranslator` 转为 MPC 代码
- `needRunPsi`：由 mpcContent 中是否含 `PSI_OPTION = True` 决定

**prepare 流程**（`MPCExecutorHook`）：

**无 PSI**：

```
storage.download(本机构 dataset → 本地)
MpcUtils.makeDatasetToMpcDataDirect(CSV → mpc_prepare.csv)
storage.upload(mpc_prepare.csv)
```
**有 PSI**（`prepareWithPsi`）：

```
download 本机构原始 CSV
download psi_result.csv
MpcUtils.mergeAndSortById(CSV + psi_result → mpc_prepare.csv)
upload mpc_prepare.csv、.mpc 脚本
```
**输出路径**（`getMpcPath()`）：

| 文件 | 路径模式 |
|------|---------|
| mpc_prepare.csv | `{user}/MPC/{jobId}/mpc_prepare.csv` |
| {jobId}.mpc | `{user}/MPC/{jobId}/{jobId}.mpc` |
| mpc_result.csv | `{user}/MPC/{jobId}/mpc_result.csv` |
| mpc_output.txt | `{user}/MPC/{jobId}/mpc_output.txt` |

### 2.4 ML（联邦机器学习）

**JobParam**：`ModelJobParam`

```json
{
  "modelSetting": { "...": "算法超参", "usePsi": true, "useIv": false },
  "modelPredictAlgorithm": "...",
  "dataSetList": [
    {
      "dataset": { "datasetID": "d-aaa" },
      "idFields": ["id"],
      "labelProvider": true,
      "labelField": "y",
      "receiveResult": true
    },
    {
      "dataset": { "datasetID": "d-bbb" },
      "idFields": ["id"],
      "labelProvider": false
    }
  ]
}
```
**约束**：

- 必须指定 **labelProvider** 方（持有标签列）
- 本机构必须在 `dataSetList` 中
- `usePsi=true` 时走 `MLPSIExecutorHook`，先执行 PSI 再训练/预测

**输入解析**（`parseLabelProviderInfo`）：

```
selfDataset.getDataset().obtainDatasetInfo(datasetMapper)
modelRequest.setDatasetPath(selfDataset.getDataset().getPath())
modelRequest.setIsLabelProvider(本机构是否为 labelProvider)
```
**PSI 输出作为 ML 输入**（`parseIDFilePath`）：

```
modelRequest.setIdFilePath(PSI 默认输出路径 psi_result.csv)
```
**下游请求转换**：

| 阶段 | 输出类型 |
|------|---------|
| Preprocessing | `PreprocessingRequest`（WEDPR_TRAIN / WEDPR_PREDICT） |
| FeatureEngineering | `FeatureEngineeringRequest`（useIv=true 时） |
| MultiParty ML | `ModelJobRequest` |

### 2.5 PIR（Private Information Retrieval）

PIR **不通过 JobParam.dataSetList**，而在 **服务发布** 时构建索引。

**入口**：`PirDatasetConstructorImpl.construct(PirServiceSetting)`

```
1. datasetMapper.getDatasetByDatasetId(datasetId)
2. StoragePathBuilder.getInstance(storageType, storagePath)
3. fileStorage.download → PirServiceConfig.getPirCacheDir()/datasetId
4. 解析 datasetFields（CSV 表头）
5. CREATE TABLE pir_{datasetId} (业务列 + wedpr_pir_id + wedpr_pir_id_hash)
6. CSVFileParser.processCsvContent 逐行 INSERT
```
**输入规范**：

| 项 | 要求 |
|----|------|
| 数据源 | 已 Success 的 datasetId |
| 文件格式 | 存储层 CSV |
| idField | 必须在 datasetFields 中存在 |
| 禁止字段名 | `wedpr_pir_id`、`wedpr_pir_id_hash`（系统保留） |

**输出**：MySQL 表 `pir_{tableId}`，供 PIR 查询引擎使用；查询结果文件默认 `{pirCache}/{user}/{jobId}/pir_result`。

---

## 3. 各数据源格式 → 任务输入的对照

| 原始 dataSourceType | 入库后存储形态 | 任务侧看到的类型 | idFields/labelField 对应 |
|--------------------|--------------|----------------|------------------------|
| CSV | LOCAL/HDFS 上 CSV 文件 | `FileMeta` → CSV | CSV 表头列名 |
| EXCEL | 转 CSV 后存储 | 同 CSV | 同 CSV |
| DB | JDBC 导出 CSV 后存储 | 同 CSV | SQL 结果列名 |
| HIVE | Hive SQL 导出 CSV 后存储 | 同 CSV | SQL 结果列名 |
| HDFS | HDFS 上 CSV（不复制） | `FileMeta` path 指向 HDFS | CSV 表头列名 |
| DB/HIVE 动态 | 任务时临时导出 CSV | 每次任务重新 processData | 同 CSV |

**重要**：任务创建 UI / API **不需要**也不应该传递 `dataSourceType`；只需传 `datasetID` 和列名配置（`idFields`、`labelField`）。

> 入库侧各格式如何归一化为 CSV，见 [`Phase3_数据上传全流程解析.md`](Phase3_数据上传全流程解析.md) §4。

---

## 4. 任务缓存与文件命名约定

配置项（`ExecutorConfig`，可通过 `application-wedpr.properties` 覆盖）：

| 配置键 | 默认值 | 用途 |
|--------|--------|------|
| `wedpr.executor.job.cache.dir` | `./.cache/jobs` | 任务本地缓存根目录 |
| `wedpr.executor.psi.tmp.file.name` | `psi_prepare.csv` | PSI 字段提取临时名 |
| `wedpr.executor.psi.result.file.name` | `psi_result.csv` | PSI 结果 |
| `wedpr.executor.mpc.prepare.file.name` | `mpc_prepare.csv` | MPC 输入 |
| `wedpr.executor.mpc.result.file.name` | `mpc_result.csv` | MPC 结果 |
| `wedpr.executor.mpc.output.file.name` | `mpc_output.txt` | MPC 文本输出 |

用户级任务路径（`WeDPRCommonConfig.getUserJobCachePath`）：

```
{storageBase}/{user}/{JobType}/{jobId}/{fileName}
```

---

## 5. 任务引用数据集的前置校验

任务读取数据集前会校验数据集状态（`DatasetStoragePathRetriever`）：

```java
if (status != DatasetStatus.Success.getCode()) {
    throw new DatasetException("dataset is not available status");
}
```

即只有 `status=Success(0)` 的数据集才能被任务引用；未 Success（Created / 处理中 / Failure / Fatal）一律拒绝。完整状态机见 [`Phase3_数据上传全流程解析.md`](Phase3_数据上传全流程解析.md) §10。

---

## 6. 关键源码索引

| 主题 | 路径 |
|------|------|
| 任务读数据集 | `dataset/datasource/storage/DatasetStoragePathRetriever.java` |
| FileMeta | `scheduler/executor/impl/model/FileMeta.java` |
| PSI 参数与 prepare | `scheduler/executor/impl/psi/model/PSIJobParam.java` |
| MPC 参数 | `scheduler/executor/impl/mpc/MPCJobParam.java` |
| ML 参数 | `scheduler/executor/impl/ml/model/ModelJobParam.java` |
| PIR 构建 | `task-plugin/pir/.../PirDatasetConstructorImpl.java` |
| CSV 字段提取/解析 | `wedpr-common/utils/CSVFileParser.java` |

---

## 7. 相关文档

- [Phase3：数据上传全流程解析](Phase3_数据上传全流程解析.md) — 数据集入库、落盘、链上同步（本文档的前置）
- [Phase4：隐私计算任务调度全流程](phase4_privacy_compute_task_flow_and_io.md) — 任务创建、调度、跨机构执行
- [站点端运行机制](phase2_site_runtime.md) — 启动、调度、API 分层
- [WeDPR 系统架构说明](WeDPR系统架构说明.md) — 整体架构
