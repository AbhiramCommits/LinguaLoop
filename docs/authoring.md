# Authoring a language unit (non-engineer guide)

You can add a whole unit of lessons and exercises to LinguaLoop without
touching code or deploying anything. This guide assumes no programming
knowledge beyond editing a text file (or using the web form).

## The three exercise types

| Type              | What the learner does                          | What you write                       |
| ----------------- | ---------------------------------------------- | ------------------------------------ |
| `TRANSLATE`       | Reads a prompt, types a translation            | `prompt`, `answer`                   |
| `MULTIPLE_CHOICE` | Picks the correct answer from a list           | `prompt`, `answer`, `choices` (2–8)  |
| `LISTEN`          | Listens to audio, types what they heard        | `prompt`, `answer`, `caption`        |

- `prompt` is the instruction shown to the learner.
- `answer` is the exact expected answer. Spelling counts, but accents and
  punctuation do not — "buenos dias" matches "Buenos días" automatically.
- For `MULTIPLE_CHOICE`, `answer` must be one of the `choices`.
- For `LISTEN`, `caption` is the transcript. Audio is generated
  automatically afterwards (see below) — you do not record anything.

## The unit bundle

A bundle is one language + one unit + its lessons. Two formats are
accepted; YAML is easiest to read and write.

### YAML

```yaml
language:
  code: fr
  name: French
unit:
  title: Unité 1 · Salutations
  position: 1
lessons:
  - title: Salutations
    position: 1
    exercises:
      - type: TRANSLATE
        prompt: Good morning
        answer: Bonjour
      - type: MULTIPLE_CHOICE
        prompt: Which one means hello?
        answer: Bonjour
        choices: [Bonjour, Au revoir, Merci]
```

### CSV (first row defines the unit, then one row per exercise)

```
language_code,language_name,unit_title,unit_position,lesson,position,type,prompt,answer,choices,caption
fr,French,Unité 1,1,Salutations,1,TRANSLATE,Good morning,Bonjour,,
,,,,"Salutations",1,MULTIPLE_CHOICE,Which one means hello?,Bonjour,"Bonjour;Au revoir;Merci",
```

## Rules that are checked for you

- Language codes look like `fr` or `pt-BR` (no other characters).
- `position` numbers start at 1 and must be unique within the unit.
- Every lesson needs at least one exercise.
- `TRANSLATE` and `LISTEN` exercises must not have `choices`;
  `MULTIPLE_CHOICE` needs 2–8 choices and the answer among them.
- One invalid row rejects the whole bundle — nothing is saved — and every
  error is reported with its line number.

## Steps to publish

1. **Check locally** (no server needed):
   ```
   cd tools
   uv sync
   uv run -- python import_unit.py --yaml my_unit.yaml --check-only
   ```
2. **Dry-run** against the server (shows exactly what would change,
   persists nothing):
   ```
   uv run -- python import_unit.py --yaml my_unit.yaml \
     --email you@example.com --password your-password --dry-run
   ```
3. **Commit it**:
   ```
   uv run -- python import_unit.py --yaml my_unit.yaml \
     --email you@example.com --password your-password
   ```
4. **Fix mistakes by re-importing** — importing the same unit position
   again replaces it, so just edit the file and re-run step 3.

## The /author page

If your account has the admin role, the web app has an `/author` page:
a form-based editor for the same bundle, a live preview of how learners
will see each exercise, drag-and-drop (plus arrow buttons) to reorder
exercises, and the same dry-run/commit flow. Generate the YAML from the
form, or paste your own file in.

## Getting audio for LISTEN exercises

Audio is synthesized, not recorded. After importing a unit with LISTEN
exercises:

```
uv sync --group tts
DATABASE_URL="postgresql://…" uv run --group tts -- python audio_pipeline.py --tts
```

The pipeline generates clips with an open-source Piper voice, transcodes
them to small Opus/MP3 files and links them to the exercises. It only
accepts voices whose license allows redistribution, and it records the
engine, voice and license in `CONTENT.md`.

## Licensing

Only submit text you wrote yourself or have explicit permission to
redistribute. See `CONTENT.md` for the full rules.
