package com.lingualoop.api.admin.imports;

import java.util.ArrayList;
import java.util.List;

/** A parsed unit bundle with source line numbers for precise error reporting. */
public class UnitBundle {

    public record LanguageInfo(String code, String name, int line) {
    }

    public record UnitInfo(String title, int position, int line) {
    }

    public record ExerciseInfo(String type, String prompt, String answer, List<String> choices, String caption,
            int line) {
    }

    public record LessonInfo(String title, int position, int line, List<ExerciseInfo> exercises) {
    }

    public final LanguageInfo language;
    public final UnitInfo unit;
    public final List<LessonInfo> lessons = new ArrayList<>();

    public UnitBundle(LanguageInfo language, UnitInfo unit) {
        this.language = language;
        this.unit = unit;
    }

    public int totalExercises() {
        return lessons.stream().mapToInt(lesson -> lesson.exercises().size()).sum();
    }
}
