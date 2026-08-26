/**
 * Placeholder database types.
 *
 * Replace this file with generated types once the schema exists (Phase 1):
 *
 *   supabase gen types typescript --linked > packages/core/src/types/database.ts
 *
 * Keeping a valid `Database` shape here lets the typed Supabase client compile
 * before any tables are defined.
 */
export type Database = {
  public: {
    Tables: Record<string, never>;
    Views: Record<string, never>;
    Functions: Record<string, never>;
    Enums: Record<string, never>;
    CompositeTypes: Record<string, never>;
  };
};
