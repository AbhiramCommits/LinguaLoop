import { useState } from "react";
import { useExperimentResults, useExperiments } from "../api/queries";
import type { VariantResultsDto } from "../api/client";

function formatRate(rate: number | null): string {
  return rate == null ? "—" : `${(rate * 100).toFixed(1)}%`;
}

function formatP(p: number | null): string {
  if (p == null) {
    return "—";
  }
  if (p < 0.001) {
    return "<0.001";
  }
  return p.toFixed(3);
}

function formatAccuracy(value: number | null): string {
  return value == null ? "—" : value.toFixed(2);
}

function formatItems(value: number | null): string {
  return value == null ? "—" : value.toFixed(1);
}

function ResultsTable({ results }: { results: VariantResultsDto[] }) {
  return (
    <table className="results-table">
      <thead>
        <tr>
          <th scope="col">Variant</th>
          <th scope="col">n</th>
          <th scope="col">D1 return</th>
          <th scope="col">D1 p-value</th>
          <th scope="col">D7 return</th>
          <th scope="col">D7 p-value</th>
          <th scope="col">2nd-exposure accuracy</th>
          <th scope="col">Items / session</th>
        </tr>
      </thead>
      <tbody>
        {results.map((row) => (
          <tr key={row.key}>
            <th scope="row">
              {row.key}
              {row.control && <span className="control-badge">control</span>}
            </th>
            <td>{row.n}</td>
            <td>{formatRate(row.d1ReturnRate)}</td>
            <td>{formatP(row.d1PValue)}</td>
            <td>{formatRate(row.d7ReturnRate)}</td>
            <td>{formatP(row.d7PValue)}</td>
            <td>{formatAccuracy(row.meanSecondAttemptAccuracy)}</td>
            <td>{formatItems(row.meanItemsPerSession)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ExperimentPanel({ experimentKey }: { experimentKey: string }) {
  const [includeSimulated, setIncludeSimulated] = useState(false);
  const results = useExperimentResults(experimentKey, includeSimulated);

  return (
    <section aria-labelledby={`results-${experimentKey}`}>
      <h2 id={`results-${experimentKey}`}>{experimentKey}</h2>
      <div className="simulated-toggle">
        <input
          id={`simulated-${experimentKey}`}
          type="checkbox"
          checked={includeSimulated}
          onChange={(event) => setIncludeSimulated(event.target.checked)}
        />
        <label htmlFor={`simulated-${experimentKey}`}>Include simulated learners</label>
      </div>

      {results.isLoading && <p className="muted">Loading results…</p>}
      {results.isError && (
        <p className="error" role="alert">
          {results.error instanceof Error ? results.error.message : "Could not load results"}
        </p>
      )}
      {results.data && (
        <>
          <ResultsTable results={results.data.variants} />
          {results.data.variants.some((row) => !row.enoughData) && (
            <p className="muted not-enough-data" role="status">
              Not enough data for variants below 30 learners — interpret p-values cautiously.
            </p>
          )}
          {includeSimulated && (
            <p className="simulated-warning" role="status">
              Showing simulated learners — these numbers are synthetic training data, not real
              learner measurements. See the simulator documentation for the forgetting model.
            </p>
          )}
        </>
      )}
    </section>
  );
}

export function ExperimentsScreen() {
  const experiments = useExperiments();

  return (
    <section aria-labelledby="experiments-heading">
      <h2 id="experiments-heading">Experiments</h2>
      {experiments.isLoading && <p className="muted">Loading experiments…</p>}
      {experiments.isError && (
        <p className="error" role="alert">
          {experiments.error instanceof Error ? experiments.error.message : "Could not load experiments"}
        </p>
      )}
      {experiments.data?.map((experiment) => (
        <div key={experiment.key} className="experiment-block">
          <p className="muted">
            {experiment.description} · status: {experiment.status}
          </p>
          {experiment.status === "DRAFT" ? (
            <p className="muted">Results are not available for DRAFT experiments.</p>
          ) : (
            <ExperimentPanel experimentKey={experiment.key} />
          )}
        </div>
      ))}
    </section>
  );
}
