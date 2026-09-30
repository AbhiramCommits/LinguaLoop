package com.lingualoop.api.admin.imports;

/** A single import failure, tied to a source line. */
public record RowError(int line, String message) {
}
