import { useCallback, useEffect, useState } from "react";

interface Language {
  id: number;
  code: string;
  name: string;
  unitCount: number;
}

interface LessonSummary {
  id: number;
  title: string;
  position: number;
  exerciseCount: number;
}

interface Unit {
  id: number;
  languageId: number;
  title: string;
  position: number;
  lessons: LessonSummary[];
}

interface Exercise {
  id: number;
  type: "TRANSLATE" | "MULTIPLE_CHOICE" | "LISTEN";
  prompt: string;
  answer: string;
  choices: string[];
  caption: string | null;
  audioAsset: { id: number; url: string; mimeType: string; durationMs: number } | null;
}

interface Lesson {
  id: number;
  unitId: number;
  title: string;
  position: number;
  exercises: Exercise[];
}

interface AuthResponse {
  token: string;
  learner: { id: number; email: string; displayName: string; timezone: string };
}

interface QueueItem {
  exerciseId: number;
  type: string;
  prompt: string;
  answer: string;
  choices: string[];
  caption: string | null;
  lessonTitle: string;
  unitTitle: string;
  review: { repetitions: number; intervalDays: number; easeFactor: number; lastGrade: number | null };
}

interface Stats {
  attemptsTotal: number;
  exercisesStudied: number;
  exercisesMastered: number;
  averageGrade: number | null;
  dueNow: number;
  sessionsCompleted: number;
  streak: { currentDays: number; longestDays: number; lastActiveDate: string | null };
}

interface Session {
  id: number;
  lessonId: number;
  variantKey: string;
  startedAt: string;
  exerciseCount: number;
}

const TOKEN_KEY = "lingualoop.token";

async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail ?? `${response.status} ${response.statusText}`);
  }
  return response.json();
}

function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [authError, setAuthError] = useState<string | null>(null);

  const [languages, setLanguages] = useState<Language[]>([]);
  const [units, setUnits] = useState<Unit[] | null>(null);
  const [unit, setUnit] = useState<Unit | null>(null);
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [queue, setQueue] = useState<QueueItem[] | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadLanguages = useCallback(async () => {
    try {
      setLanguages(await api<Language[]>("/api/languages"));
    } catch (err) {
      setError((err as Error).message);
    }
  }, []);

  useEffect(() => {
    loadLanguages();
  }, [loadLanguages]);

  const register = async () => {
    try {
      const auth = await api<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({ email, password, displayName: displayName || email }),
      });
      finishAuth(auth);
    } catch (err) {
      setAuthError((err as Error).message);
    }
  };

  const login = async () => {
    try {
      const auth = await api<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      finishAuth(auth);
    } catch (err) {
      setAuthError((err as Error).message);
    }
  };

  const finishAuth = (auth: AuthResponse) => {
    localStorage.setItem(TOKEN_KEY, auth.token);
    setToken(auth.token);
    setDisplayName(auth.learner.displayName);
    setAuthError(null);
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setSession(null);
    setQueue(null);
    setStats(null);
  };

  const openLanguage = async (languageId: number) => {
    setUnits(await api<Unit[]>(`/api/languages/${languageId}/units`));
    setUnit(null);
    setLesson(null);
    setSession(null);
  };

  const openUnit = async (unitId: number) => {
    setUnit(await api<Unit>(`/api/units/${unitId}`));
    setLesson(null);
    setSession(null);
  };

  const openLesson = async (lessonId: number) => {
    setSession(null);
    setLesson(await api<Lesson>(`/api/lessons/${lessonId}`));
  };

  const startSession = async (lessonId: number) => {
    setBusy(true);
    try {
      setSession(await api<Session>("/api/sessions", {
        method: "POST",
        body: JSON.stringify({ lessonId }),
      }));
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const submitGrade = async (exerciseId: number, grade: number) => {
    if (!session) return;
    setBusy(true);
    try {
      await api(`/api/sessions/${session.id}/attempts`, {
        method: "POST",
        body: JSON.stringify({ exerciseId, grade, latencyMs: 1500, hintShown: false }),
      });
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const completeSession = async () => {
    if (!session) return;
    await api(`/api/sessions/${session.id}/complete`, { method: "POST" });
    setSession(null);
    setStats(await api<Stats>("/api/learners/me/stats"));
  };

  const loadQueue = async () => {
    const response = await api<{ items: QueueItem[]; dueCount: number }>("/api/learners/me/queue");
    setQueue(response.items);
  };

  const loadStats = async () => {
    setStats(await api<Stats>("/api/learners/me/stats"));
  };

  return (
    <>
      <header>
        <h1>LinguaLoop</h1>
        {token ? (
          <div>
            <span>{displayName || "learner"}</span>{" "}
            <button className="secondary" onClick={logout}>Log out</button>
          </div>
        ) : (
          <span>Learn · Listen · Retain</span>
        )}
      </header>
      <main>
        {!token && (
          <section>
            <h2>Sign in or create an account</h2>
            <input placeholder="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            <input placeholder="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <input placeholder="display name (register only)" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            <button onClick={login}>Log in</button>{" "}
            <button className="secondary" onClick={register}>Register</button>
            {authError && <div className="error">{authError}</div>}
          </section>
        )}

        {error && <section><div className="error">{error}</div></section>}

        <section>
          <h2>Languages</h2>
          {languages.length === 0 && <p className="muted">No content seeded yet — run tools/seed.py.</p>}
          <ul>
            {languages.map((language) => (
              <li key={language.id}>
                <span className="clickable" onClick={() => openLanguage(language.id)}>
                  {language.name} ({language.code})
                </span>{" "}
                <span className="muted">{language.unitCount} units</span>
              </li>
            ))}
          </ul>

          {units && (
            <>
              <h2>Units</h2>
              <ul>
                {units.map((u) => (
                  <li key={u.id}>
                    <span className="clickable" onClick={() => openUnit(u.id)}>{u.title}</span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {unit && (
            <>
              <h2>{unit.title}</h2>
              <ul>
                {unit.lessons.map((l) => (
                  <li key={l.id}>
                    <span className="clickable" onClick={() => openLesson(l.id)}>
                      {l.title}
                    </span>{" "}
                    <span className="muted">{l.exerciseCount} exercises</span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {lesson && (
            <>
              <h2>{lesson.title}</h2>
              {!session ? (
                <button onClick={() => startSession(lesson.id)} disabled={busy || !token}>
                  Start session
                </button>
              ) : (
                <p className="muted">
                  Session #{session.id} in progress (variant: {session.variantKey}). Grade each exercise 0–5.
                </p>
              )}
              {lesson.exercises.map((exercise) => (
                <div className="exercise" key={exercise.id}>
                  <strong>
                    [{exercise.type}] {exercise.prompt}
                  </strong>
                  {exercise.choices.length > 0 && (
                    <ul>{exercise.choices.map((choice) => <li key={choice}>{choice}</li>)}</ul>
                  )}
                  {exercise.audioAsset && (
                    <div>
                      <audio controls src={exercise.audioAsset.url} />
                    </div>
                  )}
                  <details>
                    <summary>Answer</summary>
                    {exercise.answer}
                  </details>
                  {session && (
                    <div className="grade-buttons">
                      {[0, 1, 2, 3, 4, 5].map((grade) => (
                        <button key={grade} disabled={busy} onClick={() => submitGrade(exercise.id, grade)}>
                          {grade}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
              {session && (
                <button className="secondary" onClick={completeSession}>
                  Complete session
                </button>
              )}
            </>
          )}
        </section>

        {token && (
          <section>
            <h2>My practice</h2>
            <button onClick={loadQueue}>Load review queue</button>{" "}
            <button className="secondary" onClick={loadStats}>Load stats</button>
            {queue && (
              <>
                <h2>Review queue ({queue.length} due)</h2>
                {queue.length === 0 && <p className="muted">Nothing due right now. Come back later!</p>}
                {queue.map((item) => (
                  <div className="exercise" key={item.exerciseId}>
                    <strong>
                      [{item.type}] {item.prompt}
                    </strong>{" "}
                    <span className="muted">
                      {item.unitTitle} · {item.lessonTitle} · reps {item.review.repetitions}
                    </span>
                    <details>
                      <summary>Answer</summary>
                      {item.answer}
                    </details>
                  </div>
                ))}
              </>
            )}
            {stats && (
              <>
                <h2>Stats</h2>
                <ul>
                  <li>Attempts: {stats.attemptsTotal}</li>
                  <li>Exercises studied: {stats.exercisesStudied}</li>
                  <li>Mastered (interval ≥ 21d): {stats.exercisesMastered}</li>
                  <li>Average grade: {stats.averageGrade?.toFixed(2) ?? "—"}</li>
                  <li>Due now: {stats.dueNow}</li>
                  <li>Streak: {stats.streak.currentDays} day(s) (longest {stats.streak.longestDays})</li>
                </ul>
              </>
            )}
          </section>
        )}
      </main>
    </>
  );
}

export default App;
