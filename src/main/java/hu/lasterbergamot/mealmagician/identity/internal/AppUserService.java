package hu.lasterbergamot.mealmagician.identity.internal;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and refreshes {@link AppUser} rows when allowlisted people sign in. */
@Service
class AppUserService {

  private final AppUserRepository users;
  private final Clock clock;

  AppUserService(AppUserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  /**
   * Records a sign-in: creates the user on the first one, refreshes e-mail and name afterwards.
   *
   * <p>Only call this for people who passed the allowlist check, so refused visitors never get a
   * row.
   *
   * @param googleSub Google's stable account id
   * @param email the verified, normalized e-mail address
   * @param displayName the name to show
   * @return the saved user
   */
  @Transactional
  AppUser recordSignIn(String googleSub, String email, String displayName) {
    var user =
        users
            .findByGoogleSub(googleSub)
            .orElseGet(() -> new AppUser(googleSub, email, displayName, Instant.now(clock)));
    user.refreshProfile(email, displayName);
    return users.save(user);
  }
}
