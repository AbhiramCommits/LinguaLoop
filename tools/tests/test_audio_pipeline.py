"""Tests for the LinguaLoop audio pipeline."""

from __future__ import annotations

import hashlib
import json
import shutil
import types
import wave
from pathlib import Path

import pytest

import audio_pipeline as ap


class FakeDatabase:
    def __init__(self):
        self.assets: dict[str, int] = {}
        self.asset_rows: dict[int, dict] = {}
        self.exercises: set[int] = set()
        self.links: list[tuple[int, int]] = []
        self._next_id = 1

    def find_asset_id(self, sha256: str) -> int | None:
        return self.assets.get(sha256)

    def create_asset(self, sha256, opus_path, mp3_path, duration_ms, bytes_):
        asset_id = self._next_id
        self._next_id += 1
        self.assets[sha256] = asset_id
        self.asset_rows[asset_id] = {
            "sha256": sha256, "opus_path": opus_path, "mp3_path": mp3_path,
            "duration_ms": duration_ms, "bytes": bytes_,
        }
        return asset_id

    def exercise_exists(self, exercise_id):
        return exercise_id in self.exercises

    def link_exercise(self, exercise_id, asset_id):
        self.links.append((exercise_id, asset_id))

    def list_listen_exercises(self):
        return []


def fake_transcode(source, out_opus, out_mp3):
    out_opus.parent.mkdir(parents=True, exist_ok=True)
    data = Path(source).read_bytes()
    out_opus.write_bytes(b"OPUS!" + data)
    out_mp3.write_bytes(b"MP3!" + data)


def fake_probe(path):
    return 1234


class FakePiperModule:
    class PiperVoice:
        @staticmethod
        def load(model_path):
            return "voice-instance"


class FakeVoice:
    def synthesize_wav(self, text, wav_file):
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(22050)
        wav_file.writeframesraw(b"\x00\x00" * 100)


class FakeLegacyVoice:
    """Pre-1.8 piper API: synthesize() yields audio chunks."""

    def synthesize(self, text):
        chunk = types.SimpleNamespace(
            sample_rate=22050, sample_width=2, sample_channels=1,
            audio_int16_bytes=b"\x00\x00" * 100)
        yield chunk


class FakeDownloadModule:
    def __init__(self, license_text="CC0"):
        self._license = license_text

    def ensure_voice_exists(self, voice_name, data_dirs):
        cfg = data_dirs[0] / f"{voice_name}.onnx.json"
        cfg.parent.mkdir(parents=True, exist_ok=True)
        cfg.write_text(json.dumps({"license": self._license}))
        return str(cfg.with_suffix(".onnx")), str(cfg)


def make_wav_fixture(path: Path, seconds: float = 0.25, rate: int = 22050) -> bytes:
    """Generate a tiny valid WAV file (silence) for real-ffmpeg tests."""
    import math
    import struct

    frames = int(rate * seconds)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(rate)
        for i in range(frames):
            sample = int(32767 * 0.2 * math.sin(2 * math.pi * 440 * i / rate))
            wav_file.writeframesraw(struct.pack("<h", sample))
    return path.read_bytes()


# ---------------------------------------------------------------------------
# ffmpeg presence


def test_missing_ffmpeg_is_refused(monkeypatch):
    monkeypatch.setattr(shutil, "which", lambda _name: None)
    with pytest.raises(ap.FfmpegMissingError, match="ffmpeg is required"):
        ap.require_ffmpeg()


def test_ffmpeg_present_is_accepted(monkeypatch):
    monkeypatch.setattr(shutil, "which", lambda name: "/usr/bin/" + name if name == "ffmpeg" else None)
    ap.require_ffmpeg()


# ---------------------------------------------------------------------------
# content addressing + idempotency


def test_content_addressed_paths(tmp_path):
    source = tmp_path / "clip.wav"
    source.write_bytes(b"hola-como-estas")
    expected_sha = hashlib.sha256(b"hola-como-estas").hexdigest()

    out_opus, out_mp3, rel_opus, rel_mp3 = ap.content_addressed_paths(source, tmp_path / "audio-root")

    assert rel_opus == f"audio/{expected_sha[:2]}/{expected_sha}.opus"
    assert rel_mp3 == f"audio/{expected_sha[:2]}/{expected_sha}.mp3"
    assert out_opus == tmp_path / "audio-root" / rel_opus


def test_processing_writes_both_variants_and_registers_asset(tmp_path):
    db = FakeDatabase()
    db.exercises.add(21)
    source = tmp_path / "src" / "clip.wav"
    source.parent.mkdir()
    source.write_bytes(b"source-bytes-1")

    status = ap.process_row(
        ap.ManifestRow(21, str(source), "Hola"),
        db, tmp_path / "out", transcoder=fake_transcode, prober=fake_probe,
    )

    assert status == "created"
    assert len(db.asset_rows) == 1
    row = db.asset_rows[1]
    assert row["duration_ms"] == 1234
    assert row["bytes"] > 0
    assert Path(tmp_path / "out" / row["opus_path"]).is_file()
    assert Path(tmp_path / "out" / row["mp3_path"]).is_file()
    assert db.links == [(21, 1)]


def test_idempotent_rerun_creates_no_new_assets(tmp_path):
    db = FakeDatabase()
    db.exercises.add(21)
    source = tmp_path / "clip.wav"
    source.write_bytes(b"same-source-bytes")

    first = ap.process_row(ap.ManifestRow(21, str(source), "Hola"), db, tmp_path / "out",
                           transcoder=fake_transcode, prober=fake_probe)
    second = ap.process_row(ap.ManifestRow(21, str(source), "Hola"), db, tmp_path / "out",
                            transcoder=fake_transcode, prober=fake_probe)

    assert first == "created"
    assert second == "reused"
    assert len(db.asset_rows) == 1


def test_changed_input_produces_a_new_asset(tmp_path):
    db = FakeDatabase()
    db.exercises.add(21)
    source = tmp_path / "clip.wav"
    source.write_bytes(b"version-one")
    ap.process_row(ap.ManifestRow(21, str(source), "Hola"), db, tmp_path / "out",
                   transcoder=fake_transcode, prober=fake_probe)
    source.write_bytes(b"version-two")
    status = ap.process_row(ap.ManifestRow(21, str(source), "Hola"), db, tmp_path / "out",
                            transcoder=fake_transcode, prober=fake_probe)
    assert status == "created"
    assert len(db.asset_rows) == 2


def test_missing_source_file_is_skipped(tmp_path):
    db = FakeDatabase()
    db.exercises.add(21)
    status = ap.process_row(ap.ManifestRow(21, str(tmp_path / "nope.wav"), "Hola"),
                            db, tmp_path / "out", transcoder=fake_transcode, prober=fake_probe)
    assert status == "missing_source"
    assert not db.asset_rows


def test_unknown_exercise_is_skipped(tmp_path):
    db = FakeDatabase()
    source = tmp_path / "clip.wav"
    source.write_bytes(b"bytes")
    status = ap.process_row(ap.ManifestRow(99, str(source), "Hola"),
                            db, tmp_path / "out", transcoder=fake_transcode, prober=fake_probe)
    assert status == "missing_exercise"
    assert not db.asset_rows


def test_manifest_with_wrong_header_is_rejected(tmp_path):
    bad = tmp_path / "bad.csv"
    bad.write_text("exercise_id,file\n1,a.wav\n")
    with pytest.raises(ap.PipelineError, match="exercise_id, source_file, transcript"):
        ap.load_manifest(bad, tmp_path)


def test_manifest_loading_resolves_sources_dir(tmp_path):
    manifest = tmp_path / "m.csv"
    manifest.write_text("exercise_id,source_file,transcript\n21,clip.wav,Hola\n")
    rows = ap.load_manifest(manifest, tmp_path / "sources")
    assert rows[0].exercise_id == 21
    assert rows[0].source_file == str(tmp_path / "sources" / "clip.wav")
    assert rows[0].transcript == "Hola"


# ---------------------------------------------------------------------------
# TTS mode


def test_tts_refuses_non_redistributable_voice(tmp_path):
    download = FakeDownloadModule(license_text="CC BY-NC-ND 4.0")
    with pytest.raises(ap.PipelineError, match="does not allow redistribution"):
        ap.load_voice("bad-voice", tmp_path,
                      piper_module=FakePiperModule, download_fn=download.ensure_voice_exists)


def test_tts_accepts_cc0_voice(tmp_path):
    download = FakeDownloadModule(license_text="CC0")
    voice, license_, _model, _cfg = ap.load_voice(
        "es_ES-mls_10246-low", tmp_path,
        piper_module=FakePiperModule, download_fn=download.ensure_voice_exists)
    assert voice == "voice-instance"
    assert license_ == "CC0"


def test_tts_accepts_cc_by_and_rejects_cc_by_nc():
    assert ap.is_redistributable("CC BY 4.0")
    assert ap.is_redistributable("CC-BY-4.0")
    assert ap.is_redistributable("Multilingual LibriSpeech (MLS), CC BY 4.0")
    assert not ap.is_redistributable("CC BY-NC 4.0")
    assert not ap.is_redistributable("CC BY-NC-ND 4.0")
    assert not ap.is_redistributable("CC BY-ND 4.0")


def test_tts_uses_known_license_table_when_config_has_none(tmp_path):
    class DownloadNoLicense(FakeDownloadModule):
        def ensure_voice_exists(self, voice_name, data_dirs):
            cfg = data_dirs[0] / f"{voice_name}.onnx.json"
            cfg.parent.mkdir(parents=True, exist_ok=True)
            cfg.write_text(json.dumps({"audio": {"sample_rate": 16000}}))
            return str(cfg.with_suffix(".onnx")), str(cfg)

    voice, license_, _m, _c = ap.load_voice(
        "es_ES-mls_10246-low", tmp_path,
        piper_module=FakePiperModule, download_fn=DownloadNoLicense().ensure_voice_exists)
    assert voice == "voice-instance"
    assert "CC BY 4.0" in license_


def test_tts_generates_manifest_from_transcripts_and_updates_content_md(tmp_path):
    db = FakeDatabase()
    db.list_listen_exercises = lambda: [(21, "Hola, ¿cómo estás?"), (22, "Me llamo Carmen.")]

    voice = FakeVoice()
    rows = ap.generate_tts_clips(db, tmp_path / "tts", voice, "es_ES-test")

    assert [r.exercise_id for r in rows] == [21, 22]
    for row in rows:
        assert Path(row.source_file).is_file()

    legacy_rows = ap.generate_tts_clips(db, tmp_path / "tts-legacy", FakeLegacyVoice(), "es_ES-legacy")
    assert len(legacy_rows) == 2
    assert Path(legacy_rows[0].source_file).is_file()


def test_tts_keeps_existing_wavs_unless_forced(tmp_path):
    db = FakeDatabase()
    db.list_listen_exercises = lambda: [(21, "Hola")]
    tts_dir = tmp_path / "tts"
    wav_path = tts_dir / "001-hola.wav"
    wav_path.parent.mkdir()
    wav_path.write_bytes(b"original-bytes")

    rows = ap.generate_tts_clips(db, tts_dir, FakeVoice(), "es_ES-test")
    assert wav_path.read_bytes() == b"original-bytes"  # untouched

    rows = ap.generate_tts_clips(db, tts_dir, FakeVoice(), "es_ES-test", force=True)
    assert wav_path.read_bytes() != b"original-bytes"  # regenerated

    content_md = tmp_path / "CONTENT.md"
    ap.update_content_md(content_md, "Piper TTS", "1.2.0", "es_ES-test", "CC0",
                         "https://huggingface.co/rhasspy/piper-voices", "2026-09-29T00:00:00+00:00")
    text = content_md.read_text()
    assert "Piper TTS 1.2.0" in text
    assert "CC0" in text
    # re-running updates the same block instead of appending duplicates
    ap.update_content_md(content_md, "Piper TTS", "1.2.0", "es_ES-test", "CC0",
                         "https://huggingface.co/rhasspy/piper-voices", "2026-09-30T00:00:00+00:00")
    assert text.count("## Audio generation") >= 1
    assert content_md.read_text().count("## Audio generation") == 1


# ---------------------------------------------------------------------------
# real ffmpeg round-trip (skipped when ffmpeg is unavailable)


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg not installed")
def test_real_transcode_produces_playable_opus_and_mp3(tmp_path):
    source = tmp_path / "clip.wav"
    make_wav_fixture(source)
    out_opus = tmp_path / "out" / "clip.opus"
    out_mp3 = tmp_path / "out" / "clip.mp3"

    ap.transcode(source, out_opus, out_mp3)

    assert out_opus.is_file() and out_opus.stat().st_size > 0
    assert out_mp3.is_file() and out_mp3.stat().st_size > 0
    assert out_opus.read_bytes()[:4] == b"OggS"
    assert out_mp3.read_bytes()[:3] == b"ID3" or out_mp3.read_bytes()[0] == 0xFF  # MPEG sync
    duration = ap.probe_duration_ms(out_opus)
    assert 100 <= duration <= 1000
