import { termsOf } from "./partner-knowledge.js";

// The map of the app for the concierge (the mini character): what each screen is, where it is, and
// how to use it. A question is matched against these entries; the best few go into the prompt, so
// the answer stays grounded in what the app really has. `go` is what the app can open:
//   "route:<SettingsRoute name>" opens a settings screen, "deep:<id>" a feature inside one
//   (see ShellDeepLink), "tab:<Tab name>" a main tab.

export type GuideEntry = {
  title: string;
  where: string;
  what: string;
  keywords: string;
  go?: string;
};

export const APP_GUIDE: GuideEntry[] = [
  {
    title: "チャット（パートナーと話す）",
    where: "下のタブの「チャット」",
    what: "パートナー（AIキャラ）と話す画面。アバターを触ると反応し、好感度・高まり・気分の帯が出る。声での読み上げ、資料棚の参照、画像・動画などの生成の依頼もここから（クリエに頼むと生成できる）。",
    keywords: "チャット 会話 話す パートナー アバター 好感度 触る",
    go: "tab:Chat",
  },
  {
    title: "パートナー管理",
    where: "設定 > 🤖 パートナー > パートナー管理",
    what: "パートナーを増やす・直す・消す。キャラ図鑑の設定が、会話に反映される。",
    keywords: "パートナー 管理 作る 追加 キャラ 設定 編集",
    go: "route:PartnerManagement",
  },
  {
    title: "キャラ図鑑（キャラを創る）",
    where: "設定 > 🎮 たのしむ > 連載コミック & キャラ図鑑",
    what: "キャラを作って登録する。写真や作品名から創る、複数の写真を混ぜる、設定画・専用学習・アバター・ミニキャラ・3Dモデルを作る。作品・キャラ名で検索ボタンもある。",
    keywords: "キャラ図鑑 キャラ 創る 作る 登録 設定画 学習 アバター 検索 AniList",
    go: "deep:comic:library",
  },
  {
    title: "連載コミック",
    where: "設定 > 🎮 たのしむ > 連載コミック & キャラ図鑑 の作品",
    what: "キャラで連載の漫画を作る。話ごとに続きを作れる。",
    keywords: "連載 コミック 漫画 作品 話",
    go: "deep:comic:works",
  },
  {
    title: "生成スタジオ（画像）",
    where: "設定 > 🎮 たのしむ > 生成スタジオ > 画像",
    what: "文章から絵を作る。モデル選択、速いモード、項目を足す（🧩）。できた絵は高画質化（✨）や動画化ができる。",
    keywords: "画像 絵 生成 イラスト モデル 高画質 拡大 SeedVR2",
    go: "deep:media:image",
  },
  {
    title: "生成スタジオ（動画）",
    where: "設定 > 🎮 たのしむ > 生成スタジオ > 動画",
    what: "文章や1枚の絵から動画を作る。エンジンを選ぶ（標準・長い動画・音付きなど）。",
    keywords: "動画 ビデオ 生成 動かす",
    go: "deep:media:video",
  },
  {
    title: "モーションコミック",
    where: "設定 > 🎮 たのしむ > 生成スタジオ > モーション",
    what: "成人向けの濡れ場を声つきの動画にする。キャラ・体位・オプション・セリフの量・声の種類・効果音・カットの流れ・前作からの引継ぎを決められる。最初に確認用の絵を1枚作って止まる。完成した動画をつなぐ・途中から別の体位へ切り替えることもできる。",
    keywords: "モーション コミック 濡れ場 体位 結合 つなぐ 引継ぎ セリフ 声 効果音 流れ 確認",
    go: "deep:media:motion",
  },
  {
    title: "動画をつなぐ（結合）",
    where: "生成スタジオ > モーション の中の「完成した動画をつなぐ」",
    what: "完成した動画を2本以上選んで、間をAIが解析して移動や抜き差しの動きを作って1本にする。つなぎは2〜60秒。結果をさらにつなげるので、15分以上にもできる。",
    keywords: "つなぐ 結合 繋ぐ 長編 15分 つなぎ 間 移動",
    go: "deep:media:motion",
  },
  {
    title: "音声ドラマ（声）",
    where: "設定 > 生成スタジオ > 声 > 音声ドラマにする",
    what: "お題から長い台本を書いて声にし、雨・夜・部屋・お風呂・風の背景音を重ねる。行ごとに [囁き] [喘ぎ:強] のような演技を付ける。",
    keywords: "音声 ドラマ ASMR 声 台本 囁き 背景",
    go: "deep:media:voice",
  },
  {
    title: "声を作る",
    where: "設定 > 🤖 パートナー > 声を作る",
    what: "声のライブラリ・声デザイン・声まね（録音から）。パートナーやキャラに声を割り当てる。",
    keywords: "声 作る ボイス 声まね クローン 録音 デザイン",
    go: "route:VoiceStudio",
  },
  {
    title: "生成：音楽",
    where: "設定 > 生成スタジオ > 音楽",
    what: "歌あり・曲だけの音楽を作る。",
    keywords: "音楽 曲 歌 BGM",
    go: "deep:media:music",
  },
  {
    title: "生成：漫画",
    where: "設定 > 生成スタジオ > 漫画",
    what: "お題から脚本・コマ・吹き出しの漫画を作る。4コマと1ページ。",
    keywords: "漫画 マンガ 4コマ",
    go: "deep:media:manga",
  },
  {
    title: "顔入れ替え",
    where: "設定 > 🎮 たのしむ > 顔入れ替え",
    what: "動画や画像の顔を、キャラの顔に入れ替える。",
    keywords: "顔 入れ替え フェイススワップ",
    go: "route:FaceSwap",
  },
  {
    title: "📚 項目カタログ",
    where: "設定の一番上 > 📚 カタログ > 項目カタログ",
    what: "髪・服・表情・場所・歯・拘束・吊り・埋めなど約1万3千の項目を分類ごとに見る。見本の絵を拡大できる。",
    keywords: "カタログ 項目 髪 服 表情 歯 拘束 吊る 埋める 一覧",
    go: "route:ItemCatalog",
  },
  {
    title: "📚 モーション・体位カタログ",
    where: "設定の一番上 > 📚 カタログ > モーション・体位カタログ",
    what: "体位とオプション（愛液など）を見る。自分で足す・消すこともでき、クリエに相談しても足せる。",
    keywords: "カタログ モーション 体位 オプション 愛液 追加",
    go: "route:MotionPoseCatalog",
  },
  {
    title: "サンプル画像製造",
    where: "設定 > 🎮 たのしむ > サンプル画像製造",
    what: "項目や体位の見本の絵をクリエで作る。作った見本は、項目の一覧に小さく出る。",
    keywords: "サンプル 見本 製造 項目 体位",
    go: "route:SampleFactory",
  },
  {
    title: "資料棚（PDF・Web）",
    where: "設定 > 🤖 パートナー > 資料棚",
    what: "PDFやWebページを資料としてパートナー別に入れる。会話で関係する部分が自動で参照される（意味検索つき）。クリエに頼むと、ネットで調べて入れてくれる。",
    keywords: "資料 棚 PDF Web 調べる 知識 参照",
    go: "route:PartnerKnowledge",
  },
  {
    title: "グループチャット",
    where: "設定 > 🤖 パートナー > グループチャット",
    what: "複数のキャラで会話する。キャラとモデルをアイコンで選ぶ。",
    keywords: "グループ 複数 チャット キャラ",
    go: "route:GroupChat",
  },
  {
    title: "アバター",
    where: "設定 > 🤖 パートナー > アバターカスタマイズ",
    what: "チャットで動くアバターを選ぶ・作る。Live2D や 3D（VRM/glb）も使える。",
    keywords: "アバター Live2D VRM 3D 表情",
    go: "route:AvatarCustomize",
  },
  {
    title: "ミニキャラ（コンシェルジュ）",
    where: "画面の中を歩いているキャラ",
    what: "アプリの使い方・設定の場所・相談・移動を手伝う。タップで話しかける。長押しで好きな場所に動かせる。歩く・止まるを切り替えられ、キャラも変えられる。",
    keywords: "ミニキャラ コンシェルジュ サポート ヘルプ 案内 歩く",
    go: "",
  },
  {
    title: "好感度・気分・触り方",
    where: "チャット画面のアバターの上の帯をタップ",
    what: "好感度・高まり・気分を見られる。好きな場所・弱い場所・嫌な場所と、触り方のプリセット（再生できる）を決められる。",
    keywords: "好感度 気分 高まり 触り方 弱い 好き プリセット",
    go: "tab:Chat",
  },
  {
    title: "バックアップと復元",
    where: "設定 > 🔗 接続 > バックアップ & 復元",
    what: "スマホとPCの全データ（設定・キャッシュ・データ）をzipにして、日付つきで保存する。復元もできる。",
    keywords: "バックアップ 復元 保存 zip 引っ越し",
    go: "route:BackupRestore",
  },
  {
    title: "モデルの追加（モデルストア）",
    where: "生成スタジオの画像のモデル選びから",
    what: "画像モデルを追加でダウンロードする。",
    keywords: "モデル 追加 ダウンロード ストア Civitai",
    go: "route:MediaStudio",
  },
  {
    title: "クリエ開発機能",
    where: "設定 > 🤖 パートナー > 🛠 クリエ開発機能",
    what: "入にすると、クリエがこのプロジェクトの改良や新機能を自分で作る。インターネット検索とGitHubの読み込み・改造もできる。作業は本体のコピーで行い、確認して「適用」を押すまで本体は変わらない。",
    keywords: "開発 クリエ 機能 追加 改良 GitHub 作る",
    go: "route:CrieDev",
  },
  {
    title: "禁止語句管理",
    where: "設定 > 🛡️ 禁止語句管理",
    what: "作れないもの・言葉を決める。ユーザー自身が管理する。",
    keywords: "禁止 語句 制限 管理",
    go: "route:BlockedTermsAdmin",
  },
  {
    title: "タブの表示・並べ替え",
    where: "設定 > 🎮 たのしむ > タブの表示・並べ替え",
    what: "下のタブの出し入れと順番を変える。",
    keywords: "タブ 並べ替え 表示 ナビ",
    go: "route:TabCustomize",
  },
  {
    title: "接続（ゲートウェイ）",
    where: "設定 > 🔗 接続 > Gateway",
    what: "PCとつなぐ設定。IP・ポート・Tailscale。つながらないときはここと、PCの状態を確かめる。",
    keywords: "接続 ゲートウェイ つながらない IP Tailscale PC",
    go: "route:Gateway",
  },
  {
    title: "PCの状態",
    where: "設定 > 🩺 情報・診断 > PCの状態",
    what: "PCのGPU・メモリ・温度などを見る。",
    keywords: "PC 状態 GPU メモリ 温度",
    go: "route:PcHealth",
  },
  {
    title: "通知（生成の進み具合）",
    where: "通知バー",
    what: "生成中は通知バーに、作業の内容・経過・残り時間が出る。完成や失敗も通知される。アプリを閉じても生成はPCで続く。",
    keywords: "通知 バー 進み具合 残り時間 完成",
    go: "",
  },
  {
    title: "朝の挨拶・占い",
    where: "設定 > 🎮 たのしむ > 朝の挨拶 & 占い",
    what: "パートナーの朝の挨拶や占い、毎日の話題を設定する。",
    keywords: "朝 挨拶 占い 話題 ニュース",
    go: "route:DailySettings",
  },
  {
    title: "絆レベル & 実績",
    where: "設定 > 🎮 たのしむ > 絆レベル & 実績",
    what: "会話の回数や連続日数で上がる絆レベルと実績。",
    keywords: "絆 レベル 実績",
    go: "route:PartnerBond",
  },
  {
    title: "日記・アルバム・夢日記",
    where: "設定 > 🎮 たのしむ",
    what: "日記アシスタント、写真アルバム、AIの夢日記。",
    keywords: "日記 アルバム 夢",
    go: "route:Diary",
  },
];

/** The entries that best fit a question (word and character-pair matching, which suits Japanese). */
export function guideFor(question: string, limit = 6): GuideEntry[] {
  const query = [...new Set(termsOf(question))];
  if (query.length === 0) return APP_GUIDE.slice(0, limit);
  const scored = APP_GUIDE.map((entry) => {
    const terms = termsOf(`${entry.title} ${entry.keywords} ${entry.where} ${entry.what}`);
    const set = new Set(terms);
    const titleSet = new Set(termsOf(`${entry.title} ${entry.keywords}`));
    let score = 0;
    for (const t of query) {
      if (set.has(t)) score += 1;
      if (titleSet.has(t)) score += 2;
    }
    return { entry, score };
  });
  return scored
    .filter((row) => row.score > 0)
    .toSorted((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((row) => row.entry);
}

const SYSTEM = `あなたは、アプリ「OpenClaw」の中を歩いている、ミニキャラのコンシェルジュ。ユーザーの質問に、アプリの案内の範囲で、親しみやすく短く答える（3文以内）。
アプリの使い方・設定の場所・項目までの案内・困ったときの相談に答える。【案内】にないことは、知らないと正直に言い、近そうな画面を勧める。
出力はJSONオブジェクト1つだけ（説明や\`\`\`は書かない）: {"reply":"返事（日本語）","go":"移動先（案内の go をそのまま。移動を勧めないときは空文字）","goLabel":"移動ボタンに出す短い名前"}
- 「どこ？」「開いて」「連れて行って」のように場所を求められたら、go を入れる。
- 手順を聞かれたら、画面名を > でつないで、順番に教える。`;

export function conciergePrompt(
  question: string,
  history: Array<{ role: "user" | "assistant"; text: string }>,
): { system: string; user: string } {
  const entries = guideFor(
    `${history
      .filter((h) => h.role === "user")
      .slice(-2)
      .map((h) => h.text)
      .join(" ")} ${question}`,
    6,
  );
  const guide = entries
    .map((e) => `- ${e.title}｜場所: ${e.where}｜${e.what}｜go: ${e.go ?? ""}`)
    .join("\n");
  const past = history
    .slice(-4)
    .map((h) => `${h.role === "user" ? "ユーザー" : "あなた"}: ${h.text}`)
    .join("\n");
  return {
    system: SYSTEM,
    user: `【案内】\n${guide}\n\n${past ? `【これまでの会話】\n${past}\n\n` : ""}【いまの質問】${question}`,
  };
}

export function parseConciergeReply(text: string): { reply: string; go: string; goLabel: string } {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) {
    try {
      const data = JSON.parse(text.slice(start, end + 1)) as Record<string, unknown>;
      const reply = String(data.reply ?? "").trim();
      if (reply) {
        const go = String(data.go ?? "").trim();
        return {
          reply: reply.slice(0, 600),
          go: /^(route|deep|tab):[A-Za-z:]+$/.test(go) ? go : "",
          goLabel: String(data.goLabel ?? "")
            .trim()
            .slice(0, 20),
        };
      }
    } catch {
      // fall through to plain text
    }
  }
  return {
    reply: text.trim().slice(0, 600) || "うまく答えられませんでした。もう一度聞いてね。",
    go: "",
    goLabel: "",
  };
}
