import { useEffect, useRef, useState, type FormEvent } from "react";
import type { Exercise } from "../api/client";

export interface RendererProps {
  exercise: Exercise;
  disabled: boolean;
  hintDelaySeconds?: number;
  onAnswer: (answer: string, hintShown: boolean) => void;
}

export function useHintTimer(delaySeconds: number): { ready: boolean; remaining: number } {
  const [remaining, setRemaining] = useState(Math.max(0, delaySeconds));
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (delaySeconds <= 0) {
      return;
    }
    timer.current = setInterval(() => {
      setRemaining((value) => Math.max(0, value - 1));
    }, 1000);
    return () => {
      if (timer.current) {
        clearInterval(timer.current);
      }
    };
  }, [delaySeconds]);

  return { ready: remaining === 0, remaining };
}

export function hintPattern(answer: string): string {
  return answer
    .split(/\s+/)
    .map((word) => word.charAt(0) + "_".repeat(Math.max(0, word.length - 1)))
    .join(" ");
}

export function TranslateExercise({ exercise, disabled, onAnswer, hintDelaySeconds = 0 }: RendererProps) {
  const [value, setValue] = useState("");
  const [hintUsed, setHintUsed] = useState(false);
  const hint = useHintTimer(hintDelaySeconds);
  const hintId = `hint-${exercise.id}`;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!value.trim() || disabled) {
      return;
    }
    onAnswer(value, hintUsed);
  };

  return (
    <form onSubmit={submit}>
      <label htmlFor={`answer-${exercise.id}`}>Your translation</label>
      <input
        id={`answer-${exercise.id}`}
        type="text"
        autoComplete="off"
        value={value}
        disabled={disabled}
        onChange={(event) => setValue(event.target.value)}
      />
      <div className="hint-row">
        <button
          type="button"
          className="secondary"
          aria-controls={hintId}
          aria-expanded={hintUsed}
          disabled={disabled || !hint.ready}
          onClick={() => setHintUsed(true)}
        >
          {hint.ready ? "Show hint" : `Hint in ${hint.remaining}s`}
        </button>
        <span id={hintId} className="hint-content">
          {hintUsed ? hintPattern(exercise.answer) : ""}
        </span>
      </div>
      <button type="submit" disabled={disabled || !value.trim()}>
        Check
      </button>
    </form>
  );
}
