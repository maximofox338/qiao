package com.qiao.account.service;

/**
 * Implemented by each module that stores personal data, for data export and account deletion (spec §1.9).
 */
public interface PersonalDataHandler {

    /**
     * Key of this module's section in the export document.
     */
    String section();

    Object export(long userId);

    /**
     * Throws an {@link com.qiao.common.error.ApiException} when the account cannot be deleted yet.
     */
    default void assertDeletable(long userId) {
    }

    /**
     * Removes or anonymizes personal data; transactional records are kept.
     */
    default void anonymize(long userId) {
    }
}
