package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets any controller declare a {@link CurrentHousehold} parameter and receive the verified
 * household of the request. A person with no household gets a 404: there is nothing of theirs to
 * show, and the header already links to the page where they create one.
 */
@Component
class CurrentHouseholdArgumentResolver implements HandlerMethodArgumentResolver {

  private final CurrentHouseholdResolver resolver;

  CurrentHouseholdArgumentResolver(CurrentHouseholdResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CurrentHousehold.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    return resolver.resolve(request).orElseThrow(HouseholdNotFoundException::new);
  }
}
