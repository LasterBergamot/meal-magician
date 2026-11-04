package hu.lasterbergamot.mealmagician.shared.web;

/**
 * Tells the app shell whether the assistant is switched on, through {@link
 * ViewContext#assistant()}, so the navigation can leave out the Wizard's destination while it is
 * off.
 *
 * <p>The {@code assistant} module publishes the one bean of this type. The indirection exists
 * because {@code shared} may not depend on {@code assistant} (that would be a cycle), the same
 * reason as {@link ShellUserSource}. When no bean is present (a slice without the assistant), the
 * assistant counts as off.
 */
@FunctionalInterface
public interface AssistantSwitchSource {

  /**
   * Whether the assistant is switched on ({@code mm.ai.enabled}).
   *
   * @return {@code true} when the assistant is on
   */
  boolean enabled();
}
