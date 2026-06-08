-- 差分隐私配置字段迁移（已有库执行一次）
ALTER TABLE `wedpr_dataset`
    ADD COLUMN `differential_privacy_meta` TEXT DEFAULT NULL COMMENT '差分隐私配置JSON'
    AFTER `data_source_meta`;
