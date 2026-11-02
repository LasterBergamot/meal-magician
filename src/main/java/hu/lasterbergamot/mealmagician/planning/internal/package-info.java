/**
 * Planning internals: the JPA entities for planned slots, plan templates, occasions, occasion
 * templates and their items, the repositories that query them, {@code SlotWriter} (the one writer
 * behind copy and apply), {@code OccasionTypes} (message keys), and the week page's controller and
 * page models. Other modules must not import this package; they use the types in the {@code
 * planning} root package.
 */
package hu.lasterbergamot.mealmagician.planning.internal;
