# 封锁窗口许可服务（本地模拟演练）

> **安全声明**：本服务仅供信号检修计划员在本地进行流程演练。它**不连接、不驱动任何真实铁路
> 信号 / 联锁 / 供电 / 行车设备**，所有“调度回执”均为本地生成的模拟文本，**禁止用于真实行车组织**。

## 业务规则

1. **多相邻区段**：一个封锁窗口可占用多个区段，但区段必须在相邻关系表中逐对连通，不能用任意编号拼凑。
2. **冲突按互斥作业矩阵判定**：冲突 = 时间重叠 **且** 占用区段逐区段求交 **且** 作业类型命中 `mutex_rule` 矩阵。
   - 矩阵空白即兼容（如 `CABLE_TEST` 与 `ROUTINE_INSPECTION` 同区段同时间也允许并行）。
   - **禁止只比设备编号**；时间重叠本身也不构成冲突。
   - 编制计划时只做矩阵预检（不阻断），开工闸门只与已在作业（`IN_PROGRESS`）的计划竞争；
     并发开工通过 `SELECT … FOR UPDATE` 行锁串行化，先开工者取得区段。
3. **人员资质**：四个必需岗位（作业负责人 `WORK_LEADER`、安全员 `SAFETY_OFFICER`、
   驻站联络员 `CONTACT`、现场防护员 `PROTECTOR`）各 1 人；资质必须覆盖作业类型，
   且在窗口**预计结束时间**前不得失效，否则派工/开工被拒并提示重新安排。
4. **开工**：岗位到齐、保护条件逐项确认、无矩阵冲突、当前时间在计划窗内，才受理并出具模拟调度回执；
   支持幂等键，确认重试重放首次结果（不产生第二次开工）。
5. **销记**：先逐项提交复核（保护撤除 `REVIEW`）与逐人撤离确认（`EVACUATION`），齐备后才销记；
   销记同样支持幂等。
6. **迟到消息**：已销记（`RELEASED`）计划收到迟到开工消息时，只登记拒绝留痕，状态**绝不**变回作业中。
7. **失败语义**：开工/销记前置条件不满足返回 HTTP 422，`missingConditions` 数组逐项给出
   条件码、中文说明和上下文（区段/证件号/冲突计划号），**不使用统一“审批失败”**。

## 技术栈

- Spring Boot 3.3（Web / Data JPA / Validation）、Flyway、PostgreSQL、springdoc-openapi
- 时间统一 UTC `Instant`；`java.time.Clock` 以 Bean 注入，测试可固定/拨钟，跨午夜窗口无特殊分支
- 数据库：`text[]` 存区段、`tstzrange` 生成列 + GIST 索引支撑重叠候选查询、`@Version` 乐观锁 + 开工行级悲观锁
- 测试：JUnit5 + Mockito 单元测试；嵌入式 PostgreSQL 16（zonky，无需 Docker/外部数据库）集成测试

## 运行

需要 JDK 17、一个 PostgreSQL（≥ 14）。

```bash
createdb blockwindow_drill
psql -d blockwindow_drill -c "CREATE USER blockwindow PASSWORD 'blockwindow';"  # 或复用超级用户
DB_URL=jdbc:postgresql://localhost:5432/blockwindow_drill \
DB_USERNAME=blockwindow DB_PASSWORD=blockwindow \
./mvnw spring-boot:run
```

Flyway 启动时自动执行 `src/main/resources/db/migration/`：

| 文件 | 内容 |
| --- | --- |
| `V1__schema.sql` | 区段、人员资质、窗口（含 tstzrange/GIST）、派工、保护条件、互斥矩阵、模拟回执、确认审计表 |
| `V2__seed_drill_data.sql` | 4 个相邻模拟区段、5 名演练人员（含 1 名即将到期）、5 条互斥矩阵 |
| `V3__seed_concurrent_drill_personnel.sql` | 并发竞争演练用两套四岗人员 |

- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 代码生成 OpenAPI：`GET /v3/api-docs`
- 手写留档 OpenAPI：`GET /openapi.yaml`（仓库文件 `src/main/resources/openapi/block-window-permit-openapi.yaml`）

## 测试

```bash
mvn test          # 25 个用例：18 单元 + 7 嵌入式 PostgreSQL 集成
```

重点场景：

- `CrossMidnight`：22:30→次日 01:30 跨午夜全流程（派工/保护/开工/复核/撤离/销记）
- `CompetingPlans` / `concurrentCompetingPlansSerializeOnRowLock`：两计划竞争同一区段
  （顺序与真实并发各一）；另有“矩阵空白允许并行”“区段不相交不冲突”对照
- `StartRetry`：开工确认同键重放、补齐条件后重试成功
- `QualificationExpiry`：预计结束前失效 → 拒绝并要求重新安排
- `ReleasePreconditions`：缺复核/缺撤离逐项列出
- `LateMessages`：迟到开工消息不能重开已销记计划

## 快速演练（HTTP）

见 `scripts/drill-demo.sh`（curl 脚本，覆盖一次完整流程与 422 逐项失败）。
