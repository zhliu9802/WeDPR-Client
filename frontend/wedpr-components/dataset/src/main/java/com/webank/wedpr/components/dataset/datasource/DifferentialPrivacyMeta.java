package com.webank.wedpr.components.dataset.datasource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.webank.wedpr.common.utils.ObjectMapperFactory;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据集差分隐私配置，由创建数据集时前端传入并持久化。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifferentialPrivacyMeta {

    public static final String MECHANISM_LAPLACE = "laplace";
    public static final String MECHANISM_GAUSSIAN = "gaussian";

    /** 是否启用差分隐私 */
    private Boolean enabled = Boolean.FALSE;

    /** 隐私预算 ε，越小隐私保护越强 */
    private Double epsilon;

    /** 隐私参数 δ，高斯机制时使用 */
    private Double delta;

    /** 全局敏感度，默认 1.0 */
    private Double sensitivity = 1.0;

    /** 加噪机制：laplace / gaussian */
    private String mechanism = MECHANISM_LAPLACE;

    /** 需要加噪的列名列表 */
    private List<String> columns = new ArrayList<>();

    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public void validate() throws DatasetException {
        if (!isEnabled()) {
            return;
        }
        if (epsilon == null || epsilon <= 0) {
            throw new DatasetException("启用差分隐私时，epsilon 必须大于 0");
        }
        if (MECHANISM_GAUSSIAN.equalsIgnoreCase(mechanism)) {
            if (delta == null || delta <= 0 || delta >= 1) {
                throw new DatasetException("高斯机制下，delta 必须在 (0, 1) 区间内");
            }
        }
        if (columns == null || columns.isEmpty()) {
            throw new DatasetException("启用差分隐私时，必须至少选择一列");
        }
        for (int i = 0; i < columns.size(); i++) {
            if (StringUtils.isBlank(columns.get(i))) {
                throw new DatasetException("差分隐私列名不能为空");
            }
            columns.set(i, columns.get(i).trim());
        }
        if (sensitivity == null || sensitivity <= 0) {
            throw new DatasetException("sensitivity 必须大于 0");
        }
        if (StringUtils.isBlank(mechanism)) {
            mechanism = MECHANISM_LAPLACE;
        }
    }

    public static DifferentialPrivacyMeta deserialize(String json) throws DatasetException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return ObjectMapperFactory.getObjectMapper()
                    .readValue(json, DifferentialPrivacyMeta.class);
        } catch (Exception e) {
            throw new DatasetException("解析差分隐私配置失败: " + e.getMessage());
        }
    }

    public static String serialize(DifferentialPrivacyMeta meta) throws DatasetException {
        if (meta == null) {
            return "";
        }
        try {
            return ObjectMapperFactory.getObjectMapper().writeValueAsString(meta);
        } catch (Exception e) {
            throw new DatasetException("序列化差分隐私配置失败: " + e.getMessage());
        }
    }
}
