package hu.lasterbergamot.mealmagician.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the allowlist; the key is the normalized e-mail address. */
public interface AllowedEmailRepository extends JpaRepository<AllowedEmail, String> {}
