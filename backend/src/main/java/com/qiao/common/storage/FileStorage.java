package com.qiao.common.storage;

import java.io.InputStream;

/**
 * Binary object storage addressed by opaque keys. Callers generate random keys; implementations never trust
 * user-provided names.
 */
public interface FileStorage {

    void put(String key, InputStream content, long sizeBytes, String contentType);

    InputStream open(String key);
}
