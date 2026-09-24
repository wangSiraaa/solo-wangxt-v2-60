# 信号检修封锁窗口许可服务（本地模拟演练）

> ⚠️ **仅用于本地模拟演练**：本服务不连接、不控制任何真实铁路信号/联锁/调度设备。
> 所有“调度回执”均由本地模拟器生成（编号前缀 `SIM-RCP-`，载荷以 `[SIMULATION]` 开头），
> 不代表任何真实行车许可。

Spring Boot 3.3 + PostgreSQL + Flyway 的封锁窗口许可服务，面向信号检修计划员演练以下流程：

- 计划可占用**多个相邻区段**（创建时校验相邻连通性）；
- **保护条件**（逐区段）与**具备资格的人员到岗**全部到位才能开工；
- 窗口重叠**不一定冲突**：冲突只按**互斥作业矩阵**（时间重叠 ∧ 共同区段 ∧ 矩阵 `exclusive=true`）逐对裁决，
  矩阵明确允许时可并行——禁止只比设备编号；
- 人员资质必须覆盖到**预计结束时刻**；在预计结束前失效的计划必须 `/reschedule` 重新安排；
- 销记前逐项确认 `SITE_REVIEW`（现场复核）与 `PERSONNEL_WITHDRAWAL`（人员撤离）；
- 计划销记后，**迟到的开工消息**一律拒绝（`PLAN_ALREADY_CLOSED`），不得重新开工；
- 开工/销记支持 `idempotencyKey` 幂等重试：成功结果原样回放（`replayed=true`），不重复发回执。

## 生命周期

```
DRAFT ──/schedule──▶ SCHEDULED ──/start──▶ ACTIVE ──/closeout──▶ CLOSED
                        │                     ▲
                        └──资质失效等──/reschedule
                                           （原计划 → RESCHEDULED，接替计划 SCHEDULED，
                                            保护/到岗需重新确认）
```

## 运行

需要 JDK 17、PostgreSQL（建库后由 Flyway 自动建表并写入演练种子）。

```bash
createdb rail_window_sim
DB_HOST=localhost DB_PORT=5432 DB_NAME=rail_window_sim DB_USER=rail DB_PASSWORD=rail \
  mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs（离线快照见 [`docs/openapi.json`](docs/openapi.json)）
- 模拟回执样例：[`docs/sample-dispatch-receipt.json`](docs/sample-dispatch-receipt.json)
- 数据库迁移：`src/main/resources/db/migration/V1__initial_schema.sql`、`V2__seed_demo_data.sql`

## 时间（可注入时钟）

生产使用 `Clock.systemUTC()`（`ClockConfig`）。测试用 `MutableClock` Bean 覆盖，可固定/推进到任意时刻，
用于跨午夜窗口、迟到消息等场景；所有时间均为带时区的绝对时刻（ISO-8601，UTC），
因此“跨午夜”只是 `windowEnd` 落在次日，比较逻辑不受墙钟字面值影响。

## 主要 API

| 动作 | 方法与路径 |
| --- | --- |
| 创建计划（多区段） | `POST /api/plans` |
| 排定窗口 | `POST /api/plans/{id}/schedule` |
| 重新安排 | `POST /api/plans/{id}/reschedule` |
| 区段保护确认（逐项） | `POST /api/plans/{id}/protections` |
| 人员到岗 | `POST /api/plans/{id}/arrivals` |
| 开工前预检 | `GET /api/plans/{id}/preconditions` |
| 开工（支持幂等重试） | `POST /api/plans/{id}/start` |
| 销记清单项确认 | `POST /api/plans/{id}/closeout-items/{SITE_REVIEW|PERSONNEL_WITHDRAWAL}` |
| 销记（支持幂等重试） | `POST /api/plans/{id}/closeout` |
| 互斥作业矩阵维护 | `POST/GET /api/reference/mutex-rules` |
| 人员 / 资质 / 区段 | `/api/reference/persons`、`/qualifications`、`/sections` |

## 失败响应：逐项说明缺失条件

开工/销记被拒绝时返回 `422`，`violations` 逐项列出缺失条件（代码 + 说明 + 相关引用），
不使用笼统的“审批失败”：

```json
{
  "error": "CONDITIONS_NOT_MET",
  "message": "存在未确认保护到位的区段 等 2 项条件不满足",
  "violations": [
    { "code": "PROTECTION_NOT_READY", "message": "以下区段尚未确认封锁保护到位：[S01]",
      "refs": { "sectionCodes": ["S01"] } },
    { "code": "MUTEX_CONFLICT",
      "message": "与计划 PL-000012（作业[SIGNAL_REPAIR]）在区段[S01]冲突：互斥矩阵规定 ... exclusive=true",
      "refs": { "otherPlanNo": "PL-000012", "sharedSections": ["S01"] } }
  ]
}
```

条件代码见 `ConditionCode`：`WINDOW_NOT_OPEN` / `WINDOW_EXPIRED` / `PROTECTION_NOT_READY` /
`PERSONNEL_NOT_ARRIVED` / `QUALIFICATION_MISSING` / `QUALIFICATION_EXPIRES_BEFORE_FINISH` /
`MUTEX_CONFLICT` / `PLAN_ALREADY_CLOSED` / `PLAN_SUPERSEDED` / `CLOSEOUT_ITEMS_PENDING` 等。

## 互斥作业矩阵（种子）

| 作业 A | 作业 B | exclusive | 含义 |
| --- | --- | --- | --- |
| SIGNAL_REPAIR | SIGNAL_REPAIR | true | 同类检修同区段禁止并行 |
| CATENARY | SIGNAL_REPAIR | true | 接触网与信号检修互斥 |
| LINE_INSPECTION | SIGNAL_REPAIR | false | 巡检可与信号检修并行 |
| CATENARY | LINE_INSPECTION | false | 巡检可与接触网检修并行 |

矩阵中没有的作业对按**保守原则**拒绝，并提示先登记矩阵条目；可以通过
`POST /api/reference/mutex-rules` 扩展。

## 测试

```bash
mvn test
```

测试使用 Zonky embedded-postgres（随测试进程启动的本地 PostgreSQL，无需 Docker/root）。
覆盖场景：

- `CrossMidnightWindowIntegrationTest`：跨午夜窗口开工；窗口结束/销记后迟到开工消息被拒且不复活；
- `MutexMatrixIntegrationTest`：两个计划竞争同一区段（矩阵互斥 → 后开工者 422；
  矩阵允许 → 重叠同区段并行）；
- `StartRetryIdempotencyIntegrationTest`：开工确认重试三次只产生一份回执；失败请求不写幂等记录，补齐条件后同键可成功；
- `QualificationExpiryRescheduleIntegrationTest`：资质在预计结束前失效被拒并重新安排，原计划作废、接替计划重新确认后开工；
- `CloseoutAndConditionsIntegrationTest`：销记清单逐项确认、缺失条件逐项返回、多区段相邻性；
- `OpenApiSmokeIntegrationTest`：OpenAPI 文档生成并刷新 `docs/openapi.json`。

## 演练脚本

仓库提供端到端 cURL 脚本（需先启动服务并准备好数据库）：

```bash
scripts/demo-walkthrough.sh
```
