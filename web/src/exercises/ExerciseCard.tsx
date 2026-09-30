import type { Exercise } from "../api/client";
import { ChoiceExercise } from "./ChoiceExercise";
import { ListenExercise } from "./ListenExercise";
import { TranslateExercise } from "./TranslateExercise";

export type AnswerStatus = "submitting" | "success" | "queued";

export interface Feedback {
  correct: boolean;
  status: AnswerStatus;
}

interface ExerciseCardProps {
  exercise: Exercise;
  feedback: Feedback | null;
  disabled: boolean;
  onAnswer: (answer: string, hintShown: boolean) => void;
}

function renderRenderer(exercise: Exercise, disabled: boolean, onAnswer: ExerciseCardProps["onAnswer"]) {
  switch (exercise.type) {
    case "MULTIPLE_CHOICE":
      return <ChoiceExercise exercise={exercise} disabled={disabled} onAnswer={onAnswer} />;
    case "LISTEN":
      return <ListenExercise exercise={exercise} disabled={disabled} onAnswer={onAnswer} />;
    case "TRANSLATE":
    default:
      return <TranslateExercise exercise={exercise} disabled={disabled} onAnswer={onAnswer} />;
  }
}

export function ExerciseCard({ exercise, feedback, disabled, onAnswer }: ExerciseCardProps) {
  return (
    <div className="exercise-card">
      <h3>
        <span className={`type-badge type-${exercise.type.toLowerCase()}`}>{exercise.type}</span>{" "}
        {exercise.type === "MULTIPLE_CHOICE" ? exercise.prompt : null}
      </h3>
      {exercise.type !== "MULTIPLE_CHOICE" && <p className="prompt">{exercise.prompt}</p>}

      {renderRenderer(exercise, disabled || feedback !== null, onAnswer)}

      <div className="feedback" role="status" aria-live="polite" aria-atomic="true">
        {feedback && (
          <>
            {feedback.correct ? (
              <span className="feedback-correct">
                <span aria-hidden="true">✓</span> Correct
              </span>
            ) : (
              <span className="feedback-incorrect">
                <span aria-hidden="true">✗</span> Incorrect — the correct answer is{" "}
                <strong>{exercise.answer}</strong>
              </span>
            )}
            {feedback.status === "queued" && (
              <span className="feedback-queued"> (saved offline, will sync)</span>
            )}
            {feedback.status === "submitting" && <span> Submitting…</span>}
          </>
        )}
      </div>
    </div>
  );
}
