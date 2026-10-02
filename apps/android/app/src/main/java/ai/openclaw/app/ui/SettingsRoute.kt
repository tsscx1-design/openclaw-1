package ai.openclaw.app.ui

import ai.openclaw.app.i18n.NativeText
import ai.openclaw.app.i18n.nativeText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class SettingsCategory(
  val title: NativeText,
  /** true = デフォルトで展開表示 / false = 折りたたみ表示 */
  val defaultExpanded: Boolean,
) {
  // カテゴリ表示順序（enum順 = 画面表示順）。名前だけで中身が分かる7グループに整理。
  Partner(nativeText("🤖 パートナー"), defaultExpanded = true),
  Fun(nativeText("🎮 たのしむ"), defaultExpanded = true),
  AiModels(nativeText("🧠 AI・モデル"), defaultExpanded = false),
  Automation(nativeText("⚙️ 自動化・ツール"), defaultExpanded = false),
  Connection(nativeText("🔗 接続"), defaultExpanded = false),
  Device(nativeText("📱 この端末"), defaultExpanded = false),
  Info(nativeText("🩺 情報・診断"), defaultExpanded = false),
}

internal enum class SettingsRoute(
  val title: NativeText,
  val icon: ImageVector,
  val category: SettingsCategory?,
) {
  // ── ルーティング専用（カテゴリなし） ──
  Home(nativeText("Settings"), Icons.Outlined.Settings, null),

  // ── 🤖 AIパートナー ──
  PartnerManagement(nativeText("パートナー管理"), Icons.Default.Person, SettingsCategory.Partner),
  PartnerCharacterSheet(nativeText("キャラクターシート"), Icons.Default.Face, SettingsCategory.Partner),
  CoreAgentSettings(nativeText("人格・口調・SOUL設定"), Icons.Default.Psychology, SettingsCategory.Partner),
  GroupChat(nativeText("グループチャット"), Icons.Default.Groups, SettingsCategory.Partner),
  AvatarCustomize(nativeText("アバターカスタマイズ"), Icons.Default.Palette, SettingsCategory.Partner),
  VoiceStudio(nativeText("声を作る"), Icons.Default.Mic, SettingsCategory.Partner),
  PartnerBond(nativeText("絆レベル & 実績"), Icons.Default.EmojiEvents, SettingsCategory.Fun),
  PartnerChat(nativeText("Partner Chat"), Icons.Default.Person, SettingsCategory.Partner),

  // ── 🧠 知識・記憶 ──
  MemoryInspector(nativeText("記憶 & RAG"), Icons.Default.Psychology, SettingsCategory.Partner),
  PartnerMemory(nativeText("AIメモリー管理"), Icons.Default.Storage, SettingsCategory.Partner),
  FeatureSettings(nativeText("Web検索 & 学習"), Icons.Default.Search, SettingsCategory.AiModels),
  PartnerDream(nativeText("AIの夢日記"), Icons.Default.Nightlight, SettingsCategory.Fun),
  MoodDashboard(nativeText("感情ダッシュボード"), Icons.Default.Favorite, SettingsCategory.Fun),

  // ── 🎮 生活・エンタメ ──
  DailySettings(nativeText("朝の挨拶 & 占い"), Icons.Default.WbSunny, SettingsCategory.Fun),
  Diary(nativeText("日記アシスタント"), Icons.AutoMirrored.Filled.MenuBook, SettingsCategory.Fun),
  MiniGame(nativeText("ミニゲーム"), Icons.Default.SportsEsports, SettingsCategory.Fun),

  // ── ⚙️ モデル・自動化 ──
  OllamaCatalog(nativeText("モデルカタログ"), Icons.Outlined.Inventory2, SettingsCategory.AiModels),
  OnDeviceGguf(nativeText("端末内GGUF & オフラインAI"), Icons.Default.Storage, SettingsCategory.AiModels),
  Agents(nativeText("Agents"), Icons.Default.Person, SettingsCategory.AiModels),
  ProvidersModels(nativeText("Providers & Models"), Icons.Outlined.Inventory2, SettingsCategory.AiModels),
  Approvals(nativeText("Approvals"), Icons.Default.Lock, SettingsCategory.Automation),
  CronJobs(nativeText("Automations"), Icons.Outlined.AccessTime, SettingsCategory.Automation),
  Usage(nativeText("Usage"), Icons.Default.Storage, SettingsCategory.Automation),
  SpecialistJobs(nativeText("裏作業員"), Icons.Default.Engineering, SettingsCategory.Automation),
  Skills(nativeText("Skills"), Icons.Default.Extension, SettingsCategory.AiModels),
  SkillWorkshop(nativeText("Skill Workshop"), Icons.Default.AutoAwesome, SettingsCategory.AiModels),
  SystemAgent(nativeText("OpenClaw"), Icons.Default.Bolt, SettingsCategory.AiModels),
  Dreaming(nativeText("Dreaming"), Icons.Default.Nightlight, SettingsCategory.Automation),
  Terminal(nativeText("Terminal"), Icons.Outlined.Terminal, SettingsCategory.Automation),
  Desktop(nativeText("Desktop"), Icons.Outlined.DesktopWindows, SettingsCategory.Automation),

  // ── 🔗 接続・サーバー ──
  Gateway(nativeText("Gateway"), Icons.Default.Cloud, SettingsCategory.Connection),
  GatewayConfig(nativeText("Gateway Config"), Icons.Default.Settings, SettingsCategory.Connection),
  NodesDevices(nativeText("Nodes & Devices"), Icons.Default.Cloud, SettingsCategory.Connection),
  Channels(nativeText("Channels"), Icons.Default.Notifications, SettingsCategory.Connection),
  BackupRestore(nativeText("バックアップ & 復元"), Icons.Default.Storage, SettingsCategory.Connection),

  // ── 📱 端末・プライバシー ──
  Voice(nativeText("Voice"), Icons.Default.Mic, SettingsCategory.Device),
  Notifications(nativeText("Notifications"), Icons.Default.Notifications, SettingsCategory.Device),
  PhoneCapabilities(nativeText("Phone Capabilities"), Icons.Default.Lock, SettingsCategory.Device),

  // ── 🎨 プロフィール・外観 ──
  Profile(nativeText("Profile"), Icons.Default.Person, SettingsCategory.Device),
  Appearance(nativeText("Appearance"), Icons.Default.Palette, SettingsCategory.Device),
  About(nativeText("About"), Icons.Default.Storage, SettingsCategory.Info),
  Licenses(nativeText("Licenses"), Icons.Default.Storage, SettingsCategory.Info),

  // ── 🔧 診断 ──
  PcHealth(nativeText("PCの状態"), Icons.Default.Thermostat, SettingsCategory.Info),
  Health(nativeText("Health"), Icons.Default.Settings, SettingsCategory.Info),
  ;

  fun isAvailable(desktopObserveAvailable: Boolean): Boolean = this != Desktop || desktopObserveAvailable
}

