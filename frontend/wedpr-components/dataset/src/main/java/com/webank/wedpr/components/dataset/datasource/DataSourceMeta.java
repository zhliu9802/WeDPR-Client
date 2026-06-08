package com.webank.wedpr.components.dataset.datasource;

public interface DataSourceMeta {
    // dynamic data source or not
    default boolean dynamicDataSource() {
        return false;
    }
}
