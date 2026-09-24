#!/usr/bin/env bash
# 本地演练演示脚本：需要服务已在 localhost:8080 运行（mvn spring-boot:run）
# 内容：编制跨午夜窗口 → 派工 → 逐项确认保护 → 演示一次 422 逐项失败 → 开工（含重试）
#       → 销记复核/撤离 → 销记 → 迟到开工消息被拒
set -u
BASE=${BASE:-http://localhost:8080}
P=BW-DEMO-$RANDOM
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print(json.dumps(d,ensure_ascii=False,indent=2))" 2>/dev/null || cat; }

echo "== 1. 编制跨午夜窗口（S02+S03 相邻）$P"
curl -s -X POST "$BASE/api/plans" -H 'Content-Type: application/json' -d "{
  \"planNo\":\"$P\",\"title\":\"跨午夜信号更换演练\",\"workType\":\"SIGNAL_REPLACEMENT\",
  \"sectionCodes\":[\"S02\",\"S03\"],
  \"plannedStart\":\"2026-09-30T22:30:00Z\",\"plannedEnd\":\"2026-10-01T01:30:00Z\",
  \"zoneId\":\"ZONE-A\"}" | j

echo "== 2. 派工（四岗，使用种子人员）"
for r in "B1001 WORK_LEADER" "B1002 SAFETY_OFFICER" "B1003 CONTACT" "B1004 PROTECTOR"; do
  set -- $r
  curl -s -X POST "$BASE/api/plans/$P/assignments" -H 'Content-Type: application/json' \
    -d "{\"badge\":\"$1\",\"roleOnPlan\":\"$2\"}" | j
done

echo "== 3. 先试开工（保护未确认）→ 期望 HTTP 422 + 逐项 missingConditions"
curl -s -i -X POST "$BASE/api/plans/$P/start" -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"demo-start","operatorBadge":"B1001"}' | head -1
curl -s -X POST "$BASE/api/plans/$P/start" -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"demo-start","operatorBadge":"B1001"}' | j

echo "== 4. 拉取保护项并逐项确认"
ids=$(curl -s "$BASE/api/plans/$P" | python3 -c "import sys,json;[print(x['id']) for x in json.load(sys.stdin)['plan']['protections']]")
for id in $ids; do
  curl -s -X POST "$BASE/api/plans/protections/$id/confirm" -H 'Content-Type: application/json' \
    -d '{"confirmedBy":"B1004"}' >/dev/null
done
echo "confirmed $(echo "$ids" | wc -w) protection items"

echo "== 5. 开工 + 同键重试（第二次 replayed=true）"
curl -s -X POST "$BASE/api/plans/$P/start" -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"demo-start","operatorBadge":"B1001"}' | j
curl -s -X POST "$BASE/api/plans/$P/start" -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"demo-start","operatorBadge":"B1001"}' | j

echo "== 6. 销记前：逐项 REVIEW 复核 + EVACUATION 撤离"
curl -s "$BASE/api/plans/$P" | python3 -c "
import sys,json,urllib.request
base='$BASE'; plan='$P'
d=json.load(sys.stdin)['plan']
for x in d['protections']:
    body=json.dumps({'checkType':'REVIEW',
        'refKey':x['sectionCode']+'/'+x['requirementCode'],
        'sectionCode':x['sectionCode'],'label':x['label']+'撤除复核',
        'confirmedBy':'B1002','confirmed':True}).encode()
    urllib.request.urlopen(urllib.request.Request(base+'/api/plans/'+plan+'/release-checks',
        data=body,headers={'Content-Type':'application/json'},method='POST')).read()
for b in ['B1001','B1002','B1003','B1004']:
    body=json.dumps({'checkType':'EVACUATION','refKey':b,'label':b+'已撤离',
        'confirmedBy':'B1002','confirmed':True}).encode()
    urllib.request.urlopen(urllib.request.Request(base+'/api/plans/'+plan+'/release-checks',
        data=body,headers={'Content-Type':'application/json'},method='POST')).read()
print('release checks submitted')
"

echo "== 7. 销记"
curl -s -X POST "$BASE/api/plans/$P/release" -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"demo-release","operatorBadge":"B1001"}' | j

echo "== 8. 迟到开工消息：accepted=false，planStatus 保持 RELEASED"
curl -s -X POST "$BASE/api/plans/$P/late-start?messageId=LATE-001&operatorBadge=B1001" | j

echo "== 9. 审计记录"
curl -s "$BASE/api/plans/$P/confirmations" | j
