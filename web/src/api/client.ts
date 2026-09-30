export const TOKEN_KEY = "lingualoop.token";
export const LEARNER_KEY = "lingualoop.learner";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export class NetworkError extends Error {
  constructor() {
    super("Network request failed");
    this.name = "NetworkError";
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  let response: Response;
  try {
    response = await fetch(path, { ...options, headers });
  } catch {
    throw new NetworkError();
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(response.status, problem?.detail ?? `${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export interface AudioAssetDto {
  id: number;
  durationMs: number;
  bytes: number;
}

export interface Exercise {
  id: number;
  type: "TRANSLATE" | "MULTIPLE_CHOICE" | "LISTEN";
  prompt: string;
  answer: string;
  choices: string[];
  caption: string | null;
  audioAsset: AudioAssetDto | null;
}

export interface LessonSummary {
  id: number;
  title: string;
  position: number;
  exerciseCount: number;
}

export interface Unit {
  id: number;
  languageId: number;
  title: string;
  position: number;
  lessons: LessonSummary[];
}

export interface Language {
  id: number;
  code: string;
  name: string;
  unitCount: number;
}

export interface Lesson {
  id: number;
  unitId: number;
  title: string;
  position: number;
  exercises: Exercise[];
}

export interface LearnerDto {
  id: number;
  email: string;
  displayName: string;
  timezone: string;
}

export interface AuthResponse {
  token: string;
  learner: LearnerDto;
}

export interface ReviewInfo {
  easeFactor: number;
  intervalDays: number;
  repetitions: number;
  dueAt: string;
  lastGrade: number | null;
  lapses: number;
}

export interface QueueItem {
  exerciseId: number;
  type: string;
  prompt: string;
  answer: string;
  choices: string[];
  caption: string | null;
  audioAsset: AudioAssetDto | null;
  lessonId: number;
  lessonTitle: string;
  unitId: number;
  unitTitle: string;
  review: ReviewInfo | null;
  new: boolean;
}

export interface QueueResponse {
  items: QueueItem[];
  dueCount: number;
}

export interface StreakDto {
  currentDays: number;
  longestDays: number;
  lastActiveDate: string | null;
}

export interface LessonMastery {
  lessonId: number;
  title: string;
  mastery: number;
  masteredExercises: number;
  totalExercises: number;
}

export interface UnitMastery {
  unitId: number;
  title: string;
  mastery: number;
  lessons: LessonMastery[];
}

export interface Stats {
  attemptsTotal: number;
  exercisesStudied: number;
  exercisesMastered: number;
  averageGrade: number | null;
  dueNow: number;
  sessionsCompleted: number;
  streak: StreakDto;
  unit: UnitMastery | null;
}

export interface AttemptResult {
  attemptId: number;
  exerciseId: number;
  grade: number;
  review: ReviewInfo;
}

export interface CompleteSessionResponse {
  id: number;
  startedAt: string;
  endedAt: string | null;
  variantKey: string | null;
  attemptCount: number;
  averageGrade: number | null;
}

export interface SessionDto {
  id: number;
  lessonId: number;
  variantKey: string | null;
  hintDelaySeconds: number | null;
  startedAt: string;
  exerciseCount: number;
}

export interface ExperimentDto {
  key: string;
  description: string;
  status: "DRAFT" | "RUNNING" | "STOPPED";
  variants: { key: string; weight: number; control: boolean }[];
}

export interface VariantResultsDto {
  key: string;
  control: boolean;
  n: number;
  enoughData: boolean;
  d1ReturnRate: number | null;
  d1PValue: number | null;
  d7ReturnRate: number | null;
  d7PValue: number | null;
  meanSecondAttemptAccuracy: number | null;
  meanItemsPerSession: number | null;
}

export interface ExperimentResultsDto {
  experimentKey: string;
  status: string;
  controlVariantKey: string;
  includeSimulated: boolean;
  variants: VariantResultsDto[];
}

export function normalizeAnswer(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]/gu, "");
}

export function isCorrectAnswer(given: string, expected: string): boolean {
  return normalizeAnswer(given) === normalizeAnswer(expected);
}
