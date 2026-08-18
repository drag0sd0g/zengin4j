package io.zengin4j.iso20022.api;

/**
 * Where {@code EndToEndId} lives on the Zengin side.
 *
 * <p>ISO 20022 makes {@code EndToEndId} mandatory and gives it 35 characters:
 * it is the reference the debtor and the creditor reconcile against, and it is
 * meant to survive the whole payment chain unchanged. The Zengin formats have
 * no field for it. The nearest thing is a 顧客コード — ten bytes, and already
 * carrying whatever the originator puts there.
 *
 * <p>So there is no correct answer, only a choice the caller has to make and
 * live with, which is why this is a policy rather than a default (Q2). Every
 * option loses something, and each says so in the loss report.
 *
 * @since 0.5.0
 */
public enum EndToEndIdPolicy {

    /**
     * Carry it in 顧客コード1.
     *
     * <p>The natural home — the table in §15.9 puts it here — but ten bytes
     * against thirty-five. A reference that does not fit is truncated and
     * reported {@code CRITICAL}, because a truncated reconciliation reference
     * is worse than an absent one: it looks usable and matches the wrong
     * payment.
     */
    CUSTOMER_CODE_1,

    /**
     * Carry it in 顧客コード2.
     *
     * <p>Same size, same truncation risk. Worth choosing when 顧客コード1 is
     * already committed to something the originator's own systems key on.
     */
    CUSTOMER_CODE_2,

    /**
     * Do not carry it.
     *
     * <p>Reported {@code DROPPED} at {@code MATERIAL} on the way down, and
     * written as {@code NOTPROVIDED} on the way up — the value the standard
     * defines for a reference the debtor did not supply. Honest, and the right
     * choice when the 顧客コード fields already mean something and truncating a
     * reference into them would corrupt both.
     */
    DROP
}
