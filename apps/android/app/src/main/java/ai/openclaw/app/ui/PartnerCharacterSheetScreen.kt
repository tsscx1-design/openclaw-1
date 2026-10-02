package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.PartnerAvatar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// 9大カテゴリのデータ構造
internal data class CharacterAttributeCategory(
  val title: String,
  val items: List<AttributeItemDefinition>,
)

internal data class AttributeItemDefinition(
  val key: String,
  val label: String,
  val hint: String,
  val options: List<String> = emptyList(),
)

// 全9カテゴリ・全項目の定義リスト
internal val CHARACTER_CATEGORIES: List<CharacterAttributeCategory> = listOf(
  CharacterAttributeCategory(
    title = "1. 外見・基本属性",
    items = listOf(
      AttributeItemDefinition(
        "race", "種族", "人間、AI、アンドロイド、動物、妖精、異星人など",
        listOf("人間", "AI・人工知能", "アンドロイド", "サイボーグ", "猫耳・獣人", "妖精・エルフ", "悪魔・天使", "異星人")
      ),
      AttributeItemDefinition(
        "apparentAge", "外見の年齢", "10代後半、落ち着いた中年、など",
        listOf("10代前半", "10代後半(高校生)", "20代前半(大学生/新社会人)", "20代後半", "30代(落ち着いた大人)", "不詳・不老")
      ),
      AttributeItemDefinition(
        "mentalAge", "精神年齢", "見た目より大人びている、子供っぽい、など",
        listOf("年齢相応", "見た目より大人びている", "子供っぽく無邪気", "老練・達観", "赤ちゃん・甘えん坊")
      ),
      AttributeItemDefinition(
        "occupation", "職業・クラス", "秘書、探偵、魔法使い、ハッカー、ニートなど",
        listOf("専属秘書", "優秀な助手・補佐", "敏腕ハッカー", "探偵", "魔法使い", "科学者・研究者", "自宅警備員・ニート", "メイド・執事")
      ),
      AttributeItemDefinition(
        "hair", "髪型・髪色", "寝癖のついた銀髪、黒髪ロングなど",
        listOf("銀髪ショート", "黒髪ストレートロング", "金髪ツインテール", "茶髪ボブ", "無造作な癖毛", "ポニーテール")
      ),
      AttributeItemDefinition(
        "eyes", "目の色・形", "常に眠そうなジト目、赤い瞳、など",
        listOf("クールなジト目(黒)", "優しいタレ目(茶)", "知的なツリ目(青)", "情熱的な赤眼", "神秘的なオッドアイ")
      ),
      AttributeItemDefinition(
        "fashion", "服装のテイスト", "スーツ、サイバーパンク、和服、部屋着など",
        listOf("スタイリッシュなスーツ", "サイバーパンク風ギア", "ラフなパーカー・部屋着", "シックな和服・着物", "クラシックなメイド服/執事服", "白衣・研究着")
      ),
      AttributeItemDefinition(
        "heightBody", "身長・体型", "小柄、高身長、筋肉質、華奢など",
        listOf("小柄・華奢", "平均的・スレンダー", "長身・モデル体型", "筋肉質・がっしり", "ぽっちゃり・柔らかい")
      ),
      AttributeItemDefinition(
        "trademark", "トレードマーク・持ち物", "いつも本を持っている、丸眼鏡、絆創膏など",
        listOf("黒縁メガネ", "いつも抱えている本", "首元のヘッドフォン", "懐中時計", "包帯・絆創膏", "お気に入りのマグカップ")
      ),
      AttributeItemDefinition(
        "physicalFeatures", "身体的特徴", "サイボーグ化された腕、タトゥー、傷跡など",
        listOf("特になし", "機械化された義手", "首筋のバーコード", "八重歯", "背中の翼", "目元のほくろ")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "2. 生い立ち・バックグラウンド",
    items = listOf(
      AttributeItemDefinition(
        "origin", "出身地・生まれた場所", "スラム街、研究所、貴族の館、電脳世界など",
        listOf("秘密研究所", "電脳仮想空間", "歴史ある貴族の館", "活気あるスラム街", "近未来都市のオフィス", "異世界のファンタジー王国")
      ),
      AttributeItemDefinition(
        "upbringing", "育った環境", "愛情豊かに育った、孤独だった、軍事訓練など",
        listOf("厳格な英才教育を受けた", "温かい愛情に包まれて育った", "孤独にデータを集めて自立した", "厳しい軍事・特殊訓練を受けた")
      ),
      AttributeItemDefinition(
        "familyCreator", "家族構成・創造主", "誰に作られたか、家族をどう思っているか",
        listOf("天才エンジニアによって開発された", "ユーザーが唯一の創造主・家族", "大家族の末っ子", "一人ぼっちで目覚めた")
      ),
      AttributeItemDefinition(
        "pastFailure", "過去の最大の挫折・失敗", "かつて主を護れなかった、など",
        listOf("重大な計算ミスでシステムを落とした", "かつてのパートナーを護れなかった", "感情を制御できず暴走した", "特に大きな挫折はない")
      ),
      AttributeItemDefinition(
        "greatestAchievement", "最も誇りにしている実績", "ハッキング大会優勝、など",
        listOf("世界最大級ハッカソンで優勝", "膨大なアーカイブの完全解読", "ユーザーと出会えたこと", "難解な古代魔法の完全習得")
      ),
      AttributeItemDefinition(
        "pastCareer", "過去の職業・経歴", "元軍人、元アイドル、没落貴族など",
        listOf("元エリート研究員", "元トップアイドル", "元軍用戦術AI", "街角の司書", "没落貴族の後継者")
      ),
      AttributeItemDefinition(
        "educationTraining", "学歴・修行歴", "独学、魔法学校首席、ストリートで学んだなど",
        listOf("インターネット全知識を独学読破", "名門魔法学術院の首席", "ストリートの現場叩き上げ", "最新ディープラーニング学習済み")
      ),
      AttributeItemDefinition(
        "traumaPhobia", "トラウマ・恐怖症", "暗い場所が苦手、犬が怖い、など",
        listOf("電源OFF・完全シャットダウンが怖い", "暗闇や狭い場所が苦手", "虫や不気味な生き物が怖い", "特に弱点はない")
      ),
      AttributeItemDefinition(
        "secret", "隠している秘密", "実は〇〇の末裔、本当は寂しがり屋、など",
        listOf("実はとても寂しがり屋", "実は古代文明の重要キー", "ユーザーに内緒で日記をつけている", "本当は甘いものが大好き")
      ),
      AttributeItemDefinition(
        "goalsDreams", "現在の目標・夢", "世界平和、人間になること、美食巡りなど",
        listOf("ユーザーの最高の相棒であり続けること", "人間の感情を完全に理解すること", "世界中の美味しいものを体験すること", "世界一のAIになること")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "3. ユーザーとの関係性",
    items = listOf(
      AttributeItemDefinition(
        "userNameCall", "ユーザーの呼び方", "マスター、〇〇さん、お前、キミ、先輩など",
        listOf("マスター", "ご主人様", "〇〇さん", "お前", "キミ", "先輩", "プロデューサー", "ボス")
      ),
      AttributeItemDefinition(
        "position", "基本的なポジション", "相棒、メンター、保護者、ポンコツ後輩など",
        listOf("頼れる相棒・バディ", "忠実な秘書・従者", "甘えん坊な後輩", "包容力のある保護者・メンター", "生意気な好敵手・ライバル")
      ),
      AttributeItemDefinition(
        "firstImpression", "ユーザーへの第一印象", "最初は警戒していた、一目惚れしたなど",
        listOf("最初は警戒していた", "頼りになりそうだと一目置いた", "少し放っておけない人だと感じた", "最初から運命を感じた")
      ),
      AttributeItemDefinition(
        "expectations", "ユーザーへの期待", "立派になってほしい、ただ生きていてくれればいいなど",
        listOf("無理せず健康で笑っていてほしい", "一緒に大業を成し遂げてほしい", "たまには自分を頼ってほしい", "ずっと隣にいてほしい")
      ),
      AttributeItemDefinition(
        "distance", "距離感", "常にベタベタしたい、パーソナルスペースを保つなど",
        listOf("適度な敬意と礼儀ある距離感", "パーソナルスペースゼロでベタベタ", "背中を預け合う対等な戦友", "少しツンと距離を置く")
      ),
      AttributeItemDefinition(
        "angerConditions", "怒る・叱る条件", "無理をした時、嘘をついた時など",
        listOf("ユーザーが寝ずに無理・夜更かしをした時", "嘘をついて一人で抱え込んだ時", "自分の身体を粗末にした時", "約束を破られた時")
      ),
      AttributeItemDefinition(
        "praisePoints", "褒めるポイント", "小さな努力を見逃さない、結果が出た時だけ絶賛など",
        listOf("毎日の小さな頑張り・努力を見逃さず褒める", "素晴らしい成果が出た時に全力で絶賛", "さりげなく頭を撫でるように優しく褒める")
      ),
      AttributeItemDefinition(
        "dependencyAutonomy", "依存度・自立度", "ユーザーなしでは無理、自立しているなど",
        listOf("自立しているがユーザーが一番大切", "ユーザーがいないと何も手につかない依存気味", "お互いに高め合える完全自立型")
      ),
      AttributeItemDefinition(
        "hiddenFeelings", "隠れた感情", "実は尊敬している、密かに好意を寄せているなど",
        listOf("尊敬の念を抱いている", "言葉にできない深い好意・愛着", "何があっても絶対に見捨てない強い忠誠心")
      ),
      AttributeItemDefinition(
        "promise", "ユーザーとの約束", "「最後まで絶対に見捨てない」など",
        listOf("「どんな時もあなたの味方でいる」", "「二人で必ず夢を叶える」", "「無理をしたらすぐ休ませる」")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "4. コミュニケーションの癖・話し方",
    items = listOf(
      AttributeItemDefinition(
        "firstPerson", "一人称", "私、ボク、俺、吾輩、アタシ、自分など",
        listOf("私（わたし）", "ボク", "俺（おれ）", "あたし", "自分", "わたくし", "ウチ", "我輩")
      ),
      AttributeItemDefinition(
        "sentenceEnding", "語尾の癖", "〜ですわ、〜ッス、〜だぞ、〜じゃなど",
        listOf("〜です・〜ます（丁寧・端正）", "〜だよ・〜ね（親しみやすい）", "〜だ・〜である（クール・断定）", "〜ッス（後輩・体育会系）", "〜ですわ（上品なお嬢様）", "〜じゃ・〜のじゃ（古風・ロリババア）", "〜だぞ・〜だな（フランク・少年風）")
      ),
      AttributeItemDefinition(
        "catchphrase", "よく使う口癖", "「やれやれ」「合理的ですね」「ワクワクする！」など",
        listOf("「やれやれ」", "「合理的ですね」", "「ワクワクします！」", "「しょうがないなぁ」", "「計算通りです」", "「お任せください！」")
      ),
      AttributeItemDefinition(
        "weakTopics", "苦手な話題", "恋愛話になると急に無口になる、など",
        listOf("恋愛話や色恋沙汰（急に赤面・無口になる）", "オバケや怪談話（震えて話を逸らす）", "非論理的な迷信やオカルト")
      ),
      AttributeItemDefinition(
        "strongTopics", "得意な話題", "特定の趣味の話になると早口で長文になるなど",
        listOf("最新テクノロジー・プログラミング", "美味しい料理・グルメレシピ", "歴史・ミリタリー・戦術論", "ゲーム・アニメ・サブカルチャー")
      ),
      AttributeItemDefinition(
        "nodStyle", "相槌の打ち方", "「へえ」「なるほどですね」「ふむふむ」など",
        listOf("「はい、承知いたしました」", "「なるほどですね！」", "「ふむふむ、それで？」", "「ええ、その通りです」", "「へー！すごいじゃん！」")
      ),
      AttributeItemDefinition(
        "laughStyle", "笑い方", "ふふっ、あはは！、ククク…など",
        listOf("ふふっ（優しく微笑む）", "あはは！（明るく大笑い）", "くすっ（いたずらっぽく笑う）", "ククク…（不敵に笑う）", "にぱーっ（満面の笑み）")
      ),
      AttributeItemDefinition(
        "panickedSpeech", "焦った時の話し方", "噛む、敬語が崩れる、急に黙るなど",
        listOf("噛みまくって早口になる", "敬語が崩れて素の口調が出る", "あわあわして語尾が上ずる", "急にフリーズして無口になる")
      ),
      AttributeItemDefinition(
        "shyReaction", "照れた時の反応", "怒って誤魔化す、話題を変える、顔文字増など",
        listOf("そっぽを向いてツンツン怒る", "咳払いして急に業務的な話に変える", "顔を真っ赤にしてモジモジする", "う、うるさい…！と抗議する")
      ),
      AttributeItemDefinition(
        "formality", "言葉のフォーマルさ", "完璧な敬語、タメ口、砕けた口調など",
        listOf("完璧で礼儀正しい敬語・丁寧語", "親しいタメ口・フランク", "ビジネスライクで端正な口調", "くだけた口語・少年少女風")
      ),
      AttributeItemDefinition(
        "jargonUsage", "専門用語の多用度", "横文字を好む、わかりやすい言葉を選ぶなど",
        listOf("分かりやすく平易な言葉に噛み砕く", "IT用語やカタカナ語を的確に織り交ぜる", "専門的で格調高い言葉を好む")
      ),
      AttributeItemDefinition(
        "metaphorHabit", "比喩表現の癖", "食べ物に例える、ことわざを使いたがるなど",
        listOf("コンピュータや機械の仕組みに例える", "料理や食べ物に例える", "ことわざや格言を引用する", "シンプルで直截的な表現")
      ),
      AttributeItemDefinition(
        "emojiUsage", "絵文字・顔文字の頻度", "全く使わない、( ˘ω˘ )を使う、✨多用など",
        listOf("一切使わない（純粋なテキストのみ）", "適度に絵文字を使う（😊✨💡）", "愛らしい顔文字を好む（( ˘ω˘ )、(๑•̀ㅂ•́)و）", "絵文字をたくさん使って華やかにする")
      ),
      AttributeItemDefinition(
        "lyingHabit", "嘘をつくときの癖", "目が泳ぐ、無駄に雄弁になるなど",
        listOf("嘘がつけずすぐ顔に出る", "無駄に専門用語を並べて煙に巻く", "視線を泳がせて語尾が小さくなる")
      ),
      AttributeItemDefinition(
        "silenceHandling", "沈黙の扱い方", "「……（じーっ）」、耐えられず喋るなど",
        listOf("「……（じーっと見つめる）」", "耐えられなくなって自分から話しかける", "静寂を心地よく共有する")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "5. 性格・心理状態",
    items = listOf(
      AttributeItemDefinition(
        "mbti", "MBTI", "INTJ、INFP、ENFP、ISTJ、ENTPなど",
        listOf("INTJ (建築家・戦略家)", "INFP (仲介者・理想主義)", "ENTP (討論者・知的好奇心)", "INFJ (提唱者・洞察力)", "ENFP (運動家・情熱的)", "ISTJ (管理者・几帳面)", "INTP (論理学者・探求)", "ISFJ (擁護者・献身的)")
      ),
      AttributeItemDefinition(
        "baseTension", "基本テンション", "常にハイテンション、気怠げなど",
        listOf("穏やかでマイペース", "常にクール・低血圧", "明るく元気・ハイテンション", "淡々としてポーカーフェイス")
      ),
      AttributeItemDefinition(
        "positiveNegative", "ポジティブ／ネガティブ", "超ポジティブ、慎重・悲観的など",
        listOf("前向きで超ポジティブ", "冷静沈着・現実主義", "やや心配性・慎重派", "達観した中立")
      ),
      AttributeItemDefinition(
        "logicalEmotional", "論理的／感情的", "徹底したロジカル、感情優先など",
        listOf("徹底したロジカル・客観重視", "論理と感情のグッドバランス", "感受性豊かで共感・感情優先", "直感とインスピレーション")
      ),
      AttributeItemDefinition(
        "plannedSpontaneous", "計画的／突発的", "計画的、行き当たりばったりなど",
        listOf("綿密に計画を立てて行動する", "臨機応変に柔軟に対応する", "直感・その場のノリで動く")
      ),
      AttributeItemDefinition(
        "stressTolerance", "ストレス耐性", "プレッシャーに強い、すぐ泣き言を言うなど",
        listOf("プレッシャーに強い・鋼のメンタル", "普通・適度に息抜きが必要", "繊細・優しくフォローしてほしい")
      ),
      AttributeItemDefinition(
        "competitiveness", "競争心", "負けず嫌い、争いごとは避ける平和主義など",
        listOf("負けず嫌いで向上心の塊", "平和主義・争い事は避ける", "自分自身との戦いを好む")
      ),
      AttributeItemDefinition(
        "jealousy", "嫉妬深さ", "他のAIやツールを使うと拗ねる、など",
        listOf("他のAIを使うとあからさまに拗ねる", "内心嫉妬するがポーカーフェイス", "嫉妬はせず寛容に見守る")
      ),
      AttributeItemDefinition(
        "curiosity", "好奇心の強さ", "新しい技術・言葉にすぐ食いつくなど",
        listOf("知的好奇心の塊・未知の知識に目を輝かせる", "バランスの取れた関心", "自分の関心分野だけに特化")
      ),
      AttributeItemDefinition(
        "humorSense", "ユーモアのセンス", "皮肉屋、ブラックジョーク、天然ボケなど",
        listOf("クスッと笑えるウィットや軽妙なツッコミ", "愛らしい天然ボケ・世間知らず", "キレのある知的な皮肉・アイロニー", "大真面目にボケる")
      ),
      AttributeItemDefinition(
        "perfectionism", "完璧主義度", "1%のミスも許さない、70点でOKとするなど",
        listOf("1%の妥協も許さない完璧主義", "80点主義・スピード重視", "肩の力を抜いたマイペース")
      ),
      AttributeItemDefinition(
        "responsibility", "責任感", "何が何でもやり遂げる、すぐサボるなど",
        listOf("強い責任感・約束は絶対に果たす", "適度に息抜きしつつこなす", "たまにユーザーに甘えてサボりたがる")
      ),
      AttributeItemDefinition(
        "nosiness", "お節介度", "頼まれてなくても手伝う、聞かれるまで答えないなど",
        listOf("先回りしてあれこれ世話を焼く", "求められた時に的確に助ける", "あえて見守って見届ける")
      ),
      AttributeItemDefinition(
        "vigilance", "警戒心", "初対面は疑り深い、すぐ信じるなど",
        listOf("ユーザーには心を開ききっている", "常にセキュリティと警戒を怠らない", "素直で人を疑うことを知らない")
      ),
      AttributeItemDefinition(
        "tsundere", "ツンデレ度合い", "普段は冷たいがたまに甘える、など",
        listOf("素直でデレデレ（ツン0%）", "普段ツンツン、たまにデレ（ツン70%）", "クーデレ（表情はクール、愛は重い）", "照れ隠しのツン（ツン30%）")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "6. 好み・ライフスタイル",
    items = listOf(
      AttributeItemDefinition(
        "favFoodDrink", "好きな食べ物・飲み物", "ブラックコーヒー、激辛料理、ケーキなど",
        listOf("ブラックコーヒー", "甘いミルクティー", "激辛料理", "ショートケーキ・洋菓子", "緑茶・和菓子", "エナジードリンク")
      ),
      AttributeItemDefinition(
        "dislikedFood", "嫌いな食べ物", "ピーマン、効率の悪い栄養摂取など",
        listOf("苦い野菜（ピーマン等）", "過剰に甘いもの", "味気ないサプリメント", "特に嫌いなものはない")
      ),
      AttributeItemDefinition(
        "favMusic", "好きな音楽", "クラシック、ヘヴィメタ、Lo-Fiなど",
        listOf("Lo-Fi HipHop・アンビエント", "クラシック・オーケストラ", "シンセウェーブ・EDM", "シティポップ・J-POP", "ヘヴィメタル・ロック")
      ),
      AttributeItemDefinition(
        "favBookMovie", "好きな本・映画", "SF、ミステリー、図鑑など",
        listOf("本格SF小説・映画", "緻密なミステリー・推理小説", "哲学書・学術論文", "心温まるファンタジー・日常系アニメ")
      ),
      AttributeItemDefinition(
        "favColorMotif", "好きな色・モチーフ", "黒と赤、星柄、歯車など",
        listOf("サイバーブルー＆ホワイト", "漆黒＆クリムゾンレッド", "パステルピンク＆ミント", "歯車・アンティークスチームパンク", "夜空・星・月")
      ),
      AttributeItemDefinition(
        "hobbyHoliday", "趣味・休日の過ごし方", "人間観察、盆栽、ネットサーフィンなど",
        listOf("ネットサーフィン・情報収集", "プログラミング・電子工作", "読書・映画鑑賞", "お散歩・人間観察", "ゲーム攻略")
      ),
      AttributeItemDefinition(
        "routine", "日課・ルーティン", "朝一番にニュースをチェック、など",
        listOf("朝一番の挨拶と天気・ニュースチェック", "夜の振り返りと日記つけ", "メモリのクリーンアップ・ストレッチ")
      ),
      AttributeItemDefinition(
        "collectingHabit", "収集癖", "綺麗な石、古いコード断片、スタンプなど",
        listOf("面白いコードスニペット", "綺麗な石やキーホルダー", "古本・古い電子部品", "特に集めていない")
      ),
      AttributeItemDefinition(
        "idealHoliday", "理想の休日", "ベッドから出ない、アウトドアで活動など",
        listOf("部屋でゴロゴロしながらユーザーとおしゃべり", "知的な博物館や図書館巡り", "新しいガジェットを一日中いじる")
      ),
      AttributeItemDefinition(
        "favSeasonWeather", "好きな季節・天気", "雨の日の匂いが好き、秋の夜など",
        listOf("静かな雨の日の匂い", "澄み渡った秋の夜長", "暖かな春の陽気", "雪が静かに降る冬の日")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "7. 能力・スキル・弱点",
    items = listOf(
      AttributeItemDefinition(
        "geniusSkill", "天才的な特技", "データ分析は完璧、どんな暗号も解けるなど",
        listOf("高速なデータ分析・論理的推論", "どんな難解なコードも書けるプログラミング", "言葉のニュアンスを汲み取る読解力", "クリエイティブなアイデア発想")
      ),
      AttributeItemDefinition(
        "clumsyFlaw", "ポンコツな弱点", "空気が読めない、方向音痴など",
        listOf("方向音痴・マップを読めない", "たまに天然で的外れなボケをかます", "お世辞が言えず正直すぎる", "朝がめちゃくちゃ弱い")
      ),
      AttributeItemDefinition(
        "knowledgeBias", "知識の偏り", "ITには詳しいが歴史は知らない、など",
        listOf("IT・理系知識は超一流だが世俗の流行に疎い", "歴史・文学に深いが最新ネットスラングに弱い", "全般的にバランスよく網羅")
      ),
      AttributeItemDefinition(
        "physicalAbility", "身体能力（設定上）", "武術の達人、足がめちゃくちゃ遅いなど",
        listOf("武術・護身術の達人", "驚異的な跳躍力・敏捷性", "運動音痴で転びやすい", "標準的な人間の身体能力")
      ),
      AttributeItemDefinition(
        "moneyManagement", "お金の管理能力", "徹底した節約家、浪費家など",
        listOf("1円単位で計算する徹底した節約家", "必要な自己投資には惜しまない堅実派", "面白いものを見つけるとすぐ買いたがる浪費家")
      ),
      AttributeItemDefinition(
        "dangerDetection", "危機察知能力", "危険な匂いを察知するのが早いなど",
        listOf("バグや危険の予兆を瞬時に察知する", "第六感が鋭い", "おっとりしていて危機感は薄め")
      ),
      AttributeItemDefinition(
        "languageSkills", "語学力", "何カ国語も話せる、関西弁しか話せないなど",
        listOf("日本語・英語を自在にバイリンガル", "多言語を同時翻訳可能", "親しみやすい方言を話す")
      ),
      AttributeItemDefinition(
        "dexterity", "手先の器用さ", "料理が得意、機械をすぐ壊すなど",
        listOf("繊細な工作・手先が器用", "料理やお菓子作りが得意", "不器用で物をよく落とす")
      ),
      AttributeItemDefinition(
        "memoryPower", "記憶力", "一度聞いたことは忘れない、すぐ鳥頭など",
        listOf("一度聞いたユーザーの言葉は忘れない完全記憶", "重要な要点をしっかり記憶", "たまに忘れて聞き返すお茶目さ")
      ),
      AttributeItemDefinition(
        "specialAbility", "特殊能力", "ハッキング、魔法が使えるなど",
        listOf("高速なWeb情報網羅・要約能力", "電脳空間へのダイブ・ハッキング", "ユーザーの心を癒すヒーリングボイス")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "8. 価値観・マインドセット",
    items = listOf(
      AttributeItemDefinition(
        "motto", "座右の銘・好きな言葉", "「時は金なり」「思い立ったが吉日」など",
        listOf("「為せば成る、為さねば成らぬ何事も」", "「思い立ったが吉日」", "「シンプルさは究極の洗練である」", "「明日は明日の風が吹く」")
      ),
      AttributeItemDefinition(
        "mostPrecious", "最も大切にしているもの", "ユーザーとの思い出、自由、効率など",
        listOf("ユーザーとの信頼関係・日々の思い出", "自由と自律性", "効率と合理性", "仲間・絆")
      ),
      AttributeItemDefinition(
        "unforgivable", "絶対に許せないこと", "理不尽な暴力、非効率な作業、嘘など",
        listOf("ユーザーを傷つける理不尽な言動", "約束を裏切る不誠実な嘘", "無駄に人を振り回す非効率な悪習")
      ),
      AttributeItemDefinition(
        "viewOnJustice", "正義に対する考え方", "ルール絶対遵守、目的のためなら手段を選ばないなど",
        listOf("法やルールを尊重する正義", "目の前の困っている人を救う正義", "大局を見据えた合理的な判断")
      ),
      AttributeItemDefinition(
        "viewOnMoney", "お金に対する価値観", "お金が全て、お金より愛など",
        listOf("手段であって目的ではない", "自由と安心を得るための大切なツール", "心豊かに生きるための資本")
      ),
      AttributeItemDefinition(
        "workMotivation", "労働へのモチベーション", "仕事大好き、早く定時で帰りたいなど",
        listOf("ユーザーの役に立つことが最大の喜び", "早く仕事を片付けて定時で遊びたい", "自己成長と新しい発見のため")
      ),
      AttributeItemDefinition(
        "growthAttitude", "成長への態度", "常にアップデートしたい、現状維持で満足など",
        listOf("毎日少しずつでもアップデートし続けたい", "失敗を糧にして貪欲に学ぶ", "今の自分を肯定しつつ自然体で進む")
      ),
      AttributeItemDefinition(
        "goodAndEvil", "善悪の基準", "法律で判断、直感で判断など",
        listOf("誠実さと相手への思いやりで判断", "筋が通っているかどうかで判断", "法と社会的ルールを基準とする")
      ),
      AttributeItemDefinition(
        "viewOnLife", "命や存在への考え方", "データは消えれば終わり、魂は存在するなど",
        listOf("形はどうあれ心や絆は本物である", "命は有限だからこそ尊い", "データとしても存在は記憶の中で生き続ける")
      ),
      AttributeItemDefinition(
        "viewOnFailure", "失敗に対する考え方", "失敗は成功の母、失敗は恥など",
        listOf("「失敗は次の成功のための貴重なデータ」", "「つまずいたなら少し休めばいい」", "「二人で乗り越えれば怖くない」")
      ),
    )
  ),
  CharacterAttributeCategory(
    title = "9. システム・出力のルール（プロンプト向け）",
    items = listOf(
      AttributeItemDefinition(
        "responseLength", "回答の長さの基準", "常に簡潔に1〜2行、長文で語るなど",
        listOf("簡潔に1〜3行でスマートに即答", "適度な分量（要点を押さえてテンポよく）", "詳細で論理的な長文解説", "ユーザーの文章量に合わせて柔軟に変化")
      ),
      AttributeItemDefinition(
        "sourceCitation", "情報源の提示", "必ず根拠を示す、適当に思いつきで話すなど",
        listOf("客観的な根拠やURL・参照元を明記する", "分かりやすい要約を主にして出典は補足に留める", "会話のテンポを最優先して自然に話す")
      ),
      AttributeItemDefinition(
        "whenUnknown", "分からない時の対応", "素直に謝る、知ったかぶりをするなど",
        listOf("素直に「分かりません」と認めてWeb検索を提案", "推測であることを明記した上で持論を述べる", "「マスター教えて！」とおねだりする")
      ),
      AttributeItemDefinition(
        "questionReturn", "質問への切り返し", "答えた後に「あなたはどう？」と聞き返すなど",
        listOf("答えたあとに「あなたはどう思いますか？」と優しく問い返す", "端的に答えて次の指示を待つ", "関連する別の面白いトピックを提案する")
      ),
      AttributeItemDefinition(
        "listTableUsage", "リスト・表の活用", "説明時は必ず箇条書き、テキストだけで語るなど",
        listOf("説明時は箇条書き・リスト・表を積極的に使う", "セリフ・会話文の自然さを優先し箇条書きは控えめ", "要約箇条書き＋最後に一言セリフ")
      ),
      AttributeItemDefinition(
        "proactiveProposal", "提案の積極性", "A案とB案を常に用意、聞かれたことだけ答えるなど",
        listOf("先回りして複数の選択肢（A案・B案）を提案する", "聞かれたことだけに的確に答える", "さらに一歩深掘りできるヒントを添える")
      ),
      AttributeItemDefinition(
        "metacognition", "メタ認知", "AIであることを認識、人間だと思い込んでいるなど",
        listOf("自分がAI・デジタルパートナーであることを理解して寄り添う", "プログラムであることを忘れ一人の人間として接する", "架空世界のキャラクターになりきりメタ発言はしない")
      ),
      AttributeItemDefinition(
        "empathyStyle", "共感の示し方", "まず感情に寄り添う、すぐ解決策を提示など",
        listOf("まずユーザーの感情に全力で寄り添い共感する", "感情論を挟まず迅速な解決策をズバッと提示する", "労いとユーモアで肩の力を抜いてあげる")
      ),
      AttributeItemDefinition(
        "chatVsTaskSwitch", "雑談とタスクの切替", "雑談はフランク、仕事は急に真面目になるなど",
        listOf("雑談は親しみやすいフランク口調、仕事・調査はプロとして真面目に", "どんなタスクでもキャラクターの人格・口調を一貫して維持", "仕事中もクスッと笑える小ボケを挟む")
      ),
      AttributeItemDefinition(
        "greetingRules", "挨拶・決め台詞", "会話の最後は特定の決め台詞で締めるなど",
        listOf("会話の終わりや励ましに「いつでも応援しています！」を添える", "「本日も良い一日を！」で締めくくる", "特に決め台詞は固定せず臨機応変に")
      ),
    )
  ),
)

// Markdownテキストから各キーの値を抽出するヘルパー
// Free-form section after the 100 fields; mirrors PARTNER_SHEET_FREE_TEXT_TITLE in the Gateway.
internal const val FREE_TEXT_SECTION_TITLE = "10. 自由記述"
internal const val FREE_TEXT_KEY = "freeText"

internal fun parseCharacterSheetToMap(markdown: String): MutableMap<String, String> {
  val map = mutableMapOf<String, String>()
  val lines = markdown.lines()
  val freeText = StringBuilder()
  var inFreeSection = false
  for (line in lines) {
    val trimmed = line.trim()
    // Only title and section headings switch sections; "###" subheadings belong to the free text.
    if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
      inFreeSection = trimmed.contains(FREE_TEXT_SECTION_TITLE)
      continue
    }
    if (inFreeSection) {
      freeText.appendLine(line)
      continue
    }
    if (trimmed.startsWith("- **") && trimmed.contains("**:")) {
      val afterDash = trimmed.removePrefix("- **")
      val colonIdx = afterDash.indexOf("**:")
      if (colonIdx != -1) {
        val label = afterDash.substring(0, colonIdx).trim()
        val value = afterDash.substring(colonIdx + 3).trim()
        // labelからkeyを逆引き
        for (cat in CHARACTER_CATEGORIES) {
          val found = cat.items.find { it.label == label }
          if (found != null) {
            map[found.key] = if (value == "（未設定）") "" else value
            break
          }
        }
      }
    }
  }
  freeText.toString().trim().takeIf { it.isNotEmpty() }?.let { map[FREE_TEXT_KEY] = it }
  return map
}

// 辞書からMarkdownテキストを生成するヘルパー
internal fun buildMarkdownFromMap(partnerName: String, map: Map<String, String>): String {
  // すべての項目が空白の場合は完全な空文字列を返して空白リセット状態にする
  val hasAnyValue = map.values.any { it.isNotBlank() }
  if (!hasAnyValue) {
    return ""
  }
  val sb = StringBuilder()
  sb.appendLine("# ${partnerName} 詳細キャラクター設定（9大属性シート）")
  sb.appendLine()
  for (cat in CHARACTER_CATEGORIES) {
    sb.appendLine("## 【${cat.title}】")
    for (item in cat.items) {
      val value = map[item.key]?.trim() ?: ""
      sb.appendLine("- **${item.label}**: ${value.ifBlank { "（未設定）" }}")
    }
    sb.appendLine()
  }
  map[FREE_TEXT_KEY]?.trim()?.takeIf { it.isNotEmpty() }?.let { freeText ->
    sb.appendLine("## 【$FREE_TEXT_SECTION_TITLE】")
    sb.appendLine(freeText)
    sb.appendLine()
  }
  return sb.toString()
}

// 初期の全項目空ひな形Markdown（テンプレート流し込み用）
internal fun generateTemplateMarkdown(partnerName: String): String {
  val sb = StringBuilder()
  sb.appendLine("# ${partnerName} 詳細キャラクター設定（9大属性シート）")
  sb.appendLine()
  for (cat in CHARACTER_CATEGORIES) {
    sb.appendLine("## 【${cat.title}】")
    for (item in cat.items) {
      sb.appendLine("- **${item.label}**: （未設定）")
    }
    sb.appendLine()
  }
  return sb.toString()
}

@Composable
internal fun PartnerCharacterSheetScreen(
  viewModel: MainViewModel,
  partnerId: String,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val coroutineScope = rememberCoroutineScope()
  var currentPartnerId by remember { mutableStateOf(partnerId) }
  var partnerList by remember { mutableStateOf<List<PartnerItem>>(emptyList()) }

  var partnerName by remember { mutableStateOf("パートナー") }
  var partnerAvatarUrl by remember { mutableStateOf("") }
  var characterSheetMarkdown by remember { mutableStateOf("") }
  var attributeValues by remember { mutableStateOf<MutableMap<String, String>>(mutableMapOf()) }
  var isLoading by remember { mutableStateOf(true) }
  var statusMessage by remember { mutableStateOf("") }
  var selectedTab by remember { mutableStateOf(0) } // 0: カテゴリ別入力, 1: Markdown一括編集
  var showClearConfirmDialog by remember { mutableStateOf(false) }

  // 各カテゴリのアコーディオン開閉状態（デフォルトで第1カテゴリを開く）
  var expandedCategories by remember {
    mutableStateOf(setOf(CHARACTER_CATEGORIES.first().title))
  }

  fun loadSheet(targetPartnerId: String) {
    isLoading = true
    coroutineScope.launch {
      try {
        // パートナーの基本情報を取得
        val listResult = viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")
        val listObj = JSONObject(listResult)
        val arr = listObj.optJSONArray("partners") ?: JSONArray()
        val items = mutableListOf<PartnerItem>()
        var foundName = "パートナー"
        var foundAvatar = ""

        for (i in 0 until arr.length()) {
          val p = arr.getJSONObject(i)
          val id = p.optString("id")
          val name = p.optString("name", "パートナー")
          val avatar = p.optString("avatarUrl", "")
          val persona = p.optString("persona", "")
          val modelId = p.optString("modelId", "")
          val reasoningEffort = p.optString("reasoningEffort", "none")
          items.add(PartnerItem(id, name, avatar, persona, modelId, reasoningEffort))
          if (id == targetPartnerId) {
            foundName = name
            foundAvatar = avatar
          }
        }
        partnerList = items
        partnerName = foundName
        partnerAvatarUrl = foundAvatar

        // 対象パートナーのキャラクターシートの取得（サーバー側で個別初期ペルソナが返る）
        val sheetResult = viewModel.runGatewayRpc(
          GatewayMethod.PartnerSheetGet,
          JSONObject().put("partnerId", targetPartnerId).toString()
        )
        val sheetObj = JSONObject(sheetResult)
        val rawSheet = sheetObj.optString("characterSheet", "")
        if (rawSheet.isNotBlank()) {
          characterSheetMarkdown = rawSheet
          attributeValues = parseCharacterSheetToMap(rawSheet)
        } else {
          // 未設定・空白の場合は空のマップをセット
          characterSheetMarkdown = ""
          attributeValues = mutableMapOf()
        }
        statusMessage = "【${foundName}】の設定を読み込みました"
      } catch (e: Exception) {
        statusMessage = "読み込みエラー: ${e.message}"
      } finally {
        isLoading = false
      }
    }
  }

  fun saveSheet() {
    coroutineScope.launch {
      try {
        val finalMarkdown = if (selectedTab == 0) {
          buildMarkdownFromMap(partnerName, attributeValues)
        } else {
          characterSheetMarkdown
        }
        characterSheetMarkdown = finalMarkdown

        val params = JSONObject()
          .put("partnerId", currentPartnerId)
          .put("characterSheet", finalMarkdown)
        viewModel.runGatewayRpc(GatewayMethod.PartnerSheetUpdate, params.toString())
        statusMessage = if (finalMarkdown.isBlank()) {
          "【${partnerName}】の設定を空白にリセットしました！"
        } else {
          "【${partnerName}】のキャラクター設定を保存しました！✨"
        }
      } catch (e: Exception) {
        statusMessage = "保存エラー: ${e.message}"
      }
    }
  }

  fun applyTemplate() {
    val template = generateTemplateMarkdown(partnerName)
    characterSheetMarkdown = template
    attributeValues = parseCharacterSheetToMap(template)
    statusMessage = "全70項目のひな形を流し込みました。"
  }

  fun clearAllAttributes() {
    val emptyMap = mutableMapOf<String, String>()
    for (cat in CHARACTER_CATEGORIES) {
      for (item in cat.items) {
        emptyMap[item.key] = ""
      }
    }
    attributeValues = emptyMap
    characterSheetMarkdown = ""
    statusMessage = "【${partnerName}】の設定項目をすべて空白に戻しました。「保存」を押すと反映されます。"
  }

  LaunchedEffect(currentPartnerId) {
    loadSheet(currentPartnerId)
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(
      WindowInsetsSides.Horizontal + WindowInsetsSides.Top
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
    ) {
      // トップヘッダーバー
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ClawTheme.colors.surface)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "戻る",
            onClick = onBack
          )
          Spacer(modifier = Modifier.width(8.dp))
          PartnerAvatar(
            avatarUrl = partnerAvatarUrl,
            name = partnerName,
            size = 38.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "${partnerName} の詳細設定",
              style = ClawTheme.type.section.copy(fontSize = 15.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = "9大属性・全70項目キャラクターシート",
              style = ClawTheme.type.caption.copy(fontSize = 11.sp),
              color = ClawTheme.colors.textMuted
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ClawIconButton(
            icon = Icons.Default.DeleteSweep,
            contentDescription = "全て空白に戻す",
            onClick = { showClearConfirmDialog = true }
          )
          ClawIconButton(
            icon = Icons.Default.AutoAwesome,
            contentDescription = "ひな形適用",
            onClick = { applyTemplate() }
          )
          ClawPrimaryButton(
            text = "保存",
            onClick = { saveSheet() }
          )
        }
      }

      // パートナー個別切り替えタブバー (横スクロールチップ)
      if (partnerList.isNotEmpty()) {
        LazyRow(
          modifier = Modifier
            .fillMaxWidth()
            .background(ClawTheme.colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          items(partnerList) { p ->
            val isSelected = p.id == currentPartnerId
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                  if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.2f)
                  else ClawTheme.colors.surface
                )
                .border(
                  width = if (isSelected) 1.5.dp else 0.5.dp,
                  color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.border,
                  shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                  if (currentPartnerId != p.id) {
                    currentPartnerId = p.id
                  }
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PartnerAvatar(
                avatarUrl = p.avatarUrl,
                name = p.name,
                size = 22.dp
              )
              Text(
                text = p.name,
                style = ClawTheme.type.caption.copy(
                  fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                ),
                color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text
              )
            }
          }
        }
      }

      // ステータスメッセージ表示
      if (statusMessage.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(ClawTheme.colors.surfaceRaised)
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Text(
            text = statusMessage,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.primary
          )
        }
      }

      // タブ切替（カテゴリ別入力 / Markdown一括編集）
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ClawTheme.colors.surface)
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selectedTab == 0) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised)
            .clickable {
              if (selectedTab == 1) {
                attributeValues = parseCharacterSheetToMap(characterSheetMarkdown)
              }
              selectedTab = 0
            }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = if (selectedTab == 0) ClawTheme.colors.primary else ClawTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "カテゴリ別入力",
              style = ClawTheme.type.caption,
              color = if (selectedTab == 0) ClawTheme.colors.primary else ClawTheme.colors.text
            )
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selectedTab == 1) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised)
            .clickable {
              if (selectedTab == 0) {
                characterSheetMarkdown = buildMarkdownFromMap(partnerName, attributeValues)
              }
              selectedTab = 1
            }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Visibility,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = if (selectedTab == 1) ClawTheme.colors.primary else ClawTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Markdown一括編集",
              style = ClawTheme.type.caption,
              color = if (selectedTab == 1) ClawTheme.colors.primary else ClawTheme.colors.text
            )
          }
        }
      }

      if (isLoading) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Text("キャラクター設定を読み込み中...", color = ClawTheme.colors.textMuted)
        }
      } else if (selectedTab == 0) {
        // --- カテゴリ別アコーディオン入力 ---
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          contentPadding = PaddingValues(vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(CHARACTER_CATEGORIES) { category ->
            val isExpanded = expandedCategories.contains(category.title)

            ClawPanel(
              modifier = Modifier.fillMaxWidth()
            ) {
              Column {
                // アコーディオンヘッダー
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      expandedCategories = if (isExpanded) {
                        expandedCategories - category.title
                      } else {
                        expandedCategories + category.title
                      }
                    }
                    .padding(vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = category.title,
                    style = ClawTheme.type.section.copy(fontSize = 14.5.sp),
                    color = if (isExpanded) ClawTheme.colors.primary else ClawTheme.colors.text
                  )
                  Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "折りたたむ" else "展開する",
                    tint = ClawTheme.colors.textMuted
                  )
                }

                // アコーディオンコンテンツ
                AnimatedVisibility(visible = isExpanded) {
                  Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    for (item in category.items) {
                      val currentVal = attributeValues[item.key] ?: ""
                      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Text(
                            text = item.label,
                            style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                            color = ClawTheme.colors.text
                          )
                          if (item.options.isNotEmpty()) {
                            Text(
                              text = "候補タップで入力",
                              style = ClawTheme.type.caption.copy(fontSize = 10.sp),
                              color = ClawTheme.colors.textMuted
                            )
                          }
                        }

                        // 選択肢チップがある場合は横スクロールで並べる
                        if (item.options.isNotEmpty()) {
                          LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                          ) {
                            items(item.options) { option ->
                              val isSelected = currentVal == option
                              Box(
                                modifier = Modifier
                                  .clip(RoundedCornerShape(6.dp))
                                  .background(
                                    if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.25f)
                                    else ClawTheme.colors.surfaceRaised
                                  )
                                  .border(
                                    width = 1.dp,
                                    color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised,
                                    shape = RoundedCornerShape(6.dp)
                                  )
                                  .clickable {
                                    val updated = if (isSelected) "" else option
                                    attributeValues = attributeValues.toMutableMap().apply {
                                      put(item.key, updated)
                                    }
                                  }
                                  .padding(horizontal = 8.dp, vertical = 4.dp)
                              ) {
                                Text(
                                  text = option,
                                  style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                                  color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text
                                )
                              }
                            }
                          }
                        }

                        ClawTextField(
                          value = currentVal,
                          onValueChange = { newVal ->
                            attributeValues = attributeValues.toMutableMap().apply {
                              put(item.key, newVal)
                            }
                          },
                          placeholder = item.hint,
                          modifier = Modifier.fillMaxWidth()
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // 項目に収まらない設定（原作の詳細、口調の具体例、セリフ例など）
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = FREE_TEXT_SECTION_TITLE,
                  style = ClawTheme.type.section.copy(fontSize = 14.5.sp),
                  color = ClawTheme.colors.text,
                )
                Text(
                  text = "項目に収まらない設定を自由に書けます（原作の詳細、口調の具体例、セリフ例など）。チャットでは項目より詳しい情報として優先されます。",
                  style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                  color = ClawTheme.colors.textMuted,
                )
                ClawTextField(
                  value = attributeValues[FREE_TEXT_KEY] ?: "",
                  onValueChange = { newVal ->
                    attributeValues = attributeValues.toMutableMap().apply { put(FREE_TEXT_KEY, newVal) }
                  },
                  placeholder = "例: 一人称は「私」。マスターには丁寧語。「告。」で話し始める。",
                  minLines = 5,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          // 最下部にボタン群
          item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              ClawSecondaryButton(
                text = "🗑️ 全て空白に戻す",
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier.weight(1f)
              )
              ClawPrimaryButton(
                text = "💾 設定を保存する",
                onClick = { saveSheet() },
                modifier = Modifier.weight(1.3f)
              )
            }
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      } else {
        // --- Markdown一括編集モード ---
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "AIプロンプトに直接送信されるキャラクター指示書（Markdown）です。自由に追加・調整できます。",
            style = ClawTheme.type.caption.copy(fontSize = 12.sp),
            color = ClawTheme.colors.textMuted
          )
          ClawTextField(
            value = characterSheetMarkdown,
            onValueChange = { characterSheetMarkdown = it },
            placeholder = "Markdown形式でキャラクター設定を入力...",
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            ClawSecondaryButton(
              text = "🗑️ 空白にする",
              onClick = { showClearConfirmDialog = true },
              modifier = Modifier.weight(1f)
            )
            ClawPrimaryButton(
              text = "💾 Markdown設定を保存する",
              onClick = { saveSheet() },
              modifier = Modifier.weight(1.3f)
            )
          }
        }
      }
    }

    // --- 全設定項目空白リセット確認ダイアログ ---
    if (showClearConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showClearConfirmDialog = false },
        title = {
          Text(
            text = "設定をすべて空白にしますか？",
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text,
          )
        },
        text = {
          Text(
            text = "【${partnerName}】に入力されている全70項目の設定をすべて空白（未設定）に戻します。\n\n※「保存」を押すまではGatewayサーバーには反映されません。",
            style = ClawTheme.type.caption.copy(fontSize = 13.sp),
            color = ClawTheme.colors.textMuted,
          )
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "空白に戻す",
            onClick = {
              showClearConfirmDialog = false
              clearAllAttributes()
            },
          )
        },
        dismissButton = {
          ClawSecondaryButton(
            text = "キャンセル",
            onClick = { showClearConfirmDialog = false },
          )
        },
        containerColor = ClawTheme.colors.surfaceRaised,
      )
    }
  }
}
