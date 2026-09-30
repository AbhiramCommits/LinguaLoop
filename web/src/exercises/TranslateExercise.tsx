import { useState, type FormEvent } from "react";
import type { Exercise } from "../api/client";

export interface RendererProps {
  exercise: Exercise;
  disabled: boolean;
  onAnswer: (answer: string, hintShown: boolean) => void;
}

export function TranslateExercise({ exercise, disabled, onAnswer }: RendererProps) {
  const [value, setValue] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!value.trim() || disabled) {
      return;
    }
    onAnswer(value, false);
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
      <button type="submit" disabled={disabled || !value.trim()}>
        Check
      </button>
    </form>
  );
}
