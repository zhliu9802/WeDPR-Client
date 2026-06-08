package com.webank.wedpr.components.dataset.utils;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.webank.wedpr.components.dataset.datasource.DifferentialPrivacyMeta;
import com.webank.wedpr.components.db.mapper.dataset.exception.DatasetException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对 CSV 指定数值列施加差分隐私噪声（拉普拉斯 / 高斯机制）。
 */
public class DifferentialPrivacyUtils {

    private static final Logger logger = LoggerFactory.getLogger(DifferentialPrivacyUtils.class);

    private DifferentialPrivacyUtils() {}

    /**
     * 对 CSV 文件指定列加噪，结果覆盖原文件。
     *
     * @param csvPath CSV 文件路径
     * @param meta 差分隐私配置
     * @param headerFields 表头列名（来自 analyzeData）
     */
    public static void applyToCsvFile(
            String csvPath, DifferentialPrivacyMeta meta, List<String> headerFields)
            throws DatasetException {
        if (meta == null || !meta.isEnabled()) {
            return;
        }
        meta.validate();

        Set<String> targetColumns = new HashSet<>(meta.getColumns());
        Map<Integer, String> columnIndexMap = new HashMap<>();
        for (int i = 0; i < headerFields.size(); i++) {
            String field = headerFields.get(i).trim();
            if (targetColumns.contains(field)) {
                columnIndexMap.put(i, field);
            }
        }
        if (columnIndexMap.isEmpty()) {
            throw new DatasetException(
                    "差分隐私列 "
                            + meta.getColumns()
                            + " 在数据集表头 "
                            + headerFields
                            + " 中不存在");
        }

        Path sourcePath = Paths.get(csvPath);
        Path tempPath = Paths.get(csvPath + ".dp.tmp");
        Random random = new Random();

        try (BufferedReader bufferedReader =
                        Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(bufferedReader);
                BufferedWriter bufferedWriter =
                        Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8);
                CSVWriter csvWriter = new CSVWriter(bufferedWriter)) {

            String[] headers = csvReader.readNext();
            if (headers == null) {
                throw new DatasetException("CSV 文件为空，无法应用差分隐私");
            }
            csvWriter.writeNext(headers);

            String[] row;
            long processedRows = 0;
            long noisedCells = 0;
            while ((row = csvReader.readNext()) != null) {
                String[] outputRow = row.clone();
                for (Map.Entry<Integer, String> entry : columnIndexMap.entrySet()) {
                    int colIndex = entry.getKey();
                    if (colIndex >= outputRow.length) {
                        continue;
                    }
                    String raw = outputRow[colIndex];
                    if (raw == null || raw.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        double value = Double.parseDouble(raw.trim());
                        double noise = sampleNoise(meta, random);
                        outputRow[colIndex] = formatNumeric(value + noise);
                        noisedCells++;
                    } catch (NumberFormatException e) {
                        logger.warn(
                                "列 {} 值 '{}' 非数值，跳过差分隐私加噪",
                                entry.getValue(),
                                raw);
                    }
                }
                csvWriter.writeNext(outputRow);
                processedRows++;
            }

            logger.info(
                    "差分隐私加噪完成, file: {}, rows: {}, noisedCells: {}, mechanism: {}, epsilon: {}, columns: {}",
                    csvPath,
                    processedRows,
                    noisedCells,
                    meta.getMechanism(),
                    meta.getEpsilon(),
                    meta.getColumns());

        } catch (DatasetException e) {
            throw e;
        } catch (Exception e) {
            logger.error("应用差分隐私失败, csvPath: {}, e: ", csvPath, e);
            throw new DatasetException("应用差分隐私失败: " + e.getMessage());
        }

        try {
            Files.move(tempPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new DatasetException("替换加噪后 CSV 文件失败: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception ignored) {
            }
        }
    }

    /** 根据机制采样噪声 */
    private static double sampleNoise(DifferentialPrivacyMeta meta, Random random) {
        double sensitivity = meta.getSensitivity();
        double epsilon = meta.getEpsilon();
        if (DifferentialPrivacyMeta.MECHANISM_GAUSSIAN.equalsIgnoreCase(meta.getMechanism())) {
            double delta = meta.getDelta();
            // 高斯机制标准差：σ = sensitivity * sqrt(2 ln(1.25/δ)) / ε
            double sigma =
                    sensitivity * Math.sqrt(2 * Math.log(1.25 / delta)) / Math.max(epsilon, 1e-9);
            return random.nextGaussian() * sigma;
        }
        // 默认拉普拉斯机制：scale = sensitivity / ε
        double scale = sensitivity / Math.max(epsilon, 1e-9);
        return sampleLaplace(random, scale);
    }

    private static double sampleLaplace(Random random, double scale) {
        double u = random.nextDouble() - 0.5;
        return -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
    }

    private static String formatNumeric(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.6f", value);
    }

    /** 加噪后刷新数据集文件 hash 与大小 */
    public static void refreshDatasetFileMeta(
            com.webank.wedpr.components.db.mapper.dataset.dao.Dataset dataset,
            String csvPath,
            String hashAlgorithm)
            throws DatasetException {
        File file = new File(csvPath);
        if (!file.exists()) {
            return;
        }
        try {
            dataset.setDatasetSize(FileUtils.getFileSize(csvPath));
            dataset.setDatasetVersionHash(FileUtils.calculateFileHash(csvPath, hashAlgorithm));
        } catch (Exception e) {
            throw new DatasetException("刷新加噪后文件元数据失败: " + e.getMessage());
        }
    }

    public static List<String> parseFieldList(String datasetFields) {
        List<String> fields = new ArrayList<>();
        if (datasetFields == null || datasetFields.trim().isEmpty()) {
            return fields;
        }
        for (String part : datasetFields.split(",")) {
            if (!part.trim().isEmpty()) {
                fields.add(part.trim());
            }
        }
        return fields;
    }
}
