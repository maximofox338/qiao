package com.qiao.team.service;

/**
 * Tells the team module whether a team (or one of its members) is committed to active work. Implemented by the
 * modules that own applications and contracts, using only their own tables (no bean cycle with TeamService).
 */
public interface TeamWorkDirectory {

    /**
     * @param engineerUserId a member, or {@code null} for any member of the team
     * @return whether the team (member) is in an application still open or a contract not yet finished
     */
    boolean hasActiveWork(long teamId, Long engineerUserId);
}
