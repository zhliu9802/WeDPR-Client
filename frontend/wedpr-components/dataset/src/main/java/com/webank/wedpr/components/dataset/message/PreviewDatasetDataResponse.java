package com.webank.wedpr.components.dataset.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 数据集原始数据分页预览响应 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreviewDatasetDataResponse {
    private String datasetId;
    private String datasetTitle;
    private List<String> columns;
    private List<List<String>> rows;
    private long totalCount;
    private int pageNum;
    private int pageSize;
}
