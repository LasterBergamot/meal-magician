/**
 * Database types for the Supabase schema defined in `supabase/migrations`.
 *
 * These are HAND-AUTHORED to match the Phase 1 migrations so the typed client
 * (and the data-access layer) compile with real types before the Supabase CLI
 * is wired up. Once the CLI can reach a linked project, regenerate them to stay
 * authoritative (Relationships included):
 *
 *   supabase gen types typescript --linked > packages/core/src/types/database.ts
 */

export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[];

export type Database = {
  public: {
    Tables: {
      profiles: {
        Row: {
          id: string;
          display_name: string | null;
          avatar_url: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id: string;
          display_name?: string | null;
          avatar_url?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          display_name?: string | null;
          avatar_url?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      households: {
        Row: {
          id: string;
          name: string;
          created_by: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          name: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          name?: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      household_members: {
        Row: {
          id: string;
          household_id: string;
          user_id: string;
          role: Database["public"]["Enums"]["member_role"];
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          user_id: string;
          role?: Database["public"]["Enums"]["member_role"];
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          user_id?: string;
          role?: Database["public"]["Enums"]["member_role"];
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      household_invites: {
        Row: {
          id: string;
          household_id: string;
          email: string | null;
          role: Database["public"]["Enums"]["member_role"];
          token: string;
          status: string;
          invited_by: string | null;
          accepted_by: string | null;
          accepted_at: string | null;
          expires_at: string;
          created_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          email?: string | null;
          role?: Database["public"]["Enums"]["member_role"];
          token?: string;
          status?: string;
          invited_by?: string | null;
          accepted_by?: string | null;
          accepted_at?: string | null;
          expires_at?: string;
          created_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          email?: string | null;
          role?: Database["public"]["Enums"]["member_role"];
          token?: string;
          status?: string;
          invited_by?: string | null;
          accepted_by?: string | null;
          accepted_at?: string | null;
          expires_at?: string;
          created_at?: string;
        };
        Relationships: [];
      };
      meals: {
        Row: {
          id: string;
          household_id: string;
          name: string;
          description: string | null;
          notes: string | null;
          created_by: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          name: string;
          description?: string | null;
          notes?: string | null;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          name?: string;
          description?: string | null;
          notes?: string | null;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      day_templates: {
        Row: {
          id: string;
          household_id: string;
          name: string;
          created_by: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          name: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          name?: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      day_template_items: {
        Row: {
          id: string;
          day_template_id: string;
          slot: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position: number;
        };
        Insert: {
          id?: string;
          day_template_id: string;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position?: number;
        };
        Update: {
          id?: string;
          day_template_id?: string;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id?: string;
          position?: number;
        };
        Relationships: [];
      };
      week_templates: {
        Row: {
          id: string;
          household_id: string;
          name: string;
          created_by: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          name: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          name?: string;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      week_template_items: {
        Row: {
          id: string;
          week_template_id: string;
          day_of_week: number;
          slot: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position: number;
        };
        Insert: {
          id?: string;
          week_template_id: string;
          day_of_week: number;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position?: number;
        };
        Update: {
          id?: string;
          week_template_id?: string;
          day_of_week?: number;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id?: string;
          position?: number;
        };
        Relationships: [];
      };
      planned_meals: {
        Row: {
          id: string;
          household_id: string;
          date: string;
          slot: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position: number;
          created_by: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          household_id: string;
          date: string;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id: string;
          position?: number;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          household_id?: string;
          date?: string;
          slot?: Database["public"]["Enums"]["meal_slot"];
          meal_id?: string;
          position?: number;
          created_by?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
    };
    Views: {
      [_ in never]: never;
    };
    Functions: {
      create_household: {
        Args: { p_name: string };
        Returns: Database["public"]["Tables"]["households"]["Row"];
      };
      accept_invite: {
        Args: { p_token: string };
        Returns: Database["public"]["Tables"]["household_members"]["Row"];
      };
      apply_day_template: {
        Args: { p_day_template_id: string; p_date: string };
        Returns: Database["public"]["Tables"]["planned_meals"]["Row"][];
      };
      apply_week_template: {
        Args: { p_week_template_id: string; p_start_date: string };
        Returns: Database["public"]["Tables"]["planned_meals"]["Row"][];
      };
      save_day_as_template: {
        Args: { p_household_id: string; p_date: string; p_name: string };
        Returns: Database["public"]["Tables"]["day_templates"]["Row"];
      };
      save_week_as_template: {
        Args: { p_household_id: string; p_start_date: string; p_name: string };
        Returns: Database["public"]["Tables"]["week_templates"]["Row"];
      };
      is_household_member: {
        Args: { _household_id: string };
        Returns: boolean;
      };
      is_household_admin: {
        Args: { _household_id: string };
        Returns: boolean;
      };
      shares_household: {
        Args: { _user_id: string };
        Returns: boolean;
      };
    };
    Enums: {
      meal_slot: "breakfast" | "lunch" | "dinner" | "snack";
      member_role: "admin" | "member";
    };
    CompositeTypes: {
      [_ in never]: never;
    };
  };
};
