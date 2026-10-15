package ai.openclaw.app.ui

/** 外見オプションの一覧。大ジャンル → 小分類 → 項目。サーバーの character-options.ts と同じ並び（id は同じ）。 */
internal data class CharacterOptionPart(
  val name: String,
  /** true なら1つだけ選べる。false ならいくつでも選べる。 */
  val single: Boolean,
  val items: List<Pair<String, String>>,
)

internal data class CharacterOptionGenre(
  val name: String,
  val parts: List<CharacterOptionPart>,
)

internal val CHARACTER_OPTION_CATALOG: List<CharacterOptionGenre> =
  listOf(
    optionGenre0(),
    optionGenre1(),
    optionGenre2(),
    optionGenre3(),
    optionGenre4(),
    optionGenre5(),
    optionGenre6(),
    optionGenre7(),
    optionGenre8(),
    optionGenre9(),
    optionGenre10(),
    optionGenre11(),
    optionGenre12(),
    optionGenre13(),
    optionGenre14(),
    optionGenre15(),
    optionGenre16(),
    optionGenre17(),
    optionGenre18(),
    optionGenre19(),
    optionGenre20(),
    optionGenre21(),
    optionGenre22(),
    optionGenre23(),
    optionGenre24(),
    optionGenre25(),
    optionGenre26(),
    optionGenre27(),
    optionGenre28(),
    optionGenre29(),
    optionGenre30(),
    optionGenre31(),
    optionGenre32(),
    optionGenre33(),
    optionGenre34(),
    optionGenre35(),
    optionGenre36(),
    optionGenre37(),
    optionGenre38(),
    optionGenre39(),
    optionGenre40(),
    optionGenre41(),
    optionGenre42(),
    optionGenre43(),
    optionGenre44(),
    optionGenre45(),
    optionGenre46(),
    optionGenre47(),
    optionGenre48(),
    optionGenre49(),
    optionGenre50(),
    optionGenre51(),
    optionGenre52(),
    optionGenre53(),
    optionGenre54(),
    optionGenre55(),
    optionGenre56(),
    optionGenre57(),
    optionGenre58(),
    optionGenre59(),
    optionGenre60(),
    optionGenre61(),
    optionGenre62(),
    optionGenre63(),
    optionGenre64(),
    optionGenre65(),
    optionGenre66(),
    optionGenre67(),
    optionGenre68(),
    optionGenre69(),
    optionGenre70(),
    optionGenre71(),
    optionGenre72(),
    optionGenre73(),
    optionGenre74(),
    optionGenre75(),
    optionGenre76(),
    optionGenre77(),
    optionGenre78(),
    optionGenre79(),
    optionGenre80(),
    optionGenre81(),
    optionGenre82(),
    optionGenre83(),
    optionGenre84(),
    optionGenre85(),
    optionGenre86(),
    optionGenre87(),
    optionGenre88(),
    optionGenre89(),
    optionGenre90(),
    optionGenre91(),
    optionGenre92(),
    optionGenre93(),
    optionGenre94(),
    optionGenre95(),
    optionGenre96(),
    optionGenre97(),
    optionGenre98(),
    optionGenre99(),
    optionGenre100(),
    optionGenre101(),
    optionGenre102(),
    optionGenre103(),
    optionGenre104(),
    optionGenre105(),
    optionGenre106(),
    optionGenre107(),
    optionGenre108(),
    optionGenre109(),
    optionGenre110(),
    optionGenre111(),
    optionGenre112(),
    optionGenre113(),
    optionGenre114(),
    optionGenre115(),
    optionGenre116(),
    optionGenre117(),
    optionGenre118(),
    optionGenre119(),
    optionGenre120(),
    optionGenre121(),
    optionGenre122(),
    optionGenre123(),
    optionGenre124(),
    optionGenre125(),
    optionGenre126(),
    optionGenre127(),
    optionGenre128(),
    optionGenre129(),
    optionGenre130(),
    optionGenre131(),
    optionGenre132(),
    optionGenre133(),
    optionGenre134(),
    optionGenre135(),
    optionGenre136(),
    optionGenre137(),
    optionGenre138(),
    optionGenre139(),
    optionGenre140(),
    optionGenre141(),
    optionGenre142(),
    optionGenre143(),
    optionGenre144(),
    optionGenre145(),
    optionGenre146(),
    optionGenre147(),
    optionGenre148(),
    optionGenre149(),
    optionGenre150(),
    optionGenre151(),
    optionGenre152(),
    optionGenre153(),
    optionGenre154(),
    optionGenre155(),
    optionGenre156(),
    optionGenre157(),
    optionGenre158(),
    optionGenre159(),
    optionGenre160(),
    optionGenre161(),
    optionGenre162(),
    optionGenre163(),
    optionGenre164(),
    optionGenre165(),
    optionGenre166(),
    optionGenre167(),
    optionGenre168(),
    optionGenre169(),
    optionGenre170(),
    optionGenre171(),
    optionGenre172(),
    optionGenre173(),
    optionGenre174(),
    optionGenre175(),
    optionGenre176(),
    optionGenre177(),
    optionGenre178(),
    optionGenre179(),
    optionGenre180(),
    optionGenre181(),
    optionGenre182(),
    optionGenre183(),
    optionGenre184(),
    optionGenre185(),
    optionGenre186(),
    optionGenre187(),
    optionGenre188(),
    optionGenre189(),
    optionGenre190(),
    optionGenre191(),
    optionGenre192(),
    optionGenre193(),
    optionGenre194(),
    optionGenre195(),
    optionGenre196(),
    optionGenre197(),
    optionGenre198(),
    optionGenre199(),
    optionGenre200(),
    optionGenre201(),
    optionGenre202(),
    optionGenre203(),
    optionGenre204(),
    optionGenre205(),
    optionGenre206(),
    optionGenre207(),
    optionGenre208(),
    optionGenre209(),
    optionGenre210(),
    optionGenre211(),
    optionGenre212(),
    optionGenre213(),
    optionGenre214(),
    optionGenre215(),
    optionGenre216(),
    optionGenre217(),
    optionGenre218(),
    optionGenre219(),
  )

private fun optionPart0(): CharacterOptionPart =
  CharacterOptionPart(
    "眼帯・目の補助",
    true,
    listOf(
      "eyepatch" to "眼帯（指定なし）",
      "eyepatch_left" to "眼帯（左目）",
      "eyepatch_right" to "眼帯（右目）",
      "eyepatch_both" to "眼帯（両目）",
      "prosthetic_eye" to "義眼",
      "blind_eye" to "片目が白濁",
      "blindfold" to "目隠し",
    ),
  )

private fun optionPart1(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳の特徴",
    true,
    listOf(
      "heterochromia" to "オッドアイ",
      "glowing_eyes" to "光る瞳",
      "empty_eyes" to "ハイライトなし",
    ),
  )

private fun optionPart2(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳の形",
    true,
    listOf(
      "slit_pupils" to "縦長の瞳",
      "star_pupils" to "星の瞳",
      "cross_pupils" to "十字の瞳",
      "heart_pupils" to "ハートの瞳",
      "spiral_eyes" to "渦巻きの瞳",
      "ringed_eyes" to "輪の瞳",
    ),
  )

private fun optionPart3(): CharacterOptionPart =
  CharacterOptionPart(
    "目元",
    false,
    listOf(
      "tear_mole" to "泣きぼくろ",
      "eye_bags" to "目の下の隈",
      "long_lashes" to "長いまつ毛",
      "eye_scar" to "目の傷",
      "eyeshadow" to "濃いアイシャドウ",
      "eye_marks" to "目の下の紋様",
    ),
  )

private fun optionPart4(): CharacterOptionPart =
  CharacterOptionPart(
    "耳",
    true,
    listOf(
      "cat_ears" to "猫耳としっぽ",
      "bunny_ears" to "うさ耳",
      "dog_ears" to "犬耳",
      "fox_ears" to "狐耳",
      "wolf_ears" to "狼耳",
      "bear_ears" to "熊耳",
      "horse_ears" to "馬耳",
      "floppy_ears" to "垂れ耳",
      "pointy_ears" to "エルフ耳",
      "fin_ears" to "ヒレ耳",
    ),
  )

private fun optionPart5(): CharacterOptionPart =
  CharacterOptionPart(
    "耳の補助",
    false,
    listOf(
      "hearing_aid" to "補聴器",
      "cochlear_implant" to "人工内耳",
    ),
  )

private fun optionPart6(): CharacterOptionPart =
  CharacterOptionPart(
    "角",
    true,
    listOf(
      "demon" to "悪魔の角としっぽ",
      "demon_horns" to "悪魔の角",
      "oni_horns" to "鬼の角",
      "dragon_horns" to "竜の角",
      "ram_horns" to "羊の角",
      "unicorn_horn" to "一本角",
      "antlers" to "鹿の角",
    ),
  )

private fun optionPart7(): CharacterOptionPart =
  CharacterOptionPart(
    "頭上の飾り・触角",
    false,
    listOf(
      "halo" to "光の輪",
      "broken_halo" to "壊れた光の輪",
      "antennae" to "触角",
      "ahoge" to "アホ毛",
    ),
  )

private fun optionPart8(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の特徴",
    false,
    listOf(
      "streaked_hair" to "白髪の一部・メッシュ",
      "inner_color" to "インナーカラー",
      "gradient_hair" to "グラデーションの髪",
      "two_tone_hair" to "ツートンの髪",
      "hair_over_eye" to "片目が隠れる髪",
      "drill_hair" to "縦ロール",
    ),
  )

private fun optionPart9(): CharacterOptionPart =
  CharacterOptionPart(
    "口元",
    false,
    listOf(
      "fang" to "八重歯",
      "fangs" to "牙",
      "mouth_mole" to "口元のほくろ",
      "mouth_scar" to "口の傷",
      "dark_lips" to "濃い口紅",
    ),
  )

private fun optionPart10(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の肌・傷",
    false,
    listOf(
      "freckles" to "そばかす",
      "face_mole" to "ほくろ",
      "cheek_scar" to "頬の傷",
      "face_scar" to "顔の傷跡",
      "bandaid_nose" to "鼻の絆創膏",
      "cheek_gauze" to "頬のガーゼ",
      "face_bandage" to "顔の包帯",
      "forehead_jewel" to "額の宝石",
      "third_eye" to "第三の目",
      "face_paint" to "フェイスペイント",
      "facial_mark" to "顔の紋様",
    ),
  )

private fun optionGenre0(): CharacterOptionGenre =
  CharacterOptionGenre(
    "顔・頭",
    listOf(
    optionPart0(),
    optionPart1(),
    optionPart2(),
    optionPart3(),
    optionPart4(),
    optionPart5(),
    optionPart6(),
    optionPart7(),
    optionPart8(),
    optionPart9(),
    optionPart10(),
    ),
  )

private fun optionPart11(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の大きさ・頭身",
    true,
    listOf(
      "face_small" to "小顔",
      "face_slightly_small" to "やや小さめの顔",
      "face_slightly_large" to "やや大きめの顔",
      "face_large" to "大きな顔",
      "heads_6" to "6頭身",
      "heads_7" to "7頭身",
      "heads_8" to "8頭身",
      "heads_9" to "9頭身",
    ),
  )

private fun optionPart12(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の形",
    true,
    listOf(
      "face_round" to "丸顔",
      "face_oval" to "卵型",
      "face_long" to "面長",
      "face_heart" to "逆三角形",
      "face_square" to "ベース型（四角い輪郭）",
      "face_angular" to "彫りの深い顔",
    ),
  )

private fun optionPart13(): CharacterOptionPart =
  CharacterOptionPart(
    "あご",
    true,
    listOf(
      "chin_sharp" to "シャープなあご",
      "chin_round" to "丸いあご",
      "chin_small" to "小さなあご",
      "chin_cleft" to "割れあご",
      "chin_long" to "長いあご",
    ),
  )

private fun optionPart14(): CharacterOptionPart =
  CharacterOptionPart(
    "額・頬",
    false,
    listOf(
      "forehead_wide" to "広い額",
      "forehead_narrow" to "狭い額",
      "cheekbones_high" to "高い頬骨",
      "cheeks_hollow" to "こけた頬",
      "cheeks_blush" to "赤みのある頬",
    ),
  )

private fun optionPart15(): CharacterOptionPart =
  CharacterOptionPart(
    "眉",
    true,
    listOf(
      "brow_thick" to "太い眉",
      "brow_thin" to "細い眉",
      "brow_straight" to "まっすぐな眉",
      "brow_arched" to "弧を描く眉",
      "brow_furrowed" to "ひそめた眉",
      "brow_none" to "眉が薄い",
    ),
  )

private fun optionPart16(): CharacterOptionPart =
  CharacterOptionPart(
    "目の大きさ・形",
    true,
    listOf(
      "eyes_large" to "大きな目",
      "eyes_small" to "小さめの目",
      "eyes_narrow" to "切れ長の目",
      "eyes_tsuri" to "吊り目",
      "eyes_tare" to "垂れ目",
      "eyes_jito" to "ジト目",
      "eyes_round" to "丸い目",
    ),
  )

private fun optionPart17(): CharacterOptionPart =
  CharacterOptionPart(
    "目と目の間隔",
    true,
    listOf(
      "eyes_wide_set" to "離れた目",
      "eyes_close_set" to "寄った目",
    ),
  )

private fun optionPart18(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻",
    true,
    listOf(
      "nose_small" to "小さな鼻",
      "nose_high" to "高い鼻筋",
      "nose_round" to "丸い鼻",
      "nose_pointed" to "とがった鼻",
      "nose_large" to "大きな鼻",
    ),
  )

private fun optionPart19(): CharacterOptionPart =
  CharacterOptionPart(
    "口・唇",
    true,
    listOf(
      "mouth_small" to "小さな口",
      "mouth_wide" to "大きな口",
      "lips_plump" to "厚い唇",
      "lips_thin" to "薄い唇",
    ),
  )

private fun optionGenre1(): CharacterOptionGenre =
  CharacterOptionGenre(
    "顔のつくり",
    listOf(
    optionPart11(),
    optionPart12(),
    optionPart13(),
    optionPart14(),
    optionPart15(),
    optionPart16(),
    optionPart17(),
    optionPart18(),
    optionPart19(),
    ),
  )

private fun optionPart20(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の長さ",
    true,
    listOf(
      "hair_short" to "ショート",
      "hair_bob" to "ボブ",
      "hair_medium" to "ミディアム",
      "hair_long" to "ロング",
      "hair_very_long" to "ベリーロング",
    ),
  )

private fun optionPart21(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の色",
    true,
    listOf(
      "hc_black" to "黒",
      "hc_brown" to "茶",
      "hc_blonde" to "金",
      "hc_silver" to "銀",
      "hc_white" to "白",
      "hc_grey" to "灰",
      "hc_red" to "赤",
      "hc_orange" to "オレンジ",
      "hc_pink" to "ピンク",
      "hc_purple" to "紫",
      "hc_blue" to "青",
      "hc_green" to "緑",
      "hc_rainbow" to "虹色",
    ),
  )

private fun optionPart22(): CharacterOptionPart =
  CharacterOptionPart(
    "髪質",
    true,
    listOf(
      "hair_straight" to "ストレート",
      "hair_wavy" to "ウェーブ",
      "hair_curly" to "巻き髪",
      "hair_messy" to "くせ毛・はねた髪",
      "hair_fluffy" to "ふわふわ",
    ),
  )

private fun optionPart23(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪",
    true,
    listOf(
      "bangs_blunt" to "ぱっつん",
      "bangs_side" to "斜め前髪",
      "bangs_parted" to "センター分け",
      "bangs_none" to "おでこを出す",
      "bangs_see_through" to "シースルーバング",
    ),
  )

private fun optionPart24(): CharacterOptionPart =
  CharacterOptionPart(
    "髪型",
    true,
    listOf(
      "style_ponytail" to "ポニーテール",
      "style_twintails" to "ツインテール",
      "style_side_ponytail" to "サイドテール",
      "style_bun" to "お団子",
      "style_braid" to "三つ編み",
      "style_half_up" to "ハーフアップ",
      "style_updo" to "アップスタイル",
      "style_down" to "おろし髪",
    ),
  )

private fun optionGenre2(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪",
    listOf(
    optionPart20(),
    optionPart21(),
    optionPart22(),
    optionPart23(),
    optionPart24(),
    ),
  )

private fun optionPart25(): CharacterOptionPart =
  CharacterOptionPart(
    "色",
    true,
    listOf(
      "ec_black" to "黒",
      "ec_brown" to "茶",
      "ec_amber" to "琥珀",
      "ec_blue" to "青",
      "ec_aqua" to "水色",
      "ec_green" to "緑",
      "ec_red" to "赤",
      "ec_gold" to "金",
      "ec_purple" to "紫",
      "ec_pink" to "ピンク",
      "ec_grey" to "灰",
    ),
  )

private fun optionGenre3(): CharacterOptionGenre =
  CharacterOptionGenre(
    "瞳の色",
    listOf(
    optionPart25(),
    ),
  )

private fun optionPart26(): CharacterOptionPart =
  CharacterOptionPart(
    "腕の欠損・義手",
    true,
    listOf(
      "missing_arm_left" to "左腕欠損",
      "missing_arm_right" to "右腕欠損",
      "missing_arms" to "両腕欠損",
      "prosthetic_arm" to "義手（指定なし）",
      "prosthetic_arm_left" to "義手（左）",
      "prosthetic_arm_right" to "義手（右）",
      "prosthetic_arms" to "義手（両腕）",
      "mechanical_arm" to "機械の腕",
      "hook_hand" to "鉤の義手",
    ),
  )

private fun optionPart27(): CharacterOptionPart =
  CharacterOptionPart(
    "腕・手の印",
    false,
    listOf(
      "arm_bandage" to "腕の包帯",
      "hand_bandage" to "手の包帯",
      "arm_scar" to "腕の傷跡",
      "burn_scar" to "火傷の跡",
      "missing_fingers" to "指の欠け",
      "long_nails" to "長い爪",
      "nail_polish" to "マニキュア",
      "arm_cast" to "腕のギプス",
      "arm_sling" to "三角巾",
    ),
  )

private fun optionPart28(): CharacterOptionPart =
  CharacterOptionPart(
    "脚の欠損・義足",
    true,
    listOf(
      "missing_leg_left" to "左足欠損",
      "missing_leg_right" to "右足欠損",
      "missing_legs" to "両足欠損",
      "prosthetic_leg" to "義足（指定なし）",
      "prosthetic_leg_left" to "義足（左）",
      "prosthetic_leg_right" to "義足（右）",
      "prosthetic_legs" to "義足（両足）",
      "mechanical_leg" to "機械の脚",
    ),
  )

private fun optionPart29(): CharacterOptionPart =
  CharacterOptionPart(
    "脚の印",
    false,
    listOf(
      "leg_bandage" to "脚の包帯",
      "leg_scar" to "脚の傷跡",
      "leg_cast" to "脚のギプス",
      "leg_brace" to "脚の装具",
    ),
  )

private fun optionPart30(): CharacterOptionPart =
  CharacterOptionPart(
    "胴・首の印",
    false,
    listOf(
      "neck_scar" to "首の傷跡",
      "neck_bandage" to "首の包帯",
      "chest_scar" to "胸の傷跡",
      "torso_scar" to "お腹の傷跡",
      "back_scar" to "背中の傷跡",
      "surgical_scar" to "手術痕",
      "stitches" to "縫い目",
      "torso_bandage" to "胴の包帯",
      "bandaid" to "絆創膏",
      "gauze" to "ガーゼ",
    ),
  )

private fun optionPart31(): CharacterOptionPart =
  CharacterOptionPart(
    "翼",
    true,
    listOf(
      "angel_wings" to "天使の翼",
      "demon_wings" to "悪魔の翼",
      "bat_wings" to "コウモリの翼",
      "dragon_wings" to "竜の翼",
      "butterfly_wings" to "蝶の翅",
      "fairy_wings" to "妖精の羽",
      "mechanical_wings" to "機械の翼",
      "one_wing" to "片翼",
      "broken_wing" to "折れた翼",
    ),
  )

private fun optionPart32(): CharacterOptionPart =
  CharacterOptionPart(
    "しっぽ",
    true,
    listOf(
      "fox_tail" to "狐のしっぽ",
      "wolf_tail" to "狼のしっぽ",
      "dog_tail" to "犬のしっぽ",
      "horse_tail" to "馬のしっぽ",
      "dragon_tail" to "竜のしっぽ",
      "lizard_tail" to "とかげのしっぽ",
    ),
  )

private fun optionPart33(): CharacterOptionPart =
  CharacterOptionPart(
    "人ならざる体",
    true,
    listOf(
      "mermaid" to "人魚",
      "lamia" to "ラミア（蛇の下半身）",
      "harpy" to "ハーピー（鳥の腕）",
      "arachne" to "蜘蛛の脚",
      "slime_girl" to "スライム",
      "monster_girl" to "モンスター娘",
    ),
  )

private fun optionPart34(): CharacterOptionPart =
  CharacterOptionPart(
    "首・肩",
    true,
    listOf(
      "neck_long" to "長い首",
      "neck_slender" to "細い首",
      "neck_short" to "短めの首",
      "neck_thick" to "太い首",
      "shoulders_broad" to "広い肩",
      "shoulders_narrow" to "華奢な肩",
      "shoulders_sloping" to "なで肩",
    ),
  )

private fun optionPart35(): CharacterOptionPart =
  CharacterOptionPart(
    "手・足の大きさ",
    false,
    listOf(
      "hands_large" to "大きな手",
      "hands_small" to "小さな手",
      "fingers_long" to "長い指",
      "feet_large" to "大きな足",
      "feet_small" to "小さな足",
    ),
  )

private fun optionPart36(): CharacterOptionPart =
  CharacterOptionPart(
    "人形・機械の体",
    false,
    listOf(
      "doll_joints" to "球体関節",
      "stitched_skin" to "継ぎはぎの肌",
      "cyborg" to "サイボーグ",
      "mechanical_parts" to "機械の部品",
      "exposed_wires" to "むき出しの配線",
      "circuit_lines" to "光る回路の線",
      "missing_limbs" to "四肢欠損（両腕・両脚なし）",
    ),
  )

private fun optionPart37(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の色・質感",
    true,
    listOf(
      "pale_skin" to "色白",
      "tan_skin" to "小麦色の肌",
      "dark_skin" to "褐色の肌",
      "albino" to "アルビノ",
      "grey_skin" to "灰色の肌",
      "blue_skin" to "青い肌",
      "red_skin" to "赤い肌",
      "green_skin" to "緑の肌",
      "scales" to "鱗のある肌",
    ),
  )

private fun optionPart38(): CharacterOptionPart =
  CharacterOptionPart(
    "印象",
    true,
    listOf(
      "youthful" to "若々しい（成人）",
      "mature" to "大人っぽい（成人）",
    ),
  )

private fun optionGenre4(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体",
    listOf(
    optionPart26(),
    optionPart27(),
    optionPart28(),
    optionPart29(),
    optionPart30(),
    optionPart31(),
    optionPart32(),
    optionPart33(),
    optionPart34(),
    optionPart35(),
    optionPart36(),
    optionPart37(),
    optionPart38(),
    ),
  )

private fun optionPart39(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・紋様",
    false,
    listOf(
      "womb_tattoo" to "淫紋",
      "tattoo" to "タトゥー",
      "sleeve_tattoo" to "腕いっぱいのタトゥー",
      "back_tattoo" to "背中のタトゥー",
      "neck_tattoo" to "首のタトゥー",
      "full_tattoo" to "全身のタトゥー",
      "tribal_tattoo" to "民族風の紋様",
      "henna" to "ヘナ",
      "body_writing" to "体に落書き",
    ),
  )

private fun optionPart40(): CharacterOptionPart =
  CharacterOptionPart(
    "痣・跡",
    false,
    listOf(
      "hickey" to "痣（キスマーク・あざ）",
      "tan_lines" to "日焼け跡",
      "whip_marks" to "鞭の跡",
      "bite_marks" to "噛み跡",
      "rope_marks" to "縄の跡",
    ),
  )

private fun optionPart41(): CharacterOptionPart =
  CharacterOptionPart(
    "体毛",
    false,
    listOf(
      "armpit_hair" to "わき毛",
      "leg_hair" to "すね毛",
      "body_hair" to "体毛",
    ),
  )

private fun optionPart42(): CharacterOptionPart =
  CharacterOptionPart(
    "下の毛",
    true,
    listOf(
      "shaved" to "パイパン",
      "pubic_hair" to "陰毛あり",
    ),
  )

private fun optionGenre5(): CharacterOptionGenre =
  CharacterOptionGenre(
    "肌の印",
    listOf(
    optionPart39(),
    optionPart40(),
    optionPart41(),
    optionPart42(),
    ),
  )

private fun optionPart43(): CharacterOptionPart =
  CharacterOptionPart(
    "ピアス",
    false,
    listOf(
      "navel_piercing" to "へそピアス",
      "ear_piercing" to "耳ピアス",
      "nipple_piercing" to "乳首ピアス",
      "tongue_piercing" to "舌ピアス",
      "lip_piercing" to "口ピアス",
      "eyebrow_piercing" to "眉ピアス",
      "nose_piercing" to "鼻ピアス",
      "septum_piercing" to "鼻中隔ピアス",
    ),
  )

private fun optionPart44(): CharacterOptionPart =
  CharacterOptionPart(
    "眼鏡・顔の小物",
    false,
    listOf(
      "glasses" to "眼鏡",
      "sunglasses" to "サングラス",
      "monocle" to "片眼鏡",
      "round_glasses" to "丸眼鏡",
      "medical_mask" to "マスク",
      "face_veil" to "顔のベール",
    ),
  )

private fun optionPart45(): CharacterOptionPart =
  CharacterOptionPart(
    "首",
    false,
    listOf(
      "collar" to "首輪",
      "leash" to "首輪とリード",
      "choker" to "チョーカー",
      "necklace" to "ネックレス",
      "pendant" to "ペンダント",
      "scarf" to "マフラー",
      "bell_collar" to "鈴の首輪",
    ),
  )

private fun optionPart46(): CharacterOptionPart =
  CharacterOptionPart(
    "頭の飾り",
    false,
    listOf(
      "hair_ribbon" to "髪のリボン",
      "hair_clip" to "ヘアピン",
      "headband" to "カチューシャ",
      "headband_forehead" to "カチューシャ（デコ出し）",
      "crown" to "王冠",
      "tiara" to "ティアラ",
      "headphones" to "ヘッドホン",
      "hair_flower" to "髪飾りの花",
      "witch_hat" to "魔女の帽子",
      "beret" to "ベレー帽",
      "maid_headdress" to "ヘッドドレス",
    ),
  )

private fun optionPart47(): CharacterOptionPart =
  CharacterOptionPart(
    "手・腕",
    false,
    listOf(
      "gloves" to "手袋",
      "fingerless_gloves" to "指なし手袋",
      "elbow_gloves" to "長手袋",
      "ring" to "指輪",
      "bracelet" to "ブレスレット",
      "wristwatch" to "腕時計",
      "armband" to "腕章",
      "wristband" to "リストバンド",
      "g1_acc_hand_ring_stack" to "重ねリング",
      "g1_acc_hand_ring_big" to "大きな宝石の指輪",
      "g1_acc_hand_ring_skull" to "ドクロのリング",
      "g1_acc_hand_ring_wedding" to "結婚指輪",
      "g1_acc_hand_gloves_leather" to "革手袋",
      "g1_acc_hand_gloves_lace" to "レース手袋",
      "g1_acc_hand_gloves_satin" to "サテンの長手袋",
      "g1_acc_hand_gloves_fingerless_long" to "指なしロング手袋",
      "g1_acc_hand_gloves_fishnet" to "網の手袋",
      "g1_acc_hand_gloves_latex" to "ラテックス手袋",
      "g1_acc_hand_gloves_white" to "白手袋",
      "g1_acc_hand_gloves_rubber" to "ゴム手袋",
      "g1_acc_hand_mittens" to "ミトン",
      "g1_acc_hand_arm_warmers" to "アームウォーマー",
      "g1_acc_hand_detached_sleeves" to "ディタッチドスリーブ",
      "g1_acc_hand_bracelet_bangle" to "バングル",
      "g1_acc_hand_bracelet_charm" to "チャームブレスレット",
      "g1_acc_hand_bracelet_leather" to "革ブレスレット",
      "g1_acc_hand_bracelet_spiked" to "スパイクブレスレット",
      "g1_acc_hand_cuffs_gold" to "金のカフ",
      "g1_acc_hand_armlet" to "二の腕のアームレット",
      "g1_acc_hand_hair_tie_wrist" to "手首のヘアゴム",
      "g1_acc_hand_wristband_sport" to "スポーツリストバンド",
      "g1_acc_hand_watch_gold" to "金時計",
      "g1_acc_hand_watch_smart" to "スマートウォッチ",
      "g1_acc_hand_henna" to "ヘナ模様の手",
    ),
  )

private fun optionPart48(): CharacterOptionPart =
  CharacterOptionPart(
    "脚・足",
    false,
    listOf(
      "garter" to "ガーター",
      "anklet" to "アンクレット",
      "thigh_strap" to "太もものベルト",
    ),
  )

private fun optionPart49(): CharacterOptionPart =
  CharacterOptionPart(
    "口・目の拘束具",
    false,
    listOf(
      "ball_gag" to "ボールギャグ",
    ),
  )

private fun optionPart50(): CharacterOptionPart =
  CharacterOptionPart(
    "首・胸",
    false,
    listOf(
      "g1_acc_neck_pearl" to "真珠のネックレス",
      "g1_acc_neck_choker_velvet" to "ベルベットチョーカー",
      "g1_acc_neck_choker_lace" to "レースチョーカー",
      "g1_acc_neck_choker_bell" to "ベルのチョーカー",
      "g1_acc_neck_choker_heart" to "ハートチョーカー",
      "g1_acc_neck_choker_ring" to "リングチョーカー",
      "g1_acc_neck_ribbon_bow" to "リボンチョーカー",
      "g1_acc_neck_cross" to "十字架のネックレス",
      "g1_acc_neck_locket" to "ロケット",
      "g1_acc_neck_pendant_gem" to "宝石ペンダント",
      "g1_acc_neck_crescent" to "三日月ペンダント",
      "g1_acc_neck_star" to "星のペンダント",
      "g1_acc_neck_key" to "鍵のペンダント",
      "g1_acc_neck_layered" to "重ねづけネックレス",
      "g1_acc_neck_collar_gold" to "金の首飾り",
      "g1_acc_neck_scarf_silk" to "シルクスカーフ",
      "g1_acc_neck_bandana" to "首のバンダナ",
      "g1_acc_neck_tie_loose" to "ゆるんだネクタイ",
      "g1_acc_neck_fur" to "ファーのえり巻き",
      "g1_acc_neck_flower_lei" to "花のレイ",
      "g1_acc_chest_chain" to "胸元のチェーン",
      "g1_acc_chest_brooch" to "ブローチ",
      "g1_acc_chest_bib" to "ビブネックレス",
      "g1_acc_nipple_chain_jewel" to "ブレストジュエリー",
    ),
  )

private fun optionPart51(): CharacterOptionPart =
  CharacterOptionPart(
    "足・脚",
    false,
    listOf(
      "g1_acc_leg_anklet_bell" to "鈴のアンクレット",
      "g1_acc_leg_anklet_chain" to "チェーンのアンクレット",
      "g1_acc_leg_anklet_pearl" to "真珠のアンクレット",
      "g1_acc_leg_thigh_ring" to "太ももリング",
      "g1_acc_leg_garter_lace" to "レースの靴下留め",
      "g1_acc_leg_garter_ribbon" to "リボンの脚飾り",
      "g1_acc_leg_chain" to "脚のチェーン",
      "g1_acc_leg_toe_ring_gem" to "トゥリング(宝石)",
      "g1_acc_leg_legwarmers_knit" to "ニットレッグウォーマー",
      "g1_acc_leg_sock_suspender" to "ソックスガーター",
      "g1_acc_leg_over_knee_socks" to "オーバーニー",
      "g1_acc_leg_thigh_boots_latex" to "ラテックスブーツ",
      "g1_acc_leg_ankle_boots_heel" to "ヒールのアンクルブーツ",
      "g1_acc_leg_platform_boots" to "厚底ブーツ",
      "g1_acc_leg_cowboy_boots" to "カウボーイブーツ",
      "g1_acc_leg_combat_boots" to "コンバットブーツ",
      "g1_acc_leg_rain_boots" to "レインブーツ",
      "g1_acc_leg_uggs" to "もこもこブーツ",
      "g1_acc_leg_stiletto_red" to "赤いハイヒール",
      "g1_acc_leg_glass_heels" to "透明ヒール",
      "g1_acc_leg_sandals_gladiator" to "グラディエーターサンダル",
      "g1_acc_leg_geta_modern" to "モダン下駄",
      "g1_acc_leg_zori" to "草履",
      "g1_acc_leg_mary_jane" to "ストラップ靴",
      "g1_acc_leg_loafer_adult" to "ローファー(大人)",
      "g1_acc_leg_oxford" to "オックスフォードシューズ",
      "g1_acc_leg_slippers_plush" to "もこもこスリッパ",
    ),
  )

private fun optionPart52(): CharacterOptionPart =
  CharacterOptionPart(
    "頭・顔",
    false,
    listOf(
      "g1_acc_head_crown_gold" to "金の王冠",
      "g1_acc_head_tiara_pearl" to "真珠のティアラ",
      "g1_acc_head_flower_crown" to "花冠",
      "g1_acc_head_laurel" to "月桂冠",
      "g1_acc_head_veil_bridal" to "ブライダルベール",
      "g1_acc_head_cat_ear_headband" to "猫耳カチューシャ",
      "g1_acc_head_bunny_ear_headband" to "うさ耳カチューシャ",
      "g1_acc_head_devil_horn_headband" to "悪魔角カチューシャ",
      "g1_acc_head_halo_headband" to "天使輪カチューシャ",
      "g1_acc_head_witch_hat" to "魔女帽子(大)",
      "g1_acc_head_bucket_hat" to "バケットハット",
      "g1_acc_head_sun_hat" to "つば広サンハット",
      "g1_acc_head_cowboy" to "カウガールハット",
      "g1_acc_head_fedora" to "中折れ帽",
      "g1_acc_head_top_hat_mini" to "ミニシルクハット",
      "g1_acc_head_beanie" to "ビーニー",
      "g1_acc_head_headscarf" to "ヘッドスカーフ",
      "g1_acc_head_turban" to "ターバン",
      "g1_acc_head_hijab_simple" to "スカーフ巻き",
      "g1_acc_head_hair_stick" to "かんざし",
      "g1_acc_head_hair_comb" to "髪飾りの櫛",
      "g1_acc_head_hair_clip_butterfly" to "蝶のヘアクリップ",
      "g1_acc_head_hair_pins_many" to "ヘアピンたくさん",
      "g1_acc_head_ribbon_big" to "大きなリボン",
      "g1_acc_head_chain_headpiece" to "頭のチェーン飾り",
      "g1_acc_head_ear_cuff" to "イヤーカフ",
      "g1_acc_head_earrings_hoop" to "フープピアス",
      "g1_acc_head_earrings_drop" to "ドロップイヤリング",
      "g1_acc_head_earrings_pearl" to "真珠イヤリング",
      "g1_acc_head_earrings_tassel" to "タッセルイヤリング",
      "g1_acc_head_earrings_star" to "星のイヤリング",
      "g1_acc_head_earrings_chain" to "チェーンイヤリング",
      "g1_acc_head_nose_stud" to "鼻のスタッド",
      "g1_acc_head_lip_ring" to "唇ピアス",
      "g1_acc_head_eyebrow_bar" to "眉のバーピアス",
      "g1_acc_head_face_jewel" to "顔のジュエリー",
      "g1_acc_head_mouth_mask_black" to "黒マスク",
      "g1_acc_head_mouth_mask_white" to "白い不織布マスク",
      "g1_acc_head_lace_mask" to "レースのマスク",
      "g1_acc_head_masquerade" to "仮面舞踏会マスク",
      "g1_acc_head_cat_mask" to "キャットマスク",
      "g1_acc_head_glasses_cat" to "キャットアイ眼鏡",
      "g1_acc_head_glasses_round_thin" to "細縁の丸眼鏡",
      "g1_acc_head_glasses_square" to "スクエア眼鏡",
      "g1_acc_head_glasses_rimless" to "縁なし眼鏡",
      "g1_acc_head_glasses_red" to "赤縁眼鏡",
      "g1_acc_head_glasses_chain" to "眼鏡チェーン",
      "g1_acc_head_glasses_top" to "眼鏡を頭に",
      "g1_acc_head_sunglasses_aviator" to "ティアドロップサングラス",
      "g1_acc_head_sunglasses_heart" to "ハート型サングラス",
      "g1_acc_head_sunglasses_small" to "小さいサングラス",
      "g1_acc_head_visor" to "サンバイザー",
      "g1_acc_head_goggles_head" to "ゴーグルを頭に",
      "g1_acc_head_earmuffs_fur" to "ふわふわイヤーマフ",
      "g1_acc_head_headphones_cat" to "猫耳ヘッドホン",
      "g1_acc_head_earbuds" to "イヤホン",
    ),
  )

private fun optionPart53(): CharacterOptionPart =
  CharacterOptionPart(
    "体・ベルト・バッグ",
    false,
    listOf(
      "g1_acc_body_belt_wide" to "太いコルセットベルト",
      "g1_acc_body_belt_chain" to "チェーンベルト",
      "g1_acc_body_belt_studded" to "鋲ベルト",
      "g1_acc_body_belt_rope" to "ロープベルト",
      "g1_acc_body_belt_sash" to "帯飾り",
      "g1_acc_body_belt_coin" to "コインベルト",
      "g1_acc_body_waist_beads" to "ウエストビーズ",
      "g1_acc_body_harness_leather" to "レザーハーネス",
      "g1_acc_body_suspenders_x" to "Xサスペンダー",
      "g1_acc_body_garter_thigh" to "太もものガーター",
      "g1_acc_body_navel_jewel" to "へそのジュエリー",
      "g1_acc_body_back_chain" to "背中のチェーン",
      "g1_acc_body_temp_tattoo" to "タトゥーシール",
      "g1_acc_body_tail_plug_fluffy" to "ふわふわしっぽ飾り",
      "g1_acc_body_wings_costume" to "飾り羽",
      "g1_acc_body_cape_short" to "ショートケープ",
      "g1_acc_body_bag_clutch" to "クラッチバッグ",
      "g1_acc_body_bag_tote" to "トートバッグ",
      "g1_acc_body_bag_mini" to "ミニバッグ",
      "g1_acc_body_bag_leather" to "革のハンドバッグ",
      "g1_acc_body_bag_basket" to "かごバッグ",
      "g1_acc_body_bag_chain" to "チェーンバッグ",
      "g1_acc_body_umbrella_clear" to "透明傘",
      "g1_acc_body_umbrella_lace" to "レースの日傘",
      "g1_acc_body_fan_hand" to "手持ち扇子",
      "g1_acc_body_fan_feather" to "羽根扇",
      "g1_acc_body_cigarette_holder" to "長いパイプ",
      "g1_acc_body_kiseru" to "キセル",
      "g1_acc_body_wine_bottle" to "ワインボトル",
      "g1_acc_body_champagne" to "シャンパングラス",
      "g1_acc_body_flower_single" to "一輪の花",
      "g1_acc_body_rose_mouth" to "口にバラ",
      "g1_acc_body_teddy" to "クマのぬいぐるみ",
      "g1_acc_body_phone_selfie" to "自撮りスマホ",
      "g1_acc_body_lollipop_big" to "大きなキャンディ",
      "g1_acc_body_balloon_heart" to "ハート風船",
    ),
  )

private fun optionGenre6(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アクセサリー",
    listOf(
    optionPart43(),
    optionPart44(),
    optionPart45(),
    optionPart46(),
    optionPart47(),
    optionPart48(),
    optionPart49(),
    optionPart50(),
    optionPart51(),
    optionPart52(),
    optionPart53(),
    ),
  )

private fun optionPart54(): CharacterOptionPart =
  CharacterOptionPart(
    "移動の補助",
    true,
    listOf(
      "wheelchair" to "車椅子",
      "electric_wheelchair" to "電動車椅子",
      "crutches" to "松葉杖",
      "cane" to "杖",
      "walker" to "歩行器",
      "white_cane" to "白杖",
    ),
  )

private fun optionPart55(): CharacterOptionPart =
  CharacterOptionPart(
    "付き添い",
    false,
    listOf(
      "guide_dog" to "盲導犬",
    ),
  )

private fun optionPart56(): CharacterOptionPart =
  CharacterOptionPart(
    "医療",
    false,
    listOf(
      "oxygen_tube" to "酸素チューブ",
      "iv_drip" to "点滴",
      "hospital_gown" to "病衣",
      "neck_brace" to "首のコルセット",
      "hospital_wristband" to "入院のリストバンド",
    ),
  )

private fun optionGenre7(): CharacterOptionGenre =
  CharacterOptionGenre(
    "補助具・医療",
    listOf(
    optionPart54(),
    optionPart55(),
    optionPart56(),
    ),
  )

private fun optionPart57(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装",
    true,
    listOf(
      "cos_maid" to "メイド",
      "cos_nurse" to "ナース",
      "cos_police" to "婦警",
      "cos_miko" to "巫女",
      "cos_bunny" to "バニーガール",
      "cos_china" to "チャイナドレス",
      "cos_kimono" to "着物",
      "cos_yukata" to "浴衣",
      "cos_oiran" to "花魁",
      "cos_kyonshi" to "キョンシー",
      "cos_witch" to "魔女",
      "cos_angel" to "天使の衣装",
      "cos_devil" to "悪魔の衣装",
      "cos_santa" to "サンタ",
      "cos_waitress" to "ウェイトレス",
      "cos_military" to "軍服",
      "cos_prisoner" to "囚人服",
      "cos_nun" to "シスター",
      "cos_princess" to "プリンセスのドレス",
      "cos_idol" to "アイドル衣装",
      "cos_race_queen" to "レースクイーン",
      "cos_cheer" to "チアリーダー",
      "cos_cabin_attendant" to "客室乗務員",
      "cos_office" to "オフィスレディ",
      "cos_gothic_lolita" to "ゴシックロリータ",
      "cos_punk" to "パンク",
      "cos_visual_kei" to "ヴィジュアル系",
      "cos_magical_girl" to "魔法少女風",
      "cos_zombie" to "ゾンビ",
      "cos_ninja" to "くノ一",
      "cos_swimsuit" to "水着",
      "cos_competition_swimsuit" to "競泳水着（競技用）",
      "cos_leotard" to "レオタード",
      "cos_yoga" to "ヨガウェア",
      "cos_tennis" to "テニスウェア",
      "cos_jersey" to "ジャージ（スポーツウェア）",
      "cos_running" to "ランニングウェア",
      "cos_dance" to "ダンス衣装",
      "cos_bikini_halter" to "ビキニ（ホルターネック）",
      "cos_bikini_bandeau" to "ビキニ（バンドゥ）",
    ),
  )

private fun optionPart58(): CharacterOptionPart =
  CharacterOptionPart(
    "コスプレの小物",
    false,
    listOf(
      "prop_wig" to "ウィッグ",
      "prop_contacts" to "カラーコンタクト",
      "prop_wand" to "魔法のステッキ",
      "prop_sword" to "剣",
      "prop_staff" to "杖（武器）",
      "prop_umbrella" to "和傘",
      "prop_fan" to "扇子",
      "prop_mask" to "仮面",
    ),
  )

private fun optionGenre8(): CharacterOptionGenre =
  CharacterOptionGenre(
    "衣装・コスプレ",
    listOf(
    optionPart57(),
    optionPart58(),
    ),
  )

private fun optionPart59(): CharacterOptionPart =
  CharacterOptionPart(
    "系統",
    true,
    listOf(
      "style_gyaru" to "ギャル",
      "style_natural" to "ナチュラルメイク",
      "style_no_makeup" to "すっぴん風",
      "style_gothic" to "ゴスメイク",
      "style_idol" to "アイドル風",
      "style_glam" to "派手なメイク",
    ),
  )

private fun optionPart60(): CharacterOptionPart =
  CharacterOptionPart(
    "メイクの部品",
    false,
    listOf(
      "glitter_eyeshadow" to "ラメのアイシャドウ",
      "red_lipstick" to "赤い口紅",
      "glossy_lips" to "つやのある唇",
      "pink_blush" to "濃いチーク",
      "false_lashes" to "つけまつ毛",
      "winged_liner" to "跳ねたアイライン",
      "crystal_nails" to "ジェルネイル",
      "french_nails" to "フレンチネイル",
    ),
  )

private fun optionPart61(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の質感（実写向け）",
    false,
    listOf(
      "skin_texture" to "自然な肌の質感",
      "skin_blemish" to "肌の小さな凹凸",
      "shiny_skin" to "つやのある肌",
      "sweat_skin" to "汗ばむ肌",
    ),
  )

private fun optionGenre9(): CharacterOptionGenre =
  CharacterOptionGenre(
    "メイク・実写の質感",
    listOf(
    optionPart59(),
    optionPart60(),
    optionPart61(),
    ),
  )

private fun optionPart62(): CharacterOptionPart =
  CharacterOptionPart(
    "靴",
    true,
    listOf(
      "sh_pumps" to "パンプス",
      "sh_stiletto" to "ピンヒール",
      "sh_platform" to "厚底ヒール",
      "sh_strappy" to "ストラップヒール",
      "sh_boots_short" to "ショートブーツ",
      "sh_boots_long" to "ロングブーツ",
      "sh_boots_thigh" to "サイハイブーツ",
      "sh_boots_lace" to "編み上げブーツ",
      "sh_sneakers" to "スニーカー",
      "sh_loafers" to "ローファー",
      "sh_mary_jane" to "メリージェーン",
      "sh_sandals" to "サンダル",
      "sh_mules" to "ミュール",
      "sh_flats" to "バレエシューズ",
      "sh_geta" to "下駄・草履",
      "sh_slippers" to "スリッパ",
      "sh_patent" to "エナメルの靴",
      "sh_barefoot" to "はだし",
      "sh_toe_ring" to "素足にトゥリング",
    ),
  )

private fun optionPart63(): CharacterOptionPart =
  CharacterOptionPart(
    "ソックス",
    true,
    listOf(
      "sk_ankle" to "くるぶしソックス",
      "sk_crew" to "ふつうの丈のソックス",
      "sk_high" to "ハイソックス",
      "sk_otk" to "ニーソックス（膝上）",
      "sk_black" to "黒いソックス",
      "sk_white" to "白いソックス",
      "sk_striped" to "しま柄ニーソ",
      "sk_ribbon" to "リボン付きソックス",
      "sk_lace" to "レースのソックス",
      "sk_tabi" to "足袋",
      "sk_toe" to "五本指ソックス",
      "sk_mismatch" to "左右ちがうソックス",
      "sk_single" to "片足だけニーソ",
      "sk_fluffy" to "もこもこ靴下",
      "sk_leg_warmer" to "レッグウォーマー",
    ),
  )

private fun optionPart64(): CharacterOptionPart =
  CharacterOptionPart(
    "ストッキング・タイツ",
    true,
    listOf(
      "st_black" to "黒ストッキング",
      "st_nude" to "肌色ストッキング",
      "st_sheer" to "薄く透けるストッキング",
      "st_fishnet" to "網タイツ",
      "st_garter" to "ガーターストッキング",
      "st_garter_belt" to "ガーターベルト",
      "st_seam" to "バックシーム（縫い目）ストッキング",
      "st_lace_top" to "レースのトップのサイハイ",
      "st_torn" to "伝線・破れたストッキング",
      "st_open_toe" to "つま先なしストッキング",
      "st_crotchless" to "股の開いたストッキング",
      "st_tights_thick" to "厚手のタイツ",
      "st_tights_color" to "色つきタイツ",
      "st_glitter" to "ラメ入りストッキング",
      "st_bodystocking" to "ボディストッキング",
      "st_wet" to "濡れて透けるストッキング",
    ),
  )

private fun optionPart65(): CharacterOptionPart =
  CharacterOptionPart(
    "レギンス・スパッツ",
    true,
    listOf(
      "lg_leggings" to "レギンス",
      "lg_yoga" to "ヨガパンツ",
      "lg_spats" to "スパッツ",
      "lg_leather" to "レザーレギンス",
      "lg_latex" to "ラテックスのレギンス",
      "lg_sheer" to "透けるレギンス",
      "lg_cropped" to "七分丈レギンス",
      "lg_printed" to "柄入りレギンス",
      "lg_ripped" to "破れ加工のレギンス",
      "lg_tight_jeans" to "ぴったりしたジーンズ",
    ),
  )

private fun optionPart66(): CharacterOptionPart =
  CharacterOptionPart(
    "足元の仕上げ",
    false,
    listOf(
      "ft_ankle_ribbon" to "足首のリボン",
      "ft_pedicure" to "ペディキュア",
      "ft_pedicure_red" to "赤いペディキュア",
      "ft_foot_jewelry" to "足の甲のアクセサリー",
      "ft_dirty_feet" to "汚れた素足",
      "ft_arch" to "足の甲を見せる",
    ),
  )

private fun optionGenre10(): CharacterOptionGenre =
  CharacterOptionGenre(
    "足元・レッグウェア",
    listOf(
    optionPart62(),
    optionPart63(),
    optionPart64(),
    optionPart65(),
    optionPart66(),
    ),
  )

private fun optionPart67(): CharacterOptionPart =
  CharacterOptionPart(
    "帽子・ヘッドウェア",
    true,
    listOf(
      "ht_cap" to "キャップ",
      "ht_straw" to "麦わら帽子",
      "ht_sun" to "つば広の帽子",
      "ht_top" to "シルクハット",
      "ht_nurse" to "ナースキャップ",
      "ht_police" to "警官帽",
      "ht_hood" to "フード",
      "ht_veil" to "ベール付きの帽子",
      "ht_cat" to "猫耳フード",
    ),
  )

private fun optionPart68(): CharacterOptionPart =
  CharacterOptionPart(
    "トップス",
    true,
    listOf(
      "tp_tshirt" to "Tシャツ",
      "tp_oversize" to "だぼっとした大きめTシャツ",
      "tp_blouse" to "ブラウス",
      "tp_shirt" to "ワイシャツ",
      "tp_camisole" to "キャミソール",
      "tp_tank" to "タンクトップ",
      "tp_tube" to "チューブトップ",
      "tp_crop" to "クロップトップ（へそ出し）",
      "tp_off_shoulder" to "オフショルダー",
      "tp_halter" to "ホルターネック",
      "tp_knit" to "ニット・セーター",
      "tp_open_back_knit" to "背中の開いたニット",
      "tp_hoodie" to "パーカー",
      "tp_cardigan" to "カーディガン",
      "tp_jacket" to "ジャケット",
      "tp_leather_jacket" to "レザージャケット",
      "tp_bolero" to "ボレロ",
      "tp_lace" to "レースのトップス",
      "tp_wet_shirt" to "濡れて透けたシャツ",
      "tp_unbuttoned" to "ボタンを外したシャツ",
      "tp_bustier" to "ビスチェ",
      "tp_apron_only" to "裸エプロン",
    ),
  )

private fun optionPart69(): CharacterOptionPart =
  CharacterOptionPart(
    "ボトムス",
    true,
    listOf(
      "bt_mini" to "ミニスカート",
      "bt_micro" to "マイクロミニ",
      "bt_flare" to "フレアスカート",
      "bt_tight" to "タイトスカート",
      "bt_long" to "ロングスカート",
      "bt_slit" to "スリット入りスカート",
      "bt_leather" to "レザースカート",
      "bt_shorts" to "ショートパンツ",
      "bt_hot" to "ホットパンツ",
      "bt_jeans" to "ジーンズ",
      "bt_leather_pants" to "レザーパンツ",
      "bt_hakama" to "袴",
      "bt_lifted" to "スカートをたくし上げる",
      "bt_no_bottom" to "下を履いていない（シャツのみ）",
    ),
  )

private fun optionPart70(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピース・ドレス",
    true,
    listOf(
      "ov_sundress" to "サンドレス",
      "ov_shirt_dress" to "シャツワンピ",
      "ov_evening" to "イブニングドレス",
      "ov_cocktail" to "カクテルドレス",
      "ov_bodycon" to "ボディコンワンピ",
      "ov_slip" to "スリップドレス",
      "ov_backless" to "背中の開いたドレス",
      "ov_sideless" to "サイドが大きく開いたドレス",
      "ov_wedding" to "ウェディングドレス",
      "ov_kimono_short" to "ミニ丈の着物",
      "ov_sweater_dress" to "ニットワンピ",
      "ov_latex" to "ラテックスのドレス",
    ),
  )

private fun optionPart71(): CharacterOptionPart =
  CharacterOptionPart(
    "水着",
    true,
    listOf(
      "sw_string" to "紐のビキニ",
      "sw_micro" to "マイクロビキニ",
      "sw_sling" to "スリングショット",
      "sw_highleg" to "ハイレグ水着",
      "sw_onepiece" to "ワンピース水着",
      "sw_monokini" to "モノキニ",
      "sw_pareo" to "パレオ付き水着",
      "sw_rash" to "ラッシュガード",
      "sw_frill" to "フリルの水着",
      "sw_wet" to "濡れた水着",
      "sw_untied" to "ほどけかけの水着",
    ),
  )

private fun optionPart72(): CharacterOptionPart =
  CharacterOptionPart(
    "ブラ",
    true,
    listOf(
      "br_lace" to "レースのブラ",
      "br_halfcup" to "ハーフカップブラ",
      "br_push_up" to "ぐっと寄せるブラ",
      "br_wireless" to "ノンワイヤーブラ",
      "br_sports" to "スポーツブラ",
      "br_front_open" to "フロントホックのブラ",
      "br_open_cup" to "カップのない（オープン）ブラ",
      "br_sheer" to "透けるブラ",
      "br_pasties" to "ニップレス",
      "br_none" to "ブラなし",
      "br_black" to "黒いブラ",
      "br_white" to "白いブラ",
      "br_red" to "赤いブラ",
      "br_strapless" to "ストラップなしブラ",
    ),
  )

private fun optionPart73(): CharacterOptionPart =
  CharacterOptionPart(
    "パンツ（下着）",
    true,
    listOf(
      "pn_lace" to "レースのショーツ",
      "pn_thong" to "Tバック",
      "pn_string" to "紐パン",
      "pn_highleg" to "ハイレグショーツ",
      "pn_boyshort" to "ボーイレッグショーツ",
      "pn_seamless" to "シームレスショーツ（縫い目なし）",
      "pn_sheer" to "透けるショーツ",
      "pn_open" to "股の開いたショーツ",
      "pn_white" to "白いショーツ",
      "pn_black" to "黒いショーツ",
      "pn_pink" to "ピンクのショーツ",
      "pn_pull_aside" to "ずらしたショーツ",
      "pn_pulled_down" to "膝までおろしたショーツ",
      "pn_wet" to "濡れたショーツ",
      "pn_none" to "履いていない",
    ),
  )

private fun optionPart74(): CharacterOptionPart =
  CharacterOptionPart(
    "下着セット・ボディ",
    true,
    listOf(
      "ls_teddy" to "テディ（ワンピース下着）",
      "ls_babydoll" to "ベビードール",
      "ls_negligee" to "ネグリジェ",
      "ls_corset" to "コルセット",
      "ls_garter_set" to "ブラ・ガーターベルト・ストッキングのセット",
      "ls_bodysuit" to "ボディスーツ",
      "ls_sheer_robe" to "透けるガウン",
      "ls_towel" to "バスタオル1枚",
      "ls_kitchen_apron" to "エプロンだけ",
    ),
  )

private fun optionGenre11(): CharacterOptionGenre =
  CharacterOptionGenre(
    "衣装の部位別（カスタムメイド式）",
    listOf(
    optionPart67(),
    optionPart68(),
    optionPart69(),
    optionPart70(),
    optionPart71(),
    optionPart72(),
    optionPart73(),
    optionPart74(),
    ),
  )

private fun optionPart75(): CharacterOptionPart =
  CharacterOptionPart(
    "チーク",
    true,
    listOf(
      "mk_blush_pink" to "ピンクのチーク",
      "mk_blush_peach" to "ピーチのチーク",
      "mk_blush_red" to "頬の赤み（照れ）",
      "mk_blush_nose" to "鼻まで広がる赤み",
      "mk_blush_heavy" to "ほてった赤み",
    ),
  )

private fun optionPart76(): CharacterOptionPart =
  CharacterOptionPart(
    "リップ",
    true,
    listOf(
      "mk_lip_red" to "赤いリップ",
      "mk_lip_pink" to "ピンクのリップ",
      "mk_lip_nude" to "ヌードベージュのリップ",
      "mk_lip_dark" to "ダークカラーのリップ",
      "mk_lip_black" to "黒いリップ",
      "mk_lip_gloss" to "グロスたっぷり",
      "mk_lip_smudged" to "口紅がにじむ",
    ),
  )

private fun optionPart77(): CharacterOptionPart =
  CharacterOptionPart(
    "アイシャドウ",
    true,
    listOf(
      "mk_eye_pink" to "ピンクのアイシャドウ",
      "mk_eye_brown" to "ブラウンのアイシャドウ",
      "mk_eye_smoky" to "スモーキーアイ",
      "mk_eye_blue" to "ブルーのアイシャドウ",
      "mk_eye_purple" to "パープルのアイシャドウ",
      "mk_eye_gold" to "ゴールドのアイシャドウ",
      "mk_eye_red" to "赤いアイシャドウ（目尻）",
      "mk_eye_glitter" to "ラメの目元",
    ),
  )

private fun optionPart78(): CharacterOptionPart =
  CharacterOptionPart(
    "アイライン・まつ毛",
    true,
    listOf(
      "mk_liner_thick" to "太いアイライン",
      "mk_liner_under" to "下まぶたのライン",
      "mk_lash_long" to "長くカールしたまつ毛",
      "mk_lash_wet" to "涙で濡れたまつ毛",
      "mk_mascara_run" to "にじんだマスカラ",
    ),
  )

private fun optionPart79(): CharacterOptionPart =
  CharacterOptionPart(
    "ほくろ・そばかす",
    false,
    listOf(
      "mk_mole_cheek" to "頬のほくろ",
      "mk_mole_breast" to "胸のほくろ",
      "mk_mole_thigh" to "太もものほくろ",
      "mk_beauty_mark" to "セクシーなほくろ",
    ),
  )

private fun optionPart80(): CharacterOptionPart =
  CharacterOptionPart(
    "ネイル",
    true,
    listOf(
      "mk_nail_red" to "赤いネイル",
      "mk_nail_pink" to "ピンクのネイル",
      "mk_nail_black" to "黒いネイル",
      "mk_nail_white" to "白いネイル",
      "mk_nail_long" to "長い爪",
      "mk_nail_art" to "ネイルアート",
      "mk_nail_short" to "短く切った爪",
    ),
  )

private fun optionPart81(): CharacterOptionPart =
  CharacterOptionPart(
    "ボディメイク",
    false,
    listOf(
      "mk_body_glitter" to "ボディグリッター",
      "mk_body_paint" to "ボディペイント",
      "mk_body_oil" to "ボディオイルのつや",
      "mk_body_highlight" to "鎖骨と肩のハイライト",
      "mk_body_blush" to "全身の赤み",
      "mk_body_sparkle_tan" to "日焼け肌にラメ",
    ),
  )

private fun optionGenre12(): CharacterOptionGenre =
  CharacterOptionGenre(
    "メイク（細かく）",
    listOf(
    optionPart75(),
    optionPart76(),
    optionPart77(),
    optionPart78(),
    optionPart79(),
    optionPart80(),
    optionPart81(),
    ),
  )

private fun optionPart82(): CharacterOptionPart =
  CharacterOptionPart(
    "表情（アダルト）",
    true,
    listOf(
      "ex_ahegao" to "アヘ顔",
      "ex_melted" to "とろけ顔",
      "ex_flushed" to "上気した顔",
      "ex_pleasure" to "快感にゆがむ顔",
      "ex_climax" to "絶頂の顔",
      "ex_hold_back" to "声をこらえる顔",
      "ex_crying" to "涙ぐむ顔",
      "ex_seductive" to "誘う目つき",
      "ex_contempt" to "見下す目つき",
      "ex_shy_lewd" to "恥ずかしがりながら見つめる",
      "ex_dazed" to "ぼうっとした目",
      "ex_fainted" to "気絶・失神（目を閉じてぐったり）",
      "ex_drunk" to "酔ってとろんとした顔",
      "ex_in_heat" to "発情した顔",
    ),
  )

private fun optionPart83(): CharacterOptionPart =
  CharacterOptionPart(
    "表情の部品",
    false,
    listOf(
      "ex_tongue_out" to "舌を出す",
      "ex_drool" to "よだれ",
      "ex_sweat" to "汗",
      "ex_tears" to "涙",
      "ex_steam" to "荒い息（白い息・蒸気）",
      "ex_heart_eyes" to "ハートの目",
      "ex_white_eyes" to "白目をむく",
      "ex_open_mouth" to "口を開ける",
      "ex_cross_eyed" to "寄り目",
      "ex_clenched_teeth" to "歯を食いしばる",
      "ex_hand_mouth" to "手で口をふさぐ",
      "ex_blush_ears" to "耳まで赤い",
      "ex_trembling" to "ふるえる",
      "ex_goosebumps" to "鳥肌",
    ),
  )

private fun optionPart84(): CharacterOptionPart =
  CharacterOptionPart(
    "体の状態（アダルト）",
    false,
    listOf(
      "ex_aphrodisiac" to "媚薬で発情",
      "ex_wet_skin" to "汗でぬれた肌",
      "ex_afterglow" to "余韻（ぐったり）",
      "ex_messy_hair" to "乱れた髪",
      "ex_messy_clothes" to "乱れた服",
      "ex_red_marks" to "赤い跡（叩かれた・縛られた跡）",
      "ex_kiss_marks" to "キスマーク",
      "ex_leaking" to "あふれた体液",
    ),
  )

private fun optionGenre13(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アダルトの表情・状態",
    listOf(
    optionPart82(),
    optionPart83(),
    optionPart84(),
    ),
  )

private fun optionPart85(): CharacterOptionPart =
  CharacterOptionPart(
    "拘束具",
    false,
    listOf(
      "bd_handcuffs" to "手錠",
      "bd_leg_cuffs" to "足枷",
      "bd_leather_cuffs" to "革の拘束バンド（手首・足首）",
      "bd_rope" to "縄（緊縛）",
      "bd_kikko" to "亀甲縛り",
      "bd_behind_back" to "後ろ手に縛る",
      "bd_spread" to "開脚拘束",
      "bd_suspension" to "宙吊り",
      "bd_x_cross" to "X字の磔台",
      "bd_pillory" to "首と手首を固定する板",
      "bd_chair" to "拘束椅子",
      "bd_armbinder" to "アームバインダー",
      "bd_straitjacket" to "拘束衣",
      "bd_tape" to "ボンデージテープ",
      "bd_harness" to "ボディハーネス",
      "bd_chain" to "鎖",
      "bd_ball_gag" to "ボールギャグ（口枷）",
      "bd_ring_gag" to "開口器",
      "bd_tape_gag" to "口にテープ",
      "bd_hood" to "ラバーフード・マスク",
      "bd_wooden_horse" to "三角木馬",
    ),
  )

private fun optionPartBondageOutfit(): CharacterOptionPart =
  CharacterOptionPart(
    "ボンテージ衣装",
    false,
    listOf(
      "bo_leather_open_bust" to "レザーボンテージ（胸が出るコルセット）",
      "bo_overbust_lace" to "編み上げオーバーバストコルセット",
      "bo_mesh_corset_tback" to "メッシュコルセットとTバック",
      "bo_enamel_frill_corset" to "赤黒エナメルのフリルコルセット",
      "bo_leather_corset_fishnet" to "レザーコルセットと網タイツ",
      "bo_collar_leash_lingerie" to "首枷とリード付きランジェリー",
      "bo_m_emblem" to "「M」の刻印入りボンテージ",
      "bo_chain_harness" to "チェーン付き拘束ハーネス",
      "bo_leather_harness_hook" to "本革ハーネス（金属フック付き）",
      "bo_slave_body_belt" to "スレイブ・ボディベルト",
      "bo_enamel_dress" to "エナメルワンピース",
      "bo_mini_two_piece" to "ミニスカツーピース（5連バンド）",
      "bo_bustier_skirt" to "スカート付きコルセットとTバック",
      "bo_queen_dress" to "エナメル女王様ドレス（スリット・手袋）",
      "bo_glossy_corset_skirt" to "エナメルグロッシーコルセット",
      "bo_catsuit_zip" to "前後ジッパーのキャットスーツ",
      "bo_catsuit_spandex" to "スパンデックスのキャットスーツ",
      "bo_latex_catsuit_mask" to "ラテックスキャットスーツ（全頭マスク）",
      "bo_catsuit_red_enamel" to "赤いエナメルのハイネックキャットスーツ",
      "bo_catsuit_leather_zips" to "合皮キャットスーツ（腕・脚にジップ）",
      "bo_jumpsuit_belts" to "レザー調ジャンプスーツ（ベルト付き）",
      "bo_bodystocking_hood" to "全頭マスク一体のタイツ",
      "bo_restraint_suit" to "四肢を拘束する拘束衣コス",
    ),
  )

private fun optionPart86(): CharacterOptionPart =
  CharacterOptionPart(
    "責め・SMの道具",
    false,
    listOf(
      "sm_whip" to "鞭",
      "sm_crop" to "乗馬鞭",
      "sm_paddle" to "パドル",
      "sm_candle" to "低温ろうそく",
      "sm_feather" to "羽根",
      "sm_clamps" to "乳首クリップ",
      "sm_ice" to "氷",
      "sm_dominatrix" to "女王様の衣装（レザー）",
      "sm_latex" to "ラテックス・ゴムの衣装",
      "sm_mask" to "ボンデージマスク",
      "sm_spiked" to "スパイクの首輪・ベルト",
    ),
  )

private fun optionPart87(): CharacterOptionPart =
  CharacterOptionPart(
    "グッズ",
    false,
    listOf(
      "gd_vibe" to "バイブ",
      "gd_wand" to "電マ",
      "gd_rotor" to "ローター（リモコン）",
      "gd_dildo" to "ディルド",
      "gd_plug" to "アナルプラグ",
      "gd_tail_plug" to "しっぽ付きプラグ",
      "gd_chastity" to "貞操帯",
      "gd_beads" to "アナルビーズ",
      "gd_lotion" to "ローション・オイル",
      "gd_aphrodisiac" to "媚薬のびん・薬",
      "gd_camera" to "撮影される（カメラ・スマホ）",
      "gd_mirror" to "鏡の前",
      "gd_cuffs_bed" to "ベッドに固定する枷",
    ),
  )

private fun optionGenre14(): CharacterOptionGenre =
  CharacterOptionGenre(
    "拘束・SM・グッズ（成人向け）",
    listOf(
    optionPart85(),
    optionPartBondageOutfit(),
    optionPart86(),
    optionPart87(),
    ),
  )

private fun optionPart88(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方",
    true,
    listOf(
      "hs_high_pony" to "高い位置のポニーテール",
      "hs_low_pony" to "低い位置のポニーテール",
      "hs_low_twin" to "低めのツインテール",
      "hs_odango" to "お団子ヘア",
      "hs_messy_bun" to "ゆるいお団子",
      "hs_braid_crown" to "編み込みのカチューシャ風",
      "hs_french_braid" to "フレンチ編み",
      "hs_fishtail" to "フィッシュボーン編み",
      "hs_single_braid" to "三つ編み（1本）",
      "hs_twin_braids" to "三つ編み（2本）",
      "hs_side_braid" to "サイドの編み込み",
      "hs_ribbon_bundle" to "リボンで束ねる",
      "hs_scrunchie" to "シュシュで束ねる",
    ),
  )

private fun optionPart89(): CharacterOptionPart =
  CharacterOptionPart(
    "カット・形",
    true,
    listOf(
      "hs_pixie" to "ピクシーカット",
      "hs_wolf" to "ウルフカット",
      "hs_undercut" to "刈り上げ・アンダーカット",
      "hs_layered" to "レイヤーカット",
      "hs_straight_long" to "ストレートのロング",
      "hs_wavy_long" to "ゆるふわウェーブのロング",
      "hs_curly" to "くるくるの巻き髪",
      "hs_afro" to "アフロ",
      "hs_dreads" to "ドレッドヘア",
      "hs_cornrow" to "コーンロウ",
      "hs_bald" to "丸刈り・スキンヘッド",
    ),
  )

private fun optionPart90(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪・横髪",
    false,
    listOf(
      "hs_curtain" to "センターパート（カーテンバング）",
      "hs_antenna" to "触角のような横髪",
      "hs_sidelocks" to "長い横髪",
      "hs_bangs_clip" to "前髪をピンでとめる",
    ),
  )

private fun optionGenre15(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪型・アレンジ（追加）",
    listOf(
    optionPart88(),
    optionPart89(),
    optionPart90(),
    ),
  )

private fun optionPart91(): CharacterOptionPart =
  CharacterOptionPart(
    "和・民族衣装",
    true,
    listOf(
      "co_furisode" to "振袖",
      "co_kimono_obi" to "帯をしめた着物",
      "co_haori" to "羽織り",
      "co_miko" to "巫女服",
      "co_hanfu" to "漢服",
      "co_hanbok" to "韓服（チマチョゴリ）",
      "co_ao_dai" to "アオザイ",
      "co_sari" to "サリー",
      "co_dirndl" to "ディアンドル",
      "co_kilt" to "キルト",
      "co_belly_dance" to "ベリーダンスの衣装",
      "co_kebaya" to "ケバヤ",
    ),
  )

private fun optionPart92(): CharacterOptionPart =
  CharacterOptionPart(
    "ゴス・パンク・系統",
    true,
    listOf(
      "co_gothic_lolita" to "ゴシックロリータ",
      "co_sweet_lolita" to "甘ロリ",
      "co_punk" to "パンクファッション",
      "co_goth" to "ゴスファッション",
      "co_visual" to "ヴィジュアル系",
      "co_street" to "ストリート系",
      "co_girly" to "ガーリーな服",
      "co_mode" to "モード系",
      "co_techwear" to "テックウェア",
      "co_steampunk" to "スチームパンク",
    ),
  )

private fun optionPart93(): CharacterOptionPart =
  CharacterOptionPart(
    "職業の服（大人）",
    true,
    listOf(
      "co_police" to "警察官",
      "co_doctor" to "医師（白衣）",
      "co_nurse2" to "看護師",
      "co_teacher" to "女教師（スーツ・眼鏡）",
      "co_secretary" to "秘書",
      "co_cafe_maid" to "カフェのエプロン",
      "co_bartender" to "バーテンダー",
      "co_bunny" to "バニーガール",
      "co_race_queen" to "レースクイーン",
      "co_cheer" to "チアリーダー",
      "co_idol_stage" to "アイドルのステージ衣装",
      "co_band" to "バンドの衣装",
      "co_chef" to "コック服",
      "co_pilot" to "パイロット",
      "co_firefighter" to "消防士",
      "co_lab_coat" to "研究者の白衣",
      "co_yoga" to "ヨガウェア",
      "co_tennis" to "テニスウェア",
      "co_gym" to "ジムウェア",
    ),
  )

private fun optionPart94(): CharacterOptionPart =
  CharacterOptionPart(
    "ファンタジー・SFの服",
    true,
    listOf(
      "co_mage_robe" to "魔導士のローブ",
      "co_witch" to "魔女の服",
      "co_knight_armor" to "騎士の鎧",
      "co_bikini_armor" to "ビキニアーマー",
      "co_elf" to "エルフの服",
      "co_priestess" to "聖職者（修道服）",
      "co_assassin" to "暗殺者の装束",
      "co_pirate" to "海賊",
      "co_cyber_suit" to "サイバーパンクのスーツ",
      "co_space_suit" to "宇宙服",
      "co_mecha_pilot" to "パイロットスーツ（SF）",
      "co_angel" to "天使の衣装",
      "co_vampire" to "吸血鬼の衣装",
      "co_royal" to "王女のドレス",
      "co_ballgown" to "舞踏会のドレス",
    ),
  )

private fun optionPart95(): CharacterOptionPart =
  CharacterOptionPart(
    "コスプレ・系統（追加）",
    true,
    listOf(
      "c2_saint" to "聖女",
      "c2_priest" to "僧侶（回復役）",
      "c2_judo" to "柔道着",
      "c2_kendo" to "剣道着",
      "c2_oni" to "鬼の衣装（虎柄）",
      "c2_imp" to "小悪魔",
      "c2_samurai" to "侍",
      "c2_valkyrie" to "戦姫（ヴァルキュリア）",
      "c2_bus_guide" to "バスガイド",
      "c2_cabaret" to "キャバ嬢",
      "c2_otaku" to "ヲタク風",
      "c2_magical" to "魔法少女風（大人のコスプレ）",
      "c2_pita" to "ピタコス（ぴったりスーツ）",
      "c2_young_lady" to "ヤングレディ（きれいめ）",
      "c2_ulzzang" to "オルチャン系（韓国風）",
      "c2_jirai" to "地雷ゴシック",
      "c2_punk_jirai" to "パンク地雷",
      "c2_subjira" to "サブジラ系（サブカル×地雷）",
      "c2_succubus" to "サキュバスの衣装",
      "c2_bondage_outfit" to "ボンテージ衣装",
      "c2_gyaru" to "ギャルファッション",
      "c2_catgirl" to "猫系（猫耳・鈴）",
      "c2_hypno" to "催眠術師風",
      "c2_anime_cos" to "アニメキャラのコスプレ（作品名＋キャラ名を下の入力欄に）",
    ),
  )

private fun optionPart96(): CharacterOptionPart =
  CharacterOptionPart(
    "小物（衣装の飾り）",
    false,
    listOf(
      "c2_pompoms" to "ボンボン",
      "c2_bowtie" to "蝶ネクタイ",
      "c2_body_chain" to "ボディチェーン",
      "c2_belly_chain" to "ベリーチェーン",
      "c2_harness_chain" to "ハーネスチェーン",
      "c2_long_necklace" to "ロングネックレス・チェストチェーン",
      "c2_charm_clip" to "チャーム付きクリップジュエリー",
      "c2_leather_choker" to "レザーチョーカーと連結用の金属パーツ",
      "c2_mekakure" to "メカクレ（目が隠れる髪）",
      "c2_dwarf" to "ドワーフ（頑丈な体つき・三つ編みひげ飾り）",
    ),
  )

private fun optionGenre16(): CharacterOptionGenre =
  CharacterOptionGenre(
    "衣装",
    listOf(
    optionPart91(),
    optionPart92(),
    optionPart93(),
    optionPart94(),
    optionPart95(),
    optionPart96(),
    ),
  )

private fun optionPart97(): CharacterOptionPart =
  CharacterOptionPart(
    "手に持つもの",
    false,
    listOf(
      "pr_umbrella" to "傘",
      "pr_wagasa" to "和傘",
      "pr_parasol" to "日傘",
      "pr_uchiwa" to "うちわ",
      "pr_katana" to "日本刀",
      "pr_staff" to "杖",
      "pr_bow" to "弓矢",
      "pr_gun" to "拳銃",
      "pr_book" to "本",
      "pr_phone" to "スマートフォン",
      "pr_camera" to "カメラ",
      "pr_mic" to "マイク",
      "pr_guitar" to "ギター",
      "pr_bouquet" to "花束",
      "pr_plush" to "ぬいぐるみ",
      "pr_coffee" to "コーヒーカップ",
      "pr_wine" to "ワイングラス",
      "pr_cigarette" to "たばこ",
      "pr_lollipop" to "棒つきキャンディ",
      "pr_icecream" to "アイスクリーム",
      "pr_balloon" to "風船",
      "pr_lantern" to "提灯・ランタン",
      "pr_flag" to "旗",
      "pr_tablet" to "タブレットとペン",
      "pr_game" to "ゲーム機",
    ),
  )

private fun optionPart98(): CharacterOptionPart =
  CharacterOptionPart(
    "身につける小物",
    false,
    listOf(
      "pr_belt" to "太いベルト",
      "pr_waist_chain" to "ウエストチェーン",
      "pr_cape" to "マント",
      "pr_backpack" to "リュック",
      "pr_shoulder_bag" to "ショルダーバッグ",
      "pr_handbag" to "ハンドバッグ",
      "pr_earmuffs" to "イヤーマフ",
      "pr_bandana" to "バンダナ",
      "pr_mask_fox" to "お面",
      "pr_wings_ornament" to "背中の飾りの翼",
      "pr_watch" to "腕時計（大きめ）",
      "pr_ribbon_neck" to "首のリボン",
      "pr_tie" to "ネクタイ",
      "pr_suspenders" to "サスペンダー",
      "pr_apron" to "エプロン",
    ),
  )

private fun optionGenre17(): CharacterOptionGenre =
  CharacterOptionGenre(
    "持ち物・小道具",
    listOf(
    optionPart97(),
    optionPart98(),
    ),
  )

private fun optionPart99(): CharacterOptionPart =
  CharacterOptionPart(
    "立ちポーズ",
    true,
    listOf(
      "ps_v_sign" to "ピースサイン",
      "ps_hands_hips" to "腰に手を当てる",
      "ps_arms_crossed" to "腕を組む",
      "ps_look_back" to "振り返る",
      "ps_hand_chest" to "胸に手を当てる",
      "ps_salute" to "敬礼",
      "ps_wave" to "手を振る",
      "ps_hug_self" to "自分を抱きしめる",
      "ps_lean_wall" to "壁にもたれる",
      "ps_hair_flip" to "髪をかき上げる",
      "ps_stretch" to "伸びをする",
      "ps_pose_model" to "モデル立ち",
      "ps_jump" to "ジャンプ",
      "ps_run" to "走る",
      "ps_dance" to "踊る",
      "ps_curtsy" to "スカートをつまんで礼",
      "ps_bow" to "おじぎ",
    ),
  )

private fun optionPart100(): CharacterOptionPart =
  CharacterOptionPart(
    "座る・寝る",
    true,
    listOf(
      "ps_seiza" to "正座",
      "ps_sit_floor" to "床に座る（ぺたん座り）",
      "ps_sit_chair" to "椅子に座る",
      "ps_sit_cross" to "あぐら",
      "ps_kneel" to "ひざまずく",
      "ps_crouch" to "しゃがむ",
      "ps_lie_back" to "あおむけに寝る",
      "ps_lie_side" to "横向きに寝る",
      "ps_lie_front" to "うつぶせに寝る",
      "ps_all_fours" to "四つん這い",
      "ps_knee_hug" to "ひざを抱える",
      "ps_legs_up" to "脚を上げる",
    ),
  )

private fun optionPart101(): CharacterOptionPart =
  CharacterOptionPart(
    "手・顔のしぐさ",
    false,
    listOf(
      "ps_cover_mouth" to "口を手で隠す",
      "ps_finger_lips" to "唇に指を当てる",
      "ps_chin_hand" to "あごに手",
      "ps_head_tilt" to "首をかしげる",
      "ps_blow_kiss" to "投げキッス",
      "ps_beckon" to "手招き",
      "ps_pointing" to "指さす",
      "ps_cheek_hand" to "頬に手を当てる",
      "ps_biting_finger" to "指をくわえる",
      "ps_holding_hands" to "手をつなぐ",
    ),
  )

private fun optionPart102(): CharacterOptionPart =
  CharacterOptionPart(
    "立ちポーズ（その2）",
    true,
    listOf(
      "h2_hand_on_hip" to "手を腰に当てて立つ",
      "h2_hands_on_hips" to "両手を腰に当てて立つ",
      "h2_contrapposto" to "片脚に体重をかけて立つ",
      "h2_crossed_arms" to "腕を組んで立つ",
      "h2_standing_on_one_leg" to "片脚立ち",
      "h2_legs_apart" to "脚を広げて立つ",
      "h2_crossed_legs" to "足を交差させて立つ",
      "h2_standing" to "胸を張って立つ",
      "h2_standing_2" to "背筋を伸ばして立つ",
      "h2_leaning_on_wall" to "壁にもたれて立つ",
      "h2_back_against_wall" to "壁に背をつけて立つ",
      "h2_arms_behind_back" to "手を後ろに回して立つ",
      "h2_own_hands_together" to "手を前で組んで立つ",
      "h2_outstretched_arm" to "片手を伸ばして立つ",
      "h2_spread_arms" to "両腕を広げて立つ",
      "h2_leaning_forward" to "前かがみで立つ",
      "h2_leaning_back" to "のけぞって立つ",
      "h2_hands_in_pockets" to "ポケットに手を入れて立つ",
      "h2_superhero_landing" to "スーパーヒーロー着地",
    ),
  )

private fun optionPart103(): CharacterOptionPart =
  CharacterOptionPart(
    "座りポーズ",
    true,
    listOf(
      "h2_wariza" to "横座り",
      "h2_knees_to_chest" to "抱え座り",
      "h2_sitting" to "脚を伸ばして座る",
      "h2_crossed_legs_2" to "脚を組んで座る",
      "h2_sitting_2" to "片膝を立てて座る",
      "h2_butterfly_sitting" to "蝶々座り",
      "h2_pigeon_pose" to "鳩のポーズ",
      "h2_sitting_on_chair" to "椅子に深く座る",
      "h2_sitting_backwards_on_chair" to "椅子に後ろ向きに座る",
      "h2_sitting_sideways_on_chair" to "椅子に横向きに座る",
      "h2_on_one_knee" to "片膝をつく",
      "h2_sitting_on_desk" to "机に座る",
      "h2_sitting_on_counter" to "カウンターに座る",
      "h2_sitting_on_couch" to "ソファに沈んで座る",
      "h2_sitting_on_bench" to "ベンチに座る",
      "h2_sitting_on_engawa" to "縁側に座る",
      "h2_sitting_on_stairs" to "階段に座る",
      "h2_head_on_knee" to "膝に頬杖をつく",
      "h2_head_rest" to "頬杖をつく",
      "h2_leaning_back_2" to "背もたれにのけぞる",
      "h2_sitting_on_lap" to "膝の上に座る",
    ),
  )

private fun optionPart104(): CharacterOptionPart =
  CharacterOptionPart(
    "寝そべりポーズ",
    true,
    listOf(
      "h2_on_back" to "仰向け",
      "h2_on_stomach" to "うつ伏せ",
      "h2_fetal_position" to "胎児のポーズ",
      "h2_legs_up" to "ベッドで脚を上げる",
      "h2_reading" to "ベッドで本を読む寝そべり",
      "h2_lying_on_couch" to "ソファで寝そべる",
      "h2_spread_arms_2" to "床で大の字",
      "h2_lying_on_stomach" to "胸を床につけて脚を曲げる",
      "h2_lying_on_side" to "頬杖をついて寝そべる",
      "h2_arm_pillow" to "腕を枕にして寝る",
      "h2_arms_up" to "腕を頭の上に伸ばして寝る",
      "h2_crossed_legs_3" to "脚を組んで寝そべる",
      "h2_knees_up" to "膝を立てて仰向け",
      "h2_lying_on_side_2" to "横寝で脚を重ねる",
      "h2_sitting_up" to "寝ぼけて起き上がる",
      "h2_rolling_on_floor" to "ごろんと転がる",
    ),
  )

private fun optionPart105(): CharacterOptionPart =
  CharacterOptionPart(
    "手の位置・手のしぐさ",
    false,
    listOf(
      "h2_hands_on_own_cheeks" to "両頬に手を当てる",
      "h2_hand_on_own_chin" to "あごに手を当てる",
      "h2_hand_to_mouth" to "口元に手を当てる",
      "h2_covering_mouth" to "口を手で覆う",
      "h2_finger_to_mouth" to "口元に人差し指",
      "h2_hand_in_hair" to "髪に手を通す",
      "h2_hand_on_own_neck" to "首筋に手を当てる",
      "h2_own_hands_clasped" to "胸の前で手を組む",
      "h2_praying" to "祈るように手を組む",
      "h2_own_hands_together_2" to "両手を合わせる",
      "h2_interlocked_fingers" to "指を絡める",
      "h2_arms_behind_head" to "腕を頭の後ろで組む",
      "h2_hand_behind_head" to "頭の後ろに手を回す",
      "h2_hands_on_head" to "頭の上で手を組む",
      "h2_arm_up" to "片手を挙げて挨拶",
      "h2_finger_heart" to "指でハートを作る",
      "h2_heart_hands" to "手でハートを作る",
      "h2_ok_sign" to "OKサイン",
      "h2_thumbs_up" to "サムズアップ",
      "h2_finger_gun" to "銃の形の指",
      "h2_paw_pose" to "招き猫のポーズ",
      "h2_palm_facing_viewer" to "手のひらを向ける",
      "h2_outstretched_hand" to "手を差し出す",
      "h2_reaching_toward_viewer" to "カメラへ手を伸ばす",
      "h2_biting_finger" to "指を噛む",
      "h2_licking_finger" to "指をなめる",
      "h2_looking_at_nails" to "爪を見る",
      "h2_showing_ring" to "指輪を見せる",
      "h2_back_of_hand" to "手の甲を見せる",
      "h2_hand_on_own_stomach" to "おなかに手を当てる",
      "h2_hand_on_own_thigh" to "太ももに手を置く",
      "h2_skirt_hold" to "スカートを軽く持つ",
      "h2_clothes_pull" to "服の裾を引っ張る",
      "h2_holding_clothes" to "服の裾をつかむ",
      "h2_sleeves_covering_mouth" to "袖を口元にあてる",
      "h2_covering_face_with_sleeves" to "袖で顔を隠す",
    ),
  )

private fun optionPart106(): CharacterOptionPart =
  CharacterOptionPart(
    "首・頭・視線",
    false,
    listOf(
      "h2_head_back" to "首を後ろにそらす",
      "h2_head_down" to "うつむく",
      "h2_head_up" to "顔を上げる",
      "h2_looking_to_the_side" to "横を向く",
      "h2_looking_back_2" to "肩越しに振り返る",
      "h2_looking_up" to "上目づかいで見る",
      "h2_looking_down_2" to "見下ろす視線",
      "h2_looking_afar" to "遠くを見る",
      "h2_averting_eyes" to "目をそらす",
      "h2_looking_around" to "視線を泳がせる",
      "h2_looking_at_mirror" to "鏡を見る",
      "h2_looking_at_own_hands" to "自分の手を見る",
      "h2_looking_at_phone" to "スマホを見る",
      "h2_eye_contact" to "目を合わせる",
      "h2_half_closed_eyes_3" to "半目で見る",
      "h2_looking_outside" to "窓の外を見る",
      "h2_leaning_on_window" to "窓にもたれて外を見る",
    ),
  )

private fun optionPart107(): CharacterOptionPart =
  CharacterOptionPart(
    "歩き・走り・動き",
    false,
    listOf(
      "h2_walking" to "歩く",
      "h2_walking_2" to "颯爽と歩く",
      "h2_walking_slowly" to "ゆっくり歩く",
      "h2_running_2" to "全力で走る",
      "h2_jumping" to "ジャンプする",
      "h2_hopping" to "跳ねる",
      "h2_spinning" to "くるっと回る",
      "h2_spinning_2" to "回転してスカートが広がる",
      "h2_running_towards_viewer" to "駆け寄る",
      "h2_walking_3" to "振り返りながら歩く",
      "h2_holding_hands" to "手をつないで歩く",
      "h2_walking_away" to "後ろ向きに歩き去る",
      "h2_walking_up_stairs" to "階段を上る",
      "h2_walking_down_stairs" to "階段を下りる",
      "h2_skipping" to "スキップ",
      "h2_falling" to "落ちる",
      "h2_tripping" to "つまずく",
      "h2_tying_shoes" to "かがんで靴ひもを結ぶ",
      "h2_bent_over" to "かがんで物を拾う",
    ),
  )

private fun optionPart108(): CharacterOptionPart =
  CharacterOptionPart(
    "ストレッチ・運動",
    false,
    listOf(
      "h2_forward_bend" to "前屈",
      "h2_split" to "開脚",
      "h2_side_stretch" to "体側を伸ばす",
      "h2_yoga" to "ヨガのポーズ",
      "h2_downward_dog_pose" to "ダウンドッグ",
      "h2_tree_pose" to "木のポーズ",
      "h2_warrior_pose" to "戦士のポーズ",
      "h2_body_bridge" to "ブリッジ",
      "h2_shoulder_stand" to "肩立ち",
      "h2_push_up" to "腕立て伏せ",
      "h2_sit_up" to "腹筋運動",
      "h2_squat_exercise" to "スクワット",
      "h2_lunge" to "ランジ",
      "h2_twisted_torso" to "腰をひねるストレッチ",
      "h2_jumping_rope" to "縄跳び",
      "h2_dancing" to "ダンスポーズ",
      "h2_ballet_pose" to "バレエのポーズ",
      "h2_flamenco_pose" to "フラメンコ風ポーズ",
      "h2_pole_dancing" to "ポールダンス風ポーズ",
    ),
  )

private fun optionPart109(): CharacterOptionPart =
  CharacterOptionPart(
    "日常動作（1）",
    false,
    listOf(
      "h2_yawning" to "あくびをする",
      "h2_sigh" to "ため息をつく",
      "h2_adjusting_hair_2" to "髪を整える",
      "h2_applying_makeup" to "化粧をする",
      "h2_applying_lipstick_2" to "口紅を塗る",
      "h2_looking_at_mirror_2" to "鏡を見ながら化粧直し",
      "h2_brushing_teeth" to "歯をみがく",
      "h2_washing_face" to "顔を洗う",
      "h2_drying_hair_2" to "タオルで髪を拭く",
      "h2_hair_dryer" to "ドライヤーで髪を乾かす",
      "h2_changing_clothes" to "服を着替える",
      "h2_putting_on_shoes" to "靴を履く",
      "h2_putting_on_socks" to "靴下を履く",
      "h2_putting_on_stockings" to "ストッキングを履く",
      "h2_buttoning" to "ボタンを留める",
      "h2_adjusting_necktie" to "ネクタイを直す",
      "h2_adjusting_skirt" to "スカートを直す",
      "h2_adjusting_eyewear" to "眼鏡を直す",
      "h2_using_phone" to "スマホを操作する",
      "h2_selfie" to "自撮りする",
      "h2_taking_picture" to "写真を撮る",
      "h2_reading_2" to "本を読む",
      "h2_reading_letter" to "手紙を読む",
      "h2_drinking_coffee" to "コーヒーを飲む",
      "h2_drinking_tea" to "お茶を飲む",
      "h2_eating" to "食べ物を口に運ぶ",
      "h2_cooking" to "料理をする",
      "h2_washing_dishes" to "洗い物をする",
      "h2_cleaning" to "掃除をする",
      "h2_folding_clothes" to "洗濯物をたたむ",
    ),
  )

private fun optionPart110(): CharacterOptionPart =
  CharacterOptionPart(
    "日常動作（2）",
    false,
    listOf(
      "h2_carrying" to "荷物を運ぶ",
      "h2_umbrella" to "傘をさす",
      "h2_opening_window" to "窓を開ける",
      "h2_opening_door" to "ドアを開ける",
      "h2_leaning_on_door" to "ドアにもたれる",
      "h2_making_bed" to "ベッドを整える",
      "h2_falling_onto_bed" to "ベッドに倒れ込む",
      "h2_relaxing_on_couch" to "ソファでくつろぐ",
      "h2_drinking_beer" to "ビールを飲む（成人）",
      "h2_drinking_wine" to "ワインを飲む",
      "h2_smoking" to "タバコを吸う（成人）",
    ),
  )

private fun optionPart111(): CharacterOptionPart =
  CharacterOptionPart(
    "寄り添う・ふたりの距離",
    false,
    listOf(
      "h2_shoulder_to_shoulder" to "肩を寄せ合う",
      "h2_back_to_back" to "背中合わせ",
      "h2_face_to_face" to "向かい合う",
      "h2_forehead_to_forehead" to "額をくっつける",
      "h2_cheek_to_cheek" to "頬をくっつける",
      "h2_interlocked_fingers_2" to "指を絡めて手をつなぐ",
      "h2_waist_hug" to "腰を抱く",
      "h2_hug_from_behind" to "後ろから抱きしめる",
      "h2_hug" to "抱きしめる",
      "h2_head_on_shoulder" to "肩に頭を預ける",
      "h2_lap_pillow" to "膝枕",
      "h2_headpat" to "頭をなでる",
      "h2_princess_carry" to "お姫様抱っこ",
      "h2_piggyback" to "おんぶ",
    ),
  )

private fun optionGenre18(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ポーズ・しぐさ",
    listOf(
    optionPart99(),
    optionPart100(),
    optionPart101(),
    optionPart102(),
    optionPart103(),
    optionPart104(),
    optionPart105(),
    optionPart106(),
    optionPart107(),
    optionPart108(),
    optionPart109(),
    optionPart110(),
    optionPart111(),
    ),
  )

private fun optionPart112(): CharacterOptionPart =
  CharacterOptionPart(
    "表情",
    true,
    listOf(
      "fx_smile" to "にっこり",
      "fx_laugh" to "大笑い",
      "fx_smirk" to "ふふんと笑う",
      "fx_grin" to "にやり",
      "fx_serious" to "真剣な顔",
      "fx_angry" to "怒った顔",
      "fx_sad" to "悲しい顔",
      "fx_cry" to "泣き顔",
      "fx_sleepy" to "眠そうな顔",
      "fx_surprised" to "驚いた顔",
      "fx_embarrassed" to "照れた顔",
      "fx_expressionless" to "無表情",
      "fx_wink" to "ウインク",
      "fx_tongue" to "あっかんべー",
      "fx_pout" to "ふくれっ面",
      "fx_dokya" to "どや顔",
      "fx_worried" to "心配そうな顔",
      "fx_scared" to "怯えた顔",
      "fx_kind" to "やさしい微笑み",
    ),
  )

private fun optionPart113(): CharacterOptionPart =
  CharacterOptionPart(
    "表情（アダルト・追加）",
    true,
    listOf(
      "xf_mesu" to "メス顔",
      "xf_nho" to "ンホ顔",
      "xf_oho" to "オホ顔",
      "xf_ohho_eyes" to "オッホアイ（上向きの目）",
      "xf_ahetoro" to "アヘトロ顔",
      "xf_gesu" to "ゲス顔",
      "xf_kiss_wait" to "キス待ち",
      "xf_hypno_eyes" to "催眠（ぐるぐる目）",
      "xf_heart_eyes" to "ハート目",
      "xf_lick_lips" to "舌なめずり",
      "xf_alluring" to "艶笑（艶めかしい笑み）",
      "xf_hollow" to "虚ろな目（ハイライトなし）",
      "xf_tongue_drool" to "舌出し（ヨダレあり）",
      "xf_tongue_dry" to "舌出し（ヨダレなし）",
      "xf_double_peace" to "ダブルピース",
      "xf_joy" to "喜び（満面の笑み）",
    ),
  )

private fun optionPart114(): CharacterOptionPart =
  CharacterOptionPart(
    "感情の表情",
    true,
    listOf(
      "xf_despair" to "絶望",
      "xf_pale" to "青ざめる",
      "xf_reluctant" to "嫌がる（弱・ためらい）",
      "xf_confused" to "戸惑う",
      "xf_shy_laugh" to "照れ笑い",
      "xf_ashamed" to "恥ずかしがる（顔を隠す）",
      "xf_sulk" to "不貞腐れる",
    ),
  )

private fun optionPart115(): CharacterOptionPart =
  CharacterOptionPart(
    "笑い・喜び",
    false,
    listOf(
      "g2_fx_smile_soft" to "やわらかい笑顔",
      "g2_fx_smile_big" to "満面の笑顔",
      "g2_fx_smile_closed" to "目を閉じた笑顔",
      "g2_fx_smile_tilt" to "首をかしげて笑う",
      "g2_fx_smile_teeth" to "歯を見せて笑う",
      "g2_fx_smile_sly" to "ずるい笑み",
      "g2_fx_smile_evil" to "悪い笑み",
      "g2_fx_smile_gentle" to "慈しむ笑み",
      "g2_fx_smile_sad" to "さみしげな笑み",
      "g2_fx_smile_awk" to "愛想笑い",
      "g2_fx_smile_nervous" to "引きつった笑い",
      "g2_fx_smile_confident" to "自信のある笑み",
      "g2_fx_smile_cat" to "ねこ口の笑み",
      "g2_fx_smile_wink" to "ウインクで笑う",
      "g2_fx_laugh_hand" to "口に手を当てて笑う",
      "g2_fx_laugh_out" to "声を上げて笑う",
      "g2_fx_laugh_tears" to "笑い泣き",
      "g2_fx_giggle" to "くすくす笑い",
      "g2_fx_chuckle" to "ふっと笑う",
      "g2_fx_happy_tears" to "うれし涙",
      "g2_fx_joy_jump" to "飛び跳ねて喜ぶ",
      "g2_fx_sparkle_eyes" to "目をきらきらさせる",
      "g2_fx_starry" to "星の形の瞳",
      "g2_fx_bright" to "ぱっと明るい顔",
      "g2_fx_content" to "満足した顔",
      "g2_fx_relief" to "ほっとした顔",
      "g2_fx_proud" to "誇らしげな顔",
    ),
  )

private fun optionPart116(): CharacterOptionPart =
  CharacterOptionPart(
    "泣き・悲しみ",
    false,
    listOf(
      "g2_fx_cry_quiet" to "声を殺して泣く",
      "g2_fx_cry_loud" to "大声で泣く",
      "g2_fx_cry_stream" to "滝のような涙",
      "g2_fx_cry_wail" to "わんわん泣き",
      "g2_fx_cry_sob" to "しゃくり上げる",
      "g2_fx_cry_eyes" to "うるうるの目",
      "g2_fx_cry_hold" to "涙をこらえる",
      "g2_fx_cry_lip" to "唇をかんで耐える",
      "g2_fx_cry_hand" to "手で顔をおおって泣く",
      "g2_fx_cry_smile" to "泣きながら笑う",
      "g2_fx_cry_wipe" to "涙をぬぐう",
      "g2_fx_sad_down" to "うつむく悲しみ",
      "g2_fx_sad_eyes" to "悲しげな目",
      "g2_fx_lonely" to "さみしそうな顔",
      "g2_fx_dejected" to "落ち込む顔",
      "g2_fx_despair2" to "がっくり",
      "g2_fx_hurt" to "傷ついた顔",
      "g2_fx_worried2" to "不安げな顔",
      "g2_fx_pleading" to "懇願する顔",
      "g2_fx_puppy" to "子犬のような目",
      "g2_fx_pout_cry" to "泣きべそ",
      "g2_fx_trembling_lip" to "唇がふるえる",
      "g2_fx_grief" to "悲嘆",
    ),
  )

private fun optionPart117(): CharacterOptionPart =
  CharacterOptionPart(
    "怒り・不満",
    false,
    listOf(
      "g2_fx_angry_big" to "激怒",
      "g2_fx_angry_vein" to "青筋を立てる",
      "g2_fx_angry_glare" to "にらむ",
      "g2_fx_angry_cold" to "冷たい怒り",
      "g2_fx_angry_teeth" to "歯をむく",
      "g2_fx_angry_pout" to "ぷんぷん",
      "g2_fx_angry_shout" to "叫ぶ",
      "g2_fx_irritated" to "いらだつ",
      "g2_fx_annoyed" to "うんざり",
      "g2_fx_scoff" to "鼻で笑う",
      "g2_fx_disgust" to "嫌悪",
      "g2_fx_disgust_look" to "軽蔑のまなざし",
      "g2_fx_eyeroll" to "あきれて目を回す",
      "g2_fx_sigh" to "ため息",
      "g2_fx_cheeks_puff" to "ほおをふくらませる",
      "g2_fx_sulk" to "すねる",
      "g2_fx_turn_away" to "ぷいっと横を向く",
      "g2_fx_jealous" to "やきもち",
      "g2_fx_grudge" to "うらみがましい目",
      "g2_fx_menace" to "威圧する",
    ),
  )

private fun optionPart118(): CharacterOptionPart =
  CharacterOptionPart(
    "驚き・戸惑い",
    false,
    listOf(
      "g2_fx_shock_big" to "大きな驚き",
      "g2_fx_shock_open" to "口をぽかんと開ける",
      "g2_fx_shock_tiny" to "目が点",
      "g2_fx_surprised_jump" to "びくっとする",
      "g2_fx_scare" to "おびえる",
      "g2_fx_horror" to "恐怖",
      "g2_fx_blank" to "頭が真っ白",
      "g2_fx_confused" to "きょとん",
      "g2_fx_puzzled" to "首をひねる",
      "g2_fx_doubt" to "疑いの目",
      "g2_fx_flustered" to "あたふた",
      "g2_fx_panic" to "パニック",
      "g2_fx_sweatdrop" to "冷や汗たらり",
      "g2_fx_gasp" to "はっと息をのむ",
      "g2_fx_wideeyes" to "目を見開く",
      "g2_fx_pupils_small" to "瞳が小さくなる",
    ),
  )

private fun optionPart119(): CharacterOptionPart =
  CharacterOptionPart(
    "恥じらい・誘惑・成人向け",
    false,
    listOf(
      "g2_fx_shy_look" to "上目づかいで恥じらう",
      "g2_fx_shy_turn" to "目をそらして恥じらう",
      "g2_fx_shy_cover" to "手で顔をかくす",
      "g2_fx_shy_peek" to "指のすき間からのぞく",
      "g2_fx_shy_cheek" to "頬を染める",
      "g2_fx_shy_full" to "真っ赤な顔",
      "g2_fx_shy_embar" to "はずかしさに涙目",
      "g2_fx_shy_smile" to "はにかむ",
      "g2_fx_shy_fidget" to "もじもじ",
      "g2_fx_seduce_eye" to "流し目",
      "g2_fx_seduce_half" to "半目で見つめる",
      "g2_fx_seduce_lip" to "唇をなめる",
      "g2_fx_seduce_bite" to "唇をかむ",
      "g2_fx_seduce_finger" to "指を唇に",
      "g2_fx_seduce_kiss" to "キスをせがむ顔",
      "g2_fx_seduce_breath" to "吐息",
      "g2_fx_seduce_smile" to "艶めいた微笑み",
      "g2_fx_seduce_beckon" to "誘うように手招き",
      "g2_fx_seduce_over" to "肩ごしに見る",
      "g2_fx_seduce_hair" to "髪をかき上げて誘う",
      "g2_fx_taunt" to "挑発する",
      "g2_fx_tease" to "からかう",
      "g2_fx_dom" to "見下ろす（支配的）",
      "g2_fx_sub" to "見上げる（従順）",
      "g2_fx_mesmer" to "とろんとした目",
      "g2_fx_ecstasy" to "恍惚",
      "g2_fx_ecstasy_tears" to "恍惚の涙",
      "g2_fx_ecstasy_open" to "恍惚で口が開く",
      "g2_fx_ecstasy_roll" to "目が上を向く",
      "g2_fx_ecstasy_shiver" to "身をふるわせる",
      "g2_fx_pleasure_sigh" to "甘い声",
      "g2_fx_pleasure_bite" to "声をがまんして歯をかむ",
      "g2_fx_pleasure_back" to "のけぞる",
      "g2_fx_pleasure_lost" to "快感で我を忘れる",
      "g2_fx_love" to "恋する目",
      "g2_fx_love_sigh" to "うっとり",
      "g2_fx_lust" to "欲情した目",
      "g2_fx_lust_stare" to "獲物を見る目",
      "g2_fx_yandere" to "ヤンデレの笑み",
      "g2_fx_devotion" to "うやうやしい目",
    ),
  )

private fun optionPart120(): CharacterOptionPart =
  CharacterOptionPart(
    "無表情・その他",
    false,
    listOf(
      "g2_fx_blank_face" to "無表情（真顔）",
      "g2_fx_deadpan" to "冷めた真顔",
      "g2_fx_stoic" to "凛とした顔",
      "g2_fx_bored" to "退屈そう",
      "g2_fx_sleepy" to "眠そうな目（半開き）",
      "g2_fx_closed" to "目を閉じる",
      "g2_fx_one_eye" to "片目をつぶる",
      "g2_fx_wink_l" to "左ウインク",
      "g2_fx_wink_r" to "右ウインク",
      "g2_fx_tongue" to "舌をちらり",
      "g2_fx_tongue_cute" to "てへぺろ",
      "g2_fx_tongue_lick" to "頬をなめる舌",
      "g2_fx_tongue_long" to "ぺろりと舌なめずり",
      "g2_fx_blow_cheek" to "頬をふくらませる",
      "g2_fx_pucker" to "唇をとがらせる",
      "g2_fx_whistle" to "口笛",
      "g2_fx_hum" to "鼻歌",
      "g2_fx_thinking" to "考え込む",
      "g2_fx_focus" to "集中する顔",
      "g2_fx_determ" to "決意の顔",
      "g2_fx_smirk_dark" to "不敵な笑み",
      "g2_fx_cool" to "クールな顔",
      "g2_fx_aloof" to "そっけない",
      "g2_fx_lookaway" to "そっぽを向く",
      "g2_fx_stare" to "じっと見つめる",
      "g2_fx_glance" to "ちらっと見る",
      "g2_fx_side_eye" to "横目でにらむ",
      "g2_fx_down_eye" to "伏し目",
      "g2_fx_lashes_down" to "まつげを伏せる",
      "g2_fx_brow_up" to "片眉を上げる",
      "g2_fx_brow_flat" to "眉がハの字",
      "g2_fx_brow_angry" to "眉がつり上がる",
      "g2_fx_wide_grin" to "にやにや",
      "g2_fx_goofy" to "間抜けな顔",
      "g2_fx_derp" to "おとぼけ顔",
      "g2_fx_silly" to "変顔",
      "g2_fx_comical" to "ギャグ顔",
      "g2_fx_chibi_face" to "ちびキャラ顔",
      "g2_fx_sparkle_bg" to "花が咲く背景の笑顔",
    ),
  )

private fun optionGenre19(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情",
    listOf(
    optionPart112(),
    optionPart113(),
    optionPart114(),
    optionPart115(),
    optionPart116(),
    optionPart117(),
    optionPart118(),
    optionPart119(),
    optionPart120(),
    ),
  )

private fun optionPart121(): CharacterOptionPart =
  CharacterOptionPart(
    "室内",
    true,
    listOf(
      "bg_bedroom" to "寝室",
      "bg_bathroom" to "浴室・バスルーム",
      "bg_onsen" to "温泉",
      "bg_living" to "リビング",
      "bg_kitchen" to "キッチン",
      "bg_library" to "図書館・書斎",
      "bg_cafe" to "カフェ",
      "bg_bar" to "バー",
      "bg_hotel" to "ホテルの部屋",
      "bg_office" to "オフィス",
      "bg_hospital" to "病室",
      "bg_dressing" to "更衣室・試着室",
      "bg_studio" to "撮影スタジオ",
      "bg_tatami" to "和室",
      "bg_castle_hall" to "城の広間",
      "bg_dungeon" to "地下室・牢屋",
    ),
  )

private fun optionPart122(): CharacterOptionPart =
  CharacterOptionPart(
    "屋外",
    true,
    listOf(
      "bg_beach" to "海辺",
      "bg_pool" to "プールサイド",
      "bg_forest" to "森",
      "bg_flower_field" to "花畑",
      "bg_street" to "街の通り",
      "bg_night_city" to "夜の街（ネオン）",
      "bg_shrine" to "神社",
      "bg_rooftop" to "屋上",
      "bg_mountain" to "山",
      "bg_snow" to "雪景色",
      "bg_festival" to "夏祭り",
      "bg_park" to "公園",
      "bg_station" to "駅のホーム",
      "bg_car" to "車内",
      "bg_fantasy_field" to "ファンタジーの草原",
      "bg_space" to "宇宙船の中",
      "bg_ruins" to "遺跡",
    ),
  )

private fun optionPart123(): CharacterOptionPart =
  CharacterOptionPart(
    "光・天気・雰囲気",
    false,
    listOf(
      "lt_golden" to "夕方の金色の光",
      "lt_moon" to "月明かり",
      "lt_neon" to "ネオンの光",
      "lt_rain" to "雨",
      "lt_snow_fall" to "雪が降る",
      "lt_candle" to "ろうそくの灯り",
      "lt_studio" to "スタジオの照明",
      "lt_backlight" to "逆光",
      "lt_soft" to "やわらかい光",
      "lt_dramatic" to "ドラマチックな陰影",
      "lt_sunbeam" to "木もれ日",
      "lt_fog" to "霧",
      "lt_sparkle" to "きらきらした光",
      "lt_petals" to "花びらが舞う",
    ),
  )

private fun optionGenre20(): CharacterOptionGenre =
  CharacterOptionGenre(
    "背景・場所",
    listOf(
    optionPart121(),
    optionPart122(),
    optionPart123(),
    ),
  )

private fun optionPart124(): CharacterOptionPart =
  CharacterOptionPart(
    "画風",
    true,
    listOf(
      "st_90s" to "90年代アニメ風",
      "st_80s" to "80年代アニメ風",
      "st_cel" to "セル画のような塗り",
      "st_watercolor" to "水彩",
      "st_sketch" to "ラフスケッチ",
      "st_lineart" to "線画",
      "st_oil" to "油絵",
      "st_manga" to "漫画（白黒）",
      "st_pixel" to "ドット絵",
      "st_semi_real" to "半実写・リアル寄り",
      "st_photo" to "写真のような実写",
      "st_ukiyoe" to "浮世絵風",
      "st_pastel" to "パステル調",
      "st_dark" to "ダークで重い雰囲気",
      "st_game_cg" to "ゲームのイベントCG風",
    ),
  )

private fun optionGenre21(): CharacterOptionGenre =
  CharacterOptionGenre(
    "絵柄・画風",
    listOf(
    optionPart124(),
    ),
  )

private fun optionPart125(): CharacterOptionPart =
  CharacterOptionPart(
    "種族",
    true,
    listOf(
      "rc_angel" to "天使（輪と翼）",
      "rc_vampire" to "吸血鬼（牙・青白い肌）",
      "rc_android" to "アンドロイド",
      "rc_kitsune" to "九尾の狐",
      "rc_dragon" to "竜人",
      "rc_werewolf" to "人狼",
      "rc_fairy" to "妖精",
      "rc_ghost" to "幽霊",
      "rc_zombie" to "ゾンビ風（軽いメイク）",
      "rc_alien" to "宇宙人",
      "rc_dark_elf" to "ダークエルフ",
    ),
  )

private fun optionGenre22(): CharacterOptionGenre =
  CharacterOptionGenre(
    "種族・ファンタジー（追加）",
    listOf(
    optionPart125(),
    ),
  )

private fun optionPart126(): CharacterOptionPart =
  CharacterOptionPart(
    "体の状態・行為",
    false,
    listOf(
      "pl_squirt" to "潮吹き",
      "pl_pregnant" to "妊娠（おなかが大きい）",
      "pl_milk" to "母乳",
      "pl_nipple_play" to "自分で乳首をいじる",
      "pl_breast_grope" to "自分で胸をいじる",
      "pl_masturbate" to "オナニー（指）",
      "pl_fingering" to "手マン（相手の手）",
      "pl_clit_pierce" to "クリトリスピアス",
      "pl_swallow" to "ごっくん",
      "pl_bukkake" to "ぶっかけ",
      "pl_breed" to "孕ませ（中出し）",
      "pl_titsuck" to "乳吸",
      "pl_enema" to "浣腸（プレイ）",
      "pl_watersports" to "聖水（おしっこ）",
      "pl_anal_training" to "アナル開発（プラグ・ビーズ）",
      "pl_pet" to "ペット化（四つん這い・首輪）",
      "pl_invisible_man" to "男透明化（ペニスだけ見える）",
      "pl_pov" to "主観（POV）",
    ),
  )

private fun optionGenre23(): CharacterOptionGenre =
  CharacterOptionGenre(
    "プレイ・状態（絵に出るもの）",
    listOf(
    optionPart126(),
    ),
  )

private fun optionPart127(): CharacterOptionPart =
  CharacterOptionPart(
    "口のプレイ",
    false,
    listOf(
      "p3_cleaning" to "お掃除フェラ（バキューム）",
      "p3_vacuum" to "バキュームフェラ",
      "p3_irrumatio" to "イラマチオ",
      "p3_saliva" to "唾液プレイ",
      "p3_ball_lick" to "玉舐め",
      "p3_tongue_cum" to "舌上射精",
      "p3_cum_bubble" to "ザーメンぶくぶく",
      "p3_deep_kiss" to "ディープキス",
      "p3_milk_drink" to "母乳飲食",
    ),
  )

private fun optionPart128(): CharacterOptionPart =
  CharacterOptionPart(
    "責め・焦らし・羞恥",
    false,
    listOf(
      "p3_tease" to "焦らしプレイ",
      "p3_blindfold_play" to "目隠しプレイ",
      "p3_abandon" to "放置プレイ",
      "p3_dirty_talk" to "言葉責め",
      "p3_watched" to "視姦プレイ",
      "p3_brush" to "筆責め",
      "p3_tickle" to "くすぐりプレイ",
      "p3_shame" to "羞恥プレイ",
      "p3_remote_vibe" to "遠隔バイブ（外で）",
      "p3_hold_back" to "イキ我慢",
      "p3_shave" to "剃毛プレイ",
      "p3_ejac_control" to "射精管理",
      "p3_trample" to "踏みつけプレイ",
      "p3_wrap_bondage" to "ラップ拘束",
      "p3_male_squirt" to "男の潮吹き",
      "p3_mutual_masturbation" to "相互オナニー",
    ),
  )

private fun optionPart129(): CharacterOptionPart =
  CharacterOptionPart(
    "場所・小道具のプレイ",
    false,
    listOf(
      "p3_toilet" to "トイレでプレイ",
      "p3_karaoke" to "カラオケボックス",
      "p3_elevator" to "エレベーターの中",
      "p3_warehouse" to "倉庫",
      "p3_her_room" to "彼女の部屋",
      "p3_soap" to "ソープ（泡・マットプレイ）",
      "p3_desert_island" to "無人島",
      "p3_messy_room" to "汚部屋",
      "p3_condoms" to "コンドームまみれ",
      "p3_banana_sprite" to "バナナとスプライト（小道具）",
    ),
  )

private fun optionPart130(): CharacterOptionPart =
  CharacterOptionPart(
    "見た目のハプニング・コスプレ状況",
    false,
    listOf(
      "p3_doll" to "ラブドール化",
      "p3_nip_slip" to "ポロリ",
      "p3_coat" to "裸コート（コートの下は裸）",
      "p3_zentai" to "全身タイツ",
      "p3_scream" to "快感の絶叫",
      "p3_cry_loud" to "号泣",
      "p3_handstand" to "逆立ち",
      "p3_wakame_sake" to "ワカメ酒",
      "p3_uterus" to "ウテルスセックス（深い挿入）",
    ),
  )

private fun optionGenre24(): CharacterOptionGenre =
  CharacterOptionGenre(
    "プレイ・シチュエーション（追加3）",
    listOf(
    optionPart127(),
    optionPart128(),
    optionPart129(),
    optionPart130(),
    ),
  )

private fun optionPart131(): CharacterOptionPart =
  CharacterOptionPart(
    "性格・雰囲気",
    true,
    listOf(
      "p3_yandere" to "ヤンデレ",
      "p3_menhera" to "メンヘラ",
      "p3_tsundere" to "ツンデレ",
      "p3_dosukebe" to "ドスケベ",
      "p3_tennen" to "天然",
      "p3_azatoi" to "あざとい",
      "p3_baka" to "お馬鹿",
      "p3_aneki" to "姉御肌",
    ),
  )

private fun optionPart132(): CharacterOptionPart =
  CharacterOptionPart(
    "職業・立場（追加）",
    true,
    listOf(
      "p3_vtuber" to "Vチューバー",
      "p3_childcare" to "保育士",
      "p3_twins" to "双子（2人並ぶ）",
      "p3_half" to "ハーフ",
      "p3_yakuza" to "ヤクザの姐さん",
      "p3_wakaokami" to "若女将",
      "p3_widow" to "未亡人",
      "p3_stepsister_older" to "義理の姉",
      "p3_stepsister_younger" to "義理の妹（成人）",
      "p3_sengoku" to "戦国乙女",
      "p3_thief_cat" to "泥棒猫（怪盗）",
      "p3_cow" to "牝牛（牛耳・牛柄）",
      "p3_mimic" to "ミミック（宝箱の魔物）",
    ),
  )

private fun optionPart133(): CharacterOptionPart =
  CharacterOptionPart(
    "魔法・特徴（追加）",
    false,
    listOf(
      "p3_magic_eye" to "魔眼",
      "p3_magic_book" to "魔法書",
      "p3_long_tongue" to "舌が長い",
      "p3_inner_color" to "インナーカラー",
      "p3_mesh" to "メッシュ（ハイライト）",
    ),
  )

private fun optionGenre25(): CharacterOptionGenre =
  CharacterOptionGenre(
    "キャラの属性・タイプ（追加3）",
    listOf(
    optionPart131(),
    optionPart132(),
    optionPart133(),
    ),
  )

private fun optionPart134(): CharacterOptionPart =
  CharacterOptionPart(
    "けがの状態",
    false,
    listOf(
      "p3_fracture_arm" to "骨折（腕：添え木・包帯・三角巾）",
      "p3_fracture_leg" to "骨折（脚：ギプス）",
      "p3_fracture_rib" to "肋骨の骨折（胸にコルセット・包帯）",
      "p3_sprain" to "捻挫（足首に包帯・湿布）",
      "p3_bruise" to "打撲（青・紫のあざ）",
      "p3_crutches_cast" to "松葉杖でギプスの脚を浮かせる",
    ),
  )

private fun optionGenre26(): CharacterOptionGenre =
  CharacterOptionGenre(
    "けが・手当て（追加）",
    listOf(
    optionPart134(),
    ),
  )

private fun optionPart135(): CharacterOptionPart =
  CharacterOptionPart(
    "顔まわりの責め具",
    false,
    listOf(
      "s4_nose_hook" to "鼻フック",
      "s4_mouth_spreader" to "強制開口器（開口マスク）",
      "s4_tongue_clamp" to "舌クリップ",
      "s4_bit_gag" to "くつわ（馬のはみ）",
      "s4_head_harness" to "ヘッドハーネス（革の頭ベルト）",
      "s4_earplugs" to "耳栓と目隠し（感覚遮断）",
    ),
  )

private fun optionPart136(): CharacterOptionPart =
  CharacterOptionPart(
    "体の責め具",
    false,
    listOf(
      "s4_nipple_weights" to "乳首ウェイト（おもり）",
      "s4_clothespins" to "洗濯ばさみ（体につける）",
      "s4_cock_ring" to "コックリング",
      "s4_posture_collar" to "ネックコルセット（首を固定）",
      "s4_ballet_boots" to "バレエブーツ（つま先立ち）",
      "s4_ankle_ball" to "足かせの鉄球",
      "s4_cuff_chain_set" to "首輪・手枷・足枷の鎖つきセット",
      "s4_pony_gear" to "ポニープレイの馬具（はみ・手綱・しっぽ）",
      "s4_pig_tail" to "豚のしっぽ（プラグ）",
      "s4_leash_chain" to "鎖のリード",
    ),
  )

private fun optionGenre27(): CharacterOptionGenre =
  CharacterOptionGenre(
    "SMグッズ・プレイ（追加4）",
    listOf(
    optionPart135(),
    optionPart136(),
    ),
  )

private fun optionPart137(): CharacterOptionPart =
  CharacterOptionPart(
    "職業コスプレ",
    false,
    listOf(
      "g1_cos_maid_french" to "フレンチメイド",
      "g1_cos_maid_victorian" to "ヴィクトリアンメイド",
      "g1_cos_maid_gothic" to "ゴシックメイド",
      "g1_cos_butler" to "執事風",
      "g1_cos_hostess" to "ホステス風ドレス",
      "g1_cos_flight_attendant_retro" to "レトロ客室乗務員",
      "g1_cos_train_conductor" to "車掌風",
      "g1_cos_chef_patissier" to "パティシエ",
      "g1_cos_barista" to "バリスタ",
      "g1_cos_florist" to "花屋のエプロン",
      "g1_cos_librarian" to "司書",
      "g1_cos_receptionist" to "受付嬢",
      "g1_cos_secretary_pencil" to "タイトスーツ秘書",
      "g1_cos_detective" to "探偵",
      "g1_cos_spy" to "スパイ",
      "g1_cos_agent_suit" to "エージェント風スーツ",
      "g1_cos_pilot_retro" to "飛行士",
      "g1_cos_racing_driver" to "ドライバー",
      "g1_cos_biker" to "ライダー",
      "g1_cos_mechanic" to "メカニック",
      "g1_cos_welder" to "工場作業員",
      "g1_cos_construction" to "現場作業員",
      "g1_cos_farmer" to "農家",
      "g1_cos_cowgirl" to "カウガール",
      "g1_cos_lifeguard" to "ライフガード",
      "g1_cos_scientist" to "科学者",
      "g1_cos_dr_plague" to "ペストドクター風",
      "g1_cos_astronaut" to "宇宙飛行士",
      "g1_cos_diver" to "ダイバー",
      "g1_cos_firefighter_2" to "救助隊員",
      "g1_cos_paramedic" to "救急隊員",
      "g1_cos_pharmacist" to "薬剤師",
      "g1_cos_surgeon" to "外科医",
      "g1_cos_judge" to "裁判官",
      "g1_cos_lawyer" to "弁護士",
      "g1_cos_reporter" to "記者",
      "g1_cos_announcer" to "アナウンサー",
      "g1_cos_dj" to "DJ",
      "g1_cos_idol_sparkle" to "キラキラアイドル",
      "g1_cos_idol_band" to "バンドガール",
      "g1_cos_rock_star" to "ロックスター",
      "g1_cos_cheer_pro" to "プロチア",
      "g1_cos_figure_skater" to "フィギュアスケーター",
      "g1_cos_gymnast_leo" to "新体操",
      "g1_cos_boxer" to "ボクサー",
      "g1_cos_wrestler" to "女子プロレス",
      "g1_cos_sumo_mawashi_style" to "力士風",
      "g1_cos_martial_artist" to "武道家",
      "g1_cos_archer_sport" to "アーチャー",
      "g1_cos_fencer" to "フェンシング",
      "g1_cos_tennis_pro" to "プロテニス",
      "g1_cos_golfer" to "ゴルファー",
      "g1_cos_skier" to "スキーヤー",
      "g1_cos_snowboarder" to "スノーボーダー",
      "g1_cos_surfer" to "サーファー",
    ),
  )

private fun optionPart138(): CharacterOptionPart =
  CharacterOptionPart(
    "ファンタジー職・世界のコスプレ",
    false,
    listOf(
      "g1_cos_witch_forest" to "森の魔女",
      "g1_cos_witch_sexy" to "セクシー魔女",
      "g1_cos_sorceress" to "ソーサレス",
      "g1_cos_necromancer" to "死霊術師風",
      "g1_cos_alchemist" to "錬金術師",
      "g1_cos_druid" to "ドルイド",
      "g1_cos_shaman" to "シャーマン",
      "g1_cos_priestess" to "女司祭",
      "g1_cos_oracle" to "神託の巫女",
      "g1_cos_fortune_teller" to "占い師",
      "g1_cos_gypsy_dancer" to "流浪の踊り子",
      "g1_cos_paladin" to "聖騎士",
      "g1_cos_dark_knight" to "暗黒騎士",
      "g1_cos_samurai_female" to "女武者",
      "g1_cos_ronin" to "浪人",
      "g1_cos_shinobi_modern" to "現代忍者",
      "g1_cos_assassin_hood" to "フード付き暗殺者",
      "g1_cos_thief_rogue" to "盗賊",
      "g1_cos_ranger" to "レンジャー",
      "g1_cos_elf_archer" to "エルフの弓兵",
      "g1_cos_barbarian" to "バーバリアン",
      "g1_cos_gladiator" to "剣闘士",
      "g1_cos_amazon" to "アマゾネス",
      "g1_cos_viking" to "ヴァイキング",
      "g1_cos_valkyrie_2" to "北欧の戦乙女",
      "g1_cos_pirate_captain" to "海賊船長",
      "g1_cos_sea_goddess" to "海の女神",
      "g1_cos_greek_goddess" to "ギリシャ女神風",
      "g1_cos_egyptian_queen" to "エジプト女王",
      "g1_cos_ancient_queen" to "古代王妃",
      "g1_cos_roman_toga" to "ローマ風トガ",
      "g1_cos_archmage" to "大魔導士",
      "g1_cos_battle_mage" to "戦闘魔導士",
      "g1_cos_fairy_tale_queen" to "おとぎ話の女王",
      "g1_cos_snow_queen" to "雪の女王",
      "g1_cos_fire_queen" to "炎の女王",
      "g1_cos_witch_hunter" to "魔女狩り人風",
      "g1_cos_vampire_hunter" to "ヴァンパイアハンター",
      "g1_cos_exorcist" to "悪魔祓い",
      "g1_cos_reaper" to "死神",
      "g1_cos_geisha_modern" to "芸妓風",
      "g1_cos_kunoichi_red" to "赤装束のくノ一",
      "g1_cos_taoist" to "道士",
      "g1_cos_wuxia" to "武侠風",
      "g1_cos_wuxia_swordswoman" to "中華の女剣士",
      "g1_cos_princess_arabian" to "アラビアンプリンセス",
      "g1_cos_genie" to "ジニー",
      "g1_cos_mermaid_costume" to "マーメイド衣装",
      "g1_cos_pixie" to "ピクシー",
      "g1_cos_cat_burglar" to "怪盗スーツ",
    ),
  )

private fun optionPart139(): CharacterOptionPart =
  CharacterOptionPart(
    "動物・季節・舞台のコスプレ",
    false,
    listOf(
      "g1_cos_kigurumi_pajama" to "着ぐるみパジャマ",
      "g1_cos_cow_costume" to "牛コスチューム",
      "g1_cos_leopard_print" to "ヒョウ柄コスチューム",
      "g1_cos_tiger_print" to "トラ柄ビキニ",
      "g1_cos_zebra_print" to "ゼブラ柄コスチューム",
      "g1_cos_bee_costume" to "ミツバチ",
      "g1_cos_ladybug" to "てんとう虫",
      "g1_cos_butterfly_costume" to "蝶コスチューム",
      "g1_cos_swan_ballet" to "白鳥のバレエ衣装",
      "g1_cos_black_swan" to "黒鳥衣装",
      "g1_cos_peacock" to "クジャク衣装",
      "g1_cos_penguin_hoodie" to "ペンギンパーカー",
      "g1_cos_frog_raincoat" to "カエルのレインコート",
      "g1_cos_dinosaur_hoodie" to "恐竜パーカー",
      "g1_cos_pumpkin_halloween" to "ハロウィンかぼちゃ",
      "g1_cos_skeleton_costume" to "ガイコツ衣装",
      "g1_cos_mummy_bandage" to "ミイラ風包帯衣装",
      "g1_cos_frankenstein_bride" to "フランケンの花嫁風",
      "g1_cos_scarecrow" to "かかし",
      "g1_cos_snowman" to "雪だるま衣装",
      "g1_cos_gingerbread" to "ジンジャーブレッド衣装",
      "g1_cos_reindeer" to "トナカイ衣装",
      "g1_cos_elf_christmas" to "クリスマスエルフ",
      "g1_cos_mrs_claus" to "ミセスサンタ",
      "g1_cos_easter_bunny" to "イースターバニー",
      "g1_cos_valentine" to "バレンタイン衣装",
      "g1_cos_cupid" to "キューピッド",
      "g1_cos_new_year_kimono" to "お正月の晴れ着",
      "g1_cos_fruit_costume" to "フルーツ衣装",
      "g1_cos_candy_costume" to "キャンディ衣装",
      "g1_cos_sailor_pirate" to "ボーダー海賊風",
      "g1_cos_toy_soldier" to "ブリキの兵隊",
      "g1_cos_doll_porcelain" to "ビスクドール風",
      "g1_cos_marionette" to "マリオネット風",
      "g1_cos_clown_sexy" to "ピエロ風",
      "g1_cos_circus_ringmaster" to "サーカス団長",
      "g1_cos_circus_acrobat" to "曲芸師",
      "g1_cos_magician_stage" to "マジシャン",
      "g1_cos_cabaret_dancer" to "キャバレーダンサー",
      "g1_cos_showgirl" to "ショーガール",
      "g1_cos_burlesque" to "バーレスク",
      "g1_cos_flapper" to "20年代フラッパー",
      "g1_cos_pinup_retro" to "ピンナップ風",
      "g1_cos_vintage_worker" to "ヴィンテージ作業服",
    ),
  )

private fun optionPart140(): CharacterOptionPart =
  CharacterOptionPart(
    "SF・サイバーのコスプレ",
    false,
    listOf(
      "g1_cos_cyber_runner" to "サイバーランナー",
      "g1_cos_hacker" to "ハッカー",
      "g1_cos_android_maid" to "アンドロイドメイド",
      "g1_cos_space_captain" to "宇宙船長",
      "g1_cos_space_pirate" to "宇宙海賊",
      "g1_cos_bounty_hunter" to "賞金稼ぎ",
      "g1_cos_mecha_pilot" to "メカパイロット",
      "g1_cos_soldier_future" to "未来兵",
      "g1_cos_alien_queen" to "異星の女王",
      "g1_cos_cyber_geisha" to "サイバー芸者",
      "g1_cos_steampunk_inventor" to "スチームパンク発明家",
      "g1_cos_steampunk_aviator" to "スチームパンク飛行士",
      "g1_cos_dieselpunk" to "ディーゼルパンク",
      "g1_cos_post_apoc" to "終末世界の旅人",
      "g1_cos_wasteland_raider" to "荒野の略奪者風",
      "g1_cos_shelter_suit" to "シェルター住人風",
      "g1_cos_holo_idol" to "ホログラムアイドル",
      "g1_cos_virtual_singer" to "バーチャル歌姫",
      "g1_cos_neon_samurai" to "ネオン侍",
      "g1_cos_cyber_nun" to "サイバーシスター",
      "g1_cos_mad_scientist" to "マッドサイエンティスト",
      "g1_cos_time_traveler" to "時間旅行者",
    ),
  )

private fun optionGenre28(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コスプレ（追加）",
    listOf(
    optionPart137(),
    optionPart138(),
    optionPart139(),
    optionPart140(),
    ),
  )

private fun optionPart141(): CharacterOptionPart =
  CharacterOptionPart(
    "世界の民族衣装",
    false,
    listOf(
      "g1_fas_hanbok_modern" to "モダン韓服",
      "g1_fas_qipao_modern" to "モダンチーパオ",
      "g1_fas_cheongsam_velvet" to "ベルベットのチャイナ",
      "g1_fas_tang_suit" to "唐装",
      "g1_fas_ao_dai_white" to "白いアオザイ",
      "g1_fas_sari_silk" to "シルクのサリー",
      "g1_fas_lehenga" to "レヘンガ",
      "g1_fas_salwar" to "サルワールカミーズ",
      "g1_fas_kimono_yukata_hair" to "浴衣の夏祭り風",
      "g1_fas_kimono_houmongi" to "訪問着",
      "g1_fas_kimono_tomesode" to "留袖",
      "g1_fas_kimono_komon" to "小紋",
      "g1_fas_kimono_taisho" to "大正ロマン風",
      "g1_fas_wafuku_modern" to "モダン和装",
      "g1_fas_haori_jacket" to "羽織",
      "g1_fas_jinbei" to "甚平",
      "g1_fas_happi" to "法被",
      "g1_fas_dirndl_alpine" to "アルプス風民族衣装",
      "g1_fas_kilt_scottish" to "スコットランド風キルト",
      "g1_fas_flamenco" to "フラメンコ衣装",
      "g1_fas_mexican_folk" to "メキシコ民族衣装",
      "g1_fas_hawaiian_muumuu" to "ムウムウ",
      "g1_fas_polynesian" to "ポリネシア風",
      "g1_fas_african_wax" to "アフリカンプリント",
      "g1_fas_kente_style" to "ケンテ風織物",
      "g1_fas_moroccan_kaftan" to "モロッコのカフタン",
      "g1_fas_abaya" to "アバヤ風",
      "g1_fas_hijab_fashion" to "ヒジャブファッション",
      "g1_fas_turkish_style" to "トルコ風衣装",
      "g1_fas_russian_sarafan" to "ロシア民族衣装",
      "g1_fas_nordic_knit" to "北欧ニット",
      "g1_fas_inuit_fur" to "極北の毛皮服",
      "g1_fas_bavarian" to "バイエルン風",
      "g1_fas_celtic_dress" to "ケルト風ドレス",
      "g1_fas_thai_traditional" to "タイの伝統衣装",
      "g1_fas_balinese" to "バリ風衣装",
      "g1_fas_peruvian_poncho" to "アンデス風ポンチョ",
      "g1_fas_native_style" to "北米先住民風",
      "g1_fas_hanfu_song" to "宋風漢服",
      "g1_fas_hanfu_tang" to "唐風漢服",
      "g1_fas_hanfu_ming" to "明風漢服",
    ),
  )

private fun optionPart142(): CharacterOptionPart =
  CharacterOptionPart(
    "ストリート・トレンド",
    false,
    listOf(
      "g1_fas_y2k_baby_tee" to "Y2Kベビーティー",
      "g1_fas_y2k_velour" to "Y2Kベロアジャージ",
      "g1_fas_y2k_butterfly" to "蝶のY2Kトップ",
      "g1_fas_2000s_gal" to "2000年代ギャル",
      "g1_fas_90s_grunge" to "90年代グランジ",
      "g1_fas_90s_minimal" to "90年代ミニマル",
      "g1_fas_80s_power" to "80年代パワースーツ",
      "g1_fas_80s_neon" to "80年代ネオン",
      "g1_fas_70s_disco" to "70年代ディスコ",
      "g1_fas_70s_boho" to "70年代ボヘミアン",
      "g1_fas_60s_mod" to "60年代モッズ",
      "g1_fas_50s_retro" to "50年代レトロ",
      "g1_fas_korean_chic" to "韓国シック",
      "g1_fas_korean_street" to "韓国ストリート",
      "g1_fas_korean_airport" to "韓国空港ファッション",
      "g1_fas_japanese_gyaru_new" to "ギャル系ミックス",
      "g1_fas_ura_harajuku" to "裏原系",
      "g1_fas_decora" to "デコラ系",
      "g1_fas_fairy_kei" to "フェアリー系",
      "g1_fas_lolita_ouji" to "王子系",
      "g1_fas_mori_girl" to "森ガール",
      "g1_fas_dolly_kei" to "ドーリー系",
      "g1_fas_kogal_adult_casual" to "大人カジュアル",
      "g1_fas_clean_girl" to "クリーンガール",
      "g1_fas_quiet_luxury" to "クワイエットラグジュアリー",
      "g1_fas_old_money" to "オールドマネー",
      "g1_fas_coastal_grandma" to "コースタル風",
      "g1_fas_cottagecore" to "コテージコア",
      "g1_fas_dark_academia_adult" to "ダークアカデミア",
      "g1_fas_light_academia" to "ライトアカデミア",
      "g1_fas_balletcore" to "バレエコア",
      "g1_fas_gorpcore" to "ゴープコア",
      "g1_fas_normcore" to "ノームコア",
      "g1_fas_athleisure" to "アスレジャー",
      "g1_fas_streetwear_baggy" to "ダボダボストリート",
      "g1_fas_cargo_pants" to "カーゴパンツ",
      "g1_fas_tomboy" to "ボーイッシュ",
      "g1_fas_preppy" to "プレッピー",
      "g1_fas_western_boho" to "ウエスタンボーホー",
      "g1_fas_punk_tartan" to "タータンパンク",
      "g1_fas_goth_adult" to "大人ゴス",
      "g1_fas_cyber_goth" to "サイバーゴス",
      "g1_fas_witchy" to "ウィッチコア",
      "g1_fas_vaporwave" to "ヴェイパーウェイヴ",
      "g1_fas_techwear" to "テックウェア風",
      "g1_fas_rave" to "レイブ衣装",
      "g1_fas_festival" to "フェス衣装",
      "g1_fas_kawaii_pop" to "ポップカワイイ",
      "g1_fas_yume_kawaii" to "ゆめかわ",
      "g1_fas_jirai_adult" to "地雷系大人",
      "g1_fas_onee_kei" to "お姉系",
      "g1_fas_office_casual" to "オフィスカジュアル",
      "g1_fas_resort_wear" to "リゾートウェア",
      "g1_fas_safari" to "サファリ風",
      "g1_fas_nautical" to "マリンスタイル",
      "g1_fas_pajama_set" to "パジャマセット",
      "g1_fas_loungewear" to "ラウンジウェア",
      "g1_fas_kimono_robe" to "ガウンローブ",
    ),
  )

private fun optionPart143(): CharacterOptionPart =
  CharacterOptionPart(
    "ドレス・フォーマル",
    false,
    listOf(
      "g1_fas_ballgown" to "ボールガウン",
      "g1_fas_mermaid_dress" to "マーメイドドレス",
      "g1_fas_bodycon_mini" to "ボディコンミニ",
      "g1_fas_slip_satin" to "サテンのスリップドレス",
      "g1_fas_halter_gown" to "ホルターガウン",
      "g1_fas_high_slit_gown" to "ハイスリットガウン",
      "g1_fas_sequin_dress" to "スパンコールドレス",
      "g1_fas_velvet_dress" to "ベルベットドレス",
      "g1_fas_lace_dress" to "総レースドレス",
      "g1_fas_tulle_dress" to "チュールドレス",
      "g1_fas_tuxedo_female" to "タキシード",
      "g1_fas_suit_vest" to "ベスト付きスーツ",
      "g1_fas_wide_trouser_suit" to "ワイドパンツスーツ",
      "g1_fas_cape_dress" to "ケープドレス",
      "g1_fas_off_shoulder_gown" to "オフショルドレス",
      "g1_fas_one_shoulder" to "ワンショルダードレス",
      "g1_fas_cutout_dress" to "カットアウトドレス",
      "g1_fas_wrap_dress" to "ラップドレス",
      "g1_fas_corset_dress" to "コルセットドレス",
      "g1_fas_gown_train" to "トレーンの長いドレス",
      "g1_fas_wedding_veil_short" to "ミニウェディング",
      "g1_fas_black_dress" to "リトルブラックドレス",
      "g1_fas_red_dress" to "真っ赤なドレス",
      "g1_fas_white_summer_dress" to "白いサマードレス",
      "g1_fas_maxi_floral" to "花柄マキシ",
      "g1_fas_trench_coat" to "トレンチコート",
      "g1_fas_fur_coat" to "ファーコート",
      "g1_fas_long_coat" to "ロングコート",
      "g1_fas_puffer" to "ダウンジャケット",
      "g1_fas_bomber" to "ボンバージャケット",
      "g1_fas_varsity_adult" to "ヴァーシティジャケット",
      "g1_fas_denim_jacket" to "デニムジャケット",
      "g1_fas_kimono_coat" to "キモノコート",
    ),
  )

private fun optionPart144(): CharacterOptionPart =
  CharacterOptionPart(
    "水着の種類",
    false,
    listOf(
      "g1_swm_bandeau_string" to "バンドゥ紐水着",
      "g1_swm_triangle" to "トライアングルビキニ",
      "g1_swm_sling_bikini" to "スリングビキニ",
      "g1_swm_high_waist" to "ハイウエストビキニ",
      "g1_swm_cheeky" to "チーキー水着",
      "g1_swm_brazilian" to "ブラジリアン",
      "g1_swm_tankini" to "タンキニ",
      "g1_swm_halter_one" to "ホルターワンピ水着",
      "g1_swm_plunge_one" to "深Vワンピ水着",
      "g1_swm_cutout_one" to "カットアウトワンピ水着",
      "g1_swm_backless_one" to "バックレス水着",
      "g1_swm_strappy" to "ストラップ水着",
      "g1_swm_ring" to "リングつき水着",
      "g1_swm_lace_up" to "編み上げ水着",
      "g1_swm_crochet" to "クロシェ水着",
      "g1_swm_floral" to "花柄ビキニ",
      "g1_swm_leopard" to "ヒョウ柄ビキニ",
      "g1_swm_striped" to "ストライプ水着",
      "g1_swm_polka" to "水玉ビキニ",
      "g1_swm_metallic" to "メタリック水着",
      "g1_swm_sheer_cover" to "透けるカバーアップ",
      "g1_swm_sarong" to "サロンスカート",
      "g1_swm_beach_shirt" to "ビーチシャツ羽織",
      "g1_swm_wet_look" to "濡れた肌の水着",
      "g1_swm_retro_one" to "レトロ水着",
      "g1_swm_surf_shorts" to "サーフパンツ",
      "g1_swm_wetsuit_half" to "ハーフウェットスーツ",
      "g1_swm_monokini_cut" to "モノキニ(深カット)",
    ),
  )

private fun optionPart145(): CharacterOptionPart =
  CharacterOptionPart(
    "スポーツウェア",
    false,
    listOf(
      "g1_spt_sports_bra_leggings" to "スポブラとレギンス",
      "g1_spt_cycling" to "サイクルウェア",
      "g1_spt_pole_fit" to "ポールフィット",
      "g1_spt_pilates" to "ピラティスウェア",
      "g1_spt_swim_competition" to "競技ビキニ",
      "g1_spt_track_suit" to "トラックスーツ",
      "g1_spt_basketball" to "バスケユニフォーム",
      "g1_spt_soccer" to "サッカーユニフォーム",
      "g1_spt_volleyball" to "ビーチバレー",
      "g1_spt_baseball_fan" to "野球観戦風",
      "g1_spt_rock_climb" to "クライミング",
      "g1_spt_hiking" to "ハイキング",
      "g1_spt_ski_bunny" to "スキーバニー",
      "g1_spt_ice_skater" to "アイススケート",
      "g1_spt_horse_riding" to "乗馬服",
      "g1_spt_rowing" to "ボートウェア",
      "g1_spt_bellydance_practice" to "ダンス練習着",
      "g1_spt_ballet_practice" to "バレエ練習着",
      "g1_spt_judo_practice" to "柔術ラッシュ",
    ),
  )

private fun optionGenre29(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ファッション（追加）",
    listOf(
    optionPart141(),
    optionPart142(),
    optionPart143(),
    optionPart144(),
    optionPart145(),
    ),
  )

private fun optionPart146(): CharacterOptionPart =
  CharacterOptionPart(
    "ブラの種類",
    false,
    listOf(
      "g1_lin_bra_balconette" to "バルコネットブラ",
      "g1_lin_bra_plunge" to "プランジブラ",
      "g1_lin_bra_bralette" to "ブラレット",
      "g1_lin_bra_triangle" to "トライアングルブラ",
      "g1_lin_bra_bandeau" to "バンドゥブラ",
      "g1_lin_bra_halter" to "ホルターブラ",
      "g1_lin_bra_longline" to "ロングラインブラ",
      "g1_lin_bra_push_up" to "プッシュアップブラ",
      "g1_lin_bra_front_tie" to "フロントリボンブラ",
      "g1_lin_bra_cage" to "ケージブラ",
      "g1_lin_bra_harness" to "ハーネスブラ",
      "g1_lin_bra_mesh" to "メッシュブラ",
      "g1_lin_bra_satin" to "サテンブラ",
      "g1_lin_bra_embroidered" to "刺繍ブラ",
      "g1_lin_bra_cupless_lace" to "カップレスレース",
      "g1_lin_bra_underbust" to "アンダーバスト",
      "g1_lin_bra_shelf" to "シェルフブラ",
      "g1_lin_bra_zip" to "ジップブラ",
      "g1_lin_bra_slip_cup" to "スリップブラ",
    ),
  )

private fun optionPart147(): CharacterOptionPart =
  CharacterOptionPart(
    "ショーツの種類",
    false,
    listOf(
      "g1_lin_pan_thong_lace" to "レースTバック",
      "g1_lin_pan_g_string" to "Gストリング",
      "g1_lin_pan_brief_high" to "ハイウエストブリーフ",
      "g1_lin_pan_french" to "フレンチカット",
      "g1_lin_pan_bikini_side" to "サイドストラップ",
      "g1_lin_pan_ribbon_side" to "サイドリボン",
      "g1_lin_pan_crotchless_lace" to "オープンクロッチ",
      "g1_lin_pan_garter_attach" to "ガーター付きショーツ",
      "g1_lin_pan_cheeky" to "チーキーショーツ",
      "g1_lin_pan_boyshort_lace" to "レースボーイショーツ",
      "g1_lin_pan_micro" to "マイクロショーツ",
      "g1_lin_pan_strappy" to "ストラップショーツ",
      "g1_lin_pan_mesh" to "メッシュショーツ",
      "g1_lin_pan_satin" to "サテンショーツ",
      "g1_lin_pan_cotton_plain" to "綿の無地ショーツ",
      "g1_lin_pan_stripe" to "ストライプショーツ",
      "g1_lin_pan_polka" to "水玉ショーツ",
      "g1_lin_pan_floral" to "花柄ショーツ",
      "g1_lin_pan_red" to "赤のショーツ",
      "g1_lin_pan_white_lace" to "白レースのショーツ",
    ),
  )

private fun optionPart148(): CharacterOptionPart =
  CharacterOptionPart(
    "ランジェリー・一体型",
    false,
    listOf(
      "g1_lin_babydoll_sheer" to "透けるベビードール",
      "g1_lin_chemise_silk" to "シルクのシュミーズ",
      "g1_lin_teddy_lace" to "レースのテディ",
      "g1_lin_bodysuit_lace" to "レースボディ",
      "g1_lin_bodysuit_mesh" to "メッシュボディスーツ",
      "g1_lin_bodystocking_fishnet" to "網ボディストッキング",
      "g1_lin_bodystocking_open" to "オープンボディストッキング",
      "g1_lin_bodystocking_black" to "黒ボディストッキング",
      "g1_lin_negligee_long" to "ロングネグリジェ",
      "g1_lin_robe_lace" to "レースのガウン",
      "g1_lin_garter_set_white" to "白ガーターセット",
      "g1_lin_garter_set_black" to "黒ガーターセット",
      "g1_lin_garter_wide" to "ワイドガーターベルト",
      "g1_lin_suspender_belt" to "サスペンダーベルト",
      "g1_lin_corset_underbust" to "アンダーバストコルセット",
      "g1_lin_corset_overbust" to "オーバーバストコルセット",
      "g1_lin_corset_leather" to "レザーコルセット",
      "g1_lin_waist_cincher" to "ウエストシンチャー",
      "g1_lin_slip_lace" to "レースのスリップ",
      "g1_lin_cami_set" to "キャミとショーツ",
      "g1_lin_peekaboo" to "ピークアブー",
      "g1_lin_ouvert" to "オープン型ランジェリー",
      "g1_lin_strap_harness" to "ストラップだけの下着",
      "g1_lin_chain_lingerie" to "チェーンランジェリー",
      "g1_lin_ribbon_wrap" to "リボンを巻いただけ",
      "g1_lin_pasties_star" to "スターのニップレス",
      "g1_lin_pasties_tassel" to "タッセル付きニップレス",
      "g1_lin_pasties_heart" to "ハートのニップレス",
      "g1_lin_bridal_lingerie" to "ブライダルランジェリー",
      "g1_lin_stocking_thighhigh_lace" to "レースのストッキング",
      "g1_lin_stocking_seamed_black" to "黒シーム入りストッキング",
      "g1_lin_stocking_white_sheer" to "白の薄いストッキング",
      "g1_lin_stocking_red" to "赤いストッキング",
      "g1_lin_stocking_patterned" to "柄ストッキング",
      "g1_lin_pantyhose_black" to "黒パンスト",
      "g1_lin_pantyhose_nude" to "ベージュパンスト",
      "g1_lin_pantyhose_torn" to "破れたパンスト",
      "g1_lin_pantyhose_sheer_white" to "白パンスト",
      "g1_lin_tights_opaque" to "厚手の黒タイツ",
    ),
  )

private fun optionPart149(): CharacterOptionPart =
  CharacterOptionPart(
    "下着姿の状態・着方",
    false,
    listOf(
      "g1_lin_state_one_strap_down" to "片方の肩ひもが落ちる",
      "g1_lin_state_unhooked" to "ホックが外れたブラ",
      "g1_lin_state_bra_lifted" to "ブラをずらす",
      "g1_lin_state_bra_pulled_down" to "ブラを下げる",
      "g1_lin_state_panties_half" to "太ももまで下ろす",
      "g1_lin_state_panties_one_leg" to "片足に下着",
      "g1_lin_state_pantyhose_torn_crotch" to "股が破けたパンスト",
      "g1_lin_state_shirt_open_bra" to "シャツの前を開く",
      "g1_lin_state_robe_slip" to "ガウンがはだける",
      "g1_lin_state_pulling_strap" to "ひもを引く",
      "g1_lin_state_undressing" to "脱ぎかけ",
      "g1_lin_state_removing_stockings" to "ストッキングを脱ぐ",
      "g1_lin_state_unzip" to "ジッパーを下げる",
      "g1_lin_state_dress_slip" to "ワンピースがずり落ちる",
      "g1_lin_state_towel_only" to "タオルだけを巻く",
      "g1_lin_state_see_through_wet" to "濡れて透ける下着",
      "g1_lin_state_underwear_only" to "下着だけ",
      "g1_lin_state_matching_set" to "おそろいのセット",
      "g1_lin_state_oversized_shirt_panties" to "ぶかぶかシャツとショーツ",
      "g1_lin_state_apron_lingerie" to "エプロンと下着",
      "g1_lin_state_coat_lingerie" to "コートの下は下着",
      "g1_lin_state_holding_bra" to "ブラを手に持つ",
      "g1_lin_state_stuffing_bra" to "胸を整える",
      "g1_lin_state_fastening" to "ホックを留める",
    ),
  )

private fun optionGenre30(): CharacterOptionGenre =
  CharacterOptionGenre(
    "下着姿（追加）",
    listOf(
    optionPart146(),
    optionPart147(),
    optionPart148(),
    optionPart149(),
    ),
  )

private fun optionPart150(): CharacterOptionPart =
  CharacterOptionPart(
    "鎧・防具",
    false,
    listOf(
      "g1_eq_plate_full" to "フルプレートアーマー",
      "g1_eq_plate_female" to "女性用プレートメイル",
      "g1_eq_chainmail" to "鎖かたびら",
      "g1_eq_scale_armor" to "うろこ鎧",
      "g1_eq_leather_armor" to "革鎧",
      "g1_eq_studded_leather" to "鋲付き革鎧",
      "g1_eq_samurai_armor" to "大鎧",
      "g1_eq_light_armor_fantasy" to "軽装ファンタジー鎧",
      "g1_eq_pauldron_single" to "片方だけの肩当て",
      "g1_eq_gauntlet" to "ガントレット",
      "g1_eq_greaves" to "すね当て",
      "g1_eq_sabaton" to "鉄の靴",
      "g1_eq_helm_horned" to "角つき兜",
      "g1_eq_helm_visor" to "面頬つき兜",
      "g1_eq_circlet" to "サークレット",
      "g1_eq_gorget" to "喉当て",
      "g1_eq_tassets" to "腰の草摺り",
      "g1_eq_cloak_fur" to "毛皮のマント",
      "g1_eq_cloak_hooded" to "フード付きマント",
      "g1_eq_cape_red" to "赤いケープ",
      "g1_eq_surcoat" to "サーコート",
      "g1_eq_bone_armor" to "骨の鎧",
      "g1_eq_dragon_scale_armor" to "竜鱗の鎧",
      "g1_eq_crystal_armor" to "水晶の鎧",
      "g1_eq_golden_armor" to "黄金の鎧",
      "g1_eq_black_armor" to "黒騎士の鎧",
      "g1_eq_holy_armor" to "聖なる白銀の鎧",
      "g1_eq_shield_round" to "丸盾",
      "g1_eq_shield_tower" to "大盾",
      "g1_eq_shield_buckler" to "小型盾",
    ),
  )

private fun optionPart151(): CharacterOptionPart =
  CharacterOptionPart(
    "武器",
    false,
    listOf(
      "g1_eq_sword_long" to "長剣",
      "g1_eq_sword_great" to "大剣",
      "g1_eq_sword_rapier" to "レイピア",
      "g1_eq_sword_scimitar" to "シミター",
      "g1_eq_sword_twin" to "双剣",
      "g1_eq_sword_holy" to "聖剣",
      "g1_eq_sword_demon" to "魔剣",
      "g1_eq_katana_sheath" to "鞘に収めた刀",
      "g1_eq_naginata" to "薙刀",
      "g1_eq_spear" to "槍",
      "g1_eq_trident" to "三叉槍",
      "g1_eq_halberd" to "ハルバード",
      "g1_eq_axe_battle" to "戦斧",
      "g1_eq_axe_double" to "両刃の斧",
      "g1_eq_hammer_war" to "戦槌",
      "g1_eq_mace" to "メイス",
      "g1_eq_flail" to "フレイル",
      "g1_eq_dagger" to "短剣",
      "g1_eq_dagger_twin" to "二本の短剣",
      "g1_eq_kunai" to "クナイ",
      "g1_eq_shuriken" to "手裏剣",
      "g1_eq_whip_battle" to "戦闘用の鞭",
      "g1_eq_scythe" to "大鎌",
      "g1_eq_bow_long" to "長弓",
      "g1_eq_bow_compound" to "機械弓",
      "g1_eq_crossbow" to "クロスボウ",
      "g1_eq_staff_crystal" to "水晶の杖",
      "g1_eq_staff_wooden" to "木の杖",
      "g1_eq_grimoire_floating" to "浮かぶ魔導書",
      "g1_eq_orb" to "魔法の宝珠",
      "g1_eq_fan_war" to "鉄扇",
      "g1_eq_chakram" to "チャクラム",
      "g1_eq_boomerang_blade" to "ブーメラン刃",
      "g1_eq_claws" to "鉤爪武器",
      "g1_eq_nunchaku" to "ヌンチャク",
      "g1_eq_tonfa" to "トンファー",
      "g1_eq_pistol_dual" to "二丁拳銃",
      "g1_eq_revolver" to "リボルバー",
      "g1_eq_rifle" to "ライフル",
      "g1_eq_sniper" to "スナイパーライフル",
      "g1_eq_shotgun" to "ショットガン",
      "g1_eq_smg" to "サブマシンガン",
      "g1_eq_assault_rifle" to "アサルトライフル",
      "g1_eq_rocket" to "ロケットランチャー",
    ),
  )

private fun optionPart152(): CharacterOptionPart =
  CharacterOptionPart(
    "SF・サイバー装備",
    false,
    listOf(
      "g1_eq_power_armor" to "パワーアーマー",
      "g1_eq_exoskeleton" to "外骨格",
      "g1_eq_plugsuit_style" to "フィットスーツ(SF)",
      "g1_eq_combat_suit" to "戦闘スーツ",
      "g1_eq_tactical_vest" to "タクティカルベスト",
      "g1_eq_cyber_visor" to "サイバーバイザー",
      "g1_eq_cyber_arm_blade" to "腕のブレード",
      "g1_eq_cyber_eye" to "サイバーアイ",
      "g1_eq_cyber_spine" to "背中の接続端子",
      "g1_eq_neck_port" to "首のジャック",
      "g1_eq_backpack_mech" to "メカバックパック",
      "g1_eq_jetpack" to "ジェットパック",
      "g1_eq_wings_energy" to "光の翼",
      "g1_eq_hologram_screen" to "ホログラム画面",
      "g1_eq_drone_companion" to "相棒ドローン",
      "g1_eq_energy_sword" to "エネルギーソード",
      "g1_eq_laser_gun" to "レーザー銃",
      "g1_eq_gas_mask" to "ガスマスク",
      "g1_eq_respirator" to "防毒マスク",
      "g1_eq_oxygen_mask" to "酸素マスク",
      "g1_eq_space_helmet_off" to "宇宙ヘルメット(小脇)",
      "g1_eq_flight_jacket_future" to "未来のフライトジャケット",
      "g1_eq_headset_comm" to "通信ヘッドセット",
      "g1_eq_arm_terminal" to "腕端末",
      "g1_eq_belt_utility" to "ユーティリティベルト",
      "g1_eq_thigh_holster" to "太もものホルスター",
      "g1_eq_shoulder_holster" to "ショルダーホルスター",
      "g1_eq_bandolier" to "弾帯",
      "g1_eq_ammo_belt" to "ベルト弾",
    ),
  )

private fun optionPart153(): CharacterOptionPart =
  CharacterOptionPart(
    "ローブ・装束・小道具",
    false,
    listOf(
      "g1_eq_robe_wizard" to "魔法使いのローブ",
      "g1_eq_robe_hooded_black" to "黒いフードローブ",
      "g1_eq_robe_white_healer" to "白い聖職ローブ",
      "g1_eq_robe_silk_sage" to "賢者の絹ローブ",
      "g1_eq_robe_sheer" to "透けるローブ",
      "g1_eq_mantle_feather" to "羽根のマント",
      "g1_eq_stole_fur" to "ファーストール",
      "g1_eq_veil_mage" to "魔導のヴェール",
      "g1_eq_tabard" to "タバード",
      "g1_eq_potion_belt" to "ポーションベルト",
      "g1_eq_spellbook" to "呪文書",
      "g1_eq_scroll" to "巻物",
      "g1_eq_rune_stone" to "ルーン石",
      "g1_eq_amulet_glow" to "光るお守り",
      "g1_eq_lantern_hand" to "手提げランタン",
      "g1_eq_telescope" to "望遠鏡",
      "g1_eq_compass" to "コンパス",
      "g1_eq_map" to "地図",
      "g1_eq_pocket_watch" to "懐中時計",
      "g1_eq_key_large" to "大きな鍵",
      "g1_eq_rope_coil" to "ロープ",
      "g1_eq_grapple" to "フック",
      "g1_eq_backpack_adventure" to "冒険バッグ",
      "g1_eq_bedroll" to "寝袋",
    ),
  )

private fun optionGenre31(): CharacterOptionGenre =
  CharacterOptionGenre(
    "装備品（追加）",
    listOf(
    optionPart150(),
    optionPart151(),
    optionPart152(),
    optionPart153(),
    ),
  )

private fun optionPart154(): CharacterOptionPart =
  CharacterOptionPart(
    "メカクレ・前髪",
    false,
    listOf(
      "g1_hr_bang_hide_both" to "両目が隠れる前髪",
      "g1_hr_bang_hide_one_r" to "右目が隠れる",
      "g1_hr_bang_hide_one_l" to "左目が隠れる",
      "g1_hr_bang_peekaboo" to "のぞく片目",
      "g1_hr_bang_mouth_visible" to "口だけ見える",
      "g1_hr_bang_heavy" to "重たい前髪",
      "g1_hr_bang_long_side" to "長い横髪で隠す",
      "g1_hr_bang_see_through" to "薄めの前髪",
      "g1_hr_bang_blunt" to "姫前髪",
      "g1_hr_bang_choppy" to "ざっくり前髪",
      "g1_hr_bang_curtain" to "カーテンバング",
      "g1_hr_bang_asymmetric" to "アシンメトリー前髪",
      "g1_hr_bang_parted_middle" to "真ん中分け前髪",
      "g1_hr_bang_micro" to "極短前髪",
      "g1_hr_bang_diagonal" to "斜めバング",
      "g1_hr_bang_wispy" to "ふんわり前髪",
      "g1_hr_bang_braided" to "編み込み前髪",
      "g1_hr_bang_pinned_back" to "ピンで留めた前髪",
      "g1_hr_bang_cross_clip" to "ばってんピン",
      "g1_hr_bang_ahoge_bang" to "ひとふさのアホ毛",
      "g1_hr_bang_hair_between_eyes" to "目の間の髪",
      "g1_hr_bang_covering_one_eye_long" to "ロングで片目隠し",
      "g1_hr_bang_wind" to "風にゆれる前髪",
      "g1_hr_bang_wet" to "濡れた前髪",
    ),
  )

private fun optionPart155(): CharacterOptionPart =
  CharacterOptionPart(
    "髪型",
    false,
    listOf(
      "g1_hr_style_long_straight_black" to "黒髪ロングストレート",
      "g1_hr_style_long_wavy" to "ゆるウェーブロング",
      "g1_hr_style_long_curly" to "ロングカール",
      "g1_hr_style_semi_long" to "セミロング",
      "g1_hr_style_lob" to "ロブ",
      "g1_hr_style_bob_asymmetric" to "アシンメトリーボブ",
      "g1_hr_style_bob_inward" to "内巻きボブ",
      "g1_hr_style_bob_outward" to "外ハネボブ",
      "g1_hr_style_bob_long" to "ロングボブ",
      "g1_hr_style_pixie_short" to "ショートピクシー",
      "g1_hr_style_mullet" to "マッシュウルフ",
      "g1_hr_style_shaggy" to "シャギーカット",
      "g1_hr_style_layered_long" to "レイヤーロング",
      "g1_hr_style_feathered" to "フェザーカット",
      "g1_hr_style_hime_cut" to "姫カット",
      "g1_hr_style_bun_high" to "ハイお団子",
      "g1_hr_style_bun_low" to "ローお団子",
      "g1_hr_style_bun_double" to "ツインお団子",
      "g1_hr_style_chignon" to "シニヨン",
      "g1_hr_style_french_twist" to "フレンチツイスト",
      "g1_hr_style_ponytail_side_long" to "サイドポニー(ロング)",
      "g1_hr_style_ponytail_high_long" to "ハイポニー(ロング)",
      "g1_hr_style_ponytail_low_loose" to "ゆるローポニー",
      "g1_hr_style_twintail_high" to "ハイツイン",
      "g1_hr_style_pigtail_braid" to "三つ編みおさげ",
      "g1_hr_style_braid_dutch" to "ダッチブレイド",
      "g1_hr_style_braid_waterfall" to "ウォーターフォール編み",
      "g1_hr_style_braid_box" to "ボックスブレイド",
      "g1_hr_style_braid_micro" to "マイクロブレイド",
      "g1_hr_style_cornrow_modern" to "コーンロウ(おしゃれ)",
      "g1_hr_style_twist_out" to "ツイスト",
      "g1_hr_style_afro_puff" to "アフロパフ",
      "g1_hr_style_dreadlocks_long" to "長いドレッド",
      "g1_hr_style_updo_elegant" to "エレガントアップ",
      "g1_hr_style_updo_wedding" to "ブライダルアップ",
      "g1_hr_style_half_up_bun" to "ハーフアップお団子",
      "g1_hr_style_half_up_braided" to "ハーフアップ編み込み",
      "g1_hr_style_ringlets" to "縦巻きカール",
      "g1_hr_style_drill" to "ドリルヘア",
      "g1_hr_style_victory_roll" to "ヴィクトリーロール",
      "g1_hr_style_pompadour" to "ポンパドール",
      "g1_hr_style_beehive" to "ビーハイブ",
      "g1_hr_style_finger_wave" to "フィンガーウェーブ",
      "g1_hr_style_buzz_cut" to "バズカット",
      "g1_hr_style_bald_wig_cap" to "ウィッグネット",
      "g1_hr_style_hair_antenna_double" to "アホ毛2本",
      "g1_hr_style_hair_flower" to "髪に花",
      "g1_hr_style_hair_ribbon_braid" to "リボン編み込み",
      "g1_hr_style_hair_pearl" to "真珠を編み込む",
      "g1_hr_style_hair_wet_slick" to "濡れた髪",
      "g1_hr_style_hair_messy_bed" to "寝ぐせ",
      "g1_hr_style_hair_sweaty" to "汗で貼りつく髪",
      "g1_hr_style_hair_floating" to "ふわりと広がる",
      "g1_hr_style_hair_covering_chest" to "髪で胸を隠す",
      "g1_hr_style_hair_tucked_ear" to "耳にかける",
      "g1_hr_style_hair_tuck" to "髪を耳にかける仕草",
    ),
  )

private fun optionPart156(): CharacterOptionPart =
  CharacterOptionPart(
    "髪色・髪のバリエーション",
    false,
    listOf(
      "g1_hc_platinum" to "プラチナブロンド",
      "g1_hc_honey_blonde" to "ハニーブロンド",
      "g1_hc_strawberry_blonde" to "ストロベリーブロンド",
      "g1_hc_ash_blonde" to "アッシュブロンド",
      "g1_hc_ash_gray" to "アッシュグレー",
      "g1_hc_silver_blue" to "シルバーブルー",
      "g1_hc_milk_tea" to "ミルクティー",
      "g1_hc_chocolate" to "チョコレートブラウン",
      "g1_hc_chestnut" to "栗色",
      "g1_hc_auburn" to "オーバーン",
      "g1_hc_copper_red" to "カッパー",
      "g1_hc_burgundy" to "ワインレッド",
      "g1_hc_blue_black" to "青みがかった黒",
      "g1_hc_pastel_pink" to "パステルピンク",
      "g1_hc_pastel_lavender" to "ラベンダー",
      "g1_hc_pastel_mint" to "ミント",
      "g1_hc_pastel_blue" to "パステルブルー",
      "g1_hc_neon_green" to "ネオングリーン",
      "g1_hc_neon_pink" to "ネオンピンク",
      "g1_hc_teal" to "ティール",
      "g1_hc_galaxy" to "ギャラクシー髪",
      "g1_hc_rainbow" to "レインボー髪",
      "g1_hc_ombre_pink_blonde" to "金からピンク",
      "g1_hc_ombre_black_blue" to "黒から青",
      "g1_hc_ombre_black_red" to "黒から赤",
      "g1_hc_split_dye" to "スプリットカラー",
      "g1_hc_streak_front" to "前髪メッシュ",
      "g1_hc_streak_white" to "白髪ひとふさ",
      "g1_hc_roots_dark" to "プリン頭",
      "g1_hc_underlights" to "裏カラー",
      "g1_hc_two_tone_side" to "サイド別色",
      "g1_hc_silver_white_long" to "銀白の長髪",
      "g1_hc_gradient_tips" to "毛先カラー",
      "g1_hc_shiny" to "つやつや髪",
      "g1_hc_fluffy" to "ふわふわ髪",
      "g1_hc_silky" to "さらさら髪",
      "g1_hc_frizzy" to "広がる髪",
      "g1_hc_curly_natural" to "自然なくせ毛",
      "g1_hc_coily" to "コイルヘア",
      "g1_hc_very_long" to "腰より長い髪",
      "g1_hc_extremely_long" to "床に届く髪",
    ),
  )

private fun optionGenre32(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪型・メカクレ（追加）",
    listOf(
    optionPart154(),
    optionPart155(),
    optionPart156(),
    ),
  )

private fun optionPart157(): CharacterOptionPart =
  CharacterOptionPart(
    "アイメイク",
    false,
    listOf(
      "g2_eye_dbl" to "ダブルウイング",
      "g2_eye_cut" to "カットクリース",
      "g2_eye_halo" to "ハローアイ",
      "g2_eye_gradshadow" to "グラデーションアイ",
      "g2_eye_inner" to "目頭ハイライト",
      "g2_eye_lowlash" to "下まつげマスカラ",
      "g2_eye_lashgap" to "束感まつげ",
      "g2_eye_lashfan" to "ボリュームラッシュ",
      "g2_eye_lashbot" to "下まつ毛エクステ風",
      "g2_eye_graphic" to "グラフィックライナー",
      "g2_eye_colorliner" to "カラーライナー",
      "g2_eye_whiteliner" to "白アイライン",
      "g2_eye_waterline" to "ウォーターラインの黒",
      "g2_eye_puffy" to "涙袋メイク",
      "g2_eye_glitter_tear" to "目頭のラメ",
      "g2_eye_rhine" to "目尻ラインストーン",
      "g2_eye_gold_liner" to "ゴールドライナー",
      "g2_eye_kohl" to "コール（目の縁取り）",
      "g2_eye_arab" to "アラビアンアイ",
      "g2_eye_egypt" to "エジプト風アイ",
      "g2_eye_smudge" to "にじませアイ",
      "g2_eye_pandas" to "パンダ目風",
      "g2_eye_sunset" to "サンセットアイ",
      "g2_eye_neonshadow" to "ネオンアイシャドウ",
      "g2_eye_greenshadow" to "グリーンのアイシャドウ",
      "g2_eye_pinkshadow2" to "ローズのアイシャドウ",
      "g2_eye_dsilver" to "シルバーのアイシャドウ",
      "g2_eye_mono" to "モノトーンアイ",
      "h2_eyeliner" to "アイライナー",
      "h2_drooping_eyeliner" to "下がりアイライン",
      "h2_red_eyeliner" to "赤いアイライン",
      "h2_black_eyeliner" to "ブラックのアイライン",
      "h2_smoky_eyeliner" to "囲み目メイク",
      "h2_eyeshadow" to "アイシャドウ",
      "h2_glitter_under_eyes" to "ラメを目の下に散らす",
      "h2_eyeshadow_under_eye" to "下まぶたのシャドウ",
      "h2_red_eyeshadow_at_corner_of_eyes" to "目尻の赤み",
      "h2_mascara" to "マスカラ",
      "h2_long_mascara" to "長いマスカラ",
      "h2_runny_makeup" to "涙で流れたマスカラ",
      "h2_smudged_eye_makeup" to "にじんだアイメイク",
      "h2_eyebrow_pencil" to "アイブロウペンシルの眉",
      "h2_eyelid_highlight" to "まぶたのハイライト",
      "h2_brow_highlight" to "眉の下のハイライト",
      "h2_heavy_eye_makeup" to "アイメイクが濃い",
      "h2_natural_makeup" to "ナチュラルなアイメイク",
    ),
  )

private fun optionPart158(): CharacterOptionPart =
  CharacterOptionPart(
    "リップ",
    false,
    listOf(
      "g2_lip_gradient" to "グラデーションリップ",
      "g2_lip_ombre" to "オンブレリップ",
      "g2_lip_blur" to "ぼかしリップ",
      "g2_lip_matte" to "マットリップ",
      "g2_lip_overline" to "オーバーリップ",
      "g2_lip_burgundy" to "ボルドーのリップ",
      "g2_lip_coral" to "コーラルのリップ",
      "g2_lip_orange" to "オレンジのリップ",
      "g2_lip_nude_brown" to "ブラウンのリップ",
      "g2_lip_purple" to "パープルのリップ",
      "g2_lip_blue" to "ブルーのリップ",
      "g2_lip_green" to "グリーンのリップ",
      "g2_lip_white" to "白いリップ",
      "g2_lip_glitter" to "ラメのリップ",
      "g2_lip_metal" to "メタリックリップ",
      "g2_lip_chrome" to "クロームリップ",
      "g2_lip_cherry" to "チェリーレッドのリップ",
      "g2_lip_stain" to "ティントリップ",
      "g2_lip_cupid" to "クピッドボウ強調",
      "g2_lip_heart" to "ハート型リップ",
      "g2_lip_bite" to "噛みリップ（血色）",
      "g2_lip_outline" to "リップライナー",
      "g2_lip_twotone" to "ツートーンリップ",
      "g2_lip_smudge" to "にじんだ口紅",
      "h2_beige_lipstick" to "ベージュのリップ",
      "h2_wine_red_lipstick" to "ワインレッドのリップ",
      "h2_smeared_lipstick" to "口紅がはみ出す",
      "h2_kiss_mark" to "キスマーク（口紅の跡）",
      "h2_lipstick_mark_on_cheek" to "口紅の跡が頬につく",
    ),
  )

private fun optionPart159(): CharacterOptionPart =
  CharacterOptionPart(
    "チーク・ベース",
    false,
    listOf(
      "g2_chk_blush_high" to "高い位置のチーク",
      "g2_chk_nose" to "鼻チーク",
      "g2_chk_sunburn" to "日焼け風チーク",
      "g2_chk_draw" to "頬の斜めチーク",
      "g2_chk_ichigo" to "いちごメイク",
      "g2_chk_heart" to "ハートのチーク",
      "g2_chk_circle" to "まあるいチーク",
      "g2_chk_crush" to "ほてり風チーク",
      "g2_chk_contour" to "シェーディング",
      "g2_chk_highlight" to "ハイライト強め",
      "g2_chk_dewy" to "つや肌ベース",
      "g2_chk_matte" to "セミマット肌",
      "g2_chk_porcelain" to "陶器肌",
      "g2_chk_freckle_draw" to "描きそばかす",
      "g2_chk_moledraw" to "描きほくろ",
    ),
  )

private fun optionPart160(): CharacterOptionPart =
  CharacterOptionPart(
    "国別・流行メイク",
    false,
    listOf(
      "g2_reg_douyin" to "中華風メイク（抖音）",
      "g2_reg_hanfu" to "漢服メイク（花鈿）",
      "g2_reg_korea_glass" to "グラスキン",
      "g2_reg_jirai" to "地雷メイク",
      "g2_reg_yamanba" to "ヤマンバメイク",
      "g2_reg_gyaru_new" to "盛りギャルメイク",
      "g2_reg_hime_gyaru" to "姫ギャルメイク",
      "g2_reg_aomi" to "90年代メイク",
      "g2_reg_showa" to "昭和レトロメイク",
      "g2_reg_geisha" to "芸者メイク",
      "g2_reg_kabuki" to "歌舞伎の隈取",
      "g2_reg_oiran" to "花魁メイク",
      "g2_reg_latina" to "ラテン系メイク",
      "g2_reg_indian" to "インド風メイク（ビンディ）",
      "g2_reg_arab" to "中東風メイク",
      "g2_reg_euro" to "ヨーロッパ風ナチュラル",
      "g2_reg_french" to "フレンチガール風",
      "g2_reg_russian" to "ロシアンメイク",
      "g2_reg_y2k" to "Y2Kメイク",
      "g2_reg_80s" to "80年代メイク",
      "g2_reg_50s" to "50年代ピンナップメイク",
      "g2_reg_20s" to "20年代フラッパーメイク",
      "g2_reg_vamp" to "ヴァンプメイク",
      "g2_reg_thai" to "タイ風メイク",
      "g2_reg_baddie" to "バディメイク",
      "g2_reg_cyber" to "サイバーメイク",
      "g2_reg_fairy" to "フェアリーメイク",
      "g2_reg_mermaid" to "マーメイドメイク",
      "g2_reg_doll" to "ドールメイク",
    ),
  )

private fun optionPart161(): CharacterOptionPart =
  CharacterOptionPart(
    "特殊メイク・ステージ",
    false,
    listOf(
      "g2_spm_clown" to "ピエロメイク",
      "g2_spm_harley" to "ひし形フェイスペイント",
      "g2_spm_skull" to "スカルメイク",
      "g2_spm_catface" to "猫ひげメイク",
      "g2_spm_foxface" to "狐の隈取",
      "g2_spm_stars" to "星のフェイスジェム",
      "g2_spm_cheekstars" to "頬の星シール",
      "g2_spm_hearts_sticker" to "ハートのシール",
      "g2_spm_tears_paint" to "涙の絵",
      "g2_spm_cracks" to "ひび割れ風メイク",
      "g2_spm_scale" to "うろこメイク",
      "g2_spm_vein" to "血管風ライン",
      "g2_spm_glow" to "光るメイク",
      "g2_spm_uvneon" to "ブラックライトメイク",
      "g2_spm_halfface" to "半顔ペイント",
      "g2_spm_zombie2" to "ゾンビ風メイク（強め）",
      "g2_spm_alien" to "エイリアン風メイク",
      "g2_spm_elf" to "エルフ風ハイライト",
      "g2_spm_butterfly" to "蝶のフェイスアート",
      "g2_spm_flower_face" to "花のフェイスアート",
      "g2_spm_geom" to "幾何学フェイスペイント",
      "g2_spm_stage" to "ステージメイク",
      "g2_spm_tribal_face" to "民族風フェイスペイント",
    ),
  )

private fun optionPart162(): CharacterOptionPart =
  CharacterOptionPart(
    "ネイル",
    false,
    listOf(
      "g2_nail_chrome" to "クロームネイル",
      "g2_nail_mirror" to "ミラーネイル",
      "g2_nail_glitter" to "ラメネイル",
      "g2_nail_cat" to "マグネットネイル",
      "g2_nail_stone" to "ストーンネイル",
      "g2_nail_marble" to "大理石ネイル",
      "g2_nail_aura" to "オーラネイル",
      "g2_nail_ombre" to "グラデーションネイル",
      "g2_nail_stiletto" to "スティレットネイル",
      "g2_nail_square" to "スクエアネイル",
      "g2_nail_coffin" to "コフィンネイル",
      "g2_nail_almond" to "アーモンドネイル",
      "g2_nail_round" to "ラウンドネイル",
      "g2_nail_long_acrylic" to "ロングのつけ爪",
      "g2_nail_heart" to "ハートネイル",
      "g2_nail_leopard" to "豹柄ネイル",
      "g2_nail_checker" to "チェッカーネイル",
      "g2_nail_star" to "星ネイル",
      "g2_nail_cherry" to "さくらんぼネイル",
      "g2_nail_charm" to "チャーム付きネイル",
      "g2_nail_mismatch" to "ちがう色のネイル",
      "g2_nail_pastel" to "パステルネイル",
      "g2_nail_black_french" to "黒フレンチ",
      "g2_nail_red_long" to "赤いロングネイル",
      "g2_nail_gold" to "ゴールドネイル",
      "g2_nail_silver" to "シルバーネイル",
      "g2_nail_blue" to "ブルーネイル",
      "g2_nail_green" to "グリーンネイル",
      "g2_nail_purple" to "パープルネイル",
      "g2_nail_yellow" to "イエローネイル",
      "g2_nail_nude" to "ヌードネイル",
    ),
  )

private fun optionPart163(): CharacterOptionPart =
  CharacterOptionPart(
    "チーク・ハイライト・ベース",
    false,
    listOf(
      "h2_pink_blush" to "頬紅（ピンク）",
      "h2_orange_blush" to "頬紅（オレンジ）",
      "h2_blush_under_eyes" to "目の下のチーク",
      "h2_nose_blush_3" to "鼻まで広がるチーク",
      "h2_highlighter" to "ハイライト",
      "h2_bronzer" to "ブロンザー",
      "h2_foundation" to "ファンデーション",
      "h2_dewy_makeup" to "ツヤ肌メイク",
      "h2_matte_makeup" to "マットな肌メイク",
      "h2_pale_makeup" to "青白いメイク",
      "h2_no_makeup" to "ノーメイク",
      "h2_glitter_highlight" to "ラメのハイライト",
    ),
  )

private fun optionPart164(): CharacterOptionPart =
  CharacterOptionPart(
    "国別・年代別メイク",
    false,
    listOf(
      "h2_korean_makeup" to "韓国風メイク",
      "h2_japanese_makeup" to "日本のナチュラルメイク",
      "h2_chinese_makeup" to "中国風メイク",
      "h2_forehead_decoration" to "額の花鈿",
      "h2_bindi" to "インド風の額のビンディ",
      "h2_arabian_makeup" to "アラビア風の太アイライン",
      "h2_egyptian_makeup" to "エジプト風アイメイク",
      "h2_gyaru_makeup" to "ギャル風メイク",
      "h2_vintage_makeup" to "50年代風メイク",
      "h2_1960s_makeup" to "60年代風メイク",
      "h2_1980s_makeup" to "80年代風メイク",
      "h2_1990s_makeup" to "90年代風メイク",
      "h2_gothic_makeup" to "ゴシックメイク",
      "h2_punk_makeup" to "パンクメイク",
      "h2_visual_kei_makeup" to "ビジュアル系メイク",
      "h2_stage_makeup" to "舞台化粧",
      "h2_white_face_powder" to "白塗りの顔",
      "h2_geisha_makeup" to "芸妓風メイク",
      "h2_geisha_makeup_2" to "目元に赤を入れた和風メイク",
      "h2_halloween_makeup" to "ハロウィンのメイク",
      "h2_drag_makeup" to "派手な舞台風メイク",
      "h2_elegant_makeup" to "大人っぽいエレガントメイク",
      "h2_elderly_woman_makeup" to "上品な年配のメイク",
    ),
  )

private fun optionGenre33(): CharacterOptionGenre =
  CharacterOptionGenre(
    "化粧",
    listOf(
    optionPart157(),
    optionPart158(),
    optionPart159(),
    optionPart160(),
    optionPart161(),
    optionPart162(),
    optionPart163(),
    optionPart164(),
    ),
  )

private fun optionPart165(): CharacterOptionPart =
  CharacterOptionPart(
    "全体のスタイル",
    false,
    listOf(
      "g2_bp_full" to "全身ペイント",
      "g2_bp_half" to "半身ペイント",
      "g2_bp_clothes" to "服のように塗る",
      "g2_bp_bikini" to "ビキニ風ペイント",
      "g2_bp_lingerie" to "ランジェリー風ペイント",
      "g2_bp_stocking_paint" to "ストッキング風ペイント",
      "g2_bp_gloves_paint" to "手袋風ペイント",
      "g2_bp_dress" to "ドレス風ペイント",
      "g2_bp_armor" to "鎧風ペイント",
      "g2_bp_suit" to "スーツ風ペイント",
      "g2_bp_cat" to "動物柄ペイント",
      "g2_bp_tiger" to "トラ柄ペイント",
      "g2_bp_zebra" to "シマウマ柄",
      "g2_bp_leopard" to "ヒョウ柄ペイント",
      "g2_bp_cow" to "牛柄ペイント",
      "g2_bp_snake" to "ヘビ柄ペイント",
      "g2_bp_giraffe" to "キリン柄",
      "g2_bp_camo" to "迷彩ペイント",
      "g2_bp_galaxy" to "銀河柄ペイント",
      "g2_bp_marble" to "大理石ペイント",
      "g2_bp_watercolor" to "水彩ペイント",
      "g2_bp_gold" to "金色ペイント",
      "g2_bp_silver" to "銀色ペイント",
      "g2_bp_bronze" to "青銅像風ペイント",
      "g2_bp_stone" to "石像風ペイント",
      "g2_bp_glass" to "ガラス細工風",
      "g2_bp_porcelain" to "陶器人形風",
    ),
  )

private fun optionPart166(): CharacterOptionPart =
  CharacterOptionPart(
    "色と塗料",
    false,
    listOf(
      "g2_bp_red" to "赤いペイント",
      "g2_bp_blue" to "青いペイント",
      "g2_bp_green" to "緑のペイント",
      "g2_bp_white" to "白いペイント",
      "g2_bp_black" to "黒いペイント",
      "g2_bp_pink" to "ピンクのペイント",
      "g2_bp_purple" to "紫のペイント",
      "g2_bp_orange" to "オレンジのペイント",
      "g2_bp_yellow" to "黄色いペイント",
      "g2_bp_rainbow" to "虹色ペイント",
      "g2_bp_gradient" to "グラデーションペイント",
      "g2_bp_neon" to "ネオン塗料",
      "g2_bp_glow" to "蓄光塗料（暗闇で光る）",
      "g2_bp_uv" to "ブラックライト塗料",
      "g2_bp_glitter" to "グリッター塗料",
      "g2_bp_pearl" to "パール塗料",
      "g2_bp_holo" to "ホログラム塗料",
      "g2_bp_metallic" to "メタリック塗料",
      "g2_bp_drip" to "垂れるペイント",
      "g2_bp_splatter" to "飛び散るペイント",
      "g2_bp_handprint" to "手形のペイント",
      "g2_bp_fingerpaint" to "指で塗った跡",
      "g2_bp_brush" to "筆の跡",
      "g2_bp_sponge" to "エアブラシ塗り",
    ),
  )

private fun optionPart167(): CharacterOptionPart =
  CharacterOptionPart(
    "部位別",
    false,
    listOf(
      "g2_bp_face" to "顔だけペイント",
      "g2_bp_chest" to "胸にペイント",
      "g2_bp_belly" to "おなかにペイント",
      "g2_bp_back" to "背中にペイント",
      "g2_bp_arm" to "腕にペイント",
      "g2_bp_leg" to "脚にペイント",
      "g2_bp_thigh" to "太ももにペイント",
      "g2_bp_hip" to "腰・おしりのペイント",
      "g2_bp_shoulder" to "肩にペイント",
      "g2_bp_neck" to "首のペイント",
      "g2_bp_hand" to "手の甲のペイント",
      "g2_bp_foot" to "足のペイント",
      "g2_bp_between" to "胸の谷間にペイント",
      "g2_bp_collar" to "鎖骨のペイント",
      "g2_bp_waist" to "くびれのライン",
    ),
  )

private fun optionPart168(): CharacterOptionPart =
  CharacterOptionPart(
    "絵柄",
    false,
    listOf(
      "g2_bp_flower" to "花の絵柄",
      "g2_bp_cherry" to "桜の絵柄",
      "g2_bp_peony" to "牡丹の絵柄",
      "g2_bp_koi" to "鯉の絵柄",
      "g2_bp_dragon" to "龍の絵柄",
      "g2_bp_tiger2" to "虎の絵柄",
      "g2_bp_phoenix" to "鳳凰の絵柄",
      "g2_bp_wave" to "波の絵柄",
      "g2_bp_clouds" to "雲の絵柄",
      "g2_bp_oni" to "鬼の面の絵柄",
      "g2_bp_hannya" to "般若の絵柄",
      "g2_bp_snake_irez" to "蛇と牡丹",
      "g2_bp_butterfly" to "蝶の絵柄",
      "g2_bp_vine" to "つたの絵柄",
      "g2_bp_rose" to "薔薇の絵柄",
      "g2_bp_lotus" to "蓮の絵柄",
      "g2_bp_feather" to "羽根の絵柄",
      "g2_bp_wing" to "翼の絵柄",
      "g2_bp_star" to "星の絵柄",
      "g2_bp_moon" to "月の絵柄",
      "g2_bp_sun" to "太陽の絵柄",
      "g2_bp_mandala" to "曼荼羅の絵柄",
      "g2_bp_geom" to "幾何学の絵柄",
      "g2_bp_stripes" to "縞模様",
      "g2_bp_dots" to "水玉模様",
      "g2_bp_heart" to "ハートの絵柄",
      "g2_bp_text" to "文字を書く",
      "g2_bp_graffiti" to "落書き風",
      "g2_bp_cartoon" to "イラスト風ペイント",
      "g2_bp_circuit" to "基板の模様",
    ),
  )

private fun optionPart169(): CharacterOptionPart =
  CharacterOptionPart(
    "祭り・民族風",
    false,
    listOf(
      "g2_bp_festival" to "祭りのペイント",
      "g2_bp_samba" to "サンバ風ペイント",
      "g2_bp_holi" to "ホーリー風（色粉）",
      "g2_bp_tribal" to "部族風ライン",
      "g2_bp_maori" to "渦巻き紋様風",
      "g2_bp_henna_hands" to "ヘナ（手）",
      "g2_bp_henna_arm" to "ヘナ（腕）",
      "g2_bp_henna_belly" to "ヘナ（おなか）",
      "g2_bp_henna_foot" to "ヘナ（足）",
      "g2_bp_africa" to "アフリカ風白塗り",
      "g2_bp_kabuki" to "歌舞伎風ペイント",
      "g2_bp_nebuta" to "ねぶた風の色",
      "g2_bp_aztec" to "アステカ風",
      "g2_bp_celtic" to "ケルト風の結び目",
      "g2_bp_nordic" to "北欧のルーン風",
    ),
  )

private fun optionGenre34(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ボディーペイント",
    listOf(
    optionPart165(),
    optionPart166(),
    optionPart167(),
    optionPart168(),
    optionPart169(),
    ),
  )

private fun optionPart170(): CharacterOptionPart =
  CharacterOptionPart(
    "形（ハート）",
    false,
    listOf(
      "g2_im_heart" to "ハート型の淫紋",
      "g2_im_heart_wing" to "翼つきハート",
      "g2_im_heart_horn" to "角つきハート",
      "g2_im_heart_arrow" to "矢が刺さったハート",
      "g2_im_heart_chain" to "鎖のハート",
      "g2_im_heart_lock" to "鍵穴のハート",
      "g2_im_heart_double" to "二重ハート",
      "g2_im_heart_triple" to "三つのハート",
      "g2_im_heart_ring" to "ハートを囲む輪",
      "g2_im_heart_thorn" to "いばらのハート",
      "g2_im_heart_rose" to "薔薇のハート",
      "g2_im_heart_flame" to "炎のハート",
      "g2_im_heart_crown" to "王冠つきハート",
      "g2_im_heart_split" to "割れたハート",
      "g2_im_heart_star" to "星つきハート",
      "g2_im_heart_flower" to "花つきハート",
      "g2_im_heart_tail" to "しっぽつきハート",
      "g2_im_heart_eye" to "目のあるハート",
      "g2_im_heart_skull" to "ドクロつきハート",
      "g2_im_heart_arrowdown" to "下向き矢印のハート",
    ),
  )

private fun optionPart171(): CharacterOptionPart =
  CharacterOptionPart(
    "形（翼・紋様）",
    false,
    listOf(
      "g2_im_wing" to "翼のみの紋",
      "g2_im_wing_demon" to "悪魔の翼の紋",
      "g2_im_wing_angel" to "天使の翼の紋",
      "g2_im_wing_bat" to "コウモリの翼の紋",
      "g2_im_wing_bone" to "骨の翼の紋",
      "g2_im_wing_feather" to "羽根の翼の紋",
      "g2_im_circle" to "魔法陣の円",
      "g2_im_pentagram" to "五芒星",
      "g2_im_hexagram" to "六芒星",
      "g2_im_triangle" to "逆三角形の紋",
      "g2_im_spiral" to "渦巻きの紋",
      "g2_im_snake" to "蛇の紋",
      "g2_im_ouroboros" to "ウロボロス",
      "g2_im_tentacle" to "触手の紋",
      "g2_im_flower" to "花の紋",
      "g2_im_lotus" to "蓮の紋",
      "g2_im_rose" to "薔薇の紋",
      "g2_im_moon" to "月の紋",
      "g2_im_sun" to "太陽の紋",
      "g2_im_star" to "星の紋",
      "g2_im_eye" to "目の紋",
      "g2_im_cat" to "猫の足あとの紋",
      "g2_im_fox" to "狐の紋",
      "g2_im_butterfly" to "蝶の紋",
      "g2_im_succubus" to "サキュバスの紋",
      "g2_im_rune" to "ルーン文字の紋",
      "g2_im_kanji" to "漢字の紋",
      "g2_im_seal" to "封印の紋",
      "g2_im_contract" to "契約の紋",
      "g2_im_slave" to "従属の紋",
      "g2_im_pet" to "所有の紋",
      "g2_im_curse" to "呪いの紋",
      "g2_im_blessing" to "祝福の紋",
    ),
  )

private fun optionPart172(): CharacterOptionPart =
  CharacterOptionPart(
    "場所",
    false,
    listOf(
      "g2_im_loc_belly" to "下腹部",
      "g2_im_loc_navel" to "へその下",
      "g2_im_loc_navel_around" to "へその周り",
      "g2_im_loc_chest" to "胸の上",
      "g2_im_loc_between" to "胸の谷間",
      "g2_im_loc_breast" to "胸（左右）",
      "g2_im_loc_hip" to "腰",
      "g2_im_loc_waist" to "わき腹",
      "g2_im_loc_thigh" to "太もも",
      "g2_im_loc_thigh_inner" to "内もも",
      "g2_im_loc_butt" to "おしり",
      "g2_im_loc_back" to "背中",
      "g2_im_loc_lowerback" to "腰の後ろ",
      "g2_im_loc_nape" to "うなじ",
      "g2_im_loc_neck" to "首",
      "g2_im_loc_shoulder" to "肩",
      "g2_im_loc_arm" to "腕",
      "g2_im_loc_hand" to "手の甲",
      "g2_im_loc_forehead" to "額",
      "g2_im_loc_cheek" to "頬",
      "g2_im_loc_eye" to "目の下",
      "g2_im_loc_pelvis" to "骨盤まわり",
      "g2_im_loc_pubic" to "恥丘",
      "g2_im_loc_ankle" to "足首",
      "g2_im_loc_foot" to "足の甲",
    ),
  )

private fun optionPart173(): CharacterOptionPart =
  CharacterOptionPart(
    "色・光り方",
    false,
    listOf(
      "g2_im_pink" to "ピンクの淫紋",
      "g2_im_red" to "赤い淫紋",
      "g2_im_purple" to "紫の淫紋",
      "g2_im_black" to "黒い淫紋",
      "g2_im_gold" to "金色の淫紋",
      "g2_im_blue" to "青い淫紋",
      "g2_im_white" to "白い淫紋",
      "g2_im_rainbow" to "虹色の淫紋",
      "g2_im_glow" to "光る淫紋",
      "g2_im_pulse" to "脈打つ淫紋",
      "g2_im_flicker" to "ちらちら点滅する淫紋",
      "g2_im_neon" to "ネオン色の淫紋",
      "g2_im_faint" to "うっすら浮かぶ淫紋",
      "g2_im_vivid" to "くっきりした淫紋",
      "g2_im_burn" to "焼き印のような淫紋",
      "g2_im_ink" to "墨のような淫紋",
      "g2_im_shine" to "光が漏れる淫紋",
      "g2_im_particles" to "光の粒が舞う",
      "g2_im_aura" to "淫紋から立つ光",
      "g2_im_hot" to "熱で赤くなる淫紋",
      "g2_im_expand" to "広がる淫紋",
      "g2_im_multi" to "複数の淫紋",
    ),
  )

private fun optionGenre35(): CharacterOptionGenre =
  CharacterOptionGenre(
    "淫紋",
    listOf(
    optionPart170(),
    optionPart171(),
    optionPart172(),
    optionPart173(),
    ),
  )

private fun optionPart174(): CharacterOptionPart =
  CharacterOptionPart(
    "体型",
    false,
    listOf(
      "g2_bd_hourglass" to "砂時計体型",
      "g2_bd_pear" to "洋ナシ体型",
      "g2_bd_apple" to "りんご体型",
      "g2_bd_athletic" to "アスリート体型",
      "g2_bd_slim_thick" to "スリムなのにむっちり",
      "g2_bd_curvy" to "曲線的",
      "g2_bd_petite_adult" to "小柄な大人",
      "g2_bd_tall_adult" to "背の高い大人",
      "g2_bd_very_tall" to "とても背が高い",
      "g2_bd_long_legs" to "脚が長い",
      "g2_bd_short_legs" to "脚が短め",
      "g2_bd_chubby" to "ぽっちゃり",
      "g2_bd_plump" to "むちむち",
      "g2_bd_bbw" to "ふくよか",
      "g2_bd_skinny" to "細身",
      "g2_bd_lean" to "引き締まった",
      "g2_bd_toned" to "うっすら筋肉",
      "g2_bd_muscle" to "筋肉質",
      "g2_bd_abs" to "腹筋が割れた",
      "g2_bd_abs_light" to "うっすら腹筋",
      "g2_bd_biceps" to "力こぶ",
      "g2_bd_thick_arms" to "太めの二の腕",
      "g2_bd_narrow_waist" to "ウエストが細い",
      "g2_bd_wide_hip" to "腰骨が張る",
      "g2_bd_thigh_gap" to "すき間のある太もも",
      "g2_bd_thick_thigh" to "太ももが太い",
      "g2_bd_thin_thigh" to "細い脚",
      "g2_bd_calf" to "ふくらはぎが締まる",
      "g2_bd_muscle_leg" to "筋肉質な脚",
      "g2_bd_dimples" to "腰のえくぼ",
      "g2_bd_hip_dips" to "ヒップディップ",
      "g2_bd_bubble" to "丸く盛り上がったお尻",
      "g2_bd_flat_butt" to "ぺたんとしたお尻",
      "g2_bd_large_butt" to "大きなお尻",
      "g2_bd_perky" to "上向きのお尻",
      "g2_bd_belly_soft" to "やわらかいおなか",
      "g2_bd_belly_small" to "ぽこっとしたおなか",
      "g2_bd_navel_deep" to "縦長のへそ",
      "g2_bd_innie" to "へそのくぼみ",
    ),
  )

private fun optionPart175(): CharacterOptionPart =
  CharacterOptionPart(
    "胸",
    false,
    listOf(
      "g2_bd_breast_small" to "小ぶりな胸",
      "g2_bd_breast_medium" to "ふつうの胸",
      "g2_bd_breast_large" to "大きな胸",
      "g2_bd_breast_huge" to "巨大な胸",
      "g2_bd_breast_gigantic" to "超巨大な胸",
      "g2_bd_breast_flat" to "ぺったんこの胸",
      "g2_bd_breast_saggy" to "垂れた胸",
      "g2_bd_breast_perky" to "上向きの胸",
      "g2_bd_breast_round" to "丸い胸",
      "g2_bd_breast_teardrop" to "しずく型の胸",
      "g2_bd_breast_wide" to "離れた胸",
      "g2_bd_breast_close" to "寄った胸",
      "g2_bd_breast_asym" to "左右差のある胸",
      "g2_bd_breast_bounce" to "揺れる胸",
      "g2_bd_breast_press" to "押しつぶされた胸",
      "g2_bd_breast_under" to "下乳",
      "g2_bd_breast_side" to "横乳",
      "g2_bd_breast_cleavage" to "谷間",
      "g2_bd_nip_puffy" to "ふくらんだ乳輪",
      "g2_bd_nip_inverted" to "陥没乳首",
      "g2_bd_nip_large" to "大きめの乳輪",
      "g2_bd_nip_small" to "小さめの乳首",
      "g2_bd_nip_dark" to "濃い色の乳首",
      "g2_bd_nip_pink" to "ピンクの乳首",
      "g2_bd_nip_long" to "長い乳首",
      "g2_bd_nip_erect" to "立った乳首",
    ),
  )

private fun optionPart176(): CharacterOptionPart =
  CharacterOptionPart(
    "肌と体液の見た目",
    false,
    listOf(
      "g2_sk_fair" to "明るい肌",
      "g2_sk_olive" to "オリーブ肌",
      "g2_sk_ebony" to "黒檀の肌",
      "g2_sk_shiny" to "つやつやの肌",
      "g2_sk_oily" to "油っぽい肌",
      "g2_sk_matte" to "マットな肌",
      "g2_sk_soft" to "やわらかそうな肌",
      "g2_sk_glow" to "内側から光る肌",
      "g2_sk_blush_all" to "全身ほんのり赤い",
      "g2_sk_vein" to "うっすら血管",
      "g2_sk_stretch" to "肌のすじ（伸びあと）",
      "g2_sk_cellulite" to "セルライト",
      "g2_sk_mole_many" to "ほくろが多い",
      "g2_sk_freckles_heavy" to "そばかすたっぷり",
      "g2_sk_freckles_body" to "体のそばかす",
      "g2_sk_vitiligo" to "白斑",
      "g2_sk_birthmark" to "あざ（生まれつき）",
      "g2_sk_beauty_belly" to "おなかのほくろ",
      "g2_sk_tan_bikini" to "ビキニの日焼けあと",
      "g2_sk_tan_swim" to "水着の日焼けあと",
      "g2_sk_tan_shorts" to "ショーツの日焼けあと",
      "g2_sk_tan_bra" to "ブラの日焼けあと",
      "g2_sk_tan_sock" to "ソックスの日焼けあと",
      "g2_sk_tan_watch" to "腕時計の日焼けあと",
      "g2_sk_tan_gloves" to "手袋の日焼けあと",
      "g2_sk_tan_strap" to "ストラップの日焼けあと",
      "g2_sk_tan_heart" to "ハート型の日焼けあと",
      "g2_sk_tan_stripes" to "縞の日焼けあと",
      "g2_sk_sweat_fine" to "うっすら汗",
      "g2_sk_sweat_wet" to "汗だく",
      "g2_sk_sweat_drip" to "汗がしたたる",
      "g2_sk_sweat_chest" to "胸の汗",
      "g2_sk_sweat_back" to "背中の汗",
      "g2_sk_sweat_neck" to "首すじの汗",
      "g2_sk_wet_skin" to "濡れた肌",
      "g2_sk_wet_body" to "全身ずぶ濡れ",
      "g2_sk_oiled" to "オイルでつやつや",
      "g2_sk_lotion" to "ローションでぬるぬる",
      "g2_sk_steam" to "湯気の立つ肌",
      "g2_sk_droplets" to "水滴",
      "g2_sk_drool_light" to "よだれ（口の端）",
      "g2_sk_drool_long" to "長い糸のよだれ",
      "g2_sk_tear_trail" to "涙のあと",
      "g2_sk_tear_drop" to "ひとすじの涙",
      "g2_sk_tear_many" to "ぽろぽろ涙",
      "g2_sk_snot" to "鼻水",
    ),
  )

private fun optionPart177(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー",
    false,
    listOf(
      "g2_tt_back" to "背中一面のタトゥー",
      "g2_tt_chest" to "胸のタトゥー",
      "g2_tt_thigh" to "太もものタトゥー",
      "g2_tt_rib" to "あばらのタトゥー",
      "g2_tt_hand" to "手の甲のタトゥー",
      "g2_tt_finger" to "指のタトゥー",
      "g2_tt_neck_back" to "うなじのタトゥー",
      "g2_tt_ankle" to "足首のタトゥー",
      "g2_tt_hip" to "腰のタトゥー",
      "g2_tt_lower_back" to "腰のトライバル",
      "g2_tt_irezumi" to "和彫り",
      "g2_tt_floral" to "花のタトゥー",
      "g2_tt_script" to "文字のタトゥー",
      "g2_tt_minimal" to "ワンポイントタトゥー",
      "g2_tt_geometric" to "幾何学タトゥー",
      "g2_tt_watercolor" to "水彩風タトゥー",
      "g2_tt_blackwork" to "ブラックワーク",
      "g2_tt_uv" to "光るタトゥー",
      "g2_tt_henna_like" to "ヘナ風タトゥー",
    ),
  )

private fun optionPart178(): CharacterOptionPart =
  CharacterOptionPart(
    "ピアス",
    false,
    listOf(
      "g2_pc_ear_many" to "耳のピアスがたくさん",
      "g2_pc_ear_cartilage" to "耳の軟骨ピアス",
      "g2_pc_industrial" to "インダストリアルピアス",
      "g2_pc_tragus" to "トラガスピアス",
      "g2_pc_nose_ring" to "鼻輪",
      "g2_pc_labret" to "ラブレット",
      "g2_pc_navel_dangle" to "へそピアス（ぶら下がり）",
      "g2_pc_nipple_bar" to "乳首バーベル",
      "g2_pc_nipple_ring" to "乳首リング",
      "g2_pc_nipple_chain" to "乳首チェーン",
      "g2_pc_vch" to "クリフードのピアス",
      "g2_pc_labia" to "陰唇のピアス",
      "g2_pc_surface" to "サーフェスピアス",
      "g2_pc_bridge" to "ブリッジピアス",
      "g2_pc_gauge" to "ストレッチピアス",
      "g2_pc_septum_ring" to "セプタムリング",
    ),
  )

private fun optionPart179(): CharacterOptionPart =
  CharacterOptionPart(
    "体毛",
    false,
    listOf(
      "g2_hr_armpit_none" to "ワキ毛なし（つるつる）",
      "g2_hr_pubic_trim" to "整えた下の毛",
      "g2_hr_pubic_heavy" to "濃い下の毛",
      "g2_hr_pubic_heart" to "ハート型の下の毛",
      "g2_hr_pubic_landing" to "ランディングストリップ",
      "g2_hr_arm" to "腕の毛",
      "g2_hr_peach" to "うぶ毛",
      "g2_hr_trail" to "おなかの毛",
      "g2_hr_smooth" to "全身つるつる",
    ),
  )

private fun optionPart180(): CharacterOptionPart =
  CharacterOptionPart(
    "体質・特徴",
    false,
    listOf(
      "g2_fe_flexible" to "体がやわらかい",
      "g2_fe_stiff" to "体がかたい",
      "g2_fe_sens" to "敏感な体",
      "g2_fe_ticklish" to "くすぐったがり",
      "g2_fe_pale_veins" to "青い血管の透けた肌",
      "g2_fe_muscle_vein" to "筋肉の血管",
      "g2_fe_shortstack" to "低身長でむっちり",
      "g2_fe_collarbone" to "はっきりした鎖骨",
      "g2_fe_ribs" to "あばらが浮く",
      "g2_fe_hipbone" to "腰骨が浮く",
      "g2_fe_shoulderblade" to "肩甲骨がきれい",
      "g2_fe_spine" to "背骨のライン",
      "g2_fe_pits" to "ワキのくぼみ",
      "g2_fe_feet_arch" to "足の土踏まず",
      "g2_fe_toes" to "足の指がきれい",
      "g2_fe_soles" to "足の裏",
      "g2_fe_knees" to "膝のくぼみ",
    ),
  )

private fun optionGenre36(): CharacterOptionGenre =
  CharacterOptionGenre(
    "身体の調整・オプション（追加）",
    listOf(
    optionPart174(),
    optionPart175(),
    optionPart176(),
    optionPart177(),
    optionPart178(),
    optionPart179(),
    optionPart180(),
    ),
  )

private fun optionPart181(): CharacterOptionPart =
  CharacterOptionPart(
    "発熱・風邪（見た目のみ）",
    false,
    listOf(
      "g2_ill_fever" to "熱で赤い顔",
      "g2_ill_fever_high" to "高熱でぼうっと",
      "g2_ill_chills" to "寒気で震える",
      "g2_ill_cold" to "風邪気味",
      "g2_ill_sneeze" to "くしゃみ",
      "g2_ill_cough" to "せき",
      "g2_ill_cough_hand" to "口を押さえてせき",
      "g2_ill_runny" to "鼻をかむ",
      "g2_ill_nosered" to "鼻が赤い",
      "g2_ill_mask" to "マスク（風邪）",
      "g2_ill_thermo" to "体温計をくわえる",
      "g2_ill_cooling" to "冷却シート",
      "g2_ill_icepack" to "氷のうを頭に",
      "g2_ill_blanket" to "毛布にくるまる",
      "g2_ill_bed" to "寝込む",
      "g2_ill_tissue" to "ティッシュの山",
      "g2_ill_sore" to "のどが痛い",
      "g2_ill_hoarse" to "声がかれる",
      "g2_ill_headache" to "頭痛",
      "g2_ill_dizzy" to "めまい",
      "g2_ill_stagger" to "ふらつく",
      "g2_ill_faint_lean" to "壁にもたれて息をつく",
      "g2_ill_pale" to "青白い顔",
      "g2_ill_sweat_cold" to "冷や汗",
      "g2_ill_gasp" to "息切れ",
      "g2_ill_heavy_breath" to "荒い呼吸",
      "g2_ill_heartbeat" to "胸を押さえる",
      "g2_ill_nausea" to "気分が悪い",
      "g2_ill_hand_mouth" to "口を押さえる",
      "g2_ill_exhaust" to "ぐったり疲れた",
      "g2_ill_tired_eyes" to "疲れた目",
      "g2_ill_weak" to "力が抜ける",
      "g2_ill_drowsy" to "眠気",
      "g2_ill_nod" to "うとうと",
      "g2_ill_wake" to "寝起き",
      "g2_ill_eyerub" to "目をこする",
      "g2_ill_sleepwalk" to "寝ぼけ顔",
      "g2_ill_hangover" to "二日酔い",
      "g2_ill_drunk" to "酔ってふらふら",
      "g2_ill_drunk_red" to "酔って赤い顔",
      "g2_ill_drunk_loose" to "酔ってゆるんだ顔",
      "g2_ill_shake" to "震える手",
      "g2_ill_shake_body" to "体が震える",
      "g2_ill_hot_summer" to "暑さでばてる",
      "g2_ill_cool_down" to "うちわであおぐ",
      "g2_ill_sunstroke" to "熱中症気味",
      "g2_ill_cold_winter" to "寒さで赤い鼻",
      "g2_ill_aching" to "体が重い",
      "g2_ill_cramp" to "お腹をおさえる",
      "g2_ill_period" to "生理痛でつらい",
      "g2_ill_hiccup" to "しゃっくり",
      "g2_ill_burp" to "げっぷをがまん",
      "g2_ill_allergy" to "花粉症",
      "g2_ill_sneeze_tears" to "くしゃみで涙目",
      "g2_ill_stuffy" to "鼻づまり",
      "g2_ill_insomnia" to "寝不足",
      "g2_ill_fatigue_work" to "過労",
      "g2_ill_dehydrate" to "ぐったり（脱水）",
      "g2_ill_faint" to "立ちくらみ",
    ),
  )

private fun optionGenre37(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体調状態",
    listOf(
    optionPart181(),
    ),
  )

private fun optionPart182(): CharacterOptionPart =
  CharacterOptionPart(
    "麻痺・しびれ（RPGの状態異常風）",
    false,
    listOf(
      "g2_pa_paralyzed" to "麻痺",
      "g2_pa_numb" to "しびれて動けない",
      "g2_pa_limp" to "力が入らない",
      "g2_pa_weakkneed" to "膝が笑う",
      "g2_pa_collapse" to "崩れ落ちる",
      "g2_pa_slump" to "ぐったり座り込む",
      "g2_pa_wall" to "壁にずるずる寄りかかる",
      "g2_pa_lie" to "倒れて動けない",
      "g2_pa_arm_limp" to "腕が上がらない",
      "g2_pa_hand_drop" to "手から物が落ちる",
      "g2_pa_finger" to "指先がしびれる",
      "g2_pa_twitch" to "ぴくぴくけいれん",
      "g2_pa_shock" to "電気でしびれる",
      "g2_pa_thunder" to "雷魔法でしびれる",
      "g2_pa_spark_skin" to "肌の上を走る電気",
      "g2_pa_stunned" to "スタン状態",
      "g2_pa_dazed" to "ぼうっとして動けない",
      "g2_pa_frozen" to "凍りついたように動けない",
      "g2_pa_freeze_ice" to "氷で動けない",
      "g2_pa_petrify" to "石化",
      "g2_pa_petrify_half" to "石化（半身）",
      "g2_pa_petrify_hand" to "石化（手足）",
      "g2_pa_slow" to "スロウ（動きが遅い）",
      "g2_pa_sleep_spell" to "眠りの魔法",
      "g2_pa_charm" to "魅了の魔法",
      "g2_pa_confuse" to "混乱",
      "g2_pa_dizzy_stars" to "目を回す",
      "g2_pa_bind_magic" to "光の拘束魔法",
      "g2_pa_web" to "蜘蛛の糸で動けない",
      "g2_pa_slime_stuck" to "スライムで動けない",
      "g2_pa_mud" to "泥にはまって動けない",
      "g2_pa_toxic" to "毒で力が抜ける（見た目だけ）",
      "g2_pa_poison_purple" to "紫のもや（毒状態）",
      "g2_pa_status_icon" to "状態異常アイコン",
      "g2_pa_stun_stars" to "頭上で回る星",
      "g2_pa_sigh" to "声も出ない",
      "g2_pa_eyes_open" to "目だけ動く",
      "g2_pa_mouth_numb" to "口がしびれて話せない",
      "g2_pa_kneel" to "膝をついて動けない",
      "g2_pa_prop" to "剣で体を支える",
      "g2_pa_stagger_hold" to "壁につかまる",
      "g2_pa_flush" to "麻痺で顔が赤い",
      "g2_pa_sweat" to "麻痺で汗ばむ",
      "g2_pa_gasp" to "息だけ荒い",
      "g2_pa_helpless" to "身動きできず見上げる",
      "g2_pa_floor_look" to "床で見上げる",
      "g2_pa_hand_reach" to "手だけ伸ばす",
      "g2_pa_sparkle" to "麻痺の火花",
      "g2_pa_yellow_aura" to "黄色い光につつまれる",
      "g2_pa_magic_circle" to "足元の魔法陣",
      "g2_pa_bound_light" to "光の輪で固定",
      "g2_pa_slowtime" to "時間が止まったよう",
    ),
  )

private fun optionGenre38(): CharacterOptionGenre =
  CharacterOptionGenre(
    "麻痺状態",
    listOf(
    optionPart182(),
    ),
  )

private fun optionPart183(): CharacterOptionPart =
  CharacterOptionPart(
    "足・脚のフェチ",
    false,
    listOf(
      "g3_fet_soles" to "足の裏を見せる",
      "g3_fet_toes_spread" to "足指を広げる",
      "g3_fet_toe_curl" to "足指を丸める",
      "g3_fet_arch" to "土踏まずのアップ",
      "g3_fet_ankle" to "足首のライン",
      "g3_fet_calf" to "ふくらはぎ",
      "g3_fet_thigh_gap" to "太もものすき間",
      "g3_fet_thighs_squeeze" to "太ももを寄せる",
      "g3_fet_knees" to "ひざ裏",
      "g3_fet_heel_dangle" to "ヒールをぶらぶら",
      "g3_fet_wet_footprint" to "濡れた足跡",
      "g3_fet_stocking_toe" to "つま先の透け",
      "g3_fet_foot_close" to "足裏のクローズアップ",
    ),
  )

private fun optionPart184(): CharacterOptionPart =
  CharacterOptionPart(
    "上半身のフェチ",
    false,
    listOf(
      "g3_fet_armpit" to "脇を見せる",
      "g3_fet_armpit_one" to "片腕を上げる脇",
      "g3_fet_nape_hair_up" to "髪をまとめたうなじ",
      "g3_fet_collarbone" to "鎖骨",
      "g3_fet_shoulder_blade" to "肩甲骨",
      "g3_fet_back_line" to "背中のライン",
      "g3_fet_back_dimples" to "腰のくぼみ",
      "g3_fet_hands" to "手のアップ",
      "g3_fet_fingers_long" to "細く長い指",
      "g3_fet_glove_hand" to "手袋の手",
      "g3_fet_wrist" to "手首",
      "g3_fet_ears" to "耳のアップ",
      "g3_fet_earlobe" to "耳たぶ",
      "g3_fet_lips" to "唇のアップ",
      "g3_fet_navel" to "おへそ",
      "g3_fet_abs" to "腹筋",
      "g3_fet_waist" to "くびれ",
      "g3_fet_hip_bones" to "腰骨",
      "g3_fet_hip_dips" to "骨盤のライン",
      "g3_fet_veins" to "浮き出る血管",
    ),
  )

private fun optionPart185(): CharacterOptionPart =
  CharacterOptionPart(
    "髪・顔・声のフェチ",
    false,
    listOf(
      "g3_fet_hair_long_flow" to "長い髪を流す",
      "g3_fet_hair_smell" to "髪を嗅ぐしぐさ",
      "g3_fet_hair_brush" to "髪を梳く",
      "g3_fet_hair_ponytail_swing" to "揺れるポニーテール",
      "g3_fet_hair_wet" to "濡れ髪",
      "g3_fet_hair_tuck" to "髪を耳にかける",
      "g3_fet_hair_loose" to "ほつれ毛",
      "g3_fet_hair_braid_long" to "長い三つ編み",
      "g3_fet_hair_armpit" to "腋毛",
      "g3_fet_mole_chest" to "胸元のほくろ",
      "g3_fet_glasses_fog" to "眼鏡の曇り",
      "g3_fet_glasses_off" to "眼鏡を外す",
      "g3_fet_tired_eyes" to "眠そうな目",
      "g3_fet_heavy_breath" to "荒い息",
      "g3_fet_whisper" to "耳元でささやく",
      "g3_fet_voice_low" to "低い声のイメージ",
      "g3_fet_voice_sweet" to "甘い声のイメージ",
      "g3_fet_voice_hoarse" to "かすれた声",
      "g3_fet_sleep_face" to "寝顔",
      "g3_fet_sleep_drool" to "寝ながらよだれ",
    ),
  )

private fun optionPart186(): CharacterOptionPart =
  CharacterOptionPart(
    "汗・濡れ・体温のフェチ",
    false,
    listOf(
      "g3_fet_sweat_neck" to "首筋の汗",
      "g3_fet_sweat_shirt" to "汗で張りつく服",
      "g3_fet_after_workout" to "運動後の汗",
      "g3_fet_wet_clothes" to "濡れた服",
      "g3_fet_wet_shirt" to "濡れ透けシャツ",
      "g3_fet_rain_soaked" to "雨でずぶ濡れ",
      "g3_fet_steam" to "湯気に包まれる",
      "g3_fet_oil_skin" to "オイルで光る肌",
      "g3_fet_lotion" to "ローションを塗る",
      "g3_fet_sunscreen" to "日焼け止めを塗る",
      "g3_fet_flushed" to "火照った肌",
      "g3_fet_shiver" to "ぞくっと震える",
      "g3_fet_condensation" to "冷たい飲み物の水滴",
    ),
  )

private fun optionPart187(): CharacterOptionPart =
  CharacterOptionPart(
    "着衣・半脱ぎのフェチ",
    false,
    listOf(
      "g3_fet_half_undressed" to "半脱ぎ",
      "g3_fet_shirt_unbutton" to "ボタンを外しかけ",
      "g3_fet_shirt_slip" to "肩からずり落ちる服",
      "g3_fet_strap_slip" to "肩ひもが落ちる",
      "g3_fet_skirt_pinch" to "スカートをつまむ",
      "g3_fet_wind_skirt" to "風でめくれる裾",
      "g3_fet_clothes_pull" to "服を引っぱる",
      "g3_fet_pantyhose_tear" to "破れたタイツ",
      "g3_fet_opaque_tights" to "黒タイツ",
      "g3_fet_knee_socks" to "ニーソックス",
      "g3_fet_zettai" to "絶対領域",
      "g3_fet_oversized_shirt" to "彼シャツ",
      "g3_fet_oversized_sweater" to "ぶかぶかニット",
      "g3_fet_sleeves_long" to "萌え袖",
      "g3_fet_towel_only" to "バスタオル一枚",
      "g3_fet_open_robe" to "はだけたバスローブ",
      "g3_fet_yukata_loose" to "着崩れた浴衣",
      "g3_fet_kimono_nape" to "着物のうなじ",
      "g3_fet_lingerie_sheer" to "透けるランジェリー",
      "g3_fet_slip_dress" to "スリップ",
      "g3_fet_latex_full" to "ラテックスの全身",
      "g3_fet_suit_woman" to "スーツ姿",
      "g3_fet_nurse" to "ナース服",
      "g3_fet_maid" to "メイド服",
      "g3_fet_bunny_suit" to "バニースーツ",
      "g3_fet_open_back" to "背中の開いた服",
      "g3_fet_keyhole_sweater" to "穴あきニット",
      "g3_fet_side_boob" to "横乳が見える服",
    ),
  )

private fun optionPart188(): CharacterOptionPart =
  CharacterOptionPart(
    "体格・筋肉・サイズ差のフェチ",
    false,
    listOf(
      "g3_fet_height_diff" to "身長差（背伸び）",
      "g3_fet_size_diff" to "体格差",
      "g3_fet_tall_woman" to "高身長の女性",
      "g3_fet_muscle_woman" to "筋肉質の女性",
      "g3_fet_abs_woman" to "腹筋が割れた女性",
      "g3_fet_amazon" to "アマゾネス体型",
      "g3_fet_back_muscle" to "広い背中",
      "g3_fet_thick_thigh" to "むっちり太もも",
      "g3_fet_chubby" to "ぽっちゃり体型",
      "g3_fet_soft_body" to "やわらかい体",
      "g3_fet_mature_figure" to "熟れた体つき",
      "g3_fet_older_woman" to "年上のお姉さん",
      "g3_fet_princess_carry" to "お姫様だっこ",
      "g3_fet_lifted" to "持ち上げられる",
      "g3_fet_big_arms_hug" to "大きな腕に包まれる",
      "g3_fet_kabedon" to "壁ドン",
      "g3_fet_chin_lift" to "あごクイ",
      "g3_fet_lap_sit" to "ひざの上に座る",
      "g3_fet_lap_pillow" to "ひざ枕",
      "g3_fet_ear_clean" to "耳かき",
    ),
  )

private fun optionPart189(): CharacterOptionPart =
  CharacterOptionPart(
    "観察・露出・羞恥のフェチ",
    false,
    listOf(
      "g3_fet_peek_door" to "ドアの隙間から見る",
      "g3_fet_peek_mirror" to "鏡越しの視線",
      "g3_fet_gaze_caught" to "見られて気づく",
      "g3_fet_gaze_shy" to "視線に恥じらう",
      "g3_fet_cover_chest" to "手で隠す",
      "g3_fet_cover_skirt" to "裾を押さえる",
      "g3_fet_cover_towel" to "タオルで隠す",
      "g3_fet_shy_exposure" to "恥ずかしい露出",
      "g3_fet_balcony" to "ベランダの薄着",
      "g3_fet_window_light" to "窓辺の薄着",
      "g3_fet_curtain_silhouette" to "カーテン越しのシルエット",
      "g3_fet_coat_only" to "コートの下は薄着",
      "g3_fet_changing" to "着替えの途中",
      "g3_fet_fitting_room" to "試着室",
      "g3_fet_beach_change" to "海辺で着替え",
      "g3_fet_onsen_steam" to "温泉の湯けむり",
      "g3_fet_bubble_bath" to "泡風呂",
      "g3_fet_shower_glass" to "曇りガラス越しのシャワー",
      "g3_fet_sweat_blush" to "恥ずかしさで汗",
      "g3_fet_pov_close" to "ぐっと寄る視点",
    ),
  )

private fun optionGenre39(): CharacterOptionGenre =
  CharacterOptionGenre(
    "マニアック（フェチ）",
    listOf(
    optionPart183(),
    optionPart184(),
    optionPart185(),
    optionPart186(),
    optionPart187(),
    optionPart188(),
    optionPart189(),
    ),
  )

private fun optionPart190(): CharacterOptionPart =
  CharacterOptionPart(
    "獣人・動物の特徴",
    false,
    listOf(
      "g3_fet2_wolf_girl" to "狼の獣人",
      "g3_fet2_fox_girl" to "狐の獣人",
      "g3_fet2_kitsune_nine" to "九尾",
      "g3_fet2_cat_beast" to "猫の獣人",
      "g3_fet2_dog_beast" to "犬の獣人",
      "g3_fet2_rabbit_beast" to "うさぎの獣人",
      "g3_fet2_tiger_beast" to "虎の獣人",
      "g3_fet2_lion_beast" to "獅子の獣人",
      "g3_fet2_bear_beast" to "熊の獣人",
      "g3_fet2_deer_beast" to "鹿の獣人",
      "g3_fet2_cow_beast" to "牛の獣人",
      "g3_fet2_sheep_beast" to "羊の獣人",
      "g3_fet2_goat_beast" to "山羊の獣人",
      "g3_fet2_horse_beast" to "馬の獣人",
      "g3_fet2_mouse_beast" to "ネズミの獣人",
      "g3_fet2_squirrel_beast" to "リスの獣人",
      "g3_fet2_raccoon_beast" to "たぬきの獣人",
      "g3_fet2_panda_beast" to "パンダの獣人",
      "g3_fet2_bird_beast" to "鳥の獣人",
      "g3_fet2_owl_beast" to "フクロウの獣人",
      "g3_fet2_bat_beast" to "コウモリの獣人",
      "g3_fet2_reptile_beast" to "トカゲの獣人",
      "g3_fet2_furry_body" to "全身ふさふさの毛",
      "g3_fet2_paw_hands" to "肉球の手",
      "g3_fet2_digitigrade" to "獣の脚",
      "g3_fet2_beast_mane" to "ふさふさのたてがみ",
      "g3_fet2_whiskers" to "ひげ",
    ),
  )

private fun optionPart191(): CharacterOptionPart =
  CharacterOptionPart(
    "モンスター娘・亜人",
    false,
    listOf(
      "g3_fet2_centaur" to "ケンタウロス",
      "g3_fet2_arachne" to "アラクネ（蜘蛛）",
      "g3_fet2_scylla" to "スキュラ",
      "g3_fet2_alraune" to "アルラウネ（花）",
      "g3_fet2_dryad" to "ドライアド",
      "g3_fet2_kappa" to "河童娘",
      "g3_fet2_oni" to "鬼娘",
      "g3_fet2_yuki_onna" to "雪女",
      "g3_fet2_tengu" to "天狗",
      "g3_fet2_nekomata" to "猫又",
      "g3_fet2_rokurokubi" to "ろくろ首",
      "g3_fet2_jorogumo" to "女郎蜘蛛",
      "g3_fet2_succubus" to "サキュバス",
      "g3_fet2_dhampir" to "半吸血鬼",
      "g3_fet2_elf" to "エルフ",
      "g3_fet2_dwarf_girl" to "ドワーフ（成人）",
      "g3_fet2_orc_girl" to "オーク娘",
      "g3_fet2_goblin_girl" to "ゴブリン娘（成人）",
      "g3_fet2_ogre_girl" to "オーガ娘",
      "g3_fet2_troll_girl" to "トロール娘",
      "g3_fet2_lizardfolk" to "リザードマン娘",
      "g3_fet2_dragon_girl" to "ドラゴン娘",
      "g3_fet2_demon_girl" to "悪魔娘",
      "g3_fet2_fallen_angel" to "堕天使",
      "g3_fet2_valkyrie" to "戦乙女",
      "g3_fet2_gorgon" to "メデューサ",
      "g3_fet2_sphinx" to "スフィンクス",
      "g3_fet2_siren" to "セイレーン",
      "g3_fet2_naga" to "ナーガ",
      "g3_fet2_insect_girl" to "昆虫娘",
      "g3_fet2_mantis_girl" to "カマキリ娘",
      "g3_fet2_bee_girl" to "ミツバチ娘",
      "g3_fet2_butterfly_girl" to "蝶の娘",
      "g3_fet2_moth_girl" to "蛾の娘",
      "g3_fet2_ant_girl" to "アリ娘",
      "g3_fet2_scorpion_girl" to "サソリ娘",
      "g3_fet2_crab_girl" to "カニ娘",
      "g3_fet2_octopus_girl" to "タコ娘",
      "g3_fet2_jellyfish_girl" to "クラゲ娘",
      "g3_fet2_shark_girl" to "サメ娘",
      "g3_fet2_turtle_girl" to "カメ娘",
      "g3_fet2_frog_girl" to "カエル娘",
      "g3_fet2_snake_girl" to "蛇娘",
    ),
  )

private fun optionPart192(): CharacterOptionPart =
  CharacterOptionPart(
    "身体の変化・異形",
    false,
    listOf(
      "g3_fet2_horns_long" to "長い角",
      "g3_fet2_horns_small" to "小さな角",
      "g3_fet2_antlers" to "枝分かれした角",
      "g3_fet2_halo_broken" to "欠けた光の輪",
      "g3_fet2_extra_ears" to "耳が四つ",
      "g3_fet2_third_eye" to "三つ目",
      "g3_fet2_multi_eyes" to "多くの目",
      "g3_fet2_four_arms" to "四本の腕",
      "g3_fet2_six_arms" to "六本の腕",
      "g3_fet2_extra_tails" to "複数の尻尾",
      "g3_fet2_long_tail" to "長い尻尾",
      "g3_fet2_prehensile_tail" to "巻きつく尻尾",
      "g3_fet2_tail_ribbon" to "飾りつき尻尾",
      "g3_fet2_wing_big" to "大きな翼",
      "g3_fet2_wing_four" to "四枚の翼",
      "g3_fet2_wing_black" to "黒い羽根",
      "g3_fet2_wing_butterfly" to "蝶の羽",
      "g3_fet2_wing_clear" to "透明な羽",
      "g3_fet2_wing_crystal" to "水晶の翼",
      "g3_fet2_wing_flame" to "炎の翼",
      "g3_fet2_wing_wrap" to "翼で体を包む",
      "g3_fet2_scales_body" to "鱗の肌",
      "g3_fet2_scales_patch" to "ところどころ鱗",
      "g3_fet2_skin_purple" to "紫の肌",
      "g3_fet2_skin_gold" to "金色の肌",
      "g3_fet2_skin_stripes" to "縞模様の肌",
      "g3_fet2_skin_spots" to "斑点の肌",
      "g3_fet2_skin_crack" to "ひび割れた肌",
      "g3_fet2_skin_stone" to "石の肌",
      "g3_fet2_skin_wood" to "木の肌",
      "g3_fet2_skin_ice" to "氷の肌",
      "g3_fet2_skin_marble" to "大理石の肌",
      "g3_fet2_skin_porcelain" to "陶器の肌",
      "g3_fet2_glow_tattoo" to "光る紋様",
      "g3_fet2_fin_ears" to "ひれの耳",
      "g3_fet2_gills" to "えら",
      "g3_fet2_webbed" to "水かき",
      "g3_fet2_claws" to "鋭い爪",
      "g3_fet2_long_tongue" to "長い舌",
      "g3_fet2_forked_tongue" to "二股の舌",
      "g3_fet2_plant_vines" to "植物のつる",
      "g3_fet2_flower_hair" to "花の髪飾り",
      "g3_fet2_leaf_hair" to "葉の髪",
      "g3_fet2_moss_body" to "苔むした体",
      "g3_fet2_mushroom" to "キノコの帽子",
      "g3_fet2_flame_hair" to "炎の髪",
      "g3_fet2_water_hair" to "水の髪",
      "g3_fet2_star_hair" to "星空の髪",
      "g3_fet2_cloud_hair" to "雲の髪",
      "g3_fet2_electric" to "電気をまとう",
      "g3_fet2_glow_skin" to "光る肌",
      "g3_fet2_bioluminescent" to "発光する模様",
      "g3_fet2_transparent" to "透明な体",
      "g3_fet2_translucent" to "半透明の体",
      "g3_fet2_floating" to "宙に浮く",
      "g3_fet2_shadow_body" to "影の体",
      "g3_fet2_smoke_body" to "煙の体",
      "g3_fet2_liquid_body" to "液体の体",
      "g3_fet2_gel_body" to "ゼリーの体",
      "g3_fet2_metal_statue" to "金属の像",
      "g3_fet2_chimera" to "キメラ",
      "g3_fet2_heterochromia" to "異色の瞳",
      "g3_fet2_blank_eyes" to "白目のない瞳",
    ),
  )

private fun optionPart193(): CharacterOptionPart =
  CharacterOptionPart(
    "機械・サイボーグ・人工物",
    false,
    listOf(
      "g3_fet2_robot_girl" to "ロボット娘",
      "g3_fet2_mecha_eye" to "機械の瞳",
      "g3_fet2_visor" to "バイザー",
      "g3_fet2_mecha_ears" to "機械の耳",
      "g3_fet2_neck_ports" to "首のコネクタ",
      "g3_fet2_cables" to "ケーブルが伸びる",
      "g3_fet2_hologram" to "ホログラムの体",
      "g3_fet2_glitch" to "グリッチ",
      "g3_fet2_circuit_lines" to "回路の模様",
      "g3_fet2_doll_joints" to "球体関節人形",
      "g3_fet2_puppet" to "あやつり人形",
      "g3_fet2_clockwork" to "ぜんまい仕掛け",
      "g3_fet2_steampunk_arm" to "スチームパンクの義肢",
      "g3_fet2_living_armor" to "生きた鎧",
      "g3_fet2_golem" to "ゴーレム",
      "g3_fet2_mannequin" to "マネキン",
      "g3_fet2_toy_body" to "おもちゃの体",
      "g3_fet2_monitor_head" to "頭がモニター",
    ),
  )

private fun optionPart194(): CharacterOptionPart =
  CharacterOptionPart(
    "巨大・小さい・変身",
    false,
    listOf(
      "g3_fet2_giantess" to "巨大娘",
      "g3_fet2_giantess_city" to "街より大きい",
      "g3_fet2_giantess_hand" to "手のひらに乗せる",
      "g3_fet2_micro_adult" to "手のひらサイズの成人",
      "g3_fet2_pocket_tiny" to "ポケットの中",
      "g3_fet2_shrunken_adult" to "縮んだ大人",
      "g3_fet2_big_beast" to "大きな獣と人",
      "g3_fet2_transforming" to "変身の途中",
      "g3_fet2_half_beast" to "半分獣化",
      "g3_fet2_tail_grow" to "尻尾が生える",
      "g3_fet2_wing_sprout" to "翼が生える",
      "g3_fet2_turn_slime" to "スライム化",
      "g3_fet2_turn_crystal" to "結晶化",
      "g3_fet2_turn_plant" to "植物化",
      "g3_fet2_turn_ice" to "氷づけ",
      "g3_fet2_body_swap" to "入れ替わり",
      "g3_fet2_shapeshift" to "姿を変える",
      "g3_fet2_clones" to "分身",
      "g3_fet2_possessed" to "憑依",
      "g3_fet2_floating_parts" to "宙に浮く手",
    ),
  )

private fun optionGenre40(): CharacterOptionGenre =
  CharacterOptionGenre(
    "現実には不可（人外・非現実）",
    listOf(
    optionPart190(),
    optionPart191(),
    optionPart192(),
    optionPart193(),
    optionPart194(),
    ),
  )

private fun optionPart195(): CharacterOptionPart =
  CharacterOptionPart(
    "触手風の演出（飾り・植物）",
    false,
    listOf(
      "g3_fet3_tentacle_hair" to "髪が触手風",
      "g3_fet3_tentacle_ribbon" to "リボン状の触手風飾り",
      "g3_fet3_vine_wrap" to "つるが体に巻く",
      "g3_fet3_vine_arms" to "つるの腕",
      "g3_fet3_vine_flower" to "つるに咲く花",
      "g3_fet3_vine_leg" to "脚にからむつる",
      "g3_fet3_root_hair" to "根っこの髪",
      "g3_fet3_octopus_arms" to "タコの腕",
      "g3_fet3_deep_sea" to "深海の触手",
      "g3_fet3_pet_tentacle" to "ペットの触手",
      "g3_fet3_tentacle_tail" to "触手の尻尾",
      "g3_fet3_tentacle_wings" to "触手の翼",
      "g3_fet3_shadow_tentacle" to "影の触手",
      "g3_fet3_ink_tentacle" to "墨の触手",
      "g3_fet3_light_tentacle" to "光の触手",
      "g3_fet3_cloth_wrap" to "布が巻きつく",
      "g3_fet3_ribbon_bind" to "リボンが巻く",
      "g3_fet3_scarf_wrap" to "マフラーが巻く",
      "g3_fet3_hair_wrap" to "髪が体に巻く",
      "g3_fet3_chain_float" to "宙に浮く鎖",
    ),
  )

private fun optionPart196(): CharacterOptionPart =
  CharacterOptionPart(
    "アブノーマルな関係・仕草",
    false,
    listOf(
      "g3_fet3_master_servant" to "主従",
      "g3_fet3_pet_human" to "ペットとしての人",
      "g3_fet3_dress_doll" to "着せ替え人形",
      "g3_fet3_mannequin_pose" to "マネキンのポーズ",
      "g3_fet3_statue_pose" to "像のポーズ",
      "g3_fet3_hypno_swirl" to "ぐるぐる目",
      "g3_fet3_hypno_pendulum" to "振り子の催眠風",
      "g3_fet3_mind_blank" to "ぼんやりした表情",
      "g3_fet3_melting_face" to "とろけた表情",
      "g3_fet3_drunk" to "酔った様子",
      "g3_fet3_fever" to "熱でぼうっと",
      "g3_fet3_dazed" to "くらくら",
      "g3_fet3_lying_back" to "仰向けで見上げる",
      "g3_fet3_cuddle" to "ぴったり寄り添う",
      "g3_fet3_hug_back" to "背中から抱きしめる",
      "g3_fet3_hug_pillow" to "抱き枕にされる",
      "g3_fet3_lick_pose" to "なめる仕草",
      "g3_fet3_bite_finger" to "指をかむ",
      "g3_fet3_kiss_neck" to "首にキス",
      "g3_fet3_kiss_hand" to "手にキス",
      "g3_fet3_kiss_foot" to "足先にキス",
      "g3_fet3_offer_foot" to "足を差し出す",
      "g3_fet3_under_foot" to "足を見上げる",
      "g3_fet3_lick_screen" to "画面をなめる",
      "g3_fet3_ear_nibble" to "耳を甘がみ",
      "g3_fet3_scent" to "匂いを嗅ぐ",
      "g3_fet3_scent_blush" to "匂いで赤くなる",
      "g3_fet3_sniff_clothes" to "服の匂いを嗅ぐ",
      "g3_fet3_perfume" to "香水をつける",
      "g3_fet3_breath_fog" to "白い吐息",
      "g3_fet3_hot_breath" to "熱い吐息",
      "g3_fet3_feather_tickle" to "羽根でくすぐる",
      "g3_fet3_ice_cube" to "氷を肌に当てる",
      "g3_fet3_ice_lick" to "氷をなめる",
      "g3_fet3_cream_lick" to "クリームをなめる",
      "g3_fet3_honey_drip" to "はちみつが垂れる",
      "g3_fet3_melted_choco" to "とけたチョコ",
      "g3_fet3_candy_lick" to "飴をなめる",
      "g3_fet3_fruit_bite" to "果実をかじる",
      "g3_fet3_milk_spill" to "ミルクがこぼれる",
      "g3_fet3_wine_spill" to "ワインがこぼれる",
    ),
  )

private fun optionPart197(): CharacterOptionPart =
  CharacterOptionPart(
    "ボディペイント・装飾・濡れ演出",
    false,
    listOf(
      "g3_fet3_flower_cover" to "花で体を覆う",
      "g3_fet3_petal_bath" to "花びらの風呂",
      "g3_fet3_glitter_body" to "ラメの肌",
      "g3_fet3_pearl_chain" to "真珠の飾り",
      "g3_fet3_jewel_navel" to "おへその宝石",
      "g3_fet3_gold_paint" to "金のボディペイント",
      "g3_fet3_henna" to "ヘナ模様",
      "g3_fet3_stickers" to "飾りシール",
      "g3_fet3_bandage_fashion" to "包帯のファッション",
      "g3_fet3_tape_x" to "Xの絆創膏",
      "g3_fet3_clear_vinyl" to "透明ビニールの服",
      "g3_fet3_pvc_dress" to "ビニールのドレス",
      "g3_fet3_mud" to "泥んこ",
      "g3_fet3_foam" to "泡まみれ",
      "g3_fet3_water_balloon" to "水風船で濡れる",
      "g3_fet3_hose_water" to "ホースの水",
      "g3_fet3_sprinkler" to "スプリンクラー",
      "g3_fet3_poolside" to "プールサイドの濡れ",
      "g3_fet3_rain_shelter" to "雨宿り",
      "g3_fet3_snow_light" to "雪の中の薄着",
      "g3_fet3_cold_shiver" to "寒さで震える",
    ),
  )

private fun optionPart198(): CharacterOptionPart =
  CharacterOptionPart(
    "ギャップ・無防備",
    false,
    listOf(
      "g3_fet3_gap_cool_shy" to "クールなのに赤面",
      "g3_fet3_gap_tough_soft" to "強そうなのに照れる",
      "g3_fet3_gap_big_cute" to "大柄でかわいい",
      "g3_fet3_gap_glasses" to "眼鏡の下は派手",
      "g3_fet3_gap_sleepy" to "寝ぼけた無防備",
      "g3_fet3_gap_home" to "家での無防備な姿",
      "g3_fet3_gap_after_bath" to "湯上がり",
      "g3_fet3_gap_morning" to "朝の寝ぐせ",
      "g3_fet3_gap_cook" to "料理中のうしろ姿",
      "g3_fet3_gap_laundry" to "洗濯物を干す",
      "g3_fet3_gap_work_tired" to "仕事帰りの疲れ",
      "g3_fet3_gap_unmask" to "仮面を外す",
      "g3_fet3_gap_wig" to "ウィッグを外す",
      "g3_fet3_gap_makeup" to "化粧前後",
      "g3_fet3_gap_cosplay" to "コスプレ変身",
    ),
  )

private fun optionGenre41(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アブノーマル",
    listOf(
    optionPart195(),
    optionPart196(),
    optionPart197(),
    optionPart198(),
    ),
  )

private fun optionPart199(): CharacterOptionPart =
  CharacterOptionPart(
    "ローター・バイブ系の装着状態",
    false,
    listOf(
      "g3_toy_bullet_thigh" to "太ももにローター固定",
      "g3_toy_bullet_pocket" to "ポケットにリモコン",
      "g3_toy_egg_wire" to "コードつきの卵型",
      "g3_toy_egg_wireless" to "無線の卵型",
      "g3_toy_remote_hand" to "リモコンを握られる",
      "g3_toy_remote_phone" to "スマホ操作",
      "g3_toy_finger_vib" to "指サック型",
      "g3_toy_rabbit_vib" to "ラビット型",
      "g3_toy_wand_hold" to "電マを握る",
      "g3_toy_wand_thigh" to "電マを太ももへ",
      "g3_toy_wand_head" to "電マの先端アップ",
      "g3_toy_massager_small" to "小型マッサージャー",
      "g3_toy_dual_stim" to "ダブル刺激型",
      "g3_toy_gspot_curve" to "曲がった形",
      "g3_toy_clit_suction" to "吸引器",
      "g3_toy_suction_close" to "吸引器のアップ",
      "g3_toy_air_pulse" to "空気の脈動型",
      "g3_toy_tongue_vib" to "舌型",
      "g3_toy_panty_vib" to "下着型バイブ",
      "g3_toy_panty_remote" to "遠隔バイブの下着",
      "g3_toy_vib_bra" to "振動ブラ",
      "g3_toy_vib_ring" to "振動リング",
      "g3_toy_vib_belt" to "ベルト固定のバイブ",
      "g3_toy_vib_bed" to "寝具に置かれたバイブ",
      "g3_toy_vib_hand" to "バイブを手に持つ",
      "g3_toy_vib_box" to "おもちゃ箱",
      "g3_toy_vib_charge" to "充電中のおもちゃ",
      "g3_toy_vib_shy" to "おもちゃを見て照れる",
      "g3_toy_vib_shop" to "おもちゃ売り場",
      "g3_toy_unboxing" to "開封する",
    ),
  )

private fun optionPart200(): CharacterOptionPart =
  CharacterOptionPart(
    "ディルド・ボール・プラグ系の装着状態",
    false,
    listOf(
      "g3_toy_dildo_suction" to "吸盤つきディルド",
      "g3_toy_dildo_glass" to "ガラス製",
      "g3_toy_dildo_silicone" to "シリコン製",
      "g3_toy_dildo_double" to "両端型",
      "g3_toy_dildo_pink" to "ピンク色",
      "g3_toy_dildo_black" to "黒色",
      "g3_toy_dildo_clear" to "透明",
      "g3_toy_dildo_strapon" to "ペニスバンド",
      "g3_toy_dildo_harness_hip" to "腰ベルト",
      "g3_toy_dildo_holding" to "両手で持つ",
      "g3_toy_dildo_stand" to "机に立つ",
      "g3_toy_ball_beads" to "玉つなぎ",
      "g3_toy_ball_chain" to "ボールチェーン",
      "g3_toy_ball_pull" to "引き抜く寸前",
      "g3_toy_ball_kegel" to "ケーゲルボール",
      "g3_toy_ball_silver" to "銀の玉",
      "g3_toy_ball_hand" to "玉を手のひらに",
      "g3_toy_plug_jewel" to "宝石つきプラグ",
      "g3_toy_plug_tail" to "尻尾つきプラグ",
      "g3_toy_plug_cat_tail" to "猫尻尾プラグ",
      "g3_toy_plug_dog_tail" to "犬尻尾プラグ",
      "g3_toy_plug_bunny_tail" to "うさぎ尻尾プラグ",
      "g3_toy_plug_heart" to "ハート型",
      "g3_toy_plug_steel" to "金属製",
      "g3_toy_plug_glass" to "ガラス製プラグ",
      "g3_toy_plug_silicone" to "シリコン製プラグ",
      "g3_toy_plug_vib" to "振動プラグ",
      "g3_toy_plug_inflate" to "ふくらむ型",
      "g3_toy_plug_display" to "並べたプラグ",
      "g3_toy_plug_base" to "底面の宝石",
      "g3_toy_plug_wear" to "装着した後ろ姿",
      "g3_toy_belt_chastity" to "貞操帯つき",
      "g3_toy_belt_key" to "鍵を持つ人",
      "g3_toy_belt_vib" to "振動つきの帯",
      "g3_toy_cage_ring" to "リング型",
      "g3_toy_pump_pocket" to "ポンプ型",
      "g3_toy_nipple_clamp" to "ニップルクランプ",
      "g3_toy_nipple_tassel" to "房つきクランプ",
      "g3_toy_nipple_bell" to "鈴つきクランプ",
      "g3_toy_nipple_chain" to "二つをつなぐ鎖",
      "g3_toy_nipple_suction" to "吸引カップ",
      "g3_toy_pearl_belt" to "真珠ベルト",
      "g3_toy_crotch_rope" to "股縄風のひも",
      "g3_toy_egg_bath" to "お風呂のおもちゃ",
    ),
  )

private fun optionPart201(): CharacterOptionPart =
  CharacterOptionPart(
    "持つ・見せる（1）",
    false,
    listOf(
      "h4_toy_vibrator_hold" to "おもちゃを手に持つ",
      "h4_toy_vibrator_wand" to "電動マッサージ器を持つ",
      "h4_toy_remote_hold" to "リモコンを握る",
      "h4_toy_remote_other" to "相手がリモコンを持つ",
      "h4_toy_remote_bulge" to "下着越しにふくらむ機器",
      "h4_toy_cord_visible" to "コードがのぞく",
      "h4_toy_cord_thigh" to "太ももに這うコード",
      "h4_toy_battery_pack" to "腰につけた機器",
      "h4_toy_blush_hide" to "機器を隠して耐える表情",
      "h4_toy_trembling_stand" to "立ったまま耐えてふるえる",
      "h4_toy_public_secret" to "人前でこっそり耐える",
      "h4_toy_dildo_hold" to "模造品を手に持つ",
      "h4_toy_dildo_lick" to "模造品に舌を寄せる",
      "h4_toy_dildo_suction" to "吸盤で立てた模造品",
      "h4_toy_egg_vibe" to "卵型の機器",
      "h4_toy_bullet" to "小型の機器",
    ),
  )

private fun optionPart202(): CharacterOptionPart =
  CharacterOptionPart(
    "持つ・見せる（2）",
    false,
    listOf(
      "h4_toy_rabbit" to "ウサギ耳型の機器",
      "h4_toy_glass_toy" to "ガラスのおもちゃ",
      "h4_toy_silicone_toy" to "シリコンのおもちゃ",
      "h4_toy_pastel_toys" to "パステルのおもちゃ",
      "h4_toy_toy_box" to "おもちゃ箱を広げる",
      "h4_toy_lube_bottle" to "ローションボトル",
      "h4_toy_lube_pour" to "ローションを垂らす",
      "h4_toy_condom_pack" to "ゴムの袋をくわえる",
      "h4_toy_condom_hold" to "ゴムの袋を持つ",
      "h4_toy_onahole_hold" to "筒型のおもちゃを持つ",
      "h4_toy_pillow_toy" to "抱き枕につけた道具",
      "h4_toy_massage_chair" to "マッサージチェア",
      "h4_toy_massage_back" to "背中のマッサージ",
      "h4_toy_massage_oil" to "オイルマッサージ",
    ),
  )

private fun optionPart203(): CharacterOptionPart =
  CharacterOptionPart(
    "装着・器具の見た目（1）",
    false,
    listOf(
      "h4_wear_tail_plug" to "しっぽつきの器具",
      "h4_wear_tail_bunny" to "うさぎしっぽの器具",
      "h4_wear_nipple_clamp" to "胸の飾りクリップ",
      "h4_wear_clamp_chain" to "クリップをつなぐ鎖",
      "h4_wear_clamp_bell" to "鈴つきクリップ",
      "h4_wear_nipple_tassel" to "飾りタッセル",
      "h4_wear_pasties_heart" to "ハート型のシール",
      "h4_wear_pasties_cross" to "X字のテープ",
      "h4_wear_pasties_flower" to "花型のシール",
      "h4_wear_tape_x_chest" to "胸のバツ印のテープ",
      "h4_wear_chest_chain" to "胸元の鎖飾り",
      "h4_wear_hip_chain" to "腰につけた鎖",
      "h4_wear_crotch_chain" to "股ぐらの飾り鎖",
      "h4_wear_belly_chain" to "おなかの飾りチェーン",
    ),
  )

private fun optionPart204(): CharacterOptionPart =
  CharacterOptionPart(
    "装着・器具の見た目（2）",
    false,
    listOf(
      "h4_wear_thigh_chain" to "太ももの鎖",
      "h4_wear_chastity_metal" to "金属の貞操帯",
      "h4_wear_chastity_lock" to "鍵のかかった貞操帯",
      "h4_wear_chastity_key_hold" to "貞操帯の鍵を持つ",
      "h4_wear_chastity_key_necklace" to "鍵を首から下げる",
      "h4_wear_chastity_lace" to "レースの貞操帯風",
      "h4_wear_chastity_gold" to "金色の貞操帯",
      "h4_wear_padlock_belt" to "南京錠つきのベルト",
      "h4_wear_padlock_collar" to "南京錠つきの首輪",
      "h4_wear_padlock_choker" to "鍵穴つきチョーカー",
      "h4_wear_key_hold_mouth" to "鍵を口にくわえる",
      "h4_wear_vibe_panty" to "機器つきの下着",
      "h4_wear_lace_harness_panty" to "ストラップつきの下着",
      "h4_wear_strap_on_harness" to "腰ベルトのハーネス",
      "h4_wear_ring_belt" to "金属リングのベルト",
      "h4_wear_o_ring_choker" to "Oリングのチョーカー",
      "h4_wear_leash_ring" to "リードのつく輪",
      "h4_wear_belly_toy_bulge" to "おなかのふくらみの演出",
    ),
  )

private fun optionGenre42(): CharacterOptionGenre =
  CharacterOptionGenre(
    "おもちゃ",
    listOf(
    optionPart199(),
    optionPart200(),
    optionPart201(),
    optionPart202(),
    optionPart203(),
    optionPart204(),
    ),
  )

private fun optionPart205(): CharacterOptionPart =
  CharacterOptionPart(
    "拘束具・革製品",
    false,
    listOf(
      "g3_sm5_wrist_leather" to "革の手枷",
      "g3_sm5_wrist_metal" to "金属の手枷",
      "g3_sm5_wrist_fur" to "ファーつきの手枷",
      "g3_sm5_wrist_pink" to "ピンクの手錠",
      "g3_sm5_ankle_chain" to "足首の鎖",
      "g3_sm5_thigh_cuff" to "太ももの枷",
      "g3_sm5_mitten" to "ミトン型の拘束",
      "g3_sm5_spread_bar" to "開脚バー",
      "g3_sm5_wrist_bar" to "手首のバー",
      "g3_sm5_hogtie_rope" to "縄の海老反り風",
      "g3_sm5_shibari_chest" to "胸の縄",
      "g3_sm5_shibari_back" to "後ろ手縛り",
      "g3_sm5_shibari_leg" to "脚の縄",
      "g3_sm5_shibari_suspension" to "吊り縄の演出",
      "g3_sm5_shibari_red" to "赤い縄",
      "g3_sm5_shibari_white" to "白い縄",
      "g3_sm5_shibari_black" to "黒い縄",
      "g3_sm5_shibari_hemp" to "麻縄",
      "g3_sm5_ribbon_tie" to "リボンで結ぶ",
      "g3_sm5_scarf_tie" to "スカーフで結ぶ",
      "g3_sm5_tie_up" to "ネクタイで縛る",
      "g3_sm5_belt_wrist" to "ベルトで固定",
      "g3_sm5_tape_wrist" to "テープで固定",
      "g3_sm5_cable_tie" to "結束バンド",
      "g3_sm5_chain_wall" to "壁の鎖",
      "g3_sm5_ceiling_hook" to "天井のフック",
      "g3_sm5_x_cross" to "Xの十字架",
      "g3_sm5_bondage_bed" to "拘束ベッド",
      "g3_sm5_stocks" to "さらし台",
      "g3_sm5_cage" to "檻",
      "g3_sm5_pet_cage" to "ペット用ケージ",
      "g3_sm5_bench" to "拘束ベンチ",
      "g3_sm5_swing" to "スイング",
      "g3_sm5_suspension_cuff" to "吊り用の枷",
      "g3_sm5_cuffed_front" to "前で拘束",
      "g3_sm5_cuffed_up" to "頭上で拘束",
      "g3_sm5_cuffed_back" to "後ろ手に拘束",
      "g3_sm5_chain_neck" to "首輪から鎖",
      "g3_sm5_collar_ring" to "輪つき首輪",
      "g3_sm5_collar_lock" to "鍵つき首輪",
      "g3_sm5_collar_tag" to "名札つき首輪",
      "g3_sm5_collar_choker" to "チョーカー首輪",
      "g3_sm5_collar_wide" to "幅広の首輪",
      "g3_sm5_collar_pearl" to "真珠の首輪",
      "g3_sm5_collar_lace" to "レースの首輪",
      "g3_sm5_collar_metal" to "金属の首輪",
      "g3_sm5_collar_bell" to "鈴つきの首輪",
      "g3_sm5_leash_rope" to "縄のリード",
      "g3_sm5_leash_hold" to "リードを持つ手",
    ),
  )

private fun optionPart206(): CharacterOptionPart =
  CharacterOptionPart(
    "口・目・耳・頭の道具",
    false,
    listOf(
      "g3_sm5_gag_ring" to "開口リング",
      "g3_sm5_gag_bit" to "馬具風のくつわ",
      "g3_sm5_gag_cloth" to "布の口枷",
      "g3_sm5_gag_tape" to "テープの口封じ",
      "g3_sm5_gag_scarf" to "スカーフの口枷",
      "g3_sm5_gag_pacifier" to "おしゃぶり型",
      "g3_sm5_gag_panel" to "革の口枷",
      "g3_sm5_gag_muzzle" to "マズル",
      "g3_sm5_gag_drool" to "口枷とよだれ",
      "g3_sm5_gag_clear" to "透明の口枷",
      "g3_sm5_gag_pink" to "ピンクの口枷",
      "g3_sm5_blindfold_silk" to "絹の目隠し",
      "g3_sm5_blindfold_leather" to "革の目隠し",
      "g3_sm5_blindfold_lace" to "レースの目隠し",
      "g3_sm5_blindfold_mask" to "目隠しマスク",
      "g3_sm5_blindfold_tie" to "ネクタイの目隠し",
      "g3_sm5_blindfold_tears" to "目隠しと涙",
      "g3_sm5_hood" to "革のフード",
      "g3_sm5_hood_latex" to "ラバーのフード",
      "g3_sm5_hood_eye" to "目の部分だけ開いたフード",
      "g3_sm5_hood_zip" to "ファスナーつきのフード",
      "g3_sm5_mask_gas" to "ガスマスク風",
      "g3_sm5_mask_cat" to "猫の仮面",
      "g3_sm5_mask_venetian" to "仮面舞踏会風",
      "g3_sm5_mask_half" to "ハーフマスク",
      "g3_sm5_headharness" to "頭のハーネス",
      "g3_sm5_ear_plug" to "耳栓",
      "g3_sm5_headphone_noise" to "ノイズヘッドホン",
      "g3_sm5_sensory" to "感覚を奪う装い",
      "g3_sm5_pony_head" to "ポニーの頭飾り",
    ),
  )

private fun optionPart207(): CharacterOptionPart =
  CharacterOptionPart(
    "ハーネス・ラバー・衣装",
    false,
    listOf(
      "g3_sm5_harness_chest" to "胸のハーネス",
      "g3_sm5_harness_body" to "全身のハーネス",
      "g3_sm5_harness_thigh" to "太もものハーネス",
      "g3_sm5_harness_back" to "背中のハーネス",
      "g3_sm5_harness_o_ring" to "Oリングのハーネス",
      "g3_sm5_harness_x" to "X型ハーネス",
      "g3_sm5_harness_chain" to "鎖のハーネス",
      "g3_sm5_harness_belt" to "ベルトだけの衣装",
      "g3_sm5_harness_strap_dress" to "ストラップのドレス",
      "g3_sm5_harness_garter" to "ガーターハーネス",
      "g3_sm5_harness_waist" to "ウエストコルセット",
      "g3_sm5_corset_tight" to "きつく絞ったコルセット",
      "g3_sm5_corset_lace_back" to "背中の編み上げ",
      "g3_sm5_corset_leather" to "革のコルセット",
      "g3_sm5_rubber_catsuit" to "ラバーのキャットスーツ",
      "g3_sm5_rubber_leotard" to "ラバーのレオタード",
      "g3_sm5_rubber_gloves" to "ラバーの手袋",
      "g3_sm5_rubber_boots" to "ラバーのブーツ",
      "g3_sm5_rubber_stockings" to "ラバーのストッキング",
      "g3_sm5_rubber_dress" to "ラバーのドレス",
      "g3_sm5_rubber_skirt" to "ラバーのスカート",
      "g3_sm5_rubber_pink" to "ピンクのラバー",
      "g3_sm5_rubber_red" to "赤いラバー",
      "g3_sm5_rubber_white" to "白いラバー",
      "g3_sm5_rubber_glossy" to "ぬらりと光る質感",
      "g3_sm5_rubber_nurse" to "ラバーのナース",
      "g3_sm5_rubber_doll" to "ラバードール",
      "g3_sm5_leather_jacket" to "革ジャケット",
      "g3_sm5_leather_strapless" to "革のチューブトップ",
      "g3_sm5_leather_shorts" to "革のショートパンツ",
      "g3_sm5_leather_boots" to "革のロングブーツ",
      "g3_sm5_leather_cap" to "革のキャップ",
      "g3_sm5_ballet_boots" to "バレエブーツ",
      "g3_sm5_spiked_choker" to "スパイクのチョーカー",
      "g3_sm5_stud_bracelet" to "鋲のブレスレット",
      "g3_sm5_chain_mail" to "鎖かたびら風",
    ),
  )

private fun optionPart208(): CharacterOptionPart =
  CharacterOptionPart(
    "道具の見た目（演出）",
    false,
    listOf(
      "g3_sm5_whip_leather" to "革の鞭を持つ",
      "g3_sm5_whip_flogger" to "フロッガー（房鞭）",
      "g3_sm5_whip_feather" to "羽根の鞭",
      "g3_sm5_paddle_wood" to "木のパドル",
      "g3_sm5_paddle_heart" to "ハート型のパドル",
      "g3_sm5_cane" to "細い棒",
      "g3_sm5_ruler" to "定規",
      "g3_sm5_candle_red" to "赤いろうそく",
      "g3_sm5_candle_pink" to "ピンクのろうそく",
      "g3_sm5_candle_black" to "黒いろうそく",
      "g3_sm5_candle_hold" to "ろうそくを持つ",
      "g3_sm5_candle_wax_drip" to "垂れるろう（演出）",
      "g3_sm5_candle_holder" to "燭台",
      "g3_sm5_wax_art" to "ろうの模様",
      "g3_sm5_feather_duster" to "羽根ぼうき",
      "g3_sm5_pinwheel" to "ピンホイール",
      "g3_sm5_silk_scarf" to "絹のスカーフ",
      "g3_sm5_key_lock" to "鍵とかぎ穴",
      "g3_sm5_padlock" to "南京錠",
      "g3_sm5_chain_heavy" to "太い鎖",
      "g3_sm5_dungeon" to "地下の拘束部屋",
      "g3_sm5_red_room" to "赤い部屋",
      "g3_sm5_play_room" to "プレイルーム",
      "g3_sm5_throne" to "王座に座る支配者",
      "g3_sm5_whip_pose" to "鞭を構える",
      "g3_sm5_boot_on" to "ブーツを差し出す",
      "g3_sm5_stand_over" to "見下ろす",
      "g3_sm5_crown_queen" to "女王様の冠",
      "g3_sm5_mistress_gloves" to "黒手袋の女王様",
      "g3_sm5_domina_suit" to "女王様の衣装",
      "g3_sm5_slave_collar" to "従者の首輪",
      "g3_sm5_contract" to "契約書",
      "g3_sm5_safeword" to "合言葉のカード",
      "g3_sm5_aftercare" to "アフターケア",
      "g3_sm5_aftercare_water" to "水を渡す",
      "g3_sm5_aftercare_cuddle" to "抱きしめて落ち着く",
      "g3_sm5_aftercare_rub" to "手首をさする",
      "g3_sm5_cuff_marks" to "枷の跡",
      "g3_sm5_pony_play" to "ポニープレイ風",
      "g3_sm5_puppy_play" to "子犬プレイ風（成人）",
      "g3_sm5_kitten_play" to "子猫プレイ風（成人）",
      "g3_sm5_cow_play" to "牛プレイ風",
      "g3_sm5_maid_slave" to "メイドの従者",
      "g3_sm5_servant_tray" to "トレイを運ぶ",
      "g3_sm5_bound_robe" to "ガウンの拘束",
      "g3_sm5_bound_ribbon_gift" to "プレゼントのリボン拘束",
      "g3_sm5_bound_vines" to "つるの拘束",
      "g3_sm5_bound_magic" to "魔法の鎖",
      "g3_sm5_bound_web" to "蜘蛛の巣",
      "g3_sm5_bound_jelly" to "スライムの拘束",
      "g3_sm5_bound_crystal" to "氷や水晶の枷",
      "g3_sm5_bound_light" to "光の枷",
    ),
  )

private fun optionGenre43(): CharacterOptionGenre =
  CharacterOptionGenre(
    "SM（追加5）",
    listOf(
    optionPart205(),
    optionPart206(),
    optionPart207(),
    optionPart208(),
    ),
  )

private fun optionPart209(): CharacterOptionPart =
  CharacterOptionPart(
    "シャツ・ブラウス",
    false,
    listOf(
      "h1_peasant_blouse" to "ペザントブラウス",
      "h1_bow_blouse" to "ボウブラウス",
      "h1_pussybow" to "リボンタイブラウス",
      "h1_frilled_shirt" to "フリルシャツ",
      "h1_tuxedo_shirt" to "タキシードシャツ",
      "h1_flannel_shirt" to "ネルシャツ",
      "h1_polo_shirt" to "ポロシャツ",
      "h1_aloha_shirt" to "アロハシャツ",
      "h1_henley_shirt" to "ヘンリーネックシャツ",
      "h1_open_collar_shirt" to "開襟シャツ",
      "h1_oversized_shirt" to "オーバーサイズシャツ",
      "h1_boyfriend_shirt" to "ボーイフレンドシャツ",
      "h1_work_shirt" to "ワークシャツ",
      "h1_denim_shirt" to "デニムシャツ",
      "h1_linen_shirt" to "リネンシャツ",
      "h1_sheer_blouse" to "シースルーブラウス",
      "h1_satin_blouse" to "サテンブラウス",
      "h1_lace_blouse" to "レースブラウス",
      "h1_tie_front_shirt" to "裾結びシャツ",
      "h1_cropped_shirt" to "クロップドシャツ",
      "h1_wrap_blouse" to "カシュクールブラウス",
      "h1_pleated_blouse" to "プリーツブラウス",
      "h1_smock_top" to "スモックトップ",
      "i2_band_collar_shirt" to "スタンドカラーシャツ",
      "i2_mandarin_collar_shirt" to "バンドカラーシャツ",
      "i2_bow_tie_blouse" to "ボウタイブラウス",
      "i2_jabot_blouse" to "ジャボブラウス",
      "i2_skipper_shirt" to "スキッパーシャツ",
      "i2_western_shirt" to "ウエスタンシャツ",
      "i2_yoke_shirt" to "ヨークシャツ",
      "i2_gauze_shirt" to "ガーゼシャツ",
      "i2_pintuck_blouse" to "ピンタックブラウス",
      "i2_pierrot_blouse" to "ピエロブラウス",
      "i2_off_shoulder_blouse" to "オフショルダーブラウス",
      "i2_camisole_blouse" to "キャミソールブラウス",
      "i2_ruffle_shirt" to "ラッフルシャツ",
      "i2_pirate_shirt" to "海賊シャツ",
      "i2_argyle_shirt" to "アーガイルシャツ",
      "i2_striped_open_shirt" to "ストライプオープンシャツ",
      "i2_camp_collar_shirt" to "半袖開襟シャツ",
      "i2_round_collar_shirt" to "ラウンドカラーシャツ",
    ),
  )

private fun optionPart210(): CharacterOptionPart =
  CharacterOptionPart(
    "Tシャツ・カットソー",
    false,
    listOf(
      "h1_tshirt_oversized" to "ビッグTシャツ",
      "h1_tshirt_cropped" to "クロップドTシャツ",
      "h1_tshirt_knotted" to "裾結びTシャツ",
      "h1_tshirt_print" to "プリントTシャツ",
      "h1_tshirt_band" to "バンドTシャツ",
      "h1_tshirt_vneck" to "VネックTシャツ",
      "h1_tshirt_long" to "ロングTシャツ",
      "h1_tshirt_ringer" to "リンガーTシャツ",
      "h1_tshirt_tight" to "ぴったりTシャツ",
      "h1_tshirt_wet" to "濡れたTシャツ",
      "h1_tshirt_baggy" to "だぼだぼTシャツ",
      "h1_raglan_tee" to "ラグランTシャツ",
      "h1_thermal_shirt" to "サーマルシャツ",
      "h1_compression_top" to "コンプレッションシャツ",
      "i2_striped_cut_and_sew" to "ボーダーカットソー",
      "i2_breton_shirt" to "バスクシャツ",
      "i2_long_sleeve_tee" to "ロングスリーブT",
      "i2_pocket_tee" to "ポケットTシャツ",
      "i2_band_tee_style" to "バンドTシャツ風",
      "i2_sleeveless_cut_and_sew" to "ノースリーブカットソー",
      "i2_waffle_knit_shirt" to "ワッフルシャツ",
      "i2_frilled_tank_top" to "フリルタンクトップ",
      "i2_rash_guard_top" to "ラッシュガードトップ",
      "i2_tie_dye_t_shirt" to "タイダイTシャツ",
      "i2_lace_polo_shirt" to "レースポロ",
      "i2_rugby_shirt" to "ラグビーシャツ",
    ),
  )

private fun optionPart211(): CharacterOptionPart =
  CharacterOptionPart(
    "キャミ・タンクトップ",
    false,
    listOf(
      "h1_camisole_lace" to "レースキャミソール",
      "h1_camisole_satin" to "サテンキャミソール",
      "h1_camisole_strappy" to "細ストラップキャミ",
      "h1_tank_loose" to "ゆるタンクトップ",
      "h1_tank_ribbed" to "リブタンクトップ",
      "h1_tank_cropped" to "クロップドタンク",
      "h1_tank_racerback" to "レーサーバックタンク",
      "h1_bandeau_top" to "バンドゥトップ",
      "h1_bustier_top" to "ビスチェトップ",
      "h1_corset_top" to "コルセットトップ",
      "h1_off_shoulder_top" to "オフショルダートップ",
      "h1_one_shoulder_top" to "ワンショルダートップ",
      "h1_cold_shoulder" to "コールドショルダー",
      "h1_keyhole_top" to "キーホールトップ",
      "h1_cutout_top" to "カットアウトトップ",
      "h1_twist_front_top" to "ツイストフロントトップ",
      "h1_cowl_neck_top" to "カウルネックトップ",
      "h1_backless_top" to "バックレストップ",
      "h1_mesh_top" to "メッシュトップ",
      "h1_bralette_top" to "ブラレットトップ",
      "h1_crossed_straps_top" to "クロスストラップトップ",
      "h1_sports_bra_top" to "スポーツブラトップ",
    ),
  )

private fun optionPart212(): CharacterOptionPart =
  CharacterOptionPart(
    "ニット・セーター",
    false,
    listOf(
      "h1_sweater_aran" to "ケーブルニット",
      "h1_sweater_fisherman" to "フィッシャーマンセーター",
      "h1_sweater_nordic" to "ノルディック柄セーター",
      "h1_sweater_oversized" to "オーバーサイズセーター",
      "h1_sweater_ribbed" to "リブニット",
      "h1_sweater_turtleneck" to "タートルネックニット",
      "h1_sweater_cropped" to "クロップドニット",
      "h1_sweater_open_shoulder" to "オープンショルダーニット",
      "h1_sweater_backless" to "背中あきニット",
      "h1_sweater_keyhole" to "胸もと穴あきニット",
      "h1_sweater_off_shoulder" to "オフショルニット",
      "h1_sweater_mohair" to "モヘアニット",
      "h1_sweater_vest_knit" to "ニットベスト",
      "h1_cardigan_long" to "ロングカーディガン",
      "h1_cardigan_cropped" to "ショートカーディガン",
      "h1_cardigan_oversized" to "だぼカーディガン",
      "h1_cardigan_open" to "前開きカーディガン",
      "h1_knit_poncho" to "ニットポンチョ",
      "h1_zip_hoodie" to "ジップパーカー",
      "h1_cropped_hoodie" to "クロップドパーカー",
      "h1_sweatshirt" to "スウェットトレーナー",
      "i2_fair_isle_sweater" to "フェアアイル柄ニット",
      "i2_cable_knit_sweater" to "ケーブル編みニット",
      "i2_angora_sweater" to "アンゴラニット",
      "i2_cashmere_sweater" to "カシミアニット",
      "i2_chunky_knit_sweater" to "ローゲージニット",
      "i2_fine_knit_sweater" to "ハイゲージニット",
      "i2_mock_neck_sweater" to "モックネックニット",
      "i2_boat_neck_sweater" to "ボートネックニット",
      "i2_v_neck_sweater" to "Vネックニット",
      "i2_crew_neck_sweater" to "クルーネックニット",
      "i2_knit_polo" to "ニットポロ",
      "i2_knit_camisole" to "ニットキャミ",
      "i2_knit_bolero" to "ニットボレロ",
      "i2_knit_cape" to "ニットケープ",
      "i2_garter_stitch_sweater" to "ガーターニット",
      "i2_openwork_knit_sweater" to "透かし編みニット",
      "i2_puff_sleeve_sweater" to "ボリューム袖ニット",
      "i2_knit_cardigan" to "ニットカーディガン",
      "i2_shawl_collar_cardigan" to "ショールカラーカーディガン",
      "i2_argyle_vest" to "アーガイルベスト",
    ),
  )

private fun optionPart213(): CharacterOptionPart =
  CharacterOptionPart(
    "ベスト・チュニック",
    false,
    listOf(
      "h1_waistcoat_top" to "ウエストコート",
      "h1_suit_vest" to "スーツベスト",
      "h1_denim_vest" to "デニムベスト",
      "h1_leather_vest" to "レザーベスト",
      "h1_fur_vest" to "ファーベスト",
      "h1_puffer_vest" to "ダウンベスト",
      "h1_tunic_top" to "チュニック",
      "h1_long_tunic" to "ロングチュニック",
      "h1_kaftan_top" to "カフタン",
      "h1_poncho_top" to "ポンチョ",
      "h1_cape_top" to "ケープ",
      "h1_capelet_top" to "ケープレット",
      "h1_smock_garment" to "スモック",
    ),
  )

private fun optionPart214(): CharacterOptionPart =
  CharacterOptionPart(
    "スウェット・パーカー",
    false,
    listOf(
      "i2_pullover_hoodie" to "プルオーバーパーカー",
      "i2_half_zip_sweatshirt" to "ハーフジップスウェット",
      "i2_crewneck_sweatshirt" to "クルースウェット",
      "i2_animal_ear_hoodie" to "耳付きパーカー",
      "i2_kigurumi_hoodie" to "着ぐるみ風パーカー",
    ),
  )

private fun optionPart215(): CharacterOptionPart =
  CharacterOptionPart(
    "特殊なトップス",
    false,
    listOf(
      "i2_bandeau_top" to "ベアトップ",
      "i2_criss_cross_halter_top" to "クロスホルタートップ",
      "i2_cape_top" to "ケープ付きトップ",
      "i2_peplum_top" to "ペプラムトップ",
      "i2_cache_coeur_top" to "カシュクールトップ",
      "i2_poncho_top" to "ポンチョトップ",
      "i2_cape_blouse" to "ケープブラウス",
      "i2_babydoll_top" to "ベビードール風トップ",
      "i2_handkerchief_top" to "ハンカチーフトップ",
      "i2_lace_up_top" to "レースアップトップ",
      "i2_velvet_top" to "ベルベットトップ",
      "i2_backless_wrap_top" to "バックレスラップトップ",
    ),
  )

private fun optionGenre44(): CharacterOptionGenre =
  CharacterOptionGenre(
    "トップス",
    listOf(
    optionPart209(),
    optionPart210(),
    optionPart211(),
    optionPart212(),
    optionPart213(),
    optionPart214(),
    optionPart215(),
    ),
  )

private fun optionPart216(): CharacterOptionPart =
  CharacterOptionPart(
    "スカート（1）",
    false,
    listOf(
      "h1_pencil_skirt" to "ペンシルスカート",
      "h1_a_line_skirt" to "Aラインスカート",
      "h1_circle_skirt" to "サーキュラースカート",
      "h1_tiered_skirt" to "ティアードスカート",
      "h1_gathered_skirt" to "ギャザースカート",
      "h1_balloon_skirt" to "バルーンスカート",
      "h1_trumpet_skirt" to "トランペットスカート",
      "h1_mermaid_skirt" to "マーメイドスカート",
      "h1_wrap_skirt" to "ラップスカート",
      "h1_tulip_skirt" to "チューリップスカート",
      "h1_bubble_skirt" to "バブルスカート",
      "h1_high_waist_skirt" to "ハイウエストスカート",
      "h1_lowleg_skirt" to "ローライズスカート",
      "h1_midi_skirt" to "ミディスカート",
      "h1_mini_skirt_micro" to "マイクロミニスカート",
      "h1_high_low_skirt" to "アシンメトリースカート",
      "h1_overskirt" to "オーバースカート",
      "h1_suspender_skirt" to "サスペンダースカート",
      "h1_overall_skirt" to "サロペットスカート",
      "h1_cargo_skirt" to "カーゴスカート",
      "h1_denim_skirt" to "デニムスカート",
      "h1_plaid_skirt" to "チェックスカート",
      "h1_latex_skirt" to "ラテックススカート",
      "h1_satin_skirt" to "サテンスカート",
      "h1_tulle_skirt" to "チュールスカート",
      "h1_lace_skirt" to "レーススカート",
      "h1_sequin_skirt" to "スパンコールスカート",
      "h1_slit_skirt" to "スリットスカート",
      "h1_split_skirt_front" to "前開きスカート",
      "h1_layered_skirt" to "レイヤードスカート",
    ),
  )

private fun optionPart217(): CharacterOptionPart =
  CharacterOptionPart(
    "スカート（2）",
    false,
    listOf(
      "h1_showgirl_skirt" to "ショーガールスカート",
      "h1_tutu_skirt" to "チュチュスカート",
      "h1_pareo_wrap" to "パレオ",
      "h1_sarong_wrap" to "サロン",
      "h1_hakama_skirt" to "袴スカート",
      "h1_kimono_skirt" to "着物スカート",
      "h1_petticoat_skirt" to "ペチコートスカート",
      "h1_hoop_skirt" to "フープスカート",
      "h1_bustle_skirt" to "バッスルスカート",
      "h1_culotte_skirt" to "キュロットスカート",
      "h1_apron_skirt" to "エプロンスカート",
      "h1_mesh_skirt" to "メッシュスカート",
      "h1_sheer_skirt" to "透け感スカート",
      "h1_flowing_skirt" to "ひらひらスカート",
    ),
  )

private fun optionPart218(): CharacterOptionPart =
  CharacterOptionPart(
    "パンツ・ズボン（1）",
    false,
    listOf(
      "h1_bell_bottoms_pants" to "ベルボトム",
      "h1_bootcut_pants" to "ブーツカットパンツ",
      "h1_skinny_pants" to "スキニーパンツ",
      "h1_wide_pants" to "ワイドパンツ",
      "h1_palazzo_pants" to "パラッツォパンツ",
      "h1_gaucho_pants" to "ガウチョパンツ",
      "h1_baggy_pants" to "バギーパンツ",
      "h1_harem_pants" to "ハーレムパンツ",
      "h1_chino_pants" to "チノパン",
      "h1_slacks" to "スラックス",
      "h1_trousers_high_waist" to "ハイウエストパンツ",
      "h1_capri_pants" to "カプリパンツ",
      "h1_cigarette_pants" to "サブリナパンツ",
      "h1_jogger_pants" to "ジョガーパンツ",
      "h1_sweatpants_loose" to "スウェットパンツ",
      "h1_track_pants" to "トラックパンツ",
      "h1_latex_pants" to "ラテックスパンツ",
      "h1_vinyl_pants" to "ビニールパンツ",
      "h1_lowleg_pants" to "ローライズパンツ",
      "h1_pants_rolled" to "裾まくりパンツ",
      "h1_pants_cutout" to "カットアウトパンツ",
      "h1_ripped_jeans" to "ダメージジーンズ",
      "h1_cutoff_jeans" to "カットオフジーンズ",
      "h1_jeans_flared" to "フレアジーンズ",
      "h1_jeans_baggy" to "だぼジーンズ",
      "h1_jeans_high" to "ハイウエストジーンズ",
      "h1_overalls_pants" to "オーバーオール",
      "h1_overalls_shorts" to "ショートオーバーオール",
      "h1_jumpsuit_pants" to "ジャンプスーツ",
      "h1_romper_adult" to "ロンパース",
    ),
  )

private fun optionPart219(): CharacterOptionPart =
  CharacterOptionPart(
    "パンツ・ズボン（2）",
    false,
    listOf(
      "h1_knickerbockers" to "ニッカボッカ",
      "h1_jodhpurs" to "ジョッパーズ",
      "h1_hakama_pants" to "袴パンツ",
      "h1_sarouel" to "サルエルパンツ",
      "h1_parachute_pants" to "パラシュートパンツ",
      "h1_pleated_pants" to "タックパンツ",
      "h1_suspender_pants" to "サスペンダー付きパンツ",
      "h1_chaps_pants" to "チャップス",
      "h1_pelvic_curtain" to "前垂れ",
    ),
  )

private fun optionPart220(): CharacterOptionPart =
  CharacterOptionPart(
    "ショートパンツ",
    false,
    listOf(
      "h1_denim_shorts_h" to "デニムショートパンツ",
      "h1_cutoff_shorts" to "カットオフショーツ",
      "h1_bike_shorts_h" to "バイクショーツ",
      "h1_dolphin_shorts_h" to "ドルフィンショーツ",
      "h1_gym_shorts_h" to "ジムショーツ",
      "h1_pleated_shorts" to "プリーツショーツ",
      "h1_bermuda_shorts" to "バミューダショーツ",
      "h1_cargo_shorts" to "カーゴショーツ",
      "h1_high_waist_shorts" to "ハイウエストショーツ",
      "h1_lowleg_shorts_h" to "ローライズショーツ",
      "h1_sweat_shorts" to "スウェットショーツ",
      "h1_suspender_shorts" to "サスペンダーショーツ",
      "h1_shorts_under_skirt" to "スカートの下のショーツ",
      "h1_open_fly_shorts" to "前開きショーツ",
    ),
  )

private fun optionPart221(): CharacterOptionPart =
  CharacterOptionPart(
    "スカート",
    false,
    listOf(
      "i2_semi_tight_skirt" to "セミタイトスカート",
      "i2_barrel_skirt" to "バレルスカート",
      "i2_peg_top_skirt" to "ペグトップスカート",
      "i2_umbrella_skirt" to "アンブレラスカート",
      "i2_box_pleat_skirt" to "ボックスプリーツスカート",
      "i2_accordion_pleat_skirt" to "アコーディオンプリーツスカート",
      "i2_kilt_skirt" to "キルトスカート",
      "i2_sarong_skirt" to "巻きスカート",
      "i2_fishtail_skirt" to "フィッシュテールスカート",
      "i2_velvet_skirt" to "ベルベットスカート",
      "i2_corduroy_skirt" to "コーデュロイスカート",
      "i2_pleated_mini_skirt" to "プリーツミニ",
      "i2_tiered_long_skirt" to "ティアードロング",
      "i2_petticoat_skirt" to "パニエスカート",
      "i2_full_long_skirt" to "ボリュームロング",
      "i2_below_knee_skirt" to "ひざ下丈スカート",
      "i2_hipbone_skirt" to "ヒップボーンスカート",
      "i2_wrap_culottes" to "ラップキュロット",
      "i2_gored_skirt" to "ゴアードスカート",
    ),
  )

private fun optionPart222(): CharacterOptionPart =
  CharacterOptionPart(
    "パンツ",
    false,
    listOf(
      "i2_tapered_pants" to "スリムテーパードパンツ",
      "i2_straight_leg_pants" to "ストレートパンツ",
      "i2_flare_pants" to "フレアパンツ",
      "i2_riding_breeches" to "乗馬ズボン",
      "i2_crease_pants" to "スタプレパンツ",
      "i2_painter_pants" to "ペインターパンツ",
      "i2_work_pants" to "ワークパンツ",
      "i2_suit_trousers" to "スーツパンツ",
      "i2_velvet_pants" to "ベルベットパンツ",
      "i2_patent_leather_pants" to "エナメルパンツ",
      "i2_corduroy_pants" to "コーデュロイパンツ",
      "i2_black_jeans" to "ブラックデニム",
      "i2_boyfriend_jeans" to "ボーイフレンドデニム",
      "i2_mom_jeans" to "マムジーンズ",
      "i2_straight_jeans" to "ストレートデニム",
      "i2_wide_leg_jeans" to "ワイドデニム",
      "i2_salopette" to "サロペット",
    ),
  )

private fun optionPart223(): CharacterOptionPart =
  CharacterOptionPart(
    "ショーツ・レギンス",
    false,
    listOf(
      "i2_half_pants" to "ハーフパンツ",
      "i2_biker_shorts" to "サイクルパンツ",
      "i2_flared_shorts" to "フレアショーツ",
      "i2_tailored_shorts" to "テーラードショーツ",
      "i2_stirrup_leggings" to "トレンカ",
      "i2_mesh_leggings" to "メッシュレギンス",
    ),
  )

private fun optionGenre45(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ボトムス",
    listOf(
    optionPart216(),
    optionPart217(),
    optionPart218(),
    optionPart219(),
    optionPart220(),
    optionPart221(),
    optionPart222(),
    optionPart223(),
    ),
  )

private fun optionPart224(): CharacterOptionPart =
  CharacterOptionPart(
    "日常のワンピース（1）",
    false,
    listOf(
      "h1_turtleneck_dress" to "タートルネックワンピ",
      "h1_halter_dress" to "ホルターネックワンピ",
      "h1_tube_dress" to "チューブドレス",
      "h1_tent_dress" to "テントワンピ",
      "h1_empire_dress" to "エンパイアドレス",
      "h1_a_line_dress" to "Aラインワンピ",
      "h1_fit_and_flare_dress" to "フィット＆フレアワンピ",
      "h1_babydoll_dress" to "ベビードールワンピ",
      "h1_smock_dress" to "スモックワンピ",
      "h1_pinafore_dress" to "ジャンパースカートワンピ",
      "h1_apron_dress" to "エプロンドレス",
      "h1_denim_dress" to "デニムワンピ",
      "h1_polo_dress" to "ポロワンピ",
      "h1_t_shirt_dress" to "Tシャツワンピ",
      "h1_tiered_dress" to "ティアードワンピ",
      "h1_pleated_dress" to "プリーツワンピ",
      "h1_tennis_dress" to "テニスワンピ",
      "h1_sailor_collar_dress_none" to "カシュクールワンピ",
      "h1_maxi_dress" to "マキシワンピ",
      "h1_midi_dress" to "ミディワンピ",
      "h1_mini_dress" to "ミニワンピ",
      "h1_micro_dress" to "マイクロミニワンピ",
      "h1_high_low_dress" to "ハイローワンピ",
      "h1_one_piece_slit" to "スリット入りワンピ",
      "h1_lace_up_dress" to "編み上げワンピ",
      "h1_button_front_dress" to "前ボタンワンピ",
      "h1_zip_front_dress" to "前ジップワンピ",
      "h1_backless_halter_dress" to "背中あきホルターワンピ",
      "h1_sheer_dress" to "シースルーワンピ",
      "h1_mesh_dress" to "メッシュワンピ",
    ),
  )

private fun optionPart225(): CharacterOptionPart =
  CharacterOptionPart(
    "日常のワンピース（2）",
    false,
    listOf(
      "h1_knit_bodycon" to "ニットボディコン",
    ),
  )

private fun optionPart226(): CharacterOptionPart =
  CharacterOptionPart(
    "フォーマル・パーティドレス（1）",
    false,
    listOf(
      "h1_princess_line_dress" to "プリンセスラインドレス",
      "h1_a_line_gown" to "Aラインドレス",
      "h1_empire_gown" to "エンパイアラインドレス",
      "h1_bell_line_dress" to "ベルラインドレス",
      "h1_slim_line_dress" to "スレンダーラインドレス",
      "h1_column_dress" to "コラムドレス",
      "h1_sweetheart_gown" to "スウィートハートドレス",
      "h1_halter_gown" to "ホルターネックドレス",
      "h1_strapless_gown" to "ストラップレスドレス",
      "h1_sequin_gown" to "スパンコールガウン",
      "h1_satin_gown" to "サテンガウン",
      "h1_silk_gown" to "シルクガウン",
      "h1_velvet_gown" to "ベルベットガウン",
      "h1_beaded_gown" to "ビーズ刺繍ドレス",
      "h1_lace_gown" to "レースガウン",
      "h1_high_slit_gown" to "ハイスリットドレス",
      "h1_plunging_gown" to "深Vネックドレス",
      "h1_open_back_gown" to "オープンバックドレス",
      "h1_long_gloves_gown" to "長手袋とドレス",
      "h1_ballroom_dress" to "社交ダンスドレス",
      "h1_latin_dance_dress" to "ラテンダンスドレス",
      "h1_flamenco_ruffles" to "フリルスカートドレス",
      "h1_ao_dai_dress" to "アオザイ風ドレス",
      "h1_robe_de_soiree" to "ローブデコルテ",
      "h1_tea_dress" to "ティードレス",
      "h1_afternoon_dress" to "アフタヌーンドレス",
      "h1_bridesmaid_dress" to "ブライズメイドドレス",
      "h1_wedding_veil_gown" to "ヴェール付きウェディング",
      "h1_wedding_short" to "ミニウェディングドレス",
      "h1_wedding_mermaid" to "マーメイドウェディング",
    ),
  )

private fun optionPart227(): CharacterOptionPart =
  CharacterOptionPart(
    "フォーマル・パーティドレス（2）",
    false,
    listOf(
      "h1_wedding_trained" to "トレーン付きウェディング",
      "h1_black_gown" to "黒のイブニングドレス",
      "h1_gold_gown" to "金色のドレス",
      "h1_silver_gown" to "銀色のドレス",
    ),
  )

private fun optionPart228(): CharacterOptionPart =
  CharacterOptionPart(
    "ジャンプスーツ・ボディスーツ",
    false,
    listOf(
      "h1_jumpsuit_wide" to "ワイドジャンプスーツ",
      "h1_jumpsuit_halter" to "ホルタージャンプスーツ",
      "h1_jumpsuit_cutout" to "カットアウトジャンプスーツ",
      "h1_jumpsuit_leather" to "レザージャンプスーツ",
      "h1_jumpsuit_mechanic" to "つなぎ",
      "h1_jumpsuit_unzipped" to "胸元を開けたつなぎ",
      "h1_bodysuit_long" to "長袖ボディスーツ",
      "h1_bodysuit_halter" to "ホルターボディスーツ",
      "h1_bodysuit_lace" to "レースボディスーツ",
      "h1_bodysuit_turtleneck" to "タートルネックボディスーツ",
      "h1_bodysuit_thong" to "Tバックボディスーツ",
      "h1_leotard_high_cut" to "ハイカットレオタード",
      "h1_leotard_long_sleeve" to "長袖レオタード",
      "h1_leotard_open_back" to "背中あきレオタード",
      "h1_leotard_see" to "シースルーレオタード",
      "h1_leotard_strapless" to "ストラップレスレオタード",
      "h1_leotard_metal" to "メタリックレオタード",
      "h1_unitard_full" to "ユニタード",
      "h1_catsuit" to "キャットスーツ",
    ),
  )

private fun optionPart229(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピースのシルエット",
    false,
    listOf(
      "i2_sheath_dress" to "シースドレス",
      "i2_slender_line_dress" to "スレンダーライン",
      "i2_trumpet_dress" to "トランペットドレス",
      "i2_gathered_dress" to "ギャザーワンピース",
      "i2_drop_waist_dress" to "ドロップウエストドレス",
      "i2_balloon_dress" to "バルーンワンピース",
      "i2_peplum_dress" to "ペプラムドレス",
      "i2_cami_dress" to "キャミワンピース",
      "i2_off_shoulder_dress" to "オフショルダーワンピース",
      "i2_knit_dress" to "ニットワンピース",
      "i2_jersey_dress" to "ジャージワンピース",
      "i2_hoodie_dress" to "パーカーワンピース",
      "i2_satin_dress" to "サテンドレス",
      "i2_chiffon_dress" to "シフォンワンピース",
      "i2_long_sleeve_dress" to "ロングスリーブドレス",
      "i2_puff_sleeve_dress" to "パフスリーブワンピース",
      "i2_bell_sleeve_dress" to "ベルスリーブドレス",
      "i2_caped_dress" to "ケープ付きドレス",
      "i2_long_slit_dress" to "スリットロングドレス",
      "i2_trapeze_dress" to "トラペーズドレス",
    ),
  )

private fun optionPart230(): CharacterOptionPart =
  CharacterOptionPart(
    "フォーマル・パーティー",
    false,
    listOf(
      "i2_prom_dress" to "プロムドレス",
      "i2_mid_century_dress" to "ミッドセンチュリー風ドレス",
      "i2_tuxedo_dress" to "タキシードドレス",
      "i2_blazer_dress" to "ブレザードレス",
      "i2_jacket_dress" to "ジャケットドレス",
      "i2_gala_dress" to "ガラドレス",
      "i2_opera_dress" to "オペラドレス",
      "i2_tutu_dress" to "バレエ風チュチュドレス",
      "i2_belly_dance_costume" to "ベリーダンス衣装",
    ),
  )

private fun optionPart231(): CharacterOptionPart =
  CharacterOptionPart(
    "ガウン・ローブ",
    false,
    listOf(
      "i2_tea_gown" to "ティーガウン",
      "i2_negligee_gown" to "ネグリジェガウン",
      "i2_wrap_robe" to "ラップローブ",
      "i2_cape_robe" to "ケープローブ",
    ),
  )

private fun optionGenre46(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ワンピース・ドレス",
    listOf(
    optionPart224(),
    optionPart225(),
    optionPart226(),
    optionPart227(),
    optionPart228(),
    optionPart229(),
    optionPart230(),
    optionPart231(),
    ),
  )

private fun optionPart232(): CharacterOptionPart =
  CharacterOptionPart(
    "コート",
    false,
    listOf(
      "h1_duffle_coat_h" to "ダッフルコート",
      "h1_chesterfield_coat" to "チェスターコート",
      "h1_pea_coat" to "ピーコート",
      "h1_overcoat_h" to "オーバーコート",
      "h1_belted_coat" to "ベルト付きコート",
      "h1_wrap_coat" to "ラップコート",
      "h1_mod_coat" to "モッズコート",
      "h1_duster_coat" to "ダスターコート",
      "h1_cocoon_coat" to "コクーンコート",
      "h1_faux_fur_coat" to "エコファーコート",
      "h1_shearling_coat" to "ムートンコート",
      "h1_long_down_coat" to "ロングダウンコート",
      "h1_hooded_coat" to "フード付きコート",
      "h1_cape_coat" to "ケープコート",
      "h1_military_coat" to "ミリタリーコート",
      "h1_frock_coat" to "フロックコート",
      "h1_redingote" to "リダンゴト",
      "h1_swing_coat" to "スイングコート",
      "h1_raincoat_clear" to "透明レインコート",
      "h1_raincoat_yellow" to "黄色いレインコート",
      "h1_poncho_rain" to "レインポンチョ",
      "h1_open_coat_naked" to "前を開けたコート",
      "h1_coat_off_shoulder" to "肩落ちコート",
      "i2_balmacaan_coat" to "ステンカラーコート",
      "i2_gown_coat" to "ガウンコート",
      "i2_collarless_coat" to "ノーカラーコート",
      "i2_stand_collar_coat" to "スタンドカラーコート",
      "i2_boa_coat" to "ボアコート",
      "i2_quilted_coat" to "キルティングコート",
      "i2_down_coat" to "ダウンコート",
      "i2_long_puffer_coat" to "ロングダウン",
      "i2_poncho_coat" to "ポンチョコート",
      "i2_inverness_coat" to "インバネスコート",
      "i2_cashmere_coat" to "カシミアコート",
      "i2_wool_coat" to "ウールコート",
      "i2_oilskin_coat" to "オイルドコート",
      "i2_robe_coat" to "ローブコート",
      "i2_rubber_coat" to "ラバーコート",
      "i2_vinyl_coat" to "ビニールコート",
    ),
  )

private fun optionPart233(): CharacterOptionPart =
  CharacterOptionPart(
    "ジャケット",
    false,
    listOf(
      "h1_blazer_h" to "テーラードジャケット",
      "h1_suit_jacket_h" to "スーツジャケット",
      "h1_cropped_jacket_h" to "クロップドジャケット",
      "h1_bolero_jacket" to "ボレロジャケット",
      "h1_tuxedo_jacket" to "タキシードジャケット",
      "h1_smoking_jacket" to "スモーキングジャケット",
      "h1_safari_jacket_h" to "サファリジャケット",
      "h1_flight_jacket" to "フライトジャケット",
      "h1_souvenir_jacket" to "スカジャン",
      "h1_biker_jacket" to "ライダースジャケット",
      "h1_track_jacket" to "トラックジャケット",
      "h1_windbreaker" to "ウインドブレーカー",
      "h1_coach_jacket" to "コーチジャケット",
      "h1_quilted_jacket" to "キルティングジャケット",
      "h1_parka_jacket" to "マウンテンパーカー",
      "h1_anorak" to "アノラック",
      "h1_field_jacket" to "フィールドジャケット",
      "h1_tank_jacket_open" to "前開きジャケット",
      "h1_jacket_tied_waist" to "腰巻きジャケット",
      "h1_jacket_shoulder" to "肩掛けジャケット",
      "h1_jacket_removed_one" to "片腕だけ脱ぐジャケット",
      "h1_tailcoat_h" to "燕尾服",
      "h1_morning_coat" to "モーニングコート",
      "h1_kamishimo" to "裃",
      "h1_hanten" to "半纏",
      "i2_double_breasted_blazer" to "ダブルブレザー",
      "i2_collarless_jacket" to "ノーカラージャケット",
      "i2_spencer_jacket" to "スペンサージャケット",
      "i2_double_rider_jacket" to "ダブルライダース",
      "i2_ma_1_jacket" to "MA-1ジャケット",
      "i2_deck_jacket" to "デッキジャケット",
      "i2_military_jacket" to "ミリタリージャケット",
      "i2_shirt_jacket" to "シャツジャケット",
      "i2_work_jacket" to "ワークジャケット",
      "i2_coverall_jacket" to "カバーオール",
      "i2_hunting_jacket" to "ハンティングジャケット",
      "i2_golf_jacket" to "ゴルフジャケット",
      "i2_harrington_jacket" to "ハリントンジャケット",
      "i2_nylon_jacket" to "ナイロンジャケット",
      "i2_fleece_jacket" to "フリースジャケット",
      "i2_sherpa_jacket" to "ボアジャケット",
      "i2_long_denim_jacket" to "ロングデニムジャケット",
      "i2_corduroy_jacket" to "コーデュロイジャケット",
      "i2_suede_jacket" to "スエードジャケット",
      "i2_patent_leather_jacket" to "エナメルジャケット",
      "i2_collarless_tweed_jacket" to "ノーカラーツイード",
      "i2_velvet_jacket" to "ベルベットジャケット",
      "i2_frog_button_jacket" to "フロッグボタンジャケット",
      "i2_kung_fu_jacket" to "カンフージャケット",
    ),
  )

private fun optionPart234(): CharacterOptionPart =
  CharacterOptionPart(
    "ネックライン・襟（1）",
    false,
    listOf(
      "h1_neck_crew" to "クルーネック",
      "h1_neck_v" to "Vネック",
      "h1_neck_deep_v" to "ディープVネック",
      "h1_neck_scoop" to "スクープネック",
      "h1_neck_square" to "スクエアネック",
      "h1_neck_sweetheart" to "スウィートハートネック",
      "h1_neck_boat" to "ボートネック",
      "h1_neck_off_shoulder" to "オフショルダーネック",
      "h1_neck_cowl" to "カウルネック",
      "h1_neck_keyhole" to "キーホールネック",
      "h1_neck_turtle" to "タートルネック",
      "h1_neck_mock" to "モックネック",
      "h1_neck_mandarin" to "チャイナカラー",
      "h1_neck_high" to "ハイネック",
      "h1_neck_peter_pan" to "ピーターパンカラー",
      "h1_neck_puritan" to "ピューリタンカラー",
      "h1_neck_wing" to "ウイングカラー",
      "h1_neck_shawl" to "ショールカラー",
      "h1_neck_notch" to "ノッチドラペル",
      "h1_neck_peak" to "ピークドラペル",
      "h1_neck_medici" to "メディチカラー",
      "h1_neck_ruffle" to "フリルカラー",
      "h1_neck_ruff" to "ラフカラー",
      "h1_neck_popped" to "立てた襟",
      "h1_neck_open" to "開襟",
      "h1_neck_asym" to "アシンメトリーネック",
      "h1_neck_lace_up" to "編み上げネック",
      "h1_neck_choker_collar" to "チョーカーネック",
      "h1_neck_illusion" to "イリュージョンネック",
      "h1_neck_queen_anne" to "クイーンアンネック",
    ),
  )

private fun optionPart235(): CharacterOptionPart =
  CharacterOptionPart(
    "ネックライン・襟（2）",
    false,
    listOf(
      "h1_neck_jewel" to "ジュエルネック",
      "h1_neck_bandeau" to "バンドゥネック",
      "h1_neck_low" to "大きく開いた胸元",
      "h1_neck_surplice" to "クロスオーバーネック",
    ),
  )

private fun optionPart236(): CharacterOptionPart =
  CharacterOptionPart(
    "袖（1）",
    false,
    listOf(
      "h1_sleeve_puff" to "パフスリーブ",
      "h1_sleeve_puff_short" to "パフ半袖",
      "h1_sleeve_puff_long" to "パフ長袖",
      "h1_sleeve_bishop" to "ビショップスリーブ",
      "h1_sleeve_bell" to "ベルスリーブ",
      "h1_sleeve_poet" to "ポエットスリーブ",
      "h1_sleeve_leg_of_mutton" to "レッグオブマトンスリーブ",
      "h1_sleeve_dolman" to "ドルマンスリーブ",
      "h1_sleeve_raglan" to "ラグランスリーブ",
      "h1_sleeve_cap" to "キャップスリーブ",
      "h1_sleeve_flutter" to "フラッタースリーブ",
      "h1_sleeve_angel" to "エンジェルスリーブ",
      "h1_sleeve_juliet" to "ジュリエットスリーブ",
      "h1_sleeve_wide" to "ワイドスリーブ",
      "h1_sleeve_detached_puffy" to "パフの分離袖",
      "h1_sleeve_single_detached" to "片方だけの分離袖",
      "h1_sleeve_layered" to "重ね袖",
      "h1_sleeve_slashed" to "スラッシュスリーブ",
      "h1_sleeve_lace" to "レース袖",
      "h1_sleeve_see_through" to "透け袖",
      "h1_sleeve_three_quarter" to "七分袖",
      "h1_sleeve_past_fingers" to "指先まである袖",
      "h1_sleeve_rolled" to "腕まくり",
      "h1_sleeve_pushed" to "ひじまで袖まくり",
      "h1_sleeve_thumbhole" to "親指穴の袖",
      "h1_sleeve_fur_trim" to "ファー袖口",
      "h1_sleeve_frill_trim" to "フリル袖口",
      "h1_sleeve_mismatched" to "左右違いの袖",
      "h1_sleeve_one_short" to "片袖だけない",
      "h1_sleeve_torn" to "破れた袖",
    ),
  )

private fun optionPart237(): CharacterOptionPart =
  CharacterOptionPart(
    "袖（2）",
    false,
    listOf(
      "h1_sleeve_furisode" to "振袖の袖",
      "h1_sleeve_arm_out" to "袖から腕を出す",
      "h1_sleeve_hands_inside" to "袖の中に手を入れる",
      "h1_sleeve_sleeveless" to "ノースリーブ",
      "h1_sleeve_cuffs" to "カフス",
      "h1_sleeve_fishnet" to "網タイツ風の袖",
    ),
  )

private fun optionPart238(): CharacterOptionPart =
  CharacterOptionPart(
    "ベスト・ケープ",
    false,
    listOf(
      "i2_military_vest" to "ミリタリーベスト",
      "i2_fishing_vest" to "フィッシングベスト",
      "i2_long_cape" to "ロングケープ",
      "i2_blanket_cape" to "ブランケットケープ",
    ),
  )

private fun optionGenre47(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アウター",
    listOf(
    optionPart232(),
    optionPart233(),
    optionPart234(),
    optionPart235(),
    optionPart236(),
    optionPart237(),
    optionPart238(),
    ),
  )

private fun optionPart239(): CharacterOptionPart =
  CharacterOptionPart(
    "ブーツ",
    false,
    listOf(
      "h1_boot_chelsea" to "チェルシーブーツ",
      "h1_boot_chukka" to "チャッカブーツ",
      "h1_boot_desert" to "デザートブーツ",
      "h1_boot_engineer" to "エンジニアブーツ",
      "h1_boot_jungle" to "ジャングルブーツ",
      "h1_boot_tactical" to "タクティカルブーツ",
      "h1_boot_work" to "ワークブーツ",
      "h1_boot_riding" to "乗馬ブーツ",
      "h1_boot_wellington" to "長靴",
      "h1_boot_snow" to "スノーブーツ",
      "h1_boot_mouton" to "ムートンブーツ",
      "h1_boot_ugg_none" to "ファーブーツ",
      "h1_boot_knee" to "ニーハイブーツ",
      "h1_boot_over_knee" to "オーバーニーブーツ",
      "h1_boot_bootie" to "ブーティ",
      "h1_boot_lace_up_high" to "編み上げロングブーツ",
      "h1_boot_buckle" to "バックルブーツ",
      "h1_boot_zipper" to "ジッパーブーツ",
      "h1_boot_stiletto" to "ピンヒールブーツ",
      "h1_boot_block_heel" to "チャンキーヒールブーツ",
      "h1_boot_side_zip" to "サイドゴアブーツ",
      "h1_boot_pointy" to "ポインテッドブーツ",
      "h1_boot_patent" to "エナメルブーツ",
      "h1_boot_biker" to "バイクブーツ",
      "h1_boot_mountain" to "登山靴",
      "h1_boot_valenki" to "フェルトブーツ",
      "h1_boot_pirate" to "海賊ブーツ",
      "h1_boot_ninja" to "忍者足袋ブーツ",
      "h1_boot_open_toe" to "オープントゥブーツ",
      "h1_boot_wedge" to "ウェッジブーツ",
    ),
  )

private fun optionPart240(): CharacterOptionPart =
  CharacterOptionPart(
    "パンプス・ヒール・フラット",
    false,
    listOf(
      "h1_heel_wedge" to "ウェッジヒール",
      "h1_heel_block" to "太ヒール",
      "h1_heel_kitten" to "キトゥンヒール",
      "h1_heel_ankle_strap" to "アンクルストラップヒール",
      "h1_heel_t_strap" to "Tストラップヒール",
      "h1_heel_peep_toe" to "ピープトゥ",
      "h1_heel_slingback" to "スリングバック",
      "h1_heel_d_orsay" to "ドルセイパンプス",
      "h1_heel_clear" to "クリアヒール",
      "h1_heel_glitter" to "グリッターヒール",
      "h1_heel_extreme" to "超ハイヒール",
      "h1_heel_pointed" to "ポインテッドパンプス",
      "h1_heel_black_patent" to "黒エナメルヒール",
      "h1_shoe_flats" to "フラットシューズ",
      "h1_shoe_derby" to "ダービーシューズ",
      "h1_shoe_monk" to "モンクストラップ",
      "h1_shoe_brogue" to "ブローグ",
      "h1_shoe_boat" to "デッキシューズ",
      "h1_shoe_espadrille" to "エスパドリーユ",
      "h1_shoe_crocs_none" to "クロッグ",
      "h1_shoe_wooden_clogs" to "木靴",
      "h1_shoe_animal_slippers" to "動物スリッパ",
      "h1_shoe_ballet_pointe" to "トウシューズ",
      "h1_shoe_tap" to "タップシューズ",
      "h1_shoe_dance" to "ダンスシューズ",
      "h1_shoe_pointed_flat" to "ポインテッドフラット",
      "h1_shoe_moccasin" to "モカシン",
      "h1_shoe_barefoot" to "裸足",
    ),
  )

private fun optionPart241(): CharacterOptionPart =
  CharacterOptionPart(
    "スニーカー・サンダル",
    false,
    listOf(
      "h1_sneaker_low" to "ローカットスニーカー",
      "h1_sneaker_high" to "ハイカットスニーカー",
      "h1_sneaker_chunky" to "ダッドスニーカー",
      "h1_sneaker_slip_on" to "スリッポン",
      "h1_sneaker_running" to "ランニングシューズ",
      "h1_sneaker_platform" to "厚底スニーカー",
      "h1_sneaker_canvas" to "キャンバススニーカー",
      "h1_sneaker_basketball" to "バスケットシューズ",
      "h1_sneaker_tennis" to "テニスシューズ",
      "h1_sneaker_untied" to "ひもがほどけたスニーカー",
      "h1_sandal_flip_flops" to "ビーチサンダル",
      "h1_sandal_strappy" to "ストラップサンダル",
      "h1_sandal_platform" to "厚底サンダル",
      "h1_sandal_heel" to "ヒールサンダル",
      "h1_sandal_wedge" to "ウェッジサンダル",
      "h1_sandal_sport" to "スポーツサンダル",
      "h1_sandal_slide" to "スライドサンダル",
      "h1_sandal_cross_laced" to "クロスレースサンダル",
      "h1_sandal_toe_ring" to "トングサンダル",
      "h1_sandal_huarache" to "ワラチェ",
      "h1_sandal_geta" to "下駄",
      "h1_sandal_waraji" to "草鞋",
      "h1_sandal_okobo" to "おこぼ",
      "h1_sandal_winged" to "翼つきサンダル",
      "h1_sandal_jeweled" to "宝石飾りサンダル",
      "h1_sandal_roman" to "ローマ風サンダル",
      "h1_sandal_barefoot" to "ベアフットサンダル",
      "h1_sandal_fur" to "ファーサンダル",
    ),
  )

private fun optionPart242(): CharacterOptionPart =
  CharacterOptionPart(
    "靴下・タイツ・ストッキング（1）",
    false,
    listOf(
      "h1_sock_over_knee" to "オーバーニーソックス",
      "h1_sock_thigh" to "サイハイソックス",
      "h1_sock_hip" to "ヒップハイ",
      "h1_sock_tube" to "チューブソックス",
      "h1_sock_loose" to "ルーズソックス",
      "h1_sock_striped" to "ボーダーソックス",
      "h1_sock_polka" to "水玉ソックス",
      "h1_sock_lace_trim" to "レース縁ソックス",
      "h1_sock_frilled" to "フリルソックス",
      "h1_sock_ribbon" to "リボンソックス",
      "h1_sock_ribbed" to "リブソックス",
      "h1_sock_fluffy" to "ふわふわソックス",
      "h1_sock_mismatched" to "左右違いの靴下",
      "h1_sock_toeless" to "つま先なしソックス",
      "h1_sock_stirrup" to "ストラップ付きタイツ",
      "h1_sock_slouch" to "たるませた靴下",
      "h1_sock_uneven" to "長さ違いの靴下",
      "h1_sock_cat_paw" to "肉球ソックス",
      "h1_sock_bear" to "くま耳レッグウェア",
      "h1_sock_bow" to "リボン付きレッグウェア",
      "h1_sock_thigh_bow" to "リボン付きニーハイ",
      "h1_sock_thigh_lace" to "レースニーハイ",
      "h1_sock_thigh_garter" to "ガーターストラップ付きニーハイ",
      "h1_sock_thigh_torn" to "破れたニーハイ",
      "h1_sock_thigh_seamed" to "バックシーム付きニーハイ",
      "h1_sock_thigh_latex" to "ラテックスニーハイ",
      "h1_pantyhose_sheer" to "薄手ストッキング",
      "h1_pantyhose_thick" to "厚手タイツ",
      "h1_pantyhose_lace" to "レースタイツ",
      "h1_pantyhose_seamed" to "シームストッキング",
    ),
  )

private fun optionPart243(): CharacterOptionPart =
  CharacterOptionPart(
    "靴下・タイツ・ストッキング（2）",
    false,
    listOf(
      "h1_pantyhose_pattern" to "柄タイツ",
      "h1_pantyhose_gradient" to "グラデーションタイツ",
      "h1_pantyhose_under_shorts" to "ショーツ下のタイツ",
      "h1_pantyhose_toeless" to "つま先なしタイツ",
      "h1_pantyhose_glossy" to "光沢タイツ",
      "h1_pantyhose_mesh" to "メッシュタイツ",
      "h1_pantyhose_footless" to "フットレスタイツ",
      "h1_pantyhose_stockings_lace_top" to "レース上端ストッキング",
      "h1_pantyhose_stockings_seam" to "バックシームストッキング",
    ),
  )

private fun optionGenre48(): CharacterOptionGenre =
  CharacterOptionGenre(
    "靴・靴下（追加）",
    listOf(
    optionPart239(),
    optionPart240(),
    optionPart241(),
    optionPart242(),
    optionPart243(),
    ),
  )

private fun optionPart244(): CharacterOptionPart =
  CharacterOptionPart(
    "帽子（1）",
    false,
    listOf(
      "h1_hat_beanie_pompom" to "ポンポンニット帽",
      "h1_hat_earflap" to "耳当て付き帽子",
      "h1_hat_baseball_back" to "後ろ向きキャップ",
      "h1_hat_trilby" to "トリルビー",
      "h1_hat_panama" to "パナマ帽",
      "h1_hat_boater" to "カンカン帽",
      "h1_hat_bowler" to "山高帽",
      "h1_hat_newsboy" to "キャスケット",
      "h1_hat_flat" to "ハンチング",
      "h1_hat_peaked" to "制帽",
      "h1_hat_kepi" to "ケピ帽",
      "h1_hat_shako" to "シャコー帽",
      "h1_hat_garrison" to "ギャリソンキャップ",
      "h1_hat_boonie" to "ブーニーハット",
      "h1_hat_sombrero" to "ソンブレロ",
      "h1_hat_picture" to "ピクチャーハット",
      "h1_hat_cloche" to "クロッシュ",
      "h1_hat_pillbox" to "ピルボックスハット",
      "h1_hat_fascinator" to "ファシネーター",
      "h1_hat_tricorne" to "三角帽",
      "h1_hat_bicorne" to "二角帽",
      "h1_hat_pirate" to "海賊帽",
      "h1_hat_wizard" to "魔法使いの帽子",
      "h1_hat_witch_mini" to "ミニ魔女帽",
      "h1_hat_jester" to "道化師の帽子",
      "h1_hat_fez" to "フェズ",
      "h1_hat_keffiyeh" to "ケフィヤ",
      "h1_hat_hijab" to "ヒジャブ",
      "h1_hat_tenugui" to "手ぬぐい頭巻き",
      "h1_hat_hachimaki" to "ハチマキ",
    ),
  )

private fun optionPart245(): CharacterOptionPart =
  CharacterOptionPart(
    "帽子（2）",
    false,
    listOf(
      "h1_hat_rice" to "菅笠",
      "h1_hat_roningasa" to "編み笠",
      "h1_hat_eboshi" to "烏帽子",
      "h1_hat_kabuto" to "兜",
      "h1_hat_helmet_pilot" to "パイロットヘルメット",
      "h1_hat_helmet_motorcycle" to "バイクヘルメット",
      "h1_hat_helmet_hard" to "ヘルメット（工事）",
      "h1_hat_helmet_bicycle" to "自転車ヘルメット",
      "h1_hat_helmet_space" to "宇宙ヘルメット",
      "h1_hat_helmet_winged" to "翼付きヘルメット",
      "h1_hat_helmet_diving" to "潜水ヘルメット",
      "h1_hat_pickelhaube" to "スパイク付きヘルメット",
      "h1_hat_bearskin" to "毛皮の帽子",
      "h1_hat_ushanka" to "ウシャンカ",
      "h1_hat_papakha" to "パパーハ",
      "h1_hat_chef" to "コック帽",
      "h1_hat_flight_attendant" to "客室乗務員の帽子",
      "h1_hat_nightcap" to "ナイトキャップ",
      "h1_hat_shower" to "シャワーキャップ",
      "h1_hat_santa" to "サンタ帽",
      "h1_hat_party" to "パーティハット",
      "h1_hat_animal" to "動物帽子",
      "h1_hat_cat" to "ねこ耳帽子",
      "h1_hat_bunny" to "うさ耳帽子",
      "h1_hat_mob" to "モブキャップ",
      "h1_hat_bonnet" to "ボンネット",
      "h1_hat_hennin" to "エナン",
      "h1_hat_mitre" to "ミトラ",
      "h1_hat_kokoshnik" to "ココシニク",
      "h1_hat_mini_hat" to "ミニハット",
    ),
  )

private fun optionPart246(): CharacterOptionPart =
  CharacterOptionPart(
    "帽子（3）",
    false,
    listOf(
      "h1_hat_veil_hat" to "ヴェール付き帽子",
      "h1_hat_feather" to "羽根飾り帽子",
      "h1_hat_flower_hat" to "花飾り帽子",
      "h1_hat_ribbon" to "リボン付き帽子",
      "h1_hat_tilted" to "斜めにかぶる帽子",
      "h1_hat_over_eye" to "目深にかぶる帽子",
      "h1_hat_tip" to "帽子を軽く上げる",
      "h1_hat_holding" to "帽子を手に持つ",
      "h1_hat_hand_on" to "帽子に手を添える",
    ),
  )

private fun optionPart247(): CharacterOptionPart =
  CharacterOptionPart(
    "ヘッドドレス・冠（1）",
    false,
    listOf(
      "h1_head_mini_crown" to "ミニ王冠",
      "h1_head_diadem" to "ダイアデム",
      "h1_head_fengguan" to "鳳冠",
      "h1_head_mian" to "冕冠",
      "h1_head_headchain" to "ヘッドチェーン",
      "h1_head_veil" to "ベール",
      "h1_head_headband" to "ヘアバンド",
      "h1_head_headband_bow" to "リボンカチューシャ",
      "h1_head_headband_knit" to "ニットヘアバンド",
      "h1_head_sweatband" to "スウェットバンド",
      "h1_head_headset" to "ヘッドセット",
      "h1_head_sunglasses" to "サングラスを頭に",
      "h1_head_glasses_half" to "ハーフリム眼鏡",
      "h1_head_glasses_heart" to "ハート形サングラス",
      "h1_head_mask_fox" to "狐面",
      "h1_head_mask_plague" to "ペスト医師マスク",
      "h1_head_mask_domino" to "ドミノマスク",
      "h1_head_mask_veil_face" to "フェイスベール",
      "h1_head_balaclava" to "目出し帽",
      "h1_head_hood_down" to "フードを下ろす",
      "h1_head_scarf_wrap" to "スカーフを頭に巻く",
      "h1_head_earrings_stud" to "スタッドピアス",
      "h1_head_earrings_drop" to "ドロップピアス",
      "h1_head_earrings_chain" to "チェーンピアス",
      "h1_head_earrings_big" to "大きなイヤリング",
      "h1_head_earrings_cross" to "十字架のイヤリング",
      "h1_head_earrings_heart" to "ハートのイヤリング",
      "h1_head_earring_single" to "片耳だけのイヤリング",
      "h1_head_face_chain" to "フェイスチェーン",
    ),
  )

private fun optionPart248(): CharacterOptionPart =
  CharacterOptionPart(
    "ヘッドドレス・冠（2）",
    false,
    listOf(
      "h1_head_face_jewel" to "フェイスジュエル",
      "h1_head_hair_scrunchie" to "シュシュ",
      "h1_head_hair_clip" to "ヘアクリップ",
      "h1_head_hair_beads" to "ヘアビーズ",
      "h1_head_hair_tie_ribbon" to "ポニーテールのリボン",
      "h1_head_hair_bell" to "髪の鈴",
    ),
  )

private fun optionGenre49(): CharacterOptionGenre =
  CharacterOptionGenre(
    "帽子・頭（追加）",
    listOf(
    optionPart244(),
    optionPart245(),
    optionPart246(),
    optionPart247(),
    optionPart248(),
    ),
  )

private fun optionPart249(): CharacterOptionPart =
  CharacterOptionPart(
    "手袋・腕の装飾（1）",
    false,
    listOf(
      "h1_glove_long_satin" to "サテン長手袋",
      "h1_glove_half_fingerless" to "半指手袋",
      "h1_glove_fold_over" to "折り返し手袋",
      "h1_glove_see_through" to "透ける手袋",
      "h1_glove_oven" to "オーブンミット",
      "h1_glove_paw" to "肉球手袋",
      "h1_glove_bridal" to "ブライダルガントレット",
      "h1_glove_spiked" to "スパイク付き手袋",
      "h1_glove_fur_trim" to "ファー付き手袋",
      "h1_glove_frilled" to "フリル付き手袋",
      "h1_glove_ribbon" to "リボン付き手袋",
      "h1_glove_single" to "片方だけの手袋",
      "h1_glove_mismatched" to "左右違いの手袋",
      "h1_glove_striped" to "縞の手袋",
      "h1_glove_cross_laced" to "編み上げ手袋",
      "h1_glove_unworn" to "外した手袋",
      "h1_glove_removing" to "手袋を外す",
      "h1_glove_bite" to "手袋を噛んで外す",
      "h1_glove_holding" to "手袋を持つ",
      "h1_glove_yugake" to "弓懸",
      "h1_glove_kote" to "籠手",
      "h1_glove_cuffs" to "手袋のカフス",
      "h1_bracer_h" to "ブレイサー",
      "h1_bracelet_chain" to "チェーンブレスレット",
      "h1_bracelet_beaded" to "ビーズブレスレット",
      "h1_armlet_h" to "アームレット",
      "h1_arm_guards" to "腕ガード",
      "h1_wrist_cuffs_h" to "リストカフ",
    ),
  )

private fun optionPart250(): CharacterOptionPart =
  CharacterOptionPart(
    "手袋・腕の装飾（2）",
    false,
    listOf(
      "h1_wrist_scrunchie" to "手首のシュシュ",
      "h1_wrist_ruff" to "手首のフリル",
      "h1_hand_chains_h" to "ハンドチェーン",
      "h1_ring_multiple" to "重ね付けの指輪",
      "h1_ring_claw" to "爪リング",
      "h1_ring_thumb" to "親指の指輪",
      "h1_nails_coffin" to "ロングネイル",
      "h1_fingerless_arm" to "アームカバー",
    ),
  )

private fun optionGenre50(): CharacterOptionGenre =
  CharacterOptionGenre(
    "手・腕（追加）",
    listOf(
    optionPart249(),
    optionPart250(),
    ),
  )

private fun optionPart251(): CharacterOptionPart =
  CharacterOptionPart(
    "バッグ",
    false,
    listOf(
      "h1_bag_satchel" to "サッチェルバッグ",
      "h1_bag_messenger" to "メッセンジャーバッグ",
      "h1_bag_pochette" to "ポシェット",
      "h1_bag_mini_backpack" to "ミニリュック",
      "h1_bag_fanny" to "ウエストポーチ",
      "h1_bag_belt_pouch" to "ベルトポーチ",
      "h1_bag_boston" to "ボストンバッグ",
      "h1_bag_briefcase" to "ブリーフケース",
      "h1_bag_suitcase" to "スーツケース",
      "h1_bag_drawstring" to "巾着バッグ",
      "h1_bag_kinchaku" to "巾着袋",
      "h1_bag_furoshiki" to "風呂敷",
      "h1_bag_tote_shopping" to "買い物袋",
      "h1_bag_paper" to "紙袋",
      "h1_bag_plastic" to "ビニール袋",
      "h1_bag_sports" to "スポーツバッグ",
      "h1_bag_guitar_case" to "ギターケース",
      "h1_bag_instrument" to "楽器ケース",
      "h1_bag_camera" to "カメラバッグ",
      "h1_bag_quiver" to "矢筒",
      "h1_bag_saddle" to "サドルバッグ",
      "h1_bag_hip_flask" to "携帯水筒",
      "h1_bag_umbrella_clear" to "ビニール傘",
      "h1_bag_purse" to "財布",
      "h1_bag_coin_purse" to "がま口",
      "h1_bag_pouch" to "ポーチ",
      "h1_bag_holding_bag" to "バッグを持つ",
      "h1_bag_carrying" to "バッグを肩に掛ける",
    ),
  )

private fun optionPart252(): CharacterOptionPart =
  CharacterOptionPart(
    "ベルト・サスペンダー・帯",
    false,
    listOf(
      "h1_belt_leather" to "革ベルト",
      "h1_belt_wide" to "太ベルト",
      "h1_belt_buckle" to "大きなバックル",
      "h1_belt_double" to "ダブルベルト",
      "h1_belt_o_ring" to "Oリングベルト",
      "h1_belt_sash" to "サッシュ",
      "h1_belt_obi" to "帯",
      "h1_belt_obi_big" to "大きな帯リボン",
      "h1_belt_obijime" to "帯締め",
      "h1_belt_gun" to "ガンベルト",
      "h1_belt_sam_browne" to "サムブラウンベルト",
      "h1_belt_cross_body" to "斜めがけベルト",
      "h1_belt_harness" to "ハーネス",
      "h1_belt_chest_harness" to "チェストハーネス",
      "h1_belt_leg_belt" to "脚ベルト",
      "h1_belt_corset_belt" to "コルセットベルト",
      "h1_belt_cummerbund" to "カマーバンド",
      "h1_belt_belly_dance" to "ヒップスカーフ",
      "h1_belt_keys" to "腰のキーチェーン",
      "h1_belt_apron" to "腰エプロン",
      "h1_belt_pouches" to "腰のポーチ",
      "h1_belt_sword" to "腰の刀",
      "h1_belt_tied_waist" to "腰に巻いた上着",
      "h1_belt_tied_shirt" to "腰に巻いたシャツ",
      "h1_belt_loose" to "ゆるいベルト",
      "h1_belt_unbuckled" to "外したベルト",
    ),
  )

private fun optionPart253(): CharacterOptionPart =
  CharacterOptionPart(
    "首まわり（1）",
    false,
    listOf(
      "h1_neck_choker_pendant" to "ペンダント付きチョーカー",
      "h1_neck_choker_studded" to "スタッズチョーカー",
      "h1_neck_choker_cross" to "十字チョーカー",
      "h1_neck_choker_wide" to "太いチョーカー",
      "h1_neck_collar_belt" to "ベルト首輪",
      "h1_neck_collar_chain" to "鎖のついた首輪",
      "h1_neck_collar_animal" to "獣用首輪",
      "h1_neck_necklace_chain" to "チェーンネックレス",
      "h1_neck_necklace_gold" to "金のネックレス",
      "h1_neck_necklace_silver" to "銀のネックレス",
      "h1_neck_necklace_heart" to "ハートのネックレス",
      "h1_neck_necklace_star" to "星のネックレス",
      "h1_neck_necklace_bead" to "ビーズのネックレス",
      "h1_neck_necklace_shell" to "貝殻のネックレス",
      "h1_neck_necklace_lei" to "レイ",
      "h1_neck_necklace_magatama" to "勾玉",
      "h1_neck_necklace_amulet" to "お守り",
      "h1_neck_necklace_long" to "ロングネックレス",
      "h1_neck_necklace_tooth" to "牙のネックレス",
      "h1_neck_scarf_long" to "長いマフラー",
      "h1_neck_scarf_fluffy" to "もこもこマフラー",
      "h1_neck_scarf_fur" to "ファーマフラー",
      "h1_neck_scarf_plaid" to "チェックのマフラー",
      "h1_neck_scarf_striped" to "縞のマフラー",
      "h1_neck_scarf_shared" to "二人で巻くマフラー",
      "h1_neck_scarf_over_mouth" to "口元までのマフラー",
      "h1_neck_scarf_neckerchief" to "ネッカチーフ",
      "h1_neck_stole" to "ストール",
      "h1_neck_boa" to "フェザーボア",
      "h1_neck_ascot" to "アスコットタイ",
    ),
  )

private fun optionPart254(): CharacterOptionPart =
  CharacterOptionPart(
    "首まわり（2）",
    false,
    listOf(
      "h1_neck_bolo" to "ボロタイ",
      "h1_neck_necktie_loose" to "ゆるめたネクタイ",
      "h1_neck_necktie_wide" to "太いネクタイ",
      "h1_neck_necktie_cross" to "クロスタイ",
      "h1_neck_necktie_between" to "胸の間のネクタイ",
      "h1_neck_ribbon_big" to "大きな首リボン",
      "h1_neck_towel" to "首にタオル",
      "h1_neck_headphones" to "首にヘッドホン",
      "h1_neck_goggles" to "首にゴーグル",
      "h1_neck_whistle" to "首に笛",
      "h1_neck_lanyard" to "ネックストラップ",
      "h1_neck_neck_warmer" to "ネックウォーマー",
      "h1_neck_gorget" to "ゴルゲット",
      "h1_neck_pet_cone" to "エリザベスカラー",
    ),
  )

private fun optionGenre51(): CharacterOptionGenre =
  CharacterOptionGenre(
    "バッグ・小物（追加）",
    listOf(
    optionPart251(),
    optionPart252(),
    optionPart253(),
    optionPart254(),
    ),
  )

private fun optionPart255(): CharacterOptionPart =
  CharacterOptionPart(
    "アジア（1）",
    false,
    listOf(
      "h1_eth_ao_ba_ba" to "アオバーバー",
      "h1_eth_non_la" to "ノンラー",
      "h1_eth_batik_sarong" to "バティックのサロン",
      "h1_eth_sinh" to "タイのシン",
      "h1_eth_pha_sin" to "パーシン",
      "h1_eth_barong" to "バロン・タガログ",
      "h1_eth_baro_saya" to "バロ・サヤ",
      "h1_eth_longyi" to "ロンジー",
      "h1_eth_sari_blouse" to "サリーとチョリ",
      "h1_eth_salwar" to "サルワール・カミーズ",
      "h1_eth_kurta" to "クルタ",
      "h1_eth_dhoti" to "ドーティ",
      "h1_eth_dupatta" to "ドゥパッタ",
      "h1_eth_kimono_iromuji" to "色無地",
      "h1_eth_kimono_uchikake" to "打掛",
      "h1_eth_kimono_junihitoe" to "十二単",
      "h1_eth_kimono_hakama" to "袴姿",
      "h1_eth_kimono_hakama_haori" to "袴と羽織",
      "h1_eth_kimono_taisho" to "大正ロマン着物",
      "h1_eth_kimono_geisha" to "芸妓の着物",
      "h1_eth_kimono_maiko" to "舞妓風の着物",
      "h1_eth_kimono_oiran" to "花魁の着物",
      "h1_eth_kimono_layered" to "重ね着物",
      "h1_eth_kimono_loose" to "着崩した着物",
      "h1_eth_kimono_sleeve_roll" to "たすき掛け",
      "h1_eth_kimono_apron" to "割烹着",
      "h1_eth_kimono_sarashi" to "さらし",
      "h1_eth_kimono_fundoshi" to "ふんどし",
      "h1_eth_kimono_mizu_happi" to "祭り法被",
    ),
  )

private fun optionPart256(): CharacterOptionPart =
  CharacterOptionPart(
    "アジア（2）",
    false,
    listOf(
      "h1_eth_kimono_nagajuban" to "長襦袢",
      "h1_eth_kimono_geta" to "下駄と浴衣",
      "h1_eth_ryuso" to "琉装",
      "h1_eth_ainu" to "アイヌ衣装",
      "h1_eth_hanbok_jeogori" to "短いチョゴリ",
      "h1_eth_hanbok_royal" to "宮廷韓服",
      "h1_eth_qipao_short" to "短いチャイナドレス",
      "h1_eth_qipao_long" to "ロングチャイナドレス",
      "h1_eth_qipao_sleeveless" to "ノースリーブチャイナ",
      "h1_eth_qipao_floral" to "花柄チャイナドレス",
      "h1_eth_changshan" to "長衫",
      "h1_eth_hanfu_qiyao" to "斉腰襦裙",
      "h1_eth_hanfu_beizi" to "褙子",
      "h1_eth_hanfu_dalingpao" to "大領袍",
      "h1_eth_hanfu_sleeves_wide" to "広袖の漢服",
      "h1_eth_mongolian_deel" to "モンゴルのデール",
      "h1_eth_tibetan_chuba" to "チベットのチュバ",
      "h1_eth_miao_silver" to "ミャオ族の銀飾り",
      "h1_eth_hmong" to "モン族の刺繍衣装",
      "h1_eth_burmese" to "ミャンマー衣装",
      "h1_eth_uzbek" to "ウズベクの衣装",
      "h1_eth_central_asia" to "中央アジアの衣装",
    ),
  )

private fun optionPart257(): CharacterOptionPart =
  CharacterOptionPart(
    "中東・アフリカ",
    false,
    listOf(
      "h1_eth_abaya" to "アバヤ",
      "h1_eth_burqa" to "ブルカ",
      "h1_eth_niqab" to "ニカブ",
      "h1_eth_chador" to "チャドル",
      "h1_eth_thobe" to "トーブ",
      "h1_eth_jellaba" to "ジュラバ",
      "h1_eth_harem" to "ハーレム衣装",
      "h1_eth_belly_dance_veil" to "ベリーダンスのヴェール",
      "h1_eth_arabian_dancer" to "アラビアンダンサー",
      "h1_eth_persian" to "ペルシャ風衣装",
      "h1_eth_egypt_ancient" to "古代エジプトの衣装",
      "h1_eth_egypt_collar" to "ウセクカラー",
      "h1_eth_cleopatra_wig" to "エジプト風ウィッグ",
      "h1_eth_roman_toga" to "古代ローマのトガ",
      "h1_eth_greek_chiton" to "ギリシャのキトン",
      "h1_eth_dashiki" to "ダシキ",
      "h1_eth_kente" to "ケンテ布",
      "h1_eth_kanga" to "カンガ",
      "h1_eth_boubou" to "ブーブー",
      "h1_eth_maasai" to "マサイの衣装",
      "h1_eth_zulu" to "ズールー風衣装",
      "h1_eth_ethiopian" to "エチオピアの衣装",
      "h1_eth_ankara_print" to "アンカラ柄ドレス",
      "h1_eth_gele" to "ゲレ",
    ),
  )

private fun optionPart258(): CharacterOptionPart =
  CharacterOptionPart(
    "ヨーロッパ・アメリカ（1）",
    false,
    listOf(
      "h1_eth_tartan_wrap" to "タータンのショール",
      "h1_eth_tracht" to "トラハト",
      "h1_eth_sarafan_h" to "サラファン",
      "h1_eth_rubashka" to "ルバシカ",
      "h1_eth_ukrainian_vyshyvanka" to "ヴィシヴァンカ",
      "h1_eth_polish_folk" to "ポーランド民族衣装",
      "h1_eth_hungarian_folk" to "ハンガリー民族衣装",
      "h1_eth_czech_folk" to "チェコ民族衣装",
      "h1_eth_swedish_folk" to "スウェーデン民族衣装",
      "h1_eth_norwegian_bunad" to "ブーナッド",
      "h1_eth_dutch_folk" to "オランダ民族衣装",
      "h1_eth_basque" to "バスク風衣装",
      "h1_eth_greek_folk" to "ギリシャ民族衣装",
      "h1_eth_italian_folk" to "イタリア民族衣装",
      "h1_eth_portuguese_fado" to "ポルトガル風ショール",
      "h1_eth_irish_folk" to "アイルランド民族衣装",
      "h1_eth_scottish_highland" to "スコットランド高地の服",
      "h1_eth_gypsy" to "ボヘミアン風衣装",
      "h1_eth_flamenco_h" to "フラメンコのドレス",
      "h1_eth_mantilla" to "マンティーラ",
      "h1_eth_poncho_andes" to "アンデスのポンチョ",
      "h1_eth_chullo" to "チュヨ",
      "h1_eth_pollera" to "ポジェラ",
      "h1_eth_huipil" to "ウイピル",
      "h1_eth_rebozo" to "レボソ",
      "h1_eth_china_poblana" to "チナ・ポブラーナ",
      "h1_eth_native_american" to "ネイティブアメリカン風衣装",
      "h1_eth_war_bonnet" to "羽根の頭飾り",
      "h1_eth_hawaiian_muumuu" to "ムームー",
      "h1_eth_hula" to "フラの衣装",
    ),
  )

private fun optionPart259(): CharacterOptionPart =
  CharacterOptionPart(
    "ヨーロッパ・アメリカ（2）",
    false,
    listOf(
      "h1_eth_hawaiian_lei" to "ハワイのレイ",
      "h1_eth_polynesian" to "ポリネシア風衣装",
      "h1_eth_maori" to "マオリ風衣装",
      "h1_eth_aboriginal_none" to "オセアニア風ボディペイント",
      "h1_eth_inuit" to "イヌイットの毛皮服",
      "h1_eth_sami" to "サーミの衣装",
      "h1_eth_viking" to "ヴァイキング風衣装",
      "h1_eth_medieval_peasant" to "中世の農民服",
      "h1_eth_medieval_noble" to "中世貴族の服",
      "h1_eth_renaissance" to "ルネサンスドレス",
      "h1_eth_elizabethan" to "エリザベス朝ドレス",
      "h1_eth_rococo" to "ロココドレス",
      "h1_eth_baroque" to "バロック調の服",
      "h1_eth_victorian" to "ヴィクトリアン・ドレス",
      "h1_eth_regency" to "リージェンシー・ドレス",
      "h1_eth_edwardian" to "エドワーディアン・ドレス",
      "h1_eth_belle_epoque" to "ベル・エポック風",
    ),
  )

private fun optionGenre52(): CharacterOptionGenre =
  CharacterOptionGenre(
    "民族衣装（追加）",
    listOf(
    optionPart255(),
    optionPart256(),
    optionPart257(),
    optionPart258(),
    optionPart259(),
    ),
  )

private fun optionPart260(): CharacterOptionPart =
  CharacterOptionPart(
    "年代",
    true,
    listOf(
      "h1_era_1920s" to "1920年代フラッパー",
      "h1_era_1930s" to "1930年代",
      "h1_era_1940s" to "1940年代",
      "h1_era_1950s" to "1950年代ロカビリー",
      "h1_era_1950s_pinup" to "ピンナップ",
      "h1_era_1960s" to "1960年代モッズ",
      "h1_era_1970s" to "1970年代ディスコ",
      "h1_era_1970s_hippie" to "ヒッピー",
      "h1_era_1980s" to "1980年代",
      "h1_era_1980s_bubble" to "バブル期ボディコン",
      "h1_era_1990s" to "1990年代",
      "h1_era_1990s_kogal_none" to "90年代ストリート",
      "h1_era_2000s" to "2000年代",
      "h1_era_y2k" to "Y2Kファッション",
      "h1_era_retro" to "レトロモダン",
      "h1_era_showa" to "昭和レトロ",
      "h1_era_meiji" to "明治の洋装",
      "h1_era_cyber" to "サイバー近未来",
      "h1_era_neon" to "ネオン近未来",
      "h1_era_future_white" to "白い近未来スーツ",
      "h1_era_post_apoc" to "ポストアポカリプス",
      "h1_era_solarpunk" to "ソーラーパンク",
      "h1_era_atompunk" to "アトムパンク",
      "h1_era_space_age" to "スペースエイジ",
    ),
  )

private fun optionPart261(): CharacterOptionPart =
  CharacterOptionPart(
    "ファッションスタイル（1）",
    false,
    listOf(
      "h1_sty_hiphop" to "ヒップホップ系",
      "h1_sty_skater" to "スケーター系",
      "h1_sty_minimal" to "ミニマル",
      "h1_sty_ivy" to "アイビー",
      "h1_sty_british" to "ブリティッシュ",
      "h1_sty_french_chic" to "フレンチシック",
      "h1_sty_parisian" to "パリジェンヌ",
      "h1_sty_italian" to "イタリアン・マダム",
      "h1_sty_boho" to "ボヘミアン",
      "h1_sty_fairycore" to "フェアリーコア",
      "h1_sty_goblincore" to "ゴブリンコア",
      "h1_sty_dark_academia" to "ダークアカデミア風",
      "h1_sty_light_academia" to "ライトアカデミア風",
      "h1_sty_kawaii" to "カワイイ系",
      "h1_sty_decora" to "デコラ",
      "h1_sty_fairy_kei" to "フェアリーケイ",
      "h1_sty_onee" to "お姉さん系",
      "h1_sty_jirai" to "地雷系",
      "h1_sty_ryousan" to "量産型",
      "h1_sty_gothic" to "ゴシック",
      "h1_sty_emo" to "エモ系",
      "h1_sty_grunge" to "グランジ",
      "h1_sty_biker" to "バイカー",
      "h1_sty_rocker" to "ロッカー",
      "h1_sty_western" to "ウェスタン",
      "h1_sty_safari" to "サファリ",
      "h1_sty_nautical" to "マリン風",
      "h1_sty_resort" to "リゾート",
      "h1_sty_beach" to "ビーチスタイル",
      "h1_sty_ski" to "スキーウェア",
    ),
  )

private fun optionPart262(): CharacterOptionPart =
  CharacterOptionPart(
    "ファッションスタイル（2）",
    false,
    listOf(
      "h1_sty_winter" to "冬のコーデ",
      "h1_sty_spring" to "春のコーデ",
      "h1_sty_summer" to "夏のコーデ",
      "h1_sty_autumn" to "秋のコーデ",
      "h1_sty_rain" to "雨の日のコーデ",
      "h1_sty_loungewear" to "ルームウェア",
      "h1_sty_pajama" to "パジャマ",
      "h1_sty_nightgown" to "ナイトガウン",
      "h1_sty_robe" to "バスローブ",
      "h1_sty_robe_silk" to "シルクのガウン",
      "h1_sty_towel_wrap" to "バスタオル巻き",
      "h1_sty_hoodie_only" to "パーカーだけ",
      "h1_sty_tracksuit" to "ジャージ上下",
      "h1_sty_denim_total" to "デニムオンデニム",
      "h1_sty_all_black" to "全身黒コーデ",
      "h1_sty_all_white" to "全身白コーデ",
      "h1_sty_two_tone" to "ツートンコーデ",
      "h1_sty_monochrome" to "モノトーンコーデ",
      "h1_sty_pastel" to "パステルコーデ",
      "h1_sty_neon_color" to "ビビッドカラー",
      "h1_sty_earth" to "アースカラーコーデ",
      "h1_sty_red_black" to "赤と黒",
      "h1_sty_pink_black" to "ピンクと黒",
      "h1_sty_blue_white" to "青と白",
      "h1_sty_gold_black" to "金と黒",
      "h1_sty_layered" to "重ね着",
      "h1_sty_oversized" to "オーバーサイズ",
      "h1_sty_tight" to "ぴったりした服",
      "h1_sty_loose" to "ゆったりした服",
      "h1_sty_asymmetric" to "アシンメトリー",
    ),
  )

private fun optionPart263(): CharacterOptionPart =
  CharacterOptionPart(
    "ファッションスタイル（3）",
    false,
    listOf(
      "h1_sty_cutout_all" to "カットアウト多め",
      "h1_sty_sheer" to "透け素材",
      "h1_sty_patchwork" to "パッチワーク",
      "h1_sty_embroidery" to "刺繍入り",
      "h1_sty_frills_many" to "フリルたっぷり",
      "h1_sty_ribbons_many" to "リボンたっぷり",
      "h1_sty_lace_many" to "レースたっぷり",
      "h1_sty_fur_trim_many" to "ファートリム",
      "h1_sty_fringe" to "フリンジ",
      "h1_sty_tassel" to "タッセル飾り",
      "h1_sty_sequin" to "スパンコール",
      "h1_sty_glitter_clothes" to "ラメ素材",
      "h1_sty_metallic" to "メタリック素材",
      "h1_sty_holographic" to "ホログラム素材",
      "h1_sty_chain_trim" to "チェーン飾り",
      "h1_sty_zipper_many" to "ジッパー多用",
      "h1_sty_buckle_straps" to "バックル＆ストラップ",
      "h1_sty_lace_up_clothes" to "編み上げ",
      "h1_sty_corset_style" to "コルセットスタイル",
    ),
  )

private fun optionGenre53(): CharacterOptionGenre =
  CharacterOptionGenre(
    "年代・スタイル",
    listOf(
    optionPart260(),
    optionPart261(),
    optionPart262(),
    optionPart263(),
    ),
  )

private fun optionPart264(): CharacterOptionPart =
  CharacterOptionPart(
    "制服・仕事着（1）",
    false,
    listOf(
      "h1_job_nurse_pink" to "ピンクのナース服",
      "h1_job_doctor_coat" to "白衣",
      "h1_job_surgeon" to "手術着",
      "h1_job_scrubs" to "スクラブ",
      "h1_job_pharmacist" to "薬剤師の白衣",
      "h1_job_dentist" to "歯科医",
      "h1_job_vet" to "獣医",
      "h1_job_police_h" to "警察官の制服",
      "h1_job_security" to "警備員",
      "h1_job_swat" to "特殊部隊",
      "h1_job_captain" to "船長",
      "h1_job_sailor_navy" to "海軍士官",
      "h1_job_train_conductor" to "車掌",
      "h1_job_driver" to "運転手",
      "h1_job_taxi" to "タクシー運転手",
      "h1_job_postwoman" to "郵便配達員",
      "h1_job_delivery" to "宅配スタッフ",
      "h1_job_cleaning" to "清掃員",
      "h1_job_housekeeper" to "ハウスキーパー",
      "h1_job_maid_classic" to "クラシックメイド",
      "h1_job_butler" to "執事",
      "h1_job_butler_female" to "女執事",
      "h1_job_waitress_diner" to "ダイナーのウェイトレス",
      "h1_job_sommelier" to "ソムリエ",
      "h1_job_chef_h" to "シェフ",
      "h1_job_baker" to "パン職人",
      "h1_job_cafe_apron" to "カフェエプロン",
      "h1_job_hostess" to "ホステス",
      "h1_job_cabaret" to "キャバクラ嬢風",
      "h1_job_office_lady" to "OL",
    ),
  )

private fun optionPart265(): CharacterOptionPart =
  CharacterOptionPart(
    "制服・仕事着（2）",
    false,
    listOf(
      "h1_job_pants_suit" to "パンツスーツ",
      "h1_job_three_piece" to "スリーピース",
      "h1_job_engineer" to "エンジニア",
      "h1_job_mechanic" to "整備士",
      "h1_job_welder" to "溶接工",
      "h1_job_construction" to "建設作業員",
      "h1_job_fisher" to "漁師",
      "h1_job_hunter" to "狩人",
      "h1_job_explorer" to "探検家",
      "h1_job_photographer" to "カメラマン",
      "h1_job_artist" to "画家",
      "h1_job_potter" to "陶芸家",
      "h1_job_musician_orchestra" to "オーケストラ奏者",
      "h1_job_conductor" to "指揮者",
      "h1_job_pianist" to "ピアニスト",
      "h1_job_singer" to "歌手",
      "h1_job_idol_h" to "アイドル",
      "h1_job_band" to "バンドマン風",
      "h1_job_dancer_h" to "ダンサー",
      "h1_job_ballerina" to "バレリーナ",
      "h1_job_gymnast" to "新体操選手",
      "h1_job_campaign_girl" to "キャンペーンガール",
      "h1_job_model" to "モデル",
      "h1_job_circus" to "サーカス団員",
      "h1_job_clown" to "ピエロ",
      "h1_job_priest" to "聖職者",
      "h1_job_monk" to "僧侶",
      "h1_job_politician" to "政治家",
      "h1_job_diplomat" to "外交官",
      "h1_job_princess_guard" to "女騎士",
    ),
  )

private fun optionPart266(): CharacterOptionPart =
  CharacterOptionPart(
    "制服・仕事着（3）",
    false,
    listOf(
      "h1_job_park_ranger" to "パークレンジャー",
      "h1_job_courier" to "バイク便",
      "h1_job_racing_driver" to "レーシングドライバー",
      "h1_job_fighter_pilot" to "戦闘機パイロット",
      "h1_job_mech_pilot" to "ロボットパイロット",
      "h1_job_stewardess_vintage" to "昭和のスチュワーデス",
    ),
  )

private fun optionPart267(): CharacterOptionPart =
  CharacterOptionPart(
    "ミリタリー（1）",
    false,
    listOf(
      "h1_mil_dress_uniform" to "軍の礼装",
      "h1_mil_combat" to "戦闘服",
      "h1_mil_camo" to "迷彩服",
      "h1_mil_desert" to "砂漠迷彩",
      "h1_mil_woodland" to "森林迷彩",
      "h1_mil_snow" to "雪上迷彩",
      "h1_mil_plate_carrier" to "防弾ベスト",
      "h1_mil_helmet" to "軍用ヘルメット",
      "h1_mil_beret" to "軍のベレー帽",
      "h1_mil_boots" to "軍用ブーツ",
      "h1_mil_officer" to "将校",
      "h1_mil_medals" to "勲章",
      "h1_mil_epaulette" to "肩章",
      "h1_mil_aiguillette" to "飾緒",
      "h1_mil_greatcoat" to "軍用外套",
      "h1_mil_peacoat_naval" to "海軍コート",
      "h1_mil_flight_suit" to "飛行服",
      "h1_mil_g_suit" to "耐Gスーツ",
      "h1_mil_tank_crew" to "戦車兵の服",
      "h1_mil_navy_white" to "海軍の白い軍服",
      "h1_mil_air_force" to "空軍の制服",
      "h1_mil_marine" to "海兵隊風",
      "h1_mil_field_jacket" to "野戦ジャケット",
      "h1_mil_flak_jacket" to "フラックジャケット",
      "h1_mil_cap_field" to "野戦帽",
      "h1_mil_dog_tag" to "ドッグタグ",
      "h1_mil_female_officer" to "女性士官",
      "h1_mil_nurse_wwii" to "軍の看護服",
      "h1_mil_cold_war" to "冷戦風の軍服",
      "h1_mil_ceremonial" to "儀仗兵",
    ),
  )

private fun optionPart268(): CharacterOptionPart =
  CharacterOptionPart(
    "ミリタリー（2）",
    false,
    listOf(
      "h1_mil_royal_guard" to "近衛兵",
      "h1_mil_napoleonic" to "ナポレオン時代の軍服",
      "h1_mil_samurai" to "侍の装束",
      "h1_mil_leather_armor" to "革の鎧",
      "h1_mil_chainmail" to "鎖帷子",
      "h1_mil_fantasy_armor_f" to "女性用ファンタジー鎧",
      "h1_mil_tabard_knight" to "騎士のサーコート",
    ),
  )

private fun optionGenre54(): CharacterOptionGenre =
  CharacterOptionGenre(
    "職業の服（成人のコスプレ）",
    listOf(
    optionPart264(),
    optionPart265(),
    optionPart266(),
    optionPart267(),
    optionPart268(),
    ),
  )

private fun optionPart269(): CharacterOptionPart =
  CharacterOptionPart(
    "競技・運動（1）",
    false,
    listOf(
      "h1_spt_golf" to "ゴルフウェア",
      "h1_spt_marathon" to "マラソン選手",
      "h1_spt_crossfit" to "クロスフィットウェア",
      "h1_spt_boxing" to "ボクシングウェア",
      "h1_spt_kickboxing" to "キックボクシング",
      "h1_spt_mma" to "格闘技ウェア",
      "h1_spt_karate" to "空手着",
      "h1_spt_taekwondo" to "テコンドー道着",
      "h1_spt_kyudo" to "弓道着",
      "h1_spt_wrestling" to "レスリング",
      "h1_spt_pro_wrestling" to "女子プロレス風",
      "h1_spt_snowboard" to "スノーボードウェア",
      "h1_spt_skate_ice" to "フィギュアスケート衣装",
      "h1_spt_speed_skate" to "スピードスケートスーツ",
      "h1_spt_swim_comp" to "競泳水着",
      "h1_spt_swim_cap" to "スイムキャップ",
      "h1_spt_diving_suit" to "ウェットスーツ",
      "h1_spt_surf" to "サーフィンウェア",
      "h1_spt_paddle" to "カヤック",
      "h1_spt_hiking" to "登山ウェア",
      "h1_spt_camping" to "キャンプスタイル",
      "h1_spt_fishing" to "釣り人スタイル",
      "h1_spt_horse" to "乗馬ウェア",
      "h1_spt_archery" to "アーチェリー",
      "h1_spt_soccer" to "サッカーウェア",
      "h1_spt_basketball" to "バスケットウェア",
      "h1_spt_baseball" to "野球ユニフォーム",
      "h1_spt_volleyball" to "バレーボールウェア",
      "h1_spt_rugby" to "ラグビージャージ",
      "h1_spt_american_football" to "アメフト",
    ),
  )

private fun optionPart270(): CharacterOptionPart =
  CharacterOptionPart(
    "競技・運動（2）",
    false,
    listOf(
      "h1_spt_ice_hockey" to "アイスホッケー",
      "h1_spt_dance_modern" to "モダンダンス衣装",
      "h1_spt_pole_dance" to "ポールダンス衣装",
      "h1_spt_ballroom" to "ボールルームドレス",
      "h1_spt_gymnastics" to "体操選手",
      "h1_spt_track" to "陸上競技ユニフォーム",
      "h1_spt_racing_suit" to "レーシングスーツ",
      "h1_spt_jersey_over" to "大きめジャージ",
      "h1_spt_headband" to "スポーツヘアバンド",
      "h1_spt_sports_bra_cross" to "クロスバックスポブラ",
      "h1_spt_compression_shorts" to "コンプレッションショーツ",
      "h1_spt_compression_leggings" to "コンプレッションレギンス",
      "h1_spt_running_shorts" to "ランニングショーツ",
      "h1_spt_track_jacket_h" to "ウォームアップジャケット",
      "h1_spt_sweatband_towel" to "スポーツタオル",
      "h1_spt_sweat" to "汗ばんだ服",
    ),
  )

private fun optionGenre55(): CharacterOptionGenre =
  CharacterOptionGenre(
    "スポーツウェア",
    listOf(
    optionPart269(),
    optionPart270(),
    ),
  )

private fun optionPart271(): CharacterOptionPart =
  CharacterOptionPart(
    "ビキニ・セパレート（1）",
    false,
    listOf(
      "h1_sw_bikini_triangle" to "三角ビキニ",
      "h1_sw_bikini_bralette" to "ブラレットビキニ",
      "h1_sw_bikini_underwire" to "ワイヤー入りビキニ",
      "h1_sw_bikini_push_up" to "パッド入りビキニ",
      "h1_sw_bikini_balconette" to "バルコネットビキニ",
      "h1_sw_bikini_sports" to "スポーツビキニ",
      "h1_sw_bikini_side_tie" to "サイドリボンビキニ",
      "h1_sw_bikini_lowleg" to "ローライズビキニ",
      "h1_sw_bikini_highleg" to "ハイレグビキニ",
      "h1_sw_bikini_thong" to "Tバックビキニ",
      "h1_sw_bikini_brazilian" to "ブラジリアンビキニ",
      "h1_sw_bikini_skirt" to "スカート付きビキニ",
      "h1_sw_bikini_shorts" to "ショーツ型ビキニ",
      "h1_sw_bikini_boyshort" to "ボーイレッグビキニ",
      "h1_sw_bikini_leaf" to "葉っぱビキニ",
      "h1_sw_bikini_venus" to "ヴィーナスビキニ",
      "h1_sw_bikini_cupless" to "カップなしビキニ",
      "h1_sw_bikini_ruffle" to "ラッフルビキニ",
      "h1_sw_bikini_animal" to "アニマル柄ビキニ",
      "h1_sw_bikini_flag" to "星条旗柄ビキニ",
      "h1_sw_bikini_tie_dye" to "タイダイビキニ",
      "h1_sw_bikini_reversible" to "リバーシブルビキニ",
      "h1_sw_bikini_beaded" to "ビーズ飾りビキニ",
      "h1_sw_bikini_shell" to "貝殻ビキニ",
      "h1_sw_bikini_coin" to "コイン飾りビキニ",
      "h1_sw_bikini_chain" to "チェーンビキニ",
      "h1_sw_bikini_sheer" to "透けビキニ",
      "h1_sw_bikini_white" to "白ビキニ",
      "h1_sw_bikini_black" to "黒ビキニ",
      "h1_sw_bikini_red" to "赤ビキニ",
    ),
  )

private fun optionPart272(): CharacterOptionPart =
  CharacterOptionPart(
    "ビキニ・セパレート（2）",
    false,
    listOf(
      "h1_sw_bikini_pink" to "ピンクビキニ",
      "h1_sw_bikini_blue" to "青ビキニ",
      "h1_sw_bikini_yellow" to "黄色いビキニ",
      "h1_sw_bikini_gold" to "金色ビキニ",
      "h1_sw_bikini_neon" to "ネオンビキニ",
    ),
  )

private fun optionPart273(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピース水着・その他（1）",
    false,
    listOf(
      "h1_sw_one_plunge" to "プランジワンピース水着",
      "h1_sw_one_wrap" to "ラップ水着",
      "h1_sw_one_ruched" to "ギャザー水着",
      "h1_sw_one_skirt" to "スカート付き水着",
      "h1_sw_one_high_neck" to "ハイネック水着",
      "h1_sw_one_open_back" to "オープンバック水着",
      "h1_sw_one_turtle" to "タートルネック水着",
      "h1_sw_one_zip" to "ジップ付き水着",
      "h1_sw_one_mesh" to "メッシュ水着",
      "h1_sw_one_strapless" to "ストラップレス水着",
      "h1_sw_one_asym" to "アシンメトリー水着",
      "h1_sw_one_retro" to "レトロスカート水着",
      "h1_sw_one_sporty" to "スポーティ水着",
      "h1_sw_one_thong" to "Tバック水着",
      "h1_sw_one_high_cut" to "ハイカット水着",
      "h1_sw_one_sling_micro" to "極小スリング水着",
      "h1_sw_one_cross" to "クロスストラップ水着",
      "h1_sw_rash_zip" to "ジップラッシュガード",
      "h1_sw_rash_open" to "前を開けたラッシュガード",
      "h1_sw_wetsuit_half" to "半袖ウェットスーツ",
      "h1_sw_wetsuit_unzipped" to "ファスナーを下げたウェットスーツ",
      "h1_sw_cover_shirt" to "水着の上のシャツ",
      "h1_sw_cover_kimono" to "ビーチカバーアップ",
      "h1_sw_cover_pareo" to "パレオ巻き",
      "h1_sw_cover_sarong_beach" to "ビーチサロン",
      "h1_sw_cover_towel" to "ビーチタオル巻き",
      "h1_sw_cover_sheer" to "透ける羽織り",
      "h1_sw_cover_jacket" to "水着にジャケット",
      "h1_sw_cover_hoodie" to "水着にパーカー",
      "h1_sw_cover_straw" to "麦わら帽子と水着",
    ),
  )

private fun optionPart274(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピース水着・その他（2）",
    false,
    listOf(
      "h1_sw_cover_sunglasses" to "サングラスと水着",
      "h1_sw_vintage" to "ヴィンテージ水着",
      "h1_sw_bathing_costume" to "昔の水着",
      "h1_sw_floatie" to "浮き輪を持つ",
      "h1_sw_float_unicorn" to "大きな浮き輪",
      "h1_sw_swim_tube" to "浮き輪に乗る",
      "h1_sw_swim_cap_h" to "水泳帽",
      "h1_sw_goggles" to "水泳ゴーグル",
      "h1_sw_diving_mask" to "ダイビングマスク",
      "h1_sw_bikini_slipping" to "ずれた水着",
      "h1_sw_bikini_tan_strong" to "くっきり日焼け跡",
      "h1_sw_bikini_adjust" to "水着を直す",
      "h1_sw_bikini_tying" to "水着の紐を結ぶ",
      "h1_sw_bikini_untying" to "紐をほどく",
      "h1_sw_swim_brief" to "競泳ビキニ",
      "h1_sw_swim_jammer" to "ジャマー水着",
      "h1_sw_swim_legskin" to "レッグスキン",
    ),
  )

private fun optionGenre56(): CharacterOptionGenre =
  CharacterOptionGenre(
    "水着（追加）",
    listOf(
    optionPart271(),
    optionPart272(),
    optionPart273(),
    optionPart274(),
    ),
  )

private fun optionPart275(): CharacterOptionPart =
  CharacterOptionPart(
    "ブラ・トップ",
    false,
    listOf(
      "h1_lg_bra_full" to "フルカップブラ",
      "h1_lg_bra_underwire" to "ワイヤーブラ",
      "h1_lg_bra_front_clasp" to "フロントホックブラ",
      "h1_lg_bra_racerback" to "レーサーバックブラ",
      "h1_lg_bra_open_cup" to "オープンカップブラ",
      "h1_lg_bra_cupless" to "カップレスブラ",
      "h1_lg_bra_quarter" to "クォーターカップブラ",
      "h1_lg_bra_adhesive" to "ヌーブラ",
      "h1_lg_bra_strap_slip" to "落ちたブラ肩紐",
      "h1_lg_bra_strap_visible" to "見えているブラ紐",
      "h1_lg_bra_peek" to "チラ見えブラ",
      "h1_lg_bra_through_shirt" to "白シャツから透けるブラ",
      "h1_lg_bra_unhooking" to "ホックを外す",
      "h1_lg_bra_two_tone" to "二色ブラ",
      "h1_lg_bra_polka" to "水玉ブラ",
      "h1_lg_bra_striped" to "縞ブラ",
      "h1_lg_bra_floral" to "花柄ブラ",
      "h1_lg_bra_leopard" to "ヒョウ柄ブラ",
      "h1_lg_bra_pink" to "ピンクのブラ",
      "h1_lg_bra_blue" to "水色ブラ",
      "h1_lg_bra_purple" to "紫ブラ",
    ),
  )

private fun optionPart276(): CharacterOptionPart =
  CharacterOptionPart(
    "ショーツ",
    false,
    listOf(
      "h1_lg_pan_bikini" to "ビキニショーツ",
      "h1_lg_pan_brief" to "ブリーフ型ショーツ",
      "h1_lg_pan_hipster" to "ヒップスター",
      "h1_lg_pan_boyshort" to "ボーイショーツ",
      "h1_lg_pan_thong" to "Tバックショーツ",
      "h1_lg_pan_brazilian" to "ブラジリアンショーツ",
      "h1_lg_pan_tanga" to "タンガ",
      "h1_lg_pan_side_tie" to "サイド紐パン",
      "h1_lg_pan_sheer" to "透けショーツ",
      "h1_lg_pan_cotton" to "コットンショーツ",
      "h1_lg_pan_frilled" to "フリルショーツ",
      "h1_lg_pan_ribbon" to "リボンショーツ",
      "h1_lg_pan_animal" to "ヒョウ柄ショーツ",
      "h1_lg_pan_pearl" to "パールショーツ",
      "h1_lg_pan_visible" to "スカートから見える下着",
      "h1_lg_pan_pulled_up" to "食い込んだショーツ",
      "h1_lg_pan_down" to "下げかけたショーツ",
      "h1_lg_pan_knee" to "ひざまで下ろしたショーツ",
      "h1_lg_pan_blue" to "水色ショーツ",
      "h1_lg_pan_purple" to "紫ショーツ",
      "h1_lg_pan_green" to "緑ショーツ",
      "h1_lg_pan_gold" to "金色ショーツ",
    ),
  )

private fun optionPart277(): CharacterOptionPart =
  CharacterOptionPart(
    "ボディ・ガーター・スリップ（1）",
    false,
    listOf(
      "h1_lg_body_teddy" to "テディ",
      "h1_lg_body_bodysuit" to "ランジェリーボディスーツ",
      "h1_lg_body_chemise" to "シュミーズ",
      "h1_lg_body_babydoll" to "ベビードールランジェリー",
      "h1_lg_body_negligee" to "ネグリジェ（透け）",
      "h1_lg_body_nightie" to "ナイティ",
      "h1_lg_body_robe_lace" to "レースローブ",
      "h1_lg_body_kimono_robe" to "ランジェリーガウン",
      "h1_lg_body_corset_lace" to "編み上げコルセット",
      "h1_lg_body_corset_steel" to "スティールボーンコルセット",
      "h1_lg_body_waist_cincher" to "ウエストニッパー",
      "h1_lg_body_girdle" to "ガードル",
      "h1_lg_body_garter_lace" to "レースガーターリング",
      "h1_lg_body_harness" to "ランジェリーハーネス",
      "h1_lg_body_strappy" to "ストラップランジェリー",
      "h1_lg_body_choker_set" to "チョーカー付きランジェリー",
      "h1_lg_body_open_lace" to "オープンランジェリー",
      "h1_lg_body_tied" to "紐ランジェリー",
      "h1_lg_body_ribbon" to "リボンだけの装い",
      "h1_lg_body_black_set" to "黒の上下セット",
      "h1_lg_body_white_set" to "白の上下セット",
      "h1_lg_body_red_set" to "赤の上下セット",
      "h1_lg_body_pink_set" to "ピンクの上下セット",
      "h1_lg_body_purple_set" to "紫の上下セット",
      "h1_lg_body_blue_set" to "青の上下セット",
      "h1_lg_body_matching" to "おそろいの下着",
      "h1_lg_body_silk" to "シルクのランジェリー",
      "h1_lg_body_velvet" to "ベルベットのランジェリー",
      "h1_lg_body_latex" to "ラテックスのランジェリー",
      "h1_lg_body_leather" to "レザーのランジェリー",
    ),
  )

private fun optionPart278(): CharacterOptionPart =
  CharacterOptionPart(
    "ボディ・ガーター・スリップ（2）",
    false,
    listOf(
      "h1_lg_body_sheer_all" to "全体が透ける下着",
      "h1_lg_body_wet_white" to "濡れた白い下着",
      "h1_lg_body_pearls" to "真珠つきランジェリー",
      "h1_lg_body_gems" to "宝石つきランジェリー",
      "h1_lg_body_fur" to "ファーつきランジェリー",
      "h1_lg_body_feather" to "羽根つきランジェリー",
      "h1_lg_body_maid_lingerie" to "メイドランジェリー",
      "h1_lg_body_nurse_lingerie" to "ナースランジェリー",
      "h1_lg_body_bunny_lingerie" to "バニーランジェリー",
      "h1_lg_body_cat_lingerie" to "ねこランジェリー",
      "h1_lg_body_angel_lingerie" to "天使ランジェリー",
      "h1_lg_body_devil_lingerie" to "悪魔ランジェリー",
      "h1_lg_body_santa_lingerie" to "サンタランジェリー",
    ),
  )

private fun optionGenre57(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ランジェリー・下着（追加）",
    listOf(
    optionPart275(),
    optionPart276(),
    optionPart277(),
    optionPart278(),
    ),
  )

private fun optionPart279(): CharacterOptionPart =
  CharacterOptionPart(
    "着崩し方（1）",
    false,
    listOf(
      "h1_dis_shoulder_off" to "肩出し",
      "h1_dis_one_shoulder_off" to "片肩を出す",
      "h1_dis_both_shoulders" to "両肩を出す",
      "h1_dis_strap_slip" to "肩紐が落ちる",
      "h1_dis_strap_one" to "片方の肩紐が落ちる",
      "h1_dis_collar_open" to "襟元を開ける",
      "h1_dis_buttons_open" to "ボタンを外す",
      "h1_dis_buttons_top" to "上のボタンを外す",
      "h1_dis_buttons_half" to "ボタンを半分外す",
      "h1_dis_buttons_missing" to "ボタンが外れる",
      "h1_dis_shirt_front_open" to "前を開けたシャツ",
      "h1_dis_shirt_untucked" to "裾を出したシャツ",
      "h1_dis_shirt_half_tucked" to "半分入れたシャツ",
      "h1_dis_shirt_knot" to "裾を結ぶ",
      "h1_dis_shirt_lift" to "シャツをめくる",
      "h1_dis_shirt_pull" to "裾を引っ張る",
      "h1_dis_shirt_lift_self" to "自分でめくる",
      "h1_dis_skirt_lift" to "スカートをめくる",
      "h1_dis_skirt_wind" to "風でスカートがめくれる",
      "h1_dis_skirt_hold" to "スカートを押さえる",
      "h1_dis_skirt_tuck" to "スカートの裾をたくし上げる",
      "h1_dis_skirt_hitched" to "スカートを腰までまくる",
      "h1_dis_skirt_rolled" to "ウエストで折ったスカート",
      "h1_dis_skirt_slip" to "ずり下がったスカート",
      "h1_dis_skirt_unzipped" to "ファスナーを下げたスカート",
      "h1_dis_pants_unzipped" to "ファスナーを下げたパンツ",
      "h1_dis_pants_button" to "ボタンを外したパンツ",
      "h1_dis_pants_down" to "パンツを下げる",
      "h1_dis_pants_slip" to "ずり下がったパンツ",
      "h1_dis_pants_belt_open" to "ベルトを外す",
    ),
  )

private fun optionPart280(): CharacterOptionPart =
  CharacterOptionPart(
    "着崩し方（2）",
    false,
    listOf(
      "h1_dis_pants_roll" to "裾を折る",
      "h1_dis_zipper_chest" to "胸元までジッパー",
      "h1_dis_jacket_off_shoulders" to "ジャケットを肩から落とす",
      "h1_dis_jacket_open" to "ジャケットを開ける",
      "h1_dis_jacket_drag" to "片腕だけ袖を通す",
      "h1_dis_coat_open" to "コートの前を開ける",
      "h1_dis_robe_slip" to "ローブがはだける",
      "h1_dis_kimono_slip" to "着物が肩からはだける",
      "h1_dis_kimono_nape" to "うなじを見せる着物",
      "h1_dis_kimono_leg" to "裾から足が出る着物",
      "h1_dis_yukata_open" to "はだけた浴衣",
      "h1_dis_dress_strap" to "ドレスの肩紐が落ちる",
      "h1_dis_dress_pulled" to "ドレスをずらす",
      "h1_dis_dress_hem" to "ドレスの裾をつまむ",
      "h1_dis_dress_open_back" to "背中のファスナーが開く",
      "h1_dis_dress_torn" to "ドレスの裂け目",
      "h1_dis_dress_slit_leg" to "スリットから脚",
      "h1_dis_top_down" to "トップスをずらす",
      "h1_dis_top_up" to "トップスをずり上げる",
      "h1_dis_top_aside" to "トップスを横にずらす",
      "h1_dis_bottom_aside" to "ボトムスを横にずらす",
      "h1_dis_bikini_aside" to "水着をずらす",
      "h1_dis_bikini_top_lift" to "水着の上をずらす",
      "h1_dis_clothes_wet" to "濡れて透ける服",
      "h1_dis_clothes_wrinkled" to "しわになった服",
      "h1_dis_clothes_messy" to "乱れた服装",
      "h1_dis_clothes_torn" to "破れた服",
      "h1_dis_clothes_burnt" to "焦げた服",
      "h1_dis_clothes_dirty" to "汚れた服",
      "h1_dis_clothes_mud" to "泥のついた服",
    ),
  )

private fun optionPart281(): CharacterOptionPart =
  CharacterOptionPart(
    "着崩し方（3）",
    false,
    listOf(
      "h1_dis_clothes_stain" to "ワインのしみ",
      "h1_dis_clothes_sweat" to "汗で張り付く服",
      "h1_dis_clothes_cling" to "肌に張り付く服",
      "h1_dis_clothes_stretch" to "引っ張られた服",
      "h1_dis_clothes_tight_chest" to "胸がきつい服",
      "h1_dis_clothes_loose_neck" to "ゆるんだ襟ぐり",
      "h1_dis_clothes_inside_out" to "裏返しの服",
      "h1_dis_clothes_backwards" to "後ろ前の服",
      "h1_dis_clothes_wrong_buttons" to "ボタンのかけ違い",
      "h1_dis_clothes_changing" to "着替え中",
      "h1_dis_clothes_dressing" to "着るところ",
      "h1_dis_clothes_removing_top" to "トップスを脱ぐ",
      "h1_dis_clothes_removing_bottom" to "ボトムスを脱ぐ",
      "h1_dis_clothes_removing_socks" to "靴下を脱ぐ",
      "h1_dis_clothes_removing_shoes" to "靴を脱ぐ",
      "h1_dis_clothes_removing_gloves" to "手袋を脱ぐ",
      "h1_dis_clothes_removing_jacket" to "ジャケットを脱ぐ",
      "h1_dis_clothes_removing_coat" to "コートを脱ぐ",
      "h1_dis_clothes_removing_bra" to "ブラを外す",
      "h1_dis_clothes_removing_dress" to "ドレスを脱ぐ",
      "h1_dis_clothes_dropped" to "床に落ちた服",
      "h1_dis_clothes_pile" to "脱ぎ捨てた服の山",
      "h1_dis_clothes_hanging" to "ハンガーの服",
      "h1_dis_clothes_folded" to "たたんだ服",
      "h1_dis_clothes_bite" to "服の裾を噛む",
      "h1_dis_clothes_grab" to "服をつかむ",
      "h1_dis_clothes_tug" to "服を引っ張る",
      "h1_dis_clothes_sleeve_pull" to "袖を引く",
      "h1_dis_clothes_hide" to "服で隠す",
    ),
  )

private fun optionPart282(): CharacterOptionPart =
  CharacterOptionPart(
    "着崩し方（4）",
    false,
    listOf(
      "h1_dis_clothes_arms_cross" to "腕で隠す",
      "h1_dis_clothes_blanket" to "毛布で隠す",
      "h1_dis_clothes_shirt_hold" to "シャツの前を合わせる",
      "h1_dis_clothes_apron_hold" to "エプロンで隠す",
    ),
  )

private fun optionGenre58(): CharacterOptionGenre =
  CharacterOptionGenre(
    "着崩し・乱れ",
    listOf(
    optionPart279(),
    optionPart280(),
    optionPart281(),
    optionPart282(),
    ),
  )

private fun optionPart283(): CharacterOptionPart =
  CharacterOptionPart(
    "生地・素材（1）",
    false,
    listOf(
      "h1_fab_cotton" to "綿",
      "h1_fab_linen" to "麻",
      "h1_fab_silk" to "絹",
      "h1_fab_satin" to "サテン",
      "h1_fab_velvet" to "ベルベット",
      "h1_fab_velour" to "ベロア",
      "h1_fab_corduroy" to "コーデュロイ",
      "h1_fab_denim" to "デニム",
      "h1_fab_leather" to "革",
      "h1_fab_suede" to "スエード",
      "h1_fab_patent" to "エナメル",
      "h1_fab_latex" to "ラテックス",
      "h1_fab_pvc" to "PVC",
      "h1_fab_rubber" to "ゴム",
      "h1_fab_lace" to "レース",
      "h1_fab_tulle" to "チュール",
      "h1_fab_chiffon" to "シフォン",
      "h1_fab_organza" to "オーガンジー",
      "h1_fab_mesh" to "メッシュ",
      "h1_fab_fishnet" to "網目",
      "h1_fab_wool" to "ウール",
      "h1_fab_cashmere" to "カシミヤ",
      "h1_fab_mohair" to "モヘア",
      "h1_fab_angora" to "アンゴラ",
      "h1_fab_fleece" to "フリース",
      "h1_fab_fur" to "毛皮",
      "h1_fab_faux_fur" to "フェイクファー",
      "h1_fab_feather" to "羽毛",
      "h1_fab_tweed" to "ツイード",
      "h1_fab_flannel" to "フランネル",
    ),
  )

private fun optionPart284(): CharacterOptionPart =
  CharacterOptionPart(
    "生地・素材（2）",
    false,
    listOf(
      "h1_fab_gingham_fabric" to "ギンガム",
      "h1_fab_jersey" to "ジャージ素材",
      "h1_fab_spandex" to "スパンデックス",
      "h1_fab_nylon" to "ナイロン",
      "h1_fab_neoprene" to "ネオプレン",
      "h1_fab_sequin" to "スパンコール生地",
      "h1_fab_lame" to "ラメ生地",
      "h1_fab_brocade" to "ブロケード",
      "h1_fab_jacquard" to "ジャカード",
      "h1_fab_damask" to "ダマスク織",
      "h1_fab_taffeta" to "タフタ",
      "h1_fab_crepe" to "クレープ",
      "h1_fab_canvas" to "キャンバス地",
      "h1_fab_burlap" to "麻袋地",
      "h1_fab_knit" to "ニット",
      "h1_fab_crochet" to "かぎ針編み",
      "h1_fab_ribbed_knit" to "リブ編み",
      "h1_fab_cable_knit" to "ケーブル編み",
      "h1_fab_towel" to "タオル地",
      "h1_fab_transparent" to "透明素材",
      "h1_fab_glow" to "蓄光素材",
      "h1_fab_wet_look" to "ウェットルック",
      "h1_fab_kimono_silk" to "縮緬",
      "h1_fab_tie_dye" to "絞り染め",
      "h1_fab_batik" to "ろうけつ染め",
      "h1_fab_indigo" to "藍染",
    ),
  )

private fun optionPart285(): CharacterOptionPart =
  CharacterOptionPart(
    "柄（1）",
    false,
    listOf(
      "h1_pat_stripe" to "ストライプ",
      "h1_pat_horizontal" to "ボーダー",
      "h1_pat_vertical" to "縦縞",
      "h1_pat_pinstripe" to "ピンストライプ",
      "h1_pat_diagonal" to "斜めストライプ",
      "h1_pat_check" to "チェック柄",
      "h1_pat_plaid" to "タータンチェック",
      "h1_pat_houndstooth" to "千鳥格子",
      "h1_pat_glen" to "グレンチェック",
      "h1_pat_argyle" to "アーガイル",
      "h1_pat_polka" to "水玉",
      "h1_pat_floral" to "花柄",
      "h1_pat_rose" to "バラ柄",
      "h1_pat_sakura" to "桜柄",
      "h1_pat_sunflower" to "ひまわり柄",
      "h1_pat_peony" to "牡丹柄",
      "h1_pat_camellia" to "椿柄",
      "h1_pat_plum" to "梅柄",
      "h1_pat_chrysanthemum" to "菊柄",
      "h1_pat_lily" to "百合柄",
      "h1_pat_morning_glory" to "朝顔柄",
      "h1_pat_spider_lily" to "彼岸花柄",
      "h1_pat_leaf" to "葉っぱ柄",
      "h1_pat_maple" to "紅葉柄",
      "h1_pat_paisley" to "ペイズリー",
      "h1_pat_damask" to "ダマスク柄",
      "h1_pat_chevron" to "シェブロン",
      "h1_pat_herringbone" to "ヘリンボーン",
      "h1_pat_zigzag" to "ジグザグ",
      "h1_pat_seigaiha" to "青海波",
    ),
  )

private fun optionPart286(): CharacterOptionPart =
  CharacterOptionPart(
    "柄（2）",
    false,
    listOf(
      "h1_pat_asanoha" to "麻の葉",
      "h1_pat_ichimatsu" to "市松模様",
      "h1_pat_kikko" to "亀甲",
      "h1_pat_shippo" to "七宝",
      "h1_pat_uroko" to "鱗文様",
      "h1_pat_karakusa" to "唐草",
      "h1_pat_arabesque" to "アラベスク",
      "h1_pat_ukiyo_wave" to "波文様",
      "h1_pat_cloud" to "雲柄",
      "h1_pat_fan" to "扇柄",
      "h1_pat_crane" to "鶴柄",
      "h1_pat_koi" to "鯉柄",
      "h1_pat_butterfly" to "蝶柄",
      "h1_pat_dragon" to "龍柄",
      "h1_pat_tiger" to "虎柄",
      "h1_pat_leopard" to "ヒョウ柄",
      "h1_pat_zebra" to "ゼブラ柄",
      "h1_pat_snake" to "ヘビ柄",
      "h1_pat_cow" to "牛柄",
      "h1_pat_camo" to "迷彩柄",
      "h1_pat_cat_paw" to "肉球柄",
      "h1_pat_star" to "星柄",
      "h1_pat_moon" to "月柄",
      "h1_pat_sun" to "太陽柄",
      "h1_pat_heart" to "ハート柄",
      "h1_pat_skull" to "ドクロ柄",
      "h1_pat_bone" to "骨柄",
      "h1_pat_bat" to "コウモリ柄",
      "h1_pat_cherry" to "さくらんぼ柄",
      "h1_pat_strawberry" to "いちご柄",
    ),
  )

private fun optionPart287(): CharacterOptionPart =
  CharacterOptionPart(
    "柄（3）",
    false,
    listOf(
      "h1_pat_lemon" to "レモン柄",
      "h1_pat_fruit" to "フルーツ柄",
      "h1_pat_watermelon" to "スイカ柄",
      "h1_pat_pineapple" to "パイナップル柄",
      "h1_pat_animal_all" to "動物柄",
      "h1_pat_music" to "音符柄",
      "h1_pat_constellation" to "星座柄",
      "h1_pat_space" to "宇宙柄",
      "h1_pat_sparkle" to "キラキラ柄",
      "h1_pat_clover" to "クローバー柄",
      "h1_pat_crescent" to "三日月柄",
      "h1_pat_wing" to "翼柄",
      "h1_pat_triangle" to "三角柄",
      "h1_pat_ribbon" to "リボン柄",
      "h1_pat_lace" to "レース柄",
      "h1_pat_tropical" to "トロピカル柄",
      "h1_pat_hibiscus" to "ハイビスカス柄",
      "h1_pat_aloha" to "アロハ柄",
      "h1_pat_marble" to "大理石柄",
      "h1_pat_gradient" to "グラデーション",
      "h1_pat_ombre" to "オンブレ",
      "h1_pat_rainbow" to "レインボー",
      "h1_pat_two_tone" to "ツートーン",
      "h1_pat_multicolor" to "マルチカラー",
      "h1_pat_color_block" to "カラーブロック",
      "h1_pat_abstract" to "抽象柄",
      "h1_pat_graffiti" to "グラフィティ柄",
      "h1_pat_pixel" to "ピクセル柄",
      "h1_pat_circuit" to "回路柄",
      "h1_pat_binary" to "デジタル柄",
    ),
  )

private fun optionPart288(): CharacterOptionPart =
  CharacterOptionPart(
    "柄（4）",
    false,
    listOf(
      "h1_pat_plain" to "無地",
    ),
  )

private fun optionGenre59(): CharacterOptionGenre =
  CharacterOptionGenre(
    "生地・柄",
    listOf(
    optionPart283(),
    optionPart284(),
    optionPart285(),
    optionPart286(),
    optionPart287(),
    optionPart288(),
    ),
  )

private fun optionPart289(): CharacterOptionPart =
  CharacterOptionPart(
    "長さ・カット",
    true,
    listOf(
      "h2_very_short_hair" to "ベリーショート",
      "h2_absurdly_long_hair" to "アブノーマルに長い髪",
      "h2_bowl_cut" to "ボウルカット",
      "h2_undercut" to "アンダーカット",
      "h2_flattop" to "フラットトップ",
      "h2_crew_cut" to "クルーカット",
      "h2_inverted_bob" to "インバーテッドボブ（前下がりボブ）",
      "h2_jellyfish_cut" to "クラゲカット",
      "h2_mushroom_cut" to "マッシュルームカット",
      "h2_mullet" to "マレット（襟足長め）",
      "h2_sidecut" to "サイドカット（片側刈り上げ）",
      "h2_bald_female" to "スキンヘッド（成人女性）",
      "h2_big_hair" to "ボリュームのある髪",
      "h2_short_hair_with_long_locks" to "襟足だけ長い髪",
      "h2_blunt_ends" to "毛先がそろった髪",
      "h2_choppy_layers" to "ローレイヤー",
    ),
  )

private fun optionPart290(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方（ポニー・ツイン）",
    false,
    listOf(
      "h2_short_ponytail" to "ショートポニーテール",
      "h2_high_side_ponytail" to "高めサイドポニー",
      "h2_low_side_ponytail" to "低めサイドポニー",
      "h2_front_ponytail" to "前に垂らしたポニーテール",
      "h2_folded_ponytail" to "二つ折りポニーテール",
      "h2_split_ponytail" to "割れポニーテール",
      "h2_short_twintails" to "ショートツインテール",
      "h2_uneven_twintails" to "左右非対称ツインテール",
      "h2_tri_tails" to "三つ結び",
      "h2_quad_tails" to "四つ結び",
      "h2_low_tied_long_hair" to "低い位置で結んだ長い髪",
      "h2_multi_tied_hair" to "複数ゴムで結んだ髪",
      "h2_one_side_up" to "片側だけ結ぶ",
      "h2_two_side_up" to "両側を上で結ぶ",
      "h2_half_up_twintails" to "ハーフツイン",
      "h2_half_up_braid" to "ハーフアップの編み込み",
      "h2_half_up_half_down" to "ハーフアップハーフダウン",
      "h2_topknot" to "ちょんまげ風の結い髪",
    ),
  )

private fun optionPart291(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方（お団子・まとめ髪）",
    false,
    listOf(
      "h2_single_hair_bun" to "シングルお団子",
      "h2_double_bun" to "ダブルお団子",
      "h2_triple_bun" to "トリプルお団子",
      "h2_very_low_bun" to "低い位置のお団子",
      "h2_cone_hair_bun" to "円錐型のお団子",
      "h2_donut_hair_bun" to "ドーナツ型のお団子",
      "h2_heart_hair_bun" to "ハート型のお団子",
      "h2_braided_bun" to "編み込みのお団子",
      "h2_bun_with_braided_base" to "根元を編んだお団子",
      "h2_bantu_knots" to "バンツーノット",
      "h2_hair_rings" to "輪っかの髪",
      "h2_single_hair_ring" to "片側だけ輪っかの髪",
      "h2_tri_hair_rings" to "三つ輪の髪",
      "h2_quad_hair_rings" to "四つ輪の髪",
      "h2_bow_shaped_hair" to "蝶結びの形の髪",
      "h2_flower_shaped_hair" to "花形にまとめた髪",
      "h2_star_shaped_hair" to "星形にまとめた髪",
      "h2_four_leaf_clover_hairstyle" to "四つ葉型の髪",
      "h2_rabbit_hairstyle" to "うさぎ型の髪",
      "h2_french_twist" to "夜会巻き",
      "h2_twisted_updo" to "ツイストアップ",
      "h2_messy_updo" to "ラフなまとめ髪",
      "h2_hair_up" to "クリップ留めのまとめ髪",
    ),
  )

private fun optionPart292(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方（和風・中華風・伝統）",
    false,
    listOf(
      "h2_shimada_hairstyle" to "島田髷",
      "h2_bow_shimada" to "蝶結び島田",
      "h2_marumage" to "丸髷",
      "h2_ichogaeshi" to "銀杏返し",
      "h2_katsuyamamage" to "勝山髷",
      "h2_yuiwata" to "結綿",
      "h2_oiran_hairstyle" to "花魁の結い髪",
      "h2_mizura" to "水車髷",
      "h2_shuangyaji" to "双鴉髻（左右の輪）",
      "h2_danyaji" to "単鴉髻",
      "h2_feixianji" to "飛仙髻",
      "h2_luo_ji" to "螺髻（巻貝のお団子）",
      "h2_double_luo_ji" to "双螺髻",
      "h2_yuanbao_ji" to "元宝髻",
      "h2_hanfu_hairstyle" to "漢服風の結い髪",
    ),
  )

private fun optionPart293(): CharacterOptionPart =
  CharacterOptionPart(
    "編み込み",
    false,
    listOf(
      "h2_single_braid" to "三つ編み1本",
      "h2_low_twin_braids" to "低い位置の二本三つ編み",
      "h2_tri_braids" to "三つ編み3本",
      "h2_quad_braids" to "三つ編み4本",
      "h2_rope_braid" to "ロープ編み",
      "h2_braided_dreadlocks" to "編み込みドレッド",
      "h2_front_braid" to "フロントブレイド",
      "h2_half_crown_braid" to "ハーフクラウンブレイド",
      "h2_braided_ponytail" to "編み込みポニーテール",
      "h2_low_braided_long_hair" to "低い位置の長い三つ編み",
      "h2_four_strand_braid" to "四つ編み",
      "h2_twist_braid" to "ねじり編み",
      "h2_side_braid" to "編みおろし",
    ),
  )

private fun optionPart294(): CharacterOptionPart =
  CharacterOptionPart(
    "髪のボリュームと質感",
    true,
    listOf(
      "h2_huge_afro" to "巨大アフロ",
      "h2_beehive_hairdo" to "ビーハイブ（蜂の巣型）",
      "h2_quiff" to "クイッフ（前髪を立ち上げ）",
      "h2_mohawk" to "モヒカン",
      "h2_pompadour" to "リーゼント風",
      "h2_spiked_hair" to "ツンツン立った髪",
      "h2_pointy_hair" to "とがった髪",
      "h2_ringlets" to "ふんわり巻き毛",
      "h2_twin_drills" to "縦ロール2本",
      "h2_single_drill" to "縦ロール1本",
      "h2_tri_drills" to "縦ロール3本",
      "h2_kinky_hair" to "縮れ毛",
      "h2_messy_long_hair" to "ぼさぼさのロング",
      "h2_sleek_hair" to "しっとりまとまった髪",
      "h2_shiny_hair" to "つやのあるキューティクル髪",
    ),
  )

private fun optionPart295(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の動き・状態",
    false,
    listOf(
      "h2_floating_hair" to "揺れる髪",
      "h2_wind_lift" to "風になびく髪",
      "h2_hair_stuck_to_face" to "汗で張り付いた髪",
      "h2_hair_slicked_back" to "後ろに流した髪",
      "h2_hair_pulled_back" to "後ろにまとめた髪",
      "h2_hair_over_shoulder" to "肩にかかった髪",
      "h2_hair_over_breasts" to "胸にかかる髪",
      "h2_hair_over_one_breast" to "片胸に髪がかかる",
      "h2_hair_wrap" to "髪が体に巻きつく",
      "h2_hair_flip" to "髪をなびかせる",
      "h2_twirling_hair" to "髪の先を指に巻く",
      "h2_adjusting_hair" to "髪を結び直す",
      "h2_tying_hair" to "髪を結ぶ途中",
      "h2_hand_in_own_hair" to "髪を触る",
      "h2_brushing_hair" to "髪をとかす",
      "h2_drying_hair" to "髪を乾かす",
      "h2_towel_on_head" to "タオルで髪を巻く",
    ),
  )

private fun optionPart296(): CharacterOptionPart =
  CharacterOptionPart(
    "ボブ・ショート系",
    true,
    listOf(
      "i1_hair_italian_bob" to "イタリアンボブ",
      "i1_hair_french_bob" to "フレンチボブ",
      "i1_hair_tassel_bob" to "タッセルボブ",
      "i1_hair_kubire_bob" to "くびれボブ",
      "i1_hair_mini_bob" to "ミニボブ",
      "i1_hair_stacked_bob" to "スタックボブ",
      "i1_hair_layered_bob" to "レイヤーボブ",
      "i1_hair_cut_off_bob" to "切りっぱなしボブ",
      "i1_hair_wedge" to "ウェッジカット",
      "i1_hair_pixie_mullet" to "ピクシーマレット",
      "i1_hair_pixie_shag" to "ピクシーシャグ",
      "i1_hair_angled_pixie" to "アングルピクシー",
      "i1_hair_hush_cut" to "ハッシュカット",
      "i1_hair_yosinmori" to "ヨシンモリ",
      "i1_hair_round_short" to "丸みショート",
      "i1_hair_hand_shape" to "ハンサムショート",
      "i1_hair_kitty_cut" to "キティカット",
      "i1_hair_v_cut" to "Vカット",
      "i1_hair_u_cut" to "Uカット",
      "i1_hair_razor_cut" to "レザーカット",
      "i1_hair_bixie" to "ビクシー（ボブとピクシーの間）",
      "i1_hair_cloud_bob" to "雲のようなふんわりボブ",
      "i1_hair_bubble_bob" to "ふくらみボブ（バブルボブ）",
      "i1_hair_glass_bob" to "ガラスのようなツヤボブ",
      "i1_hair_blunt_lob" to "ブラントロブ",
      "i1_hair_clavi_cut" to "クラビカット（鎖骨ライン）",
      "i1_hair_earlobe_bob" to "耳たぶ丈ボブ",
      "i1_hair_jaw_bob" to "あご先ボブ",
    ),
  )

private fun optionPart297(): CharacterOptionPart =
  CharacterOptionPart(
    "ミディアム・ロング系",
    true,
    listOf(
      "i1_hair_airy_midi" to "エアリーミディ",
      "i1_hair_butterfly_cut" to "バタフライカット",
      "i1_hair_semi_butterfly" to "セミバタフライレイヤー",
      "i1_hair_high_layer" to "ハイレイヤー",
      "i1_hair_face_layer" to "フェイスレイヤー",
      "i1_hair_sotohane_midi" to "外ハネミディ",
      "i1_hair_reverse_curl" to "リバースカール",
      "i1_hair_floor_length" to "床につくほど長い髪",
      "i1_hair_knee_length" to "ひざ丈の長い髪",
      "i1_hair_shaggy_long" to "シャギーロング",
      "i1_hair_hime_long" to "姫カットロング",
      "i1_hair_wolf_long" to "ロングウルフ",
      "i1_hair_disconnected" to "ディスコネクト（段差の大きい）",
      "i1_hair_wolf_mullet" to "マレットウルフ",
      "i1_hair_curly_mullet" to "カーリーマレット",
      "i1_hair_asym_chopped" to "アシンメトリーのチョップド",
      "i1_hair_half_shave" to "片側だけ刈り上げ",
      "i1_hair_nape_undercut" to "襟足の刈り上げ",
      "i1_hair_blunt_long" to "ワンレングスロング",
      "i1_hair_gradient_long" to "グラデーションカットのロング",
      "i1_hair_heavy_one_length" to "重めワンレン",
      "i1_hair_wispy_ends" to "毛先が軽い（すき毛）",
      "i1_hair_tapered_tips" to "先細りの毛先",
      "i1_hair_uneven_length" to "長さがふぞろい",
      "i1_hair_back_long_front_short" to "前が短く後ろが長い",
      "i1_hair_front_long_back_short" to "前が長く後ろが短い",
    ),
  )

private fun optionPart298(): CharacterOptionPart =
  CharacterOptionPart(
    "巻き髪・パーマ・うねり",
    true,
    listOf(
      "i1_hair_nuance_perm" to "ニュアンスパーマ",
      "i1_hair_yurufuwa" to "ゆるふわパーマ",
      "i1_hair_sobage" to "ソバージュ",
      "i1_hair_s_wave" to "Sカールのうねり",
      "i1_hair_hollywood_wave" to "ハリウッドウェーブ",
      "i1_hair_pin_curl" to "ピンカール",
      "i1_hair_digital_perm" to "デジタルパーマ風の巻き",
      "i1_hair_mix_curl" to "ミックス巻き",
      "i1_hair_one_curl" to "ワンカール",
      "i1_hair_wet_curl" to "ウェットカール",
      "i1_hair_afro_curls" to "アフロカール",
      "i1_hair_twist_out" to "ツイストアウト",
      "i1_hair_crimped" to "クリンプ（ギザギザの波）",
      "i1_hair_corkscrew" to "コークスクリュー（ねじれ巻き）",
      "i1_hair_loose_ringlet" to "ゆるい縦ロール",
      "i1_hair_wavy_ends" to "毛先だけ波打つ",
      "i1_hair_root_volume" to "根元ふんわりボリューム",
      "i1_hair_flat_iron_straight" to "アイロンでまっすぐ",
      "i1_hair_air_dry" to "自然乾燥のゆるいうねり",
      "i1_hair_bedhead_morning" to "寝起きのボサボサ",
    ),
  )

private fun optionPart299(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方の髪型",
    true,
    listOf(
      "i1_hair_half_twintail" to "ハーフツインテール",
      "i1_hair_side_pony_long" to "サイドに結んだ長いポニー",
      "i1_hair_pony_up" to "ポニーアップ（高い位置の結び）",
      "i1_hair_wavy_pony" to "ウェーブポニーテール",
      "i1_hair_sleek_pony" to "つるんとタイトなポニー",
      "i1_hair_bubble_pony" to "ふくらませポニー（バブルポニー）",
      "i1_hair_half_pony" to "ハーフポニー",
      "i1_hair_four_ties" to "四つ結びのかたまり",
      "i1_hair_one_tie" to "ひとつ結び",
      "i1_hair_nape_tie" to "うなじで結ぶ",
      "i1_hair_tail_inner_twist" to "くるりんぱのひとつ結び",
      "i1_hair_loop_pony" to "輪っかポニー（ループ）",
      "i1_hair_ribbon_twin" to "リボンで結んだツイン",
      "i1_hair_drill_twin" to "縦ロールのツイン",
      "i1_hair_high_bun_pony" to "ポニーと団子の合わせ",
      "i1_hair_twin_buns_low" to "ローツインお団子",
      "i1_hair_triple_bun" to "三つお団子",
      "i1_hair_cinnamon_bun" to "シナモンロール風の巻き団子",
      "i1_hair_space_buns" to "スペースバン（頭上の二つ団子）",
      "i1_hair_donut_bun" to "ドーナツお団子",
      "i1_hair_messy_bun_high" to "高めのラフなお団子",
      "i1_hair_low_bun_sleek" to "低い位置のぴたっとお団子",
      "i1_hair_ballerina_bun" to "バレリーナ風お団子",
      "i1_hair_top_knot_man" to "トップノット（頭頂で結ぶ）",
    ),
  )

private fun optionPart300(): CharacterOptionPart =
  CharacterOptionPart(
    "編み込み・三つ編み系",
    true,
    listOf(
      "i1_hair_boxer_braid" to "ボクサーブレイド（二本の太い編み込み）",
      "i1_hair_milkmaid" to "ミルクメイド編み",
      "i1_hair_loose_braid" to "ゆるい三つ編み",
      "i1_hair_tight_braid" to "きつめの三つ編み",
      "i1_hair_braid_wrap" to "編み込みで巻いたポニー",
      "i1_hair_braid_crown_loose" to "ゆるい王冠アレンジ",
      "i1_hair_back_braid_hair_down" to "後ろで一本編み",
      "i1_hair_braid_accent" to "一房だけの細い三つ編み",
      "i1_hair_braid_beads" to "ビーズ付きの編み髪",
      "i1_hair_braided_tail_loop" to "編み込みループ",
      "i1_hair_viking_braid" to "バイキング風の編み込み",
      "i1_hair_elf_braid" to "エルフ風の細い編み込み",
      "i1_hair_braid_half_up" to "編み込みハーフアップ",
      "i1_hair_lace_braid" to "レース編み",
      "i1_hair_five_strand" to "五つ編み",
      "i1_hair_heart_braid" to "ハート形に編んだ髪",
    ),
  )

private fun optionPart301(): CharacterOptionPart =
  CharacterOptionPart(
    "伝統・和風・歴史の髪",
    true,
    listOf(
      "i1_hair_nihongami" to "日本髪",
      "i1_hair_yuiage" to "結い上げ（和装用）",
      "i1_hair_tsubushi_shimada" to "つぶし島田",
      "i1_hair_momoware" to "桃割れ",
      "i1_hair_maiko_hair" to "舞妓風の髪",
      "i1_hair_tang_bun" to "唐風の高い髷",
      "i1_hair_hanfu_double" to "漢服風のふたつ髷",
      "i1_hair_hanbok_hair" to "韓服風の編み下げ",
      "i1_hair_taira_hair" to "垂髪（すべらかし）",
      "i1_hair_kyokaku" to "平安風の長い黒髪",
      "i1_hair_miko_hair" to "巫女風の結び髪",
      "i1_hair_chonmage_woman" to "女性の小さな髷",
      "i1_hair_odango_kimono" to "着物に合うシニヨン",
      "i1_hair_greek" to "ギリシャ風の編み上げ",
      "i1_hair_roman_curl" to "ローマ風の巻き髪",
      "i1_hair_victorian_updo" to "ヴィクトリア風の盛り髪",
      "i1_hair_gibson" to "ギブソンタック",
      "i1_hair_rococo" to "ロココ風の高い盛り髪",
      "i1_hair_marie_curls" to "縦に巻いた貴婦人の髪",
      "i1_hair_bouffant" to "ブーファン（ふくらませ）",
      "i1_hair_flapper" to "20年代のフラッパー風ボブ",
      "i1_hair_poodle" to "プードルカール（70年代風）",
      "i1_hair_feather_70s" to "70年代のサーファー風羽根カット",
      "i1_hair_80s_big" to "80年代のビッグヘア",
      "i1_hair_90s_pieces" to "90年代のピースヘア",
    ),
  )

private fun optionPart302(): CharacterOptionPart =
  CharacterOptionPart(
    "ファンタジー・二次元の髪",
    false,
    listOf(
      "i1_hair_hair_wings" to "羽のように広がる髪",
      "i1_hair_antenna_double" to "二本のアホ毛",
      "i1_hair_question_ahoge" to "はてなマーク形のアホ毛",
      "i1_hair_long_ahoge" to "長く伸びるアホ毛",
      "i1_hair_ahoge_wiggle" to "ぴょこぴょこ動くアホ毛",
      "i1_hair_smoke_hair" to "煙のようにたなびく髪",
      "i1_hair_star_hair" to "星が混じった髪",
      "i1_hair_vine_hair" to "つるが絡む髪",
      "i1_hair_flower_hair" to "花が咲く髪",
      "i1_hair_leaf_hair" to "葉が混じった髪",
      "i1_hair_tentacle_free" to "ふわふわ生きているような髪",
      "i1_hair_wind_blown" to "強い風になびく髪",
      "i1_hair_underwater" to "水中でただよう髪",
      "i1_hair_gravity_defy" to "重力に逆らって立つ髪",
      "i1_hair_light_particles" to "光の粒がこぼれる髪",
      "i1_hair_ice_hair" to "氷のような髪",
      "i1_hair_lightning_hair" to "稲妻が走る髪",
      "i1_hair_feather_hair" to "羽根が混じる髪",
      "i1_hair_cat_ear_hair" to "猫耳のように立つ髪の房",
      "i1_hair_horn_hair" to "角のようにまとめた髪",
      "i1_hair_drill_hair_fantasy" to "巻き角のようなドリル髪",
      "i1_hair_tail_hair" to "尻尾のように長く伸びる髪",
      "i1_hair_cape_hair" to "マントのように背に広がる髪",
      "i1_hair_halo_hair" to "光の輪のように見える髪",
      "i1_hair_binary_split" to "光と影で分かれた髪",
    ),
  )

private fun optionGenre60(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪型",
    listOf(
    optionPart289(),
    optionPart290(),
    optionPart291(),
    optionPart292(),
    optionPart293(),
    optionPart294(),
    optionPart295(),
    optionPart296(),
    optionPart297(),
    optionPart298(),
    optionPart299(),
    optionPart300(),
    optionPart301(),
    optionPart302(),
    ),
  )

private fun optionPart303(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪の形",
    true,
    listOf(
      "h2_arched_bangs" to "アーチ型前髪",
      "h2_center_flap_bangs" to "中央が分かれた前髪",
      "h2_crossed_bangs" to "交差した前髪",
      "h2_fanged_bangs" to "牙のような前髪",
      "h2_short_bangs" to "短い前髪",
      "h2_long_bangs" to "長い前髪",
      "h2_double_parted_bangs" to "二重に分けた前髪",
      "h2_swept_bangs" to "流した前髪",
      "h2_colored_bangs" to "一部色違いの前髪",
      "h2_bangs_tied_up" to "ちょんまげ前髪（上で結ぶ）",
      "h2_forehead" to "おでこ全開",
      "h2_widow_s_peak" to "ひし形の生え際（富士額）",
      "h2_widow_s_peak_2" to "M字の生え際",
      "h2_coin_bangs" to "コイン型前髪",
      "h2_short_bangs_2" to "眉上の短い前髪",
    ),
  )

private fun optionPart304(): CharacterOptionPart =
  CharacterOptionPart(
    "メカクレ（髪で目が隠れる）",
    true,
    listOf(
      "h2_hair_over_eyes" to "両目が隠れる",
      "h2_eyes_visible_through_hair" to "前髪越しに目が透ける",
      "h2_hair_between_eyes" to "前髪の隙間から片目",
      "h2_eyebrows_hidden_by_hair" to "前髪で眉が隠れる",
      "h2_hair_over_half_of_face" to "長い前髪で顔の半分が隠れる",
      "h2_hair_over_eyes_2" to "目だけ隠れる長い前髪",
      "h2_bangs_reaching_nose" to "鼻まで届く前髪",
      "h2_hair_over_one_eye" to "片目隠れ斜め前髪",
      "h2_shaded_face" to "前髪で目元が影になる",
    ),
  )

private fun optionPart305(): CharacterOptionPart =
  CharacterOptionPart(
    "横髪・もみあげ",
    false,
    listOf(
      "h2_sidelocks" to "触角ヘア（横の束）",
      "h2_asymmetrical_sidelocks" to "左右非対称の横髪",
      "h2_drill_sidelocks" to "縦ロールの横髪",
      "h2_low_tied_sidelocks" to "低く結んだ横髪",
      "h2_sidelocks_tied_back" to "後ろで結んだ横髪",
      "h2_single_sidelock" to "片側だけの横髪",
      "h2_loose_hair_strands" to "顔まわりのほつれ毛",
      "h2_long_sideburns" to "もみあげ（長め）",
      "h2_hair_behind_ear" to "耳にかけた横髪",
      "h2_ears_visible" to "耳出しの髪",
      "h2_hair_strands_on_cheek" to "頬に垂れる髪",
      "h2_hair_intakes" to "インテーク（頬横の髪束）",
      "h2_single_hair_intake" to "片側のインテーク",
    ),
  )

private fun optionPart306(): CharacterOptionPart =
  CharacterOptionPart(
    "アホ毛・頭頂の毛",
    false,
    listOf(
      "h2_heart_ahoge" to "ハートのアホ毛",
      "h2_huge_ahoge" to "巨大アホ毛",
      "h2_multiple_ahoge" to "複数のアホ毛",
      "h2_ahoge" to "動くアホ毛",
      "h2_antenna_hair" to "触角のような毛",
      "h2_heart_antenna_hair" to "ハートの触角ヘア",
      "h2_curly_ahoge" to "くるっと巻いたアホ毛",
      "h2_ahoge_2" to "ぴょんと立つ髪",
      "h2_hair_whorl" to "髪のつむじ",
      "h2_lone_nape_hair" to "ネープの後れ毛",
      "h2_nape" to "うなじを出す",
    ),
  )

private fun optionPart307(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の形ユニーク",
    false,
    listOf(
      "h2_hair_horns" to "髪が角のように立つ",
      "h2_hair_ears" to "耳のように見える髪束",
      "h2_nape_hair" to "ふさふさの襟足",
      "h2_heart_shaped_hair" to "ハート型にまとめた髪",
      "h2_flowing_hair" to "髪が帯状に広がる",
      "h2_fluffy_hair" to "髪が巨大なふわふわ",
    ),
  )

private fun optionGenre61(): CharacterOptionGenre =
  CharacterOptionGenre(
    "前髪・メカクレ・横髪・アホ毛",
    listOf(
    optionPart303(),
    optionPart304(),
    optionPart305(),
    optionPart306(),
    optionPart307(),
    ),
  )

private fun optionPart308(): CharacterOptionPart =
  CharacterOptionPart(
    "髪色（基本色追加）",
    true,
    listOf(
      "h2_aqua_hair" to "水色の髪（アクア）",
      "h2_brown_hair" to "栗色の髪",
      "h2_black_hair" to "暗めの黒髪（濡れ羽色）",
      "h2_dark_brown_hair" to "ダークブラウンの髪",
      "h2_wine_red_hair" to "ワインレッドの髪",
      "h2_coral_pink_hair" to "コーラルピンクの髪",
      "h2_navy_blue_hair" to "ネイビーの髪",
      "h2_turquoise_hair" to "ターコイズの髪",
      "h2_beige_hair" to "ベージュの髪",
      "h2_orange_hair" to "オレンジブロンド",
      "h2_copper_hair" to "カッパーレッド",
    ),
  )

private fun optionPart309(): CharacterOptionPart =
  CharacterOptionPart(
    "髪色の変わり種（柄・複数色）",
    false,
    listOf(
      "h2_multicolored_hair" to "マルチカラーの髪",
      "h2_colored_tips" to "毛先だけ色違い",
      "h2_roots" to "プリン（根元だけ地毛）",
      "h2_split_color_hair" to "左右で色が分かれた髪",
      "h2_striped_hair" to "縞模様の髪",
      "h2_spotted_hair" to "斑点のある髪",
      "h2_patterned_hair" to "模様のある髪",
      "h2_streaked_hair" to "ハイライトの筋",
      "h2_see_through_hair" to "透ける髪",
      "h2_bleached_hair" to "ブリーチした髪",
      "h2_highlights" to "ブロンズのメッシュ",
      "h2_highlights_2" to "ハイライト細め（ベビーライト）",
      "h2_white_inner_hair" to "ホワイトのインナー",
      "h2_blue_inner_hair" to "ブルーのインナー",
      "h2_pink_inner_hair" to "ピンクのインナー",
      "h2_golden_tips" to "金の毛先",
      "h2_ombre_hair" to "色が少しずつ変わるオンブレ",
      "h2_glowing_hair_tips" to "毛先だけ光る髪",
      "h2_starry_hair" to "星空のような髪",
      "h2_fiery_hair" to "炎のような髪",
      "h2_cloud_hair" to "雲のような髪",
      "h2_crystal_hair" to "結晶の髪",
      "h2_plant_hair" to "植物の髪",
      "h2_snake_hair" to "蛇の髪",
      "h2_tentacle_hair" to "触手の髪",
    ),
  )

private fun optionPart310(): CharacterOptionPart =
  CharacterOptionPart(
    "髪質",
    true,
    listOf(
      "h2_straight_hair" to "さらさらストレート",
      "h2_wavy_hair" to "ゆるふわウェーブ",
      "h2_curly_hair_2" to "強めの天然パーマ",
      "h2_spiral_perm" to "スパイラルパーマ",
      "h2_beach_waves" to "ビーチウェーブ",
      "h2_curled_hair" to "コテ巻き",
      "h2_twisted_curls" to "ツイストカール",
      "h2_inward_curl" to "内巻き",
      "h2_flipped_hair" to "外ハネ",
      "h2_messy_hair_2" to "くせ毛",
      "h2_fine_hair" to "細く柔らかい髪",
      "h2_thick_hair" to "太くしっかりした髪",
      "h2_shiny_hair_2" to "ツヤ髪",
      "h2_dry_hair" to "パサついた髪",
    ),
  )

private fun optionPart311(): CharacterOptionPart =
  CharacterOptionPart(
    "毛量",
    true,
    listOf(
      "h2_thin_hair" to "毛量が少ない",
      "h2_thick_hair_2" to "毛量が多い",
      "h2_voluminous_hair" to "ふさふさ",
    ),
  )

private fun optionPart312(): CharacterOptionPart =
  CharacterOptionPart(
    "ヘアアクセサリーの付け方",
    false,
    listOf(
      "h2_turban_headband" to "ターバン風ヘアバンド",
      "h2_barrette" to "バレッタ",
      "h2_claw_clip" to "バンスクリップ",
      "h2_scrunchie" to "シュシュで結ぶ",
      "h2_hair_tie" to "ヘアゴムで結ぶ",
      "h2_crossed_hairpins" to "ヘアピンをクロスで留める",
      "h2_x_hair_ornament" to "X字ヘアピン",
      "h2_flower_hair_ornament" to "フラワーヘアピン",
      "h2_feather_hair_ornament" to "羽根の髪飾り",
      "h2_pearl_hair_ornament" to "真珠の髪飾り",
      "h2_hair_veil" to "ベール付き髪飾り",
      "h2_bell_hair_ornament" to "髪飾りの鈴",
      "h2_hair_scarf" to "スカーフを髪に巻く",
      "h2_flower_in_hair" to "髪にさした花",
      "h2_two_kanzashi" to "かんざし2本",
    ),
  )

private fun optionGenre62(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪色・質感",
    listOf(
    optionPart308(),
    optionPart309(),
    optionPart310(),
    optionPart311(),
    optionPart312(),
    ),
  )

private fun optionPart313(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳の色（基本・複数色）",
    true,
    listOf(
      "h2_hazel_eyes" to "ヘーゼルの瞳",
      "h2_orange_eyes" to "オレンジ色の瞳",
      "h2_yellow_eyes" to "黄色い瞳",
      "h2_white_eyes" to "白い瞳",
      "h2_golden_eyes" to "ゴールドの瞳",
      "h2_silver_eyes" to "シルバーの瞳",
      "h2_emerald_eyes" to "エメラルドの瞳",
      "h2_sapphire_eyes" to "サファイアの瞳",
      "h2_ruby_eyes" to "ルビーの瞳",
      "h2_amethyst_eyes" to "アメジストの瞳",
      "h2_turquoise_eyes" to "ターコイズの瞳",
      "h2_multicolored_eyes" to "マルチカラーの瞳",
      "h2_gradient_eyes" to "グラデーションの瞳",
      "h2_two_tone_eyes" to "二色の瞳",
      "h2_rainbow_eyes" to "虹色の瞳",
      "h2_ringed_eyes" to "縁取りのある瞳",
    ),
  )

private fun optionPart314(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳孔・黒目の形",
    true,
    listOf(
      "h2_constricted_pupils" to "収縮した瞳孔",
      "h2_dilated_pupils" to "開いた瞳孔",
      "h2_horizontal_pupils" to "横長の瞳孔",
      "h2_rectangular_pupils" to "四角い瞳孔",
      "h2_cat_shaped_pupils" to "猫の瞳",
      "h2_crescent_shaped_pupils" to "月の瞳",
      "h2_diamond_shaped_pupils" to "ダイヤの瞳",
      "h2_flower_shaped_pupils" to "花の瞳",
      "h2_gear_shaped_pupils" to "歯車の瞳",
      "h2_triangle_shaped_pupils" to "三角の瞳",
      "h2_clover_shaped_pupils" to "四つ葉の瞳",
      "h2_snowflake_pupils" to "雪の結晶の瞳",
      "h2_lightning_bolt_shaped_pupils" to "稲妻の瞳",
      "h2_butterfly_shaped_pupils" to "蝶の瞳",
      "h2_clock_eyes" to "時計の瞳",
      "h2_no_pupils" to "瞳孔なし",
      "h2_extra_pupils" to "二重瞳孔",
      "h2_mismatched_pupils" to "瞳の色違いの瞳孔",
      "h2_sparkling_eyes" to "十字きらめきの瞳",
    ),
  )

private fun optionPart315(): CharacterOptionPart =
  CharacterOptionPart(
    "白目・目の状態",
    true,
    listOf(
      "h2_black_sclera" to "黒い白目",
      "h2_no_sclera" to "白目のない目",
      "h2_blue_sclera" to "青い白目",
      "h2_yellow_sclera" to "黄色い白目",
      "h2_solid_eyes" to "全面一色の目",
      "h2_blank_eyes" to "がらんどうの目",
      "h2_eye_reflection" to "目に反射する光",
      "h2_glowing_purple_eyes" to "バイオレットの光る目",
      "h2_flaming_eyes" to "炎が燃える目",
      "h2_electric_eyes" to "電気が走る目",
      "h2_mechanical_eyes" to "機械の目",
      "h2_single_mechanical_eye" to "片方だけ機械の目",
      "h2_glass_eye" to "ガラスの目",
      "h2_half_closed_eyes" to "半分閉じた目",
      "h2_narrowed_eyes" to "細めた目",
      "h2_wide_eyed" to "大きく見開いた目",
      "h2_looking_down" to "見下ろす目",
      "h2_sideways_glance" to "斜めに見る目",
      "h2_half_closed_eyes_2" to "うっとりした目",
    ),
  )

private fun optionPart316(): CharacterOptionPart =
  CharacterOptionPart(
    "目の形",
    true,
    listOf(
      "h2_almond_eyes" to "アーモンドアイ",
      "h2_sharp_eyes" to "切れ長のつり目",
      "h2_tareme" to "タレ目",
      "h2_sanpaku" to "三白眼",
      "h2_large_eyes" to "丸く大きな目",
      "h2_monolid" to "奥二重",
      "h2_double_eyelid" to "二重まぶた",
      "h2_mature_eyes" to "アーモンド型の大人目",
      "h2_aegyo_sal" to "涙袋（ふくらみ）",
      "h2_droopy_eyes" to "下がり気味の目尻",
      "h2_upturned_eyes" to "上がり気味の目尻",
      "h2_asymmetrical_eyes" to "左右非対称の目",
    ),
  )

private fun optionPart317(): CharacterOptionPart =
  CharacterOptionPart(
    "まつ毛",
    false,
    listOf(
      "h2_thick_eyelashes" to "濃いまつ毛",
      "h2_colored_eyelashes" to "色付きのまつ毛",
      "h2_lower_eyelashes" to "下まつ毛を強調",
      "h2_curled_eyelashes" to "上向きのまつ毛",
      "h2_white_eyelashes" to "白いまつ毛",
      "h2_detailed_eyelashes" to "まつ毛が一本一本見える",
      "h2_long_thick_eyelashes" to "長く濃いまつ毛",
    ),
  )

private fun optionPart318(): CharacterOptionPart =
  CharacterOptionPart(
    "眉の形・状態",
    true,
    listOf(
      "h2_v_shaped_eyebrows" to "Vの字の眉（怒り眉）",
      "h2_furrowed_brow" to "困り眉",
      "h2_raised_eyebrows" to "両眉を上げる",
      "h2_raised_inner_eyebrows" to "眉頭を上げる",
      "h2_angular_eyebrows" to "山のある眉",
      "h2_drooping_eyebrows" to "下がり眉",
      "h2_short_eyebrows" to "短い眉（麻呂眉風）",
      "h2_thick_eyebrows" to "二重の眉（二重眉毛）",
      "h2_mismatched_eyebrows" to "左右で形が違う眉",
      "h2_colored_eyebrows" to "色付きの眉",
      "h2_white_eyebrows" to "白い眉",
      "h2_eyebrow_cut" to "眉の切れ込み",
      "h2_eyebrow_scar" to "眉の傷",
      "h2_no_eyebrows" to "眉毛なし",
      "h2_furrowed_brow_3" to "眉間のしわ",
      "h2_strong_eyebrows" to "凛々しい眉",
    ),
  )

private fun optionGenre63(): CharacterOptionGenre =
  CharacterOptionGenre(
    "目・眉",
    listOf(
    optionPart313(),
    optionPart314(),
    optionPart315(),
    optionPart316(),
    optionPart317(),
    optionPart318(),
    ),
  )

private fun optionPart319(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻",
    true,
    listOf(
      "h2_dot_nose" to "ドットの鼻",
      "h2_nose_highlight" to "鼻筋のハイライト",
      "h2_red_nose" to "鼻の頭が赤い",
      "h2_small_round_nose" to "鼻が小さく丸い",
      "h2_aquiline_nose" to "鷲鼻",
      "h2_prominent_nose_bridge" to "段のある鼻筋",
      "h2_freckles_on_nose" to "鼻にそばかす",
      "h2_nose_blush" to "鼻に赤み",
      "h2_nostrils_visible" to "鼻の穴が見える",
    ),
  )

private fun optionPart320(): CharacterOptionPart =
  CharacterOptionPart(
    "唇の形・状態",
    true,
    listOf(
      "h2_plump_lips" to "ぷっくり唇",
      "h2_full_lower_lip" to "ふっくら下唇",
      "h2_defined_cupid_s_bow" to "山のはっきりした唇",
      "h2_pale_lips" to "唇の色が淡い",
      "h2_chapped_lips" to "唇が荒れている",
      "h2_colored_lips" to "色付きリップ",
      "h2_pout" to "ふくらんだ口",
      "h2_pout_2" to "とがらせた口",
      "h2_smile" to "口角を上げる",
      "h2_closed_mouth" to "口を結ぶ",
      "h2_parted_lips" to "少し開けた口",
      "h2_open_mouth" to "大きく開けた口",
      "h2_mole_on_lip" to "唇のほくろ",
      "h2_mole_at_corner_of_mouth" to "唇の端のほくろ",
    ),
  )

private fun optionPart321(): CharacterOptionPart =
  CharacterOptionPart(
    "歯・舌",
    false,
    listOf(
      "h2_sharp_teeth" to "ギザ歯",
      "h2_single_fang" to "八重歯（片側）",
      "h2_teeth" to "歯が見える笑顔",
      "h2_clenched_teeth" to "食いしばる歯",
      "h2_gap_teeth" to "歯の隙間（すきっ歯）",
      "h2_tongue_out" to "舌の先を見せる",
      "h2_tongue_marking" to "舌に模様",
      "h2_colored_tongue" to "色付きの舌",
      "h2_even_teeth" to "歯並びの良い口元",
      "h2_gold_tooth" to "金歯",
      "h2_bubble_gum" to "ガムを膨らませる",
      "h2_lollipop_in_mouth" to "飴をくわえる",
    ),
  )

private fun optionPart322(): CharacterOptionPart =
  CharacterOptionPart(
    "歯の種類",
    false,
    listOf(
      "h4_teeth_saw" to "のこぎり状のギザ歯",
      "h4_teeth_shark" to "サメの歯（何列もの歯）",
      "h4_teeth_long_fangs" to "上の牙だけ長い",
      "h4_teeth_both_fangs" to "上下の牙",
      "h4_teeth_tiny_sharp" to "小さな尖った歯",
      "h4_teeth_buck" to "出っ歯",
      "h4_teeth_crooked" to "歯並びが悪い（乱杭歯）",
      "h4_teeth_braces" to "歯列矯正（ブレース）",
      "h4_teeth_snaggle" to "重なった八重歯",
      "h4_teeth_missing" to "前歯が一本欠けている",
      "h4_teeth_diamond" to "ダイヤ付きの歯（グリル）",
      "h4_teeth_silver" to "銀歯・メタルの歯",
      "h4_teeth_beast" to "獣の歯（大きな犬歯）",
      "h4_teeth_vamp_smile" to "笑うと見える吸血鬼の牙",
      "h4_teeth_dragon" to "竜の歯",
      "h4_teeth_black" to "黒い歯",
      "h4_teeth_sparkle" to "白く輝く歯",
      "h4_teeth_big_gap" to "大きなすきっ歯",
      "h4_teeth_sharp_grin" to "ギザ歯でにやりと笑う",
      "h4_teeth_mouth_full" to "口いっぱいの鋭い歯",
    ),
  )

private fun optionPart323(): CharacterOptionPart =
  CharacterOptionPart(
    "ほくろ・そばかす・しわ",
    false,
    listOf(
      "h2_mole_at_corner_of_eye" to "目尻のほくろ",
      "h2_mole_on_neck" to "首筋のほくろ",
      "h2_mole_between_breasts" to "谷間のほくろ",
      "h2_mole_on_shoulder" to "肩のほくろ",
      "h2_mole_on_collarbone" to "鎖骨のほくろ",
      "h2_mole_on_back" to "背中のほくろ",
      "h2_multiple_moles" to "複数のほくろ",
      "h2_freckles_on_cheeks" to "頬いっぱいのそばかす",
      "h2_freckles_on_shoulders" to "肩のそばかす",
      "h2_laugh_lines" to "目尻の笑いじわ",
      "h2_nasolabial_folds" to "ほうれい線",
      "h2_forehead_wrinkles" to "額のしわ",
      "h2_bags_under_eyes" to "目の下のたるみ",
      "h2_neck_wrinkles" to "首のしわ",
    ),
  )

private fun optionPart324(): CharacterOptionPart =
  CharacterOptionPart(
    "頬・あごの特徴",
    false,
    listOf(
      "h2_dimples" to "えくぼ",
      "h2_chubby_cheeks" to "ふっくらした頬",
      "h2_full_face_blush" to "頬全体の赤み",
      "h2_nose_blush_2" to "鼻筋から頬の赤み",
      "h2_heart_on_cheek" to "頬のハート型の模様",
      "h2_bandaid_on_cheek" to "頬に絆創膏",
      "h2_goatee" to "あごひげ（成人）",
      "h2_stubble" to "うっすらひげ",
    ),
  )

private fun optionGenre64(): CharacterOptionGenre =
  CharacterOptionGenre(
    "鼻・口・歯・顔まわり",
    listOf(
    optionPart319(),
    optionPart320(),
    optionPart321(),
    optionPart322(),
    optionPart323(),
    optionPart324(),
    ),
  )

private fun optionPart325(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の色合い",
    true,
    listOf(
      "h2_tan" to "健康的な小麦肌",
      "h2_pink_skin_tone" to "ピンクがかった肌",
      "h2_yellow_undertone_skin" to "黄みのある肌",
      "h2_bronze_skin" to "ブロンズ肌",
      "h2_metal_skin" to "金属質の肌",
      "h2_plastic_skin" to "プラスチックのような肌",
      "h2_silver_skin" to "銀色の肌",
      "h2_two_tone_skin" to "二色の肌",
      "h2_patchwork_skin" to "継ぎ接ぎの肌",
      "h2_body_blush" to "全身が染まった赤み",
    ),
  )

private fun optionPart326(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の質感・つや",
    false,
    listOf(
      "h2_smooth_skin" to "ミルクのように滑らかな肌",
      "h2_moist_skin" to "しっとりした肌",
      "h2_firm_skin" to "ハリのある肌",
      "h2_fine_skin_texture" to "きめ細かい肌",
      "h2_skin_pores" to "毛穴の見える肌",
      "h2_vellus_hair" to "うぶ毛の見える肌",
      "h2_glossy_skin" to "肌に光沢",
      "h2_powdered_skin" to "パウダーを塗った肌",
      "h2_glitter_on_skin" to "グリッターを散らした肌",
      "h2_shimmering_skin" to "きらめくボディパウダー",
    ),
  )

private fun optionPart327(): CharacterOptionPart =
  CharacterOptionPart(
    "汗・濡れ",
    false,
    listOf(
      "h2_sweat" to "汗ばんだ肌",
      "h2_sweatdrop" to "汗が流れる",
      "h2_sweating_profusely" to "全身に汗",
      "h2_sweat_2" to "汗で光る体",
      "h2_water_drops" to "水滴のついた肌",
      "h2_shower" to "シャワーで濡れた体",
      "h2_steam" to "湯気を立てる体",
      "h2_wet_from_rain" to "雨に濡れた体",
      "h2_wet_clothes" to "水に濡れて透ける服",
      "h2_flushed_face" to "火照った頬",
      "h2_full_body_blush" to "全身が赤らむ",
    ),
  )

private fun optionPart328(): CharacterOptionPart =
  CharacterOptionPart(
    "日焼け跡・跡",
    false,
    listOf(
      "h2_tan_lines" to "ビキニの日焼け跡",
      "h2_swimsuit_tan_lines" to "ワンピース水着の日焼け跡",
      "h2_sandal_tan_lines" to "サンダルの日焼け跡",
      "h2_tank_top_tan_lines" to "タンクトップの日焼け跡",
      "h2_panty_line" to "下着の跡",
      "h2_elastic_marks" to "下着の締め付け跡",
      "h2_bra_strap_marks" to "ブラ紐の跡",
      "h2_sock_marks" to "ソックスの跡",
      "h2_stocking_marks" to "ストッキングの跡",
      "h2_scratch_marks" to "赤みのひっかき跡",
      "h2_skin_blemish" to "肌荒れ",
      "h2_veins" to "うっすら青い血管",
      "h2_smooth_stomach" to "妊娠線なし（なめらかなお腹）",
    ),
  )

private fun optionPart329(): CharacterOptionPart =
  CharacterOptionPart(
    "体毛・その他",
    false,
    listOf(
      "h2_hairy_legs" to "毛深い脚",
      "h2_smooth_armpit" to "手入れされた脇",
      "h2_collarbone_shadow" to "鎖骨の影",
      "h2_back_dimples" to "背中のくぼみ（背筋）",
    ),
  )

private fun optionGenre65(): CharacterOptionGenre =
  CharacterOptionGenre(
    "肌",
    listOf(
    optionPart325(),
    optionPart326(),
    optionPart327(),
    optionPart328(),
    optionPart329(),
    ),
  )

private fun optionPart330(): CharacterOptionPart =
  CharacterOptionPart(
    "全体のシルエット（成人）",
    true,
    listOf(
      "h2_slender_body" to "スレンダー体型",
      "h2_slender" to "細身で脚が長い",
      "h2_fat" to "太め体型",
      "h2_tall" to "大柄な体型",
      "h2_petite_adult_woman" to "小柄で細身（成人）",
      "h2_toned_body" to "引き締まった体型",
      "h2_inverted_triangle_body" to "逆三角形の体型",
      "h2_pear_shaped_body" to "洋梨型（下半身にボリューム）",
      "h2_straight_body_shape" to "スレンダーな直線体型",
      "h2_model_figure" to "モデル体型",
      "h2_high_waist" to "腰が高い体型",
    ),
  )

private fun optionPart331(): CharacterOptionPart =
  CharacterOptionPart(
    "身長",
    true,
    listOf(
      "h2_tall_2" to "背が高い",
      "h2_average_height" to "平均的な身長",
      "h2_short_stature_adult" to "背が低め（成人）",
      "h2_tall_and_slender" to "長身で細い",
    ),
  )

private fun optionPart332(): CharacterOptionPart =
  CharacterOptionPart(
    "身長差",
    true,
    listOf(
      "h2_height_difference" to "男性が大きい身長差",
      "h2_height_difference_2" to "女性が高い身長差",
      "h2_similar_height" to "同じくらいの身長",
      "h2_height_difference_3" to "彼女の頭が胸に届く身長差",
    ),
  )

private fun optionPart333(): CharacterOptionPart =
  CharacterOptionPart(
    "筋肉",
    false,
    listOf(
      "h2_obliques" to "腹斜筋",
      "h2_thick_arms" to "太い腕",
      "h2_muscular_shoulders" to "肩の筋肉",
      "h2_pectorals" to "大胸筋",
      "h2_muscular_back" to "背中の筋肉",
      "h2_muscular_calves" to "ふくらはぎの筋肉",
      "h2_groin_tendon" to "Vライン",
      "h2_groin_tendon_2" to "鼠径部の筋",
      "h2_muscular_neck" to "太い首の筋肉",
    ),
  )

private fun optionPart334(): CharacterOptionPart =
  CharacterOptionPart(
    "胸",
    true,
    listOf(
      "h2_slightly_sagging_breasts" to "わずかに垂れた胸",
      "h2_breasts_apart" to "左右に離れた胸",
      "h2_cleavage" to "中央に寄った胸",
      "h2_pointy_breasts" to "先のとがった胸",
      "h2_breasts_squeezed_together" to "腕で寄せた谷間",
      "h2_veiny_breasts" to "胸の血管",
    ),
  )

private fun optionPart335(): CharacterOptionPart =
  CharacterOptionPart(
    "胸元・乳首の見た目",
    true,
    listOf(
      "h2_small_areolae" to "小さめの乳輪",
      "h2_light_areolae" to "淡い乳輪",
      "h2_dark_areolae" to "濃い色の乳輪",
      "h2_covered_nipples" to "服越しに浮く乳首",
    ),
  )

private fun optionPart336(): CharacterOptionPart =
  CharacterOptionPart(
    "腰・お腹・おへそ",
    false,
    listOf(
      "h2_slim_waist" to "細いウエスト",
      "h2_high_waist_2" to "ウエストの高さが高い",
      "h2_waist" to "腰のくびれ",
      "h2_belly" to "ぽっこりお腹",
      "h2_flat_stomach" to "引き締まったお腹",
      "h2_navel" to "縦長のおへそ",
      "h2_round_navel" to "丸いおへそ",
      "h2_shallow_navel" to "浅いおへそ",
    ),
  )

private fun optionPart337(): CharacterOptionPart =
  CharacterOptionPart(
    "尻",
    true,
    listOf(
      "h2_small_ass" to "小さめのお尻",
      "h2_round_ass" to "丸いお尻",
      "h2_huge_ass" to "ボリュームのあるお尻",
      "h2_gigantic_ass" to "とても大きなお尻",
      "h2_toned_ass" to "引き締まったお尻",
      "h2_peach_shaped_ass" to "桃のようなお尻",
      "h2_butt_dimples" to "お尻のえくぼ",
      "h2_ass_crease" to "お尻と太ももの境目",
      "h2_ass_shake" to "お尻が揺れる",
    ),
  )

private fun optionPart338(): CharacterOptionPart =
  CharacterOptionPart(
    "脚・太もも",
    true,
    listOf(
      "h2_toned_thighs" to "太ももが引き締まった",
      "h2_thighs_together" to "太ももがくっつく",
      "h2_knock_kneed" to "X脚",
      "h2_bowlegged" to "O脚",
      "h2_knees" to "きれいな膝",
      "h2_calves" to "ふくらはぎの形がきれい",
      "h2_slim_ankles" to "足首が細い",
      "h2_thick_ankles" to "足首が太い",
      "h2_long_legs_2" to "脚の長さと上半身のバランス",
    ),
  )

private fun optionPart339(): CharacterOptionPart =
  CharacterOptionPart(
    "肩・首・背中",
    false,
    listOf(
      "h2_narrow_shoulders" to "華奢な肩幅",
      "h2_neck" to "首筋",
      "h2_back" to "美しい背中のライン",
      "h2_collarbone" to "鎖骨の形",
    ),
  )

private fun optionGenre66(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体型",
    listOf(
    optionPart330(),
    optionPart331(),
    optionPart332(),
    optionPart333(),
    optionPart334(),
    optionPart335(),
    optionPart336(),
    optionPart337(),
    optionPart338(),
    optionPart339(),
    ),
  )

private fun optionPart340(): CharacterOptionPart =
  CharacterOptionPart(
    "手の形",
    true,
    listOf(
      "h2_slender_hands" to "ほっそりした手",
      "h2_bony_hands" to "骨ばった手",
      "h2_short_fingers" to "短い指",
      "h2_veiny_hands" to "血管の浮いた手",
      "h2_veiny_hands_2" to "手の甲の血管",
      "h2_knuckles" to "節の目立つ指",
      "h2_plump_hands" to "ふっくらした手",
      "h2_palms" to "手のひら",
      "h2_slim_wrists" to "手首が細い",
      "h2_rough_hands" to "ごつごつした手",
      "h2_wrinkled_hands" to "手のしわ",
    ),
  )

private fun optionPart341(): CharacterOptionPart =
  CharacterOptionPart(
    "爪の長さ・形",
    true,
    listOf(
      "h2_squoval_nails" to "先の丸い四角（スクエアオフ）",
      "h2_pointy_nails" to "とがった爪（ポイント）",
      "h2_stiletto_nails" to "ステレット（細長く尖った）",
      "h2_oval_nails" to "オーバル型の爪",
      "h2_curved_long_nails" to "爪が長くて曲がる",
      "h2_natural_nails" to "自然な素爪",
    ),
  )

private fun optionPart342(): CharacterOptionPart =
  CharacterOptionPart(
    "爪の色・ネイル",
    false,
    listOf(
      "h2_blue_nails" to "青いネイル",
      "h2_purple_nails" to "紫のネイル",
      "h2_green_nails" to "緑のネイル",
      "h2_gold_nails" to "金色のネイル",
      "h2_silver_nails" to "銀色のネイル",
      "h2_nude_nails" to "ヌーディーなネイル",
      "h2_beige_nails" to "ベージュのネイル",
      "h2_orange_nails" to "オレンジのネイル",
      "h2_wine_red_nails" to "ワインレッドのネイル",
      "h2_pearl_nails" to "パールのネイル",
      "h2_matte_nails" to "マットなネイル",
      "h2_reverse_french_manicure" to "逆フレンチ",
      "h2_holographic_nails" to "ホログラムネイル",
      "h2_rhinestone_nails" to "ストーンのネイル",
      "h2_floral_nails" to "花柄ネイル",
      "h2_star_nails" to "星柄ネイル",
      "h2_striped_nails" to "ストライプネイル",
      "h2_polka_dot_nails" to "ドットネイル",
      "h2_checkered_nails" to "チェック柄ネイル",
      "h2_3d_nails" to "キラキラ3Dネイル",
      "h2_mismatched_nail_polish" to "左右で違う色の爪",
      "h2_multicolored_nails" to "指ごとに違う色の爪",
      "h2_press_on_nails" to "つけ爪",
    ),
  )

private fun optionPart343(): CharacterOptionPart =
  CharacterOptionPart(
    "足と足の爪",
    false,
    listOf(
      "h2_slender_feet" to "華奢な足",
      "h2_high_arches" to "甲の高い足",
      "h2_veiny_feet" to "足の甲の血管",
      "h2_toes" to "きれいな足指",
      "h2_long_toes" to "長い足指",
      "h2_heels" to "かかと",
      "h2_arched_feet" to "高いアーチの足裏",
      "h2_toenail_polish" to "足の爪に塗ったネイル",
      "h2_black_toenails" to "黒いペディキュア",
      "h2_pink_toenails" to "ピンクのペディキュア",
      "h2_white_toenails" to "白いペディキュア",
      "h2_long_toenails" to "足の爪が長い",
      "h2_tiptoes" to "つま先立ち",
      "h2_spread_toes" to "足の指を広げる",
      "h2_toes_up" to "足の指をそらす",
      "h2_plantar_flexion" to "足の指を伸ばす",
      "h2_dirty_feet" to "汚れた足",
      "h2_pigeon_toed" to "内股のつま先",
    ),
  )

private fun optionGenre67(): CharacterOptionGenre =
  CharacterOptionGenre(
    "手・爪・足",
    listOf(
    optionPart340(),
    optionPart341(),
    optionPart342(),
    optionPart343(),
    ),
  )

private fun optionPart344(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥーの柄",
    false,
    listOf(
      "h2_dragon_tattoo" to "和彫り風の龍",
      "h2_koi_tattoo" to "和彫り風の鯉",
      "h2_peony_tattoo" to "和彫り風の牡丹",
      "h2_cherry_blossom_tattoo" to "和彫り風の桜",
      "h2_hannya_tattoo" to "般若の入れ墨",
      "h2_rose_tattoo" to "バラのタトゥー",
      "h2_heart_tattoo" to "ハートのタトゥー",
      "h2_star_tattoo" to "星のタトゥー",
      "h2_butterfly_tattoo" to "蝶のタトゥー",
      "h2_snake_tattoo" to "蛇のタトゥー",
      "h2_feather_tattoo" to "羽根のタトゥー",
      "h2_wing_tattoo" to "翼のタトゥー",
      "h2_cross_tattoo" to "十字のタトゥー",
      "h2_moon_tattoo" to "月のタトゥー",
      "h2_sun_tattoo" to "太陽のタトゥー",
      "h2_swallow_tattoo" to "ツバメのタトゥー",
      "h2_skull_tattoo" to "スカルのタトゥー",
      "h2_number_tattoo" to "数字のタトゥー",
      "h2_minimalist_tattoo" to "ミニマルな線画のタトゥー",
      "h2_mandala_tattoo" to "マンダラのタトゥー",
      "h2_lace_tattoo" to "レースのようなタトゥー",
      "h2_flower_tattoo" to "花束のタトゥー",
      "h2_vine_tattoo" to "植物のつるのタトゥー",
      "h2_fire_tattoo" to "炎のタトゥー",
      "h2_wave_tattoo" to "波のタトゥー",
      "h2_spider_web_tattoo" to "蜘蛛の巣のタトゥー",
      "h2_cat_tattoo" to "猫のタトゥー",
      "h2_bird_tattoo" to "鳥のタトゥー",
      "h2_small_tattoo" to "ワンポイントのタトゥー",
      "h2_white_ink_tattoo" to "ホワイトインクのタトゥー",
      "h2_colorful_tattoo" to "カラフルなタトゥー",
    ),
  )

private fun optionPart345(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥーの入っている場所",
    false,
    listOf(
      "h2_shoulder_tattoo" to "肩のタトゥー",
      "h2_collarbone_tattoo" to "鎖骨のタトゥー",
      "h2_sternum_tattoo" to "胸の谷間のタトゥー",
      "h2_stomach_tattoo" to "お腹のタトゥー",
      "h2_navel_tattoo" to "へその周りのタトゥー",
      "h2_spine_tattoo" to "背骨に沿ったタトゥー",
      "h2_shoulder_blade_tattoo" to "肩甲骨のタトゥー",
      "h2_side_tattoo" to "脇腹のタトゥー",
      "h2_upper_arm_tattoo" to "上腕のタトゥー",
      "h2_forearm_tattoo" to "前腕のタトゥー",
      "h2_wrist_tattoo" to "手首のタトゥー",
      "h2_calf_tattoo" to "ふくらはぎのタトゥー",
      "h2_foot_tattoo" to "足の甲のタトゥー",
      "h2_ass_tattoo" to "お尻のタトゥー",
      "h2_behind_ear_tattoo" to "耳の後ろのタトゥー",
      "h2_facial_tattoo" to "顔のタトゥー",
      "h2_chest_to_stomach_tattoo" to "胸から腹へのタトゥー",
    ),
  )

private fun optionPart346(): CharacterOptionPart =
  CharacterOptionPart(
    "ピアス（耳）",
    false,
    listOf(
      "h2_ear_piercing" to "耳たぶのピアス",
      "h2_helix_piercing" to "ヘリックスピアス",
      "h2_conch_piercing" to "コンクピアス",
      "h2_long_dangling_earrings" to "ロングピアス",
      "h2_ring_earrings" to "リング状イヤリング",
    ),
  )

private fun optionPart347(): CharacterOptionPart =
  CharacterOptionPart(
    "ピアス（顔・体）",
    false,
    listOf(
      "h2_snake_bites_piercing" to "スネークバイト（口の両端）",
      "h2_medusa_piercing" to "メデューサピアス（人中）",
      "h2_areola_piercing" to "乳輪ピアス",
      "h2_dermal_piercing" to "ダーマルピアス（皮膚留め）",
      "h2_back_piercing" to "背中のピアス",
    ),
  )

private fun optionPart348(): CharacterOptionPart =
  CharacterOptionPart(
    "ボディペイント・ボディアート",
    false,
    listOf(
      "h2_hand_henna" to "手のヘナ",
      "h2_rhinestones_on_face" to "顔のラインストーン",
      "h2_body_jewelry" to "ボディジュエリー",
      "h2_temporary_tattoo" to "一時的なタトゥーシール",
      "h2_glowing_body_paint" to "ライトで光る蛍光ペイント",
      "h2_body_markings" to "マークや紋様",
    ),
  )

private fun optionGenre68(): CharacterOptionGenre =
  CharacterOptionGenre(
    "タトゥー・ピアス・ボディアート",
    listOf(
    optionPart344(),
    optionPart345(),
    optionPart346(),
    optionPart347(),
    optionPart348(),
    ),
  )

private fun optionPart349(): CharacterOptionPart =
  CharacterOptionPart(
    "天使・悪魔系",
    true,
    listOf(
      "h3_race_seraph" to "熾天使",
      "h3_race_cherub" to "智天使",
      "h3_race_archangel" to "大天使",
      "h3_race_throne_angel" to "座天使",
      "h3_race_angel_of_mercy" to "慈悲の天使",
      "h3_race_dark_angel" to "闇の天使",
      "h3_race_broken_halo_angel" to "欠けた光輪の天使",
      "h3_race_archdemon" to "上級悪魔",
      "h3_race_imp" to "インプ",
      "h3_race_demon_noble" to "悪魔貴族",
      "h3_race_hellfire_demon" to "地獄の炎の悪魔",
      "h3_race_ice_demon" to "氷の悪魔",
      "h3_race_shaitan" to "シャイターン",
      "h3_race_asura" to "阿修羅",
      "h3_race_yaksha" to "夜叉",
      "h3_race_rakshasa" to "羅刹",
      "h3_race_mara" to "魔羅の眷属",
      "h3_race_archon" to "アルコーン",
      "h3_race_nephilim" to "ネフィリム",
      "h3_race_guardian_spirit" to "守護霊",
      "h3_race_celestial_maiden" to "天女",
      "h3_race_apsara" to "アプサラス",
    ),
  )

private fun optionPart350(): CharacterOptionPart =
  CharacterOptionPart(
    "鬼・妖怪系",
    true,
    listOf(
      "h3_race_red_oni" to "赤鬼",
      "h3_race_blue_oni" to "青鬼",
      "h3_race_yellow_oni" to "黄鬼",
      "h3_race_green_oni" to "緑鬼",
      "h3_race_black_oni" to "黒鬼",
      "h3_race_one_horn_oni" to "一本角の鬼",
      "h3_race_oni_princess" to "鬼姫",
      "h3_race_crow_tengu" to "烏天狗",
      "h3_race_kappa" to "河童",
      "h3_race_kitsune" to "妖狐",
      "h3_race_tanuki" to "化け狸",
      "h3_race_bakeneko" to "化け猫",
      "h3_race_nopperabo" to "のっぺらぼう風",
      "h3_race_hitotsume" to "一つ目の妖怪",
      "h3_race_hyakume" to "百目",
      "h3_race_futakuchi" to "二口の妖怪",
      "h3_race_nue" to "鵺",
      "h3_race_tsukumogami" to "付喪神",
      "h3_race_karakasa" to "唐傘お化け",
      "h3_race_chochin" to "提灯お化け",
      "h3_race_zashiki_warashi_like" to "座敷の守り神",
      "h3_race_tsuchigumo" to "土蜘蛛",
      "h3_race_ayakashi" to "あやかし",
      "h3_race_hitodama_spirit" to "人魂の化身",
      "h3_race_kodama" to "木霊",
      "h3_race_yamauba_young" to "山姫",
      "h3_race_raijin" to "雷神",
      "h3_race_fujin" to "風神",
      "h3_race_ryujin" to "竜神",
      "h3_race_dragon_girl_japan" to "和風の龍人",
      "h3_race_kirin" to "麒麟",
      "h3_race_hou_ou" to "鳳凰",
      "h3_race_baku" to "獏",
      "h3_race_kamaitachi" to "鎌鼬",
      "h3_race_amabie" to "アマビエ風",
      "h3_race_ushi_oni" to "牛鬼",
      "h3_race_yamata" to "八岐大蛇の娘",
      "h3_race_shikigami" to "式神",
      "h3_race_inugami" to "犬神",
      "h3_race_oboro" to "朧の精",
    ),
  )

private fun optionPart351(): CharacterOptionPart =
  CharacterOptionPart(
    "妖精・精霊系",
    true,
    listOf(
      "h3_race_flower_fairy" to "花の妖精",
      "h3_race_forest_fairy" to "森の妖精",
      "h3_race_sea_fairy" to "海の妖精",
      "h3_race_moon_fairy" to "月の妖精",
      "h3_race_sun_fairy" to "太陽の妖精",
      "h3_race_ice_fairy" to "氷の妖精",
      "h3_race_dew_fairy" to "朝露の妖精",
      "h3_race_firefly_fairy" to "蛍の妖精",
      "h3_race_mushroom_fairy" to "きのこの妖精",
      "h3_race_nymph" to "ニンフ",
      "h3_race_naiad" to "ナイアス",
      "h3_race_oread" to "山の精霊",
      "h3_race_undine" to "ウンディーネ",
      "h3_race_salamander" to "サラマンダー",
      "h3_race_sylph" to "シルフ",
      "h3_race_gnome_spirit" to "ノーム",
      "h3_race_will_o_wisp" to "鬼火の精",
      "h3_race_banshee" to "バンシー",
      "h3_race_sidhe" to "シー",
      "h3_race_seelie" to "シーリーコート",
      "h3_race_unseelie" to "アンシーリーコート",
      "h3_race_fairy_queen" to "妖精の女王",
      "h3_race_titania_like" to "夏の妖精女王風",
      "h3_race_sprite" to "スプライト",
      "h3_race_will_wood" to "ウッドエルフ",
      "h3_race_sylvan" to "森の民",
      "h3_race_leprechaun_like" to "緑の小人",
      "h3_race_brownie" to "ブラウニー",
      "h3_race_selkie" to "セルキー",
      "h3_race_cait_sith" to "ケット・シー",
      "h3_race_kelpie" to "ケルピー",
      "h3_race_elemental_lord" to "精霊王",
      "h3_race_spirit_of_lake" to "湖の精霊",
      "h3_race_spirit_of_wind" to "風の精霊",
      "h3_race_spirit_of_stone" to "石の精霊",
      "h3_race_spirit_of_rain" to "雨の精霊",
      "h3_race_spirit_of_dawn" to "暁の精霊",
      "h3_race_spirit_of_dusk" to "黄昏の精霊",
      "h3_race_spirit_of_starlight" to "星光の精霊",
    ),
  )

private fun optionPart352(): CharacterOptionPart =
  CharacterOptionPart(
    "エルフ・亜人系",
    true,
    listOf(
      "h3_race_high_elf" to "ハイエルフ",
      "h3_race_snow_elf" to "スノーエルフ",
      "h3_race_sea_elf" to "シーエルフ",
      "h3_race_blood_elf_like" to "紅の森の耳長族",
      "h3_race_desert_elf" to "砂漠のエルフ",
      "h3_race_half_elf" to "ハーフエルフ",
      "h3_race_dwarf_girl" to "ドワーフの女",
      "h3_race_halfling_adult" to "ハーフリング",
      "h3_race_gnome_adult" to "ノームの女",
      "h3_race_orc_girl" to "オーク族",
      "h3_race_half_orc" to "ハーフオーク",
      "h3_race_goblin_girl" to "ゴブリン族",
      "h3_race_hobgoblin" to "ホブゴブリン",
      "h3_race_troll_girl" to "トロール族",
      "h3_race_ogre_girl" to "オーガ族",
      "h3_race_giantess_adult" to "巨人族",
      "h3_race_cyclops_girl" to "サイクロプス族",
      "h3_race_minotaur_girl" to "ミノタウロス族",
      "h3_race_satyr_girl" to "サテュロス族",
      "h3_race_faun" to "ファウン",
      "h3_race_lizardfolk" to "リザードマン",
      "h3_race_kobold_girl" to "コボルト族",
      "h3_race_lamia" to "ラミア",
      "h3_race_gorgon" to "ゴルゴン",
      "h3_race_medusa_like" to "蛇髪の女",
      "h3_race_merman_like" to "半魚人",
      "h3_race_arachne" to "アラクネ",
      "h3_race_alraune" to "アルラウネ",
      "h3_race_zombie_girl_cute" to "ゾンビ娘（かわいい系）",
      "h3_race_skeleton_girl" to "スケルトン娘",
      "h3_race_lich" to "リッチ",
      "h3_race_dullahan" to "デュラハン",
      "h3_race_wraith" to "レイス",
      "h3_race_mummy_girl" to "ミイラ娘",
      "h3_race_werecat" to "人猫",
      "h3_race_dhampir" to "ダンピール",
      "h3_race_vampire_queen" to "吸血鬼の女王",
      "h3_race_bat_girl" to "コウモリ娘",
      "h3_race_fairy_dragon" to "小竜の化身",
    ),
  )

private fun optionGenre69(): CharacterOptionGenre =
  CharacterOptionGenre(
    "種族（追加2）",
    listOf(
    optionPart349(),
    optionPart350(),
    optionPart351(),
    optionPart352(),
    ),
  )

private fun optionPart353(): CharacterOptionPart =
  CharacterOptionPart(
    "獣耳・尻尾の種類",
    true,
    listOf(
      "h3_beast_fox" to "狐獣人",
      "h3_beast_cat" to "猫獣人",
      "h3_beast_dog" to "犬獣人",
      "h3_beast_rabbit" to "兎獣人",
      "h3_beast_tiger" to "虎獣人",
      "h3_beast_lion" to "獅子獣人",
      "h3_beast_leopard" to "豹獣人",
      "h3_beast_cheetah" to "チーター獣人",
      "h3_beast_panther" to "黒豹獣人",
      "h3_beast_snow_leopard" to "ユキヒョウ獣人",
      "h3_beast_bear" to "熊獣人",
      "h3_beast_panda" to "パンダ獣人",
      "h3_beast_red_panda" to "レッサーパンダ獣人",
      "h3_beast_raccoon" to "アライグマ獣人",
      "h3_beast_squirrel" to "リス獣人",
      "h3_beast_hamster" to "ハムスター獣人",
      "h3_beast_mouse" to "ネズミ獣人",
      "h3_beast_deer" to "鹿獣人",
      "h3_beast_reindeer" to "トナカイ獣人",
      "h3_beast_sheep" to "羊獣人",
      "h3_beast_goat" to "山羊獣人",
      "h3_beast_cow" to "牛獣人",
      "h3_beast_horse" to "馬獣人",
      "h3_beast_unicorn" to "ユニコーン獣人",
      "h3_beast_zebra" to "シマウマ獣人",
      "h3_beast_giraffe" to "キリン獣人",
      "h3_beast_elephant" to "象獣人",
      "h3_beast_hippo" to "カバ獣人",
      "h3_beast_rhino" to "サイ獣人",
      "h3_beast_camel" to "ラクダ獣人",
      "h3_beast_alpaca" to "アルパカ獣人",
      "h3_beast_monkey" to "猿獣人",
      "h3_beast_gorilla" to "ゴリラ獣人",
      "h3_beast_sloth" to "ナマケモノ獣人",
      "h3_beast_koala" to "コアラ獣人",
      "h3_beast_kangaroo" to "カンガルー獣人",
      "h3_beast_otter" to "カワウソ獣人",
      "h3_beast_seal" to "アザラシ獣人",
      "h3_beast_bat" to "コウモリ獣人",
      "h3_beast_hedgehog" to "ハリネズミ獣人",
      "h3_beast_skunk" to "スカンク獣人",
      "h3_beast_badger" to "アナグマ獣人",
      "h3_beast_weasel" to "イタチ獣人",
      "h3_beast_meerkat" to "ミーアキャット獣人",
      "h3_beast_hyena" to "ハイエナ獣人",
      "h3_beast_jackal" to "ジャッカル獣人",
      "h3_beast_coyote" to "コヨーテ獣人",
      "h3_beast_fennec" to "フェネック獣人",
      "h3_beast_arctic_fox" to "ホッキョクギツネ獣人",
      "h3_beast_silver_fox" to "銀狐獣人",
      "h3_beast_lynx" to "オオヤマネコ獣人",
      "h3_beast_sphynx" to "無毛猫獣人",
      "h3_beast_calico" to "三毛猫獣人",
      "h3_beast_siamese" to "シャム猫獣人",
      "h3_beast_husky" to "ハスキー獣人",
      "h3_beast_shiba" to "柴犬獣人",
      "h3_beast_retriever" to "レトリーバー獣人",
      "h3_beast_dalmatian" to "ダルメシアン獣人",
    ),
  )

private fun optionPart354(): CharacterOptionPart =
  CharacterOptionPart(
    "鳥・爬虫類・水生・昆虫",
    true,
    listOf(
      "h3_bird_owl" to "フクロウ獣人",
      "h3_bird_eagle" to "ワシ獣人",
      "h3_bird_hawk" to "タカ獣人",
      "h3_bird_crow" to "カラス獣人",
      "h3_bird_swan" to "白鳥獣人",
      "h3_bird_crane" to "鶴獣人",
      "h3_bird_peacock" to "孔雀獣人",
      "h3_bird_parrot" to "オウム獣人",
      "h3_bird_penguin" to "ペンギン獣人",
      "h3_bird_flamingo" to "フラミンゴ獣人",
      "h3_bird_sparrow" to "スズメ獣人",
      "h3_bird_duck" to "アヒル獣人",
      "h3_bird_chicken" to "鶏獣人",
      "h3_bird_hummingbird" to "ハチドリ獣人",
      "h3_bird_kingfisher" to "カワセミ獣人",
      "h3_bird_phoenix" to "不死鳥獣人",
      "h3_bird_thunderbird" to "雷鳥の化身",
      "h3_rep_snake" to "蛇獣人",
      "h3_rep_python" to "ニシキヘビ獣人",
      "h3_rep_cobra" to "コブラ獣人",
      "h3_rep_gecko" to "ヤモリ獣人",
      "h3_rep_chameleon" to "カメレオン獣人",
      "h3_rep_crocodile" to "ワニ獣人",
      "h3_rep_turtle" to "亀獣人",
      "h3_rep_frog" to "カエル獣人",
      "h3_rep_axolotl" to "ウーパールーパー獣人",
      "h3_rep_salamander" to "イモリ獣人",
      "h3_aqua_shark" to "サメ獣人",
      "h3_aqua_whale" to "クジラ獣人",
      "h3_aqua_dolphin" to "イルカ獣人",
      "h3_aqua_orca" to "シャチ獣人",
      "h3_aqua_octopus" to "タコ獣人",
      "h3_aqua_squid" to "イカ獣人",
      "h3_aqua_jellyfish" to "クラゲ獣人",
      "h3_aqua_crab" to "カニ獣人",
      "h3_aqua_lobster" to "エビ獣人",
      "h3_aqua_goldfish" to "金魚獣人",
      "h3_aqua_betta" to "ベタ獣人",
      "h3_aqua_anglerfish" to "チョウチンアンコウ獣人",
      "h3_aqua_seahorse" to "タツノオトシゴ獣人",
      "h3_aqua_starfish" to "ヒトデ獣人",
      "h3_aqua_coral" to "サンゴの人魚",
      "h3_aqua_nautilus" to "オウムガイ獣人",
      "h3_ins_bee" to "蜂の娘",
      "h3_ins_ladybug" to "てんとう虫の娘",
      "h3_ins_dragonfly" to "トンボの娘",
      "h3_ins_firefly" to "蛍の娘",
      "h3_ins_ant" to "蟻の娘",
      "h3_ins_mantis" to "カマキリの娘",
      "h3_ins_beetle" to "カブトムシの娘",
      "h3_ins_stag_beetle" to "クワガタの娘",
      "h3_ins_cicada" to "蝉の娘",
      "h3_ins_cricket" to "コオロギの娘",
      "h3_ins_silkworm" to "蚕の娘",
      "h3_ins_scorpion" to "サソリの娘",
      "h3_ins_spider" to "蜘蛛の娘",
    ),
  )

private fun optionGenre70(): CharacterOptionGenre =
  CharacterOptionGenre(
    "獣人・動物系（追加2）",
    listOf(
    optionPart353(),
    optionPart354(),
    ),
  )

private fun optionPart355(): CharacterOptionPart =
  CharacterOptionPart(
    "角",
    true,
    listOf(
      "h3_horn_double_small" to "小さな二本角",
      "h3_horn_curved" to "湾曲した角",
      "h3_horn_long_straight" to "長い直角",
      "h3_horn_glowing" to "光る角",
      "h3_horn_crystal" to "水晶の角",
      "h3_horn_gold" to "金の角",
      "h3_horn_black" to "黒い角",
      "h3_horn_white" to "白い角",
      "h3_horn_red" to "赤い角",
      "h3_horn_blue" to "青い角",
      "h3_horn_broken" to "折れた角",
      "h3_horn_ringed" to "輪飾りのついた角",
      "h3_horn_flame" to "炎の角",
      "h3_horn_ice" to "氷の角",
      "h3_horn_branching" to "分かれた角",
      "h3_horn_unicorn" to "ユニコーンの角",
      "h3_horn_hair_tip" to "髪から伸びる角",
      "h3_horn_tiny_pair" to "ちょこんとした角",
      "h3_horn_triple" to "三本の角",
      "h3_horn_headband" to "角のカチューシャ",
    ),
  )

private fun optionPart356(): CharacterOptionPart =
  CharacterOptionPart(
    "翼",
    true,
    listOf(
      "h3_wing_angel_white" to "白い天使の翼",
      "h3_wing_angel_gold" to "金の翼",
      "h3_wing_insect" to "透明な虫の羽",
      "h3_wing_dragonfly" to "トンボの羽",
      "h3_wing_ice" to "氷の翼",
      "h3_wing_water" to "水の翼",
      "h3_wing_six" to "六枚の翼",
      "h3_wing_small" to "小さな翼",
      "h3_wing_giant" to "巨大な翼",
      "h3_wing_peacock" to "孔雀の羽の翼",
      "h3_wing_peacock_blue" to "青緑の羽の翼",
      "h3_wing_owl" to "フクロウの翼",
      "h3_wing_ragged" to "ぼろぼろの翼",
      "h3_wing_flower" to "花びらの翼",
      "h3_wing_leaf" to "葉の翼",
      "h3_wing_star" to "星屑の翼",
      "h3_wing_halo" to "光輪つき翼",
      "h3_wing_head" to "頭の翼",
      "h3_wing_back_ribbons" to "リボンの翼",
      "h3_wing_stained_glass" to "ステンドグラスの翼",
    ),
  )

private fun optionPart357(): CharacterOptionPart =
  CharacterOptionPart(
    "尻尾・耳・触角",
    true,
    listOf(
      "h3_tail_fox_fluffy" to "ふさふさの狐の尻尾",
      "h3_tail_two" to "二本の尻尾",
      "h3_tail_demon" to "悪魔の尻尾",
      "h3_tail_heart" to "ハート型の尻尾",
      "h3_tail_dragon" to "竜の尻尾",
      "h3_tail_fish" to "魚の尾びれ",
      "h3_tail_snake" to "蛇の下半身",
      "h3_tail_peacock" to "孔雀の尾羽",
      "h3_tail_bird" to "鳥の尾羽",
      "h3_tail_scorpion" to "さそりの尾",
      "h3_tail_flame" to "炎の尻尾",
      "h3_tail_ice" to "氷の尻尾",
      "h3_tail_crystal" to "水晶の尻尾",
      "h3_tail_ribbon" to "リボンの尻尾",
      "h3_tail_ringed" to "縞模様の尻尾",
      "h3_tail_bushy" to "ふくらんだ尻尾",
      "h3_tail_white_tip" to "先が白い尻尾",
      "h3_ear_elf" to "尖った長い耳",
      "h3_ear_fin" to "ヒレの耳",
      "h3_ear_feather" to "羽根の耳",
      "h3_ear_bat" to "コウモリの耳",
      "h3_ear_rabbit_long" to "長いうさ耳",
      "h3_ear_four" to "四つの耳",
      "h3_ear_crystal" to "水晶の耳",
      "h3_antenna_insect" to "虫の触角",
      "h3_antenna_feather" to "羽毛状の触角",
      "h3_antenna_glowing" to "光る触角",
      "h3_antenna_ahoge" to "触角のようなあほ毛",
      "h3_antenna_snail" to "カタツムリの目",
      "h3_ear_horn_hair" to "耳の房毛",
    ),
  )

private fun optionPart358(): CharacterOptionPart =
  CharacterOptionPart(
    "肌・鱗・甲殻・素材の体",
    true,
    listOf(
      "h3_skin_scales_fine" to "細かい鱗",
      "h3_skin_scales_rainbow" to "虹色の鱗",
      "h3_skin_scales_gold" to "金の鱗",
      "h3_skin_scales_silver" to "銀の鱗",
      "h3_skin_dragon_scales" to "竜の鱗",
      "h3_skin_fish_scales" to "魚の鱗",
      "h3_skin_shell" to "貝殻の甲殻",
      "h3_skin_chitin" to "光沢のある外骨格",
      "h3_skin_feather" to "羽毛の肌",
      "h3_skin_fur_body" to "全身の毛皮",
      "h3_skin_fur_patches" to "部分的な毛並み",
      "h3_skin_moss" to "苔むした肌",
      "h3_skin_bark" to "樹皮の肌",
      "h3_skin_clay" to "粘土の肌",
      "h3_skin_doll" to "人形のような肌",
      "h3_skin_glass" to "ガラスの肌",
      "h3_skin_crystal" to "結晶の肌",
      "h3_skin_gem" to "宝石の肌",
      "h3_skin_metal_silver" to "銀色の金属の肌",
      "h3_skin_metal_gold" to "金色の金属の肌",
      "h3_skin_copper" to "銅色の肌",
      "h3_skin_chrome" to "鏡面の肌",
      "h3_skin_translucent" to "半透明の肌",
      "h3_skin_slime" to "ゼリー状の体",
      "h3_skin_water" to "水でできた体",
      "h3_skin_fire" to "炎の体",
      "h3_skin_ice" to "氷の体",
      "h3_skin_wind" to "風の体",
      "h3_skin_light" to "光の体",
      "h3_skin_starry" to "星空の肌",
      "h3_skin_candy" to "飴細工の体",
      "h3_skin_origami" to "紙でできた体",
      "h3_skin_cloth" to "布でできた体",
      "h3_skin_wood_doll" to "木製の人形の体",
      "h3_skin_candle_wax" to "蝋の肌",
      "h3_skin_pearl" to "真珠の肌",
      "h3_skin_opal" to "オパールの肌",
      "h3_skin_cracked_porcelain" to "ひび割れた陶器",
      "h3_skin_kintsugi" to "金継ぎの肌",
      "h3_skin_tattoo_glow" to "光る模様の肌",
      "h3_skin_markings_tribal" to "部族の紋様",
      "h3_skin_constellation" to "星座模様の肌",
      "h3_skin_spots_leopard" to "豹柄の肌",
      "h3_skin_gradient" to "グラデーションの肌",
    ),
  )

private fun optionPart359(): CharacterOptionPart =
  CharacterOptionPart(
    "多腕・多目・特殊な体",
    true,
    listOf(
      "h3_body_many_arms" to "千手のような腕",
      "h3_body_two_heads" to "二つの頭",
      "h3_body_three_faces" to "三つの顔",
      "h3_body_extra_eyes" to "多目",
      "h3_body_four_eyes" to "四つの目",
      "h3_body_eyes_hand" to "手のひらの目",
      "h3_body_eyes_on_wings" to "翼の目",
      "h3_body_flower_hair" to "花の髪",
      "h3_body_wire_hair" to "ケーブルの髪",
      "h3_body_floating_hair" to "宙に浮く髪",
      "h3_body_halo_floating" to "浮かぶ光輪",
      "h3_body_hovering" to "足が浮いた下半身",
      "h3_body_wisp_lower" to "霧の下半身",
      "h3_body_long_arms" to "長い腕",
      "h3_body_claw_hands" to "鉤爪の手",
      "h3_body_bird_legs" to "鳥の脚",
      "h3_body_hooves" to "ひづめの脚",
      "h3_body_fins_arms" to "ヒレの腕",
      "h3_body_no_pupils" to "白い目",
      "h3_body_galaxy_eyes" to "銀河の瞳",
      "h3_body_flame_eyes" to "炎の瞳",
      "h3_body_gem_eyes" to "宝石の瞳",
    ),
  )

private fun optionGenre71(): CharacterOptionGenre =
  CharacterOptionGenre(
    "身体の特徴（追加2）",
    listOf(
    optionPart355(),
    optionPart356(),
    optionPart357(),
    optionPart358(),
    optionPart359(),
    ),
  )

private fun optionPart360(): CharacterOptionPart =
  CharacterOptionPart(
    "魔法の属性",
    true,
    listOf(
      "h3_magic_fire" to "火の魔法",
      "h3_magic_water" to "水の魔法",
      "h3_magic_wind" to "風の魔法",
      "h3_magic_earth" to "土の魔法",
      "h3_magic_thunder" to "雷の魔法",
      "h3_magic_ice" to "氷の魔法",
      "h3_magic_light" to "光の魔法",
      "h3_magic_dark" to "闇の魔法",
      "h3_magic_plant" to "植物の魔法",
      "h3_magic_metal" to "金属の魔法",
      "h3_magic_space" to "空間魔法",
      "h3_magic_time" to "時間魔法",
      "h3_magic_gravity" to "重力魔法",
      "h3_magic_sound" to "音の魔法",
      "h3_magic_dream" to "夢の魔法",
      "h3_magic_illusion" to "幻術",
      "h3_magic_healing" to "癒しの魔法",
      "h3_magic_barrier" to "結界",
      "h3_magic_summon" to "召喚魔法",
      "h3_magic_curse" to "呪いの魔法",
      "h3_magic_blessing" to "祝福の魔法",
      "h3_magic_star" to "星の魔法",
      "h3_magic_moon" to "月の魔法",
      "h3_magic_sun" to "太陽の魔法",
      "h3_magic_red_mist" to "赤い霧の魔法",
      "h3_magic_poison_mist" to "毒霧の魔法",
      "h3_magic_sand" to "砂の魔法",
      "h3_magic_crystal" to "結晶魔法",
      "h3_magic_mirror" to "鏡の魔法",
      "h3_magic_flower" to "花の魔法",
    ),
  )

private fun optionPart361(): CharacterOptionPart =
  CharacterOptionPart(
    "魔法陣・紋章",
    true,
    listOf(
      "h3_circle_basic" to "基本の魔法陣",
      "h3_circle_back" to "背後の魔法陣",
      "h3_circle_hand" to "手元の魔法陣",
      "h3_circle_multi" to "多重の魔法陣",
      "h3_circle_hexagram" to "六芒星の魔法陣",
      "h3_circle_pentagram" to "五芒星の魔法陣",
      "h3_circle_runes" to "ルーン文字の輪",
      "h3_circle_sigil" to "シジル",
      "h3_circle_gear" to "歯車の魔法陣",
      "h3_circle_constellation" to "星座の魔法陣",
      "h3_circle_zodiac" to "黄道十二宮の輪",
      "h3_circle_floral" to "花模様の魔法陣",
      "h3_circle_snow" to "雪の結晶の魔法陣",
      "h3_circle_geometric" to "幾何学模様の魔法陣",
      "h3_circle_orbital" to "軌道の輪",
      "h3_circle_halo_runes" to "文字が回る光輪",
      "h3_crest_family" to "家紋風の紋章",
      "h3_crest_wing" to "翼の紋章",
      "h3_crest_dragon" to "竜の紋章",
      "h3_crest_moon" to "月の紋章",
      "h3_crest_sun" to "太陽の紋章",
      "h3_crest_flower" to "花の紋章",
      "h3_crest_hand" to "手の甲の紋章",
      "h3_crest_forehead" to "額の紋章",
      "h3_crest_chest" to "胸元の紋章",
      "h3_crest_neck" to "首の紋章",
      "h3_crest_eye" to "目の紋章",
      "h3_crest_cheek" to "頬の紋様",
      "h3_crest_branded_glow" to "光る刻印",
      "h3_crest_spiral_arm" to "腕に走る光の線",
      "h3_crest_circuit_lines" to "回路状の模様",
      "h3_crest_vine_marks" to "蔦模様の紋様",
    ),
  )

private fun optionPart362(): CharacterOptionPart =
  CharacterOptionPart(
    "オーラ・光・エフェクト",
    false,
    listOf(
      "h3_fx_aura_white" to "白いオーラ",
      "h3_fx_aura_gold" to "金色のオーラ",
      "h3_fx_aura_red" to "赤いオーラ",
      "h3_fx_aura_blue" to "青いオーラ",
      "h3_fx_aura_purple" to "紫のオーラ",
      "h3_fx_aura_green" to "緑のオーラ",
      "h3_fx_aura_black" to "黒いオーラ",
      "h3_fx_aura_rainbow" to "虹色のオーラ",
      "h3_fx_aura_flame" to "炎のオーラ",
      "h3_fx_aura_lightning" to "雷のオーラ",
      "h3_fx_aura_ice" to "冷気のオーラ",
      "h3_fx_aura_wind" to "風のオーラ",
      "h3_fx_glow_body" to "全身が淡く光る",
      "h3_fx_glow_edge" to "輪郭の光",
      "h3_fx_glow_hands" to "手が光る",
      "h3_fx_glow_hair" to "髪が光る",
      "h3_fx_glow_eyes" to "目から光が漏れる",
      "h3_fx_sparkles" to "きらめく粒子",
      "h3_fx_fireflies" to "蛍のような光の粒",
      "h3_fx_light_particles" to "光の粒子",
      "h3_fx_floating_petals" to "舞う花びら",
      "h3_fx_floating_feathers" to "舞う羽根",
      "h3_fx_floating_leaves" to "舞う木の葉",
      "h3_fx_snow_swirl" to "舞う雪",
      "h3_fx_bubbles" to "シャボン玉",
      "h3_fx_light_beam" to "差し込む光の筋",
      "h3_fx_god_rays" to "神々しい後光",
      "h3_fx_lens_flare" to "まばゆい光",
      "h3_fx_stars_around" to "周囲に漂う星",
      "h3_fx_moon_glow" to "月光に包まれる",
      "h3_fx_embers" to "舞う火の粉",
      "h3_fx_lightning_arcs" to "走る稲妻",
      "h3_fx_smoke_wisps" to "立ちのぼる煙",
      "h3_fx_water_ripple" to "水の波紋",
      "h3_fx_water_droplets" to "宙に浮く水滴",
      "h3_fx_glass_shards" to "舞うガラス片",
      "h3_fx_chains_light" to "光の鎖",
      "h3_fx_ribbons_light" to "光のリボン",
      "h3_fx_strings_light" to "光の糸",
      "h3_fx_orbiting_orbs" to "周回する光球",
      "h3_fx_fox_fire" to "狐火",
      "h3_fx_ghost_flames" to "青白い炎",
      "h3_fx_dark_mist" to "黒い霧",
      "h3_fx_time_ripple" to "時の波紋",
      "h3_fx_glitch" to "ノイズのゆがみ",
      "h3_fx_afterimage" to "残像",
      "h3_fx_double_exposure" to "二重写しの像",
      "h3_fx_prism" to "プリズムの光",
      "h3_fx_aurora_sky" to "オーロラのかかる空",
      "h3_fx_cherry_blossom_storm" to "桜吹雪",
      "h3_fx_dandelion" to "たんぽぽの綿毛",
      "h3_fx_butterflies_around" to "周囲を舞う蝶",
    ),
  )

private fun optionPart363(): CharacterOptionPart =
  CharacterOptionPart(
    "能力・魔法のポーズ",
    true,
    listOf(
      "h3_pose_cast_spell" to "呪文を唱える",
      "h3_pose_staff_raise" to "杖を掲げる",
      "h3_pose_orb_hold" to "光球を掲げる",
      "h3_pose_palm_flame" to "掌に炎を灯す",
      "h3_pose_summon_circle" to "召喚の陣の中心",
      "h3_pose_flying_cast" to "宙に浮かんで詠唱",
      "h3_pose_ritual" to "儀式の舞",
      "h3_pose_prayer" to "祈りの手",
      "h3_pose_barrier" to "結界を張る",
      "h3_pose_telekinesis" to "念動力",
      "h3_pose_wind_hair" to "魔力で髪が舞う",
      "h3_pose_levitate" to "浮遊して座る",
      "h3_pose_transform_ring" to "変身の輪",
      "h3_pose_awakening" to "力の覚醒",
      "h3_pose_spread_wings" to "翼を広げる",
      "h3_pose_wing_wrap" to "翼で身を包む",
      "h3_pose_sleeping_wings" to "翼にくるまって眠る",
      "h3_pose_tail_hug" to "尻尾を抱く",
      "h3_pose_tail_curl" to "尻尾を体に巻く",
      "h3_pose_ear_perk" to "耳を立てる",
      "h3_pose_ear_droop" to "耳が垂れる",
      "h3_pose_tail_wag" to "尻尾を振る",
      "h3_pose_fang_smile" to "牙を見せて笑う",
      "h3_pose_stargazing" to "星を見上げる",
      "h3_pose_crystal_touch" to "水晶に触れる",
    ),
  )

private fun optionGenre72(): CharacterOptionGenre =
  CharacterOptionGenre(
    "魔法・能力・エフェクト",
    listOf(
    optionPart360(),
    optionPart361(),
    optionPart362(),
    optionPart363(),
    ),
  )

private fun optionPart364(): CharacterOptionPart =
  CharacterOptionPart(
    "サイボーグ・アンドロイド",
    true,
    listOf(
      "h3_sf_gynoid" to "ガイノイド",
      "h3_sf_cyborg_face" to "半顔が機械",
      "h3_sf_cyborg_neck" to "首の接合線",
      "h3_sf_cyborg_spine" to "背中のポート",
      "h3_sf_panel_lines" to "パネルの継ぎ目",
      "h3_sf_exposed_mechanics" to "機械が見える背中",
      "h3_sf_headphones_ears" to "耳の通信機",
      "h3_sf_hologram_girl" to "ホログラムの女性",
      "h3_sf_ai_avatar" to "AIアバター",
      "h3_sf_virtual_idol" to "仮想アイドル",
      "h3_sf_data_ghost" to "データの亡霊",
      "h3_sf_nanomachine" to "ナノマシンの体",
      "h3_sf_liquid_metal" to "液体金属の体",
      "h3_sf_bio_organism" to "バイオ生命体",
      "h3_sf_clone" to "クローン",
      "h3_sf_artificial_human" to "人造人間",
      "h3_sf_doll_automaton" to "自動人形",
      "h3_sf_clockwork" to "ぜんまい仕掛けの体",
      "h3_sf_steam_cyborg" to "蒸気機関のサイボーグ",
      "h3_sf_alien_blue" to "青い肌の異星人",
      "h3_sf_alien_insectoid" to "昆虫型異星人",
      "h3_sf_alien_tentacle_hair" to "触手髪の異星人",
      "h3_sf_alien_silver" to "銀の肌の異星人",
      "h3_sf_hybrid_experiment" to "実験体",
      "h3_sf_space_nomad" to "宇宙の放浪者",
    ),
  )

private fun optionPart365(): CharacterOptionPart =
  CharacterOptionPart(
    "スーツ・装備・未来服",
    true,
    listOf(
      "h3_suit_plug" to "ぴったりした機能スーツ",
      "h3_suit_space_light" to "軽装の宇宙服",
      "h3_suit_visor_helmet" to "ヘルメットとバイザー",
      "h3_suit_power" to "パワードスーツ",
      "h3_suit_mecha_musume" to "メカ娘",
      "h3_suit_mecha_wings" to "メカの翼",
      "h3_suit_hover_board" to "ホバーボード",
      "h3_suit_holo_dress" to "ホログラムのドレス",
      "h3_suit_neon_jacket" to "ネオン光の上着",
      "h3_suit_cyber_kimono" to "サイバー和装",
      "h3_suit_cyber_ninja" to "サイバー忍者",
      "h3_suit_cyber_priestess" to "電脳巫女",
      "h3_suit_lab_coat" to "白衣と研究服",
      "h3_suit_pilot" to "パイロットスーツ",
      "h3_suit_bio_suit" to "バイオスーツ",
      "h3_suit_crystal_armor" to "結晶の鎧",
      "h3_suit_light_armor" to "光の鎧",
      "h3_suit_mech_armor" to "機械装甲",
      "h3_suit_visor_glow" to "光るゴーグル",
      "h3_suit_ear_antenna" to "アンテナ付き帽子",
      "h3_suit_wires_hair" to "髪に差したケーブル",
      "h3_suit_silver_foil" to "銀色の宇宙風衣装",
      "h3_suit_astro_cape" to "星間マント",
      "h3_suit_drone_companion" to "ドローンの相棒",
      "h3_suit_pet_robot" to "ペットロボット",
      "h3_suit_cat_robot" to "猫型ロボットを連れる",
      "h3_suit_holo_screens" to "宙に浮かぶ画面",
    ),
  )

private fun optionPart366(): CharacterOptionPart =
  CharacterOptionPart(
    "SFの場面・背景",
    true,
    listOf(
      "h3_scene_spaceship_bridge" to "宇宙船の艦橋",
      "h3_scene_spaceship_hall" to "宇宙船の廊下",
      "h3_scene_space_window" to "宇宙を望む窓",
      "h3_scene_space_station" to "宇宙ステーション",
      "h3_scene_moon_base" to "月面基地",
      "h3_scene_mars" to "赤い惑星の大地",
      "h3_scene_alien_planet" to "異星の風景",
      "h3_scene_neon_city" to "ネオンの未来都市",
      "h3_scene_cyber_alley" to "サイバーパンクの路地",
      "h3_scene_floating_city" to "空中都市",
      "h3_scene_dome_city" to "ドーム都市",
      "h3_scene_lab" to "研究所",
      "h3_scene_cryo_pod" to "冷凍睡眠カプセル",
      "h3_scene_server_room" to "サーバールーム",
      "h3_scene_virtual_space" to "仮想空間",
      "h3_scene_hologram_stage" to "ホログラムのステージ",
      "h3_scene_observatory" to "天文台",
      "h3_scene_orbital_ring" to "軌道リング",
      "h3_scene_ruined_future" to "荒廃した未来",
      "h3_scene_garden_dome" to "植物園ドーム",
      "h3_scene_zero_gravity" to "無重力の船内",
      "h3_scene_airlock" to "エアロック",
      "h3_scene_time_machine" to "時間旅行の装置",
      "h3_scene_warp_gate" to "転送ゲート",
      "h3_scene_robot_factory" to "ロボット工場",
    ),
  )

private fun optionGenre73(): CharacterOptionGenre =
  CharacterOptionGenre(
    "SF・機械",
    listOf(
    optionPart364(),
    optionPart365(),
    optionPart366(),
    ),
  )

private fun optionPart367(): CharacterOptionPart =
  CharacterOptionPart(
    "RPG状態異常（見た目）",
    true,
    listOf(
      "h3_state_frozen" to "凍結",
      "h3_state_frozen_half" to "半分凍る",
      "h3_state_icicle_hair" to "霜のついた髪",
      "h3_state_stone_gentle" to "石化（やさしい表現）",
      "h3_state_stone_half" to "半分石化",
      "h3_state_statue" to "彫像のように静止",
      "h3_state_golden_statue" to "金の像に変わる",
      "h3_state_glassified" to "ガラス化",
      "h3_state_paralysis" to "麻痺（雷が走る）",
      "h3_state_sleep" to "眠り",
      "h3_state_sleep_bubble" to "鼻ちょうちんの眠り",
      "h3_state_enchanted_sleep" to "魔法の眠り",
      "h3_state_dizzy" to "目が回る",
      "h3_state_charm" to "魅了",
      "h3_state_hypnosis_eyes" to "催眠の瞳",
      "h3_state_blessed" to "祝福を受けた状態",
      "h3_state_cursed" to "呪いを受けた状態",
      "h3_state_poison_look" to "毒の状態（見た目のみ）",
      "h3_state_burning" to "燃える（装飾的な炎）",
      "h3_state_wet" to "ずぶ濡れ",
      "h3_state_electrified" to "帯電",
      "h3_state_blind" to "目が見えない状態",
      "h3_state_mute" to "声が出ない状態",
      "h3_state_silence_seal" to "封印の魔法",
      "h3_state_slow" to "鈍足",
      "h3_state_haste" to "加速",
      "h3_state_berserk_neutral" to "闘気に満ちた状態",
      "h3_state_regen" to "再生の光",
      "h3_state_weakened" to "弱体",
      "h3_state_invisible" to "透明化",
      "h3_state_half_invisible" to "半分透明",
      "h3_state_phantom" to "幽体離脱",
      "h3_state_levitate" to "浮遊",
      "h3_state_giant" to "巨大化",
      "h3_state_mini" to "縮小化（成人のまま）",
      "h3_state_doll_size" to "手のひらサイズ",
      "h3_state_afterimage_clone" to "残像の分身",
      "h3_state_mirror_self" to "鏡像",
      "h3_state_shadow_self" to "影の分身",
      "h3_state_halfway_transform" to "変身途中",
      "h3_state_transform_beast" to "獣へ変身中",
      "h3_state_transform_dragon" to "竜へ変身中",
      "h3_state_transform_bird" to "鳥へ変身中",
      "h3_state_transform_fish" to "人魚へ変身中",
      "h3_state_transform_tree" to "木へ変わる（穏やか）",
      "h3_state_transform_light" to "光の粒へ変わる",
      "h3_state_transform_water" to "水へ溶ける",
      "h3_state_transform_butterflies" to "蝶になって散る",
      "h3_state_transform_doll" to "人形化",
      "h3_state_transform_mist" to "霧化",
      "h3_state_transform_robot" to "機械化",
      "h3_state_horns_grow" to "角が生える",
      "h3_state_eyes_change" to "瞳の色が変わる",
      "h3_state_hair_change" to "髪の色が変わる",
      "h3_state_time_stopped" to "時が止まる",
      "h3_state_floating_asleep" to "宙に浮いて眠る",
      "h3_state_capsule_sleep" to "水槽の中の眠り",
      "h3_state_cocoon" to "繭に包まれる",
      "h3_state_crystal_cocoon" to "水晶の繭",
      "h3_state_amber" to "琥珀に閉じ込められる",
      "h3_state_bubble" to "泡に包まれる",
      "h3_state_snowglobe" to "スノードームの中",
      "h3_state_book_sealed" to "本に吸い込まれる",
      "h3_state_mirror_enter" to "鏡に入る",
      "h3_state_portal_step" to "扉をくぐる",
    ),
  )

private fun optionGenre74(): CharacterOptionGenre =
  CharacterOptionGenre(
    "状態・変化",
    listOf(
    optionPart367(),
    ),
  )

private fun optionPart368(): CharacterOptionPart =
  CharacterOptionPart(
    "星座モチーフ",
    true,
    listOf(
      "h3_const_aries" to "おひつじ座モチーフ",
      "h3_const_taurus" to "おうし座モチーフ",
      "h3_const_gemini" to "ふたご座モチーフ",
      "h3_const_cancer" to "かに座モチーフ",
      "h3_const_leo" to "しし座モチーフ",
      "h3_const_virgo" to "おとめ座モチーフ",
      "h3_const_libra" to "てんびん座モチーフ",
      "h3_const_scorpio" to "さそり座モチーフ",
      "h3_const_sagittarius" to "いて座モチーフ",
      "h3_const_capricorn" to "やぎ座モチーフ",
      "h3_const_aquarius" to "みずがめ座モチーフ",
      "h3_const_pisces" to "うお座モチーフ",
      "h3_const_ophiuchus" to "へびつかい座モチーフ",
      "h3_const_orion" to "オリオン座モチーフ",
      "h3_const_cassiopeia" to "カシオペヤ座モチーフ",
      "h3_const_ursa" to "おおぐま座モチーフ",
      "h3_const_ursa_minor" to "こぐま座モチーフ",
      "h3_const_lyra" to "こと座モチーフ",
      "h3_const_cygnus" to "はくちょう座モチーフ",
      "h3_const_aquila" to "わし座モチーフ",
      "h3_const_crux" to "みなみじゅうじ座モチーフ",
      "h3_const_phoenix" to "ほうおう座モチーフ",
      "h3_const_pegasus" to "ペガスス座モチーフ",
      "h3_const_andromeda" to "アンドロメダ座モチーフ",
      "h3_const_perseus" to "ペルセウス座モチーフ",
      "h3_const_draco" to "りゅう座モチーフ",
      "h3_const_canis_major" to "おおいぬ座モチーフ",
      "h3_const_columba" to "はと座モチーフ",
      "h3_const_pavo" to "くじゃく座モチーフ",
      "h3_const_coma" to "かみのけ座モチーフ",
    ),
  )

private fun optionPart369(): CharacterOptionPart =
  CharacterOptionPart(
    "元素・自然・天体モチーフ",
    true,
    listOf(
      "h3_elem_fire" to "炎の元素",
      "h3_elem_water" to "水の元素",
      "h3_elem_earth" to "土の元素",
      "h3_elem_air" to "風の元素",
      "h3_elem_wood" to "木の五行",
      "h3_elem_gold" to "金の五行",
      "h3_elem_thunder" to "雷の元素",
      "h3_elem_ice" to "氷の元素",
      "h3_elem_light" to "光の元素",
      "h3_elem_dark" to "闇の元素",
      "h3_elem_void" to "虚無",
      "h3_elem_aether" to "エーテル",
      "h3_elem_magma" to "溶岩",
      "h3_elem_lava_hair" to "溶岩の髪",
      "h3_elem_storm" to "嵐",
      "h3_elem_tide" to "潮",
      "h3_elem_desert" to "砂漠",
      "h3_elem_sky" to "空",
      "h3_elem_cloud" to "雲",
      "h3_elem_rainbow" to "虹",
      "h3_elem_aurora" to "オーロラ",
      "h3_elem_sunset" to "夕焼け",
      "h3_elem_sunrise" to "朝焼け",
      "h3_elem_midnight" to "真夜中",
      "h3_elem_eclipse" to "日食",
      "h3_elem_lunar_eclipse" to "月食",
      "h3_elem_new_moon" to "新月",
      "h3_elem_full_moon" to "満月",
      "h3_elem_comet" to "彗星",
      "h3_elem_meteor" to "流星",
      "h3_elem_nebula" to "星雲",
      "h3_elem_galaxy" to "銀河",
      "h3_elem_planet" to "惑星",
      "h3_elem_saturn_ring" to "土星の輪",
      "h3_elem_black_hole" to "ブラックホール",
      "h3_elem_milky_way" to "天の川",
      "h3_elem_tanabata" to "七夕",
      "h3_elem_twin_moons" to "二つの月",
    ),
  )

private fun optionPart370(): CharacterOptionPart =
  CharacterOptionPart(
    "四季・二十四節気",
    true,
    listOf(
      "h3_season_spring" to "春モチーフ",
      "h3_season_summer" to "夏モチーフ",
      "h3_season_autumn" to "秋モチーフ",
      "h3_season_winter" to "冬モチーフ",
      "h3_season_late_spring" to "晩春",
      "h3_season_rainy" to "梅雨",
      "h3_season_midsummer" to "盛夏",
      "h3_season_late_autumn" to "晩秋",
      "h3_season_midwinter" to "真冬",
      "h3_term_risshun" to "立春",
      "h3_term_usui" to "雨水",
      "h3_term_keichitsu" to "啓蟄",
      "h3_term_shunbun" to "春分",
      "h3_term_seimei" to "清明",
      "h3_term_kokuu" to "穀雨",
      "h3_term_rikka" to "立夏",
      "h3_term_shouman" to "小満",
      "h3_term_boushu" to "芒種",
      "h3_term_geshi" to "夏至",
      "h3_term_shousho" to "小暑",
      "h3_term_taisho" to "大暑",
      "h3_term_risshuu" to "立秋",
      "h3_term_shosho" to "処暑",
      "h3_term_hakuro" to "白露",
      "h3_term_shuubun" to "秋分",
      "h3_term_kanro" to "寒露",
      "h3_term_soukou" to "霜降",
      "h3_term_rittou" to "立冬",
      "h3_term_shousetsu" to "小雪",
      "h3_term_taisetsu" to "大雪",
      "h3_term_touji" to "冬至",
      "h3_term_shoukan" to "小寒",
      "h3_term_daikan" to "大寒",
      "h3_season_hanami" to "花見",
      "h3_season_fireflies_night" to "蛍の夜",
      "h3_season_moon_viewing" to "月見",
      "h3_season_snow_lantern" to "雪灯り",
    ),
  )

private fun optionPart371(): CharacterOptionPart =
  CharacterOptionPart(
    "宝石・鉱物モチーフ",
    true,
    listOf(
      "h3_gem_garnet" to "ガーネット",
      "h3_gem_amethyst" to "アメジスト",
      "h3_gem_aquamarine" to "アクアマリン",
      "h3_gem_diamond" to "ダイヤモンド",
      "h3_gem_emerald" to "エメラルド",
      "h3_gem_pearl" to "真珠",
      "h3_gem_moonstone" to "ムーンストーン",
      "h3_gem_alexandrite" to "アレキサンドライト",
      "h3_gem_ruby" to "ルビー",
      "h3_gem_peridot" to "ペリドット",
      "h3_gem_spinel" to "スピネル",
      "h3_gem_sapphire" to "サファイア",
      "h3_gem_opal" to "オパール",
      "h3_gem_tourmaline" to "トルマリン",
      "h3_gem_topaz" to "トパーズ",
      "h3_gem_citrine" to "シトリン",
      "h3_gem_turquoise" to "ターコイズ",
      "h3_gem_tanzanite" to "タンザナイト",
      "h3_gem_lapis" to "ラピスラズリ",
      "h3_gem_jade" to "翡翠",
      "h3_gem_obsidian" to "黒曜石",
      "h3_gem_onyx" to "オニキス",
      "h3_gem_rose_quartz" to "ローズクォーツ",
      "h3_gem_clear_quartz" to "水晶",
      "h3_gem_fluorite" to "フローライト",
      "h3_gem_labradorite" to "ラブラドライト",
      "h3_gem_malachite" to "マラカイト",
      "h3_gem_sunstone" to "サンストーン",
      "h3_gem_bismuth" to "ビスマス結晶",
      "h3_gem_geode" to "晶洞",
      "h3_gem_coral_gem" to "珊瑚",
      "h3_gem_meteorite" to "隕石",
      "h3_gem_gold_dust" to "金粉",
      "h3_gem_silver_filigree" to "銀細工",
      "h3_gem_stained_glass" to "ステンドグラス",
      "h3_gem_sea_glass" to "シーグラス",
    ),
  )

private fun optionPart372(): CharacterOptionPart =
  CharacterOptionPart(
    "神話・幻獣モチーフ",
    true,
    listOf(
      "h3_myth_olympian_goddess" to "ギリシア風の女神",
      "h3_myth_nike" to "勝利の女神",
      "h3_myth_moon_goddess" to "月の女神",
      "h3_myth_sun_goddess" to "太陽の女神",
      "h3_myth_earth_goddess" to "大地の女神",
      "h3_myth_storm_goddess" to "嵐の女神",
      "h3_myth_harvest_goddess" to "豊穣の女神",
      "h3_myth_hunt_goddess" to "狩りの女神",
      "h3_myth_wisdom_goddess" to "知恵の女神",
      "h3_myth_love_goddess" to "愛の女神",
      "h3_myth_underworld_goddess" to "冥界の女神",
      "h3_myth_norse_goddess" to "北欧風の女神",
      "h3_myth_egypt_goddess" to "エジプト風の女神",
      "h3_myth_cat_goddess" to "猫頭の女神風",
      "h3_myth_jackal_priestess" to "ジャッカルの巫女",
      "h3_myth_celtic_druid" to "ケルトの森の巫女",
      "h3_myth_japan_kami" to "日本神話の女神",
      "h3_myth_sun_deity_mirror" to "鏡を持つ太陽神",
      "h3_myth_fox_deity" to "稲荷風の狐神",
      "h3_myth_dragon_palace_princess" to "龍宮の姫",
      "h3_myth_hindu_goddess" to "インド神話風の女神",
      "h3_myth_chinese_immortal" to "仙女",
      "h3_myth_moon_rabbit" to "月の兎",
      "h3_myth_qilin" to "麒麟の化身",
      "h3_myth_chinese_dragon" to "東洋の龍",
      "h3_myth_fenghuang" to "鳳凰の化身",
      "h3_myth_griffin" to "グリフォン",
      "h3_myth_pegasus" to "ペガサス",
      "h3_myth_unicorn" to "ユニコーン",
      "h3_myth_hippogriff" to "ヒッポグリフ",
      "h3_myth_chimera" to "キマイラ",
      "h3_myth_wyvern" to "ワイバーン",
      "h3_myth_hydra" to "ヒュドラ",
      "h3_myth_kraken" to "クラーケン",
      "h3_myth_leviathan" to "リヴァイアサン",
      "h3_myth_behemoth" to "ベヒモス",
      "h3_myth_fenrir" to "フェンリル",
      "h3_myth_cerberus" to "ケルベロス",
      "h3_myth_basilisk" to "バジリスク",
      "h3_myth_thunderbird" to "サンダーバード",
      "h3_myth_yggdrasil" to "世界樹",
      "h3_myth_avalon" to "霧の島",
      "h3_myth_elysium" to "楽園",
      "h3_myth_atlantis" to "水底の都",
      "h3_myth_celestial_palace" to "天空の宮殿",
      "h3_myth_dragon_nest" to "竜の巣",
      "h3_myth_fairy_ring" to "妖精の輪",
      "h3_myth_hollow_tree" to "洞の木の家",
      "h3_myth_floating_island" to "浮遊島",
      "h3_myth_crystal_cave" to "水晶の洞窟",
      "h3_myth_ice_palace" to "氷の宮殿",
      "h3_myth_fire_temple" to "炎の神殿",
      "h3_myth_sky_shrine" to "空の祠",
      "h3_myth_moon_palace" to "月の宮",
    ),
  )

private fun optionGenre75(): CharacterOptionGenre =
  CharacterOptionGenre(
    "神話・星・元素モチーフ",
    listOf(
    optionPart368(),
    optionPart369(),
    optionPart370(),
    optionPart371(),
    optionPart372(),
    ),
  )

private fun optionPart373(): CharacterOptionPart =
  CharacterOptionPart(
    "肩・胸まわり",
    false,
    listOf(
      "h4_mess_shoulder_slip" to "肩からずり落ちた服",
      "h4_mess_one_shoulder_bare" to "片肩だけ出した着崩し",
      "h4_mess_both_shoulders" to "両肩を落とした着崩し",
      "h4_mess_strap_fall" to "ずり落ちた肩ひも",
      "h4_mess_strap_both" to "両方外れた肩ひも",
      "h4_mess_collar_wide" to "襟元を大きく開けた",
      "h4_mess_neckline_pull" to "胸元を指で引いた",
      "h4_mess_neckline_down" to "胸元をずらした服",
      "h4_mess_open_front" to "前開きの服",
      "h4_mess_shirt_unbutton_half" to "ボタンを半分外したシャツ",
      "h4_mess_shirt_open_bra" to "シャツの前を開いて下着が見える",
      "h4_mess_cardigan_off" to "カーディガンが肩から落ちる",
      "h4_mess_robe_open" to "はだけたガウン",
      "h4_mess_kimono_loose" to "着崩れた着物",
      "h4_mess_obi_loose" to "ほどけかけた帯",
      "h4_mess_towel_loose" to "ゆるんだバスタオル",
      "h4_mess_apron_slip" to "ずれたエプロン",
      "h4_mess_tube_down" to "ずり下げたチューブトップ",
      "h4_mess_halter_untied" to "ほどけたホルターネックの紐",
      "h4_mess_bra_visible_strap" to "見えているブラの肩ひも",
      "h4_mess_bra_peek" to "のぞくブラ",
      "h4_mess_bra_pull_up" to "ずり上げたブラ",
      "h4_mess_bra_pull_down" to "ずり下げたブラ",
      "h4_mess_bra_unhooked" to "ホックを外したブラ",
    ),
  )

private fun optionPart374(): CharacterOptionPart =
  CharacterOptionPart(
    "裾・腰・下半身まわり",
    false,
    listOf(
      "h4_mess_skirt_lift_wind" to "風でめくれたスカート",
      "h4_mess_skirt_hem_hold" to "裾を押さえて恥じらう",
      "h4_mess_skirt_tuck_waist" to "裾を腰に挟んだ",
      "h4_mess_dress_lift" to "ドレスの裾をたくし上げ",
      "h4_mess_dress_hem_bite" to "裾を口にくわえる",
      "h4_mess_shirt_lift" to "シャツをたくし上げる",
      "h4_mess_sweater_lift" to "セーターをたくし上げる",
      "h4_mess_hem_bare_belly" to "裾からのぞく腹",
      "h4_mess_pants_pulled_down" to "ずり下げたパンツ（ズボン）",
      "h4_mess_pants_half" to "半分下ろしたズボン",
      "h4_mess_shorts_down" to "ずり下げたショートパンツ",
      "h4_mess_belt_hanging" to "垂れ下がったベルト",
      "h4_mess_button_open_waist" to "ウエストのボタンを外した",
      "h4_mess_panties_aside" to "下着を横にずらした",
      "h4_mess_panties_down_thigh" to "太ももまで下ろした下着",
      "h4_mess_panties_one_leg" to "片足に引っかかった下着",
      "h4_mess_panties_ankle" to "足首にかかった下着",
      "h4_mess_panties_peek_waist" to "腰からのぞく下着",
      "h4_mess_thong_peek" to "ローライズからのぞくTバック",
      "h4_mess_swimsuit_aside" to "水着をずらした",
      "h4_mess_swimsuit_pull" to "水着の食い込みを直す",
      "h4_mess_leotard_aside" to "レオタードをずらした",
      "h4_mess_bikini_untied_side" to "サイドの紐がほどけたビキニ",
      "h4_mess_bikini_top_lift" to "ビキニトップをずらす",
    ),
  )

private fun optionPart375(): CharacterOptionPart =
  CharacterOptionPart(
    "半脱ぎ・脱ぎかけ",
    false,
    listOf(
      "h4_half_undress_shirt" to "シャツを脱ぎかけ",
      "h4_half_undress_dress_zip" to "背中のファスナーを下ろしかけたドレス",
      "h4_half_undress_dress_fall" to "ドレスが腰まで落ちた",
      "h4_half_undress_top_over_head" to "頭の上で絡まったトップス",
      "h4_half_undress_sleeve_stuck" to "袖が腕に引っかかった",
      "h4_half_undress_one_sleeve" to "片袖だけ脱いだ",
      "h4_half_undress_trench_only" to "トレンチコート一枚",
      "h4_half_undress_oversize_shirt" to "大きなシャツ一枚",
      "h4_half_undress_dress_shirt_only" to "ワイシャツ一枚",
      "h4_half_undress_sweater_only" to "セーター一枚",
      "h4_half_undress_towel_only" to "タオル一枚",
      "h4_half_undress_ribbon_only" to "リボンだけ",
      "h4_half_undress_sheet" to "シーツをまとう",
      "h4_half_undress_arm_cover" to "腕で胸を隠す",
      "h4_half_undress_hair_cover" to "長い髪で隠す",
      "h4_half_undress_pillow_cover" to "抱き枕で体を隠す",
      "h4_half_undress_strategic" to "かろうじて隠す構図",
      "h4_half_undress_stocking_only" to "ストッキングだけ",
      "h4_half_undress_heels_only" to "ハイヒールだけ",
      "h4_half_undress_gloves_only" to "手袋だけ",
      "h4_half_undress_choker_only" to "チョーカーだけ",
      "h4_half_undress_jewelry_only" to "宝石の飾りだけ",
      "h4_half_undress_glasses_only" to "眼鏡だけ",
    ),
  )

private fun optionPart376(): CharacterOptionPart =
  CharacterOptionPart(
    "着崩し・乱れた身なり",
    false,
    listOf(
      "h4_dis_hair_stuck_sweat" to "汗で貼りついた髪",
      "h4_dis_hair_untied" to "ほどけた髪",
      "h4_dis_hair_ribbon_loose" to "ゆるんだ髪飾り",
      "h4_dis_hair_over_eye" to "目にかかる髪",
      "h4_dis_makeup_smeared" to "にじんだ化粧",
      "h4_dis_lipstick_smear" to "はみ出た口紅",
      "h4_dis_mascara_run" to "流れたマスカラ",
      "h4_dis_clothes_wrinkled" to "しわだらけの服",
      "h4_dis_tie_pulled" to "引っ張られたネクタイ",
      "h4_dis_ribbon_untied" to "ほどけたリボン",
      "h4_dis_shoes_off" to "片方脱げた靴",
      "h4_dis_socks_down" to "ずり落ちた靴下",
      "h4_dis_after_party" to "宴のあとの乱れ",
      "h4_dis_morning_after" to "朝の気だるい乱れ",
      "h4_dis_sheet_tangle" to "シーツに絡まる",
      "h4_dis_sheet_rumpled" to "乱れたシーツ",
    ),
  )

private fun optionPart377(): CharacterOptionPart =
  CharacterOptionPart(
    "透け・濡れ透け",
    false,
    listOf(
      "h4_see_wet_dress" to "濡れて透けたドレス",
      "h4_see_wet_white_tee" to "濡れて透けた白Tシャツ",
      "h4_see_wet_yukata" to "濡れて透けた浴衣",
      "h4_see_wet_bikini" to "濡れたビキニ",
      "h4_see_wet_tights" to "濡れたタイツ",
      "h4_see_wet_hair_clothes" to "ずぶ濡れの全身",
      "h4_see_rain_drenched" to "雨に打たれてずぶ濡れ",
      "h4_see_shower_stream" to "シャワーを浴びる",
      "h4_see_bath_steam" to "湯気の立つ入浴",
      "h4_see_nightgown_sheer" to "透けるネグリジェ",
      "h4_see_chiffon" to "シフォンのゆるい透け",
      "h4_see_lace_sheer" to "レースの透け感",
      "h4_see_mesh" to "メッシュ生地の透け",
      "h4_see_fishnet_top" to "網目のトップス",
      "h4_see_sheer_sleeve" to "シアーな袖",
      "h4_see_sheer_back" to "シアーな背中の開き",
      "h4_see_sheer_cutout" to "透けるカットアウト",
      "h4_see_silhouette" to "薄布越しのシルエット",
      "h4_see_backlit_thin" to "逆光で透ける服",
      "h4_see_cling_fabric" to "体に貼りつく布",
    ),
  )

private fun optionGenre76(): CharacterOptionGenre =
  CharacterOptionGenre(
    "衣装の乱れ・半脱ぎ（追加）",
    listOf(
    optionPart373(),
    optionPart374(),
    optionPart375(),
    optionPart376(),
    optionPart377(),
    ),
  )

private fun optionPart378(): CharacterOptionPart =
  CharacterOptionPart(
    "火照り・目と口もと（1）",
    false,
    listOf(
      "h4_ex_full_blush" to "顔全体が真っ赤",
      "h4_ex_blush_neck" to "首筋まで赤い",
      "h4_ex_blush_chest" to "胸元まで赤く染まる",
      "h4_ex_soft_breath" to "ため息のような吐息",
      "h4_ex_half_open_mouth" to "うっすら開いた口",
      "h4_ex_open_mouth_tongue" to "舌先がのぞく口",
      "h4_ex_finger_to_lips" to "指を唇に当てる",
      "h4_ex_pout_kiss" to "キスを求める口もと",
      "h4_ex_wet_lips" to "濡れた唇",
      "h4_ex_glossy_lips" to "つややかな唇",
      "h4_ex_saliva_string" to "糸を引く唾液",
    ),
  )

private fun optionPart379(): CharacterOptionPart =
  CharacterOptionPart(
    "火照り・目と口もと（2）",
    false,
    listOf(
      "h4_ex_drool_chin" to "あごに伝うよだれ",
      "h4_ex_tears_streaming" to "涙があふれる",
      "h4_ex_tears_corner" to "目尻の涙",
      "h4_ex_half_lidded" to "とろんとした半目",
      "h4_ex_dazed_eyes" to "ぼんやりした目",
      "h4_ex_rolled_eyes" to "上目づかいで黒目が上がる",
      "h4_ex_trance_smile" to "うっとりした笑み",
      "h4_ex_eyes_closed_bliss" to "目を閉じて浸る",
      "h4_ex_eyes_closed_tight" to "ぎゅっと目をつぶる",
      "h4_ex_furrowed_brow" to "眉を寄せる",
      "h4_ex_troubled_brow" to "困り眉の表情",
      "h4_ex_seductive_smile" to "誘うような微笑み",
      "h4_ex_smug_lust" to "余裕のある艶やかな笑み",
    ),
  )

private fun optionPart380(): CharacterOptionPart =
  CharacterOptionPart(
    "体の状態・余韻（1）",
    false,
    listOf(
      "h4_st_sweat_light" to "うっすら汗ばむ",
      "h4_st_sweat_heavy" to "汗びっしょり",
      "h4_st_sweat_beads" to "玉の汗",
      "h4_st_sweat_neck" to "首筋を流れる汗",
      "h4_st_sweat_chest" to "胸の谷間の汗",
      "h4_st_sweat_thigh" to "太ももの汗",
      "h4_st_shiver" to "ふるえる体",
      "h4_st_shiver_legs" to "ふるえる脚",
      "h4_st_weak_knees" to "膝に力が入らない",
      "h4_st_limp" to "力が抜けた体",
      "h4_st_collapse" to "ぐったり倒れ込む",
      "h4_st_after_glow" to "余韻に浸る",
      "h4_st_exhausted_smile" to "疲れた満足げな笑み",
      "h4_st_lying_bed_spent" to "ベッドで脱力",
      "h4_st_curled_up" to "体を丸めて余韻に耐える",
      "h4_st_hug_pillow_shy" to "枕に顔を埋める",
      "h4_st_cover_face_hands" to "手で顔を隠す",
      "h4_st_clutch_sheet" to "シーツを握りしめる",
      "h4_st_arch_back" to "背中を反らす",
      "h4_st_toes_curl" to "つま先が丸まる",
      "h4_st_toes_spread" to "つま先が開く",
    ),
  )

private fun optionPart381(): CharacterOptionPart =
  CharacterOptionPart(
    "体の状態・余韻（2）",
    false,
    listOf(
      "h4_st_flushed_skin_glow" to "紅潮した肌のつや",
      "h4_st_steamy_skin" to "湯気立つ肌",
      "h4_st_hot_fever" to "熱にうかされた表情",
      "h4_st_drunk_blush" to "酔って火照る",
      "h4_st_sleepy_lazy" to "けだるい眠気",
      "h4_st_hair_wet_lines" to "濡れ髪が頬に貼りつく",
      "h4_st_hand_on_chest" to "胸に手を当てて息を整える",
      "h4_st_hand_on_neck" to "首元に手を当てる",
      "h4_st_hand_between_thighs" to "太ももに手を挟む",
      "h4_st_knees_together" to "膝をぴたりと閉じる",
      "h4_st_thigh_gap_squirm" to "もじもじと脚をこすり合わせる",
      "h4_st_pigeon_toed" to "内股で立つ",
      "h4_st_all_fours_look_back" to "四つん這いで振り返る",
      "h4_st_lying_side_pose" to "横向きに寝そべる",
      "h4_st_prone_feet_up" to "うつ伏せで足を上げる",
      "h4_st_reclining_languid" to "もたれて寝そべる",
      "h4_st_sitting_wariza" to "ぺたん座り",
      "h4_st_squat_shy" to "しゃがんで恥じらう",
    ),
  )

private fun optionGenre77(): CharacterOptionGenre =
  CharacterOptionGenre(
    "成人向けの表情・状態",
    listOf(
    optionPart378(),
    optionPart379(),
    optionPart380(),
    optionPart381(),
    ),
  )

private fun optionPart382(): CharacterOptionPart =
  CharacterOptionPart(
    "水・汗・泡（1）",
    false,
    listOf(
      "h4_wet_water_drops" to "肌を伝う水滴",
      "h4_wet_dripping_hair" to "髪からしたたる水",
      "h4_wet_oil_massage" to "オイルをぬる",
      "h4_wet_lotion_smear" to "ローションのぬめり",
      "h4_wet_lotion_cover" to "ローションまみれ",
      "h4_wet_cream_skin" to "クリームをぬった肌",
      "h4_wet_foam_body" to "泡まみれの体",
      "h4_wet_soap_scrub" to "泡で洗う",
      "h4_wet_sponge" to "スポンジで洗う",
      "h4_wet_steam_mirror" to "湯気の曇った鏡",
      "h4_wet_onsen_flush" to "温泉で火照る",
      "h4_wet_sauna" to "サウナの汗",
      "h4_wet_pool_drip" to "プールサイドで水滴",
      "h4_wet_sea_wet" to "海から上がった濡れ姿",
      "h4_wet_waterfall" to "水を浴びる",
      "h4_wet_hose_spray" to "水しぶき",
      "h4_wet_rain_shelter" to "雨宿りで濡れる",
    ),
  )

private fun optionPart383(): CharacterOptionPart =
  CharacterOptionPart(
    "水・汗・泡（2）",
    false,
    listOf(
      "h4_wet_glistening" to "つやめく肌",
      "h4_wet_sweat_shine" to "汗でてかる肌",
      "h4_wet_water_glass_spill" to "飲み物をこぼして濡れる",
      "h4_wet_milk_spill" to "牛乳でぬれた口もと",
      "h4_wet_whipped_cream" to "生クリームを肌にぬる",
      "h4_wet_chocolate_body" to "チョコレートを肌にぬる",
      "h4_wet_honey_drip" to "はちみつのしたたり",
      "h4_wet_fruit_juice" to "果汁がしたたる",
      "h4_wet_ice_mouth" to "氷を口に含む",
      "h4_wet_popsicle_lick" to "アイスを舐める",
      "h4_wet_banana_bite" to "バナナをほおばる",
      "h4_wet_candy_lick" to "飴を舐める",
      "h4_wet_sticky_fingers" to "ねばついた指",
      "h4_wet_liquid_string" to "指先の糸ひく液",
      "h4_wet_mess_face" to "顔に飛んだしぶき",
      "h4_wet_body_paint_drip" to "ペイントがしたたる",
      "h4_wet_wet_look_gloss" to "濡れたような質感の肌",
    ),
  )

private fun optionPart384(): CharacterOptionPart =
  CharacterOptionPart(
    "汗の演出（部位）",
    false,
    listOf(
      "h4_sw_forehead" to "額の汗",
      "h4_sw_cheek" to "頬を伝う汗",
      "h4_sw_armpit" to "脇の汗",
      "h4_sw_collarbone" to "鎖骨にたまる汗",
      "h4_sw_underboob" to "胸の下の汗",
      "h4_sw_stomach" to "おなかの汗",
      "h4_sw_hands" to "手のひらの汗",
      "h4_sw_feet" to "足の汗",
      "h4_sw_whole_body" to "全身の汗",
      "h4_sw_summer_heat" to "夏の暑さの汗",
      "h4_sw_towel_wipe" to "タオルで汗をぬぐう",
      "h4_sw_shirt_stuck" to "汗で貼りついた服",
      "h4_sw_drip_chin" to "あごから落ちる汗",
      "h4_sw_drip_hair" to "髪の生え際の汗",
    ),
  )

private fun optionGenre78(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体液・汗・濡れ・ぬるぬる",
    listOf(
    optionPart382(),
    optionPart383(),
    optionPart384(),
    ),
  )

private fun optionPart385(): CharacterOptionPart =
  CharacterOptionPart(
    "縄の縛り方（1）",
    false,
    listOf(
      "h4_rope_takate_kote" to "高手小手縛り",
      "h4_rope_diamond" to "菱縄縛り",
      "h4_rope_breast" to "胸を挟む縄",
      "h4_rope_breast_top_bottom" to "胸の上下を縛る縄",
      "h4_rope_chest_wrap" to "胸まわりに巻く縄",
      "h4_rope_one_hand" to "片手だけ縛る",
      "h4_rope_palms_together" to "合掌縛り",
      "h4_rope_reverse_prayer" to "合掌を背中で縛る",
      "h4_rope_box_tie" to "ボックスタイ",
      "h4_rope_elbows" to "肘を縛る",
      "h4_rope_wrists_front" to "手首を前で縛る",
      "h4_rope_wrists_back" to "手首を背中で縛る",
      "h4_rope_wrists_overhead" to "両手首を頭上に縛る",
      "h4_rope_fingers" to "指先を縛る",
      "h4_rope_thumbs" to "親指を縛る",
      "h4_rope_ankles" to "足首を縛る",
      "h4_rope_knees" to "膝を縛る",
      "h4_rope_thighs" to "太ももを縛る",
      "h4_rope_calf_thigh" to "膝を曲げて縛る",
      "h4_rope_frogtie" to "蛙縛り",
      "h4_rope_hogtie" to "後ろ手高手の海老反り",
      "h4_rope_shrimp" to "海老縛り",
      "h4_rope_reverse_shrimp" to "逆海老縛り",
      "h4_rope_crab" to "蟹縛り",
      "h4_rope_cocoon" to "繭のように巻く縄",
      "h4_rope_full_body" to "全身に巻く縄",
    ),
  )

private fun optionPart386(): CharacterOptionPart =
  CharacterOptionPart(
    "縄の縛り方（2）",
    false,
    listOf(
      "h4_rope_waist" to "腰に回す縄",
      "h4_rope_hip_harness" to "腰の縄ハーネス",
      "h4_rope_crotch" to "股に通した縄",
      "h4_rope_neck" to "首から下げる縄",
      "h4_rope_neck_loose" to "ゆるい首縄",
      "h4_rope_back_x" to "背中でクロスする縄",
      "h4_rope_over_clothes" to "服の上から縛る縄",
      "h4_rope_under_clothes" to "服の下に縄",
      "h4_rope_marks" to "縄のあとが残る肌",
      "h4_rope_knots" to "結び目のアップ",
      "h4_rope_suspension_light" to "軽い吊り",
      "h4_rope_suspension_back" to "仰向けの吊り",
      "h4_rope_suspension_inverted" to "逆さ吊り",
      "h4_rope_suspension_hands" to "両手を吊る",
      "h4_rope_beam" to "梁に通した縄",
      "h4_rope_hair_tied" to "髪を結んだ縄",
      "h4_rope_ribbon_tie" to "リボンで縛る",
      "h4_rope_silk_scarf" to "絹のスカーフで縛る",
      "h4_rope_cloth_belt" to "帯で縛る",
      "h4_rope_tie_cord" to "ひもで縛る",
      "h4_rope_cotton" to "綿縄",
      "h4_rope_cord_pink" to "ピンクの縄",
    ),
  )

private fun optionPart387(): CharacterOptionPart =
  CharacterOptionPart(
    "手枷・足枷・拘束具（1）",
    false,
    listOf(
      "h4_rs_wrist_cuffs_metal" to "金属の手錠",
      "h4_rs_wrist_cuffs_chain" to "鎖つきの手枷",
      "h4_rs_wrist_cuffs_behind" to "背中で手錠",
      "h4_rs_wrist_cuffs_front" to "前で手錠",
      "h4_rs_wrist_cuffs_bed" to "ベッドの柱につないだ手枷",
      "h4_rs_ankle_cuffs_chain" to "鎖つきの足枷",
      "h4_rs_upper_arm_cuffs" to "二の腕の枷",
      "h4_rs_cuffs_collar" to "首輪と手枷をつなぐ",
      "h4_rs_cuffs_wall" to "壁につながれた手枷",
      "h4_rs_cuffs_ceiling" to "天井から下がる手枷",
      "h4_rs_x_frame" to "X字の拘束台",
      "h4_rs_x_cross_standing" to "X字に立たされる",
      "h4_rs_bed_spread" to "ベッドで大の字に拘束",
      "h4_rs_bed_posts" to "四隅を縛られる",
      "h4_rs_chair_arms" to "椅子に腕を固定",
      "h4_rs_chair_full" to "椅子に全身を固定",
      "h4_rs_chair_legs_spread" to "椅子で脚を開いて固定",
      "h4_rs_stocks" to "さらし台の枷",
      "h4_rs_pillory_neck_hands" to "首と手を板で固定",
      "h4_rs_wooden_horse" to "木馬風の台",
      "h4_rs_cage_small" to "小さな檻に入る",
    ),
  )

private fun optionPart388(): CharacterOptionPart =
  CharacterOptionPart(
    "手枷・足枷・拘束具（2）",
    false,
    listOf(
      "h4_rs_cage_bird" to "鳥かご",
      "h4_rs_cage_hanging" to "吊られた檻",
      "h4_rs_cage_bed" to "檻つきのベッド",
      "h4_rs_cage_dog" to "犬用の檻",
      "h4_rs_straitjacket_white" to "白い拘束衣",
      "h4_rs_monoglove" to "一本袖の手袋",
      "h4_rs_boxing_arms" to "腕を胴に固定",
      "h4_rs_leg_binder" to "脚を束ねる拘束",
      "h4_rs_hobble_skirt" to "足首を縛る長いスカート",
      "h4_rs_mummy_wrap" to "ミイラのような包帯",
      "h4_rs_wrap_tape" to "テープでぐるぐる巻き",
      "h4_rs_bondage_belt_chest" to "胸を締める革ベルト",
      "h4_rs_bondage_belt_waist" to "腰を締める革ベルト",
      "h4_rs_belt_thigh" to "太ももの革ベルト",
      "h4_rs_belt_arm" to "腕を締める革ベルト",
      "h4_rs_belt_body_x" to "体をX字に締めるベルト",
      "h4_rs_strap_bondage" to "ストラップ拘束",
      "h4_rs_hair_tied_back" to "髪を引っ張るリード",
      "h4_rs_wrist_leash" to "手首のリード",
      "h4_rs_suspension_cuffs" to "吊り用の手枷",
      "h4_rs_swing" to "拘束用ブランコ",
      "h4_rs_hammock" to "ハンモックで吊る",
      "h4_rs_bondage_table" to "拘束台に乗る",
      "h4_rs_vacuum_bed" to "ラバーの寝袋風",
    ),
  )

private fun optionPart389(): CharacterOptionPart =
  CharacterOptionPart(
    "口・目・耳まわりの拘束（1）",
    false,
    listOf(
      "h4_rg_ball_gag_red" to "赤いボールギャグ",
      "h4_rg_ball_gag_black" to "黒いボールギャグ",
      "h4_rg_ring_gag" to "リングギャグ",
      "h4_rg_bit_gag" to "馬の轡のようなギャグ",
      "h4_rg_cloth_gag" to "布を噛ませる",
      "h4_rg_improvised_gag" to "ハンカチをくわえる",
      "h4_rg_otm_gag" to "布をかませて巻く",
      "h4_rg_panty_gag" to "下着を口に",
      "h4_rg_ribbon_gag" to "リボンの口枷",
      "h4_rg_harness_gag" to "ハーネスつきの口枷",
      "h4_rg_gag_drool" to "口枷からよだれ",
      "h4_rg_gag_tears" to "口枷と涙",
      "h4_rg_gag_blush" to "口枷と赤面",
      "h4_rg_blindfold_cloth" to "布の目隠し",
      "h4_rg_blindfold_satin" to "サテンの目隠し",
      "h4_rg_blindfold_red" to "赤い目隠し",
      "h4_rg_blindfold_black" to "黒い目隠し",
      "h4_rg_blindfold_white" to "白い目隠し",
      "h4_rg_blindfold_scarf" to "スカーフの目隠し",
      "h4_rg_blindfold_mask" to "アイマスクの目隠し",
    ),
  )

private fun optionPart390(): CharacterOptionPart =
  CharacterOptionPart(
    "口・目・耳まわりの拘束（2）",
    false,
    listOf(
      "h4_rg_blindfold_tears" to "目隠しからこぼれる涙",
      "h4_rg_blindfold_smile" to "目隠しで微笑む口もと",
      "h4_rg_blindfold_lift" to "目隠しを少しずらす",
      "h4_rg_headphones_blind" to "ヘッドホンと目隠し",
      "h4_rg_hood_zip_mouth" to "口にジッパーのあるマスク",
      "h4_rg_hood_eyes_only" to "目だけ出るフード",
      "h4_rg_hood_full" to "頭全体を覆うフード",
      "h4_rg_collar_posture" to "姿勢を整える首枷",
      "h4_rg_slave_collar" to "所有の証の首輪",
      "h4_rg_name_tag_collar" to "名札つきの首輪",
      "h4_rg_collar_ring" to "輪のついた首輪",
      "h4_rg_collar_lock" to "鍵のかかる首輪",
      "h4_rg_collar_leather_wide" to "幅広の革の首輪",
      "h4_rg_collar_chain" to "鎖の首輪",
      "h4_rg_collar_ribbon" to "リボンの首輪",
      "h4_rg_leash_hold_other" to "リードを持たれる",
      "h4_rg_leash_hold_self" to "自分でリードを持つ",
      "h4_rg_leash_mouth" to "リードをくわえる",
      "h4_rg_leash_short" to "短いリード",
      "h4_rg_leash_long" to "長いリード",
    ),
  )

private fun optionGenre79(): CharacterOptionGenre =
  CharacterOptionGenre(
    "拘束・縄（合意の上のSM演出）",
    listOf(
    optionPart385(),
    optionPart386(),
    optionPart387(),
    optionPart388(),
    optionPart389(),
    optionPart390(),
    ),
  )

private fun optionPart391(): CharacterOptionPart =
  CharacterOptionPart(
    "ラバー・ラテックス（1）",
    false,
    listOf(
      "h4_lr_latex_catsuit" to "ラテックスのキャットスーツ",
      "h4_lr_latex_catsuit_red" to "赤いラテックススーツ",
      "h4_lr_latex_catsuit_black" to "黒いラテックススーツ",
      "h4_lr_latex_catsuit_white" to "白いラテックススーツ",
      "h4_lr_latex_catsuit_pink" to "ピンクのラテックススーツ",
      "h4_lr_latex_catsuit_clear" to "透明なラテックススーツ",
      "h4_lr_latex_catsuit_blue" to "青いラテックススーツ",
      "h4_lr_latex_skirt" to "ラテックスのスカート",
      "h4_lr_latex_gloves" to "ラテックスの長手袋",
      "h4_lr_latex_stockings" to "ラテックスのストッキング",
      "h4_lr_latex_corset" to "ラテックスのコルセット",
      "h4_lr_latex_bra" to "ラテックスのブラ",
      "h4_lr_latex_shorts" to "ラテックスのショートパンツ",
      "h4_lr_latex_boots" to "ラテックスのブーツ",
      "h4_lr_latex_nurse" to "ラテックスの看護服風",
      "h4_lr_latex_maid" to "ラテックスのメイド服風",
      "h4_lr_latex_hood" to "ラテックスのフード",
    ),
  )

private fun optionPart392(): CharacterOptionPart =
  CharacterOptionPart(
    "ラバー・ラテックス（2）",
    false,
    listOf(
      "h4_lr_latex_gasmask" to "ガスマスク風のマスク",
      "h4_lr_latex_cap" to "ラテックスのキャップ",
      "h4_lr_latex_wrinkle" to "ラテックスのしわ",
      "h4_lr_latex_zipper" to "ジッパーつきの全身スーツ",
      "h4_lr_latex_zipper_front" to "前ジッパーを少し下ろす",
      "h4_lr_latex_wet" to "濡れたラテックス",
      "h4_lr_latex_inflatable" to "膨らませたラバー衣装",
      "h4_lr_pvc_mini" to "PVCのミニドレス",
      "h4_lr_pvc_boots" to "PVCのブーツ",
      "h4_lr_pvc_coat" to "PVCのコート",
      "h4_lr_vinyl_raincoat" to "ビニールのレインコート",
      "h4_lr_rubber_apron" to "ゴムのエプロン",
      "h4_lr_rubber_boots" to "ゴム長靴",
      "h4_lr_diving_suit" to "ダイビングスーツ",
      "h4_lr_neoprene" to "ネオプレーン素材",
    ),
  )

private fun optionPart393(): CharacterOptionPart =
  CharacterOptionPart(
    "革・レザー（1）",
    false,
    listOf(
      "h4_lr_leather_bra" to "革のブラ",
      "h4_lr_leather_harness_chest" to "胸の革ハーネス",
      "h4_lr_leather_harness_full" to "全身の革ハーネス",
      "h4_lr_leather_harness_shoulder" to "肩のハーネス",
      "h4_lr_leather_harness_x" to "X字のハーネス",
      "h4_lr_leather_harness_bra_o" to "Oリングのハーネスブラ",
      "h4_lr_leather_pants" to "革のパンツ",
      "h4_lr_leather_skirt_mini" to "革のミニスカート",
      "h4_lr_leather_dress" to "革のドレス",
      "h4_lr_leather_jacket_open" to "前を開けた革ジャケット",
      "h4_lr_leather_coat_long" to "ロングレザーコート",
      "h4_lr_leather_bodysuit" to "革のボディスーツ",
      "h4_lr_leather_gloves_long" to "革の長手袋",
      "h4_lr_leather_boots_thigh" to "革のニーハイブーツ",
      "h4_lr_leather_boots_heel" to "革のピンヒールブーツ",
      "h4_lr_leather_choker" to "革のチョーカー",
      "h4_lr_leather_belt_wide" to "幅広の革ベルト",
      "h4_lr_leather_cuff_arm" to "革のアームカフ",
    ),
  )

private fun optionPart394(): CharacterOptionPart =
  CharacterOptionPart(
    "革・レザー（2）",
    false,
    listOf(
      "h4_lr_leather_cap" to "革の帽子",
      "h4_lr_leather_mask_half" to "革のハーフマスク",
      "h4_lr_leather_mask_cat" to "猫耳つきの革マスク",
      "h4_lr_leather_whip_accessory" to "飾りの鞭を携える",
      "h4_lr_leather_crop" to "乗馬鞭を持つ",
      "h4_lr_leather_paddle" to "革のパドルを持つ",
      "h4_lr_leather_flogger" to "房鞭を持つ",
      "h4_lr_leather_studs" to "鋲つきの革",
      "h4_lr_leather_spikes" to "スパイクの飾り",
      "h4_lr_leather_laced" to "編み上げの革",
      "h4_lr_leather_laced_side" to "サイドの編み上げ",
      "h4_lr_leather_buckle" to "バックルが並ぶ装い",
      "h4_lr_leather_straps_dress" to "ストラップだけのドレス",
      "h4_lr_leather_straps_bikini" to "ストラップだけの水着",
      "h4_lr_leather_wrapped" to "革ベルトで体を巻く",
      "h4_lr_dominatrix_outfit" to "女王様風の装い",
      "h4_lr_dominatrix_peaked" to "制帽つきの女王様",
      "h4_lr_dominatrix_heels" to "ピンヒールで立つ",
      "h4_lr_dominatrix_foot_on" to "足を踏み出す",
      "h4_lr_dominatrix_sit_throne" to "玉座に座る支配者",
      "h4_lr_dominatrix_cigarette" to "長い煙管を持つ",
    ),
  )

private fun optionPart395(): CharacterOptionPart =
  CharacterOptionPart(
    "金属・チェーン・ジュエリー",
    false,
    listOf(
      "h4_mt_chain_dress" to "鎖のドレス",
      "h4_mt_chain_bikini" to "鎖のビキニ",
      "h4_mt_chain_top" to "鎖のトップス",
      "h4_mt_metal_bra" to "金属のブラ",
      "h4_mt_metal_bikini" to "金属のビキニ",
      "h4_mt_metal_cuffs" to "金属の腕輪",
      "h4_mt_metal_corset" to "金属のコルセット",
      "h4_mt_metal_armor_lewd" to "露出の多い鎧",
      "h4_mt_golden_jewel_set" to "黄金の装飾一式",
      "h4_mt_jewel_chain_body" to "宝石のボディチェーン",
      "h4_mt_pearl_body" to "真珠のボディ飾り",
      "h4_mt_pearl_choker" to "真珠の首飾り",
      "h4_mt_anklet_bell" to "鈴つきのアンクレット",
      "h4_mt_harem_veil" to "ハーレム風の薄布",
      "h4_mt_arabian_gold" to "アラビア風の金装飾",
    ),
  )

private fun optionGenre80(): CharacterOptionGenre =
  CharacterOptionGenre(
    "革・ラバー・金属の衣装（フェティッシュ）",
    listOf(
    optionPart391(),
    optionPart392(),
    optionPart393(),
    optionPart394(),
    optionPart395(),
    ),
  )

private fun optionPart396(): CharacterOptionPart =
  CharacterOptionPart(
    "足・脚フェチ（1）",
    false,
    listOf(
      "h4_ft_foot_arch" to "足の甲と土踏まず",
      "h4_ft_foot_toes_close" to "足指のアップ",
      "h4_ft_foot_toenail" to "ペディキュアの爪",
      "h4_ft_foot_barefoot_floor" to "素足で床に立つ",
      "h4_ft_foot_cross_ankle" to "足首を組む",
      "h4_ft_foot_leg_up_wall" to "壁に足を上げる",
      "h4_ft_foot_leg_stretch" to "脚を伸ばしてストレッチ",
      "h4_ft_foot_foot_near_face" to "足先を顔に寄せる",
      "h4_ft_foot_slipper_dangle" to "スリッパをぶら下げる足",
      "h4_ft_foot_heel_dangle" to "ヒールを脱ぎかけの足",
      "h4_ft_foot_heel_off" to "ヒールを脱いだ足",
      "h4_ft_foot_sandal_toe" to "サンダルの足先",
      "h4_ft_foot_stocking_toe" to "ストッキングのつま先",
    ),
  )

private fun optionPart397(): CharacterOptionPart =
  CharacterOptionPart(
    "足・脚フェチ（2）",
    false,
    listOf(
      "h4_ft_foot_stocking_sole" to "ストッキングの足裏",
      "h4_ft_foot_sock_sole" to "靴下の足裏",
      "h4_ft_foot_bare_thigh" to "むき出しの太もも",
      "h4_ft_foot_thick_thigh" to "むっちりした太もも",
      "h4_ft_foot_thigh_squeeze" to "太ももをぎゅっと閉じる",
      "h4_ft_foot_knee_back" to "膝の裏",
      "h4_ft_foot_ankle_strap" to "足首のストラップ",
      "h4_ft_foot_lace_up_legs" to "脚に編み上げるひも",
      "h4_ft_foot_leg_focus" to "脚だけを見せる構図",
      "h4_ft_foot_leg_lowangle" to "ローアングルで脚",
      "h4_ft_foot_stepping_pose" to "踏みつける足",
      "h4_ft_foot_foot_on_chair" to "椅子に足をのせる",
      "h4_ft_foot_heel_press" to "ヒールで踏みしめる",
    ),
  )

private fun optionPart398(): CharacterOptionPart =
  CharacterOptionPart(
    "脇・うなじ・背中・手・髪フェチ（1）",
    false,
    listOf(
      "h4_fm_armpit_raise" to "腕を上げた脇",
      "h4_fm_armpit_both" to "両腕を頭の後ろで組む",
      "h4_fm_armpit_close" to "脇のアップ",
      "h4_fm_armpit_sleeveless" to "ノースリーブの脇",
      "h4_fm_nape_hair_up" to "うなじの見える髪型",
      "h4_fm_nape_close" to "うなじのアップ",
      "h4_fm_nape_wet" to "濡れたうなじ",
      "h4_fm_nape_loose" to "ほつれ毛のうなじ",
      "h4_fm_neck_collarbone" to "鎖骨のライン",
      "h4_fm_neck_bite_mark" to "首筋の淡い跡",
      "h4_fm_back_bare" to "むき出しの背中",
      "h4_fm_back_open_dress" to "背中の大きく開いたドレス",
      "h4_fm_back_from_behind" to "後ろ姿",
      "h4_fm_back_hair_lifted" to "髪をかき上げる背中",
    ),
  )

private fun optionPart399(): CharacterOptionPart =
  CharacterOptionPart(
    "脇・うなじ・背中・手・髪フェチ（2）",
    false,
    listOf(
      "h4_fm_hand_slender" to "細くしなやかな手",
      "h4_fm_hand_rings" to "いくつもの指輪",
      "h4_fm_hand_glove_pull" to "手袋を外す指",
      "h4_fm_hand_glove_bite" to "手袋の指先をくわえる",
      "h4_fm_hand_touch_face" to "頬に触れる手",
      "h4_fm_hand_chin_lift" to "あごに指を添える",
      "h4_fm_hand_hair_stroke" to "髪をなでる手",
      "h4_fm_hair_long_straight" to "長い黒髪のつや",
      "h4_fm_hair_silky" to "さらさらの髪",
      "h4_fm_hair_sniff" to "髪の香りを嗅ぐ",
      "h4_fm_hair_tangle" to "髪が体にまとわる",
      "h4_fm_hair_mouth" to "口にかかる髪",
      "h4_fm_hair_twirl" to "指に髪を巻く",
    ),
  )

private fun optionPart400(): CharacterOptionPart =
  CharacterOptionPart(
    "声・匂い・筋肉・体格差（1）",
    false,
    listOf(
      "h4_fv_ear_blow" to "耳に息を吹きかける",
      "h4_fv_ear_kiss" to "耳へのキス",
      "h4_fv_ear_lick" to "耳を舐める",
      "h4_fv_ear_red" to "耳たぶが赤い",
      "h4_fv_ear_cup" to "耳に手を当てる",
      "h4_fv_ear_headphone_asmr" to "耳元で聞くASMR",
      "h4_fv_mic_binaural" to "ダミーヘッドマイク",
      "h4_fv_scent_neck" to "首筋の匂いを嗅ぐ",
      "h4_fv_scent_perfume_wrist" to "手首に香水",
      "h4_fv_scent_flowers" to "花の香りに包まれる",
      "h4_fv_muscle_abs" to "腹筋のライン",
      "h4_fv_muscle_back" to "背筋の盛り上がり",
      "h4_fv_muscle_arms" to "引き締まった腕",
      "h4_fv_muscle_chest" to "厚い胸板",
      "h4_fv_muscle_thighs" to "筋肉のついた太もも",
      "h4_fv_muscle_flex" to "筋肉を見せる",
    ),
  )

private fun optionPart401(): CharacterOptionPart =
  CharacterOptionPart(
    "声・匂い・筋肉・体格差（2）",
    false,
    listOf(
      "h4_fv_muscle_female_abs" to "腹筋の割れた女性",
      "h4_fv_muscle_sweat" to "筋肉に光る汗",
      "h4_fv_muscle_veins" to "腕の血管",
      "h4_fv_muscle_towel" to "上半身裸にタオル",
      "h4_fv_size_tall_short" to "背の高い人と小柄な人",
      "h4_fv_size_hug_up" to "見上げて抱きつく",
      "h4_fv_size_hug_down" to "見下ろして抱きしめる",
      "h4_fv_size_hand" to "大きな手と小さな手",
      "h4_fv_size_lift" to "軽々と抱き上げる",
      "h4_fv_size_shadow" to "影に包まれる体格差",
      "h4_fv_size_giantess" to "巨大な女性",
      "h4_fv_size_big_woman" to "大柄な女性",
      "h4_fv_size_wide_shoulder" to "肩幅の広い人",
      "h4_fv_size_bear" to "熊のような大男",
      "h4_fv_size_curvy" to "曲線の豊かな体",
      "h4_fv_size_soft_belly" to "やわらかいお腹",
      "h4_fv_size_bbw" to "豊満な体",
    ),
  )

private fun optionGenre81(): CharacterOptionGenre =
  CharacterOptionGenre(
    "フェチ（足・脇・うなじ・手・髪ほか）",
    listOf(
    optionPart396(),
    optionPart397(),
    optionPart398(),
    optionPart399(),
    optionPart400(),
    optionPart401(),
    ),
  )

private fun optionPart402(): CharacterOptionPart =
  CharacterOptionPart(
    "小物・コスプレのフェチ（1）",
    false,
    listOf(
      "h4_fc_glasses_black" to "黒縁の眼鏡",
      "h4_fc_glasses_red" to "赤フレームの眼鏡",
      "h4_fc_glasses_slip" to "ずり落ちた眼鏡",
      "h4_fc_glasses_over_eyes" to "眼鏡越しの上目づかい",
      "h4_fc_glasses_steam" to "曇った眼鏡",
      "h4_fc_glasses_wet" to "雫のついた眼鏡",
      "h4_fc_glasses_pushed" to "指で眼鏡を押し上げる",
      "h4_fc_glasses_bite" to "眼鏡のつるをくわえる",
      "h4_fc_glasses_semi_rim" to "半縁眼鏡",
      "h4_fc_glasses_chain" to "チェーンつき眼鏡",
      "h4_fc_gloves_white_long" to "白い長手袋",
      "h4_fc_gloves_black_long" to "黒い長手袋",
      "h4_fc_gloves_satin" to "サテンの手袋",
      "h4_fc_gloves_red" to "赤い手袋",
      "h4_fc_gloves_leather_black" to "黒革の手袋",
      "h4_fc_gloves_fingerless_lace" to "指なしレース手袋",
      "h4_fc_gloves_latex_surgical" to "薄いゴム手袋",
      "h4_fc_gloves_cuff_tight" to "手首を締める手袋",
      "h4_fc_gloves_hand_closeup" to "手袋の手のアップ",
      "h4_fc_gloves_touch_face" to "手袋の手で顔に触れる",
      "h4_fc_mask_surgical" to "白いマスク",
    ),
  )

private fun optionPart403(): CharacterOptionPart =
  CharacterOptionPart(
    "小物・コスプレのフェチ（2）",
    false,
    listOf(
      "h4_fc_mask_black" to "黒いマスク",
      "h4_fc_mask_down" to "あごまで下げたマスク",
      "h4_fc_mask_veil" to "口元のヴェール",
      "h4_fc_mask_masquerade" to "仮面舞踏会の仮面",
      "h4_fc_mask_half" to "半分だけの仮面",
      "h4_fc_mask_eye" to "目元の仮面",
      "h4_fc_mask_oni" to "鬼の面",
      "h4_fc_mask_phantom" to "仮面の下の微笑み",
      "h4_fc_whitecoat_open" to "前を開けた白衣",
      "h4_fc_whitecoat_glasses" to "白衣と眼鏡の研究者",
      "h4_fc_whitecoat_short" to "丈の短い白衣",
      "h4_fc_whitecoat_tight" to "体に張りつく白衣",
      "h4_fc_whitecoat_stockings" to "白衣とストッキング",
      "h4_fc_whitecoat_nothing" to "白衣一枚",
      "h4_fc_whitecoat_clipboard" to "クリップボードを持つ",
      "h4_fc_nurse_adult" to "大人の看護服風コスプレ",
      "h4_fc_nurse_syringe" to "注射器を持つ演出",
      "h4_fc_nurse_black" to "黒のナース服",
      "h4_fc_nurse_zip" to "ジッパーのナース服",
    ),
  )

private fun optionPart404(): CharacterOptionPart =
  CharacterOptionPart(
    "小物・コスプレのフェチ（3）",
    false,
    listOf(
      "h4_fc_doctor_adult" to "大人の医者風コスプレ",
      "h4_fc_secretary" to "秘書風のスーツ",
      "h4_fc_secretary_skirt" to "タイトスカートの秘書",
      "h4_fc_secretary_shirt_open" to "ボタンを外した秘書",
      "h4_fc_office_lady" to "OL風の装い",
      "h4_fc_flight_attendant" to "客室乗務員風",
      "h4_fc_police_adult" to "婦警風コスプレ",
      "h4_fc_police_handcuffs" to "手錠を持つ婦警風",
      "h4_fc_military_adult" to "軍服風コスプレ",
      "h4_fc_cabin_cap" to "制帽姿",
      "h4_fc_maid_adult" to "大人のメイド服",
      "h4_fc_maid_black_white" to "白黒のフレンチメイド",
      "h4_fc_maid_short" to "丈の短いメイド服",
      "h4_fc_maid_apron_only" to "メイドのエプロン",
      "h4_fc_maid_stockings" to "メイド服とニーソックス",
      "h4_fc_maid_feather_duster" to "はたきを持つメイド",
      "h4_fc_bunny_tights" to "バニーのタイツ",
      "h4_fc_bunny_ears_only" to "バニーの耳だけ",
      "h4_fc_bunny_black" to "黒いバニー",
      "h4_fc_bunny_white" to "白いバニー",
      "h4_fc_bunny_red" to "赤いバニー",
      "h4_fc_bunny_pink" to "ピンクのバニー",
      "h4_fc_bunny_tray" to "トレーを持つバニー",
      "h4_fc_cheer_adult" to "大人のチアガール風",
      "h4_fc_catgirl_adult" to "猫の耳としっぽのコスプレ",
    ),
  )

private fun optionPart405(): CharacterOptionPart =
  CharacterOptionPart(
    "小物・コスプレのフェチ（4）",
    false,
    listOf(
      "h4_fc_witch_adult" to "大人の魔女風",
      "h4_fc_vampire_adult" to "吸血鬼風の装い",
      "h4_fc_succubus_costume" to "小悪魔風の装い",
      "h4_fc_angel_costume" to "天使風の装い",
      "h4_fc_nun_adult" to "修道女風の装い",
      "h4_fc_nun_open" to "はだけた修道服",
      "h4_fc_miko_adult" to "巫女風の装い",
      "h4_fc_miko_loose" to "着崩した巫女装束",
      "h4_fc_santa_costume" to "サンタ風の衣装",
      "h4_fc_santa_mini" to "ミニスカサンタ",
      "h4_fc_pirate" to "海賊風の装い",
      "h4_fc_ninja_outfit" to "くのいち風の装い",
      "h4_fc_cat_burglar" to "怪盗風ボディスーツ",
      "h4_fc_racing_queen" to "レースクイーン風",
      "h4_fc_idol_stage" to "アイドル風ステージ衣装",
      "h4_fc_gym_trainer" to "ジム用ウェア",
      "h4_fc_leotard_dance" to "ダンス用レオタード",
      "h4_fc_ballet_tutu" to "バレエのチュチュ",
      "h4_fc_aerobics" to "エアロビ風の装い",
      "h4_fc_boxer_ring" to "リングガール風",
    ),
  )

private fun optionGenre82(): CharacterOptionGenre =
  CharacterOptionGenre(
    "眼鏡・手袋・マスク・白衣風（成人のコスプレ）",
    listOf(
    optionPart402(),
    optionPart403(),
    optionPart404(),
    optionPart405(),
    ),
  )

private fun optionPart406(): CharacterOptionPart =
  CharacterOptionPart(
    "脚まわりの素材とデザイン（1）",
    false,
    listOf(
      "h4_sg_pantyhose_sheer_black" to "薄手の黒タイツ",
      "h4_sg_pantyhose_white" to "白タイツ",
      "h4_sg_pantyhose_red" to "赤いタイツ",
      "h4_sg_pantyhose_purple" to "紫のタイツ",
      "h4_sg_pantyhose_pink" to "ピンクのタイツ",
      "h4_sg_pantyhose_brown" to "ブラウンのタイツ",
      "h4_sg_pantyhose_gray" to "グレーのタイツ",
      "h4_sg_pantyhose_lace_pattern" to "レース模様のタイツ",
      "h4_sg_pantyhose_polka" to "ドット柄のタイツ",
      "h4_sg_pantyhose_stripe" to "縞のタイツ",
      "h4_sg_pantyhose_argyle" to "アーガイル柄のタイツ",
      "h4_sg_pantyhose_pattern_star" to "星柄のタイツ",
      "h4_sg_pantyhose_floral" to "花柄のタイツ",
      "h4_sg_pantyhose_gloss" to "光沢のあるタイツ",
      "h4_sg_pantyhose_matte" to "マットなタイツ",
      "h4_sg_pantyhose_open_toe" to "つま先の開いたタイツ",
      "h4_sg_pantyhose_ladder" to "伝線が走ったタイツ",
      "h4_sg_pantyhose_tear_thigh" to "太ももの破れたタイツ",
      "h4_sg_pantyhose_tear_knee" to "膝の破れたタイツ",
      "h4_sg_pantyhose_hole_small" to "小さな穴の開いたタイツ",
      "h4_sg_pantyhose_panties_visible" to "タイツ越しの下着",
      "h4_sg_pantyhose_no_panties" to "タイツの下に何も付けない",
    ),
  )

private fun optionPart407(): CharacterOptionPart =
  CharacterOptionPart(
    "脚まわりの素材とデザイン（2）",
    false,
    listOf(
      "h4_sg_pantyhose_bulge" to "タイツの食い込み",
      "h4_sg_pantyhose_pull_down" to "タイツをずり下ろす",
      "h4_sg_pantyhose_pull_up" to "タイツを引き上げる",
      "h4_sg_pantyhose_wearing" to "タイツをはく途中",
      "h4_sg_pantyhose_peel" to "タイツを脱ぐ途中",
      "h4_sg_pantyhose_knee_wet" to "濡れたストッキングのひざ",
      "h4_sg_fishnet_large" to "大きな網目の網タイツ",
      "h4_sg_fishnet_small" to "細かい網目の網タイツ",
      "h4_sg_fishnet_stocking_thigh" to "網目のニーハイ",
      "h4_sg_fishnet_armwarmer" to "網目のアームウォーマー",
      "h4_sg_fishnet_bodystocking" to "網目のボディストッキング",
      "h4_sg_fishnet_top_bra" to "網目のブラ",
      "h4_sg_fishnet_dress" to "網目のドレス",
      "h4_sg_thighhigh_lace_top" to "レースの縁のニーハイ",
      "h4_sg_thighhigh_bow" to "リボンつきのニーハイ",
      "h4_sg_thighhigh_garter_clip" to "留め具つきのニーハイ",
      "h4_sg_thighhigh_slipping" to "ずり落ちるニーハイ",
      "h4_sg_thighhigh_rolled" to "ロールダウンしたニーソ",
      "h4_sg_thighhigh_asymmetric" to "左右違うニーソ",
      "h4_sg_thighhigh_white" to "白いニーソ",
      "h4_sg_thighhigh_black" to "黒いニーソ",
      "h4_sg_thighhigh_red" to "赤いニーソ",
    ),
  )

private fun optionPart408(): CharacterOptionPart =
  CharacterOptionPart(
    "脚まわりの素材とデザイン（3）",
    false,
    listOf(
      "h4_sg_thighhigh_pink" to "ピンクのニーソ",
      "h4_sg_thighhigh_blue" to "青いニーソ",
      "h4_sg_thighhigh_purple" to "紫のニーソ",
      "h4_sg_thighhigh_striped_black" to "黒白しまニーソ",
      "h4_sg_thighhigh_over_pantyhose" to "タイツの上のニーソ",
      "h4_sg_thighhigh_sheer" to "透けるニーソ",
      "h4_sg_thighhigh_fluff" to "ふわふわニーソ",
      "h4_sg_legwarmers_thigh" to "太もも丈のレッグウォーマー",
      "h4_sg_bodysuit_black_matte" to "黒いボディスーツ",
      "h4_sg_bodysuit_white" to "白いボディスーツ",
      "h4_sg_bodysuit_red" to "赤いボディスーツ",
      "h4_sg_bodysuit_sheer" to "透けるボディスーツ",
      "h4_sg_bodysuit_mesh" to "メッシュのボディスーツ",
      "h4_sg_bodysuit_cutout" to "カットアウトのボディスーツ",
      "h4_sg_bodysuit_zipper_front" to "前ジッパーのボディスーツ",
      "h4_sg_bodysuit_covered_nipples_effect" to "胸の形が浮かぶ薄生地",
      "h4_sg_bodysuit_shiny" to "光沢のある全身スーツ",
      "h4_sg_bodysuit_gloves_attached" to "手袋つきの全身スーツ",
      "h4_sg_zentai" to "全身タイツ（ゼンタイ）",
    ),
  )

private fun optionPart409(): CharacterOptionPart =
  CharacterOptionPart(
    "脚まわりの素材とデザイン（4）",
    false,
    listOf(
      "h4_sg_zentai_colored" to "色つきのゼンタイ",
      "h4_sg_zentai_face_covered" to "顔まで覆うゼンタイ",
      "h4_sg_spandex" to "スパンデックスの質感",
      "h4_sg_leotard_thong_cut" to "Tバックのレオタード",
      "h4_sg_leotard_open_back" to "背中の開いたレオタード",
      "h4_sg_leotard_sheer" to "透けるレオタード",
      "h4_sg_leotard_shiny" to "光沢のあるレオタード",
      "h4_sg_leotard_cutout_front" to "胸元の抜けたレオタード",
      "h4_sg_garter_wide_lace" to "幅広のレースのガーターベルト",
      "h4_sg_garter_black_satin" to "黒サテンのガーターベルト",
      "h4_sg_garter_white_satin" to "白サテンのガーターベルト",
      "h4_sg_garter_red" to "赤いガーターベルト",
      "h4_sg_garter_ring_thigh" to "太ももに巻くガーターリング",
      "h4_sg_garter_ring_lace" to "レースのガーターリング",
      "h4_sg_garter_ring_flower" to "花つきのガーターリング",
      "h4_sg_garter_ring_gun" to "道具を差すガーターリング",
      "h4_sg_garter_belt_no_panties" to "ガーターベルトだけの下半身",
      "h4_sg_garter_belt_over_panties" to "ショーツの上にガーターベルト",
      "h4_sg_garter_belt_under_panties" to "ショーツの下にガーターベルト",
      "h4_sg_garter_clip_snap" to "ストッキングをはじくクリップ",
    ),
  )

private fun optionGenre83(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ストッキング・タイツ・ボディスーツ（追加）",
    listOf(
    optionPart406(),
    optionPart407(),
    optionPart408(),
    optionPart409(),
    ),
  )

private fun optionPart410(): CharacterOptionPart =
  CharacterOptionPart(
    "恥じらいの仕草と表情（1）",
    false,
    listOf(
      "h4_sh_cover_chest_shy" to "腕で胸を隠して恥じらう",
      "h4_sh_cover_crotch_shy" to "手で前を隠して恥じらう",
      "h4_sh_cover_face_peek" to "指の隙間からのぞく",
      "h4_sh_look_away_blush" to "赤くなって顔をそむける",
      "h4_sh_eyes_down_shy" to "うつむいて目を伏せる",
      "h4_sh_trembling_voice" to "震える声で口ごもる",
      "h4_sh_nervous_smile" to "ひきつった照れ笑い",
      "h4_sh_embarrassed_full" to "全身で恥ずかしがる",
      "h4_sh_flustered_wave" to "あたふたと慌てる",
      "h4_sh_shrinking" to "身を縮める",
      "h4_sh_hide_behind_curtain" to "カーテンに身を隠す",
      "h4_sh_hide_behind_door" to "ドアの陰から顔を出す",
      "h4_sh_hide_behind_towel" to "タオルで体を隠す",
      "h4_sh_hide_in_blanket" to "布団に潜る",
      "h4_sh_pull_blanket_up" to "布団を口元まで引き上げる",
      "h4_sh_peek_wardrobe" to "試着室から顔だけ出す",
      "h4_sh_try_on_shy" to "試着して恥ずかしがる",
      "h4_sh_mirror_check" to "鏡で身なりを確認",
      "h4_sh_mirror_embarrassed" to "鏡に映る自分に赤面",
      "h4_sh_surprised_blush" to "不意を突かれて赤面",
    ),
  )

private fun optionPart411(): CharacterOptionPart =
  CharacterOptionPart(
    "恥じらいの仕草と表情（2）",
    false,
    listOf(
      "h4_sh_found_out_blush" to "見つかって固まる",
      "h4_sh_wardrobe_malfunction" to "衣装の不具合に気づく",
      "h4_sh_noticed_slip" to "ずれに気づいて直す",
      "h4_sh_hold_down_hem" to "風で裾を押さえる",
      "h4_sh_hide_blush_scarf" to "マフラーに顔を埋める",
      "h4_sh_hide_face_hat" to "帽子で顔を隠す",
      "h4_sh_hide_face_fan" to "扇子で顔を隠す",
      "h4_sh_hide_face_book" to "本で顔を隠す",
      "h4_sh_hide_face_plush" to "ぬいぐるみで顔を隠す",
      "h4_sh_pillow_bite" to "枕を噛んで耐える",
      "h4_sh_both_hands_mouth" to "両手で口を覆う",
      "h4_sh_bite_sleeve" to "袖を噛む",
      "h4_sh_bite_handkerchief" to "ハンカチを噛む",
      "h4_sh_tip_toe_fidget" to "もじもじと爪先立ち",
      "h4_sh_twiddle_fingers" to "指先をいじる",
      "h4_sh_hands_behind_shy" to "手を後ろに組む",
      "h4_sh_hand_hold_sleeve" to "相手の袖をつまむ",
      "h4_sh_peek_sideways" to "横目でちらりとのぞく",
    ),
  )

private fun optionPart412(): CharacterOptionPart =
  CharacterOptionPart(
    "露出の演出（1）",
    false,
    listOf(
      "h4_ex_show_shoulder" to "肩を出す",
      "h4_ex_show_collarbone" to "鎖骨を見せる",
      "h4_ex_show_cleavage_light" to "胸元をのぞかせる",
      "h4_ex_show_underboob" to "胸の下を見せる",
      "h4_ex_show_sideboob" to "横から見える胸",
      "h4_ex_show_backboob" to "背中ごしの胸の丸み",
      "h4_ex_show_back_wide" to "背中を大きく見せる",
      "h4_ex_show_midriff" to "おなかを見せる",
      "h4_ex_show_navel" to "おへそを見せる",
      "h4_ex_show_hip" to "腰骨を見せる",
      "h4_ex_show_hips_side" to "腰の横を見せる",
      "h4_ex_show_inner_thigh" to "内ももを見せる",
      "h4_ex_show_butt_cheek" to "お尻のライン",
      "h4_ex_show_butt_back" to "後ろから見るお尻",
      "h4_ex_show_butt_shorts" to "ショートパンツのお尻",
      "h4_ex_show_butt_swimsuit" to "水着のお尻",
      "h4_ex_show_butt_tight" to "ぴったりしたお尻",
      "h4_ex_show_butt_dress" to "ドレス越しのお尻",
      "h4_ex_show_pantyline" to "下着のライン",
      "h4_ex_show_bra_line" to "ブラの線",
      "h4_ex_show_bra_through" to "白いシャツから透けるブラ",
      "h4_ex_show_panties_dress" to "裾から見える下着",
      "h4_ex_show_nude_back" to "裸の背中",
      "h4_ex_show_nude_silhouette" to "影のみの裸体",
      "h4_ex_show_nude_artistic" to "芸術的な裸体",
      "h4_ex_show_nude_window" to "窓辺の裸体",
      "h4_ex_show_nude_curtain" to "カーテン越しの裸体",
      "h4_ex_show_nude_sheets" to "シーツをまとった裸体",
    ),
  )

private fun optionPart413(): CharacterOptionPart =
  CharacterOptionPart(
    "露出の演出（2）",
    false,
    listOf(
      "h4_ex_show_nude_steam" to "湯気の中の裸体",
      "h4_ex_show_nude_flowers" to "花で隠した裸体",
      "h4_ex_show_nude_petals" to "花びらのヴェール",
      "h4_ex_show_nude_water" to "水中の裸体",
      "h4_ex_show_nude_bubbles" to "泡で隠した裸体",
      "h4_ex_show_nude_light" to "光で隠した裸体",
      "h4_ex_show_nude_censor_hair" to "髪だけで隠した裸体",
      "h4_ex_show_nude_censor_steam" to "湯気で隠した裸体",
      "h4_ex_show_nude_censor_mosaic" to "モザイク風の修整",
      "h4_ex_show_nude_black_bar" to "黒線の修整",
      "h4_ex_show_nude_censor_light" to "光る修整",
      "h4_ex_show_nude_tape_cross" to "テープだけの装い",
      "h4_ex_show_open_back_dress" to "背中全開のドレス",
      "h4_ex_show_slit_dress" to "深いスリットのドレス",
      "h4_ex_show_keyhole" to "胸元のキーホール",
      "h4_ex_show_cutout_side" to "サイドのカットアウト",
      "h4_ex_show_cutout_belly" to "おなかの抜けた服",
      "h4_ex_show_cutout_back" to "背中の抜けた服",
      "h4_ex_show_cutout_underboob" to "胸の下の抜けた服",
      "h4_ex_show_sling_bikini" to "ひもだけの水着",
      "h4_ex_show_string_bikini" to "ひも水着",
      "h4_ex_show_strapless" to "ストラップのない服",
      "h4_ex_show_bandage_wrap" to "包帯だけを巻いた",
      "h4_ex_show_leaves_cover" to "葉で隠した",
      "h4_ex_show_body_markings" to "体の模様だけの装い",
      "h4_ex_show_cape_only" to "マントだけ",
      "h4_ex_show_long_coat_only" to "長いコート一枚",
    ),
  )

private fun optionPart414(): CharacterOptionPart =
  CharacterOptionPart(
    "露出の演出（3）",
    false,
    listOf(
      "h4_ex_show_beach_towel" to "ビーチタオルだけ",
      "h4_ex_show_pareo" to "パレオだけ",
      "h4_ex_show_fur_only" to "毛皮だけ",
      "h4_ex_show_shawl_only" to "ショールだけ",
      "h4_ex_show_slip_on_bed" to "ベッドでスリップ姿",
      "h4_ex_show_balcony" to "バルコニーでガウン",
      "h4_ex_show_morning_window" to "朝の窓辺でシャツ一枚",
      "h4_ex_show_kitchen_apron" to "台所で後ろ姿",
      "h4_ex_show_beach_lying" to "砂浜で寝そべる",
      "h4_ex_show_hot_spring_edge" to "温泉の縁に座る",
      "h4_ex_show_pool_edge" to "プールの縁に座る",
      "h4_ex_show_roof_night" to "夜の屋上で風に吹かれる",
      "h4_ex_show_forest_lake" to "湖畔の水辺",
      "h4_ex_show_waterfall" to "滝のある水浴び",
      "h4_ex_show_window_rain" to "雨の窓辺",
      "h4_ex_show_fog_mirror" to "曇った鏡に手をつく",
      "h4_ex_show_glass_press" to "ガラスに体を押し当てる",
      "h4_ex_show_stairs_looking_up" to "階段の途中",
      "h4_ex_show_train_seat" to "電車の座席で脚を組む",
      "h4_ex_show_car_seat" to "車の座席で脚を組む",
      "h4_ex_show_car_window" to "車の窓にもたれる",
      "h4_ex_show_locker" to "更衣室のベンチ",
      "h4_ex_show_bath_house" to "銭湯",
    ),
  )

private fun optionGenre84(): CharacterOptionGenre =
  CharacterOptionGenre(
    "露出・羞恥の演出",
    listOf(
    optionPart410(),
    optionPart411(),
    optionPart412(),
    optionPart413(),
    optionPart414(),
    ),
  )

private fun optionPart415(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント・ピアス（1）",
    false,
    listOf(
      "h4_tt_tattoo_arm" to "腕のタトゥー",
      "h4_tt_tattoo_sleeve" to "腕全体の刺青",
      "h4_tt_tattoo_back" to "背中の大きな刺青",
      "h4_tt_tattoo_leg" to "脚のタトゥー",
      "h4_tt_tattoo_hip" to "腰まわりのタトゥー",
      "h4_tt_tattoo_stomach" to "おなかのタトゥー",
      "h4_tt_tattoo_under_eye" to "目の下の小さな模様",
      "h4_tt_tattoo_floral" to "花の刺青",
      "h4_tt_tattoo_peony" to "牡丹の和彫り",
      "h4_tt_tattoo_koi" to "鯉の刺青",
      "h4_tt_tattoo_dragon" to "龍の刺青",
      "h4_tt_tattoo_tiger" to "虎の刺青",
    ),
  )

private fun optionPart416(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント・ピアス（2）",
    false,
    listOf(
      "h4_tt_tattoo_hannya" to "般若の刺青",
      "h4_tt_tattoo_wave" to "波の刺青",
      "h4_tt_tattoo_wings" to "翼の刺青",
      "h4_tt_tattoo_geometric" to "幾何学模様の刺青",
      "h4_tt_tattoo_script" to "文字の刺青",
      "h4_tt_tattoo_moon" to "月の刺青",
      "h4_tt_tattoo_symbol" to "紋様の刺青",
      "h4_tt_tattoo_neon" to "発光する紋様",
      "h4_tt_tattoo_gold" to "金色の入れ墨",
      "h4_tt_tattoo_white" to "白い入れ墨",
      "h4_tt_tattoo_red" to "赤い入れ墨",
      "h4_tt_tattoo_blue" to "青い入れ墨",
      "h4_tt_tattoo_henna" to "ヘナの模様",
      "h4_tt_tattoo_lace" to "レース柄のタトゥー",
      "h4_tt_tattoo_fake_sticker" to "貼るタトゥー",
      "h4_tt_tattoo_cover_scar" to "傷あとを覆う模様",
      "h4_tt_mark_womb_motif" to "腹部の淫紋風の紋様",
      "h4_tt_mark_womb_heart" to "ハート型の紋様",
      "h4_tt_mark_womb_wing" to "翼のような紋様",
    ),
  )

private fun optionPart417(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント・ピアス（3）",
    false,
    listOf(
      "h4_tt_mark_womb_pink" to "ピンクの紋様",
      "h4_tt_mark_womb_red" to "赤い紋様",
      "h4_tt_mark_womb_violet" to "紫の紋様",
      "h4_tt_mark_magic_circle" to "魔法陣の紋様",
      "h4_tt_mark_succubus" to "淫魔風の紋様",
      "h4_tt_mark_seal" to "封印の紋様",
      "h4_tt_mark_contract" to "契約の紋様",
      "h4_tt_mark_hand_back" to "手の甲の紋様",
      "h4_tt_mark_breast" to "胸の紋様",
      "h4_tt_mark_neck" to "首筋の紋様",
      "h4_tt_mark_thigh" to "太ももの紋様",
      "h4_tt_mark_stomach_lace" to "おなかの模様",
      "h4_tt_paint_gold" to "金色のボディペイント",
      "h4_tt_paint_silver" to "銀色のボディペイント",
      "h4_tt_paint_floral" to "花のボディペイント",
      "h4_tt_paint_animal" to "動物模様のボディペイント",
      "h4_tt_paint_stripes" to "縞のボディペイント",
      "h4_tt_paint_bikini" to "水着を描いたペイント",
      "h4_tt_paint_dress" to "ドレスを描いたペイント",
      "h4_tt_paint_suit" to "スーツを描いたペイント",
      "h4_tt_paint_uv" to "蛍光ペイント",
    ),
  )

private fun optionPart418(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント・ピアス（4）",
    false,
    listOf(
      "h4_tt_paint_face_half" to "顔半分のペイント",
      "h4_tt_paint_graffiti" to "体に描かれた文字",
      "h4_tt_paint_marker_body" to "体に文字を書く",
      "h4_tt_paint_lipstick_body" to "口紅で体に描く",
      "h4_tt_paint_chalk" to "白い粉を塗る",
      "h4_tt_paint_mud" to "泥を塗った肌",
      "h4_tt_paint_clay" to "粘土のパック",
      "h4_tt_pierce_ear_many" to "耳に複数のピアス",
      "h4_tt_pierce_navel_chain" to "へそのチェーン",
      "h4_tt_pierce_nipple_bar" to "胸のバーベル風の飾り",
      "h4_tt_pierce_nipple_chain" to "胸の飾り鎖",
      "h4_tt_pierce_dermal" to "ダーマルのピアス",
      "h4_tt_pierce_cheek" to "頬のピアス",
      "h4_tt_pierce_dimple" to "えくぼピアス",
    ),
  )

private fun optionPart419(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント・ピアス（5）",
    false,
    listOf(
      "h4_tt_pierce_gold" to "金色のピアス",
      "h4_tt_pierce_silver" to "銀色のピアス",
      "h4_tt_pierce_black" to "黒いピアス",
      "h4_tt_pierce_jewel" to "宝石のピアス",
      "h4_tt_pierce_ring" to "リングのピアス",
      "h4_tt_pierce_stud" to "スタッドのピアス",
      "h4_tt_pierce_chain_link" to "鎖でつなぐピアス",
      "h4_tt_pierce_stretched" to "拡張した耳たぶ",
      "h4_tt_pierce_plug" to "耳の拡張具",
      "h4_tt_corset_piercing" to "コルセット風の背中ピアス",
      "h4_tt_ribbon_back" to "背中を飾るリボン",
      "h4_tt_body_jewel" to "肌に貼った宝石",
      "h4_tt_body_gems_belly" to "おなかの宝石",
      "h4_tt_body_gems_face" to "顔の宝石",
      "h4_tt_body_gems_chest" to "胸元の宝石",
      "h4_tt_body_gems_shoulder" to "肩の宝石",
      "h4_tt_body_pearl" to "肌に貼った真珠",
      "h4_tt_body_sticker" to "体のシール",
      "h4_tt_body_bandaid" to "絆創膏を貼る",
      "h4_tt_body_bandaid_cross" to "絆創膏のバツ印",
      "h4_tt_body_bandage_arm" to "腕に巻く包帯",
      "h4_tt_body_eyepatch" to "眼帯",
      "h4_tt_body_cast" to "ギプス",
    ),
  )

private fun optionGenre85(): CharacterOptionGenre =
  CharacterOptionGenre(
    "タトゥー・淫紋・ボディペイント・ピアス（成人向け演出）",
    listOf(
    optionPart415(),
    optionPart416(),
    optionPart417(),
    optionPart418(),
    optionPart419(),
    ),
  )

private fun optionPart420(): CharacterOptionPart =
  CharacterOptionPart(
    "ジャンル・嗜好の演出（1）",
    false,
    listOf(
      "h4_gn_bdsm_aesthetic" to "BDSM調の美学",
      "h4_gn_domme" to "支配する側の貫禄",
      "h4_gn_sub" to "従う側のしおらしさ",
      "h4_gn_switch" to "主従が入れ替わる構図",
      "h4_gn_service" to "奉仕する姿勢",
      "h4_gn_worship" to "崇拝するまなざし",
      "h4_gn_pet_play_cat" to "ねこになりきる",
      "h4_gn_pet_play_dog" to "いぬになりきる",
      "h4_gn_pet_play_rabbit" to "うさぎになりきる",
      "h4_gn_pet_play_pony" to "ポニー風になりきる",
      "h4_gn_pet_paw_gloves" to "肉球の手袋",
      "h4_gn_pet_paw_shoes" to "肉球のブーツ",
      "h4_gn_pet_bowl" to "床に置いた器",
      "h4_gn_pet_bed" to "ペット用のクッション",
      "h4_gn_pet_headband_ears" to "獣耳のカチューシャ",
      "h4_gn_pet_tail_belt" to "ベルトにつけたしっぽ",
      "h4_gn_leash_walk" to "リードで散歩",
      "h4_gn_exhibition_show" to "見せつける仕草",
      "h4_gn_tease_pose" to "焦らすポーズ",
      "h4_gn_tease_finger" to "焦らす指の仕草",
      "h4_gn_tease_zipper" to "ジッパーを少しずつ下ろす",
      "h4_gn_tease_strip_stage" to "ゆっくり脱ぐ演出",
      "h4_gn_striptease_pole" to "ポールダンス",
      "h4_gn_striptease_chair" to "椅子を使った踊り",
      "h4_gn_striptease_stage" to "ステージの照明",
    ),
  )

private fun optionPart421(): CharacterOptionPart =
  CharacterOptionPart(
    "ジャンル・嗜好の演出（2）",
    false,
    listOf(
      "h4_gn_cabaret" to "キャバレー風",
      "h4_gn_burlesque_fan" to "羽扇で隠すバーレスク",
      "h4_gn_burlesque_boa" to "羽毛のボア",
      "h4_gn_burlesque_corset" to "バーレスクのコルセット",
      "h4_gn_burlesque_stockings" to "バーレスクのストッキング",
      "h4_gn_pinup_polka" to "水玉のピンナップ",
      "h4_gn_retro_lingerie" to "レトロな下着姿",
      "h4_gn_boudoir" to "ブードワール写真",
      "h4_gn_noir_woman" to "ノワールの女",
      "h4_gn_femme_fatale" to "妖艶な悪女",
      "h4_gn_gothic_lolita_adult" to "大人のゴシック装い",
      "h4_gn_punk_fetish" to "パンク×フェチ",
      "h4_gn_cyber_fetish" to "サイバー風フェチ",
      "h4_gn_cyber_leds" to "発光するボディ装飾",
      "h4_gn_cyber_visor" to "バイザー付きのスーツ",
      "h4_gn_cyber_mask" to "サイバーなマスク",
      "h4_gn_steampunk_corset" to "スチームパンクのコルセット",
      "h4_gn_fantasy_elf_robe" to "エルフ風の薄衣",
      "h4_gn_fantasy_succubus" to "淫魔風の装い",
      "h4_gn_fantasy_demon_horns" to "角と尻尾の悪魔風",
      "h4_gn_fantasy_vampire_bite" to "吸血鬼の牙",
      "h4_gn_fantasy_witch_potion" to "魔女の薬瓶",
    ),
  )

private fun optionPart422(): CharacterOptionPart =
  CharacterOptionPart(
    "ジャンル・嗜好の演出（3）",
    false,
    listOf(
      "h4_gn_fantasy_mermaid" to "人魚風の装い",
      "h4_gn_fantasy_harpy" to "鳥の翼と脚",
      "h4_gn_fantasy_monster_girl" to "獣人風の装い",
      "h4_gn_fantasy_kitsune" to "狐の耳と九尾",
      "h4_gn_fantasy_queen" to "女王の貫禄",
      "h4_gn_fantasy_priestess" to "聖職者風の薄衣",
      "h4_gn_fantasy_amazon" to "アマゾネス風の装い",
      "h4_gn_fantasy_dancer" to "踊り子風の装い",
      "h4_gn_fantasy_gladiator" to "剣闘士風の装い",
      "h4_gn_fantasy_assassin" to "暗殺者風のボディスーツ",
      "h4_gn_fantasy_pirate_queen" to "海賊の女王風",
      "h4_gn_fantasy_sorceress" to "魔導師風の薄衣",
      "h4_gn_scifi_suit" to "SF風の全身スーツ",
      "h4_gn_scifi_android" to "アンドロイド風の関節",
      "h4_gn_scifi_transparent" to "透明素材のスーツ",
      "h4_gn_scifi_neon_wet" to "ネオンで光る濡れ肌",
      "h4_gn_dollification" to "人形になりきる",
      "h4_gn_mannequin" to "マネキン風",
      "h4_gn_statue_marble" to "大理石の像のよう",
      "h4_gn_statue_gold" to "金の像のよう",
      "h4_gn_statue_wax" to "蝋人形のよう",
    ),
  )

private fun optionGenre86(): CharacterOptionGenre =
  CharacterOptionGenre(
    "世界的なジャンル名・嗜好（見た目で表せるもの）",
    listOf(
    optionPart420(),
    optionPart421(),
    optionPart422(),
    ),
  )

private fun optionPart423(): CharacterOptionPart =
  CharacterOptionPart(
    "全体の形",
    true,
    listOf(
      "h6_br_conical" to "円すい形（とがった形）",
      "h6_br_bell" to "釣り鐘形",
      "h6_br_hemisphere" to "半球形（お椀型）",
      "h6_br_asymmetric" to "左右で大きさが違う",
      "h6_br_east_west" to "外向き（離れ乳）",
      "h6_br_close_set" to "寄っている（谷間が深い）",
      "h6_br_side_set" to "外側に流れる形",
      "h6_br_athletic" to "引き締まった形（スポーツ型）",
      "h6_br_slim" to "ほっそりした形",
      "h6_br_full_top" to "上が張った形",
      "h6_br_full_bottom" to "下に重みのある形",
      "h6_br_drooping" to "やわらかく垂れた形",
      "h6_br_perky" to "上向きの形",
      "h6_br_flat" to "なだらか（控えめ）",
      "h6_br_huge_round" to "大きく丸い形",
      "h6_br_huge_heavy" to "大きく重たい形",
    ),
  )

private fun optionPart424(): CharacterOptionPart =
  CharacterOptionPart(
    "やわらかさ・張り",
    true,
    listOf(
      "h6_br_firm" to "ハリのある胸",
      "h6_br_soft" to "やわらかい胸",
      "h6_br_bouncy" to "よく揺れる胸",
      "h6_br_heavy_sway" to "ゆっくり揺れる胸",
      "h6_br_lifted" to "持ち上げられた胸",
    ),
  )

private fun optionPart425(): CharacterOptionPart =
  CharacterOptionPart(
    "谷間・胸元の見せ方",
    false,
    listOf(
      "h6_br_cleavage_deep" to "深い谷間",
      "h6_br_arms_under" to "腕で胸を寄せる",
      "h6_br_hands_cup" to "両手で胸を包む",
      "h6_br_hands_cover" to "手で胸を隠す",
      "h6_br_hug_pillow" to "クッションを抱いて胸を隠す",
    ),
  )

private fun optionGenre87(): CharacterOptionGenre =
  CharacterOptionGenre(
    "乳房の形（追加）",
    listOf(
    optionPart423(),
    optionPart424(),
    optionPart425(),
    ),
  )

private fun optionPart426(): CharacterOptionPart =
  CharacterOptionPart(
    "乳首の色",
    true,
    listOf(
      "h6_nip_light_pink" to "うすいピンク",
      "h6_nip_coral" to "サーモンピンク",
      "h6_nip_rose" to "バラ色",
      "h6_nip_light_brown" to "うすい茶色",
      "h6_nip_brown" to "茶色",
      "h6_nip_dark_brown" to "濃い茶色",
      "h6_nip_beige" to "ベージュ",
      "h6_nip_purple" to "紫がかった色（ファンタジー）",
      "h6_nip_blue" to "青い乳首（ファンタジー）",
    ),
  )

private fun optionPart427(): CharacterOptionPart =
  CharacterOptionPart(
    "乳首の形・大きさ",
    true,
    listOf(
      "h6_nip_medium" to "ふつうの大きさ",
      "h6_nip_large" to "大きい乳首",
      "h6_nip_flat" to "平たい乳首",
      "h6_nip_dimpled" to "くぼみのある乳首",
      "h6_nip_heart" to "ハート形の乳首",
      "h6_nip_star" to "星形の乳首",
    ),
  )

private fun optionPart428(): CharacterOptionPart =
  CharacterOptionPart(
    "乳輪の色・大きさ",
    true,
    listOf(
      "h6_are_medium" to "ふつうの乳輪",
      "h6_are_huge" to "とても大きい乳輪",
      "h6_are_puffy" to "ふっくらした乳輪",
      "h6_are_pale" to "うすい色の乳輪",
      "h6_are_pink" to "ピンクの乳輪",
      "h6_are_brown" to "茶色の乳輪",
      "h6_are_gradient" to "ぼかしたような乳輪",
      "h6_are_none" to "乳輪が目立たない",
    ),
  )

private fun optionPart429(): CharacterOptionPart =
  CharacterOptionPart(
    "乳首の演出",
    false,
    listOf(
      "h6_nip_chain" to "乳首ピアスをつなぐ鎖",
      "h6_nip_tape_x" to "テープのバツ印",
      "h6_nip_wet" to "濡れた乳首",
      "h6_nip_milk" to "母乳がにじむ（成人向け）",
      "h6_nip_blush" to "赤みを帯びた乳首",
      "h6_nip_peek" to "ちらりと見える乳首",
    ),
  )

private fun optionGenre88(): CharacterOptionGenre =
  CharacterOptionGenre(
    "乳首・乳輪（追加）",
    listOf(
    optionPart426(),
    optionPart427(),
    optionPart428(),
    optionPart429(),
    ),
  )

private fun optionPart430(): CharacterOptionPart =
  CharacterOptionPart(
    "陰毛の量・手入れ",
    true,
    listOf(
      "h6_ph_none" to "ない（つるつる）",
      "h6_ph_stubble" to "剃りあとが残る",
      "h6_ph_sparse" to "うすい",
      "h6_ph_light" to "少なめ",
      "h6_ph_natural" to "自然な量",
      "h6_ph_thick" to "濃い",
      "h6_ph_bush" to "もじゃもじゃ（ふさふさ）",
      "h6_ph_waxed" to "ほとんど処理してある",
      "h6_ph_overgrown" to "のび放題",
    ),
  )

private fun optionPart431(): CharacterOptionPart =
  CharacterOptionPart(
    "陰毛の形",
    true,
    listOf(
      "h6_phs_triangle" to "三角形",
      "h6_phs_narrow_strip" to "ごく細い縦ライン",
      "h6_phs_heart" to "ハート形",
      "h6_phs_diamond" to "ひし形",
      "h6_phs_oval" to "楕円形",
      "h6_phs_rectangle" to "四角形",
      "h6_phs_arrow" to "矢印形",
      "h6_phs_star" to "星形",
      "h6_phs_lightning" to "いなずま形",
      "h6_phs_butterfly" to "蝶形",
      "h6_phs_curly" to "くるくるの巻き毛",
      "h6_phs_straight" to "まっすぐな毛",
      "h6_phs_wavy" to "ゆるいウェーブ",
      "h6_phs_v_shape" to "V字（Vライン）",
      "h6_phs_i_line" to "Iライン処理（ない）",
      "h6_phs_o_line" to "Oラインのみ処理",
      "h6_phs_side_shaved" to "両側だけ剃る",
    ),
  )

private fun optionPart432(): CharacterOptionPart =
  CharacterOptionPart(
    "陰毛の色",
    true,
    listOf(
      "h6_phc_blonde" to "金髪",
      "h6_phc_red" to "赤毛",
      "h6_phc_auburn" to "赤茶色",
      "h6_phc_silver" to "銀・白髪",
      "h6_phc_gray" to "灰色",
      "h6_phc_match_hair" to "髪の色と同じ",
      "h6_phc_blue" to "青（ファンタジー）",
      "h6_phc_pink" to "ピンク（ファンタジー）",
      "h6_phc_purple" to "紫（ファンタジー）",
      "h6_phc_green" to "緑（ファンタジー）",
      "h6_phc_two_tone" to "髪と違う色のメッシュ",
      "h6_phc_gradient" to "根もとから色が変わる",
    ),
  )

private fun optionPart433(): CharacterOptionPart =
  CharacterOptionPart(
    "陰毛まわりの演出",
    false,
    listOf(
      "h6_phx_wet" to "濡れた陰毛",
      "h6_phx_glitter" to "ラメをのせた陰毛",
      "h6_phx_peek_panties" to "下着からはみ出る毛",
      "h6_phx_see_through" to "透けて見える毛",
      "h6_phx_armpit_none" to "わきの毛なし",
      "h6_phx_happy_trail" to "おへそ下の産毛",
      "h6_phx_covered_hair" to "髪や布で隠す",
    ),
  )

private fun optionGenre89(): CharacterOptionGenre =
  CharacterOptionGenre(
    "陰毛（追加）",
    listOf(
    optionPart430(),
    optionPart431(),
    optionPart432(),
    optionPart433(),
    ),
  )

private fun optionPart434(): CharacterOptionPart =
  CharacterOptionPart(
    "おしり・腰",
    true,
    listOf(
      "h6_bd_round_hips" to "丸いおしり",
      "h6_bd_peach" to "もも形のおしり",
      "h6_bd_heart_hips" to "ハート形のおしり",
      "h6_bd_big_butt" to "大きなおしり",
      "h6_bd_firm_butt" to "引き締まったおしり",
      "h6_bd_no_gap" to "太ももが触れ合う",
    ),
  )

private fun optionPart435(): CharacterOptionPart =
  CharacterOptionPart(
    "おへそ・おなか",
    true,
    listOf(
      "h6_bd_abs" to "うっすら割れた腹筋",
    ),
  )

private fun optionGenre90(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体の細部（そのほか）",
    listOf(
    optionPart434(),
    optionPart435(),
    ),
  )

private fun optionPart436(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装",
    false,
    listOf(
      "s1_vtem_0_2" to "ベール付きウェディングドレス",
      "s1_vtem_0_3" to "猫メイド服",
      "s1_vtem_0_4" to "妖精風メイド服",
      "s1_vtem_0_5" to "サンタ服",
      "s1_vtem_0_6" to "ハロウィンのミイラ衣装",
      "s1_vtem_0_7" to "ナース風包帯衣装",
      "s1_vtem_0_8" to "キョンシー風スウェット",
      "s1_vtem_0_9" to "紫陽花レースのシフォンワンピース",
      "s1_vtem_0_10" to "ロココ調ジャンパースカート",
      "s1_vtem_0_11" to "カシュクールワンピース",
      "s1_vtem_0_12" to "地雷系ミニスカート衣装",
      "s1_vtem_0_13" to "サイバーネオン風ミニワンピース",
      "s1_vtem_0_14" to "レザー風サイバーパーカー",
      "s1_vtem_0_15" to "ジャージ風オーバーサイズ衣装",
      "s1_vtem_0_16" to "オフショルダードレス",
      "s1_vtem_0_17" to "赤ずきん風ケープ衣装",
      "s1_vtem_0_18" to "魔術師のケープ衣装",
      "s1_vtem_0_19" to "貴公子風ショートパンツ衣装",
      "s1_vtem_0_20" to "絆創膏柄のファンシードール衣装",
      "s1_vtem_0_21" to "もこもこクラウド衣装",
      "s1_vtem_0_22" to "プリマ風ミニ丈衣装",
      "s1_vtem_0_23" to "賢者風ローブ衣装",
      "s1_vtem_0_24" to "ゆめかわナース衣装",
      "s1_vtem_0_25" to "見習い魔導士の衣装",
      "s1_vtem_0_26" to "白タイツのドール衣装",
      "s1_vtem_0_27" to "占星術師の衣装",
      "s1_vtem_0_28" to "花札柄の和風ミニ丈衣装",
      "s1_vtem_0_29" to "バスタオル姿",
      "s1_vtem_0_30" to "牛柄パジャマ",
      "s1_vtem_0_31" to "クラシックなユニフォーム",
      "s1_vtem_0_32" to "男子用ボレロ衣装",
      "s1_vtem_0_33" to "男子用ゲーミングフード衣装",
    ),
  )

private fun optionGenre91(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟦 VTEM：衣装",
    listOf(
    optionPart436(),
    ),
  )

private fun optionPart437(): CharacterOptionPart =
  CharacterOptionPart(
    "アクセサリー",
    false,
    listOf(
      "s1_vtem_1_0" to "丸メガネ",
      "s1_vtem_1_1" to "ボストンメガネ",
      "s1_vtem_1_2" to "瓶底メガネ",
      "s1_vtem_1_3" to "伊達メガネ",
      "s1_vtem_1_4" to "猫耳",
      "s1_vtem_1_6" to "お団子ウィッグ",
      "s1_vtem_1_7" to "エクステ",
      "s1_vtem_1_8" to "魔女帽子",
      "s1_vtem_1_9" to "海賊帽子",
      "s1_vtem_1_10" to "ナース帽",
      "s1_vtem_1_11" to "サンタ帽子",
      "s1_vtem_1_12" to "かぼちゃの被り物",
      "s1_vtem_1_13" to "狼の被り物",
      "s1_vtem_1_14" to "食パンの被り物",
      "s1_vtem_1_15" to "恐竜の被り物",
      "s1_vtem_1_16" to "フェイスシールド",
      "s1_vtem_1_18" to "タイツ",
      "s1_vtem_1_19" to "ニーハイソックス",
      "s1_vtem_1_20" to "手持ちマイク(ねこ型)",
      "s1_vtem_1_21" to "手持ちマイク(うさぎ型)",
      "s1_vtem_1_22" to "リボン",
      "s1_vtem_1_23" to "包帯",
      "s1_vtem_1_24" to "タトゥー風シール",
      "s1_vtem_1_25" to "ファーの襟巻き",
      "s1_vtem_1_26" to "羽飾り",
    ),
  )

private fun optionGenre92(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟦 VTEM：アクセサリー",
    listOf(
    optionPart437(),
    ),
  )

private fun optionPart438(): CharacterOptionPart =
  CharacterOptionPart(
    "小道具",
    false,
    listOf(
      "s1_vtem_2_0" to "魔導書",
      "s1_vtem_2_1" to "アコースティックギター",
      "s1_vtem_2_2" to "サイリウム",
      "s1_vtem_2_3" to "ペンライト",
      "s1_vtem_2_4" to "段ボール箱",
      "s1_vtem_2_5" to "ガチャカプセル",
      "s1_vtem_2_11" to "手持ちソーダ",
      "s1_vtem_2_12" to "ドリンクカップ",
      "s1_vtem_2_13" to "ベッド",
      "s1_vtem_2_14" to "枕",
      "s1_vtem_2_15" to "手持ちのカード",
      "s1_vtem_2_16" to "数字の札",
      "s1_vtem_2_17" to "召喚魔法陣の紙",
      "s1_vtem_2_18" to "ジャック",
      "s1_vtem_2_20" to "ランタンの灯り",
      "s1_vtem_2_21" to "蝋燭と燭台",
      "s1_vtem_2_22" to "魔女の鍋",
      "s1_vtem_2_23" to "おみくじ",
      "s1_vtem_2_24" to "賽銭箱",
      "s1_vtem_2_25" to "おせち",
      "s1_vtem_2_26" to "お団子と月見酒",
      "s1_vtem_2_27" to "クラッカー",
      "s1_vtem_2_28" to "くす玉",
      "s1_vtem_2_29" to "バースデーケーキ",
      "s1_vtem_2_30" to "ケーキのキャンドル",
      "s1_vtem_2_31" to "ローストチキン",
      "s1_vtem_2_32" to "ハロウィンのお菓子",
      "s1_vtem_2_33" to "雪だるま",
      "s1_vtem_2_34" to "双眼鏡風の望遠鏡",
      "s1_vtem_2_36" to "デスクに置く手",
    ),
  )

private fun optionGenre93(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟦 VTEM：小道具",
    listOf(
    optionPart438(),
    ),
  )

private fun optionPart439(): CharacterOptionPart =
  CharacterOptionPart(
    "エフェクト",
    false,
    listOf(
      "s1_vtem_3_5" to "変身の光",
      "s1_vtem_3_6" to "トランプ化の演出",
      "s1_vtem_3_7" to "カプセルが開く演出",
      "s1_vtem_3_8" to "窓からひょっこり顔を出す演出",
      "s1_vtem_3_9" to "召喚の魔法陣の光",
      "s1_vtem_3_10" to "異世界転生の光の柱",
      "s1_vtem_3_11" to "揺れる蝋燭の炎",
      "s1_vtem_3_12" to "コウモリが飛ぶ画面切替",
      "s1_vtem_3_13" to "かぼちゃが転がる画面切替",
      "s1_vtem_3_14" to "お化けが横切る画面切替",
      "s1_vtem_3_15" to "ループする準備中アニメーション",
      "s1_vtem_3_16" to "イルミネーションの点滅",
      "s1_vtem_3_17" to "サイリウムの光の揺れ",
      "s1_vtem_3_18" to "紙吹雪",
      "s1_vtem_3_19" to "風船が浮かぶ演出",
      "s1_vtem_3_20" to "星の瞬き",
      "s1_vtem_3_21" to "天の川の光",
      "s1_vtem_3_22" to "お化けのふわふわ浮遊",
      "s1_vtem_3_23" to "動く状態の発光LED",
      "s1_vtem_3_24" to "揺れる光のハロー",
      "s1_vtem_3_25" to "桜の花びらの舞",
      "s1_vtem_3_26" to "雪の結晶の舞",
      "s1_vtem_3_27" to "火花",
      "s1_vtem_3_28" to "星屑",
      "s1_vtem_3_29" to "魔法のオーラ",
      "s1_vtem_3_31" to "泡のきらめき",
      "s1_vtem_3_32" to "妖精の粉",
      "s1_vtem_3_33" to "輝く涙のハイライト",
      "s1_vtem_3_34" to "光の粒の降下",
      "s1_vtem_3_35" to "ハートの浮遊",
      "s1_vtem_3_36" to "音符の浮遊",
      "s1_vtem_3_37" to "汗マーク",
      "s1_vtem_3_38" to "怒りマーク",
      "s1_vtem_3_39" to "ぐるぐる目の渦",
    ),
  )

private fun optionGenre94(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟦 VTEM：エフェクト",
    listOf(
    optionPart439(),
    ),
  )

private fun optionPart440(): CharacterOptionPart =
  CharacterOptionPart(
    "背景",
    false,
    listOf(
      "s1_vtem_4_0" to "お正月の門松と掛け軸の部屋",
      "s1_vtem_4_1" to "初詣の神社と鳥居",
      "s1_vtem_4_2" to "ハロウィンの墓地と壁紙",
      "s1_vtem_4_3" to "クリスマスの壁と窓とイルミネーション",
      "s1_vtem_4_4" to "クリスマスのフロア飾り",
      "s1_vtem_4_5" to "クリスマスツリーの部屋",
      "s1_vtem_4_6" to "バレンタインのルーム",
      "s1_vtem_4_7" to "誕生日の飾り付けの部屋",
      "s1_vtem_4_8" to "記念日のバルーンの部屋",
      "s1_vtem_4_9" to "お月見の縁側",
      "s1_vtem_4_10" to "配信用のデスク周り",
      "s1_vtem_4_11" to "寝落ち配信用のベッドルーム",
      "s1_vtem_4_12" to "キッチン(バレンタイン仕様)",
      "s1_vtem_4_13" to "年越しそばの食卓",
      "s1_vtem_4_14" to "ひな祭りの雛飾りの段",
      "s1_vtem_4_15" to "パーティ会場の飾り",
      "s1_vtem_4_16" to "配信準備中の待機画面",
      "s1_vtem_4_17" to "おやすみ配信の暗い部屋",
      "s1_vtem_4_18" to "お花畑の背景",
      "s1_vtem_4_19" to "夜の街並みの背景",
      "s1_vtem_4_20" to "星空の背景",
      "s1_vtem_4_21" to "和室の背景",
      "s1_vtem_4_22" to "図書室の背景",
      "s1_vtem_4_23" to "神社の境内",
      "s1_vtem_4_24" to "桜並木",
      "s1_vtem_4_25" to "屋上の背景",
      "s1_vtem_4_26" to "海辺の背景",
      "s1_vtem_4_27" to "プールサイドの背景",
      "s1_vtem_4_28" to "ステージの背景",
      "s1_vtem_4_29" to "カフェの店内",
      "s1_vtem_4_30" to "魔女の部屋の背景",
      "s1_vtem_4_31" to "魔法使いの書斎",
      "s1_vtem_4_32" to "宇宙船の内部",
      "s1_vtem_4_33" to "砂漠の遺跡",
      "s1_vtem_4_34" to "雪景色の背景",
      "s1_vtem_4_35" to "夕焼けの背景",
    ),
  )

private fun optionGenre95(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟦 VTEM：背景",
    listOf(
    optionPart440(),
    ),
  )

private fun optionPart441(): CharacterOptionPart =
  CharacterOptionPart(
    "拘束具",
    false,
    listOf(
      "s1_booth_5_0" to "首輪と鎖",
      "s1_booth_5_1" to "革の首輪",
      "s1_booth_5_2" to "猫の首輪",
      "s1_booth_5_3" to "鈴付きの首輪",
      "s1_booth_5_4" to "電流風の首輪",
      "s1_booth_5_5" to "開閉する手錠",
      "s1_booth_5_6" to "猫の手を模した手錠",
      "s1_booth_5_7" to "壊れた手錠",
      "s1_booth_5_8" to "ぶら下がる足枷",
      "s1_booth_5_9" to "鉄の枷セット",
      "s1_booth_5_10" to "木の枷セット",
      "s1_booth_5_11" to "金属の拘束具",
      "s1_booth_5_12" to "脱獄囚の拘束衣",
      "s1_booth_5_13" to "拘束衣風コート",
      "s1_booth_5_14" to "レザーのアームバインダー",
      "s1_booth_5_15" to "コルセット付きアームバインダー",
      "s1_booth_5_16" to "拘束ベルト",
      "s1_booth_5_17" to "口枷",
      "s1_booth_5_18" to "金属の口輪",
      "s1_booth_5_19" to "ぎざぎざのマズルガード",
      "s1_booth_5_20" to "ベルト式の目隠し",
      "s1_booth_5_21" to "革のマスク",
      "s1_booth_5_22" to "呼吸孔付きのレザーマスク",
      "s1_booth_5_23" to "拘束用マスク",
      "s1_booth_5_24" to "X字の拘束架",
      "s1_booth_5_25" to "十字架の拘束台",
      "s1_booth_5_26" to "拘束された手を引き止める鎖",
      "s1_booth_5_27" to "空中から垂れる鎖",
      "s1_booth_5_28" to "拘束チェーンの粒子",
      "s1_booth_5_29" to "金属製の貞操帯",
      "s1_booth_5_30" to "SM診察台",
      "s1_booth_5_31" to "液体状のスライム拘束",
      "s1_booth_5_32" to "封印の鎖",
      "s1_booth_5_33" to "封印の札",
      "s1_booth_5_34" to "縄での後ろ手拘束",
      "s1_booth_5_35" to "縄の緊縛",
      "s1_booth_5_36" to "レザーベルトのチョーカー",
      "s1_booth_5_37" to "蔦の拘束アイマスク",
      "s1_booth_5_38" to "布の猿ぐつわ",
      "s1_booth_5_39" to "腕輪型の拘束器",
      "s1_booth_5_40" to "重力カフ",
    ),
  )

private fun optionGenre96(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：拘束具",
    listOf(
    optionPart441(),
    ),
  )

private fun optionPart442(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装",
    false,
    listOf(
      "s1_booth_6_0" to "縞模様の囚人服",
      "s1_booth_6_1" to "セクシーな囚人服",
      "s1_booth_6_2" to "拘束違反の透けるギャル衣装",
      "s1_booth_6_3" to "ベルトドレス",
      "s1_booth_6_4" to "拘束服風ワンピース",
      "s1_booth_6_5" to "ラバースーツ",
      "s1_booth_6_6" to "サイバー風ボディスーツ",
      "s1_booth_6_7" to "ゴシックコーディネート",
      "s1_booth_6_8" to "蝶柄のサイバーゴシック衣装",
      "s1_booth_6_9" to "はだかリボン",
      "s1_booth_6_10" to "リボンだけのボディ装飾",
      "s1_booth_6_11" to "縛られた水兵服",
      "s1_booth_6_12" to "ストライプのサイハイソックス",
      "s1_booth_6_13" to "ストライプのタイツ",
      "s1_booth_6_14" to "患者衣",
      "s1_booth_6_15" to "白衣のナース風衣装",
      "s1_booth_6_16" to "研究者風の白衣",
      "s1_booth_6_17" to "制御服風のスーツ",
      "s1_booth_6_18" to "隔離施設の制服",
      "s1_booth_6_19" to "軍服風の衣装",
      "s1_booth_6_20" to "黒革のロングコート",
      "s1_booth_6_21" to "ファー付きのコート",
      "s1_booth_6_22" to "ボンテージ風のワンピース",
      "s1_booth_6_23" to "和風の緊縛衣装",
      "s1_booth_6_25" to "ハーネス付きのレオタード",
      "s1_booth_6_26" to "魔女のローブ",
      "s1_booth_6_27" to "狐耳に合う着物風衣装",
      "s1_booth_6_28" to "水兵風の鎖付き衣装",
      "s1_booth_6_29" to "包帯姿",
    ),
  )

private fun optionGenre97(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：衣装",
    listOf(
    optionPart442(),
    ),
  )

private fun optionPart443(): CharacterOptionPart =
  CharacterOptionPart(
    "アクセサリー",
    false,
    listOf(
      "s1_booth_7_4" to "獣耳",
      "s1_booth_7_5" to "しっぽ",
      "s1_booth_7_6" to "悪魔の羽根",
      "s1_booth_7_9" to "リボンのチョーカー",
      "s1_booth_7_10" to "手首のカフ",
      "s1_booth_7_11" to "腕輪",
      "s1_booth_7_13" to "ガーターリング",
      "s1_booth_7_14" to "髪飾り",
      "s1_booth_7_15" to "ボディタトゥー",
      "s1_booth_7_16" to "ハート型のニプレス",
      "s1_booth_7_17" to "ニプレス",
      "s1_booth_7_18" to "ネームタグ付きの首輪",
      "s1_booth_7_19" to "鈴",
      "s1_booth_7_20" to "首から下げる札",
      "s1_booth_7_21" to "ベネチアンマスク",
      "s1_booth_7_22" to "猫の手グローブ",
      "s1_booth_7_23" to "ロング手袋",
      "s1_booth_7_24" to "フィンガーレスグローブ",
      "s1_booth_7_25" to "口元のマスク",
    ),
  )

private fun optionGenre98(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：アクセサリー",
    listOf(
    optionPart443(),
    ),
  )

private fun optionPart444(): CharacterOptionPart =
  CharacterOptionPart(
    "小道具",
    false,
    listOf(
      "s1_booth_8_0" to "SM風の診察台",
      "s1_booth_8_1" to "拘束台",
      "s1_booth_8_2" to "鉄格子",
      "s1_booth_8_3" to "鳥かご型の檻",
      "s1_booth_8_4" to "鎖付きのベッド",
      "s1_booth_8_5" to "拘束具付きの鉄製ベッド",
      "s1_booth_8_6" to "鉄製のパイプ構造物",
      "s1_booth_8_7" to "手枷台",
      "s1_booth_8_8" to "首枷台",
      "s1_booth_8_9" to "磔台",
      "s1_booth_8_10" to "拘束ポッド",
      "s1_booth_8_11" to "水槽",
      "s1_booth_8_12" to "培養カプセル",
      "s1_booth_8_13" to "実験台",
      "s1_booth_8_14" to "手術台",
      "s1_booth_8_15" to "点滴スタンド",
      "s1_booth_8_16" to "注射器",
      "s1_booth_8_17" to "錠前",
      "s1_booth_8_18" to "鍵",
      "s1_booth_8_19" to "鉄の鎖",
      "s1_booth_8_20" to "銀の鎖",
      "s1_booth_8_21" to "縄",
      "s1_booth_8_22" to "革のベルト",
      "s1_booth_8_23" to "革の鞭",
      "s1_booth_8_24" to "蝋燭",
      "s1_booth_8_25" to "首輪のリード",
      "s1_booth_8_26" to "鞭の束",
      "s1_booth_8_27" to "ガラスの筒",
      "s1_booth_8_28" to "メカニカルな固定具",
      "s1_booth_8_29" to "囚人用の鉄球",
      "s1_booth_8_30" to "足に付ける鉄球",
      "s1_booth_8_31" to "看板",
    ),
  )

private fun optionGenre99(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：小道具",
    listOf(
    optionPart444(),
    ),
  )

private fun optionPart445(): CharacterOptionPart =
  CharacterOptionPart(
    "エフェクト",
    false,
    listOf(
      "s1_booth_9_0" to "封印のエフェクト",
      "s1_booth_9_1" to "封印の魔法陣",
      "s1_booth_9_2" to "鎖術の光",
      "s1_booth_9_3" to "銀鎖の輝き",
      "s1_booth_9_4" to "霊的な鎖の光",
      "s1_booth_9_5" to "紫の封印光",
      "s1_booth_9_6" to "拘束の光の輪",
      "s1_booth_9_7" to "鎖が絡みつく表現",
      "s1_booth_9_8" to "闇のオーラ",
      "s1_booth_9_9" to "呪いの紋様",
      "s1_booth_9_10" to "呪印",
      "s1_booth_9_11" to "淫紋風のハート形の紋様",
      "s1_booth_9_12" to "燃える鎖",
      "s1_booth_9_13" to "電流のスパーク",
      "s1_booth_9_14" to "雷の拘束",
      "s1_booth_9_15" to "氷の拘束",
      "s1_booth_9_16" to "水の拘束",
      "s1_booth_9_17" to "蔦の拘束",
      "s1_booth_9_18" to "茨の拘束",
      "s1_booth_9_19" to "光の手錠",
      "s1_booth_9_21" to "精神支配の波紋",
      "s1_booth_9_22" to "洗脳の渦",
      "s1_booth_9_23" to "催眠の渦巻き",
      "s1_booth_9_24" to "状態異常アイコン(麻痺)",
      "s1_booth_9_25" to "状態異常アイコン(拘束)",
      "s1_booth_9_26" to "状態異常アイコン(睡眠)",
      "s1_booth_9_27" to "赤く光る眼",
      "s1_booth_9_28" to "紫の煙",
      "s1_booth_9_29" to "ひび割れる呪い",
      "s1_booth_9_30" to "鎖が砕ける演出",
    ),
  )

private fun optionGenre100(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：エフェクト",
    listOf(
    optionPart445(),
    ),
  )

private fun optionPart446(): CharacterOptionPart =
  CharacterOptionPart(
    "背景・立ち絵・ポーズ",
    false,
    listOf(
      "s1_booth_10_0" to "病室の背景",
      "s1_booth_10_1" to "手術室の背景",
      "s1_booth_10_2" to "実験室の背景",
      "s1_booth_10_3" to "研究所の背景",
      "s1_booth_10_4" to "地下牢の背景",
      "s1_booth_10_5" to "牢屋の背景",
      "s1_booth_10_6" to "刑務所の背景",
      "s1_booth_10_7" to "取調室の背景",
      "s1_booth_10_8" to "拘置所の廊下",
      "s1_booth_10_9" to "警察署の背景",
      "s1_booth_10_10" to "SM風ホテルの部屋",
      "s1_booth_10_11" to "ラブホテル風の部屋",
      "s1_booth_10_12" to "ベッドを真上から見た構図",
      "s1_booth_10_13" to "ベッドを足元から見た構図",
      "s1_booth_10_14" to "病院の廊下",
      "s1_booth_10_15" to "地下室",
      "s1_booth_10_16" to "廃墟の背景",
      "s1_booth_10_17" to "古い洋館の背景",
      "s1_booth_10_18" to "儀式の間",
      "s1_booth_10_19" to "魔法陣の床",
      "s1_booth_10_20" to "教会の背景",
      "s1_booth_10_21" to "手を掴まれるポーズ",
      "s1_booth_10_22" to "腕を引き止める手のポーズ",
      "s1_booth_10_23" to "肩を抱く手のポーズ",
      "s1_booth_10_24" to "腰を引き寄せる手のポーズ",
      "s1_booth_10_25" to "押さえつける手の線画",
      "s1_booth_10_26" to "後ろ手に縛られた立ち姿",
      "s1_booth_10_27" to "鎖につながれた立ち姿",
      "s1_booth_10_28" to "拘束された男性の立ち絵",
      "s1_booth_10_29" to "奴隷の褐色の立ち絵",
      "s1_booth_10_30" to "囚人の立ち絵",
      "s1_booth_10_31" to "正座で拘束された姿",
      "s1_booth_10_32" to "床に座り込む姿",
      "s1_booth_10_33" to "壁に手をつく姿",
      "s1_booth_10_34" to "吊られた姿勢",
    ),
  )

private fun optionGenre101(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟥 BOOTH：背景・立ち絵・ポーズ",
    listOf(
    optionPart446(),
    ),
  )

private fun optionPart447(): CharacterOptionPart =
  CharacterOptionPart(
    "拘束具・器具",
    false,
    listOf(
      "s1_bowl_11_0" to "ニップルフックハーネス",
      "s1_bowl_11_1" to "頑丈な拘束ベルト",
      "s1_bowl_11_2" to "拘束縄",
      "s1_bowl_11_3" to "拘束縄の付属パーツ",
      "s1_bowl_11_4" to "魔法風の拘束具",
      "s1_bowl_11_5" to "空中から垂れる鎖と手枷",
      "s1_bowl_11_6" to "立ち枷",
      "s1_bowl_11_7" to "口枷セット",
      "s1_bowl_11_8" to "轡",
      "s1_bowl_11_10" to "洗脳仮面",
      "s1_bowl_11_11" to "電気首輪",
      "s1_bowl_11_12" to "怪しい液体入りの首輪",
      "s1_bowl_11_14" to "拘束用の固定台",
      "s1_bowl_11_15" to "レーザー拘束ポッド",
      "s1_bowl_11_16" to "懲罰用ドック",
      "s1_bowl_11_17" to "股責めの鉄棒",
      "s1_bowl_11_18" to "磔の柱",
      "s1_bowl_11_19" to "十字架",
      "s1_bowl_11_20" to "拘束の機械アタッチメント",
      "s1_bowl_11_21" to "ロボットアームのブラシ",
      "s1_bowl_11_23" to "ミルキングテーブル",
      "s1_bowl_11_24" to "穴あきロッカー",
      "s1_bowl_11_25" to "穴あきの壁",
      "s1_bowl_11_26" to "透明な壁の穴",
      "s1_bowl_11_27" to "拘束された石像",
      "s1_bowl_11_28" to "玉を挟む器具",
      "s1_bowl_11_29" to "木馬",
      "s1_bowl_11_30" to "木製の枷",
      "s1_bowl_11_31" to "縄の結び目",
      "s1_bowl_11_32" to "革のストラップ",
      "s1_bowl_11_34" to "革製の拘束ハーネス",
      "s1_bowl_11_35" to "電気ショック風の器具",
    ),
  )

private fun optionGenre102(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：拘束具・器具",
    listOf(
    optionPart447(),
    ),
  )

private fun optionPart448(): CharacterOptionPart =
  CharacterOptionPart(
    "アクセサリー",
    false,
    listOf(
      "s1_bowl_12_0" to "ハート型のピアス",
      "s1_bowl_12_1" to "コンドームのネックレス",
      "s1_bowl_12_2" to "手錠アクセ",
      "s1_bowl_12_3" to "太ももアクセ(ベルト)",
      "s1_bowl_12_4" to "太ももの飾りベルト",
      "s1_bowl_12_5" to "下着の詰め合わせ",
      "s1_bowl_12_6" to "リング",
      "s1_bowl_12_7" to "口枷アクセ",
      "s1_bowl_12_8" to "髪を飾るリボン",
      "s1_bowl_12_9" to "ボリュームのある髪飾り",
      "s1_bowl_12_10" to "イヤリング",
      "s1_bowl_12_11" to "ストッキング",
      "s1_bowl_12_12" to "スカーフ",
      "s1_bowl_12_13" to "ねじ込み式のボディピアス",
    ),
  )

private fun optionGenre103(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：アクセサリー",
    listOf(
    optionPart448(),
    ),
  )

private fun optionPart449(): CharacterOptionPart =
  CharacterOptionPart(
    "小道具",
    false,
    listOf(
      "s1_bowl_13_0" to "バイブレーターの形の玩具",
      "s1_bowl_13_1" to "ローターの形の玩具",
      "s1_bowl_13_2" to "たまご形のローター",
      "s1_bowl_13_3" to "アナルビーズ風の玩具",
      "s1_bowl_13_4" to "ディルド風の玩具",
      "s1_bowl_13_5" to "オナホ風の玩具",
      "s1_bowl_13_6" to "看板「交尾中」",
      "s1_bowl_13_7" to "フリップボード",
      "s1_bowl_13_8" to "虫眼鏡",
      "s1_bowl_13_9" to "スケスケの虫眼鏡",
      "s1_bowl_13_10" to "円筒のガラス容器",
      "s1_bowl_13_11" to "包丁",
      "s1_bowl_13_12" to "杵と臼",
      "s1_bowl_13_13" to "バスタブ",
      "s1_bowl_13_14" to "オイルライター",
      "s1_bowl_13_15" to "電球ソーダ",
      "s1_bowl_13_16" to "巻き尺",
      "s1_bowl_13_17" to "スケール",
      "s1_bowl_13_18" to "紳士用スマホ",
      "s1_bowl_13_19" to "マジックの道具",
      "s1_bowl_13_20" to "試着ボックス",
      "s1_bowl_13_21" to "更衣室",
      "s1_bowl_13_22" to "小判",
      "s1_bowl_13_23" to "魔物の卵",
      "s1_bowl_13_24" to "卵",
      "s1_bowl_13_25" to "おげんき飴",
      "s1_bowl_13_26" to "媚薬の瓶",
      "s1_bowl_13_27" to "避妊薬のシート",
      "s1_bowl_13_28" to "検査薬",
      "s1_bowl_13_29" to "パッケージ(コンドーム)",
      "s1_bowl_13_30" to "ベルトコンベヤの水車",
      "s1_bowl_13_31" to "フリップ式の札",
      "s1_bowl_13_32" to "円形ゼリーの相撲台",
      "s1_bowl_13_33" to "ビームギロチン",
      "s1_bowl_13_34" to "包丁と鍋",
      "s1_bowl_13_35" to "看板(標識)",
      "s1_bowl_13_36" to "エロ標識のホログラム",
      "s1_bowl_13_37" to "スロットマシン",
      "s1_bowl_13_38" to "カウンター",
      "s1_bowl_13_39" to "クイズ番組風のセット",
      "s1_bowl_13_40" to "ボード",
      "s1_bowl_13_41" to "小道具(雑貨)",
      "s1_bowl_13_42" to "火炎瓶",
      "s1_bowl_13_43" to "マジックボックス",
    ),
  )

private fun optionGenre104(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：小道具",
    listOf(
    optionPart449(),
    ),
  )

private fun optionPart450(): CharacterOptionPart =
  CharacterOptionPart(
    "エフェクト・シェーダー",
    false,
    listOf(
      "s1_bowl_14_0" to "涙目のエフェクト",
      "s1_bowl_14_1" to "ハイライトが揺れるエフェクト",
      "s1_bowl_14_2" to "泡のパーティクル",
      "s1_bowl_14_3" to "紙のパーティクル",
      "s1_bowl_14_4" to "粉のパーティクル",
      "s1_bowl_14_5" to "スライムが沸き立つ演出",
      "s1_bowl_14_6" to "星のきらきら",
      "s1_bowl_14_7" to "ネギ型パーティクル",
      "s1_bowl_14_8" to "水墨画風のエフェクト",
      "s1_bowl_14_9" to "漫画風のエフェクト",
      "s1_bowl_14_10" to "コマ落ちエフェクト",
      "s1_bowl_14_11" to "空気感のあるぼかし",
      "s1_bowl_14_12" to "カラーノイズ",
      "s1_bowl_14_13" to "リムライト",
      "s1_bowl_14_14" to "縁取りシルエット",
      "s1_bowl_14_15" to "トゥーン風の陰影",
      "s1_bowl_14_16" to "光沢のある肌",
      "s1_bowl_14_17" to "タコ皮膚の質感",
      "s1_bowl_14_18" to "ボケ味",
      "s1_bowl_14_19" to "モーションブラー",
      "s1_bowl_14_20" to "色調補正(夜)",
      "s1_bowl_14_21" to "色調補正(サイバーパンク)",
      "s1_bowl_14_22" to "色調補正(夢)",
      "s1_bowl_14_23" to "色調補正(夜明け)",
      "s1_bowl_14_24" to "色調補正(白昼夢)",
      "s1_bowl_14_25" to "発光モーフ",
      "s1_bowl_14_26" to "花火",
      "s1_bowl_14_27" to "爆発の破片",
      "s1_bowl_14_28" to "噴水",
      "s1_bowl_14_29" to "レーザーの光",
      "s1_bowl_14_30" to "光の筋",
      "s1_bowl_14_31" to "ホログラム",
      "s1_bowl_14_32" to "円形/四角/ハート/六角形の図形の演出",
      "s1_bowl_14_33" to "光の粒の舞",
      "s1_bowl_14_34" to "魔法陣の光",
      "s1_bowl_14_35" to "風に舞う花びら",
      "s1_bowl_14_36" to "降る雪",
      "s1_bowl_14_37" to "雨粒",
    ),
  )

private fun optionGenre105(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：エフェクト・シェーダー",
    listOf(
    optionPart450(),
    ),
  )

private fun optionPart451(): CharacterOptionPart =
  CharacterOptionPart(
    "背景・ステージ",
    false,
    listOf(
      "s1_bowl_15_0" to "巨大スクリーンのあるステージ",
      "s1_bowl_15_1" to "地下駐車場",
      "s1_bowl_15_2" to "水路のステージ",
      "s1_bowl_15_3" to "渡り廊下のステージ",
      "s1_bowl_15_4" to "廊下",
      "s1_bowl_15_5" to "多目的通路",
      "s1_bowl_15_6" to "多目的倉庫",
      "s1_bowl_15_7" to "ダンスホール",
      "s1_bowl_15_8" to "青い洞穴",
      "s1_bowl_15_9" to "月白の浮遊宮",
      "s1_bowl_15_10" to "小さな始まりのステージ",
      "s1_bowl_15_11" to "因習村",
      "s1_bowl_15_12" to "Route66風の道路",
      "s1_bowl_15_13" to "一人になれる道路",
      "s1_bowl_15_14" to "ハロウィンの真夜中の仮面舞踏会",
      "s1_bowl_15_15" to "舞台のスポットライト",
      "s1_bowl_15_16" to "ライブ会場",
      "s1_bowl_15_17" to "体育館",
      "s1_bowl_15_18" to "研究室",
      "s1_bowl_15_19" to "牢屋",
      "s1_bowl_15_20" to "壁際の壁",
      "s1_bowl_15_21" to "磨りガラスの壁",
      "s1_bowl_15_22" to "コンクリートの壁",
      "s1_bowl_15_23" to "夜の街",
      "s1_bowl_15_24" to "廃墟",
      "s1_bowl_15_25" to "洞窟",
      "s1_bowl_15_26" to "神殿",
      "s1_bowl_15_27" to "教会",
      "s1_bowl_15_28" to "図書館",
      "s1_bowl_15_29" to "宇宙の背景",
      "s1_bowl_15_30" to "草原",
      "s1_bowl_15_31" to "雪原",
      "s1_bowl_15_32" to "露天風呂",
      "s1_bowl_15_33" to "大浴場",
    ),
  )

private fun optionGenre106(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：背景・ステージ",
    listOf(
    optionPart451(),
    ),
  )

private fun optionPart452(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装",
    false,
    listOf(
      "s1_bowl_16_0" to "水着(ビキニ)",
      "s1_bowl_16_1" to "白い水着",
      "s1_bowl_16_2" to "バニーガール衣装",
      "s1_bowl_16_3" to "逆バニー衣装",
      "s1_bowl_16_4" to "ビキニのウェイトレス衣装",
      "s1_bowl_16_5" to "サンタビキニ",
      "s1_bowl_16_6" to "牛柄ビキニ",
      "s1_bowl_16_7" to "ビキニにバスタオル",
      "s1_bowl_16_8" to "セクシーな警察官の衣装",
      "s1_bowl_16_9" to "ハロウィンの衣装",
      "s1_bowl_16_10" to "バレンタインの衣装",
      "s1_bowl_16_12" to "和服",
      "s1_bowl_16_16" to "下着(ブラとショーツ)",
      "s1_bowl_16_17" to "着ぐるみ",
      "s1_bowl_16_18" to "ツナギ",
      "s1_bowl_16_20" to "ローブ",
      "s1_bowl_16_21" to "ドレス",
      "s1_bowl_16_22" to "コート",
    ),
  )

private fun optionGenre107(): CharacterOptionGenre =
  CharacterOptionGenre(
    "🟩 BowlRoll：衣装",
    listOf(
    optionPart452(),
    ),
  )

private fun optionPart453(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪の種類",
    true,
    listOf(
      "i1_bang_birkin" to "バーキン前髪",
      "i1_bang_korean" to "韓国風の薄めの前髪",
      "i1_bang_hatsumi" to "短めの眉上バング",
      "i1_bang_on_mayu" to "オン眉",
      "i1_bang_wide" to "ワイドバング",
      "i1_bang_feather" to "フェザーバング",
      "i1_bang_micro_asym" to "斜めに切った短い前髪",
      "i1_bang_zigzag" to "ギザギザの前髪",
      "i1_bang_curtain_long" to "長いカーテンバング",
      "i1_bang_ox_cat" to "猫のようなV字前髪",
      "i1_bang_cowlick" to "つむじで跳ねる前髪",
      "i1_bang_thick_heavy" to "重たい厚め前髪",
      "i1_bang_thin_airy" to "軽く透ける前髪",
      "i1_bang_split_center" to "センター分けの前髪",
      "i1_bang_off_center" to "少しずらしたセンター",
      "i1_bang_side_part" to "サイドパート前髪",
      "i1_bang_deep_side" to "深いサイド分け",
      "i1_bang_kakiage" to "かきあげ前髪",
      "i1_bang_pompadour" to "ポンパドール前髪",
      "i1_bang_brush_up" to "立ち上げ前髪",
      "i1_bang_tuck_ear" to "耳にかけた前髪",
      "i1_bang_bangs_half" to "半分だけ分けた前髪",
      "i1_bang_braided" to "三つ編みで留めた前髪",
      "i1_bang_eyes_hidden" to "目が隠れるほど長い前髪",
      "i1_bang_blunt_long" to "長めのぱっつん",
      "i1_bang_open_forehead_curl" to "おでこを出して巻く",
      "i1_bang_cat_bangs" to "猫耳のようにわけた前髪",
    ),
  )

private fun optionPart454(): CharacterOptionPart =
  CharacterOptionPart(
    "もみあげ・顔まわりの髪",
    false,
    listOf(
      "i1_side_cheek_lock" to "頬にかかる横髪",
      "i1_side_hime_lock" to "姫カットの横髪",
      "i1_side_long_thin" to "細く長い横髪",
      "i1_side_curl_lock" to "くるんと巻いた横髪",
      "i1_side_braided_lock" to "編み込んだ横髪",
      "i1_side_ringlet_chest" to "胸元まで垂れる巻き毛の横髪",
      "i1_side_bead_lock" to "ビーズをつけた横髪",
      "i1_side_wisp" to "後れ毛（ほつれ毛）",
      "i1_side_nape_wisp" to "うなじの後れ毛",
      "i1_side_sideburn" to "もみあげを細く残す",
      "i1_side_ear_covering" to "耳をかくす髪",
      "i1_side_face_frame_color" to "顔まわりだけ色を変える",
      "i1_side_long_bangs_pushed" to "長い前髪を横に分ける",
      "i1_side_back_flick" to "毛先が後ろにはねる",
    ),
  )

private fun optionGenre108(): CharacterOptionGenre =
  CharacterOptionGenre(
    "前髪（追加）",
    listOf(
    optionPart453(),
    optionPart454(),
    ),
  )

private fun optionPart455(): CharacterOptionPart =
  CharacterOptionPart(
    "基本の髪色",
    true,
    listOf(
      "i1_col_milk_tea" to "ミルクティーベージュ",
      "i1_col_greige" to "グレージュ",
      "i1_col_olive_beige" to "オリーブベージュ",
      "i1_col_latte" to "ラテベージュ",
      "i1_col_sand_beige" to "サンドベージュ",
      "i1_col_hazel_beige" to "ヘーゼルベージュ",
      "i1_col_topaz_brown" to "トパーズブラウン",
      "i1_col_marron" to "マロンブラウン",
      "i1_col_espresso" to "エスプレッソブラウン",
      "i1_col_caramel" to "キャラメルブラウン",
      "i1_col_golden" to "ゴールデンブロンド",
      "i1_col_cherry_red" to "チェリーレッド",
      "i1_col_dusty_pink" to "くすみピンク",
      "i1_col_baby_pink" to "ペールピンク",
      "i1_col_lilac_gray" to "ライラックグレー",
      "i1_col_smoky_purple" to "スモーキーパープル",
      "i1_col_navy_black" to "ネイビーブラック",
      "i1_col_blue_black" to "青みのある黒髪",
      "i1_col_charcoal" to "チャコールグレー",
      "i1_col_silver_white" to "シルバーホワイト",
      "i1_col_mint_ash" to "ミントアッシュ",
      "i1_col_khaki_ash" to "カーキアッシュ",
      "i1_col_sage" to "セージグリーン",
      "i1_col_sky_blue" to "スカイブルー",
      "i1_col_denim" to "デニムブルー",
      "i1_col_peach" to "ピーチ色",
      "i1_col_cream_yellow" to "クリームイエロー",
      "i1_col_amber" to "琥珀色",
      "i1_col_rose_gold" to "ローズゴールド",
      "i1_col_champagne" to "シャンパンゴールド",
      "i1_col_foggy_beige" to "フォギーベージュ",
      "i1_col_pink_brown" to "ピンクブラウン",
      "i1_col_lavender_greige" to "ラベンダーグレージュ",
      "i1_col_pearl" to "パールホワイト",
      "i1_col_graphite" to "グラファイト（鉛色）",
    ),
  )

private fun optionPart456(): CharacterOptionPart =
  CharacterOptionPart(
    "ハイライト・部分カラー",
    false,
    listOf(
      "i1_col_highlight_thin" to "細いハイライト",
      "i1_col_highlight_thick" to "太いハイライト",
      "i1_col_highlight_tips" to "毛先だけハイライト",
      "i1_col_inverse_tri" to "逆三角のハイライト",
      "i1_col_single_highlight" to "ひと束だけのハイライト",
      "i1_col_balayage" to "バレイヤージュ",
      "i1_col_hand_painted" to "手描きのような色の筋",
      "i1_col_sun_kissed" to "日に焼けたようなハイライト",
      "i1_col_earring" to "イヤリングカラー",
      "i1_col_hidden_gradient" to "隠しグラデ",
      "i1_col_bleach_streak" to "ブリーチ筋",
      "i1_col_money_piece" to "前髪の太い一房だけ色（マネーピース）",
      "i1_col_peekaboo" to "襟足の下だけ色（ピークアブー）",
      "i1_col_roots_shadow" to "根元ぼかし（シャドールーツ）",
      "i1_col_stripe_bangs" to "前髪だけ色違い",
      "i1_col_chunk" to "太い束で色を変える",
      "i1_col_zebra" to "ゼブラ模様の染め分け",
      "i1_col_leopard_dye" to "ヒョウ柄の染め",
      "i1_col_split_dye" to "左右で色が違う（スプリット）",
      "i1_col_color_block" to "色の面で分ける（カラーブロック）",
      "i1_col_under_dye" to "内側全体の色（ヒドゥン）",
      "i1_col_two_tone_bob" to "二色ボブ",
      "i1_col_stars_pattern" to "星模様の染め",
      "i1_col_heart_pattern" to "ハート模様の染め",
    ),
  )

private fun optionPart457(): CharacterOptionPart =
  CharacterOptionPart(
    "グラデーション・多色",
    true,
    listOf(
      "i1_col_ombre_subtle" to "ゆるやかなオンブレ",
      "i1_col_ombre_reverse" to "逆オンブレ（根元が明るい）",
      "i1_col_sombre" to "ソンブレ（ごく自然なグラデ）",
      "i1_col_rainbow_tips" to "毛先だけ虹色",
      "i1_col_pastel_multi" to "パステルの多色",
      "i1_col_sunset" to "夕焼けのグラデ",
      "i1_col_dawn" to "明け方の青から桃色",
      "i1_col_aurora" to "オーロラ色",
      "i1_col_holo" to "ホログラム調",
      "i1_col_opal" to "オパール調の乳白色",
      "i1_col_mermaid" to "マーメイドカラー（青緑紫）",
      "i1_col_unicorn" to "ユニコーンカラー",
      "i1_col_galaxy_dye" to "宇宙のような染め",
      "i1_col_cotton_candy" to "綿あめ色",
      "i1_col_uv" to "ブラックライトで光る髪",
      "i1_col_gradient_red_black" to "黒から赤へのグラデ",
      "i1_col_gradient_blue_white" to "青から白へのグラデ",
      "i1_col_gradient_pink_blue" to "桃色から水色へのグラデ",
      "i1_col_gradient_purple_pink" to "紫から桃色へのグラデ",
      "i1_col_gradient_gold_white" to "金から白へのグラデ",
      "i1_col_gradient_green_blue" to "緑から青へのグラデ",
      "i1_col_gradient_orange_pink" to "橙から桃色へのグラデ",
      "i1_col_gradient_black_silver" to "黒から銀へのグラデ",
      "i1_col_gradient_brown_blonde" to "茶からブロンドへのグラデ",
      "i1_col_hair_pastel_tips_two" to "二色の毛先",
      "i1_col_gold_streak" to "金の筋が入った黒髪",
      "i1_col_silver_streak" to "銀の筋が入った黒髪",
      "i1_col_white_streak" to "白い筋が入った髪",
      "i1_col_skunk" to "前髪の中央だけ白い筋",
      "i1_col_cruella_split" to "左右で黒と白に分かれた髪",
      "i1_col_marble" to "大理石のような濃淡",
      "i1_col_tie_dye" to "タイダイ染め",
      "i1_col_watercolor" to "水彩のようなにじみ",
      "i1_col_candy_stripe" to "キャンディ縞の染め",
      "i1_col_glitter_roots" to "根元にラメ",
    ),
  )

private fun optionPart458(): CharacterOptionPart =
  CharacterOptionPart(
    "白髪・自然な色の変化",
    false,
    listOf(
      "i1_col_gray_blur" to "白髪ぼかし",
      "i1_col_salt_pepper" to "ソルト＆ペッパー（白と黒の混じり）",
      "i1_col_silver_fox" to "シルバーフォックス（美しい銀髪）",
      "i1_col_grey_streak" to "一房だけの白髪",
      "i1_col_natural_light" to "光に透ける明るい茶",
      "i1_col_sun_bleached" to "日焼けで退色した髪",
      "i1_col_dark_roots_light_ends" to "根元が濃く毛先が明るい",
      "i1_col_chlorine_green" to "プールで緑がかった金髪",
      "i1_col_two_natural" to "自然な二色の毛（赤毛に金の筋）",
      "i1_col_dark_auburn" to "ダークオーバーン（赤茶）",
      "i1_col_ginger" to "ジンジャーの赤毛",
      "i1_col_henna" to "ヘナの赤茶",
      "i1_col_jet_black" to "漆黒の髪",
      "i1_col_brown_black" to "茶色がかった黒",
      "i1_col_blue_silver" to "青みのある銀髪",
      "i1_col_warm_gray" to "温かみのあるグレー",
      "i1_col_lavender_white" to "ラベンダー色を帯びた白",
      "i1_col_mint_white" to "ミントを帯びた白",
      "i1_col_pink_white" to "ピンクを帯びた白",
    ),
  )

private fun optionGenre109(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ヘアカラー・グラデ（追加）",
    listOf(
    optionPart455(),
    optionPart456(),
    optionPart457(),
    optionPart458(),
    ),
  )

private fun optionPart459(): CharacterOptionPart =
  CharacterOptionPart(
    "アップスタイル",
    true,
    listOf(
      "i1_arr_kachimori" to "カチモリ（毛先を残したお団子）",
      "i1_arr_claw_clip_up" to "バンスクリップでまとめる",
      "i1_arr_claw_clip_half" to "バンスクリップのハーフアップ",
      "i1_arr_banana_clip" to "バナナクリップで縦にまとめる",
      "i1_arr_chignon_low" to "ローシニヨン",
      "i1_arr_chignon_twisted" to "ねじりシニヨン",
      "i1_arr_tight_chignon" to "タイトなシニヨン",
      "i1_arr_messy_chignon" to "無造作なシニヨン",
      "i1_arr_roll_up" to "くるっと内側に巻き込むアップ",
      "i1_arr_pin_twist" to "ピン留めだけのねじり上げ",
      "i1_arr_pencil_bun" to "ペンで留めたお団子",
      "i1_arr_chopstick" to "箸のような棒で留める",
      "i1_arr_hair_stick" to "ヘアスティックのまとめ髪",
      "i1_arr_hair_fork" to "ヘアフォークで留める",
      "i1_arr_pearl_updo" to "パール飾りの上品アップ",
      "i1_arr_wedding_updo" to "ウェディング風の編み上げ",
      "i1_arr_romantic_updo" to "ロマンチックなゆるアップ",
      "i1_arr_banana_roll" to "バナナロール（縦に巻く）",
      "i1_arr_waterfall_updo" to "滝のように流れる後れ毛アップ",
      "i1_arr_pompadour_updo" to "ポンパドールのアップ",
      "i1_arr_afro_puff" to "アフロパフ（まるい束）",
      "i1_arr_pineapple" to "パイナップルアップ（頭頂で結ぶ）",
      "i1_arr_hair_flowers" to "花を挿したアップ",
      "i1_arr_bouquet_updo" to "ブーケのような盛りアップ",
      "i1_arr_hair_bow_pony" to "リボン結びにした髪の束",
      "i1_arr_hair_bow_bun" to "髪でリボンを作ったお団子",
      "i1_arr_ribbon_braid_in" to "リボンを編み込んだアップ",
      "i1_arr_wrap_ribbon" to "リボンで巻いたまとめ髪",
      "i1_arr_bandana_wrap" to "バンダナでまとめた髪",
      "i1_arr_scarf_wrap" to "スカーフで巻いたアレンジ",
      "i1_arr_turban" to "ターバン巻き",
    ),
  )

private fun optionPart460(): CharacterOptionPart =
  CharacterOptionPart(
    "ハーフアップ・ダウンアレンジ",
    true,
    listOf(
      "i1_arr_half_up_twist" to "ねじりハーフアップ",
      "i1_arr_half_up_braid" to "三つ編みのハーフアップ",
      "i1_arr_half_up_tight" to "タイトなハーフアップ",
      "i1_arr_half_up_big_clip" to "大きなクリップのハーフアップ",
      "i1_arr_half_up_ribbon" to "リボンのハーフアップ",
      "i1_arr_half_up_curl" to "巻き髪ハーフアップ",
      "i1_arr_half_up_pony" to "ハーフアップポニー",
      "i1_arr_half_up_rolled" to "くるりんぱのハーフアップ",
      "i1_arr_half_down_bun" to "上はお団子で下は下ろす",
      "i1_arr_half_up_two_buns" to "ハーフアップのふたつ団子",
      "i1_arr_half_up_twin_braid" to "ハーフアップの三つ編み二本",
      "i1_arr_half_up_lock_back" to "サイドの毛を後ろでまとめる",
      "i1_arr_pull_through" to "くるりんぱ",
      "i1_arr_twist_back" to "サイドを後ろでねじって留める",
      "i1_arr_pinned_back" to "ピンで後ろに留める",
      "i1_arr_x_pin_back" to "バッテンピンで留める",
      "i1_arr_ear_tuck_clip" to "耳かけとクリップ",
      "i1_arr_slick_back" to "オールバック",
      "i1_arr_wet_look" to "濡れ髪風のタイト",
      "i1_arr_side_swept_down" to "片側に流した下ろし髪",
      "i1_arr_hair_behind_shoulder" to "肩の後ろに流した髪",
      "i1_arr_flip_hair" to "髪を片手でかきあげる",
      "i1_arr_hair_wrap_neck" to "首に巻きつくような髪",
      "i1_arr_hair_tied_front" to "前に垂らして結んだ髪",
      "i1_arr_hair_back_pin" to "後頭部の高い位置で留めるクリップ",
      "i1_arr_hair_pull_through_scrunchie" to "シュシュでざっくり結ぶ",
    ),
  )

private fun optionPart461(): CharacterOptionPart =
  CharacterOptionPart(
    "編み込みアップ・凝ったアレンジ",
    true,
    listOf(
      "i1_arr_braid_updo" to "編み込みアップ",
      "i1_arr_braid_chignon" to "編み込みシニヨン",
      "i1_arr_braid_crown_bun" to "編み込みの王冠と団子",
      "i1_arr_pinwheel_braid" to "風車のように巻いた編み込み",
      "i1_arr_basket_weave" to "かご編みのまとめ髪",
      "i1_arr_celtic_knot" to "ケルト結びのような編み",
      "i1_arr_knot_braid" to "結び目を連ねる編み",
      "i1_arr_loop_braid" to "ループ編みのポニー",
      "i1_arr_ladder_braid" to "はしご編み",
      "i1_arr_snake_braid" to "蛇のように這う編み込み",
      "i1_arr_cage_braid" to "かごのように囲む編み込み",
      "i1_arr_dragon_braid" to "竜の背のような編み込み",
      "i1_arr_fantasy_updo" to "ファンタジー風の複雑な盛り髪",
      "i1_arr_tiara_updo" to "ティアラ付きの盛り髪",
      "i1_arr_queen_hair" to "女王のような高い盛り髪",
      "i1_arr_witch_updo" to "魔女のようなひとまとめ",
      "i1_arr_priestess_hair" to "神官風のまっすぐな髪と輪",
      "i1_arr_hair_cuffs" to "ヘアカフで飾った束",
      "i1_arr_hair_jewels" to "宝石をちりばめた髪",
      "i1_arr_hair_chain" to "髪を鎖でつなぐ",
      "i1_arr_hair_charm" to "ヘアチャーム",
      "i1_arr_hair_tassel" to "房飾りをつけた束",
      "i1_arr_hair_bell" to "鈴をつけた髪",
      "i1_arr_hair_lantern" to "髪に小さな灯りを飾る",
    ),
  )

private fun optionGenre110(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ヘアアレンジ・まとめ髪（追加）",
    listOf(
    optionPart459(),
    optionPart460(),
    optionPart461(),
    ),
  )

private fun optionPart462(): CharacterOptionPart =
  CharacterOptionPart(
    "質感",
    true,
    listOf(
      "i1_tex_glass" to "ガラスのようなツヤ",
      "i1_tex_matte" to "マットな質感",
      "i1_tex_dry_airy" to "軽くパサついた空気感",
      "i1_tex_cotton" to "綿のようなふわふわ",
      "i1_tex_fine" to "細くやわらかい髪",
      "i1_tex_flat_smooth" to "ぺたんと平らな髪",
      "i1_tex_greasy" to "オイリーなしっとり髪",
      "i1_tex_frizz_halo" to "ほつれ毛が光に透ける",
      "i1_tex_flyaways" to "細かいアホ毛が浮く",
      "i1_tex_static" to "静電気で浮いた髪",
      "i1_tex_damp" to "湿り気のある髪",
      "i1_tex_dripping" to "毛先からしずくが落ちる",
      "i1_tex_sweaty" to "汗で湿った髪",
      "i1_tex_bleached" to "ブリーチ後の傷んだ髪",
      "i1_tex_split_ends" to "枝毛",
      "i1_tex_dusty" to "ほこりっぽい乾いた髪",
      "i1_tex_ash_powdery" to "粉をまとったような髪",
      "i1_tex_snow_hair" to "雪をかぶった髪",
      "i1_tex_rain_hair" to "雨に濡れた髪",
      "i1_tex_sand_hair" to "砂まじりの髪",
      "i1_tex_leaf_stuck" to "葉っぱがついた髪",
      "i1_tex_petals_hair" to "花びらが舞う髪",
      "i1_tex_ribbon_flow" to "リボンのようになめらかな髪",
      "i1_tex_silk_thread" to "絹糸のような髪",
      "i1_tex_wire" to "針金のような硬い髪",
      "i1_tex_cloud_soft" to "雲のようなやわらかさ",
      "i1_tex_yarn" to "毛糸のような太い巻き",
      "i1_tex_straw" to "わらのように乾いた金髪",
      "i1_tex_goop" to "ジェルで固めた束感",
      "i1_tex_wax_spike" to "ワックスで立てた毛先",
      "i1_tex_sleek" to "ぴたっとなめらか",
      "i1_tex_airy_see" to "透け感のある軽い毛束",
      "i1_tex_chunky_strand" to "束感のある毛先",
    ),
  )

private fun optionPart463(): CharacterOptionPart =
  CharacterOptionPart(
    "光の当たり方・輝き",
    false,
    listOf(
      "i1_shine_rim" to "髪の輪郭に光が当たる",
      "i1_shine_backlit" to "逆光で透ける髪",
      "i1_shine_golden_hour" to "夕日で金色に光る髪",
      "i1_shine_moonlight" to "月光で青白く光る髪",
      "i1_shine_streak_highlight" to "髪の流れに沿った細い光",
      "i1_shine_gradient_light" to "光で色が移り変わる髪",
      "i1_shine_neon_edge" to "ネオンの縁取り光",
      "i1_shine_sparkle" to "きらきら光る髪",
      "i1_shine_glitter" to "ラメを散らした髪",
      "i1_shine_pearl_shine" to "真珠のような艶",
      "i1_shine_metal" to "金属のような光沢",
      "i1_shine_oil" to "油膜のような虹色の艶",
      "i1_shine_wet_shine" to "濡れたような強い艶",
      "i1_shine_soft_matte_light" to "やわらかい光でマットに見える",
      "i1_shine_dark_depth" to "黒髪の奥行きのある艶",
      "i1_shine_blue_black" to "青みを帯びた黒髪の艶",
      "i1_shine_red_sheen" to "赤みを帯びた艶",
      "i1_shine_silver_sheen" to "銀色の艶",
      "i1_shine_white_glow" to "白く淡く光る髪",
      "i1_shine_subsurface" to "光が透けて内側が明るい髪",
    ),
  )

private fun optionGenre111(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪の質感・ツヤ",
    listOf(
    optionPart462(),
    optionPart463(),
    ),
  )

private fun optionPart464(): CharacterOptionPart =
  CharacterOptionPart(
    "髪に付けるもの",
    false,
    listOf(
      "i1_acc_big_scrunchie" to "大きなシュシュ",
      "i1_acc_velvet_scrunchie" to "ベルベットのシュシュ",
      "i1_acc_satin_ribbon" to "サテンのリボン",
      "i1_acc_lace_ribbon" to "レースのリボン",
      "i1_acc_pearl_pin" to "パールのヘアピン",
      "i1_acc_pearl_comb" to "パールのコーム",
      "i1_acc_flower_clip" to "花のヘアクリップ",
      "i1_acc_star_clip" to "星のヘアクリップ",
      "i1_acc_moon_clip" to "月のヘアクリップ",
      "i1_acc_heart_clip" to "ハートのヘアクリップ",
      "i1_acc_cat_clip" to "猫のヘアクリップ",
      "i1_acc_cherry_clip" to "さくらんぼのヘアピン",
      "i1_acc_snap_clip" to "パッチンピン",
      "i1_acc_big_barrette" to "大きめのバレッタ",
      "i1_acc_metal_barrette" to "金属のバレッタ",
      "i1_acc_bobby_pins" to "ヘアピンを多数並べる",
      "i1_acc_color_pins" to "色とりどりのヘアピン",
      "i1_acc_wire_headband" to "カチューシャ（細いもの）",
      "i1_acc_wide_headband" to "太めのカチューシャ",
      "i1_acc_knot_headband" to "ねじりカチューシャ",
      "i1_acc_pearl_headband" to "パールのカチューシャ",
      "i1_acc_crown" to "小さな王冠",
      "i1_acc_tikka" to "額飾り（ティカ）",
      "i1_acc_hair_jewelry" to "髪に絡めた宝石飾り",
      "i1_acc_feather_clip" to "羽根のヘアクリップ",
      "i1_acc_hair_sticks_two" to "ヘアスティック二本",
      "i1_acc_kanzashi_flower" to "花かんざし",
      "i1_acc_tsumami" to "つまみ細工の髪飾り",
      "i1_acc_hair_bell_ribbon" to "鈴付きリボン",
      "i1_acc_mizuhiki" to "水引の髪飾り",
      "i1_acc_hair_bow_big" to "大きなリボンカチューシャ",
      "i1_acc_bow_hair_clip" to "リボン付きバレッタ",
      "i1_acc_pom_pom" to "ぽんぽん付きゴム",
      "i1_acc_bead_hair_tie" to "ビーズのヘアゴム",
      "i1_acc_cord_hair_tie" to "ひもで結ぶ",
      "i1_acc_leather_tie" to "革ひもで結ぶ",
      "i1_acc_hair_wrap_cloth" to "布で巻いた束",
      "i1_acc_hat_hair" to "帽子の下からのぞく髪",
      "i1_acc_hair_hat_through" to "帽子の後ろの穴から出すポニー",
      "i1_acc_net" to "ヘアネット",
      "i1_acc_snood" to "スヌード風のまとめ",
      "i1_acc_flower_comb" to "花の飾りコーム",
    ),
  )

private fun optionGenre112(): CharacterOptionGenre =
  CharacterOptionGenre(
    "髪飾りと小物（追加）",
    listOf(
    optionPart464(),
    ),
  )

private fun optionPart465(): CharacterOptionPart =
  CharacterOptionPart(
    "ストリート・カジュアル系",
    false,
    listOf(
      "i2_urban_outdoor_fashion" to "アーバンアウトドア",
      "i2_cyberpunk_streetwear" to "サイバーパンク風ストリート",
      "i2_surfer_style" to "サーファー系",
      "i2_dancer_streetwear" to "ダンサー系ストリート",
      "i2_workwear_style" to "ワークウェア系",
      "i2_americana_casual" to "アメリカンカジュアル",
      "i2_trad_style" to "トラッド",
      "i2_british_trad" to "ブリティッシュトラッド",
      "i2_parisian_chic" to "パリジェンヌ風",
      "i2_korean_casual_fashion" to "韓国風カジュアル",
      "i2_taiwanese_girly_fashion" to "台湾風ガーリー",
      "i2_relaxed_silhouette_fashion" to "リラックスシルエット系",
      "i2_oversized_fashion" to "オーバーサイズ系",
      "i2_unisex_casual" to "ユニセックスカジュアル",
      "i2_genderless_fashion" to "ジェンダーレス系",
    ),
  )

private fun optionPart466(): CharacterOptionPart =
  CharacterOptionPart(
    "かわいい・フェミニン系",
    false,
    listOf(
      "i2_french_girly_style" to "フレンチガーリー",
      "i2_natural_style" to "ナチュラル系",
      "i2_yama_girl" to "山ガール",
      "i2_grandmacore" to "グラニーコア",
      "i2_dreamcore_fashion" to "ドリームコア風",
      "i2_kitsch_fashion" to "キッチュ系",
      "i2_pastel_goth" to "パステルゴス",
      "i2_ryousangata_style" to "量産型女子",
      "i2_subculture_girl_style" to "サブカル系",
      "i2_seiso_style" to "清楚系",
      "i2_ojou_sama_style" to "お嬢様系",
      "i2_hime_style" to "姫系",
      "i2_sweet_and_edgy_mix" to "甘辛ミックス",
      "i2_mature_cute_style" to "大人かわいい",
      "i2_conservative_style" to "コンサバ系",
      "i2_elegance_style" to "エレガンス",
      "i2_madame_style" to "マダム風",
      "i2_neat_casual" to "きれいめカジュアル",
      "i2_sporty_feminine" to "スポーティフェミニン",
    ),
  )

private fun optionPart467(): CharacterOptionPart =
  CharacterOptionPart(
    "モード・個性派系",
    false,
    listOf(
      "i2_deconstructed_fashion" to "デコンストラクション",
      "i2_all_black_mode_fashion" to "ブラックモード",
      "i2_androgynous_fashion" to "アンドロジナス",
      "i2_darkwear" to "ダークウェア",
      "i2_neo_gothic_fashion" to "ネオゴシック",
      "i2_victorian_goth" to "ヴィクトリアンゴス",
      "i2_vampire_goth" to "ヴァンパイアゴス",
      "i2_romantic_goth" to "ロマンチックゴス",
      "i2_elegant_gothic" to "エレガントゴシック",
      "i2_punk_rock_style" to "パンクロック系",
      "i2_soft_grunge" to "ソフトグランジ",
      "i2_rave_fashion" to "レイヴ系",
      "i2_kandi_rave_fashion" to "キャンディレイヴ",
      "i2_glam_rock_fashion" to "グラムロック",
      "i2_new_romantic_fashion" to "ニューロマンティック",
      "i2_post_apocalyptic_fashion" to "ポストアポカリプス風",
      "i2_wasteland_fashion" to "ウェイストランド風",
      "i2_nomad_style" to "ノマド風",
      "i2_military_style" to "ミリタリー系",
      "i2_safari_look" to "サファリルック",
      "i2_desert_look" to "デザートルック",
      "i2_neo_classical_fashion" to "ネオクラシック",
      "i2_draped_mode_fashion" to "ドレープモード",
    ),
  )

private fun optionPart468(): CharacterOptionPart =
  CharacterOptionPart(
    "ギャル・日本の流行系",
    false,
    listOf(
      "i2_amuro_style_fashion" to "アムラー風",
      "i2_akamoji_style" to "赤文字系",
      "i2_aomoji_style" to "青文字系",
      "i2_shibu_casual" to "渋カジ",
      "i2_harajuku_fashion" to "原宿系",
      "i2_b_kei_style" to "B系",
      "i2_akiba_style" to "アキバ系",
      "i2_marine_style" to "マリン系",
      "i2_college_style" to "カレッジ系",
      "i2_clean_style" to "きれいめ系",
      "i2_motekei_style" to "モテ系",
      "i2_one_mile_wear" to "ワンマイルウェア",
    ),
  )

private fun optionGenre113(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ファッション系統",
    listOf(
    optionPart465(),
    optionPart466(),
    optionPart467(),
    optionPart468(),
    ),
  )

private fun optionPart469(): CharacterOptionPart =
  CharacterOptionPart(
    "日本の伝統衣装",
    false,
    listOf(
      "i2_chu_furisode" to "中振袖",
      "i2_kuro_tomesode" to "黒留袖",
      "i2_iro_tomesode" to "色留袖",
      "i2_tsukesage" to "付け下げ",
      "i2_edo_komon" to "江戸小紋",
      "i2_tsumugi_kimono" to "紬",
      "i2_samue" to "作務衣",
      "i2_andon_hakama" to "行灯袴",
      "i2_showa_retro_kimono" to "昭和レトロ着物",
      "i2_modern_kimono" to "モダン着物",
      "i2_antique_kimono" to "アンティーク着物",
      "i2_kimono_remake" to "着物アレンジ",
      "i2_denim_kimono" to "デニム着物",
      "i2_hakama_and_boots" to "袴ブーツスタイル",
      "i2_nishaku_sleeve_kimono" to "二尺袖",
      "i2_ko_uchigi" to "小袿",
      "i2_uchiki" to "袿",
      "i2_karaginu_mo" to "唐衣裳",
      "i2_hitatare" to "直垂",
      "i2_kariginu" to "狩衣",
      "i2_suikan" to "水干",
      "i2_jinbaori" to "陣羽織",
      "i2_chanchanko" to "ちゃんちゃんこ",
      "i2_shiromuku" to "白無垢",
      "i2_iro_uchikake" to "色打掛",
      "i2_tsunokakushi" to "角隠し",
      "i2_watab_shi" to "綿帽子",
      "i2_oiran_costume" to "花魁衣装",
      "i2_geiko_costume" to "芸妓衣装",
      "i2_maiko_costume" to "舞妓衣装",
      "i2_shinto_priest_costume" to "神職装束",
      "i2_yamabushi_costume" to "山伏装束",
      "i2_buddhist_monk_robe" to "僧衣",
      "i2_setta" to "雪駄",
    ),
  )

private fun optionPart470(): CharacterOptionPart =
  CharacterOptionPart(
    "着物の着こなし・小物",
    false,
    listOf(
      "i2_nukiemon" to "抜き衣紋",
      "i2_bunko_musubi" to "文庫結び",
      "i2_butterfly_obi_knot" to "蝶結びの帯",
      "i2_kai_no_kuchi_knot" to "貝の口",
      "i2_nagoya_obi" to "名古屋帯",
      "i2_fukuro_obi" to "袋帯",
      "i2_hanhaba_obi" to "半幅帯",
      "i2_heko_obi" to "兵児帯",
      "i2_obiage" to "帯揚げ",
      "i2_obidome" to "帯留め",
      "i2_kasane_eri" to "重ね襟",
      "i2_han_eri" to "半襟",
      "i2_maekake" to "前掛け",
      "i2_kanzashi_with_kimono" to "かんざし付き和装",
      "i2_holding_wagasa" to "和傘持ち",
    ),
  )

private fun optionPart471(): CharacterOptionPart =
  CharacterOptionPart(
    "中国・東アジア",
    false,
    listOf(
      "i2_ruqun" to "襦裙",
      "i2_quju" to "曲裾",
      "i2_qixiong_ruqun" to "斉胸襦裙",
      "i2_qipao" to "旗袍",
      "i2_zhongshan_suit" to "中山服",
      "i2_mamianqun" to "馬面裙",
      "i2_pibo" to "披帛",
      "i2_daopao" to "道袍",
      "i2_chima_jeogori" to "チマチョゴリ",
      "i2_paji_jeogori" to "パジチョゴリ",
      "i2_tibetan_chuba" to "チベット衣装",
      "i2_miao_costume" to "ミャオ族衣装",
      "i2_silver_ornament_costume" to "銀飾り衣装",
    ),
  )

private fun optionGenre114(): CharacterOptionGenre =
  CharacterOptionGenre(
    "和装・民族衣装（追加）",
    listOf(
    optionPart469(),
    optionPart470(),
    optionPart471(),
    ),
  )

private fun optionPart472(): CharacterOptionPart =
  CharacterOptionPart(
    "東南アジア",
    false,
    listOf(
      "i2_sabai" to "サバイ",
      "i2_batik" to "バティック",
      "i2_terno_dress" to "テルノ",
      "i2_baro_t_saya" to "バロット・サヤ",
      "i2_baju_kurung" to "バジュクロン",
      "i2_cham_costume" to "チャム族衣装",
    ),
  )

private fun optionPart473(): CharacterOptionPart =
  CharacterOptionPart(
    "南アジア・中央アジア",
    false,
    listOf(
      "i2_anarkali" to "アナルカリ",
      "i2_sherwani" to "シェルワニ",
      "i2_lungi" to "ルンギ",
      "i2_pashmina_shawl" to "パシュミナショール",
      "i2_bhutan_gho_kira" to "ブータンのゴとキラ",
      "i2_kira" to "キラ",
      "i2_gho" to "ゴ",
      "i2_nepali_daura_suruwal" to "ネパールのダウラスルワル",
      "i2_ghagra" to "ガグラ",
      "i2_uzbek_costume" to "ウズベク衣装",
      "i2_tubeteika" to "トゥビテイカ",
      "i2_kazakh_costume" to "カザフ民族衣装",
      "i2_pagri" to "パグリ",
    ),
  )

private fun optionPart474(): CharacterOptionPart =
  CharacterOptionPart(
    "中東・アフリカ",
    false,
    listOf(
      "i2_djellaba" to "ジェラバ",
      "i2_galabeya" to "ガラベーヤ",
      "i2_african_print_dress" to "アフリカンプリントドレス",
      "i2_berber_dress" to "ベルベル衣装",
      "i2_tuareg_tagelmust" to "トゥアレグ衣装",
      "i2_zulu_attire" to "ズールー衣装",
      "i2_himba_style" to "ヒンバ族風",
    ),
  )

private fun optionPart475(): CharacterOptionPart =
  CharacterOptionPart(
    "ヨーロッパ",
    false,
    listOf(
      "i2_lederhosen" to "レーダーホーゼン",
      "i2_scottish_kilt" to "スコットランドキルト",
      "i2_gakti" to "サーミのガクティ",
      "i2_romanian_folk_blouse" to "ルーマニア刺繍ブラウス",
      "i2_bulgarian_folk_costume" to "ブルガリア民族衣装",
      "i2_bata_de_cola" to "バタ・デ・コーラ",
      "i2_matador_costume" to "闘牛士衣装",
      "i2_portuguese_folk_costume" to "ポルトガル民族衣装",
      "i2_breton_costume" to "ブルターニュ衣装",
      "i2_alsace_costume" to "アルザス衣装",
      "i2_swiss_folk_costume" to "スイス民族衣装",
    ),
  )

private fun optionPart476(): CharacterOptionPart =
  CharacterOptionPart(
    "アメリカ・オセアニア",
    false,
    listOf(
      "i2_charro_suit" to "チャロ衣装",
      "i2_gaucho_attire" to "ガウチョ衣装",
      "i2_native_american_style" to "ネイティブアメリカン風",
      "i2_fringed_buckskin_outfit" to "フリンジ付きスエード衣装",
      "i2_cowboy_style" to "カウボーイスタイル",
      "i2_cowgirl_style" to "カウガール風",
      "i2_hawaiian_attire" to "ハワイアン衣装",
      "i2_pa_u_skirt" to "パウスカート",
      "i2_hula_costume" to "フラダンス衣装",
      "i2_tahitian_costume" to "タヒチアン衣装",
      "i2_maori_attire" to "マオリ衣装",
      "i2_samoan_attire" to "サモア衣装",
      "i2_lavalava" to "ラバラバ",
    ),
  )

private fun optionGenre115(): CharacterOptionGenre =
  CharacterOptionGenre(
    "各国の伝統衣装（追加）",
    listOf(
    optionPart472(),
    optionPart473(),
    optionPart474(),
    optionPart475(),
    optionPart476(),
    ),
  )

private fun optionPart477(): CharacterOptionPart =
  CharacterOptionPart(
    "ビキニ・ワンピース水着",
    false,
    listOf(
      "i2_high_neck_bikini" to "ハイネックビキニ",
      "i2_ribbon_bikini" to "リボンビキニ",
      "i2_one_shoulder_swimsuit" to "ワンショルダー水着",
      "i2_rash_guard_swimsuit" to "ラッシュガード水着",
      "i2_surf_suit" to "サーフスーツ",
      "i2_sports_swimsuit" to "スポーツ水着",
      "i2_see_through_swimsuit" to "シースルー水着",
      "i2_lace_swimsuit" to "レース水着",
      "i2_velvet_swimsuit" to "ベルベット水着",
      "i2_glossy_swimsuit" to "エナメル水着",
      "i2_1950s_style_swimsuit" to "50年代風水着",
    ),
  )

private fun optionPart478(): CharacterOptionPart =
  CharacterOptionPart(
    "リゾート・ビーチ",
    false,
    listOf(
      "i2_beach_robe" to "ビーチローブ",
      "i2_beach_dress" to "ビーチワンピース",
      "i2_crochet_dress" to "クロシェワンピース",
      "i2_beach_pants" to "ビーチパンツ",
      "i2_beach_shirt" to "ビーチシャツ",
      "i2_wide_brim_hat" to "つば広ハット",
      "i2_beach_bag" to "ビーチバッグ",
      "i2_resort_dress" to "リゾートワンピース",
      "i2_boho_resort_wear" to "ボヘミアンリゾート",
      "i2_yachting_wear" to "ヨットウェア",
      "i2_hawaiian_resort_wear" to "ハワイアンリゾート",
      "i2_poolside_gown" to "プールサイドガウン",
    ),
  )

private fun optionGenre116(): CharacterOptionGenre =
  CharacterOptionGenre(
    "水着・リゾート（追加）",
    listOf(
    optionPart477(),
    optionPart478(),
    ),
  )

private fun optionPart479(): CharacterOptionPart =
  CharacterOptionPart(
    "ランジェリー",
    false,
    listOf(
      "i2_longline_corset" to "ロングコルセット",
      "i2_side_tie_panties" to "サイドリボンショーツ",
      "i2_satin_lingerie" to "サテンランジェリー",
    ),
  )

private fun optionPart480(): CharacterOptionPart =
  CharacterOptionPart(
    "部屋着",
    false,
    listOf(
      "i2_satin_pajamas" to "サテンパジャマ",
      "i2_silk_pajamas" to "シルクパジャマ",
      "i2_button_up_pajamas" to "ボタンダウンパジャマ",
      "i2_short_pajamas" to "ショートパジャマ",
      "i2_sweat_set" to "スウェットセットアップ",
      "i2_lounge_set" to "ラウンジセット",
      "i2_fluffy_loungewear" to "もこもこルームウェア",
      "i2_fleece_loungewear" to "フリースルームウェア",
      "i2_dressing_gown" to "ガウン",
      "i2_wearable_blanket" to "着る毛布",
      "i2_oversized_shirt_only" to "オーバーサイズシャツ着用",
    ),
  )

private fun optionGenre117(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ランジェリー・部屋着（追加）",
    listOf(
    optionPart479(),
    optionPart480(),
    ),
  )

private fun optionPart481(): CharacterOptionPart =
  CharacterOptionPart(
    "ブーツ",
    false,
    listOf(
      "i2_side_zip_boots" to "サイドジップブーツ",
      "i2_heeled_boots" to "ヒールブーツ",
      "i2_jodhpur_boots" to "ジョッパーブーツ",
      "i2_victorian_boots" to "ヴィクトリアンブーツ",
      "i2_steampunk_boots" to "スチームパンクブーツ",
      "i2_boot_covers" to "ブーツカバー",
    ),
  )

private fun optionPart482(): CharacterOptionPart =
  CharacterOptionPart(
    "パンプス・フラット",
    false,
    listOf(
      "i2_round_toe_pumps" to "ラウンドトゥパンプス",
      "i2_open_toe_pumps" to "オープントゥパンプス",
      "i2_strappy_pumps" to "ストラップパンプス",
      "i2_ankle_strap_pumps" to "アンクルストラップパンプス",
      "i2_t_strap_shoes" to "Tストラップシューズ",
      "i2_platform_mary_janes" to "厚底メリージェーン",
      "i2_tassel_loafers" to "タッセルローファー",
      "i2_horsebit_loafers" to "ビットローファー",
      "i2_penny_loafers" to "コインローファー",
      "i2_brogue_shoes" to "ブローグシューズ",
      "i2_driving_shoes" to "ドライビングシューズ",
      "i2_chunky_heels" to "チャンキーヒール",
      "i2_comfort_pumps" to "コンフォートパンプス",
      "i2_patent_pumps" to "エナメルパンプス",
      "i2_satin_pumps" to "サテンパンプス",
      "i2_velvet_pumps" to "ベルベットパンプス",
      "i2_sequin_pumps" to "スパンコールパンプス",
    ),
  )

private fun optionPart483(): CharacterOptionPart =
  CharacterOptionPart(
    "サンダル・スニーカー",
    false,
    listOf(
      "i2_flip_flops" to "ビーチサンダル型",
      "i2_footbed_sandals" to "フットベッドサンダル",
      "i2_heeled_mules" to "ヒールミュール",
      "i2_huaraches" to "ハラチェ",
      "i2_heeled_sneakers" to "ヒール付きスニーカー",
      "i2_skate_shoes" to "スケートシューズ",
      "i2_leather_sneakers" to "レザースニーカー",
      "i2_slip_on_sneakers" to "スリッポンスニーカー",
      "i2_ice_skates" to "アイススケート靴",
      "i2_jazz_shoes" to "ジャズシューズ",
      "i2_sock_sneakers" to "ソックスシューズ",
      "i2_tabi_sneakers" to "足袋スニーカー",
    ),
  )

private fun optionPart484(): CharacterOptionPart =
  CharacterOptionPart(
    "靴下・タイツ",
    false,
    listOf(
      "i2_argyle_socks" to "アーガイルソックス",
      "i2_striped_thigh_highs" to "ストライプニーハイ",
      "i2_garter_socks" to "ガーターソックス",
      "i2_single_thigh_high" to "片方だけニーソ",
      "i2_wool_tights" to "ウールタイツ",
      "i2_ribbed_tights" to "リブタイツ",
      "i2_sheer_tights" to "透けタイツ",
      "i2_open_toe_tights" to "オープントゥタイツ",
      "i2_glitter_stockings" to "ラメストッキング",
    ),
  )

private fun optionGenre118(): CharacterOptionGenre =
  CharacterOptionGenre(
    "靴・足元（追加）",
    listOf(
    optionPart481(),
    optionPart482(),
    optionPart483(),
    optionPart484(),
    ),
  )

private fun optionPart485(): CharacterOptionPart =
  CharacterOptionPart(
    "織りと素材",
    false,
    listOf(
      "i2_satin_fabric" to "サテン生地",
      "i2_silk_fabric" to "シルク生地",
      "i2_velvet_fabric" to "ベルベット生地",
      "i2_velour_fabric" to "ベロア生地",
      "i2_chiffon_fabric" to "シフォン生地",
      "i2_organza_fabric" to "オーガンジー生地",
      "i2_tulle_fabric" to "チュール生地",
      "i2_lace_fabric" to "レース生地",
      "i2_georgette" to "ジョーゼット",
      "i2_tweed_fabric" to "ツイード生地",
      "i2_wool_fabric" to "ウール生地",
      "i2_flannel_fabric" to "フランネル生地",
      "i2_corduroy_fabric" to "コーデュロイ生地",
      "i2_denim_fabric" to "デニム生地",
      "i2_gauze_fabric" to "ガーゼ生地",
      "i2_crinkle_fabric" to "楊柳生地",
      "i2_broadcloth_fabric" to "ブロード生地",
      "i2_gingham_fabric" to "ギンガム生地",
      "i2_jacquard_fabric" to "ジャカード織",
      "i2_muslin" to "モスリン",
      "i2_suede_fabric" to "スエード生地",
      "i2_leather_fabric" to "レザー生地",
      "i2_patent_leather_fabric" to "エナメル生地",
      "i2_rubber_fabric" to "ラバー生地",
      "i2_latex_fabric" to "ラテックス生地",
      "i2_pvc_fabric" to "PVC生地",
      "i2_vinyl_fabric" to "ビニール生地",
      "i2_sweat_fabric" to "スウェット生地",
      "i2_waffle_fabric" to "ワッフル生地",
      "i2_fleece_fabric" to "フリース生地",
      "i2_sherpa_fabric" to "ボア生地",
      "i2_fur_fabric" to "ファー生地",
      "i2_mohair_fabric" to "モヘア生地",
      "i2_angora_fabric" to "アンゴラ生地",
      "i2_cashmere_fabric" to "カシミア生地",
      "i2_quilted_fabric" to "キルティング生地",
      "i2_nylon_fabric" to "ナイロン生地",
      "i2_power_mesh" to "パワーネット",
      "i2_holographic_fabric" to "ホログラム生地",
      "i2_neon_fabric" to "蛍光生地",
      "i2_reflective_fabric" to "リフレクター素材",
      "i2_washi_fabric" to "和紙生地",
      "i2_rinzu" to "綸子",
      "i2_habutae" to "羽二重",
    ),
  )

private fun optionPart486(): CharacterOptionPart =
  CharacterOptionPart(
    "柄・模様",
    false,
    listOf(
      "i2_striped_pattern" to "ストライプ柄",
      "i2_hickory_stripe" to "ヒッコリーストライプ",
      "i2_multicolor_stripes" to "マルチボーダー",
      "i2_gingham_check" to "ギンガムチェック",
      "i2_plaid_check" to "プレイド柄",
      "i2_windowpane_check" to "ウィンドウペーン",
      "i2_buffalo_check" to "バッファローチェック",
      "i2_argyle_pattern" to "アーガイル柄",
      "i2_paisley_pattern" to "ペイズリー柄",
      "i2_polka_dot_pattern" to "ドット柄",
      "i2_diamond_pattern" to "ダイヤ柄",
      "i2_quilt_pattern" to "キルト柄",
      "i2_karakusa_pattern" to "唐草模様",
      "i2_asa_no_ha_pattern" to "麻の葉柄",
      "i2_yagasuri_pattern" to "矢絣",
      "i2_kikko_pattern" to "亀甲柄",
      "i2_tatewaku_pattern" to "立涌",
      "i2_shochikubai_pattern" to "松竹梅柄",
      "i2_phoenix_pattern" to "鳳凰柄",
      "i2_goldfish_pattern" to "金魚柄",
      "i2_fireworks_pattern" to "花火柄",
      "i2_rabbit_print" to "うさぎ柄",
      "i2_cat_print" to "猫柄",
      "i2_snakeskin_print" to "スネーク柄",
      "i2_camouflage_pattern" to "カモフラージュ柄",
      "i2_dazzle_camouflage" to "ダズル迷彩",
      "i2_tie_dye_pattern" to "タイダイ柄",
      "i2_ombre_dye" to "グラデーション染め",
      "i2_sarasa_print" to "更紗",
      "i2_batik_pattern" to "バティック柄",
      "i2_ikat_pattern" to "イカット柄",
      "i2_celtic_pattern" to "ケルト柄",
      "i2_nordic_pattern" to "ノルディック柄",
      "i2_fair_isle_pattern" to "フェアアイル柄",
      "i2_geometric_pattern" to "ジオメトリック柄",
      "i2_botanical_print" to "ボタニカル柄",
      "i2_ditsy_floral_print" to "小花柄",
      "i2_large_floral_print" to "大花柄",
      "i2_palm_tree_print" to "ヤシの木柄",
      "i2_nautical_print" to "マリン柄",
      "i2_anchor_print" to "アンカー柄",
      "i2_playing_card_print" to "トランプ柄",
      "i2_chess_check" to "チェス柄",
      "i2_bandana_print" to "バンダナ柄",
      "i2_monogram_pattern" to "モノグラム総柄",
      "i2_cyber_pattern" to "サイバー柄",
      "i2_glitch_pattern" to "グリッチ柄",
      "i2_psychedelic_print" to "サイケデリック柄",
      "i2_op_art_pattern" to "オプアート柄",
      "i2_art_deco_pattern" to "アールデコ柄",
      "i2_art_nouveau_pattern" to "アールヌーボー柄",
      "i2_arts_and_crafts_floral_pattern" to "アーツアンドクラフツ風花柄",
      "i2_rococo_pattern" to "ロココ柄",
      "i2_baroque_pattern" to "バロック柄",
    ),
  )

private fun optionPart487(): CharacterOptionPart =
  CharacterOptionPart(
    "装飾",
    false,
    listOf(
      "i2_beaded_embroidery" to "ビーズ刺繍",
      "i2_sequin_embellishment" to "スパンコール装飾",
      "i2_pearl_embellishment" to "パール装飾",
      "i2_tassel" to "タッセル",
      "i2_pom_pom" to "ポンポン",
      "i2_ribbon_trim" to "リボン飾り",
      "i2_piping_trim" to "パイピング",
      "i2_gathers" to "ギャザー",
      "i2_pintucks" to "ピンタック",
      "i2_smocking" to "スモッキング",
      "i2_shirring" to "シャーリング",
      "i2_applique" to "アップリケ",
      "i2_zipper_detail" to "ファスナー飾り",
      "i2_many_belts" to "ベルト多用",
      "i2_many_straps" to "ストラップ多用",
      "i2_buckle_detail" to "バックル飾り",
      "i2_eyelets" to "ハトメ",
      "i2_lace_up_detail" to "レースアップ",
      "i2_drape" to "ドレープ",
      "i2_grommets" to "グロメット",
    ),
  )

private fun optionGenre119(): CharacterOptionGenre =
  CharacterOptionGenre(
    "素材と柄",
    listOf(
    optionPart485(),
    optionPart486(),
    optionPart487(),
    ),
  )

private fun optionPart488(): CharacterOptionPart =
  CharacterOptionPart(
    "20世紀前半",
    false,
    listOf(
      "i2_edwardian_fashion" to "エドワーディアン風",
      "i2_s_bend_silhouette" to "S字シルエット",
      "i2_gibson_girl_fashion" to "ギブソンガール風",
      "i2_1910s_fashion" to "1910年代風",
      "i2_1920s_drop_waist_style" to "ドロップウエストの20年代",
      "i2_garconne_style" to "ガルソンヌ",
      "i2_art_deco_fashion" to "アールデコ風",
      "i2_bias_cut_dress" to "バイアスカットドレス",
      "i2_wartime_utility_style" to "戦時中風ユーティリティ",
      "i2_victory_roll_style" to "ヴィクトリーロール風",
    ),
  )

private fun optionPart489(): CharacterOptionPart =
  CharacterOptionPart(
    "20世紀後半",
    false,
    listOf(
      "i2_new_look" to "ニュールック",
      "i2_rockabilly_fashion" to "ロカビリー",
      "i2_1950s_circle_dress" to "サーキュラードレス50s",
      "i2_ponytail_scarf_style" to "ポニーテールスカーフ",
      "i2_mod_fashion" to "モッズファッション",
      "i2_1960s_mini_skirt" to "ミニスカート60s",
      "i2_flower_power_fashion" to "フラワーパワー",
      "i2_hippie_flares" to "ヒッピーフレア",
      "i2_disco_fashion" to "ディスコファッション",
      "i2_1970s_flared_jeans" to "フレアジーンズ70s",
      "i2_1970s_safari_suit" to "サファリスーツ70s",
      "i2_1970s_western_style" to "ウエスタン70s",
      "i2_1970s_boho_style" to "ボヘミアン70s",
      "i2_1970s_punk_style" to "パンク70s",
      "i2_new_wave_fashion" to "ニューウェイブ",
      "i2_1980s_shoulder_pads" to "肩パッド80s",
      "i2_1980s_bodycon" to "ボディコン80s",
      "i2_power_dressing" to "パワードレッシング",
      "i2_1980s_neon_fashion" to "ネオン80s",
      "i2_aerobics_wear" to "エアロビクスウェア",
      "i2_1980s_leg_warmers" to "レッグウォーマー80s",
      "i2_bubble_era_japan_fashion" to "バブリー",
      "i2_1990s_grunge" to "グランジ90s",
      "i2_1990s_minimalism" to "ミニマリズム90s",
      "i2_1990s_hip_hop" to "ヒップホップ90s",
      "i2_1990s_rave" to "レイヴ90s",
      "i2_1990s_streetwear" to "ストリート90s",
      "i2_1990s_tracksuit" to "トラックスーツ90s",
      "i2_1990s_slip_dress" to "スライドドレス90s",
    ),
  )

private fun optionPart490(): CharacterOptionPart =
  CharacterOptionPart(
    "2000年代以降",
    false,
    listOf(
      "i2_y2k_outfit" to "Y2Kドレスアップ",
      "i2_2000s_low_rise" to "ローライズ2000s",
      "i2_trucker_hat" to "トラッカーハット",
      "i2_velour_tracksuit" to "ベロアセットアップ",
      "i2_bling_fashion" to "ブリンブリン",
      "i2_2010s_fashion" to "2010年代風",
      "i2_2010s_athleisure" to "アスレジャー2010s",
      "i2_2020s_fashion" to "2020年代風",
      "i2_retro_futurism_fashion" to "レトロフューチャー",
      "i2_futuristic_fashion" to "未来的ファッション",
      "i2_space_suit_style" to "宇宙服風",
      "i2_neo_retro_fashion" to "ネオレトロ",
      "i2_80s_revival" to "80年代リバイバル",
    ),
  )

private fun optionPart491(): CharacterOptionPart =
  CharacterOptionPart(
    "時代の衣装",
    false,
    listOf(
      "i2_ancient_greek_dress" to "古代ギリシャ風",
      "i2_chiton" to "キトン",
      "i2_toga" to "トガ",
      "i2_ancient_roman_dress" to "古代ローマ風",
      "i2_stola" to "ストラ",
      "i2_ancient_egyptian_dress" to "古代エジプト風",
      "i2_kalasiris" to "カラシリス",
      "i2_byzantine_dress" to "ビザンチン風",
      "i2_medieval_european_dress" to "中世ヨーロッパ風",
      "i2_bliaut" to "ブリオー",
      "i2_cotehardie" to "コタルディ",
      "i2_farthingale_dress" to "ファージンゲールドレス",
      "i2_baroque_dress" to "バロック風ドレス",
      "i2_robe_a_la_francaise" to "ローブ・ア・ラ・フランセーズ",
      "i2_panier_dress" to "パニエドレス",
      "i2_regency_fashion" to "リージェンシー風",
      "i2_crinoline_dress" to "クリノリンドレス",
      "i2_victorian_maid_style" to "ヴィクトリアンメイド風",
      "i2_victorian_mourning_dress" to "ヴィクトリアン喪服",
    ),
  )

private fun optionGenre120(): CharacterOptionGenre =
  CharacterOptionGenre(
    "年代別ファッション（追加）",
    listOf(
    optionPart488(),
    optionPart489(),
    optionPart490(),
    optionPart491(),
    ),
  )

private fun optionPart492(): CharacterOptionPart =
  CharacterOptionPart(
    "作業着",
    false,
    listOf(
      "i2_boiler_suit" to "ツナギ作業着",
      "i2_bib_apron" to "ロングエプロン",
      "i2_bistro_apron" to "サロンエプロン",
      "i2_gardening_wear" to "ガーデニングウェア",
      "i2_farm_work_wear" to "農作業着",
      "i2_monpe" to "もんぺ",
      "i2_kasuri_work_clothes" to "かすり作業着",
      "i2_tekko_kyahan" to "手甲脚絆",
      "i2_jika_tabi" to "地下足袋",
      "i2_work_gloves" to "軍手",
      "i2_welder_outfit" to "溶接工風",
      "i2_welding_mask" to "溶接マスク",
      "i2_safety_vest" to "安全ベスト",
      "i2_hi_vis_vest" to "反射ベスト",
      "i2_tool_belt" to "ツールベルト",
      "i2_garage_coveralls" to "ガレージツナギ",
      "i2_mechanic_coveralls" to "整備士つなぎ",
      "i2_carpenter_outfit" to "大工風",
      "i2_carpenter_apron" to "大工エプロン",
      "i2_blacksmith_apron" to "鍛冶屋風",
      "i2_potter_apron" to "陶芸家風",
      "i2_artist_smock" to "画家のスモック",
      "i2_baker_outfit" to "パン職人風",
      "i2_chef_coat" to "コックコート",
      "i2_barista_outfit" to "バリスタ風",
      "i2_fisherman_outfit" to "漁師風",
      "i2_oilskin_fisherman_outfit" to "オイルスキン漁師服",
      "i2_angler_outfit" to "釣り師風",
      "i2_mountaineer_outfit" to "登山者風",
      "i2_explorer_outfit" to "探検家風",
      "i2_treasure_hunter_outfit" to "トレジャーハンター風",
      "i2_aviator_outfit" to "パイロット風",
      "i2_aviator_goggles" to "飛行士ゴーグル",
      "i2_photographer_outfit" to "カメラマン風",
      "i2_traveler_outfit" to "旅人風",
      "i2_winter_mountaineering_gear" to "冬山防寒着",
      "i2_rancher_outfit" to "牧場主風",
      "i2_cowboy_work_wear" to "カウボーイ作業着",
    ),
  )

private fun optionGenre121(): CharacterOptionGenre =
  CharacterOptionGenre(
    "職業・制服ではない作業着（追加）",
    listOf(
    optionPart492(),
    ),
  )

private fun optionPart493(): CharacterOptionPart =
  CharacterOptionPart(
    "ヒロイン戦闘服",
    false,
    listOf(
      "i3_skin_tight_combat_suit" to "ボディライン重視の戦闘スーツ",
      "i3_light_battle_outfit" to "肩当てつきの軽装戦闘服",
      "i3_battle_leotard" to "腰布つきのバトルレオタード",
      "i3_high_leg_bodysuit" to "ハイカットのバトルボディスーツ",
      "i3_glowing_lines_bodysuit" to "光のラインが走る戦闘スーツ",
      "i3_long_coat_battle_outfit" to "ロングコートつき戦闘服",
      "i3_asymmetrical_sleeve" to "片袖だけのアシンメトリー戦闘服",
      "i3_tactical_vest" to "ベルトまみれの戦術ベスト",
      "i3_hooded_battle_cape" to "フード付きの戦闘ケープ",
      "i3_battle_dress" to "ひらひらスカートの戦闘ドレス",
      "i3_long_skirt_with_slit" to "スリット入りの戦闘ロングスカート",
      "i3_bikini_armor" to "金属プレート付きのビキニアーマー風",
      "i3_mini_skirt_knight_armor" to "ミニスカートの騎士風軽鎧",
      "i3_breastplate" to "胸当てと腰当てだけの軽装鎧",
      "i3_ceremonial_armor" to "装飾の多い儀礼用鎧",
      "i3_white_and_gold_armor" to "白と金の聖騎士風鎧",
      "i3_black_and_red_armor" to "黒と赤の暗黒騎士風鎧",
      "i3_full_plate_armor" to "フルプレートの女性騎士鎧",
      "i3_winged_pauldrons" to "翼のような肩当てを持つ鎧",
      "i3_hero_outfit" to "マントつきの勇者風衣装",
      "i3_leather_armor" to "旅の戦士風の革鎧",
      "i3_poncho_adventurer_outfit" to "ポンチョ型の冒険者衣装",
      "i3_archer_outfit" to "弓使い風の緑のフード衣装",
      "i3_musketeer_coat" to "銃士風の長いコートとブーツ",
      "i3_mercenary_gear" to "傭兵風の肩装甲と革ベルト",
      "i3_gladiator_outfit" to "闘技場の剣闘士風衣装",
      "i3_amazon_warrior_outfit" to "アマゾネス風の毛皮と革の衣装",
      "i3_barbarian_fur_bikini" to "蛮族風の毛皮ビキニ",
      "i3_hunter_outfit" to "狩人風のマントと革ベルト",
      "i3_gauntlets" to "ガントレットとグリーブの格闘家衣装",
      "i3_kung_fu_outfit" to "カンフー風の中華格闘服",
      "i3_gi_remix" to "空手着風の道着アレンジ",
      "i3_heavy_shoulder_armor" to "重戦車のような肩装甲の衣装",
      "i3_dual_wielder_outfit" to "双剣士風の黒い衣装",
      "i3_spearman_outfit" to "槍使い風の軽装",
      "i3_magic_gunner_coat" to "魔導銃士風のコート",
      "i3_gun_belt" to "ガンベルト付きの女ガンマン衣装",
      "i3_assassin_hood" to "アサシン風のフードと短剣ベルト",
      "i3_ninja_battle_outfit" to "忍装束風のバトルアレンジ",
      "i3_tactical_gear" to "特殊部隊風のタクティカルギア",
      "i3_pilot_suit" to "パイロットスーツ風の戦闘服",
      "i3_valkyrie_armor" to "戦乙女風の翼つき鎧",
      "i3_empress_war_outfit" to "女帝風の豪華な戦装束",
      "i3_flame_pattern_battle_outfit" to "赤い炎模様の戦闘衣装",
      "i3_ice_crystal_battle_outfit" to "氷の結晶飾りの戦闘衣装",
      "i3_lightning_pattern_battle_outfit" to "雷模様の戦闘衣装",
      "i3_wind_feather_battle_outfit" to "風をまとう羽根飾りの戦闘衣装",
      "i3_earth_tone_battle_outfit" to "大地の色の岩飾り戦闘衣装",
      "i3_boxing_robe" to "ボクサー風のガウンとグローブ",
    ),
  )

private fun optionGenre122(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・戦う衣装",
    listOf(
    optionPart493(),
    ),
  )

private fun optionPart494(): CharacterOptionPart =
  CharacterOptionPart(
    "変身ヒロイン風",
    false,
    listOf(
      "i3_transformation_heroine_dress" to "ひらひらの変身ヒロイン風ドレス",
      "i3_ribbon_covered_costume" to "リボンだらけの変身コスチューム",
      "i3_heart_brooch" to "ハートのブローチつき変身衣装",
      "i3_star_motif_costume" to "星のモチーフの変身衣装",
      "i3_crescent_moon_motif_costume" to "三日月のモチーフの変身衣装",
      "i3_flower_motif_costume" to "花のモチーフの変身衣装",
      "i3_magical_wand" to "魔法のステッキに合うミニドレス",
      "i3_large_back_bow" to "大きなリボンの背中飾りがついた衣装",
      "i3_tiered_frills" to "段フリルのパフスリーブ衣装",
      "i3_pink_transformation_outfit" to "ピンク基調の甘い変身衣装",
      "i3_light_blue_transformation_outfit" to "水色基調のさわやかな変身衣装",
      "i3_gold_trim_transformation_outfit" to "金色の縁取りの華やかな変身衣装",
      "i3_gradient_skirt" to "グラデーションのスカートの変身衣装",
      "i3_twin_tails" to "ツインテールに合わせた変身ヒロイン風衣装",
      "i3_mini_skirt" to "ミニスカートとニーハイの変身衣装",
      "i3_choker" to "チョーカーとリボンのセット",
      "i3_mascot_companion" to "ぬいぐるみマスコットを連れた衣装",
      "i3_transformation_belt" to "変身アイテムの小物ベルト",
      "i3_sentai_hero_suit" to "戦隊ヒロイン風のヒーロースーツ",
      "i3_tokusatsu_hero_suit" to "特撮ヒーロー風のメタリックスーツ",
      "i3_hero_mask" to "ヒーローマスクつき衣装",
      "i3_hero_cape" to "ヒーロー風のマントとブーツ",
      "i3_masked_hero_outfit" to "覆面ヒーロー風の衣装",
      "i3_dark_heroine_outfit" to "正義のダークヒロイン風衣装",
      "i3_phantom_thief_outfit" to "白いマントの怪盗風衣装",
      "i3_phantom_thief" to "怪盗風の黒いシルクハット衣装",
      "i3_witch_hat" to "魔法使いの帽子とマントの変身衣装",
      "i3_metallic_gloss_outfit" to "メタリックな光沢の変身衣装",
      "i3_glass_like_sparkling_dress" to "ガラスのようなきらめきのドレス",
      "i3_iridescent_outfit" to "オーロラ色のきらめく変身衣装",
      "i3_star_dust_skirt" to "星屑をまとったようなスカート",
      "i3_armillary_sphere_ornament" to "天球儀飾りの魔法衣装",
      "i3_angel_feather_transformation_outfit" to "羽つきの天使風変身衣装",
      "i3_wing_like_ribbon" to "翼のようなリボンがついた衣装",
      "i3_large_hair_ornament" to "髪飾りが大きい変身ヒロイン風衣装",
      "i3_frilled_gloves" to "フリルグローブとロングブーツ",
    ),
  )

private fun optionGenre123(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・変身ヒロイン風",
    listOf(
    optionPart494(),
    ),
  )

private fun optionPart495(): CharacterOptionPart =
  CharacterOptionPart(
    "ファンタジー衣装",
    false,
    listOf(
      "i3_elf_robe" to "エルフ風の薄布のローブ",
      "i3_elf_archer_outfit" to "エルフ風の弓使い衣装",
      "i3_dark_elf_outfit" to "ダークエルフ風の紫と黒の衣装",
      "i3_forest_fairy_outfit" to "森の妖精風の葉っぱ衣装",
      "i3_flower_spirit_dress" to "花の精霊風のドレス",
      "i3_water_spirit_outfit" to "水の精霊風の透ける羽衣",
      "i3_wind_spirit_outfit" to "風の精霊風の羽衣",
      "i3_fire_spirit_outfit" to "炎の精霊風の赤い衣装",
      "i3_snow_spirit_outfit" to "雪の精霊風の白い衣装",
      "i3_moon_goddess_robe" to "月の女神風の薄衣",
      "i3_sun_priestess_robe" to "太陽の巫女風の金のローブ",
      "i3_sky_princess_dress" to "天空の姫風の羽衣ドレス",
      "i3_dragon_princess_dress" to "竜の姫風の鱗模様ドレス",
      "i3_dragoon_armor" to "竜騎士風の鎧とマント",
      "i3_dragon_horns" to "角つきの竜人風衣装",
      "i3_mermaid_dress" to "人魚風のスパンコールドレス",
      "i3_mermaid_tail_skirt" to "人魚の尾びれスカート",
      "i3_sea_queen_dress" to "海の女王風の貝殻ドレス",
      "i3_fairy_wings" to "妖精風の透明な羽根つきドレス",
      "i3_centaur_style_outfit" to "半人半馬にも見える革の衣装",
      "i3_beast_person_outfit" to "獣人風のふわふわ衣装",
      "i3_kitsune_costume" to "白狐風のふんわり衣装",
      "i3_cat_ears" to "猫耳としっぽ付きの冒険者衣装",
      "i3_witch_hat_2" to "魔女の大きな帽子と星のローブ",
      "i3_fairytale_witch_dress" to "おとぎ話の魔女風ドレス",
      "i3_traveling_mage_robe" to "旅の魔法使いのフードローブ",
      "i3_archmage_robe" to "大魔導士風の長いローブ",
      "i3_sage_outfit" to "賢者風の本と杖を持つ衣装",
      "i3_astrologer_robe" to "占星術師風の星座柄ローブ",
      "i3_alchemist_goggles" to "錬金術師風のゴーグルとコート",
      "i3_shaman_talisman" to "呪術師風の札と数珠の衣装",
      "i3_necromancer_robe" to "死霊術師風の黒いローブ",
      "i3_bard_outfit" to "吟遊詩人風の羽根帽子と楽器",
      "i3_travelling_dancer" to "旅の踊り子風のコイン飾り",
      "i3_arabian_dancer_outfit" to "アラビアンナイト風の踊り子衣装",
      "i3_desert_princess_outfit" to "砂漠の姫風の透け布衣装",
      "i3_gypsy_skirt" to "ジプシー風の刺繍スカート",
      "i3_roman_toga_style_outfit" to "ローマ風のトーガ衣装",
      "i3_egyptian_style_outfit" to "エジプト風のゴールドアクセサリー衣装",
      "i3_queen_fur_cape" to "女王風の毛皮つきマント",
      "i3_court_dress" to "貴族風の宮廷ドレス",
      "i3_princess_tiara" to "姫君風のティアラとドレス",
      "i3_villainess_dress" to "悪役令嬢風の豪華なドレス",
      "i3_noble_lady" to "令嬢風の縦ロールとドレス",
      "i3_masquerade_mask" to "舞踏会の仮面と夜会ドレス",
      "i3_butler_suit" to "貴族風の男装(執事)",
      "i3_prince_coat" to "王子風の金モールの上着",
      "i3_knight_captain_uniform" to "騎士団長風の勲章つき軍装",
      "i3_vampire_cape" to "吸血鬼風の黒いマントとドレス",
      "i3_gothic_vampire_noble_outfit" to "ゴシックヴァンパイア風の貴族服",
      "i3_demon_lord_cape" to "魔王風の角と黒いマント",
      "i3_succubus_wings" to "サキュバス風の黒い翼衣装",
      "i3_little_devil_tail" to "小悪魔風の尻尾と角",
      "i3_fallen_angel" to "堕天使風の黒い羽根",
      "i3_angel_wings" to "天使風の白い羽根とハロー",
      "i3_grim_reaper_robe" to "死神風の黒いローブ",
      "i3_ghost_costume" to "ゴースト風のシーツ衣装",
      "i3_zombie_costume" to "ゾンビ風の包帯と破れた服",
      "i3_mummy_wrapped_bandages" to "ミイラ風の包帯衣装",
      "i3_stitched_patchwork_costume" to "フランケン風のつぎはぎ衣装",
      "i3_cat_witch_hat" to "ネコ魔女風のとんがり帽子",
      "i3_pumpkin_dress" to "カボチャモチーフのハロウィンドレス",
      "i3_bat_wing_mini_dress" to "コウモリ翼のミニドレス",
      "i3_spiderweb_lace_outfit" to "蜘蛛の巣模様のレース衣装",
      "i3_temple_priestess" to "神殿の巫女風の白いドレープ",
      "i3_greek_style_dress" to "ギリシャ神話風のドレープドレス",
    ),
  )

private fun optionGenre124(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・ファンタジー",
    listOf(
    optionPart495(),
    ),
  )

private fun optionPart496(): CharacterOptionPart =
  CharacterOptionPart(
    "SF衣装",
    false,
    listOf(
      "i3_cyberpunk_glowing_jacket" to "近未来サイバーパンク風の光るジャケット",
      "i3_neon_cyber_outfit" to "ネオン配色のサイバー衣装",
      "i3_visor" to "バイザーつきのサイバー衣装",
      "i3_cybernetic_arm" to "サイバーパンク風の機械の腕",
      "i3_holographic_jacket" to "ホログラム模様のジャケット",
      "i3_powered_suit_costume" to "パワードスーツ風のコスチューム",
      "i3_robot_armor" to "成人のロボット風鎧",
      "i3_robot_joints" to "ロボ風の関節パーツ衣装",
      "i3_android_outfit" to "アンドロイド風の白い衣装",
      "i3_maid_robot_outfit" to "メイドロボット風の衣装",
      "i3_cyborg_suit" to "サイボーグ風の継ぎ目スーツ",
      "i3_space_suit" to "宇宙服風のカラフルなスーツ",
      "i3_jumpsuit" to "宇宙艦隊風のジャンプスーツ",
      "i3_space_pilot_jacket" to "スペースパイロット風のジャケット",
      "i3_space_pirate_coat" to "宇宙海賊風のコート",
      "i3_sleek_armor" to "銀河の戦士風の流線形アーマー",
      "i3_glossy_battle_bodysuit" to "バトルスーツ風の光沢ボディスーツ",
      "i3_metallic_silver_leotard" to "メタリックシルバーのレオタード",
      "i3_retro_futurism_outfit" to "レトロフューチャー風の衣装",
      "i3_steampunk_corset_dress" to "スチームパンク風のコルセットドレス",
      "i3_steampunk_goggles" to "スチームパンク風のゴーグルと歯車",
      "i3_steampunk_aviator_coat" to "スチームパンク風の飛行士コート",
      "i3_steampunk_engineer_apron" to "蒸気機関士風のエプロン",
      "i3_dieselpunk_leather_jacket" to "ディーゼルパンク風のレザージャケット",
      "i3_post_apocalyptic_gear" to "ポストアポカリプス風の廃品装備",
      "i3_gas_mask" to "ガスマスクつきのサバイバル衣装",
      "i3_wasteland_poncho_outfit" to "荒野の旅人風のポンチョ衣装",
      "i3_cyber_ninja_outfit" to "サイバー忍者風の衣装",
      "i3_futuristic_guard_suit" to "未来都市の警備員風スーツ",
      "i3_transparent_raincoat" to "透明素材のレインコート風衣装",
      "i3_vinyl_futuristic_dress" to "ビニール素材の近未来ドレス",
      "i3_operator_uniform" to "アニメ風のオペレーター衣装",
      "i3_headset" to "ヘッドセットと通信機の衣装",
      "i3_hacker_hoodie" to "ハッカー風のフード衣装",
      "i3_digital_hoodie" to "デジタル柄のフード衣装",
      "i3_virtual_world_outfit" to "バーチャル空間の光る衣装",
      "i3_circuit_pattern_outfit" to "回路柄の衣装",
      "i3_inventor_outfit" to "ガジェットを背負った発明家風",
      "i3_alien_antennae_headband" to "エイリアン風の触角ヘアバンド",
      "i3_alien_outfit" to "宇宙人風の銀色衣装",
      "i3_white_astronaut_suit" to "月面基地風の白いスーツ",
      "i3_rocket_motif_outfit" to "ロケット風モチーフの衣装",
      "i3_planet_ring_motif_outfit" to "惑星の輪モチーフの衣装",
      "i3_time_traveler_coat" to "タイムトラベラー風のコート",
      "i3_clockwork_dress" to "時計仕掛けのドレス",
    ),
  )

private fun optionGenre125(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・SF・メカ（追加）",
    listOf(
    optionPart496(),
    ),
  )

private fun optionPart497(): CharacterOptionPart =
  CharacterOptionPart(
    "和風衣装",
    false,
    listOf(
      "i3_miko_red_and_white_outfit" to "巫女風の紅白装束",
      "i3_white_miko_dress" to "白装束の巫女風ドレス",
      "i3_kagura_dancer_outfit" to "神楽舞の舞姫風衣装",
      "i3_fox_mask" to "狐面と巫女服の組み合わせ",
      "i3_large_obi_bow" to "帯が大きなリボンになった着物",
      "i3_furisode" to "袖の長い振袖風衣装",
      "i3_oiran_kimono" to "花魁風の豪華な着物",
      "i3_taisho_roman_outfit" to "袴ブーツの大正風衣装",
      "i3_taisho_roman" to "大正ロマン風の矢絣着物",
      "i3_meiji_era_outfit" to "明治風の洋装アレンジ",
      "i3_japanese_cross_dress" to "和装男装の袴スタイル",
      "i3_samurai_princess_kimono" to "武家の姫風の着物",
      "i3_female_swordsman" to "女剣士風の袴と羽織",
      "i3_samurai_haori" to "侍風の羽織と刀",
      "i3_ronin_haori" to "浪人風のゆるい羽織",
      "i3_ninja_outfit" to "忍者風の和風戦闘服",
      "i3_kunoichi" to "くノ一風の鎖かたびら",
      "i3_onmyoji_kariginu" to "陰陽師風の狩衣",
      "i3_ofuda" to "陰陽師の札と式神",
      "i3_buddhist_robe" to "僧侶風の袈裟衣装",
      "i3_nun_veil" to "尼僧風の白頭巾",
      "i3_yukata" to "花火大会の浴衣と団扇",
      "i3_yukata_2" to "縁日の浴衣と金魚柄",
      "i3_happi_coat" to "夏祭りの法被とはちまき",
      "i3_matsuri_outfit" to "祭り衣装の鯉口シャツ",
      "i3_japanese_style_swimsuit" to "和風の水着(さらし風)",
      "i3_kitsune_ears" to "狐の妖怪風の耳としっぽ",
      "i3_nine_tailed_fox_outfit" to "九尾の狐風の衣装",
      "i3_bakeneko_outfit" to "化け猫風の鈴つき着物",
      "i3_oni_horns" to "鬼の角つき着物",
      "i3_oni_princess_outfit" to "鬼姫風の赤い衣装",
      "i3_tengu_outfit" to "天狗風の山伏衣装",
      "i3_kappa_style_outfit" to "河童風の緑の衣装",
      "i3_yuki_onna_white_kimono" to "雪女風の白い着物",
      "i3_japanese_spirit_outfit" to "和風の精霊風のおぼろな着物",
      "i3_ghost_kimono" to "幽霊風の白い着物",
      "i3_chochin_obake_style_outfit" to "提灯お化け風の衣装",
      "i3_karakasa_obake_style_outfit" to "唐傘お化け風の衣装",
      "i3_long_scarf" to "ろくろ首風の長いスカーフ",
      "i3_japanese_mermaid_outfit" to "和風の人魚衣装",
      "i3_otohime_outfit" to "乙姫風の羽衣",
      "i3_hagoromo" to "天女の羽衣",
      "i3_hanfu" to "中華風の漢服",
      "i3_fairy_hanfu_dress" to "仙女風の漢服ドレス",
      "i3_wuxia_outfit" to "武侠風の中華戦闘服",
      "i3_china_dress" to "チャイナ風のミニドレス",
      "i3_chinese_tassel_outfit" to "チャイナ風のフリンジ衣装",
      "i3_miao_silver_headdress_outfit" to "苗族風の銀飾り衣装",
      "i3_ryukyu_bingata_outfit" to "琉球風の紅型着物",
      "i3_ainu_embroidery_outfit" to "アイヌ風の刺繍衣装",
      "i3_mini_kimono_dress" to "和風アレンジのミニ着物ドレス",
    ),
  )

private fun optionGenre126(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・和風・妖怪",
    listOf(
    optionPart497(),
    ),
  )

private fun optionPart498(): CharacterOptionPart =
  CharacterOptionPart(
    "メイド・給仕",
    false,
    listOf(
      "i3_cafe_maid" to "フリルたっぷりのカフェメイド",
      "i3_classic_maid" to "ミニ丈のクラシックメイド",
      "i3_british_maid" to "ロング丈の英国メイド",
      "i3_wa_maid" to "和風メイド(着物エプロン)",
      "i3_chinese_maid_dress" to "中華メイド",
      "i3_cat_ear_maid" to "黒猫耳メイド",
      "i3_rabbit_ear_maid" to "うさ耳メイド",
      "i3_twin_braids" to "ふたつ結びと猫リボンのメイド",
      "i3_puff_sleeve_maid_dress" to "パフスリーブのメイドワンピ",
      "i3_sleeveless_maid" to "袖なしのメイドベスト",
      "i3_off_shoulder_maid" to "肩出しメイド",
      "i3_big_back_ribbon_maid" to "背中リボンのメイド",
      "i3_heart_shaped_apron" to "エプロンがハート型のメイド",
      "i3_lace_headdress" to "レースカチューシャとレースカフス",
      "i3_garter_belt_maid" to "ガーターベルトつきメイド",
      "i3_head_maid_dress" to "メイド長風の落ち着いたドレス",
      "i3_maid_and_butler_pair_outfit" to "メイドと執事の対衣装",
      "i3_butler_vest" to "執事風のベストと懐中時計",
      "i3_tailcoat" to "男装執事風の燕尾服",
      "i3_waitress_apron_dress" to "給仕係風のエプロンドレス",
      "i3_retro_cafe_waitress_outfit" to "レトロ喫茶店風のウェイトレス",
      "i3_barmaid_outfit" to "パブ風のバーメイド",
      "i3_bartender_vest" to "バーテンダー風のベストと蝶ネクタイ",
      "i3_sommelier_apron" to "ソムリエ風のエプロン",
      "i3_hall_staff_tuxedo" to "ホール係風の黒いタキシード",
      "i3_concept_cafe_outfit" to "コンカフェ風の衣装",
      "i3_bakery_outfit" to "ケーキ屋さん風の衣装",
      "i3_patisserie_chef_coat" to "パティシエ風のコック服",
      "i3_tea_party_apron_dress" to "ティーパーティー風のエプロンドレス",
      "i3_wonderland_style_dress" to "不思議の国風のエプロンドレス",
      "i3_storybook_apron_dress" to "絵本風のエプロンワンピ",
      "i3_kappogi_apron" to "お手伝いさん風のかっぽう着",
      "i3_ryokan_hostess_kimono" to "温泉宿の仲居風の着物",
      "i3_ryokan_yukata" to "旅館の浴衣と半纏",
      "i3_kappogi" to "和風カフェの割烹着",
    ),
  )

private fun optionGenre127(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・メイド・給仕（追加）",
    listOf(
    optionPart498(),
    ),
  )

private fun optionPart499(): CharacterOptionPart =
  CharacterOptionPart(
    "VTuber・アイドル衣装",
    false,
    listOf(
      "i3_sparkly_stage_mini_dress" to "ステージ向けのきらきらミニドレス",
      "i3_group_matching_outfit" to "ユニット揃いの衣装",
      "i3_twintail_idol_outfit" to "ツインテールに合わせたアイドル衣装",
      "i3_live_costume" to "ライブ衣装風のフリルスカート",
      "i3_stage_blouse" to "リボン飾りのステージブラウス",
      "i3_off_shoulder_idol_blouse" to "肩出しのアイドルブラウス",
      "i3_peplum_stage_outfit" to "ペプラムのステージ衣装",
      "i3_sequin_stage_outfit" to "スパンコールのステージ衣装",
      "i3_neon_punk_idol_outfit" to "ネオンカラーのパンクアイドル衣装",
      "i3_rock_band_outfit" to "ロックバンド風のライブ衣装",
      "i3_girl_band_leather_jacket" to "ガールズバンド風の革ジャケット",
      "i3_visual_kei_band_outfit" to "ヴィジュアル系風のバンド衣装",
      "i3_vocaloid_style_outfit" to "ボーカロイド風のデジタル衣装",
      "i3_virtual_singer_outfit" to "バーチャルシンガー風のツインテール衣装",
      "i3_hologram_stage_outfit" to "ホログラムステージ風の衣装",
      "i3_k_pop_style_outfit" to "K-POP風のコーディネート",
      "i3_dance_unit_crop_top_outfit" to "ダンスユニット風のクロップトップ衣装",
      "i3_cheer_dance_outfit" to "チアダンス風のポンポン衣装",
      "i3_cheerleader" to "ミニスカのチアスタイル",
      "i3_baton_twirler_outfit" to "バトントワラー風の衣装",
      "i3_hula_outfit" to "フラダンス風のレイとスカート",
      "i3_aloha_style_idol_set" to "アロハ風のアイドルセット",
      "i3_wedding_stage_dress" to "ウェディング風のステージドレス",
      "i3_angel_idol_white_dress" to "天使系アイドルの白いドレス",
      "i3_devil_idol_black_dress" to "悪魔系アイドルの黒いドレス",
      "i3_jirai_kei_dress" to "地雷系ファッション風のワンピース",
      "i3_ryousangata_fashion" to "量産型ファッション風のワンピース",
      "i3_yume_kawaii_outfit" to "ゆめかわ系の淡い衣装",
      "i3_pastel_fluffy_outfit" to "パステルカラーのふわふわ衣装",
      "i3_menhera_style_black_and_pink" to "メンヘラ系ファッション風の黒ピンク衣装",
      "i3_steampunk_streamer_outfit" to "スチームパンク配信者風の衣装",
      "i3_streamer_hoodie_outfit" to "配信者向けのパーカー衣装",
      "i3_gamer_outfit" to "ヘッドホンつきのゲーマー衣装",
      "i3_gaming_outfit" to "ゲーミングチェアに合う衣装",
      "i3_cat_ear_headphones" to "猫耳ヘッドホンの配信衣装",
      "i3_fox_ears_streamer_outfit" to "狐耳の配信者風衣装",
      "i3_witch_vtuber_style_outfit" to "魔女系VTuber風衣装",
      "i3_vampire_vtuber_style_outfit" to "吸血鬼系VTuber風衣装",
      "i3_ojou_sama_vtuber_style_dress" to "お嬢様系VTuber風ドレス",
      "i3_pirate_vtuber_style_outfit" to "海賊系VTuber風の衣装",
      "i3_ninja_vtuber_style_outfit" to "忍者系VTuber風の衣装",
      "i3_wafuku_vtuber_style_outfit" to "和風VTuber風の衣装",
      "i3_adult_elegant_vtuber_dress" to "大人っぽいVTuber風ドレス",
      "i3_half_angel_half_devil_outfit" to "天使悪魔の半分ずつ衣装",
      "i3_asymmetrical_color_outfit" to "左右非対称の配色衣装",
      "i3_fluffy_pajamas" to "パジャマ配信風のもこもこ衣装",
      "i3_yukata_stream_outfit" to "浴衣配信風の衣装",
      "i3_swimsuit_stream_outfit" to "水着配信風の衣装",
      "i3_santa_stream_outfit" to "サンタ配信風のミニスカ衣装",
      "i3_new_year_kimono_stream_outfit" to "年末の着物配信衣装",
      "i3_oversized_hoodie" to "オーバーサイズのパーカーとショートパンツ",
      "i3_fluffy_fur_coat" to "ふわふわのファーつきアウター",
      "i3_tail_jacket" to "しっぽつきのジャージ風",
      "i3_animal_print_loungewear" to "アニマル柄のルームウェア",
      "i3_kigurumi_pajamas" to "きぐるみパジャマ風",
    ),
  )

private fun optionGenre128(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・VTuber・アイドル衣装（追加）",
    listOf(
    optionPart499(),
    ),
  )

private fun optionPart500(): CharacterOptionPart =
  CharacterOptionPart(
    "ゲーム風衣装",
    false,
    listOf(
      "i3_hero_blue_cape" to "勇者風の青いマントと剣",
      "i3_mage_pointed_hat" to "魔法使い風の三角帽子とローブ",
      "i3_cleric_white_robe" to "僧侶風の白いローブ",
      "i3_warrior_heavy_armor" to "戦士風の重装鎧",
      "i3_thief_light_outfit" to "盗賊風の軽装",
      "i3_monk_martial_arts_outfit" to "武闘家風の道着",
      "i3_dancer_veil_outfit" to "踊り子風の薄布衣装",
      "i3_merchant_outfit" to "商人風の大きな荷物",
      "i3_traveler_cloak_and_hood" to "旅人風のマントとフード",
      "i3_alchemist_goggles_2" to "錬金術師風のゴーグルと薬瓶",
      "i3_cook_bandana" to "料理人風のバンダナ",
      "i3_guild_receptionist_outfit" to "冒険者ギルドの受付嬢風",
      "i3_starter_adventurer_gear" to "冒険者の初期装備風",
      "i3_high_level_legendary_gear" to "レベルアップした豪華装備風",
      "i3_rare_gear" to "レアアイテム風の金装飾",
      "i3_dungeon_explorer" to "ダンジョン探索者風のランタン装備",
      "i3_hunter_gear" to "狩猟装備風の毛皮の鎧",
      "i3_bone_armor" to "ドラゴンハンター風の骨の装備",
      "i3_pirate_captain_coat" to "海賊船の船長風コート",
      "i3_pirate_bandana" to "海賊風のスカーフとブーツ",
      "i3_sky_pirate_goggles" to "空賊風のゴーグルとコート",
      "i3_airship_pilot_outfit" to "飛空艇の操縦士風",
      "i3_ninja_action_outfit" to "忍者アクションゲーム風の衣装",
      "i3_fighting_game_outfit" to "格闘ゲーム風のコンビネーション衣装",
      "i3_sports_game_outfit" to "スポーツゲーム風のユニフォーム",
      "i3_dance_game_sporty_outfit" to "ダンスゲーム風のスポーティ衣装",
      "i3_rhythm_game_neon_outfit" to "音楽ゲーム風のネオン衣装",
      "i3_rhythm_game_headphones_outfit" to "リズムゲーム風のヘッドホン衣装",
      "i3_card_game_mage_outfit" to "カードゲーム風の魔法使い衣装",
      "i3_strategist_outfit" to "シミュレーションゲーム風の軍師衣装",
      "i3_strategist_feather_fan" to "軍師風の羽扇と羽織",
      "i3_three_kingdoms_general_armor" to "三国志風の武将衣装",
      "i3_sengoku_armor_remix" to "戦国風の甲冑アレンジ",
      "i3_sengoku_helmet" to "戦国武将風の兜と陣羽織",
      "i3_action_rpg_dual_swords_outfit" to "アクションRPG風の双剣士",
      "i3_magic_knight_armor" to "魔法騎士風のマントつき軽鎧",
      "i3_summoner_staff" to "召喚士風の杖と宝石",
      "i3_spirit_caller_robe" to "精霊使い風のゆったりローブ",
      "i3_beast_tamer" to "獣使い風の毛皮の肩当て",
      "i3_archer_quiver" to "弓兵風の矢筒とブーツ",
      "i3_dragoon_spear" to "竜騎士風の槍と翼飾り",
      "i3_gunner_coat" to "ガンナー風のコートと銃",
      "i3_support_class_medic_pouch" to "サポート職風の医療ポーチ",
      "i3_mage_staff" to "魔導士風の宝石つき杖",
      "i3_necromancer_staff" to "ネクロマンサー風の杖とドクロ飾り",
      "i3_healer_white_outfit" to "ヒーラー風のやさしい白衣装",
      "i3_support_character" to "応援団長風のサポート衣装",
      "i3_tank_class" to "タンク風の大盾と鎧",
      "i3_sniper_ghillie_suit" to "スナイパー風のギリースーツ",
      "i3_fps_tactical_outfit" to "FPSゲーム風のタクティカル衣装",
      "i3_camouflage_outfit" to "サバイバルゲーム風の迷彩服",
      "i3_open_world_traveler_gear" to "オープンワールド風の旅装",
      "i3_roguelike_explorer_outfit" to "ローグライクの探索者風",
      "i3_pouches" to "ポーチだらけの収集家風",
      "i3_colorful_skin_outfit" to "装備スキン風のカラフル衣装",
      "i3_seasonal_limited_outfit" to "季節限定風の特別衣装",
      "i3_summer_swimsuit_variant" to "水着バージョン風の夏衣装",
      "i3_new_year_variant_kimono" to "正月バージョン風の晴れ着",
      "i3_valentine_chocolate_theme_outfit" to "バレンタイン風のチョコレート衣装",
      "i3_halloween_game_outfit" to "ハロウィン風のゲーム衣装",
      "i3_christmas_game_outfit" to "クリスマス風のゲーム衣装",
    ),
  )

private fun optionGenre129(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・ゲーム風（追加）",
    listOf(
    optionPart500(),
    ),
  )

private fun optionPart501(): CharacterOptionPart =
  CharacterOptionPart(
    "イベント・季節",
    false,
    listOf(
      "i3_halloween_pumpkin_witch_outfit" to "ハロウィンのかぼちゃ魔女衣装",
      "i3_halloween_imp_outfit" to "ハロウィンの小悪魔衣装",
      "i3_halloween_cat_ears_dress" to "ハロウィンの猫耳ドレス",
      "i3_black_cat_mini_dress" to "ハロウィンの黒猫ミニワンピ",
      "i3_halloween_vampire_dress" to "ハロウィンの吸血鬼ドレス",
      "i3_halloween_stitched_costume" to "ハロウィンのフランケン風衣装",
      "i3_skeleton_print_costume" to "ハロウィンのスケルトン柄衣装",
      "i3_skull_themed_costume" to "ハロウィンの骸骨モチーフ衣装",
      "i3_halloween_witch_hat" to "ハロウィンの魔女帽子つき衣装",
      "i3_ghost_sheet_costume" to "ハロウィンのお化けシーツ衣装",
      "i3_red_hooded_cape" to "ハロウィンのフードケープ衣装",
      "i3_zombie_nurse_costume" to "ハロウィンのゾンビナース風",
      "i3_zombie_maid_costume" to "ハロウィンのゾンビメイド風",
      "i3_halloween_bunny_outfit" to "ハロウィンのバニー風衣装",
      "i3_bat_costume" to "ハロウィンのコウモリ衣装",
      "i3_spider_woman_costume" to "ハロウィンの蜘蛛女風",
      "i3_doll_costume" to "ハロウィンの人形風ドレス",
      "i3_jester_costume" to "ハロウィンの道化師衣装",
      "i3_puppeteer_costume" to "ハロウィンの人形師風衣装",
      "i3_mini_skirt_santa_costume" to "クリスマスのミニスカサンタ",
      "i3_santa_hat" to "クリスマスのサンタ帽とケープ",
      "i3_reindeer_costume" to "クリスマスのトナカイ風衣装",
      "i3_snowman_costume" to "クリスマスの雪だるま風衣装",
      "i3_christmas_tree_dress" to "クリスマスのツリー飾り風ドレス",
      "i3_gift_wrap_outfit" to "クリスマスのプレゼント包み風衣装",
      "i3_gingerbread_costume" to "クリスマスのジンジャーブレッド風",
      "i3_snow_queen_dress" to "クリスマスの雪の女王風ドレス",
      "i3_white_fur_cape" to "クリスマスの白い毛皮ケープ",
      "i3_furisode_2" to "正月の晴れ着",
      "i3_hatsumode_outfit" to "初詣用の和服と巫女風",
      "i3_oni_costume" to "節分の鬼のビキニ風衣装",
      "i3_tanabata_orihime_outfit" to "七夕の織姫風衣装",
      "i3_hanami_floral_kimono" to "お花見の花柄着物",
      "i3_yukata_3" to "夏祭りの浴衣と花飾り",
      "i3_fireworks_yukata" to "花火大会の浴衣",
      "i3_beach_swimsuit" to "海開きの水着",
      "i3_pareo" to "プールサイドのパレオ",
      "i3_straw_hat" to "夏の麦わら帽子とワンピ",
      "i3_tsukimi_rabbit_outfit" to "お月見のうさぎ衣装",
      "i3_autumn_leaves_kimono" to "紅葉狩りの和服",
      "i3_halloween_cafe_waitress_outfit" to "ハロウィンコンカフェ風のウェイトレス",
      "i3_valentine_chocolate_dress" to "バレンタインのチョコレート色ドレス",
      "i3_white_day_white_outfit" to "ホワイトデーの白い衣装",
      "i3_spring_flower_fairy_outfit" to "春の花の妖精風",
      "i3_raincoat_outfit" to "梅雨のレインコート風衣装",
      "i3_frog_motif_raincoat" to "カエルモチーフのレイン衣装",
      "i3_hydrangea_yukata" to "紫陽花柄の浴衣",
      "i3_snow_outfit" to "雪山の冬のスノーウェア",
      "i3_fur_coat" to "雪遊びのファーコート",
      "i3_scarf" to "暖かいマフラーと手袋の冬コーデ",
      "i3_wedding_dress" to "ウェディングドレス風の純白ドレス",
      "i3_wedding_veil" to "ウェディング風のベールとブーケ",
      "i3_party_dress" to "ドレスアップしたパーティー衣装",
      "i3_cosplay_photo_shoot_outfit" to "コスプレ撮影会向けの衣装",
      "i3_pair_cosplay_outfit" to "併せ撮影向けのペア衣装",
      "i3_group_matching_costume" to "グループ併せのお揃い衣装",
      "i3_stage_cosplay_costume" to "ステージコスプレ向けの豪華衣装",
      "i3_large_prop_costume" to "大型造形つきの衣装",
      "i3_large_wings_prop" to "造形物の翼つき衣装",
      "i3_backpack_wings" to "羽根背負い式の大きな翼",
    ),
  )

private fun optionGenre130(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アニメ系コスプレ・イベント・季節（追加）",
    listOf(
    optionPart501(),
    ),
  )

private fun optionPart502(): CharacterOptionPart =
  CharacterOptionPart(
    "コスプレの小物",
    false,
    listOf(
      "i3_sword_and_shield" to "片手剣と盾",
      "i3_rapier" to "細身のレイピア",
      "i3_long_spear" to "長い槍",
      "i3_large_scythe" to "大きな鎌",
      "i3_magic_staff" to "魔法の杖(宝石つき)",
      "i3_star_wand" to "魔法のステッキ(星)",
      "i3_heart_wand" to "魔法のステッキ(ハート)",
      "i3_crystal_ball" to "水晶玉",
      "i3_ancient_scroll" to "古い巻物",
      "i3_matchlock_prop" to "火縄銃風の小道具",
      "i3_gun_holster" to "拳銃のホルスター",
      "i3_bangasa" to "番傘",
      "i3_chochin" to "提灯",
      "i3_hannya_mask" to "般若面",
      "i3_butterfly_mask" to "蝶の仮面",
      "i3_hourglass_pendant" to "砂時計のペンダント",
      "i3_cross_pendant" to "十字架のペンダント",
      "i3_moon_pendant" to "月のペンダント",
      "i3_feather_earrings" to "羽根のイヤリング",
      "i3_nine_tails" to "九本のしっぽ",
      "i3_cat_tail" to "猫のしっぽ",
      "i3_bunny_tail" to "うさぎのしっぽ",
      "i3_devil_tail" to "悪魔のしっぽ",
      "i3_kanzashi_flowers" to "簪の花飾り",
      "i3_large_ribbon_headband" to "大きなリボンのカチューシャ",
      "i3_pointed_hat" to "三角帽子",
      "i3_captain_hat" to "キャプテンハット",
      "i3_tricorn_hat" to "三角帽の海賊帽",
      "i3_veiled_hat" to "ベールつき帽子",
      "i3_fur_hood" to "ファーつきフード",
      "i3_long_cloak" to "長いマント",
      "i3_fan_back_ornament" to "扇型の背中飾り",
      "i3_vambrace" to "ヴァンブレイス",
      "i3_pauldron" to "肩当て",
      "i3_greaves" to "脚甲",
      "i3_breastplate_2" to "胸当て",
      "i3_faulds" to "腰当て",
      "i3_dagger_belt" to "ダガーのベルト",
      "i3_goggles_helmet" to "ゴーグル付きヘルメット",
      "i3_chain_choker" to "鎖つきチョーカー",
      "i3_collar_choker" to "首輪風のチョーカー",
      "i3_shoulder_ribbon" to "肩リボン",
      "i3_high_heels" to "ハイヒール",
      "i3_fluffy_cuffs" to "ふわふわ袖のカフス",
      "i3_fur_ball_ornament" to "ファーボール飾り",
      "i3_bell_ornament" to "鈴飾り",
      "i3_prayer_beads" to "数珠",
      "i3_scabbard" to "剣の鞘",
      "i3_suitcase_bag" to "トランク型バッグ",
      "i3_book_bag" to "本型バッグ",
      "i3_plush_companion" to "ぬいぐるみの相棒",
      "i3_familiar_on_shoulder" to "肩乗りの小さな使い魔",
    ),
  )

private fun optionGenre131(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コスプレの小物・武器・装飾（追加）",
    listOf(
    optionPart502(),
    ),
  )

private fun optionPart503(): CharacterOptionPart =
  CharacterOptionPart(
    "コスプレのメイクと髪",
    false,
    listOf(
      "i3_long_twintails" to "ロングツインテール",
      "i3_messy_short_hair" to "くせ毛のショート",
      "i3_pastel_wig" to "パステルカラーのウィッグ",
      "i3_light_blue_hair" to "水色のウィッグ",
      "i3_rainbow_gradient_hair" to "虹色グラデーションのウィッグ",
      "i3_multicolored_bangs" to "色ちがいの前髪",
      "i3_asymmetrical_hair" to "アシンメトリーの髪型",
      "i3_uneven_sidelocks" to "左右で長さが違うサイドロック",
      "i3_many_hair_ornaments" to "髪飾りだらけの盛りヘア",
      "i3_large_flower_hair_ornament" to "大きな花飾りのヘア",
      "i3_kanzashi_updo" to "かんざしでまとめた和風ヘア",
      "i3_nihongami_style" to "日本髪風のアレンジ",
      "i3_red_contact_lenses" to "カラーコンタクト(赤)",
      "i3_blue_contact_lenses" to "カラーコンタクト(青)",
      "i3_heart_pupils" to "ハート形の瞳",
      "i3_star_pupils" to "星形の瞳",
      "i3_long_eyelashes" to "つけまつげで大きな目元",
      "i3_red_eyeshadow" to "赤いアイシャドウのキャラ風メイク",
      "i3_star_sticker_makeup" to "星やハートのシールを貼ったメイク",
      "i3_face_gems" to "フェイスジェム",
      "i3_glitter_tears" to "涙のようなラメ",
      "i3_blush" to "頬の赤み(チーク)",
      "i3_cheek_marking" to "頬に描いた模様",
      "i3_bold_lipstick" to "あざやかな口紅",
      "i3_pointed_ears_makeup" to "とがった耳メイク",
      "i3_horn_makeup" to "角のように見える額メイク",
      "i3_red_geisha_makeup" to "和風の紅化粧",
      "i3_white_makeup" to "白塗りの花魁メイク",
      "i3_dark_circles_makeup" to "くまメイク",
      "i3_zombie_makeup" to "ゾンビ風のメイク",
      "i3_soot_makeup" to "スチームパンクのすすメイク",
      "i3_elf_shimmer_makeup" to "エルフ風のキラキラメイク",
      "i3_mermaid_scales_makeup" to "人魚風のうろこメイク",
      "i3_fairy_glitter_makeup" to "妖精風のきらめきメイク",
    ),
  )

private fun optionGenre132(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コスプレのメイクと髪（追加）",
    listOf(
    optionPart503(),
    ),
  )

private fun optionPart504(): CharacterOptionPart =
  CharacterOptionPart(
    "輪郭・あごの形",
    true,
    listOf(
      "j1_face_001" to "すっきり細いあご",
      "j1_face_002" to "二重あご気味",
      "j1_face_003" to "横幅のある顔",
      "j1_face_004" to "ふくらみのあるほお",
      "j1_face_005" to "あごが前に出た顔",
      "j1_face_006" to "あごが引っ込んだ顔",
      "j1_face_007" to "丸い額",
      "j1_face_008" to "出っ張った額",
    ),
  )

private fun optionPart505(): CharacterOptionPart =
  CharacterOptionPart(
    "目の形・大きさ",
    true,
    listOf(
      "j1_face_009" to "アーモンド形の目",
      "j1_face_010" to "たっぷりした二重幅",
      "j1_face_011" to "目が顔の下寄り",
      "j1_face_012" to "目が顔の上寄り",
      "j1_face_013" to "外側が下がった目じり",
      "j1_face_014" to "外側が上がった目じり",
      "j1_face_015" to "重たげなまぶた",
      "j1_face_016" to "丸くて大きな瞳",
    ),
  )

private fun optionPart506(): CharacterOptionPart =
  CharacterOptionPart(
    "まつげ・アイライン",
    true,
    listOf(
      "j1_face_017" to "短いまつげ",
      "j1_face_018" to "濃い下まつげ",
      "j1_face_019" to "まばらなまつげ",
      "j1_face_020" to "たれ目ライン",
      "j1_face_021" to "目頭だけのライン",
      "j1_face_022" to "まつげが影を落とす目",
      "j1_face_023" to "束になったまつげ",
    ),
  )

private fun optionPart507(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳の模様・ハイライト",
    false,
    listOf(
      "j1_face_024" to "星形のハイライト",
      "j1_face_025" to "ふちが濃い瞳",
      "j1_face_026" to "瞳が小さい",
      "j1_face_027" to "下側に光がたまった瞳",
      "j1_face_028" to "白目が多い目",
      "j1_face_029" to "目の中に模様がある",
    ),
  )

private fun optionPart508(): CharacterOptionPart =
  CharacterOptionPart(
    "眉",
    true,
    listOf(
      "j1_face_030" to "長い眉",
      "j1_face_031" to "離れた眉",
      "j1_face_032" to "寄った眉",
      "j1_face_033" to "濃くて整った眉",
      "j1_face_034" to "眉尻が細い眉",
      "j1_face_035" to "つながり気味の眉",
      "j1_face_036" to "片方だけ上がった眉",
      "j1_face_037" to "切れ目のある眉",
      "j1_face_038" to "ふわふわした眉",
    ),
  )

private fun optionPart509(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻",
    true,
    listOf(
      "j1_face_039" to "低めの鼻",
      "j1_face_040" to "とがった鼻先",
      "j1_face_041" to "上向きの鼻",
      "j1_face_042" to "鼻筋がすっとした鼻",
      "j1_face_043" to "かぎ鼻",
      "j1_face_044" to "鼻の下が長い",
      "j1_face_045" to "鼻がほとんど描かれない",
      "j1_face_046" to "鼻の頭に小さなほくろ",
    ),
  )

private fun optionPart510(): CharacterOptionPart =
  CharacterOptionPart(
    "口・唇・歯",
    true,
    listOf(
      "j1_face_047" to "上唇が厚い",
      "j1_face_048" to "下唇が厚い",
      "j1_face_049" to "口角の上がった口",
      "j1_face_050" to "口角の下がった口",
      "j1_face_051" to "前歯が見える",
      "j1_face_052" to "歯並びの良い歯",
      "j1_face_053" to "口の端のほくろ",
      "j1_face_054" to "唇の下のほくろ",
      "j1_face_055" to "口もとの小さな傷",
    ),
  )

private fun optionPart511(): CharacterOptionPart =
  CharacterOptionPart(
    "耳",
    true,
    listOf(
      "j1_face_056" to "耳たぶの大きな耳",
      "j1_face_057" to "耳たぶがない耳",
      "j1_face_058" to "立った耳",
      "j1_face_059" to "折れた耳",
      "j1_face_060" to "耳が髪から出ている",
      "j1_face_061" to "耳の後ろの飾り",
    ),
  )

private fun optionGenre133(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・顔のパーツ（追加）",
    listOf(
    optionPart504(),
    optionPart505(),
    optionPart506(),
    optionPart507(),
    optionPart508(),
    optionPart509(),
    optionPart510(),
    optionPart511(),
    ),
  )

private fun optionPart512(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪の形",
    true,
    listOf(
      "j1_hair_001" to "サイド分け",
      "j1_hair_002" to "おでこを出した前髪",
      "j1_hair_003" to "ひし形に割れた前髪",
      "j1_hair_004" to "束で分かれた前髪",
      "j1_hair_005" to "ななめ分けの長い前髪",
      "j1_hair_006" to "ウェーブのある前髪",
      "j1_hair_007" to "ストレートな重め前髪",
    ),
  )

private fun optionPart513(): CharacterOptionPart =
  CharacterOptionPart(
    "後ろ髪の長さ・形",
    true,
    listOf(
      "j1_hair_008" to "ショートの襟足",
      "j1_hair_009" to "ボブの後ろ髪",
      "j1_hair_010" to "鎖骨までの後ろ髪",
      "j1_hair_011" to "お尻までの後ろ髪",
      "j1_hair_012" to "内巻きの毛先",
      "j1_hair_013" to "外はねの毛先",
      "j1_hair_014" to "段のない後ろ髪",
      "j1_hair_015" to "襟足だけ長い",
    ),
  )

private fun optionPart514(): CharacterOptionPart =
  CharacterOptionPart(
    "横髪（サイドの髪）",
    true,
    listOf(
      "j1_hair_016" to "あごまでの横髪",
      "j1_hair_017" to "耳を隠す横髪",
      "j1_hair_018" to "三つ編みの横髪",
      "j1_hair_019" to "顔を縁取る短い横髪",
      "j1_hair_020" to "ひねった横髪",
      "j1_hair_021" to "束ねた横髪",
      "j1_hair_022" to "横髪にリボン",
      "j1_hair_023" to "ウェーブの横髪",
      "j1_hair_024" to "片側だけ長い横髪",
    ),
  )

private fun optionPart515(): CharacterOptionPart =
  CharacterOptionPart(
    "アホ毛（頭頂の毛）",
    true,
    listOf(
      "j1_hair_025" to "はねる大きなアホ毛",
      "j1_hair_026" to "立ったアホ毛",
      "j1_hair_027" to "ぐにゃりと垂れたアホ毛",
      "j1_hair_028" to "ぴょこんと動くアホ毛",
      "j1_hair_029" to "アホ毛なし",
      "j1_hair_030" to "前に垂れるアホ毛",
    ),
  )

private fun optionPart516(): CharacterOptionPart =
  CharacterOptionPart(
    "まとめ髪・結び方",
    true,
    listOf(
      "j1_hair_031" to "二つ結び（耳の下）",
      "j1_hair_032" to "ポニーテールの根元にリボン",
      "j1_hair_033" to "結んだ毛束を前に流す",
      "j1_hair_034" to "首の後ろで低く結ぶ",
      "j1_hair_035" to "ヘアバンドでまとめる",
      "j1_hair_036" to "巻き髪のサイドアップ",
      "j1_hair_037" to "ターバン巻きの髪",
    ),
  )

private fun optionPart517(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の色の入り方",
    false,
    listOf(
      "j1_hair_038" to "全体が1色の髪",
      "j1_hair_039" to "つやのない髪",
      "j1_hair_040" to "白髪まじり",
      "j1_hair_041" to "シルバーのメッシュ",
    ),
  )

private fun optionGenre134(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・髪型と髪の部位（追加）",
    listOf(
    optionPart512(),
    optionPart513(),
    optionPart514(),
    optionPart515(),
    optionPart516(),
    optionPart517(),
    ),
  )

private fun optionPart518(): CharacterOptionPart =
  CharacterOptionPart(
    "全体の背丈・バランス",
    true,
    listOf(
      "j1_body_001" to "背が高い（高身長）",
      "j1_body_002" to "背が低め",
      "j1_body_003" to "八頭身",
      "j1_body_004" to "胴が長いバランス",
      "j1_body_005" to "上半身が華奢",
      "j1_body_006" to "下半身が豊か",
      "j1_body_007" to "やせ型",
    ),
  )

private fun optionPart519(): CharacterOptionPart =
  CharacterOptionPart(
    "首・肩・腕",
    true,
    listOf(
      "j1_body_008" to "鎖骨のくぼみ",
      "j1_body_009" to "たくましい二の腕",
      "j1_body_010" to "ふっくらした二の腕",
      "j1_body_011" to "短い腕",
    ),
  )

private fun optionPart520(): CharacterOptionPart =
  CharacterOptionPart(
    "胸のスライダー的な表現",
    true,
    listOf(
      "j1_body_013" to "胸がやや小さい",
      "j1_body_014" to "胸の位置が高い",
      "j1_body_015" to "胸の位置が低い",
      "j1_body_016" to "胸が上を向く",
      "j1_body_017" to "胸が左右に離れる",
      "j1_body_018" to "胸が中央に寄る",
      "j1_body_019" to "胸の下の丸み",
      "j1_body_020" to "胸の横の丸み",
      "j1_body_021" to "胸まわりが細い",
      "j1_body_022" to "胸の大きさが控えめ",
      "j1_body_023" to "胸の影が強い",
    ),
  )

private fun optionPart521(): CharacterOptionPart =
  CharacterOptionPart(
    "お腹・腰・ウエスト",
    true,
    listOf(
      "j1_body_024" to "細すぎるウエスト",
      "j1_body_025" to "ゆるやかなウエスト",
      "j1_body_026" to "腹筋の筋",
      "j1_body_027" to "縦線のあるお腹",
      "j1_body_028" to "おなかのぽっこり",
      "j1_body_029" to "ウエストのえくぼ",
      "j1_body_031" to "腰の幅が狭い",
      "j1_body_032" to "脇腹のライン",
      "j1_body_033" to "下腹のなだらかな線",
    ),
  )

private fun optionPart522(): CharacterOptionPart =
  CharacterOptionPart(
    "お尻・太もも・脚",
    true,
    listOf(
      "j1_body_034" to "小ぶりなお尻",
      "j1_body_035" to "ふつうのお尻",
      "j1_body_037" to "上がったお尻",
      "j1_body_038" to "すらりとした脚",
      "j1_body_039" to "むっちりした脚",
      "j1_body_040" to "O脚気味",
      "j1_body_041" to "ひざの形がくっきり",
      "j1_body_042" to "ふくらはぎの丸み",
      "j1_body_043" to "細い足首",
    ),
  )

private fun optionPart523(): CharacterOptionPart =
  CharacterOptionPart(
    "手・指・爪の形",
    true,
    listOf(
      "j1_body_044" to "短くてふっくらした指",
      "j1_body_045" to "細い手首",
      "j1_body_047" to "手のひらが見える",
    ),
  )

private fun optionPart524(): CharacterOptionPart =
  CharacterOptionPart(
    "体の質感",
    false,
    listOf(
      "j1_body_049" to "乾いてさらさらな肌",
      "j1_body_050" to "血色のよい頬と肌",
      "j1_body_051" to "赤みがかった肌",
      "j1_body_052" to "ほんのり赤くなった肌",
    ),
  )

private fun optionGenre135(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・体型のスライダー表現（追加）",
    listOf(
    optionPart518(),
    optionPart519(),
    optionPart520(),
    optionPart521(),
    optionPart522(),
    optionPart523(),
    optionPart524(),
    ),
  )

private fun optionPart525(): CharacterOptionPart =
  CharacterOptionPart(
    "日焼け跡",
    false,
    listOf(
      "j1_skin_001" to "ワンピース水着型の日焼け跡",
      "j1_skin_003" to "ショーツ跡の日焼け",
      "j1_skin_005" to "ホルターネック型の日焼け跡",
      "j1_skin_006" to "Tシャツ焼け",
      "j1_skin_009" to "ネックレス跡の日焼け",
      "j1_skin_010" to "上半身だけの日焼け",
      "j1_skin_011" to "下半身だけの日焼け",
      "j1_skin_012" to "真っ黒な日焼け",
      "j1_skin_013" to "日焼けのむら",
      "j1_skin_014" to "日焼けの白い抜け",
    ),
  )

private fun optionPart526(): CharacterOptionPart =
  CharacterOptionPart(
    "ほくろ・そばかす・あざ",
    false,
    listOf(
      "j1_skin_015" to "お尻のほくろ",
      "j1_skin_016" to "腕のほくろ",
      "j1_skin_017" to "手の甲のほくろ",
      "j1_skin_018" to "そばかす（頬）",
      "j1_skin_019" to "そばかす（肩）",
      "j1_skin_020" to "そばかす（全身）",
      "j1_skin_021" to "そばかす（鼻まわり）",
      "j1_skin_022" to "あざのある肌",
      "j1_skin_023" to "ふたつのほくろ",
      "j1_skin_024" to "ほくろが散らばる",
      "j1_skin_025" to "帝王切開の跡のような横線",
    ),
  )

private fun optionPart527(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ボディペイント・シール",
    false,
    listOf(
      "j1_skin_026" to "腕のワンポイントタトゥー",
      "j1_skin_027" to "ふとももの花のタトゥー",
      "j1_skin_028" to "顔に貼ったシール",
      "j1_skin_029" to "腕の模様の線",
      "j1_skin_030" to "星や月のボディシール",
      "j1_skin_031" to "チョーカー風のタトゥー",
      "j1_skin_032" to "骨盤まわりの模様",
    ),
  )

private fun optionPart528(): CharacterOptionPart =
  CharacterOptionPart(
    "ピアス・穴・傷",
    false,
    listOf(
      "j1_skin_033" to "軟骨ピアス",
      "j1_skin_034" to "連なる耳のピアス",
      "j1_skin_035" to "ひじや肩の傷あと",
      "j1_skin_036" to "鼻すじの傷",
      "j1_skin_037" to "縫い目のような傷",
    ),
  )

private fun optionGenre136(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・肌・日焼け跡・印（追加）",
    listOf(
    optionPart525(),
    optionPart526(),
    optionPart527(),
    optionPart528(),
    ),
  )

private fun optionPart529(): CharacterOptionPart =
  CharacterOptionPart(
    "シャツ・ブラウス",
    true,
    listOf(
      "j1_top_001" to "半袖のカジュアルシャツ",
      "j1_top_002" to "長袖の無地ブラウス",
      "j1_top_003" to "フリルブラウス",
      "j1_top_004" to "パフスリーブのブラウス",
      "j1_top_005" to "オフィス風ブラウス",
      "j1_top_006" to "ノースリーブのシャツ",
      "j1_top_007" to "前結びのシャツ",
      "j1_top_008" to "シャツをズボンに入れた着方",
      "j1_top_009" to "袖まくりしたシャツ",
    ),
  )

private fun optionPart530(): CharacterOptionPart =
  CharacterOptionPart(
    "Tシャツ・カットソー",
    true,
    listOf(
      "j1_top_010" to "無地のTシャツ",
      "j1_top_011" to "Vネックのカットソー",
      "j1_top_012" to "丸首のカットソー",
      "j1_top_013" to "リブ編みのトップス",
      "j1_top_014" to "ハイネックのノースリーブ",
      "j1_top_015" to "袖なしのカーディガン",
    ),
  )

private fun optionPart531(): CharacterOptionPart =
  CharacterOptionPart(
    "ニット・パーカー・上着",
    true,
    listOf(
      "j1_top_016" to "ファーのついたコート",
    ),
  )

private fun optionGenre137(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・トップス（追加）",
    listOf(
    optionPart529(),
    optionPart530(),
    optionPart531(),
    ),
  )

private fun optionPart532(): CharacterOptionPart =
  CharacterOptionPart(
    "スカート",
    true,
    listOf(
      "j1_btm_001" to "花柄のスカート",
      "j1_btm_002" to "ベルト付きスカート",
    ),
  )

private fun optionPart533(): CharacterOptionPart =
  CharacterOptionPart(
    "パンツ・ショーツ",
    true,
    listOf(
      "j1_btm_003" to "ベルトで留めたパンツ",
    ),
  )

private fun optionPart534(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピース・ドレス",
    true,
    listOf(
      "j1_btm_004" to "夏のワンピース",
      "j1_btm_005" to "膝丈のワンピース",
      "j1_btm_006" to "ノースリーブのワンピース",
      "j1_btm_007" to "フリルのワンピース",
      "j1_btm_008" to "サロン風のワンピース",
    ),
  )

private fun optionGenre138(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・ボトムス・ワンピース（追加）",
    listOf(
    optionPart532(),
    optionPart533(),
    optionPart534(),
    ),
  )

private fun optionPart535(): CharacterOptionPart =
  CharacterOptionPart(
    "ブラ・ショーツ（上下）",
    true,
    listOf(
      "j1_wear_001" to "レースのブラとショーツ",
      "j1_wear_002" to "無地のコットン下着",
      "j1_wear_003" to "リボン付きの下着",
      "j1_wear_004" to "透けるメッシュの下着",
      "j1_wear_005" to "ローライズのショーツ",
      "j1_wear_006" to "花柄の下着",
      "j1_wear_007" to "ガーターベルト付きの下着",
      "j1_wear_008" to "コルセット風の下着",
      "j1_wear_009" to "ボディストッキング風の下着",
    ),
  )

private fun optionPart536(): CharacterOptionPart =
  CharacterOptionPart(
    "水着",
    true,
    listOf(
      "j1_wear_010" to "ハイウエストのビキニ",
      "j1_wear_011" to "水着の上にシャツ",
      "j1_wear_012" to "ストライプの水着",
      "j1_wear_013" to "花柄の水着",
      "j1_wear_014" to "黒の水着",
      "j1_wear_015" to "ハイネックの水着",
    ),
  )

private fun optionPart537(): CharacterOptionPart =
  CharacterOptionPart(
    "靴下・ストッキング",
    true,
    listOf(
      "j1_wear_016" to "白のくるぶし丈ソックス",
      "j1_wear_017" to "黒のくるぶし丈ソックス",
      "j1_wear_018" to "ひざ下までのソックス",
      "j1_wear_019" to "太もも丈のストッキング",
      "j1_wear_020" to "黒のタイツ",
      "j1_wear_021" to "透けるストッキング",
      "j1_wear_022" to "網目の大きなタイツ",
      "j1_wear_023" to "リボン付きのストッキング",
      "j1_wear_024" to "ストッキングの伝線",
      "j1_wear_025" to "片足だけ履いた靴下",
      "j1_wear_026" to "フットカバー",
    ),
  )

private fun optionPart538(): CharacterOptionPart =
  CharacterOptionPart(
    "靴・サンダル",
    true,
    listOf(
      "j1_wear_027" to "ヒールのサンダル",
      "j1_wear_028" to "ルームシューズ",
      "j1_wear_029" to "オープントゥの靴",
      "j1_wear_030" to "つま先の見える靴",
      "j1_wear_031" to "靴を脱いだ足",
    ),
  )

private fun optionPart539(): CharacterOptionPart =
  CharacterOptionPart(
    "手袋・アームウェア",
    true,
    listOf(
      "j1_wear_032" to "ニットの手袋",
      "j1_wear_033" to "手首までの短い手袋",
      "j1_wear_034" to "ブレスレット風の手袋",
      "j1_wear_035" to "シルクの手袋",
      "j1_wear_036" to "手袋を外す仕草",
    ),
  )

private fun optionGenre139(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・下着・水着・靴下・靴・手袋（追加）",
    listOf(
    optionPart535(),
    optionPart536(),
    optionPart537(),
    optionPart538(),
    optionPart539(),
    ),
  )

private fun optionPart540(): CharacterOptionPart =
  CharacterOptionPart(
    "頭・髪まわり",
    false,
    listOf(
      "j1_acc_001" to "リボンのカチューシャ",
      "j1_acc_002" to "小さなリボン",
      "j1_acc_003" to "ヘアゴムの飾り",
      "j1_acc_004" to "キツネミミのカチューシャ",
    ),
  )

private fun optionPart541(): CharacterOptionPart =
  CharacterOptionPart(
    "顔まわり",
    false,
    listOf(
      "j1_acc_005" to "太縁メガネ",
      "j1_acc_006" to "ハート形のサングラス",
      "j1_acc_007" to "布のマスク",
      "j1_acc_008" to "口もとのスカーフ",
      "j1_acc_009" to "つけぼくろ",
    ),
  )

private fun optionPart542(): CharacterOptionPart =
  CharacterOptionPart(
    "首・胸元",
    false,
    listOf(
      "j1_acc_010" to "ハートのペンダント",
      "j1_acc_011" to "襟の飾り",
      "j1_acc_012" to "ドッグタグ風のペンダント",
    ),
  )

private fun optionPart543(): CharacterOptionPart =
  CharacterOptionPart(
    "耳・手・腕・脚",
    false,
    listOf(
      "j1_acc_013" to "ロングイヤリング",
      "j1_acc_014" to "薬指の指輪",
      "j1_acc_015" to "手首のスカーフ",
    ),
  )

private fun optionPart544(): CharacterOptionPart =
  CharacterOptionPart(
    "バッグ・持ち物",
    false,
    listOf(
      "j1_acc_016" to "ペットボトル",
      "j1_acc_017" to "缶ジュース",
      "j1_acc_018" to "ケーキの箱",
    ),
  )

private fun optionGenre140(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・アクセサリーの種類（追加）",
    listOf(
    optionPart540(),
    optionPart541(),
    optionPart542(),
    optionPart543(),
    optionPart544(),
    ),
  )

private fun optionPart545(): CharacterOptionPart =
  CharacterOptionPart(
    "アイメイク",
    false,
    listOf(
      "j1_mk_001" to "オレンジのアイシャドウ",
      "j1_mk_003" to "まぶたの端に赤み",
      "j1_mk_004" to "涙ぶくろのハイライト",
      "j1_mk_005" to "太いまつげメイク",
      "j1_mk_006" to "アイラインを太く入れたメイク",
      "j1_mk_007" to "グリッターアイライナー",
    ),
  )

private fun optionPart546(): CharacterOptionPart =
  CharacterOptionPart(
    "口紅・リップ",
    true,
    listOf(
      "j1_mk_008" to "ピンクの口紅",
      "j1_mk_010" to "縁取りのリップ",
      "j1_mk_011" to "ティントのリップ",
      "j1_mk_012" to "ラメ入りリップ",
      "j1_mk_013" to "ほんのり色づいた唇",
    ),
  )

private fun optionPart547(): CharacterOptionPart =
  CharacterOptionPart(
    "チーク・肌のメイク",
    false,
    listOf(
      "j1_mk_015" to "鼻先の赤み",
      "j1_mk_016" to "目のまわりの赤み",
      "j1_mk_017" to "首すじの赤み",
      "j1_mk_018" to "ガーリーメイク",
      "j1_mk_019" to "パーティーメイク",
      "j1_mk_020" to "ラメを散らしたメイク",
      "j1_mk_021" to "星のペイントメイク",
    ),
  )

private fun optionPart548(): CharacterOptionPart =
  CharacterOptionPart(
    "ネイル",
    false,
    listOf(
      "j1_mk_023" to "花柄のネイル",
      "j1_mk_025" to "透明なネイル",
      "j1_mk_027" to "爪を塗りかけ",
    ),
  )

private fun optionGenre141(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・メイク（追加）",
    listOf(
    optionPart545(),
    optionPart546(),
    optionPart547(),
    optionPart548(),
    ),
  )

private fun optionPart549(): CharacterOptionPart =
  CharacterOptionPart(
    "性格のタイプ",
    true,
    listOf(
      "j1_pers_001" to "おっとりした性格",
      "j1_pers_002" to "しっかり者の性格",
      "j1_pers_003" to "活発で明るい性格",
      "j1_pers_004" to "内気で控えめな性格",
      "j1_pers_005" to "クールで冷静な性格",
      "j1_pers_006" to "人懐っこい性格",
      "j1_pers_007" to "きまじめな性格",
      "j1_pers_008" to "のんびり屋",
      "j1_pers_009" to "負けず嫌いな性格",
      "j1_pers_010" to "世話好きな性格",
      "j1_pers_011" to "自由奔放な性格",
      "j1_pers_012" to "ミステリアスな性格",
      "j1_pers_013" to "毒舌な性格",
      "j1_pers_014" to "天然な性格",
      "j1_pers_015" to "クーデレ",
      "j1_pers_016" to "ヤンデレ風（愛が重い）",
      "j1_pers_017" to "お嬢様気質",
      "j1_pers_018" to "ギャル気質",
      "j1_pers_019" to "清楚で上品な性格",
      "j1_pers_020" to "お姉さん気質",
      "j1_pers_021" to "ボクっ娘",
      "j1_pers_023" to "体育会系の性格",
      "j1_pers_024" to "さびしがり屋",
      "j1_pers_025" to "気まぐれな性格",
      "j1_pers_026" to "頼れる姉御肌",
      "j1_pers_027" to "甘えん坊な性格",
      "j1_pers_028" to "おせっかいな性格",
      "j1_pers_029" to "ポジティブな性格",
      "j1_pers_030" to "ネガティブな性格",
      "j1_pers_031" to "好奇心旺盛な性格",
      "j1_pers_032" to "ミーハーな性格",
      "j1_pers_033" to "マイペースな性格",
      "j1_pers_034" to "強がりな性格",
      "j1_pers_035" to "泣き虫な性格",
    ),
  )

private fun optionPart550(): CharacterOptionPart =
  CharacterOptionPart(
    "好みと習慣",
    false,
    listOf(
      "j1_pers_036" to "音楽が好き",
      "j1_pers_037" to "読書が好き",
      "j1_pers_038" to "運動が好き",
      "j1_pers_039" to "料理が好き",
      "j1_pers_040" to "お菓子作りが好き",
      "j1_pers_041" to "ゲームが好き",
      "j1_pers_042" to "おしゃれが好き",
      "j1_pers_043" to "お酒が好き",
      "j1_pers_044" to "散歩が好き",
      "j1_pers_045" to "旅行が好き",
      "j1_pers_046" to "寝るのが好き",
      "j1_pers_047" to "おしゃべりが好き",
      "j1_pers_048" to "ひとりが好き",
      "j1_pers_049" to "甘いものが好き",
      "j1_pers_050" to "辛いものが好き",
      "j1_pers_051" to "お風呂が好き",
      "j1_pers_052" to "朝型",
      "j1_pers_053" to "夜型",
      "j1_pers_054" to "方向音痴",
      "j1_pers_055" to "忘れっぽい",
      "j1_pers_056" to "きれい好き",
      "j1_pers_057" to "少食",
      "j1_pers_058" to "おでかけ好き",
      "j1_pers_059" to "インドア派",
      "j1_pers_060" to "アウトドア派",
    ),
  )

private fun optionPart551(): CharacterOptionPart =
  CharacterOptionPart(
    "口調・話し方",
    true,
    listOf(
      "j1_pers_062" to "ていねいな敬語",
      "j1_pers_063" to "やわらかい話し方",
      "j1_pers_064" to "ぶっきらぼうな話し方",
      "j1_pers_065" to "くだけた話し方",
      "j1_pers_066" to "お嬢様ことば",
      "j1_pers_067" to "方言まじりの話し方",
      "j1_pers_068" to "関西弁",
      "j1_pers_069" to "のんびりした話し方",
      "j1_pers_070" to "早口",
      "j1_pers_071" to "小声でぼそぼそ話す",
      "j1_pers_072" to "はきはきした話し方",
      "j1_pers_073" to "語尾をのばす話し方",
      "j1_pers_074" to "丁寧だが距離のある話し方",
      "j1_pers_075" to "甘えた話し方",
      "j1_pers_076" to "冷たい話し方",
      "j1_pers_077" to "低く落ち着いた声",
      "j1_pers_078" to "高くて明るい声",
      "j1_pers_079" to "透きとおる声",
      "j1_pers_080" to "笑いながら話す",
      "j1_pers_081" to "ため息まじりの話し方",
      "j1_pers_082" to "ひとりごとが多い",
      "j1_pers_083" to "口ぐせのある話し方",
      "j1_pers_084" to "古風な話し方",
    ),
  )

private fun optionPart552(): CharacterOptionPart =
  CharacterOptionPart(
    "しぐさ・くせ",
    false,
    listOf(
      "j1_pers_085" to "髪をなでつける",
      "j1_pers_086" to "そわそわと手を動かす",
      "j1_pers_087" to "指で口もとを隠す",
      "j1_pers_088" to "横目で見る",
      "j1_pers_090" to "小さく手を振る",
      "j1_pers_091" to "ひとさし指を立てる",
      "j1_pers_092" to "爪をかむ",
      "j1_pers_093" to "スカートのすそをつまむ",
      "j1_pers_094" to "髪の毛先をいじる",
      "j1_pers_095" to "頬に両手を当てる",
      "j1_pers_096" to "もじもじと脚をすり合わせる",
      "j1_pers_097" to "口を押さえて笑う",
    ),
  )

private fun optionGenre142(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・性格と口調としぐさ（追加）",
    listOf(
    optionPart549(),
    optionPart550(),
    optionPart551(),
    optionPart552(),
    ),
  )

private fun optionPart553(): CharacterOptionPart =
  CharacterOptionPart(
    "やさしい表情",
    true,
    listOf(
      "j1_expr_001" to "はにかんだ笑顔",
      "j1_expr_002" to "ほんのり微笑む",
      "j1_expr_003" to "目を細めた笑顔",
      "j1_expr_005" to "おだやかな無表情",
      "j1_expr_006" to "ほっとした表情",
      "j1_expr_007" to "うっとり見つめる",
      "j1_expr_008" to "夢見るような表情",
      "j1_expr_009" to "満足そうな表情",
      "j1_expr_010" to "安心した表情",
      "j1_expr_011" to "見守るような表情",
      "j1_expr_012" to "笑いをこらえる表情",
      "j1_expr_013" to "楽しそうに笑う",
      "j1_expr_014" to "目を輝かせる",
    ),
  )

private fun optionPart554(): CharacterOptionPart =
  CharacterOptionPart(
    "感情の強い表情",
    true,
    listOf(
      "j1_expr_015" to "驚いた表情",
      "j1_expr_016" to "困った表情",
      "j1_expr_017" to "あせった表情",
      "j1_expr_018" to "怒った表情",
      "j1_expr_019" to "むっとした表情",
      "j1_expr_020" to "泣きそうな表情",
      "j1_expr_021" to "悲しい表情",
      "j1_expr_022" to "不安げな表情",
      "j1_expr_023" to "疑うような表情",
      "j1_expr_024" to "得意げな表情",
      "j1_expr_025" to "うんざりした表情",
      "j1_expr_026" to "眠そうな表情",
      "j1_expr_027" to "冷めた表情",
      "j1_expr_028" to "見下すような表情",
      "j1_expr_029" to "けげんな表情",
      "j1_expr_030" to "決意した表情",
      "j1_expr_031" to "考え込む表情",
    ),
  )

private fun optionPart555(): CharacterOptionPart =
  CharacterOptionPart(
    "目線の向き",
    true,
    listOf(
      "j1_expr_032" to "見上げる目線",
      "j1_expr_033" to "見下ろす目線",
    ),
  )

private fun optionPart556(): CharacterOptionPart =
  CharacterOptionPart(
    "口もとの動き",
    true,
    listOf(
      "j1_expr_035" to "口を大きく開ける",
      "j1_expr_036" to "あくびの口",
      "j1_expr_037" to "息をのむ口もと",
      "j1_expr_038" to "笑うと見える歯",
      "j1_expr_039" to "声を殺すような口もと",
    ),
  )

private fun optionGenre143(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・表情と目線（追加）",
    listOf(
    optionPart553(),
    optionPart554(),
    optionPart555(),
    optionPart556(),
    ),
  )

private fun optionPart557(): CharacterOptionPart =
  CharacterOptionPart(
    "室内の場所",
    true,
    listOf(
      "j1_place_001" to "ベッドの端に座る",
      "j1_place_002" to "カーテン越しの窓辺",
      "j1_place_003" to "リビングのソファ",
      "j1_place_004" to "キッチンのカウンター",
      "j1_place_005" to "玄関",
      "j1_place_006" to "書斎の机",
      "j1_place_007" to "クローゼットの前",
      "j1_place_008" to "洗面所の鏡の前",
      "j1_place_009" to "バスタブの中",
      "j1_place_010" to "洗濯室",
      "j1_place_011" to "畳の和室",
      "j1_place_012" to "こたつのある部屋",
      "j1_place_013" to "ホテルの大きなベッド",
      "j1_place_014" to "ホテルのバルコニー",
      "j1_place_015" to "ネットカフェ個室",
      "j1_place_016" to "映画館の座席",
      "j1_place_017" to "車の中（助手席）",
      "j1_place_018" to "電車の座席",
      "j1_place_019" to "バスの座席",
      "j1_place_020" to "船室",
    ),
  )

private fun optionPart558(): CharacterOptionPart =
  CharacterOptionPart(
    "屋外の場所",
    true,
    listOf(
      "j1_place_021" to "夏の海辺",
      "j1_place_022" to "砂浜のパラソルの下",
      "j1_place_023" to "温泉の露天風呂",
      "j1_place_024" to "川辺",
      "j1_place_025" to "滝のそば",
      "j1_place_026" to "森の小道",
      "j1_place_027" to "木漏れ日の公園",
      "j1_place_028" to "公園のベンチ",
      "j1_place_029" to "橋の上",
      "j1_place_030" to "夜の街角",
      "j1_place_031" to "観覧車の中",
      "j1_place_032" to "水族館",
      "j1_place_033" to "動物園",
      "j1_place_034" to "縁日の屋台",
      "j1_place_035" to "花火大会の河原",
      "j1_place_036" to "雪の積もった道",
      "j1_place_037" to "ゲレンデ",
    ),
  )

private fun optionPart559(): CharacterOptionPart =
  CharacterOptionPart(
    "時間帯・光",
    true,
    listOf(
      "j1_place_038" to "朝日がさす",
      "j1_place_039" to "朝のやわらかい光",
      "j1_place_040" to "昼の強い日ざし",
      "j1_place_041" to "午後のおだやかな光",
      "j1_place_042" to "夜の街灯の下",
      "j1_place_043" to "月明かりの夜",
      "j1_place_044" to "星空の下",
      "j1_place_045" to "深夜の暗い部屋",
      "j1_place_046" to "スタンドライトのやわらかい光",
      "j1_place_047" to "テレビの光だけの部屋",
      "j1_place_048" to "窓からの斜光",
      "j1_place_049" to "フラッシュの光",
      "j1_place_050" to "木漏れ日",
    ),
  )

private fun optionPart560(): CharacterOptionPart =
  CharacterOptionPart(
    "天気・季節",
    true,
    listOf(
      "j1_place_051" to "くもり空",
      "j1_place_052" to "強い雨",
      "j1_place_053" to "雨上がりの虹",
      "j1_place_054" to "吹雪",
      "j1_place_055" to "風の強い日",
      "j1_place_056" to "霧の朝",
      "j1_place_057" to "梅雨の雨",
      "j1_place_058" to "落ち葉の道",
      "j1_place_059" to "真冬の寒さ",
      "j1_place_060" to "蒸し暑い夏の日",
      "j1_place_061" to "雷の音におびえる空",
    ),
  )

private fun optionGenre144(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・場所・時間帯・天気（追加）",
    listOf(
    optionPart557(),
    optionPart558(),
    optionPart559(),
    optionPart560(),
    ),
  )

private fun optionPart561(): CharacterOptionPart =
  CharacterOptionPart(
    "上半身の乱れ",
    false,
    listOf(
      "j1_mess_001" to "襟元がゆるんだ服",
      "j1_mess_002" to "ボタンが2つ外れた胸元",
      "j1_mess_003" to "肩がずれた服",
      "j1_mess_004" to "片肩だけずれたトップス",
      "j1_mess_005" to "上着を肩までずらす",
      "j1_mess_006" to "胸を腕でかくす",
      "j1_mess_007" to "ブラがずれている",
      "j1_mess_008" to "胸の谷間がのぞく",
      "j1_mess_009" to "シャツの裾を結んだ",
      "j1_mess_010" to "服が汗で貼りつく",
      "j1_mess_011" to "服の下に下着が透ける",
      "j1_mess_012" to "上着をはおっただけ",
      "j1_mess_013" to "肩までおろしたワンピース",
      "j1_mess_014" to "襟もとから見える鎖骨",
    ),
  )

private fun optionPart562(): CharacterOptionPart =
  CharacterOptionPart(
    "下半身の乱れ",
    false,
    listOf(
      "j1_mess_015" to "めくれたスカート",
      "j1_mess_016" to "風でふわりとめくれるスカート",
      "j1_mess_017" to "ずれたスカート",
      "j1_mess_018" to "ウエストが折り返されたスカート",
      "j1_mess_019" to "ももの途中で止まった下着",
      "j1_mess_020" to "めくれた裾から見える太もも",
      "j1_mess_021" to "ストッキングが破れている",
      "j1_mess_022" to "ストッキングを脱ぎかけ",
      "j1_mess_023" to "靴下が片方脱げた",
      "j1_mess_024" to "脱ぎかけのスカート",
      "j1_mess_025" to "膝までおろしたスカート",
      "j1_mess_026" to "履き途中のズボン",
    ),
  )

private fun optionPart563(): CharacterOptionPart =
  CharacterOptionPart(
    "濡れ・水まわり",
    false,
    listOf(
      "j1_mess_028" to "泡だらけの体",
      "j1_mess_029" to "湯気の立つ体",
      "j1_mess_030" to "バスタオルを巻く",
      "j1_mess_031" to "濡れて透ける白いシャツ",
      "j1_mess_032" to "プールサイドで濡れる",
      "j1_mess_033" to "水をかぶった顔",
      "j1_mess_034" to "あごから落ちる水滴",
      "j1_mess_035" to "温泉で赤くなる肌",
    ),
  )

private fun optionGenre145(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・衣装の着崩れ・乱れ方（追加）",
    listOf(
    optionPart561(),
    optionPart562(),
    optionPart563(),
    ),
  )

private fun optionPart564(): CharacterOptionPart =
  CharacterOptionPart(
    "寄り添う・抱き合う",
    true,
    listOf(
      "j1_adult_001" to "向かい合って抱きしめる",
      "j1_adult_002" to "腕に抱きつく",
      "j1_adult_003" to "見つめ合って額を寄せる",
      "j1_adult_004" to "ひざまくらで耳かき",
      "j1_adult_005" to "背中合わせに座る",
      "j1_adult_006" to "ベッドで並んで眠る",
      "j1_adult_007" to "毛布を共有する",
      "j1_adult_008" to "ふたりで傘に入る",
      "j1_adult_009" to "マフラーを共有する",
    ),
  )

private fun optionPart565(): CharacterOptionPart =
  CharacterOptionPart(
    "キス・触れ合い",
    true,
    listOf(
      "j1_adult_010" to "軽いキス",
      "j1_adult_011" to "ほおにキス",
      "j1_adult_012" to "額にキス",
      "j1_adult_013" to "鎖骨へのキス",
      "j1_adult_014" to "おなかへのキス",
      "j1_adult_015" to "唇を重ねたまま見つめる",
      "j1_adult_016" to "キスの直前",
      "j1_adult_017" to "頬をなでる",
      "j1_adult_018" to "背中をなでる",
      "j1_adult_019" to "肩をもむ",
      "j1_adult_020" to "手を重ねる",
    ),
  )

private fun optionPart566(): CharacterOptionPart =
  CharacterOptionPart(
    "体位・体の向き（合意の成人同士）",
    true,
    listOf(
      "j1_adult_021" to "側位（横向き）",
      "j1_adult_022" to "対面座位",
      "j1_adult_023" to "背面座位",
      "j1_adult_024" to "立ちバック",
      "j1_adult_025" to "駅弁の体勢",
      "j1_adult_026" to "壁に手をつく",
      "j1_adult_027" to "机に手をつく",
      "j1_adult_028" to "ベッドの縁で",
      "j1_adult_029" to "ソファの背にもたれる",
      "j1_adult_030" to "逆騎乗位",
      "j1_adult_031" to "開脚して座る",
      "j1_adult_032" to "脚を肩にのせる",
      "j1_adult_033" to "上から覆いかぶさる",
      "j1_adult_034" to "組み合わさる体勢",
      "j1_adult_035" to "ひざ立ち",
      "j1_adult_036" to "向かい合って立つ",
      "j1_adult_037" to "座った相手にまたがる",
      "j1_adult_038" to "膝を抱えて寝る",
      "j1_adult_039" to "腰に脚を回す",
    ),
  )

private fun optionPart567(): CharacterOptionPart =
  CharacterOptionPart(
    "口や手を使う愛撫",
    true,
    listOf(
      "j1_adult_040" to "口での愛撫",
      "j1_adult_041" to "胸の愛撫",
      "j1_adult_042" to "互いを同時に愛撫",
      "j1_adult_043" to "下着の上からの愛撫",
      "j1_adult_044" to "服の上からの愛撫",
      "j1_adult_045" to "背中を舌でなぞる",
      "j1_adult_046" to "首すじを吸う",
      "j1_adult_047" to "乳首へのキス",
      "j1_adult_048" to "おへそへのキス",
      "j1_adult_049" to "太ももへのキス",
      "j1_adult_050" to "足への愛撫",
      "j1_adult_051" to "足指をなめる",
      "j1_adult_052" to "髪で包むような愛撫",
    ),
  )

private fun optionPart568(): CharacterOptionPart =
  CharacterOptionPart(
    "感情・余韻の表現",
    false,
    listOf(
      "j1_adult_053" to "汗ばんだ体で寄り添う",
      "j1_adult_054" to "互いを見つめて微笑む",
      "j1_adult_055" to "熱を帯びた目",
      "j1_adult_056" to "恥ずかしそうに顔をかくす",
      "j1_adult_057" to "枕に顔をうずめる",
      "j1_adult_058" to "ぎゅっと手を握る",
    ),
  )

private fun optionGenre146(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コイカツ系・成人同士の合意の親密な場面（追加）",
    listOf(
    optionPart564(),
    optionPart565(),
    optionPart566(),
    optionPart567(),
    optionPart568(),
    ),
  )

private fun optionPart569(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の土台タイプ",
    true,
    listOf(
      "j2_face_soft" to "やわらかい印象の顔立ち",
      "j2_face_sharp" to "キリッとした大人の顔立ち",
      "j2_face_cool" to "クールな顔立ち",
      "j2_face_cute_adult" to "愛らしい大人の顔立ち",
      "j2_face_elegant" to "上品で端正な顔立ち",
      "j2_face_androgynous" to "中性的な顔立ち",
      "j2_face_handsome" to "凛々しい顔立ち",
      "j2_face_chiseled" to "彫りの深い顔立ち",
      "j2_face_heart" to "ハート形の輪郭",
      "j2_face_round_cheek" to "ふっくらした頬の輪郭",
      "j2_face_slim_jaw" to "シャープな顎の輪郭",
    ),
  )

private fun optionPart570(): CharacterOptionPart =
  CharacterOptionPart(
    "目の形と目つき",
    true,
    listOf(
      "j2_eye_big_round" to "大きな丸い目",
      "j2_eye_narrow_calm" to "細くて穏やかな目",
      "j2_eye_upper_lash_only" to "上まつげだけ強調",
    ),
  )

private fun optionPart571(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳・ハイライトの種類",
    false,
    listOf(
      "j2_pupil_star" to "星型のハイライト",
      "j2_pupil_double_highlight" to "二重のハイライト",
    ),
  )

private fun optionPart572(): CharacterOptionPart =
  CharacterOptionPart(
    "眉の形",
    true,
    listOf(
      "j2_brow_forked" to "二股に分かれた眉",
      "j2_brow_light" to "薄く色の淡い眉",
      "j2_brow_sharp_angle" to "角度のついた眉",
      "j2_brow_hidden" to "前髪に隠れた眉",
    ),
  )

private fun optionPart573(): CharacterOptionPart =
  CharacterOptionPart(
    "口・唇の形",
    true,
    listOf(
      "j2_mouth_full_lips" to "ふっくらした唇",
      "j2_mouth_cat" to "ω形の口（猫口）",
      "j2_mouth_smile_closed" to "口を閉じた微笑み",
      "j2_mouth_o" to "丸く開いた口",
      "j2_mouth_wavy" to "波打つ口",
    ),
  )

private fun optionPart574(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻・耳・ほくろ",
    false,
    listOf(
      "j2_nose_high" to "鼻筋の通った鼻",
      "j2_ear_pointy_j2" to "とがった耳",
      "j2_ear_long_elf" to "長い耳",
      "j2_ear_round" to "丸い耳",
      "j2_ear_hidden" to "髪に隠れた耳",
      "j2_mole_chin" to "あごのほくろ",
    ),
  )

private fun optionGenre147(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・顔のパーツ（追加）",
    listOf(
    optionPart569(),
    optionPart570(),
    optionPart571(),
    optionPart572(),
    optionPart573(),
    optionPart574(),
    ),
  )

private fun optionPart575(): CharacterOptionPart =
  CharacterOptionPart(
    "基本の表情",
    true,
    listOf(
      "j2_ex_neutral_calm" to "落ち着いた無表情",
      "j2_ex_gentle_smile" to "おだやかな微笑み",
      "j2_ex_big_grin" to "満面の笑み",
      "j2_ex_laugh_closed" to "目を閉じて笑う",
      "j2_ex_sob" to "大泣き",
      "j2_ex_tear_up" to "涙ぐむ",
      "j2_ex_pout_sulk" to "むくれた顔",
      "j2_ex_surprised" to "おどろいた顔",
      "j2_ex_shy" to "はずかしそうな顔",
      "j2_ex_smug" to "得意げなドヤ顔",
      "j2_ex_confused" to "こまった顔",
      "j2_ex_angry_mild" to "ぷんぷん怒る顔",
      "j2_ex_dazed" to "ぼんやりした顔",
      "j2_ex_proud" to "胸を張る得意顔",
      "j2_ex_stage_smile" to "ステージ向けの笑顔",
    ),
  )

private fun optionPart576(): CharacterOptionPart =
  CharacterOptionPart(
    "配信で使う演出つきの表情",
    false,
    listOf(
      "j2_ex_star_eyes" to "星の目",
      "j2_ex_blush_lines" to "頬の赤み線",
      "j2_ex_shock_lines" to "ショックの縦線",
      "j2_ex_sparkle_bg" to "きらめきの演出",
      "j2_ex_flower_bg" to "花が咲く演出",
      "j2_ex_dark_face" to "影が落ちた顔",
      "j2_ex_ellipsis" to "「…」の吹き出し顔",
    ),
  )

private fun optionGenre148(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・表情（追加）",
    listOf(
    optionPart575(),
    optionPart576(),
    ),
  )

private fun optionPart577(): CharacterOptionPart =
  CharacterOptionPart(
    "前髪・もみあげ",
    true,
    listOf(
      "j2_hair_bangs_wispy" to "透け感のある前髪",
      "j2_hair_sidelocks_curl" to "カールしたもみあげ",
    ),
  )

private fun optionPart578(): CharacterOptionPart =
  CharacterOptionPart(
    "後ろ髪・まとめ髪",
    true,
    listOf(
      "j2_hair_back_straight" to "まっすぐ下ろした後ろ髪",
    ),
  )

private fun optionPart579(): CharacterOptionPart =
  CharacterOptionPart(
    "髪のパーツ・毛先",
    false,
    listOf(
      "j2_hair_ahoge_double" to "アホ毛（2本）",
      "j2_hair_ombre" to "毛先のグラデーション",
      "j2_hair_ends_curl" to "毛先のカール",
      "j2_hair_highlight_ring" to "天使の輪（つや）",
      "j2_hair_floating" to "ふわっと浮いた髪",
      "j2_hair_wet_look" to "濡れたような質感の髪",
      "j2_hair_glitter" to "ラメ入りの髪",
      "j2_hair_flowing_strands" to "風になびく後れ毛",
    ),
  )

private fun optionPart580(): CharacterOptionPart =
  CharacterOptionPart(
    "髪色（追加）",
    true,
    listOf(
      "j2_hc_navy" to "ネイビー",
      "j2_hc_silver_blue" to "銀青色",
    ),
  )

private fun optionGenre149(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・髪（追加）",
    listOf(
    optionPart577(),
    optionPart578(),
    optionPart579(),
    optionPart580(),
    ),
  )

private fun optionPart581(): CharacterOptionPart =
  CharacterOptionPart(
    "体のバランス",
    true,
    listOf(
      "j2_body_tall_slim" to "背が高くほっそり",
      "j2_body_slim_arms" to "細い腕",
      "j2_body_tall_big" to "大柄でがっしり",
    ),
  )

private fun optionPart582(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の色・質感",
    true,
    listOf(
      "j2_skin_pale_blush" to "透明感のある肌",
      "j2_skin_bronze" to "ブロンズ色の肌",
      "j2_skin_pink_tone" to "ピンク寄りの肌",
      "j2_skin_lavender" to "薄紫の肌（異種族）",
      "j2_skin_sparkle" to "ラメを散らした肌",
      "j2_skin_tan_line_j2" to "水着の日焼け跡",
    ),
  )

private fun optionPart583(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の模様・印",
    false,
    listOf(
      "j2_mark_whisker" to "ほおのひげ模様",
      "j2_mark_cheek_star" to "頬の星マーク",
      "j2_mark_scale_patch" to "うろこ状の肌",
      "j2_mark_vein_pattern" to "光る模様の線",
      "j2_mark_spot" to "斑点模様",
      "j2_mark_tribal" to "部族風の模様",
    ),
  )

private fun optionGenre150(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・体型と肌（追加）",
    listOf(
    optionPart581(),
    optionPart582(),
    optionPart583(),
    ),
  )

private fun optionPart584(): CharacterOptionPart =
  CharacterOptionPart(
    "洋服のトップス",
    true,
    listOf(
      "j2_top_cropped" to "丈の短いトップス",
      "j2_top_blouse_ribbon" to "リボン付きブラウス",
      "j2_top_shirt_open" to "シャツを羽織る",
      "j2_top_vest" to "ベスト",
      "j2_top_sleeveless_knit" to "ノースリーブニット",
    ),
  )

private fun optionPart585(): CharacterOptionPart =
  CharacterOptionPart(
    "ボトムス",
    true,
    listOf(
      "j2_btm_pleated_j2" to "プリーツスカート",
    ),
  )

private fun optionPart586(): CharacterOptionPart =
  CharacterOptionPart(
    "ワンピース・ドレス",
    true,
    listOf(
      "j2_dress_maxi" to "ロングワンピース",
      "j2_dress_pinafore" to "ジャンパースカート",
      "j2_dress_party" to "パーティードレス",
      "j2_dress_slit" to "スリット入りドレス",
    ),
  )

private fun optionPart587(): CharacterOptionPart =
  CharacterOptionPart(
    "和装・民族衣装",
    true,
    listOf(
      "j2_jp_kimono_komon" to "小紋の着物",
      "j2_jp_miko_j2" to "巫女装束",
      "j2_jp_happi" to "はっぴ",
      "j2_jp_modern_wa" to "和モダンの衣装",
    ),
  )

private fun optionPart588(): CharacterOptionPart =
  CharacterOptionPart(
    "水着・ルームウェア",
    true,
    listOf(
      "j2_swim_sarong_cover" to "水着の上に羽織る薄手の服",
      "j2_swim_float" to "浮き輪",
      "j2_room_loungewear" to "ゆったりルームウェア",
    ),
  )

private fun optionPart589(): CharacterOptionPart =
  CharacterOptionPart(
    "靴・靴下・脚まわり",
    true,
    listOf(
      "j2_shoe_platform" to "厚底靴",
    ),
  )

private fun optionGenre151(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・衣装（追加）",
    listOf(
    optionPart584(),
    optionPart585(),
    optionPart586(),
    optionPart587(),
    optionPart588(),
    optionPart589(),
    ),
  )

private fun optionPart590(): CharacterOptionPart =
  CharacterOptionPart(
    "ステージ衣装",
    true,
    listOf(
      "j2_stg_idol_frill" to "フリルたっぷりのアイドル衣装",
      "j2_stg_tuxedo_female" to "タキシード風の衣装",
      "j2_stg_military_stage" to "軍服風のステージ衣装",
      "j2_stg_band" to "バンド衣装",
      "j2_stg_dance_unit" to "ダンスユニット風の衣装",
      "j2_stg_diva" to "ドレープのあるディーバ衣装",
      "j2_stg_fantasy_armor_light" to "軽装の騎士風衣装",
      "j2_stg_cyber" to "サイバーパンク風の衣装",
      "j2_stg_gothic" to "ゴシック調の衣装",
      "j2_stg_steampunk" to "スチームパンク風の衣装",
      "j2_stg_space" to "宇宙服風の衣装",
      "j2_stg_racing" to "レーシングスーツ風の衣装",
      "j2_stg_mascot" to "マスコット風の着ぐるみ衣装",
      "j2_stg_vtuber_debut" to "デビュー配信の初期衣装",
      "j2_stg_anniv" to "記念配信用の特別衣装",
      "j2_stg_seasonal_xmas" to "クリスマス衣装",
      "j2_stg_seasonal_newyear" to "お正月衣装",
    ),
  )

private fun optionPart591(): CharacterOptionPart =
  CharacterOptionPart(
    "職業・役の衣装",
    true,
    listOf(
      "j2_job_butler" to "執事服",
      "j2_job_nurse_adult" to "看護師風の衣装",
      "j2_job_barista" to "カフェの店員衣装",
      "j2_job_pilot" to "パイロット風の衣装",
      "j2_job_police_style" to "警察官風の衣装",
      "j2_job_detective" to "探偵風の衣装",
    ),
  )

private fun optionGenre152(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・ステージ衣装と配信演出（追加）",
    listOf(
    optionPart590(),
    optionPart591(),
    ),
  )

private fun optionPart592(): CharacterOptionPart =
  CharacterOptionPart(
    "頭まわりのアクセサリー",
    false,
    listOf(
      "j2_acc_horn_headband" to "角つきカチューシャ",
    ),
  )

private fun optionPart593(): CharacterOptionPart =
  CharacterOptionPart(
    "顔まわり・首まわり",
    false,
    listOf(
      "j2_acc_mask_fashion" to "ファッション用のマスク",
      "j2_acc_earring_drop" to "しずく形のイヤリング",
    ),
  )

private fun optionGenre153(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・アクセサリー（追加）",
    listOf(
    optionPart592(),
    optionPart593(),
    ),
  )

private fun optionPart594(): CharacterOptionPart =
  CharacterOptionPart(
    "目元のメイク",
    false,
    listOf(
      "j2_mk_eyeshadow_smoky" to "スモーキーなアイシャドウ",
      "j2_mk_under_eye_pearl" to "涙袋のパール",
      "j2_mk_lower_lash_line" to "下まつげのライン",
    ),
  )

private fun optionPart595(): CharacterOptionPart =
  CharacterOptionPart(
    "口元・頬のメイク",
    true,
    listOf(
      "j2_mk_lip_dark" to "深い色のリップ",
      "j2_mk_highlight_cheek" to "頬のハイライト",
    ),
  )

private fun optionPart596(): CharacterOptionPart =
  CharacterOptionPart(
    "顔のシール・飾り",
    false,
    listOf(
      "j2_mk_sticker_heart" to "ハートのフェイスシール",
      "j2_mk_tear_mark" to "涙のペイント",
    ),
  )

private fun optionGenre154(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・メイク（追加）",
    listOf(
    optionPart594(),
    optionPart595(),
    optionPart596(),
    ),
  )

private fun optionPart597(): CharacterOptionPart =
  CharacterOptionPart(
    "立ちポーズ",
    true,
    listOf(
      "j2_pose_hair_twirl" to "髪をくるくる触る",
      "j2_pose_lean_forward" to "前かがみで覗き込む",
      "j2_pose_stretch" to "のびをする",
      "j2_pose_victory_arms" to "両手を上げて喜ぶ",
      "j2_pose_curtsy" to "ドレスをつまむお辞儀",
      "j2_pose_pointing" to "指をさす",
    ),
  )

private fun optionPart598(): CharacterOptionPart =
  CharacterOptionPart(
    "座りポーズ",
    true,
    listOf(
      "j2_sit_chair_legs_cross" to "椅子で脚を組む",
      "j2_sit_knees_up" to "ひざを立てて座る",
      "j2_sit_desk_chin" to "机に肘をついて座る",
      "j2_sit_beanbag" to "ビーズクッションに沈む",
    ),
  )

private fun optionPart599(): CharacterOptionPart =
  CharacterOptionPart(
    "配信中のしぐさ",
    true,
    listOf(
      "j2_live_wave_start" to "配信開始のあいさつ",
      "j2_live_thanks" to "感謝を伝える",
      "j2_live_cheer_clap" to "拍手する",
      "j2_live_headset_adjust" to "ヘッドセットを直す",
      "j2_live_read_comments" to "コメントを読む",
      "j2_live_game_focus" to "ゲームに集中する",
      "j2_live_game_win" to "勝って喜ぶ",
      "j2_live_game_lose" to "負けて落ち込む",
      "j2_live_drink_cup" to "飲み物を飲む",
      "j2_live_snack" to "お菓子をつまむ",
      "j2_live_mic_close" to "マイクに近づいて話す",
      "j2_live_whisper" to "ひそひそ話すしぐさ",
      "j2_live_sing_hold_mic" to "マイクを握って歌う",
      "j2_live_bye_wave" to "配信終了のあいさつ",
    ),
  )

private fun optionPart600(): CharacterOptionPart =
  CharacterOptionPart(
    "ダンス・ライブ",
    true,
    listOf(
      "j2_dance_idol" to "アイドル風のダンス",
      "j2_dance_cool" to "クールなダンス",
      "j2_dance_pop_step" to "ポップなステップ",
      "j2_dance_spin_skirt" to "スカートを広げて回る",
      "j2_dance_ballroom" to "社交ダンス風",
      "j2_dance_stage_point" to "ステージで客席を指さす",
      "j2_dance_wave_audience" to "観客に手を振る",
    ),
  )

private fun optionGenre155(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・ポーズとモーション（追加）",
    listOf(
    optionPart597(),
    optionPart598(),
    optionPart599(),
    optionPart600(),
    ),
  )

private fun optionPart601(): CharacterOptionPart =
  CharacterOptionPart(
    "音声機材",
    false,
    listOf(
      "j2_gear_mic_condenser" to "コンデンサーマイク",
      "j2_gear_mic_stand" to "マイクスタンド",
      "j2_gear_mic_arm" to "マイクアーム",
      "j2_gear_pop_filter" to "ポップガード",
      "j2_gear_headset_gaming" to "ゲーミングヘッドセット",
      "j2_gear_headset_wireless" to "ワイヤレスヘッドホン",
      "j2_gear_audio_interface" to "オーディオインターフェース",
      "j2_gear_mixer" to "ミキサー",
      "j2_gear_speaker" to "スピーカー",
    ),
  )

private fun optionPart602(): CharacterOptionPart =
  CharacterOptionPart(
    "映像・照明機材",
    false,
    listOf(
      "j2_gear_ring_light" to "リングライト",
      "j2_gear_softbox" to "ソフトボックス照明",
      "j2_gear_webcam" to "ウェブカメラ",
      "j2_gear_camera_tripod" to "三脚つきカメラ",
      "j2_gear_green_screen" to "グリーンバック",
      "j2_gear_neon_sign" to "ネオンサイン",
      "j2_gear_led_strip" to "LEDテープライト",
      "j2_gear_tablet_stand" to "タブレットスタンド",
      "j2_gear_stream_deck" to "配信用ボタンデバイス",
    ),
  )

private fun optionPart603(): CharacterOptionPart =
  CharacterOptionPart(
    "机まわりの小物",
    false,
    listOf(
      "j2_desk_gaming_pc" to "ゲーミングPC",
      "j2_desk_dual_monitor" to "2画面のモニター",
      "j2_desk_keyboard_rgb" to "光るキーボード",
      "j2_desk_mouse_pad" to "大きなマウスパッド",
      "j2_desk_gaming_chair" to "ゲーミングチェア",
      "j2_desk_controller" to "ゲームコントローラー",
      "j2_desk_energy_drink" to "エナジードリンク",
      "j2_desk_figure" to "フィギュアの飾り棚",
      "j2_desk_cat_lamp" to "ねこ型ランプ",
      "j2_desk_notebook" to "ノートとペン",
      "j2_desk_tissue_box" to "ティッシュ箱",
    ),
  )

private fun optionGenre156(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・配信者の小物と機材（追加）",
    listOf(
    optionPart601(),
    optionPart602(),
    optionPart603(),
    ),
  )

private fun optionPart604(): CharacterOptionPart =
  CharacterOptionPart(
    "配信部屋の背景",
    true,
    listOf(
      "j2_bg_room_cozy" to "居心地のよい部屋",
      "j2_bg_room_gamer" to "ゲーミングルーム",
      "j2_bg_room_pastel" to "パステル調の部屋",
      "j2_bg_room_loft" to "ロフトのある部屋",
      "j2_bg_room_library" to "本棚のある書斎",
      "j2_bg_room_studio" to "防音スタジオ",
      "j2_bg_room_kotatsu" to "こたつのある和室",
      "j2_bg_room_balcony" to "ベランダ",
      "j2_bg_room_night_window" to "夜景の見える窓",
      "j2_bg_room_attic" to "屋根裏部屋",
    ),
  )

private fun optionPart605(): CharacterOptionPart =
  CharacterOptionPart(
    "ステージ・イベント会場",
    true,
    listOf(
      "j2_bg_stage_concert" to "コンサートステージ",
      "j2_bg_stage_festival" to "野外フェスのステージ",
      "j2_bg_stage_runway" to "ファッションショーの花道",
      "j2_bg_stage_theater" to "劇場の舞台",
      "j2_bg_stage_neon" to "ネオンのステージ",
      "j2_bg_stage_confetti" to "紙吹雪の舞うステージ",
      "j2_bg_stage_balloon" to "風船で飾った会場",
      "j2_bg_stage_space" to "宇宙空間のステージ",
      "j2_bg_stage_underwater" to "海中のステージ",
      "j2_bg_stage_sky" to "雲の上のステージ",
    ),
  )

private fun optionPart606(): CharacterOptionPart =
  CharacterOptionPart(
    "配信画面の装飾・抜き背景",
    false,
    listOf(
      "j2_bg_overlay_frame" to "配信枠のフレーム",
      "j2_bg_overlay_chat" to "コメント欄つきの画面",
      "j2_bg_gradient_pastel" to "淡いグラデーション背景",
      "j2_bg_pattern_dots" to "水玉模様の背景",
      "j2_bg_pattern_stripes" to "ストライプの背景",
      "j2_bg_pattern_checker" to "チェック柄の背景",
      "j2_bg_simple_white" to "白一色の背景",
      "j2_bg_simple_green" to "緑一色の背景（合成用）",
      "j2_bg_sparkle_particles" to "きらめく粒の背景",
      "j2_bg_cherry_blossom" to "桜の舞う背景",
      "j2_bg_sunset_city" to "夕焼けの街並み",
      "j2_bg_beach_day" to "昼の海辺",
      "j2_bg_forest_light" to "木漏れ日の森",
      "j2_bg_snow_town" to "雪の街",
    ),
  )

private fun optionGenre157(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・背景とステージ（追加）",
    listOf(
    optionPart604(),
    optionPart605(),
    optionPart606(),
    ),
  )

private fun optionPart607(): CharacterOptionPart =
  CharacterOptionPart(
    "演出エフェクト",
    false,
    listOf(
      "j2_fx_sparkle_burst" to "きらめきの飛び散り",
      "j2_fx_heart_float" to "ふわふわ浮かぶハート",
      "j2_fx_star_trail" to "星の軌跡",
      "j2_fx_confetti_pop" to "クラッカーの紙吹雪",
      "j2_fx_light_beam" to "ステージの光線",
      "j2_fx_smoke_stage" to "ステージのスモーク",
      "j2_fx_petals" to "舞い散る花びら",
      "j2_fx_lens_flare" to "レンズフレア",
      "j2_fx_glitch" to "グリッチ効果",
      "j2_fx_snowfall" to "雪が降る演出",
      "j2_fx_aura" to "体を包む光のオーラ",
      "j2_fx_speech_bubble" to "吹き出し",
      "j2_fx_emote_float" to "スタンプのように浮かぶ絵文字",
    ),
  )

private fun optionPart608(): CharacterOptionPart =
  CharacterOptionPart(
    "立ち絵の構図",
    true,
    listOf(
      "j2_comp_bust_up" to "バストアップ",
      "j2_comp_full_body_j2" to "全身の立ち絵",
      "j2_comp_knee_up" to "ひざ上まで",
      "j2_comp_face_close" to "顔のアップ",
      "j2_comp_from_side" to "横向き",
      "j2_comp_three_quarter" to "斜め45度",
      "j2_comp_from_below" to "少し下から見上げる",
      "j2_comp_from_above" to "少し上から見下ろす",
      "j2_comp_icon_square" to "アイコン用の正方形",
      "j2_comp_t_pose" to "Tポーズ（3D化用）",
      "j2_comp_a_pose" to "Aポーズ（3D化用）",
      "j2_comp_turnaround" to "正面・横・背面の三面図",
      "j2_comp_expression_sheet" to "表情差分シート",
    ),
  )

private fun optionGenre158(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・配信画面の演出（追加）",
    listOf(
    optionPart607(),
    optionPart608(),
    ),
  )

private fun optionPart609(): CharacterOptionPart =
  CharacterOptionPart(
    "目の細部",
    false,
    listOf(
      "j2_x_eye_lid_shadow" to "まぶたの影",
      "j2_x_eye_tear_bag" to "ふくらんだ涙袋",
      "j2_x_eye_lash_gap" to "まつげの隙間が見える",
      "j2_x_eye_iris_big" to "大きな虹彩",
      "j2_x_eye_iris_small" to "小さな虹彩",
      "j2_x_eye_highlight_lower" to "下側に入る反射光",
      "j2_x_eye_color_split" to "虹彩の上下で色が変わる",
      "j2_x_eye_iris_glow" to "発光する虹彩",
      "j2_x_eye_half_lid" to "半分まぶたを下ろす",
      "j2_x_eye_crescent" to "三日月形に笑う目",
      "j2_x_eye_dot" to "点だけで描く目",
    ),
  )

private fun optionPart610(): CharacterOptionPart =
  CharacterOptionPart(
    "顔のこまかな形",
    false,
    listOf(
      "j2_x_face_cheek_blush_gradient" to "頬から鼻にかかる赤み",
      "j2_x_face_philtrum_short" to "人中の短い口元",
      "j2_x_face_chin_pointed" to "とがったあご",
      "j2_x_face_collarbone" to "くっきりした鎖骨",
      "j2_x_face_fang_small" to "小さな八重歯",
      "j2_x_face_tongue_tip" to "舌先を少し出す",
      "j2_x_face_teeth_row" to "歯をのぞかせる笑顔",
      "j2_x_face_lip_bite" to "唇を軽くかむ",
    ),
  )

private fun optionPart611(): CharacterOptionPart =
  CharacterOptionPart(
    "声と話し方を連想させる口の動き",
    false,
    listOf(
      "j2_x_mouth_a" to "「あ」の口",
      "j2_x_mouth_i" to "「い」の口",
      "j2_x_mouth_u" to "「う」の口",
      "j2_x_mouth_e" to "「え」の口",
      "j2_x_mouth_o" to "「お」の口",
      "j2_x_mouth_mumble" to "もごもごした口",
      "j2_x_mouth_inhale" to "息を吸う口",
      "j2_x_mouth_whistle" to "口笛を吹く口",
    ),
  )

private fun optionGenre159(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・キャラメイクの細部（追加）",
    listOf(
    optionPart609(),
    optionPart610(),
    optionPart611(),
    ),
  )

private fun optionPart612(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の量と質感",
    true,
    listOf(
      "j2_x_hair_volume_flat" to "ぺたんとした髪",
      "j2_x_hair_curly_tight" to "細かく巻いた髪",
    ),
  )

private fun optionPart613(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の長さ",
    true,
    listOf(
      "j2_x_len_ear" to "耳が出る長さ",
      "j2_x_len_chin" to "あごの長さ",
      "j2_x_len_shoulder" to "肩にかかる長さ",
      "j2_x_len_back" to "背中までの長さ",
      "j2_x_len_waist" to "腰までの長さ",
      "j2_x_len_floor" to "床に届く長さ",
    ),
  )

private fun optionPart614(): CharacterOptionPart =
  CharacterOptionPart(
    "髪を飾るパーツ",
    false,
    listOf(
      "j2_x_hair_ribbon_braid" to "リボンを編み込む",
      "j2_x_hair_tassel" to "房飾り",
      "j2_x_hair_comb_ornament" to "くし型の髪飾り",
      "j2_x_hair_stick" to "ヘアスティック",
    ),
  )

private fun optionGenre160(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・髪型の細部（追加）",
    listOf(
    optionPart612(),
    optionPart613(),
    optionPart614(),
    ),
  )

private fun optionPart615(): CharacterOptionPart =
  CharacterOptionPart(
    "袖と襟の形",
    true,
    listOf(
      "j2_x_sleeve_long_cover" to "手の甲まで隠れる長い袖",
      "j2_x_sleeve_cold_shoulder" to "肩だけ出る袖",
      "j2_x_sleeve_rolled" to "まくった袖",
      "j2_x_collar_halter" to "首元で留めるホルター",
      "j2_x_collar_v" to "深いVネック",
    ),
  )

private fun optionPart616(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装の質感・柄",
    false,
    listOf(
      "j2_x_fabric_lace" to "レース地",
      "j2_x_fabric_satin" to "光沢のあるサテン",
      "j2_x_fabric_sheer" to "透け感のある薄布",
      "j2_x_fabric_knit" to "ざっくりしたニット地",
      "j2_x_pattern_stripes_cloth" to "ボーダー柄",
    ),
  )

private fun optionPart617(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装の装飾",
    false,
    listOf(
      "j2_x_deco_frill" to "フリル飾り",
      "j2_x_deco_ribbon_chest" to "胸元のリボン",
      "j2_x_deco_bell_clothes" to "服につく鈴",
      "j2_x_deco_zipper" to "ジッパー飾り",
      "j2_x_deco_cutout" to "胸元のカットアウト",
      "j2_x_deco_embroidery" to "刺繍",
      "j2_x_deco_hood" to "フード付き",
    ),
  )

private fun optionGenre161(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・衣装の細部（追加）",
    listOf(
    optionPart615(),
    optionPart616(),
    optionPart617(),
    ),
  )

private fun optionPart618(): CharacterOptionPart =
  CharacterOptionPart(
    "画面に出るもの",
    false,
    listOf(
      "j2_x_ui_follow_button" to "フォローボタン風の飾り",
      "j2_x_ui_viewer_count" to "視聴者数の表示",
      "j2_x_ui_subtitle_box" to "字幕テロップ",
      "j2_x_ui_title_banner" to "配信タイトルの帯",
      "j2_x_ui_countdown" to "開始までのカウントダウン",
      "j2_x_ui_chat_bubble_list" to "チャットが流れる欄",
      "j2_x_ui_donation_alert" to "支援通知の演出",
      "j2_x_ui_level_up" to "レベルアップ表示",
      "j2_x_ui_waiting_screen" to "待機画面",
      "j2_x_ui_ending_screen" to "エンディング画面",
      "j2_x_ui_logo_corner" to "角に置くロゴ",
    ),
  )

private fun optionPart619(): CharacterOptionPart =
  CharacterOptionPart(
    "配信の雰囲気",
    true,
    listOf(
      "j2_x_mood_cozy_late" to "深夜のまったり配信",
      "j2_x_mood_morning" to "朝の配信",
      "j2_x_mood_energetic" to "元気いっぱいの配信",
      "j2_x_mood_asmr_soft" to "ささやき配信の雰囲気",
      "j2_x_mood_horror_game" to "ホラーゲーム配信の暗い雰囲気",
      "j2_x_mood_cooking" to "料理配信",
      "j2_x_mood_art_draw" to "お絵描き配信",
      "j2_x_mood_karaoke" to "歌枠",
      "j2_x_mood_talk" to "雑談配信",
      "j2_x_mood_collab" to "二人でのコラボ配信",
    ),
  )

private fun optionGenre162(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・配信者らしい演出（追加）",
    listOf(
    optionPart618(),
    optionPart619(),
    ),
  )

private fun optionPart620(): CharacterOptionPart =
  CharacterOptionPart(
    "手に持つもの",
    false,
    listOf(
      "j2_x_hold_tablet" to "タブレット",
      "j2_x_hold_pen_stylus" to "スタイラスペン",
      "j2_x_hold_banner" to "応援バナー",
      "j2_x_hold_cup_tea" to "ティーカップ",
      "j2_x_hold_boba" to "タピオカドリンク",
      "j2_x_hold_cake" to "ケーキ",
      "j2_x_hold_keyboard_music" to "キーボード楽器",
      "j2_x_hold_violin" to "バイオリン",
      "j2_x_hold_tambourine" to "タンバリン",
    ),
  )

private fun optionPart621(): CharacterOptionPart =
  CharacterOptionPart(
    "身近なマスコットと小動物",
    false,
    listOf(
      "j2_x_pet_cat" to "ねこ",
      "j2_x_pet_dog" to "いぬ",
      "j2_x_pet_rabbit" to "うさぎ",
      "j2_x_pet_hamster" to "ハムスター",
      "j2_x_pet_bird_small" to "小鳥",
      "j2_x_pet_fox_small" to "小さなきつね",
      "j2_x_pet_slime" to "スライム風のマスコット",
      "j2_x_pet_floating_drone" to "ふわふわ浮かぶロボットのお供",
      "j2_x_pet_ghost_small" to "小さなおばけのお供",
      "j2_x_pet_fairy" to "妖精のお供",
      "j2_x_pet_dragon_small" to "小さなドラゴン",
    ),
  )

private fun optionGenre163(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・小物と持ち物（追加）",
    listOf(
    optionPart620(),
    optionPart621(),
    ),
  )

private fun optionPart622(): CharacterOptionPart =
  CharacterOptionPart(
    "3Dモデル向けのポーズと見え方",
    true,
    listOf(
      "j2_x_3d_front_flat" to "正面を向いた平らなライティング",
      "j2_x_3d_neutral_hands" to "手を体の横に自然に下ろす",
      "j2_x_3d_open_palms" to "手のひらを開いて立つ",
      "j2_x_3d_no_overlap" to "腕と体が重ならない",
      "j2_x_3d_plain_bg" to "無地の背景で全身",
      "j2_x_3d_split_layers" to "パーツごとに分けやすい配色",
      "j2_x_3d_eyes_open" to "目を開いた基準顔",
      "j2_x_3d_mouth_closed" to "口を閉じた基準顔",
      "j2_x_3d_symmetry" to "左右対称に近い立ち姿",
      "j2_x_3d_hair_separate" to "髪が体にかぶらない",
    ),
  )

private fun optionGenre164(): CharacterOptionGenre =
  CharacterOptionGenre(
    "アバター系・3D化と書き出し向けの形（追加）",
    listOf(
    optionPart622(),
    ),
  )

private fun optionPart623(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の輪郭",
    true,
    listOf(
      "j3_fc_diamond" to "ひし形の輪郭",
    ),
  )

private fun optionPart624(): CharacterOptionPart =
  CharacterOptionPart(
    "目の形",
    true,
    listOf(
      "j3_ey_catlike" to "猫のような目",
    ),
  )

private fun optionPart625(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳",
    true,
    listOf(
      "j3_pu_shine" to "瞳のハイライト強め",
      "j3_pu_nohl" to "ハイライトなしの瞳",
    ),
  )

private fun optionPart626(): CharacterOptionPart =
  CharacterOptionPart(
    "まつ毛",
    true,
    listOf(
      "j3_ls_low" to "下まつ毛が目立つ",
      "j3_ls_wing" to "はね上がったまつ毛",
    ),
  )

private fun optionPart627(): CharacterOptionPart =
  CharacterOptionPart(
    "眉",
    true,
    listOf(
      "j3_eb_dot" to "丸い眉（まろ眉風）",
    ),
  )

private fun optionPart628(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻",
    true,
    listOf(
      "j3_ns_button" to "ぷくっとした鼻",
      "j3_ns_wide" to "横に広がった小鼻",
      "j3_ns_bridgeband" to "鼻にかかる絆創膏",
    ),
  )

private fun optionPart629(): CharacterOptionPart =
  CharacterOptionPart(
    "口と唇",
    true,
    listOf(
      "j3_mt_heart" to "ハート形の唇",
      "j3_mt_matte" to "マットな唇",
    ),
  )

private fun optionPart630(): CharacterOptionPart =
  CharacterOptionPart(
    "歯",
    true,
    listOf(
      "j3_th_braces" to "歯の矯正器具",
    ),
  )

private fun optionPart631(): CharacterOptionPart =
  CharacterOptionPart(
    "耳",
    true,
    listOf(
      "j3_ea_lobe" to "大きな耳たぶ",
    ),
  )

private fun optionGenre165(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・顔のパーツ（追加）",
    listOf(
    optionPart623(),
    optionPart624(),
    optionPart625(),
    optionPart626(),
    optionPart627(),
    optionPart628(),
    optionPart629(),
    optionPart630(),
    optionPart631(),
    ),
  )

private fun optionPart632(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の部位",
    false,
    listOf(
      "j3_hr_sideburn" to "もみあげ",
      "j3_hr_hairpin" to "ヘアピンで留めた髪",
      "j3_hr_short_wave" to "ショートのウェーブ",
      "j3_hr_shag" to "ふんわりしたシャグ",
      "j3_hr_perm" to "パーマヘア",
      "j3_hr_bundle" to "太い髪束",
      "j3_hr_tail_curly" to "巻き髪のポニーテール",
      "j3_hr_cascade" to "滝のように流れる髪",
      "j3_hr_pointed" to "とがった毛先",
      "j3_hr_ring" to "輪っか状の髪",
    ),
  )

private fun optionGenre166(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・髪の部位（追加）",
    listOf(
    optionPart632(),
    ),
  )

private fun optionPart633(): CharacterOptionPart =
  CharacterOptionPart(
    "体格の全体",
    true,
    listOf(
      "j3_bd_short" to "低身長（成人）",
      "j3_bd_slim" to "ほっそりした体",
      "j3_bd_fit" to "フィットネス体型",
      "j3_bd_curvy" to "メリハリのある体",
      "j3_bd_rect" to "ストレート体型",
      "j3_bd_mature" to "大人の女性の体",
      "j3_bd_bonysoft" to "骨ばった体",
    ),
  )

private fun optionGenre167(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・体型（追加）",
    listOf(
    optionPart633(),
    ),
  )

private fun optionPart634(): CharacterOptionPart =
  CharacterOptionPart(
    "胸まわり",
    true,
    listOf(
      "j3_ch_sternum" to "胸の中心線",
      "j3_ch_cleavage_line" to "谷間のライン",
      "j3_ch_rib" to "あばらのライン",
      "j3_ch_nipple_pert" to "つんと上向きの先端",
    ),
  )

private fun optionPart635(): CharacterOptionPart =
  CharacterOptionPart(
    "腰・お腹",
    true,
    listOf(
      "j3_wa_thick" to "太めの腰",
      "j3_wa_lowback" to "背中のくぼみ",
      "j3_wa_lowrise" to "低い位置の腰ライン",
    ),
  )

private fun optionPart636(): CharacterOptionPart =
  CharacterOptionPart(
    "お尻",
    true,
    listOf(
      "j3_hp_cheek" to "お尻の下のライン",
      "j3_hp_curve" to "お尻から腰の曲線",
    ),
  )

private fun optionPart637(): CharacterOptionPart =
  CharacterOptionPart(
    "脚",
    true,
    listOf(
      "j3_lg_x" to "X脚気味",
      "j3_lg_toned" to "引き締まった脚",
    ),
  )

private fun optionPart638(): CharacterOptionPart =
  CharacterOptionPart(
    "腕・手",
    true,
    listOf(
      "j3_ar_thick" to "ふっくらした腕",
      "j3_ar_elbow" to "ひじのライン",
    ),
  )

private fun optionGenre168(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・体のライン（追加）",
    listOf(
    optionPart634(),
    optionPart635(),
    optionPart636(),
    optionPart637(),
    optionPart638(),
    ),
  )

private fun optionPart639(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の色味",
    true,
    listOf(
      "j3_sk_cool" to "青みがかった肌",
      "j3_sk_freckled_s" to "そばかす（少なめ）",
      "j3_sk_scar" to "古い傷あと",
    ),
  )

private fun optionPart640(): CharacterOptionPart =
  CharacterOptionPart(
    "日焼けあと",
    true,
    listOf(
      "j3_tn_sleeve" to "半袖の日焼けあと",
      "j3_tn_tshirt" to "Tシャツの日焼けあと",
      "j3_tn_goggle" to "ゴーグルの日焼けあと",
      "j3_tn_gradient" to "日焼けのグラデーション",
    ),
  )

private fun optionPart641(): CharacterOptionPart =
  CharacterOptionPart(
    "ほくろ",
    true,
    listOf(
      "j3_ml_forehead" to "おでこのほくろ",
    ),
  )

private fun optionPart642(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の印",
    false,
    listOf(
      "j3_bm_stripe" to "縞模様のペイント",
      "j3_bm_stick" to "ボディシール",
      "j3_bm_gem" to "肌につけた宝石",
      "j3_bm_ear_hole" to "耳のゲージ（拡張ピアス）",
    ),
  )

private fun optionGenre169(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・肌とマーク（追加）",
    listOf(
    optionPart639(),
    optionPart640(),
    optionPart641(),
    optionPart642(),
    ),
  )

private fun optionPart643(): CharacterOptionPart =
  CharacterOptionPart(
    "アイメイク",
    false,
    listOf(
      "j3_mk_lashes_dramatic" to "ボリュームまつ毛",
      "j3_mk_glitter_eye" to "目尻のラメ",
      "j3_mk_gem_eye" to "目のまわりの宝石",
      "j3_mk_dark_circle" to "濃いクマのメイク",
      "j3_mk_brow_pencil" to "眉メイク",
      "j3_mk_brow_color" to "色つきの眉メイク",
    ),
  )

private fun optionPart644(): CharacterOptionPart =
  CharacterOptionPart(
    "リップ・チーク・ネイル",
    false,
    listOf(
      "j3_mk_blush_light" to "うすいチーク",
      "j3_mk_highlight" to "ハイライトメイク",
      "j3_mk_fox_makeup" to "キツネ風メイク",
      "j3_mk_cat_makeup" to "ネコ風メイク",
      "j3_mk_tear" to "涙の跡メイク",
    ),
  )

private fun optionGenre170(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・メイク（追加）",
    listOf(
    optionPart643(),
    optionPart644(),
    ),
  )

private fun optionPart645(): CharacterOptionPart =
  CharacterOptionPart(
    "トップス",
    false,
    listOf(
      "j3_tp_shirt_white" to "白シャツ",
      "j3_tp_shrug" to "ショールのような羽織",
      "j3_tp_bodice" to "ボディス",
      "j3_tp_sheer_top" to "透ける素材のトップ",
      "j3_tp_sleeveless_turtle" to "ノースリーブのタートルネック",
      "j3_tp_highneck" to "ハイネックのトップ",
      "j3_tp_cutout" to "胸元に穴のあいたトップ",
      "j3_tp_backless" to "背中の開いたトップ",
      "j3_tp_strap_top" to "ストラップだけのトップ",
      "j3_tp_yoga" to "ヨガウェアのトップ",
      "j3_tp_bolero_lace" to "レースのボレロ",
      "j3_tp_chinese_top" to "チャイナ風のトップ",
      "j3_tp_coat_only" to "コート一枚だけの姿",
      "j3_tp_pullover" to "プルオーバー",
      "j3_tp_muscle_tee" to "マッスルシャツ",
      "j3_tp_lingerie_top" to "ランジェリーのようなトップ",
      "j3_tp_tank_long" to "ロング丈のタンクトップ",
    ),
  )

private fun optionPart646(): CharacterOptionPart =
  CharacterOptionPart(
    "ボトムス",
    false,
    listOf(
      "j3_bt_tennis_skirt" to "テニススカート",
      "j3_bt_slit" to "スリットの入ったスカート",
      "j3_bt_asymmetric" to "アシンメトリーのスカート",
      "j3_bt_micro_shorts" to "マイクロショートパンツ",
      "j3_bt_lowrise" to "ローライズのボトム",
      "j3_bt_crotchless" to "股の開いたボトム",
      "j3_bt_thigh_slit" to "太ももの見えるスリット",
    ),
  )

private fun optionGenre171(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・上半身の服（追加）",
    listOf(
    optionPart645(),
    optionPart646(),
    ),
  )

private fun optionPart647(): CharacterOptionPart =
  CharacterOptionPart(
    "ドレス・ワンピース",
    false,
    listOf(
      "j3_dr_cheongsam_long" to "ロングのチャイナドレス",
      "j3_dr_qipao_slit" to "深いスリットのチャイナドレス",
      "j3_dr_kimono_dress" to "着物風ワンピース",
      "j3_dr_witch" to "魔女風ドレス",
      "j3_dr_polka" to "水玉のワンピース",
      "j3_dr_floral" to "花柄のワンピース",
      "j3_dr_stripe" to "縞柄のワンピース",
      "j3_dr_checker" to "チェック柄のワンピース",
    ),
  )

private fun optionGenre172(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・ワンピース・ドレス（追加）",
    listOf(
    optionPart647(),
    ),
  )

private fun optionPart648(): CharacterOptionPart =
  CharacterOptionPart(
    "下着",
    false,
    listOf(
      "j3_ul_bra_cross" to "胸元で交差するブラ",
      "j3_ul_panty_frilly" to "フリルのショーツ",
      "j3_ul_panty_pull" to "ショーツを引き下げ",
      "j3_ul_harness" to "ハーネスランジェリー",
      "j3_ul_bodysuit_wet" to "濡れたボディスーツ",
      "j3_ul_open_crotch" to "股の開いたボディスーツ",
    ),
  )

private fun optionPart649(): CharacterOptionPart =
  CharacterOptionPart(
    "水着",
    false,
    listOf(
      "j3_sw_bikini_front" to "フロントひもビキニ",
      "j3_sw_bikini_cross" to "胸元が交差するビキニ",
      "j3_sw_bikini_bottom" to "ビキニのボトムのみ",
      "j3_sw_bikini_top" to "ビキニのトップのみ",
      "j3_sw_surf" to "サーフ水着",
      "j3_sw_swimshorts" to "水着のショートパンツ",
    ),
  )

private fun optionPart650(): CharacterOptionPart =
  CharacterOptionPart(
    "靴下・タイツ",
    false,
    listOf(
      "j3_st_ribbon" to "リボン付きのソックス",
      "j3_st_fishnet_wide" to "大きな網目のタイツ",
      "j3_st_pantyhose_ripped_crotch" to "股が破れたパンスト",
      "j3_st_stocking_bare" to "素足",
      "j3_st_stocking_pattern" to "柄入りのタイツ",
      "j3_st_stocking_heart" to "ハート柄のタイツ",
      "j3_st_stocking_ribbed" to "リブ編みのソックス",
      "j3_st_stocking_wet" to "濡れた靴下",
    ),
  )

private fun optionGenre173(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・下着・水着・靴下（追加）",
    listOf(
    optionPart648(),
    optionPart649(),
    optionPart650(),
    ),
  )

private fun optionPart651(): CharacterOptionPart =
  CharacterOptionPart(
    "靴",
    false,
    listOf(
      "j3_sh_highcut" to "ハイカットのスニーカー",
      "j3_sh_heels_open" to "つま先の開いたヒール",
      "j3_sh_foot_wrap" to "足に巻く包帯",
      "j3_sh_heels_hand" to "手に持った靴",
    ),
  )

private fun optionPart652(): CharacterOptionPart =
  CharacterOptionPart(
    "手袋",
    false,
    listOf(
      "j3_gl_short" to "短い手袋",
      "j3_gl_claw" to "爪のついた手袋",
      "j3_gl_off" to "片方だけ外した手袋",
    ),
  )

private fun optionGenre174(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・靴・手袋（追加）",
    listOf(
    optionPart651(),
    optionPart652(),
    ),
  )

private fun optionPart653(): CharacterOptionPart =
  CharacterOptionPart(
    "頭・顔まわり",
    false,
    listOf(
      "j3_ac_hairpin_butterfly" to "蝶の髪飾り",
      "j3_ac_hairpin_star" to "星の髪飾り",
      "j3_ac_hairpin_heart" to "ハートの髪飾り",
      "j3_ac_hairpin_comb" to "櫛の髪飾り",
      "j3_ac_glasses_rimless" to "縁なしメガネ",
      "j3_ac_glasses_cat" to "キャットアイ型メガネ",
      "j3_ac_mask_half" to "口もとだけのマスク",
    ),
  )

private fun optionGenre175(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・アクセサリー（追加）",
    listOf(
    optionPart653(),
    ),
  )

private fun optionPart654(): CharacterOptionPart =
  CharacterOptionPart(
    "性格の傾向",
    false,
    listOf(
      "j3_pe_calm" to "おっとり落ち着いた",
      "j3_pe_bold" to "大胆",
      "j3_pe_dandere" to "無口でおとなしい",
      "j3_pe_genki" to "元気いっぱい",
      "j3_pe_masochist" to "いじられ好き",
      "j3_pe_flirty" to "甘えん坊",
      "j3_pe_polite" to "礼儀正しい",
      "j3_pe_tomboy" to "男勝り",
      "j3_pe_otaku" to "オタク気質",
      "j3_pe_gamer" to "ゲーム好き",
      "j3_pe_chuuni" to "中二病",
      "j3_pe_pessimistic" to "ネガティブ",
      "j3_pe_mother" to "世話焼き（母性的）",
      "j3_pe_sweet_talk" to "甘い言葉づかい",
      "j3_pe_sharp_tongue" to "毒舌",
      "j3_pe_liar" to "かけひき上手",
      "j3_pe_perfectionist" to "完璧主義",
      "j3_pe_vain" to "見栄っ張り",
      "j3_pe_sleepy" to "いつも眠そう",
      "j3_pe_loner" to "一匹狼",
    ),
  )

private fun optionGenre176(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・性格のタイプ（追加）",
    listOf(
    optionPart654(),
    ),
  )

private fun optionPart655(): CharacterOptionPart =
  CharacterOptionPart(
    "表情",
    false,
    listOf(
      "j3_ex_smile_wry" to "苦笑い",
      "j3_ex_worried" to "心配顔",
      "j3_ex_kiss_pucker" to "キスをねだる唇",
      "j3_ex_pleasure" to "心地よさそうな顔",
      "j3_ex_lustful" to "熱っぽい表情",
      "j3_ex_look_viewer" to "こちらを見つめる顔",
      "j3_ex_triangle_mouth" to "三角形の口",
      "j3_ex_fang_smile" to "八重歯を見せる笑顔",
      "j3_ex_blush_lines" to "赤面の斜線",
    ),
  )

private fun optionGenre177(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・表情の種類（追加）",
    listOf(
    optionPart655(),
    ),
  )

private fun optionPart656(): CharacterOptionPart =
  CharacterOptionPart(
    "立ちポーズ",
    false,
    listOf(
      "j3_po_stand_shush" to "静かにのポーズ",
      "j3_po_stand_cheer" to "ガッツポーズ",
      "j3_po_stand_kick" to "キックのポーズ",
      "j3_po_stand_cover_both" to "胸と下を隠す",
      "j3_po_stand_hold_arm" to "自分の腕をつかむ",
      "j3_po_stand_adjust_strap" to "肩ひもを直す",
      "j3_po_stand_pull_collar" to "襟を引っぱる",
      "j3_po_stand_shirt_pull" to "シャツを引き下げる",
    ),
  )

private fun optionPart657(): CharacterOptionPart =
  CharacterOptionPart(
    "座りポーズ",
    false,
    listOf(
      "j3_po_sit_squat_wide" to "大きく足を開いてしゃがむ",
      "j3_po_sit_hug_knees" to "ひざを抱えて座る",
      "j3_po_sit_wall" to "壁にもたれて座る",
      "j3_po_sit_leg_open" to "脚を開いて座る",
      "j3_po_sit_m" to "M字に脚を開く",
      "j3_po_sit_arms_back" to "両手を後ろについて座る",
      "j3_po_sit_window" to "窓辺に座る",
      "j3_po_sit_railing" to "手すりに座る",
      "j3_po_sit_table" to "テーブルに座る",
      "j3_po_sit_floor_lean" to "床に両手をついて座る",
    ),
  )

private fun optionPart658(): CharacterOptionPart =
  CharacterOptionPart(
    "寝ポーズ",
    false,
    listOf(
      "j3_po_lie_feet_up" to "うつぶせで足をぱたぱたさせる",
      "j3_po_lie_hair_spread" to "髪を広げて寝る",
      "j3_po_lie_cover" to "布団にくるまる",
    ),
  )

private fun optionPart659(): CharacterOptionPart =
  CharacterOptionPart(
    "四つんばい・前かがみ",
    false,
    listOf(
      "j3_po_bend_waist" to "腰を曲げて手を床につく",
      "j3_po_cat_stretch" to "猫のようにのびる",
      "j3_po_hands_knees_head" to "手とひざをついて頭を下げる",
    ),
  )

private fun optionGenre178(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・ポーズ・しぐさ（追加）",
    listOf(
    optionPart656(),
    optionPart657(),
    optionPart658(),
    optionPart659(),
    ),
  )

private fun optionPart660(): CharacterOptionPart =
  CharacterOptionPart(
    "日常のしぐさ",
    false,
    listOf(
      "j3_gs_hug_tight" to "強く抱きしめる",
      "j3_gs_hug_neck" to "首に腕を回す",
      "j3_gs_hug_waist" to "腰に腕を回す",
      "j3_gs_cheek_touch" to "頬にふれる",
      "j3_gs_cheek_kiss" to "頬へのキス",
      "j3_gs_hand_kiss" to "手の甲へのキス",
      "j3_gs_kiss_long" to "長いキス",
      "j3_gs_snuggle" to "寄り添う",
      "j3_gs_lean_shoulder" to "肩にもたれる",
      "j3_gs_dance_couple" to "ペアで踊る",
      "j3_gs_share_drink" to "ひとつの飲み物を分ける",
      "j3_gs_selfie_mirror" to "鏡の前の自撮り",
      "j3_gs_phone_call" to "電話をかける",
      "j3_gs_write" to "書きものをする",
      "j3_gs_stretch_morning" to "朝ののび",
      "j3_gs_undress_slow" to "ゆっくり脱ぐ",
      "j3_gs_dress" to "服を着る",
      "j3_gs_pour_tea" to "お茶をそそぐ",
      "j3_gs_neck_bite" to "首筋をあまがみ",
      "j3_gs_poke" to "ほっぺをつつく",
      "j3_gs_pinch" to "頬をつまむ",
      "j3_gs_rub_back" to "背中をさする",
      "j3_gs_massage_foot" to "足のマッサージ",
      "j3_gs_comb_hair" to "髪をとかしてあげる",
    ),
  )

private fun optionGenre179(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・しぐさ・ふれあい（追加）",
    listOf(
    optionPart660(),
    ),
  )

private fun optionPart661(): CharacterOptionPart =
  CharacterOptionPart(
    "屋内の場所",
    false,
    listOf(
      "j3_lc_bedroom_dim" to "薄暗い寝室",
      "j3_lc_toilet_clean" to "洗面所",
      "j3_lc_closet" to "クローゼット",
      "j3_lc_study" to "書斎",
      "j3_lc_lounge" to "ラウンジ",
      "j3_lc_restaurant" to "レストラン",
      "j3_lc_hotel_suite" to "スイートルーム",
      "j3_lc_ryokan" to "旅館の和室",
      "j3_lc_tatami" to "畳の部屋",
      "j3_lc_futon" to "布団のある和室",
      "j3_lc_pool_indoor" to "屋内プール",
      "j3_lc_gym" to "ジム",
      "j3_lc_dance_studio" to "ダンススタジオ",
      "j3_lc_theater" to "劇場",
      "j3_lc_karaoke" to "カラオケルーム",
      "j3_lc_arcade" to "ゲームセンター",
      "j3_lc_cinema" to "映画館",
      "j3_lc_museum" to "美術館",
      "j3_lc_train" to "電車の中",
      "j3_lc_car_back" to "車の後部座席",
      "j3_lc_plane" to "飛行機の中",
      "j3_lc_yacht" to "ヨットの上",
      "j3_lc_cruise" to "クルーズ船",
      "j3_lc_clinic" to "診察室",
      "j3_lc_massage_room" to "マッサージルーム",
      "j3_lc_spa" to "スパ",
      "j3_lc_salon" to "美容室",
      "j3_lc_laundry" to "コインランドリー",
      "j3_lc_convenience" to "コンビニ",
      "j3_lc_meeting" to "会議室",
      "j3_lc_garage" to "ガレージ",
    ),
  )

private fun optionGenre180(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・屋内の場所（追加）",
    listOf(
    optionPart661(),
    ),
  )

private fun optionPart662(): CharacterOptionPart =
  CharacterOptionPart(
    "屋外の場所",
    false,
    listOf(
      "j3_lo_beach_night" to "夜のビーチ",
      "j3_lo_pool" to "屋外プール",
      "j3_lo_forest_night" to "夜の森",
      "j3_lo_mountain" to "山の頂上",
      "j3_lo_cabin" to "山小屋",
      "j3_lo_ski" to "スキー場",
      "j3_lo_oasis" to "オアシス",
      "j3_lo_garden_rose" to "バラ園",
      "j3_lo_terrace" to "テラス",
      "j3_lo_street" to "街角",
      "j3_lo_alley" to "路地裏",
      "j3_lo_crossing" to "交差点",
      "j3_lo_temple" to "寺",
      "j3_lo_torii" to "鳥居",
      "j3_lo_market" to "市場",
      "j3_lo_mall" to "ショッピングモール",
      "j3_lo_harbor" to "港",
      "j3_lo_castle" to "城",
      "j3_lo_cathedral" to "大聖堂",
      "j3_lo_farm" to "農場",
      "j3_lo_vineyard" to "ブドウ畑",
      "j3_lo_airport" to "空港",
      "j3_lo_parking" to "駐車場",
      "j3_lo_highway" to "高速道路のサービスエリア",
      "j3_lo_ferris" to "観覧車",
      "j3_lo_stadium" to "スタジアム",
      "j3_lo_racetrack" to "サーキット",
      "j3_lo_space" to "宇宙空間",
      "j3_lo_moon" to "月面",
      "j3_lo_cloud" to "雲の上",
      "j3_lo_fantasy" to "幻想的な世界",
      "j3_lo_cyber" to "サイバーな街",
      "j3_lo_ruins_future" to "未来都市",
    ),
  )

private fun optionPart663(): CharacterOptionPart =
  CharacterOptionPart(
    "時間帯",
    true,
    listOf(
      "j3_tm_evening" to "夕暮れ",
      "j3_tm_twilight" to "薄明かり",
    ),
  )

private fun optionPart664(): CharacterOptionPart =
  CharacterOptionPart(
    "天気・季節",
    true,
    listOf(
      "j3_wt_crescent" to "三日月",
    ),
  )

private fun optionPart665(): CharacterOptionPart =
  CharacterOptionPart(
    "光",
    false,
    listOf(
      "j3_lt_hard" to "強い光",
      "j3_lt_side" to "横からの光",
      "j3_lt_top" to "真上からの光",
      "j3_lt_under" to "下からの光",
      "j3_lt_cinematic" to "映画のような光",
      "j3_lt_neon_pink" to "ピンクのネオン光",
      "j3_lt_neon_blue" to "青いネオン光",
      "j3_lt_sunbeam" to "差しこむ陽ざし",
      "j3_lt_window" to "窓から入る光",
      "j3_lt_lamp" to "間接照明",
      "j3_lt_tv" to "テレビの光",
      "j3_lt_flash" to "カメラのフラッシュ",
      "j3_lt_god" to "天使のはしご",
      "j3_lt_low_key" to "暗めの明暗",
      "j3_lt_high_key" to "明るい明暗",
      "j3_lt_color_split" to "色違いの二色ライト",
      "j3_lt_shadow_blinds" to "ブラインドの影",
      "j3_lt_shadow_leaves" to "葉っぱの影",
    ),
  )

private fun optionGenre181(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・屋外の場所・時間・天気・光（追加）",
    listOf(
    optionPart662(),
    optionPart663(),
    optionPart664(),
    optionPart665(),
    ),
  )

private fun optionPart666(): CharacterOptionPart =
  CharacterOptionPart(
    "体位",
    false,
    listOf(
      "j3_ap_butterfly" to "ひざ裏を持つ体位",
      "j3_ap_legs_up" to "脚を上げた体位",
      "j3_ap_legs_shoulder" to "脚を肩にかける体位",
      "j3_ap_full_nelson" to "後ろから腕を取る体位",
      "j3_ap_wall" to "壁を使う体位",
      "j3_ap_table" to "テーブルの上",
      "j3_ap_chair" to "椅子を使う体位",
      "j3_ap_bed_edge" to "ベッドの端",
      "j3_ap_bridge" to "ブリッジの体位",
      "j3_ap_anilingus_pos" to "後ろから舌で愛撫する体位",
      "j3_ap_straddle_face" to "顔にまたがる体位",
      "j3_ap_assjob" to "お尻を使った愛撫",
      "j3_ap_naizuri_kneel" to "ひざ立ちで胸を使う",
      "j3_ap_tonguekiss" to "舌を絡めるキス",
      "j3_ap_foreplay" to "前戯",
      "j3_ap_cuddle_after" to "行為のあとの抱擁",
      "j3_ap_creampie" to "中に出されたあと",
      "j3_ap_facial" to "顔にかかった様子",
      "j3_ap_cum_body" to "体にかかった様子",
      "j3_ap_cum_mouth" to "口にためた様子",
      "j3_ap_cum_breasts" to "胸にかかった様子",
      "j3_ap_cum_belly" to "お腹にかかった様子",
      "j3_ap_cum_back" to "背中にかかった様子",
      "j3_ap_cum_hair" to "髪にかかった様子",
      "j3_ap_cum_ass" to "お尻にかかった様子",
      "j3_ap_cum_legs" to "脚にかかった様子",
      "j3_ap_cum_clothes" to "服にかかった様子",
      "j3_ap_cum_pool" to "白い液がたまった様子",
      "j3_ap_cum_drip" to "液がしたたる様子",
      "j3_ap_pussy_juice" to "愛液がしたたる様子",
      "j3_ap_gag_swallow" to "飲み込む様子",
      "j3_ap_bath_play" to "お風呂での行為",
      "j3_ap_shower_play" to "シャワーでの行為",
      "j3_ap_pool_play" to "プールでの行為",
      "j3_ap_beach_play" to "ビーチでの行為",
      "j3_ap_car_play" to "車内での行為",
      "j3_ap_office_play" to "オフィスでの行為",
      "j3_ap_kitchen_play" to "キッチンでの行為",
      "j3_ap_balcony_play" to "ベランダでの行為",
      "j3_ap_outdoor" to "屋外での行為",
      "j3_ap_threesome" to "3人での行為",
      "j3_ap_ffm" to "女性2人と男性1人",
      "j3_ap_mmf" to "男性2人と女性1人",
      "j3_ap_group" to "複数人での行為",
      "j3_ap_yuri" to "女性同士の行為",
      "j3_ap_breast_grab" to "後ろから胸をつかむ",
      "j3_ap_neck_kiss_back" to "後ろから首筋にキス",
    ),
  )

private fun optionPart667(): CharacterOptionPart =
  CharacterOptionPart(
    "状態",
    false,
    listOf(
      "j3_as_aroused" to "興奮した状態",
      "j3_as_wet" to "濡れた状態",
      "j3_as_spread" to "大きく開いた状態",
      "j3_as_gaping" to "ひらいた状態",
      "j3_as_shaking_legs" to "脚のふるえ",
      "j3_as_afterglow_face" to "余韻の顔",
      "j3_as_sweat_body" to "汗ばんだ体",
      "j3_as_blush_body" to "全身が赤みを帯びる",
      "j3_as_heart_marks" to "ハートマークが浮かぶ",
      "j3_as_moan_text" to "声の文字（効果音）",
      "j3_as_sound_effect" to "効果音の文字",
      "j3_as_kiss_thread" to "キスの糸",
      "j3_as_grab_sheet" to "シーツをつかむ",
      "j3_as_hip_lift" to "腰が浮く",
      "j3_as_eyes_up" to "白目寄りの目",
      "j3_as_hand_hold_sheet" to "両手でシーツをにぎる",
      "j3_as_smile_afterglow" to "満足そうな笑顔",
      "j3_as_sleep_after" to "終わったあとの眠り",
    ),
  )

private fun optionGenre182(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・成人向けの体位・行為の種類（追加）",
    listOf(
    optionPart666(),
    optionPart667(),
    ),
  )

private fun optionPart668(): CharacterOptionPart =
  CharacterOptionPart(
    "家具・小物",
    false,
    listOf(
      "j3_sp_bed_double" to "ダブルベッド",
      "j3_sp_bed_single" to "シングルベッド",
      "j3_sp_bed_round" to "丸いベッド",
      "j3_sp_sofa_leather" to "革張りのソファ",
      "j3_sp_chair_arm" to "ひじ掛け椅子",
      "j3_sp_chair_office" to "オフィスチェア",
      "j3_sp_stool_bar" to "バーのスツール",
      "j3_sp_table_low" to "ちゃぶ台",
      "j3_sp_desk" to "机",
      "j3_sp_shelf" to "本棚",
      "j3_sp_mirror_full" to "姿見",
      "j3_sp_mirror_hand" to "手鏡",
      "j3_sp_dresser" to "ドレッサー",
      "j3_sp_curtain_sheer" to "レースのカーテン",
      "j3_sp_window" to "窓",
      "j3_sp_door" to "ドア",
      "j3_sp_lamp_floor" to "フロアランプ",
      "j3_sp_lamp_desk" to "卓上ランプ",
      "j3_sp_chandelier" to "シャンデリア",
      "j3_sp_candle_many" to "たくさんのろうそく",
      "j3_sp_cushion" to "クッション",
      "j3_sp_sheet" to "シーツ",
      "j3_sp_rug" to "ラグ",
      "j3_sp_carpet_red" to "赤いじゅうたん",
      "j3_sp_fireplace" to "暖炉",
      "j3_sp_piano" to "ピアノ",
      "j3_sp_pole" to "ポール",
      "j3_sp_shower_head" to "シャワーヘッド",
      "j3_sp_bucket" to "おけ",
      "j3_sp_washbasin" to "洗面台",
      "j3_sp_tv" to "テレビ",
      "j3_sp_pc" to "パソコン",
      "j3_sp_tripod" to "三脚",
      "j3_sp_notebook" to "ノート",
      "j3_sp_pen" to "ペン",
      "j3_sp_plant" to "観葉植物",
      "j3_sp_wine" to "ワイン",
      "j3_sp_champagne" to "シャンパン",
      "j3_sp_cocktail" to "カクテル",
      "j3_sp_beer" to "ビール",
      "j3_sp_sake" to "日本酒",
      "j3_sp_tea" to "お茶",
      "j3_sp_coffee" to "コーヒー",
      "j3_sp_strawberry" to "いちご",
      "j3_sp_chocolate" to "チョコレート",
      "j3_sp_honey" to "はちみつ",
      "j3_sp_popsicle" to "アイスキャンディ",
      "j3_sp_banana" to "バナナ",
      "j3_sp_cherry" to "さくらんぼ",
      "j3_sp_cigar" to "葉巻（成人の演出）",
      "j3_sp_gift" to "プレゼントの箱",
      "j3_sp_present_ribbon" to "リボンを巻いた姿",
      "j3_sp_flower_petals" to "花びらをまいたベッド",
      "j3_sp_bicycle" to "自転車",
      "j3_sp_motorbike" to "バイク",
      "j3_sp_car" to "車",
      "j3_sp_sports_car" to "スポーツカー",
      "j3_sp_boat" to "ボート",
      "j3_sp_beach_chair" to "ビーチチェア",
      "j3_sp_watergun" to "水鉄砲",
      "j3_sp_lantern_floating" to "空に浮かぶランタン",
    ),
  )

private fun optionGenre183(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・スタジオ小道具（追加）",
    listOf(
    optionPart668(),
    ),
  )

private fun optionPart669(): CharacterOptionPart =
  CharacterOptionPart(
    "背景・エフェクト・構図",
    false,
    listOf(
      "j3_se_blur" to "背景のぼかし",
      "j3_se_bokeh" to "ボケ玉",
      "j3_se_vignette" to "周辺を暗くする",
      "j3_se_chromatic" to "色のにじみ",
      "j3_se_vintage" to "レトロな色調",
      "j3_se_wide" to "広角レンズ",
      "j3_se_tele" to "望遠レンズ",
      "j3_se_portrait" to "縦の構図",
      "j3_se_over" to "肩ごしの構図",
      "j3_se_window" to "窓ごしの構図",
      "j3_se_crowd" to "人ごみの背景",
      "j3_se_rain_window" to "窓の雨だれ",
      "j3_se_mist" to "低くたちこめる霧",
      "j3_se_light_leak" to "光漏れ",
      "j3_se_lens_dirt" to "レンズの汚れ",
      "j3_se_petal_rose" to "バラの花びら",
      "j3_se_flame" to "炎",
      "j3_se_thunder" to "稲妻",
      "j3_se_aura" to "オーラ",
      "j3_se_disco" to "ミラーボール",
      "j3_se_gradient_bg" to "グラデーションの背景",
      "j3_se_solid_bg" to "無地の背景",
      "j3_se_black_bg" to "黒い背景",
      "j3_se_pink_bg" to "ピンクの背景",
      "j3_se_blue_bg" to "青い背景",
      "j3_se_pattern_bg" to "柄の背景",
      "j3_se_heart_bg" to "ハートの背景",
      "j3_se_star_bg" to "星の背景",
      "j3_se_flower_bg" to "花の背景",
      "j3_se_curtain_bg" to "カーテンの背景",
      "j3_se_brick_bg" to "レンガの壁",
      "j3_se_wood_bg" to "木の壁",
      "j3_se_tile_bg" to "タイルの壁",
      "j3_se_marble" to "大理石の背景",
      "j3_se_white_room" to "白い部屋",
      "j3_se_dark_room" to "暗い部屋",
      "j3_se_border" to "枠線",
      "j3_se_polaroid" to "インスタント写真風",
      "j3_se_filmstrip" to "フィルムの枠",
      "j3_se_text_frame" to "文字入りの枠",
      "j3_se_censor_bar" to "黒い帯",
      "j3_se_halftone" to "網点",
      "j3_se_watercolor" to "水彩風",
      "j3_se_oil" to "油絵風",
      "j3_se_comic" to "マンガ風",
      "j3_se_3d" to "3Dレンダ風",
    ),
  )

private fun optionGenre184(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ハニセレ系・スタジオ背景・エフェクト・構図（追加）",
    listOf(
    optionPart669(),
    ),
  )

private fun optionPart670(): CharacterOptionPart =
  CharacterOptionPart(
    "顔の輪郭",
    true,
    listOf(
      "j4_face_inverted_tri" to "逆三角形の輪郭",
      "j4_face_soft_jaw" to "あごがやわらかい輪郭",
    ),
  )

private fun optionPart671(): CharacterOptionPart =
  CharacterOptionPart(
    "目の形",
    true,
    listOf(
      "j4_eye_big_round" to "大きく丸い目",
      "j4_eye_single_lid" to "ひとえまぶた",
      "j4_eye_gentle" to "おだやかな目",
    ),
  )

private fun optionPart672(): CharacterOptionPart =
  CharacterOptionPart(
    "眉の形",
    true,
    listOf(
      "j4_brow_angry" to "きりっとした眉",
    ),
  )

private fun optionPart673(): CharacterOptionPart =
  CharacterOptionPart(
    "鼻・口の形",
    true,
    listOf(
      "j4_nose_round" to "丸い鼻先",
    ),
  )

private fun optionPart674(): CharacterOptionPart =
  CharacterOptionPart(
    "耳・歯",
    false,
    listOf(
      "j4_ear_big" to "大きめの耳",
      "j4_ear_small" to "小さな耳",
      "j4_tooth_gap" to "すきっ歯",
    ),
  )

private fun optionGenre185(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・顔と輪郭（追加）",
    listOf(
    optionPart670(),
    optionPart671(),
    optionPart672(),
    optionPart673(),
    optionPart674(),
    ),
  )

private fun optionPart675(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の長さ",
    true,
    listOf(
      "j4_hair_bob_short" to "ショートボブ",
    ),
  )

private fun optionPart676(): CharacterOptionPart =
  CharacterOptionPart(
    "結び方・アレンジ",
    false,
    listOf(
      "j4_tie_bun_top" to "てっぺんのお団子",
      "j4_tie_hair_loop" to "輪っかにした髪",
    ),
  )

private fun optionPart677(): CharacterOptionPart =
  CharacterOptionPart(
    "髪の色と模様",
    false,
    listOf(
      "j4_hc_pastel" to "パステルカラーの髪",
    ),
  )

private fun optionGenre186(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・髪（追加）",
    listOf(
    optionPart675(),
    optionPart676(),
    optionPart677(),
    ),
  )

private fun optionPart678(): CharacterOptionPart =
  CharacterOptionPart(
    "全体の体つき",
    true,
    listOf(
      "j4_body_athletic" to "引き締まった運動体型",
      "j4_body_pear" to "下半身にボリューム",
    ),
  )

private fun optionPart679(): CharacterOptionPart =
  CharacterOptionPart(
    "ウエスト・腰まわり",
    false,
    listOf(
      "j4_waist_straight" to "くびれ控えめ",
    ),
  )

private fun optionPart680(): CharacterOptionPart =
  CharacterOptionPart(
    "脚・お尻",
    false,
    listOf(
      "j4_leg_slim_thigh" to "細い太もも",
    ),
  )

private fun optionPart681(): CharacterOptionPart =
  CharacterOptionPart(
    "肩・首・腕・手",
    false,
    listOf(
      "j4_foot_arch" to "足の甲が高い足",
    ),
  )

private fun optionPart682(): CharacterOptionPart =
  CharacterOptionPart(
    "体の特徴",
    false,
    listOf(
      "j4_body_scar_old" to "うすい古い傷あと",
      "j4_body_flushed" to "体がほんのり赤い",
    ),
  )

private fun optionGenre187(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・体型の表現（追加）",
    listOf(
    optionPart678(),
    optionPart679(),
    optionPart680(),
    optionPart681(),
    optionPart682(),
    ),
  )

private fun optionPart683(): CharacterOptionPart =
  CharacterOptionPart(
    "肌の色味",
    true,
    listOf(
      "j4_skin_natural" to "ふつうの肌色",
      "j4_skin_tan_light" to "うすい日焼け肌",
      "j4_skin_tan_deep" to "濃い日焼け肌",
      "j4_skin_rosy" to "ばら色の頬の肌",
      "j4_skin_cool" to "青みがかった白い肌",
    ),
  )

private fun optionPart684(): CharacterOptionPart =
  CharacterOptionPart(
    "日焼けあと",
    true,
    listOf(
      "j4_tan_one_piece" to "ワンピース水着の日焼けあと",
      "j4_tan_shorts" to "短パンの形の日焼けあと",
      "j4_tan_strap" to "肩ひもの日焼けあと",
      "j4_tan_sock" to "靴下の形の日焼けあと",
      "j4_tan_glove" to "手袋の形の日焼けあと",
      "j4_tan_gradient" to "なだらかな日焼けグラデ",
    ),
  )

private fun optionPart685(): CharacterOptionPart =
  CharacterOptionPart(
    "肌のつや・質感",
    true,
    listOf(
      "j4_skin_dewy" to "しっとりつやのある肌",
      "j4_skin_pores" to "肌のきめ（リアル）",
    ),
  )

private fun optionPart686(): CharacterOptionPart =
  CharacterOptionPart(
    "タトゥー・ペイント",
    false,
    listOf(
      "j4_tat_lower_belly" to "下腹のタトゥー",
      "j4_tat_stickers" to "肌に貼るシール",
    ),
  )

private fun optionGenre188(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・肌と体のペイント（追加）",
    listOf(
    optionPart683(),
    optionPart684(),
    optionPart685(),
    optionPart686(),
    ),
  )

private fun optionPart687(): CharacterOptionPart =
  CharacterOptionPart(
    "アイメイク",
    false,
    listOf(
      "j4_mk_liner_thin" to "細いアイライン",
    ),
  )

private fun optionPart688(): CharacterOptionPart =
  CharacterOptionPart(
    "チーク・リップ",
    false,
    listOf(
      "j4_mk_blush_deep" to "濃いほほ紅",
      "j4_mk_lip_gradient" to "グラデーションのリップ",
    ),
  )

private fun optionPart689(): CharacterOptionPart =
  CharacterOptionPart(
    "顔まわりのメイク",
    false,
    listOf(
      "j4_mk_eyebrow_fill" to "眉を描き足したメイク",
      "j4_mk_nails_color" to "はなやかなネイル",
    ),
  )

private fun optionGenre189(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・メイク（追加）",
    listOf(
    optionPart687(),
    optionPart688(),
    optionPart689(),
    ),
  )

private fun optionPart690(): CharacterOptionPart =
  CharacterOptionPart(
    "下着",
    false,
    listOf(
      "j4_und_pan_high" to "ハイレグのショーツ",
    ),
  )

private fun optionPart691(): CharacterOptionPart =
  CharacterOptionPart(
    "水着",
    false,
    listOf(
      "j4_sw_bikini_tie" to "ひも結びのビキニ",
      "j4_sw_cutout" to "カットアウトの水着",
    ),
  )

private fun optionPart692(): CharacterOptionPart =
  CharacterOptionPart(
    "靴",
    false,
    listOf(
      "j4_sh_boots" to "ブーツ",
      "j4_sh_boots_thigh" to "ももまでのブーツ",
      "j4_sh_zori" to "ぞうり",
    ),
  )

private fun optionPart693(): CharacterOptionPart =
  CharacterOptionPart(
    "コスチューム",
    false,
    listOf(
      "j4_co_cabin" to "客室乗務員ふうの服",
      "j4_co_waitress" to "ウェイトレスの服",
      "j4_co_dress_long" to "ロングドレス",
      "j4_co_sportswear" to "スポーツウェア",
      "j4_co_knight" to "女騎士の鎧",
      "j4_co_cowgirl" to "カウガール衣装",
      "j4_co_dancer" to "ダンサーの衣装",
      "j4_co_belly" to "踊り子の衣装",
      "j4_co_overalls_only" to "サロペットだけを着た姿",
      "j4_co_oneshoulder" to "片側の肩が出る服",
      "j4_co_sideopen" to "脇の開いた服",
      "j4_co_leather" to "レザーの服",
      "j4_co_untied" to "ひもをほどいた服",
      "j4_co_pulled_up" to "すそを持ち上げた服",
      "j4_co_partial_nude" to "一部だけ脱いだ姿",
    ),
  )

private fun optionGenre190(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・衣装の種類（追加）",
    listOf(
    optionPart690(),
    optionPart691(),
    optionPart692(),
    optionPart693(),
    ),
  )

private fun optionPart694(): CharacterOptionPart =
  CharacterOptionPart(
    "頭まわり",
    false,
    listOf(
      "j4_ac_headband_cloth" to "布のヘアバンド",
      "j4_ac_headdress" to "頭かざり",
      "j4_ac_horns" to "つの",
    ),
  )

private fun optionPart695(): CharacterOptionPart =
  CharacterOptionPart(
    "顔・首・体",
    false,
    listOf(
      "j4_ac_glasses_square" to "四角いメガネ",
      "j4_ac_eyepatch_cute" to "かざりの眼帯",
      "j4_ac_wings" to "つばさ",
    ),
  )

private fun optionGenre191(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・アクセサリー（追加）",
    listOf(
    optionPart694(),
    optionPart695(),
    ),
  )

private fun optionPart696(): CharacterOptionPart =
  CharacterOptionPart(
    "性格",
    true,
    listOf(
      "j4_pe_gentle" to "おだやかで優しい",
      "j4_pe_cheerful" to "明るく元気",
      "j4_pe_shy" to "はずかしがり屋",
      "j4_pe_cool" to "クールで落ち着いている",
      "j4_pe_airhead" to "天然でのんびり",
      "j4_pe_mischievous" to "いたずら好き",
      "j4_pe_serious" to "まじめで几帳面",
      "j4_pe_proud" to "プライドが高い",
      "j4_pe_adventurous" to "冒険好き",
      "j4_pe_gourmet" to "食いしん坊",
      "j4_pe_bookish" to "読書好きでおとなしい",
      "j4_pe_tomboy" to "さっぱりした姉御肌",
      "j4_pe_ojousama" to "お嬢さま気質",
      "j4_pe_dominant" to "リードしたがる",
      "j4_pe_optimist" to "ポジティブ思考",
      "j4_pe_gloomy" to "ネガティブでもの静か",
      "j4_pe_curious" to "好奇心旺盛",
      "j4_pe_stubborn" to "頑固",
      "j4_pe_artistic" to "芸術家肌",
      "j4_pe_sporty" to "スポーツ好き",
      "j4_pe_mysterious" to "ミステリアス",
      "j4_pe_clumsy" to "ドジっ子",
    ),
  )

private fun optionPart697(): CharacterOptionPart =
  CharacterOptionPart(
    "恋愛の傾向",
    true,
    listOf(
      "j4_lv_romantic" to "ロマンチスト",
      "j4_lv_straightforward" to "好意をまっすぐ伝える",
      "j4_lv_slow_burn" to "ゆっくり距離を縮める",
      "j4_lv_passionate" to "情熱的",
      "j4_lv_tender" to "甘く優しい",
    ),
  )

private fun optionGenre192(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・性格のタイプ（追加）",
    listOf(
    optionPart696(),
    optionPart697(),
    ),
  )

private fun optionPart698(): CharacterOptionPart =
  CharacterOptionPart(
    "表情",
    false,
    listOf(
      "j4_ex_annoyed" to "あきれ顔",
      "j4_ex_heavy_breath" to "息づかいの荒い顔",
      "j4_ex_disdain" to "見くだす顔",
    ),
  )

private fun optionPart699(): CharacterOptionPart =
  CharacterOptionPart(
    "しぐさ",
    false,
    listOf(
      "j4_ge_cover_face" to "両手で顔をおおう",
      "j4_ge_chin_rest" to "ほおづえをつく",
      "j4_ge_shrug" to "肩をすくめる",
      "j4_ge_lean_on" to "寄りかかる",
      "j4_ge_head_pat" to "頭をなでられる",
      "j4_ge_toast" to "かんぱいする",
    ),
  )

private fun optionPart700(): CharacterOptionPart =
  CharacterOptionPart(
    "ポーズ（立ち・座り・寝姿）",
    false,
    listOf(
      "j4_po_stretch_legs" to "開脚ストレッチ",
      "j4_po_yoga_dog" to "下向きの犬のポーズ",
      "j4_po_dutch" to "ななめに構えたポーズ",
      "j4_po_sleep_sit" to "座ったまま眠る",
    ),
  )

private fun optionGenre193(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・表情としぐさ（追加）",
    listOf(
    optionPart698(),
    optionPart699(),
    optionPart700(),
    ),
  )

private fun optionPart701(): CharacterOptionPart =
  CharacterOptionPart(
    "家や部屋",
    false,
    listOf(
      "j4_sc_cabin" to "木の小屋",
      "j4_sc_treehouse" to "ツリーハウス",
      "j4_sc_tent" to "テント",
      "j4_sc_hut" to "わらぶきの小屋",
      "j4_sc_loft" to "ロフト",
      "j4_sc_dining" to "食卓",
      "j4_sc_bath_wood" to "木のお風呂",
      "j4_sc_veranda" to "縁側",
      "j4_sc_garden_house" to "庭つきの家",
      "j4_sc_workshop" to "作業小屋",
      "j4_sc_bonfire" to "たき火のそば",
      "j4_sc_campsite" to "キャンプ場",
      "j4_sc_bed_canopy" to "天がいつきのベッド",
      "j4_sc_futon" to "ふとん",
    ),
  )

private fun optionPart702(): CharacterOptionPart =
  CharacterOptionPart(
    "島・自然",
    false,
    listOf(
      "j4_na_white_sand" to "白い砂浜",
      "j4_na_cove" to "入り江",
      "j4_na_cliff" to "断崖",
      "j4_na_cave" to "ほら穴",
      "j4_na_jungle" to "ジャングル",
      "j4_na_palm" to "ヤシの木",
      "j4_na_bamboo" to "竹林",
      "j4_na_river" to "川",
      "j4_na_pond" to "池",
      "j4_na_lake" to "湖",
      "j4_na_tidepool" to "潮だまり",
      "j4_na_coral" to "サンゴ礁",
      "j4_na_volcano" to "火山",
      "j4_na_mountain" to "山の中",
      "j4_na_hill" to "丘の上",
      "j4_na_rock" to "岩場",
      "j4_na_sandbar" to "砂州",
      "j4_na_lighthouse" to "灯台",
      "j4_na_pier" to "桟橋",
      "j4_na_bridge_wood" to "木のつり橋",
      "j4_na_dock" to "船着き場",
      "j4_na_boat" to "小さな船",
      "j4_na_raft" to "いかだ",
      "j4_na_farm" to "畑",
      "j4_na_orchard" to "果樹園",
      "j4_na_garden" to "庭",
      "j4_na_greenhouse" to "温室",
      "j4_na_ranch" to "牧場",
      "j4_na_fishing_spot" to "釣り場",
    ),
  )

private fun optionPart703(): CharacterOptionPart =
  CharacterOptionPart(
    "時間と空",
    true,
    listOf(
      "j4_tm_dawn" to "夜明け",
      "j4_tm_noon" to "昼",
      "j4_tm_afternoon" to "午後",
      "j4_tm_dusk" to "たそがれ",
      "j4_tm_night" to "夜",
    ),
  )

private fun optionPart704(): CharacterOptionPart =
  CharacterOptionPart(
    "天気",
    true,
    listOf(
      "j4_we_sunny" to "晴れ",
      "j4_we_cloudy" to "くもり",
      "j4_we_drizzle" to "小雨",
      "j4_we_storm" to "あらし",
      "j4_we_wind" to "強い風",
      "j4_we_rainbow" to "にじ",
      "j4_we_after_rain" to "雨上がり",
      "j4_we_heat" to "うだるような暑さ",
      "j4_we_sunshower" to "天気雨",
    ),
  )

private fun optionPart705(): CharacterOptionPart =
  CharacterOptionPart(
    "季節",
    true,
    listOf(
      "j4_se_spring" to "春",
      "j4_se_summer" to "夏",
      "j4_se_autumn" to "秋",
      "j4_se_winter" to "冬",
      "j4_se_cherry" to "桜の季節",
      "j4_se_leaves" to "紅葉",
    ),
  )

private fun optionPart706(): CharacterOptionPart =
  CharacterOptionPart(
    "光",
    false,
    listOf(
      "j4_li_rim" to "ふち取りの光",
      "j4_li_lantern" to "ランタンの光",
      "j4_li_firelight" to "たき火の光",
      "j4_li_warm" to "あたたかい色の光",
      "j4_li_cool" to "ひんやりした色の光",
      "j4_li_moon_sea" to "月夜の海",
    ),
  )

private fun optionGenre194(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・生活の場面（追加）",
    listOf(
    optionPart701(),
    optionPart702(),
    optionPart703(),
    optionPart704(),
    optionPart705(),
    optionPart706(),
    ),
  )

private fun optionPart707(): CharacterOptionPart =
  CharacterOptionPart(
    "毎日の行動",
    false,
    listOf(
      "j4_lf_baking" to "お菓子を焼く",
      "j4_lf_picnic" to "ピクニック",
      "j4_lf_bbq" to "バーベキュー",
      "j4_lf_laundry" to "せんたくをする",
      "j4_lf_gardening" to "畑しごとをする",
      "j4_lf_watering" to "水やりをする",
      "j4_lf_harvest" to "収かくをする",
      "j4_lf_fishing" to "つりをする",
      "j4_lf_diving" to "もぐる",
      "j4_lf_swimming" to "泳ぐ",
      "j4_lf_surfing" to "サーフィン",
      "j4_lf_snorkel" to "シュノーケリング",
      "j4_lf_beach_play" to "砂浜で遊ぶ",
      "j4_lf_sandcastle" to "砂のお城をつくる",
      "j4_lf_gather" to "木の実や貝を集める",
      "j4_lf_craft" to "ものづくりをする",
      "j4_lf_sewing" to "裁縫をする",
      "j4_lf_building" to "小屋を建てる",
      "j4_lf_chopping" to "薪を割る",
      "j4_lf_campfire" to "たき火をおこす",
      "j4_lf_cooking_fire" to "火で焼く",
      "j4_lf_writing" to "日記を書く",
      "j4_lf_painting" to "絵を描く",
      "j4_lf_music" to "楽器を弾く",
      "j4_lf_guitar" to "ギターを弾く",
      "j4_lf_stargaze" to "星を見る",
      "j4_lf_sunbath" to "日光浴",
      "j4_lf_nap" to "昼寝をする",
      "j4_lf_wake" to "目をさます",
      "j4_lf_bathing" to "入浴する",
      "j4_lf_stretching" to "ストレッチ",
      "j4_lf_jog" to "ジョギング",
      "j4_lf_dance_party" to "ダンスパーティー",
      "j4_lf_karaoke" to "カラオケ",
      "j4_lf_game" to "ゲームで遊ぶ",
      "j4_lf_movie" to "映画を見る",
      "j4_lf_photo" to "写真をとる",
      "j4_lf_pet" to "ペットとふれあう",
      "j4_lf_feeding_pet" to "ペットにえさをやる",
      "j4_lf_shopping" to "買い物をする",
      "j4_lf_festival" to "お祭りを楽しむ",
      "j4_lf_sparkler" to "線香花火",
      "j4_lf_lantern" to "ちょうちんを持つ",
      "j4_lf_hotspring" to "温泉につかる",
      "j4_lf_wine" to "ワインで乾杯",
      "j4_lf_late_talk" to "夜の語らい",
    ),
  )

private fun optionGenre195(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・生活の行動（追加）",
    listOf(
    optionPart707(),
    ),
  )

private fun optionPart708(): CharacterOptionPart =
  CharacterOptionPart(
    "ふれあい",
    false,
    listOf(
      "j4_in_kiss_forehead" to "おでこへのキス",
      "j4_in_embrace" to "抱きあう",
      "j4_in_face_hold" to "顔を両手でそっと包む",
      "j4_in_ear_nibble" to "耳をあまがみする",
      "j4_in_neck_nuzzle" to "首もとにすり寄る",
      "j4_in_wash_back" to "背中を流す",
      "j4_in_wash_hair" to "髪を洗ってあげる",
      "j4_in_bath_together" to "いっしょに入浴する",
      "j4_in_bed_together" to "いっしょにベッドで過ごす",
      "j4_in_wake_kiss" to "寝起きのキス",
      "j4_in_straddle" to "またがって見つめあう",
    ),
  )

private fun optionPart709(): CharacterOptionPart =
  CharacterOptionPart(
    "体位（合意のある成人どうし）",
    false,
    listOf(
      "j4_pos_standing_back" to "立ったまま後ろから",
      "j4_pos_sitting_face" to "向かいあって座る体位",
      "j4_pos_lap" to "ひざの上にのる体位",
      "j4_pos_legs_wrap" to "脚を相手にからめる体位",
      "j4_pos_mating_press" to "深く押し重なる体位",
      "j4_pos_69" to "たがいに向きあう体位",
      "j4_pos_bridge" to "背をそらせた体位",
      "j4_pos_chair" to "いすに座った体位",
      "j4_pos_wall" to "壁にもたれる体位",
      "j4_pos_table" to "テーブルに手をつく体位",
      "j4_pos_bath" to "浴室での体位",
      "j4_pos_bed_edge" to "ベッドの端での体位",
      "j4_pos_carry" to "抱きかかえられる体位",
      "j4_pos_piledriver" to "腰を高く上げる体位",
      "j4_pos_side_leg" to "片脚を上げた横向きの体位",
    ),
  )

private fun optionPart710(): CharacterOptionPart =
  CharacterOptionPart(
    "行為の種類（合意のある成人どうし）",
    false,
    listOf(
      "j4_act_breast_play" to "胸への愛撫",
      "j4_act_nipple" to "乳首への愛撫",
      "j4_act_thigh" to "太ももでのご奉仕",
      "j4_act_footjob" to "足でのご奉仕",
      "j4_act_tribadism" to "女性どうしで体を重ねる",
      "j4_act_yuri_kiss" to "女性どうしのキス",
      "j4_act_ice" to "氷を使った愛撫",
      "j4_act_feather" to "羽根でのくすぐり",
      "j4_act_mirror_sex" to "鏡の前で",
      "j4_act_cuddle_after" to "寄りそう後戯",
    ),
  )

private fun optionPart711(): CharacterOptionPart =
  CharacterOptionPart(
    "親密な場面の状態",
    false,
    listOf(
      "j4_st_arch" to "体をそらす",
      "j4_st_grab_pillow" to "まくらをにぎる",
      "j4_st_clutch_back" to "相手の背中にしがみつく",
      "j4_st_leg_lock" to "脚をからめる",
      "j4_st_laughing" to "笑いあう",
      "j4_st_after_exhaust" to "ぐったりした様子",
      "j4_st_messy_bed" to "乱れたベッド",
    ),
  )

private fun optionGenre196(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・大人どうしの親密な場面（追加）",
    listOf(
    optionPart708(),
    optionPart709(),
    optionPart710(),
    optionPart711(),
    ),
  )

private fun optionPart712(): CharacterOptionPart =
  CharacterOptionPart(
    "カメラ・構図",
    true,
    listOf(
      "j4_cam_wide" to "広い風景の中の人物",
      "j4_cam_dutch" to "傾けた構図",
      "j4_cam_selfie" to "自撮り構図",
      "j4_cam_mirror" to "鏡ごしの構図",
      "j4_cam_depth" to "背景をぼかした構図",
      "j4_cam_fisheye" to "魚眼レンズふう",
      "j4_cam_telephoto" to "望遠レンズふう",
      "j4_cam_symmetry" to "左右対称の構図",
      "j4_cam_framed" to "窓の枠に収めた構図",
      "j4_cam_reflection" to "水面や窓への映りこみ",
      "j4_cam_polaroid" to "インスタント写真ふう",
      "j4_cam_film" to "フィルムふうの色調",
      "j4_cam_sepia" to "セピア色",
      "j4_cam_mono" to "白黒",
      "j4_cam_vivid" to "あざやかな色調",
    ),
  )

private fun optionPart713(): CharacterOptionPart =
  CharacterOptionPart(
    "背景と効果",
    false,
    listOf(
      "j4_fx_sparkles" to "きらきらしたかがやき",
      "j4_fx_smoke" to "けむり",
      "j4_fx_butterflies" to "ちょう",
      "j4_fx_birds" to "飛ぶ鳥",
      "j4_fx_wind_skirt" to "風にゆれるすそ",
      "j4_fx_snowflakes" to "雪の結晶",
      "j4_fx_sunset_glow" to "夕やけの空",
      "j4_fx_blurry_bg" to "ぼんやりしたライトの背景",
      "j4_fx_stage_light" to "舞台のライト",
    ),
  )

private fun optionPart714(): CharacterOptionPart =
  CharacterOptionPart(
    "家具・小道具",
    false,
    listOf(
      "j4_pr_sofa" to "ソファ",
      "j4_pr_blanket" to "もうふ",
      "j4_pr_table" to "テーブル",
      "j4_pr_chair_wood" to "木のいす",
      "j4_pr_rocking" to "ゆりいす",
      "j4_pr_curtain" to "カーテン",
      "j4_pr_lamp" to "スタンド",
      "j4_pr_vase" to "花びん",
      "j4_pr_cup" to "カップ",
      "j4_pr_fruit" to "くだもの",
      "j4_pr_shaved_ice" to "かき氷",
      "j4_pr_watermelon" to "スイカ",
      "j4_pr_coconut" to "ココナッツのドリンク",
      "j4_pr_beach_ball" to "ビーチボール",
      "j4_pr_surfboard" to "サーフボード",
      "j4_pr_parasol" to "ビーチパラソル",
      "j4_pr_bucket" to "桶",
      "j4_pr_fishing_rod" to "つりざお",
      "j4_pr_basket" to "かご",
      "j4_pr_lantern_paper" to "ちょうちん",
      "j4_pr_swing" to "ぶらんこ",
      "j4_pr_hot_drink" to "あたたかい飲みもの",
    ),
  )

private fun optionGenre197(): CharacterOptionGenre =
  CharacterOptionGenre(
    "AI系・スタジオと撮影（追加）",
    listOf(
    optionPart712(),
    optionPart713(),
    optionPart714(),
    ),
  )

private fun optionPart715(): CharacterOptionPart =
  CharacterOptionPart(
    "場所",
    false,
    listOf(
      "j5_cafe_terrace" to "カフェのテラス席",
      "j5_cafe_counter" to "カフェのカウンター席",
      "j5_beach_day" to "昼のビーチ",
      "j5_beach_sunset" to "夕暮れのビーチ",
      "j5_seaside_path" to "海辺の遊歩道",
      "j5_night_view" to "夜景の見える展望台",
      "j5_nightclub_floor" to "ナイトクラブのフロア",
      "j5_club_bar" to "バーのカウンター",
      "j5_amusement_park" to "遊園地",
      "j5_ferris_wheel" to "観覧車のゴンドラ内",
      "j5_clothing_store" to "洋服店の店内",
      "j5_supermarket" to "スーパーマーケット",
      "j5_island_city" to "海に面した人工島の街並み",
      "j5_hotel_suite" to "高層ホテルのスイートルーム",
      "j5_rooftop_lounge" to "屋上のラウンジ",
      "j5_pier_night" to "夜の桟橋",
      "j5_shopping_street" to "にぎやかな商店街",
      "j5_home_kitchen" to "自宅のキッチン",
      "j5_dining_room" to "ダイニングで向かい合う席",
    ),
  )

private fun optionPart716(): CharacterOptionPart =
  CharacterOptionPart(
    "時間帯・光",
    true,
    listOf(
      "j5_friday_evening" to "金曜の夕方の街",
      "j5_morning_light" to "朝の明るい光",
      "j5_midnight_neon" to "深夜のネオン",
      "j5_sunday_late" to "日曜深夜の静かな街",
      "j5_rain_night" to "雨の夜の街",
      "j5_blue_hour" to "夜明け前の青い空",
    ),
  )

private fun optionGenre198(): CharacterOptionGenre =
  CharacterOptionGenre(
    "街のデートスポット（追加）",
    listOf(
    optionPart715(),
    optionPart716(),
    ),
  )

private fun optionPart717(): CharacterOptionPart =
  CharacterOptionPart(
    "行動",
    false,
    listOf(
      "j5_shopping_bags" to "買い物袋を持つ",
      "j5_try_on_clothes" to "試着した服を見せる",
      "j5_serve_dish" to "できた料理を運ぶ",
      "j5_feed_spoon" to "食べさせてあげる",
      "j5_take_selfie" to "自撮りをする",
      "j5_photo_pose" to "カメラに向かってポーズをとる",
      "j5_video_call" to "ビデオ通話をする",
      "j5_texting" to "スマートフォンでメッセージを打つ",
      "j5_night_view_gaze" to "夜景を並んで眺める",
      "j5_sea_breeze_hair" to "海風に髪をなびかせる",
      "j5_shared_umbrella" to "ひとつの傘に入る",
      "j5_drink_cocktail" to "カクテルを飲む",
      "j5_play_claw_game" to "クレーンゲームで遊ぶ",
      "j5_hug_plush" to "景品のぬいぐるみを抱える",
      "j5_reception_desk" to "受付で手続きをする",
    ),
  )

private fun optionGenre199(): CharacterOptionGenre =
  CharacterOptionGenre(
    "デート・日常の行動（追加）",
    listOf(
    optionPart717(),
    ),
  )

private fun optionPart718(): CharacterOptionPart =
  CharacterOptionPart(
    "顎のライン",
    true,
    listOf(
      "j5_jaw_sharp" to "シャープな顎",
      "j5_jaw_round" to "丸みのある顎",
      "j5_jaw_square" to "角ばった顎",
      "j5_jaw_slim" to "細くすっきりした顎",
      "j5_jaw_short" to "短めの顎",
    ),
  )

private fun optionPart719(): CharacterOptionPart =
  CharacterOptionPart(
    "頬",
    true,
    listOf(
      "j5_cheek_flat" to "平らな頬",
    ),
  )

private fun optionPart720(): CharacterOptionPart =
  CharacterOptionPart(
    "目の周り",
    true,
    listOf(
      "j5_eye_deep_set" to "彫りの深い目もと",
      "j5_eye_shallow" to "平らで穏やかな目もと",
      "j5_temple_smooth" to "なだらかなこめかみ",
    ),
  )

private fun optionPart721(): CharacterOptionPart =
  CharacterOptionPart(
    "ヘアサロン風のアレンジ",
    false,
    listOf(
      "j5_salon_cut_long" to "毛先を長くのばす",
      "j5_salon_cut_short" to "毛先を短く整える",
      "j5_salon_comb_flow" to "髪を流れるようにとかす",
      "j5_salon_curl_ends" to "毛先をカールさせる",
      "j5_salon_roll_up" to "毛先を内側に巻く",
      "j5_salon_roll_out" to "毛先を外側にはねさせる",
      "j5_salon_shine" to "つやのある髪",
    ),
  )

private fun optionPart722(): CharacterOptionPart =
  CharacterOptionPart(
    "性格の雰囲気",
    true,
    listOf(
      "j5_pers_innocent" to "天真らんまんな表情",
      "j5_pers_sassy" to "生意気そうな表情",
      "j5_pers_serene" to "おだやかでうららかな表情",
      "j5_pers_smiling" to "いつも笑顔の表情",
    ),
  )

private fun optionGenre200(): CharacterOptionGenre =
  CharacterOptionGenre(
    "キャラメイク（顔・髪・性格）（追加）",
    listOf(
    optionPart718(),
    optionPart719(),
    optionPart720(),
    optionPart721(),
    optionPart722(),
    ),
  )

private fun optionPart723(): CharacterOptionPart =
  CharacterOptionPart(
    "揺れ・質感",
    false,
    listOf(
      "j5_jiggle_breast" to "胸が自然に揺れる",
      "j5_jiggle_belly" to "おなかがやわらかく揺れる",
      "j5_jiggle_hair" to "歩くと髪が揺れる",
      "j5_skin_rim_light" to "肌に縁取りの光が当たる",
      "j5_fabric_fold_detail" to "服のしわが細かく見える",
      "j5_env_light_tint" to "周囲の光の色が肌に映る",
      "j5_sun_glow_skin" to "日差しで肌が輝く",
    ),
  )

private fun optionGenre201(): CharacterOptionGenre =
  CharacterOptionGenre(
    "体の動き・質感（追加）",
    listOf(
    optionPart723(),
    ),
  )

private fun optionPart724(): CharacterOptionPart =
  CharacterOptionPart(
    "衣装",
    false,
    listOf(
      "j5_outfit_date_casual" to "デート用のカジュアルな服",
      "j5_outfit_beach_cover" to "水着の上に羽織る薄手の上着",
      "j5_outfit_night_dress" to "夜の外出用ドレス",
      "j5_outfit_dance_practice" to "ダンス練習用のウェア",
      "j5_outfit_cook_apron" to "料理用のエプロンスタイル",
      "j5_outfit_leather_harness" to "革のハーネス風の装い",
      "j5_outfit_mistress" to "女王様風の衣装",
      "j5_outfit_cafe_waitress" to "カフェの店員風の服",
      "j5_outfit_party_gown" to "パーティー用のロングドレス",
      "j5_outfit_sheer_blouse" to "透け感のあるブラウス",
    ),
  )

private fun optionGenre202(): CharacterOptionGenre =
  CharacterOptionGenre(
    "衣装（街・ダンス・撮影）（追加）",
    listOf(
    optionPart724(),
    ),
  )

private fun optionPart725(): CharacterOptionPart =
  CharacterOptionPart(
    "ステージ",
    false,
    listOf(
      "j5_dance_stage_spot" to "スポットライトのステージ",
      "j5_dance_twirl" to "くるりと回るダンス",
      "j5_dance_pose_finish" to "決めポーズで終わる",
      "j5_dance_pair" to "ふたりで踊る",
      "j5_dance_backstage" to "舞台袖で出番を待つ",
      "j5_dance_lights" to "色とりどりの照明",
    ),
  )

private fun optionGenre203(): CharacterOptionGenre =
  CharacterOptionGenre(
    "ダンス・ステージ（追加）",
    listOf(
    optionPart725(),
    ),
  )

private fun optionPart726(): CharacterOptionPart =
  CharacterOptionPart(
    "ふれあい",
    false,
    listOf(
      "j5_int_kiss_soft" to "やさしいキス",
      "j5_int_kiss_deep" to "深いキス",
      "j5_int_forehead" to "おでこを寄せ合う",
      "j5_int_lie_together" to "並んで横になる",
      "j5_int_massage" to "肩や背中をマッサージする",
      "j5_int_whisper" to "耳もとでささやく",
      "j5_int_undress_help" to "服を脱がせ合う",
      "j5_int_shower_together" to "ふたりでシャワーを浴びる",
    ),
  )

private fun optionPart727(): CharacterOptionPart =
  CharacterOptionPart(
    "体位",
    false,
    listOf(
      "j5_pos_missionary" to "正常位",
      "j5_pos_cowgirl" to "騎乗位",
      "j5_pos_reverse_cowgirl" to "背面騎乗位",
      "j5_pos_doggy" to "後背位",
      "j5_pos_spoon" to "横向きで後ろから",
      "j5_pos_standing" to "立ったまま",
      "j5_pos_lotus" to "向かい合って座る",
      "j5_pos_prone_bone" to "うつぶせ",
      "j5_pos_carried" to "抱きかかえる",
    ),
  )

private fun optionPart728(): CharacterOptionPart =
  CharacterOptionPart(
    "行為",
    false,
    listOf(
      "j5_act_oral_her" to "口でのご奉仕",
      "j5_act_oral_him" to "口での愛撫を受ける",
      "j5_act_paizuri" to "胸ではさむ",
      "j5_act_handjob" to "手でのご奉仕",
      "j5_act_grinding" to "体を擦り合わせる",
      "j5_act_fingering" to "指でふれる",
      "j5_act_face_cover" to "顔を手で隠す恥じらい",
    ),
  )

private fun optionGenre204(): CharacterOptionGenre =
  CharacterOptionGenre(
    "親密な場面の種類（成人同士・合意）（追加）",
    listOf(
    optionPart726(),
    optionPart727(),
    optionPart728(),
    ),
  )

private fun optionPart729(): CharacterOptionPart =
  CharacterOptionPart(
    "笑い方",
    true,
    listOf(
      "k1_expr_smile_gentle" to "ほほえみ（やわらかい）",
      "k1_expr_smile_warm" to "あたたかい笑顔",
      "k1_expr_smile_closed_eyes" to "目を閉じて微笑む",
      "k1_expr_smile_eyes_crinkle" to "目尻が下がる笑顔",
      "k1_expr_smile_fang" to "八重歯でにっこり",
      "k1_expr_laugh_belly" to "お腹を抱えて笑う",
      "k1_expr_laugh_hysterical" to "笑いが止まらない",
      "k1_expr_laugh_suppress" to "笑いをこらえる",
      "k1_expr_laugh_tearful" to "笑いすぎて涙",
      "k1_expr_smile_playful" to "いたずらっぽい笑み",
      "k1_expr_smile_smug" to "したり顔",
      "k1_expr_smile_confident" to "自信たっぷりの笑み",
      "k1_expr_smile_sweet" to "甘い笑顔",
      "k1_expr_smile_cheerful" to "元気な笑顔",
      "k1_expr_smile_bright" to "輝く笑顔",
      "k1_expr_smile_sunny" to "ひだまりのような笑顔",
      "k1_expr_smile_lopsided" to "片側だけ上がる笑み",
      "k1_expr_smile_sad" to "寂しげな微笑み",
      "k1_expr_smile_resigned" to "あきらめの笑み",
      "k1_expr_smile_knowing" to "すべてお見通しの笑み",
      "k1_expr_smile_mysterious" to "ミステリアスな笑み",
      "k1_expr_smile_gentle_motherly" to "慈愛の微笑み",
      "k1_expr_smile_pure" to "無邪気な笑顔",
      "k1_expr_smile_cheeky" to "生意気な笑み",
      "k1_expr_smile_evil" to "悪だくみの笑み",
      "k1_expr_smile_dazzling" to "まぶしい笑顔",
    ),
  )

private fun optionPart730(): CharacterOptionPart =
  CharacterOptionPart(
    "うれしい・満足",
    true,
    listOf(
      "k1_expr_joy_delight" to "大喜び",
      "k1_expr_joy_ecstatic" to "歓喜",
      "k1_expr_joy_banzai" to "万歳して喜ぶ",
      "k1_expr_joy_tears" to "うれし泣き",
      "k1_expr_joy_touched" to "感動して目をうるませる",
      "k1_expr_joy_content" to "満ち足りた表情",
      "k1_expr_joy_relieved" to "ほっと安心する顔",
      "k1_expr_joy_peaceful" to "やすらいだ顔",
      "k1_expr_joy_grateful" to "感謝の表情",
      "k1_expr_joy_anticipation" to "わくわくした顔",
      "k1_expr_joy_dreamy" to "うっとり夢見心地",
      "k1_expr_joy_love_struck" to "恋する顔",
      "k1_expr_joy_yum" to "おいしそうに味わう顔",
      "k1_expr_joy_nom_happy" to "ほおばって幸せそう",
      "k1_expr_joy_warm_fuzzy" to "ほっこりした顔",
      "k1_expr_joy_victory" to "勝ち誇った顔",
      "k1_expr_joy_cheeks_pink" to "ほおを染めて喜ぶ",
      "k1_expr_joy_sleeping_smile" to "寝顔で微笑む",
      "k1_expr_joy_embarrassed_happy" to "うれし恥ずかしい顔",
    ),
  )

private fun optionPart731(): CharacterOptionPart =
  CharacterOptionPart(
    "楽しさ・おどけ",
    true,
    listOf(
      "k1_expr_fun_amused" to "おもしろがる顔",
      "k1_expr_fun_mischief" to "いたずら心の顔",
      "k1_expr_fun_excited_grin" to "興奮した笑い",
      "k1_expr_fun_thrilled" to "スリルを楽しむ顔",
      "k1_expr_fun_curious_smile" to "興味津々の笑顔",
      "k1_expr_fun_wink_cheerful" to "ウインクして笑う",
      "k1_expr_fun_peace_smile" to "ピースして笑う",
      "k1_expr_fun_laughing_eyes_closed" to "目をつぶって大笑い",
      "k1_expr_fun_silly" to "おどけた顔",
      "k1_expr_fun_goofy_grin" to "まぬけな笑い",
      "k1_expr_fun_cheek_pull_face" to "ほっぺを引っ張った変顔",
      "k1_expr_fun_squint_grin" to "目を細めてにやり",
    ),
  )

private fun optionGenre205(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・喜び（追加）",
    listOf(
    optionPart729(),
    optionPart730(),
    optionPart731(),
    ),
  )

private fun optionPart732(): CharacterOptionPart =
  CharacterOptionPart(
    "怒りの段階",
    true,
    listOf(
      "k1_expr_anger_irritated" to "いらいらした顔",
      "k1_expr_anger_annoyed_sigh" to "うんざりしてため息",
      "k1_expr_anger_grumpy" to "不機嫌な顔",
      "k1_expr_anger_sulky" to "ふてくされた顔",
      "k1_expr_anger_stern" to "きびしい顔",
      "k1_expr_anger_seething" to "煮えくり返る怒り",
      "k1_expr_anger_shout" to "怒鳴る顔",
      "k1_expr_anger_snarl" to "うなる顔",
      "k1_expr_anger_growl" to "うなり声をあげる顔",
      "k1_expr_anger_fuming" to "ぷんすか怒る",
      "k1_expr_anger_stomp" to "地団駄を踏む",
      "k1_expr_anger_shaking_fist" to "拳を震わせる",
      "k1_expr_anger_pointing" to "指をさして怒る",
      "k1_expr_anger_scolding" to "叱る顔",
      "k1_expr_anger_hmph" to "ふんと顔をそむける",
      "k1_expr_anger_tsun" to "つんとすました顔",
      "k1_expr_anger_jealous" to "やきもちを焼く顔",
      "k1_expr_anger_jealous_glare" to "嫉妬のにらみ",
      "k1_expr_anger_disdain" to "見下す顔",
      "k1_expr_anger_snub" to "せせら笑う",
      "k1_expr_anger_sneer" to "冷笑",
      "k1_expr_anger_indignant" to "憤慨する顔",
      "k1_expr_anger_offended" to "心外だという顔",
      "k1_expr_anger_vengeful" to "復讐に燃える目",
      "k1_expr_anger_betrayed" to "裏切られた顔",
      "k1_expr_anger_tantrum_cry" to "怒りながら泣く",
      "k1_expr_anger_vexed" to "悔しがる顔",
      "k1_expr_anger_biting_handkerchief" to "ハンカチを噛んで悔しがる",
      "k1_expr_anger_dark_aura" to "黒いオーラの怒り",
      "k1_expr_anger_smiling_angry" to "笑顔で怒っている",
      "k1_expr_anger_pout_cheeks" to "ほほをふくらませて怒る",
      "k1_expr_anger_eyebrows_sharp" to "眉をつり上げる",
      "k1_expr_anger_death_stare" to "凍りつく視線",
      "k1_expr_anger_menacing" to "凄みのある顔",
      "k1_expr_anger_battle_cry" to "雄たけびを上げる",
      "k1_expr_anger_fed_up" to "あきれて怒る",
    ),
  )

private fun optionPart733(): CharacterOptionPart =
  CharacterOptionPart(
    "不満・ふくれ顔",
    true,
    listOf(
      "k1_expr_dissatisfy_pout" to "口をとがらせる",
      "k1_expr_dissatisfy_sullen" to "むすっとした顔",
      "k1_expr_dissatisfy_disapproval" to "不服そうな顔",
      "k1_expr_dissatisfy_flat_gaze" to "ジトっとした不満顔",
      "k1_expr_dissatisfy_bored" to "退屈そうな顔",
      "k1_expr_dissatisfy_sulk_corner" to "すねて座り込む",
      "k1_expr_dissatisfy_complaining" to "文句を言う顔",
      "k1_expr_dissatisfy_stubborn" to "頑固な顔",
      "k1_expr_dissatisfy_unconvinced" to "納得いかない顔",
    ),
  )

private fun optionGenre206(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・怒り（追加）",
    listOf(
    optionPart732(),
    optionPart733(),
    ),
  )

private fun optionPart734(): CharacterOptionPart =
  CharacterOptionPart(
    "悲しみの段階",
    true,
    listOf(
      "k1_expr_sad_gloomy" to "さえない顔",
      "k1_expr_sad_downcast" to "うつむいた顔",
      "k1_expr_sad_melancholy" to "物憂げな顔",
      "k1_expr_sad_wistful" to "切ない顔",
      "k1_expr_sad_heartbroken" to "胸が張り裂けそうな顔",
      "k1_expr_sad_despair" to "絶望の顔",
      "k1_expr_sad_grief" to "悲嘆にくれる顔",
      "k1_expr_sad_regret" to "後悔の顔",
      "k1_expr_sad_guilt" to "罪悪感のある顔",
      "k1_expr_sad_apologetic" to "申し訳なさそうな顔",
      "k1_expr_sad_disappointed" to "がっかりした顔",
      "k1_expr_sad_dejected" to "肩を落とす",
      "k1_expr_sad_depressed" to "ふさぎ込んだ顔",
      "k1_expr_sad_hollow" to "うつろな悲しみ",
      "k1_expr_sad_resigned" to "あきらめきった顔",
      "k1_expr_sad_longing" to "恋しがる顔",
      "k1_expr_sad_pining" to "思いを寄せる切ない顔",
      "k1_expr_sad_lonely_rain" to "雨の中のさみしい顔",
      "k1_expr_sad_pensive_window" to "窓辺の物思い",
      "k1_expr_sad_hug_knees_cry" to "ひざを抱えて泣く",
      "k1_expr_sad_hands_face_cry" to "両手で顔をおおって泣く",
      "k1_expr_sad_quiet_tears" to "静かに涙を流す",
      "k1_expr_sad_sobbing" to "しゃくりあげて泣く",
      "k1_expr_sad_tears_eyes_closed" to "目をつぶって泣く",
      "k1_expr_sad_trembling_lip" to "唇を震わせる",
      "k1_expr_sad_nose_red" to "鼻を赤くして泣く",
      "k1_expr_sad_sniffle" to "鼻をすする",
      "k1_expr_sad_puffy_eyes" to "泣きはらした目",
      "k1_expr_sad_pleading" to "すがるような目",
      "k1_expr_sad_puppy_eyes" to "うるうるの子犬の目",
      "k1_expr_sad_biting_lip_tears" to "唇をかんで涙をこらえる",
      "k1_expr_sad_looking_up_tears" to "上を向いて涙をこらえる",
      "k1_expr_sad_farewell" to "別れ際のさみしい笑顔",
      "k1_expr_sad_haggard" to "やつれた顔",
      "k1_expr_sad_shadowed_eyes" to "目に影が落ちる",
      "k1_expr_sad_hair_over_face" to "前髪で目が隠れる悲しみ",
      "k1_expr_sad_head_down" to "うなだれる",
    ),
  )

private fun optionGenre207(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・悲しみ（追加）",
    listOf(
    optionPart734(),
    ),
  )

private fun optionPart735(): CharacterOptionPart =
  CharacterOptionPart(
    "驚き",
    true,
    listOf(
      "k1_expr_surprise_slight" to "ちょっと驚いた顔",
      "k1_expr_surprise_shock" to "ショックを受けた顔",
      "k1_expr_surprise_stunned" to "呆然とした顔",
      "k1_expr_surprise_agape" to "あんぐり口を開ける",
      "k1_expr_surprise_dot_eyes" to "点目で固まる",
      "k1_expr_surprise_shrink_pupils" to "瞳孔が縮む",
      "k1_expr_surprise_pop_eyes" to "目が飛び出る",
      "k1_expr_surprise_pointing" to "指をさして驚く",
      "k1_expr_surprise_jump_back" to "飛びのいて驚く",
      "k1_expr_surprise_speechless" to "言葉を失う",
      "k1_expr_surprise_disbelief" to "信じられない顔",
      "k1_expr_surprise_double_take" to "二度見する",
      "k1_expr_surprise_epiphany" to "ひらめいた顔",
      "k1_expr_surprise_realize" to "はっと気づく顔",
      "k1_expr_surprise_delighted_gasp" to "うれしい驚き",
      "k1_expr_surprise_hands_cheeks" to "両手を頬に当てて驚く",
      "k1_expr_surprise_triangle_mouth" to "口が三角になる驚き",
      "k1_expr_surprise_confused_what" to "何が起きたかわからない顔",
      "k1_expr_surprise_blank" to "頭が真っ白な顔",
    ),
  )

private fun optionPart736(): CharacterOptionPart =
  CharacterOptionPart(
    "恐れ・不安",
    true,
    listOf(
      "k1_expr_fear_nervous" to "緊張した顔",
      "k1_expr_fear_anxious" to "不安な顔",
      "k1_expr_fear_uneasy" to "落ち着かない顔",
      "k1_expr_fear_scared" to "おびえた顔",
      "k1_expr_fear_terrified" to "恐怖におののく顔",
      "k1_expr_fear_trembling" to "ぶるぶる震える",
      "k1_expr_fear_cowering" to "縮こまって怯える",
      "k1_expr_fear_hide_behind" to "物陰からのぞく不安な顔",
      "k1_expr_fear_pale" to "血の気が引いた顔",
      "k1_expr_fear_cold_sweat" to "冷や汗をかく",
      "k1_expr_fear_stiffened" to "硬直した顔",
      "k1_expr_fear_whimper" to "怯えて小さく泣く",
      "k1_expr_fear_hand_chest" to "胸に手を当てて不安がる",
      "k1_expr_fear_avoid_gaze" to "目をそらす不安顔",
      "k1_expr_fear_dread" to "嫌な予感の顔",
      "k1_expr_fear_shivering" to "寒気を感じる顔",
      "k1_expr_fear_nightmare" to "悪夢にうなされる顔",
      "k1_expr_fear_eerie" to "不気味さに引く顔",
      "k1_expr_fear_breath_hold" to "息をひそめる",
      "k1_expr_fear_tense_sweat" to "手に汗握る顔",
      "k1_expr_fear_panic" to "パニックの顔",
      "k1_expr_fear_flustered" to "あわてふためく顔",
      "k1_expr_fear_shaking_pupils" to "瞳が揺れる",
      "k1_expr_fear_helpless" to "どうしようもなく困る顔",
    ),
  )

private fun optionPart737(): CharacterOptionPart =
  CharacterOptionPart(
    "嫌悪・困惑",
    true,
    listOf(
      "k1_expr_disgust_slight" to "うっすら嫌な顔",
      "k1_expr_disgust_grimace" to "しかめっ面",
      "k1_expr_disgust_wince" to "顔をゆがめる",
      "k1_expr_disgust_nose_wrinkle" to "鼻にしわを寄せる",
      "k1_expr_disgust_blech" to "うえっと舌を出す",
      "k1_expr_disgust_yuck" to "げんなりした顔",
      "k1_expr_disgust_cringe" to "引いてしまう顔",
      "k1_expr_disgust_look_away" to "目を背ける",
      "k1_expr_disgust_hold_nose" to "鼻をつまむ",
      "k1_expr_disgust_sour" to "すっぱい顔",
      "k1_expr_disgust_bitter" to "にがい顔",
      "k1_expr_confuse_puzzled" to "首をかしげる戸惑い",
      "k1_expr_confuse_perplexed" to "当惑した顔",
      "k1_expr_confuse_swirl_thoughts" to "混乱して頭が回る顔",
      "k1_expr_confuse_question_marks" to "?マークを浮かべる",
      "k1_expr_confuse_awkward" to "気まずい顔",
      "k1_expr_confuse_dubious" to "うさんくさそうな顔",
      "k1_expr_confuse_skeptical" to "疑いのまなざし",
      "k1_expr_confuse_thinking" to "考え込む顔",
      "k1_expr_confuse_troubled" to "悩む顔",
      "k1_expr_confuse_scratching_head" to "頭をかいて困る",
      "k1_expr_confuse_pondering" to "思案顔",
      "k1_expr_confuse_sweatdrop_smile" to "冷や汗の苦笑",
      "k1_expr_confuse_blink_blink" to "ぱちぱちと瞬きする",
      "k1_expr_confuse_gap_stare" to "ぽかんと見る",
    ),
  )

private fun optionGenre208(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・驚き怖れ（追加）",
    listOf(
    optionPart735(),
    optionPart736(),
    optionPart737(),
    ),
  )

private fun optionPart738(): CharacterOptionPart =
  CharacterOptionPart(
    "照れ・赤面",
    true,
    listOf(
      "k1_expr_blush_light" to "うっすら赤らむ頬",
      "k1_expr_blush_cheeks_pink" to "ほんのりピンクの頬",
      "k1_expr_blush_deep" to "真っ赤になる",
      "k1_expr_blush_full_face" to "顔じゅう赤い",
      "k1_expr_blush_neck" to "首まで赤い",
      "k1_expr_blush_bury_face" to "顔をうずめる",
      "k1_expr_blush_fidget" to "もじもじする",
      "k1_expr_blush_twiddle_fingers" to "指をいじる",
      "k1_expr_blush_hair_play" to "髪をいじって照れる",
      "k1_expr_blush_scratch_cheek" to "ほほをかいて照れる",
      "k1_expr_blush_nervous_laugh" to "照れ隠しに笑う",
      "k1_expr_blush_stammer" to "口ごもる",
      "k1_expr_blush_tongue_tied" to "言葉につまる",
      "k1_expr_blush_steam" to "頭から湯気が出る",
      "k1_expr_blush_flustered_waving" to "あわてて手を振る",
      "k1_expr_blush_wobbly_mouth" to "口が波線になる照れ",
      "k1_expr_blush_tsundere" to "照れ隠しで怒る",
      "k1_expr_blush_denial" to "真っ赤になって否定する",
      "k1_expr_blush_overheat" to "恥ずかしさで頭がパンクする",
      "k1_expr_blush_praised" to "ほめられて照れる",
      "k1_expr_blush_confession" to "告白する顔",
      "k1_expr_blush_waiting_answer" to "返事を待つ緊張顔",
      "k1_expr_blush_heart_pounding" to "胸がドキドキする顔",
      "k1_expr_blush_gaze_up" to "上目づかいで照れる",
      "k1_expr_blush_glance_shy" to "ちらちら見る",
      "k1_expr_blush_hold_skirt" to "裾をぎゅっと握る",
      "k1_expr_blush_clasp_hands" to "胸元で手を組む",
      "k1_expr_blush_dressing_flustered" to "着替えを見られて赤面",
      "k1_expr_blush_shame_cry" to "恥ずかしさで涙目",
      "k1_expr_blush_shame_pout" to "恥ずかしさでむくれる",
    ),
  )

private fun optionGenre209(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・照れ恥じらい（追加）",
    listOf(
    optionPart738(),
    ),
  )

private fun optionPart739(): CharacterOptionPart =
  CharacterOptionPart(
    "視線の向き",
    true,
    listOf(
      "k1_expr_eye_direct_gaze" to "まっすぐ見つめる",
      "k1_expr_eye_looking_afar" to "遠くを見つめる",
      "k1_expr_eye_looking_away_blush" to "目をそらして赤面",
      "k1_expr_eye_gaze_deep" to "吸い込まれるような瞳",
      "k1_expr_eye_glaring" to "にらみつける",
      "k1_expr_eye_peeking_one" to "片目だけ開けて見る",
      "k1_expr_eye_peek_through_hair" to "髪のすき間から見る",
      "k1_expr_eye_over_glasses" to "眼鏡越しに見る",
      "k1_expr_eye_from_below" to "下から覗き込む",
      "k1_expr_eye_closing_slowly" to "ゆっくり目を閉じる",
      "k1_expr_eye_darting" to "目が泳ぐ",
      "k1_expr_eye_squint_far" to "遠くを見ようと目を細める",
    ),
  )

private fun optionPart740(): CharacterOptionPart =
  CharacterOptionPart(
    "目の開き方・まばたき",
    true,
    listOf(
      "k1_expr_eye_closed_calm" to "静かに目を閉じる",
      "k1_expr_eye_half_lidded_sleepy" to "眠そうな半目",
      "k1_expr_eye_narrow_sharp" to "鋭く細めた目",
      "k1_expr_eye_wide_open" to "大きく開いた目",
      "k1_expr_eye_blink_mid" to "まばたきの途中",
      "k1_expr_eye_wink_left" to "左目でウインク",
      "k1_expr_eye_wink_right" to "右目でウインク",
      "k1_expr_eye_squint_bright" to "まぶしくて目を細める",
      "k1_expr_eye_akanbe" to "あかんべえ",
      "k1_expr_eye_one_closed_pain" to "片目をつぶって耐える",
      "k1_expr_eye_drowsy_nod" to "うとうとする目",
      "k1_expr_eye_dull_eyes" to "どんよりした目",
      "k1_expr_eye_bags" to "目の下のくま",
      "k1_expr_eye_long_lashes" to "長いまつげを伏せる",
      "k1_expr_eye_flutter" to "まつげをぱちぱちさせる",
    ),
  )

private fun optionPart741(): CharacterOptionPart =
  CharacterOptionPart(
    "瞳の表現",
    true,
    listOf(
      "k1_expr_pupil_dot" to "点の瞳",
      "k1_expr_pupil_empty" to "光のない瞳",
      "k1_expr_pupil_sparkle" to "ハイライトが輝く瞳",
      "k1_expr_pupil_ring" to "リング模様の瞳",
      "k1_expr_pupil_reflecting" to "何かを映した瞳",
      "k1_expr_pupil_tears_pool" to "涙が溜まった瞳",
      "k1_expr_pupil_shiny_wet" to "うるんだ瞳",
      "k1_expr_pupil_wide_light" to "大きな瞳に光",
      "k1_expr_pupil_unfocused" to "焦点の合わない目",
      "k1_expr_pupil_shadowed" to "影で暗くなった瞳",
      "k1_expr_pupil_bright_hope" to "希望に輝く瞳",
      "k1_expr_pupil_cold" to "冷たい瞳",
      "k1_expr_pupil_gentle" to "やさしい瞳",
      "k1_expr_pupil_fierce" to "鋭い瞳",
      "k1_expr_pupil_mischief" to "いたずらな瞳",
    ),
  )

private fun optionPart742(): CharacterOptionPart =
  CharacterOptionPart(
    "目の周り・涙",
    true,
    listOf(
      "k1_expr_tear_welling" to "涙が浮かぶ",
      "k1_expr_tear_dripping" to "涙のしずく",
      "k1_expr_tear_eye_corner" to "目尻に涙",
      "k1_expr_tear_cheeks_wet" to "頬を涙がつたう",
      "k1_expr_tear_blur_vision" to "涙でにじむ視界",
      "k1_expr_tear_heart" to "ハートの涙",
      "k1_expr_tear_sparkle" to "キラキラの涙",
      "k1_expr_tear_rain" to "涙の雨",
      "k1_expr_tear_pop" to "涙がぽろっと落ちる",
      "k1_expr_tear_laugh_corner" to "笑い涙",
      "k1_expr_tear_comedic_streams" to "ギャグ調の涙の川",
      "k1_expr_eyelid_swollen" to "はれたまぶた",
      "k1_expr_mascara_running" to "マスカラが流れる",
      "k1_expr_eye_flushed_corner" to "目のふちが赤い",
      "k1_expr_eye_red_nose_cry" to "目も鼻も赤い",
      "k1_expr_eye_wide_shine" to "驚きに光る目",
    ),
  )

private fun optionGenre210(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・目（追加）",
    listOf(
    optionPart739(),
    optionPart740(),
    optionPart741(),
    optionPart742(),
    ),
  )

private fun optionPart743(): CharacterOptionPart =
  CharacterOptionPart(
    "口の形",
    true,
    listOf(
      "k1_expr_mouth_open_small" to "小さく開いた口",
      "k1_expr_mouth_open_o" to "おの口（丸く開く）",
      "k1_expr_mouth_open_wide" to "大きく開いた口",
      "k1_expr_mouth_open_shouting" to "叫ぶ口",
      "k1_expr_mouth_cat" to "猫口（ω）",
      "k1_expr_mouth_straight" to "一文字の口",
      "k1_expr_mouth_downturned" to "への字口",
      "k1_expr_mouth_pucker" to "ちゅっと口を突き出す",
      "k1_expr_mouth_smirk_side" to "口の端だけ上げる",
      "k1_expr_mouth_corner_down" to "口角が下がる",
      "k1_expr_mouth_quiver" to "口がふるえる",
      "k1_expr_mouth_teeth_lip" to "歯で唇をかむ",
      "k1_expr_mouth_lip_curl" to "唇をめくる",
      "k1_expr_mouth_covered" to "口元を隠す",
      "k1_expr_mouth_shh" to "しーっとする",
      "k1_expr_mouth_chewing" to "もぐもぐ口を動かす",
      "k1_expr_mouth_cheek_stuffed" to "頬をふくらませてほおばる",
      "k1_expr_mouth_cheek_one_side" to "片頬だけふくらませる",
      "k1_expr_mouth_yawn_stretch" to "のびをしながらあくび",
      "k1_expr_mouth_cough" to "せき込む",
      "k1_expr_mouth_whistle" to "口笛を吹く",
      "k1_expr_mouth_blow_breath" to "ふーっと息を吹く",
      "k1_expr_mouth_breath_fog" to "白い息",
      "k1_expr_mouth_gulp" to "ごくりと息をのむ",
      "k1_expr_mouth_tongue_tip" to "舌先をのぞかせる",
      "k1_expr_mouth_tongue_out_playful" to "べーっと舌を出す",
      "k1_expr_mouth_drool_slight" to "よだれが出そうな口",
      "k1_expr_mouth_drool_trail" to "口の端からよだれ",
      "k1_expr_mouth_full" to "口いっぱいにほおばる",
      "k1_expr_mouth_candy" to "飴を舐める口",
      "k1_expr_mouth_straw" to "ストローをくわえる",
      "k1_expr_mouth_sip" to "ひと口すすって味わう",
      "k1_expr_mouth_food_smile" to "食べ物を前に笑顔",
    ),
  )

private fun optionPart744(): CharacterOptionPart =
  CharacterOptionPart(
    "歯・唇・舌",
    true,
    listOf(
      "k1_expr_teeth_fang" to "牙をのぞかせる",
      "k1_expr_teeth_fang_one" to "八重歯が片方のぞく",
      "k1_expr_teeth_sharp" to "ギザギザの歯",
      "k1_expr_teeth_upper_row" to "上の歯だけ見える",
      "k1_expr_teeth_gap" to "前歯のすき間",
      "k1_expr_teeth_bite_lower" to "下唇をかむ",
      "k1_expr_lip_pink" to "桜色の唇",
      "k1_expr_lip_chapped" to "かわいた唇",
      "k1_expr_lip_pressed" to "ぎゅっと結んだ唇",
      "k1_expr_lip_smile_corner" to "唇の端が上がる",
      "k1_expr_lip_cream" to "口元にクリーム",
      "k1_expr_tongue_out_open" to "口を開けて舌を出す",
      "k1_expr_tongue_head_tilt" to "舌を出して首をかしげる",
      "k1_expr_tongue_hang" to "舌をだらんと垂らす",
      "k1_expr_tongue_pant" to "はあはあと舌を出す",
      "k1_expr_tongue_lick_candy" to "飴をなめる舌",
    ),
  )

private fun optionGenre211(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・口（追加）",
    listOf(
    optionPart743(),
    optionPart744(),
    ),
  )

private fun optionPart745(): CharacterOptionPart =
  CharacterOptionPart(
    "眉の動き",
    true,
    listOf(
      "k1_expr_brow_furrowed" to "眉間にしわ",
      "k1_expr_brow_knit" to "眉をひそめる",
      "k1_expr_brow_angled_down" to "眉が八の字に下がる",
      "k1_expr_brow_thick_serious" to "太い眉で真剣",
      "k1_expr_brow_thin_arched" to "細くアーチした眉",
      "k1_expr_brow_short" to "短い眉（まろ眉）",
      "k1_expr_brow_waggle" to "眉をひょこひょこ動かす",
      "k1_expr_brow_sad_slope" to "悲しげに下がる眉",
      "k1_expr_brow_pleading" to "すがるような眉",
      "k1_expr_brow_worry_smile" to "困り眉で笑う",
      "k1_expr_brow_proud_arch" to "得意げな眉",
      "k1_expr_brow_soft" to "やわらかい眉",
      "k1_expr_brow_tilt_one" to "片方だけ下がった眉",
      "k1_expr_brow_expressive" to "感情豊かな眉",
      "k1_expr_brow_bold_makeup" to "はっきり描いた眉",
      "k1_expr_brow_light" to "薄い色の眉",
      "k1_expr_brow_dark" to "濃い眉",
      "k1_expr_brow_shadow" to "眉間に影",
    ),
  )

private fun optionGenre212(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・眉（追加）",
    listOf(
    optionPart745(),
    ),
  )

private fun optionPart746(): CharacterOptionPart =
  CharacterOptionPart(
    "括りつけ",
    false,
    listOf(
      "bb_tied_chair" to "椅子に括りつけ",
      "bb_tied_chair_legs" to "椅子の脚に足首を括る",
      "bb_tied_bed" to "ベッドの四隅に括りつけ",
      "bb_tied_pillar" to "柱に括りつけ",
      "bb_tied_tree" to "木に括りつけ",
      "bb_tied_table" to "テーブルに括りつけ",
      "bb_tied_stairs" to "階段の手すりに括りつけ",
      "bb_tied_bars" to "鉄格子に括りつけ",
    ),
  )

private fun optionPart747(): CharacterOptionPart =
  CharacterOptionPart(
    "貼り付け",
    false,
    listOf(
      "bb_wall_spread" to "壁に大の字で貼り付け",
      "bb_wall_arms_up" to "壁に両手を上げて貼り付け",
      "bb_wall_chains" to "壁の鎖で貼り付け",
      "bb_pole_tied" to "ポールに貼り付け",
      "bb_pole_back" to "ポールに背中合わせで縛る",
      "bb_pole_hug" to "ポールを抱く形で縛る",
      "bb_glass_press" to "ガラスに押し付けて拘束",
      "bb_cross_x" to "X字の十字架に貼り付け",
      "bb_board_wheel" to "回転台に貼り付け",
      "bb_door_hanging" to "ドアに手首を固定",
    ),
  )

private fun optionPart748(): CharacterOptionPart =
  CharacterOptionPart(
    "吊る",
    false,
    listOf(
      "bb_hang_whole" to "全身を吊る",
      "bb_hang_feet" to "足を吊る（逆さ吊り）",
      "bb_hang_hands" to "両手を吊る",
      "bb_hang_one_leg" to "片足を吊る",
      "bb_hang_both_legs_spread" to "両足を開いて吊る",
      "bb_hang_waist" to "腰を吊る",
      "bb_hang_chest" to "胸を縄で吊る",
      "bb_hang_torso" to "胴体を吊る",
      "bb_hang_horizontal" to "水平に吊る（うつ伏せ）",
      "bb_hang_face_up" to "仰向けに吊る",
      "bb_hang_ceiling_hook" to "天井のフックで吊る",
      "bb_hang_swing" to "ブランコ型の吊り",
      "bb_hang_wrists_toes" to "つま先立ちで手首だけ吊る",
      "bb_hang_arms_back" to "後ろ手で吊る",
      "bb_hang_thighs" to "太ももを吊る",
    ),
  )

private fun optionPart749(): CharacterOptionPart =
  CharacterOptionPart(
    "埋める",
    false,
    listOf(
      "bb_buried_neck" to "土に首まで埋める",
      "bb_buried_shoulders" to "土に肩まで埋める",
      "bb_buried_waist" to "土に腰まで埋める",
      "bb_buried_lower" to "下半身を土に埋める",
      "bb_buried_sand" to "砂浜に首まで埋める",
      "bb_buried_sand_body" to "砂に体を埋める（顔と胸が出る）",
      "bb_buried_legs_up" to "逆さに埋める（足だけ出る）",
      "bb_buried_arms_out" to "腕だけ出して埋める",
      "bb_buried_snow" to "雪に埋める",
      "bb_buried_flowers" to "花畑に埋もれる",
      "bb_buried_slime_pit" to "スライムの中に埋まる",
      "bb_buried_cushions" to "クッションに埋もれる",
    ),
  )

private fun optionPart750(): CharacterOptionPart =
  CharacterOptionPart(
    "縛りの種類",
    false,
    listOf(
      "bb_shibari_kikko" to "亀甲縛り（胴）",
      "bb_shibari_kikko_full" to "亀甲縛り（全身）",
      "bb_shibari_takate" to "高手小手縛り",
      "bb_shibari_ebi" to "海老責め",
      "bb_shibari_m_leg" to "M字開脚縛り",
      "bb_shibari_hogtie" to "逆海老（ホグタイ）",
      "bb_shibari_frogtie" to "カエル縛り",
      "bb_shibari_futomomo" to "太もも縛り",
      "bb_shibari_breast" to "胸の上下を縛る",
      "bb_shibari_crotch_rope" to "股縄",
      "bb_shibari_knotted" to "結び目のある縄",
      "bb_shibari_mummy" to "ミイラ巻き",
      "bb_shibari_gote" to "後ろ手に縛る（手首）",
      "bb_shibari_pretzel" to "足を組んで縛る",
      "bb_shibari_prayer" to "合掌縛り",
      "bb_shibari_harness" to "縄のハーネス",
      "bb_shibari_neck_chest" to "首から胸へ流れる縄",
      "bb_shibari_japanese" to "和室で縛られる",
      "bb_shibari_scarf_silk" to "絹の布で縛る",
      "bb_shibari_tape" to "テープで巻く",
    ),
  )

private fun optionGenre213(): CharacterOptionGenre =
  CharacterOptionGenre(
    "拘束・吊り・埋め（成人向け・追加）",
    listOf(
    optionPart746(),
    optionPart747(),
    optionPart748(),
    optionPart749(),
    optionPart750(),
    ),
  )

private fun optionPart751(): CharacterOptionPart =
  CharacterOptionPart(
    "泣き笑い・複雑な顔",
    true,
    listOf(
      "k1_expr_mix_laugh_cry" to "泣き笑い",
      "k1_expr_mix_smile_tears_joy" to "笑顔のまま涙",
      "k1_expr_mix_angry_cry" to "怒りと悲しみ",
      "k1_expr_mix_embarrassed_angry" to "恥ずかしさと怒り",
      "k1_expr_mix_embarrassed_happy" to "恥ずかしさと喜び",
      "k1_expr_mix_scared_curious" to "怖いのに気になる顔",
      "k1_expr_mix_sad_relief" to "悲しいけどほっとした顔",
      "k1_expr_mix_shock_laugh" to "驚きすぎて笑う",
      "k1_expr_mix_nervous_excited" to "緊張とわくわく",
      "k1_expr_mix_pride_shame" to "誇りと恥じらい",
      "k1_expr_mix_love_jealous" to "愛情と嫉妬",
      "k1_expr_mix_hate_love" to "愛憎の入り混じる顔",
      "k1_expr_mix_wry_bittersweet" to "ほろ苦い笑み",
      "k1_expr_mix_sleepy_happy" to "眠いけど幸せな顔",
      "k1_expr_mix_tired_smile" to "疲れた笑顔",
      "k1_expr_mix_guilty_pleasure" to "後ろめたい喜び",
      "k1_expr_mix_overjoyed_scared" to "うれしすぎて怖い顔",
      "k1_expr_mix_determined_afraid" to "怖いけど決意した顔",
      "k1_expr_mix_conflicted" to "迷い揺れる顔",
      "k1_expr_mix_tsundere_soft" to "ツンの後のデレ顔",
      "k1_expr_mix_yandere_smile" to "ひとすじの狂気の笑み",
      "k1_expr_mix_blank_smile" to "目が笑っていない笑顔",
      "k1_expr_mix_hidden_anger_smile" to "笑顔の裏の怒り",
      "k1_expr_mix_awestruck" to "畏敬の念で見つめる顔",
      "k1_expr_mix_flustered_pleased" to "あたふたしてうれしい顔",
      "k1_expr_mix_laugh_pain" to "痛いけど笑ってしまう顔",
      "k1_expr_mix_frustrated_laugh" to "悔しくて笑う",
      "k1_expr_mix_tearful_proud" to "誇らしくて涙ぐむ顔",
      "k1_expr_mix_adoring_blush" to "崇拝するような照れ顔",
      "k1_expr_mix_wistful_fond" to "懐かしむ顔",
      "k1_expr_mix_reunion" to "再会に涙ぐむ笑顔",
    ),
  )

private fun optionGenre214(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・混ざった感情（追加）",
    listOf(
    optionPart751(),
    ),
  )

private fun optionPart752(): CharacterOptionPart =
  CharacterOptionPart(
    "性格が出る表情",
    true,
    listOf(
      "k1_expr_char_cool" to "クールな無表情",
      "k1_expr_char_stoic" to "ストイックな顔",
      "k1_expr_char_aloof" to "そっけない顔",
      "k1_expr_char_suave" to "余裕の表情",
      "k1_expr_char_ojousama" to "お嬢様の高笑い",
      "k1_expr_char_genki" to "元気いっぱいの顔",
      "k1_expr_char_airhead" to "ぽやっとした顔",
      "k1_expr_char_clumsy" to "どじっ子の困り顔",
      "k1_expr_char_serious" to "生真面目な顔",
      "k1_expr_char_strict" to "厳格な顔",
      "k1_expr_char_elegant" to "優雅な微笑み",
      "k1_expr_char_noble" to "気品ある表情",
      "k1_expr_char_arrogant" to "高慢な顔",
      "k1_expr_char_haughty_chin" to "あごを上げる高慢顔",
      "k1_expr_char_sleepy_type" to "いつも眠そうな顔",
      "k1_expr_char_lazy" to "だるそうな顔",
      "k1_expr_char_tired_deadpan" to "疲れきった無表情",
      "k1_expr_char_easygoing" to "おっとりした微笑み",
      "k1_expr_char_older_sister" to "お姉さんの余裕の笑み",
      "k1_expr_char_fox_smile" to "狐のような笑み",
      "k1_expr_char_cat_like" to "猫のような気まぐれな顔",
      "k1_expr_char_loyal" to "忠犬のような顔",
      "k1_expr_char_trickster" to "策士の笑み",
      "k1_expr_char_cunning_eyes" to "ずる賢い目",
      "k1_expr_char_evil_laugh" to "悪役の高笑い",
      "k1_expr_char_madness_laugh" to "狂ったように笑う",
      "k1_expr_char_chuunibyou" to "中二病の決め顔",
      "k1_expr_char_dramatic" to "芝居がかった顔",
      "k1_expr_char_reserved" to "控えめな顔",
      "k1_expr_char_timid" to "おどおどした顔",
      "k1_expr_char_shy_quiet" to "内気な顔",
      "k1_expr_char_bold" to "大胆な顔",
      "k1_expr_char_resolute" to "意志の強い顔",
      "k1_expr_char_hero" to "ヒーローの決め顔",
      "k1_expr_char_idol" to "アイドルの笑顔",
      "k1_expr_char_calm_kind" to "穏やかで優しい顔",
      "k1_expr_char_sage" to "賢者の落ち着いた顔",
      "k1_expr_char_snicker" to "いたずらっ子のニヤニヤ",
      "k1_expr_char_lecherous" to "スケベ顔の変な笑い",
      "k1_expr_char_gentleman" to "紳士的な笑み",
      "k1_expr_char_deadpan_joke" to "真顔で冗談を言う",
      "k1_expr_char_poker_face" to "ポーカーフェイス",
      "k1_expr_char_eccentric" to "変わり者の天才顔",
    ),
  )

private fun optionGenre215(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・性格が出る（追加）",
    listOf(
    optionPart752(),
    ),
  )

private fun optionPart753(): CharacterOptionPart =
  CharacterOptionPart(
    "記号・効果",
    true,
    listOf(
      "k1_expr_anime_sweat_drop" to "汗マーク（大粒）",
      "k1_expr_anime_anger_mark" to "怒りマーク（十字血管）",
      "k1_expr_anime_heart_eyes" to "目がハート",
      "k1_expr_anime_heart_float" to "ハートが浮かぶ",
      "k1_expr_anime_star_eyes" to "目が星",
      "k1_expr_anime_blue_lines" to "青ざめ線",
      "k1_expr_anime_dark_cloud" to "頭上の暗雲",
      "k1_expr_anime_flower_bg" to "背景に花（うれしい）",
      "k1_expr_anime_sparkle_bg" to "きらきらの背景",
      "k1_expr_anime_sweat_flying" to "汗が飛び散る",
      "k1_expr_anime_steam_nose" to "鼻息荒く",
      "k1_expr_anime_steam_ears" to "耳から湯気",
      "k1_expr_anime_notes" to "音符が飛ぶ",
      "k1_expr_anime_exclamation" to "頭上に!マーク",
      "k1_expr_anime_ellipsis" to "頭上に...",
      "k1_expr_anime_light_bulb" to "電球マーク",
      "k1_expr_anime_zzz" to "ぐうぐう寝息のZ",
      "k1_expr_anime_bubble_nose" to "鼻ちょうちん",
      "k1_expr_anime_chibi" to "ちびキャラ化",
      "k1_expr_anime_x_eyes" to "ばってん目",
      "k1_expr_anime_white_eyes" to "白目の驚き",
      "k1_expr_anime_black_eyes_dark" to "真っ黒な目",
      "k1_expr_anime_no_eyes" to "目が描かれない",
      "k1_expr_anime_shadow_face" to "顔に影が落ちる",
      "k1_expr_anime_blush_lines" to "頬の斜線",
      "k1_expr_anime_stone_face" to "石になる",
      "k1_expr_anime_ash_white" to "真っ白な灰になる",
      "k1_expr_anime_soul_out" to "魂が抜ける",
      "k1_expr_anime_flaming_eyes" to "燃える瞳",
      "k1_expr_anime_fire_aura" to "燃えるオーラ",
      "k1_expr_anime_lightning" to "稲妻が走る",
      "k1_expr_anime_ears_twitch" to "耳がぴくぴく動く",
      "k1_expr_anime_ahoge_reaction" to "アホ毛が反応する",
      "k1_expr_anime_ears_perk" to "耳がぴんと立つ",
      "k1_expr_anime_tail_puff" to "しっぽが膨らむ",
      "k1_expr_anime_ears_flat" to "耳を伏せる",
      "k1_expr_anime_fang_cute" to "ちらりと見える牙",
      "k1_expr_anime_cheek_dot" to "頬にポツポツ",
      "k1_expr_anime_sigh_cloud" to "ため息の煙",
      "k1_expr_anime_glass_shine" to "眼鏡がきらりと光る",
      "k1_expr_anime_glasses_hide_eyes" to "眼鏡で目が見えない",
      "k1_expr_anime_hair_hide_eyes" to "前髪で目が見えない",
      "k1_expr_anime_gag_tears" to "ギャグ調の泣き顔",
      "k1_expr_anime_gag_scream" to "ギャグ調の叫び",
      "k1_expr_anime_gag_pale" to "ギャグ調の真っ青な顔",
    ),
  )

private fun optionGenre216(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・二次元の表現（追加）",
    listOf(
    optionPart753(),
    ),
  )

private fun optionPart754(): CharacterOptionPart =
  CharacterOptionPart(
    "一瞬の表情",
    true,
    listOf(
      "k1_expr_moment_catch" to "はっとする瞬間",
      "k1_expr_moment_wind_squint" to "風に目を細める",
      "k1_expr_moment_turn_around" to "振り向きざまの笑顔",
      "k1_expr_moment_sneeze_before" to "くしゃみをする直前",
      "k1_expr_moment_sneeze" to "くしゃみをする顔",
      "k1_expr_moment_hiccup" to "しゃっくりする",
      "k1_expr_moment_dozing" to "ふと眠りに落ちる",
      "k1_expr_moment_wake" to "目が覚める瞬間",
      "k1_expr_moment_wake_confused" to "寝ぼけた顔",
      "k1_expr_moment_stretch" to "のびをして気持ちよさそう",
      "k1_expr_moment_first_bite" to "ひと口目の驚き",
      "k1_expr_moment_realize_late" to "遅れて気づく顔",
      "k1_expr_moment_shield_eyes" to "まぶしくて目を覆う",
      "k1_expr_moment_trip" to "つまずいた瞬間の顔",
      "k1_expr_moment_sweets_delight" to "甘いものに感動する",
      "k1_expr_moment_spicy" to "辛くて顔をしかめる",
      "k1_expr_moment_cold_shiver" to "寒くて身をすくめる",
      "k1_expr_moment_hot_fan" to "暑くて顔をあおぐ",
      "k1_expr_moment_headache" to "頭が痛くて顔をしかめる",
      "k1_expr_moment_dizzy" to "めまいがする顔",
      "k1_expr_moment_hungry" to "お腹がすいた顔",
      "k1_expr_moment_full" to "満腹の顔",
      "k1_expr_moment_cheek_squish" to "頬が枕でつぶれた寝顔",
    ),
  )

private fun optionPart755(): CharacterOptionPart =
  CharacterOptionPart(
    "演技・パフォーマンス",
    true,
    listOf(
      "k1_expr_act_dramatic" to "劇的な決め顔",
      "k1_expr_act_sing" to "歌っている顔",
      "k1_expr_act_sing_closed" to "目を閉じて熱唱する",
      "k1_expr_act_sing_shout" to "全力で歌う",
      "k1_expr_act_model_walk" to "キメ顔のモデル歩き",
      "k1_expr_act_photo_smile" to "写真映えの笑顔",
      "k1_expr_act_selfie" to "自撮りの顔",
      "k1_expr_act_duck_face" to "アヒル口",
      "k1_expr_act_kiss_camera" to "カメラにキス",
      "k1_expr_act_wink_v" to "ウインク+ピース",
      "k1_expr_act_cool_pose" to "クールなキメ顔",
      "k1_expr_act_cute_pose" to "ぶりっこポーズの顔",
      "k1_expr_act_narcissist" to "ナルシストの顔",
      "k1_expr_act_mirror_check" to "鏡で表情を確認",
      "k1_expr_act_stage_nervous" to "ステージ前の緊張顔",
      "k1_expr_act_audience_wave" to "客席に手を振る笑顔",
      "k1_expr_act_laugh_pose" to "高笑いのポーズ",
      "k1_expr_act_victory_pose" to "勝利のポーズで笑う",
      "k1_expr_act_bow_polite" to "おじぎをしてにっこり",
      "k1_expr_act_sleepy_greeting" to "眠い目で元気に挨拶",
      "k1_expr_act_whisper_secret" to "内緒話の顔",
      "k1_expr_act_wink_secret" to "ひみつの合図のウインク",
      "k1_expr_act_thumbs_up" to "サムズアップの笑顔",
      "k1_expr_act_salute_smile" to "敬礼して笑う",
      "k1_expr_act_pointing_you" to "あなたを指さす笑顔",
      "k1_expr_act_beckon_smile" to "おいでおいでの笑顔",
      "k1_expr_act_tilt_head_smile" to "首をかしげて微笑む",
      "k1_expr_act_look_up_smile" to "見上げて微笑む",
      "k1_expr_act_shy_wave" to "はにかんで手を振る",
      "k1_expr_act_hide_laugh" to "隠れてくすくす笑う",
    ),
  )

private fun optionGenre217(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・一瞬と演技（追加）",
    listOf(
    optionPart754(),
    optionPart755(),
    ),
  )

private fun optionPart756(): CharacterOptionPart =
  CharacterOptionPart(
    "快感・うっとり",
    true,
    listOf(
      "k1_expr_ad_bliss_soft" to "うっとりした快感の顔",
      "k1_expr_ad_bliss_parted" to "唇が開いて息が漏れる",
      "k1_expr_ad_bliss_sigh" to "甘いため息",
      "k1_expr_ad_bliss_shiver" to "ぞくぞくする顔",
      "k1_expr_ad_bliss_bite_lip" to "唇をかんで声を殺す",
      "k1_expr_ad_bliss_hand_mouth" to "手で口を押さえて声をこらえる",
      "k1_expr_ad_bliss_arch_neck" to "のけぞって息をのむ",
      "k1_expr_ad_bliss_head_back" to "頭をのけぞらせる",
      "k1_expr_ad_bliss_flushed" to "全身が火照った顔",
      "k1_expr_ad_bliss_sweat" to "汗ばむ頬",
      "k1_expr_ad_bliss_hazy" to "ぼんやり夢うつつ",
      "k1_expr_ad_bliss_smile" to "満足そうに微笑む",
      "k1_expr_ad_bliss_lazy_gaze" to "けだるい視線",
      "k1_expr_ad_bliss_trembling" to "震える頬",
      "k1_expr_ad_bliss_tears_pleasure" to "快感で涙ぐむ",
      "k1_expr_ad_bliss_heart_gaze" to "ハート目で見つめる",
      "k1_expr_ad_bliss_whimper" to "かすれた声をもらす顔",
      "k1_expr_ad_bliss_pant" to "はあはあと息づかい",
      "k1_expr_ad_bliss_moan_open" to "口を開けて声をあげる",
      "k1_expr_ad_bliss_stifle" to "声を押し殺す",
      "k1_expr_ad_bliss_clutch_sheet" to "シーツを握りしめる顔",
      "k1_expr_ad_bliss_clutch_pillow" to "枕をかかえて耐える",
      "k1_expr_ad_bliss_hold_hands" to "相手の手を握る",
      "k1_expr_ad_bliss_eyes_wet" to "とろけた潤んだ瞳",
    ),
  )

private fun optionPart757(): CharacterOptionPart =
  CharacterOptionPart(
    "誘う・恥じらい",
    true,
    listOf(
      "k1_expr_ad_tease_beckon" to "おいでと誘う顔",
      "k1_expr_ad_tease_sideeye" to "流し目で誘う",
      "k1_expr_ad_tease_lick" to "唇をなめて誘う",
      "k1_expr_ad_tease_finger_lip" to "指を唇にあてて誘う",
      "k1_expr_ad_tease_whisper" to "耳元でささやく顔",
      "k1_expr_ad_tease_smirk" to "艶っぽい薄笑い",
      "k1_expr_ad_tease_half_lid" to "ねっとりとした半目",
      "k1_expr_ad_tease_upturned" to "上目づかいで誘う",
      "k1_expr_ad_tease_pout" to "拗ねて甘える",
      "k1_expr_ad_tease_coy" to "はにかんで誘う",
      "k1_expr_ad_tease_wink_blow" to "ウインクして投げキッス",
      "k1_expr_ad_tease_bite_finger" to "指をかんで見つめる",
      "k1_expr_ad_tease_chin_hand" to "あごに手を添えて見つめる",
      "k1_expr_ad_tease_closing_in" to "顔を近づけて見つめる",
      "k1_expr_ad_tease_kiss_wait" to "キスを待つ顔",
      "k1_expr_ad_tease_kiss_blush" to "キスして赤らむ顔",
      "k1_expr_ad_tease_hide_face_shy" to "恥ずかしがって顔を隠す",
      "k1_expr_ad_tease_look_away_shy" to "見られて目をそらす",
      "k1_expr_ad_tease_bashful_nod" to "はにかんでうなずく",
      "k1_expr_ad_tease_reluctant_yes" to "恥ずかしいけどいいよの顔",
      "k1_expr_ad_tease_clasp_chest" to "胸の前で手を握る",
      "k1_expr_ad_tease_love_look" to "愛おしげに見つめる",
      "k1_expr_ad_tease_smile_afterglow" to "余韻の微笑み",
      "k1_expr_ad_tease_nuzzle_smile" to "すり寄って微笑む",
      "k1_expr_ad_tease_cuddle_sleepy" to "寄り添って眠そうな顔",
      "k1_expr_ad_tease_pillow_talk" to "ピロートークの笑み",
    ),
  )

private fun optionPart758(): CharacterOptionPart =
  CharacterOptionPart(
    "絶頂・とろけ顔",
    true,
    listOf(
      "k1_expr_ad_climax_gasp" to "絶頂で息をのむ",
      "k1_expr_ad_climax_arch" to "絶頂でのけぞる",
      "k1_expr_ad_climax_shudder" to "全身が震える絶頂",
      "k1_expr_ad_climax_tears" to "絶頂で涙があふれる",
      "k1_expr_ad_climax_tongue" to "舌が出る絶頂",
      "k1_expr_ad_climax_voice" to "声が裏返る",
      "k1_expr_ad_climax_toes" to "つま先が丸まる顔",
      "k1_expr_ad_climax_breath_after" to "絶頂のあとの荒い息",
      "k1_expr_ad_climax_blank" to "頭が真っ白な絶頂顔",
      "k1_expr_ad_climax_smile_after" to "絶頂後の力ない笑み",
      "k1_expr_ad_climax_collapsed" to "くたっと力が抜ける",
      "k1_expr_ad_climax_twitch" to "余韻でぴくぴくする",
      "k1_expr_ad_melt_face" to "とろけた顔",
      "k1_expr_ad_melt_drool" to "よだれをこぼす幸せ顔",
      "k1_expr_ad_melt_spacey" to "うつろな幸せ顔",
      "k1_expr_ad_melt_smile_soft" to "とろけきった笑み",
      "k1_expr_ad_melt_nose_red" to "鼻先まで赤い火照り",
      "k1_expr_ad_melt_cheek_neck" to "頬から首まで火照る",
      "k1_expr_ad_melt_dazed_tears" to "ぼうっとして涙目",
      "k1_expr_ad_melt_gaze_down" to "うっとりと見下ろす",
    ),
  )

private fun optionPart759(): CharacterOptionPart =
  CharacterOptionPart(
    "アヘ顔の段階",
    true,
    listOf(
      "k1_expr_ahegao_mild" to "ゆるいアヘ顔",
      "k1_expr_ahegao_soft_tongue" to "舌を出す弱いアヘ顔",
      "k1_expr_ahegao_half" to "半目のアヘ顔",
      "k1_expr_ahegao_rolling" to "白目寄りのアヘ顔",
      "k1_expr_ahegao_cross" to "寄り目のアヘ顔",
      "k1_expr_ahegao_heart" to "ハート目のアヘ顔",
      "k1_expr_ahegao_double_peace" to "ダブルピース+アヘ顔",
      "k1_expr_ahegao_tears_drool" to "涙とよだれのアヘ顔",
      "k1_expr_ahegao_flushed_full" to "赤面フルのアヘ顔",
      "k1_expr_ahegao_gentle_smile" to "穏やかに崩れた笑顔",
      "k1_expr_ahegao_manga" to "漫画的な崩れ顔",
      "k1_expr_ahegao_spiral" to "渦巻き目のアヘ顔",
      "k1_expr_ahegao_open_mouth" to "大きく口を開けたアヘ顔",
      "k1_expr_ahegao_cheeks_hands" to "両頬に手を当ててアヘ顔",
      "k1_expr_ahegao_bite_lip" to "唇をかんだアヘ顔",
    ),
  )

private fun optionPart760(): CharacterOptionPart =
  CharacterOptionPart(
    "余韻・休息",
    true,
    listOf(
      "k1_expr_ad_rest_sleep_smile" to "寝顔の余韻",
      "k1_expr_ad_rest_cuddle_gaze" to "寄り添って見つめ合う",
      "k1_expr_ad_rest_hair_stick" to "汗で濡れた髪の余韻",
      "k1_expr_ad_rest_breath_calm" to "息が整う顔",
      "k1_expr_ad_rest_blank_stare" to "ぼんやり天井を見る",
      "k1_expr_ad_rest_teary_smile" to "涙の残る微笑み",
      "k1_expr_ad_rest_forehead_kiss" to "おでこにキスされて照れる",
      "k1_expr_ad_rest_shy_cover" to "シーツで顔を隠す",
      "k1_expr_ad_rest_peek_sheet" to "シーツからのぞく顔",
      "k1_expr_ad_rest_lazy_stretch" to "ゆっくり伸びをする",
    ),
  )

private fun optionGenre218(): CharacterOptionGenre =
  CharacterOptionGenre(
    "表情・成人向け（追加）",
    listOf(
    optionPart756(),
    optionPart757(),
    optionPart758(),
    optionPart759(),
    optionPart760(),
    ),
  )

private fun optionGenre219(): CharacterOptionGenre =
  CharacterOptionGenre(
    "コスプレ・プレイ・動き・表情（追加6）",
    listOf(
      CharacterOptionPart(
        "職業コスのデザイン違い",
        false,
        listOf(
          "n6_nurse_white_mini" to "白いミニのナース服",
          "n6_nurse_pink_zip" to "ピンクのファスナー式ナース服",
          "n6_nurse_latex" to "ラテックスのナース服",
          "n6_police_mini" to "ミニスカ婦警",
          "n6_police_handcuffs" to "手錠をさげた婦警",
          "n6_prisoner_stripe" to "縞模様の囚人服",
          "n6_bunny_classic" to "定番のバニーガール",
          "n6_bunny_latex" to "ラテックスのバニー",
          "n6_maid_short" to "ミニ丈のメイド服",
          "n6_maid_latex" to "ラバーのメイド服",
          "n6_teacher_tight" to "タイトな女教師",
          "n6_cabin_attendant" to "客室乗務員の制服",
          "n6_race_queen" to "レースクイーン",
          "n6_cheer_mini" to "ミニのチアガール",
        ),
      ),
      CharacterOptionPart(
        "ラバー・革の小物",
        false,
        listOf(
          "n6_pin_heel_boots" to "ピンヒールのロングブーツ",
          "n6_ballet_boots" to "バレエブーツ（爪先立ち）",
          "n6_long_gloves_latex" to "ラテックスのロンググローブ",
          "n6_leather_gloves" to "革の手袋",
          "n6_latex_stockings" to "ラバーのストッキング",
          "n6_latex_hood" to "ラバーのフードマスク",
          "n6_eye_mask" to "アイマスク（目隠し）",
          "n6_choker_ring" to "リング付きの首輪",
          "n6_garter_belt_wide" to "幅広のガーターベルト",
          "n6_waist_cincher" to "ウエストを絞るコルセット",
        ),
      ),
      CharacterOptionPart(
        "プレイの場面セット",
        true,
        listOf(
          "n6_scene_exam" to "診察室の場面",
          "n6_scene_interrogation" to "取り調べ室の場面",
          "n6_scene_master_servant" to "主従の場面",
          "n6_scene_service" to "ご奉仕の場面",
          "n6_scene_tutor" to "家庭教師の場面",
          "n6_scene_boss" to "女上司と部下の場面",
          "n6_scene_prison" to "牢獄の場面",
          "n6_scene_dungeon" to "調教部屋の場面",
          "n6_scene_hotel" to "ホテルの一室の場面",
          "n6_scene_bath" to "お風呂の場面",
          "n6_scene_massage" to "マッサージの場面",
          "n6_scene_gym" to "ジムの個人レッスン",
        ),
      ),
      CharacterOptionPart(
        "動きの途中のポーズ",
        true,
        listOf(
          "n6_mo_walk" to "歩いてくる",
          "n6_mo_runway" to "ランウェイウォーク",
          "n6_mo_look_back" to "振り返る",
          "n6_mo_crouch" to "しゃがむ",
          "n6_mo_all_fours" to "四つん這い",
          "n6_mo_kneel_sit" to "ぺたん座り",
          "n6_mo_bend_over" to "前かがみになる",
          "n6_mo_stretch" to "伸びをする",
          "n6_mo_hair_flip" to "髪をかき上げる",
          "n6_mo_dress_lift" to "スカートをつまんで持ち上げる",
          "n6_mo_lie_back" to "あお向けに寝そべる",
          "n6_mo_twist" to "体をひねる",
        ),
      ),
      CharacterOptionPart(
        "表情の変化（段階）",
        true,
        listOf(
          "n6_ex_shy_to_melt" to "照れから、とろけ顔へ",
          "n6_ex_smug_to_flushed" to "強気な顔が、赤くなる",
          "n6_ex_resist_to_yield" to "嫌がる顔から、受け入れる顔へ",
          "n6_ex_calm_to_pant" to "落ち着いた顔から、息が乱れる",
          "n6_ex_smile_to_tears" to "笑顔から、涙ぐむ",
          "n6_ex_cool_to_blush" to "クールな顔が、崩れる",
          "n6_ex_serious_to_giggle" to "真顔から、くすっと笑う",
          "n6_ex_proud_to_ahegao" to "誇らしげな顔から、とろ顔へ",
          "n6_ex_sleepy_to_wake" to "眠そうな顔から、目覚める",
          "n6_ex_angry_to_pout" to "怒りから、ふくれ顔へ",
        ),
      ),
    ),
  )

/** 小分類のプルダウンで選んだ結果。previous を外し、next（null なら外すだけ）を選ぶ。1つだけの小分類では、同じ小分類の他の項目も外す。 */
internal fun chooseCharacterOption(
  selected: Set<String>,
  part: CharacterOptionPart,
  previous: String?,
  next: String?,
): Set<String> {
  val kept = if (previous == null) selected else selected - previous
  if (next == null) return kept
  val cleared = if (part.single) kept - part.items.map { it.first }.toSet() else kept
  return cleared + next
}

/** 選んだ項目ごとの条件を、「項目名：内容」を「 / 」でつないだ1行にまとめる。選んでいない項目と空の入力は含めない。 */
internal fun lookNotesText(selected: Set<String>, notes: Map<String, String>): String =
  (CHARACTER_OPTION_CATALOG.flatMap { it.parts }.flatMap { it.items } + LEGACY_OPTION_ITEMS)
    .filter { it.first in selected }
    .map { it.second to notes[it.first].orEmpty().trim().replace('\n', ' ') }
    .filter { it.second.isNotEmpty() }
    .joinToString(" / ") { "${it.first}：${it.second}" }

/** 一覧には出さない、他の項目と同じ意味の項目。保存済みのキャラが使っている id が、これまでどおり効くように残す。 */
internal val LEGACY_OPTION_ITEMS: List<Pair<String, String>> =
  legacyOptions0() +
    legacyOptions1() +
    legacyOptions2()

private fun legacyOptions0(): List<Pair<String, String>> =
  listOf(
    "g1_acc_head_septum" to "セプタム",
    "sh_combat" to "ごついブーツ",
    "ht_beanie" to "ニット帽",
    "ht_cowboy" to "カウボーイハット",
    "ov_chinese" to "チャイナドレス",
    "ov_yukata" to "浴衣",
    "sw_bikini" to "ビキニ",
    "mk_liner_wing" to "跳ね上げアイライン",
    "mk_lash_false" to "つけまつ毛",
    "mk_freckles" to "そばかす",
    "mk_mole_mouth" to "口元のほくろ",
    "bd_blindfold" to "目隠し",
    "bd_collar_leash" to "首輪とリード",
    "hs_ahoge" to "アホ毛",
    "hs_drill" to "縦ロール",
    "hs_side_pony" to "サイドポニーテール",
    "hs_half_up" to "ハーフアップ",
    "hs_updo" to "アップスタイル",
    "hs_see_through" to "シースルーバング",
    "hs_side_swept" to "斜めの前髪",
    "hs_one_eye" to "片目が隠れる前髪",
    "hs_widows_peak" to "おでこを出す",
    "co_flight_attendant" to "客室乗務員",
    "co_ol" to "OLスーツ",
    "co_waitress" to "ウェイトレス",
    "co_military" to "軍服",
    "c2_kunoichi" to "くノ一",
    "pr_fan" to "扇子",
    "pr_sword" to "剣",
    "pr_wand" to "魔法のステッキ",
    "pr_goggles" to "ゴーグル",
    "pr_halo" to "天使の輪",
    "h2_kneeling" to "膝をついて座る",
    "h2_finger_in_mouth" to "指を口にくわえる",
    "h2_looking_back" to "後ろを振り向く",
    "h2_running" to "小走り",
    "h2_arms_up_2" to "両腕を上げて伸びをする",
    "fx_jito" to "ジト目",
    "g2_fx_smirk_one" to "片方の口角だけ上げる",
    "g2_fx_kiss" to "投げキス（顔）",
    "g2_fx_serious" to "まじめな顔",
    "g2_fx_up_eye" to "上目づかい",
    "rc_mermaid" to "人魚",
    "rc_slime" to "スライム娘",
    "rc_lamia" to "ラミア（蛇の下半身）",
    "rc_harpy" to "ハーピー",
    "g1_lin_pan_black" to "黒のショーツ",
    "g1_lin_state_panties_aside" to "ずらした下着",
    "g1_eq_wand" to "魔法の杖",
    "g1_eq_wings_mech" to "メカ翼",
    "g1_hr_bang_swept" to "流し前髪",
    "g1_hr_style_bun_messy" to "ゆるお団子",
    "g1_hr_style_twintail_low" to "ローツイン",
    "g1_hr_style_braid_crown" to "王冠編み",
    "g1_hr_style_braid_side" to "サイドブレイド",
    "g1_hr_style_braid_french" to "フレンチブレイド",
    "g1_hr_style_shaved_side" to "片側刈り上げ",
    "g2_eye_wing" to "ウイングライナー",
    "h2_winged_eyeliner" to "はね上げアイライン",
    "h2_thick_eyeliner" to "太めのアイライン",
    "h2_white_eyeliner" to "白いアイライン",
    "h2_brown_eyeshadow" to "ブラウンアイシャドウ",
    "h2_purple_eyeshadow" to "紫のアイシャドウ",
    "h2_gold_eyeshadow" to "金のアイシャドウ",
    "h2_glitter_eyeshadow" to "ラメ入りアイシャドウ",
    "h2_purple_lipstick" to "紫のリップ",
    "h2_blue_lipstick" to "青いリップ",
    "h2_matte_lipstick" to "マットなリップ",
    "h2_applying_lipstick" to "口紅をつけ直す",
    "g2_bd_wide_shoulder" to "肩幅が広い",
    "g2_sk_pale" to "青白い肌",
    "g2_sk_tan" to "日焼け肌（濃いめ）",
    "g2_sk_dark" to "濃い肌",
    "g2_sk_goosebump" to "さぶいぼ",
    "g2_sk_beauty_lip" to "口もとのほくろ（下）",
    "g2_sk_beauty_eye" to "目もとのほくろ",
    "g2_sk_beauty_chest" to "胸もとのほくろ",
    "g2_tt_sleeve" to "スリーブタトゥー",
    "g2_tt_tribal" to "トライバル柄",
    "g2_pc_eyebrow" to "眉のピアス（バー）",
    "g2_pc_nose_stud" to "小鼻のピアス",
    "g2_pc_lip" to "唇のピアス",
    "g2_pc_tongue" to "舌のピアス（バー）",
    "g2_hr_armpit" to "ワキ毛（はえている）",
    "g2_hr_leg" to "脚の毛",
    "g2_fe_longneck" to "首すじが長い",
    "g2_ill_yawn" to "あくび",
    "g2_pa_tremble" to "ぷるぷる震える",
    "g3_fet_toe_ring" to "足の指輪",
    "g3_fet_hair_over_eye" to "片目隠れの髪",
    "g3_fet_seam_stocking" to "縫い目つきストッキング",
    "g3_fet_sling_bikini" to "スリングショット水着",
    "g3_fet_gaze_back" to "振り向く視線",
    "g3_fet2_vampire" to "吸血鬼",
    "g3_fet2_angel" to "天使",
    "g3_fet2_horns_ram" to "巻き角",
    "g3_toy_nipple_cover" to "ニップルカバー",
    "h4_wear_tail_cat" to "ねこしっぽの器具",
    "h4_wear_tail_dog" to "いぬしっぽの器具",
    "h4_wear_jewel_plug" to "宝石つきの器具",
    "h4_wear_pasties_star" to "星型のシール",
    "g3_sm5_leather_gloves" to "革の手袋",
    "g3_sm5_stud_belt" to "鋲のベルト",
    "g3_sm5_chain_belt" to "鎖のベルト",
    "h1_head_glasses_pushed" to "眼鏡を押し上げる",
    "h1_eth_bindi" to "ビンディ",
    "h1_dis_clothes_hold" to "服を持つ",
    "h2_buzz_cut" to "刈り上げ",
    "h2_layered_hair" to "レイヤードカット",
    "h2_long_layered_hair" to "段のあるロング",
    "h2_high_ponytail" to "ハイポニーテール",
    "h2_low_ponytail" to "ローポニーテール",
    "h2_side_ponytail" to "サイドポニー",
    "h2_low_twintails" to "ローツインテール",
    "h2_half_updo" to "ハーフアップ（半分結び）",
    "h2_kanzashi" to "髪にさした簪",
    "h2_twin_braids" to "三つ編み2本",
    "h2_box_braids" to "ボックスブレイズ",
    "h2_braided_bangs" to "前髪の編み込み",
    "h2_crown_braid" to "クラウンブレイド（王冠編み）",
    "h2_fishtail_braid" to "フィッシュボーン（魚の骨編み）",
    "h2_french_braid" to "後ろでまとめる編み込み",
    "h2_curly_hair" to "カールした髪",
    "h2_messy_hair" to "ボサボサの髪",
    "h2_asymmetrical_bangs" to "左右非対称の前髪",
    "h2_blunt_bangs" to "ぱっつん前髪（一直線）",
    "h2_wispy_bangs" to "薄めの束感前髪",
    "h2_choppy_bangs" to "ざっくり切った前髪",
    "h2_diagonal_bangs" to "斜めに流した前髪",
    "h2_parted_bangs" to "7:3に分けた前髪",
    "h2_blonde_hair" to "金髪（ブロンド）",
    "h2_auburn_hair" to "赤茶の髪",
    "h2_silver_hair" to "シルバーグレー",
    "h2_lavender_hair" to "ラベンダー色の髪",
    "h2_mint_green_hair" to "ミントグリーンの髪",
    "h2_burgundy_hair" to "バーガンディの髪",
    "h2_colored_inner_hair" to "内側だけ色違い（イヤリングカラー）",
    "h2_gradient_hair" to "グラデーションヘア",
    "h2_rainbow_hair" to "虹色の髪",
    "h2_two_tone_hair" to "ダブルカラー",
    "h2_hair_clip" to "ヘアクリップで留める",
    "h2_hairband" to "ヘアバンドをつける",
    "h2_aqua_eyes" to "アクア色の瞳",
    "h2_grey_eyes" to "グレーの瞳",
    "h2_pink_eyes" to "ピンクの瞳",
    "h2_purple_eyes" to "紫の瞳",
    "h2_sparkling_eyes_2" to "きらきら輝く目",
    "h2_teary_eyes" to "潤んだ目",
    "h2_teary_eyes_2" to "涙目",
    "h2_wide_set_eyes" to "目と目の間が離れた目",
  )

private fun legacyOptions1(): List<Pair<String, String>> =
  listOf(
    "h2_close_set_eyes" to "目と目の間が近い目",
    "h2_furrowed_brow_2" to "しかめた眉",
    "h2_straight_eyebrows" to "平行眉",
    "h2_arched_eyebrows" to "アーチ眉",
    "h2_eyebrows_hidden_by_hair_2" to "前髪で隠れた眉",
    "h2_biting_lip" to "唇を噛む",
    "h2_licking_lips" to "唇を舐める",
    "h2_mole_under_mouth" to "下唇のほくろ",
    "h2_tongue_piercing" to "舌ピアス（ストレートバー）",
    "h2_mole_on_thigh" to "ふとももの内側のほくろ",
    "h2_mole_on_stomach" to "お腹のほくろ",
    "h2_freckles_on_nose_2" to "鼻筋のそばかす",
    "h2_freckles" to "顔じゅうのそばかす",
    "h2_blush" to "頬の赤み（チーク色）",
    "h2_high_cheekbones" to "ほほ骨が目立つ",
    "h2_hollow_cheeks" to "頬のくぼみ",
    "h2_stubble_2" to "無精ひげ",
    "h2_pale_skin" to "透き通る白い肌",
    "h2_dark_skin" to "濃い褐色肌",
    "h2_very_dark_skin" to "とても濃い肌",
    "h2_olive_skin" to "オリーブ色の肌",
    "h2_vitiligo" to "白斑のある肌",
    "h2_oiled_skin" to "オイルを塗った肌",
    "h2_visible_veins" to "血管が透ける肌",
    "h2_sweat_on_chest" to "胸元の汗",
    "h2_watch_tan_line" to "時計の日焼け跡",
    "h2_stretch_marks" to "ストレッチマーク",
    "h2_freckles_on_shoulders_2" to "そばかすだらけの肩",
    "h2_curvy" to "グラマラス体型",
    "h2_plump" to "ふくよか体型",
    "h2_muscular_female" to "筋肉質体型",
    "h2_biceps" to "上腕二頭筋",
    "h2_muscular_legs" to "筋肉質の脚",
    "h2_prominent_collarbone" to "鎖骨が目立つ",
    "h2_flat_chest" to "まな板（小さめ）",
    "h2_small_breasts" to "ささやかな胸",
    "h2_medium_breasts" to "適度な胸",
    "h2_large_breasts" to "豊かな胸",
    "h2_huge_breasts" to "ボリュームのある胸",
    "h2_gigantic_breasts" to "超巨乳",
    "h2_perky_breasts" to "ツンと上向きの胸",
    "h2_sagging_breasts" to "重たげに垂れる胸",
    "h2_bouncing_breasts" to "弾む胸",
    "h2_puffy_nipples" to "ぷっくり乳首",
    "h2_narrow_waist" to "くびれた腰",
    "h2_wide_hips" to "広い腰骨",
    "h2_hip_dips" to "腰骨のくぼみ",
    "h2_soft_belly" to "ほんのりお腹",
    "h2_midriff" to "おへそ出し",
    "h2_long_legs" to "とても長い脚",
    "h2_thick_thighs" to "太ももが太め",
    "h2_thigh_gap" to "太ももの隙間（サイハイギャップ）",
    "h2_broad_shoulders" to "広い肩幅",
    "h2_long_neck" to "首が長い",
    "h2_large_hands" to "大きな手（成人）",
    "h2_long_fingers" to "指の長い手",
    "h2_short_nails" to "短く整えた爪",
    "h2_round_nails" to "丸みのある爪（ラウンド）",
    "h2_square_nails" to "四角い爪（スクエア）",
    "h2_almond_nails" to "アーモンド型の爪",
    "h2_coffin_nails" to "コフィン（棺型）",
    "h2_glitter_nails" to "ラメ入りネイル",
    "h2_marble_nails" to "マーブルネイル",
    "h2_heart_nail_art" to "ハートのネイルアート",
    "h2_leopard_print_nails" to "ひょう柄ネイル",
    "h2_large_feet" to "大きな足（成人）",
    "h2_tribal_tattoo" to "トライバルタトゥー",
    "h2_geometric_tattoo" to "幾何学模様のタトゥー",
    "h2_blackwork_tattoo" to "ブラックワークのタトゥー",
    "h2_watercolor_tattoo" to "水彩風のタトゥー",
    "h2_neck_tattoo" to "首筋のタトゥー",
    "h2_hip_tattoo" to "腰骨のタトゥー",
    "h2_sleeve_tattoo" to "片腕全部のスリーブタトゥー",
    "h2_multiple_ear_piercings" to "ロブの連続ピアス",
    "h2_single_earring" to "片耳だけのピアス",
    "h2_labret_piercing" to "ラブレットピアス（下唇）",
    "h2_nose_stud" to "ノーズスタッド（小鼻）",
    "h2_nose_ring" to "ノーズリング",
    "h2_bridge_piercing" to "ブリッジピアス（鼻筋）",
    "h2_waist_chain" to "腰回りの飾りチェーン",
    "h3_beast_wolf" to "狼獣人",
    "h3_rep_lizard" to "トカゲ獣人",
    "h3_horn_antler" to "枝角",
    "h3_wing_black" to "黒い翼",
    "h3_tail_lizard" to "トカゲの尻尾",
    "h3_body_star_hair" to "星の髪",
    "h3_body_crystal_hair" to "水晶の髪",
    "h3_body_webbed_hands" to "水かきのある手",
    "h3_body_fangs" to "鋭い牙",
    "h3_body_slit_pupils" to "縦長の瞳孔",
    "h3_body_glow_eyes" to "光る目",
    "h3_body_mismatched_eyes" to "左右で色の違う目",
    "h3_fx_mist" to "漂う霧",
    "h4_mess_strap_one" to "片方だけ外れた肩ひも",
    "h4_mess_skirt_lift_hand" to "裾をたくし上げる",
    "h4_half_undress_coat_open" to "コートの前を開く",
    "h4_half_undress_apron_only" to "エプロン一枚",
    "h4_dis_lipstick_mark" to "口紅のキスマーク",
    "h4_dis_clothes_on_floor" to "床に散らばった服",
    "h4_ex_heavy_breath" to "荒い息づかい",
    "h4_ex_tongue_long" to "長く出した舌",
    "h4_ex_drool_thin" to "口の端のよだれ",
    "h4_st_stretching_pose" to "ぐっと伸びをする",
    "h4_rg_ball_gag_pink" to "ピンクのボールギャグ",
    "h4_rg_ball_gag_clear" to "透明のボールギャグ",
    "h4_rg_tape_gag" to "テープで口を塞ぐ",
    "h4_rg_muzzle" to "マズル（口輪）",
    "h4_lr_raincoat_clear" to "透明なレインコート",
    "h4_mt_belly_dancer" to "ダンサー風の飾り",
    "h4_fm_armpit_sweat" to "汗ばむ脇",
    "h4_fm_back_dimples" to "背中のえくぼ",
    "h4_fm_back_looking_back" to "肩越しに振り向く",
    "h4_fm_hair_brush" to "髪をブラシでとかす",
    "h4_fv_size_princess" to "お姫さま抱っこ",
    "h4_fc_glasses_cat_eye" to "ツリ目型の眼鏡",
    "h4_fc_gloves_lace" to "レースの手袋",
    "h4_fc_mask_fox" to "狐の面",
    "h4_fc_whitecoat_stethoscope" to "聴診器と白衣",
    "h4_sg_fishnet_gloves" to "網目の手袋",
    "h4_sg_bodysuit_lace" to "レースのボディスーツ",
    "h4_sg_bodysuit_halter" to "ホルターのボディスーツ",
    "h4_sg_bodysuit_long_sleeve" to "長袖のボディスーツ",
    "h4_sg_bodysuit_turtleneck" to "タートルネックのボディスーツ",
    "h4_sg_leotard_high_cut" to "ハイレグのレオタード",
    "h4_sh_hand_over_mouth" to "手で口を押さえる",
    "h4_ex_show_thigh" to "太ももを見せる",
    "h4_ex_show_plunging_neckline" to "深く開いた胸元",
    "h4_ex_show_micro_bikini" to "極小のビキニ",
    "h4_ex_show_highleg_swim" to "ハイレグの水着",
    "h4_tt_tattoo_tribal" to "トライバル柄の刺青",
    "h4_tt_tattoo_rose" to "薔薇の刺青",
    "h4_tt_tattoo_cherry" to "桜の刺青",
    "h4_tt_tattoo_snake" to "蛇の刺青",
    "h4_tt_tattoo_butterfly" to "蝶の刺青",
    "h4_tt_tattoo_skull" to "髑髏の刺青",
    "h4_tt_tattoo_mandala" to "曼荼羅の刺青",
    "h4_tt_tattoo_number" to "数字の刺青",
    "h4_tt_tattoo_heart" to "ハートの刺青",
    "h4_tt_tattoo_star" to "星の刺青",
    "h4_tt_tattoo_sun" to "太陽の刺青",
    "h4_tt_paint_white" to "白いボディペイント",
    "h4_tt_paint_pink" to "ピンクのボディペイント",
    "h4_tt_paint_red" to "赤いボディペイント",
    "h4_tt_paint_blue" to "青いボディペイント",
    "h4_tt_paint_black" to "黒いボディペイント",
    "h4_tt_paint_rainbow" to "虹色のボディペイント",
    "h4_tt_paint_glitter" to "ラメのボディペイント",
    "h4_tt_pierce_tragus" to "トラガスのピアス",
    "h4_tt_pierce_ear_chain" to "耳をつなぐ鎖",
    "h4_tt_pierce_nose" to "鼻のピアス",
  )

private fun legacyOptions2(): List<Pair<String, String>> =
  listOf(
    "h4_tt_pierce_septum" to "セプタム（鼻中隔）ピアス",
    "h4_tt_pierce_eyebrow" to "眉のピアス",
    "h4_tt_pierce_tongue" to "舌のピアス",
    "h4_tt_pierce_bridge" to "ブリッジのピアス",
    "h4_tt_pierce_surface" to "表面ピアス",
    "h4_tt_body_glitter" to "肌のラメ",
    "h4_gn_fantasy_angel_fallen" to "堕ちた天使",
    "h4_gn_scifi_doll" to "人形風の関節",
    "h6_br_round" to "丸い形",
    "h6_br_teardrop" to "しずく形",
    "h6_br_underboob" to "下乳を見せる",
    "h6_br_sideboob" to "横乳を見せる",
    "h6_nip_dark" to "黒ずんだ色",
    "h6_nip_small" to "小さい乳首",
    "h6_nip_puffy" to "ぷっくりした乳首",
    "h6_nip_erect" to "立っている乳首",
    "h6_are_small" to "小さい乳輪",
    "h6_are_large" to "大きい乳輪",
    "h6_nip_barbell" to "バーベル型の乳首ピアス",
    "h6_nip_ring" to "リング型の乳首ピアス",
    "h6_nip_heart_pasties" to "ハート形のニップレス",
    "h6_nip_tassel" to "タッセルつきニップレス",
    "h6_ph_trimmed" to "整えてある",
    "h6_phs_strip" to "細い縦ライン（ランディングストリップ）",
    "h6_phx_armpit" to "わきの毛あり",
    "h6_phx_leg_hair" to "すね毛あり",
    "h6_bd_flat_butt" to "平たいおしり",
    "h6_bd_thigh_gap" to "太ももの隙間",
    "h6_bd_navel_deep" to "深いおへそ",
    "h6_bd_stretch_marks" to "肉割れ（妊娠線風）",
    "s1_vtem_1_5" to "ウマ耳",
    "s1_vtem_1_17" to "ヴェール",
    "s1_vtem_2_35" to "手のポーズ(口元に当てる手)",
    "s1_booth_6_24" to "マイクロビキニ風の水着",
    "s1_booth_7_0" to "十字架のピアス",
    "s1_booth_7_1" to "ハートのピアス",
    "s1_booth_7_2" to "鎖のネックレス",
    "s1_booth_7_3" to "鎖の付いたピアス",
    "s1_booth_7_7" to "メガネ",
    "s1_booth_7_8" to "アイパッチ",
    "s1_booth_7_12" to "足首のアンクレット",
    "s1_booth_10_35" to "膝立ちの姿勢",
    "s1_bowl_11_9" to "貞操帯(金属)",
    "s1_bowl_11_13" to "拘束機械のアーム",
    "s1_bowl_11_22" to "鎖付きの首枷",
    "i3_mascot_costume" to "マスコット風のきぐるみ衣装",
    "j1_body_012" to "肩甲骨が浮く背中",
    "j1_body_046" to "とがった爪",
    "j1_skin_002" to "ブラ跡の日焼け",
    "j1_skin_004" to "ストラップ跡の日焼け",
    "j1_skin_007" to "靴下跡の日焼け",
    "j1_skin_008" to "手袋跡の日焼け",
    "j1_mk_002" to "緑のアイシャドウ",
    "j1_mk_009" to "オレンジの口紅",
    "j1_mk_014" to "斜めに入れたチーク",
    "j1_mk_024" to "つけ爪（ロング）",
    "j1_mess_027" to "水滴がつく肌",
    "j2_eye_droopy" to "たれ目",
    "j2_eye_tsurime" to "つり目",
    "j2_hair_bangs_long" to "目にかかる長い前髪",
    "j2_job_idol_producer" to "スーツ姿のマネージャー風",
    "j2_pose_thinking" to "あごに手を当てて考える",
    "j3_mt_open_s" to "少し開いた口",
    "j3_bt_open_fly" to "ファスナーを開けたボトム",
    "j3_ul_panty_string" to "ひも結びショーツ",
    "j3_ac_hat_sun" to "つばの広い帽子",
    "j3_ex_cat_mouth" to "ω形の口",
    "j4_leg_calf_toned" to "引き締まったふくらはぎ",
    "j4_skin_matte" to "さらっとした肌",
    "j4_mk_liner_color" to "色つきアイライン",
    "j4_mk_blush_round" to "丸いチーク",
    "j4_mk_blush_cross" to "横に広がるチーク",
    "j4_pe_clingy" to "あまえんぼう",
    "j4_pe_jealous" to "やきもち焼き",
    "j4_act_vibrator" to "大人向けの玩具を使う",
    "j5_dance_mic" to "マイクを持って歌う",
    "k1_expr_smile_faint" to "かすかな微笑み",
    "k1_expr_joy_sparkle_eyes" to "きらきらした目",
    "k1_expr_blush_look_away" to "恥ずかしくて目をそらす",
    "k1_expr_ad_bliss_sweat_hair" to "汗で髪が貼りつく",
  )
