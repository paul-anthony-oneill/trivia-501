"use client";

import { createContext, useContext, useEffect, useState, useCallback, useMemo } from "react";
import { type User, type Session } from "@supabase/supabase-js";
import { createClient } from "@/utils/supabase/client";
import { apiFetch } from "@/lib/api/client";

interface PlayerProfile {
  playerId: string;
  displayName: string | null;
  avatarUrl: string | null;
  gamesPlayed: number;
  gamesWon: number;
  bestScore: number | null;
}

interface AuthState {
  user: User | null;
  session: Session | null;
  loading: boolean;
  /** True when the backend confirms it sees the same JWT identity as Supabase. */
  backendConfirmed: boolean;
  /** Player profile from the backend (null for anonymous users). */
  profile: PlayerProfile | null;
  signInWithGoogle: () => Promise<void>;
  signInWithEmail: (email: string, password: string) => Promise<{ error?: string }>;
  signUpWithEmail: (email: string, password: string) => Promise<{ error?: string }>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState>({
  user: null,
  session: null,
  loading: true,
  backendConfirmed: false,
  profile: null,
  signInWithGoogle: async () => {},
  signInWithEmail: async () => ({}),
  signUpWithEmail: async () => ({}),
  signOut: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [backendConfirmed, setBackendConfirmed] = useState(false);
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  // createBrowserClient throws synchronously when NEXT_PUBLIC_SUPABASE_* vars are
  // absent (e.g. during SSR prerendering on CI without those vars). Catch and fall
  // back to null so the build doesn't crash; auth simply stays unavailable.
  const supabase = useMemo(() => {
    try { return createClient(); } catch { return null; }
  }, []);

  // Confirm backend auth state whenever the Supabase session changes
  useEffect(() => {
    if (!session?.access_token) {
      setBackendConfirmed(false);
      setProfile(null);
      return;
    }

    let cancelled = false;

    async function verifyBackend() {
      try {
        const res = await apiFetch("/api/freeplay/profile");
        if (!cancelled) {
          if (res.ok) {
            const data = await res.json();
            setProfile(data);
            setBackendConfirmed(true);
          } else {
            // 404 = backend doesn't recognise this JWT (expired, missing secret, etc.)
            setProfile(null);
            setBackendConfirmed(false);
          }
        }
      } catch {
        // Network error — don't flip to false on transient failures
        if (!cancelled) {
          // Keep previous state on transient errors
        }
      }
    }

    verifyBackend();
    return () => { cancelled = true; };
  }, [session?.access_token]);

  useEffect(() => {
    if (!supabase) { setLoading(false); return; }

    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setUser(data.session?.user ?? null);
      setLoading(false);
    });

    const { data: subscription } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      setUser(session?.user ?? null);
    });

    return () => subscription.subscription.unsubscribe();
  }, [supabase]);

  const signInWithGoogle = useCallback(async () => {
    if (!supabase) return;
    await supabase.auth.signInWithOAuth({
      provider: "google",
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    });
  }, [supabase]);

  const signInWithEmail = useCallback(
    async (email: string, password: string): Promise<{ error?: string }> => {
      if (!supabase) return { error: "Supabase client not available" };
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) return { error: error.message };
      return {};
    },
    [supabase],
  );

  const signUpWithEmail = useCallback(
    async (email: string, password: string): Promise<{ error?: string }> => {
      if (!supabase) return { error: "Supabase client not available" };
      const { error } = await supabase.auth.signUp({
        email,
        password,
        options: { emailRedirectTo: `${window.location.origin}/auth/callback` },
      });
      if (error) return { error: error.message };
      return {};
    },
    [supabase],
  );

  const signOut = useCallback(async () => {
    if (!supabase) return;
    await supabase.auth.signOut();
  }, [supabase]);

  return (
    <AuthContext.Provider value={{ user, session, loading, backendConfirmed, profile, signInWithGoogle, signInWithEmail, signUpWithEmail, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
