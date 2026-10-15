import { researchPartnerSubject, type ResearchIO, type SubjectKind } from "./partner-research.js";
import {
  digestResearch,
  fastSheetCompletion,
  generatePartnerSheet,
  workerSheetCompletion,
  type SheetCompletion,
} from "./partner-sheet-generator.js";
import { savePartnerProfileWithSheet } from "./server-methods/partners.js";

// One partner-creation flow for the agent tool and the Gateway method: research the subject on
// several sites, digest the pages, fill the sheet and save the partner.

export type PartnerCreationRequest = {
  name: string;
  source?: string;
  kind?: SubjectKind;
  aliases?: string[];
  notes?: string;
  urls?: string[];
  partnerId?: string;
};

export type PartnerCreationResult = {
  partnerId: string;
  name: string;
  kind: SubjectKind;
  filled: number;
  total: number;
  sites: string[];
  sourceCount: number;
  quoteCount: number;
  durationMs: number;
};

/** Trims a request from the agent or the app into the bounded shape the flow accepts. */
export function sanitizePartnerCreationRequest(raw: unknown): PartnerCreationRequest | undefined {
  const input = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const text = (value: unknown, max: number) =>
    typeof value === "string" && value.trim() ? value.trim().slice(0, max) : undefined;
  const list = (value: unknown, max: number, filter: (item: string) => boolean = () => true) =>
    Array.isArray(value)
      ? value
          .filter((item): item is string => typeof item === "string")
          .map((item) => item.trim())
          .filter((item) => item.length > 0 && filter(item))
          .slice(0, max)
      : undefined;
  const name = text(input.name, 60);
  if (!name) {
    return undefined;
  }
  const kind = input.kind === "real_person" || input.kind === "fictional" ? input.kind : undefined;
  return {
    name,
    source: text(input.source, 80),
    kind,
    aliases: list(input.aliases, 5)?.map((alias) => alias.slice(0, 40)),
    notes: text(input.notes, 600),
    urls: list(input.urls, 5, (url) => /^https?:\/\//i.test(url)),
    partnerId: text(input.partnerId, 80),
  };
}

/**
 * Researches the subject on several sites and saves the partner. Throws with a next step when no
 * page about the subject could be read, instead of saving an empty character.
 */
export async function createPartnerFromResearch(
  params: PartnerCreationRequest & {
    io: ResearchIO;
    /**
     * Reads the pages. The CPU Gemma worker by default: the faster GPU chat model misread
     * articles (a song title became a speech habit), and the notes decide everything after.
     */
    reader?: SheetCompletion;
    /** Fills the sheet fields from the clean notes (GPU when idle, CPU worker otherwise). */
    fast?: SheetCompletion;
    /** Writes the portrait and sample dialogue (natural Japanese). */
    writer?: SheetCompletion;
    save?: typeof savePartnerProfileWithSheet;
    onProgress?: (message: string) => void;
  },
): Promise<PartnerCreationResult> {
  const started = Date.now();
  const progress = params.onProgress ?? (() => undefined);
  const reader = params.reader ?? workerSheetCompletion;
  const fast = params.fast ?? fastSheetCompletion;
  const writer = params.writer ?? workerSheetCompletion;
  const save = params.save ?? savePartnerProfileWithSheet;
  const label = params.source ? `${params.name}（${params.source}）` : params.name;

  const bundle = await researchPartnerSubject({
    name: params.name,
    source: params.source,
    kind: params.kind,
    aliases: params.aliases,
    urls: params.urls,
    io: params.io,
    onProgress: (message) => progress(`🔎 ${label}: ${message}`),
  });
  if (bundle.sources.length === 0) {
    throw new Error(
      `「${label}」について読める資料が見つかりませんでした。名前の表記（正式名・愛称）や作品名を変えるか、参考ページのURLを指定してください。`,
    );
  }
  const digest = await digestResearch({
    bundle,
    source: params.source,
    complete: reader,
    onSource: (index, total, site) =>
      progress(`📖 ${label}: 資料を読み取っています（${index}/${total}）${site}`),
  });
  const generated = await generatePartnerSheet({
    name: params.name,
    source: params.source,
    notes: params.notes,
    research: { bundle, digest, writer },
    complete: fast,
    onCategory: (title) => progress(`✍️ ${label}: キャラクターシートを作っています（${title}）`),
  });
  const saved = await save({
    partnerId: params.partnerId,
    name: params.name,
    persona: generated.persona,
    characterSheet: generated.sheet,
    realPerson: bundle.kind === "real_person",
  });
  return {
    partnerId: saved.id,
    name: saved.name,
    kind: bundle.kind,
    filled: generated.filled,
    total: generated.total,
    sites: [...new Set(bundle.sources.map((source) => source.site))],
    sourceCount: bundle.sources.length,
    quoteCount: digest.quotes.length,
    durationMs: Date.now() - started,
  };
}
