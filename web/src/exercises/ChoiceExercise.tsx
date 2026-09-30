import { useState, type FormEvent } from "react";
import { type RendererProps } from "./TranslateExercise";

export function ChoiceExercise({ exercise, disabled, onAnswer }: RendererProps) {
  const [selected, setSelected] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!selected || disabled) {
      return;
    }
    onAnswer(selected, false);
  };

  return (
    <form onSubmit={submit}>
      <fieldset disabled={disabled}>
        <legend>{exercise.prompt}</legend>
        {exercise.choices.map((choice) => {
          const id = `choice-${exercise.id}-${choice}`;
          return (
            <div className="choice-row" key={choice}>
              <input
                id={id}
                type="radio"
                name={`choices-${exercise.id}`}
                value={choice}
                checked={selected === choice}
                onChange={() => setSelected(choice)}
              />
              <label htmlFor={id}>{choice}</label>
            </div>
          );
        })}
      </fieldset>
      <button type="submit" disabled={disabled || !selected}>
        Check
      </button>
    </form>
  );
}
