import { resolveOllamaBaseUrlForRun } from "./provider-base-url.js";

/**
 * Ollama Manager provides programmatic access to Ollama's model management REST API.
 */
export class OllamaManager {
  /** 明示的に固定した場合のみ保持。null の場合は毎回設定から動的解決する (問題9修正)。 */
  private readonly fixedBaseUrl: string | undefined;

  constructor(baseUrl?: string) {
    // baseUrl を明示指定した場合のみ固定。省略時は毎回動的解決する。
    this.fixedBaseUrl = baseUrl;
  }

  /**
   * 問題9修正: Ollama の baseUrl を毎回動的解決する。
   * これにより openclaw.json の baseUrl 変更が Gateway 再起動なしに反映される。
   */
  private getBaseUrl(): string {
    return this.fixedBaseUrl ?? resolveOllamaBaseUrlForRun() ?? "http://127.0.0.1:11434";
  }

  /**
   * List all models currently downloaded in Ollama.
   */
  async listModels(): Promise<any[]> {
    const baseUrl = this.getBaseUrl();
    try {
      const response = await fetch(new URL("/api/tags", baseUrl).toString(), {
        method: "GET",
        headers: { "Content-Type": "application/json" },
      });
      if (response.ok) {
        const data = (await response.json()) as { models?: unknown };
        return Array.isArray(data?.models) ? data.models : [];
      }
    } catch (e) {
      // ignore network errors for first try
    }

    // Fallback to OpenAI API format for llama-server.exe compatibility
    try {
      const response = await fetch(new URL("/v1/models", baseUrl).toString(), {
        method: "GET",
        headers: { "Content-Type": "application/json" },
      });
      if (response.ok) {
        const data = (await response.json()) as { data?: unknown };
        // Convert OpenAI format to Ollama format
        return (Array.isArray(data?.data) ? data.data : []).map((m: any) => ({
          name: m.id,
          model: m.id,
          details: {
            parameter_size: m.meta?.size
              ? `${Math.round(m.meta.size / 1024 / 1024 / 1024)}B`
              : "Unknown",
            quantization_level: m.meta?.ftype || "Unknown",
          },
        }));
      }
    } catch (e) {
      // ignore
    }

    return [];
  }

  /**
   * Pull (download) a model from the Ollama registry.
   */
  async pullModel(modelName: string): Promise<void> {
    await this.pullModelWithProgress(modelName);
  }

  /**
   * Pull a model with streaming progress updates.
   */
  async pullModelWithProgress(
    modelName: string,
    onProgress?: (progress: {
      status: string;
      completed?: number;
      total?: number;
      percent?: number;
    }) => void,
  ): Promise<void> {
    const url = new URL("/api/pull", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: modelName, stream: true }),
    });

    if (!response.ok || !response.body) {
      throw new Error(`Failed to pull Ollama model ${modelName}: ${response.statusText}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        try {
          const data = JSON.parse(trimmed);
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          if (onProgress) {
            onProgress({
              status: data.status || "downloading",
              completed: data.completed,
              total: data.total,
              percent,
            });
          }
        } catch {
          // ignore malformed JSON chunk
        }
      }
    }

    // A+-2 修正: ストリーム終了後にバッファに残っている最後の行を処理
    if (buffer.trim()) {
      try {
        const data = JSON.parse(buffer.trim());
        if (onProgress) {
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          onProgress({
            status: data.status || "downloading",
            completed: data.completed,
            total: data.total,
            percent,
          });
        }
      } catch {
        /* 不完全なJSON — 無視 */
      }
    }
  }

  /**
   * Unload (stop) a model from VRAM by sending an empty request with keep_alive = 0.
   */
  async stopModel(modelName: string): Promise<void> {
    const cleanName = modelName.replace(/^ollama\//, "").trim();
    const url = new URL("/api/generate", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: cleanName,
        keep_alive: 0,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to stop/unload Ollama model ${cleanName}: ${response.statusText}`);
    }
  }

  /**
   * Load a model strictly into System RAM (CPU-only, num_gpu=0) and keep it warm.
   */
  async loadModelToRam(modelName: string): Promise<void> {
    const cleanName = modelName.replace(/^ollama\//, "").trim();
    const url = new URL("/api/generate", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: cleanName,
        keep_alive: "24h",
        options: {
          num_gpu: 0,
        },
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to load Ollama model ${cleanName} to RAM: ${response.statusText}`);
    }
  }

  /**
   * Delete a model from local Ollama storage.
   */
  async deleteModel(modelName: string): Promise<void> {
    const cleanName = modelName.replace(/^ollama\//, "");
    const url = new URL("/api/delete", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: cleanName,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to delete Ollama model ${cleanName}: ${response.statusText}`);
    }
  }

  /**
   * Search Hugging Face Hub for GGUF models by genre, language, tags, sort, and query.
   */
  async searchHuggingFace(options: {
    query?: string;
    category?: string;
    sort?: "downloads" | "likes" | "lastModified";
    limit?: number;
  }): Promise<any[]> {
    const url = new URL("https://huggingface.co/api/models");
    const searchTerms: string[] = [];
    if (options.query?.trim()) {
      searchTerms.push(options.query.trim());
    }

    if (options.category === "japanese") {
      searchTerms.push("japanese");
    } else if (options.category === "uncensored") {
      searchTerms.push("uncensored");
    } else if (options.category === "roleplay") {
      searchTerms.push("roleplay");
    } else if (options.category === "reasoning") {
      searchTerms.push("reasoning");
    } else if (options.category === "coding") {
      searchTerms.push("code");
    }

    if (searchTerms.length > 0) {
      url.searchParams.set("search", searchTerms.join(" "));
    }

    url.searchParams.set("filter", "gguf");
    url.searchParams.set("sort", options.sort ?? "downloads");
    url.searchParams.set("direction", "-1");
    url.searchParams.set("limit", String(Math.min(50, Math.max(5, options.limit ?? 25))));

    const res = await fetch(url.toString(), {
      headers: { "User-Agent": "OpenClaw-Gateway/1.0" },
    });
    if (!res.ok) {
      throw new Error(`Hugging Face API error: ${res.statusText}`);
    }
    const data: unknown = await res.json();
    return (Array.isArray(data) ? data : []).map((m: any) => ({
      id: m.id,
      pullId: `hf.co/${m.id}`,
      likes: m.likes || 0,
      downloads: m.downloads || 0,
      lastModified: m.lastModified,
      pipelineTag: m.pipeline_tag,
      tags: (m.tags || []).slice(0, 10),
    }));
  }

  /**
   * Create (import) an Ollama model directly from a local GGUF file path.
   */
  async createModelFromGguf(
    modelName: string,
    ggufPath: string,
    onProgress?: (status: {
      status: string;
      completed?: number;
      total?: number;
      percent?: number;
    }) => void,
  ): Promise<void> {
    const cleanName = modelName.replace(/^ollama\//, "").trim();
    const normalizedPath = ggufPath.replace(/\\/g, "/");
    const url = new URL("/api/create", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: cleanName,
        from: normalizedPath,
        stream: true,
      }),
    });

    if (!response.ok || !response.body) {
      const errText = await response.text().catch(() => "");
      throw new Error(`Failed to create model from GGUF: ${errText || response.statusText}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        try {
          const data = JSON.parse(trimmed);
          if (data.error) {
            throw new Error(data.error);
          }
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          if (onProgress) {
            onProgress({
              status: data.status || "processing",
              completed: data.completed,
              total: data.total,
              percent,
            });
          }
        } catch (e: any) {
          if (e.message && !e.message.includes("Unexpected token")) {
            throw e;
          }
        }
      }
    }

    // A+-2 修正: ストリーム終了後にバッファ残りを処理
    if (buffer.trim()) {
      try {
        const data = JSON.parse(buffer.trim());
        if (data.error) throw new Error(data.error);
        if (onProgress) {
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          onProgress({
            status: data.status || "processing",
            completed: data.completed,
            total: data.total,
            percent,
          });
        }
      } catch (e: any) {
        if (e.message && !e.message.includes("Unexpected token")) throw e;
      }
    }
  }

  /**
   * Scan local common directories for .gguf files to allow easy one-click import.
   */
  async scanLocalGgufFiles(
    customDirs?: string[],
  ): Promise<Array<{ name: string; path: string; sizeGb: string }>> {
    const fs = await import("node:fs");
    const path = await import("node:path");
    const os = await import("node:os");

    const home = os.homedir();
    const searchDirs: string[] = [];

    // 問題5修正: customDirs の各エントリをホームディレクトリ以下に限定して検証
    if (customDirs && customDirs.length > 0) {
      for (const rawDir of customDirs) {
        if (typeof rawDir !== "string") continue;
        const normalized = path.resolve(rawDir);
        // セキュリティ: ホームディレクトリの直下のみ許可（任意ディレクトリスキャンを防止）
        if (!normalized.startsWith(home + path.sep) && normalized !== home) {
          console.warn(
            `[OllamaManager] customDir '${rawDir}' is outside home directory — skipped for security.`,
          );
          continue;
        }
        if (!searchDirs.includes(normalized)) {
          searchDirs.push(normalized);
        }
      }
    }

    // デフォルト検索ディレクトリ (ハードコード修正: ホーム相対に変更)
    const downloads = path.join(home, "Downloads");
    const desktop = path.join(home, "Desktop");
    const documents = path.join(home, "Documents");
    const openclawModels = path.join(home, "openclaw-models");
    for (const d of [downloads, desktop, documents, openclawModels]) {
      if (fs.existsSync(d) && !searchDirs.includes(d)) {
        searchDirs.push(d);
      }
    }

    const results: Array<{ name: string; path: string; sizeGb: string }> = [];
    for (const dir of searchDirs) {
      try {
        if (!fs.existsSync(dir)) continue;
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
          if (entry.isFile() && entry.name.toLowerCase().endsWith(".gguf")) {
            const fullPath = path.join(dir, entry.name);
            const stats = fs.statSync(fullPath);
            const sizeGb = (stats.size / (1024 * 1024 * 1024)).toFixed(2) + " GB";
            results.push({
              name: entry.name,
              path: fullPath,
              sizeGb,
            });
          }
        }
      } catch {
        // ignore dir read error
      }
    }
    return results;
  }

  /**
   * Automatically calculate the optimal execution parameters based on system RAM (64GB), CPU cores, and model size.
   */
  async optimizeGgufOptions(params?: {
    modelSizeGb?: number;
    targetDevice?: "vram" | "ram" | "auto";
  }): Promise<{
    num_ctx: number;
    num_gpu: number;
    num_thread: number;
    strategy: string;
    summary: string;
  }> {
    const os = await import("node:os");
    const totalRamGb = Math.round(os.totalmem() / (1024 * 1024 * 1024));
    const cpuCores = os.cpus().length;
    const optimalThreads = Math.max(4, Math.min(16, cpuCores - 2));

    const targetDevice = params?.targetDevice || "auto";
    const sizeGb = params?.modelSizeGb || 5;

    let num_ctx = 16384;
    let num_gpu = 99; // Default: offload as many layers to GPU as possible
    let strategy = "VRAM-Priority Fast Response";
    let summary = "";

    if (targetDevice === "ram" || (targetDevice === "auto" && sizeGb > 16)) {
      // 64GB RAM High-Capacity Deep Thinking Mode
      num_ctx = totalRamGb >= 60 ? 32768 : 16384;
      num_gpu = 15; // Hybrid: partial layers in VRAM, bulk weights in 64GB RAM
      strategy = "64GB RAM Hybrid Deep Thinking";
      summary = `64GBメインメモリを活用し、コンテキスト長を${num_ctx}に拡大。CPU+GPUハイブリッド推論 (${optimalThreads}スレッド) に最適化しました。`;
    } else {
      // VRAM Fast Interactive Persona Mode
      num_ctx = 16384;
      num_gpu = 99;
      strategy = "VRAM Ultra-Fast Response";
      summary = `GPU VRAMを優先活用し、低遅延・リアルタイムな対話応答 (num_ctx: ${num_ctx}) に最適化しました。`;
    }

    return {
      num_ctx,
      num_gpu,
      num_thread: optimalThreads,
      strategy,
      summary,
    };
  }

  /**
   * Create an optimized Ollama model from a local GGUF file with custom context length and thread allocation.
   */
  async createOptimizedModelFromGguf(
    modelName: string,
    ggufPath: string,
    options?: { targetDevice?: "vram" | "ram" | "auto"; customCtx?: number },
    onProgress?: (status: {
      status: string;
      completed?: number;
      total?: number;
      percent?: number;
    }) => void,
  ): Promise<{ model: string; options: any }> {
    const tuning = await this.optimizeGgufOptions(options);
    const effectiveCtx = options?.customCtx || tuning.num_ctx;
    const cleanName = modelName.replace(/^ollama\//, "").trim();
    const normalizedPath = ggufPath.replace(/\\/g, "/");

    // Construct an optimized Modelfile content
    const modelfile = [
      `FROM ${normalizedPath}`,
      `PARAMETER num_ctx ${effectiveCtx}`,
      `PARAMETER num_thread ${tuning.num_thread}`,
      `PARAMETER stop "<|im_start|>"`,
      `PARAMETER stop "<|im_end|>"`,
    ].join("\n");

    const url = new URL("/api/create", this.getBaseUrl());
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: cleanName,
        modelfile,
        stream: true,
      }),
    });

    if (!response.ok || !response.body) {
      const errText = await response.text().catch(() => "");
      throw new Error(
        `Failed to create optimized model from GGUF: ${errText || response.statusText}`,
      );
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        try {
          const data = JSON.parse(trimmed);
          if (data.error) {
            throw new Error(data.error);
          }
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          if (onProgress) {
            onProgress({
              status: data.status || "optimizing",
              completed: data.completed,
              total: data.total,
              percent,
            });
          }
        } catch (e: any) {
          if (e.message && !e.message.includes("Unexpected token")) {
            throw e;
          }
        }
      }
    }

    // A+-2 修正: ストリーム終了後にバッファ残りを処理
    if (buffer.trim()) {
      try {
        const data = JSON.parse(buffer.trim());
        if (data.error) throw new Error(data.error);
        if (onProgress) {
          let percent: number | undefined = undefined;
          if (
            typeof data.total === "number" &&
            data.total > 0 &&
            typeof data.completed === "number"
          ) {
            percent = Math.min(100, Math.round((data.completed / data.total) * 100));
          }
          onProgress({
            status: data.status || "optimizing",
            completed: data.completed,
            total: data.total,
            percent,
          });
        }
      } catch (e: any) {
        if (e.message && !e.message.includes("Unexpected token")) throw e;
      }
    }

    return {
      model: cleanName,
      options: { ...tuning, num_ctx: effectiveCtx },
    };
  }

  /**
   * Dual LLM Co-op Orchestration:
   * VRAM Model (Fast, persona, user-facing) cooperates with RAM Model (Heavy 64GB, deep reasoning, memory/task worker).
   */
  async dualChatOrchestrate(params: {
    vramModel: string;
    ramModel?: string;
    messages: Array<{ role: string; content: string }>;
    personaPrompt?: string;
  }): Promise<{
    content: string;
    usedDualThinker: boolean;
    thoughtProcess?: string;
    latencyMs: number;
  }> {
    const startTime = Date.now();
    const { vramModel, ramModel, messages, personaPrompt } = params;
    const lastUserMessage = [...messages].reverse().find((m) => m.role === "user")?.content || "";

    // Determine if the task requires deep thinking / heavy memory search
    const complexIndicators = [
      "詳しく",
      "理由",
      "考察",
      "分析",
      "なぜ",
      "どうして",
      "比較",
      "計画",
      "コード",
      "作成して",
      "要約",
      "整理して",
      "思い出して",
      "過去の",
      "思考",
      "深掘り",
      "説明して",
      "教えて",
      "解いて",
      "アイデア",
      "ステップ",
    ];
    const isComplex =
      Boolean(ramModel) &&
      (lastUserMessage.length > 50 || complexIndicators.some((kw) => lastUserMessage.includes(kw)));

    let thoughtProcess: string | undefined = undefined;

    if (isComplex && ramModel) {
      // Step 1: Request deep analytical reasoning from the RAM-backed heavyweight model
      try {
        const ramUrl = new URL("/api/chat", this.getBaseUrl());
        // 問題6修正: RAM モデルへの fetch に 90秒タイムアウトを設定
        const ramRes = await fetch(ramUrl.toString(), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          signal: AbortSignal.timeout(90_000),
          body: JSON.stringify({
            model: ramModel,
            messages: [
              {
                role: "system",
                content:
                  "あなたは64GB RAMを活用した深層思考・知識分析担当のサポートAIです。ユーザーの質問に対して論理的根拠、重要事実、具体的な解決策を深く論理立てて簡潔にまとめてください。",
              },
              {
                role: "user",
                content: lastUserMessage,
              },
            ],
            stream: false,
            options: {
              num_ctx: 32768,
            },
          }),
        });
        if (ramRes.ok) {
          const ramData = (await ramRes.json()) as { message?: { content?: string } } | null;
          thoughtProcess = ramData?.message?.content || "";
        }
      } catch (e) {
        // Fall back to direct VRAM model answering if RAM model call fails (timeout included)
      }
    }

    // Step 2: VRAM model generates the final user-facing response with persona & tone
    const vramMessages: Array<{ role: string; content: string }> = [];
    if (personaPrompt) {
      vramMessages.push({ role: "system", content: personaPrompt });
    }

    // Include recent conversational history
    for (const msg of messages.slice(-6, -1)) {
      vramMessages.push(msg);
    }

    if (thoughtProcess) {
      // Feed the RAM model's deep thought into VRAM model's context
      vramMessages.push({
        role: "user",
        content: `${lastUserMessage}\n\n[システム内部連携: サポートAI(64GB RAM)による深層思考結果]\n${thoughtProcess}\n\n上記思考結果を踏まえ、あなたの設定・口調に沿って自然で温かく回答してください。`,
      });
    } else {
      vramMessages.push({ role: "user", content: lastUserMessage });
    }

    const vramUrl = new URL("/api/chat", this.getBaseUrl());
    // 問題6修正: VRAM モデルへの fetch に 120秒タイムアウトを設定
    const vramRes = await fetch(vramUrl.toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: AbortSignal.timeout(120_000),
      body: JSON.stringify({
        model: vramModel,
        messages: vramMessages,
        stream: false,
        options: {
          num_ctx: 16384,
        },
      }),
    });

    if (!vramRes.ok) {
      const errText = await vramRes.text().catch(() => "");
      throw new Error(`VRAM model chat error: ${errText || vramRes.statusText}`);
    }

    const vramData = (await vramRes.json()) as { message?: { content?: string } } | null;
    const finalContent = vramData?.message?.content || "";
    const latencyMs = Date.now() - startTime;

    return {
      content: finalContent,
      usedDualThinker: Boolean(thoughtProcess),
      thoughtProcess,
      latencyMs,
    };
  }
}
