import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ApiError,
  NetworkError,
  apiFetch,
  isCorrectAnswer,
  type AttemptResult,
  type CompleteSessionResponse,
  type SessionDto,
} from "../api/client";
import { useLesson, useStartSession } from "../api/queries";
import { ExerciseCard, type Feedback } from "../exercises/ExerciseCard";
import { countPending, enqueueOp } from "../offline/db";
import { notifyQueued } from "../offline/status";

interface AttemptVariables {
  sessionId: number;
  exerciseId: number;
  grade: number;
  latencyMs: number;
  hintShown: boolean;
  correct: boolean;
}

interface AnsweredState {
  grade: number;
  correct: boolean;
  status: "submitting" | "success" | "queued";
}

export function LessonScreen() {
  const { lessonId: lessonIdParam } = useParams();
  const lessonId = Number(lessonIdParam);
  const navigate = useNavigate();

  const lesson = useLesson(Number.isNaN(lessonId) ? null : lessonId);
  const startSession = useStartSession();

  const [session, setSession] = useState<SessionDto | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answered, setAnswered] = useState<Record<number, AnsweredState>>({});
  const [error, setError] = useState<string | null>(null);
  const latencyStartedAt = useRef<number>(performance.now());

  const exercises = lesson.data?.exercises ?? [];

  useEffect(() => {
    if (lesson.data && !session && !startSession.isPending) {
      startSession.mutate(lesson.data.id, {
        onSuccess: setSession,
        onError: (err) =>
          setError(err instanceof Error ? err.message : "Could not start this lesson"),
      });
    }
  }, [lesson.data, session, startSession]);

  useEffect(() => {
    latencyStartedAt.current = performance.now();
  }, [currentIndex]);

  const attempt = useMutation({
    mutationFn: (vars: AttemptVariables) =>
      apiFetch<AttemptResult>(`/api/sessions/${vars.sessionId}/attempts`, {
        method: "POST",
        body: JSON.stringify({
          exerciseId: vars.exerciseId,
          grade: vars.grade,
          latencyMs: vars.latencyMs,
          hintShown: vars.hintShown,
        }),
      }),
    onMutate: (vars) => {
      setAnswered((previous) => ({
        ...previous,
        [vars.exerciseId]: { grade: vars.grade, correct: vars.correct, status: "submitting" },
      }));
    },
    onSuccess: (_result, vars) => {
      setAnswered((previous) => ({
        ...previous,
        [vars.exerciseId]: { grade: vars.grade, correct: vars.correct, status: "success" },
      }));
    },
    onError: async (err, vars) => {
      if (err instanceof NetworkError || !navigator.onLine) {
        await enqueueOp({
          kind: "attempt",
          sessionId: vars.sessionId,
          exerciseId: vars.exerciseId,
          grade: vars.grade,
          latencyMs: vars.latencyMs,
          hintShown: vars.hintShown,
        });
        notifyQueued(await countPending());
        setAnswered((previous) => ({
          ...previous,
          [vars.exerciseId]: { grade: vars.grade, correct: vars.correct, status: "queued" },
        }));
        return;
      }
      // Roll back the optimistic answer on real server errors.
      setAnswered((previous) => {
        const next = { ...previous };
        delete next[vars.exerciseId];
        return next;
      });
      setError(err instanceof ApiError ? err.message : "Could not save your answer");
    },
  });

  const complete = useMutation({
    mutationFn: () => apiFetch<CompleteSessionResponse>(`/api/sessions/${session!.id}/complete`, {
      method: "POST",
    }),
    onSuccess: (summary) => {
      navigate(`/sessions/${summary.id}/summary`, { state: { summary, lesson: lesson.data } });
    },
    onError: async (err) => {
      if (!session) return;
      if (err instanceof NetworkError || !navigator.onLine) {
        await enqueueOp({ kind: "complete", sessionId: session.id });
        navigate(`/sessions/${session.id}/summary`, {
          state: { summary: localSummary(), lesson: lesson.data, offline: true },
        });
        return;
      }
      setError(err instanceof ApiError ? err.message : "Could not complete the session");
    },
  });

  const currentExercise = exercises[currentIndex];
  const answeredCount = Object.keys(answered).length;
  const allAnswered = exercises.length > 0 && answeredCount === exercises.length;

  const localSummary = (): CompleteSessionResponse => {
    const grades = Object.values(answered).map((entry) => entry.grade);
    const average = grades.length
      ? grades.reduce((sum, grade) => sum + grade, 0) / grades.length
      : null;
    return {
      id: session!.id,
      startedAt: session!.startedAt,
      endedAt: null,
      variantKey: session!.variantKey,
      attemptCount: grades.length,
      averageGrade: average,
    };
  };

  const handleAnswer = (exerciseId: number, answer: string, hintShown: boolean) => {
    if (!session || answered[exerciseId]) {
      return;
    }
    const exercise = exercises.find((entry) => entry.id === exerciseId);
    if (!exercise) {
      return;
    }
    const correct = isCorrectAnswer(answer, exercise.answer);
    const grade = correct ? 5 : 2;
    const latencyMs = Math.round(performance.now() - latencyStartedAt.current);
    setError(null);
    attempt.mutate({
      sessionId: session.id,
      exerciseId,
      grade,
      latencyMs,
      hintShown,
      correct,
    });
  };

  const feedbackFor = useMemo(
    () => (exerciseId: number): Feedback | null => {
      const entry = answered[exerciseId];
      if (!entry) {
        return null;
      }
      return { correct: entry.correct, status: entry.status };
    },
    [answered],
  );

  if (lesson.isLoading || !lesson.data) {
    return <section aria-busy="true"><p className="muted">Loading lesson…</p></section>;
  }

  if (lesson.isError) {
    return (
      <section>
        <p className="error" role="alert">
          {lesson.error instanceof Error ? lesson.error.message : "Could not load this lesson"}
        </p>
        <Link to="/">Back to home</Link>
      </section>
    );
  }

  return (
    <section aria-labelledby="lesson-title">
      <h2 id="lesson-title">{lesson.data.title}</h2>
      <p className="muted">
        Exercise {currentIndex + 1} of {exercises.length}
      </p>
      <div
        className="progressbar"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={exercises.length}
        aria-valuenow={answeredCount}
        aria-label={`Lesson progress: ${answeredCount} of ${exercises.length} exercises answered`}
      >
        <div
          className="progressbar-fill"
          style={{ width: `${exercises.length ? (answeredCount / exercises.length) * 100 : 0}%` }}
        />
      </div>

      {error && (
        <p className="error" role="alert" aria-live="assertive">
          {error}
        </p>
      )}

      {currentExercise && (
        <ExerciseCard
          exercise={currentExercise}
          feedback={feedbackFor(currentExercise.id)}
          disabled={!session || attempt.isPending}
          hintDelaySeconds={session?.hintDelaySeconds ?? 0}
          onAnswer={(answer, hintShown) => handleAnswer(currentExercise.id, answer, hintShown)}
        />
      )}

      <nav className="player-nav" aria-label="Lesson navigation">
        <button
          type="button"
          className="secondary"
          disabled={currentIndex === 0}
          onClick={() => setCurrentIndex((index) => Math.max(0, index - 1))}
        >
          Previous
        </button>
        <button
          type="button"
          disabled={currentIndex >= exercises.length - 1}
          onClick={() => setCurrentIndex((index) => Math.min(exercises.length - 1, index + 1))}
        >
          Next
        </button>
        <button
          type="button"
          className="complete-button"
          disabled={!session || !allAnswered || complete.isPending}
          onClick={() => complete.mutate()}
        >
          Complete session
        </button>
      </nav>
      {!allAnswered && (
        <p className="muted">Answer every exercise to complete the session.</p>
      )}
    </section>
  );
}
