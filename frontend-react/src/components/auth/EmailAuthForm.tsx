"use client";

import { useState, useRef } from "react";
import { useAuth } from "@/context/AuthContext";

type Mode = "signIn" | "signUp";

export default function EmailAuthForm({ onCancel }: { onCancel: () => void }) {
  const { signInWithEmail, signUpWithEmail } = useAuth();
  const [mode, setMode] = useState<Mode>("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);

  function switchMode(newMode: Mode) {
    setMode(newMode);
    setError(null);
    setSuccess(null);
    emailRef.current?.focus();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (mode === "signUp" && password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "signIn") {
        const result = await signInWithEmail(email, password);
        if (result.error) setError(result.error);
      } else {
        const result = await signUpWithEmail(email, password);
        if (result.error) {
          setError(result.error);
        } else {
          setSuccess("Check your email for a confirmation link.");
        }
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 min-w-64">
      <div>
        <label htmlFor="email" className="kicker">
          Email
        </label>
        <input
          ref={emailRef}
          id="email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full px-3 py-2 bg-bg border border-line rounded text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
          placeholder="you@example.com"
        />
      </div>

      <div>
        <label htmlFor="password" className="kicker">
          Password
        </label>
        <input
          id="password"
          type="password"
          required
          autoComplete={mode === "signIn" ? "current-password" : "new-password"}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full px-3 py-2 bg-bg border border-line rounded text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
          placeholder="••••••••"
        />
      </div>

      {mode === "signUp" && (
        <div>
          <label htmlFor="confirm-password" className="kicker">
            Confirm password
          </label>
          <input
            id="confirm-password"
            type="password"
            required
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full px-3 py-2 bg-bg border border-line rounded text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
            placeholder="••••••••"
          />
        </div>
      )}

      {error && (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      )}

      {success && (
        <p className="text-sm text-ok" role="status">
          {success}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="btn-ghost px-4 py-2 disabled:opacity-50"
      >
        {submitting
          ? "Please wait…"
          : mode === "signIn"
            ? "Sign in"
            : "Create account"}
      </button>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onCancel}
          className="kicker hover:text-ink transition-colors"
        >
          Cancel
        </button>

        {mode === "signIn" ? (
          <button
            type="button"
            onClick={() => switchMode("signUp")}
            className="kicker hover:text-ink transition-colors"
          >
            Create account
          </button>
        ) : (
          <button
            type="button"
            onClick={() => switchMode("signIn")}
            className="kicker hover:text-ink transition-colors"
          >
            Sign in instead
          </button>
        )}
      </div>
    </form>
  );
}
