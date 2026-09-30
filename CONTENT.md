# LinguaLoop content

## Seed dataset

The Spanish starter content ("Unidad 1 · Saludos y presentaciones": 1 unit,
3 lessons, 32 exercises) is authored for LinguaLoop by the project and
seeded via `tools/seed.py`. The text content is licensed under the same
license as the LinguaLoop repository.

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
