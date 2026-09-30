import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Exercise } from "../api/client";
import { ChoiceExercise } from "./ChoiceExercise";

const exercise: Exercise = {
  id: 2,
  type: "MULTIPLE_CHOICE",
  prompt: 'What does "hola" mean?',
  answer: "Hello",
  choices: ["Hello", "Goodbye", "Please", "Thank you"],
  caption: null,
  audioAsset: null,
};

describe("ChoiceExercise", () => {
  it("groups choices in a fieldset with the prompt as legend", () => {
    render(<ChoiceExercise exercise={exercise} disabled={false} onAnswer={vi.fn()} />);
    const group = screen.getByRole("group", { name: 'What does "hola" mean?' });
    expect(group).toBeInTheDocument();
    expect(screen.getAllByRole("radio")).toHaveLength(4);
  });

  it("every radio is keyboard-operable and labelled", async () => {
    const user = userEvent.setup();
    render(<ChoiceExercise exercise={exercise} disabled={false} onAnswer={vi.fn()} />);

    const radios = screen.getAllByRole("radio");
    await user.tab();
    expect(radios[0]).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(radios[1]).toBeChecked();
    await user.keyboard("{ArrowDown}");
    expect(radios[2]).toBeChecked();
  });

  it("submits the selected choice", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(<ChoiceExercise exercise={exercise} disabled={false} onAnswer={onAnswer} />);

    const check = screen.getByRole("button", { name: "Check" });
    expect(check).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: "Hello" }));
    await user.click(check);

    expect(onAnswer).toHaveBeenCalledTimes(1);
    expect(onAnswer).toHaveBeenCalledWith("Hello", false);
  });

  it("disables the whole group via the disabled prop", () => {
    render(<ChoiceExercise exercise={exercise} disabled onAnswer={vi.fn()} />);
    for (const radio of screen.getAllByRole("radio")) {
      expect(radio).toBeDisabled();
    }
    expect(screen.getByRole("button", { name: "Check" })).toBeDisabled();
  });
});
