cd /c/オープン/openclaw
reqs=(
'第1話: 世界の状況と、生き残ったレンが目を覚まして女性たちに次々と見つかる導入。田舎でスローライフを夢見るが即座に奪い合いが始まる。お約束の「嫌ですか？」「嫌じゃないです！」を入れる。'
'第2話: レンが田舎へ逃げ出し、ルナとヒナのいる田舎町でつかの間のスローライフ。しかしクリエに居場所を突き止められ、ユメ・シズク・マドカ・キララも押しかけて誘惑合戦。最後にネオ・エデンのリリスが襲来。'
'第3話: リリスの魅了とセツナたちアイギスの異能バトル。混乱の中でレンをめぐる誘惑はさらに過激に。戦いの後、ハーレム同居生活が始まってしまう引きで終える。'
)
for i in 0 1 2; do
  ok=0
  tries=0
  while [ $tries -lt 2 ]; do
    tries=$((tries+1))
    if [ $i -eq 0 ] && [ $tries -eq 1 ] && [ -n "$FIRST_JOB" ]; then job=$FIRST_JOB; else
    params=$(node -e "console.log(JSON.stringify({kind:'comic',comicSeriesId:'comic-a725f9e6',comicTask:'episode',prompt:process.argv[1],imageModel:'wai',fast:true}))" "${reqs[$i]}")
    job=$(node dist/index.js gateway call media.generate --json --timeout 60000 --params "$params" 2>/dev/null | grep -o '"jobId": "[a-f0-9]*"' | cut -d'"' -f4); fi
    echo "$(date +%H:%M) 第$((i+1))話 開始 job=$job (試行$tries)"
    last=""
    while true; do
      o=$(node dist/index.js gateway call media.job.get --json --timeout 15000 --params "{\"jobId\":\"$job\"}" 2>/dev/null)
      s=$(echo "$o" | grep -o '"status": "[a-z]*"' | head -1 | cut -d'"' -f4)
      g=$(echo "$o" | grep -o '"stage": "[^"]*"' | head -1 | cut -d'"' -f4 | sed -E 's/（?[0-9]+\/[0-9]+）?$//')
      if [ "$g" != "$last" ] && [ -n "$g" ]; then echo "$(date +%H:%M) 第$((i+1))話 $g"; last="$g"; fi
      [ "$s" = "done" ] && { echo "$(date +%H:%M) 第$((i+1))話 完成"; ok=1; break 2; }
      [ "$s" = "error" ] && { echo "$(date +%H:%M) 第$((i+1))話 失敗: $(echo "$o" | grep -o '"error": "[^"]*"' | head -1)"; break; }
      sleep 60
    done
  done
  [ $ok -eq 1 ] || { echo "STOPPED 第$((i+1))話が2回失敗したので中止"; exit 1; }
done
echo ALL_EPISODES_DONE
