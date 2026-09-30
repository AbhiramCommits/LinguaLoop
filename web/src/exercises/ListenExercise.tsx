import { useState, type FormEvent } from "react";
import { type RendererProps } from "./TranslateExercise";

export function ListenExercise({ exercise, disabled, onAnswer }: RendererProps) {
  const [value, setValue] = useState("");
  const [captionsVisible, setCaptionsVisible] = useState(false);
  const captionId = `caption-${exercise.id}`;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!value.trim() || disabled) {
      return;
    }
    onAnswer(value, captionsVisible);
  };

  return (
    <form onSubmit={submit}>
      {exercise.audioAsset ? (
        <audio controls preload="metadata" src={`/api/audio/${exercise.audioAsset.id}`}>
          Audio not supported by your browser.
        </audio>
      ) : (
        <p className="muted">Audio is not available for this exercise yet.</p>
      )}

      <div className="captions-row">
        <button
          type="button"
          className="secondary"
          aria-expanded={captionsVisible}
          aria-controls={captionId}
          onClick={() => setCaptionsVisible((visible) => !visible)}
        >
          {captionsVisible ? "Hide captions" : "Show captions"}
        </button>
        {captionsVisible && (
          <p id={captionId} className="caption">
            {exercise.caption}
          </p>
        )}
      </div>

      <label htmlFor={`answer-${exercise.id}`}>What did you hear?</label>
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
