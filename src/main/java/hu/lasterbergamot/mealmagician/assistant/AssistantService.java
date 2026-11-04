package hu.lasterbergamot.mealmagician.assistant;

import hu.lasterbergamot.mealmagician.assistant.internal.AiProperties;
import hu.lasterbergamot.mealmagician.assistant.internal.UsageLedger;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import org.springframework.stereotype.Service;

/**
 * The public entry point of the assistant module. It grows with the later issues (#158 to #162);
 * for now it answers two questions: is the assistant on, and how much has a household used this
 * month.
 */
@Service
public class AssistantService {

  private final AiProperties properties;
  private final UsageLedger ledger;
  private final HouseholdService households;

  /**
   * Creates the service.
   *
   * @param properties the {@code mm.ai.*} settings
   * @param ledger the spend ledger
   * @param households does the membership check
   */
  public AssistantService(
      AiProperties properties, UsageLedger ledger, HouseholdService households) {
    this.properties = properties;
    this.ledger = ledger;
    this.households = households;
  }

  /**
   * Whether the assistant is switched on ({@code mm.ai.enabled}). Pages call this to decide whether
   * to show their entry points at all; it needs no signed-in person and touches no database or
   * network.
   *
   * @return {@code true} when the assistant is on
   */
  public boolean enabled() {
    return properties.enabled();
  }

  /**
   * What a household has used this calendar month (Europe/Budapest), for any member to see.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the usage so far; all zeros when nothing has been used yet this month
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   */
  public MonthlyUsage usageThisMonth(long userId, long householdId) {
    if (!enabled()) {
      throw new AssistantDisabledException();
    }
    households.require(householdId, userId);
    return ledger.usageThisMonth(householdId);
  }
}
