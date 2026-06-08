package com.webank.wedpr.components.dataset.datasource.processor;

import com.webank.wedpr.common.utils.ObjectMapperFactory;
import com.webank.wedpr.components.dataset.datasource.DifferentialPrivacyMeta;
import com.webank.wedpr.components.dataset.utils.CsvUtils;
import com.webank.wedpr.components.dataset.utils.DifferentialPrivacyUtils;
import com.webank.wedpr.components.db.mapper.dataset.dao.Dataset;
import com.webank.wedpr.components.db.mapper.dataset.dao.UserInfo;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;
import com.webank.wedpr.components.storage.api.FileStorageInterface;
import com.webank.wedpr.components.storage.api.StoragePath;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在 analyze 与 upload 之间应用差分隐私加噪 */
public class DifferentialPrivacyProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DifferentialPrivacyProcessor.class);

    private DifferentialPrivacyProcessor() {}

    public static void applyIfEnabled(DataSourceProcessorContext context) throws DatasetException {
        Dataset dataset = context.getDataset();
        if (dataset == null) {
            return;
        }
        DifferentialPrivacyMeta meta =
                DifferentialPrivacyMeta.deserialize(dataset.getDifferentialPrivacyMeta());
        if (meta == null || !meta.isEnabled()) {
            return;
        }

        String csvPath = context.getCvsFilePath();
        if (csvPath == null || csvPath.trim().isEmpty()) {
            throw new DatasetException("差分隐私处理失败：CSV 文件路径为空");
        }

        List<String> headerFields = CsvUtils.readCsvHeader(csvPath);
        meta.validate();
        for (String column : meta.getColumns()) {
            if (!headerFields.contains(column)) {
                throw new DatasetException("差分隐私列 '" + column + "' 不在数据集表头中");
            }
        }

        long start = System.currentTimeMillis();
        DifferentialPrivacyUtils.applyToCsvFile(csvPath, meta, headerFields);

        String hashAlgorithm = context.getDatasetConfig().getDatasetHash();
        DifferentialPrivacyUtils.refreshDatasetFileMeta(dataset, csvPath, hashAlgorithm);
        context.setDifferentialPrivacyApplied(true);

        logger.info(
                "数据集 {} 差分隐私加噪完成, cost(ms): {}",
                dataset.getDatasetId(),
                System.currentTimeMillis() - start);
    }

    /**
     * HDFS 引用型数据源默认不上传；启用差分隐私后需将加噪文件落盘到用户存储目录。
     */
    public static void uploadNoisedFileIfNeeded(DataSourceProcessorContext context)
            throws DatasetException {
        if (!context.isDifferentialPrivacyApplied()) {
            return;
        }
        Dataset dataset = context.getDataset();
        UserInfo userInfo = context.getUserInfo();
        String csvPath = context.getCvsFilePath();
        FileStorageInterface fileStorage = context.getFileStorage();

        try {
            String userDatasetPath =
                    context.getDatasetConfig()
                            .getDatasetStoragePath(userInfo.getUser(), dataset.getDatasetId(), false);
            StoragePath storagePath =
                    fileStorage.upload(
                            context.getFilePermissionInfo(),
                            true,
                            csvPath,
                            userDatasetPath,
                            false);
            String storagePathStr =
                    ObjectMapperFactory.getObjectMapper().writeValueAsString(storagePath);
            dataset.setDatasetStorageType(fileStorage.type().toString());
            dataset.setDatasetStoragePath(storagePathStr);
            context.setStoragePath(storagePath);
            logger.info(
                    "差分隐私加噪文件已落盘, datasetId: {}, path: {}",
                    dataset.getDatasetId(),
                    storagePathStr);
        } catch (Exception e) {
            throw new DatasetException("差分隐私加噪文件落盘失败: " + e.getMessage());
        }
    }
}
