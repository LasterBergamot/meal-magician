import { createBrowserClient } from "@supabase/ssr";
import type { Database } from "@meal-magician/core";

/**
 * Browser-side Supabase client (cookie-based auth session).
 * Server-side and middleware clients are added in Phase 2 (auth).
 */
export function createClient() {
  return createBrowserClient<Database>(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!
  );
}
