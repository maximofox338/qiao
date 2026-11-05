package com.qiao.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code STORAGE=local|oss}, {@code STORAGE_LOCAL_DIR}, {@code OSS_*}.
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @DefaultValue("local") String type,
        @DefaultValue("./data/uploads") String localDir,
        @DefaultValue Oss oss) {

    public record Oss(String endpoint, String bucket, String accessKeyId, String accessKeySecret) {
    }
}
