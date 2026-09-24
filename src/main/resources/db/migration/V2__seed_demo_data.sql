-- 演练种子数据（模拟）
-- 区段：S01-S02-S03 相邻；S10 与 S01 不相邻，用于多区段相邻性校验演练
INSERT INTO section(code, name, adjacent_to) VALUES
    ('S01', '1号道岔区段', 'S02'),
    ('S02', '2号信号机区段', 'S03'),
    ('S03', '3号股道区段', NULL),
    ('S10', '独立试验区段', NULL);

INSERT INTO person(id, name, role_name) VALUES
    (1, '张工', '信号工'),
    (2, '李防护', '防护员'),
    (3, '王负责', '现场负责人');
SELECT setval(pg_get_serial_sequence('person', 'id'), 3);

-- 资质（work_type 使用大写编码）
INSERT INTO qualification(person_id, work_type, valid_from, valid_until) VALUES
    (1, 'SIGNAL_REPAIR', TIMESTAMPTZ '2026-01-01 00:00:00Z', TIMESTAMPTZ '2027-01-01 00:00:00Z'),
    (2, 'TRACK_PROTECTION', TIMESTAMPTZ '2026-01-01 00:00:00Z', TIMESTAMPTZ '2027-01-01 00:00:00Z'),
    (3, 'SIGNAL_REPAIR', TIMESTAMPTZ '2026-01-01 00:00:00Z', TIMESTAMPTZ '2026-09-25 23:00:00Z');

-- 互斥作业矩阵（存储时 work_type_a <= work_type_b）：
--  信号检修与接触网检修互斥；信号检修与线路巡检可并行；同类信号检修互斥；
--  线路巡检与接触网检修可并行。
INSERT INTO mutex_rule(work_type_a, work_type_b, exclusive, remark) VALUES
    ('SIGNAL_REPAIR', 'SIGNAL_REPAIR', TRUE,  '同类检修不得在同一区段并行'),
    ('CATENARY', 'SIGNAL_REPAIR', TRUE,  '信号与接触网检修需分别封锁，禁止并行'),
    ('LINE_INSPECTION', 'SIGNAL_REPAIR', FALSE, '巡检与信号检修可并行（矩阵明确允许）'),
    ('CATENARY', 'LINE_INSPECTION',     FALSE, '巡检与接触网检修可并行（矩阵明确允许）');
