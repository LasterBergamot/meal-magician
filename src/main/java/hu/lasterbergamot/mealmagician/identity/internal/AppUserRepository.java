package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for users. Users are global, not scoped to a household. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  /**
   * Finds the user behind a Google account.
   *
   * @param googleSub Google's stable account id
   * @return the user, or empty if this account has never signed in
   */
  Optional<AppUser> findByGoogleSub(String googleSub);

  /**
   * Finds the users whose stored e-mail is this address. Usually one; the column is not unique, so
   * a Google account that changed address and a later account that took the old one could both
   * match. The allowlist admin uses it to find whose sessions to end (#59).
   *
   * @param email the normalized (lower-case) address
   * @return the users, possibly none; an address nobody has signed in with gives an empty list
   */
  List<AppUser> findAllByEmail(String email);
}
