#!/usr/bin/env python3
"""Validate a unit bundle locally and post it to the admin import endpoint.

The local validation mirrors the server's rules exactly (same schema, same
constraints — see docs/authoring.md), so authors get line-numbered errors
before anything touches the API. When validation passes, the bundle is sent
to POST /api/admin/import (text/plain body; ?format=yaml|csv&dryRun=...),
which validates again server-side and imports atomically.

YAML shape (line numbers reported from the YAML source):

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
            caption: Bonjour

CSV shape (first row defines the unit; header + one row per exercise):

    language_code,language_name,unit_title,unit_position,lesson,position,type,prompt,answer,choices,caption
    fr,French,Unité 1,1,Salutations,1,TRANSLATE,Good morning,Bonjour,,
    ,,,,Salutations,1,MULTIPLE_CHOICE,Which is hello?,Bonjour,Bonjour;Au revoir;Merci,

Usage:
    uv run -- python import_unit.py --yaml unit.yaml --email author@example.com --password secret
    uv run -- python import_unit.py --yaml unit.yaml --token "$TOKEN" --dry-run
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

try:
    import yaml
except ImportError:
    yaml = None

LANGUAGE_CODE = re.compile(r"^[a-z]{2,3}(-[A-Z]{2})?$")
TYPES = {"TRANSLATE", "MULTIPLE_CHOICE", "LISTEN"}


class ValidationError(RuntimeError):
    def __init__(self, errors: list[tuple[int, str]]):
        super().__init__(f"{len(errors)} invalid row(s)")
        self.errors = errors


@dataclass
class Bundle:
    language: tuple[str, str, int]  # code, name, line
    unit: tuple[str, int, int]  # title, position, line
    lessons: list = field(default_factory=list)  # (title, position, line, exercises)
    # exercise = (type, prompt, answer, choices, caption, line)


def parse_yaml(text: str) -> Bundle:
    if yaml is None:
        raise ValidationError([(1, "PyYAML is required for YAML bundles (uv add pyyaml)")])
    root = yaml.compose(text)
    if root is None:
        raise ValidationError([(1, "the import document is empty")])

    def scalar(node):
        return (node.value or "").strip()

    def line(node):
        return node.start_mark.line + 1

    top = {}
    for key_node, value_node in root.value:
        top[scalar(key_node)] = value_node

    def mapping(node, context):
        if not hasattr(node, "value") or not isinstance(node.value, list):
            raise ValidationError([(line(context), "expected a mapping here")])
        return {scalar(k): v for k, v in node.value}

    language_node = top.get("language")
    if language_node is None:
        raise ValidationError([(1, "missing top-level key 'language'")])
    language_map = mapping(language_node, language_node)
    code = scalar(language_map.get("code") or language_node)
    name = scalar(language_map.get("name") or language_node)
    if language_map.get("code") is None or language_map.get("name") is None:
        raise ValidationError([(line(language_node), "language needs 'code' and 'name'")])

    unit_node = top.get("unit")
    if unit_node is None:
        raise ValidationError([(1, "missing top-level key 'unit'")])
    unit_map = mapping(unit_node, unit_node)
    unit_title = scalar(unit_map.get("title") or unit_node)
    unit_position = int(scalar(unit_map.get("position") or "0") or 0)

    lessons_node = top.get("lessons")
    if lessons_node is None or not hasattr(lessons_node, "value"):
        raise ValidationError([(line(unit_node), "missing top-level key 'lessons'")])

    bundle = Bundle((code, name, line(language_node)), (unit_title, unit_position, line(unit_node)))
    for lesson_node in lessons_node.value:
        lesson_map = mapping(lesson_node, lesson_node)
        lesson_title = scalar(lesson_map.get("title") or lesson_node)
        lesson_position = int(scalar(lesson_map.get("position") or "0") or 0)
        exercises_node = lesson_map.get("exercises")
        exercises = []
        if exercises_node is not None and hasattr(exercises_node, "value"):
            for exercise_node in exercises_node.value:
                exercise_map = mapping(exercise_node, exercise_node)
                choices = []
                choices_node = exercise_map.get("choices")
                if choices_node is not None and hasattr(choices_node, "value"):
                    choices = [scalar(c) for c in choices_node.value]
                caption = scalar(exercise_map["caption"]) if "caption" in exercise_map else None
                exercises.append((
                    scalar(exercise_map["type"]) if "type" in exercise_map else "",
                    scalar(exercise_map["prompt"]) if "prompt" in exercise_map else "",
                    scalar(exercise_map["answer"]) if "answer" in exercise_map else "",
                    choices,
                    caption,
                    line(exercise_node),
                ))
        bundle.lessons.append((lesson_title, lesson_position, line(lesson_node), exercises))
    return bundle


def parse_csv(text: str) -> Bundle:
    rows = parse_csv_rows(text)
    if len(rows) < 2:
        raise ValidationError([(1, "the CSV import is empty")])
    header = {name.strip().lower(): i for i, name in enumerate(rows[0])}
    for required in ("lesson", "position", "type", "prompt", "answer"):
        if required not in header:
            raise ValidationError([(1, f"CSV header must include column '{required}'")])

    def cell(row, column):
        if column not in header:
            return None
        index = header[column]
        return row[index] if index < len(row) else None

    first = rows[1]
    code = cell(first, "language_code")
    name = cell(first, "language_name")
    unit_title = cell(first, "unit_title")
    unit_position = cell(first, "unit_position")
    if not code or not unit_title:
        raise ValidationError([(2, "the first row must define language_code and unit_title")])
    try:
        unit_position_value = int(unit_position or "0")
    except ValueError:
        raise ValidationError([(2, f"'{unit_position}' is not a valid unit position")])

    bundle = Bundle((code, name, 2), (unit_title, unit_position_value, 2))
    current_lesson = None
    for index, row in enumerate(rows[1:], start=2):
        lesson_title = cell(row, "lesson")
        try:
            lesson_position = int(cell(row, "position") or "0")
        except ValueError:
            raise ValidationError([(index, f"'{cell(row, 'position')}' is not a valid lesson position")])
        if not lesson_title:
            raise ValidationError([(index, "lesson title must not be blank")])
        if current_lesson is None or current_lesson[0] != lesson_title:
            current_lesson = (lesson_title, lesson_position, index, [])
            bundle.lessons.append(current_lesson)
        choices = [c.strip() for c in (cell(row, "choices") or "").split(";") if c.strip()]
        current_lesson[3].append((
            cell(row, "type"), cell(row, "prompt"), cell(row, "answer"),
            choices, cell(row, "caption") or None, index,
        ))
    return bundle


def parse_csv_rows(text: str) -> list[list[str]]:
    rows = []
    row = []
    field = []
    quoted = False
    i = 0
    while i < len(text):
        char = text[i]
        if quoted:
            if char == '"':
                if i + 1 < len(text) and text[i + 1] == '"':
                    field.append('"')
                    i += 1
                else:
                    quoted = False
            else:
                field.append(char)
        elif char == '"':
            quoted = True
        elif char == ",":
            row.append("".join(field).strip())
            field = []
        elif char in "\n\r":
            if char == "\r" and i + 1 < len(text) and text[i + 1] == "\n":
                i += 1
            row.append("".join(field).strip())
            field = []
            if row:
                rows.append(row)
            row = []
        else:
            field.append(char)
        i += 1
    row.append("".join(field).strip())
    if any(row):
        rows.append(row)
    return rows


def validate(bundle: Bundle) -> None:
    errors: list[tuple[int, str]] = []
    code, name, language_line = bundle.language
    if not LANGUAGE_CODE.match(code or ""):
        errors.append((language_line, f"Language code '{code}' must look like 'es' or 'pt-BR'"))
    if not name:
        errors.append((language_line, "Language name must not be blank"))
    unit_title, unit_position, unit_line = bundle.unit
    if not unit_title:
        errors.append((unit_line, "Unit title must not be blank"))
    if unit_position < 1:
        errors.append((unit_line, "Unit position must be >= 1"))
    if not bundle.lessons:
        errors.append((unit_line, "The unit must contain at least one lesson"))

    lesson_positions = set()
    exercise_count = 0
    for lesson_title, lesson_position, lesson_line, exercises in bundle.lessons:
        if not lesson_title:
            errors.append((lesson_line, "Lesson title must not be blank"))
        if lesson_position < 1 or lesson_position in lesson_positions:
            errors.append((lesson_line, f"Lesson position {lesson_position} is missing or duplicated"))
        lesson_positions.add(lesson_position)
        if not exercises:
            errors.append((lesson_line, f"Lesson '{lesson_title}' has no exercises"))
        for exercise_type, prompt, answer, choices, _caption, line in exercises:
            exercise_count += 1
            if exercise_type not in TYPES:
                errors.append((line, f"Unknown exercise type '{exercise_type}'"))
                continue
            if not prompt:
                errors.append((line, f"{exercise_type}: prompt must not be blank"))
            if not answer:
                errors.append((line, f"{exercise_type}: answer must not be blank"))
            if exercise_type == "MULTIPLE_CHOICE":
                if not 2 <= len(choices) <= 8:
                    errors.append((line, "MULTIPLE_CHOICE needs between 2 and 8 choices"))
                if answer not in choices:
                    errors.append((line, "The answer must be one of the choices"))
                if len(choices) != len(set(choices)):
                    errors.append((line, "Choices must not contain duplicates"))
            elif choices:
                errors.append((line, f"{exercise_type} exercises must not define choices"))
    if exercise_count == 0:
        errors.append((unit_line, "The bundle contains no exercises"))
    if errors:
        raise ValidationError(errors)


def login(api_base: str, email: str, password: str) -> str:
    request = urllib.request.Request(
        f"{api_base}/api/auth/login",
        data=json.dumps({"email": email, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())["token"]


def post_import(api_base: str, token: str, content: str, format_: str, dry_run: bool) -> dict:
    url = f"{api_base}/api/admin/import?format={format_}&dryRun={str(dry_run).lower()}"
    request = urllib.request.Request(
        url,
        data=content.encode("utf-8"),
        headers={"Content-Type": "text/plain", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"import failed ({error.code}): {body[:2000]}") from error


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--yaml", help="Path to a YAML unit bundle")
    source.add_argument("--csv", help="Path to a CSV unit bundle")
    parser.add_argument("--api-base", default="http://localhost:8080", help="API base URL")
    parser.add_argument("--token", help="Admin JWT (skip --email/--password)")
    parser.add_argument("--email", help="Admin email to log in with")
    parser.add_argument("--password", help="Admin password")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what the import would change without persisting")
    parser.add_argument("--check-only", action="store_true",
                        help="Only validate locally; do not contact the API")
    args = parser.parse_args()

    path = Path(args.yaml or args.csv)
    format_ = "yaml" if args.yaml else "csv"
    text = path.read_text(encoding="utf-8")
    bundle = parse_yaml(text) if format_ == "yaml" else parse_csv(text)

    try:
        validate(bundle)
    except ValidationError as error:
        print(f"Validation failed for {path} ({format_}):")
        for line, message in sorted(error.errors):
            print(f"  line {line}: {message}")
        sys.exit(1)

    lesson_count = len(bundle.lessons)
    exercise_count = sum(len(exercises) for *_title, _pos, _line, exercises in bundle.lessons)
    print(f"{path} is valid: 1 unit, {lesson_count} lesson(s), {exercise_count} exercise(s).")

    if args.check_only:
        return

    token = args.token
    if not token:
        if not args.email or not args.password:
            parser.error("--token or (--email and --password) are required to post the import")
        token = login(args.api_base, args.email, args.password)

    result = post_import(args.api_base, token, text, format_, args.dry_run)
    mode = "dry-run" if args.dry_run else "import"
    print(f"{mode.capitalize()} result: language '{result['languageCode']}' {result['languageAction']}, "
          f"unit '{result['unitTitle']}' {result['unitAction']}"
          + (f" (replacing {result['previousExerciseCount']} exercise(s))"
             if result["unitAction"] == "REPLACED" else "")
          + f", {result['lessonCount']} lesson(s), {result['exerciseCount']} exercise(s).")
    if args.dry_run:
        print("Nothing was persisted. Re-run without --dry-run to commit.")


if __name__ == "__main__":
    main()
