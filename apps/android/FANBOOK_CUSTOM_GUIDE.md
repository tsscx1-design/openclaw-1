# ファンブックに自分の機能を足すガイド

## 0. まず全体像

```
[スマホ画面 (Kotlin)]  ──「呼ぶ」──▶  [Gateway (TypeScript)]  ──▶ 名簿・写真ファイル
  ボタン / ダイアログ                    処理を書く                    など
```

- **画面だけで済む機能**（表示、並べ替え、数える、絞り込み）→ **Kotlin だけ**書けば足ります。 → レベル1
- **データを読む・書く機能**（名簿を変える、ネットから取る）→ **Kotlin + TypeScript** の両方を書きます。 → レベル2

迷ったら、まずレベル1から始めてください。

---

## 1. 編集するファイル一覧（早見表）

パスは、すべて `C:\オープン\openclaw\` からの相対です。

| やりたいこと | 編集するファイル |
|---|---|
| **機能の中身を書く（いちばん大事）** | `apps/android/app/src/main/java/ai/openclaw/app/ui/FanBookCustom.kt` |
| グループの中のボタンの位置・名前を変える | `apps/android/app/src/main/java/ai/openclaw/app/ui/IdolFanBook.kt`（`⭐ 追加` で検索） |
| グループ長押しメニューのボタンを変える | `apps/android/app/src/main/java/ai/openclaw/app/ui/IdolFanBookMenus.kt`（`⭐ 追加機能` で検索） |
| 1人のメンバーのページにボタンを足す | `apps/android/app/src/main/java/ai/openclaw/app/ui/IdolFanBook.kt` の `FanBookMemberView` |
| Gateway に新しい処理を足す（レベル2） | 下の「レベル2」の **4つのファイル** |

---

## 2. レベル1：画面だけの機能（Kotlin だけ）

### 書く場所
`apps/android/app/src/main/java/ai/openclaw/app/ui/FanBookCustom.kt`

この中の `FanBookCustomDialog` が、2つのボタンを押したときに開く画面です。
`text = { ... }` の中に書きます。

### 使える値
- `group` … グループ名（例: `"乃木坂46"`）
- `members` … そのグループのメンバー一覧（グループの中から開いたときだけ入ります）
  - 各メンバー（`IdolMemberRow`）の主な項目: `name`（名前）、`nickname`（愛称）、`hasPhoto`（写真があるか）、`hasMemo`（メモがあるか）、`myTags`（自分のタグ）

### 例A：写真がない人を数える
```kotlin
text = {
  val noPhoto = members.filter { !it.hasPhoto }
  Text("写真がない人：${noPhoto.size}人\n" + noPhoto.joinToString("、") { it.name })
},
```

### 例B：ボタンを押すと文字が変わる
```kotlin
text = {
  var count by remember { mutableStateOf(0) }
  Column {
    Text("押した回数：$count")
    ClawSecondaryButton(text = "押す", onClick = { count++ })
  }
},
```

### 例C：メモを書いた人だけを表示する
```kotlin
text = {
  val withMemo = members.filter { it.hasMemo }
  Text(if (withMemo.isEmpty()) "メモのある人はいません" else withMemo.joinToString("\n") { "📝 ${it.name}" })
},
```

### import について
`Column`、`remember`、`mutableStateOf`、`getValue`、`setValue`、`ClawSecondaryButton` などを使うと、ファイル先頭に `import` が必要です。
Android Studio なら、赤く出た名前の上で `Alt + Enter` を押すと自動で入ります。手で書く場合は、同じフォルダの `IdolFanBookMenus.kt` の先頭にある `import` をコピーすると確実です。

---

## 3. レベル2：Gateway とやり取りする機能（Kotlin + TypeScript）

例：「グループ内の全員の名前を、Gateway に送って何かする」。
新しい処理の名前は `idolmaster.custom.hello` とします（`idolmaster.` で始めると仲間として探しやすいです）。

### 手順（4か所に書く）

**① 処理の本体（TypeScript）**
`src/gateway/server-methods/media-studio.ts`

`"idolmaster.note.get": guarded(...)` の近くに、同じ形で足します。
```ts
  "idolmaster.custom.hello": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const members = idolMembers(group); // そのグループのメンバー（idol-master.ts から import 済みか確認）
    respond(true, { count: members.length, names: members.map((m) => m.name) }, undefined);
  }),
```
- `params` … スマホから送られた値。
- `respond(true, 結果, undefined)` … スマホに返す値。
- 失敗を返すときは `fail(respond, "理由")`。

**② 名前の登録（TypeScript）**
`src/gateway/methods/core-descriptors.ts`

`["idolmaster.note.get", "partners", "operator.read", "2026.10"],` の近くに足します。
```ts
  ["idolmaster.custom.hello", "partners", "operator.read", "2026.10"],
```
- 読むだけなら `operator.read`、書き込むなら `operator.write`。
- **ここを忘れると、Gateway が「知らない処理」として断ります。**

**③ スマホ側の名前の登録（Kotlin）**
`apps/android/app/src/main/java/ai/openclaw/app/gateway/GatewayProtocol.kt`

`IdolMasterNoteGet("idolmaster.note.get"),` の近くに足します。
```kotlin
  IdolMasterCustomHello("idolmaster.custom.hello"),
```

**④ スマホから呼ぶ（Kotlin）**
`FanBookCustom.kt` に書きます。呼ぶには `viewModel` と `scope` が必要です。

- `FanBookCustomDialog` の引数に `viewModel: MainViewModel` を足します。
- 呼び出し元 2 か所（`IdolFanBook.kt` と `IdolFanBookMenus.kt`）にも、`viewModel = viewModel` を足します。
  - `FanBookGroupMenu` は `viewModel` を受け取っていないので、その呼び出し元（`IdolMasterScreen.kt`）から渡す必要があります。分かりにくければ、まず「グループの中のボタン」だけで試してください。

```kotlin
val scope = rememberCoroutineScope()
var result by remember { mutableStateOf("") }

ClawSecondaryButton(text = "Gateway に聞く", onClick = {
  scope.launch {
    runCatching {
      viewModel.call(GatewayMethod.IdolMasterCustomHello, JSONObject().put("group", group))
    }
      .onSuccess { result = "人数：${it.optInt("count")}" }
      .onFailure { result = "失敗：${it.message}" }
  }
})
Text(result)
```
実際に動いている見本は `FanBookExtras.kt` の `FanBookNoteEditor`（メモ保存）です。迷ったらこれを真似してください。

---

## 4. 動かすまで

1. ファイルを保存する。
2. **Kotlin を変えた** → Android アプリを再ビルドして、スマホに入れ直す。
3. **TypeScript を変えた** → Gateway を再起動する。
   - 稼働中の Gateway を止めるときは、必ず確認を取ってからにしてください。
4. 古い画面のまま変わらないときは、アプリを一度完全に閉じて開き直す。

> ビルドや再起動は、依頼してもらえれば私が実行します。

---

## 5. 困ったとき

| 症状 | 原因と対処 |
|---|---|
| 赤い波線が出る | `import` 不足が多い。`Alt + Enter` で自動追加 |
| ビルドエラー `Unresolved reference` | 名前の打ち間違い、または `import` 不足 |
| ボタンを押しても何も起きない（レベル2） | ②の `core-descriptors.ts` の登録漏れか、Gateway の再起動忘れ |
| 「その人が名簿に見つかりません」 | `group` や `id` の渡し忘れ |
| 画面が変わらない | アプリの再ビルド・入れ直しを忘れていないか |

エラーメッセージをそのまま貼ってもらえれば、原因を調べます。

---

## 6. 守ってほしい約束

- 勝手に件数や年齢などの**制限を入れない**（入れたいときは先に相談する）。
- 名簿を書き換える機能を作るときは、先に `createFanBookBackup()` でバックアップを取る（`idol-merge.ts` が見本）。
- トークンやパスワードをコードに直接書かない。
