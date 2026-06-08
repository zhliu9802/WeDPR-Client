/*
 *  Copyright (C) 2022 WeDPR.
 *  SPDX-License-Identifier: Apache-2.0
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * @file MPCService.h
 * @author: caryliao
 * @date 2023-03-24
 */
#pragma once
#include "Common.h"
#include "ppc-framework/rpc/RpcInterface.h"
#include "ppc-io/src/FileLineReader.h"
#include "ppc-io/src/FileLineWriter.h"
#include "ppc-tools/src/config/MPCConfig.h"
#include "ppc-tools/src/config/StorageConfig.h"
#include <bcos-utilities/Common.h>
#include <bcos-utilities/ThreadPool.h>
#include <bcos-utilities/Timer.h>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace ppc::mpc
{
class MPCService: public std::enable_shared_from_this<MPCService>
{
public:
    using Ptr = std::shared_ptr<MPCService>;
    MPCService() = default;
    virtual ~MPCService() 
    {
        if (m_threadPool)
        {
            m_threadPool->stop();
        }
    }

    void runMpcRpc(Json::Value const& request, ppc::rpc::RespFunc func);
    void killMpcRpc(Json::Value const& request, ppc::rpc::RespFunc func);
    void asyncRunMpcRpc(Json::Value const& request, ppc::rpc::RespFunc func);
    void queryMpcRpc(Json::Value const& request, ppc::rpc::RespFunc func);

    void runMpcRpcByJobInfo(const JobInfo& jobInfo);

    void setMPCConfig(ppc::tools::MPCConfig const& mpcConfig);
    void setStorageConfig(ppc::tools::StorageConfig const& storageConfig);

    JobInfo paramsToJobInfo(const Json::Value& params);
    void makeCommand(std::string& cmd, const JobInfo& jobInfo);
    void getMpcProtocol(const int participantCount, const bool isMalicious,
        std::string& mpcBinFileName, std::string& compileOption);

    void doRun(const JobInfo& jobInfo);
    void doKill(Json::Value const& request, Json::Value& response);

    void onFinish(const std::string &jobId, const std::string &msg);
    void onFailed(const std::string &jobId, const std::string &msg);
    void onKill(const std::string &jobId, const std::string &msg);

    void execCommand(const std::string cmd, int& outExitStatus, std::string& outResult);

    void writeStringToFile(const std::string& content, const std::string& filePath);
    void readAndSaveFile(const std::string &readerFilePath, const std::string &writerFilePath,ppc::io::LineReader::Ptr lineReader, ppc::io::LineWriter::Ptr lineWriter);
    ppc::io::LineReader::Ptr initialize_lineReader(const JobInfo& jobInfo,
        const std::string& readerFilePath, ppc::protocol::DataResourceType type);
    ppc::io::LineWriter::Ptr initialize_lineWriter(const JobInfo& jobInfo,
        const std::string& writerFilePath, ppc::protocol::DataResourceType type);

    void setThreadPool(std::shared_ptr<bcos::ThreadPool> threadPool)
    {
        m_threadPool = threadPool;
    }

    bool addJobIfNotRunning(const JobInfo& jobInfo);

    bool queryJobStatus(const std::string &jobId, JobStatus &jobStatus);

private:
    std::mutex x_job2Status;
    std::unordered_map<std::string, JobStatus> m_job2Status;
    std::unordered_set<std::string> m_runningJobs;
    ppc::tools::MPCConfig m_mpcConfig;
    ppc::tools::StorageConfig m_storageConfig;

    std::shared_ptr<bcos::ThreadPool> m_threadPool;
};
}  // namespace ppc::mpc
