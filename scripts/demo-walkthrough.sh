#!/usr/bin/env bash
# 端到端演练脚本（本地模拟），窗口时间相对当前系统时钟动态生成：
#   预检（逐项缺失条件）-> 保护/到岗 -> 开工 -> 重试回放
#   -> 第二个计划竞争同区段被矩阵拒绝 -> 销记清单逐项确认 -> 销记 -> 迟到开工被拒
#
# 用法: scripts/demo-walkthrough.sh [BASE_URL]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
J() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }
# iso <秒偏移>：输出相对当前的 UTC ISO 时刻
iso() { python3 -c "import datetime,sys;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=int(sys.argv[1]))).strftime('%Y-%m-%dT%H:%M:%SZ'))" "$1"; }

W_START=$(iso 10)     # 窗口 10 秒后开始
W_END=$(iso 14400)    # 4 小时后结束（跨不跨午夜取决于实际开始时刻，逻辑一致）
FINISH=$(iso 13800)
P2_START=$(iso 20)
P2_END=$(iso 18000)
P2_FINISH=$(iso 17400)

echo "== 1) 创建计划（占用 S02，指派人员 1、3）窗口 $W_START ~ $W_END"
PLAN_JSON=$(curl -s -X POST "$BASE/api/plans" -H 'Content-Type: application/json' -d "{
  \"title\":\"演练-封锁窗口\",\"workType\":\"SIGNAL_REPAIR\",
  \"windowStart\":\"$W_START\",\"windowEnd\":\"$W_END\",\"plannedFinish\":\"$FINISH\",
  \"sectionCodes\":[\"S02\"],\"personIds\":[1,3]}")
ID=$(printf '%s' "$PLAN_JSON" | J "d['id']")
echo "planId=$ID"

echo "== 2) 排定窗口（模拟调度受理回执 SIM-RCP-）"
curl -s -X POST "$BASE/api/plans/$ID/schedule" -H 'Content-Type: application/json' -d "{
  \"windowStart\":\"$W_START\",\"windowEnd\":\"$W_END\",\"plannedFinish\":\"$FINISH\"}" \
  | J "d['receipts'][0]['receiptNo']"

echo "== 3) 预检：逐项列出当前缺失条件（不是统一的审批失败）"
curl -s "$BASE/api/plans/$ID/preconditions" | J "[v['code'] for v in d['violations']]"

echo "== 4) 逐项确认区段保护与人员到岗"
curl -s -X POST "$BASE/api/plans/$ID/protections" -H 'Content-Type: application/json' \
  -d '{"sectionCode":"S02","personId":2,"note":"防护信号牌已设置"}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID/arrivals" -H 'Content-Type: application/json' -d '{"personId":1}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID/arrivals" -H 'Content-Type: application/json' -d '{"personId":3}' >/dev/null

echo "== 5) 等待窗口开放（12 秒）"
sleep 12

echo "== 6) 开工（带幂等键）"
curl -s -X POST "$BASE/api/plans/$ID/start" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3,"idempotencyKey":"demo-start"}' \
  | J "(d['replayed'], d['receipt']['receiptNo'])"

echo "== 7) 同样的开工消息重试：回放 replayed=True，回执编号不变"
curl -s -X POST "$BASE/api/plans/$ID/start" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3,"idempotencyKey":"demo-start"}' \
  | J "(d['replayed'], d['receipt']['receiptNo'])"

echo "== 8) 第二个计划竞争 S02（同类 SIGNAL_REPAIR，矩阵 exclusive=true）：排定允许，开工被逐项拒绝"
ID2=$(curl -s -X POST "$BASE/api/plans" -H 'Content-Type: application/json' -d "{
  \"title\":\"演练-竞争者\",\"workType\":\"SIGNAL_REPAIR\",
  \"windowStart\":\"$P2_START\",\"windowEnd\":\"$P2_END\",\"plannedFinish\":\"$P2_FINISH\",
  \"sectionCodes\":[\"S02\"],\"personIds\":[1,3]}" | J "d['id']")
curl -s -X POST "$BASE/api/plans/$ID2/schedule" -H 'Content-Type: application/json' -d "{
  \"windowStart\":\"$P2_START\",\"windowEnd\":\"$P2_END\",\"plannedFinish\":\"$P2_FINISH\"}" >/dev/null
curl -s -X POST "$BASE/api/plans/$ID2/protections" -H 'Content-Type: application/json' \
  -d '{"sectionCode":"S02","personId":2}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID2/arrivals" -H 'Content-Type: application/json' -d '{"personId":1}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID2/arrivals" -H 'Content-Type: application/json' -d '{"personId":3}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID2/start" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3}' \
  | J "[(v['code'], v['refs'].get('sharedSections')) for v in d['violations'] if v['code']=='MUTEX_CONFLICT']"

echo "== 9) 销记前未逐项确认 -> 422，列出待办项"
curl -s -X POST "$BASE/api/plans/$ID/closeout" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3}' | J "[v['refs']['pendingItems'] for v in d['violations']]"

echo "== 10) 逐项确认现场复核与人员撤离，然后销记"
curl -s -X POST "$BASE/api/plans/$ID/closeout-items/SITE_REVIEW" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID/closeout-items/PERSONNEL_WITHDRAWAL" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3}' >/dev/null
curl -s -X POST "$BASE/api/plans/$ID/closeout" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3,"idempotencyKey":"demo-close"}' \
  | J "(d['plan']['status'], d['receipt']['kind'])"

echo "== 11) 迟到的开工消息：计划已销记 -> PLAN_ALREADY_CLOSED，不重新开工"
curl -s -X POST "$BASE/api/plans/$ID/start" -H 'Content-Type: application/json' \
  -d '{"confirmedByPersonId":3}' | J "[v['code'] for v in d['violations']]"

echo "完成。OpenAPI 文档：$BASE/swagger-ui.html"
