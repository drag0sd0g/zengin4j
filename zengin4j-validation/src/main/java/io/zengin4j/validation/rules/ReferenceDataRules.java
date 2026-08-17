package io.zengin4j.validation.rules;

import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.engine.ValidationContext;
import io.zengin4j.validation.refdata.ReferenceDataProvider;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tier 4 — do the institutions in this file exist? (§14.3, {@code V-4xx})
 *
 * <p>Skipped entirely when no provider is supplied, which is the default
 * (R-V5). A bank code this library has never heard of is not a finding; a bank
 * code the <em>caller's own reference data</em> has never heard of is.
 *
 * <p>Every finding names the data it was checked against, because reference
 * data goes stale and a report that says "bank 1234 does not exist" without
 * saying "according to what, captured when" invites the reader to trust it
 * further than they should.
 *
 * @since 0.2.0
 */
public final class ReferenceDataRules {
    /** Field-id pairs of (bank, branch), across the formats this library ships. */
    private static final List<String[]> BANK_BRANCH_PAIRS = List.of(
            new String[] {"beneficiaryBankCode", "beneficiaryBranchCode"},
            new String[] {"payerBankCode", "payerBranchCode"},
            new String[] {"originBankCode", "originBranchCode"},
            new String[] {"collectionBankCode", "collectionBranchCode"});

    private ReferenceDataRules() {
    }

    /**
     * Every rule in this tier.
     *
     * @return the rules, never {@code null}
     */
    public static List<Rule> all() {
        return List.of(new BankExists(), new BranchExists(), new NamesMatchReferenceData());
    }

    /** Walks every (bank, branch) code pair present in any record. */
    private static void forEachInstitution(ValidationContext context, InstitutionVisitor visitor) {
        for (ZenginRecord record : StructuralRules.inOrder(context.file())) {
            RecordDescriptor layout = SyntaxRules.layoutOf(context, record);
            if (layout == null) {
                continue;
            }
            for (String[] pair : BANK_BRANCH_PAIRS) {
                Optional<FieldDescriptor> bank = layout.find(pair[0]);
                if (bank.isEmpty()) {
                    continue;
                }
                Optional<FieldDescriptor> branch = layout.find(pair[1]);
                visitor.visit(record, bank.orElseThrow(), branch.orElse(null), record.rawBytes());
            }
        }
    }

    private interface InstitutionVisitor {
        void visit(ZenginRecord record, FieldDescriptor bank, FieldDescriptor branch, byte[] bytes);
    }

    /**
     * V-403 — the name in the file disagrees with the reference data.
     *
     * <p>A warning, not an error. Institutions abbreviate, and a file saying
     * {@code ﾃｽﾄｷﾞﾝｺｳ} where the dataset says {@code ﾃｽﾄｷﾞﾝｺｳ(ｶ} is usually
     * fine. It is worth a line because a name that disagrees with its code is
     * also what a transposed code looks like — and the code is what the money
     * follows.
     */
    static final class NamesMatchReferenceData extends AbstractRule {
        NamesMatchReferenceData() {
            super("V-403", Severity.WARNING, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            ReferenceDataProvider data = context.referenceData().orElse(null);
            if (data == null) {
                return;
            }
            forEachInstitution(context, (record, bank, branch, bytes) -> {
                RecordDescriptor layout = SyntaxRules.layoutOf(context, record);
                if (layout == null) {
                    return;
                }
                String bankCode = SyntaxRules.raw(bytes, bank).trim();
                if (bankCode.isEmpty()) {
                    return;
                }
                compare(out, record, layout, bank.id().replace("Code", "Name"),
                        data.bankNameKana(bankCode), bytes);
                if (branch != null) {
                    String branchCode = SyntaxRules.raw(bytes, branch).trim();
                    compare(out, record, layout, branch.id().replace("Code", "Name"),
                            data.branchNameKana(bankCode, branchCode), bytes);
                }
            });
        }

        private void compare(Consumer<Finding> out, ZenginRecord record, RecordDescriptor layout,
                String nameFieldId, Optional<String> expected, byte[] bytes) {
            if (expected.isEmpty()) {
                return;
            }
            layout.find(nameFieldId).ifPresent(field -> {
                String actual = SyntaxRules.raw(bytes, field).stripTrailing();
                if (actual.isBlank() || actual.equals(expected.orElseThrow())) {
                    return;
                }
                out.accept(Messages.format(id() + ".message",
                                field.id(), actual, expected.orElseThrow())
                        .into(finding().at(record.recordNumber(), record.byteOffset())
                                .field(field.id(), field.offset()))
                        .actual(actual)
                        .expected(expected.orElseThrow())
                        .build());
            });
        }
    }

    /** V-401. */
    static final class BankExists extends AbstractRule {
        BankExists() {
            super("V-401", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            ReferenceDataProvider data = context.referenceData().orElse(null);
            if (data == null) {
                return;
            }
            forEachInstitution(context, (record, bank, branch, bytes) -> {
                String code = SyntaxRules.raw(bytes, bank).trim();
                if (code.isEmpty() || code.chars().allMatch(c -> c == '0')) {
                    return;
                }
                if (!data.bankExists(code)) {
                    out.accept(Messages.format(id() + ".message", code)
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(bank.id(), bank.offset()))
                            .actual(code)
                            .expected("a bank code in " + data.describe())
                            .build());
                }
            });
        }
    }

    /**
     * V-402. Only reported when the bank itself is known — a branch check
     * against an unknown bank would report the same problem twice, and the
     * second report would be less useful than the first.
     */
    static final class BranchExists extends AbstractRule {
        BranchExists() {
            super("V-402", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            ReferenceDataProvider data = context.referenceData().orElse(null);
            if (data == null) {
                return;
            }
            forEachInstitution(context, (record, bank, branch, bytes) -> {
                if (branch == null) {
                    return;
                }
                String bankCode = SyntaxRules.raw(bytes, bank).trim();
                String branchCode = SyntaxRules.raw(bytes, branch).trim();
                if (bankCode.isEmpty() || branchCode.isEmpty() || !data.bankExists(bankCode)) {
                    return;
                }
                if (!data.branchExists(bankCode, branchCode)) {
                    out.accept(Messages.format(id() + ".message", branchCode, bankCode)
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(branch.id(), branch.offset()))
                            .actual(branchCode)
                            .expected("a branch of bank " + bankCode + " in " + data.describe())
                            .build());
                }
            });
        }
    }
}
