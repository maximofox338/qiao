package com.qiao.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.project.domain.Application;
import com.qiao.project.domain.ApplicationMapper;
import com.qiao.project.domain.ApplicationMember;
import com.qiao.project.domain.ApplicationMemberMapper;
import com.qiao.project.domain.ApplicationStatus;
import com.qiao.team.service.TeamWorkDirectory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A team (member) is committed while one of its applications is still SUBMITTED, SHORTLISTED or SELECTED.
 */
@Component
public class ApplicationTeamWork implements TeamWorkDirectory {

    private final ApplicationMapper applicationMapper;
    private final ApplicationMemberMapper memberMapper;

    public ApplicationTeamWork(ApplicationMapper applicationMapper, ApplicationMemberMapper memberMapper) {
        this.applicationMapper = applicationMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public boolean hasActiveWork(long teamId, Long engineerUserId) {
        List<Long> open = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                        .select(Application::getId)
                        .eq(Application::getTeamId, teamId)
                        .in(Application::getStatus, ApplicationStatus.ACTIVE))
                .stream().map(Application::getId).toList();
        if (open.isEmpty() || engineerUserId == null) {
            return !open.isEmpty();
        }
        return memberMapper.exists(new LambdaQueryWrapper<ApplicationMember>()
                .in(ApplicationMember::getApplicationId, open)
                .eq(ApplicationMember::getEngineerUserId, engineerUserId));
    }
}
