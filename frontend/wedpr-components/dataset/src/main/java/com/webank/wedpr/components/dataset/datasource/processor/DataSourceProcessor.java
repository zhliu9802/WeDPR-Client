package com.webank.wedpr.components.dataset.datasource.processor;

import com.webank.wedpr.components.dataset.config.DatasetConfig;
import com.webank.wedpr.components.dataset.datasource.DataSourceMeta;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DataSourceProcessor {

    Logger logger = LoggerFactory.getLogger(DataSourceProcessor.class);

    // parse datasource meta
    DataSourceMeta parseDataSourceMeta(String strDataSourceMeta, DatasetConfig datasetConfig)
            throws DatasetException;

    default void setContext(DataSourceProcessorContext context) {}

    // prepare data
    // ie: merge chunk data 、convert excel to csv
    void prepareData() throws DatasetException;

    // analyze data
    void analyzeData() throws DatasetException;

    // upload data
    void uploadData() throws DatasetException;

    // cleanup
    void cleanupData() throws DatasetException;

    // process
    default void processData(DataSourceProcessorContext context) throws DatasetException {
        try {
            // init context
            setContext(context);
            // preprocess data
            prepareData();
            // data analysis, reading data fields and data volume
            analyzeData();
            // 差分隐私加噪（在落盘前对指定列施加噪声）
            DifferentialPrivacyProcessor.applyIfEnabled(context);
            // upload data to storage
            uploadData();
            // HDFS 引用型在启用差分隐私时需额外落盘加噪文件
            DifferentialPrivacyProcessor.uploadNoisedFileIfNeeded(context);
        } finally {
            // data clean
            try {
                cleanupData();
            } catch (Exception ignored) {
            }
        }
    }
}
