package com.webank.wedpr.components.scheduler.executor.impl.mpc;

import com.webank.wedpr.common.protocol.JobType;
import com.webank.wedpr.components.db.mapper.dataset.mapper.DatasetMapper;
import com.webank.wedpr.components.project.dao.JobDO;
import com.webank.wedpr.components.scheduler.executor.ExecutorParamChecker;
import java.util.Arrays;
import java.util.List;

public class MPCExecutorParamChecker implements ExecutorParamChecker {
    private final DatasetMapper datasetMapper;

    public MPCExecutorParamChecker(DatasetMapper datasetMapper) {
        this.datasetMapper = datasetMapper;
    }

    @Override
    public List<JobType> getJobTypeList() {
        return Arrays.asList(JobType.MPC, JobType.SQL);
    }

    @Override
    public Object checkAndParseJob(JobDO jobDO) throws Exception {
        MPCJobParam mpcJobParam = MPCJobParam.deserialize(jobDO.getParam());
        mpcJobParam.setJobID(jobDO.getId());
        mpcJobParam.setJobType(jobDO.getType());
        mpcJobParam.setDatasetIDList(jobDO.getDatasetList());
        // check the param
        mpcJobParam.check(datasetMapper);

        return mpcJobParam;
    }
}
