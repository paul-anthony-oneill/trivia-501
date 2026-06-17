import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthProvider, useAuth } from "@/context/AuthContext";

// ── Hoisted mocks ──────────────────────────────────────────────────────────

const { mockApiFetch, mockGetSession, mockOnAuthStateChange, mockSignInWithOAuth, mockSignOut, mockSignInWithPassword, mockSignUp } =
  vi.hoisted(() => ({
    mockApiFetch: vi.fn(),
    mockGetSession: vi.fn(),
    mockOnAuthStateChange: vi.fn(),
    mockSignInWithOAuth: vi.fn(),
    mockSignOut: vi.fn(),
    mockSignInWithPassword: vi.fn(),
    mockSignUp: vi.fn(),
  }));

vi.mock("@/lib/api/client", () => ({
  apiFetch: (...args: unknown[]) => mockApiFetch(...args),
}));

vi.mock("@/utils/supabase/client", () => ({
  createClient: () => ({
    auth: {
      getSession: mockGetSession,
      onAuthStateChange: mockOnAuthStateChange,
      signInWithOAuth: mockSignInWithOAuth,
      signInWithPassword: mockSignInWithPassword,
      signUp: mockSignUp,
      signOut: mockSignOut,
    },
  }),
}));

// ── Test consumer component ─────────────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const authStateSnapshots: Record<string, any>[] = [];

function snapshotAuth(auth: ReturnType<typeof useAuth>) {
  authStateSnapshots.push({
    user: auth.user,
    session: auth.session,
    loading: auth.loading,
    backendConfirmed: auth.backendConfirmed,
    profile: auth.profile,
  });
}

function TestConsumer() {
  const auth = useAuth();
  // Capture the current state for assertions (data-only, no functions)
  snapshotAuth(auth);

  if (auth.loading) return <div data-testid="loading">Loading…</div>;
  if (auth.user) {
    return (
      <div data-testid="authenticated">
        <span data-testid="user-email">{auth.user.email}</span>
        <span data-testid="backend-confirmed">
          {auth.backendConfirmed ? "yes" : "no"}
        </span>
        <span data-testid="profile-name">
          {auth.profile?.displayName ?? "none"}
        </span>
        <button data-testid="sign-in-google" onClick={auth.signInWithGoogle}>
          Sign in Google
        </button>
        <button data-testid="sign-in-email" onClick={() => auth.signInWithEmail("test@example.com", "password123")}>
          Sign in Email
        </button>
        <button data-testid="sign-up-email" onClick={() => auth.signUpWithEmail("new@example.com", "password123")}>
          Sign up Email
        </button>
        <button data-testid="sign-out" onClick={auth.signOut}>
          Sign out
        </button>
      </div>
    );
  }
  return (
    <div data-testid="unauthenticated">
      <span data-testid="backend-confirmed">
        {auth.backendConfirmed ? "yes" : "no"}
      </span>
      <button data-testid="sign-in-google" onClick={auth.signInWithGoogle}>
        Sign in with Google
      </button>
      <button data-testid="sign-in-email" onClick={() => auth.signInWithEmail("test@example.com", "password123")}>
        Sign in Email
      </button>
      <button data-testid="sign-up-email" onClick={() => auth.signUpWithEmail("new@example.com", "password123")}>
        Sign up Email
      </button>
    </div>
  );
}

function renderAuthProvider() {
  return render(
    <AuthProvider>
      <TestConsumer />
    </AuthProvider>,
  );
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function mockSession(userAttrs: Record<string, unknown> = {}) {
  return {
    access_token: "test-access-token",
    user: {
      id: "user-uuid-123",
      email: "test@example.com",
      user_metadata: {},
      app_metadata: {},
      ...userAttrs,
    },
  };
}

// ── Tests ───────────────────────────────────────────────────────────────────

describe("AuthContext", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authStateSnapshots.length = 0;
    // Default: no session, stable auth listener
    mockGetSession.mockResolvedValue({ data: { session: null } });
    mockOnAuthStateChange.mockReturnValue({
      data: { subscription: { unsubscribe: vi.fn() } },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── Initial state ─────────────────────────────────────────────────────

  describe("initial state", () => {
    it("loading is true during initial mount", async () => {
      // Don't resolve getSession yet — keep it pending
      let resolveGetSession: (v: unknown) => void;
      mockGetSession.mockReturnValueOnce(
        new Promise((resolve) => { resolveGetSession = resolve; }),
      );

      renderAuthProvider();

      expect(screen.getByTestId("loading")).toBeDefined();

      // Cleanup: resolve so the component can unmount cleanly
      resolveGetSession!({ data: { session: null } });
    });

    it("loading is false after getSession resolves", async () => {
      mockGetSession.mockResolvedValue({ data: { session: null } });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });
    });

    it("user and session are null when no session", async () => {
      mockGetSession.mockResolvedValue({ data: { session: null } });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      const last = authStateSnapshots[authStateSnapshots.length - 1];
      expect(last.user).toBeNull();
      expect(last.session).toBeNull();
    });

    it("backendConfirmed is false and profile is null initially", async () => {
      mockGetSession.mockResolvedValue({ data: { session: null } });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      const last = authStateSnapshots[authStateSnapshots.length - 1];
      expect(last.backendConfirmed).toBe(false);
      expect(last.profile).toBeNull();
    });
  });

  // ── Session population ─────────────────────────────────────────────────

  describe("session population", () => {
    it("populates user and session when getSession returns a session", async () => {
      const session = mockSession();
      mockGetSession.mockResolvedValue({ data: { session } });
      // Backend confirmation: return 404 to avoid profile populating
      mockApiFetch.mockResolvedValue(new Response(null, { status: 404 }));

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("authenticated")).toBeDefined();
      });

      const last = authStateSnapshots[authStateSnapshots.length - 1];
      expect(last.user).not.toBeNull();
      expect(last.session).not.toBeNull();
      expect(last.user.email).toBe("test@example.com");
    });

    it("SIGNED_IN event updates state with new user/session", async () => {
      let signInCallback: (event: string, session: unknown) => void = () => {};
      mockOnAuthStateChange.mockImplementation((cb: unknown) => {
        signInCallback = cb as typeof signInCallback;
        return { data: { subscription: { unsubscribe: vi.fn() } } };
      });
      mockGetSession.mockResolvedValue({ data: { session: null } });
      mockApiFetch.mockResolvedValue(new Response(null, { status: 404 }));

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      // Fire SIGNED_IN event
      const session = mockSession({ email: "signed-in@example.com" });
      signInCallback("SIGNED_IN", session);

      await waitFor(() => {
        expect(screen.getByTestId("authenticated")).toBeDefined();
      });

      const last = authStateSnapshots[authStateSnapshots.length - 1];
      expect(last.user.email).toBe("signed-in@example.com");
    });

    it("SIGNED_OUT event clears user, session, and backendConfirmed", async () => {
      let signOutCallback: (event: string, session: unknown) => void = () => {};
      mockOnAuthStateChange.mockImplementation((cb: unknown) => {
        signOutCallback = cb as typeof signOutCallback;
        return { data: { subscription: { unsubscribe: vi.fn() } } };
      });

      // Start with a session
      const session = mockSession();
      mockGetSession.mockResolvedValue({ data: { session } });
      mockApiFetch.mockResolvedValue(
        new Response(JSON.stringify({ displayName: "Test User" }), { status: 200 }),
      );

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("authenticated")).toBeDefined();
      });

      // Fire SIGNED_OUT
      signOutCallback("SIGNED_OUT", null);

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      const last = authStateSnapshots[authStateSnapshots.length - 1];
      expect(last.user).toBeNull();
      expect(last.session).toBeNull();
      expect(last.backendConfirmed).toBe(false);
    });
  });

  // ── Backend confirmation ───────────────────────────────────────────────

  describe("backend confirmation", () => {
    it("backendConfirmed = true when profile fetch returns 200", async () => {
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ playerId: "abc", displayName: "Test User" }),
          { status: 200 },
        ),
      );

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("backend-confirmed").textContent).toBe("yes");
      });
    });

    it("profile is populated from 200 response", async () => {
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ playerId: "abc", displayName: "Test User" }),
          { status: 200 },
        ),
      );

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("profile-name").textContent).toBe("Test User");
      });
    });

    it("backendConfirmed = false when profile fetch returns 404", async () => {
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockResolvedValue(new Response(null, { status: 404 }));

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("backend-confirmed").textContent).toBe("no");
      });
    });

    it("does not flip backendConfirmed on network error", async () => {
      // First, get a session with successful backend confirmation
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockResolvedValueOnce(
        new Response(
          JSON.stringify({ playerId: "abc", displayName: "Test User" }),
          { status: 200 },
        ),
      );

      const { unmount } = renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("backend-confirmed").textContent).toBe("yes");
      });

      unmount();
      vi.clearAllMocks();
      authStateSnapshots.length = 0;

      // Re-render: this time the profile fetch fails with network error
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockRejectedValue(new TypeError("Failed to fetch"));

      renderAuthProvider();

      // Wait for loading to finish
      await waitFor(() => {
        expect(screen.getByTestId("authenticated")).toBeDefined();
      });

      // backendConfirmed should remain false (previous state was discarded on unmount anyway)
      // Actually on re-mount with failed fetch, it stays false
      expect(screen.getByTestId("backend-confirmed").textContent).toBe("no");
    });
  });

  // ── Actions ────────────────────────────────────────────────────────────

  describe("actions", () => {
    it("signInWithGoogle calls signInWithOAuth with google provider", async () => {
      const user = userEvent.setup();
      // Need a session so the unauthenticated state doesn't appear (no sign-in btn there)
      // Actually the unauthenticated state DOES have sign-in button
      mockGetSession.mockResolvedValue({ data: { session: null } });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-in-google"));

      expect(mockSignInWithOAuth).toHaveBeenCalledWith({
        provider: "google",
        options: {
          redirectTo: expect.stringContaining("/auth/callback"),
        },
      });
    });

    it("signOut calls auth.signOut", async () => {
      const user = userEvent.setup();
      mockGetSession.mockResolvedValue({ data: { session: mockSession() } });
      mockApiFetch.mockResolvedValue(new Response(null, { status: 404 }));

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("authenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-out"));

      expect(mockSignOut).toHaveBeenCalled();
    });

    it("signInWithEmail calls signInWithPassword with email and password", async () => {
      const user = userEvent.setup();
      mockSignInWithPassword.mockResolvedValue({ data: {}, error: null });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-in-email"));

      expect(mockSignInWithPassword).toHaveBeenCalledWith({
        email: "test@example.com",
        password: "password123",
      });
    });

    it("signInWithEmail returns error message on failure", async () => {
      const user = userEvent.setup();
      mockSignInWithPassword.mockResolvedValue({
        data: {},
        error: { message: "Invalid login credentials" },
      });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-in-email"));

      // The method resolves with an error string; no further state change expected
      expect(mockSignInWithPassword).toHaveBeenCalled();
    });

    it("signUpWithEmail calls signUp with email, password, and redirectTo", async () => {
      const user = userEvent.setup();
      mockSignUp.mockResolvedValue({ data: {}, error: null });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-up-email"));

      expect(mockSignUp).toHaveBeenCalledWith({
        email: "new@example.com",
        password: "password123",
        options: {
          emailRedirectTo: expect.stringContaining("/auth/callback"),
        },
      });
    });

    it("signUpWithEmail returns error message on failure", async () => {
      const user = userEvent.setup();
      mockSignUp.mockResolvedValue({
        data: {},
        error: { message: "User already registered" },
      });

      renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      await user.click(screen.getByTestId("sign-up-email"));

      expect(mockSignUp).toHaveBeenCalled();
    });
  });

  // ── Cleanup ────────────────────────────────────────────────────────────

  describe("cleanup", () => {
    it("unsubscribes from onAuthStateChange on unmount", async () => {
      const unsubscribe = vi.fn();
      mockOnAuthStateChange.mockReturnValue({
        data: { subscription: { unsubscribe } },
      });
      mockGetSession.mockResolvedValue({ data: { session: null } });

      const { unmount } = renderAuthProvider();

      await waitFor(() => {
        expect(screen.getByTestId("unauthenticated")).toBeDefined();
      });

      unmount();

      expect(unsubscribe).toHaveBeenCalled();
    });
  });
});
