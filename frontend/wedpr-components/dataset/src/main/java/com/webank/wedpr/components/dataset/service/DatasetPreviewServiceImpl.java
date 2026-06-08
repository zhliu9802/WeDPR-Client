package com.webank.wedpr.components.dataset.service;

import com.webank.wedpr.components.dataset.common.DatasetStatus;
import com.webank.wedpr.components.dataset.config.DatasetConfig;
import com.webank.wedpr.components.dataset.datasource.storage.DatasetStoragePathRetriever;
import com.webank.wedpr.components.dataset.message.PreviewDatasetDataResponse;
import com.webank.wedpr.components.dataset.utils.CsvUtils;
import com.webank.wedpr.components.db.mapper.dataset.dao.Dataset;
import com.webank.wedpr.components.db.mapper.dataset.dao.UserInfo;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;
import com.webank.wedpr.components.db.mapper.dataset.mapper.DatasetMapper;
import com.webank.wedpr.components.storage.api.FileStorageInterface;
import com.webank.wedpr.components.storage.api.StoragePath;
import java.io.File;
import java.nio.file.Files;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("datasetPreviewService")
public class DatasetPreviewServiceImpl implements DatasetPreviewServiceApi {

    private static final Logger logger = LoggerFactory.getLogger(DatasetPreviewServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired private DatasetConfig datasetConfig;
    @Autowired private DatasetMapper datasetMapper;
    @Autowired private DatasetStoragePathRetriever datasetStoragePathRetriever;

    @Qualifier("fileStorage")
    @Autowired
    private FileStorageInterface fileStorage;

    @Override
    public PreviewDatasetDataResponse previewDatasetData(
            UserInfo userInfo, String datasetId, Integer pageNum, Integer pageSize)
            throws DatasetException {

        long startTimeMillis = System.currentTimeMillis();

        Dataset dataset = datasetMapper.getDatasetByDatasetId(datasetId, false);
        if (dataset == null) {
            throw new DatasetException("数据集不存在, datasetId: " + datasetId);
        }

        verifyOwner(userInfo, dataset);

        if (dataset.getStatus() == null
                || dataset.getStatus() != DatasetStatus.Success.getCode().intValue()) {
            throw new DatasetException("数据集尚未就绪，无法预览原始数据");
        }

        int resolvedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int resolvedPageSize =
                pageSize == null || pageSize < 1
                        ? DEFAULT_PAGE_SIZE
                        : Math.min(pageSize, MAX_PAGE_SIZE);

        StoragePath storagePath = datasetStoragePathRetriever.getDatasetStoragePath(dataset);

        if (!fileStorage.exists(storagePath)) {
            logger.error(
                    "dataset raw file not found, datasetId: {}, storagePath: {}",
                    datasetId,
                    storagePath);
            throw new DatasetException(
                    "原始数据文件不存在，本地存储中找不到该数据集文件。"
                            + "若近期执行过服务重建或清理部署目录，请重新上传数据集后再预览。");
        }

        String previewDir = datasetConfig.getDatasetDir(datasetId) + File.separator + "preview";
        String localCsvPath = previewDir + File.separator + "raw.csv";
        File previewDirFile = new File(previewDir);
        File localCsvFile = new File(localCsvPath);

        try {
            if (!previewDirFile.exists() && !previewDirFile.mkdirs()) {
                throw new DatasetException("创建预览临时目录失败");
            }
            try {
                fileStorage.download(storagePath, localCsvPath);
            } catch (RuntimeException e) {
                logger.error(
                        "download dataset raw file failed, datasetId: {}, storagePath: {}, e: ",
                        datasetId,
                        storagePath,
                        e);
                throw new DatasetException(
                        "读取原始数据文件失败，请确认数据集文件仍存在或重新上传后再试。");
            }

            CsvUtils.CsvPageResult pageResult =
                    CsvUtils.readCsvPage(
                            localCsvPath, resolvedPageNum, resolvedPageSize, MAX_PAGE_SIZE);

            long totalCount =
                    dataset.getDatasetRecordCount() != null
                            ? dataset.getDatasetRecordCount()
                            : 0L;

            long endTimeMillis = System.currentTimeMillis();
            logger.info(
                    "preview dataset data success, datasetId: {}, pageNum: {}, pageSize: {}, cost(ms): {}",
                    datasetId,
                    resolvedPageNum,
                    resolvedPageSize,
                    endTimeMillis - startTimeMillis);

            return PreviewDatasetDataResponse.builder()
                    .datasetId(datasetId)
                    .datasetTitle(dataset.getDatasetTitle())
                    .columns(pageResult.getColumns())
                    .rows(pageResult.getRows())
                    .totalCount(totalCount)
                    .pageNum(resolvedPageNum)
                    .pageSize(resolvedPageSize)
                    .build();
        } finally {
            try {
                Files.deleteIfExists(localCsvFile.toPath());
            } catch (Exception e) {
                logger.warn("delete preview temp file failed, path: {}, e: ", localCsvPath, e);
            }
        }
    }

    private void verifyOwner(UserInfo userInfo, Dataset dataset) throws DatasetException {
        if (!StringUtils.equals(userInfo.getUser(), dataset.getOwnerUserName())
                || !StringUtils.equals(userInfo.getAgency(), dataset.getOwnerAgencyName())) {
            throw new DatasetException("仅数据所有者可查看原始数据");
        }
    }
}
