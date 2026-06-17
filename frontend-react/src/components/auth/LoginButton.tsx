"use client";

import { useState } from "react";
import { useAuth } from "@/context/AuthContext";
import EmailAuthForm from "@/components/auth/EmailAuthForm";

export default function LoginButton() {
  const { user, loading, backendConfirmed, signInWithGoogle, signOut } = useAuth();
  const [showEmailForm, setShowEmailForm] = useState(false);

  if (loading) {
    return (
      <button className="kicker px-4 py-2 opacity-50" disabled>
        Loading…
      </button>
    );
  }

  if (user) {
    const authGap = !backendConfirmed;
    return (
      <div className="flex items-center gap-3">
        <div className="relative">
          {user.user_metadata?.avatar_url && (
            <img
              src={user.user_metadata.avatar_url}
              alt=""
              className={`w-7 h-7 rounded-full ${authGap ? "ring-2 ring-gold/60" : ""}`}
            />
          )}
          {/* Gold dot when Supabase has a session but backend doesn't see it */}
          {authGap && (
            <span
              className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-gold rounded-full border border-bg"
              title="Backend auth not confirmed — your games may not be saved"
            />
          )}
        </div>
        <span className="kicker max-w-32 truncate hidden sm:block">
          {user.user_metadata?.full_name || user.email}
        </span>
        <button onClick={signOut} className="kicker hover:text-ink transition-colors">
          Sign out
        </button>
      </div>
    );
  }

  if (showEmailForm) {
    return (
      <EmailAuthForm onCancel={() => setShowEmailForm(false)} />
    );
  }

  return (
    <div className="flex items-center gap-2">
      <button onClick={signInWithGoogle} className="btn-ghost px-4 py-2">
        Sign in with Google
      </button>
      <button onClick={() => setShowEmailForm(true)} className="btn-ghost px-4 py-2">
        Sign in with Email
      </button>
    </div>
  );
}
