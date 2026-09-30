#!/usr/bin/env python3
"""LinguaLoop audio pipeline: normalize, transcode and register audio assets.

Two modes:

1. Manifest mode (default)
   Consumes a CSV manifest (exercise_id, source_file, transcript) plus a
   directory of source WAV/MP3 files. Each clip is loudness-normalized
   (EBU R128, -16 LUFS) and transcoded to two low-bandwidth variants with
   ffmpeg: 64 kbps Opus (.opus) and 64 kbps MP3 (.mp3). Outputs are written
   to a content-addressed path::

       audio/{sha256[:2]}/{sha256}.{ext}

   where sha256 is the hash of the source file. Duration and byte size are
   recorded and one audio_asset row per unique clip is inserted, then linked
   to the exercise. Re-running on unchanged input produces no new assets.

   Usage:
       uv run -- python audio_pipeline.py \\
           --manifest clips.csv --sources recordings \\
           --output-dir ../audio \\
           --database-url postgresql://user:pass@localhost:5432/lingualoop

2. TTS mode (--tts)
   Generates the Spanish LISTEN clips with Piper (open-source TTS), builds
   the manifest from the seed exercises' transcripts in the database, and
   runs the pipeline. The TTS engine, voice and voice license are recorded
   in CONTENT.md; generation refuses to run with a voice whose license does
   not allow redistribution.

   Usage:
       uv sync --group tts
       uv run --group tts -- python audio_pipeline.py --tts \\
           --output-dir ../audio \\
           --database-url postgresql://user:pass@localhost:5432/lingualoop

Refuses to proceed if ffmpeg is missing.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import wave
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import psycopg

OPUS_ARGS = [
    "ffmpeg", "-y", "-i", "{source}",
    "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
    "-ar", "24000", "-c:a", "libopus", "-b:a", "64k", "{out}",
]
MP3_ARGS = [
    "ffmpeg", "-y", "-i", "{source}",
    "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
    "-ar", "24000", "-c:a", "libmp3lame", "-b:a", "64k", "{out}",
]

REDISTRIBUTABLE_LICENSES = (
    "CC0", "CC BY", "CC-BY", "MIT", "APACHE", "BSD", "MPL", "PUBLIC DOMAIN", "UNLICENSE", "ZLIB", "ISC",
)
NON_REDISTRIBUTABLE_MARKERS = (
    "NONCOMMERCIAL", "NON-COMMERCIAL", "NONCOMMERCIAL", "-NC", "NODERIV", "NO-DERIV", "-ND",
    "ALL RIGHTS RESERVED",
)
# Piper voice configs do not always embed a license field; these voices are
# derived from datasets with well-known redistributable licenses.
KNOWN_VOICE_LICENSES = {
    "es_ES-mls_10246-low": ("Multilingual LibriSpeech (MLS), CC BY 4.0", "https://openslr.org/94/"),
    "es_ES-mls_9972-low": ("Multilingual LibriSpeech (MLS), CC BY 4.0", "https://openslr.org/94/"),
}
DEFAULT_VOICE = "es_ES-mls_10246-low"
DEFAULT_DATA_DIR = Path.home() / ".local" / "share" / "piper-voices"


class PipelineError(RuntimeError):
    pass


class FfmpegMissingError(PipelineError):
    pass


@dataclass
class ManifestRow:
    exercise_id: int
    source_file: str
    transcript: str


@dataclass
class PipelineReport:
    created: int = 0
    reused: int = 0
    missing_source: int = 0
    missing_exercise: int = 0
    details: list[str] = field(default_factory=list)


def require_ffmpeg() -> None:
    if shutil.which("ffmpeg") is None:
        raise FfmpegMissingError(
            "ffmpeg is required but was not found on PATH. "
            "Install it first (e.g. 'brew install ffmpeg' or 'apt install ffmpeg')."
        )


def run_cmd(args: list[str]) -> subprocess.CompletedProcess:
    proc = subprocess.run(args, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        raise PipelineError(
            f"Command failed: {' '.join(args)}\n{proc.stderr[-800:]}"
        )
    return proc


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def content_addressed_paths(source: str | Path, output_dir: str | Path) -> tuple[Path, Path, str, str]:
    sha = sha256_file(source)
    rel_opus = f"audio/{sha[:2]}/{sha}.opus"
    rel_mp3 = f"audio/{sha[:2]}/{sha}.mp3"
    return Path(output_dir) / rel_opus, Path(output_dir) / rel_mp3, rel_opus, rel_mp3


def transcode(source: str | Path, out_opus: Path, out_mp3: Path, run: callable = run_cmd) -> None:
    out_opus.parent.mkdir(parents=True, exist_ok=True)
    run([arg.format(source=str(source), out=str(out_opus)) for arg in OPUS_ARGS])
    run([arg.format(source=str(source), out=str(out_mp3)) for arg in MP3_ARGS])


def probe_duration_ms(path: str | Path, run: callable = run_cmd) -> int:
    ffprobe = shutil.which("ffprobe")
    if ffprobe:
        proc = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True, check=False,
        )
        if proc.returncode == 0 and proc.stdout.strip():
            try:
                return int(round(float(proc.stdout.strip()) * 1000))
            except ValueError:
                pass
    proc = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(path)], capture_output=True, text=True, check=False
    )
    match = re.search(r"Duration: (\d+):(\d+):(\d+(?:\.\d+)?)", proc.stderr)
    if match:
        hours, minutes, seconds = match.groups()
        return int(round((int(hours) * 3600 + int(minutes) * 60 + float(seconds)) * 1000))
    return 0


def load_manifest(csv_path: str | Path, sources_dir: str | Path) -> list[ManifestRow]:
    rows: list[ManifestRow] = []
    with open(csv_path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None or not {"exercise_id", "source_file", "transcript"} <= set(reader.fieldnames):
            raise PipelineError(
                "Manifest CSV must have columns: exercise_id, source_file, transcript"
            )
        for line_number, row in enumerate(reader, start=2):
            try:
                exercise_id = int(row["exercise_id"])
            except (KeyError, ValueError) as exc:
                raise PipelineError(f"Manifest line {line_number}: invalid exercise_id") from exc
            source_file = Path(sources_dir) / row["source_file"]
            rows.append(ManifestRow(exercise_id, str(source_file), row["transcript"] or ""))
    if not rows:
        raise PipelineError("Manifest contains no rows")
    return rows


class Database:
    """Thin psycopg wrapper; duck-typeable for tests."""

    def __init__(self, conn):
        self.conn = conn

    def find_asset_id(self, sha256: str) -> int | None:
        row = self.conn.execute(
            "SELECT id FROM audio_asset WHERE sha256 = %s", (sha256,)
        ).fetchone()
        return row[0] if row else None

    def create_asset(self, sha256: str, opus_path: str, mp3_path: str,
                     duration_ms: int, bytes_: int) -> int:
        row = self.conn.execute(
            """
            INSERT INTO audio_asset (sha256, opus_path, mp3_path, duration_ms, bytes)
            VALUES (%s, %s, %s, %s, %s) RETURNING id
            """,
            (sha256, opus_path, mp3_path, duration_ms, bytes_),
        ).fetchone()
        return row[0]

    def exercise_exists(self, exercise_id: int) -> bool:
        return self.conn.execute(
            "SELECT 1 FROM exercise WHERE id = %s", (exercise_id,)
        ).fetchone() is not None

    def link_exercise(self, exercise_id: int, asset_id: int) -> None:
        self.conn.execute(
            "UPDATE exercise SET audio_asset_id = %s WHERE id = %s", (asset_id, exercise_id)
        )

    def list_listen_exercises(self) -> list[tuple[int, str]]:
        return [
            (row[0], row[1])
            for row in self.conn.execute(
                """
                SELECT id, caption FROM exercise
                WHERE type = 'LISTEN' AND caption IS NOT NULL
                ORDER BY id
                """
            ).fetchall()
        ]


def process_row(row: ManifestRow, db: Database, output_dir: str | Path,
                transcoder: callable = transcode, prober: callable = probe_duration_ms) -> str:
    source = Path(row.source_file)
    if not source.is_file():
        return "missing_source"
    if not db.exercise_exists(row.exercise_id):
        return "missing_exercise"

    out_opus, out_mp3, rel_opus, rel_mp3 = content_addressed_paths(source, output_dir)
    existing = db.find_asset_id(sha256_file(source))
    if existing is not None:
        db.link_exercise(row.exercise_id, existing)
        return "reused"

    if not (out_opus.is_file() and out_mp3.is_file()):
        transcoder(source, out_opus, out_mp3)
    duration_ms = prober(out_opus)
    size = out_opus.stat().st_size
    asset_id = db.create_asset(sha256_file(source), rel_opus, rel_mp3, duration_ms, size)
    db.link_exercise(row.exercise_id, asset_id)
    return "created"


def process_manifest(rows: list[ManifestRow], db: Database, output_dir: str | Path,
                     transcoder: callable = transcode, prober: callable = probe_duration_ms) -> PipelineReport:
    report = PipelineReport()
    for row in rows:
        status = process_row(row, db, output_dir, transcoder, prober)
        if status == "created":
            report.created += 1
            report.details.append(f"created asset for exercise {row.exercise_id}")
        elif status == "reused":
            report.reused += 1
        elif status == "missing_source":
            report.missing_source += 1
            print(f"WARNING: exercise {row.exercise_id}: source file missing: {row.source_file}", file=sys.stderr)
        elif status == "missing_exercise":
            report.missing_exercise += 1
            print(f"WARNING: exercise {row.exercise_id}: no such exercise in database", file=sys.stderr)
    return report


def find_voice_files(voice_name: str, data_dir: Path, download: callable | None) -> tuple[str, str]:
    local_onnx = data_dir / f"{voice_name}.onnx"
    local_cfg = data_dir / f"{voice_name}.onnx.json"
    if local_onnx.is_file() and local_cfg.is_file():
        return str(local_onnx), str(local_cfg)
    if download is None:
        raise PipelineError(
            f"Voice model not found at {local_onnx} and piper download is unavailable. "
            "Install piper-tts (uv sync --group tts) or place the .onnx and .onnx.json there."
        )
    try:
        return download(voice_name, [data_dir])
    except Exception as exc:
        raise PipelineError(
            f"Could not download voice '{voice_name}': {exc}. "
            f"Download it manually to {local_onnx} from https://huggingface.co/rhasspy/piper-voices"
        ) from exc


def make_voice_downloader(download_voices_module) -> callable | None:
    """Builds a downloader callable from the installed piper-tts API.

    piper-tts 1.x exposes ensure_voice_exists(voice, data_dirs); newer
    releases expose download_voice(voice, download_dir). Return None when
    neither exists.
    """
    ensure = getattr(download_voices_module, "ensure_voice_exists", None)
    if ensure is not None:
        return ensure
    download = getattr(download_voices_module, "download_voice", None)
    if download is None:
        return None

    def downloader(voice_name: str, data_dirs: list) -> tuple[str, str]:
        data_dir = Path(data_dirs[0])
        data_dir.mkdir(parents=True, exist_ok=True)
        download(voice_name, data_dir)
        onnx = data_dir / f"{voice_name}.onnx"
        cfg = data_dir / f"{voice_name}.onnx.json"
        if not onnx.is_file() or not cfg.is_file():
            raise PipelineError(f"piper reported success but the voice files are missing in {data_dir}")
        return str(onnx), str(cfg)

    return downloader


def voice_license(config_path: str) -> str:
    with open(config_path, encoding="utf-8") as fh:
        return json.load(fh).get("license", "")


def is_redistributable(license_text: str) -> bool:
    normalized = license_text.upper()
    if any(marker in normalized for marker in NON_REDISTRIBUTABLE_MARKERS):
        return False
    return any(token in normalized for token in REDISTRIBUTABLE_LICENSES)


def load_voice(voice_name: str, data_dir: Path,
               piper_module=None, download_fn=None) -> tuple[object, str, str, str]:
    """Returns (voice, license, model_path, config_path). Raises on non-redistributable voices."""
    model_path, cfg_path = find_voice_files(voice_name, data_dir, download_fn)
    license_ = voice_license(cfg_path)
    if not license_:
        known = KNOWN_VOICE_LICENSES.get(voice_name)
        if known is None:
            raise PipelineError(
                f"Voice '{voice_name}' has no license metadata and is not in the known-license table. "
                "Refusing to generate audio without a verified redistributable license."
            )
        license_ = known[0]
    if not is_redistributable(license_):
        raise PipelineError(
            f"Voice '{voice_name}' license '{license_}' does not allow redistribution. "
            "Choose a voice licensed CC0/CC BY/MIT/BSD/Apache-2.0."
        )
    if piper_module is None:
        try:
            from piper import PiperVoice
        except ImportError as exc:
            raise PipelineError(
                "piper-tts is required for --tts mode. Install it with: uv sync --group tts"
            ) from exc
        voice = PiperVoice.load(model_path)
    else:
        voice = piper_module.PiperVoice.load(model_path)
    return voice, license_, model_path, cfg_path


def synthesize(voice, text: str, out_wav: Path) -> None:
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(out_wav), "wb") as wav_file:
        if hasattr(voice, "synthesize_wav"):
            voice.synthesize_wav(text, wav_file)
            return
        for chunk in voice.synthesize(text):
            wav_file.setframerate(chunk.sample_rate)
            wav_file.setsampwidth(chunk.sample_width)
            wav_file.setnchannels(chunk.sample_channels)
            wav_file.writeframes(chunk.audio_int16_bytes)


def slugify(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower().strip("¡¿?!.,;: ")).strip("-")
    return slug or "clip"


def generate_tts_clips(db: Database, tts_dir: str | Path,
                       voice, voice_name: str, force: bool = False) -> list[ManifestRow]:
    rows: list[ManifestRow] = []
    for index, (exercise_id, transcript) in enumerate(db.list_listen_exercises(), start=1):
        wav_path = Path(tts_dir) / f"{index:03d}-{slugify(transcript)}.wav"
        if not wav_path.is_file() or force:
            # Piper synthesis is stochastic; keep the generated WAV as the
            # source of truth so re-runs stay content-addressable.
            synthesize(voice, transcript, wav_path)
        rows.append(ManifestRow(exercise_id, str(wav_path), transcript))
    if not rows:
        raise PipelineError(
            "No LISTEN exercises with transcripts found in the database. Run tools/seed.py first."
        )
    return rows


def update_content_md(content_md_path: str | Path, engine: str, engine_version: str,
                      voice_name: str, license_: str, voice_source: str, generated_at: str) -> None:
    block = f"""## Audio generation

- Engine: {engine} {engine_version}
- Voice: {voice_name}
- Voice license: {license_} (permits redistribution)
- Voice source: {voice_source}
- Generated: {generated_at}
- Storage: content-addressed files under `audio/<sha256[:2]>/<sha256>.<ext>`
  (64 kbps Opus + 64 kbps MP3 fallback). Audio files are not committed to git.
- License of the generated clips: derivatives of the voice model, they
  inherit the voice license above.
"""
    marker_start = "<!-- AUDIO-BLOCK-START -->"
    marker_end = "<!-- AUDIO-BLOCK-END -->"
    path = Path(content_md_path)
    if path.exists():
        content = path.read_text(encoding="utf-8")
        pattern = re.compile(marker_start + r".*?" + marker_end, re.DOTALL)
        replacement = marker_start + "\n" + block + marker_end
        if pattern.search(content):
            content = pattern.sub(replacement, content)
        else:
            content += "\n" + replacement + "\n"
    else:
        content = "# LinguaLoop content\n\n" + marker_start + "\n" + block + marker_end + "\n"
    path.write_text(content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", help="CSV manifest: exercise_id, source_file, transcript")
    parser.add_argument("--sources", default=".", help="Directory containing source files (manifest mode)")
    parser.add_argument("--output-dir", default="audio", help="Output root (default: ./audio)")
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"),
                        help="PostgreSQL URL (default: $DATABASE_URL)")
    parser.add_argument("--tts", action="store_true", help="Generate clips with Piper from seed transcripts")
    parser.add_argument("--tts-voice", default=DEFAULT_VOICE, help=f"Piper voice name (default: {DEFAULT_VOICE})")
    parser.add_argument("--tts-data-dir", default=str(DEFAULT_DATA_DIR),
                        help="Directory holding Piper voice models")
    parser.add_argument("--tts-dir", default="tts-source",
                        help="Where generated TTS WAV files are written (default: ./tts-source)")
    parser.add_argument("--force-tts", action="store_true",
                        help="Regenerate TTS WAV files even if they already exist")
    parser.add_argument("--content-md", default=str(Path(__file__).resolve().parent.parent / "CONTENT.md"),
                        help="Path to CONTENT.md to update in --tts mode")
    args = parser.parse_args()

    if not args.database_url:
        parser.error("--database-url is required (or set DATABASE_URL)")
    require_ffmpeg()

    if args.tts:
        try:
            from importlib.metadata import version as pkg_version

            from piper import download_voices
            engine_version = pkg_version("piper-tts")
        except ImportError as exc:
            raise PipelineError(
                "piper-tts is required for --tts mode. Install it with: uv sync --group tts"
            ) from exc
        download_fn = make_voice_downloader(download_voices)
        voice, license_, _model_path, _cfg_path = load_voice(
            args.tts_voice, Path(args.tts_data_dir), download_fn=download_fn)
        print(f"Loaded voice {args.tts_voice} (license: {license_})")
        with psycopg.connect(args.database_url) as conn:
            rows = generate_tts_clips(Database(conn), Path(args.tts_dir), voice, args.tts_voice,
                                      force=args.force_tts)
        update_content_md(
            args.content_md,
            engine="Piper TTS",
            engine_version=engine_version,
            voice_name=args.tts_voice,
            license_=license_,
            voice_source="https://huggingface.co/rhasspy/piper-voices",
            generated_at=datetime.now(UTC).isoformat(),
        )
        print(f"Updated {args.content_md}")
    else:
        if not args.manifest:
            parser.error("--manifest is required unless --tts is given")
        rows = load_manifest(args.manifest, args.sources)

    with psycopg.connect(args.database_url) as conn:
        db = Database(conn)
        report = process_manifest(rows, db, args.output_dir)

    print(f"Pipeline complete: {report.created} created, {report.reused} reused, "
          f"{report.missing_source} missing sources, {report.missing_exercise} missing exercises.")
    for detail in report.details:
        print(f"  {detail}")
    if report.created == 0 and report.reused == 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
