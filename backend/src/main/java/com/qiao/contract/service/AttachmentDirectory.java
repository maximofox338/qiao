package com.qiao.contract.service;

import java.util.List;

/**
 * Lists a contract's attachments for the contract views. Implemented by the delivery module, which owns them.
 */
public interface AttachmentDirectory {

    List<AttachmentSummary> list(long contractId);
}
