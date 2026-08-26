import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "../types/database";

/** A Supabase client typed against our database schema. */
export type AppSupabaseClient = SupabaseClient<Database>;

/**
 * Create a typed Supabase client from a URL + key.
 *
 * Platform-specific session handling (cookies on web via `@supabase/ssr`,
 * secure storage on mobile) is wired up inside each app; this factory is the
 * shared, typed entry point used by data-access code in this package.
 */
export function createSupabaseClient(supabaseUrl: string, supabaseKey: string): AppSupabaseClient {
  if (!supabaseUrl || !supabaseKey) {
    throw new Error("Missing Supabase URL or key.");
  }
  return createClient<Database>(supabaseUrl, supabaseKey);
}
