package com.webank.wedpr.components.scheduler.dag.utils;

import lombok.Data;

@Data
public class MpcResult {
    String mpcResultFields;
    Integer mpcResultFieldCount;
    Integer mpcResultValueCount;

    String mpcResultTimeLine;
    String mpcResultDataSendLine;
    String mpcResultGlobalDataSendLine;
}
