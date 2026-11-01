package hu.lasterbergamot.mealmagician.identity;

import hu.lasterbergamot.mealmagician.identity.internal.AppUserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks people up by id for other modules, which keep only {@code app_user} ids (the household
 * module stores a membership as two numbers). The members page uses it to turn ids into names.
 *
 * <p>It exposes {@link UserProfile}, not the entity, so a module cannot reach the Google {@code
 * sub} or the stored locale.
 */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

  private final AppUserRepository users;

  /**
   * Creates the directory.
   *
   * @param users persistence for users
   */
  public UserDirectory(AppUserRepository users) {
    this.users = users;
  }

  /**
   * Finds several people at once.
   *
   * @param ids the {@code app_user} ids; the caller has already decided the person may see them
   * @return the profiles by id; an unknown id is simply missing from the map
   */
  public Map<Long, UserProfile> find(Collection<Long> ids) {
    return users.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                user -> user.getId(),
                user -> new UserProfile(user.getId(), user.getDisplayName(), user.getEmail())));
  }
}
