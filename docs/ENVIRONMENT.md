# Environment Variables & Performance Targets

## Environment Variables

Local development connects to Supabase (see `CLAUDE.md` > Development Workflow); `DB_PASSWORD` is the only variable required to start the backend. Production values live as Fly.io secrets and Vercel env vars (see `CLAUDE.md` > Deployment & Hosting).

```bash
# API-Football (scraper service)
FOOTBALL_API_KEY=your_api_key_here

# Database (backend)
DB_PASSWORD=your_password_here   # Supabase — see CLAUDE.md > Development Workflow

# JWT (when real auth ships)
JWT_SECRET=your_secret_key_here
JWT_EXPIRATION=86400000  # 24 hours in ms

# OAuth (when implemented)
OAUTH_GOOGLE_CLIENT_ID=
OAUTH_GOOGLE_CLIENT_SECRET=
OAUTH_APPLE_CLIENT_ID=
OAUTH_APPLE_CLIENT_SECRET=
OAUTH_FACEBOOK_CLIENT_ID=
OAUTH_FACEBOOK_CLIENT_SECRET=
```

## Performance Targets

| Metric | Target |
|--------|--------|
| API Response Time (p95) | < 200ms |
| PWA Load Time (3G) | < 3s |
| Database Query Time | < 50ms |
