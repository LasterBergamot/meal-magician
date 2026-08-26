-- RLS helper functions.
--
-- These are SECURITY DEFINER so they read household_members with RLS bypassed.
-- That is what avoids the classic recursion pitfall: a policy ON
-- household_members that needs to query household_members would otherwise
-- re-trigger the same policy forever. Because these helpers run as the owner,
-- they don't invoke RLS at all.
--
-- search_path is pinned to public to prevent search_path hijacking.

create or replace function public.is_household_member(_household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.household_members hm
    where hm.household_id = _household_id
      and hm.user_id = auth.uid()
  );
$$;

create or replace function public.is_household_admin(_household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.household_members hm
    where hm.household_id = _household_id
      and hm.user_id = auth.uid()
      and hm.role = 'admin'
  );
$$;

-- True when the current user shares at least one household with _user_id.
-- Used so profiles are visible to co-members without exposing them globally.
create or replace function public.shares_household(_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.household_members me
    join public.household_members other
      on other.household_id = me.household_id
    where me.user_id = auth.uid()
      and other.user_id = _user_id
  );
$$;

revoke all on function public.is_household_member(uuid) from public;
revoke all on function public.is_household_admin(uuid) from public;
revoke all on function public.shares_household(uuid) from public;

grant execute on function public.is_household_member(uuid) to authenticated;
grant execute on function public.is_household_admin(uuid) to authenticated;
grant execute on function public.shares_household(uuid) to authenticated;
