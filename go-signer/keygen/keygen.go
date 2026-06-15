// Command keygen generates a new ed25519 keypair and prints both keys as
// base64-encoded strings suitable for use as Fly.io secrets.
//
// Usage:
//
//	go run keygen/keygen.go
//
// Output:
//
//	SIGNING_KEY=<base64-encoded 64-byte private key>
//	SIGNING_KEY_ID=<short identifier — update this on each rotation>
//	Public key (for verification): <base64-encoded 32-byte public key>
//
// Set the private key as a Fly.io secret:
//
//	fly secrets set SIGNING_KEY=<value> SIGNING_KEY_ID=<value>
//
// Store the public key in your Java backend config for independent verification.
package main

import (
	"encoding/base64"
	"fmt"
	"log"
	"time"

	"github.com/paul-anthony-oneill/trivia-501/go-signer/signer"
)

func main() {
	pub, priv, err := signer.GenerateKey()
	if err != nil {
		log.Fatalf("failed to generate keypair: %v", err)
	}

	keyID := fmt.Sprintf("k%d", time.Now().Unix())

	privB64 := base64.StdEncoding.EncodeToString(priv)
	pubB64 := base64.StdEncoding.EncodeToString(pub)

	fmt.Printf("SIGNING_KEY=%s\n", privB64)
	fmt.Printf("SIGNING_KEY_ID=%s\n", keyID)
	fmt.Printf("\nPublic key (for verification / RESULT_SIGNER_PUBLIC_KEY):\n%s\n", pubB64)
	fmt.Println("\nSet secrets on Fly.io:")
	fmt.Printf("  fly secrets set SIGNING_KEY=%s SIGNING_KEY_ID=%s --app go-signer-football-501\n", privB64, keyID)
}
