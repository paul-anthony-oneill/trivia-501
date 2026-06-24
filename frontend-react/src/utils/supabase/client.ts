import { createBrowserClient } from "@supabase/ssr";

// typeof guard: safe in any JS runtime — Next.js inlines NEXT_PUBLIC_ vars at
// build time but non-Next.js bundlers (e.g. design-sync esbuild IIFE) leave the
// expression as-is, and `process` is not a browser global.
const supabaseUrl = typeof process !== "undefined" ? process.env.NEXT_PUBLIC_SUPABASE_URL : undefined;
const supabaseKey = typeof process !== "undefined" ? process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY : undefined;

export const createClient = () =>
  createBrowserClient(supabaseUrl!, supabaseKey!);
