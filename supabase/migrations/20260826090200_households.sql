-- Households, their members, and the invite flow.

create table public.households (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 100),
  created_by uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger households_set_updated_at
  before update on public.households
  for each row execute function public.set_updated_at();

-- Join table between users and households.
-- IMPORTANT: unique is on (household_id, user_id), NOT on user_id alone. The
-- "one household per user" rule is enforced in app logic / RPCs so that
-- multi-household support later is a guard change, not a schema migration.
create table public.household_members (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role public.member_role not null default 'member',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (household_id, user_id)
);

create index household_members_user_id_idx on public.household_members (user_id);
create index household_members_household_id_idx on public.household_members (household_id);

create trigger household_members_set_updated_at
  before update on public.household_members
  for each row execute function public.set_updated_at();

-- Invites let an admin add someone by email or shareable token.
create table public.household_invites (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  email text,
  role public.member_role not null default 'member',
  token uuid not null default gen_random_uuid(),
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'revoked', 'expired')),
  invited_by uuid references auth.users (id) on delete set null,
  accepted_by uuid references auth.users (id) on delete set null,
  accepted_at timestamptz,
  expires_at timestamptz not null default (now() + interval '7 days'),
  created_at timestamptz not null default now(),
  unique (token)
);

create index household_invites_household_id_idx on public.household_invites (household_id);
create index household_invites_email_idx on public.household_invites (lower(email));
