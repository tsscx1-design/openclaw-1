// The pictures that make a chat avatar move: one face per expression with the mouth closed and
// open, and one blink. The phone shows them in turn (the mouth flaps while she speaks).

export const AVATAR_EXPRESSIONS = [
  { name: "normal", label: "通常", prompt: "a calm, natural expression" },
  { name: "happy", label: "笑顔", prompt: "a bright, happy smile" },
  { name: "sad", label: "悲しい", prompt: "a sad, teary expression with lowered eyebrows" },
  { name: "angry", label: "怒り", prompt: "an angry expression with furrowed eyebrows" },
  { name: "surprised", label: "驚き", prompt: "a surprised expression with wide eyes" },
  { name: "shy", label: "照れ", prompt: "a shy, blushing expression, looking slightly away" },
  { name: "thinking", label: "考え中", prompt: "a thoughtful expression, eyes looking up" },
  // For touches on the intimate places (adult characters only): a flushed face, half-closed eyes.
  {
    name: "aroused",
    label: "うっとり（成人向け）",
    prompt: "a flushed face, half-lidded eyes, slightly parted lips, breathing softly",
  },
] as const;

export type AvatarFrame = {
  key: string;
  label: string;
  prompt: string;
  /** A picture of the clothes (not the face). */ outfit?: boolean;
  /** A picture of another pose (the body moves). */ pose?: boolean;
};

/** Every picture of the set, in the order they are drawn (and returned by the runner). */
export function avatarFrames(): AvatarFrame[] {
  const frames: AvatarFrame[] = [];
  for (const expression of AVATAR_EXPRESSIONS) {
    frames.push({
      key: `${expression.name}_closed`,
      label: `${expression.label}（口を閉じる）`,
      prompt: `${expression.prompt}, mouth closed`,
    });
    frames.push({
      key: `${expression.name}_open`,
      label: `${expression.label}（口を開く）`,
      prompt: `${expression.prompt}, mouth open as if speaking`,
    });
  }
  frames.push({
    key: "blink",
    label: "まばたき",
    prompt: "a calm, natural expression, eyes closed, mouth closed",
  });
  return frames;
}

/** The instruction for one frame: only the face changes, everything else stays as in the picture. */
export function avatarFramePrompt(frame: AvatarFrame): string {
  if (frame.pose) {
    return (
      `Keep exactly the same character, the same clothes, the same hair, the same art style and the same framing as the reference picture. ` +
      `Change only her pose and expression: ${frame.prompt}. Plain background, no text.`
    );
  }
  if (frame.outfit) {
    return (
      `Keep exactly the same character, the same pose, the same framing, the same face with a calm expression, the same hair and the same art style as the reference picture. ` +
      `Change only her clothes: ${frame.prompt}. Plain background, no text.`
    );
  }
  return (
    `Keep exactly the same character, the same pose, the same framing, the same clothes, the same hair and the same art style as the reference picture. ` +
    `Change only the face: ${frame.prompt}. Plain background, no text.`
  );
}

/**
 * The steps of taking her clothes off, one picture each (cumulative: every picture shows the state
 * after that step). The app shows them when she is stroked in the right place and direction.
 * Only adult characters have avatars here; the pictures are made on the PC and stay on it.
 */
const UNDRESS_UNDERWEAR =
  "Under her clothes she wears a plain white bra and plain white panties (the same ones in every picture)";
const UNDRESS_STEPS: Array<{ label: string; prompt: string }> = [
  {
    label: "前を開け始める",
    prompt:
      "the front of her top is unfastened: the zipper is pulled all the way down (or the buttons are opened), but the clothes are still on her body and closed around her",
  },
  {
    label: "前を左右に開く",
    prompt:
      "the front of her top is opened and the two sides are slid apart to the left and right, showing her bra underneath",
  },
  {
    label: "片方の肩を出す",
    prompt:
      "her opened top has slipped off her left shoulder, the right side is still on, her bra is visible",
  },
  {
    label: "両肩を出す",
    prompt:
      "her opened top has slipped off both shoulders and hangs around her upper arms, her bra is visible",
  },
  {
    label: "上着を脱ぐ",
    prompt:
      "her top (jacket, hoodie or shirt) is taken off completely; she wears only her bra and her skirt or pants (or the lower part of her dress)",
  },
  {
    label: "ズボン・スカートをゆるめる",
    prompt:
      "no top; the button or zipper of her skirt or pants is opened and the waistband is loosened, her panties start to show",
  },
  {
    label: "腰まで下ろす",
    prompt: "no top; her skirt or pants are pulled down to her hips, her panties are visible",
  },
  {
    label: "太ももまで下ろす（半脱ぎ）",
    prompt:
      "no top; her skirt or pants are pulled down to the middle of her thighs, her panties are visible, she is half undressed",
  },
  {
    label: "膝まで下ろす",
    prompt: "no top; her skirt or pants are pulled down to her knees, her panties are visible",
  },
  {
    label: "足首まで下ろす",
    prompt:
      "no top; her skirt or pants are pulled down to her ankles around her shoes, her panties are visible",
  },
  {
    label: "ズボン・スカートを脱ぐ",
    prompt:
      "no top; her skirt or pants have completely left her body and lie on the floor at her feet; she wears only her bra and panties",
  },
  {
    label: "ブラの肩ひもを外す",
    prompt: "only bra and panties; the shoulder straps of her bra are slid down off her shoulders",
  },
  {
    label: "ブラをずらす（半脱ぎ）",
    prompt:
      "only bra and panties; her bra is pushed aside to the left and right so that it hangs open on her chest",
  },
  { label: "ブラを外す", prompt: "only panties; her bra is taken away completely" },
  {
    label: "下着を下ろす（半脱ぎ）",
    prompt: "no bra; her panties are pulled down to the middle of her thighs",
  },
  { label: "下着を脱ぐ", prompt: "no clothes at all, completely naked, standing in the same pose" },
];

/**
 * What must be drawn once the body is bare. Edit models tend to leave smooth blank skin there, so it
 * is written out: nipples and areolae, pubic hair in the colour of her hair, the genitals themselves.
 */
export const ANATOMY_CHEST =
  "Draw her bare breasts with realistic anatomy: both nipples and areolae clearly visible, with natural shading (no smooth blank skin, no censoring)";
export const ANATOMY_GROIN =
  "Draw her bare groin with realistic anatomy: natural pubic hair in exactly the same colour as the hair on her head, and her vulva drawn uncensored (no mosaic, no blur, no smooth blank skin)";

/** The pictures of the undress steps: un_1 … un_16. */
export function undressFrames(): AvatarFrame[] {
  return UNDRESS_STEPS.map((step, index) => ({
    key: `un_${index + 1}`,
    label: step.label,
    // The same underwear in every picture keeps the steps in line with each other.
    prompt:
      index < 12
        ? `${step.prompt}. ${UNDRESS_UNDERWEAR}`
        : index === 12
          ? `${step.prompt}. ${UNDRESS_UNDERWEAR}. ${ANATOMY_CHEST}`
          : index === 13
            ? `${step.prompt}. ${ANATOMY_CHEST}`
            : `${step.prompt}. ${ANATOMY_CHEST}. ${ANATOMY_GROIN}`,
    outfit: true,
  }));
}

/**
 * More faces and one pose for the touches (adult characters only): a blush, excitement, the
 * "ahegao" face, the peak of pleasure, disgust, and collapsing onto her knees. One picture each;
 * the app lays the face on top of the undress pictures too, so they work while she is half undressed.
 */
const EXTRA_FRAMES: Array<{ key: string; label: string; prompt: string; pose?: boolean }> = [
  {
    key: "blush",
    label: "赤面",
    prompt:
      "a deeply blushing face, red cheeks and ears, shy eyes looking away, lips pressed together",
  },
  {
    key: "excited",
    label: "興奮",
    prompt:
      "an excited, aroused face, flushed cheeks, wide shining eyes, mouth slightly open, breathing fast",
  },
  {
    key: "ahegao",
    label: "アヘ顔",
    prompt:
      "an ahegao face: eyes rolled up and crossed, tongue sticking out, drooling, deeply flushed cheeks, a blissful dazed expression",
  },
  {
    key: "climax",
    label: "絶頂",
    prompt:
      "the face at the peak of climax: eyes squeezed shut or rolled back, mouth wide open in a moan, tears of pleasure, flushed cheeks, sweat",
  },
  {
    key: "disgust",
    label: "嫌顔",
    prompt:
      "a disgusted face: frowning eyebrows, narrowed eyes, the corner of the mouth pulled down, turning her face away",
  },
  {
    key: "collapse",
    label: "膝から崩れ落ちる",
    pose: true,
    prompt:
      "her legs have given way: she collapses onto her knees and sits on the floor, her body trembling and leaning forward, her face flushed and exhausted, her hands on the floor",
  },
  {
    key: "squirt",
    label: "潮吹き",
    pose: true,
    prompt:
      "she is squirting at the peak of climax: her knees are bent and trembling, her thighs pressed together, clear fluid squirts and runs down her wet thighs and drips onto the floor, her clothes are wet, her face is flushed and ecstatic with her mouth open",
  },
];

/** The extra faces and the collapse: blush, excited, ahegao, climax, disgust, collapse. */
export function extraFrames(): AvatarFrame[] {
  return EXTRA_FRAMES.map((frame) => ({
    key: frame.key,
    label: frame.label,
    prompt: frame.prompt,
    ...(frame.pose ? { pose: true } : {}),
  }));
}

/** Every picture the avatar generation makes, in order. */
export function allAvatarFrames(): AvatarFrame[] {
  return [...avatarFrames(), ...extraFrames(), ...undressFrames()];
}

/**
 * The mini character (the helper that walks about the app's screen): five directions and a wave.
 * Left-facing views are the right-facing ones mirrored by the app, so eight directions need only these.
 */
export const MINI_FRAMES: AvatarFrame[] = [
  {
    key: "mini_front",
    label: "ミニ 正面",
    prompt:
      "seen from the front, facing the viewer, standing with the arms down, a calm friendly face",
  },
  {
    key: "mini_front34",
    label: "ミニ 斜め前",
    prompt:
      "seen from the front-right three-quarter view, in a walking pose with one foot forward, looking ahead",
  },
  {
    key: "mini_side",
    label: "ミニ 横",
    prompt: "seen exactly from the right side in profile, in a walking pose with one foot forward",
  },
  {
    key: "mini_back34",
    label: "ミニ 斜め後ろ",
    prompt:
      "seen from the back-right three-quarter view, in a walking pose, the back of her head and her hair visible",
  },
  {
    key: "mini_back",
    label: "ミニ 後ろ",
    prompt:
      "seen from directly behind, the back of her head, her hair and the back of her clothes visible, standing",
  },
  {
    key: "mini_happy",
    label: "ミニ 手を振る",
    prompt: "seen from the front, one hand raised waving, a bright smile",
  },
];

export function miniFramePrompt(frame: AvatarFrame): string {
  return (
    `Redraw the same character as a cute chibi mini character (super-deformed, about two heads tall, a big head and a small body), ` +
    `keeping the same hair colour and style, the same eye colour, the same outfit colours and the same art style as the reference picture. ` +
    `Full body, ${frame.prompt}. Flat simple shading, plain background, no text.`
  );
}
