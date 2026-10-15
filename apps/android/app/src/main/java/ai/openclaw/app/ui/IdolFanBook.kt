package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * マスターデータの「ファンブック」画面。分類（48グループ…）→ グループ → 名前（写真つきの一覧）→ 1人ずつのページ、の順に
 * 全画面で見ながら、キャラ図鑑に作る人を選ぶ。作る操作は、このファンブックの中にある。
 * 長押し：グループのカード＝分類を変える／写真のない人の写真の場所＝スマホの写真を入れる。
 */

/** グループの全メンバーを、ゲートウェイから読む。 */
internal suspend fun fetchIdolMembers(viewModel: MainViewModel, group: String): List<IdolMemberRow> {
  val array = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterMembers, JSONObject().put("group", group).toString(), timeoutMs = 60_000)).getJSONArray("members")
  return (0 until array.length()).map { array.getJSONObject(it) }.map { row ->
    val tags = row.optJSONArray("tags")
    IdolMemberRow(
      id = row.optInt("id"),
      name = row.optString("name"),
      tags = (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) },
      description = row.optString("description"),
      hasPhoto = row.optBoolean("photo"),
      photoV = row.optLong("photoV"),
      generation = row.optString("generation"),
      nickname = row.optString("nickname"),
      age = if (row.has("age")) row.optInt("age") else null,
      birthday = row.optString("birthday"),
      hometown = row.optString("hometown"),
      bloodType = row.optString("bloodType"),
      heightCm = if (row.has("heightCm")) row.optDouble("heightCm") else null,
      agency = row.optString("agency"),
      bio = row.optString("bio"),
      xUrl = row.optString("xUrl"),
      source = row.optString("source"),
      myTags = row.optJSONArray("myTags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
      hasMemo = row.optBoolean("hasMemo"),
    )
  }
}

/** 縦長（3:4）の顔写真。写真がなければ、名前の頭文字と「長押しで写真を入れる」。長押しを渡すと、そこで反応する。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IdolPhotoBox(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  modifier: Modifier,
  maxSide: Int,
  photoVersion: Int = 0,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
) {
  var bitmap by remember(group, member.id, photoVersion, member.photoV) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(group, member.id, member.hasPhoto, photoVersion, member.photoV) { if (member.hasPhoto) bitmap = IdolPhotos.load(viewModel, group, member.id, maxSide, photoVersion, member.photoV) }
  val tap = if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier
  Box(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).then(tap),
    contentAlignment = Alignment.Center,
  ) {
    val shown = bitmap
    if (shown != null) {
      Image(bitmap = shown.asImageBitmap(), contentDescription = member.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(member.name.take(1), style = ClawTheme.type.title, color = ClawTheme.colors.textMuted)
        if (onLongClick != null) Text("長押しで\n写真を入れる", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
    }
  }
}

/** 分類の中：中の分類（📁）とグループのカードが並ぶ。押すと開く。長押しで、並び替え・分類を変える・削除。最後に、グループを足すカード。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FanBookCategoryView(
  category: IdolCategoryRow,
  subCategories: List<IdolCategoryRow>,
  columns: Int,
  onColumns: (Int) -> Unit,
  onOpenGroup: (String) -> Unit,
  onGroupMenu: (String) -> Unit,
  onOpenCategory: (String) -> Unit,
  onCategoryMenu: (String) -> Unit,
  onAddGroup: () -> Unit,
  onCatalog: () -> Unit = {},
  terms: FanBookTerms = FanBookTerms("グループ", "メンバー"),
  onMoveGroup: (String, Int) -> Unit = { _, _ -> },
  onMoveCategory: (String, Int) -> Unit = { _, _ -> },
  onMoveGroupTo: (String, Int) -> Unit = { _, _ -> },
  showSettings: Boolean = false,
) {
  var reorder by remember { mutableStateOf(false) }
  val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
  var dragging by remember { mutableStateOf<String?>(null) }
  var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      FanBookSmallButton(text = if (category.name in ANIME_CATEGORIES) "🔎 作品から追加" else "🔎 DBから追加", onClick = onCatalog, primary = true)
      FanBookSmallButton(text = if (reorder) "✔ 並び替え中" else "↕ 並び替え", onClick = { reorder = !reorder })
    }
    if (showSettings) {
      FanBookColumnsBar(columns, onColumns)
      Text("押すと開きます。長押しすると、並び替え・分類を変える・削除ができます。ドラッグか ◀ ▶ で動かせます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
    LazyVerticalGrid(
      state = gridState,
      columns = fanBookCells(columns, 180.dp),
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(bottom = 32.dp),
    ) {
      items(subCategories, key = { "category:" + it.name }) { c ->
        FanBookTile(
          title = "📁 " + c.name,
          subtitle = "${c.groups.sumOf { it.count }}人・${c.groups.size}グループ",
          hint = "開く",
          onClick = { onOpenCategory(c.name) },
          onLongClick = { onCategoryMenu(c.name) },
          onBefore = if (reorder) ({ onMoveCategory(c.name, -1) }) else null,
          onAfter = if (reorder) ({ onMoveCategory(c.name, 1) }) else null,
        )
      }
      items(category.groups, key = { it.group }) { g ->
        FanBookTile(
          title = g.group,
          subtitle = if (g.count == 0) "まだ0人" else "${g.count}人",
          hint = "開く",
          onClick = { onOpenGroup(g.group) },
          onLongClick = { onGroupMenu(g.group) },
          modifier =
            Modifier
              .zIndex(if (dragging == g.group) 1f else 0f)
              .graphicsLayer {
                if (dragging == g.group) {
                  translationX = dragOffset.x
                  translationY = dragOffset.y
                  alpha = 0.85f
                }
              }
              .then(
                if (reorder) {
                  Modifier.pointerInput(g.group) {
                    detectDragGestures(
                      onDragStart = { dragging = g.group; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                      onDrag = { change, amount -> change.consume(); dragOffset += amount },
                      onDragEnd = {
                        val from = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == g.group }
                        if (from != null) {
                          val cx = from.offset.x + from.size.width / 2f + dragOffset.x
                          val cy = from.offset.y + from.size.height / 2f + dragOffset.y
                          val over =
                            gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                              it.key != g.group && cx >= it.offset.x && cx <= it.offset.x + it.size.width && cy >= it.offset.y && cy <= it.offset.y + it.size.height
                            }
                          val to = category.groups.indexOfFirst { it.group == over?.key }
                          if (to >= 0) onMoveGroupTo(g.group, to)
                        }
                        dragging = null
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                      },
                      onDragCancel = { dragging = null; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                    )
                  }
                } else {
                  Modifier
                },
              ),
          onBefore = if (reorder) ({ onMoveGroup(g.group, -1) }) else null,
          onAfter = if (reorder) ({ onMoveGroup(g.group, 1) }) else null,
        )
      }
      item(key = "add-group") {
        Column(
          modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.3f)).clickable { onAddGroup() }.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("＋", style = ClawTheme.type.title, color = ClawTheme.colors.primary)
          Text("${terms.group}を追加", style = ClawTheme.type.body, color = ClawTheme.colors.text)
        }
      }
    }
  }
}

/** グループの中：写真つきのメンバーの一覧（ファンブックの目次）。押すと1人のページ、右上の丸で、まとめて選べる。 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FanBookGroupView(
  viewModel: MainViewModel,
  group: String,
  members: List<IdolMemberRow>,
  loading: Boolean,
  query: String,
  onQuery: (String) -> Unit,
  selected: Set<Int>,
  onToggle: (Int) -> Unit,
  onOpen: (Int) -> Unit,
  onCreateSelected: () -> Unit,
  busy: Boolean,
  photoVersion: Int,
  onPickPhoto: (IdolMemberRow) -> Unit,
  onSearchImage: (IdolMemberRow) -> Unit = {},
  onPhotoFromUrl: (IdolMemberRow, Boolean) -> Unit = { _, _ -> },
  onPickMany: (IdolMemberRow, Boolean) -> Unit = { _, _ -> },
  onAddMember: () -> Unit,
  onFill: () -> Unit,
  onSource: () -> Unit,
  columns: Int = 0,
  onColumns: (Int) -> Unit = {},
  onCatalog: () -> Unit = {},
  onDeleteMembers: (List<IdolMemberRow>) -> Unit = {},
  official: IdolSourcePreset? = null,
  onOfficial: () -> Unit = {},
  terms: FanBookTerms = FanBookTerms("グループ", "メンバー"),
  showSettings: Boolean = false,
) {
  var confirmDelete by remember { mutableStateOf(false) }
  var customOpen by remember { mutableStateOf(false) }
  var photoCustomOf by remember { mutableStateOf<IdolMemberRow?>(null) }
  var tagFilter by remember { mutableStateOf("") }
  val allTags = members.flatMap { it.myTags }.distinct().sorted()
  val shown = members.filter { tagFilter.isBlank() || tagFilter in it.myTags }.filter {
    query.isBlank() || it.myTags.any { tag -> tag.contains(query.trim(), ignoreCase = true) } || it.name.contains(query.trim(), ignoreCase = true) || it.nickname.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true) || it.tags.any { tag -> tag.contains(query.trim(), ignoreCase = true) }
  }
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      FanBookSmallButton(text = "＋ ${terms.member}", onClick = onAddMember)
      FanBookSmallButton(text = "🔎 DB追加", onClick = onCatalog, primary = true)
      if (official != null && official.readable) FanBookSmallButton(text = "🌐 公式から", onClick = onOfficial, primary = true)
      FanBookSmallButton(text = "🔗 URL", onClick = onSource)
      FanBookSmallButton(text = "📥 補う", onClick = onFill)
      FanBookSmallButton(text = "⭐ 追加", onClick = { customOpen = true })
    }
    photoCustomOf?.let { FanBookPhotoCustomDialog(viewModel, group, it, photoVersion, onPickPhoto, onSearchImage, onPhotoFromUrl, onPickMany, onDismiss = { photoCustomOf = null }) }
    if (customOpen) FanBookCustomDialog(group = group, members = members, onDismiss = { customOpen = false })
    ClawTextField(value = query, onValueChange = onQuery, placeholder = "名前・愛称・タイプでさがす", modifier = Modifier.fillMaxWidth())
    if (tagFilter.isNotBlank()) {
      androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("絞り込み中：#$tagFilter", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        FanBookSmallButton(text = "解除", onClick = { tagFilter = "" })
      }
    }
    if (loading) Text("読み込み中です…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    if (showSettings) {
      FanBookColumnsBar(columns, onColumns)
      if (allTags.isNotEmpty()) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("自分のタグ：", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(top = 6.dp))
          FanBookSmallButton(text = if (tagFilter.isBlank()) "● すべて" else "すべて", onClick = { tagFilter = "" })
          allTags.forEach { tag -> FanBookSmallButton(text = if (tagFilter == tag) "● $tag" else tag, onClick = { tagFilter = if (tagFilter == tag) "" else tag }) }
        }
      }
      if (official != null && !official.readable) Text("公式ホームページ：${official.note}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      Text(
        "写真か名前を押すと、その人のページが開きます。右上の丸で、まとめて選べます。写真のない人は、写真の場所を長押しすると、スマホの写真を入れられます。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textMuted,
      )
    }
    Box(modifier = Modifier.weight(1f)) {
      LazyVerticalGrid(
        columns = fanBookCells(columns, 130.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        items(shown, key = { it.id }) { member ->
          val index = members.indexOfFirst { it.id == member.id }
          val isSelected = member.id in selected
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
              IdolPhotoBox(
                viewModel,
                group,
                member,
                Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                maxSide = 420,
                photoVersion = photoVersion,
                onClick = { onOpen(index) },
                onLongClick = if (member.hasPhoto) null else ({ onPickPhoto(member) }),
              )
              Box(
                modifier =
                  Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised.copy(alpha = 0.85f))
                    .clickable { onToggle(member.id) },
                contentAlignment = Alignment.Center,
              ) {
                Text(if (isSelected) "✓" else "", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              }
            }
            Column(modifier = Modifier.clickable { onOpen(index) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(member.name + if (member.hasMemo) " 📝" else "", style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1)
              if (member.myTags.isNotEmpty()) Text(member.myTags.joinToString(" ") { "#$it" }, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, maxLines = 1)
              member.summary().takeIf { it.isNotBlank() }?.let { Text(it, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 2) }
            }
            FanBookSmallButton(text = "⭐ 📷 画像", onClick = { photoCustomOf = member })
          }
        }
      }
    }
    if (selected.isNotEmpty()) {
      ClawPrimaryButton(text = if (busy) "作っています…" else "選んだ ${selected.size}人を、キャラ図鑑に作る", onClick = { if (!busy) onCreateSelected() }, modifier = Modifier.fillMaxWidth())
      ClawSecondaryButton(text = "🗑 選んだ ${selected.size}人（${terms.member}）を、ファンブックから消す", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
    }
    if (confirmDelete) {
      FanBookConfirmDialog(
        title = "${selected.size}人を消しますか？",
        text = "ファンブックから見えなくなります（元のデータや写真のファイルは、消えません）。同じ人をまた足すと、元に戻ります。",
        confirmText = "消す",
        onDismiss = { confirmDelete = false },
        onConfirm = {
          confirmDelete = false
          onDeleteMembers(members.filter { it.id in selected })
        },
      )
    }
  }
}

/** 1人のページ：大きな写真とプロフィール。前後にめくれて、ここから、キャラ図鑑に作る。写真は、押すか長押しで、入れ替えられる。 */
@Composable
internal fun FanBookMemberView(
  viewModel: MainViewModel,
  group: String,
  members: List<IdolMemberRow>,
  index: Int,
  onIndex: (Int) -> Unit,
  busy: Boolean,
  message: String,
  photoVersion: Int,
  onPickPhoto: (IdolMemberRow) -> Unit,
  onCreate: (IdolMemberRow, String, FanBookCreatePhotos) -> Unit,
  onDelete: (IdolMemberRow) -> Unit = {},
  onSearchImage: (IdolMemberRow) -> Unit = {},
  onAddExtraFromPhone: (IdolMemberRow) -> Unit = {},
  onShareToPhoto: (IdolMemberRow, Boolean) -> Unit = { _, _ -> },
  shareWaiting: Boolean = false,
  onCancelShare: () -> Unit = {},
  onNote: (String) -> Unit = {},
  onResearch: (IdolMemberRow) -> Unit = {},
  onPhotoFromUrl: (IdolMemberRow, Boolean) -> Unit = { _, _ -> },
  onPickMany: (IdolMemberRow, Boolean) -> Unit = { _, _ -> },
) {
  val member = members.getOrNull(index) ?: return
  var confirmDelete by remember(member.id) { mutableStateOf(false) }
  var photoCustom by remember(member.id) { mutableStateOf(false) }
  var useMain by remember(member.id) { mutableStateOf(member.hasPhoto) }
  var extraPicks by remember(member.id) { mutableStateOf(setOf<Int>()) }
  var makeSheet by remember(member.id) { mutableStateOf(false) }
  var name by remember(member.id) { mutableStateOf(member.name) }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ClawSecondaryButton(text = "← 前へ", onClick = { if (index > 0) onIndex(index - 1) })
      Text("${index + 1} / ${members.size}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.weight(1f))
      ClawSecondaryButton(text = "次へ →", onClick = { if (index < members.size - 1) onIndex(index + 1) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FanBookPhotoPager(viewModel, group, member, photoVersion, onLongClickPhoto = { onPickPhoto(member) })
        ClawSecondaryButton(text = if (member.hasPhoto) "📷 写真を入れ替える" else "📷 写真を入れる（スマホから）", onClick = { onPickPhoto(member) }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🔎 この人の画像を探す", onClick = { onSearchImage(member) }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "⭐ 📷 画像（追加機能）", onClick = { photoCustom = true }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🌐 ネットで調べる（経歴・作品・話し方・声）", onClick = { onResearch(member) }, modifier = Modifier.fillMaxWidth())
        if (shareWaiting) {
          Text("待っています：X・Instagram・TikTok・ブラウザなどで、画像を開いて「共有 → このアプリ」を選んでください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
          ClawSecondaryButton(text = "共有を待つのをやめる", onClick = onCancelShare, modifier = Modifier.fillMaxWidth())
        } else {
          ClawSecondaryButton(text = "📥 他のアプリから共有で、写真にする", onClick = { onShareToPhoto(member, false) }, modifier = Modifier.fillMaxWidth())
        }
        ClawSecondaryButton(text = "🔗 URL（画像・ページ）から、写真にする", onClick = { onPhotoFromUrl(member, false) }, modifier = Modifier.fillMaxWidth())
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(member.name, style = ClawTheme.type.title, color = ClawTheme.colors.text)
        if (member.nickname.isNotBlank()) Text("愛称：${member.nickname}", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        Text(group, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        val facts =
          listOfNotNull(
            member.generation.takeIf { it.isNotBlank() }?.let { "期：$it" },
            member.age?.let { "年齢：${it}歳" },
            member.birthday.takeIf { it.isNotBlank() }?.let { "誕生日：$it" },
            member.hometown.takeIf { it.isNotBlank() }?.let { "出身地：$it" },
            member.bloodType.takeIf { it.isNotBlank() }?.let { "血液型：${it}型" },
            member.heightCm?.let { "身長：${it.toString().removeSuffix(".0")}cm" },
            member.agency.takeIf { it.isNotBlank() }?.let { "事務所：$it" },
          )
        if (facts.isEmpty()) Text("プロフィール情報は未登録です。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        facts.forEach { Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.text) }
        if (member.tags.isNotEmpty()) Text("タイプ：${member.tags.joinToString("・")}", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        if (member.xUrl.isNotBlank()) {
          val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
          Text("X を開く", style = ClawTheme.type.caption, color = ClawTheme.colors.primary, modifier = Modifier.clickable { runCatching { uriHandler.openUri(member.xUrl) } }.padding(vertical = 4.dp))
        }
        if (member.source.isNotBlank()) Text("出典：${member.source}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
    }
    if (photoCustom) FanBookPhotoCustomDialog(viewModel, group, member, photoVersion, onPickPhoto, onSearchImage, onPhotoFromUrl, onPickMany, onDismiss = { photoCustom = false })
    FanBookExtraPhotos(viewModel, group, member, photoVersion, onChanged = onNote, onAddFromPhone = { onAddExtraFromPhone(member) }, onAddFromShare = { onShareToPhoto(member, true) }, onAddFromUrl = { onPhotoFromUrl(member, true) })
    FanBookNoteEditor(viewModel, group, member, onSaved = onNote)
    if (member.bio.isNotBlank()) Text("略歴：${member.bio}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    Text("見た目：${member.description}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    ClawTextField(value = name, onValueChange = { name = it }, placeholder = "図鑑での名前", label = "図鑑での名前（変えられます）", modifier = Modifier.fillMaxWidth())
    Text("図鑑に引き継ぐ画像（タップで選びます）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    FanBookPhotoPicker(viewModel, group, member, photoVersion, useMain, { useMain = it }, extraPicks, { n -> extraPicks = if (n in extraPicks) extraPicks - n else extraPicks + n })
    ClawSecondaryButton(
      text = if (makeSheet) "● 設定画も、そのまま作る（時間がかかります）" else "○ 設定画も、そのまま作る（時間がかかります）",
      onClick = { makeSheet = !makeSheet },
      modifier = Modifier.fillMaxWidth(),
    )
    ClawPrimaryButton(text = if (busy) "作っています…" else "🎤 キャラ図鑑に作る", onClick = { if (!busy && name.isNotBlank()) onCreate(member, name.trim(), FanBookCreatePhotos(useMain, extraPicks.sorted(), makeSheet)) }, modifier = Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
    ClawSecondaryButton(text = "🗑 この人を、ファンブックから消す", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
    if (confirmDelete) {
      FanBookConfirmDialog(
        title = "「${member.name}」を消しますか？",
        text = "ファンブックから見えなくなります（元のデータや写真のファイルは、消えません）。同じ人をまた足すと、元に戻ります。",
        confirmText = "消す",
        onDismiss = { confirmDelete = false },
        onConfirm = {
          confirmDelete = false
          onDelete(member)
        },
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
  }
}

/** 名前を1つ入れる小さなダイアログ（分類やグループを足すとき）。 */
@Composable
internal fun FanBookNameDialog(
  title: String,
  hint: String,
  label: String,
  confirmText: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hint, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawTextField(value = text, onValueChange = { text = it }, placeholder = label, label = label, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** メンバーを自分で足す（名前だけ必須。ほかは、あとから、補える）。 */
@Composable
internal fun FanBookAddMemberDialog(
  group: String,
  onDismiss: () -> Unit,
  onConfirm: (name: String, nickname: String, generation: String, birthday: String, hometown: String, description: String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var nickname by remember { mutableStateOf("") }
  var generation by remember { mutableStateOf("") }
  var birthday by remember { mutableStateOf("") }
  var hometown by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$group」にメンバーを追加") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("名前だけ入れれば、追加できます。ほかの項目は、あとから補えます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawTextField(value = name, onValueChange = { name = it }, placeholder = "名前（必須）", label = "名前", modifier = Modifier.fillMaxWidth())
        ClawTextField(value = nickname, onValueChange = { nickname = it }, placeholder = "愛称（省略可）", label = "愛称", modifier = Modifier.fillMaxWidth())
        ClawTextField(value = generation, onValueChange = { generation = it }, placeholder = "例: 3期（省略可）", label = "期", modifier = Modifier.fillMaxWidth())
        ClawTextField(value = birthday, onValueChange = { birthday = it }, placeholder = "例: 2001-02-03（省略可）", label = "誕生日", modifier = Modifier.fillMaxWidth())
        ClawTextField(value = hometown, onValueChange = { hometown = it }, placeholder = "例: 東京都（省略可）", label = "出身地", modifier = Modifier.fillMaxWidth())
        ClawTextField(value = description, onValueChange = { description = it }, placeholder = "例: idol, gentle smile, long brown hair（英語のタグ。省略可）", label = "見た目（英語のタグ）", modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), nickname.trim(), generation.trim(), birthday.trim(), hometown.trim(), description.trim()) }) { Text("追加する") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}
