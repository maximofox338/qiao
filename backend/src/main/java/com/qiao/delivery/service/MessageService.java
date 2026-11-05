package com.qiao.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.client.service.CompanyService;
import com.qiao.compliance.service.KeywordScanner;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.service.ContractParticipant;
import com.qiao.contract.service.ContractService;
import com.qiao.delivery.domain.Message;
import com.qiao.delivery.domain.MessageMapper;
import com.qiao.delivery.web.MessageView;
import com.qiao.profile.service.EngineerProfileService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Contract chat between the client company and the engineer (or every member of a team). Messages matching
 * compliance keywords are flagged.
 */
@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final ContractService contractService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final KeywordScanner keywordScanner;

    public MessageService(MessageMapper messageMapper, ContractService contractService, CompanyService companyService,
                          EngineerProfileService profileService, KeywordScanner keywordScanner) {
        this.messageMapper = messageMapper;
        this.contractService = contractService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.keywordScanner = keywordScanner;
    }

    public List<MessageView> list(long userId, long contractId) {
        ContractParticipant participant = contractService.requireParticipant(userId, contractId);
        Names names = names(participant);
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                        .eq(Message::getContractId, contractId)
                        .orderByAsc(Message::getId))
                .stream().map(m -> view(m, names)).toList();
    }

    public MessageView post(long userId, long contractId, String body) {
        ContractParticipant participant = contractService.requireParticipant(userId, contractId);
        Message message = new Message();
        message.setContractId(contractId);
        message.setSenderUserId(userId);
        message.setBody(body.trim());
        message.setFlagged(keywordScanner.matchesAny(message.getBody()));
        messageMapper.insert(message);
        return view(message, names(participant));
    }

    List<Message> sentBy(long userId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderUserId, userId)
                .orderByAsc(Message::getId));
    }

    private record Names(String client, List<Long> engineerIds, Map<Long, String> engineers) {
    }

    /**
     * Engineers know the client only by its country: the company name is shown to the client side only. Engineer
     * messages carry the sender's own alias (team members each have theirs).
     */
    private Names names(ContractParticipant viewer) {
        Contract contract = viewer.contract();
        String client = viewer.side() == UserRole.CLIENT ? companyService.get(contract.getCompanyId()).getName() : null;
        List<Long> engineerIds = contractService.engineerUserIds(contract);
        return new Names(client, engineerIds, profileService.aliases(engineerIds));
    }

    private static MessageView view(Message m, Names names) {
        UserRole side = names.engineerIds().contains(m.getSenderUserId()) ? UserRole.ENGINEER : UserRole.CLIENT;
        return new MessageView(m.getId(), m.getContractId(), m.getSenderUserId(), side,
                side == UserRole.ENGINEER ? names.engineers().get(m.getSenderUserId()) : names.client(), m.getBody(),
                Boolean.TRUE.equals(m.getFlagged()), m.getCreatedAt());
    }
}
