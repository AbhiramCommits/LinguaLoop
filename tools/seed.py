#!/usr/bin/env python3
"""Seed the LinguaLoop database with a real Spanish starter dataset.

One unit ("Saludos y presentaciones") with three lessons and 32 exercises,
including 8 LISTEN (listening comprehension) exercises.

Usage:
    DATABASE_URL="postgresql://user:pass@localhost:5432/lingualoop" uv run seed.py
    uv run seed.py --database-url postgresql://... --reset

The script is idempotent: it skips seeding if the Spanish content already
exists. Pass --reset to drop the existing LinguaLoop Spanish seed first.

Audio for the LISTEN exercises is produced separately by
audio_pipeline.py --tts (see CONTENT.md); until then the exercises carry
only their captions/transcripts.
"""

from __future__ import annotations

import argparse
import os
import sys

import psycopg
from psycopg.types.json import Jsonb

UNIT = {
    "title": "Unidad 1 · Saludos y presentaciones",
    "lessons": [
        {
            "title": "Saludos básicos",
            "exercises": [
                # (type, prompt, answer, choices | None, caption | None)
                ("TRANSLATE", "Good morning", "Buenos días", None, None),
                ("TRANSLATE", "Good afternoon", "Buenas tardes", None, None),
                ("TRANSLATE", "Good night", "Buenas noches", None, None),
                ("TRANSLATE", "See you later", "Hasta luego", None, None),
                ("TRANSLATE", "How are you?", "¿Cómo estás?", None, None),
                ("TRANSLATE", "Thank you very much", "Muchas gracias", None, None),
                ("TRANSLATE", "Please", "Por favor", None, None),
                (
                    "MULTIPLE_CHOICE",
                    'What does "hola" mean?',
                    "Hello",
                    ["Hello", "Goodbye", "Please", "Thank you"],
                    None,
                ),
                (
                    "MULTIPLE_CHOICE",
                    '"Buenos días" means…',
                    "Good morning",
                    ["Good morning", "Good night", "Good afternoon", "See you later"],
                    None,
                ),
                (
                    "MULTIPLE_CHOICE",
                    '"Adiós" means…',
                    "Goodbye",
                    ["Goodbye", "Hello", "Thanks", "Welcome"],
                    None,
                ),
            ],
        },
        {
            "title": "Presentaciones",
            "exercises": [
                ("TRANSLATE", "What is your name?", "¿Cómo te llamas?", None, None),
                ("TRANSLATE", "My name is Ana.", "Me llamo Ana.", None, None),
                ("TRANSLATE", "Nice to meet you.", "Mucho gusto.", None, None),
                ("TRANSLATE", "Where are you from?", "¿De dónde eres?", None, None),
                ("TRANSLATE", "I am from Mexico.", "Soy de México.", None, None),
                ("TRANSLATE", "I speak a little Spanish.", "Hablo un poco de español.", None, None),
                ("TRANSLATE", "Do you speak English?", "¿Hablas inglés?", None, None),
                (
                    "MULTIPLE_CHOICE",
                    '"¿De dónde eres?"',
                    "Where are you from?",
                    ["Where are you from?", "How old are you?", "What is your name?", "Where do you live?"],
                    None,
                ),
                (
                    "MULTIPLE_CHOICE",
                    '"Me llamo Pedro"',
                    "My name is Pedro.",
                    ["My name is Pedro.", "I am from Peru.", "Nice to meet you.", "I am thirty."],
                    None,
                ),
                (
                    "MULTIPLE_CHOICE",
                    '"Hasta mañana"',
                    "See you tomorrow.",
                    ["See you tomorrow.", "See you later.", "Good night.", "Good morning."],
                    None,
                ),
            ],
        },
        {
            "title": "Comprensión auditiva",
            "exercises": [
                # LISTEN exercises: caption is the transcript, answer matches.
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Hola, ¿cómo estás?",
                    None,
                    "Hola, ¿cómo estás?",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Me llamo Carmen.",
                    None,
                    "Me llamo Carmen.",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Soy de España.",
                    None,
                    "Soy de España.",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Buenos días, ¿qué tal?",
                    None,
                    "Buenos días, ¿qué tal?",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Hablo un poco de español.",
                    None,
                    "Hablo un poco de español.",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Mucho gusto.",
                    None,
                    "Mucho gusto.",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "¿Dónde está la estación?",
                    None,
                    "¿Dónde está la estación?",
                ),
                (
                    "LISTEN",
                    "Listen and type what you hear.",
                    "Nos vemos mañana.",
                    None,
                    "Nos vemos mañana.",
                ),
                ("TRANSLATE", "Where is the station?", "¿Dónde está la estación?", None, None),
                ("TRANSLATE", "See you tomorrow.", "Hasta mañana.", None, None),
                (
                    "MULTIPLE_CHOICE",
                    '"¿Dónde está la estación?"',
                    "Where is the station?",
                    ["Where is the station?", "Where is the bathroom?", "What time is it?", "How much does it cost?"],
                    None,
                ),
                (
                    "MULTIPLE_CHOICE",
                    '"¿Qué tal?"',
                    "How's it going?",
                    ["How's it going?", "What's your name?", "Where are you from?", "Good night."],
                    None,
                ),
            ],
        },
    ],
}

}

def seed(database_url: str, reset: bool) -> None:
    with psycopg.connect(database_url) as conn:
        conn.execute("SELECT 1")
        existing = conn.execute(
            "SELECT id FROM language WHERE code = 'es' AND name = 'Spanish'"
        ).fetchone()
        if existing and not reset:
            print(f"Spanish seed already present (language id {existing[0]}); nothing to do. "
                  "Pass --reset to reseed.")
            return

        with conn.transaction():
            if reset:
                _reset_spanish(conn)

            language_id = conn.execute(
                "INSERT INTO language (code, name) VALUES ('es', 'Spanish') RETURNING id"
            ).fetchone()[0]
            unit_id = conn.execute(
                "INSERT INTO unit (language_id, title, position) VALUES (%s, %s, 1) RETURNING id",
                (language_id, "Unidad 1 · Saludos y presentaciones"),
            ).fetchone()[0]

            total_exercises = 0
            listen_count = 0
            for lesson_position, lesson in enumerate(UNIT["lessons"], start=1):
                lesson_id = conn.execute(
                    "INSERT INTO lesson (unit_id, title, position) VALUES (%s, %s, %s) RETURNING id",
                    (unit_id, lesson["title"], lesson_position),
                ).fetchone()[0]
                exercise_ids = []
                for exercise_type, prompt, answer, choices, caption in lesson["exercises"]:
                    if exercise_type == "LISTEN":
                        listen_count += 1
                    exercise_id = conn.execute(
                        """
                        INSERT INTO exercise (lesson_id, type, prompt, answer, choices, audio_asset_id, caption)
                        VALUES (%s, %s, %s, %s, %s, %s, %s) RETURNING id
                        """,
                        (
                            lesson_id,
                            exercise_type,
                            prompt,
                            answer,
                            Jsonb(choices) if choices else None,
                            None,
                            caption,
                        ),
                    ).fetchone()[0]
                    exercise_ids.append(exercise_id)
                total_exercises += len(exercise_ids)
                print(f"  lesson '{lesson['title']}': {len(exercise_ids)} exercises")

    print(f"Seeded Spanish unit (id {unit_id}): 1 unit, 3 lessons, "
          f"{total_exercises} exercises ({listen_count} LISTEN). "
          "Generate audio with: uv run --group tts -- python audio_pipeline.py --tts")


def _reset_spanish(conn) -> None:
    conn.execute(
        """
        DELETE FROM language WHERE code = 'es' AND name = 'Spanish'
        """
    )
    print("Removed existing Spanish seed (cascade deletes units/lessons/exercises).")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--database-url",
        default=os.environ.get("DATABASE_URL"),
        help="PostgreSQL connection URL (default: $DATABASE_URL)",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="drop the existing Spanish seed before inserting",
    )
    args = parser.parse_args()

    if not args.database_url:
        parser.error("--database-url is required (or set DATABASE_URL)")

    try:
        seed(args.database_url, args.reset)
    except psycopg.Error as exc:
        print(f"seed failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
