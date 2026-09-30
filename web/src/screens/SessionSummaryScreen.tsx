import { useEffect, useRef } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import type { CompleteSessionResponse, Lesson } from "../api/client";
import { useStats } from "../api/queries";
import { prefetchNextLesson } from "../offline/prefetch";

interface SummaryLocationState {
  summary: CompleteSessionResponse;
  lesson: Lesson | undefined;
  offline?: boolean;
}

export function SessionSummaryScreen() {
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state ?? null) as SummaryLocationState | null;
  const stats = useStats();
  const prefetched = useRef(false);

  const lesson = state?.lesson;

  useEffect(() => {
    if (state && !prefetched.current && lesson) {
      prefetched.current = true;
      void prefetchNextLesson(lesson.unitId, lesson.id);
    }
  }, [state, lesson]);

  useEffect(() => {
    if (!state) {
      navigate("/", { replace: true });
    }
  }, [state, navigate]);

  if (!state) {
    return null;
  }

  const { summary, offline } = state;
  const average =
    summary.averageGrade != null ? `${Math.round(summary.averageGrade * 100) / 100}` : "—";

  return (
    <section aria-labelledby="summary-heading">
      <h2 id="summary-heading">Session complete</h2>
      <div role="status" aria-live="polite">
        {offline ? (
          <p className="feedback-queued">
            You finished offline — this session will sync when you are back online.
          </p>
        ) : (
          <p>
            <span className="feedback-correct" aria-hidden="true">
              ✓
            </span>{" "}
            Session saved.
          </p>
        )}
      </div>
      <ul className="summary-list">
        <li>Exercises attempted: {summary.attemptCount}</li>
        <li>Average grade: {average} / 5</li>
        {summary.variantKey && <li>Variant: {summary.variantKey}</li>}
        {stats.data?.streak && (
          <li>
            Streak: {stats.data.streak.currentDays} day
            {stats.data.streak.currentDays === 1 ? "" : "s"}
          </li>
        )}
      </ul>
      <Link to="/" className="button-link">
        Back to home
      </Link>
    </section>
  );
}
