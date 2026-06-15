// Package signer provides ed25519-based signing and verification for game results.
// All cryptography uses the Go standard library only (crypto/ed25519).
package signer

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"time"
)

// Payload is the claim set that gets signed. Every field is included in the
// signature so tampering with any of them invalidates the token.
type Payload struct {
	GameID      string    `json:"gameId"`
	PlayerID    string    `json:"playerId"`
	FinalScore  int       `json:"finalScore"`
	CompletedAt time.Time `json:"completedAt"`
	KeyID       string    `json:"kid"` // key identifier for rotation
}

// Token is the wire format returned by /sign and consumed by /verify.
type Token struct {
	// Payload is base64url-encoded JSON of Payload.
	Payload string `json:"payload"`
	// Sig is a base64url-encoded ed25519 signature over the raw Payload bytes.
	Sig string `json:"sig"`
}

var (
	ErrInvalidToken   = errors.New("token is malformed")
	ErrBadSignature   = errors.New("signature verification failed")
	ErrMissingPayload = errors.New("payload has empty required fields")
)

// Signer holds an ed25519 keypair and a key ID used in token payloads.
type Signer struct {
	privateKey ed25519.PrivateKey
	publicKey  ed25519.PublicKey
	keyID      string
}

// New constructs a Signer from a raw 64-byte ed25519 private key seed (as
// produced by keygen or loaded from an env var / Fly.io secret).
// The key ID is a short label used to identify which key signed a token;
// rotate by changing the key + ID together.
func New(privateKeyBytes []byte, keyID string) (*Signer, error) {
	if len(privateKeyBytes) != ed25519.PrivateKeySize {
		return nil, fmt.Errorf("private key must be %d bytes, got %d", ed25519.PrivateKeySize, len(privateKeyBytes))
	}
	priv := ed25519.PrivateKey(privateKeyBytes)
	pub, ok := priv.Public().(ed25519.PublicKey)
	if !ok {
		return nil, errors.New("could not derive public key from private key")
	}
	return &Signer{privateKey: priv, publicKey: pub, keyID: keyID}, nil
}

// GenerateKey creates a new random ed25519 keypair. Used by the keygen CLI.
func GenerateKey() (ed25519.PublicKey, ed25519.PrivateKey, error) {
	return ed25519.GenerateKey(rand.Reader)
}

// Sign creates a Token for the given Payload. Returns ErrMissingPayload if
// GameID or PlayerID are empty.
func (s *Signer) Sign(p Payload) (*Token, error) {
	if p.GameID == "" || p.PlayerID == "" {
		return nil, ErrMissingPayload
	}
	p.KeyID = s.keyID

	raw, err := json.Marshal(p)
	if err != nil {
		return nil, fmt.Errorf("marshal payload: %w", err)
	}

	sig := ed25519.Sign(s.privateKey, raw)

	return &Token{
		Payload: base64.RawURLEncoding.EncodeToString(raw),
		Sig:     base64.RawURLEncoding.EncodeToString(sig),
	}, nil
}

// Verify checks a Token's signature against the signer's public key and
// returns the decoded Payload if valid.
func (s *Signer) Verify(t *Token) (*Payload, error) {
	raw, err := base64.RawURLEncoding.DecodeString(t.Payload)
	if err != nil {
		return nil, fmt.Errorf("%w: payload decode: %v", ErrInvalidToken, err)
	}

	sigBytes, err := base64.RawURLEncoding.DecodeString(t.Sig)
	if err != nil {
		return nil, fmt.Errorf("%w: sig decode: %v", ErrInvalidToken, err)
	}

	if !ed25519.Verify(s.publicKey, raw, sigBytes) {
		return nil, ErrBadSignature
	}

	var p Payload
	if err := json.Unmarshal(raw, &p); err != nil {
		return nil, fmt.Errorf("%w: payload unmarshal: %v", ErrInvalidToken, err)
	}
	return &p, nil
}

// PublicKeyBytes returns the raw public key bytes for the /pubkey endpoint.
func (s *Signer) PublicKeyBytes() []byte {
	return s.publicKey
}

// KeyID returns the key identifier embedded in signed tokens.
func (s *Signer) KeyID() string {
	return s.keyID
}
