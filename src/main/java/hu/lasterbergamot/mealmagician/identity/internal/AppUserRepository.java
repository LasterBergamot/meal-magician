package hu.lasterbergamot.mealmagician.identity.internal;

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
}
