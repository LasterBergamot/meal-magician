-- Enums and shared trigger helpers.
--
-- meal_slot is deliberately broad even though the UI only exposes "lunch" for
-- now — turning on breakfast/dinner/snack later is a UI change, not a migration.
-- member_role backs the admin/member distinction inside a household.

create type public.meal_slot as enum ('breakfast', 'lunch', 'dinner', 'snack');
create type public.member_role as enum ('admin', 'member');

-- Keeps updated_at current on every row UPDATE. Attached per-table below.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
