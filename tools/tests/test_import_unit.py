"""Tests for import_unit.py — local validation mirrors the server rules."""

from __future__ import annotations

import json
import urllib.request

import pytest

import import_unit

VALID_YAML = """
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
        prompt: Which is hello?
        answer: Bonjour
        choices: [Bonjour, Au revoir, Merci]
      - type: LISTEN
        prompt: Listen and type what you hear.
        answer: Bonjour
        caption: Bonjour
"""


def test_valid_yaml_passes_local_validation():
    bundle = import_unit.parse_yaml(VALID_YAML)
    import_unit.validate(bundle)  # must not raise
    assert len(bundle.lessons) == 1
    assert len(bundle.lessons[0][3]) == 3


def test_yaml_reports_line_numbers_for_bad_rows():
    broken = """
language:
  code: fr
  name: French
unit:
  title: Unité 1
  position: 1
lessons:
  - title: Salutations
    position: 1
    exercises:
      - type: MULTIPLE_CHOICE
        prompt: Which one?
        answer: Not-a-choice
        choices: [Hello, Goodbye]
      - type: LISTEN
        prompt: Listen
        answer: Hola
        choices: [Hola]
"""
    bundle = import_unit.parse_yaml(broken)
    with pytest.raises(import_unit.ValidationError) as excinfo:
        import_unit.validate(bundle)
    errors = excinfo.value.errors
    assert len(errors) == 2
    lines = {line for line, _ in errors}
    messages = " ".join(message for _, message in errors)
    assert all(line > 0 for line in lines)
    assert "one of the choices" in messages
    assert "must not define choices" in messages


def test_csv_parses_and_validates():
    csv_text = (
        "language_code,language_name,unit_title,unit_position,lesson,position,type,prompt,answer,choices,caption\n"
        "fr,French,Unité 1,1,Salutations,1,TRANSLATE,Good morning,Bonjour,,\n"
        ",,,,Salutations,1,MULTIPLE_CHOICE,Which is hello?,Bonjour,Bonjour;Au revoir;Merci,\n"
    )
    bundle = import_unit.parse_csv(csv_text)
    import_unit.validate(bundle)
    assert len(bundle.lessons) == 1
    assert len(bundle.lessons[0][3]) == 2


def test_csv_rejects_invalid_language_code_with_line_number():
    csv_text = (
        "language_code,language_name,unit_title,unit_position,lesson,position,type,prompt,answer,choices,caption\n"
        "french!!,French,Unité 1,1,Salutations,1,TRANSLATE,Hi,Bonjour,,\n"
    )
    bundle = import_unit.parse_csv(csv_text)
    with pytest.raises(import_unit.ValidationError) as excinfo:
        import_unit.validate(bundle)
    assert excinfo.value.errors[0][0] == 2
    assert "Language code" in excinfo.value.errors[0][1]


def test_dry_run_posts_text_plain_to_the_import_endpoint(monkeypatch, tmp_path):
    captured = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps({
                "dryRun": True, "languageCode": "fr", "languageAction": "CREATED",
                "unitTitle": "Unité 1", "unitAction": "CREATED", "previousExerciseCount": 0,
                "lessons": [{"title": "Salutations", "position": 1, "exerciseCount": 1}],
                "lessonCount": 1, "exerciseCount": 1,
            }).encode()

    def fake_open(request, timeout=30):
        captured["url"] = request.full_url
        captured["content_type"] = request.headers["Content-type"]
        captured["auth"] = request.headers["Authorization"]
        captured["body"] = request.data.decode()
        return FakeResponse()

    monkeypatch.setattr(urllib.request, "urlopen", fake_open)

    bundle_path = tmp_path / "unit.yaml"
    bundle_path.write_text(VALID_YAML)

    result = import_unit.post_import("http://api", "admintoken", VALID_YAML, "yaml", dry_run=True)

    assert captured["url"] == "http://api/api/admin/import?format=yaml&dryRun=true"
    assert captured["content_type"] == "text/plain"
    assert captured["auth"] == "Bearer admintoken"
    assert "Good morning" in captured["body"]
    assert result["unitAction"] == "CREATED"
