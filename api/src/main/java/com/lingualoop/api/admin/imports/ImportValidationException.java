package com.lingualoop.api.admin.imports;

import java.util.ArrayList;
import java.util.List;

/** Carries all validation failures; the import is atomic — any error aborts the whole bundle. */
public class ImportValidationException extends RuntimeException {

    private final List<RowError> errors;

    public ImportValidationException(List<RowError> errors) {
        super("Import bundle has " + errors.size() + " invalid row(s)");
        this.errors = new ArrayList<>(errors);
    }

    public List<RowError> getErrors() {
        return errors;
    }
}
