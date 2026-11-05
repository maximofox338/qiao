-- Qiao phase 1 schema. All timestamps are DATETIME(3) in UTC.

-- ---------------------------------------------------------------- account
CREATE TABLE app_user (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    locale           VARCHAR(10)  NOT NULL,
    otp_locked_until DATETIME(3)  NULL,
    last_login_at    DATETIME(3)  NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE otp_code (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(254) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    purpose    VARCHAR(16)  NOT NULL,
    expires_at DATETIME(3)  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    used_at    DATETIME(3)  NULL,
    request_ip VARCHAR(45)  NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_otp_code_email (email, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jwt_denylist (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    jti        VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_jwt_denylist_jti (jti),
    KEY idx_jwt_denylist_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- admin
CREATE TABLE admin_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    roles           VARCHAR(128) NOT NULL,
    totp_secret     VARCHAR(64)  NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME(3)  NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_admin_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- compliance
CREATE TABLE consent (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    version    VARCHAR(20)  NOT NULL,
    granted    BOOLEAN      NOT NULL,
    scope_ref  VARCHAR(64)  NULL,
    ip         VARCHAR(45)  NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_consent_user (user_id, type, scope_ref, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transfer_event (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year             SMALLINT    NOT NULL,
    engineer_user_id BIGINT      NOT NULL,
    company_id       BIGINT      NOT NULL,
    data_kind        VARCHAR(32) NOT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_transfer_event_year_engineer (year, engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_type  VARCHAR(16)  NOT NULL,
    actor_id    BIGINT       NULL,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(40)  NULL,
    entity_id   VARCHAR(64)  NULL,
    detail      JSON         NULL,
    ip          VARCHAR(45)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_log_action (action, id),
    KEY idx_audit_log_actor (actor_type, actor_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- profile
CREATE TABLE engineer_profile (
    id                          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT        NOT NULL,
    alias                       VARCHAR(16)   NOT NULL,
    headline                    VARCHAR(160)  NULL,
    bio                         TEXT          NULL,
    skills                      JSON          NULL,
    years_experience            INT           NULL,
    region                      VARCHAR(64)   NULL,
    english_level               VARCHAR(16)   NULL,
    spanish_level               VARCHAR(16)   NULL,
    hourly_rate_usd             DECIMAL(10, 2) NULL,
    availability_hours_week     INT           NULL,
    cv_text                     TEXT          NULL,
    education                   TEXT          NULL,
    portfolio_links             JSON          NULL,
    declaration_no_restrictions BOOLEAN       NOT NULL DEFAULT FALSE,
    declaration_at              DATETIME(3)   NULL,
    status                      VARCHAR(20)   NOT NULL,
    review_note                 VARCHAR(1000) NULL,
    partner_ref                 VARCHAR(64)   NULL,
    rating_avg                  DECIMAL(3, 2) NULL,
    rating_count                INT           NOT NULL DEFAULT 0,
    created_at                  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_engineer_profile_user (user_id),
    UNIQUE KEY uk_engineer_profile_alias (alias),
    KEY idx_engineer_profile_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- client
CREATE TABLE client_company (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(200)  NULL,
    country              CHAR(2)       NULL,
    website              VARCHAR(255)  NULL,
    size                 VARCHAR(20)   NULL,
    status               VARCHAR(20)   NOT NULL,
    sanctions_status     VARCHAR(16)   NOT NULL,
    sanctions_checked_at DATETIME(3)   NULL,
    declarations_version VARCHAR(20)   NULL,
    declarations_hash    CHAR(64)      NULL,
    declarations_at      DATETIME(3)   NULL,
    review_note          VARCHAR(1000) NULL,
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_client_company_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE client_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_client_member_user (user_id),
    KEY idx_client_member_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- project
CREATE TABLE category (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL,
    name_en    VARCHAR(120) NOT NULL,
    name_es    VARCHAR(120) NOT NULL,
    name_zh    VARCHAR(120) NOT NULL,
    allowed    BOOLEAN      NOT NULL,
    sort       INT          NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_category_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project (
    id            BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT         NOT NULL,
    title         VARCHAR(200)   NOT NULL,
    description   TEXT           NOT NULL,
    category_code VARCHAR(40)    NOT NULL,
    skills        JSON           NULL,
    budget_usd    DECIMAL(12, 2) NOT NULL,
    start_date    DATE           NOT NULL,
    end_date      DATE           NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    flags         JSON           NULL,
    declarations  JSON           NULL,
    review_note   VARCHAR(1000)  NULL,
    reviewed_by   BIGINT         NULL,
    reviewed_at   DATETIME(3)    NULL,
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_project_company (company_id),
    KEY idx_project_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project_milestone (
    id          BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT         NOT NULL,
    seq         INT            NOT NULL,
    title       VARCHAR(200)   NOT NULL,
    deliverable TEXT           NOT NULL,
    due_date    DATE           NOT NULL,
    amount_usd  DECIMAL(12, 2) NOT NULL,
    created_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_project_milestone_seq (project_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE application (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT         NOT NULL,
    engineer_user_id BIGINT         NOT NULL,
    cover_note       TEXT           NULL,
    prices           JSON           NOT NULL,
    talent_total_usd DECIMAL(12, 2) NOT NULL,
    client_total_usd DECIMAL(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_application_project_engineer (project_id, engineer_user_id),
    KEY idx_application_engineer (engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- contract
CREATE TABLE contract (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT         NOT NULL,
    application_id   BIGINT         NOT NULL,
    company_id       BIGINT         NOT NULL,
    engineer_user_id BIGINT         NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    fee_pct          DECIMAL(5, 2)  NOT NULL,
    talent_total_usd DECIMAL(12, 2) NOT NULL,
    client_total_usd DECIMAL(12, 2) NOT NULL,
    activated_at     DATETIME(3)    NULL,
    completed_at     DATETIME(3)    NULL,
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_contract_application (application_id),
    KEY idx_contract_company (company_id),
    KEY idx_contract_engineer (engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE contract_document (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id         BIGINT      NOT NULL,
    kind                VARCHAR(32) NOT NULL,
    template_version    VARCHAR(20) NOT NULL,
    locale              VARCHAR(10) NOT NULL,
    body                MEDIUMTEXT  NOT NULL,
    sha256              CHAR(64)    NOT NULL,
    accepted_by_user_id BIGINT      NULL,
    accepted_at         DATETIME(3) NULL,
    accepted_ip         VARCHAR(45) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_contract_document_kind (contract_id, kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE contract_milestone (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id       BIGINT         NOT NULL,
    seq               INT            NOT NULL,
    title             VARCHAR(200)   NOT NULL,
    deliverable       TEXT           NOT NULL,
    due_date          DATE           NOT NULL,
    talent_amount_usd DECIMAL(12, 2) NOT NULL,
    client_amount_usd DECIMAL(12, 2) NOT NULL,
    status            VARCHAR(24)    NOT NULL,
    submitted_note    TEXT           NULL,
    submitted_at      DATETIME(3)    NULL,
    accepted_at       DATETIME(3)    NULL,
    revision_reason   TEXT           NULL,
    dispute_reason    TEXT           NULL,
    settled_line_id   BIGINT         NULL,
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_contract_milestone_seq (contract_id, seq),
    KEY idx_contract_milestone_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- delivery
CREATE TABLE message (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id    BIGINT      NOT NULL,
    sender_user_id BIGINT      NOT NULL,
    body           TEXT        NOT NULL,
    flagged        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_message_contract (contract_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE attachment (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id      BIGINT       NOT NULL,
    milestone_id     BIGINT       NULL,
    uploader_user_id BIGINT       NOT NULL,
    filename         VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(255) NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_attachment_contract (contract_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- billing
CREATE TABLE payment (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_milestone_id BIGINT         NOT NULL,
    provider              VARCHAR(16)    NOT NULL,
    provider_ref          VARCHAR(255)   NULL,
    amount_usd            DECIMAL(12, 2) NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    paid_at               DATETIME(3)    NULL,
    created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_payment_milestone (contract_milestone_id),
    KEY idx_payment_provider_ref (provider_ref)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE settlement_batch (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    period              CHAR(7)     NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_by_admin_id BIGINT      NOT NULL,
    exported_at         DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_settlement_batch_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE settlement_line (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id              BIGINT         NOT NULL,
    contract_milestone_id BIGINT         NOT NULL,
    engineer_user_id      BIGINT         NOT NULL,
    partner_ref           VARCHAR(64)    NULL,
    alias                 VARCHAR(16)    NOT NULL,
    talent_amount_usd     DECIMAL(12, 2) NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    partner_payment_ref   VARCHAR(128)   NULL,
    iit_withheld_cny      DECIMAL(12, 2) NULL,
    net_paid_cny          DECIMAL(12, 2) NULL,
    paid_at               DATETIME(3)    NULL,
    created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_settlement_line_milestone (contract_milestone_id),
    KEY idx_settlement_line_batch (batch_id),
    KEY idx_settlement_line_engineer (engineer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- notify
CREATE TABLE mail_outbox (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    to_email   VARCHAR(254) NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_mail_outbox_to (to_email, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- reviews (P1, no endpoints yet)
CREATE TABLE review (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id  BIGINT        NOT NULL,
    from_user_id BIGINT        NOT NULL,
    to_user_id   BIGINT        NOT NULL,
    stars        TINYINT       NOT NULL,
    comment      VARCHAR(2000) NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_contract_from (contract_id, from_user_id),
    CONSTRAINT ck_review_stars CHECK (stars BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- seed: categories (spec §9)
INSERT INTO category (code, name_en, name_es, name_zh, allowed, sort) VALUES
    ('web-dev',           'Web development',                              'Desarrollo web',                                   '网站开发',               TRUE,  10),
    ('mobile-dev',        'Mobile apps',                                  'Apps móviles',                                     '移动应用开发',           TRUE,  20),
    ('backend-api',       'Backend & APIs',                               'Backend y APIs',                                   '后端与接口',             TRUE,  30),
    ('data-bi',           'Data analysis & BI',                           'Análisis de datos y BI',                           '数据分析与BI',           TRUE,  40),
    ('ml-eng',            'Machine learning engineering (non-sensitive)', 'Ingeniería de ML (no sensible)',                   '机器学习工程（非敏感）', TRUE,  50),
    ('qa',                'QA & testing',                                 'QA y pruebas',                                     '软件测试',               TRUE,  60),
    ('devops',            'DevOps & cloud',                               'DevOps y nube',                                    '运维与云',               TRUE,  70),
    ('ui-ux',             'UI/UX design',                                 'Diseño UI/UX',                                     'UI/UX设计',              TRUE,  80),
    ('embedded-consumer', 'Embedded firmware (consumer products)',        'Firmware embebido (consumo)',                      '嵌入式固件（消费电子）', TRUE,  90),
    ('cad-mech',          'Mechanical CAD',                               'CAD mecánico',                                     '机械CAD',                TRUE,  100),
    ('pcb-consumer',      'PCB design (consumer)',                        'Diseño de PCB (consumo)',                          'PCB设计（消费电子）',    TRUE,  110),
    ('tech-writing',      'Technical writing & localization',             'Redacción técnica y localización',                 '技术写作与本地化',       TRUE,  120),
    ('crypto',            'Cryptocurrency & token platforms',             'Criptomonedas y plataformas de tokens',            '加密货币与代币平台',     FALSE, 900),
    ('semiconductor',     'Semiconductor & chip design',                  'Semiconductores y diseño de chips',                '半导体与芯片设计',       FALSE, 910),
    ('drones',            'Drones & UAV',                                 'Drones y UAV',                                     '无人机',                 FALSE, 920),
    ('geodata',           'Mapping, surveying & geodata',                 'Cartografía, topografía y geodatos',               '测绘与地理信息',         FALSE, 930),
    ('defense',           'Defense & military',                           'Defensa y militar',                                '国防与军事',             FALSE, 940),
    ('gov-data',          'Government or state-owned data',               'Datos gubernamentales o de empresas estatales',    '政府或国有企业数据',     FALSE, 950),
    ('expert-network',    'Expert consulting / research assistant / data collection', 'Consultoría de expertos / asistente de investigación / recopilación de datos', '专家咨询/研究助理/数据采集', FALSE, 960),
    ('critical-minerals', 'Rare earths, lithium & battery technology',    'Tierras raras, litio y tecnología de baterías',    '稀土、锂与电池技术',     FALSE, 970),
    ('biometric-genomic', 'Biometric or genomic data',                    'Datos biométricos o genómicos',                    '生物识别或基因数据',     FALSE, 980);
