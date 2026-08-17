package io.zengin4j.validation.refdata;

import java.util.Optional;

/**
 * Bank and branch reference data (R-V5).
 *
 * <p><strong>Optional and pluggable, and the library is complete without it.</strong>
 * No snapshot is bundled: institution data goes stale — banks merge, branches
 * close, codes are reassigned — and a snapshot compiled into a released jar
 * would be wrong within months while looking authoritative. A consumer who
 * needs these checks supplies data they control and can refresh; a consumer who
 * does not gets every other tier unaffected.
 *
 * <p>{@code zengin-code} is the obvious public dataset to wire up, and
 * {@link MapReferenceData} loads that shape.
 *
 * @since 0.2.0
 */
public interface ReferenceDataProvider {
    /**
     * Whether a bank code is known.
     *
     * @param bankCode the four-digit 統一金融機関番号
     * @return {@code true} if the provider knows it
     */
    boolean bankExists(String bankCode);

    /**
     * Whether a branch is known within a bank.
     *
     * @param bankCode   the four-digit bank code
     * @param branchCode the three-digit 統一店番号
     * @return {@code true} if the provider knows it
     */
    boolean branchExists(String bankCode, String branchCode);

    /**
     * The bank's katakana name, where the provider has one.
     *
     * @param bankCode the four-digit bank code
     * @return the name, or empty
     */
    Optional<String> bankNameKana(String bankCode);

    /**
     * The branch's katakana name, where the provider has one.
     *
     * @param bankCode   the four-digit bank code
     * @param branchCode the three-digit branch code
     * @return the name, or empty
     */
    Optional<String> branchNameKana(String bankCode, String branchCode);

    /**
     * What this data is and when it was captured, for a report that has to be
     * honest about how old its reference data was.
     *
     * @return a short description, never {@code null}
     */
    String describe();
}
