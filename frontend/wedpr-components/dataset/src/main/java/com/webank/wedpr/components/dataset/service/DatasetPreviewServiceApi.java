package com.webank.wedpr.components.dataset.service;

import com.webank.wedpr.components.dataset.message.PreviewDatasetDataResponse;
import com.webank.wedpr.components.db.mapper.dataset.dao.UserInfo;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;

public interface DatasetPreviewServiceApi {

    /**
     * 分页预览数据集原始 CSV 内容，仅数据所有者可调用。
     *
     * @param userInfo 当前登录用户
     * @param datasetId 数据集 ID
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页行数
     */
    PreviewDatasetDataResponse previewDatasetData(
            UserInfo userInfo, String datasetId, Integer pageNum, Integer pageSize)
            throws DatasetException;
}
