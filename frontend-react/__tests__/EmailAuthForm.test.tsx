import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import EmailAuthForm from "@/components/auth/EmailAuthForm";

// ── Hoisted mocks ──────────────────────────────────────────────────────────

const { mockSignInWithEmail, mockSignUpWithEmail } = vi.hoisted(() => ({
  mockSignInWithEmail: vi.fn(),
  mockSignUpWithEmail: vi.fn(),
}));

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    signInWithEmail: mockSignInWithEmail,
    signUpWithEmail: mockSignUpWithEmail,
  }),
}));

// ── Helpers ────────────────────────────────────────────────────────────────

function renderForm(onCancel = vi.fn()) {
  return render(<EmailAuthForm onCancel={onCancel} />);
}

// ── Tests ──────────────────────────────────────────────────────────────────

describe("EmailAuthForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSignInWithEmail.mockResolvedValue({});
    mockSignUpWithEmail.mockResolvedValue({});
  });

  // ── Rendering ─────────────────────────────────────────────────────────

  describe("rendering", () => {
    it("renders in sign-in mode by default", () => {
      renderForm();

      expect(screen.getByLabelText("Email")).toBeDefined();
      expect(screen.getByLabelText("Password")).toBeDefined();
      expect(screen.getByRole("button", { name: "Sign in" })).toBeDefined();
      expect(screen.getByText("Create account")).toBeDefined();
    });

    it("does not show confirm password in sign-in mode", () => {
      renderForm();

      expect(screen.queryByLabelText("Confirm password")).toBeNull();
    });

    it("shows Cancel button", () => {
      renderForm();

      expect(screen.getByText("Cancel")).toBeDefined();
    });
  });

  // ── Mode switching ────────────────────────────────────────────────────

  describe("mode switching", () => {
    it("switches to sign-up mode when 'Create account' is clicked", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.click(screen.getByText("Create account"));

      expect(screen.getByLabelText("Confirm password")).toBeDefined();
      expect(screen.getByRole("button", { name: "Create account" })).toBeDefined();
      expect(screen.getByText("Sign in instead")).toBeDefined();
    });

    it("switches back to sign-in mode when 'Sign in instead' is clicked", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.click(screen.getByText("Create account"));
      await user.click(screen.getByText("Sign in instead"));

      expect(screen.queryByLabelText("Confirm password")).toBeNull();
      expect(screen.getByRole("button", { name: "Sign in" })).toBeDefined();
    });

    it("clears error when switching mode", async () => {
      const user = userEvent.setup();
      mockSignInWithEmail.mockResolvedValue({ error: "Bad credentials" });
      renderForm();

      // Submit to trigger error — fill required fields first
      await user.type(screen.getByLabelText("Email"), "test@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.click(screen.getByRole("button", { name: "Sign in" }));
      await waitFor(() => {
        expect(screen.getByText("Bad credentials")).toBeDefined();
      });

      // Switch to sign-up — error should disappear
      await user.click(screen.getByText("Create account"));

      expect(screen.queryByText("Bad credentials")).toBeNull();
    });
  });

  // ── Password mismatch ─────────────────────────────────────────────────

  describe("password mismatch validation", () => {
    it("shows error when passwords do not match in sign-up mode", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.click(screen.getByText("Create account"));
      await user.type(screen.getByLabelText("Email"), "test@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.type(screen.getByLabelText("Confirm password"), "different");
      await user.click(screen.getByRole("button", { name: "Create account" }));

      expect(screen.getByText("Passwords do not match")).toBeDefined();
      expect(mockSignUpWithEmail).not.toHaveBeenCalled();
    });
  });

  // ── Submit — sign in ──────────────────────────────────────────────────

  describe("sign-in submit", () => {
    it("calls signInWithEmail with form values", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.type(screen.getByLabelText("Email"), "test@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.click(screen.getByRole("button", { name: "Sign in" }));

      expect(mockSignInWithEmail).toHaveBeenCalledWith("test@example.com", "password123");
    });

    it("displays error message on failure", async () => {
      const user = userEvent.setup();
      mockSignInWithEmail.mockResolvedValue({ error: "Invalid login credentials" });
      renderForm();

      await user.type(screen.getByLabelText("Email"), "test@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.click(screen.getByRole("button", { name: "Sign in" }));

      await waitFor(() => {
        expect(screen.getByText("Invalid login credentials")).toBeDefined();
      });
    });

    it("disables submit button while in flight", async () => {
      const user = userEvent.setup();
      // Never resolves — keeps the button disabled
      mockSignInWithEmail.mockReturnValue(new Promise(() => {}));
      renderForm();

      await user.type(screen.getByLabelText("Email"), "test@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.click(screen.getByRole("button", { name: "Sign in" }));

      expect(screen.getByText("Please wait…")).toBeDefined();
    });
  });

  // ── Submit — sign up ──────────────────────────────────────────────────

  describe("sign-up submit", () => {
    it("calls signUpWithEmail with form values", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.click(screen.getByText("Create account"));
      await user.type(screen.getByLabelText("Email"), "new@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.type(screen.getByLabelText("Confirm password"), "password123");
      await user.click(screen.getByRole("button", { name: "Create account" }));

      expect(mockSignUpWithEmail).toHaveBeenCalledWith("new@example.com", "password123");
    });

    it("displays error on sign-up failure", async () => {
      const user = userEvent.setup();
      mockSignUpWithEmail.mockResolvedValue({ error: "User already registered" });
      renderForm();

      await user.click(screen.getByText("Create account"));
      await user.type(screen.getByLabelText("Email"), "new@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.type(screen.getByLabelText("Confirm password"), "password123");
      await user.click(screen.getByRole("button", { name: "Create account" }));

      await waitFor(() => {
        expect(screen.getByText("User already registered")).toBeDefined();
      });
    });

    it("displays success message on successful sign-up", async () => {
      const user = userEvent.setup();
      mockSignUpWithEmail.mockResolvedValue({});
      renderForm();

      await user.click(screen.getByText("Create account"));
      await user.type(screen.getByLabelText("Email"), "new@example.com");
      await user.type(screen.getByLabelText("Password"), "password123");
      await user.type(screen.getByLabelText("Confirm password"), "password123");
      await user.click(screen.getByRole("button", { name: "Create account" }));

      await waitFor(() => {
        expect(screen.getByText("Check your email for a confirmation link.")).toBeDefined();
      });
    });
  });

  // ── Cancel ────────────────────────────────────────────────────────────

  describe("cancel", () => {
    it("calls onCancel when Cancel button is clicked", async () => {
      const user = userEvent.setup();
      const onCancel = vi.fn();
      renderForm(onCancel);

      await user.click(screen.getByText("Cancel"));

      expect(onCancel).toHaveBeenCalled();
    });
  });
});
