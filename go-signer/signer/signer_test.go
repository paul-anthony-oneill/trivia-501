package signer_test

import (
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/paul-anthony-oneill/trivia-501/go-signer/signer"
)

// newTestSigner creates a fresh random keypair for use within a single test.
func newTestSigner(t *testing.T) *signer.Signer {
	t.Helper()
	_, priv, err := signer.GenerateKey()
	if err != nil {
		t.Fatalf("GenerateKey: %v", err)
	}
	s, err := signer.New(priv, "test-key-1")
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	return s
}

func validPayload() signer.Payload {
	return signer.Payload{
		GameID:      "550e8400-e29b-41d4-a716-446655440000",
		PlayerID:    "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
		FinalScore:  -3,
		CompletedAt: time.Date(2026, 6, 14, 12, 0, 0, 0, time.UTC),
	}
}

func TestSignVerify(t *testing.T) {
	tests := []struct {
		name    string
		payload signer.Payload
		wantErr bool
		errIs   error
	}{
		{
			name:    "valid roundtrip",
			payload: validPayload(),
		},
		{
			name:    "zero final score is valid (exact checkout)",
			payload: func() signer.Payload { p := validPayload(); p.FinalScore = 0; return p }(),
		},
		{
			name:    "negative final score within checkout range",
			payload: func() signer.Payload { p := validPayload(); p.FinalScore = -10; return p }(),
		},
		{
			name:    "missing GameID",
			payload: func() signer.Payload { p := validPayload(); p.GameID = ""; return p }(),
			wantErr: true,
			errIs:   signer.ErrMissingPayload,
		},
		{
			name:    "missing PlayerID",
			payload: func() signer.Payload { p := validPayload(); p.PlayerID = ""; return p }(),
			wantErr: true,
			errIs:   signer.ErrMissingPayload,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := newTestSigner(t)
			tok, err := s.Sign(tc.payload)

			if tc.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				if tc.errIs != nil && !isError(err, tc.errIs) {
					t.Fatalf("error %v does not wrap %v", err, tc.errIs)
				}
				return
			}

			if err != nil {
				t.Fatalf("Sign: %v", err)
			}
			if tok == nil {
				t.Fatal("Sign returned nil token")
			}

			got, err := s.Verify(tok)
			if err != nil {
				t.Fatalf("Verify: %v", err)
			}
			if got.GameID != tc.payload.GameID {
				t.Errorf("GameID: got %q, want %q", got.GameID, tc.payload.GameID)
			}
			if got.FinalScore != tc.payload.FinalScore {
				t.Errorf("FinalScore: got %d, want %d", got.FinalScore, tc.payload.FinalScore)
			}
		})
	}
}

func TestVerify_TamperedPayload(t *testing.T) {
	s := newTestSigner(t)
	tok, err := s.Sign(validPayload())
	if err != nil {
		t.Fatalf("Sign: %v", err)
	}

	// Decode, modify a field, re-encode — signature no longer matches.
	raw, _ := base64.RawURLEncoding.DecodeString(tok.Payload)
	tampered := strings.Replace(string(raw), `"finalScore":-3`, `"finalScore":501`, 1)
	tok.Payload = base64.RawURLEncoding.EncodeToString([]byte(tampered))

	_, err = s.Verify(tok)
	if !isError(err, signer.ErrBadSignature) {
		t.Fatalf("expected ErrBadSignature, got %v", err)
	}
}

func TestVerify_WrongKey(t *testing.T) {
	signer1 := newTestSigner(t)
	signer2 := newTestSigner(t)

	tok, err := signer1.Sign(validPayload())
	if err != nil {
		t.Fatalf("Sign: %v", err)
	}

	// signer2 holds a different public key — verification must fail.
	_, err = signer2.Verify(tok)
	if !isError(err, signer.ErrBadSignature) {
		t.Fatalf("expected ErrBadSignature, got %v", err)
	}
}

func TestVerify_MalformedToken(t *testing.T) {
	tests := []struct {
		name  string
		token signer.Token
	}{
		{
			name:  "empty payload",
			token: signer.Token{Payload: "", Sig: "dGVzdA"},
		},
		{
			name:  "invalid base64 payload",
			token: signer.Token{Payload: "!!!not-base64!!!", Sig: "dGVzdA"},
		},
		{
			name:  "invalid base64 sig",
			token: signer.Token{Payload: "e30", Sig: "!!!not-base64!!!"},
		},
	}

	s := newTestSigner(t)
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			_, err := s.Verify(&tc.token)
			if err == nil {
				t.Fatal("expected error, got nil")
			}
		})
	}
}

func TestNew_InvalidKeySize(t *testing.T) {
	_, err := signer.New([]byte("too-short"), "kid")
	if err == nil {
		t.Fatal("expected error for wrong key size")
	}
}

func TestPublicKeyBytes(t *testing.T) {
	s := newTestSigner(t)
	pub := s.PublicKeyBytes()
	if len(pub) != ed25519.PublicKeySize {
		t.Errorf("PublicKeyBytes length: got %d, want %d", len(pub), ed25519.PublicKeySize)
	}
}

// isError checks if err matches or wraps target. Uses errors.Is for sentinel
// errors wrapped with %w; falls back to string containment for errors wrapped
// with %v (where the inner error is not part of the chain).
func isError(err, target error) bool {
	if errors.Is(err, target) {
		return true
	}
	return strings.Contains(err.Error(), target.Error())
}
