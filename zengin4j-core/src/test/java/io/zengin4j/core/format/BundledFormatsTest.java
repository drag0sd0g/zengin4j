package io.zengin4j.core.format;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.CharacterClass;
import org.junit.jupiter.api.Test;

/// Issues 3.4 and 3.5: the remaining 120-byte formats.
///
/// These tests assert the things that make each format *different*
/// from 総合振込. Asserting that a layout loads proves the loader works; asserting
/// where it diverges proves the layout was read rather than derived, which is
/// the failure mode §13.1 warns about twice.
class BundledFormatsTest {

    private final FormatRegistry registry = FormatRegistry.defaults();

    private FormatDescriptor format(String id) {
        return registry.byId(FormatId.of(id)).orElseThrow();
    }

    @Test
    void bundlesTheFourOneHundredAndTwentyByteFormats() {
        assertThat(registry.all()).extracting(descriptor -> descriptor.id().value())
                .containsExactlyInAnyOrder(
                        "sougou-furikomi", "kyuyo-furikomi", "shoyo-furikomi", "kouza-furikae");
        assertThat(registry.all()).allSatisfy(descriptor -> {
            assertThat(descriptor.recordLength()).as("%s", descriptor.id()).isEqualTo(120);
            assertThat(descriptor.verified()).as("%s", descriptor.id()).isFalse();
            assertThat(descriptor.sources()).as("%s", descriptor.id())
                    .hasSizeGreaterThanOrEqualTo(FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION);
        });
    }

    // ------------------------------------------------------------ 給与振込 (3.4)

    /// The whole reason 給与振込 has its own descriptor: fourteen data fields, not
    /// sixteen. Deriving it from 総合振込 would read filler as a 振込指定区分 and an
    /// 識別表示, and 社員番号 as an EDI payload.
    @Test
    void payrollTransferHasFourteenDataFieldsNotSixteen() {
        RecordDescriptor payroll = format("kyuyo-furikomi").record(RecordKind.DATA);
        RecordDescriptor bulk = format("sougou-furikomi").record(RecordKind.DATA);

        assertThat(payroll.fields()).hasSize(14);
        assertThat(bulk.fields()).hasSize(16);

        // The two agree byte for byte up to the tail, which is what makes the
        // difference easy to miss.
        assertThat(payroll.field("amount").offset()).isEqualTo(bulk.field("amount").offset()).isEqualTo(80);
        assertThat(payroll.field("newCode").offset()).isEqualTo(bulk.field("newCode").offset()).isEqualTo(90);

        // And then they do not.
        assertThat(payroll.find("transferCategory")).isEmpty();
        assertThat(payroll.find("identification")).isEmpty();
        assertThat(payroll.find("customerCode1")).isEmpty();
        assertThat(payroll.field("employeeNumber").offset()).isEqualTo(91);
        assertThat(payroll.field("departmentCode").offset()).isEqualTo(101);
        assertThat(payroll.field("dummy").offset()).isEqualTo(111);
        assertThat(payroll.field("dummy").length()).isEqualTo(9);
    }

    /// The payee field sits at the same offset as 総合振込's and permits a
    /// narrower character set — no Latin letters at all.
    @Test
    void payrollTransferForbidsLatinLettersInThePayeeName() {
        RecordDescriptor payroll = format("kyuyo-furikomi").record(RecordKind.DATA);

        FieldDescriptor name = payroll.field("beneficiaryName");
        assertThat(name.nameJa()).isEqualTo("受取人名");
        assertThat(name.length()).isEqualTo(30);
        assertThat(name.charClass()).isEqualTo(CharacterClass.PAYROLL_NAME);
        assertThat(name.charClass().permits('A')).isFalse();

        // The same bytes in 総合振込 hold 受取人名 too, and do permit them.
        FieldDescriptor payee = format("sougou-furikomi").record(RecordKind.DATA).field("beneficiaryName");
        assertThat(payee.offset()).isEqualTo(name.offset());
        assertThat(payee.charClass().permits('A')).isTrue();
    }

    /// D-002 again, in a third place. 社員番号 and 所属コード occupy the bytes
    /// 総合振込 uses for 顧客コード1/2, and sources split on their attribute the
    /// same way. Declared C, which preserves either reading.
    @Test
    void payrollCustomerCodeFieldsAreDeclaredTextLikeTheirBulkTransferCounterparts() {
        RecordDescriptor payroll = format("kyuyo-furikomi").record(RecordKind.DATA);
        RecordDescriptor bulk = format("sougou-furikomi").record(RecordKind.DATA);

        assertThat(payroll.field("employeeNumber").type())
                .isEqualTo(bulk.field("customerCode1").type())
                .isEqualTo(io.zengin4j.core.format.FieldType.C);
        assertThat(payroll.field("departmentCode").type())
                .isEqualTo(io.zengin4j.core.format.FieldType.C);
        assertThat(payroll.field("employeeNumber").offset())
                .isEqualTo(bulk.field("customerCode1").offset());
    }

    @Test
    void payrollTransferAdmitsOnlyTwoAccountTypes() {
        assertThat(format("kyuyo-furikomi").record(RecordKind.DATA).field("accountType").codes())
                .containsExactly("1", "2");
        assertThat(format("sougou-furikomi").record(RecordKind.DATA).field("accountType").codes())
                .containsExactly("1", "2", "4", "9");
    }

    /// 賞与振込 borrows the layout, which the standard states — and only the type code differs.
    @Test
    void bonusTransferIsPayrollTransferWithADifferentTypeCode() {
        FormatDescriptor payroll = format("kyuyo-furikomi");
        FormatDescriptor bonus = format("shoyo-furikomi");

        assertThat(bonus.typeCode()).isEqualTo("12");
        assertThat(payroll.typeCode()).isEqualTo("11");

        for (RecordKind kind : List.of(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER, RecordKind.END)) {
            List<FieldDescriptor> from = payroll.record(kind).fields();
            List<FieldDescriptor> to = bonus.record(kind).fields();

            assertThat(to).as("%s", kind).hasSameSizeAs(from);
            for (int i = 0; i < from.size(); i++) {
                FieldDescriptor a = from.get(i);
                FieldDescriptor b = to.get(i);
                assertThat(b.id()).isEqualTo(a.id());
                assertThat(b.offset()).as("%s.%s offset", kind, a.id()).isEqualTo(a.offset());
                assertThat(b.length()).as("%s.%s length", kind, a.id()).isEqualTo(a.length());
                assertThat(b.type()).isEqualTo(a.type());
                assertThat(b.charClass()).isEqualTo(a.charClass());
                // The 種別コード constant is the one thing that differs, and it is
                // rewritten rather than inherited.
                if (a.id().equals("typeCode")) {
                    assertThat(b.constant()).contains("12");
                    assertThat(a.constant()).contains("11");
                } else {
                    assertThat(b.constant()).as("%s.%s", kind, a.id()).isEqualTo(a.constant());
                }
            }
        }
    }

    // ---------------------------------------------------------- 預金口座振替 (3.5)

    /// OQ-1: one descriptor, because the instruction and result files have the
    /// same layout. Two would make every 91 file ambiguous while distinguishing
    /// nothing.
    @Test
    void directDebitIsOneDescriptorForBothDirections() {
        assertThat(registry.byTypeCode("91")).hasSize(1);
        assertThat(registry.byTypeCode("91").get(0).id()).isEqualTo(FormatId.of("kouza-furikae"));

        // The field that carries the difference is present in both, and is a
        // value rather than a position.
        FieldDescriptor result = format("kouza-furikae").record(RecordKind.DATA).field("transferResult");
        assertThat(result.offset()).isEqualTo(111);
        assertThat(result.codeList()).isPresent();
        assertThat(result.codeList().orElseThrow().byCode("1").orElseThrow().nameEn())
                .isEqualTo("Insufficient funds");
        assertThat(result.codeList().orElseThrow().byCode("4").orElseThrow().nameEn())
                .as("code 4 is a missing mandate, not a closed account")
                .isEqualTo("No direct debit mandate on file");
    }

    /// §13.1's strong requirement: the direction must be visible in the names.
    /// The header names where funds *land*; the data records name accounts
    /// to be *debited*. Reusing 総合振込's names would invert the payment.
    @Test
    void directDebitNamesItsDirectionExplicitly() {
        RecordDescriptor header = format("kouza-furikae").record(RecordKind.HEADER);
        RecordDescriptor data = format("kouza-furikae").record(RecordKind.DATA);

        assertThat(header.find("collectionBankCode")).isPresent();
        assertThat(header.find("collectionAccountNumber")).isPresent();
        assertThat(header.find("debitDate")).isPresent();
        assertThat(data.find("payerBankCode")).isPresent();
        assertThat(data.find("payerName")).isPresent();
        assertThat(data.find("debitAmount")).isPresent();

        // None of 総合振込's directional names appear, in either record.
        for (String borrowed : List.of("originBankCode", "originBranchCode", "beneficiaryBankCode",
                "beneficiaryName", "valueDate", "amount")) {
            assertThat(header.find(borrowed)).as("header must not reuse '%s'", borrowed).isEmpty();
            assertThat(data.find(borrowed)).as("data must not reuse '%s'", borrowed).isEmpty();
        }
    }

    /// Q6: the trailer is not 総合振込's, and must not be derived from it.
    @Test
    void directDebitHasItsOwnTrailerCarryingResultTotals() {
        RecordDescriptor trailer = format("kouza-furikae").record(RecordKind.TRAILER);

        assertThat(trailer.fields()).hasSize(8);
        assertThat(trailer.field("recordCount").offset()).isEqualTo(1);
        assertThat(trailer.field("totalAmount").offset()).isEqualTo(7);
        assertThat(trailer.field("collectedCount").offset()).isEqualTo(19);
        assertThat(trailer.field("collectedAmount").offset()).isEqualTo(25);
        assertThat(trailer.field("uncollectedCount").offset()).isEqualTo(37);
        assertThat(trailer.field("uncollectedAmount").offset()).isEqualTo(43);
        assertThat(trailer.field("dummy").offset()).isEqualTo(55);
        assertThat(trailer.field("dummy").length()).isEqualTo(65);

        // 総合振込's trailer is three fields and one big filler.
        assertThat(format("sougou-furikomi").record(RecordKind.TRAILER).fields()).hasSize(4);
    }

    /// 預金口座振替 admits 納税準備預金, which 総合振込 does not.
    @Test
    void directDebitAdmitsATaxReserveAccount() {
        assertThat(format("kouza-furikae").record(RecordKind.DATA).field("payerAccountType").codes())
                .containsExactly("1", "2", "3", "9");
        assertThat(format("kouza-furikae").record(RecordKind.HEADER).field("collectionAccountType").codes())
                .containsExactly("1", "2", "9");
    }

    /// The four bytes 総合振込 uses for 手形交換所番号 are unused here, and say so.
    @Test
    void directDebitLeavesTheClearingHouseBytesUnused() {
        FieldDescriptor reserved = format("kouza-furikae").record(RecordKind.DATA).field("reserved");

        assertThat(reserved.offset()).isEqualTo(38);
        assertThat(reserved.length()).isEqualTo(4);
        assertThat(reserved.filler()).isTrue();
        assertThat(format("sougou-furikomi").record(RecordKind.DATA).field("clearingHouseCode").offset())
                .isEqualTo(38);
    }

    // ------------------------------------------------------------------- OQ-9

    /// The master list carries all nine codes; fields narrow it.
    @Test
    void accountTypeIsTheMasterListNarrowedPerField() {
        CodeList accountType = registry.codeLists().get("accountType");

        assertThat(accountType.values()).hasSize(9);
        assertThat(accountType.byCode("3").orElseThrow().nameEn()).isEqualTo("Tax reserve deposit");
        assertThat(accountType.byCode("6").orElseThrow().nameEn()).isEqualTo("Time deposit");

        // Every field referencing it narrows it, and no narrowing names a code
        // the master list does not have.
        for (FormatDescriptor descriptor : registry.all()) {
            for (RecordKind kind : List.of(RecordKind.HEADER, RecordKind.DATA)) {
                descriptor.find(kind).ifPresent(record -> record.fields().stream()
                        .filter(field -> field.codeList().isPresent())
                        .forEach(field -> assertThat(field.codes())
                                .as("%s %s %s", descriptor.id(), kind, field.id())
                                .allSatisfy(code -> assertThat(
                                        field.codeList().orElseThrow().byCode(code)).isPresent())));
            }
        }
    }
}
