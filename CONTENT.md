# LinguaLoop content

Content in LinguaLoop is data, not code: anyone can contribute a language
unit without deploying anything. This file records what ships in the repo,
how new content gets contributed, and the licensing rules.

## Seed dataset

The Spanish starter content ("Unidad 1 · Saludos y presentaciones": 1 unit,
3 lessons, 32 exercises) is authored for LinguaLoop by the project and
seeded via `tools/seed.py`. Text content is licensed under the same license
as the repository (MIT).

<!-- AUDIO-BLOCK-START -->
## Audio generation

- Engine: Piper TTS 1.8.0
- Voice: es_ES-mls_10246-low
- Voice license: Multilingual LibriSpeech (MLS), CC BY 4.0 (permits redistribution)
- Voice source: https://huggingface.co/rhasspy/piper-voices
- Generated: 2026-09-30T00:52:43.969176+00:00
- Storage: content-addressed files under `audio/<sha256[:2]>/<sha256>.<ext>`
  (64 kbps Opus + 64 kbps MP3 fallback). Audio files are not committed to git.
- License of the generated clips: derivatives of the voice model, they
  inherit the voice license above.
<!-- AUDIO-BLOCK-END -->

## Contributing a new language unit

Units are bundles in one of two interchangeable formats (see
`docs/authoring.md` for the full non-engineer guide).

### Format (YAML)

```yaml
language:
  code: fr          # ISO-ish: 2-3 lowercase letters, optional -XX region
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
        prompt: Which is hello?
        answer: Bonjour
        choices: [Bonjour, Au revoir, Merci]
      - type: LISTEN
        prompt: Listen and type what you hear.
        answer: Bonjour
        caption: Bonjour          # transcript; audio is generated separately
```

Rules: `TRANSLATE`/`LISTEN` exercises must not define choices;
`MULTIPLE_CHOICE` needs 2–8 choices and the answer must be one of them;
positions are unique per unit/lesson; the import is atomic — one bad row
rejects the whole bundle with line-numbered errors.

### Flow

1. Validate locally:
   `cd tools && uv run -- python import_unit.py --yaml my_unit.yaml --check-only`
2. Dry-run against the API (nothing is persisted):
   `uv run -- python import_unit.py --yaml my_unit.yaml --email you@example.com --password … --dry-run`
3. Commit (re-running the same bundle replaces the unit at that position,
   so fixes are just re-imports):
   `uv run -- python import_unit.py --yaml my_unit.yaml --email … --password …`
4. LISTEN audio: `uv run --group tts -- python audio_pipeline.py --tts`
   generates clips with the Piper voice recorded above; only use voices
   whose license allows redistribution (the pipeline refuses otherwise).

Admins can also use the `/author` page in the web app (form editor with a
live learner preview) and the `/api/admin/*` endpoints. To become an admin
on a deployment, set `APP_ADMIN_EMAILS` (bootstrap) or run
`UPDATE learner SET role='ADMIN' WHERE email='…'`.

### Licensing rules (never violate these)

- Only submit text you wrote yourself or have explicit rights to
  redistribute; state the source for anything adapted.
- Never commit audio files to git; they are generated, content-addressed,
  and recorded here with their engine, voice and license.
- The pipeline refuses TTS voices without a redistributable license
  (CC0 / CC BY / MIT / BSD / Apache-2.0 etc.).
