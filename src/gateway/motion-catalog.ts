import { customGroupOf } from "./custom-motions.js";
import { MOTION_OPTIONS, MOTION_TEMPLATES } from "./motion-comic.js";

// The lists of the motion comic as the app shows them: every pose (grouped by the kind of play) and
// every option (grouped as they are written). The app asks for this list instead of keeping its own,
// so a pose or option added here appears in the app without building it again.

export type MotionCatalog = {
  poses: Array<{ id: string; label: string; group: string }>;
  options: Array<{ group: string; items: Array<{ id: string; label: string }> }>;
};

type PoseLike = { label: string; position: string; act?: string };

const RULES: Array<[string, RegExp]> = [
  ["ひとり", /masturbation|solo|ひとり|オナ/i],
  [
    "複数人",
    /threesome|group|gangbang|orgy|foursome|harem|double penetration|mmf|ffm|3p|4p|複数|乱交|輪|ハーレム|二穴|3P|4P/i,
  ],
  ["拘束・SM", /bondage|tied|restrained|shibari|suspension|spreader|cuffs|拘束|縛|吊|磔|SM/i],
  [
    "口・舌",
    /oral|fellatio|blowjob|cunnilingus|deepthroat|facesit|69|rimjob|フェラ|クンニ|口|舌|イラマ|シックスナイン|顔面騎乗/i,
  ],
  [
    "手・足・胸",
    /handjob|footjob|thighjob|paizuri|titfuck|breast sex|boobjob|armpit|手コキ|足コキ|パイズリ|素股|脇|太もも/i,
  ],
  ["お尻", /anal|ass sex|butt|アナル|お尻|尻/i],
  ["騎乗位", /cowgirl|riding|on top|girl on top|reverse cowgirl|騎乗/i],
  ["後背位（バック）", /doggy|from behind|rear|prone bone|バック|後背|寝バック|獣/i],
  ["立位・駅弁", /standing|against wall|carry|suspended|lifting|立ち|立位|駅弁|壁/i],
  ["座位", /sitting|seated|lap|face to face|chair|couch|座|対面/i],
  ["側位", /spoon|side|lying on side|側位|横|スプーン/i],
  ["正常位", /missionary|legs up|mating press|folded|正常|屈曲|種付けプレス|まんぐり|開脚/i],
];

export function poseGroupOf(template: PoseLike): string {
  const text = `${template.label} ${template.position} ${template.act ?? ""}`;
  for (const [group, pattern] of RULES) if (pattern.test(text)) return group;
  return "その他・特殊";
}

export function motionCatalog(): MotionCatalog {
  const poses = Object.entries(MOTION_TEMPLATES as Record<string, PoseLike>).map(
    ([id, template]) => ({
      id,
      label: template.label,
      group: customGroupOf(id) ?? poseGroupOf(template),
    }),
  );
  const byGroup = new Map<string, Array<{ id: string; label: string }>>();
  for (const [id, option] of Object.entries(
    MOTION_OPTIONS as Record<string, { label: string; group: string }>,
  )) {
    const group = customGroupOf(id) ?? option.group;
    const list = byGroup.get(group) ?? [];
    list.push({ id, label: option.label });
    byGroup.set(group, list);
  }
  return { poses, options: [...byGroup.entries()].map(([group, items]) => ({ group, items })) };
}
