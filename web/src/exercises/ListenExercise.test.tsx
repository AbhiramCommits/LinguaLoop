import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Exercise } from "../api/client";
import { ListenExercise } from "./ListenExercise";

const exercise: Exercise = {
  id: 3,
  type: "LISTEN",
  prompt: "Listen and type what you hear.",
  answer: "Hola, ¿cómo estás?",
  choices: [],
  caption: "Hola, ¿cómo estás?",
  audioAsset: { id: 7, durationMs: 2100, bytes: 12345 },
};

describe("ListenExercise", () => {
  it("plays the audio asset through the API endpoint", () => {
    render(<ListenExercise exercise={exercise} disabled={false} onAnswer={vi.fn()} />);
    const audio = document.querySelector("audio");
    expect(audio).not.toBeNull();
    expect(audio?.getAttribute("src")).toBe("/api/audio/7");
  });

  it("toggles captions with aria-expanded and reveals the transcript", async () => {
    const user = userEvent.setup();
    render(<ListenExercise exercise={exercise} disabled={false} onAnswer={vi.fn()} />);

    const toggle = screen.getByRole("button", { name: "Show captions" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Hola, ¿cómo estás?")).not.toBeInTheDocument();

    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(toggle).toHaveAttribute("aria-controls", "caption-3");
    expect(screen.getByText("Hola, ¿cómo estás?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hide captions" })).toBeInTheDocument();
  });

  it("reports hintShown=true when captions were revealed before answering", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<ListenExercise exercise={exercise} disabled={false} onAnswer={onAnswer} />);

    await user.click(screen.getByRole("button", { name: "Show captions" }));
    await user.type(screen.getByLabelText("What did you hear?"), "Hola, como estas");
    await user.click(screen.getByRole("button", { name: "Check" }));

    expect(onAnswer).toHaveBeenCalledWith("Hola, como estas", true);
  });

  it("reports hintShown=false when captions were never revealed", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<ListenExercise exercise={exercise} disabled={false} onAnswer={onAnswer} />);

    await user.type(screen.getByLabelText("What did you hear?"), "Hola");
    await user.click(screen.getByRole("button", { name: "Check" }));

    expect(onAnswer).toHaveBeenCalledWith("Hola", false);
  });
});
