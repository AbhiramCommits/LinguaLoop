package com.lingualoop.api.admin.imports;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.lingualoop.api.common.error.BadRequestException;

/**
 * Parses import bundles from YAML or CSV, tracking source line numbers for
 * every row. Both formats describe the same shape:

 * YAML:
 *   language: { code: fr, name: French }
 *   unit: { title: "...", position: 1 }
 *   lessons:
 *     - title: Salutations
 *       position: 1
 *       exercises:
 *         - type: TRANSLATE
 *           prompt: Good morning
 *           answer: Bonjour
 *
 * CSV (header: lesson,position,type,prompt,answer,choices,caption):
 *   Salutations,1,TRANSLATE,Good morning,Bonjour,,
 */
public class ImportParser {

    private ImportParser() {
    }

    public static UnitBundle parse(String content, String format) {
        return switch (format.trim().toLowerCase()) {
            case "yaml", "yml" -> parseYaml(content);
            case "csv" -> parseCsv(content);
            default -> throw new BadRequestException("Unsupported import format '" + format + "' (use yaml or csv)");
        };
    }

    static UnitBundle parseYaml(String content) {
        Node root = new Yaml().compose(new StringReader(content));
        if (root == null) {
            throw new BadRequestException("The import document is empty");
        }
        Map<String, Node> top = mapping(root);

        Node languageNode = required(top, "language", root);
        Map<String, Node> languageMap = mapping(languageNode);
        String code = scalar(required(languageMap, "code", languageNode));
        String name = scalar(required(languageMap, "name", languageNode));

        Node unitNode = required(top, "unit", root);
        Map<String, Node> unitMap = mapping(unitNode);
        String unitTitle = scalar(required(unitMap, "title", unitNode));
        int unitPosition = integer(required(unitMap, "position", unitNode));

        UnitBundle bundle = new UnitBundle(
                new UnitBundle.LanguageInfo(code, name, line(languageNode)),
                new UnitBundle.UnitInfo(unitTitle, unitPosition, line(unitNode)));

        Node lessonsNode = top.get("lessons");
        if (lessonsNode == null) {
            throw new BadRequestException("Missing top-level key 'lessons'");
        }
        for (Node lessonNode : sequence(lessonsNode)) {
            Map<String, Node> lessonMap = mapping(lessonNode);
            String lessonTitle = scalar(required(lessonMap, "title", lessonNode));
            int lessonPosition = integer(required(lessonMap, "position", lessonNode));
            List<UnitBundle.ExerciseInfo> exercises = new ArrayList<>();
            Node exercisesNode = required(lessonMap, "exercises", lessonNode);
            for (Node exerciseNode : sequence(exercisesNode)) {
                Map<String, Node> exerciseMap = mapping(exerciseNode);
                String type = scalar(required(exerciseMap, "type", exerciseNode));
                String prompt = scalar(required(exerciseMap, "prompt", exerciseNode));
                String answer = scalar(required(exerciseMap, "answer", exerciseNode));
                List<String> choices = new ArrayList<>();
                Node choicesNode = exerciseMap.get("choices");
                if (choicesNode != null) {
                    for (Node choice : sequence(choicesNode)) {
                        choices.add(scalar(choice));
                    }
                }
                String caption = exerciseMap.containsKey("caption") ? scalar(exerciseMap.get("caption")) : null;
                exercises.add(new UnitBundle.ExerciseInfo(type, prompt, answer, choices, caption, line(exerciseNode)));
            }
            bundle.lessons.add(new UnitBundle.LessonInfo(lessonTitle, lessonPosition, line(lessonNode), exercises));
        }
        return bundle;
    }

    static UnitBundle parseCsv(String content) {
        List<List<String>> rows = parseCsvRows(content);
        if (rows.isEmpty()) {
            throw new BadRequestException("The CSV import is empty");
        }
        List<String> header = rows.get(0);
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).trim().toLowerCase(), i);
        }
        for (String requiredColumn : List.of("lesson", "position", "type", "prompt", "answer")) {
            if (!columns.containsKey(requiredColumn)) {
                throw new BadRequestException("CSV header must include column '" + requiredColumn + "'");
            }
        }

        if (rows.size() < 2) {
            throw new BadRequestException("The CSV import has no exercise rows");
        }

        String languageCode = cell(rows.get(1), columns.get("language_code"));
        String languageName = cell(rows.get(1), columns.get("language_name"));
        String unitTitle = cell(rows.get(1), columns.get("unit_title"));
        String unitPositionText = cell(rows.get(1), columns.get("unit_position"));
        if (languageCode == null || unitTitle == null) {
            throw new BadRequestException(
                    "The first row must define language_code, language_name, unit_title and unit_position");
        }
        int unitPosition = parseInteger(unitPositionText, 1);

        UnitBundle bundle = new UnitBundle(
                new UnitBundle.LanguageInfo(languageCode, languageName, 2),
                new UnitBundle.UnitInfo(unitTitle, unitPosition, 2));

        UnitBundle.LessonInfo currentLesson = null;
        int line = 2;
        for (List<String> row : rows.subList(1, rows.size())) {
            String lessonTitle = cell(row, columns.get("lesson"));
            int lessonPosition = parseInteger(cell(row, columns.get("position")), line);
            if (lessonTitle == null || lessonTitle.isBlank()) {
                throw new BadRequestException("Line " + line + ": lesson title must not be blank");
            }
            if (currentLesson == null || !currentLesson.title().equals(lessonTitle)) {
                List<UnitBundle.ExerciseInfo> exercises = new ArrayList<>();
                currentLesson = new UnitBundle.LessonInfo(lessonTitle, lessonPosition, line, exercises);
                bundle.lessons.add(currentLesson);
            }
            String type = cell(row, columns.get("type"));
            String prompt = cell(row, columns.get("prompt"));
            String answer = cell(row, columns.get("answer"));
            List<String> choices = new ArrayList<>();
            String choicesCell = cell(row, columns.get("choices"));
            if (choicesCell != null && !choicesCell.isBlank()) {
                for (String choice : choicesCell.split(";")) {
                    if (!choice.isBlank()) {
                        choices.add(choice.trim());
                    }
                }
            }
            String caption = cell(row, columns.get("caption"));
            currentLesson.exercises().add(
                    new UnitBundle.ExerciseInfo(type, prompt, answer, choices, caption, line));
            line++;
        }
        return bundle;
    }

    private static List<List<String>> parseCsvRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString().trim());
                field.setLength(0);
                if (!row.isEmpty()) {
                    rows.add(row);
                    row = new ArrayList<>();
                }
                line++;
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private static String cell(List<String> row, Integer column) {
        return column == null ? null : cell(row, column.intValue());
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() ? row.get(column) : null;
    }

    private static Map<String, Node> mapping(Node node) {
        if (!(node instanceof MappingNode mapping)) {
            throw new BadRequestException("Expected a mapping at line " + line(node));
        }
        Map<String, Node> result = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            result.put(scalar(tuple.getKeyNode()), tuple.getValueNode());
        }
        return result;
    }

    private static List<Node> sequence(Node node) {
        if (!(node instanceof SequenceNode sequence)) {
            throw new BadRequestException("Expected a list at line " + line(node));
        }
        return sequence.getValue();
    }

    private static Node required(Map<String, Node> map, String key, Node context) {
        Node value = map.get(key);
        if (value == null) {
            throw new BadRequestException("Missing key '" + key + "' at line " + line(context));
        }
        return value;
    }

    private static String scalar(Node node) {
        if (!(node instanceof ScalarNode scalar)) {
            throw new BadRequestException("Expected a text value at line " + line(node));
        }
        String value = scalar.getValue();
        return value == null ? "" : value.trim();
    }

    private static int integer(Node node) {
        String value = scalar(node);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Expected a number at line " + line(node) + " but got '" + value + "'");
        }
    }

    private static int parseInteger(String value, int line) {
        if (value == null) {
            throw new BadRequestException("Line " + line + ": missing position value");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Line " + line + ": '" + value + "' is not a valid position");
        }
    }

    private static int line(Node node) {
        return node.getStartMark().getLine() + 1;
    }
}
