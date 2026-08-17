package io.zengin4j.validation.calendar;

import java.util.Optional;

/**
 * Why funds do not move on a date.
 *
 * <p>A finding that says "not a business day" tells the reader to go and find
 * out why. This carries the answer, so the finding can say "は文化の日" and the
 * reader is done.
 *
 * @param kind what kind of non-business day it is
 * @param name the holiday's name, where it has one
 * @since 0.2.0
 */
public record NonBusinessDay(Kind kind, Optional<String> name) {
    /** Categories of non-business day. */
    public enum Kind {
        /** Funds move. */
        BUSINESS_DAY,

        /** Saturday or Sunday. */
        WEEKEND,

        /** A 国民の祝日, including 振替休日 substitute holidays. */
        PUBLIC_HOLIDAY,

        /**
         * 31 December to 3 January, when financial institutions do not process
         * transfers even though 1 January is the only public holiday among them.
         */
        YEAR_END_CLOSURE
    }

    /** A business day. */
    public static final NonBusinessDay BUSINESS_DAY =
            new NonBusinessDay(Kind.BUSINESS_DAY, Optional.empty());

    /**
     * Whether funds move.
     *
     * @return {@code true} if this is a business day
     */
    public boolean isBusinessDay() {
        return kind == Kind.BUSINESS_DAY;
    }
}
