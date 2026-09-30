import { useState } from "react";
import { postImport, type Exercise, type ImportResponse } from "../api/client";
import { ChoiceExercise } from "../exercises/ChoiceExercise";
import { ListenExercise } from "../exercises/ListenExercise";
import { TranslateExercise } from "../exercises/TranslateExercise";

interface EditorExercise {
  key: number;
  type: Exercise["type"];
  prompt: string;
  answer: string;
  choices: string;
  caption: string;
}

interface EditorLesson {
  key: number;
  title: string;
  position: number;
  exercises: EditorExercise[];
}

let nextKey = 1;
const newKey = () => nextKey++;

function toExercise(exercise: EditorExercise): Exercise {
  return {
    id: exercise.key,
    type: exercise.type,
    prompt: exercise.prompt,
    answer: exercise.answer,
    choices:
      exercise.type === "MULTIPLE_CHOICE"
        ? exercise.choices.split(/[;\n]/).map((c) => c.trim()).filter(Boolean)
        : [],
    caption: exercise.type === "LISTEN" ? exercise.caption || null : null,
    audioAsset: null,
  };
}

function yamlQuote(value: string): string {
  if (/^[a-zA-Z0-9 _\-·.,!?¿¡'"()/]+$/.test(value) && !value.startsWith(" ") && !value.endsWith(" ")) {
    return value;
  }
  return `'${value.replace(/'/g, "''")}'`;
}

export function bundleToYaml(bundle: {
  languageCode: string;
  languageName: string;
  unitTitle: string;
  unitPosition: number;
  lessons: EditorLesson[];
}): string {
  const lines: string[] = [];
  lines.push("language:");
  lines.push(`  code: ${yamlQuote(bundle.languageCode)}`);
  lines.push(`  name: ${yamlQuote(bundle.languageName)}`);
  lines.push("unit:");
  lines.push(`  title: ${yamlQuote(bundle.unitTitle)}`);
  lines.push(`  position: ${bundle.unitPosition}`);
  lines.push("lessons:");
  bundle.lessons.forEach((lesson) => {
    lines.push(`  - title: ${yamlQuote(lesson.title)}`);
    lines.push(`    position: ${lesson.position}`);
    lines.push("    exercises:");
    lesson.exercises.forEach((exercise) => {
      lines.push(`      - type: ${exercise.type}`);
      lines.push(`        prompt: ${yamlQuote(exercise.prompt)}`);
      lines.push(`        answer: ${yamlQuote(exercise.answer)}`);
      if (exercise.type === "MULTIPLE_CHOICE" && exercise.choices.trim()) {
        const choices = exercise.choices
          .split(/[;\n]/)
          .map((c) => c.trim())
          .filter(Boolean)
          .map(yamlQuote);
        lines.push(`        choices: [${choices.join(", ")}]`);
      }
      if (exercise.type === "LISTEN" && exercise.caption.trim()) {
        lines.push(`        caption: ${yamlQuote(exercise.caption.trim())}`);
      }
    });
  });
  return lines.join("\n") + "\n";
}

export function AuthorScreen() {
  const [languageCode, setLanguageCode] = useState("");
  const [languageName, setLanguageName] = useState("");
  const [unitTitle, setUnitTitle] = useState("");
  const [unitPosition, setUnitPosition] = useState(1);
  const [lessons, setLessons] = useState<EditorLesson[]>([
    { key: newKey(), title: "", position: 1, exercises: [] },
  ]);
  const [selected, setSelected] = useState<{ lessonKey: number; exerciseKey: number } | null>(null);
  const [yamlText, setYamlText] = useState("");
  const [format, setFormat] = useState<"yaml" | "csv">("yaml");
  const [plan, setPlan] = useState<ImportResponse | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  const updateExercise = (lessonKey: number, exerciseKey: number, patch: Partial<EditorExercise>) => {
    setLessons((current) =>
      current.map((lesson) =>
        lesson.key === lessonKey
          ? {
              ...lesson,
              exercises: lesson.exercises.map((exercise) =>
                exercise.key === exerciseKey ? { ...exercise, ...patch } : exercise,
              ),
            }
          : lesson,
      ),
    );
  };

  const moveExercise = (lessonKey: number, exerciseKey: number, direction: -1 | 1) => {
    setLessons((current) =>
      current.map((lesson) => {
        if (lesson.key !== lessonKey) {
          return lesson;
        }
        const index = lesson.exercises.findIndex((exercise) => exercise.key === exerciseKey);
        const target = index + direction;
        if (index < 0 || target < 0 || target >= lesson.exercises.length) {
          return lesson;
        }
        const exercises = [...lesson.exercises];
        [exercises[index], exercises[target]] = [exercises[target], exercises[index]];
        return { ...lesson, exercises };
      }),
    );
  };

  const reorderExercise = (lessonKey: number, from: number, to: number) => {
    setLessons((current) =>
      current.map((lesson) => {
        if (lesson.key !== lessonKey || from === to) {
          return lesson;
        }
        const exercises = [...lesson.exercises];
        const [moved] = exercises.splice(from, 1);
        exercises.splice(to, 0, moved);
        return { ...lesson, exercises };
      }),
    );
    setDragIndex(null);
  };

  const bundle = { languageCode, languageName, unitTitle, unitPosition, lessons };

  const runImport = async (dryRun: boolean) => {
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const content = format === "yaml" ? bundleToYaml(bundle) : yamlText;
      const response = await postImport(content, format, dryRun);
      setPlan(response);
      if (dryRun) {
        setStatus("Dry run complete — nothing was persisted.");
      } else {
        setStatus(
          `Imported: ${response.lessonCount} lesson(s), ${response.exerciseCount} exercise(s) — unit ${response.unitAction.toLowerCase()}.`,
        );
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
      setPlan(null);
    } finally {
      setBusy(false);
    }
  };

  const generateYaml = () => {
    const generated = bundleToYaml(bundle);
    setYamlText(generated);
    setFormat("yaml");
  };

  const previewExercise = selected
    ? lessons.find((lesson) => lesson.key === selected.lessonKey)?.exercises.find(
        (exercise) => exercise.key === selected.exerciseKey,
      )
    : undefined;

  return (
    <section aria-labelledby="author-heading">
      <h2 id="author-heading">Lesson authoring</h2>

      <section aria-labelledby="bundle-heading">
        <h2 id="bundle-heading">1 · Language &amp; unit</h2>
        <div className="author-grid">
          <label>
            Language code (e.g. <code>fr</code>, <code>pt-BR</code>)
            <input
              value={languageCode}
              onChange={(event) => setLanguageCode(event.target.value)}
              placeholder="fr"
              maxLength={6}
            />
          </label>
          <label>
            Language name
            <input value={languageName} onChange={(event) => setLanguageName(event.target.value)} />
          </label>
          <label>
            Unit title
            <input value={unitTitle} onChange={(event) => setUnitTitle(event.target.value)} />
          </label>
          <label>
            Unit position
            <input
              type="number"
              min={1}
              value={unitPosition}
              onChange={(event) => setUnitPosition(Number(event.target.value))}
            />
          </label>
        </div>
      </section>

      <section aria-labelledby="lessons-heading">
        <h2 id="lessons-heading">2 · Lessons</h2>
        {lessons.map((lesson) => (
          <div className="author-lesson" key={lesson.key}>
            <div className="author-grid">
              <label>
                Lesson title
                <input
                  value={lesson.title}
                  onChange={(event) =>
                    setLessons((current) =>
                      current.map((entry) =>
                        entry.key === lesson.key ? { ...entry, title: event.target.value } : entry,
                      ),
                    )
                  }
                />
              </label>
              <label>
                Position
                <input
                  type="number"
                  min={1}
                  value={lesson.position}
                  onChange={(event) =>
                    setLessons((current) =>
                      current.map((entry) =>
                        entry.key === lesson.key
                          ? { ...entry, position: Number(event.target.value) }
                          : entry,
                      ),
                    )
                  }
                />
              </label>
              <button
                type="button"
                className="secondary"
                onClick={() =>
                  setLessons((current) =>
                    current.map((entry) =>
                      entry.key === lesson.key
                        ? {
                            ...entry,
                            exercises: [
                              ...entry.exercises,
                              { key: newKey(), type: "TRANSLATE", prompt: "", answer: "", choices: "", caption: "" },
                            ],
                          }
                        : entry,
                    ),
                  )
                }
              >
                Add exercise
              </button>
            </div>

            <ol className="author-exercise-list">
              {lesson.exercises.map((exercise, index) => (
                <li
                  key={exercise.key}
                  draggable
                  onDragStart={() => setDragIndex(index)}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() => reorderExercise(lesson.key, dragIndex ?? index, index)}
                  className={dragIndex === index ? "dragging" : ""}
                >
                  <div className="author-exercise-header">
                    <span className="muted" aria-hidden="true">
                      ⠿ drag
                    </span>
                    <strong>
                      {index + 1}. {exercise.type}
                    </strong>
                    <div className="author-exercise-actions">
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Move exercise ${index + 1} up`}
                        disabled={index === 0}
                        onClick={() => moveExercise(lesson.key, exercise.key, -1)}
                      >
                        ↑
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Move exercise ${index + 1} down`}
                        disabled={index === lesson.exercises.length - 1}
                        onClick={() => moveExercise(lesson.key, exercise.key, 1)}
                      >
                        ↓
                      </button>
                      <button type="button" className="secondary" onClick={() => setSelected({ lessonKey: lesson.key, exerciseKey: exercise.key })}>
                        Preview
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Remove exercise ${index + 1}`}
                        onClick={() =>
                          setLessons((current) =>
                            current.map((entry) =>
                              entry.key === lesson.key
                                ? {
                                    ...entry,
                                    exercises: entry.exercises.filter((candidate) => candidate.key !== exercise.key),
                                  }
                                : entry,
                            ),
                          )
                        }
                      >
                        ✕
                      </button>
                    </div>
                  </div>

                  <div className="author-grid">
                    <label>
                      Type
                      <select
                        value={exercise.type}
                        onChange={(event) =>
                          updateExercise(lesson.key, exercise.key, {
                            type: event.target.value as Exercise["type"],
                          })
                        }
                      >
                        <option value="TRANSLATE">TRANSLATE</option>
                        <option value="MULTIPLE_CHOICE">MULTIPLE_CHOICE</option>
                        <option value="LISTEN">LISTEN</option>
                      </select>
                    </label>
                    <label>
                      Prompt
                      <input
                        value={exercise.prompt}
                        onChange={(event) =>
                          updateExercise(lesson.key, exercise.key, { prompt: event.target.value })
                        }
                      />
                    </label>
                    <label>
                      Answer
                      <input
                        value={exercise.answer}
                        onChange={(event) =>
                          updateExercise(lesson.key, exercise.key, { answer: event.target.value })
                        }
                      />
                    </label>
                  </div>
                  {exercise.type === "MULTIPLE_CHOICE" && (
                    <label>
                      Choices (one per line; the answer must be among them)
                      <textarea
                        value={exercise.choices}
                        onChange={(event) =>
                          updateExercise(lesson.key, exercise.key, { choices: event.target.value })
                        }
                        rows={3}
                      />
                    </label>
                  )}
                  {exercise.type === "LISTEN" && (
                    <label>
                      Caption (the transcript shown to learners)
                      <input
                        value={exercise.caption}
                        onChange={(event) =>
                          updateExercise(lesson.key, exercise.key, { caption: event.target.value })
                        }
                      />
                    </label>
                  )}
                </li>
              ))}
            </ol>
          </div>
        ))}
        <button
          type="button"
          className="secondary"
          onClick={() =>
            setLessons((current) => [
              ...current,
              { key: newKey(), title: "", position: current.length + 1, exercises: [] },
            ])
          }
        >
          Add lesson
        </button>
      </section>

      {previewExercise && (
        <section aria-labelledby="preview-heading">
          <h2 id="preview-heading">3 · Learner preview</h2>
          <p className="muted">Exactly how learners will see this exercise:</p>
          {previewExercise.type === "MULTIPLE_CHOICE" && (
            <ChoiceExercise exercise={toExercise(previewExercise)} disabled={false} onAnswer={() => undefined} />
          )}
          {previewExercise.type === "LISTEN" && (
            <ListenExercise exercise={toExercise(previewExercise)} disabled={false} onAnswer={() => undefined} />
          )}
          {previewExercise.type === "TRANSLATE" && (
            <TranslateExercise exercise={toExercise(previewExercise)} disabled={false} onAnswer={() => undefined} />
          )}
        </section>
      )}

      <section aria-labelledby="import-heading">
        <h2 id="import-heading">4 · Import</h2>
        <div className="author-import-row">
          <button type="button" onClick={generateYaml}>
            Generate YAML from the editor
          </button>
          <label>
            Format
            <select value={format} onChange={(event) => setFormat(event.target.value as "yaml" | "csv")}>
              <option value="yaml">yaml</option>
              <option value="csv">csv</option>
            </select>
          </label>
        </div>
        <textarea
          aria-label="Import bundle"
          value={yamlText}
          onChange={(event) => setYamlText(event.target.value)}
          rows={12}
        />
        <div className="author-import-row">
          <button type="button" className="secondary" disabled={busy} onClick={() => runImport(true)}>
            {busy ? "Working…" : "Validate (dry run)"}
          </button>
          <button type="button" disabled={busy} onClick={() => runImport(false)}>
            {busy ? "Working…" : "Commit import"}
          </button>
        </div>
        <div className="author-status" role="status" aria-live="polite">
          {status}
        </div>
        {error && (
          <pre className="author-error" role="alert" aria-live="assertive">
            {error}
          </pre>
        )}
        {plan && (
          <div className="author-plan">
            <h3>{plan.dryRun ? "What would change" : "Imported"}</h3>
            <ul>
              <li>
                Language <code>{plan.languageCode}</code>: {plan.languageAction.toLowerCase()}
              </li>
              <li>
                Unit “{plan.unitTitle}”: {plan.unitAction.toLowerCase()}
                {plan.previousExerciseCount > 0 && ` (replacing ${plan.previousExerciseCount} exercises)`}
              </li>
              {plan.lessons.map((lesson) => (
                <li key={`${lesson.position}-${lesson.title}`}>
                  {lesson.title}: {lesson.exerciseCount} exercise(s)
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
    </section>
  );
}
