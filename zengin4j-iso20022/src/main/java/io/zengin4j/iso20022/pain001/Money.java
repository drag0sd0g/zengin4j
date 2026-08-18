package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.xml.XmlElement;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * An instructed amount and its currency.
 *
 * <p>{@code BigDecimal} rather than {@code long}, although a Zengin amount is
 * always whole yen: the XML side is {@code DecimalNumber} and a sender is free
 * to write {@code 150000.00}, or a non-JPY amount, or a fraction of a yen.
 * Reading those into a long would either round or refuse. Reading them into a
 * decimal lets the mapping say precisely what it did — a fractional yen is a
 * {@code COERCED} loss at {@code CRITICAL}, not a rounding nobody sees.
 *
 * @param amount   the amount
 * @param currency the ISO 4217 code
 * @since 0.5.0
 */
public record Money(BigDecimal amount, String currency) {

    /** The only currency a Zengin file can express. */
    public static final String JPY = "JPY";

    /**
     * ISO 4217's code for "no currency involved", used here for an amount that
     * could not be read.
     *
     * <p>Not a placeholder: it is the standard's own way of saying that a figure
     * is not a currency amount, and it flows through the same path a foreign
     * currency does — reported {@code CRITICAL}, because a payment whose amount
     * could not be read is not a payment for zero.
     */
    public static final Money UNREADABLE = new Money(BigDecimal.ZERO, "XXX");

    /**
     * The most integer digits an amount may carry.
     *
     * <p>A guard against untrusted input, not a business limit. {@code xs:decimal}
     * admits {@code 1e2000000000}, which parses in microseconds and exhausts a
     * heap the moment anything renders it — thirteen bytes of input for an
     * {@code OutOfMemoryError}. Thirty digits is a thousand times the money in
     * the world; nothing legitimate comes near it.
     */
    public static final int MAX_INTEGER_DIGITS = 30;

    /**
     * Validates the amount.
     *
     * @throws NullPointerException     if either component is null
     * @throws IllegalArgumentException if the currency is not three letters, or
     *                                  the amount carries more than
     *                                  {@link #MAX_INTEGER_DIGITS} integer digits
     */
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "'" + currency + "' is not an ISO 4217 code: they are exactly three letters");
        }
        long digits = amount.precision() - (long) amount.scale();
        if (digits > MAX_INTEGER_DIGITS) {
            // Deliberately renders the value in scientific notation. toPlainString
            // on the value being refused is what this guard exists to prevent.
            throw new IllegalArgumentException("the amount " + amount + " has " + digits
                    + " integer digits, and no payment has more than " + MAX_INTEGER_DIGITS
                    + ". Rendering it would allocate one character per digit.");
        }
    }

    /**
     * A whole-yen amount.
     *
     * @param yen the amount in yen
     * @return the amount
     */
    public static Money yen(long yen) {
        return new Money(BigDecimal.valueOf(yen), JPY);
    }

    /**
     * Reads an amount from its element.
     *
     * @param element the {@code Amt} element
     * @return the amount, or empty when {@code InstdAmt} is absent, is not a
     *         number, or carries a currency code that is not three letters
     */
    public static Optional<Money> from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return element.at("InstdAmt").flatMap(instructed -> {
            try {
                return Optional.of(new Money(new BigDecimal(instructed.text()),
                        instructed.attribute("Ccy").orElse(JPY)));
            } catch (IllegalArgumentException notAnAmount) {
                return Optional.empty();
            }
        });
    }

    /** @return true if this is the marker for an amount that could not be read */
    public boolean isUnreadable() {
        return UNREADABLE.equals(this);
    }

    /** @return true if the currency is JPY */
    public boolean isYen() {
        return JPY.equals(currency);
    }

    /** @return true if the amount has a non-zero fractional part */
    public boolean hasFraction() {
        return amount.stripTrailingZeros().scale() > 0;
    }

    /**
     * The amount as whole yen, truncated toward zero.
     *
     * @return the amount in yen
     */
    public long toYen() {
        return amount.toBigInteger().longValueExact();
    }

    /**
     * Renders the amount.
     *
     * @return the {@code Amt} element
     */
    public XmlElement toXml() {
        return XmlElement.element("Amt")
                .child(XmlElement.element("InstdAmt")
                        .attribute("Ccy", currency)
                        .text(amount.stripTrailingZeros().toPlainString()))
                .build();
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
