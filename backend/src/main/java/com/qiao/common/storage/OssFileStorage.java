package com.qiao.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.InputStream;

/**
 * Alibaba Cloud OSS storage ({@code STORAGE=oss}).
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "oss")
public class OssFileStorage implements FileStorage {

    private final OSS client;
    private final String bucket;

    public OssFileStorage(StorageProperties properties) {
        StorageProperties.Oss oss = properties.oss();
        Assert.hasText(oss.endpoint(), "OSS_ENDPOINT is required when STORAGE=oss");
        Assert.hasText(oss.bucket(), "OSS_BUCKET is required when STORAGE=oss");
        this.client = new OSSClientBuilder().build(oss.endpoint(), oss.accessKeyId(), oss.accessKeySecret());
        this.bucket = oss.bucket();
    }

    @Override
    public void put(String key, InputStream content, long sizeBytes, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(sizeBytes);
        metadata.setContentType(contentType);
        client.putObject(bucket, key, content, metadata);
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(bucket, key).getObjectContent();
    }

    @PreDestroy
    void shutdown() {
        client.shutdown();
    }
}
