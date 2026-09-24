-- 信号检修封锁窗口许可服务（本地模拟演练）
-- 仅用于模拟数据库，不连接或控制任何真实铁路设备。

CREATE TABLE section (
    code         VARCHAR(32) PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    adjacent_to  VARCHAR(32) REFERENCES section(code)
);

CREATE TABLE person (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL
);

CREATE TABLE qualification (
    id          BIGSERIAL PRIMARY KEY,
    person_id   BIGINT NOT NULL REFERENCES person(id),
    work_type   VARCHAR(48) NOT NULL,
    valid_from  TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_qual_window CHECK (valid_from < valid_until)
);
CREATE INDEX idx_qualification_person ON qualification(person_id);

-- 互斥作业矩阵：冲突判断的唯一权威依据（规范化为 a < b）
CREATE TABLE mutex_rule (
    work_type_a VARCHAR(48) NOT NULL,
    work_type_b VARCHAR(48) NOT NULL,
    exclusive   BOOLEAN NOT NULL,
    remark      VARCHAR(200),
    CONSTRAINT pk_mutex_rule PRIMARY KEY (work_type_a, work_type_b),
    CONSTRAINT chk_mutex_order CHECK (work_type_a <= work_type_b)
);

CREATE TABLE work_plan (
    id                BIGSERIAL PRIMARY KEY,
    plan_no           VARCHAR(32) NOT NULL UNIQUE,
    title             VARCHAR(200) NOT NULL,
    work_type         VARCHAR(48) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    window_start      TIMESTAMPTZ NOT NULL,
    window_end        TIMESTAMPTZ NOT NULL,
    planned_finish    TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    related_plan_id   BIGINT,
    start_receipt_no  VARCHAR(40),
    version           BIGINT,
    CONSTRAINT chk_plan_window CHECK (window_start < window_end
                                      AND planned_finish BETWEEN window_start AND window_end)
);
CREATE INDEX idx_work_plan_window ON work_plan(window_start, window_end);
CREATE INDEX idx_work_plan_status ON work_plan(status);

CREATE TABLE plan_section (
    plan_id              BIGINT NOT NULL REFERENCES work_plan(id) ON DELETE CASCADE,
    position             INT NOT NULL,
    section_code         VARCHAR(32) NOT NULL REFERENCES section(code),
    protection_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    protected_by_person_id BIGINT REFERENCES person(id),
    CONSTRAINT pk_plan_section PRIMARY KEY (plan_id, position)
);
CREATE INDEX idx_plan_section_code ON plan_section(section_code);

CREATE TABLE plan_assignee (
    plan_id   BIGINT NOT NULL REFERENCES work_plan(id) ON DELETE CASCADE,
    position  INT NOT NULL,
    person_id BIGINT NOT NULL REFERENCES person(id),
    arrived   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_plan_assignee PRIMARY KEY (plan_id, position)
);
CREATE INDEX idx_plan_assignee_person ON plan_assignee(person_id);

-- 销记前逐项确认清单
CREATE TABLE closeout_item (
    id                    BIGSERIAL PRIMARY KEY,
    plan_id               BIGINT NOT NULL REFERENCES work_plan(id) ON DELETE CASCADE,
    item_key              VARCHAR(32) NOT NULL,
    label                 VARCHAR(128) NOT NULL,
    confirmed             BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by_person_id BIGINT REFERENCES person(id),
    confirmed_at          TIMESTAMPTZ,
    CONSTRAINT uq_closeout_item UNIQUE (plan_id, item_key)
);

-- 确认链留痕
CREATE TABLE confirmation_record (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL REFERENCES work_plan(id) ON DELETE CASCADE,
    kind            VARCHAR(24) NOT NULL,
    ref_key         VARCHAR(64),
    person_id       BIGINT REFERENCES person(id),
    message         VARCHAR(256),
    created_at      TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(80)
);
CREATE INDEX idx_confirmation_plan ON confirmation_record(plan_id, id);

-- 模拟调度回执
CREATE TABLE dispatch_receipt (
    id         BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(40) NOT NULL UNIQUE,
    plan_id    BIGINT NOT NULL REFERENCES work_plan(id) ON DELETE CASCADE,
    kind       VARCHAR(16) NOT NULL,
    status     VARCHAR(24) NOT NULL,
    payload    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_receipt_plan ON dispatch_receipt(plan_id, id);

-- 幂等记录（开工/销记确认重试安全）
CREATE TABLE idempotency_record (
    operation_key VARCHAR(120) PRIMARY KEY,
    plan_id       BIGINT,
    response_json VARCHAR(4000) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);
