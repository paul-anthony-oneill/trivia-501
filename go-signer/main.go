// Command go-signer is an HTTP microservice that signs and verifies Football 501
// game results using ed25519 (Go standard library).
//
// Environment variables (set as Fly.io secrets in production):
//
//	SIGNING_KEY      — base64-encoded 64-byte ed25519 private key (required)
//	SIGNING_KEY_ID   — short label for this key, embedded in tokens (required)
//	INTERNAL_SECRET  — bearer token required on POST /sign (recommended in production)
//	PORT             — HTTP listen port, defaults to 8090
//
// Security model:
//
//	POST /sign   — must only be callable by the trusted Java backend. Protect with
//	               INTERNAL_SECRET (bearer token) AND Fly.io private networking
//	               (.internal DNS) so the endpoint is never internet-reachable.
//	               If INTERNAL_SECRET is empty the check is skipped (local dev only).
//	POST /verify — intentionally public; verifying is what third parties should do.
//	GET  /pubkey — intentionally public; needed for independent verification.
package main

import (
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/paul-anthony-oneill/trivia-501/go-signer/signer"
)

func main() {
	s, err := loadSigner()
	if err != nil {
		log.Fatalf("cannot load signer: %v", err)
	}

	secret := os.Getenv("INTERNAL_SECRET")
	if secret == "" {
		log.Println("WARNING: INTERNAL_SECRET not set — /sign is unprotected (OK for local dev, not for production)")
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", handleHealth)
	mux.HandleFunc("GET /pubkey", handlePubKey(s))
	mux.HandleFunc("POST /sign", requireSecret(secret, handleSign(s)))
	mux.HandleFunc("POST /verify", handleVerify(s))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8090"
	}

	srv := &http.Server{
		Addr:              ":" + port,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("go-signer listening on :%s", port)
	if err := srv.ListenAndServe(); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

// requireSecret is middleware that enforces a bearer token on the wrapped handler.
// Uses crypto/subtle.ConstantTimeCompare so the comparison does not short-circuit
// on the first differing byte — a non-constant-time comparison leaks timing
// information that could theoretically be used to recover the secret byte-by-byte.
// When secret is empty (local dev without INTERNAL_SECRET set) the check is skipped.
func requireSecret(secret string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if secret != "" {
			got := r.Header.Get("Authorization")
			want := "Bearer " + secret
			if subtle.ConstantTimeCompare([]byte(got), []byte(want)) != 1 {
				writeJSON(w, http.StatusUnauthorized, errorResponse{Error: "unauthorized"})
				return
			}
		}
		next(w, r)
	}
}

// loadSigner reads SIGNING_KEY and SIGNING_KEY_ID from the environment and
// constructs a Signer. Both variables are required.
func loadSigner() (*signer.Signer, error) {
	keyB64 := os.Getenv("SIGNING_KEY")
	if keyB64 == "" {
		return nil, errors.New("SIGNING_KEY env var is required")
	}
	keyBytes, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return nil, errors.New("SIGNING_KEY is not valid base64")
	}

	keyID := os.Getenv("SIGNING_KEY_ID")
	if keyID == "" {
		return nil, errors.New("SIGNING_KEY_ID env var is required")
	}

	return signer.New(keyBytes, keyID)
}

// ── Request / response types ──────────────────────────────────────────────────

type signRequest struct {
	GameID      string    `json:"gameId"`
	PlayerID    string    `json:"playerId"`
	FinalScore  int       `json:"finalScore"`
	CompletedAt time.Time `json:"completedAt"`
}

type signResponse struct {
	Token *signer.Token `json:"token"`
}

type verifyRequest struct {
	Token *signer.Token `json:"token"`
}

type verifyResponse struct {
	Valid   bool            `json:"valid"`
	Payload *signer.Payload `json:"payload,omitempty"`
	Error   string          `json:"error,omitempty"`
}

type pubKeyResponse struct {
	PublicKey string `json:"publicKey"`
	KeyID     string `json:"kid"`
}

type errorResponse struct {
	Error string `json:"error"`
}

// ── Handlers ──────────────────────────────────────────────────────────────────

func handleHealth(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
}

func handlePubKey(s *signer.Signer) http.HandlerFunc {
	encoded := base64.StdEncoding.EncodeToString(s.PublicKeyBytes())
	kid := s.KeyID()
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, pubKeyResponse{PublicKey: encoded, KeyID: kid})
	}
}

func handleSign(s *signer.Signer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req signRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid JSON"})
			return
		}

		tok, err := s.Sign(signer.Payload{
			GameID:      req.GameID,
			PlayerID:    req.PlayerID,
			FinalScore:  req.FinalScore,
			CompletedAt: req.CompletedAt,
		})
		if err != nil {
			if errors.Is(err, signer.ErrMissingPayload) {
				writeJSON(w, http.StatusBadRequest, errorResponse{Error: err.Error()})
				return
			}
			log.Printf("sign error: %v", err)
			writeJSON(w, http.StatusInternalServerError, errorResponse{Error: "signing failed"})
			return
		}

		writeJSON(w, http.StatusOK, signResponse{Token: tok})
	}
}

func handleVerify(s *signer.Signer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req verifyRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid JSON"})
			return
		}
		if req.Token == nil {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: "token is required"})
			return
		}

		payload, err := s.Verify(req.Token)
		if err != nil {
			writeJSON(w, http.StatusOK, verifyResponse{Valid: false, Error: err.Error()})
			return
		}

		writeJSON(w, http.StatusOK, verifyResponse{Valid: true, Payload: payload})
	}
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("writeJSON encode error: %v", err)
	}
}
