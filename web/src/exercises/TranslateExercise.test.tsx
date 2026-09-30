import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Exercise } from "../api/client";
import { TranslateExercise } from "./TranslateExercise";

const exercise: Exercise = {
  id: 1,
  type: "TRANSLATE",
  prompt: "Good morning",
  answer: "Buenos días",
  choices: [],
  caption: null,
  audioAsset: null,
};

describe("TranslateExercise", () => {
  it("submits the typed answer on Enter (keyboard only)", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<TranslateExercise exercise={exercise} disabled={false} onAnswer={onAnswer} />);

    const input = screen.getByLabelText("Your translation");
    await user.type(input, "Buenos días{Enter}");

    expect(onAnswer).toHaveBeenCalledTimes(1);
    expect(onAnswer).toHaveBeenCalledWith("Buenos días", false);
  });

  it("submits via the Check button and ignores empty input", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<TranslateExercise exercise={exercise} disabled={false} onAnswer={onAnswer} />);

    const button = screen.getByRole("button", { name: "Check" });
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("Your translation"), "Hola");
    await user.click(button);

    expect(onAnswer).toHaveBeenCalledWith("Hola", false);
  });

  it("disables the input once answered", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<TranslateExercise exercise={exercise} disabled onAnswer={onAnswer} />);
    expect(screen.getByLabelText("Your translation")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Check" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Check" }));
    expect(onAnswer).not.toHaveBeenCalled();
  });
});
