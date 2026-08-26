-- Transactional RPCs, called from the client via PostgREST rpc().
--
-- All are SECURITY DEFINER (so they can perform multi-table writes that would
-- otherwise be blocked mid-bootstrap by RLS) but each re-checks authorization
-- explicitly against auth.uid() so it can't be used to escalate privileges.
-- search_path is pinned to public.

-- ---------------------------------------------------------------------------
-- create_household(name) -> the new household row.
-- Creates the household and inserts the caller as its admin, atomically.
-- ---------------------------------------------------------------------------
create or replace function public.create_household(p_name text)
returns public.households
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household public.households;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  -- Single-household guard. Remove this block to allow multi-household later;
  -- the schema already supports it (no unique on household_members.user_id).
  if exists (select 1 from public.household_members where user_id = v_uid) then
    raise exception 'User already belongs to a household';
  end if;

  insert into public.households (name, created_by)
  values (btrim(p_name), v_uid)
  returning * into v_household;

  insert into public.household_members (household_id, user_id, role)
  values (v_household.id, v_uid, 'admin');

  return v_household;
end;
$$;

-- ---------------------------------------------------------------------------
-- accept_invite(token) -> the resulting membership row.
-- Validates the invite (status, expiry, optional email match), enforces the
-- single-household guard, joins the caller, and marks the invite accepted.
-- ---------------------------------------------------------------------------
create or replace function public.accept_invite(p_token uuid)
returns public.household_members
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_invite public.household_invites;
  v_member public.household_members;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select * into v_invite
  from public.household_invites
  where token = p_token;

  if v_invite.id is null then
    raise exception 'Invite not found';
  end if;

  if v_invite.status <> 'pending' then
    raise exception 'Invite is no longer valid';
  end if;

  if v_invite.expires_at < now() then
    update public.household_invites set status = 'expired' where id = v_invite.id;
    raise exception 'Invite has expired';
  end if;

  -- If the invite was addressed to a specific email, it must match the caller.
  select email into v_email from auth.users where id = v_uid;
  if v_invite.email is not null and lower(v_invite.email) <> lower(coalesce(v_email, '')) then
    raise exception 'Invite was issued for a different email';
  end if;

  -- Single-household guard (relaxable later, see create_household).
  if exists (select 1 from public.household_members where user_id = v_uid) then
    raise exception 'User already belongs to a household';
  end if;

  insert into public.household_members (household_id, user_id, role)
  values (v_invite.household_id, v_uid, v_invite.role)
  on conflict (household_id, user_id) do update set role = excluded.role
  returning * into v_member;

  update public.household_invites
  set status = 'accepted', accepted_by = v_uid, accepted_at = now()
  where id = v_invite.id;

  return v_member;
end;
$$;

-- ---------------------------------------------------------------------------
-- apply_day_template(day_template_id, date) -> inserted/updated planned rows.
-- Copies a day template's items onto a single calendar date.
-- ---------------------------------------------------------------------------
create or replace function public.apply_day_template(p_day_template_id uuid, p_date date)
returns setof public.planned_meals
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household_id uuid;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select household_id into v_household_id
  from public.day_templates
  where id = p_day_template_id;

  if v_household_id is null then
    raise exception 'Day template not found';
  end if;

  if not public.is_household_member(v_household_id) then
    raise exception 'Not a member of this household';
  end if;

  return query
  insert into public.planned_meals (household_id, date, slot, meal_id, position, created_by)
  select v_household_id, p_date, i.slot, i.meal_id, i.position, v_uid
  from public.day_template_items i
  where i.day_template_id = p_day_template_id
  on conflict (household_id, date, slot, position)
  do update set meal_id = excluded.meal_id
  returning *;
end;
$$;

-- ---------------------------------------------------------------------------
-- apply_week_template(week_template_id, start_date) -> inserted/updated rows.
-- start_date is treated as day_of_week = 1 (Monday). Each item lands on
-- start_date + (day_of_week - 1).
-- ---------------------------------------------------------------------------
create or replace function public.apply_week_template(p_week_template_id uuid, p_start_date date)
returns setof public.planned_meals
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household_id uuid;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select household_id into v_household_id
  from public.week_templates
  where id = p_week_template_id;

  if v_household_id is null then
    raise exception 'Week template not found';
  end if;

  if not public.is_household_member(v_household_id) then
    raise exception 'Not a member of this household';
  end if;

  return query
  insert into public.planned_meals (household_id, date, slot, meal_id, position, created_by)
  select v_household_id,
         p_start_date + (i.day_of_week - 1),
         i.slot, i.meal_id, i.position, v_uid
  from public.week_template_items i
  where i.week_template_id = p_week_template_id
  on conflict (household_id, date, slot, position)
  do update set meal_id = excluded.meal_id
  returning *;
end;
$$;

-- ---------------------------------------------------------------------------
-- save_day_as_template(household_id, date, name) -> the new day_template.
-- Snapshots a single day of planned_meals into a reusable day template.
-- ---------------------------------------------------------------------------
create or replace function public.save_day_as_template(
  p_household_id uuid,
  p_date date,
  p_name text
)
returns public.day_templates
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_template public.day_templates;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  if not public.is_household_member(p_household_id) then
    raise exception 'Not a member of this household';
  end if;

  insert into public.day_templates (household_id, name, created_by)
  values (p_household_id, btrim(p_name), v_uid)
  returning * into v_template;

  insert into public.day_template_items (day_template_id, slot, meal_id, position)
  select v_template.id, pm.slot, pm.meal_id, pm.position
  from public.planned_meals pm
  where pm.household_id = p_household_id
    and pm.date = p_date;

  return v_template;
end;
$$;

-- ---------------------------------------------------------------------------
-- save_week_as_template(household_id, start_date, name) -> the new week_template.
-- Snapshots the 7 days starting at start_date (inclusive) into a week template.
-- day_of_week is derived as (date - start_date) + 1, so start_date = Monday.
-- ---------------------------------------------------------------------------
create or replace function public.save_week_as_template(
  p_household_id uuid,
  p_start_date date,
  p_name text
)
returns public.week_templates
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_template public.week_templates;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  if not public.is_household_member(p_household_id) then
    raise exception 'Not a member of this household';
  end if;

  insert into public.week_templates (household_id, name, created_by)
  values (p_household_id, btrim(p_name), v_uid)
  returning * into v_template;

  insert into public.week_template_items (week_template_id, day_of_week, slot, meal_id, position)
  select v_template.id,
         ((pm.date - p_start_date) + 1)::smallint,
         pm.slot, pm.meal_id, pm.position
  from public.planned_meals pm
  where pm.household_id = p_household_id
    and pm.date >= p_start_date
    and pm.date < p_start_date + 7;

  return v_template;
end;
$$;

-- Only authenticated users may call these RPCs.
revoke all on function public.create_household(text) from public;
revoke all on function public.accept_invite(uuid) from public;
revoke all on function public.apply_day_template(uuid, date) from public;
revoke all on function public.apply_week_template(uuid, date) from public;
revoke all on function public.save_day_as_template(uuid, date, text) from public;
revoke all on function public.save_week_as_template(uuid, date, text) from public;

grant execute on function public.create_household(text) to authenticated;
grant execute on function public.accept_invite(uuid) to authenticated;
grant execute on function public.apply_day_template(uuid, date) to authenticated;
grant execute on function public.apply_week_template(uuid, date) to authenticated;
grant execute on function public.save_day_as_template(uuid, date, text) to authenticated;
grant execute on function public.save_week_as_template(uuid, date, text) to authenticated;
