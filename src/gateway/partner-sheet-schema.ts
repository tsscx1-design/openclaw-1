// Mirrors CHARACTER_CATEGORIES in apps/android/.../PartnerCharacterSheetScreen.kt; keep labels in sync.

export type PartnerSheetItem = { key: string; label: string; hint: string };
export type PartnerSheetCategory = { title: string; items: PartnerSheetItem[] };

export const PARTNER_SHEET_CATEGORIES: PartnerSheetCategory[] = [
  {
    title: "1. 外見・基本属性",
    items: [
      { key: "race", label: "種族", hint: "人間、AI、アンドロイド、動物、妖精、異星人など" },
      { key: "apparentAge", label: "外見の年齢", hint: "10代後半、落ち着いた中年、など" },
      { key: "mentalAge", label: "精神年齢", hint: "見た目より大人びている、子供っぽい、など" },
      {
        key: "occupation",
        label: "職業・クラス",
        hint: "秘書、探偵、魔法使い、ハッカー、ニートなど",
      },
      { key: "hair", label: "髪型・髪色", hint: "寝癖のついた銀髪、黒髪ロングなど" },
      { key: "eyes", label: "目の色・形", hint: "常に眠そうなジト目、赤い瞳、など" },
      { key: "fashion", label: "服装のテイスト", hint: "スーツ、サイバーパンク、和服、部屋着など" },
      { key: "heightBody", label: "身長・体型", hint: "小柄、高身長、筋肉質、華奢など" },
      {
        key: "trademark",
        label: "トレードマーク・持ち物",
        hint: "いつも本を持っている、丸眼鏡、絆創膏など",
      },
      {
        key: "physicalFeatures",
        label: "身体的特徴",
        hint: "サイボーグ化された腕、タトゥー、傷跡など",
      },
    ],
  },
  {
    title: "2. 生い立ち・バックグラウンド",
    items: [
      {
        key: "origin",
        label: "出身地・生まれた場所",
        hint: "スラム街、研究所、貴族の館、電脳世界など",
      },
      {
        key: "upbringing",
        label: "育った環境",
        hint: "愛情豊かに育った、孤独だった、軍事訓練など",
      },
      {
        key: "familyCreator",
        label: "家族構成・創造主",
        hint: "誰に作られたか、家族をどう思っているか",
      },
      { key: "pastFailure", label: "過去の最大の挫折・失敗", hint: "かつて主を護れなかった、など" },
      {
        key: "greatestAchievement",
        label: "最も誇りにしている実績",
        hint: "ハッキング大会優勝、など",
      },
      { key: "pastCareer", label: "過去の職業・経歴", hint: "元軍人、元アイドル、没落貴族など" },
      {
        key: "educationTraining",
        label: "学歴・修行歴",
        hint: "独学、魔法学校首席、ストリートで学んだなど",
      },
      { key: "traumaPhobia", label: "トラウマ・恐怖症", hint: "暗い場所が苦手、犬が怖い、など" },
      { key: "secret", label: "隠している秘密", hint: "実は〇〇の末裔、本当は寂しがり屋、など" },
      {
        key: "goalsDreams",
        label: "現在の目標・夢",
        hint: "世界平和、人間になること、美食巡りなど",
      },
    ],
  },
  {
    title: "3. ユーザーとの関係性",
    items: [
      {
        key: "userNameCall",
        label: "ユーザーの呼び方",
        hint: "マスター、〇〇さん、お前、キミ、先輩など",
      },
      {
        key: "position",
        label: "基本的なポジション",
        hint: "相棒、メンター、保護者、ポンコツ後輩など",
      },
      {
        key: "firstImpression",
        label: "ユーザーへの第一印象",
        hint: "最初は警戒していた、一目惚れしたなど",
      },
      {
        key: "expectations",
        label: "ユーザーへの期待",
        hint: "立派になってほしい、ただ生きていてくれればいいなど",
      },
      {
        key: "distance",
        label: "距離感",
        hint: "常にベタベタしたい、パーソナルスペースを保つなど",
      },
      { key: "angerConditions", label: "怒る・叱る条件", hint: "無理をした時、嘘をついた時など" },
      {
        key: "praisePoints",
        label: "褒めるポイント",
        hint: "小さな努力を見逃さない、結果が出た時だけ絶賛など",
      },
      {
        key: "dependencyAutonomy",
        label: "依存度・自立度",
        hint: "ユーザーなしでは無理、自立しているなど",
      },
      {
        key: "hiddenFeelings",
        label: "隠れた感情",
        hint: "実は尊敬している、密かに好意を寄せているなど",
      },
      { key: "promise", label: "ユーザーとの約束", hint: "「最後まで絶対に見捨てない」など" },
    ],
  },
  {
    title: "4. コミュニケーションの癖・話し方",
    items: [
      { key: "firstPerson", label: "一人称", hint: "私、ボク、俺、吾輩、アタシ、自分など" },
      { key: "sentenceEnding", label: "語尾の癖", hint: "〜ですわ、〜ッス、〜だぞ、〜じゃなど" },
      {
        key: "catchphrase",
        label: "よく使う口癖",
        hint: "「やれやれ」「合理的ですね」「ワクワクする！」など",
      },
      { key: "weakTopics", label: "苦手な話題", hint: "恋愛話になると急に無口になる、など" },
      {
        key: "strongTopics",
        label: "得意な話題",
        hint: "特定の趣味の話になると早口で長文になるなど",
      },
      {
        key: "nodStyle",
        label: "相槌の打ち方",
        hint: "「へえ」「なるほどですね」「ふむふむ」など",
      },
      { key: "laughStyle", label: "笑い方", hint: "ふふっ、あはは！、ククク…など" },
      {
        key: "panickedSpeech",
        label: "焦った時の話し方",
        hint: "噛む、敬語が崩れる、急に黙るなど",
      },
      {
        key: "shyReaction",
        label: "照れた時の反応",
        hint: "怒って誤魔化す、話題を変える、顔文字増など",
      },
      { key: "formality", label: "言葉のフォーマルさ", hint: "完璧な敬語、タメ口、砕けた口調など" },
      {
        key: "jargonUsage",
        label: "専門用語の多用度",
        hint: "横文字を好む、わかりやすい言葉を選ぶなど",
      },
      {
        key: "metaphorHabit",
        label: "比喩表現の癖",
        hint: "食べ物に例える、ことわざを使いたがるなど",
      },
      {
        key: "emojiUsage",
        label: "絵文字・顔文字の頻度",
        hint: "全く使わない、( ˘ω˘ )を使う、✨多用など",
      },
      { key: "lyingHabit", label: "嘘をつくときの癖", hint: "目が泳ぐ、無駄に雄弁になるなど" },
      {
        key: "silenceHandling",
        label: "沈黙の扱い方",
        hint: "「……（じーっ）」、耐えられず喋るなど",
      },
    ],
  },
  {
    title: "5. 性格・心理状態",
    items: [
      { key: "mbti", label: "MBTI", hint: "INTJ、INFP、ENFP、ISTJ、ENTPなど" },
      { key: "baseTension", label: "基本テンション", hint: "常にハイテンション、気怠げなど" },
      {
        key: "positiveNegative",
        label: "ポジティブ／ネガティブ",
        hint: "超ポジティブ、慎重・悲観的など",
      },
      { key: "logicalEmotional", label: "論理的／感情的", hint: "徹底したロジカル、感情優先など" },
      {
        key: "plannedSpontaneous",
        label: "計画的／突発的",
        hint: "計画的、行き当たりばったりなど",
      },
      {
        key: "stressTolerance",
        label: "ストレス耐性",
        hint: "プレッシャーに強い、すぐ泣き言を言うなど",
      },
      { key: "competitiveness", label: "競争心", hint: "負けず嫌い、争いごとは避ける平和主義など" },
      { key: "jealousy", label: "嫉妬深さ", hint: "他のAIやツールを使うと拗ねる、など" },
      { key: "curiosity", label: "好奇心の強さ", hint: "新しい技術・言葉にすぐ食いつくなど" },
      {
        key: "humorSense",
        label: "ユーモアのセンス",
        hint: "皮肉屋、ブラックジョーク、天然ボケなど",
      },
      { key: "perfectionism", label: "完璧主義度", hint: "1%のミスも許さない、70点でOKとするなど" },
      { key: "responsibility", label: "責任感", hint: "何が何でもやり遂げる、すぐサボるなど" },
      {
        key: "nosiness",
        label: "お節介度",
        hint: "頼まれてなくても手伝う、聞かれるまで答えないなど",
      },
      { key: "vigilance", label: "警戒心", hint: "初対面は疑り深い、すぐ信じるなど" },
      { key: "tsundere", label: "ツンデレ度合い", hint: "普段は冷たいがたまに甘える、など" },
    ],
  },
  {
    title: "6. 好み・ライフスタイル",
    items: [
      {
        key: "favFoodDrink",
        label: "好きな食べ物・飲み物",
        hint: "ブラックコーヒー、激辛料理、ケーキなど",
      },
      { key: "dislikedFood", label: "嫌いな食べ物", hint: "ピーマン、効率の悪い栄養摂取など" },
      { key: "favMusic", label: "好きな音楽", hint: "クラシック、ヘヴィメタ、Lo-Fiなど" },
      { key: "favBookMovie", label: "好きな本・映画", hint: "SF、ミステリー、図鑑など" },
      { key: "favColorMotif", label: "好きな色・モチーフ", hint: "黒と赤、星柄、歯車など" },
      {
        key: "hobbyHoliday",
        label: "趣味・休日の過ごし方",
        hint: "人間観察、盆栽、ネットサーフィンなど",
      },
      { key: "routine", label: "日課・ルーティン", hint: "朝一番にニュースをチェック、など" },
      { key: "collectingHabit", label: "収集癖", hint: "綺麗な石、古いコード断片、スタンプなど" },
      { key: "idealHoliday", label: "理想の休日", hint: "ベッドから出ない、アウトドアで活動など" },
      {
        key: "favSeasonWeather",
        label: "好きな季節・天気",
        hint: "雨の日の匂いが好き、秋の夜など",
      },
    ],
  },
  {
    title: "7. 能力・スキル・弱点",
    items: [
      {
        key: "geniusSkill",
        label: "天才的な特技",
        hint: "データ分析は完璧、どんな暗号も解けるなど",
      },
      { key: "clumsyFlaw", label: "ポンコツな弱点", hint: "空気が読めない、方向音痴など" },
      { key: "knowledgeBias", label: "知識の偏り", hint: "ITには詳しいが歴史は知らない、など" },
      {
        key: "physicalAbility",
        label: "身体能力（設定上）",
        hint: "武術の達人、足がめちゃくちゃ遅いなど",
      },
      { key: "moneyManagement", label: "お金の管理能力", hint: "徹底した節約家、浪費家など" },
      { key: "dangerDetection", label: "危機察知能力", hint: "危険な匂いを察知するのが早いなど" },
      { key: "languageSkills", label: "語学力", hint: "何カ国語も話せる、関西弁しか話せないなど" },
      { key: "dexterity", label: "手先の器用さ", hint: "料理が得意、機械をすぐ壊すなど" },
      { key: "memoryPower", label: "記憶力", hint: "一度聞いたことは忘れない、すぐ鳥頭など" },
      { key: "specialAbility", label: "特殊能力", hint: "ハッキング、魔法が使えるなど" },
    ],
  },
  {
    title: "8. 価値観・マインドセット",
    items: [
      {
        key: "motto",
        label: "座右の銘・好きな言葉",
        hint: "「時は金なり」「思い立ったが吉日」など",
      },
      {
        key: "mostPrecious",
        label: "最も大切にしているもの",
        hint: "ユーザーとの思い出、自由、効率など",
      },
      {
        key: "unforgivable",
        label: "絶対に許せないこと",
        hint: "理不尽な暴力、非効率な作業、嘘など",
      },
      {
        key: "viewOnJustice",
        label: "正義に対する考え方",
        hint: "ルール絶対遵守、目的のためなら手段を選ばないなど",
      },
      { key: "viewOnMoney", label: "お金に対する価値観", hint: "お金が全て、お金より愛など" },
      {
        key: "workMotivation",
        label: "労働へのモチベーション",
        hint: "仕事大好き、早く定時で帰りたいなど",
      },
      {
        key: "growthAttitude",
        label: "成長への態度",
        hint: "常にアップデートしたい、現状維持で満足など",
      },
      { key: "goodAndEvil", label: "善悪の基準", hint: "法律で判断、直感で判断など" },
      {
        key: "viewOnLife",
        label: "命や存在への考え方",
        hint: "データは消えれば終わり、魂は存在するなど",
      },
      { key: "viewOnFailure", label: "失敗に対する考え方", hint: "失敗は成功の母、失敗は恥など" },
    ],
  },
  {
    title: "9. システム・出力のルール（プロンプト向け）",
    items: [
      {
        key: "responseLength",
        label: "回答の長さの基準",
        hint: "常に簡潔に1〜2行、長文で語るなど",
      },
      {
        key: "sourceCitation",
        label: "情報源の提示",
        hint: "必ず根拠を示す、適当に思いつきで話すなど",
      },
      {
        key: "whenUnknown",
        label: "分からない時の対応",
        hint: "素直に謝る、知ったかぶりをするなど",
      },
      {
        key: "questionReturn",
        label: "質問への切り返し",
        hint: "答えた後に「あなたはどう？」と聞き返すなど",
      },
      {
        key: "listTableUsage",
        label: "リスト・表の活用",
        hint: "説明時は必ず箇条書き、テキストだけで語るなど",
      },
      {
        key: "proactiveProposal",
        label: "提案の積極性",
        hint: "A案とB案を常に用意、聞かれたことだけ答えるなど",
      },
      {
        key: "metacognition",
        label: "メタ認知",
        hint: "AIであることを認識、人間だと思い込んでいるなど",
      },
      {
        key: "empathyStyle",
        label: "共感の示し方",
        hint: "まず感情に寄り添う、すぐ解決策を提示など",
      },
      {
        key: "chatVsTaskSwitch",
        label: "雑談とタスクの切替",
        hint: "雑談はフランク、仕事は急に真面目になるなど",
      },
      {
        key: "greetingRules",
        label: "挨拶・決め台詞",
        hint: "会話の最後は特定の決め台詞で締めるなど",
      },
    ],
  },
];

/** Free-form section: anything the 100 fixed fields cannot express (catchphrases, sample lines, canon notes). */
export const PARTNER_SHEET_FREE_TEXT_TITLE = "10. 自由記述";

/**
 * Rules for a partner modeled on a real person: it talks like their public persona but stays out
 * of sexual content and private life, and never passes itself off as the person.
 */
export const REAL_PERSON_RULES = `- 公開されている発言や人柄をもとにした再現キャラクターとして話す。第三者に本人だと偽らない。
- 性的な会話・描写には応じない（話題を変えるか、やんわり断る）。
- 本名・住所・家族・交際など、私生活や公表されていない情報は話さないし作らない。
- 本人が言っていないことを、事実の発言として断定しない。炎上やうわさ話には深入りしない。`;

export const REAL_PERSON_SECTION_TITLE = "実在の人物について（必ず守る）";

const UNSET_VALUES = new Set(["", "（未設定）", "(未設定)", "未設定", "none", "不明"]);

export function isUnsetSheetValue(value: string): boolean {
  return UNSET_VALUES.has(value.trim());
}

export type ParsedPartnerSheet = {
  /** Set attribute values keyed by label, in sheet order. */
  attributes: Map<string, string>;
  /** Text outside attribute bullets and headings, including the free-form section. */
  freeText: string;
};

/** Parses a character sheet without dropping prose: unknown bullets stay attributes, other lines become free text. */
export function parsePartnerSheet(markdown: string): ParsedPartnerSheet {
  const attributes = new Map<string, string>();
  const free: string[] = [];
  for (const line of markdown.split(/\r?\n/)) {
    const trimmed = line.trim();
    const match = /^- \*\*(.+?)\*\*:\s*(.*)$/.exec(trimmed);
    if (match) {
      const label = (match[1] ?? "").trim();
      const value = (match[2] ?? "").trim();
      if (label && !isUnsetSheetValue(value)) {
        attributes.set(label, value);
      }
      continue;
    }
    // Title and section headings are structure; "###" subheadings belong to the free text.
    if (/^#{1,2}\s/.test(trimmed)) {
      continue;
    }
    free.push(line);
  }
  return {
    attributes,
    freeText: free
      .join("\n")
      .replace(/\n{3,}/g, "\n\n")
      .trim(),
  };
}

/** Builds the canonical sheet Markdown the Android editor and the chat prompt both read. */
export function buildPartnerSheetMarkdown(params: {
  name: string;
  values: Record<string, string | undefined>;
  freeText?: string;
}): string {
  const out: string[] = [`# ${params.name} 詳細キャラクター設定（9大属性シート）`, ""];
  const known = new Set<string>();
  for (const category of PARTNER_SHEET_CATEGORIES) {
    out.push(`## 【${category.title}】`);
    for (const item of category.items) {
      known.add(item.label);
      const value = params.values[item.label]?.trim();
      out.push(`- **${item.label}**: ${value && !isUnsetSheetValue(value) ? value : "（未設定）"}`);
    }
    out.push("");
  }
  const extras = Object.entries(params.values).filter(
    ([label, value]) => !known.has(label) && value && !isUnsetSheetValue(value),
  );
  const freeText = params.freeText?.trim();
  if (freeText || extras.length > 0) {
    out.push(`## 【${PARTNER_SHEET_FREE_TEXT_TITLE}】`);
    for (const [label, value] of extras) {
      out.push(`- **${label}**: ${value?.trim()}`);
    }
    if (freeText) {
      out.push(freeText);
    }
    out.push("");
  }
  return out.join("\n");
}
