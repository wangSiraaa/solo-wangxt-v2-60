-- 本地演练种子数据（仅模拟，不对应真实站场）

-- 区段：S01-S04 依次相邻（相邻关系双向存储）
INSERT INTO rail_section (code, name, adjacent_to) VALUES
 ('S01', '一场道岔区模拟区段', ARRAY['S02']),
 ('S02', '进站信号机外方模拟区段', ARRAY['S01','S03']),
 ('S03', '区间通过信号机模拟区段', ARRAY['S02','S04']),
 ('S04', '二场咽喉模拟区段', ARRAY['S03']);

-- 人员（资质有效期为演练用虚构数据）
INSERT INTO personnel (badge, name, role, qualification, qualified_work, qual_valid_from, qual_valid_until) VALUES
 ('B1001', '张伟', 'WORK_LEADER', '信号/线路检修作业负责人资格',
   ARRAY['TRACK_WORK','SIGNAL_REPLACEMENT','CABLE_TEST','ROUTINE_INSPECTION'],
   TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2028-01-01 00:00:00+00'),
 ('B1002', '李娜', 'SAFETY_OFFICER', '安全员资格',
   ARRAY['TRACK_WORK','SIGNAL_REPLACEMENT','CABLE_TEST','ROUTINE_INSPECTION','OVERHEAD_LINE_WORK'],
   TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2028-01-01 00:00:00+00'),
 ('B1003', '王强', 'CONTACT', '驻站联络员资格',
   ARRAY['TRACK_WORK','SIGNAL_REPLACEMENT','CABLE_TEST','ROUTINE_INSPECTION','OVERHEAD_LINE_WORK'],
   TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2028-01-01 00:00:00+00'),
 ('B1004', '赵磊', 'PROTECTOR', '现场防护员资格',
   ARRAY['TRACK_WORK','SIGNAL_REPLACEMENT','CABLE_TEST','ROUTINE_INSPECTION','OVERHEAD_LINE_WORK'],
   TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2028-01-01 00:00:00+00'),
 ('B1005', '孙芳', 'WORK_LEADER', '信号检修作业负责人资格（即将到期，用于资质失效演练）',
   ARRAY['SIGNAL_REPLACEMENT','CABLE_TEST'],
   TIMESTAMPTZ '2025-06-01 00:00:00+00', TIMESTAMPTZ '2026-12-31 00:00:00+00');

-- 互斥作业矩阵（仅存储一个方向，查询时双向比较）
-- 含义：两个未销记计划在【时间重叠】且【共用区段】时，若命中矩阵任一条才构成冲突。
-- 矩阵中没有的组合（如 SIGNAL_REPLACEMENT 与 CABLE_TEST）即使区段相同、时间重叠也允许并行。
INSERT INTO mutex_rule (work_type_a, work_type_b, note) VALUES
 ('TRACK_WORK', 'TRACK_WORK', '线路作业自身互斥：同一区段同一时间只允许一处线路作业'),
 ('OVERHEAD_LINE_WORK', 'OVERHEAD_LINE_WORK', '接触网作业自身互斥'),
 ('TRACK_WORK', 'OVERHEAD_LINE_WORK', '线路与接触网作业垂直交叉，禁止同时进行'),
 ('TRACK_WORK', 'SIGNAL_REPLACEMENT', '线路作业与信号更换机具侵界冲突'),
 ('SIGNAL_REPLACEMENT', 'OVERHEAD_LINE_WORK', '信号更换与接触网作业车作业冲突');
-- CABLE_TEST / ROUTINE_INSPECTION 与以上各作业均不互斥（矩阵空白即视为兼容）。
