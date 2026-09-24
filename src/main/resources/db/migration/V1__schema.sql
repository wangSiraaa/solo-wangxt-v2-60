-- 本地模拟演练库结构：信号检修封锁窗口许可服务
-- 仅用于培训演练，不连接任何真实信号/行车设备。

CREATE TABLE rail_section (
    code         TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    adjacent_to  TEXT[] NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE rail_section IS '模拟区段字典（含相邻关系，仅演练数据）';

CREATE TABLE personnel (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    badge              TEXT NOT NULL UNIQUE,
    name               TEXT NOT NULL,
    role               TEXT NOT NULL,
    qualification      TEXT NOT NULL,
    qualified_work     TEXT[] NOT NULL DEFAULT '{}',
    qual_valid_from    TIMESTAMPTZ NOT NULL,
    qual_valid_until   TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE personnel IS '模拟人员及其资质有效期';

CREATE TABLE block_window (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_no            TEXT NOT NULL UNIQUE,
    title              TEXT NOT NULL,
    work_type          TEXT NOT NULL,
    status             TEXT NOT NULL DEFAULT 'DRAFT',
    section_codes      TEXT[] NOT NULL,
    planned_start      TIMESTAMPTZ NOT NULL,
    planned_end        TIMESTAMPTZ NOT NULL,
    zone_id            TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ,
    released_at        TIMESTAMPTZ,
    start_idem_key     TEXT UNIQUE,
    release_idem_key   TEXT UNIQUE,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_block_window_status CHECK (status IN ('DRAFT','IN_PROGRESS','RELEASED')),
    CONSTRAINT chk_block_window_range  CHECK (planned_end > planned_start)
);
COMMENT ON TABLE block_window IS '封锁窗口计划（状态机 DRAFT->IN_PROGRESS->RELEASED）';

-- PostgreSQL 范围类型 + GIST，供重叠候选查询使用；是否真的冲突仍由互斥作业矩阵在应用层判定
ALTER TABLE block_window
    ADD COLUMN window_range tstzrange
    GENERATED ALWAYS AS (tstzrange(planned_start, planned_end, '[)')) STORED;
CREATE INDEX idx_block_window_range_gist ON block_window USING GIST (window_range);
CREATE INDEX idx_block_window_status ON block_window (status);

CREATE TABLE plan_assignment (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_window_id  BIGINT NOT NULL REFERENCES block_window(id) ON DELETE CASCADE,
    personnel_id     BIGINT NOT NULL REFERENCES personnel(id),
    role_on_plan     TEXT NOT NULL,
    assigned_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (block_window_id, personnel_id)
);

CREATE TABLE protection_requirement (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_window_id  BIGINT NOT NULL REFERENCES block_window(id) ON DELETE CASCADE,
    section_code     TEXT NOT NULL,
    requirement_code TEXT NOT NULL,
    label            TEXT NOT NULL,
    confirmed        BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by     TEXT,
    confirmed_at     TIMESTAMPTZ,
    UNIQUE (block_window_id, section_code, requirement_code)
);
COMMENT ON TABLE protection_requirement IS '区段保护条件逐项确认（红旗/停车信号/断表示等，均为模拟）';

CREATE TABLE mutex_rule (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    work_type_a      TEXT NOT NULL,
    work_type_b      TEXT NOT NULL,
    note             TEXT,
    UNIQUE (work_type_a, work_type_b)
);
COMMENT ON TABLE mutex_rule IS '互斥作业矩阵（有向存储，应用层双向查询）';

CREATE TABLE dispatch_receipt (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_no       TEXT NOT NULL UNIQUE,
    block_window_id  BIGINT NOT NULL REFERENCES block_window(id),
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatch_status  TEXT NOT NULL,
    display_summary  TEXT NOT NULL,
    simulated        BOOLEAN NOT NULL DEFAULT TRUE,
    note             TEXT NOT NULL
);

CREATE TABLE confirmation_record (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_window_id  BIGINT NOT NULL REFERENCES block_window(id) ON DELETE CASCADE,
    record_type      TEXT NOT NULL,
    idem_key         TEXT,
    item_key         TEXT,
    section_code     TEXT,
    payload          JSONB,
    accepted         BOOLEAN NOT NULL DEFAULT TRUE,
    reject_reason    TEXT,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE confirmation_record IS '开工确认/重试/迟到消息/销记项的审计记录';
CREATE INDEX idx_confirmation_window ON confirmation_record(block_window_id, record_type);

CREATE SEQUENCE dispatch_receipt_no_seq START 1;
