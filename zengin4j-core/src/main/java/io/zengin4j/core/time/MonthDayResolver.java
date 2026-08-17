package io.zengin4j.core.time;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Attaches a year to the yearless {@code MMDD} dates the formats carry.
 *
 * <p>振込指定日 and 引落日 are {@code N(4)} — month and day, no year. This
 * library never invents one silently (R-D9): the parsed type is
 * {@link MonthDay}, and turning it into a {@link LocalDate} means naming a
 * strategy and a reference date, here, in the caller's own code.
 *
 * <p>Immutable and thread-safe.
 *
 * @since 0.1.0
 */
public final class MonthDayResolver {
    private static final MonthDay LEAP_DAY = MonthDay.of(2, 29);

    private final ResolutionStrategy strategy;
    private final LocalDate reference;

    private MonthDayResolver(ResolutionStrategy strategy, LocalDate reference) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.reference = Objects.requireNonNull(reference, "reference");
    }

    /**
     * Creates a resolver choosing the next occurrence at or after the
     * reference date.
     *
     * @param reference the reference date, typically the file's creation date
     * @return the resolver
     */
    public static MonthDayResolver forwardLooking(LocalDate reference) {
        return new MonthDayResolver(ResolutionStrategy.FORWARD_LOOKING, reference);
    }

    /**
     * Creates a resolver choosing the occurrence closest to the reference
     * date.
     *
     * @param reference the reference date, typically the file's creation date
     * @return the resolver
     */
    public static MonthDayResolver nearest(LocalDate reference) {
        return new MonthDayResolver(ResolutionStrategy.NEAREST, reference);
    }

    /**
     * Creates a resolver.
     *
     * @param strategy  the strategy to apply
     * @param reference the reference date
     * @return the resolver
     */
    public static MonthDayResolver of(ResolutionStrategy strategy, LocalDate reference) {
        return new MonthDayResolver(strategy, reference);
    }

    /**
     * Returns the strategy this resolver applies.
     *
     * @return the strategy
     */
    public ResolutionStrategy strategy() {
        return strategy;
    }

    /**
     * Returns the reference date.
     *
     * @return the reference date
     */
    public LocalDate reference() {
        return reference;
    }

    /**
     * Resolves a month and day to a date.
     *
     * @param monthDay the month and day to resolve
     * @return the outcome, resolved or explicitly unresolved; never throws for
     *         29 February in a non-leap candidate year (R-D12)
     */
    public DateResolution resolve(MonthDay monthDay) {
        Objects.requireNonNull(monthDay, "monthDay");
        List<Integer> candidates = candidateYears();
        Optional<LocalDate> resolved = switch (strategy) {
            case FORWARD_LOOKING -> resolveForwardLooking(monthDay, candidates);
            case NEAREST -> resolveNearest(monthDay, candidates);
        };
        return new DateResolution(
                monthDay,
                strategy,
                reference,
                resolved,
                candidates,
                resolved.isPresent()
                        ? Optional.empty()
                        : Optional.of(DateResolution.UnresolvedReason.LEAP_DAY_NOT_IN_CANDIDATE_YEAR));
    }

    private List<Integer> candidateYears() {
        int year = reference.getYear();
        return switch (strategy) {
            case FORWARD_LOOKING -> List.of(year, year + 1);
            case NEAREST -> List.of(year - 1, year, year + 1);
        };
    }

    private Optional<LocalDate> resolveForwardLooking(MonthDay monthDay, List<Integer> candidates) {
        for (int year : candidates) {
            Optional<LocalDate> candidate = atYear(monthDay, year);
            if (candidate.isPresent() && !candidate.get().isBefore(reference)) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    /**
     * Chooses the candidate closest to the reference date. Ties resolve to the
     * earlier year, because candidates are considered oldest first and
     * {@code min} keeps the first of equal elements — the choice must be
     * deterministic (INV-7), and this documents which way.
     */
    private Optional<LocalDate> resolveNearest(MonthDay monthDay, List<Integer> candidates) {
        List<LocalDate> existing = new ArrayList<>(candidates.size());
        for (int year : candidates) {
            atYear(monthDay, year).ifPresent(existing::add);
        }
        return existing.stream()
                .min((left, right) -> Long.compare(distance(left), distance(right)));
    }

    private long distance(LocalDate date) {
        return Math.abs(ChronoUnit.DAYS.between(reference, date));
    }

    private static Optional<LocalDate> atYear(MonthDay monthDay, int year) {
        if (monthDay.equals(LEAP_DAY) && !Year.isLeap(year)) {
            return Optional.empty();
        }
        return Optional.of(monthDay.atYear(year));
    }
}
