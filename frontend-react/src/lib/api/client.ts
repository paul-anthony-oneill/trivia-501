"use client";

import { createClient } from "@/utils/supabase/client";
import type { SupabaseClient } from "@supabase/supabase-js";

let _supabase: SupabaseClient | null | undefined;

function getSupabase(): SupabaseClient | null {
  if (_supabase === undefined) {
    try {
      _supabase = createClient();
    } catch {
      _supabase = null;
    }
  }
  return _supabase;
}

export async function apiFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const url =
    typeof input === "string"
      ? input
      : input instanceof URL
        ? input.href
        : input.url;

  // Only inject auth for local API calls
  if (url.startsWith("/api/")) {
    const supabase = getSupabase();
    if (supabase) {
      const {
        data: { session },
      } = await supabase.auth.getSession();
      if (session?.access_token) {
        init = {
          ...init,
          headers: {
            ...init?.headers,
            Authorization: `Bearer ${session.access_token}`,
          },
        };
      }
    }
  }

  return fetch(input, init);
}
