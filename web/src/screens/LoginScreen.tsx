import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { type AuthResponse } from "../api/client";
import { useLogin, useRegister } from "../api/queries";
import { useAuth } from "../auth/AuthContext";

export function LoginScreen() {
  const navigate = useNavigate();
  const { applyAuth } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const register = useRegister();
  const login = useLogin();

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const options = {
      onSuccess: (auth: AuthResponse) => {
        applyAuth(auth);
        navigate("/");
      },
      onError: (err: Error) => setError(err.message),
    };
    if (mode === "login") {
      login.mutate({ email, password }, options);
    } else {
      register.mutate({ email, password, displayName: displayName || email }, options);
    }
  };

  const switchMode = (next: "login" | "register") => {
    setMode(next);
    setError(null);
  };

  return (
    <section className="auth-panel">
      <h2>{mode === "login" ? "Log in" : "Create an account"}</h2>
      <div className="mode-switch" role="group" aria-label="Authentication mode">
        <button
          type="button"
          className={mode === "login" ? "primary" : "secondary"}
          aria-pressed={mode === "login"}
          onClick={() => switchMode("login")}
        >
          Log in
        </button>
        <button
          type="button"
          className={mode === "register" ? "primary" : "secondary"}
          aria-pressed={mode === "register"}
          onClick={() => switchMode("register")}
        >
          Register
        </button>
      </div>

      <form onSubmit={submit}>
        <div>
          <label htmlFor="auth-email">Email</label>
          <input
            id="auth-email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="auth-password">Password</label>
          <input
            id="auth-password"
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            required
            minLength={8}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        {mode === "register" && (
          <div>
            <label htmlFor="auth-display-name">Display name</label>
            <input
              id="auth-display-name"
              type="text"
              autoComplete="name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </div>
        )}
        <button type="submit" disabled={login.isPending || register.isPending}>
          {mode === "login" ? "Log in" : "Register"}
        </button>
      </form>

      <div className="auth-error" role="alert" aria-live="assertive">
        {error}
      </div>
    </section>
  );
}
