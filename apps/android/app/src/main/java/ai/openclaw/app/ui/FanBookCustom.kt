package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawSecondaryButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 詳しい書き方・編集するファイルの一覧：apps/android/FANBOOK_CUSTOM_GUIDE.md
//
// ★ ここが「自分で機能を足す」場所です。
// グループの中の「⭐ 追加」ボタンと、グループ長押しメニューの「⭐ 追加機能」ボタンは、
// どちらも、下の FanBookCustomDialog を開きます。ダイアログの中身（text = の中）を書き換えて、
// 好きな表示やボタンを足してください。

/**
 * 追加ボタンを押したときに開く画面。
 * @param group 押されたグループの名前
 * @param members そのグループのメンバー（グループの中から開いたときだけ入る。メニューからのときは空）
 */
@Composable
internal fun FanBookCustomDialog(
  group: String,
  members: List<IdolMemberRow> = emptyList(),
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$group」の追加機能") },
    text = {
      // ↓ ここに、自分のコードを書く。（例：Column { ... } でボタンや文字を並べる）
      Text("ここに機能を書けます。（メンバー ${members.size}人）")
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

// ---------------------------------------------------------------- プロフィール画像のボタン
// 「📷 画像」ボタン（グループの一覧の各メンバー／1人のページ）を押すと開く画面です。
// 画像を入れ替える・取得する処理は、すでにあるものを呼ぶだけにしてあります（下の3つ）。
// 自分の処理を足したいときは「★ ここに書く」の場所に書いてください。

/**
 * @param member 押された人
 * @param onPickPhoto スマホの写真から選んで、メイン画像にする
 * @param onSearchImage ネットで画像を探して、選んでメイン画像にする
 * @param onPickMany スマホの写真を何枚か選ぶ。true＝1枚目をプロフィールにして残りはストック／false＝全部ストック
 * @param onPhotoFromUrl URL（画像・ページ）から、メイン画像にする（第2引数 true で追加写真）
 */
@Composable
internal fun FanBookPhotoCustomDialog(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  photoVersion: Int,
  onPickPhoto: (IdolMemberRow) -> Unit,
  onSearchImage: (IdolMemberRow) -> Unit,
  onPhotoFromUrl: (IdolMemberRow, Boolean) -> Unit,
  onPickMany: (IdolMemberRow, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「${member.name}」の画像") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IdolPhotoBox(viewModel, group, member, Modifier.width(120.dp).aspectRatio(3f / 4f), maxSide = 360, photoVersion = photoVersion)
        ClawSecondaryButton(text = "📚 複数選ぶ（1枚目＝プロフィール、残り＝ストック）", onClick = { onDismiss(); onPickMany(member, true) }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "📦 複数選んで、全部ストックに入れる", onClick = { onDismiss(); onPickMany(member, false) }, modifier = Modifier.fillMaxWidth())
        // 既にある仕組み（押すとダイアログを閉じて、その画面に移る）
        ClawSecondaryButton(text = "📷 スマホの写真から入れる", onClick = { onDismiss(); onPickPhoto(member) }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🔎 ネットで探して入れる", onClick = { onDismiss(); onSearchImage(member) }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🔗 URLから入れる", onClick = { onDismiss(); onPhotoFromUrl(member, false) }, modifier = Modifier.fillMaxWidth())
        // ★ ここに書く：自分のボタンや処理（例：ClawSecondaryButton(text = "…", onClick = { … })）
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}
