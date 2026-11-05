package com.qiao.compliance.service;

import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.compliance.domain.TransferDataKind;
import com.qiao.compliance.domain.TransferEvent;
import com.qiao.compliance.domain.TransferEventMapper;
import com.qiao.compliance.web.TransferSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts unique engineers whose personal data was shown to clients per calendar year (spec §1.6).
 * Once the yearly limit is reached, data of engineers not yet counted that year is no longer disclosed.
 */
@Service
public class TransferService {

    public static final long WARN_THRESHOLD = 50_000;
    public static final long CRITICAL_THRESHOLD = 80_000;
    public static final long LIMIT_THRESHOLD = 100_000;

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferEventMapper transferEventMapper;
    private final Clock clock;

    public TransferService(TransferEventMapper transferEventMapper, Clock clock) {
        this.transferEventMapper = transferEventMapper;
        this.clock = clock;
    }

    /**
     * Records that {@code companyId} was shown data of the given engineers.
     *
     * @throws ApiException {@code TRANSFER_LIMIT_REACHED} if this would push the yearly count past the limit
     */
    public void record(Collection<Long> engineerUserIds, long companyId, TransferDataKind kind) {
        if (engineerUserIds.isEmpty()) {
            return;
        }
        int year = currentYear();
        long counted = transferEventMapper.countUniqueEngineers(year);
        if (counted + engineerUserIds.size() > WARN_THRESHOLD) {
            Set<Long> newEngineers = new HashSet<>(engineerUserIds);
            transferEventMapper.findCountedEngineers(year, List.copyOf(engineerUserIds)).forEach(newEngineers::remove);
            if (counted + newEngineers.size() > LIMIT_THRESHOLD) {
                throw new ApiException(ErrorCode.TRANSFER_LIMIT_REACHED);
            }
            if (!newEngineers.isEmpty()) {
                log.warn("Cross-border transfer counter for {} at level {}", year, level(counted + newEngineers.size()));
            }
        }
        for (Long engineerUserId : engineerUserIds) {
            TransferEvent event = new TransferEvent();
            event.setYear(year);
            event.setEngineerUserId(engineerUserId);
            event.setCompanyId(companyId);
            event.setDataKind(kind);
            transferEventMapper.insert(event);
        }
    }

    public TransferSummary summary(Integer year) {
        int target = year == null ? currentYear() : year;
        Map<String, Long> unique = new LinkedHashMap<>();
        for (TransferDataKind kind : TransferDataKind.values()) {
            unique.put(kind.name(), transferEventMapper.countUniqueEngineersByKind(target, kind.name()));
        }
        long any = transferEventMapper.countUniqueEngineers(target);
        unique.put("ANY", any);
        return new TransferSummary(target, unique,
                new TransferSummary.Thresholds(WARN_THRESHOLD, CRITICAL_THRESHOLD, LIMIT_THRESHOLD), level(any));
    }

    public static TransferLevel level(long uniqueEngineers) {
        if (uniqueEngineers >= LIMIT_THRESHOLD) {
            return TransferLevel.LIMIT;
        }
        if (uniqueEngineers >= CRITICAL_THRESHOLD) {
            return TransferLevel.CRITICAL;
        }
        if (uniqueEngineers >= WARN_THRESHOLD) {
            return TransferLevel.WARN;
        }
        return TransferLevel.OK;
    }

    private int currentYear() {
        return LocalDate.now(clock).getYear();
    }
}
