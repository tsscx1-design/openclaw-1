package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/** 禁止設定ファイル（Gateway の dist/blocked-terms.json）の中の、語句の一覧1つ分。 */
internal data class BlockedTermsSection(
  val label: String,
  val note: String,
  /** 設定の中のオブジェクトへの道筋（例: negativePromptTerms.imageNegative）。 */
  val path: String,
  /** 語句の一覧が入っている項目名（terms / patterns / ja / enPatterns / forbiddenTags）。 */
  val field: String,
  /** このオブジェクトに enabled のスイッチがあるか。 */
  val hasSwitch: Boolean = true,
)

/** characterRules の入り切り。 */
internal data class BlockedTermsRule(
  val label: String,
  val note: String,
  val path: String,
)

internal val BLOCKED_TERMS_SECTIONS =
  listOf(
    BlockedTermsSection(
      label = "日本語の禁止語句",
      note = "依頼にこの語句が1つでも含まれると、生成を断ります。",
      path = "blockedTermsJa",
      field = "terms",
    ),
    BlockedTermsSection(
      label = "英語の禁止パターン",
      note = "正規表現です。依頼に当てはまると、生成を断ります。大文字小文字は区別しません。",
      path = "blockedTermsEnPatterns",
      field = "patterns",
    ),
    BlockedTermsSection(
      label = "身体変更の禁止語句（日本語）",
      note = "身長や年齢などを変える言い方。含まれると、生成を断ります。",
      path = "blockedBodyModifications",
      field = "ja",
    ),
    BlockedTermsSection(
      label = "身体変更の禁止パターン（英語）",
      note = "正規表現です。上の英語版です。スイッチは日本語と共通です。",
      path = "blockedBodyModifications",
      field = "enPatterns",
    ),
    BlockedTermsSection(
      label = "性的な語句（成人向けの判定）",
      note = "禁止ではありません。依頼にこの語句があると「成人向けの依頼」とみなして、成人向けのネガティブタグと画質タグを付けます。",
      path = "sexualTerms",
      field = "terms",
    ),
    BlockedTermsSection(
      label = "性的なパターン（成人向けの判定・正規表現）",
      note = "上の正規表現版です。スイッチは上と共通です。",
      path = "sexualTerms",
      field = "patterns",
    ),
    BlockedTermsSection(
      label = "ネガティブタグ（成人向け）",
      note = "性的な依頼の絵や動画に、描かないでほしいものとして足されます。",
      path = "negativePromptTerms.adultOnly",
      field = "terms",
    ),
    BlockedTermsSection(
      label = "ネガティブタグ（画像）",
      note = "モーションコミックの絵に、いつも足されます。",
      path = "negativePromptTerms.imageNegative",
      field = "terms",
    ),
    BlockedTermsSection(
      label = "ネガティブタグ（動画）",
      note = "モーションコミックの動画に、いつも足されます。",
      path = "negativePromptTerms.videoNegative",
      field = "terms",
    ),
    BlockedTermsSection(
      label = "使ってはいけない身長タグ",
      note = "キャラ作成で、AI に使わせないタグです。",
      path = "heightRules",
      field = "forbiddenTags",
      hasSwitch = false,
    ),
  )

internal val BLOCKED_TERMS_RULES =
  listOf(
    BlockedTermsRule("登場人物は成人にする", "脚本とキャラ作成の指示に「成人」と入れます。", "characterRules.adultsOnly"),
    BlockedTermsRule("子どもっぽいタグを使わせない", "キャラ作成で、子どもっぽい体型や年齢のタグを禁じます。", "characterRules.forbidChildlikeTags"),
    BlockedTermsRule("低身長でも petite を使わせない", "キャラ作成で、小柄でも大人の体型を明示させます。", "characterRules.forbidPetite"),
  )

/** "a.b.c" の道筋で、入れ子のオブジェクトを取り出す。 */
internal fun blockedTermsObjectAt(root: JSONObject?, path: String): JSONObject? {
  var node = root
  for (segment in path.split(".")) node = node?.optJSONObject(segment)
  return node
}

internal fun blockedTermsListOf(root: JSONObject?, section: BlockedTermsSection): List<String> {
  val array = blockedTermsObjectAt(root, section.path)?.optJSONArray(section.field) ?: return emptyList()
  return (0 until array.length()).map { array.optString(it) }
}

/** enabled が無い、または false でなければ有効（Gateway の読み方と同じ）。 */
internal fun blockedTermsEnabled(root: JSONObject?, path: String): Boolean =
  blockedTermsObjectAt(root, path)?.optBoolean("enabled", true) ?: true

/** 禁止する年齢の範囲（blockedAges）の from / to の表示（無ければ空）。 */
internal fun blockedTermsAgeText(root: JSONObject?, key: String): String {
  val value = blockedTermsObjectAt(root, "blockedAges")?.opt(key) as? Number ?: return ""
  return if (value.toDouble() % 1.0 == 0.0 && Math.abs(value.toDouble()) < 1e15) value.toLong().toString() else value.toString()
}

/** 年齢の判定がかかっているか。blockedAges があり、enabled が false でないとき。 */
internal fun blockedTermsAgesOn(root: JSONObject?): Boolean =
  blockedTermsObjectAt(root, "blockedAges")?.optBoolean("enabled", true) ?: false

/** 入力を数値にする。形（数であること）だけを見て、大きさや符号の制限はしない。 */
internal fun blockedTermsNumber(text: String): Number? {
  val trimmed = text.trim()
  trimmed.toLongOrNull()?.let { return it }
  return trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
}

@Composable
internal fun BlockedTermsAdminScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isConnected by viewModel.isConnected.collectAsState()

  var selected by remember { mutableStateOf(BLOCKED_TERMS_SECTIONS.first()) }
  var config by remember { mutableStateOf<JSONObject?>(null) }
  var filePath by remember { mutableStateOf("") }
  var fileExists by remember { mutableStateOf(true) }
  var loadError by remember { mutableStateOf("") }
  var newTerm by remember { mutableStateOf("") }
  var status by remember { mutableStateOf("") }
  var pendingBackupJson by remember { mutableStateOf<String?>(null) }
  var ageFrom by remember { mutableStateOf("") }
  var ageTo by remember { mutableStateOf("") }
  var problems by remember { mutableStateOf<List<String>>(emptyList()) }

  suspend fun load() {
    try {
      val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.BlockedTermsGet, "{}"))
      config = result.optJSONObject("config")
      filePath = result.optString("file", "")
      fileExists = result.optBoolean("exists", true)
      loadError = ""
      ageFrom = blockedTermsAgeText(config, "from")
      ageTo = blockedTermsAgeText(config, "to")
      val list = result.optJSONArray("problems")
      problems = if (list == null) emptyList() else (0 until list.length()).map { list.optString(it) }
    } catch (e: Exception) {
      config = null
      loadError = e.message ?: "読み込めませんでした"
    }
  }

  /** 書き換えの RPC を呼び、成功したら返ってきた設定で画面を更新する。 */
  fun edit(method: GatewayMethod, params: JSONObject, done: String) {
    scope.launch {
      try {
        val result = JSONObject(viewModel.runGatewayRpc(method, params.toString()))
        config = result.optJSONObject("config") ?: config
        ageFrom = blockedTermsAgeText(config, "from")
        ageTo = blockedTermsAgeText(config, "to")
        status = done
      } catch (e: Exception) {
        status = "エラー: ${e.message}"
      }
    }
  }

  val saveFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      val json = pendingBackupJson
      if (uri != null && json != null) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out -> out.write(json.toByteArray(Charsets.UTF_8)) }
          status = "ファイルに保存しました"
        } catch (e: Exception) {
          status = "保存エラー: ${e.message}"
        }
      }
      pendingBackupJson = null
    }

  fun restore(text: String) {
    scope.launch {
      try {
        status = "復元中..."
        val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.BlockedTermsImport, JSONObject(mapOf("json" to text)).toString()))
        status = "復元しました"
        load()
      } catch (e: Exception) {
        status = "復元エラー: ${e.message}"
      }
    }
  }

  val openFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        val text =
          context.contentResolver.openInputStream(uri)?.use { ins -> BufferedReader(InputStreamReader(ins)).readText() } ?: ""
        restore(text)
      }
    }

  LaunchedEffect(isConnected) {
    if (isConnected) load()
  }

  SettingsDetailFrame(
    title = "禁止語句管理",
    subtitle = "Gateway の dist/blocked-terms.json を編集",
    icon = Icons.Default.Security,
    onBack = onBack,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Spacer(Modifier.height(8.dp))
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text =
                when {
                  !isConnected -> "Gateway に未接続です"
                  loadError.isNotEmpty() -> loadError
                  !fileExists -> "ファイルがありません（空として動作中・何も禁止しません）: $filePath"
                  else -> "読み込み済み: $filePath"
                },
              color = if (loadError.isNotEmpty()) ClawTheme.colors.danger else ClawTheme.colors.text,
              fontSize = 13.sp,
            )
            Text(
              text = "ファイルが無い・壊れているときも、止まらずに空として動きます（何も禁止しません）。壊れているときは、間違いをここと生成の返事で知らせます。編集はすぐ反映されます（再起動は不要です）。ファイルは「復元」で作れます。",
              color = ClawTheme.colors.textMuted,
              fontSize = 12.sp,
            )
          }
        }
      }

      if (problems.isNotEmpty()) {
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "⚠️ ファイルに間違いがあります（間違っている所は無視して動いています）",
                color = ClawTheme.colors.danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
              )
              problems.forEach { Text(text = it, color = ClawTheme.colors.danger, fontSize = 12.sp) }
            }
          }
        }
      }

      if (config != null) {
        item {
          ClawSectionHeader("ルール")
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BLOCKED_TERMS_RULES.forEach { rule ->
              ClawPanel {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(rule.label, color = ClawTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(rule.note, color = ClawTheme.colors.textMuted, fontSize = 12.sp)
                  }
                  Switch(
                    checked = blockedTermsEnabled(config, rule.path),
                    onCheckedChange = { value ->
                      edit(
                        GatewayMethod.BlockedTermsSetConfig,
                        JSONObject().put("section", rule.path).put("key", "enabled").put("value", value),
                        "「${rule.label}」を${if (value) "入" else "切"}にしました",
                      )
                    },
                  )
                }
              }
            }
          }
        }

        item {
          ClawSectionHeader("禁止する年齢")
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "ここで決めた範囲（両端を含む）の年齢が、依頼に書かれているとき（15歳・十五歳・15 years old など）と、キャラの設定の年齢にあるとき、生成を断ります。入力できる数値に制限はありません。",
                color = ClawTheme.colors.textMuted,
                fontSize = 12.sp,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("年齢で断る", color = ClawTheme.colors.text, fontSize = 14.sp)
                Switch(
                  checked = blockedTermsAgesOn(config),
                  onCheckedChange = { value ->
                    edit(
                      GatewayMethod.BlockedTermsSetConfig,
                      JSONObject().put("section", "blockedAges").put("key", "enabled").put("value", value).put("create", true),
                      "年齢の判定を${if (value) "入" else "切"}にしました",
                    )
                  },
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                ClawTextField(
                  value = ageFrom,
                  onValueChange = { ageFrom = it },
                  placeholder = "から（例: 1）",
                  modifier = Modifier.weight(1f),
                )
                ClawTextField(
                  value = ageTo,
                  onValueChange = { ageTo = it },
                  placeholder = "まで（例: 20）",
                  modifier = Modifier.weight(1f),
                )
              }
              ClawSecondaryButton(
                text = "範囲を保存",
                icon = Icons.Default.Save,
                onClick = {
                  val from = blockedTermsNumber(ageFrom)
                  val to = blockedTermsNumber(ageTo)
                  if (from == null || to == null) {
                    status = "エラー: 数値で入力してください"
                  } else {
                    scope.launch {
                      try {
                        for ((key, number) in listOf("from" to from, "to" to to)) {
                          val result =
                            JSONObject(
                              viewModel.runGatewayRpc(
                                GatewayMethod.BlockedTermsSetConfig,
                                JSONObject().put("section", "blockedAges").put("key", key).put("value", number).put("create", true).toString(),
                              ),
                            )
                          config = result.optJSONObject("config") ?: config
                        }
                        status = "禁止する年齢の範囲を保存しました"
                      } catch (e: Exception) {
                        status = "エラー: ${e.message}"
                      }
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        item {
          ClawSectionHeader("語句の一覧")
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BLOCKED_TERMS_SECTIONS.forEach { section ->
              val label = "${section.label}（${blockedTermsListOf(config, section).size}）"
              if (section == selected) {
                ClawPrimaryButton(text = label, onClick = { selected = section }, modifier = Modifier.fillMaxWidth())
              } else {
                ClawSecondaryButton(text = label, onClick = { selected = section }, modifier = Modifier.fillMaxWidth())
              }
            }
          }
        }

        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(selected.label, color = ClawTheme.colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
              Text(selected.note, color = ClawTheme.colors.textMuted, fontSize = 12.sp)
              if (selected.hasSwitch) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("この一覧を使う", color = ClawTheme.colors.text, fontSize = 14.sp)
                  Switch(
                    checked = blockedTermsEnabled(config, selected.path),
                    onCheckedChange = { value ->
                      edit(
                        GatewayMethod.BlockedTermsSetConfig,
                        JSONObject().put("section", selected.path).put("key", "enabled").put("value", value),
                        "「${selected.label}」を${if (value) "使う" else "使わない"}にしました",
                      )
                    },
                  )
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                ClawTextField(
                  value = newTerm,
                  onValueChange = { newTerm = it },
                  placeholder = "追加する語句",
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = {
                    val term = newTerm.trim()
                    if (term.isEmpty()) return@IconButton
                    edit(
                      GatewayMethod.BlockedTermsAddTerm,
                      JSONObject().put("section", selected.path).put("field", selected.field).put("term", term),
                      "追加しました",
                    )
                    newTerm = ""
                  },
                ) {
                  Icon(Icons.Default.Add, contentDescription = "追加", tint = ClawTheme.colors.primary)
                }
              }
            }
          }
        }

        val terms = blockedTermsListOf(config, selected)
        if (terms.isEmpty()) {
          item {
            ClawPanel {
              Text("語句がありません", color = ClawTheme.colors.textMuted, fontSize = 14.sp)
            }
          }
        } else {
          items(terms, key = { "${selected.path}.${selected.field}:$it" }) { term ->
            ClawPanel {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = term, color = ClawTheme.colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(
                  onClick = {
                    edit(
                      GatewayMethod.BlockedTermsRemoveTerm,
                      JSONObject().put("section", selected.path).put("field", selected.field).put("term", term),
                      "削除しました",
                    )
                  },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "削除", tint = ClawTheme.colors.danger)
                }
              }
            }
          }
        }
      }

      item {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = ClawTheme.colors.border)
        Spacer(Modifier.height(8.dp))
        ClawSectionHeader("バックアップ / 復元")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawSecondaryButton(
            text = "ファイルに保存（バックアップ）",
            icon = Icons.Default.Save,
            onClick = {
              scope.launch {
                try {
                  val json = JSONObject(viewModel.runGatewayRpc(GatewayMethod.BlockedTermsExport, "{}")).optString("json", "")
                  if (json.isBlank()) {
                    status = "バックアップの中身が空です"
                    return@launch
                  }
                  pendingBackupJson = json
                  saveFileLauncher.launch("blocked-terms_backup_${System.currentTimeMillis()}.json")
                } catch (e: Exception) {
                  status = "バックアップエラー: ${e.message}"
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
          ClawSecondaryButton(
            text = "クリップボードにコピー（バックアップ）",
            onClick = {
              scope.launch {
                try {
                  val json = JSONObject(viewModel.runGatewayRpc(GatewayMethod.BlockedTermsExport, "{}")).optString("json", "")
                  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cm.setPrimaryClip(ClipData.newPlainText("blocked-terms", json))
                  status = "クリップボードにコピーしました"
                  Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                  status = "コピーエラー: ${e.message}"
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
          ClawPrimaryButton(
            text = "ファイルから復元",
            icon = Icons.Default.FileOpen,
            onClick = { openFileLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth(),
          )
          ClawSecondaryButton(
            text = "クリップボードから復元",
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
              if (text.isBlank()) status = "クリップボードが空です" else restore(text)
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      if (status.isNotBlank()) {
        item {
          ClawPanel {
            Text(
              text = status,
              color = if (status.contains("エラー")) ClawTheme.colors.danger else ClawTheme.colors.text,
              fontSize = 13.sp,
            )
          }
        }
      }

      item { Spacer(Modifier.height(32.dp)) }
    }
  }
}
