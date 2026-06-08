package com.webank.wedpr.components.scheduler.executor.impl.mpc.utils;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
import com.opencsv.exceptions.CsvException;
import com.webank.wedpr.common.utils.WeDPRException;
import com.webank.wedpr.components.scheduler.executor.impl.ExecutorConfig;
import com.webank.wedpr.components.scheduler.executor.impl.model.DatasetInfo;
import com.webank.wedpr.components.scheduler.executor.impl.model.FileMeta;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MpcUtils {

    private static final Logger logger = LoggerFactory.getLogger(MpcUtils.class);

    private MpcUtils() {}

    public static final String MPC_FIELD_ID_NAME = "id";
    public static final int DEFAULT_MPC_CONTENT_SHARE_BYTES_LENGTH = 64;
    public static final String MPC_FIELD_NORMALIZED_NAMES = "field%d";
    public static final String MPC_SOURCE_RECORD_COUNT_LABEL = "$(source%d_record_count)";
    public static final String MPC_SHARE_BYTES_LENGTH_PATTERN = "BIT_LENGTH = (\\d+)";

    public static int getShareBytesLength(String mpcContent) {

        Pattern pattern = Pattern.compile(MPC_SHARE_BYTES_LENGTH_PATTERN);
        Matcher matcher = pattern.matcher(mpcContent);

        int shareBitLength = DEFAULT_MPC_CONTENT_SHARE_BYTES_LENGTH;
        if (matcher.find()) {
            shareBitLength = Integer.parseInt(matcher.group(1));
        }
        return shareBitLength;
    }

    public static int getMpcDatasetColumnCount(String mpcContent, int selfIndex)
            throws WeDPRException {

        String strPattern = "source" + selfIndex + "_column_count\\s*=\\s*(\\d+)";
        Pattern pattern = Pattern.compile(strPattern);
        Matcher matcher = pattern.matcher(mpcContent);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        throw new WeDPRException(
                "Not found \"source" + selfIndex + "_column_count\" in mpc content");
    }

    public static int getIdFieldIndex(String file, List<String> fieldNames) throws WeDPRException {

        int index = 0;
        for (String fieldName : fieldNames) {
            if (fieldName.equals(MPC_FIELD_ID_NAME)) {

                if (logger.isInfoEnabled()) {
                    logger.info("the id field found, file: {}, index: {}", file, index);
                }

                return index;
            }
            index++;
        }

        logger.error("the id field does not exist, file: {}, fields: {}", file, fieldNames);
        throw new WeDPRException("the id field does not exist, fields: " + fieldNames);
    }

    public static List<String> makeFieldNamesNormalized(String file, List<String> fieldNames)
            throws WeDPRException {

        List<String> normalizedNames = new ArrayList<>();
        if (fieldNames.contains(MPC_FIELD_ID_NAME)) {
            normalizedNames.add(MPC_FIELD_ID_NAME);
            int size = fieldNames.size();
            for (int i = 0; i < size - 1; i++) {
                normalizedNames.add(String.format(MPC_FIELD_NORMALIZED_NAMES, i));
            }
        } else {
            int size = fieldNames.size();
            for (int i = 0; i < size; i++) {
                normalizedNames.add(String.format(MPC_FIELD_NORMALIZED_NAMES, i));
            }

            logger.error("Not found id field, file: {}, fieldNames: {}", file, fieldNames);
            throw new WeDPRException("Not found id field in dataset, file: " + file);
        }

        return normalizedNames;
    }

    public static List<String> makeSelectFields(int datasetCountNumber) {
        // select columns
        List<String> selectColumns = new ArrayList<>();
        for (int i = 0; i < datasetCountNumber; i++) {
            selectColumns.add(String.format(MPC_FIELD_NORMALIZED_NAMES, i));
        }
        return selectColumns;
    }

    public static List<Integer> makeSelectFieldsIndex(
            List<String> selectFieldNames, List<String> originalFieldNames) throws WeDPRException {
        List<Integer> indexList = new ArrayList<>();
        for (String selectFieldName : selectFieldNames) {
            int index = originalFieldNames.indexOf(selectFieldName);
            if (index == -1) {
                logger.error("Not found select field index, fieldName: {}", selectFieldName);
                throw new WeDPRException(
                        "Not found select field index, fieldName: " + selectFieldName);
            }
            indexList.add(index);
        }
        return indexList;
    }

    public static String[] makeNextLine(String[] originalLineList, List<Integer> selectIndexList) {
        String[] nextLine = new String[selectIndexList.size()];
        int curIndex = 0;
        for (Integer index : selectIndexList) {
            nextLine[curIndex++] = originalLineList[index];
        }

        return nextLine;
    }

    public static long makeDatasetToMpcDataDirect(
            String jobId,
            String datasetFilePath,
            String datasetMpcFilePath,
            int datasetCountNumber,
            boolean withHeader)
            throws IOException, CsvException, WeDPRException {

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(datasetFilePath));
                BufferedWriter bufferedWriter =
                        new BufferedWriter(new FileWriter(datasetMpcFilePath));
                CSVReader csvReader = new CSVReader(bufferedReader);
                CSVWriter csvWriter =
                        new CSVWriter(
                                bufferedWriter,
                                ' ',
                                ICSVWriter.DEFAULT_QUOTE_CHARACTER,
                                ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
                                ICSVWriter.DEFAULT_LINE_END)) {

            // read original column names
            String[] fieldNames = csvReader.readNextSilently();
            List<String> originalFieldNames = Arrays.asList(fieldNames);

            List<String> newFieldNames =
                    makeFieldNamesNormalized(datasetFilePath, originalFieldNames);

            List<String> selectFieldNames = makeSelectFields(datasetCountNumber);
            List<Integer> selectFieldsIndex =
                    makeSelectFieldsIndex(selectFieldNames, newFieldNames);

            if (withHeader) {
                // write in new column names
                csvWriter.writeNext(selectFieldNames.toArray(new String[0]), false);
            }

            // read the remaining data rows and write them into the output file
            String[] nextLine;
            long totalRowNum = 0;
            while ((nextLine = csvReader.readNext()) != null) {
                String[] writeNextLine = makeNextLine(nextLine, selectFieldsIndex);

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "prepare step, jobId: {}, next line: {}",
                            jobId,
                            Arrays.asList(writeNextLine));
                }

                csvWriter.writeNext(writeNextLine, false);
                totalRowNum++;
            }

            logger.info(
                    "prepare step, jobId: {}, totalRowNum: {}, original field names: {}, field names: {}, select field index: {}",
                    jobId,
                    totalRowNum,
                    originalFieldNames,
                    fieldNames,
                    selectFieldsIndex);

            return totalRowNum;
        }
    }

    public static String replaceMpcContentFieldHolder(
            String mpcContent, List<DatasetInfo> datasetInfoList) {

        int partCount = datasetInfoList.size();
        for (int i = 0; i < partCount; i++) {
            FileMeta dataset = datasetInfoList.get(i).getDataset();
            long datasetRecordCount = dataset.getDatasetRecordCount();

            String placeholder = String.format(MPC_SOURCE_RECORD_COUNT_LABEL, i);
            mpcContent = mpcContent.replace(placeholder, String.valueOf(datasetRecordCount));
        }

        if (logger.isTraceEnabled()) {
            logger.trace("new mpc content: {}", mpcContent);
        }

        return mpcContent;
    }

    public static String replaceMpcContentFieldHolder(
            String mpcContent, int partCount, long psiResultDataCount) {

        for (int i = 0; i < partCount; i++) {
            String placeholder = String.format(MPC_SOURCE_RECORD_COUNT_LABEL, i);
            mpcContent = mpcContent.replace(placeholder, String.valueOf(psiResultDataCount));
        }

        if (logger.isTraceEnabled()) {
            logger.trace("new mpc content: {}", mpcContent);
        }

        return mpcContent;
    }

    public static boolean checkNeedRunPsi(String jobId, String mpcContent) {

        String regex = ExecutorConfig.getMpcPsiOptionRegex();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mpcContent);

        boolean needRunPsi = matcher.find();

        logger.info("check need run psi for mpc, jobId: {}, needRunPsi: {} ", jobId, needRunPsi);

        return needRunPsi;
    }

    public static List<Integer> loadIdFieldValuesFromFile(String jobId, String inputFilePath)
            throws Exception {
        List<Integer> idValues = new ArrayList<>();

        long lineNum = 0;

        // read the id field value of the psi result file
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(inputFilePath));
                CSVReader psiResultReader = new CSVReader(bufferedReader)) {

            String[] fieldNames = psiResultReader.readNextSilently();
            List<String> originalFieldNames = Arrays.asList(fieldNames);

            int idFieldIndex = getIdFieldIndex(inputFilePath, originalFieldNames);

            String[] nextLine;
            // TODO: id值都放在内存,数据量大的时候会有较高的内存占用
            while ((nextLine = psiResultReader.readNext()) != null) {

                String idFieldValue = nextLine[idFieldIndex];
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "load id field values, jobId: {}, idFieldValue: {}, next line: {}",
                            jobId,
                            idFieldValue,
                            Arrays.asList(nextLine));
                }

                lineNum++;

                idValues.add(Integer.valueOf(idFieldValue));
            }

            logger.info(
                    "load id field values, jobId: {}, fieldNames: {}, idFieldIndex: {}, lineNum: {}",
                    jobId,
                    fieldNames,
                    idFieldIndex,
                    lineNum);
        }

        Collections.sort(idValues);
        return idValues;
    }

    public static long mergeAndSortById(
            String jobId,
            String datasetFilePath,
            String psiResultFilePath,
            String resultFilePath,
            int datasetCountNumber,
            boolean withHeader)
            throws Exception {

        Set<Integer> psiIdMap = new TreeSet<>();

        long psiResultFileLineNum = 0;

        // read the id field value of the psi result file
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(psiResultFilePath));
                CSVReader psiResultReader = new CSVReader(bufferedReader)) {

            String[] fieldNames = psiResultReader.readNextSilently();
            List<String> originalFieldNames = Arrays.asList(fieldNames);

            int idFieldIndex = getIdFieldIndex(psiResultFilePath, originalFieldNames);

            String[] nextLine;
            // TODO: id值都放在内存,数据量大的时候会有较高的内存占用
            while ((nextLine = psiResultReader.readNext()) != null) {

                String idFieldValue = nextLine[idFieldIndex];
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "prepare step(psi), jobId: {}, idFieldValue: {}, next line: {}",
                            jobId,
                            idFieldValue,
                            Arrays.asList(nextLine));
                }

                psiResultFileLineNum++;

                psiIdMap.add(Integer.valueOf(idFieldValue));
            }

            logger.info(
                    "prepare step(psi), psi result file, jobId: {}, fieldNames: {}, idFieldIndex: {}, lineNum: {}",
                    jobId,
                    fieldNames,
                    idFieldIndex,
                    psiResultFileLineNum);
        }

        // id => line  sorted
        List<String> csvFieldNames = null;
        Map<Integer, String[]> csvId2Lines = new TreeMap<>();

        long totalReadLineNum = 0;
        long datasetFileLineNum = 0;
        // read the id field value of the psi result file
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(datasetFilePath));
                CSVReader datasetFileReader = new CSVReader(bufferedReader)) {

            String[] fieldNames = datasetFileReader.readNextSilently();
            List<String> originalFieldNames = Arrays.asList(fieldNames);

            List<String> normalizedFieldNames =
                    makeFieldNamesNormalized(datasetFilePath, originalFieldNames);

            int idFieldIndex = getIdFieldIndex(datasetFilePath, originalFieldNames);

            List<String> selectFieldNames = makeSelectFields(datasetCountNumber);

            List<Integer> selectFieldsIndex =
                    makeSelectFieldsIndex(selectFieldNames, normalizedFieldNames);

            csvFieldNames = selectFieldNames;

            String[] nextLine;
            while ((nextLine = datasetFileReader.readNext()) != null) {

                datasetFileLineNum++;

                String strId = nextLine[idFieldIndex];
                Integer id = Integer.valueOf(strId);
                if (!psiIdMap.contains(id)) {
                    continue;
                }

                String[] writeNextLine = makeNextLine(nextLine, selectFieldsIndex);

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "prepare step(psi), jobId: {}, next line: {}",
                            jobId,
                            Arrays.asList(writeNextLine));
                }

                csvId2Lines.put(id, writeNextLine);
                // resultFileWriter.writeNext(writeNextLine, false);
                totalReadLineNum++;
            }

            logger.info(
                    "prepare step(psi), jobId: {}, totalReadLineNum: {}, dataset original field names: {}, normalized field names: {}, idIndex: {}, select field names: {}, select field index: {}, datasetFileLineNum: {}",
                    jobId,
                    totalReadLineNum,
                    originalFieldNames,
                    normalizedFieldNames,
                    idFieldIndex,
                    selectFieldNames,
                    selectFieldsIndex,
                    datasetFileLineNum);
        }

        long totalWriteLineNum = 0;
        // read the id field value of the psi result file
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(resultFilePath));
                CSVWriter resultFileWriter =
                        new CSVWriter(
                                bufferedWriter,
                                ' ',
                                ICSVWriter.DEFAULT_QUOTE_CHARACTER,
                                ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
                                ICSVWriter.DEFAULT_LINE_END)) {

            if (withHeader) {
                // write in new column names
                resultFileWriter.writeNext(csvFieldNames.toArray(new String[0]), false);
            }

            for (Map.Entry<Integer, String[]> entry : csvId2Lines.entrySet()) {
                Integer key = entry.getKey();
                String[] value = entry.getValue();
                resultFileWriter.writeNext(value, false);
                totalWriteLineNum++;

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "prepare step(psi), jobId: {}, id: {}, next line: {}",
                            jobId,
                            key,
                            value);
                }
            }

            logger.info(
                    "prepare step(psi), jobId: {}, totalWriteLineNum: {}",
                    jobId,
                    totalWriteLineNum);
        }

        return totalReadLineNum;

        /*
        long totalLineNum = 0;
        long datasetFileLineNum = 0;
        // read the id field value of the psi result file
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(datasetFilePath));
                CSVReader datasetFileReader = new CSVReader(bufferedReader);
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(resultFilePath));
                CSVWriter resultFileWriter =
                        new CSVWriter(
                                bufferedWriter,
                                ' ',
                                ICSVWriter.DEFAULT_QUOTE_CHARACTER,
                                ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
                                ICSVWriter.DEFAULT_LINE_END)) {

            String[] fieldNames = datasetFileReader.readNextSilently();
            List<String> originalFieldNames = Arrays.asList(fieldNames);

            List<String> normalizedFieldNames =
                    makeFieldNamesNormalized(datasetFilePath, originalFieldNames);

            int idFieldIndex = getIdFieldIndex(datasetFilePath, originalFieldNames);

            List<String> selectFieldNames = makeSelectFields(datasetCountNumber);
            List<Integer> selectFieldsIndex =
                    makeSelectFieldsIndex(selectFieldNames, normalizedFieldNames);

            if (withHeader) {
                // write in new column names
                resultFileWriter.writeNext(selectFieldNames.toArray(new String[0]), false);
            }

            String[] nextLine;
            while ((nextLine = datasetFileReader.readNext()) != null) {

                datasetFileLineNum++;

                String strId = nextLine[idFieldIndex];
                Integer id = Integer.valueOf(strId);
                if (!psiIdMap.contains(id)) {
                    continue;
                }

                String[] writeNextLine = makeNextLine(nextLine, selectFieldsIndex);

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "prepare step(psi), jobId: {}, next line: {}",
                            jobId,
                            Arrays.asList(writeNextLine));
                }

                resultFileWriter.writeNext(writeNextLine, false);
                totalLineNum++;
            }

            logger.info(
                    "prepare step(psi), jobId: {}, totalLineNum: {}, dataset original field names: {}, normalized field names: {}, idIndex: {}, select field names: {}, select field index: {}, datasetFileLineNum: {}",
                    jobId,
                    totalLineNum,
                    originalFieldNames,
                    normalizedFieldNames,
                    idFieldIndex,
                    selectFieldNames,
                    selectFieldsIndex,
                    datasetFileLineNum);
        }

        */
    }
}
