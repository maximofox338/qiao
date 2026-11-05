package com.qiao.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.account.domain.AppUser;
import com.qiao.account.service.UserService;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.notify.service.MailTemplate;
import com.qiao.notify.service.Notifier;
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.TeammateProfile;
import com.qiao.team.domain.Team;
import com.qiao.team.domain.TeamMapper;
import com.qiao.team.domain.TeamMember;
import com.qiao.team.domain.TeamMemberMapper;
import com.qiao.team.domain.TeamMemberStatus;
import com.qiao.team.domain.TeamRole;
import com.qiao.team.domain.TeamStatus;
import com.qiao.team.web.InvitationView;
import com.qiao.team.web.TeamRequest;
import com.qiao.team.web.TeamView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teams of approved engineers (spec §12). A team only groups individual contractors: it applies and delivers
 * together, but every member keeps their own profile, consents, agreement and payout.
 */
@Service
public class TeamService {

    public static final int MAX_MEMBERS = 10;
    public static final int MIN_MEMBERS_TO_APPLY = 2;

    private static final Set<TeamMemberStatus> CURRENT = EnumSet.of(TeamMemberStatus.ACTIVE, TeamMemberStatus.INVITED);

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final EngineerProfileService profileService;
    private final UserService userService;
    private final List<TeamWorkDirectory> workDirectories;
    private final Notifier notifier;
    private final AuditService auditService;
    private final Clock clock;

    public TeamService(TeamMapper teamMapper, TeamMemberMapper memberMapper, EngineerProfileService profileService,
                       UserService userService, List<TeamWorkDirectory> workDirectories, Notifier notifier,
                       AuditService auditService, Clock clock) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.profileService = profileService;
        this.userService = userService;
        this.workDirectories = workDirectories;
        this.notifier = notifier;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ engineer

    @Transactional
    public TeamView create(long userId, TeamRequest request) {
        profileService.requireApproved(userId);
        Team team = new Team();
        team.setName(request.name().trim());
        team.setDescription(Texts.trimToNull(request.description()));
        team.setLeadUserId(userId);
        team.setStatus(TeamStatus.ACTIVE);
        teamMapper.insert(team);
        TeamMember lead = new TeamMember();
        lead.setTeamId(team.getId());
        lead.setUserId(userId);
        lead.setRole(TeamRole.LEAD);
        lead.setStatus(TeamMemberStatus.ACTIVE);
        lead.setRespondedAt(clock.instant());
        memberMapper.insert(lead);
        auditService.log(Actor.user(userId), AuditAction.TEAM_CREATED, "team", team.getId());
        return view(team, userId);
    }

    /**
     * ACTIVE teams the engineer belongs to (pending invitations are listed by {@link #invitations}).
     */
    public List<TeamView> listForEngineer(long userId) {
        List<Long> teamIds = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, TeamMemberStatus.ACTIVE))
                .stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .in(Team::getId, teamIds)
                        .eq(Team::getStatus, TeamStatus.ACTIVE)
                        .orderByDesc(Team::getId))
                .stream().map(t -> view(t, userId)).toList();
    }

    /**
     * Visible to active members and to invitees of an ACTIVE team.
     */
    public TeamView get(long userId, long teamId) {
        Team team = teamMapper.selectById(teamId);
        TeamMember membership = team == null ? null : membership(teamId, userId);
        if (membership == null || !CURRENT.contains(membership.getStatus()) || team.getStatus() != TeamStatus.ACTIVE) {
            throw ApiException.notFound("Team");
        }
        return view(team, userId);
    }

    @Transactional
    public TeamView update(long userId, long teamId, TeamRequest request) {
        Team team = lockAsLead(userId, teamId);
        team.setName(request.name().trim());
        team.setDescription(Texts.trimToNull(request.description()));
        teamMapper.updateById(team);
        return view(team, userId);
    }

    @Transactional
    public TeamView invite(long userId, long teamId, String alias) {
        Team team = lockAsLead(userId, teamId);
        EngineerProfile invitee = profileService.findApprovedByAlias(alias)
                .filter(p -> userService.isActive(p.getUserId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ENGINEER_NOT_FOUND));
        TeamMember existing = membership(teamId, invitee.getUserId());
        if (existing != null && CURRENT.contains(existing.getStatus())) {
            throw new ApiException(ErrorCode.ALREADY_TEAM_MEMBER);
        }
        if (currentMembers(teamId).size() >= MAX_MEMBERS) {
            throw new ApiException(ErrorCode.TEAM_FULL);
        }
        TeamMember member = existing != null ? existing : new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(invitee.getUserId());
        member.setRole(TeamRole.MEMBER);
        member.setStatus(TeamMemberStatus.INVITED);
        member.setInvitedByUserId(userId);
        member.setRespondedAt(null);
        if (existing == null) {
            memberMapper.insert(member);
        } else {
            memberMapper.updateById(member);
        }
        auditService.log(Actor.user(userId), AuditAction.TEAM_MEMBER_INVITED, "team", teamId,
                Map.of("alias", invitee.getAlias()));
        AppUser user = userService.get(invitee.getUserId());
        notifier.send(user.getEmail(), user.getLocale(), MailTemplate.TEAM_INVITATION, team.getName(),
                profileService.aliases(List.of(userId)).get(userId));
        return view(team, userId);
    }

    public List<InvitationView> invitations(long userId) {
        List<TeamMember> invited = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, TeamMemberStatus.INVITED)
                .orderByDesc(TeamMember::getUpdatedAt));
        if (invited.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> teams = teamMapper.selectByIds(invited.stream().map(TeamMember::getTeamId).toList()).stream()
                .filter(t -> t.getStatus() == TeamStatus.ACTIVE)
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        return invited.stream().filter(m -> teams.containsKey(m.getTeamId())).map(m -> {
            Team team = teams.get(m.getTeamId());
            List<TeamMember> active = activeMembers(team.getId());
            Map<Long, String> aliases = profileService.aliases(active.stream().map(TeamMember::getUserId).toList());
            return new InvitationView(team.getId(), team.getName(), team.getDescription(),
                    aliases.get(team.getLeadUserId()),
                    active.stream().map(a -> aliases.get(a.getUserId())).toList(), m.getUpdatedAt());
        }).toList();
    }

    @Transactional
    public TeamView accept(long userId, long teamId) {
        Team team = lockActive(teamId);
        TeamMember member = requireInvitation(teamId, userId);
        profileService.requireApproved(userId);
        if (activeMembers(teamId).size() >= MAX_MEMBERS) {
            throw new ApiException(ErrorCode.TEAM_FULL);
        }
        member.setStatus(TeamMemberStatus.ACTIVE);
        member.setRespondedAt(clock.instant());
        memberMapper.updateById(member);
        auditService.log(Actor.user(userId), AuditAction.TEAM_MEMBER_JOINED, "team", teamId);
        return view(team, userId);
    }

    @Transactional
    public void decline(long userId, long teamId) {
        lockActive(teamId);
        TeamMember member = requireInvitation(teamId, userId);
        member.setStatus(TeamMemberStatus.DECLINED);
        member.setRespondedAt(clock.instant());
        memberMapper.updateById(member);
    }

    /**
     * The lead removes an active member or cancels an invitation.
     */
    @Transactional
    public TeamView remove(long userId, long teamId, long memberId) {
        Team team = lockAsLead(userId, teamId);
        TeamMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getTeamId().equals(teamId) || !CURRENT.contains(member.getStatus())) {
            throw ApiException.notFound("Team member");
        }
        if (member.getRole() == TeamRole.LEAD) {
            throw ApiException.invalidState("The lead cannot be removed; dissolve the team instead");
        }
        if (member.getStatus() == TeamMemberStatus.ACTIVE) {
            requireNoActiveWork(teamId, member.getUserId());
        }
        member.setStatus(TeamMemberStatus.REMOVED);
        member.setRespondedAt(clock.instant());
        memberMapper.updateById(member);
        auditService.log(Actor.user(userId), AuditAction.TEAM_MEMBER_LEFT, "team", teamId,
                Map.of("memberUserId", member.getUserId(), "by", "LEAD"));
        return view(team, userId);
    }

    @Transactional
    public void leave(long userId, long teamId) {
        lockActive(teamId);
        TeamMember member = membership(teamId, userId);
        if (member == null || member.getStatus() != TeamMemberStatus.ACTIVE) {
            throw ApiException.notFound("Team");
        }
        if (member.getRole() == TeamRole.LEAD) {
            throw ApiException.invalidState("The lead cannot leave; dissolve the team instead");
        }
        requireNoActiveWork(teamId, userId);
        member.setStatus(TeamMemberStatus.LEFT);
        member.setRespondedAt(clock.instant());
        memberMapper.updateById(member);
        auditService.log(Actor.user(userId), AuditAction.TEAM_MEMBER_LEFT, "team", teamId, Map.of("by", "MEMBER"));
    }

    @Transactional
    public void dissolve(long userId, long teamId) {
        Team team = lockAsLead(userId, teamId);
        requireNoActiveWork(teamId, null);
        dissolve(team);
        auditService.log(Actor.user(userId), AuditAction.TEAM_DISSOLVED, "team", teamId);
    }

    // ------------------------------------------------------------------ other modules

    /**
     * Locks an ACTIVE team led by the caller and returns its active members (lead first).
     *
     * @throws ApiException {@code NOT_FOUND} unless the caller is an active member; {@code TEAM_LEAD_ONLY} for members
     */
    public ActiveTeam lockLedTeam(long userId, long teamId) {
        Team team = lockAsLead(userId, teamId);
        List<TeamMember> members = activeMembers(teamId).stream()
                .sorted(Comparator.comparing((TeamMember m) -> m.getRole() != TeamRole.LEAD).thenComparing(TeamMember::getId))
                .toList();
        return new ActiveTeam(team, members);
    }

    public Map<Long, Team> byIds(Collection<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }
        return teamMapper.selectByIds(teamIds).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    // ------------------------------------------------------------------ personal data

    /**
     * The user's own memberships (no data about teammates).
     */
    List<Map<String, Object>> exportFor(long userId) {
        List<TeamMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId).orderByAsc(TeamMember::getId));
        Map<Long, Team> teams = byIds(memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet()));
        return memberships.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            Team team = teams.get(m.getTeamId());
            row.put("teamId", m.getTeamId());
            row.put("teamName", team == null ? null : team.getName());
            row.put("role", m.getRole());
            row.put("status", m.getStatus());
            row.put("invitedAt", m.getCreatedAt());
            row.put("respondedAt", m.getRespondedAt());
            return row;
        }).toList();
    }

    /**
     * On account deletion: teams led by the user are dissolved and the user leaves every other team.
     */
    void anonymize(long userId) {
        teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .eq(Team::getLeadUserId, userId)
                        .eq(Team::getStatus, TeamStatus.ACTIVE))
                .forEach(this::dissolve);
        memberMapper.update(null, new LambdaUpdateWrapper<TeamMember>()
                .set(TeamMember::getStatus, TeamMemberStatus.LEFT)
                .set(TeamMember::getRespondedAt, clock.instant())
                .set(TeamMember::getUpdatedAt, clock.instant())
                .eq(TeamMember::getUserId, userId)
                .in(TeamMember::getStatus, CURRENT));
    }

    // ------------------------------------------------------------------ internals

    private void dissolve(Team team) {
        team.setStatus(TeamStatus.DISSOLVED);
        teamMapper.updateById(team);
        memberMapper.update(null, new LambdaUpdateWrapper<TeamMember>()
                .set(TeamMember::getStatus, TeamMemberStatus.LEFT)
                .set(TeamMember::getRespondedAt, clock.instant())
                .set(TeamMember::getUpdatedAt, clock.instant())
                .eq(TeamMember::getTeamId, team.getId())
                .ne(TeamMember::getRole, TeamRole.LEAD)
                .in(TeamMember::getStatus, CURRENT));
    }

    private void requireNoActiveWork(long teamId, Long engineerUserId) {
        if (workDirectories.stream().anyMatch(d -> d.hasActiveWork(teamId, engineerUserId))) {
            throw new ApiException(ErrorCode.TEAM_HAS_ACTIVE_WORK);
        }
    }

    private Team lockActive(long teamId) {
        Team team = teamMapper.selectByIdForUpdate(teamId);
        if (team == null || team.getStatus() != TeamStatus.ACTIVE) {
            throw ApiException.notFound("Team");
        }
        return team;
    }

    private Team lockAsLead(long userId, long teamId) {
        Team team = lockActive(teamId);
        TeamMember membership = membership(teamId, userId);
        if (membership == null || membership.getStatus() != TeamMemberStatus.ACTIVE) {
            throw ApiException.notFound("Team");
        }
        if (membership.getRole() != TeamRole.LEAD || team.getLeadUserId() != userId) {
            throw new ApiException(ErrorCode.TEAM_LEAD_ONLY);
        }
        return team;
    }

    private TeamMember requireInvitation(long teamId, long userId) {
        TeamMember member = membership(teamId, userId);
        if (member == null || member.getStatus() != TeamMemberStatus.INVITED) {
            throw ApiException.notFound("Invitation");
        }
        return member;
    }

    private TeamMember membership(long teamId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    private List<TeamMember> activeMembers(long teamId) {
        return memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, TeamMemberStatus.ACTIVE)
                .orderByAsc(TeamMember::getId));
    }

    private List<TeamMember> currentMembers(long teamId) {
        return memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .in(TeamMember::getStatus, CURRENT)
                .orderByAsc(TeamMember::getId));
    }

    private TeamView view(Team team, long viewerUserId) {
        List<TeamMember> rows = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId())
                .orderByAsc(TeamMember::getId));
        TeamMember mine = rows.stream().filter(r -> r.getUserId() == viewerUserId).findFirst().orElse(null);
        boolean viewerIsLead = mine != null && mine.getRole() == TeamRole.LEAD;
        List<TeamMember> shown = rows.stream()
                .filter(r -> CURRENT.contains(r.getStatus()) || (viewerIsLead && r.getStatus() == TeamMemberStatus.DECLINED))
                .sorted(Comparator.comparing((TeamMember m) -> m.getRole() != TeamRole.LEAD)
                        .thenComparing(m -> m.getStatus() != TeamMemberStatus.ACTIVE)
                        .thenComparing(TeamMember::getId))
                .toList();
        Map<Long, EngineerProfile> profiles = profileService.byUserIds(shown.stream().map(TeamMember::getUserId).toList());
        List<TeamView.Member> members = shown.stream().map(m -> {
            EngineerProfile p = profiles.get(m.getUserId());
            TeammateProfile tp = p == null ? new TeammateProfile(null, null, List.of()) : TeammateProfile.from(p);
            return new TeamView.Member(m.getId(), tp.alias(), tp.headline(), tp.skills(), m.getRole(), m.getStatus());
        }).toList();
        int active = (int) rows.stream().filter(r -> r.getStatus() == TeamMemberStatus.ACTIVE).count();
        Map<Long, String> leadAlias = new LinkedHashMap<>(profileService.aliases(List.of(team.getLeadUserId())));
        boolean canApply = viewerIsLead && team.getStatus() == TeamStatus.ACTIVE && active >= MIN_MEMBERS_TO_APPLY;
        return new TeamView(team.getId(), team.getName(), team.getDescription(), team.getStatus(),
                leadAlias.get(team.getLeadUserId()), mine == null ? null : mine.getRole(),
                mine == null ? null : mine.getStatus(), members, active, canApply, team.getCreatedAt());
    }
}
