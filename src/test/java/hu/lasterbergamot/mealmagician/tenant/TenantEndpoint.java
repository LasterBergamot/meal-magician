package hu.lasterbergamot.mealmagician.tenant;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/**
 * One household-scoped endpoint, registered with the tenant guard (see {@link TenantEndpoints}).
 *
 * @param method the HTTP method the controller maps
 * @param pattern the mapping exactly as the controller declares it, for example {@code
 *     /households/members/{memberId}/remove}; the guard matches it against the real mappings
 * @param ownerOnly whether a plain member must get 403
 * @param variables the values of the path variables that point at the <em>home</em> household's
 *     resources; empty for a path without variables
 */
public record TenantEndpoint(
    HttpMethod method,
    String pattern,
    boolean ownerOnly,
    Function<TenantWorld, Map<String, Object>> variables) {

  private static final Pattern VARIABLE = Pattern.compile("\\{([^/}:]+)(?::[^/]*)?}");

  /**
   * An endpoint open to every member of the household.
   *
   * @param method the HTTP method
   * @param pattern the mapping as declared
   * @param variables the home resources' path variables
   * @return the registration
   */
  public static TenantEndpoint forMembers(
      HttpMethod method, String pattern, Function<TenantWorld, Map<String, Object>> variables) {
    return new TenantEndpoint(method, pattern, false, variables);
  }

  /**
   * An endpoint only an owner may use.
   *
   * @param method the HTTP method
   * @param pattern the mapping as declared
   * @param variables the home resources' path variables
   * @return the registration
   */
  public static TenantEndpoint ownerOnly(
      HttpMethod method, String pattern, Function<TenantWorld, Map<String, Object>> variables) {
    return new TenantEndpoint(method, pattern, true, variables);
  }

  /**
   * Whether the path names a resource (has a path variable). Such a request must find the resource
   * inside the caller's household, so another household's owner gets 404 for it; a path without ids
   * always means "my current household" and has no such check.
   *
   * @return true if the pattern has a path variable
   */
  public boolean addressesResources() {
    return VARIABLE.matcher(pattern).find();
  }

  /**
   * The key the registry and the endpoint walk compare: method and mapping.
   *
   * @return for example {@code POST /households/leave}
   */
  public String key() {
    return method.name() + " " + pattern;
  }

  /** The key, so test reports show {@code POST /households/leave} and not a record dump. */
  @Override
  public String toString() {
    return key();
  }

  /**
   * Fills the path variables in with the home household's ids.
   *
   * @param world the fixture
   * @return a concrete path a request can use
   * @throws IllegalStateException if the pattern has a variable the registration gives no value for
   *     (the registration is incomplete, so the check would silently probe a wrong path)
   */
  public String pathIn(TenantWorld world) {
    Map<String, Object> values = variables.apply(world);
    Matcher matcher = VARIABLE.matcher(pattern);
    StringBuilder path = new StringBuilder();
    while (matcher.find()) {
      Object value = values.get(matcher.group(1));
      if (value == null) {
        throw new IllegalStateException(
            key() + " has no value for the path variable {" + matcher.group(1) + "}");
      }
      matcher.appendReplacement(path, Matcher.quoteReplacement(value.toString()));
    }
    matcher.appendTail(path);
    return path.toString();
  }
}
