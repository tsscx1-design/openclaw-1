import type { Command } from "commander";
import { OllamaManager } from "../../extensions/ollama/src/ollama-manager.js";

export function registerOllamaCli(program: Command): void {
  const ollama = program.command("ollama").description("Manage Ollama models directly via API");

  ollama
    .command("list")
    .description("List currently downloaded Ollama models")
    .action(async () => {
      try {
        const manager = new OllamaManager();
        const models = await manager.listModels();
        if (models.length === 0) {
          console.log("No Ollama models found.");
          return;
        }
        for (const m of models) {
          console.log(`- ${m.name}`);
        }
      } catch (err) {
        console.error(`Error: ${err instanceof Error ? err.message : String(err)}`);
        process.exitCode = 1;
      }
    });

  ollama
    .command("pull <model>")
    .description("Pull an Ollama model")
    .action(async (model: string) => {
      try {
        const manager = new OllamaManager();
        console.log(`Pulling model ${model}...`);
        await manager.pullModel(model);
        console.log(`Successfully pulled model ${model}`);
      } catch (err) {
        console.error(`Error: ${err instanceof Error ? err.message : String(err)}`);
        process.exitCode = 1;
      }
    });

  ollama
    .command("stop <model>")
    .description("Stop (unload) an Ollama model from VRAM")
    .action(async (model: string) => {
      try {
        const manager = new OllamaManager();
        console.log(`Stopping model ${model}...`);
        await manager.stopModel(model);
        console.log(`Successfully stopped model ${model}`);
      } catch (err) {
        console.error(`Error: ${err instanceof Error ? err.message : String(err)}`);
        process.exitCode = 1;
      }
    });
}
