package io.zengin4j.validation.refdata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference data the caller supplies, held in memory.
 *
 * <p>The shape {@code zengin-code} and every similar dataset already has: bank
 * code to name, and (bank, branch) to name. Loading it is the caller's job,
 * because the caller is the one who knows how fresh their copy is and where it
 * came from.
 *
 * <p>No snapshot ships with this library. Institution data goes stale — banks
 * merge, branches close, codes move — and a copy compiled into a released jar
 * would look authoritative while being wrong, on a question where being wrong
 * means a payment goes nowhere (R-V5).
 *
 * @since 0.2.0
 */
public final class MapReferenceData implements ReferenceDataProvider {

    private final Map<String, String> banks;
    private final Map<String, String> branches;
    private final String description;

    private MapReferenceData(Map<String, String> banks, Map<String, String> branches, String description) {
        this.banks = Map.copyOf(banks);
        this.branches = Map.copyOf(branches);
        this.description = description;
    }

    /**
     * Starts building a provider.
     *
     * @param description what this data is and when it was captured; it appears
     *                    in reports, so "zengin-code 2026-08" beats "reference
     *                    data"
     * @return a builder
     */
    public static Builder describedAs(String description) {
        return new Builder(description);
    }

    @Override
    public boolean bankExists(String bankCode) {
        return banks.containsKey(bankCode);
    }

    @Override
    public boolean branchExists(String bankCode, String branchCode) {
        return branches.containsKey(key(bankCode, branchCode));
    }

    @Override
    public Optional<String> bankNameKana(String bankCode) {
        return Optional.ofNullable(banks.get(bankCode));
    }

    @Override
    public Optional<String> branchNameKana(String bankCode, String branchCode) {
        return Optional.ofNullable(branches.get(key(bankCode, branchCode)));
    }

    @Override
    public String describe() {
        return description;
    }

    private static String key(String bankCode, String branchCode) {
        return bankCode + '/' + branchCode;
    }

    /**
     * Assembles a provider.
     *
     * @since 0.2.0
     */
    public static final class Builder {

        private final Map<String, String> banks = new HashMap<>();
        private final Map<String, String> branches = new HashMap<>();
        private final String description;

        private Builder(String description) {
            this.description = Objects.requireNonNull(description, "description");
        }

        /**
         * Adds a bank.
         *
         * @param bankCode the four-digit code
         * @param nameKana its katakana name
         * @return this builder
         */
        public Builder bank(String bankCode, String nameKana) {
            banks.put(bankCode, nameKana);
            return this;
        }

        /**
         * Adds a branch.
         *
         * @param bankCode   the four-digit bank code
         * @param branchCode the three-digit branch code
         * @param nameKana   its katakana name
         * @return this builder
         */
        public Builder branch(String bankCode, String branchCode, String nameKana) {
            branches.put(key(bankCode, branchCode), nameKana);
            return this;
        }

        /**
         * Builds the provider.
         *
         * @return the provider
         */
        public MapReferenceData build() {
            return new MapReferenceData(banks, branches, description);
        }
    }
}
