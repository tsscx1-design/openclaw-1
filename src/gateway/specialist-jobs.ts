import { randomUUID } from "node:crypto";

export type SpecialistJobStatus = "running" | "completed" | "failed";

export type SpecialistJob = {
  id: string;
  kind: "delegation" | "partner-create";
  title: string;
  workerName: string;
  status: SpecialistJobStatus;
  /** What a long job is doing right now (shown while it runs). */
  stage?: string;
  startedAt: number;
  finishedAt?: number;
  resultPreview?: string;
  error?: string;
};

const MAX_JOBS = 50;
const jobs: SpecialistJob[] = [];

/** Records a background job so the app can list what the workers are doing. */
export function startSpecialistJob(
  params: Pick<SpecialistJob, "kind" | "title" | "workerName">,
): SpecialistJob {
  const job: SpecialistJob = {
    ...params,
    id: randomUUID(),
    status: "running",
    startedAt: Date.now(),
  };
  jobs.push(job);
  if (jobs.length > MAX_JOBS) {
    jobs.splice(0, jobs.length - MAX_JOBS);
  }
  return job;
}

export function setSpecialistJobStage(job: SpecialistJob, stage: string): void {
  if (job.status === "running") {
    job.stage = stage.slice(0, 200);
  }
}

export function finishSpecialistJob(
  job: SpecialistJob,
  outcome: { resultPreview?: string; error?: string },
): void {
  job.status = outcome.error ? "failed" : "completed";
  job.stage = undefined;
  job.finishedAt = Date.now();
  if (outcome.resultPreview) {
    job.resultPreview = outcome.resultPreview.slice(0, 600);
  }
  if (outcome.error) {
    job.error = outcome.error.slice(0, 300);
  }
}

/** Newest first. */
export function listSpecialistJobs(): SpecialistJob[] {
  return [...jobs].reverse().map((job) => ({ ...job }));
}

export function resetSpecialistJobsForTest(): void {
  jobs.length = 0;
}
