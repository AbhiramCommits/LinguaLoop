import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { OfflineBanner } from "./offline/OfflineBanner";
import { BrowseScreen } from "./screens/BrowseScreen";
import { HomeScreen } from "./screens/HomeScreen";
import { LessonScreen } from "./screens/LessonScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { SessionSummaryScreen } from "./screens/SessionSummaryScreen";
import type { ReactNode } from "react";

function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  const { token, learner, logout } = useAuth();

  return (
    <>
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header>
        <h1>
          <Link to="/" aria-label="LinguaLoop home">
            LinguaLoop
          </Link>
        </h1>
        <nav aria-label="Primary">
          <ul className="header-nav">
            {token && (
              <>
                <li>
                  <Link to="/">Home</Link>
                </li>
                <li>
                  <Link to="/browse">Browse</Link>
                </li>
                <li>
                  <span className="header-learner">{learner?.displayName}</span>
                </li>
                <li>
                  <button type="button" className="header-button" onClick={logout}>
                    Log out
                  </button>
                </li>
              </>
            )}
          </ul>
        </nav>
      </header>
      <OfflineBanner />
      <main id="main-content" tabIndex={-1}>
        <Routes>
          <Route
            path="/login"
            element={token ? <Navigate to="/" replace /> : <LoginScreen />}
          />
          <Route
            path="/"
            element={
              <RequireAuth>
                <HomeScreen />
              </RequireAuth>
            }
          />
          <Route
            path="/browse"
            element={
              <RequireAuth>
                <BrowseScreen />
              </RequireAuth>
            }
          />
          <Route
            path="/lessons/:lessonId"
            element={
              <RequireAuth>
                <LessonScreen />
              </RequireAuth>
            }
          />
          <Route
            path="/sessions/:sessionId/summary"
            element={
              <RequireAuth>
                <SessionSummaryScreen />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
