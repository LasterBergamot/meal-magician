package hu.lasterbergamot.mealmagician.sharing.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the public share page, under {@code mm.share}.
 *
 * @param ratePerMinute how many requests per minute the public share route answers in total, over
 *     all callers (PLAN.md D28); the default is 120 and a value below 1 stops the application from
 *     starting
 */
@ConfigurationProperties(prefix = "mm.share")
public record SharingProperties(@DefaultValue("120") int ratePerMinute) {}
