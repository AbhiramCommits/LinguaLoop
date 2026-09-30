import { apiFetch, type Lesson, type Unit } from "../api/client";

/**
 * Best-effort prefetch of the next lesson's JSON and its audio assets so a
 * learner can keep studying if connectivity drops. The service worker caches
 * lesson JSON (NetworkFirst) and audio (CacheFirst, immutable).
 */
export async function prefetchNextLesson(unitId: number, currentLessonId: number): Promise<void> {
  try {
    const unit = await apiFetch<Unit>(`/api/units/${unitId}`);
    const index = unit.lessons.findIndex((lesson) => lesson.id === currentLessonId);
    const next = index >= 0 ? unit.lessons[index + 1] : undefined;
    if (!next) {
      return;
    }
    const lesson = await apiFetch<Lesson>(`/api/lessons/${next.id}`);
    for (const exercise of lesson.exercises) {
      if (exercise.audioAsset) {
        void fetch(`/api/audio/${exercise.audioAsset.id}`, { cache: "force-cache" });
      }
    }
  } catch {
    // Prefetching is best-effort; never surface errors to the learner.
  }
}
