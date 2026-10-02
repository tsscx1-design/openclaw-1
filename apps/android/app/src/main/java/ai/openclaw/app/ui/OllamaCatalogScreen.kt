package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject



data class InstalledOllamaModel(
  val name: String,
  val sizeGb: String,
  val description: String = "",
  val executionHint: String = "⚡ GPU高速",
  val tags: List<String> = emptyList(),
)

enum class ModelCategory(val label: String) {
  ALL("すべて"),
  JAPANESE("🇯🇵 日本語特化"),
  UNCENSORED("🔓 制限解除・無検閲"),
  ROLEPLAY("🎭 キャラ対話・RP"),
  REASONING("🧠 思考・推論"),
  CODING("💻 コーディング"),
  LIGHTWEIGHT("⚡ 超軽量 (〜4GB)")
}

enum class VramTier(val label: String) {
  ALL("全VRAM"),
  LOW("〜4GB (超軽量)"),
  MID("4〜8GB (標準)"),
  HIGH("8〜12GB (高精度)"),
  ULTRA("12GB〜 (大型)")
}

enum class ViewMode {
  TABLE, // 一覧表 (テーブル表示)
  CARD   // 詳細カード表示
}

enum class CatalogTab(val label: String) {
  CURATED("📋 おすすめ厳選 (44種)"),
  HF_SEARCH("🌐 Hugging Face検索"),
  GGUF_IMPORT("📁 GGUFインポート")
}

data class CatalogModelItem(
  val id: String,
  val name: String,
  val rating: Float,
  val reviewCount: String,
  val vramRange: String,
  val vramTier: VramTier,
  val params: String,
  val categories: List<ModelCategory>,
  val description: String,
  val highlights: List<String>,
)

data class PullProgressItem(
  val model: String,
  val status: String,
  val completed: Long,
  val total: Long,
  val percent: Int,
  val done: Boolean,
  val error: String? = null,
)

data class HfModelResult(
  val id: String,
  val pullId: String,
  val likes: Int,
  val downloads: Int,
  val pipelineTag: String?,
  val tags: List<String>,
)

data class ScannedGgufFile(
  val name: String,
  val path: String,
  val sizeGb: String,
)

@Composable
internal fun OllamaCatalogScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf(CatalogTab.CURATED) }
  var customModelName by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(ModelCategory.ALL) }
  var selectedVramTier by remember { mutableStateOf(VramTier.ALL) }
  var viewMode by remember { mutableStateOf(ViewMode.TABLE) }
  var expandedModelId by remember { mutableStateOf<String?>(null) }

  // Hugging Face ライブ検索用
  var hfSearchQuery by remember { mutableStateOf("") }
  var hfCategory by remember { mutableStateOf("all") }
  var hfSort by remember { mutableStateOf("downloads") }
  var isHfSearching by remember { mutableStateOf(false) }
  var hfSearchResults by remember { mutableStateOf<List<HfModelResult>>(emptyList()) }

  // GGUF インポート / スキャン用
  var isScanningGguf by remember { mutableStateOf(false) }
  var scannedGgufFiles by remember { mutableStateOf<List<ScannedGgufFile>>(emptyList()) }
  var manualGgufPath by remember { mutableStateOf("") }
  var manualGgufModelName by remember { mutableStateOf("") }
  var isImportingGguf by remember { mutableStateOf(false) }

  var statusMessage by remember { mutableStateOf("") }
  var installedModels by remember { mutableStateOf<List<InstalledOllamaModel>>(emptyList()) }
  var activeModel by remember { mutableStateOf("") }
  var isLoadingModels by remember { mutableStateOf(false) }
  var isDeletingModel by remember { mutableStateOf(false) }
  var modelToDelete by remember { mutableStateOf<String?>(null) }
  var activeProgressMap by remember { mutableStateOf<Map<String, PullProgressItem>>(emptyMap()) }



  // インターネット調査・ベンチマーク・人気評価に基づく厳選42モデル
  val comprehensiveCatalog = remember {
    listOf(
      // === 1. 日本語特化・最高峰 (Japanese Capable) ===
      CatalogModelItem(
        id = "qwen2.5:latest",
        name = "Qwen 2.5 (7B)",
        rating = 4.9f,
        reviewCount = "18k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.6B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.ROLEPLAY, ModelCategory.CODING),
        description = "Alibaba開発。7B世界最高峰の日本語対話力・推論力。自然な敬語から複雑な相談まで万能。",
        highlights = listOf("日本語最高峰", "32kコンテキスト", "多言語・万能推奨")
      ),
      CatalogModelItem(
        id = "qwen2.5:14b",
        name = "Qwen 2.5 (14B)",
        rating = 5.0f,
        reviewCount = "12k+",
        vramRange = "9.5〜11.5 GB",
        vramTier = VramTier.HIGH,
        params = "14.8B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.REASONING, ModelCategory.CODING),
        description = "7Bを遥かに凌駕する深い文脈理解と論述力。複雑な長文読解や高度なプログラミングに抜群の性能。",
        highlights = listOf("圧倒的知性", "RTX 3060/4070推奨", "プロレベル")
      ),
      CatalogModelItem(
        id = "qwen2.5:32b",
        name = "Qwen 2.5 (32B)",
        rating = 5.0f,
        reviewCount = "8k+",
        vramRange = "20.0〜24.0 GB",
        vramTier = VramTier.ULTRA,
        params = "32.5B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.REASONING, ModelCategory.CODING),
        description = "GPT-4 / Claude 3.5 Sonnetに肉薄するオープンモデル最高峰の超知能。圧倒的な日本語表現力と論理構築。",
        highlights = listOf("商用API匹敵", "超巨大フラグシップ", "最高峰の推論力")
      ),
      CatalogModelItem(
        id = "qwen2.5:7b",
        name = "Qwen 2.5 (7B) 高精度日本語",
        rating = 4.8f,
        reviewCount = "19k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.6B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.ROLEPLAY),
        description = "世界的な大人気モデル。日本の文化・慣習・敬語に自然に適応し、高速かつ極めて流暢な日本語で応答。",
        highlights = listOf("美しい日本語", "高い指示追従", "丁寧な対話")
      ),
      CatalogModelItem(
        id = "llama3.1:8b",
        name = "Meta Llama 3.1 (8B) 学術・論理",
        rating = 4.7f,
        reviewCount = "26k+",
        vramRange = "6.0〜7.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.REASONING),
        description = "Meta社製の世界標準モデル。学術知識、論理的な論述、専門用語に強いグローバル高品質基盤。",
        highlights = listOf("世界標準モデル", "専門知識・解説", "論理的記述")
      ),
      CatalogModelItem(
        id = "llama3.3:70b",
        name = "Meta Llama 3.3 (70B) 超巨大フラグシップ",
        rating = 4.9f,
        reviewCount = "13k+",
        vramRange = "40.0〜48.0 GB",
        vramTier = VramTier.ULTRA,
        params = "70.6B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.REASONING),
        description = "70Bクラス最新の超知能モデル。重厚な学術論文から複雑なビジネス戦略まで圧倒的知性で解決。",
        highlights = listOf("超巨大フラグシップ", "GPT-4o匹敵知能", "マルチGPU/大容量RAM推奨")
      ),
      CatalogModelItem(
        id = "aya:8b",
        name = "Cohere Aya (8B) 多言語会話",
        rating = 4.6f,
        reviewCount = "8k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.ROLEPLAY),
        description = "Cohere社開発の多言語特化モデル。日本語を含む複数言語間の自然な翻訳や親しみやすい日常対話に強み。",
        highlights = listOf("多言語特化", "自然な会話", "多文化適応")
      ),
      CatalogModelItem(
        id = "qwen2.5:3b",
        name = "Qwen 2.5 (3B) 高速日本語",
        rating = 4.6f,
        reviewCount = "12k+",
        vramRange = "2.2〜3.2 GB",
        vramTier = VramTier.LOW,
        params = "3.1B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.LIGHTWEIGHT),
        description = "3Bクラス世界最強の日本語モデル。スマホや低スペック環境でも瞬時にサクサク日本語で応答可能。",
        highlights = listOf("超高速レスポンス", "省メモリ動作", "高精度日本語")
      ),
      CatalogModelItem(
        id = "qwen2.5:14b",
        name = "Qwen 2.5 (14B) ビジネス・推論",
        rating = 4.8f,
        reviewCount = "14k+",
        vramRange = "9.0〜11.0 GB",
        vramTier = VramTier.HIGH,
        params = "14.8B",
        categories = listOf(ModelCategory.JAPANESE, ModelCategory.REASONING),
        description = "ビジネス・経済ニュース・複雑な文章推敲に秀でる14Bモデル。企業分析や深い論理構築に最適。",
        highlights = listOf("ビジネス・経済特化", "長文分析", "専門用語に強い")
      ),
      CatalogModelItem(
        id = "llama3.2:3b",
        name = "Meta Llama 3.2 (3B) 親愛アシスタント",
        rating = 4.6f,
        reviewCount = "15k+",
        vramRange = "2.5〜3.5 GB",
        vramTier = VramTier.LOW,
        params = "3.2B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.JAPANESE, ModelCategory.ROLEPLAY),
        description = "親しみやすい日常会話や雑談、愛着の湧くやりとりに最適。軽量でスムーズに動作します。",
        highlights = listOf("親しみやすい日常会話", "雑談特化", "軽量高速")
      ),

      // === 2. 制限解除・無検閲 (Uncensored / Abliterated) ===
      CatalogModelItem(
        id = "huihui_ai/qwen2.5-abliterate:7b",
        name = "Qwen 2.5 (7B) Abliterated",
        rating = 4.9f,
        reviewCount = "22k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.6B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.JAPANESE, ModelCategory.ROLEPLAY),
        description = "世界的大人気Qwen 2.5の安全拒絶・説教フィルターを数学的に完全除去。高精度な日本語力と推論を保ちながら自由な創作・対話が可能。",
        highlights = listOf("拒絶・説教ゼロ", "日本語力完全維持", "自由な創作対話")
      ),
      CatalogModelItem(
        id = "huihui_ai/qwen2.5-abliterate:14b",
        name = "Qwen 2.5 (14B) Abliterated",
        rating = 5.0f,
        reviewCount = "16k+",
        vramRange = "9.5〜11.5 GB",
        vramTier = VramTier.HIGH,
        params = "14.8B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.JAPANESE, ModelCategory.REASONING),
        description = "14Bの圧倒的知性をもつ無検閲モデル。深い哲学的対話や複雑な世界観の創作小説も完全制限なしで執筆可能。",
        highlights = listOf("高知能無検閲", "深い長文執筆", "制約のない創作")
      ),
      CatalogModelItem(
        id = "huihui_ai/qwen2.5-abliterate:32b",
        name = "Qwen 2.5 (32B) Abliterated",
        rating = 5.0f,
        reviewCount = "11k+",
        vramRange = "20.0〜24.0 GB",
        vramTier = VramTier.ULTRA,
        params = "32.5B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.JAPANESE, ModelCategory.REASONING),
        description = "32B最強知性×制限解除の究極モデル。あらゆる制約から解放された圧倒的な文脈把握と文学的表現力。",
        highlights = listOf("世界最強無検閲", "文学的表現力", "完全ノーリフューザル")
      ),
      CatalogModelItem(
        id = "huihui_ai/deepseek-r1-abliterated:latest",
        name = "DeepSeek-R1 Abliterated",
        rating = 5.0f,
        reviewCount = "25k+",
        vramRange = "6.0〜8.0 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.REASONING, ModelCategory.CODING),
        description = "世界を震撼させたDeepSeek-R1の思考プロセス（Chain of Thought）から検閲・制限を取り払った究極の思考型無検閲モデル。",
        highlights = listOf("思考プロセス×無検閲", "難問・深い論考", "o1級思考ログ")
      ),
      CatalogModelItem(
        id = "dolphin-llama3:8b",
        name = "Dolphin Llama 3 (8B) 無検閲",
        rating = 4.9f,
        reviewCount = "18k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.ROLEPLAY),
        description = "オープンソース界の無検閲モデルの金字塔Dolphinシリーズ。倫理フィルタを事前学習段階から排除した自由対話。",
        highlights = listOf("元祖無検閲", "高い指示追従", "自由な対話")
      ),
      CatalogModelItem(
        id = "dolphin-mistral:latest",
        name = "Dolphin Mistral (7B) 自由創作",
        rating = 4.8f,
        reviewCount = "14k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.2B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.ROLEPLAY),
        description = "Mistralベースの制限解除モデル。忌憚のないディスカッションや柔軟なシナリオ創作をスムーズに実行。",
        highlights = listOf("完全ノーリフューザル", "柔軟な対話", "制約のない創作")
      ),
      CatalogModelItem(
        id = "hermes3:8b",
        name = "Nous Hermes 3 (8B) 倫理解除",
        rating = 4.9f,
        reviewCount = "16k+",
        vramRange = "6.0〜7.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.ROLEPLAY),
        description = "Nous Research開発。過剰な倫理検閲を排除し、人間の指示にどこまでも忠実に従うオープンソースの傑作。",
        highlights = listOf("完全ノーリフューザル", "忠実な指示追従", "ロールプレイ最高峰")
      ),
      CatalogModelItem(
        id = "huihui_ai/llama3.3-abliterated:latest",
        name = "Llama 3.3 (70B) Abliterated",
        rating = 5.0f,
        reviewCount = "9k+",
        vramRange = "40.0〜48.0 GB",
        vramTier = VramTier.ULTRA,
        params = "70.6B",
        categories = listOf(ModelCategory.UNCENSORED, ModelCategory.REASONING),
        description = "最新フラグシップLlama 3.3 70Bから説教フィルターを完全除去したプロ向け超巨大無制限モデル。",
        highlights = listOf("70B巨大無検閲", "多層的思考", "一切の検閲なし")
      ),

      // === 3. ロールプレイ・キャラクター対話 (Roleplay / AI Partner) ===
      CatalogModelItem(
        id = "mistral-nemo:12b",
        name = "Mistral Nemo (12B) 小説執筆・創作対話",
        rating = 4.9f,
        reviewCount = "18k+",
        vramRange = "8.5〜10.5 GB",
        vramTier = VramTier.HIGH,
        params = "12.2B",
        categories = listOf(ModelCategory.ROLEPLAY, ModelCategory.UNCENSORED),
        description = "128kコンテキストに対応し、登場人物のペルソナ維持・情景描写・セリフの機微において世界最高峰の評価を受けるモデル。",
        highlights = listOf("文学的描写力", "強固なキャラ維持", "128k長大記憶")
      ),
      CatalogModelItem(
        id = "wizard-vicuna-uncensored:latest",
        name = "Wizard Vicuna (7B) 感情キャラクター対話",
        rating = 4.7f,
        reviewCount = "12k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.0B",
        categories = listOf(ModelCategory.ROLEPLAY, ModelCategory.UNCENSORED),
        description = "感情豊かなセリフや親密な対話に特化した無検閲RPモデル。AIパートナーの自由な人格形成に最適。",
        highlights = listOf("感情豊かな口調", "キャラロールプレイ", "親密度表現")
      ),
      CatalogModelItem(
        id = "mistral:7b",
        name = "Mistral (7B) ペルソナ維持・洗練会話",
        rating = 4.8f,
        reviewCount = "25k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.2B",
        categories = listOf(ModelCategory.ROLEPLAY, ModelCategory.JAPANESE),
        description = "元祖高密度推論モデル。自然な会話のキャッチボールと心情描写、スマートなテンポが秀逸。",
        highlights = listOf("情景描写力", "テンポの良い会話", "クリエイティブ")
      ),

      // === 4. 思考・推論・ディープシンキング (Reasoning / o1系) ===
      CatalogModelItem(
        id = "deepseek-r1:1.5b",
        name = "DeepSeek-R1 (1.5B)",
        rating = 4.6f,
        reviewCount = "15k+",
        vramRange = "1.5〜2.5 GB",
        vramTier = VramTier.LOW,
        params = "1.5B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.LIGHTWEIGHT),
        description = "超軽量ながら「思考プロセス（思考ログ）」を出力する革新的小型モデル。スマホやノートPCでも軽快に推論。",
        highlights = listOf("超軽量思考型", "思考の可視化", "スマホでも軽快")
      ),
      CatalogModelItem(
        id = "deepseek-r1:8b",
        name = "DeepSeek-R1 (8B)",
        rating = 4.9f,
        reviewCount = "28k+",
        vramRange = "6.0〜7.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.CODING, ModelCategory.JAPANESE),
        description = "OpenAI o1匹敵の思考プロセス（Chain of Thought）を持つ最新推論モデル。回答前に深く自問自答して高精度な結論を導く。",
        highlights = listOf("思考プロセス可視化", "難問・数学特化", "世界的人気")
      ),
      CatalogModelItem(
        id = "deepseek-r1:14b",
        name = "DeepSeek-R1 (14B)",
        rating = 5.0f,
        reviewCount = "20k+",
        vramRange = "9.5〜11.5 GB",
        vramTier = VramTier.HIGH,
        params = "14.0B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.CODING),
        description = "Qwen 2.5 14Bをベースに蒸留された最高峰の思考型モデル。複雑なロジックやアルゴリズム問題を熟考して回答。",
        highlights = listOf("最高峰の推論力", "難関コーディング", "o1級知性")
      ),
      CatalogModelItem(
        id = "deepseek-r1:32b",
        name = "DeepSeek-R1 (32B)",
        rating = 5.0f,
        reviewCount = "14k+",
        vramRange = "20.0〜24.0 GB",
        vramTier = VramTier.ULTRA,
        params = "32.0B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.CODING),
        description = "OpenAI o1-previewに匹敵する最高峰のオープン推論モデル。最難関のプログラミング競技や数学論文レベルの解法を導出。",
        highlights = listOf("o1-preview匹敵", "最難関ロジック解決", "最高峰の知性")
      ),
      CatalogModelItem(
        id = "deepseek-r1:70b",
        name = "DeepSeek-R1 (70B)",
        rating = 5.0f,
        reviewCount = "9k+",
        vramRange = "40.0〜48.0 GB",
        vramTier = VramTier.ULTRA,
        params = "70.6B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.CODING),
        description = "Llama 3.3 70Bをベースにした超巨大思考モデル。複雑多岐にわたる課題を多角的に分解・推論して完璧な回答を出力。",
        highlights = listOf("70B巨大思考型", "多角分析", "エンタープライズ推論")
      ),

      // === 5. プログラミング・コーディング (Coding) ===
      CatalogModelItem(
        id = "qwen2.5-coder:1.5b",
        name = "Qwen 2.5 Coder (1.5B)",
        rating = 4.6f,
        reviewCount = "8k+",
        vramRange = "1.5〜2.5 GB",
        vramTier = VramTier.LOW,
        params = "1.5B",
        categories = listOf(ModelCategory.CODING, ModelCategory.LIGHTWEIGHT),
        description = "超軽量プログラミング特化。エディタのインライン補完やコード解説を瞬時に超高速レスポンス。",
        highlights = listOf("超軽量コード補完", "超高速レスポンス", "低リソース")
      ),
      CatalogModelItem(
        id = "qwen2.5-coder:latest",
        name = "Qwen 2.5 Coder (7B)",
        rating = 4.9f,
        reviewCount = "16k+",
        vramRange = "5.0〜6.5 GB",
        vramTier = VramTier.MID,
        params = "7.6B",
        categories = listOf(ModelCategory.CODING, ModelCategory.JAPANESE),
        description = "GPT-4oに匹敵する精度を誇るプログラミング特化。あらゆる言語の実装・バグ修正・リファクタリングに対応。",
        highlights = listOf("世界最高峰コード力", "128kコンテキスト", "日本語解説")
      ),
      CatalogModelItem(
        id = "qwen2.5-coder:14b",
        name = "Qwen 2.5 Coder (14B)",
        rating = 5.0f,
        reviewCount = "12k+",
        vramRange = "9.5〜11.5 GB",
        vramTier = VramTier.HIGH,
        params = "14.8B",
        categories = listOf(ModelCategory.CODING, ModelCategory.JAPANESE),
        description = "フルスタック開発・インフラ構築・複雑なアーキテクチャ設計に対応する大型コード専用モデル。",
        highlights = listOf("フルスタック開発", "アーキテクチャ設計", "精密なリファクタリング")
      ),
      CatalogModelItem(
        id = "qwen2.5-coder:32b",
        name = "Qwen 2.5 Coder (32B)",
        rating = 5.0f,
        reviewCount = "10k+",
        vramRange = "20.0〜24.0 GB",
        vramTier = VramTier.ULTRA,
        params = "32.5B",
        categories = listOf(ModelCategory.CODING, ModelCategory.JAPANESE),
        description = "Claude 3.5 Sonnetと互角に渡り合う最強のオープンコードモデル。巨大リポジトリの解析・リバースエンジニアリング。",
        highlights = listOf("Claude Sonnet級", "最強OSSコードモデル", "巨大コードベース解析")
      ),
      CatalogModelItem(
        id = "codestral:22b",
        name = "Codestral (22B)",
        rating = 4.8f,
        reviewCount = "9k+",
        vramRange = "14.0〜16.0 GB",
        vramTier = VramTier.ULTRA,
        params = "22.2B",
        categories = listOf(ModelCategory.CODING),
        description = "Mistral AI社開発の巨大コード特化モデル。80以上の言語と長大なコードベースを解析・FIM補完。",
        highlights = listOf("巨大コード解析", "FIM補完", "エンタープライズ品質")
      ),
      CatalogModelItem(
        id = "deepseek-coder-v2:16b",
        name = "DeepSeek Coder V2 (16B)",
        rating = 4.9f,
        reviewCount = "11k+",
        vramRange = "10.0〜12.5 GB",
        vramTier = VramTier.HIGH,
        params = "16.0B (MoE)",
        categories = listOf(ModelCategory.CODING),
        description = "MoE（混合エキスパート）構造により、336以上のプログラミング言語と128k長大コンテキストを快適処理。",
        highlights = listOf("336言語対応", "MoEアーキテクチャ", "128kコンテキスト")
      ),

      // === 6. 汎用フラグシップ・定番 (General) ===
      CatalogModelItem(
        id = "llama3.1:latest",
        name = "Meta Llama 3.1 (8B)",
        rating = 4.8f,
        reviewCount = "30k+",
        vramRange = "6.0〜7.5 GB",
        vramTier = VramTier.MID,
        params = "8.0B",
        categories = listOf(ModelCategory.ROLEPLAY, ModelCategory.CODING),
        description = "Meta社が誇る世界標準のフラグシップモデル。128kコンテキスト、優れたツール呼び出し能力と知識量。",
        highlights = listOf("世界標準フラグシップ", "128kコンテキスト", "高い安定性")
      ),
      CatalogModelItem(
        id = "llama3.1:70b",
        name = "Meta Llama 3.1 (70B)",
        rating = 4.9f,
        reviewCount = "15k+",
        vramRange = "40.0〜48.0 GB",
        vramTier = VramTier.ULTRA,
        params = "70.6B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.CODING),
        description = "業界最高峰のオープンソース大型モデル。あらゆるタスクで商用トップAPIと肩を並べる知能を発揮。",
        highlights = listOf("業界最高水準", "膨大な知識量", "最高峰の安定性")
      ),
      CatalogModelItem(
        id = "gemma2:9b",
        name = "Google Gemma 2 (9B)",
        rating = 4.8f,
        reviewCount = "15k+",
        vramRange = "6.5〜8.0 GB",
        vramTier = VramTier.MID,
        params = "9.2B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.JAPANESE),
        description = "Googleが開発した新世代アーキテクチャ。パラメータ数以上の驚異的な論理的思考力と事実再現性を実現。",
        highlights = listOf("Google最新設計", "高密度パラメータ", "優れた事実精度")
      ),
      CatalogModelItem(
        id = "gemma2:27b",
        name = "Google Gemma 2 (27B)",
        rating = 4.9f,
        reviewCount = "8k+",
        vramRange = "16.0〜19.0 GB",
        vramTier = VramTier.ULTRA,
        params = "27.2B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.JAPANESE),
        description = "70B級モデルに匹敵するベンチマークスコアを叩き出すGoogleの傑作大型モデル。高い安全性と正確な知識。",
        highlights = listOf("70B匹敵スコア", "Googleフラグシップ", "高密度推論")
      ),

      // === 7. 超軽量・省電力・モバイル向け (〜4GB VRAM) ===
      CatalogModelItem(
        id = "qwen2.5:0.5b",
        name = "Qwen 2.5 (0.5B)",
        rating = 4.5f,
        reviewCount = "7k+",
        vramRange = "0.8〜1.2 GB",
        vramTier = VramTier.LOW,
        params = "0.5B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.JAPANESE),
        description = "メモリ1GB以下で動作する世界最小クラスの実用モデル。エッジ端末やバックグラウンド監視に最適。",
        highlights = listOf("メモリ1GB未満", "超省電力", "超高速レスポンス")
      ),
      CatalogModelItem(
        id = "qwen2.5:1.5b",
        name = "Qwen 2.5 (1.5B)",
        rating = 4.6f,
        reviewCount = "9k+",
        vramRange = "1.5〜2.2 GB",
        vramTier = VramTier.LOW,
        params = "1.5B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.JAPANESE),
        description = "2GB前後のVRAMで軽快に動作。日本語の日常対話や軽い要約タスクを瞬時にこなす俊足モデル。",
        highlights = listOf("VRAM 2GB動作", "高速対話", "省リソース")
      ),
      CatalogModelItem(
        id = "qwen2.5:3b",
        name = "Qwen 2.5 (3B)",
        rating = 4.7f,
        reviewCount = "12k+",
        vramRange = "2.5〜3.5 GB",
        vramTier = VramTier.LOW,
        params = "3.1B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.JAPANESE, ModelCategory.ROLEPLAY),
        description = "わずか3GB前後のメモリで動作。スマホやノートPC、低スペック環境でも爆速で日本語対話が可能。",
        highlights = listOf("VRAM 3GBで動作", "超高速レスポンス", "省電力")
      ),
      CatalogModelItem(
        id = "phi3:mini",
        name = "Phi-3.5 Mini (3.8B)",
        rating = 4.6f,
        reviewCount = "14k+",
        vramRange = "2.8〜3.8 GB",
        vramTier = VramTier.LOW,
        params = "3.8B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.REASONING),
        description = "Microsoftが教科書品質データで学習させた高密度モデル。サイズからは信じられない論理的思考力を発揮。",
        highlights = listOf("教科書品質データ", "128k長文対応", "低負荷")
      ),
      CatalogModelItem(
        id = "llama3.2:3b",
        name = "Meta Llama 3.2 (3B)",
        rating = 4.7f,
        reviewCount = "16k+",
        vramRange = "2.5〜3.5 GB",
        vramTier = VramTier.LOW,
        params = "3.2B",
        categories = listOf(ModelCategory.LIGHTWEIGHT, ModelCategory.ROLEPLAY),
        description = "Metaの最新軽量モデル。端末上での高速処理を目的に最適化され、驚くほどレスポンスが俊敏。",
        highlights = listOf("Meta最新軽量", "爆速レスポンス", "エッジ最適化")
      ),
      // === Ternary-Bonsai-2-27B (1-bit/2-bit 三値量子化・27B超軽量) ===
      CatalogModelItem(
        id = "hf.co/prism-ml/Ternary-Bonsai-2-27B-gguf:PTQ1_0",
        name = "Ternary-Bonsai-2-27B (PTQ1_0・1bit)",
        rating = 4.7f,
        reviewCount = "新着",
        vramRange = "6.0〜7.5 GB",
        vramTier = VramTier.MID,
        params = "27B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.ROLEPLAY),
        description = "27B規模なのに1bit Ternary量子化で約6GB。Gemma-3ベースの最新高性能モデル。マルチモーダル対応。Ollama 0.6.5以降が必要。",
        highlights = listOf("27B超軽量", "1bit Ternary", "Gemma-3ベース", "Ollama最新版必須")
      ),
      CatalogModelItem(
        id = "hf.co/prism-ml/Ternary-Bonsai-2-27B-gguf:PQ2_0",
        name = "Ternary-Bonsai-2-27B (PQ2_0・2bit)",
        rating = 4.8f,
        reviewCount = "新着",
        vramRange = "7.0〜9.0 GB",
        vramTier = VramTier.MID,
        params = "27B",
        categories = listOf(ModelCategory.REASONING, ModelCategory.ROLEPLAY),
        description = "27Bを2bit量子化で約7.2GB。PTQ1_0より精度が高く、表現力・論理力が向上。Gemma-3ベース。Ollama 0.6.5以降が必要。",
        highlights = listOf("27B高精度版", "2bit量子化", "Gemma-3ベース", "Ollama最新版必須")
      ),
    )
  }

  // フィルター処理
  val filteredCatalog = remember(comprehensiveCatalog, searchQuery, selectedCategory, selectedVramTier) {
    comprehensiveCatalog.filter { item ->
      val matchesQuery = searchQuery.isBlank() ||
        item.name.contains(searchQuery, ignoreCase = true) ||
        item.id.contains(searchQuery, ignoreCase = true) ||
        item.description.contains(searchQuery, ignoreCase = true) ||
        item.highlights.any { it.contains(searchQuery, ignoreCase = true) }

      val matchesCategory = selectedCategory == ModelCategory.ALL || item.categories.contains(selectedCategory)
      val matchesVram = selectedVramTier == VramTier.ALL || item.vramTier == selectedVramTier

      matchesQuery && matchesCategory && matchesVram
    }
  }

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
      String.format("%.2f GB", mb / 1024)
    } else {
      String.format("%.1f MB", mb)
    }
  }

  fun findProgress(modelId: String): PullProgressItem? {
    val cleanId = modelId.replace("ollama/", "").trim()
    return activeProgressMap[cleanId]
      ?: activeProgressMap[modelId]
      ?: activeProgressMap.values.find {
        it.model == cleanId || it.model == modelId ||
        it.model.endsWith("/$cleanId") || cleanId.endsWith("/${it.model}") ||
        it.model.contains(cleanId) || cleanId.contains(it.model)
      }
  }

  fun resolveModelMetadata(name: String, sizeGbStr: String): Triple<String, String, List<String>> {
    val cleanName = name.replace("ollama/", "").trim()
    val lower = cleanName.lowercase()
    val sizeVal = sizeGbStr.replace(" GB", "").toDoubleOrNull() ?: 5.0

    // 1. 42モデルカタログとの照合
    val matched = comprehensiveCatalog.find { cat ->
      cat.id.equals(cleanName, ignoreCase = true) ||
      cat.id.substringBefore(":").equals(cleanName.substringBefore(":"), ignoreCase = true) ||
      cat.name.lowercase().split(" ").any { part -> part.length >= 4 && lower.contains(part) }
    }

    val execHint = if (matched != null) {
      when (matched.vramTier) {
        VramTier.LOW, VramTier.MID -> "⚡ GPU高速"
        VramTier.HIGH -> "⚡ GPU推奨 (VRAM 8GB+)"
        VramTier.ULTRA -> "📦 大型 (自動RAM併用)"
        else -> "⚡ GPU標準"
      }
    } else {
      if (sizeVal > 15.0) "📦 超大型 (自動RAM併用)"
      else if (sizeVal > 8.0) "⚡ 大型 (GPU推奨)"
      else "⚡ GPU高速"
    }

    if (matched != null) {
      return Triple(matched.description, execHint, matched.highlights)
    }

    val inferredDesc = when {
      lower.contains("qwen") -> "Alibaba開発の世界最高峰言語モデル。高い推論能力と卓越した日本語表現力を発揮します。"
      lower.contains("llama") -> "Meta開発の世界基準LLM。幅広い知識と高い汎用タスク実行能力を備えています。"
      lower.contains("mistral") || lower.contains("mixtral") -> "Mistral AI開発。キレのある文体と論理的な推論に優れた欧州発モデル。"
      lower.contains("deepseek") -> "DeepSeek開発の論理・数学・コード特化モデル。深い思考プロセスを展開可能。"
      lower.contains("gemma") -> "Google開発のオープンモデル。親しみやすく整った文章生成が得意。"
      lower.contains("phi") -> "Microsoft開発の小型高知能モデル。省リソースで鋭い論理応答。"
      lower.contains("elyza") -> "東大発ELYZAの日本語モデル。美しい日本語表現と自然な会話。"
      lower.contains("command-r") -> "Cohere開発の長文・RAG検索特化モデル。膨大なドキュメントの要約に最適。"
      lower.contains("code") || lower.contains("coder") -> "プログラミング・スクリプト生成特化モデル。"
      lower.contains("uncensored") || lower.contains("abliterated") -> "安全性フィルタ解除モデル。あらゆる創作表現や制約のない回答が可能。"
      else -> "PCにインストール済みのローカルLLMモデル。Ollama上で即座に実行可能です。"
    }

    val tags = mutableListOf<String>()
    if (lower.contains("code") || lower.contains("coder")) tags.add("💻 コード特化")
    if (lower.contains("uncensored") || lower.contains("abliterated")) tags.add("🔓 制限解除")
    if (lower.contains("jp") || lower.contains("japan") || lower.contains("elyza") || lower.contains("swallow")) tags.add("🇯🇵 日本語")
    if (sizeVal > 10.0) tags.add("📦 大型(RAM併用)") else tags.add("⚡ GPU高速")

    return Triple(inferredDesc, execHint, tags)
  }

  fun fetchModels() {
    isLoadingModels = true
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.OllamaModelsList, "{}", timeoutMs = 30_000L)
        val rawObj = JSONObject(resultJson)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val current = obj.optString("currentModel", "")
        activeModel = current.replace("ollama/", "")

        val modelsArr = obj.optJSONArray("models")
        val list = mutableListOf<InstalledOllamaModel>()
        if (modelsArr != null) {
          for (i in 0 until modelsArr.length()) {
            val m = modelsArr.getJSONObject(i)
            val name = m.optString("name", "")
            val size = m.optLong("size", 0L)
            val sizeStr = if (size > 0) String.format("%.1f GB", size.toDouble() / (1024 * 1024 * 1024)) else ""
            if (name.isNotBlank()) {
              val (desc, hint, tags) = resolveModelMetadata(name, sizeStr)
              list.add(
                InstalledOllamaModel(
                  name = name,
                  sizeGb = sizeStr,
                  description = desc,
                  executionHint = hint,
                  tags = tags,
                )
              )
            }
          }
        }
        installedModels = list
      } catch (e: Exception) {
        statusMessage = "モデル一覧の取得に失敗: ${e.message}"
      } finally {
        isLoadingModels = false
      }
    }
  }

  fun switchModel(modelName: String) {
    val cleanName = modelName.replace("ollama/", "")
    statusMessage = "モデルを切り替え中: $cleanName..."
    coroutineScope.launch {
      try {
        val currentPartnerId = viewModel.activePartnerId.value
        val params = JSONObject().apply {
          put("model", modelName)
          if (!currentPartnerId.isNullOrBlank()) {
            put("partnerId", currentPartnerId)
          }
        }
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaModelSwitch, params.toString(), timeoutMs = 60_000L)
        val rawObj = JSONObject(res)
        val resObj = rawObj.optJSONObject("payload") ?: rawObj
        val isOk = resObj.optBoolean("ok", false) || resObj.optBoolean("success", false) || (!resObj.has("error") && !resObj.has("ok"))
        if (!isOk || resObj.has("error")) {
          val errorMsg = resObj.optString("error", "切り替えに失敗しました")
          statusMessage = "切り替えエラー: $errorMsg"
          return@launch
        }
        activeModel = cleanName
        statusMessage = "モデルを「$cleanName」に切り替えました！ (システム＆パートナー反映)"
        fetchModels()
      } catch (e: Exception) {
        statusMessage = "切り替えエラー: ${e.message}"
      }
    }
  }


  fun deleteModel(name: String) {
    if (name.isBlank()) return
    isDeletingModel = true
    statusMessage = "モデル「$name」を削除中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply { put("name", name) }
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaDelete, params.toString(), timeoutMs = 60_000L)
        val rawObj = JSONObject(res)
        val resObj = rawObj.optJSONObject("payload") ?: rawObj
        val isOk = resObj.optBoolean("ok", false) || resObj.optBoolean("success", false) || (!resObj.has("error") && !resObj.has("ok"))
        if (!isOk || resObj.has("error")) {
          val errorMsg = resObj.optString("error", "削除に失敗しました")
          statusMessage = "削除エラー: $errorMsg"
          return@launch
        }
        statusMessage = "🗑️ モデル「$name」を削除しました。"
        Toast.makeText(context, "モデル「$name」を削除しました", Toast.LENGTH_SHORT).show()
        fetchModels()
      } catch (e: Exception) {
        statusMessage = "削除エラー: ${e.message}"
      } finally {
        isDeletingModel = false
        modelToDelete = null
      }
    }
  }

  fun pullModel(name: String) {
    if (name.isBlank()) return
    statusMessage = "ダウンロード開始: $name\n(バックグラウンドでダウンロードが進み、進捗率がリアルタイム表示されます)"
    coroutineScope.launch {
      try {
        val params = JSONObject().apply { put("name", name) }
        viewModel.runGatewayRpc(GatewayMethod.OllamaPull, params.toString())
      } catch (e: Exception) {
        statusMessage = "ダウンロードリクエストエラー: ${e.message}"
      }
    }
  }

  fun searchHuggingFace() {
    isHfSearching = true
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          if (hfSearchQuery.isNotBlank()) put("query", hfSearchQuery)
          if (hfCategory != "all") put("category", hfCategory)
          put("sort", hfSort)
          put("limit", 25)
        }
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaHfSearch, params.toString())
        val rawObj = JSONObject(res)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val arr = obj.optJSONArray("models")
        val list = mutableListOf<HfModelResult>()
        if (arr != null) {
          for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val tagsArr = m.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
              for (j in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(j))
              }
            }
            list.add(
              HfModelResult(
                id = m.optString("id"),
                pullId = m.optString("pullId"),
                likes = m.optInt("likes"),
                downloads = m.optInt("downloads"),
                pipelineTag = if (m.has("pipelineTag") && !m.isNull("pipelineTag")) m.optString("pipelineTag") else null,
                tags = tags
              )
            )
          }
        }
        hfSearchResults = list
        if (list.isEmpty()) {
          statusMessage = "Hugging Faceで該当するGGUFモデルが見つかりませんでした。"
        }
      } catch (e: Exception) {
        statusMessage = "Hugging Face検索エラー: ${e.message}"
      } finally {
        isHfSearching = false
      }
    }
  }

  fun scanGgufFiles() {
    isScanningGguf = true
    coroutineScope.launch {
      try {
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaGgufScan, "{}")
        val rawObj = JSONObject(res)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val arr = obj.optJSONArray("files")
        val list = mutableListOf<ScannedGgufFile>()
        if (arr != null) {
          for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            list.add(
              ScannedGgufFile(
                name = f.optString("name"),
                path = f.optString("path"),
                sizeGb = f.optString("sizeGb"),
              )
            )
          }
        }
        scannedGgufFiles = list
        statusMessage = if (list.isEmpty()) "PC内に.ggufファイルは見つかりませんでした。" else "PC内で ${list.size} 件のGGUFファイルを検出しました。"
      } catch (e: Exception) {
        statusMessage = "GGUFスキャンエラー: ${e.message}"
      } finally {
        isScanningGguf = false
      }
    }
  }

  fun importGguf(modelName: String, filePath: String) {
    if (modelName.isBlank() || filePath.isBlank()) return
    isImportingGguf = true
    statusMessage = "GGUFモデルのインポートを開始: $modelName"
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put("name", modelName)
          put("filePath", filePath)
        }
        viewModel.runGatewayRpc(GatewayMethod.OllamaGgufImport, params.toString())
        statusMessage = "「$modelName」のインポート処理を開始しました。進捗はリアルタイム表示されます。"
      } catch (e: Exception) {
        statusMessage = "GGUFインポートエラー: ${e.message}"
      } finally {
        isImportingGguf = false
      }
    }
  }

  var isOptimizingModel by remember { mutableStateOf(false) }

  fun optimizeModel(name: String, sizeGb: Double = 5.0) {
    isOptimizingModel = true
    statusMessage = "「$name」のシステム最適化パラメータを計算中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put("name", name)
          put("modelSizeGb", sizeGb)
          put("targetDevice", "auto")
        }
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaModelOptimize, params.toString(), timeoutMs = 120_000L)
        val obj = JSONObject(res)
        val payload = obj.optJSONObject("payload") ?: obj
        val summary = payload.optString("summary", "最適化完了")
        val ctx = payload.optInt("num_ctx", 16384)
        val threads = payload.optInt("num_thread", 8)
        statusMessage = "⚡ 最適化完了: $summary (num_ctx: $ctx, threads: $threads)"
        Toast.makeText(context, "最適化完了: num_ctx $ctx", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        statusMessage = "最適化エラー: ${e.message}"
      } finally {
        isOptimizingModel = false
      }
    }
  }

  LaunchedEffect(Unit) {
    fetchModels()
    while (isActive) {
      try {
        val res = viewModel.runGatewayRpc(GatewayMethod.OllamaPullProgress, "{}")
        val rawObj = JSONObject(res)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val progresses = obj.optJSONObject("progresses")
        if (progresses != null) {
          val map = mutableMapOf<String, PullProgressItem>()
          val keys = progresses.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val pObj = progresses.optJSONObject(k)
            if (pObj != null) {
              val item = PullProgressItem(
                model = pObj.optString("model", k),
                status = pObj.optString("status", ""),
                completed = pObj.optLong("completed", 0L),
                total = pObj.optLong("total", 0L),
                percent = pObj.optInt("percent", 0),
                done = pObj.optBoolean("done", false),
                error = if (pObj.has("error") && !pObj.isNull("error")) pObj.optString("error") else null,
              )
              map[k] = item
            }
          }

          val wasDownloading = activeProgressMap.values.any { !it.done && it.error == null }
          val isStillDownloading = map.values.any { !it.done && it.error == null }
          activeProgressMap = map

          if (wasDownloading && !isStillDownloading) {
            statusMessage = "🎉 ダウンロード／インポートが完了しました！一覧に反映されました。"
            fetchModels()
          }

          // 問題11修正: ダウンロード中は1秒、待機中は10秒に延長しバッテリー節約
          delay(if (isStillDownloading) 1_000L else 10_000L)
        } else {
          delay(10_000L)
        }
      } catch (_: Exception) {
        // バックグラウンドポーリングのエラーは採湖しない。エラー時は長めの間隔で再試行。
        delay(10_000L)
      }
    }
  }

  val runningDownloads = activeProgressMap.values.filter { !it.done && it.error == null }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 36.dp),
      ) {
        // --- ヘッダー ---
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              ClawIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = nativeString("Back"),
                onClick = onBack,
              )
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == CatalogTab.CURATED) {
                  ClawSecondaryButton(
                    text = if (viewMode == ViewMode.TABLE) "📋 一覧表" else "🗂️ カード",
                    onClick = {
                      viewMode = if (viewMode == ViewMode.TABLE) ViewMode.CARD else ViewMode.TABLE
                    }
                  )
                }
                ClawIconButton(
                  icon = Icons.Default.Refresh,
                  contentDescription = "再読み込み",
                  onClick = { fetchModels() },
                )
              }
            }
            Text(
              text = "モデルカタログ ＆ 高度検索",
              style = ClawTheme.type.display.copy(fontSize = 19.sp, lineHeight = 23.sp),
              color = ClawTheme.colors.text,
            )
            Text(
              text = "厳選44モデル、Hugging Faceライブ検索、PC内GGUFインポートに対応。",
              style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
              color = ClawTheme.colors.textMuted,
            )
          }
        }

        // --- メインタブ切替 (厳選 / HF検索 / GGUFインポート) ---
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CatalogTab.entries.forEach { tab ->
              val isSelected = selectedTab == tab
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised)
                  .clickable { selectedTab = tab }
                  .padding(horizontal = 14.dp, vertical = 8.dp)
              ) {
                Text(
                  text = tab.label,
                  style = ClawTheme.type.section.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.text
                )
              }
            }
          }
        }

        // --- 💻 PCモデル管理パネル ---
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "💻 モデル実行管理",
                  style = ClawTheme.type.section.copy(fontSize = 15.sp),
                  color = ClawTheme.colors.primary,
                )
                Text(
                  text = if (activeModel.isNotBlank()) "現在: $activeModel" else "モデル未選択",
                  style = ClawTheme.type.caption,
                  color = if (activeModel.isNotBlank()) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                )
              }

              // --- メインLLM (GPU優先・自動RAMオフロード) ---
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = "⚡ GPU動作 (自動RAMオフロード対応)",
                  style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                  color = ClawTheme.colors.primary
                )
                Text(
                  text = "💡 通常はGPU(VRAM)で超高速動作します。VRAMを超える大型モデル時はOllamaが自動でRAMに分割オフロードするため設定不要です。",
                  style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                  color = ClawTheme.colors.textMuted
                )
              }
            }
          }
        }

        // --- ダウンロード進行中カード (リアルタイム％ ＆ アニメーションバー) ---
        if (runningDownloads.isNotEmpty()) {
          item {
            ClawPanel {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = "⏳ ダウンロード／インポート進行中 (${runningDownloads.size}件)",
                  style = ClawTheme.type.section.copy(fontSize = 15.sp),
                  color = ClawTheme.colors.primary,
                )
                runningDownloads.forEach { prog ->
                  val formattedCompleted = formatBytes(prog.completed)
                  val formattedTotal = if (prog.total > 0) formatBytes(prog.total) else "--"
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = prog.model,
                        style = ClawTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = ClawTheme.colors.text,
                        modifier = Modifier.weight(1f),
                      )
                      Text(
                        text = "${prog.percent}%",
                        style = ClawTheme.type.section.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = ClawTheme.colors.primary,
                      )
                    }
                    LinearProgressIndicator(
                      progress = { (prog.percent / 100f).coerceIn(0f, 1f) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                      color = ClawTheme.colors.primary,
                      trackColor = ClawTheme.colors.surfaceRaised,
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text = prog.status,
                        style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                        color = ClawTheme.colors.textMuted,
                      )
                      Text(
                        text = "$formattedCompleted / $formattedTotal",
                        style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                        color = ClawTheme.colors.textMuted,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        if (statusMessage.isNotBlank()) {
          item {
            ClawPanel {
              Text(
                text = statusMessage,
                style = ClawTheme.type.body,
                color = ClawTheme.colors.text,
              )
            }
          }
        }

        // ==========================================
        // TAB 1: おすすめ厳選モデル (42種)
        // ==========================================
        if (selectedTab == CatalogTab.CURATED) {
          // --- 検索バー ＆ 絞り込みフィルター ---
          item {
            ClawPanel {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 検索ボックス
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "検索",
                    tint = ClawTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                  )
                  Box(modifier = Modifier.weight(1f)) {
                    ClawTextField(
                      value = searchQuery,
                      onValueChange = { searchQuery = it },
                      placeholder = "モデル名、タグ、特徴で検索 (例: 制限解除, 7B, ELYZA)...",
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                  if (searchQuery.isNotBlank()) {
                    ClawIconButton(
                      icon = Icons.Default.Clear,
                      contentDescription = "クリア",
                      onClick = { searchQuery = "" }
                    )
                  }
                }

                // カテゴリ絞り込みチップ
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "分類:",
                    style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = ClawTheme.colors.textMuted,
                    modifier = Modifier.padding(end = 2.dp)
                  )
                  ModelCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised)
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = cat.label,
                        style = ClawTheme.type.caption.copy(
                          fontSize = 11.5.sp,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.text
                      )
                    }
                  }
                }

                // VRAM範囲絞り込みチップ
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "VRAM:",
                    style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = ClawTheme.colors.textMuted,
                    modifier = Modifier.padding(end = 2.dp)
                  )
                  VramTier.entries.forEach { tier ->
                    val isSelected = selectedVramTier == tier
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ClawTheme.colors.accent else ClawTheme.colors.surfaceRaised)
                        .clickable { selectedVramTier = tier }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = tier.label,
                        style = ClawTheme.type.caption.copy(
                          fontSize = 11.5.sp,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.text
                      )
                    }
                  }
                }
              }
            }
          }

          // --- インストール済みモデル一覧 ---
          item {
            ClawSectionHeader(title = "インストール済みモデル (${installedModels.size})")
          }

          if (installedModels.isEmpty()) {
            item {
              ClawPanel {
                Text(
                  text = if (isLoadingModels) "モデル一覧を取得中..." else "インストール済みモデルがありません。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
            }
          } else {
            items(installedModels) { model ->
              val isCurrent = model.name == activeModel || model.name.startsWith(activeModel) || activeModel.startsWith(model.name)
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = model.name,
                          style = ClawTheme.type.section.copy(fontSize = 14.5.sp),
                          color = ClawTheme.colors.text,
                        )
                        if (isCurrent) {
                          Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "使用中",
                            tint = ClawTheme.colors.primary,
                            modifier = Modifier.padding(start = 6.dp),
                          )
                        }
                      }

                      Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        if (model.sizeGb.isNotBlank()) {
                          Text(
                            text = "サイズ: ${model.sizeGb}",
                            style = ClawTheme.type.caption,
                            color = if (isCurrent) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                          )
                        }
                        Box(
                          modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClawTheme.colors.surfaceRaised)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                          Text(
                            text = model.executionHint,
                            style = ClawTheme.type.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                            color = ClawTheme.colors.accent,
                          )
                        }
                      }
                    }

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      if (isCurrent) {
                        Text(
                          text = "使用中",
                          style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                          color = ClawTheme.colors.primary,
                          modifier = Modifier.padding(horizontal = 6.dp),
                        )
                      } else {
                        ClawPrimaryButton(
                          text = "切替",
                          onClick = { switchModel(model.name) },
                        )
                      }
                      IconButton(
                        onClick = { modelToDelete = model.name },
                        enabled = !isCurrent && !isDeletingModel,
                        modifier = Modifier.size(32.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Default.Delete,
                          contentDescription = "モデル削除",
                          tint = if (isCurrent) ClawTheme.colors.textMuted.copy(alpha = 0.3f) else ClawTheme.colors.danger,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }

                  // モデル説明文
                  if (model.description.isNotBlank()) {
                    Text(
                      text = model.description,
                      style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                      color = ClawTheme.colors.textMuted,
                    )
                  }

                  // タグチップ群
                  if (model.tags.isNotEmpty()) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      model.tags.forEach { tag ->
                        Box(
                          modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClawTheme.colors.surfaceRaised)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                          Text(
                            text = tag,
                            style = ClawTheme.type.caption.copy(fontSize = 10.sp),
                            color = ClawTheme.colors.textMuted,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          // --- おすすめモデル一覧表 / カタログ (該当件数表示) ---
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              ClawSectionHeader(title = "モデルカタログ一覧 (${filteredCatalog.size}件)")
              Text(
                text = if (viewMode == ViewMode.TABLE) "タップで詳細展開" else "全情報表示中",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted
              )
            }
          }

          if (filteredCatalog.isEmpty()) {
            item {
              ClawPanel {
                Text(
                  text = "条件に一致するモデルが見つかりませんでした。絞り込み条件やキーワードを変更してください。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
            }
          } else {
            // 一覧表（テーブル表示モード）
            if (viewMode == ViewMode.TABLE) {
              items(filteredCatalog) { item ->
                val isDownloaded = installedModels.any { it.name.contains(item.id) || item.id.contains(it.name) }
                val isCurrent = isDownloaded && (item.id == activeModel || installedModels.any { (it.name == activeModel || activeModel.startsWith(it.name)) && it.name.contains(item.id) })
                val prog = findProgress(item.id)
                val isDownloading = prog != null && !prog.done && prog.error == null
                val isExpanded = expandedModelId == item.id

                ClawPanel {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        expandedModelId = if (isExpanded) null else item.id
                      }
                  ) {
                    // メイン行（テーブル行）
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                          Text(
                            text = item.name,
                            style = ClawTheme.type.section.copy(fontSize = 14.sp),
                            color = ClawTheme.colors.text,
                          )
                          Box(
                            modifier = Modifier
                              .clip(RoundedCornerShape(4.dp))
                              .background(ClawTheme.colors.surfaceRaised)
                              .padding(horizontal = 5.dp, vertical = 2.dp)
                          ) {
                            Text(
                              text = item.params,
                              style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                              color = ClawTheme.colors.textMuted,
                            )
                          }
                        }

                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          modifier = Modifier.padding(top = 2.dp)
                        ) {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                              imageVector = Icons.Default.Star,
                              contentDescription = "評価",
                              tint = Color(0xFFFFB800),
                              modifier = Modifier.size(13.dp)
                            )
                            Text(
                              text = " ${item.rating} (${item.reviewCount})",
                              style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                              color = ClawTheme.colors.textMuted,
                            )
                          }
                          Text(
                            text = "• VRAM: ${item.vramRange}",
                            style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                            color = ClawTheme.colors.accent,
                          )
                        }
                      }

                      // アクションボタン
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isCurrent) {
                          Text(
                            text = "使用中",
                            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = ClawTheme.colors.primary,
                            modifier = Modifier.padding(end = 4.dp),
                          )
                        } else if (isDownloaded) {
                          ClawSecondaryButton(
                            text = "切り替え",
                            onClick = { switchModel(item.id) },
                          )
                        } else if (isDownloading) {
                          ClawSecondaryButton(
                            text = "DL中 ${prog.percent}%",
                            onClick = {},
                          )
                        } else {
                          ClawPrimaryButton(
                            text = "DL",
                            onClick = { pullModel(item.id) },
                          )
                        }

                        Icon(
                          imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                          contentDescription = "展開",
                          tint = ClawTheme.colors.textMuted,
                          modifier = Modifier.size(20.dp)
                        )
                      }
                    }

                    // ダウンロード中プログレスバー
                    if (prog != null && !prog.done && prog.error == null) {
                      Spacer(modifier = Modifier.height(6.dp))
                      LinearProgressIndicator(
                        progress = { (prog.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(6.dp)
                          .clip(RoundedCornerShape(3.dp)),
                        color = ClawTheme.colors.primary,
                        trackColor = ClawTheme.colors.surfaceRaised,
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = "${prog.status} (${prog.percent}%)",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.primary,
                        )
                        Text(
                          text = "${formatBytes(prog.completed)} / ${if (prog.total > 0) formatBytes(prog.total) else "--"}",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.textMuted,
                        )
                      }
                    }

                    // アコーディオン詳細情報
                    AnimatedVisibility(
                      visible = isExpanded,
                      enter = expandVertically() + fadeIn(),
                      exit = shrinkVertically() + fadeOut()
                    ) {
                      Column(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 10.dp)
                          .background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                          .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Text(
                          text = item.description,
                          style = ClawTheme.type.body.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                          color = ClawTheme.colors.text,
                        )
                        Row(
                          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                          horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                          item.highlights.forEach { h ->
                            Box(
                              modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                              Text(
                                text = "✓ $h",
                                style = ClawTheme.type.caption.copy(fontSize = 10.5.sp),
                                color = ClawTheme.colors.primary
                              )
                            }
                          }
                        }
                        Text(
                          text = "モデルID: ${item.id}",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.textMuted,
                        )
                      }
                    }
                  }
                }
              }
            } else {
              // 詳細カード表示モード
              items(filteredCatalog) { item ->
                val isDownloaded = installedModels.any { it.name.contains(item.id) || item.id.contains(it.name) }
                val isCurrent = isDownloaded && (item.id == activeModel || installedModels.any { (it.name == activeModel || activeModel.startsWith(it.name)) && it.name.contains(item.id) })
                val prog = findProgress(item.id)
                val isDownloading = prog != null && !prog.done && prog.error == null

                ClawPanel {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                          Text(
                            text = item.name,
                            style = ClawTheme.type.section.copy(fontSize = 15.sp),
                            color = ClawTheme.colors.text,
                          )
                          Box(
                            modifier = Modifier
                              .clip(RoundedCornerShape(4.dp))
                              .background(ClawTheme.colors.surfaceRaised)
                              .padding(horizontal = 5.dp, vertical = 2.dp)
                          ) {
                            Text(
                              text = item.params,
                              style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                              color = ClawTheme.colors.textMuted,
                            )
                          }
                        }
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          modifier = Modifier.padding(top = 2.dp)
                        ) {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                              imageVector = Icons.Default.Star,
                              contentDescription = "評価",
                              tint = Color(0xFFFFB800),
                              modifier = Modifier.size(13.dp)
                            )
                            Text(
                              text = " ${item.rating} (${item.reviewCount})",
                              style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                              color = ClawTheme.colors.textMuted,
                            )
                          }
                          Text(
                            text = "• VRAM目安: ${item.vramRange}",
                            style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                            color = ClawTheme.colors.accent,
                          )
                        }
                      }

                      if (isCurrent) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          ClawSecondaryButton(
                            text = "⚡ 最適化",
                            onClick = { optimizeModel(item.id, 5.0) },
                          )
                          Text(
                            text = "使用中",
                            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = ClawTheme.colors.primary,
                            modifier = Modifier.padding(start = 4.dp),
                          )
                        }
                      } else if (isDownloaded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          ClawSecondaryButton(
                            text = "⚡ 最適化",
                            onClick = { optimizeModel(item.id, 5.0) },
                          )
                          ClawSecondaryButton(
                            text = "切り替え",
                            onClick = { switchModel(item.id) },
                          )
                        }
                      } else if (isDownloading) {
                        ClawSecondaryButton(
                          text = "DL中 ${prog.percent}%",
                          onClick = {},
                        )
                      } else {
                        ClawPrimaryButton(
                          text = "ダウンロード",
                          onClick = { pullModel(item.id) },
                        )
                      }
                    }

                    Text(
                      text = item.description,
                      style = ClawTheme.type.body.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                      color = ClawTheme.colors.text,
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      item.highlights.forEach { h ->
                        Box(
                          modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                          Text(
                            text = "✓ $h",
                            style = ClawTheme.type.caption.copy(fontSize = 10.5.sp),
                            color = ClawTheme.colors.primary
                          )
                        }
                      }
                    }

                    Text(
                      text = item.id,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                    )

                    if (prog != null && !prog.done && prog.error == null) {
                      Spacer(modifier = Modifier.height(4.dp))
                      LinearProgressIndicator(
                        progress = { (prog.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(6.dp)
                          .clip(RoundedCornerShape(3.dp)),
                        color = ClawTheme.colors.primary,
                        trackColor = ClawTheme.colors.surfaceRaised,
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = "${prog.status} (${prog.percent}%)",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.primary,
                        )
                        Text(
                          text = "${formatBytes(prog.completed)} / ${if (prog.total > 0) formatBytes(prog.total) else "--"}",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.textMuted,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // ==========================================
        // TAB 2: Hugging Face ライブ検索
        // ==========================================
        if (selectedTab == CatalogTab.HF_SEARCH) {
          item {
            ClawPanel {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  text = "🌐 Hugging Face ライブGGUF検索",
                  style = ClawTheme.type.section,
                  color = ClawTheme.colors.text,
                )
                Text(
                  text = "ジャンル・用途・日本語・制限解除を指定してHugging Faceから直接検索＆DLできます。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )

                // 検索キーワード
                ClawTextField(
                  value = hfSearchQuery,
                  onValueChange = { hfSearchQuery = it },
                  placeholder = "キーワード (例: llama-3, qwen, elyza, kunoichi)...",
                  modifier = Modifier.fillMaxWidth()
                )

                // ジャンル別・用途別チップ
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  listOf(
                    "all" to "すべて",
                    "japanese" to "🇯🇵 日本語モデル",
                    "uncensored" to "🔓 制限解除 (Uncensored)",
                    "roleplay" to "🎭 ロールプレイ・RP",
                    "reasoning" to "🧠 思考・推論 (o1/R1)",
                    "coding" to "💻 コーディング"
                  ).forEach { (key, label) ->
                    val isSelected = hfCategory == key
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised)
                        .clickable { hfCategory = key }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = label,
                        style = ClawTheme.type.caption.copy(fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.text
                      )
                    }
                  }
                }

                // ソート順チップ
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(text = "並び替え:", style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.textMuted)
                  listOf(
                    "downloads" to "🔥 ダウンロード数順",
                    "likes" to "❤️ いいね順",
                    "lastModified" to "🕒 新着順"
                  ).forEach { (sortKey, label) ->
                    val isSelected = hfSort == sortKey
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ClawTheme.colors.accent else ClawTheme.colors.surfaceRaised)
                        .clickable { hfSort = sortKey }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = label,
                        style = ClawTheme.type.caption.copy(fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.text
                      )
                    }
                  }
                }

                ClawPrimaryButton(
                  text = if (isHfSearching) "Hugging Faceを検索中..." else "🔍 ライブ検索実行",
                  onClick = { searchHuggingFace() },
                  enabled = !isHfSearching,
                  modifier = Modifier.align(Alignment.End)
                )
              }
            }
          }

          // 検索結果一覧
          if (hfSearchResults.isNotEmpty()) {
            item {
              ClawSectionHeader(title = "Hugging Face 検索結果 (${hfSearchResults.size}件)")
            }
            items(hfSearchResults) { hfModel ->
              val isDownloaded = installedModels.any { it.name.contains(hfModel.id) || hfModel.id.contains(it.name) }
              val prog = findProgress(hfModel.pullId)
              val isDownloading = prog != null && !prog.done && prog.error == null

              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                      Text(
                        text = hfModel.id,
                        style = ClawTheme.type.section.copy(fontSize = 13.5.sp),
                        color = ClawTheme.colors.text
                      )
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                      ) {
                        Text(text = "❤️ ${hfModel.likes}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                        Text(text = "📥 ${hfModel.downloads} DL", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                        if (hfModel.pipelineTag != null) {
                          Text(text = "• ${hfModel.pipelineTag}", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                        }
                      }
                    }

                    if (isDownloaded) {
                      ClawSecondaryButton(
                        text = "切替",
                        onClick = { switchModel(hfModel.pullId) }
                      )
                    } else if (isDownloading) {
                      ClawSecondaryButton(
                        text = "DL中 ${prog.percent}%",
                        onClick = {}
                      )
                    } else {
                      ClawPrimaryButton(
                        text = "DL",
                        onClick = { pullModel(hfModel.pullId) }
                      )
                    }
                  }

                  // タグチップ
                  if (hfModel.tags.isNotEmpty()) {
                    Row(
                      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      hfModel.tags.take(6).forEach { t ->
                        Box(
                          modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClawTheme.colors.surfaceRaised)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                          Text(text = t, style = ClawTheme.type.caption.copy(fontSize = 10.sp), color = ClawTheme.colors.textMuted)
                        }
                      }
                    }
                  }

                  // プログレスバー
                  if (prog != null && !prog.done && prog.error == null) {
                    LinearProgressIndicator(
                      progress = { (prog.percent / 100f).coerceIn(0f, 1f) },
                      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                      color = ClawTheme.colors.primary,
                      trackColor = ClawTheme.colors.surfaceRaised
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(text = "${prog.status} (${prog.percent}%)", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.primary)
                      Text(text = "${formatBytes(prog.completed)} / ${if (prog.total > 0) formatBytes(prog.total) else "--"}", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
                    }
                  }
                }
              }
            }
          }
        }

        // ==========================================
        // TAB 3: GGUF インポート / スキャン / アップロード
        // ==========================================
        if (selectedTab == CatalogTab.GGUF_IMPORT) {
          item {
            ClawPanel {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  text = "📁 PC内GGUFファイル自動スキャン",
                  style = ClawTheme.type.section,
                  color = ClawTheme.colors.text,
                )
                Text(
                  text = "PCの「ダウンロード」や「デスクトップ」等にある.ggufファイルを自動検出し、Ollamaモデルとしてワンクリック登録します。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
                ClawPrimaryButton(
                  text = if (isScanningGguf) "スキャン中..." else "🔍 PC内のGGUFファイルを検出",
                  onClick = { scanGgufFiles() },
                  enabled = !isScanningGguf,
                  modifier = Modifier.align(Alignment.End)
                )
              }
            }
          }

          // 検出されたGGUFファイルリスト
          if (scannedGgufFiles.isNotEmpty()) {
            item {
              ClawSectionHeader(title = "検出されたGGUFファイル (${scannedGgufFiles.size}件)")
            }
            items(scannedGgufFiles) { f ->
              var customName by remember { mutableStateOf(f.name.replace(".gguf", "", ignoreCase = true).lowercase().replace("[^a-z0-9_.-]".toRegex(), "-")) }
              val prog = findProgress(customName)
              val isImporting = prog != null && !prog.done && prog.error == null

              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                      Text(text = f.name, style = ClawTheme.type.section.copy(fontSize = 14.sp), color = ClawTheme.colors.text)
                      Text(text = "サイズ: ${f.sizeGb}", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                      Text(text = f.path, style = ClawTheme.type.caption.copy(fontSize = 10.5.sp), color = ClawTheme.colors.textMuted)
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Box(modifier = Modifier.weight(1f)) {
                      ClawTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        placeholder = "登録するモデル名",
                        modifier = Modifier.fillMaxWidth()
                      )
                    }
                    ClawPrimaryButton(
                      text = if (isImporting) "インポート中 (${prog.percent}%)" else "インポート",
                      onClick = { importGguf(customName, f.path) },
                      enabled = customName.isNotBlank() && !isImporting
                    )
                  }

                  if (prog != null && !prog.done && prog.error == null) {
                    LinearProgressIndicator(
                      progress = { (prog.percent / 100f).coerceIn(0f, 1f) },
                      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                      color = ClawTheme.colors.primary,
                      trackColor = ClawTheme.colors.surfaceRaised
                    )
                    Text(text = "${prog.status} (${prog.percent}%)", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                  }
                }
              }
            }
          }

          // 手動パス入力インポート
          item {
            ClawPanel {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  text = "📝 パス直接入力でGGUFインポート",
                  style = ClawTheme.type.section,
                  color = ClawTheme.colors.text,
                )
                Text(
                  text = "PC上の任意の場所に保存されている.ggufファイルの絶対パスとモデル名を指定してインポートします。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )

                ClawTextField(
                  value = manualGgufPath,
                  onValueChange = { manualGgufPath = it },
                  placeholder = "例: C:\\AI\\models\\my-character.gguf",
                  modifier = Modifier.fillMaxWidth()
                )

                ClawTextField(
                  value = manualGgufModelName,
                  onValueChange = { manualGgufModelName = it },
                  placeholder = "登録するモデル名 (例: my-character)",
                  modifier = Modifier.fillMaxWidth()
                )

                ClawPrimaryButton(
                  text = if (isImportingGguf) "インポート中..." else "GGUFからモデルを作成",
                  onClick = {
                    importGguf(manualGgufModelName, manualGgufPath)
                    manualGgufPath = ""
                    manualGgufModelName = ""
                  },
                  enabled = manualGgufPath.isNotBlank() && manualGgufModelName.isNotBlank() && !isImportingGguf,
                  modifier = Modifier.align(Alignment.End)
                )
              }
            }
          }
        }
      }
    }

    if (modelToDelete != null) {
      val target = modelToDelete!!
      AlertDialog(
        onDismissRequest = { if (!isDeletingModel) modelToDelete = null },
        title = {
          Text("モデルの削除確認", style = ClawTheme.type.title, color = ClawTheme.colors.text)
        },
        text = {
          Text(
            "モデル「$target」をPCストレージから完全に削除しますか？\nこの操作は取り消せません。",
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
        },
        confirmButton = {
          ClawPrimaryButton(
            text = if (isDeletingModel) "削除中..." else "削除する",
            onClick = { deleteModel(target) },
            enabled = !isDeletingModel,
          )
        },
        dismissButton = {
          ClawSecondaryButton(
            text = "キャンセル",
            onClick = { modelToDelete = null },
            enabled = !isDeletingModel,
          )
        },
      )
    }
  }
}

