import { createContext, useContext, useState, type ReactNode } from "react";
import {
  LEARNER_KEY,
  TOKEN_KEY,
  type AuthResponse,
  type LearnerDto,
} from "../api/client";

interface AuthContextValue {
  token: string | null;
  learner: LearnerDto | null;
  applyAuth: (auth: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readLearner(): LearnerDto | null {
  try {
    const raw = localStorage.getItem(LEARNER_KEY);
    return raw ? (JSON.parse(raw) as LearnerDto) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [learner, setLearner] = useState<LearnerDto | null>(readLearner);

  const applyAuth = (auth: AuthResponse) => {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(LEARNER_KEY, JSON.stringify(auth.learner));
    setToken(auth.token);
    setLearner(auth.learner);
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(LEARNER_KEY);
    setToken(null);
    setLearner(null);
  };

  return (
    <AuthContext.Provider value={{ token, learner, applyAuth, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return value;
}
