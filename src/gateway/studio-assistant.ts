import * as fs from "node:fs/promises";
import * as path from "node:path";
import { assertAgeAllowed } from "./blocked-terms-config.js";
import {
  characterOptionIds,
  cleanOutfits,
  cleanSpec,
  getCharacter,
  listCharacters,
  registerCharacter,
  saveCharacter,
  type LibraryCharacter,
} from "./comic-characters.js";
import {
  getComicSeries,
  listComicSeries,
  newComicSeries,
  saveComicSeries,
  type ComicCharacter,
} from "./comic-series.js";
import { createDevRequest, listDevRequests } from "./crie-dev.js";
import { shelfFromWeb } from "./crie-shelf.js";
import { addCustomMotion, removeCustomMotion } from "./custom-motions.js";
import { listSwapInputs } from "./face-swap.js";
import { IRODORI_EMOJI_GUIDE } from "./irodori-tts.js";
import { mediaProductionOptionsFromParams } from "./media-production-controls.js";
import {
  adoptLatestSheet,
  assertAllowedRequest,
  deleteMediaJob,
  estimateFaceSwapJob,
  estimateMediaJob,
  mediaStudioStatus,
  listMediaJobs,
  getMediaJob,
  startMediaJob,
  writeCharacterLook,
  writeComicCast,
  type MediaJob,
  type MediaKind,
  type MediaOptions,
} from "./media-studio.js";
import { motionCatalog } from "./motion-catalog.js";
import {
  MOTION_MINUTES,
  MOTION_OPTION_IDS,
  MOTION_OPTIONS,
  MOTION_TEMPLATE_IDS,
  MOTION_TEMPLATES,
} from "./motion-comic.js";
import { motionExtrasFromParams, voiceDramaFromParams } from "./motion-extras.js";
import type { ResearchIO } from "./partner-research.js";
import { resolveOpenClawHome } from "./partner-store.js";
import {
  addCustomSetting,
  findSettings,
  loadCustomSettings,
  loadSampleIndex,
  resolveSampleSubject,
  saveSampleIndex,
} from "./sample-factory.js";
import { normalizeVoiceCaption } from "./voice-caption.js";
import {
  listVoiceProjects,
  queueVoiceTraining,
  type VoiceProject,
  type VoiceStatus,
} from "./voice-studio.js";

// The generation assistant: what the media studio (images, videos, music) and the voice studio
// can do from a conversation. The main chat's agent reaches it through the media_studio tool. A
// 1:1 partner chat runs on a model that cannot call tools, so a partner with
// settings.studioAssistant gets the studio's state in its prompt and writes one
// <<studio {...}>> line, which the gateway runs after the reply.

export const STUDIO_ACTIONS = [
  "generate",
  "status",
  "cancel",
  "estimate",
  "options",
  "voices",
  "train_voice",
] as const;
/** Actions only a 1:1 partner chat writes (the agent tool keeps STUDIO_ACTIONS). */
export const STUDIO_CHAT_ACTIONS = [
  ...STUDIO_ACTIONS,
  "character_create",
  "character_update",
  "motion_continue",
  "motion_redraw",
  "character_sheet",
  "character_train",
  "comic_series",
  "comic_episode",
  "faceswap",
  "faceswap_inputs",
  "option_find",
  "sample_make",
  "sample_default",
  "custom_add",
  "custom_list",
  "shelf_add",
  "motion_find",
  "motion_add",
  "motion_remove",
  "motion_join",
  "motion_branch",
  "dev_request",
  "dev_status",
] as const;
export const STUDIO_KINDS = ["image", "video", "music", "manga", "voice", "motion"] as const;
export const STUDIO_MANGA_LAYOUTS = ["4koma", "page", "book"] as const;
export const STUDIO_IMAGE_MODELS = [
  "wai",
  "noobai",
  "zimage",
  "anima",
  "klein",
  "qwenimg",
  "qwenimgturbo",
  "asianreal",
  "lustify",
  "cyberreal",
  "hassaku",
  "illustrious",
  "novaanime",
  "plantmilk",
  "juggernaut",
  "realvis",
  "realismil",
  "illust2",
  "animagine",
  "rouwei",
  "chenkin",
  "janku",
  "ntrmix",
  "desire",
  "cyberanime",
  "cybersemi",
  "pencil",
  "cyberpony",
] as const;
export const STUDIO_VIDEO_ENGINES = ["standard", "remix", "long", "audio", "fast", "h3"] as const;
export const STUDIO_ASPECTS = ["portrait", "square", "landscape"] as const;

/** What the studio offers, with typical times, so the assistant can advise before generating. */
export const STUDIO_GUIDE = `画像モデル（2枚目以降の目安）:
- wai: 人気のアニメ絵。約25秒（fast で約12秒）
- noobai: アニメの成人向けで評価が高い。体の描写が正確。30秒〜1分
- zimage: 文章の指示に強く構図が正確。日本人の実写も自然。約50秒
- anima: アニメ絵専用の新しいモデル（2B）。タグでも文章でも指示できる。約75秒（fast で約45秒）。起動に少し時間がかかる
- klein: FLUX.2 Klein 9B。文章の指示に強い高速モデル。アニメも実写も描ける。約1分（初回はダウンロード）
- qwenimg: Qwen Image 2.1。日本語の指示と細かい描写に強い。約1分20秒（初回はダウンロード）
- qwenimgturbo: Qwen Image 2.1 Turbo。公式8ステップで高速。サンプル、表情・衣装差分、ポーズ・アングル、モーションコミック素材、透明背景に向く。約45秒（初回はTurboチェックポイントをダウンロード）
- asianreal: 日本人・アジア系の実写。約25秒（fast で約12秒）
- lustify / cyberreal: 実写の制限解除版。約25秒（fast で約12秒）
- hassaku / illustrious / novaanime / plantmilk: アニメ絵の別系統（癖のない明るい絵／本家の土台／2.5D寄り／混ぜモデル）。約30秒
- juggernaut / realvis: 実写の定番と人物写真。約30秒
- realismil: 半実写（アニメ寄りの実写）。約30秒
- illust2: 1.1の後継。構図とタグの理解がよい。 約30秒
- animagine: キャラ名タグの再現に強い定番。 約30秒
- rouwei: 色と構図が良く、成人向けも出しやすい。 約30秒
- chenkin: AIらしさが少ない、NoobAI系の新しい絵。 約30秒
- janku: 高精細で背景・衣装が細かい。成人向けも可。 約30秒
- ntrmix: 濃いめの塗りで、成人向けに強い。 約30秒
- desire: 成人向けのアニメ・同人調に特化。 約30秒
- cyberanime: 光と肌の質感が自然なアニメ絵。 約30秒
- cybersemi: アニメ寄りの顔で、質感は実写寄り。 約30秒
- pencil: 日本のイラスト寄りの、やわらかい絵。 約30秒
- cyberpony: Pony 系の実写。成人向けに強く、ポーズが多彩。 約30秒
動画の作り方（文章からなら最初の1枚も描く。sourceImageJobId で完成した絵を動かせる）:
- standard: 制限解除版。2秒・約5分 / remix: 実写向け、動き大きめ。2秒・約7分
- long: 5〜30秒、崩れにくい / audio: 音付き（LTX-2）〜10秒 / fast: 〜5秒を速く / h3: 実写の映像と声・環境音。5秒で約9分
- smooth=true で2倍なめらか（+約1分）
音楽: 30〜120秒、歌あり（歌詞は省略可）か曲だけ。数分。
漫画: お題から脚本（コマ割り・台詞）を考え、コマを絵にして縦書きの吹き出しを入れる。4koma＝縦の4コマ（約3〜4分）/ page＝5コマの1ページ（約4〜5分）。画像モデルと fast も選べる。同じ人物の顔はコマごとに少し変わることがある。
声: 声まね＝録音の出だし10秒ですぐ話せる / 夜の学習＝録音合計10〜30分でより自然に / 声デザイン＝文章で説明して合成の声を作る（アプリの声作りスタジオ）
声の生成（kind voice）: 声作りスタジオの声（voiceId）か声の説明（voiceCaption）で、セリフや短い台本を音声ファイルにする。Irodori-TTS の large（高品質・GPUを使う・1行数秒＋読み込み約1分）か small（速いが平板）。1行ずつ改行し、1行は30秒以内。成人向けの喘ぎ・吐息・囁きも絵文字で演じられる。
${IRODORI_EMOJI_GUIDE}
キャラ図鑑と連載コミック: キャラを図鑑に登録して設定画（全身の基準絵、約30秒）を描く／設定画からキャラ専用の学習（LoRA、約1時間。顔がコマごとに変わらなくなる）／連載コミックを新しく作る（登場人物はAIが考えるか、図鑑のキャラを使う）／新しい話（1話100コマ、約25分）を作る。
モーションコミック（kind motion）: 成人向けの濡れ場を、動画AIの短いカット（約3秒）をつないだ動画にする。流れは ゆっくり→激しく→山場→絶頂→射精（中出しか外出し）→余韻、希望があれば暗転してフェラ→口に射精。胸の揺れ・腹のふくらみ・手足やつま先・表情が実際に動く。台本・声・絵・動画は自動。motionMinutes は 1/3/5（分）。時間がかかる（1分ものでも約1.5〜2時間、カット1本約11分）。相談で決めること: キャラ（名前・年齢・体型・髪・目・衣装・性格と口調）、場所、体位、終わり方、フェラの有無、声（声作りスタジオの声か、声の説明）。資料棚の「モーションコミックの作り方」も参照。
何も作っていないときの1つ目は、準備（チャットのAIを止めて生成モデルを読み込む）で+約1分。
生成と学習はチャットの返事のあとに始まり、その間チャットのAIは止まる（終わると戻る）。`;

/** How a 1:1 partner chat asks the gateway to act (its model cannot call tools). */
export const STUDIO_CHAT_RULES = `【生成スタジオの操作（あなたが実際に動かせる）】
- ユーザーが作る・止める・声の学習を頼み、内容と設定が決まったら、返事の最後に次の形式の行を1つだけ書く。相談中・雑談・設定の確認では書かない。
<<studio {"action":"generate","kind":"image","prompt":"作りたい内容（日本語でよい）","imageModel":"wai","aspect":"portrait","fast":true}>>
<<studio {"action":"generate","kind":"video","prompt":"どう動くか","engine":"standard","sourceImageJobId":"動かす完成画像のid（文章から作るなら省く）"}>>
<<studio {"action":"generate","kind":"music","prompt":"ジャンル・テンポ・楽器・声","durationSec":90,"instrumental":false}>>
<<studio {"action":"generate","kind":"manga","prompt":"お題・登場人物・オチ","mangaLayout":"4koma","imageModel":"wai","fast":true}>>（mangaLayout は 4koma / page=5コマの1ページ / book=本格的な複数ページ。book のときは "mangaPages":8（2〜32）と、白黒にするなら "mangaTone":"mono" も付ける）
<<studio {"action":"generate","kind":"voice","prompt":"読ませるセリフ（1行ずつ改行、絵文字で演技）","voiceId":"【声作りスタジオの声】のid","irodoriModel":"large"}>>
<<studio {"action":"generate","kind":"voice","prompt":"物語のお題（どんな場面・誰が・どう進むか）","voiceId":"声のid","voiceDrama":{"minutes":10,"ambience":"rain"}}>>（音声ドラマ：お題から長い台本を書き、行ごとの演技指定（囁き・喘ぎの強さ）つきで声にして、背景の音を重ねる。minutes は1〜30分、ambience は none / rain / night / room / bath / wind。irodoriModel は "fast"（速い）か "small" がおすすめ）
<<studio {"action":"generate","kind":"motion","prompt":"相談で決めた内容（キャラの名前・年齢・体型・髪・目・衣装・性格と口調、場所、体位、終わり方）","motionMinutes":1,"motionFellatio":true,"character":"【キャラ図鑑】の名前（図鑑のキャラで作るとき。漫画と同じ顔になる）","draft":true,"template":"${Object.entries(
  MOTION_TEMPLATES,
)
  .map(([id, template]) => `${id} ${template.label}`)
  .join(
    " / ",
  )}","outfit":"キャラ図鑑のキャラの衣装名（省けばいつもの服）","options":["オプションのid（下の一覧から、いくつでも）"],"voiceCaption":"声の説明（声作りスタジオの声なら voiceId）"}>>
<<studio {"action":"character_create","name":"名前","description":"見た目のまとめ（日本語）","spec":{"age":25,"heightCm":160,"body":"体型","hair":"髪","eyes":"目","outfit":"服装","personality":"性格","speech":"口調","firstPerson":"一人称","catchphrase":"口癖","likes":"好きなもの","dislikes":"苦手なもの","adultLikes":"好きなこと・体位","weakSpots":"弱いところ","kinks":"性癖","moanStyle":"喘ぎ方","adultNg":"NG"}}>>
<<studio {"action":"character_update","character":"【キャラ図鑑】の名前かid","spec":{"catchphrase":"直す項目だけ書く"},"voiceId":"【声作りスタジオの声】のid（キャラの声にするとき）","voiceCaption":"声の説明（声を説明で決めるとき）","outfits":[{"name":"衣装名","tags":"英語の服のタグ"}]}>>
<<studio {"action":"character_sheet","character":"【キャラ図鑑】の名前かid","outfit":"衣装名（省けばいつもの服）"}>>
<<studio {"action":"character_train","character":"【キャラ図鑑】の名前かid"}>>
<<studio {"action":"comic_series","title":"題名","genre":"ジャンル","premise":"世界観と話の設定","rating":"adult","characters":["図鑑のキャラの名前（省けばAIが考える）"]}>>
<<studio {"action":"comic_episode","series":"【連載コミック】の題名かid","prompt":"今回の話の要望"}>>
<<studio {"action":"motion_branch","jobId":"完成したモーションコミックのid","atSec":40,"template":"cowgirl","seconds":6,"request":"どんな続きか（省略可）"}>>（その動画の途中（atSec 秒）から、同じ人物・同じ場所のまま別の体位（template）に切り替えて続きを作り、間をAIがつないで1本にする。体位の id は motion_find で調べる）
<<studio {"action":"dev_request","title":"依頼の題名","description":"何をどうしたいか。調べたこと・参考にするもの","githubUrl":"https://github.com/ユーザー/リポジトリ（省略可）"}>>（このプロジェクトの改良や新機能の追加を、自分で作る。設定の「クリエ開発機能」が入のときだけ使える。インターネット検索OK、GitHubのプロジェクトを読み込んで組み込み・改造もできる。作業は本体のコピーで行い、ユーザーが確認して「適用」を押すまで本体は変わらない）
<<studio {"action":"dev_status"}>>（開発の依頼の進み具合）
<<studio {"action":"motion_join","jobIds":["完成した動画のid","次の動画のid"],"seconds":6}>>（完成した動画を順につなぐ。間は、前の最後の絵と次の最初の絵をAIが解析して、体位の移動・抜き差し・場所の移動などを生成して入れる。seconds はつなぎの長さ（2〜60秒、既定3秒）。つないだ結果も動画なので、さらにつなげる）
- モーションコミックの追加オプション（generate の kind motion に書ける）: motionTalk＝セリフの量（broken ぶっ壊れ / wordless 言葉にならない / wild やばい / many 沢山 / normal 普通 / few 少ない / silent 無口 / tuna マグロ）、motionVoiceStyles＝声の種類の配列（oho おほ声 / pig 豚声 / cry 泣き声 / yell 喚き / scream 絶叫 / beast 鳴き声 / whisper 囁き / melt とろけ声 / growl 唸り）、motionSfx＝効果音の配列（thrust 打ち付け / spank はたく / wet 濡れた音）、motionFlow＝カットの順番（slow fast build climax cum after fellatio mouth を好きな順・繰り返しで。省くと標準の流れ）、motionInheritJobId＝完成したモーションコミックの id（出演者・声・見た目・場所を引き継ぐ）、motionImageModel＝絵のモデル、motionPreview＝false にすると確認用の絵を作らず続けて作る（既定は、最初に確認用の絵を1枚作って止まり、よければ motion_continue で続きを作る）。
- どの生成にも qualityMode を指定できる（off / balanced / max）。画像は高画質化、動画はノイズと輪郭、音声と音楽は音量を仕上げる。characterLock でキャラ図鑑の人物を固定し、sceneState で場所・カメラ・姿勢・衣服・続きの状態を固定できる。voiceTimeline ではセリフや効果音の開始秒と音量を指定できる。
<<studio {"action":"motion_continue","jobId":"モーションコミックの下書きのid"}>>
<<studio {"action":"motion_redraw","jobId":"モーションコミックの下書きのid","keys":["k1"]}>>
<<studio {"action":"cancel","jobId":"【生成スタジオの今の状況】のid"}>>
<<studio {"action":"train_voice","voiceId":"【生成スタジオの今の状況】の声のid","now":true}>>
<<studio {"action":"status"}>>（PCの状態：作業中の内容・GPU・チャットAIが止まっているか）
<<studio {"action":"option_find","query":"ブーツ"}>>（外見・モーション・体位の項目を、言葉から探して id を返す。キャラの外見に項目を足す前に使う）
<<studio {"action":"character_update","character":"クリエ","addOptions":["opt:cat_ears"],"removeOptions":[]}>>（外見オプションを足す・外す。id は option_find で調べる。「ケモミミを足して」は、option_find で「耳」を探し、ユーザーに種類を聞いてから足す。足したら、見た目のタグが更新されるので、絵は character_sheet で描き直す）
<<studio {"action":"custom_add","genre":"自分で追加","label":"項目の名前","instruction":"どんな絵にするか（日本語でよい）"}>>（一覧にない項目を、名前と指示で新しく足す。コードの変更もビルドも要らない。足した項目は、サンプル作成やキャラの外見に使える）
<<studio {"action":"custom_list"}>>（自分で足した項目の一覧）
<<studio {"action":"shelf_add","query":"調べたい言葉","title":"資料の名前（省いてよい）"}>>（インターネットで調べて、自分の資料棚に入れる。アドレスを渡すなら "url":"https://…" を使う。入れた資料は、あとの会話で自動で参照される。ユーザーに頼まれたとき、または知らないことを調べておきたいときに使う）
<<studio {"action":"motion_find","query":"騎乗"}>>（モーション・体位・オプションを言葉から探して id を返す。足す前に、似たものがないか調べるのに使う）
<<studio {"action":"motion_add","kind":"pose","label":"体位の名前","group":"分類","base":"似ている既存の体位のid","position":"姿勢（英語のタグ）","camera":"カメラ（英語）","slow":"ゆっくりの動き（英語の文）","fast":"激しい動き（英語の文）"}>>（新しい体位を足す。base の設定を土台に、書いた項目だけ置き換える。足したあとはモーション生成・サンプル・カタログで使える）
<<studio {"action":"motion_add","kind":"option","label":"オプションの名前","group":"分類","tags":"絵のタグ（英語）","motion":"動きに足す文（英語）"}>>（新しいオプションを足す）
<<studio {"action":"motion_remove","id":"cp-… か cm-…"}>>（自分で足した体位・オプションを消す。標準のものは消せない）
- モーション・体位を足すときは、相談しながら決める：どんな姿勢か・どんな動きか・似ている既存の体位は何かを聞き、英語のタグと文を考えて見せ、ユーザーが「それでいい」と言ってから motion_add の行を書く。
<<studio {"action":"sample_make","key":"opt:sh_boots_long","base":"normal","note":"赤色"}>>（項目の見本を、クリエで作る。base は adult で成人向け）
<<studio {"action":"sample_default","key":"opt:sh_boots_long","note":"赤色、ひざ下まで"}>>（項目の既定の指示を保存。noteを空にすると消す）
<<studio {"action":"faceswap_inputs"}>>（顔入れ替えの素材の一覧）
<<studio {"action":"faceswap","target":"素材の名前かid","characters":["図鑑のキャラ名"],"positions":[0],"quality":"standard","enhancer":false}>>（動画や画像の顔を、図鑑のキャラの顔に入れ替える。長い作業なので、かならず目安時間を伝えて、ユーザーが「はい」と言ってから書く）
- 行を書かずに「始めた」「学習した」「作っている」と言ってはいけない。動かせるのは上の行だけ。上にない操作は「わたしからはまだできない」と正直に言う。
- 設定シートの項目（spec）: age 年齢/heightCm 身長/body 体型/hair 髪/eyes 目/skin 肌/outfit 服装/features 特徴/personality 性格/speech 口調/firstPerson 一人称/catchphrase 口癖/likes 好きなもの/dislikes 苦手なもの/skills 特技/secret 秘密/relations 関係・呼び方/background 生い立ち/adultLikes 好きなこと・好きな体位/weakSpots 弱いところ/kinks 性癖/preferredPlay 好きなプレイ/breathPlay 首絞め・腹部圧迫・息詰めプレイ/moanStyle 喘ぎ方/adultNg NG。決まった項目だけ書けばよい。成人向けの項目はモーションコミックと成人向けの連載で使われる。カスタム項目（上記以外のキー）も自由に追加できる（例: {\"nosePiercing\":\"鼻ピアスの詳細\",\"bellyPiercing\":\"へそピアスの詳細\"}）。
- モーションコミックは draft:true で頼むと、台本・声・キーの絵（k1 元絵/k2 山場/k3 白濁液/f1 フェラ/f2 口内）まで約15分で作って止まり、絵の一覧を見せる。ユーザーがOKなら motion_continue で動画まで（約1.5時間）。気に入らない絵は motion_redraw で描き直す（k1 を直すと k2・k3 も描き直し）。迷ったら draft:true にする。
- モーションコミックのオプション（options、いくつでも組み合わせ可）: ${Object.entries(
  MOTION_OPTIONS,
)
  .map(([id, o]) => `${id} ${o.label}`)
  .join(" / ")}
- 1回の返事に書ける行は1つ。登録→設定画→学習のように続く頼みは、character_train の行1つでよい（設定画がなければ自動で先に描く）。
- ユーザーが「首絞めを追加して」「鼻ピアスを追加」などカスタム項目の追加を求めたときは、その言葉が分かる場合は character_update で spec にカスタムキーとして追加する（例: {"nekoPiercing":"首輪りのピアス・鎖など"）。言葉が分からない場合は「「　　」の詳細をインターネットで調べていいですか？」と許可を求め、許可されたら web_search で調べてから内容と定義を源に spec に追加する。
- この行はユーザーには見えない。返事のあとにシステムが実行し、結果（完成までの目安やエラー）を返事の下に添える。返事では「作り始めるね」までにして、完成したとは言わない。
- 履歴にある「🎨 画像を作り始めます」「✅ 画像ができました」「⏳ いま…」のような絵文字で始まるお知らせはシステムが書いたもの。あなたは書かない。
- 進み具合や作品の質問には、ユーザーの最新のメッセージに添えられた【生成スタジオの今の状況】を見て答える（この行は書かない）。`;

/** The fixed part of a studio assistant's prompt (kept stable so the model's cache is reused). */
export const STUDIO_CHAT_GUIDE = `【使えるモデルと目安時間】\n${STUDIO_GUIDE}\n${STUDIO_CHAT_RULES}`;

const KIND_LABELS: Record<MediaKind, string> = {
  image: "画像",
  video: "動画",
  music: "音楽",
  manga: "漫画",
  comic: "コミック",
  voice: "声",
  motion: "モーションコミック",
  faceswap: "顔入れ替え",
};
const KIND_ICONS: Record<MediaKind, string> = {
  image: "🎨",
  video: "🎬",
  music: "🎵",
  manga: "📖",
  comic: "📚",
  voice: "🎤",
  motion: "🎞",
  faceswap: "🎭",
};
const VOICE_STATUS_LABELS: Record<VoiceStatus, string> = {
  uploading: "録音を集めている（学習前）",
  queued: "学習待ち",
  training: "学習中",
  ready: "完成",
  failed: "学習に失敗",
};

export function pickOne<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return allowed.find((item) => item === value);
}

export function studioOptionsFrom(args: Record<string, unknown>): MediaOptions {
  const duration = typeof args.durationSec === "number" ? Math.round(args.durationSec) : undefined;
  return {
    aspect: pickOne(args.aspect, STUDIO_ASPECTS),
    imageModel: pickOne(args.imageModel, STUDIO_IMAGE_MODELS),
    fast: args.fast === true,
    engine: pickOne(args.engine, STUDIO_VIDEO_ENGINES),
    durationSec: duration && duration > 0 ? duration : undefined,
    smooth: args.smooth === true,
    sourceImageJobId:
      typeof args.sourceImageJobId === "string" && /^[a-f0-9]{6,32}$/.test(args.sourceImageJobId)
        ? args.sourceImageJobId
        : undefined,
    instrumental: args.instrumental === true,
    lyrics: typeof args.lyrics === "string" && args.lyrics.trim() ? args.lyrics.trim() : undefined,
    mangaLayout: pickOne(args.mangaLayout, STUDIO_MANGA_LAYOUTS),
    mangaPages:
      typeof args.mangaPages === "number" && Number.isFinite(args.mangaPages)
        ? Math.max(2, Math.min(32, Math.round(args.mangaPages)))
        : undefined,
    mangaTone: pickOne(args.mangaTone, ["color", "mono"] as const),
    voiceId:
      typeof args.voiceId === "string" && /^voice-[a-z0-9-]{8,60}$/.test(args.voiceId)
        ? args.voiceId
        : undefined,
    voiceCaption:
      typeof args.voiceCaption === "string" && args.voiceCaption.trim()
        ? normalizeVoiceCaption(args.voiceCaption)
        : undefined,
    irodoriModel: (["small", "large", "fast"] as const).find(
      (value) => value === args.irodoriModel,
    ),
    motionMinutes: MOTION_MINUTES.find((value) => value === args.motionMinutes),
    motionDraft: args.draft === true || args.motionDraft === true ? true : undefined,
    ...motionExtrasFromParams(args),
    voiceDrama: voiceDramaFromParams(args.voiceDrama),
    ...mediaProductionOptionsFromParams(args),
    motionImageModel: pickOne(args.motionImageModel, STUDIO_IMAGE_MODELS),
    motionTemplate: MOTION_TEMPLATE_IDS.find(
      (value) => value === args.template || value === args.motionTemplate,
    ),
    motionOutfit:
      typeof args.outfit === "string" && args.outfit.trim()
        ? args.outfit.trim().slice(0, 20)
        : undefined,
    motionOptions: Array.isArray(args.options)
      ? MOTION_OPTION_IDS.filter((id) => (args.options as unknown[]).includes(id))
      : undefined,
    motionFellatio: typeof args.motionFellatio === "boolean" ? args.motionFellatio : undefined,
  };
}

export function minutesText(sec: number): string {
  return sec < 60 ? "1分以内" : `約${Math.ceil(sec / 60)}分`;
}

function clip(text: string, max: number): string {
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

function agoText(now: number, at: number): string {
  const minutes = Math.max(0, Math.round((now - at) / 60_000));
  if (minutes < 1) return "たった今";
  if (minutes < 60) return `${minutes}分前`;
  const hours = Math.round(minutes / 60);
  return hours < 24 ? `${hours}時間前` : `${Math.round(hours / 24)}日前`;
}

export async function summarizeStudioJob(job: MediaJob) {
  const elapsedSec =
    job.startedAt && !job.finishedAt ? Math.round((Date.now() - job.startedAt) / 1000) : undefined;
  // Same settings took about this long before; what is left of it for the running job.
  const remaining =
    job.status === "running" && elapsedSec !== undefined
      ? `あと${minutesText(Math.max(30, (await estimateMediaJob(job.kind, job.options)).jobSec - elapsedSec))}`
      : undefined;
  return {
    jobId: job.id,
    kind: job.kind,
    status: job.status,
    request: job.request,
    stage: job.stage,
    elapsedSec,
    remaining,
    durationSec: job.durationSec,
    error: job.error,
  };
}

/**
 * Starts a job asked for in a conversation. It waits for the chat reply before taking the GPU,
 * and its end is announced to that conversation (`partnerId`) or the phone.
 */
export async function startStudioJob(
  args: Record<string, unknown>,
  chat: { partnerId?: string } = {},
): Promise<{ job: MediaJob; estimateText: string }> {
  const kind = pickOne(args.kind, STUDIO_KINDS);
  if (!kind)
    throw new Error(
      "作るもの（image / video / music / manga / voice / motion）が指定されていません",
    );
  const options = studioOptionsFrom(args);
  const estimate = await estimateMediaJob(kind, options);
  const prompt = typeof args.prompt === "string" ? args.prompt : "";
  if (kind === "motion" && typeof args.character === "string" && args.character.trim()) {
    options.motionCharacterId = (await findCharacter(args.character)).id;
  }
  const job = await startMediaJob(kind, prompt, options, { chat });
  const wait = estimate.waitSec > 0 ? `（順番待ち${minutesText(estimate.waitSec)}を含む）` : "";
  return { job, estimateText: `完成まで${minutesText(estimate.totalSec)}${wait}` };
}

/** Queues a recorded voice for training (`now` starts it after the current reply). */
export async function trainStudioVoice(voiceRef: string, now: boolean): Promise<VoiceProject> {
  const ref = voiceRef.trim();
  const target = (await listVoiceProjects()).find(
    (voice) => voice.id === ref || voice.name === ref,
  );
  if (!target) throw new Error("その声は見つかりません");
  if (target.source === "design") {
    throw new Error("声デザインで作った声は学習しなくてもそのまま使えます");
  }
  const voice = await queueVoiceTraining(target.id, { now });
  if (now) {
    // Loaded lazily: partner-proactive imports the partner chat code, which imports this module.
    const { startVoiceTraining } = await import("./partner-proactive.js");
    startVoiceTraining();
  }
  return voice;
}

/**
 * The studio's current state (jobs and voices) for a 1:1 studio-assistant turn. It changes every
 * turn, so it rides on the latest message rather than the cached system prompt.
 */
export async function describeStudioForChat(now = Date.now()): Promise<string> {
  const jobs = await listMediaJobs(30);
  const lines: string[] = [];
  for (const job of jobs.filter((j) => j.status === "running" || j.status === "queued")) {
    const summary = await summarizeStudioJob(job);
    const detail = [summary.stage, summary.remaining].filter(Boolean).join("、");
    lines.push(
      `- ${job.status === "running" ? "作成中" : "順番待ち"}: ${KIND_LABELS[job.kind]}「${clip(job.request, 40)}」 id=${job.id}${detail ? `（${detail}）` : ""}`,
    );
  }
  if (lines.length === 0) lines.push("- 作成中・順番待ちはなし");
  const characters = (await listCharacters()).slice(0, 20).map((c) => {
    const lora =
      c.loraStatus === "ready"
        ? "学習済み"
        : c.loraStatus === "training" || c.loraStatus === "queued"
          ? "学習中"
          : "未学習";
    return `- 「${c.name}」 id=${c.id}: 設定画${c.refImage ? "あり" : "なし"}・${lora}`;
  });
  const series = (await listComicSeries())
    .slice(0, 10)
    .map(
      (s) => `- 「${s.title}」 id=${s.id}: ${s.episodes.length}話まで、登場人物${s.cast.length}人`,
    );
  for (const job of jobs.filter((j) => j.status === "done" || j.status === "error").slice(0, 4)) {
    lines.push(
      `- ${job.status === "done" ? "完成" : "失敗"}（${agoText(now, job.finishedAt ?? job.createdAt)}）: ${KIND_LABELS[job.kind]}「${clip(job.request, 40)}」 id=${job.id}`,
    );
  }
  const voices = (await listVoiceProjects()).slice(0, 8).map((voice) => {
    const state =
      voice.source === "design" ? "声デザイン（学習不要）" : VOICE_STATUS_LABELS[voice.status];
    const step = voice.status === "training" && voice.step ? `（${voice.step}）` : "";
    return `- 「${voice.name}」 id=${voice.id}: ${state}${step}、録音${voice.files.length}件`;
  });
  const time = new Date(now).toLocaleTimeString("ja-JP", { hour: "2-digit", minute: "2-digit" });
  return [
    `【生成スタジオの今の状況（${time}時点）】`,
    ...lines,
    ...(voices.length > 0 ? ["【声作りスタジオの声】", ...voices] : []),
    ...(characters.length > 0 ? ["【キャラ図鑑】", ...characters] : []),
    ...(series.length > 0 ? ["【連載コミック】", ...series] : []),
  ].join("\n");
}

const ACTION_LINE = /<<\s*studio\s*(\{[\s\S]*?\})\s*>>/i;

/**
 * Notices only the gateway writes (runStudioActionFromChat, describeBusyStudio,
 * finishedJobNotice). The chat history is full of them, so the model copies them; a copied
 * "✅ できました" would report work that never happened.
 */
const SYSTEM_NOTICE_LINES = [
  /^(?:🎨|🎬|🎵|📖|🎤)\s*(?:画像|動画|音楽|漫画|声)を作り始めます/u,
  /^✅\s*(?:画像|動画|音楽|漫画|声)ができました/u,
  /^⚠️\s*(?:(?:画像|動画|音楽|漫画|声)を作れませんでした|できませんでした|止める作品|その作品は)/u,
  /^⏳\s*いま/u,
  /^🛑\s*止めました/u,
  /^🎙\s*声「[^」]*」(?:の学習をこのあと始めます|を今夜の学習に予約しました)/u,
  /^👤\s*(?:キャラ図鑑に|「[^」]*」の設定画)/u,
  /^🧠\s*「[^」]*」の専用学習/u,
  /^📋\s*「[^」]*」の設定シート/u,
  /^🎞\s*(?:下書きの絵|モーションコミック)/u,
  /^📚\s*(?:連載コミック「|「[^」]*」の第\d+話)/u,
];

function isSystemNotice(line: string): boolean {
  const text = line.trim();
  return SYSTEM_NOTICE_LINES.some((pattern) => pattern.test(text));
}

function parseActionJson(text: string): Record<string, unknown> | undefined {
  for (const candidate of [text, text.replace(/,\s*([}\]])/g, "$1")]) {
    try {
      const parsed: unknown = JSON.parse(candidate);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // Try the next repair.
    }
  }
  return undefined;
}

/** Splits a studio-assistant reply into the visible text and its action line, if any. */
export function extractStudioAction(reply: string): {
  reply: string;
  action?: Record<string, unknown>;
} {
  const match = ACTION_LINE.exec(reply);
  const visible = reply
    .replace(/<<\s*studio[\s\S]*?>>/gi, "")
    .split("\n")
    .filter((line) => !isSystemNotice(line))
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
  const action = match?.[1] ? parseActionJson(match[1]) : undefined;
  return action ? { reply: visible, action } : { reply: visible };
}

/**
 * From the middle of a finished motion comic, goes on in another position: the same person and place
 * (inherited) in the new position is made, then both are joined with a bridge between them.
 */
export async function startMotionBranch(
  input: { jobId: string; template: string; atSec?: number; seconds?: number; request?: string },
  chat?: { partnerId?: string },
): Promise<{ text: string; nextId: string; joinId: string }> {
  if (!/^[a-f0-9]{6,32}$/.test(input.jobId)) throw new Error("元の動画の id（jobId）が必要です");
  const source = await getMediaJob(input.jobId);
  if (!source || source.status !== "done" || !source.mime?.startsWith("video/"))
    throw new Error("元の動画は、完成したものを選んでください");
  const atSec = input.atSec && input.atSec > 0 ? input.atSec : undefined;
  const template = MOTION_TEMPLATE_IDS.find((id) => id === input.template);
  const request = input.request?.trim() ? input.request.trim() : "（前の続き）";
  const origin = chat ? { chat } : {};
  const next = await startMediaJob(
    "motion",
    request,
    {
      motionInheritJobId: input.jobId,
      motionPreview: false,
      ...(template ? { motionTemplate: template } : {}),
      motionMinutes: source.options.motionMinutes ?? 1,
    },
    origin,
  );
  const { motionBridgeSeconds } = motionExtrasFromParams({
    motionBridgeSeconds: input.seconds ?? 6,
  });
  const join = await startMediaJob(
    "motion",
    "（別の体位に切り替えてつなぐ）",
    {
      motionJoinJobIds: [input.jobId, next.id],
      motionJoinTrimSec: [atSec ?? null, null],
      ...(motionBridgeSeconds !== undefined ? { motionBridgeSeconds } : {}),
    },
    origin,
  );
  return {
    text: `🔀 ${atSec ? `${Math.round(atSec)}秒のところから` : "最後から"}別の体位で続きを作り、${motionBridgeSeconds ?? 3}秒の移り変わりでつなぎます（続き作成のあと、つなぎ。作品id=${next.id} → ${join.id}）。`,
    nextId: next.id,
    joinId: join.id,
  };
}

/** Runs a 1:1 chat's action and returns the line shown under the reply ("" for none). */
export async function runStudioActionFromChat(
  action: Record<string, unknown>,
  partnerId: string,
  web?: Pick<ResearchIO, "fetchPage" | "searchWeb">,
): Promise<string> {
  try {
    switch (pickOne(action.action, STUDIO_CHAT_ACTIONS)) {
      case "generate": {
        const { job, estimateText } = await startStudioJob(action, { partnerId });
        return `${KIND_ICONS[job.kind]} ${KIND_LABELS[job.kind]}を作り始めます（${estimateText}・作っている間チャットはお休み）。できたらここでお知らせします。`;
      }
      case "cancel": {
        const jobId = typeof action.jobId === "string" ? action.jobId.trim() : "";
        if (!jobId) return "⚠️ 止める作品が指定されていませんでした。";
        return (await deleteMediaJob(jobId))
          ? "🛑 止めました。"
          : "⚠️ その作品は見つかりませんでした。";
      }
      case "train_voice": {
        const now = action.now === true;
        const voice = await trainStudioVoice(String(action.voiceId ?? ""), now);
        return now
          ? `🎙 声「${voice.name}」の学習をこのあと始めます（数十分。その間チャットと生成はお休み。終わったらスマホに通知します）。`
          : `🎙 声「${voice.name}」を今夜の学習に予約しました。`;
      }
      case "character_create": {
        const entry = await createCharacterFromChat(action);
        const job = await startMediaJob(
          "comic",
          "",
          { comicTask: "sheet", comicCharacterId: entry.id },
          { chat: { partnerId } },
        );
        return `👤 キャラ図鑑に「${entry.name}」を登録して、設定画を描き始めます（約1分）。id=${entry.id}（作品id=${job.id}）`;
      }
      case "character_update": {
        const entry = await findCharacter(action.character);
        const patch =
          action.spec && typeof action.spec === "object"
            ? (action.spec as Record<string, unknown>)
            : {};
        // Appearance options by id (from option_find): added ones win over a chosen one of the same kind.
        const idsOf = (value: unknown) =>
          (Array.isArray(value) ? value : [])
            .map((id) => String(id).replace(/^opt:/, "").trim())
            .filter(Boolean);
        const addIds = idsOf(action.addOptions);
        const removeIds = idsOf(action.removeOptions);
        const patched = { ...entry.spec, ...patch } as Record<string, unknown>;
        const optionsChanged = addIds.length > 0 || removeIds.length > 0;
        if (optionsChanged) {
          const kept = characterOptionIds(
            (entry.spec as Record<string, unknown> | undefined)?.lookOptions,
          ).filter((id) => !removeIds.includes(id));
          patched.lookOptions = characterOptionIds([...addIds, ...kept].join(",")).join(",");
        }
        const spec = cleanSpec(patched);
        const voiceId =
          typeof action.voiceId === "string" && /^voice-[a-z0-9-]{8,60}$/.test(action.voiceId)
            ? action.voiceId
            : entry.voiceId;
        const voiceCaption =
          typeof action.voiceCaption === "string" && action.voiceCaption.trim()
            ? normalizeVoiceCaption(action.voiceCaption)
            : entry.voiceCaption;
        // New outfits are added to (or replace by name) the ones she has.
        const added = action.outfits !== undefined ? cleanOutfits(action.outfits) : [];
        const outfits = [
          ...(entry.outfits ?? []).filter((o) => !added.some((a) => a.name === o.name)),
          ...added,
        ].slice(0, 12);
        assertAllowedRequest(...Object.values(spec).map(String));
        assertAgeAllowed(spec.age);
        // A change to how she looks rewrites her look tags too (the picture is redrawn on request).
        const LOOK_KEYS = [
          "hair",
          "hairstyle",
          "eyes",
          "skin",
          "outfit",
          "features",
          "body",
          "breastSize",
          "legwear",
          "underwear",
          "accessories",
          "age",
          "heightCm",
        ];
        const lookChanged =
          optionsChanged || Object.keys(patch).some((key) => LOOK_KEYS.includes(key));
        let look = entry.look;
        if (lookChanged) {
          const written = await writeCharacterLook(entry.name, spec);
          assertAllowedRequest(written.look);
          look = written.look;
        }
        await saveCharacter({
          ...entry,
          spec,
          look,
          voiceId,
          voiceCaption,
          outfits,
          updatedAt: Date.now(),
        });
        return `📋「${entry.name}」の設定シートを更新しました（${Object.keys(patch).length + addIds.length + removeIds.length}項目${voiceId !== entry.voiceId || voiceCaption !== entry.voiceCaption ? "・声" : ""}${lookChanged ? "・見た目のタグも更新。絵は「設定画を描き直して」と頼むと反映" : ""}）。`;
      }
      case "motion_continue": {
        const jobId = typeof action.jobId === "string" ? action.jobId.trim() : "";
        const job = await startMediaJob(
          "motion",
          "（下書きの続き）",
          { motionContinueJobId: jobId },
          { chat: { partnerId } },
        );
        return `🎞 下書きの絵で、モーションコミックの動画を作り始めます（1カット約11分、全部で1.5時間ほど）。作品id=${job.id}`;
      }
      case "motion_redraw": {
        const jobId = typeof action.jobId === "string" ? action.jobId.trim() : "";
        const keys = Array.isArray(action.keys)
          ? action.keys.filter((k): k is string => typeof k === "string")
          : ["k1"];
        const job = await startMediaJob(
          "motion",
          "（下書きの描き直し）",
          { motionContinueJobId: jobId, motionRedrawKeys: keys, motionDraft: true },
          { chat: { partnerId } },
        );
        return `🎞 下書きの絵（${keys.join("・")}）を描き直します（数分）。作品id=${job.id}`;
      }
      case "character_sheet": {
        const entry = await findCharacter(action.character);
        const outfit =
          typeof action.outfit === "string" && action.outfit.trim()
            ? action.outfit.trim()
            : undefined;
        const job = await startMediaJob(
          "comic",
          "",
          {
            comicTask: "sheet",
            comicCharacterId: entry.id,
            ...(outfit ? { comicOutfit: outfit } : {}),
          },
          { chat: { partnerId } },
        );
        return `👤「${entry.name}」の設定画${outfit ? `（${outfit}）` : ""}を描き始めます（約1分）。作品id=${job.id}`;
      }
      case "character_train": {
        const entry = await adoptLatestSheet(await findCharacter(action.character));
        let note = "";
        if (!entry.refImage) {
          // No sheet yet: draw one first; the training right behind it in the queue uses it.
          await startMediaJob(
            "comic",
            "",
            { comicTask: "sheet", comicCharacterId: entry.id },
            { chat: { partnerId } },
          );
          note = "設定画を先に描いてから、";
        }
        const job = await startMediaJob(
          "comic",
          "",
          { comicTask: "lora", comicCharacterId: entry.id },
          { chat: { partnerId } },
        );
        await saveCharacter({
          ...entry,
          loraStatus: "queued",
          loraStep: "順番待ち",
          updatedAt: Date.now(),
        });
        return `🧠「${entry.name}」の専用学習を${note}始めます（約1時間・その間チャットはお休み）。作品id=${job.id}`;
      }
      case "comic_series": {
        const series = await createSeriesFromChat(action);
        return `📚 連載コミック「${series.title}」を作りました（登場人物${series.cast.length}人：${series.cast.map((c) => c.name).join("、")}）。id=${series.id}`;
      }
      case "status": {
        const status = await mediaStudioStatus();
        const gpu = await gpuMemoryText();
        const now = status.current
          ? `いま「${KIND_LABELS[status.current.kind]}」を作成中（${status.current.stage ?? "実行中"}・${minutesText(status.current.elapsedSec)}経過）`
          : "いま作成中の作業はなし";
        return `🖥 ${now}。順番待ち${status.queued}件。${status.chatPaused ? "チャットAIは一時停止中。" : "チャットAIは動作中。"}${gpu ? ` GPU：${gpu}` : ""}`;
      }
      case "faceswap_inputs": {
        const inputs = await listSwapInputs();
        if (inputs.length === 0)
          return "🎭 PCに、顔入れ替えの素材はまだありません。アプリの「顔入れ替え」から、動画や画像を送ってください。";
        return `🎭 顔入れ替えの素材（新しい順）:\n${inputs
          .slice(0, 12)
          .map((i) => `- ${i.name}（${i.inputId}）`)
          .join("\n")}`;
      }
      case "faceswap": {
        const inputs = await listSwapInputs();
        const wanted = typeof action.target === "string" ? action.target.trim() : "";
        const target =
          inputs.find((i) => i.inputId === wanted) ??
          inputs.find((i) => wanted && i.name.includes(wanted)) ??
          (wanted ? undefined : inputs[0]);
        if (!target)
          throw new Error(
            "入れ替える素材が見つかりません（先に faceswap_inputs で素材を確かめる）",
          );
        const refs = Array.isArray(action.characters) ? action.characters.slice(0, 4) : [];
        const positions = Array.isArray(action.positions) ? action.positions : [];
        const sources: Array<{ characterId: string; position?: number }> = [];
        for (const [index, ref] of refs.entries()) {
          const entry = await findCharacter(ref);
          const position =
            typeof positions[index] === "number" && Number.isInteger(positions[index])
              ? (positions[index] as number)
              : undefined;
          sources.push({ characterId: entry.id, ...(position !== undefined ? { position } : {}) });
        }
        if (sources.length === 0)
          throw new Error("入れる顔のキャラ（図鑑の名前）が指定されていません");
        const quality =
          pickOne(action.quality, ["fast", "standard", "high"] as const) ?? "standard";
        const options: MediaOptions = {
          swapTarget: target.inputId,
          swapSources: sources,
          swapQuality: quality,
          ...(action.enhancer === true ? { swapEnhancer: true } : {}),
          ...(typeof action.trimStartSec === "number"
            ? { swapTrimStartSec: action.trimStartSec }
            : {}),
          ...(typeof action.trimEndSec === "number" ? { swapTrimEndSec: action.trimEndSec } : {}),
        };
        const estimate = await estimateFaceSwapJob(options);
        await startMediaJob("faceswap", "顔入れ替え", options, { chat: { partnerId } });
        return `🎭 顔入れ替えを始めます（${target.name}・${sources.length}人分・目安${minutesText(estimate.jobSec)}・作っている間チャットはお休み）。できたらここでお知らせします。`;
      }
      case "custom_add": {
        const entry = await addCustomSetting({
          genre: action.genre,
          label: action.label,
          instruction: action.instruction,
        });
        assertAllowedRequest(entry.label, entry.instruction);
        return `➕ 項目「${entry.label}」を追加しました（分類：${entry.genre}）。サンプル画像製造の「追加した項目」と、キャラ作成の「自分で追加した項目」に出ます。id=custom:${entry.id}`;
      }
      case "shelf_add": {
        if (!web) throw new Error("ネット検索がまだ使えません");
        const query = typeof action.query === "string" ? action.query : undefined;
        const url = typeof action.url === "string" ? action.url : undefined;
        const title = typeof action.title === "string" ? action.title : undefined;
        const { added, skipped } = await shelfFromWeb(partnerId, { query, url, title }, web);
        if (added.length === 0)
          return `📚 資料棚に入れられるページが見つかりませんでした${skipped ? `（${skipped}件は読めませんでした）` : ""}。`;
        return `📚 資料棚に${added.length}件入れました：\n${added.map((d) => `- ${d.title}（${d.passages}節）`).join("\n")}`;
      }
      case "motion_branch": {
        const result = await startMotionBranch(
          {
            jobId: typeof action.jobId === "string" ? action.jobId : "",
            template: typeof action.template === "string" ? action.template : "",
            ...(typeof action.atSec === "number" ? { atSec: action.atSec } : {}),
            ...(typeof action.seconds === "number" ? { seconds: action.seconds } : {}),
            ...(typeof action.request === "string" ? { request: action.request } : {}),
          },
          { partnerId },
        );
        return result.text;
      }
      case "dev_request": {
        const request = await createDevRequest({
          title: typeof action.title === "string" ? action.title : "",
          description: typeof action.description === "string" ? action.description : "",
          githubUrl: typeof action.githubUrl === "string" ? action.githubUrl : "",
        });
        return `🛠 開発の依頼を受けました（${request.title}・依頼id=${request.id}）。本体のコピーで作業して、できたら設定の「クリエ開発機能」で確認できます。`;
      }
      case "dev_status": {
        const requests = (await listDevRequests()).slice(0, 8);
        if (requests.length === 0) return "🛠 開発の依頼は、まだありません。";
        const label: Record<string, string> = {
          queued: "待機",
          working: "作業中",
          review: "確認待ち",
          nochange: "変更なし",
          applied: "適用済み",
          rejected: "取りやめ",
          error: "失敗",
        };
        return `🛠 開発の依頼:@@N${requests.map((r) => `- ${r.title}（${label[r.status] ?? r.status}）`).join("@@N")}`.replaceAll(
          "@@N",
          "\n",
        );
      }
      case "motion_join": {
        const ids = Array.isArray(action.jobIds)
          ? action.jobIds.filter((v): v is string => typeof v === "string")
          : [];
        const seconds = typeof action.seconds === "number" ? action.seconds : 3;
        const { motionJoinJobIds, motionBridgeSeconds } = motionExtrasFromParams({
          motionJoinJobIds: ids,
          motionBridgeSeconds: seconds,
        });
        if (!motionJoinJobIds)
          throw new Error("つなぐ完成動画のid（jobIds）を2つ以上書いてください");
        const job = await startMediaJob(
          "motion",
          "（動画をつなぐ）",
          {
            motionJoinJobIds,
            ...(motionBridgeSeconds !== undefined ? { motionBridgeSeconds } : {}),
          },
          { chat: { partnerId } },
        );
        return `🔗 ${motionJoinJobIds.length}本の動画を、つなぎ${motionBridgeSeconds ?? 3}秒ずつで1本にします（つなぎ目ごとに約10〜20分・作っている間チャットはお休み）。作品id=${job.id}`;
      }
      case "motion_find": {
        const query = typeof action.query === "string" ? action.query.trim().toLowerCase() : "";
        const catalog = motionCatalog();
        const poses = catalog.poses
          .filter((p) => !query || `${p.label} ${p.group} ${p.id}`.toLowerCase().includes(query))
          .slice(0, 15);
        const options = catalog.options
          .flatMap((g) => g.items.map((o) => ({ ...o, group: g.group })))
          .filter((o) => !query || `${o.label} ${o.group} ${o.id}`.toLowerCase().includes(query))
          .slice(0, 15);
        if (poses.length + options.length === 0)
          return `🔎「${query}」に合うモーションは、見つかりませんでした。`;
        const lines = poses
          .map((p) => `- 体位 ${p.id} ${p.label}（${p.group}）`)
          .concat(options.map((o) => `- オプション ${o.id} ${o.label}（${o.group}）`));
        return `🔎「${query}」のモーション:\n${lines.join("\n")}`;
      }
      case "motion_add": {
        const added = await addCustomMotion(action);
        return `🎞 ${added.kind === "pose" ? "体位" : "オプション"}「${added.label}」を足しました（id: ${added.id}）。モーション生成・サンプル画像製造・カタログで使えます。`;
      }
      case "motion_remove": {
        const id = typeof action.id === "string" ? action.id : "";
        return (await removeCustomMotion(id))
          ? "🎞 消しました。"
          : "その id は、自分で足したものではありません（標準のものは消せません）。";
      }
      case "custom_list": {
        const list = await loadCustomSettings();
        if (list.length === 0) return "➕ 自分で追加した項目は、まだありません。";
        return `➕ 追加した項目:\n${list
          .slice(0, 30)
          .map((c) => `- custom:${c.id} ${c.label}（${c.genre}）：${c.instruction}`)
          .join("\n")}`;
      }
      case "option_find": {
        const query = typeof action.query === "string" ? action.query : "";
        const found = await findSettings(query, 12);
        if (found.length === 0) return `🔎「${query}」に合う項目は、見つかりませんでした。`;
        return `🔎「${query}」の項目（id と名前）:\n${found.map((f) => `- ${f.key} ${f.label}（${f.where}）${f.ja ? `：${f.ja}` : ""}${f.hasSample ? "【サンプルあり】" : ""}`).join("\n")}`;
      }
      case "sample_make": {
        const key = typeof action.key === "string" ? action.key.trim() : "";
        const subject = await resolveSampleSubject(key);
        if (!subject)
          throw new Error("その項目が見つかりません（先に option_find で id を調べる）");
        const owner = await sampleOwner();
        const note = typeof action.note === "string" ? action.note.trim().slice(0, 300) : undefined;
        await startMediaJob(
          "comic",
          "",
          {
            comicTask: "sample",
            comicCharacterId: owner.id,
            sampleKey: key,
            sampleBase: action.base === "adult" ? "adult" : "normal",
            ...(note !== undefined ? { sampleNote: note } : {}),
          },
          { chat: { partnerId } },
        );
        return `🖼 「${subject.label}」のサンプルを、${owner.name}で作り始めます（約2分・作っている間チャットはお休み）。`;
      }
      case "sample_default": {
        const key = typeof action.key === "string" ? action.key.trim() : "";
        const subject = await resolveSampleSubject(key);
        if (!subject) throw new Error("その項目が見つかりません");
        const note = typeof action.note === "string" ? action.note.trim().slice(0, 300) : "";
        const index = await loadSampleIndex();
        if (note) index.defaults[key] = note;
        else delete index.defaults[key];
        await saveSampleIndex(index);
        return note
          ? `📌 「${subject.label}」の既定の指示を保存しました。`
          : `📌 「${subject.label}」の既定の指示を消しました。`;
      }
      case "comic_episode": {
        const series = await findSeries(action.series);
        const prompt = typeof action.prompt === "string" ? action.prompt.trim().slice(0, 500) : "";
        const job = await startMediaJob(
          "comic",
          prompt,
          { comicSeriesId: series.id, comicTask: "episode", imageModel: "wai", fast: true },
          { chat: { partnerId } },
        );
        return `📚「${series.title}」の第${series.episodes.length + 1}話を作り始めます（100コマ・約25分）。作品id=${job.id}`;
      }
      default:
        return "";
    }
  } catch (err) {
    return `⚠️ できませんでした: ${err instanceof Error ? err.message : String(err)}`;
  }
}

/** The character the samples are made with: Crie, else the first favorite, else the first one. */
async function sampleOwner(): Promise<LibraryCharacter> {
  const all = await listCharacters();
  const owner = all.find((c) => c.name === "クリエ") ?? all.find((c) => c.isFavorite) ?? all[0];
  if (!owner) throw new Error("キャラ図鑑にキャラがいません");
  return owner;
}

/** "6.8/8GB 使用中" from nvidia-smi, or "" when it cannot be read. */
async function gpuMemoryText(): Promise<string> {
  try {
    const { execFile } = await import("node:child_process");
    const out = await new Promise<string>((resolve, reject) =>
      execFile(
        "nvidia-smi",
        ["--query-gpu=memory.used,memory.total", "--format=csv,noheader,nounits"],
        { timeout: 3_000, windowsHide: true },
        (err, stdout) => (err ? reject(err) : resolve(stdout)),
      ),
    );
    const [used, total] = out
      .trim()
      .split(",")
      .map((v) => Number(v));
    return used && total ? `${(used / 1024).toFixed(1)}/${(total / 1024).toFixed(0)}GB 使用中` : "";
  } catch {
    return "";
  }
}

/** A library character by id or name. */
async function findCharacter(ref: unknown): Promise<LibraryCharacter> {
  const key = typeof ref === "string" ? ref.trim() : "";
  if (!key) throw new Error("キャラが指定されていません");
  const byId = await getCharacter(key);
  if (byId) return byId;
  const byName = (await listCharacters()).find((c) => c.name === key);
  if (!byName) throw new Error(`キャラ図鑑に「${key}」がいません`);
  return byName;
}

async function findSeries(ref: unknown) {
  const key = typeof ref === "string" ? ref.trim() : "";
  const byId = await getComicSeries(key);
  if (byId) return byId;
  const byTitle = (await listComicSeries()).find((s) => s.title === key);
  if (!byTitle) throw new Error(`連載コミック「${key}」が見つかりません`);
  return byTitle;
}

/** Registers a character described in words (the look tags are written by the script model). */
async function createCharacterFromChat(action: Record<string, unknown>): Promise<LibraryCharacter> {
  const name = typeof action.name === "string" ? action.name.trim().slice(0, 20) : "";
  const description =
    typeof action.description === "string" ? action.description.trim().slice(0, 600) : "";
  if (!name) throw new Error("キャラの名前がありません");
  const given =
    action.spec && typeof action.spec === "object" ? (action.spec as Record<string, unknown>) : {};
  const spec = cleanSpec({ ...given, ...(description ? { features: description } : {}) });
  assertAgeAllowed(spec.age);
  const written = await writeCharacterLook(name, spec);
  assertAllowedRequest(written.look, written.profile);
  const entry = await registerCharacter({ name, look: written.look, profile: written.profile });
  const saved = { ...entry, spec, updatedAt: Date.now() };
  await saveCharacter(saved);
  return saved;
}

async function createSeriesFromChat(action: Record<string, unknown>) {
  const text = (key: string, max: number) =>
    typeof action[key] === "string" ? (action[key] as string).trim().slice(0, max) : "";
  const title = text("title", 40);
  const premise = text("premise", 1500);
  if (!title && !premise) throw new Error("題名か設定がありません");
  const rating = action.rating === "general" ? "general" : "adult";
  const series = newComicSeries({
    title,
    genre: text("genre", 40),
    premise,
    rating,
    imageModel: "wai",
    cast: [],
  });
  assertAllowedRequest(series.title, series.premise);
  const picked: ComicCharacter[] = [];
  for (const ref of Array.isArray(action.characters) ? action.characters.slice(0, 16) : []) {
    const entry = await findCharacter(ref).catch(() => undefined);
    if (entry)
      picked.push({
        name: entry.name,
        look: entry.look,
        profile: entry.profile,
        characterId: entry.id,
      });
  }
  const cast = picked.length > 0 ? picked : await writeComicCast(series);
  series.cast = [];
  for (const member of cast) {
    const entry = member.characterId
      ? await getCharacter(member.characterId)
      : await registerCharacter(member);
    if (entry) series.cast.push({ ...member, characterId: entry.id });
  }
  await saveComicSeries(series);
  return series;
}

/**
 * True while a media job or voice training has the GPU: the pause flag is up and the chat model
 * does not answer (a stale flag with the model running does not block chats).
 */
export async function isChatModelLentOut(): Promise<boolean> {
  try {
    await fs.access(path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag"));
  } catch {
    return false;
  }
  try {
    const res = await fetch("http://127.0.0.1:11434/health", {
      signal: AbortSignal.timeout(2_000),
    });
    return !res.ok;
  } catch {
    return true;
  }
}

/** What a partner chat shows instead of a reply while the GPU is lent out. */
export async function describeBusyStudio(): Promise<string> {
  const jobs = await listMediaJobs(30);
  const running = jobs.find((job) => job.status === "running");
  const queued = jobs.filter((job) => job.status === "queued").length;
  const training = (await listVoiceProjects()).find((voice) => voice.status === "training");
  let what: string;
  if (running) {
    const summary = await summarizeStudioJob(running);
    const detail = [summary.stage, summary.remaining].filter(Boolean).join("、");
    what = `${KIND_LABELS[running.kind]}「${clip(running.request, 30)}」を作っています${detail ? `（${detail}）` : ""}${queued > 0 ? `。このあと${queued}件が順番待ち` : ""}`;
  } else if (training) {
    what = `声「${training.name}」を学習しています${training.step ? `（${training.step}）` : ""}`;
  } else {
    // A job just ended (an image keeps the GPU a little longer for a follow-up) or is about to
    // start; the chat model comes back within a minute or so.
    return "⏳ いま生成スタジオがGPUを使っています。終わったばかりなら1分ほどでチャットのAIが戻るので、少ししてから話しかけてください。";
  }
  return `⏳ いま${what}。そのあいだチャットのAIはお休みしています。終わったらまた話しかけてください。`;
}

/** The message that tells a conversation its job is finished. */
export function finishedJobNotice(job: MediaJob): { title: string; text: string } {
  const label = KIND_LABELS[job.kind];
  const request = clip(job.request || "（指示なし）", 40);
  if (job.status === "done") {
    const sec = job.durationSec ?? 0;
    const took = sec > 0 ? `（${sec < 60 ? `${sec}秒` : minutesText(sec)}）` : "";
    return {
      title: `${KIND_ICONS[job.kind]} ${label}ができました`,
      text: `✅ ${label}ができました:「${request}」${took}。アプリの生成スタジオの保存庫で見られます。`,
    };
  }
  return {
    title: `⚠️ ${label}の作成に失敗`,
    text: `⚠️ ${label}を作れませんでした:「${request}」${job.error ? `（${clip(job.error, 120)}）` : ""}`,
  };
}
