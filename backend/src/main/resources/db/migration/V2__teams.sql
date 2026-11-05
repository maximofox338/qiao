-- Teams (spec §12): engineers apply and deliver together, each one remaining an individual contractor.

-- ---------------------------------------------------------------- team
CREATE TABLE team (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(80)  NOT NULL,
    description  VARCHAR(500) NULL,
    lead_user_id BIGINT       NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_team_lead (lead_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE team_member (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    team_id            BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    role               VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    invited_by_user_id BIGINT      NULL,
    responded_at       DATETIME(3) NULL,
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_team_member (team_id, user_id),
    KEY idx_team_member_user (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- project: team applications
ALTER TABLE application
    ADD COLUMN team_id BIGINT NULL AFTER engineer_user_id,
    ADD KEY idx_application_team (team_id);

-- Snapshot of the members of a team application and their split. One application per engineer per project.
CREATE TABLE application_member (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id   BIGINT        NOT NULL,
    project_id       BIGINT        NOT NULL,
    engineer_user_id BIGINT        NOT NULL,
    role             VARCHAR(16)   NOT NULL,
    share_pct        DECIMAL(5, 2) NOT NULL,
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_application_member_project_engineer (project_id, engineer_user_id),
    KEY idx_application_member_application (application_id),
    KEY idx_application_member_engineer (engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- contract: team contracts
ALTER TABLE contract
    ADD COLUMN team_id   BIGINT      NULL AFTER engineer_user_id,
    ADD COLUMN team_name VARCHAR(80) NULL AFTER team_id;

CREATE TABLE contract_member (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id      BIGINT        NOT NULL,
    engineer_user_id BIGINT        NOT NULL,
    role             VARCHAR(16)   NOT NULL,
    share_pct        DECIMAL(5, 2) NOT NULL,
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_contract_member (contract_id, engineer_user_id),
    KEY idx_contract_member_engineer (engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One ENGINEER_DELIVERABLES document per member: the owner is stored on the document.
ALTER TABLE contract_document
    ADD COLUMN engineer_user_id BIGINT NULL AFTER kind;

UPDATE contract_document d
    JOIN contract c ON c.id = d.contract_id
SET d.engineer_user_id = c.engineer_user_id
WHERE d.kind = 'ENGINEER_DELIVERABLES';

ALTER TABLE contract_document
    DROP INDEX uk_contract_document_kind,
    ADD UNIQUE KEY uk_contract_document_owner (contract_id, kind, (IFNULL(engineer_user_id, 0)));

-- ---------------------------------------------------------------- billing: one settlement line per member
ALTER TABLE settlement_line
    DROP INDEX uk_settlement_line_milestone,
    ADD UNIQUE KEY uk_settlement_line_milestone_engineer (contract_milestone_id, engineer_user_id);
