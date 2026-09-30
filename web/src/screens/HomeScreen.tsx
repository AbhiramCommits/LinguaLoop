import { Link } from "react-router-dom";
import { useQueue, useStats } from "../api/queries";
import { MasteryRing } from "../components/MasteryRing";
import { useAuth } from "../auth/AuthContext";

export function HomeScreen() {
  const { learner } = useAuth();
  const queue = useQueue();
  const stats = useStats();

  const streak = stats.data?.streak;
  const unit = stats.data?.unit;

  return (
    <>
      <section aria-labelledby="greeting-heading">
        <h2 id="greeting-heading">Hello, {learner?.displayName ?? "learner"}</h2>
        <div className="home-overview">
          <div className="streak-card">
            <span className="streak-flame" aria-hidden="true">
              🔥
            </span>
            <div>
              <p className="streak-value">{streak?.currentDays ?? 0} day streak</p>
              <p className="muted">longest {streak?.longestDays ?? 0}</p>
            </div>
          </div>
          <div className="due-card">
            <p className="due-value">{stats.data?.dueNow ?? 0}</p>
            <p className="muted">due now</p>
          </div>
        </div>
        {stats.isError && (
          <p className="error" role="alert">
            {stats.error instanceof Error ? stats.error.message : "Could not load stats"}
          </p>
        )}
      </section>

      {unit && (
        <section aria-labelledby="mastery-heading">
          <h2 id="mastery-heading">Mastery — {unit.title}</h2>
          <div className="mastery-rings">
            <MasteryRing label={`Unit ${unit.title}`} percent={unit.mastery * 100} size={120} />
            {unit.lessons.map((lesson) => (
              <MasteryRing
                key={lesson.lessonId}
                label={`Lesson ${lesson.title}`}
                percent={lesson.mastery * 100}
              />
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="queue-heading">
        <h2 id="queue-heading">Today&apos;s review ({queue.data?.dueCount ?? 0} due)</h2>
        {queue.isLoading && <p className="muted">Loading your queue…</p>}
        {queue.isError && (
          <p className="error" role="alert">
            {queue.error instanceof Error ? queue.error.message : "Could not load the queue"}
          </p>
        )}
        {queue.data && queue.data.items.length === 0 && (
          <p className="muted">Nothing due right now. Great job — come back later!</p>
        )}
        <ul className="queue-list">
          {queue.data?.items.map((item) => (
            <li key={`${item.exerciseId}-${item.new ? "new" : "review"}`}>
              <div>
                <span className={`type-badge type-${item.type.toLowerCase()}`}>{item.type}</span>{" "}
                <strong>{item.prompt}</strong>
                <p className="muted">
                  {item.unitTitle} · {item.lessonTitle} ·{" "}
                  {item.new ? "new" : `reps ${item.review?.repetitions ?? 0}`}
                </p>
              </div>
              <Link to={`/lessons/${item.lessonId}`} className="button-link">
                Review
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
