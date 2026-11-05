package com.qiao.project.domain;

/**
 * Client declarations required on every project (spec §1.3).
 */
public record ProjectDeclarations(Boolean noControlledTech, Boolean noBulkUsSensitiveData, Boolean noEmployment,
                                  Boolean noProhibitedCategory) {

    public boolean allTrue() {
        return Boolean.TRUE.equals(noControlledTech) && Boolean.TRUE.equals(noBulkUsSensitiveData)
                && Boolean.TRUE.equals(noEmployment) && Boolean.TRUE.equals(noProhibitedCategory);
    }
}
