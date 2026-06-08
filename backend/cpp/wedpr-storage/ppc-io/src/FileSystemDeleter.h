#pragma once
#include <string>
#include <vector>
#include <boost/filesystem.hpp>
#include <bcos-utilities/Log.h>

class FileSystemDeleter
{  
public:
    FileSystemDeleter(std::vector<std::string> filePaths): m_filePaths(filePaths) {}
    ~FileSystemDeleter() 
    {
        removeAllFiles(m_filePaths);
    }

public:
    void removeAllFiles(const std::vector<std::string> &files) 
    {
        for (const auto &file : files)
        {
            try 
            {
                if (!file.empty() && boost::filesystem::exists(file))
                {
                    boost::filesystem::remove_all(file);
                    BCOS_LOG(INFO) << LOG_DESC("remove file") << LOG_KV("file", file);
                }
            } catch (...) 
            {
                // do nothing
            }
        }
    }

private:
    std::vector<std::string> m_filePaths;
};