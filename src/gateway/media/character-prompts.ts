import { QWEN_EDIT_MODEL, qwenTurboSettings } from "./config.js";

const PHOTO_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Full-body standing photograph of the same person as in the reference photo, front view, looking at the viewer, plain white background, " +
    "the whole body from head to feet, photorealistic. Keep the face, hairstyle, hair color, skin tone, body shape and clothes exactly the same as the photo.",
  anime:
    "Redraw the person in the reference photo as an anime character: full body, front view, looking at the viewer, plain white background, " +
    "from head to feet, clean line art and cel shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo, so the person is clearly recognizable.",
  illustration:
    "Redraw the person in the reference photo as a soft colored illustration: full body, front view, plain white background, from head to feet, " +
    "gentle shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo.",
};

/** The same character sheet, when the reference is an anime picture (the character's design is kept as it is). */
export const ANIME_SOURCE_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Re-imagine the anime character in the reference picture as a real person in a live-action photograph: full-body standing, front view, looking at the viewer, plain white background, " +
    "from head to feet, photorealistic, real human skin and natural body proportions. Keep the character's hair color and hairstyle, eye color, outfit design and colors, accessories and markings exactly as in the reference, like a faithful live-action adaptation.",
  anime:
    "Redraw the anime character in the reference picture as a clean character sheet: full body, front view, looking at the viewer, plain white background, from head to feet, " +
    "clean line art and cel shading. Keep the exact character design of the reference: face, eye color and shape, hair color and hairstyle, outfit, accessories, colors and proportions.",
  illustration:
    "Redraw the character in the reference picture as a soft colored illustration: full body, front view, plain white background, from head to feet, gentle shading. " +
    "Keep the exact character design of the reference: face, eyes, hair, outfit, accessories and colors.",
};

const ANIME_SOURCE_IDENTITY_PROMPT =
  " Change the rendering only; keep the individual design of reference 1: hair color and hairstyle silhouette, eye color, outfit, accessories, visible marks. Do not invent a different character. When the result must be a photograph, give the face natural human proportions while keeping the character recognizable.";

const RANDOM_BODIES = ["slender", "average", "athletic", "soft and curvy", "petite"];

const RANDOM_OUTFITS = [
  "a white blouse and a navy pleated skirt",
  "a casual hoodie and jeans",
  "a simple summer dress",
  "a knit sweater and a long skirt",
  "a denim jacket over a T-shirt with slim pants",
  "a plain blazer, a shirt and tailored trousers",
  "a trench coat over a dress",
  "a cardigan, a camisole and a mini skirt",
  "a sporty tracksuit",
  "a floral one-piece dress",
];

const RANDOM_SHOES = [
  "white sneakers",
  "loafers",
  "ankle boots",
  "flat sandals",
  "low-heeled pumps",
];

/**
 * When the reference shows only a face, there is nothing to copy for the body and clothes: they are chosen at random
 * (a different pick each time), an ordinary everyday outfit; the face, hair and skin stay exactly as in the reference.
 */
export function randomBodyPrompt(random: () => number = Math.random, outfit?: string): string {
  const pick = <T>(list: readonly T[]): T =>
    list[Math.min(list.length - 1, Math.floor(random() * list.length))]!;
  // An outfit that is already set (her group's or category's costume) wins over a random one.
  const wearing = outfit?.trim()
    ? outfit.trim()
    : `${pick(RANDOM_OUTFITS)} and ${pick(RANDOM_SHOES)}`;
  return (
    ` The reference shows only the face, with no body or clothes. Draw a natural adult full body for her: ${pick(RANDOM_BODIES)} build, wearing ${wearing}. ` +
    (outfit?.trim()
      ? "The outfit is her group's costume; the build was chosen at random because the reference has no body. "
      : "These were chosen at random because the reference has no body. ") +
    FACE_ONLY_PROPORTIONS +
    "Take only the face, hair and skin tone from the reference and keep them exactly."
  );
}

/**
 * A face close-up fills its whole frame, so the editor tends to draw the head as big as in the close-up, on a small
 * body. The sentence asks for the real proportions of a full body: the head is a small part of the height.
 */
export const FACE_ONLY_PROPORTIONS =
  "The reference is a close-up, so the face looks large in it: do NOT keep that scale. Draw the whole body in natural adult proportions, about 7 to 8 heads tall, with the head small compared to the body, narrow shoulders, long neck and long legs, and show the full body from head to feet. ";

/** The style sentence for a character's sheet: depends on what the reference is (photo / anime) and what is wanted. */
export function photoStylePrompt(
  source: "photo" | "anime" | undefined,
  style: "photo" | "anime" | "illustration",
): string {
  return source === "anime" ? ANIME_SOURCE_STYLE_PROMPT[style] : PHOTO_STYLE_PROMPT[style];
}

export function photoIdentityPrompt(source: "photo" | "anime" | undefined): string {
  return source === "anime" ? ANIME_SOURCE_IDENTITY_PROMPT : PHOTO_IDENTITY_PROMPT;
}

const PHOTO_IDENTITY_PROMPT =
  " Change the rendering style only; preserve the individual design of reference 1. Keep the distinctive face outline, brow shape, eye spacing and tilt, nose and mouth shape, hairline, hairstyle silhouette, eye and hair colors, visible marks, glasses and accessories. Do not replace the face with a generic attractive model or generic anime face. Avoid enlarging the eyes or narrowing the jaw beyond what the requested style requires. Reference 1 is authoritative when other references disagree.";

/** The Wan2GP settings of the three editors for a photo (the stronger ones are slower). */
export function photoEngineSettings(engine: "fast" | "precise" | "edit2509"): {
  settings: Record<string, unknown>;
  needsLora: boolean;
} {
  if (engine === "fast") return { settings: qwenTurboSettings(), needsLora: true };
  if (engine === "edit2509") {
    return {
      settings: {
        model_type: "qwen_image_edit_plus_20B_nunchaku_r128_int4",
        num_inference_steps: 4,
        guidance_scale: 1,
      },
      needsLora: false,
    };
  }
  // Qwen Image 2.1 at its own step count (about 5 minutes), without the speed LoRA: closer to the photo.
  return {
    settings: {
      model_type: QWEN_EDIT_MODEL,
      num_inference_steps: 30,
      guidance_scale: 4,
      negative_prompt: " ",
    },
    needsLora: false,
  };
}

/** What each kind of photo is used for, in English (for the editor). */
const PHOTO_ROLE_EN: Record<string, string> = {
  顔: "the face and skin",
  髪: "the hair (color and hairstyle)",
  服: "the clothes and outfit",
  体型: "the body shape and proportions",
  雰囲気: "the overall mood and color palette",
  小物: "the accessories",
};

const STYLE_TAIL: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Draw the same person as a realistic photograph: full body, standing, front view, looking at the viewer, plain white background, from head to feet.",
  anime:
    "Draw the same person as an anime character: full body, standing, front view, looking at the viewer, plain white background, from head to feet, clean line art and cel shading.",
  illustration:
    "Draw the same person as a soft colored illustration: full body, standing, front view, plain white background, from head to feet, gentle shading.",
};

/** The editor's instruction when several photos are given: blend them, or take one part from each. */
export function multiPhotoPrompt(
  style: "photo" | "anime" | "illustration",
  mode: "subject" | "blend" | "roles",
  roles: string[],
  count: number,
): string {
  const intro =
    mode === "subject"
      ? `Create ONE adult character. All ${count} reference pictures show the SAME target person. Use reference picture 1 as the identity anchor for face, hair and skin. Other pictures provide only visible details of this same person, such as body shape, clothing and accessories. Never combine features from bystanders or background objects.`
      : mode === "roles"
        ? `Create ONE adult person from ${count} reference pictures. ` +
          Array.from(
            { length: count },
            (_, i) =>
              `Use reference picture ${i + 1} only for ${PHOTO_ROLE_EN[roles[i] ?? ""] ?? PHOTO_ROLE_EN["顔"]}.`,
          ).join(" ")
        : `Create ONE new adult person by blending the people in the ${count} reference pictures: mix their faces, eyes, hair and style evenly, so that this is a new person who resembles all of them a little.`;
  return `${intro} ${STYLE_TAIL[style]}`;
}
