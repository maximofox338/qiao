package com.qiao.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.storage.FileStorage;
import com.qiao.common.util.Hashing;
import com.qiao.contract.service.ContractService;
import com.qiao.contract.service.MilestoneService;
import com.qiao.delivery.domain.Attachment;
import com.qiao.delivery.domain.AttachmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Contract files: size-limited, content-type sniffed, stored under random keys and only served to participants.
 */
@Service
public class AttachmentService {

    public static final DataSize MAX_SIZE = DataSize.ofMegabytes(20);
    private static final int MAX_FILENAME_LENGTH = 200;

    private final AttachmentMapper attachmentMapper;
    private final ContractService contractService;
    private final MilestoneService milestoneService;
    private final FileStorage storage;
    private final Clock clock;

    public AttachmentService(AttachmentMapper attachmentMapper, ContractService contractService,
                             MilestoneService milestoneService, FileStorage storage, Clock clock) {
        this.attachmentMapper = attachmentMapper;
        this.contractService = contractService;
        this.milestoneService = milestoneService;
        this.storage = storage;
        this.clock = clock;
    }

    public record Download(Attachment attachment, InputStream content) {
    }

    public Attachment upload(long userId, long contractId, MultipartFile file, Long milestoneId) {
        contractService.requireParticipant(userId, contractId);
        if (milestoneId != null && !milestoneService.ref(milestoneId).contractId().equals(contractId)) {
            throw ApiException.validation("milestoneId", ValidationCodes.INVALID);
        }
        if (file.isEmpty()) {
            throw ApiException.validation("file", ValidationCodes.REQUIRED);
        }
        if (file.getSize() > MAX_SIZE.toBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        String key = "attachments/" + YearMonth.now(clock) + "/" + UUID.randomUUID();
        try (InputStream raw = new BufferedInputStream(file.getInputStream())) {
            raw.mark(ContentTypeSniffer.HEAD_SIZE);
            byte[] head = new byte[ContentTypeSniffer.HEAD_SIZE];
            int read = raw.readNBytes(head, 0, head.length);
            raw.reset();
            String contentType = ContentTypeSniffer.sniff(head, read, filename)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE));
            MessageDigest digest = Hashing.sha256();
            try (DigestInputStream hashing = new DigestInputStream(raw, digest)) {
                storage.put(key, hashing, file.getSize(), contentType);
            }
            Attachment attachment = new Attachment();
            attachment.setContractId(contractId);
            attachment.setMilestoneId(milestoneId);
            attachment.setUploaderUserId(userId);
            attachment.setFilename(filename);
            attachment.setContentType(contentType);
            attachment.setSizeBytes(file.getSize());
            attachment.setStorageKey(key);
            attachment.setSha256(HexFormat.of().formatHex(digest.digest()));
            attachmentMapper.insert(attachment);
            return attachment;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Download download(long userId, long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw ApiException.notFound("Attachment");
        }
        contractService.requireParticipant(userId, attachment.getContractId());
        return new Download(attachment, storage.open(attachment.getStorageKey()));
    }

    /**
     * Links uploaded files to a milestone submission; all must belong to the contract and have been uploaded by the
     * engineer side (the engineer, or any member of a team).
     */
    void linkToMilestone(Collection<Long> attachmentIds, long contractId, long milestoneId,
                         Collection<Long> uploaderUserIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        long valid = attachmentMapper.selectCount(new LambdaQueryWrapper<Attachment>()
                .in(Attachment::getId, attachmentIds)
                .eq(Attachment::getContractId, contractId)
                .in(Attachment::getUploaderUserId, uploaderUserIds));
        if (valid != attachmentIds.stream().distinct().count()) {
            throw ApiException.validation("attachmentIds", ValidationCodes.INVALID);
        }
        attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                .set(Attachment::getMilestoneId, milestoneId)
                .set(Attachment::getUpdatedAt, clock.instant())
                .in(Attachment::getId, attachmentIds));
    }

    List<Attachment> uploadedBy(long userId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getUploaderUserId, userId)
                .orderByAsc(Attachment::getId));
    }

    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "file";
        }
        return name.length() <= MAX_FILENAME_LENGTH ? name : name.substring(name.length() - MAX_FILENAME_LENGTH);
    }
}
