package com.qiao.compliance.service;

/**
 * A keyword hit that sends content to manual review. Stored in {@code project.flags}.
 */
public record KeywordFlag(Type type, String keyword) {

    public enum Type { EMPLOYMENT, SENSITIVE }
}
