package com.webank.wedpr.components.scheduler.dag.utils;

import com.webank.wedpr.common.utils.WeDPRException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*

// mpc result file format:
  result_fields = result0
  result_values = 0
  result_values = 0
  Time = 0.98481 seconds
  Data sent = 25.5965 MB in ~2514 rounds (party 0; rounds counted double due to multi-threading; use '-v' for more     details)
  Global data sent = 51.2257 MB (all parties)
  */

public class MpcResultFileResolver {

    private static final Logger logger = LoggerFactory.getLogger(MpcResultFileResolver.class);

    public static final String PPC_RESULT_FIELDS_FLAG = "result_fields";
    public static final String PPC_RESULT_VALUES_FLAG = "result_values";
    public static final String PPC_RESULT_TIME_FLAG = "Time";
    public static final String PPC_RESULT_DATA_SEND_FLAG = "Data sent";
    public static final String PPC_RESULT_GLOBAL_DATA_SEND_FLAG = "Global data sent";

    public static final String CSV_SEP = ",";
    public static final String BLANK_SEP = " ";
    public static final String MPC_ID_FIELD = "id";

    public MpcResult doParseMpcResultFile(String mpcOutputFile, boolean onlyField)
            throws IOException, WeDPRException {

        int mpcResultRowCount = 0;
        int mpcResultFieldCount = 0;

        boolean needAddFields = true;
        MpcResult mpcResult = new MpcResult();
        String strResultFields = MPC_ID_FIELD;
        try (BufferedReader mpcOutputBufferedReader =
                new BufferedReader(new FileReader(mpcOutputFile))) {
            String line;
            while ((line = mpcOutputBufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(PPC_RESULT_FIELDS_FLAG)) {
                    strResultFields += CSV_SEP;
                    strResultFields +=
                            line.substring(line.indexOf('=') + 1)
                                    .trim()
                                    .replace(BLANK_SEP, CSV_SEP);
                    logger.info("## {}:{}", PPC_RESULT_FIELDS_FLAG, strResultFields);
                    mpcResult.setMpcResultFields(strResultFields);
                    needAddFields = false;
                } else if (line.startsWith(PPC_RESULT_VALUES_FLAG)) {
                    mpcResultFieldCount = line.split("=")[1].trim().split(BLANK_SEP).length;
                    logger.info("## {}:{}", PPC_RESULT_VALUES_FLAG, mpcResultFieldCount);
                    mpcResult.setMpcResultFieldCount(mpcResultFieldCount);
                    if (onlyField) {
                        break;
                    }
                    mpcResultRowCount++;
                } else if (line.startsWith(PPC_RESULT_TIME_FLAG)) {
                    logger.info("## {}:{}", PPC_RESULT_TIME_FLAG, line);
                    mpcResult.setMpcResultTimeLine(line);
                } else if (line.startsWith(PPC_RESULT_DATA_SEND_FLAG)) {
                    logger.info("## {}:{}", PPC_RESULT_DATA_SEND_FLAG, line);
                    mpcResult.setMpcResultDataSendLine(line);
                } else if (line.startsWith(PPC_RESULT_GLOBAL_DATA_SEND_FLAG)) {
                    logger.info("## {}:{}", PPC_RESULT_GLOBAL_DATA_SEND_FLAG, line);
                    mpcResult.setMpcResultGlobalDataSendLine(line);
                }
            }
        }

        if (needAddFields) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(strResultFields);
            for (int i = 0; i < mpcResultFieldCount; i++) {
                stringBuilder.append(CSV_SEP + "result").append(i);
            }
            mpcResult.setMpcResultFields(stringBuilder.toString());

            logger.error(
                    "Not found \"{}\" flag in mpc result file, fields: {}, mpcOutputFile: {}",
                    PPC_RESULT_FIELDS_FLAG,
                    stringBuilder,
                    mpcOutputFile);

            throw new WeDPRException(
                    "Not found \"" + PPC_RESULT_FIELDS_FLAG + "\" flag in mpc result file");
        }

        mpcResult.setMpcResultValueCount(mpcResultRowCount);
        logger.info("## mpc result: {}", mpcResult);
        return mpcResult;
    }

    public void transMpcOutputFile2ResultFile(
            String jobId,
            boolean needRunPsi,
            List<Integer> idValues,
            String mpcOutputFile,
            String mpcResultFile)
            throws IOException, WeDPRException {

        long startTimeMillis = System.currentTimeMillis();
        logger.info(
                "trans mpc output file to mpc result file start, jobId: {}, needRunPsi: {}, idValues size: {}, mpcOutputFile: {}, mpcResultFile: {}",
                jobId,
                needRunPsi,
                idValues.size(),
                mpcOutputFile,
                mpcResultFile);

        MpcResult mpcResult = doParseMpcResultFile(mpcOutputFile, true);

        int rowNumber = -1;
        try (BufferedReader mpcOutputBufferedReader =
                        new BufferedReader(new FileReader(mpcOutputFile));
                FileWriter mpcResultFileWriter = new FileWriter(mpcResultFile);
                PrintWriter csvWriter = new PrintWriter(mpcResultFileWriter)) {

            if (logger.isTraceEnabled()) {
                logger.trace("write mpc result file header: {}", mpcResult.getMpcResultFields());
            }

            // write header field
            csvWriter.write(mpcResult.getMpcResultFields());
            csvWriter.println();

            String line;
            while ((line = mpcOutputBufferedReader.readLine()) != null) {
                line = line.trim();

                if (!line.startsWith(PPC_RESULT_VALUES_FLAG)) {
                    continue;
                }

                rowNumber++;

                String[] valuesList = line.split("=")[1].trim().split(BLANK_SEP);

                StringBuilder stringBuilder = new StringBuilder();

                if (needRunPsi) {
                    if (rowNumber >= idValues.size()) {
                        Integer idValue = idValues.get(idValues.size() - 1);
                        int row = idValue + rowNumber - idValues.size() + 1;
                        stringBuilder.append(row);
                    } else {
                        stringBuilder.append(idValues.get(rowNumber));
                    }
                } else {
                    stringBuilder.append(rowNumber);
                }

                for (String value : valuesList) {
                    stringBuilder.append(CSV_SEP).append(value);
                }

                csvWriter.write(stringBuilder.toString());

                if (logger.isTraceEnabled()) {
                    logger.trace("result values: {}, index: {}", stringBuilder, rowNumber);
                }

                // add a newline at the end of each row
                csvWriter.println();
            }
        } finally {
            long endTimeMillis = System.currentTimeMillis();

            logger.info(
                    "trans mpc output file to mpc result file end, jobId: {}, mpcOutputFile: {}, mpcResultFile: {}, rowNumber: {}, costMs: {}",
                    jobId,
                    mpcOutputFile,
                    mpcResultFile,
                    rowNumber,
                    endTimeMillis - startTimeMillis);
        }
    }
}
