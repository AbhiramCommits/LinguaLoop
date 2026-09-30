import { useState } from "react";
import { Link } from "react-router-dom";
import { useLanguages, useUnit, useUnits } from "../api/queries";

export function BrowseScreen() {
  const languages = useLanguages();
  const [languageId, setLanguageId] = useState<number | null>(null);
  const [unitId, setUnitId] = useState<number | null>(null);
  const units = useUnits(languageId);
  const unit = useUnit(unitId);

  return (
    <section aria-labelledby="browse-heading">
      <h2 id="browse-heading">Browse content</h2>

      <h3>Languages</h3>
      {languages.isLoading && <p className="muted">Loading…</p>}
      {languages.isError && (
        <p className="error" role="alert">
          {languages.error instanceof Error ? languages.error.message : "Could not load languages"}
        </p>
      )}
      <ul className="browse-list">
        {languages.data?.map((language) => (
          <li key={language.id}>
            <button
              type="button"
              className="secondary"
              aria-pressed={languageId === language.id}
              onClick={() => {
                setLanguageId(language.id);
                setUnitId(null);
              }}
            >
              {language.name} ({language.code})
            </button>
          </li>
        ))}
      </ul>

      {units.data && (
        <>
          <h3>Units</h3>
          <ul className="browse-list">
            {units.data.map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  className="secondary"
                  aria-pressed={unitId === entry.id}
                  onClick={() => setUnitId(entry.id)}
                >
                  {entry.title}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {unit.data && (
        <>
          <h3>{unit.data.title}</h3>
          <ul className="browse-list">
            {unit.data.lessons.map((lesson) => (
              <li key={lesson.id}>
                <Link to={`/lessons/${lesson.id}`} className="button-link">
                  {lesson.title}
                </Link>{" "}
                <span className="muted">{lesson.exerciseCount} exercises</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
