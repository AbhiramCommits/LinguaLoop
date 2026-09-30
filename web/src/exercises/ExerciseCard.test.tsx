import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Exercise } from "../api/client";
import { ExerciseCard } from "./ExerciseCard";

const exercise: Exercise = {
  id: 4,
  type: "TRANSLATE",
  prompt: "Good night",
  answer: "Buenas noches",
  choices: [],
  caption: null,
  audioAsset: null,
};

describe("ExerciseCard feedback", () => {
  it("announces correctness via an aria-live status region (not colour alone)", () => {
    render(
      <ExerciseCard
        exercise={exercise}
        feedback={{ correct: true, status: "success" }}
        disabled
        onAnswer={vi.fn()}
      />,
    );
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Correct");
  });

  it("announces the correct answer when the learner was wrong", () => {
    render(
      <ExerciseCard
        exercise={exercise}
        feedback={{ correct: false, status: "success" }}
        disabled
        onAnswer={vi.fn()}
      />,
    );
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Incorrect");
    expect(status).toHaveTextContent("Buenas noches");
  });

  it("marks offline-queued answers explicitly", () => {
    render(
      <ExerciseCard
        exercise={exercise}
        feedback={{ correct: true, status: "queued" }}
        disabled
        onAnswer={vi.fn()}
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent("saved offline");
  });
});
