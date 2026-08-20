package io.zengin4j.iso20022.api;

import module java.base;
import io.zengin4j.core.codec.EncodingOptions;
import io.zengin4j.core.codec.ZenginFileBuilder;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.FieldTooSmallException;
import io.zengin4j.core.kana.KanaTransliterator;
import io.zengin4j.core.kana.Transliteration;
import io.zengin4j.core.kana.TransliterationOptions;
import io.zengin4j.core.kana.UntransliterableCharacterException;
import io.zengin4j.core.kana.ValueTooLongException;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.loss.MappingLossReport;
import io.zengin4j.iso20022.pain001.Agent;
import io.zengin4j.iso20022.pain001.CreditTransferTransaction;
import io.zengin4j.iso20022.pain001.GroupHeader;
import io.zengin4j.iso20022.pain001.EdiAttachment;
import io.zengin4j.iso20022.pain001.Money;
import io.zengin4j.iso20022.pain001.Pain001Document;
import io.zengin4j.iso20022.pain001.PaymentInstruction;
import io.zengin4j.iso20022.xml.XmlElement;

/// The downward leg: a `pain.001` document becomes a Zengin file.
///
/// This is where conversion stops being a translation and starts being a
/// choice. A creditor name has 140 characters of any script and thirty bytes of
/// half-width katakana to land in; a reference has thirty-five characters and
/// ten bytes; a date has a year that the destination cannot hold. Nothing here
/// can be made lossless, so everything here is reported.
///
/// Values the XML does not carry come from [MappingContext], which is
/// required rather than defaulted (R-I20). 委託者コード in particular: an
/// initiating party identifier is not required to be the originator code the
/// receiving bank knows, and assuming it is would produce a file the bank
/// rejects for a reason nobody could see in the XML.
final class Pain001ToZengin {

    private final MappingContext context;
    private final LossCollector loss = new LossCollector();

    Pain001ToZengin(MappingContext context) {
        this.context = context;
    }

    MappingResult<ZenginFile> convert(Pain001Document document, XmlElement body) {
        FormatDescriptor descriptor = context.requireTargetFormat();

        reportFlattening(document);

        PaymentInstruction first = document.payments().isEmpty()
                ? null
                : document.payments().getFirst();

        ZenginFileBuilder builder = ZenginFileBuilder.forFormat(descriptor)
                .allowUnverifiedFormats(true)
                .charset(context.targetCharset())
                .encoding(encodingOptions(), loss);

        builder.header(values -> {
            values.set("originatorCode", context.originatorCode());
            values.set("originatorName", narrow(
                    first == null ? document.groupHeader().initiatingParty().name()
                            : first.debtor().name(),
                    descriptor, RecordKind.HEADER, "originatorName",
                    IsoPaths.INITIATING_PARTY_NAME, LossSeverity.MATERIAL));
            if (first != null) {
                values.set("valueDate", executionDate(first));
                values.set("originBankCode", identifier(first.debtorAgent().bankCode(),
                        descriptor, RecordKind.HEADER, "originBankCode",
                        IsoPaths.DEBTOR_AGENT_MEMBER));
                values.set("originBranchCode", identifier(first.debtorAgent().branchCode(),
                        descriptor, RecordKind.HEADER, "originBranchCode",
                        IsoPaths.DEBTOR_AGENT_MEMBER));
                reportUnsplittableMember(first.debtorAgent(), IsoPaths.DEBTOR_AGENT_MEMBER,
                        "header.originBranchCode");
                values.set("originBankName", narrow(first.debtorAgent().name(),
                        descriptor, RecordKind.HEADER, "originBankName",
                        IsoPaths.DEBTOR_AGENT_NAME, LossSeverity.INFORMATIONAL));
                values.set("accountNumber", identifier(first.debtorAccount().number(),
                        descriptor, RecordKind.HEADER, "accountNumber",
                        "CstmrCdtTrfInitn/PmtInf/DbtrAcct/Id/Othr/Id"));
                values.set("accountType", accountType(first.debtorAccount().proprietaryType(),
                        IsoPaths.DEBTOR_ACCOUNT_TYPE, "header.accountType"));
            }
        });

        for (PaymentInstruction instruction : document.payments()) {
            for (CreditTransferTransaction transaction : instruction.transactions()) {
                payment(builder, descriptor, transaction);
            }
        }

        crossCheckGroupHeader(document, body);
        reportDroppedInstructionIds(document);
        reportOriginatorCodeOverride(document);
        reportFieldsWithNoIsoSource(document);
        return new MappingResult<>(builder.build(), MappingLossReport.of(loss.build()));
    }

    /// Several `PmtInf` blocks become one batch, and that costs something
    /// only when they disagree.
    ///
    /// A Zengin batch has one header, so one execution date, one debit
    /// account and one originating bank. When the blocks agree on all three the
    /// flattening loses only the grouping, which nothing downstream reads —
    /// reported `INFORMATIONAL`. When they disagree, the first block's
    /// values are applied to every payment in the file, including payments that
    /// asked for a different date or a different account: that is
    /// `CRITICAL`, and it is not a subtle difference to bury in a note
    /// about structure.
    private void reportFlattening(Pain001Document document) {
        if (document.payments().size() < 2) {
            return;
        }
        int blocks = document.payments().size();
        boolean agree = document.payments().stream()
                .map(instruction -> List.of(
                        instruction.requestedExecutionDate(),
                        instruction.debtorAccount(),
                        instruction.debtorAgent()))
                .distinct()
                .count() == 1;

        if (agree) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.INFORMATIONAL,
                    blocks + " payment instructions", "1 batch",
                    "the document carries " + blocks + " PmtInf blocks and they became one batch. "
                            + "They agree on execution date, debit account and originating bank, "
                            + "so only the grouping is lost.",
                    "本電文の " + blocks + " 件の PmtInf を 1 つのバッチに統合しました。"
                            + "振込指定日・引落口座・仕向銀行は一致しているため、"
                            + "失われるのはグループ分けのみです。"));
            return;
        }
        loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                blocks + " payment instructions", "1 batch",
                "the document carries " + blocks + " PmtInf blocks that do not agree on execution "
                        + "date, debit account or originating bank, and a Zengin batch has one of "
                        + "each. The first block's values were applied to every payment in the "
                        + "file — including payments that asked for a different date or a "
                        + "different account.",
                "本電文の " + blocks + " 件の PmtInf は振込指定日・引落口座・仕向銀行が一致して"
                        + "いませんが、全銀のバッチはそれぞれ 1 つしか持てません。先頭の値を"
                        + "ファイル内のすべての明細に適用しました。異なる日付や口座を指定していた"
                        + "明細も含まれます。"));
    }

    /// The document said one thing and its own payments say another.
    ///
    /// The mirror of the trailer cross-check on the upward leg, and it was
    /// missing: `GrpHdr/NbOfTxs` and `CtrlSum` are read on the way
    /// out, computed on the way in, and until this existed nothing compared them
    /// to the payments the document actually carries. A `pain.001` that
    /// contradicts itself is exactly as suspect as a Zengin file whose trailer
    /// does — and V-301 and V-302 have caught the latter since Epic 4.
    ///
    /// The payments are what is converted, because they are what the money
    /// is. The disagreement is `CRITICAL`: neither number can be trusted
    /// once they differ, and the one that was wrong might be the one somebody
    /// reconciles against.
    private void crossCheckGroupHeader(Pain001Document document, XmlElement body) {
        Optional<XmlElement> header = body.at(Pain001Document.ELEMENT + "/" + GroupHeader.ELEMENT);
        if (header.isEmpty()) {
            return;
        }

        long counted = document.numberOfTransactions();
        GroupHeader.declaredNumberOfTransactions(header.get())
                .filter(declared -> declared != counted)
                .ifPresent(declared -> loss.record(LossEntry.of(LossKind.COERCED,
                                LossSeverity.CRITICAL,
                                String.valueOf(declared), String.valueOf(counted),
                                "GrpHdr/NbOfTxs says " + declared + " and the document carries "
                                        + counted + " payments. The payments were converted; the "
                                        + "count the sender wrote is not what the sender sent.",
                                "GrpHdr/NbOfTxs は " + declared + " ですが、電文には " + counted
                                        + " 件の明細があります。明細を変換しました。送信者が"
                                        + "記載した件数は実際の内容と一致していません。")
                        .at(IsoPaths.NUMBER_OF_TRANSACTIONS, "trailer.recordCount")));

        java.math.BigDecimal summed = document.controlSum();
        GroupHeader.declaredControlSum(header.get())
                .filter(declared -> declared.compareTo(summed) != 0)
                .ifPresent(declared -> loss.record(LossEntry.of(LossKind.COERCED,
                                LossSeverity.CRITICAL,
                                declared.toPlainString(), summed.toPlainString(),
                                "GrpHdr/CtrlSum says " + declared.toPlainString()
                                        + " and the payments add up to " + summed.toPlainString()
                                        + ". The payments were converted.",
                                "GrpHdr/CtrlSum は " + declared.toPlainString()
                                        + " ですが、明細の合計は " + summed.toPlainString()
                                        + " です。明細を変換しました。")
                        .at(IsoPaths.CONTROL_SUM, "trailer.totalAmount")));
    }

    /// The debtor's own reference, which the Zengin formats have no field for.
    ///
    /// `InstrId` is optional and distinct from `EndToEndId`: it is
    /// the debtor's reference to its own bank, not the one the creditor
    /// reconciles against. There is nowhere to put it — both 顧客コード fields are
    /// already spoken for by the `EndToEndId` policy and the remittance
    /// text — so it is dropped, and said to be.
    private void reportDroppedInstructionIds(Pain001Document document) {
        long carrying = document.transactions().stream()
                .filter(transaction -> !transaction.instructionId().isBlank())
                .count();
        if (carrying == 0) {
            return;
        }
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                carrying + " InstrId", "",
                carrying + " payment" + (carrying == 1 ? " carries" : "s carry")
                        + " a PmtId/InstrId, the debtor's own reference to its own bank. The "
                        + "Zengin formats have no field for it and both 顧客コード fields are "
                        + "spoken for, so it was not carried.",
                carrying + " 件の明細が PmtId/InstrId(委託者から自行への参照番号)を持ちますが、"
                        + "全銀側に該当項目はなく、顧客コード 2 項目はいずれも他の用途に"
                        + "割り当て済みのため転送しませんでした。")
                .at(IsoPaths.INSTRUCTION_ID, ""));
    }

    /// The XML named one originator and the context named another.
    ///
    /// The context wins — an initiating party identifier is not required to be
    /// the code the receiving bank knows (R-I20). Silent when they agree, because
    /// then nothing was replaced.
    private void reportOriginatorCodeOverride(Pain001Document document) {
        String declared = document.groupHeader().initiatingParty().identifier();
        if (declared.isBlank() || declared.equals(context.originatorCode())) {
            return;
        }
        loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.INFORMATIONAL,
                declared, context.originatorCode(),
                "the document's InitgPty identifier is '" + declared + "' and 委託者コード was "
                        + "written as '" + context.originatorCode() + "' from the mapping context. "
                        + "The context is the authority here (R-I20); this only says the two "
                        + "differ.",
                "電文の InitgPty 識別子は '" + declared + "' ですが、委託者コードには"
                        + "マッピングコンテキストの '" + context.originatorCode()
                        + "' を書き出しました。ここではコンテキストが優先されます (R-I20)。"
                        + "両者が異なることのみを報告しています。")
                .at(IsoPaths.INITIATING_PARTY_ID, "header.originatorCode"));
    }

    /// Fields the ISO side never carried, left at the format's own default.
    ///
    /// Reported once per file rather than once per payment: the fact is a
    /// property of the mapping, and thirty thousand identical lines would bury
    /// the one that matters.
    ///
    /// 振込指定区分 is the one worth naming. It is dropped on the way out —
    /// ISO 20022 does not model how an instruction reaches the beneficiary's
    /// bank — so on the way back there is nothing to restore it from, and the
    /// field takes its numeric default of 0. Several institutions document that
    /// as the required value for an unused field; the bundled code list carries
    /// 7 and 8, so V-205 notes it as outside the list. Both are right, and a
    /// reader deserves to know which they are looking at.
    private void reportFieldsWithNoIsoSource(Pain001Document document) {
        if (document.payments().isEmpty()) {
            return;
        }
        loss.record(LossEntry.of(LossKind.DEFAULTED, LossSeverity.INFORMATIONAL, "", "0",
                "振込指定区分 has no ISO 20022 source, so it was left at 0. Several institutions "
                        + "require that for an unused field; the bundled code list carries 7 and "
                        + "8, so V-205 will note the value as outside the list.",
                "振込指定区分に対応する ISO 20022 の項目がないため 0 のままとしました。"
                        + "未使用項目に 0 を求める金融機関が複数あります。同梱のコードリストは "
                        + "7・8 のみのため、V-205 がリスト外の値として指摘します。")
                .at("", "data.transferCategory"));
    }

    // --------------------------------------------------------------- payments

    private void payment(ZenginFileBuilder builder, FormatDescriptor descriptor,
            CreditTransferTransaction transaction) {
        builder.payment(values -> {
            values.set("beneficiaryBankCode", identifier(transaction.creditorAgent().bankCode(),
                    descriptor, RecordKind.DATA, "beneficiaryBankCode",
                    IsoPaths.CREDITOR_AGENT_MEMBER));
            values.set("beneficiaryBranchCode",
                    identifier(transaction.creditorAgent().branchCode(),
                            descriptor, RecordKind.DATA, "beneficiaryBranchCode",
                            IsoPaths.CREDITOR_AGENT_MEMBER));
            reportUnsplittableMember(transaction.creditorAgent(),
                    IsoPaths.CREDITOR_AGENT_MEMBER, "data.beneficiaryBranchCode");
            values.set("beneficiaryBankName", narrow(transaction.creditorAgent().name(),
                    descriptor, RecordKind.DATA, "beneficiaryBankName",
                    IsoPaths.CREDITOR_AGENT_NAME, LossSeverity.INFORMATIONAL));
            values.set("beneficiaryName", narrow(transaction.creditor().name(),
                    descriptor, RecordKind.DATA, "beneficiaryName",
                    IsoPaths.CREDITOR_NAME, LossSeverity.MATERIAL));
            values.set("accountNumber", identifier(transaction.creditorAccount().number(),
                    descriptor, RecordKind.DATA, "accountNumber",
                    "CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAcct/Id/Othr/Id"));
            values.set("accountType", accountType(
                    transaction.creditorAccount().proprietaryType(),
                    IsoPaths.CREDITOR_ACCOUNT_TYPE, "data.accountType"));
            values.set("amount", amount(transaction, descriptor));
            reference(values, descriptor, transaction);
        });
    }

    /// A member id that is not four digits plus three.
    ///
    /// Within 全銀システム a participant is an office, and an office is 銀行番号
    /// followed by 支店番号 — seven digits. A sender that wrote something else is
    /// not producing a malformed file, but this mapping cannot say where the
    /// bank ends and the branch begins, and guessing would put digits in the
    /// wrong field. Reported `CRITICAL`: a wrong branch code sends the
    /// payment to a different office.
    private void reportUnsplittableMember(Agent agent, String memberPath, String targetField) {
        if (agent.splitsCleanly()) {
            return;
        }
        loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                        agent.memberId(), agent.bankCode(),
                        "the clearing-system member id '" + agent.memberId() + "' is not four "
                                + "digits of 銀行番号 followed by three of 支店番号, so the branch "
                                + "could not be read out of it. 支店番号 was left empty rather "
                                + "than guessed at.",
                        "清算機関のメンバー識別子 '" + agent.memberId()
                                + "' が銀行番号 4 桁 + 支店番号 3 桁の形式ではないため、支店番号を"
                                + "取り出せませんでした。推測せず空欄としました。")
                .at(memberPath, targetField));
    }

    /// The amount, or a diagnosis of why it cannot be one.
    ///
    /// Two things a `pain.001` can say that a Zengin file cannot: a
    /// currency other than JPY, and a fraction of a unit. Neither is rounded —
    /// both are `CRITICAL`, because quietly turning 1000.50 EUR into 1000
    /// JPY is exactly the class of mistake this whole module exists to make
    /// visible.
    private long amount(CreditTransferTransaction transaction, FormatDescriptor descriptor) {
        Money money = transaction.amount();
        long limit = maximumFor(descriptor);

        if (money.isUnreadable()) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL, "", "0",
                            "the instructed amount is absent, is not a number, or is too large to "
                                    + "represent. Written as 0 — which is not what it said, and "
                                    + "is the only thing that can be written.",
                            "指示金額が存在しないか、数値でないか、表現できない大きさです。"
                                    + "0 として書き出しましたが、これは元の値ではなく、"
                                    + "書き出せる唯一の値です。")
                    .at(IsoPaths.INSTRUCTED_AMOUNT, "data.amount"));
            return 0L;
        }

        if (!money.isYen()) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                            money.toString(), money.amount().toBigInteger() + " JPY",
                            "the amount is in " + money.currency() + " and a Zengin file can only "
                                    + "express JPY. The figure was carried across unconverted — "
                                    + "it is now a different amount of money.",
                            "金額の通貨は " + money.currency()
                                    + " ですが、全銀ファイルは JPY しか表現できません。数値は"
                                    + "換算せずに転記されており、金額としては別物になっています。")
                    .at(IsoPaths.INSTRUCTED_AMOUNT, "data.amount"));
        }
        if (money.hasFraction()) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                            money.amount().toPlainString(),
                            money.amount().toBigInteger().toString(),
                            "the amount has a fractional part and 振込金額 is whole yen. The "
                                    + "fraction was discarded, not rounded.",
                            "金額に小数部がありますが、振込金額は円単位の整数です。"
                                    + "四捨五入ではなく切り捨てました。")
                    .at(IsoPaths.INSTRUCTED_AMOUNT, "data.amount"));
        }
        return representable(money, limit);
    }

    /// The largest amount 振込金額 can hold, from its declared width.
    ///
    /// Ten digits in 総合振込. Read from the descriptor rather than written
    /// here, because a format with a wider field would otherwise be silently
    /// held to this one's limit.
    private static long maximumFor(FormatDescriptor descriptor) {
        int digits = descriptor.record(RecordKind.DATA).field("amount").length();
        long limit = 1;
        for (int i = 0; i < digits; i++) {
            limit *= 10;
        }
        return limit - 1;
    }

    /// An amount the field cannot hold, reported rather than thrown.
    ///
    /// A `pain.001` may legitimately carry a figure larger than ten
    /// digits, or a negative one, and neither can go into 振込金額. Letting the
    /// encoder throw would take a whole file down for one payment, and would do
    /// it with an exception that is not part of this module's vocabulary — so
    /// the payment is written as zero and the entry is `CRITICAL`.
    ///
    /// Zero is not a repair. Under the default threshold the conversion
    /// refuses and nobody sees it; under `acceptAnyLoss` the caller asked
    /// for a best-effort file and the report says exactly which payment is
    /// wrong and by how much.
    private long representable(Money money, long limit) {
        java.math.BigInteger whole = money.amount().toBigInteger();

        if (whole.signum() < 0) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                            whole.toString(), "0",
                            "the amount is negative and 振込金額 carries no sign. A credit "
                                    + "transfer of a negative amount is not a debit; it is not "
                                    + "expressible at all. Written as 0.",
                            "金額が負の値ですが、振込金額は符号を持ちません。負の金額の振込は"
                                    + "引落としではなく、そもそも表現できません。0 として"
                                    + "書き出しました。")
                    .at(IsoPaths.INSTRUCTED_AMOUNT, "data.amount"));
            return 0L;
        }

        if (whole.compareTo(java.math.BigInteger.valueOf(limit)) > 0) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                            whole.toString(), "0",
                            "the amount is " + whole + " and 振込金額 holds at most " + limit
                                    + ". Written as 0 rather than truncated: the leading digits "
                                    + "of an amount are the amount, and cutting them produces a "
                                    + "plausible payment for the wrong sum.",
                            "金額は " + whole + " ですが、振込金額の上限は " + limit
                                    + " です。切り詰めず 0 として書き出しました。金額の上位桁を"
                                    + "落とすと、もっともらしい別の金額になってしまうためです。")
                    .at(IsoPaths.INSTRUCTED_AMOUNT, "data.amount"));
            return 0L;
        }
        return whole.longValueExact();
    }

    /// Where the reference lands, and what it costs to put it there.
    private void reference(ZenginFileBuilder.FieldValues values, FormatDescriptor descriptor,
            CreditTransferTransaction transaction) {
        String endToEnd = transaction.endToEndId();
        boolean provided = !endToEnd.isBlank()
                && !CreditTransferTransaction.NOT_PROVIDED.equals(endToEnd);

        String remittance = transaction.remittance().ediAttachment()
                .map(this::ediPayload)
                .orElseGet(() -> String.join(" ", transaction.remittance().freeText()));

        switch (context.endToEndPolicy()) {
            case CUSTOMER_CODE_1 -> {
                if (provided) {
                    values.set("customerCode1", fit(endToEnd, descriptor, "customerCode1",
                            IsoPaths.END_TO_END_ID, LossSeverity.CRITICAL));
                }
                values.set("customerCode2", fit(remittance, descriptor, "customerCode2",
                        IsoPaths.REMITTANCE, LossSeverity.MATERIAL));
            }
            case CUSTOMER_CODE_2 -> {
                values.set("customerCode1", fit(remittance, descriptor, "customerCode1",
                        IsoPaths.REMITTANCE, LossSeverity.MATERIAL));
                if (provided) {
                    values.set("customerCode2", fit(endToEnd, descriptor, "customerCode2",
                            IsoPaths.END_TO_END_ID, LossSeverity.CRITICAL));
                }
            }
            case DROP -> {
                if (provided) {
                    loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                                    endToEnd, "",
                                    "EndToEndIdPolicy.DROP: the reference was not carried into "
                                            + "either 顧客コード. The creditor's reconciliation "
                                            + "key does not reach the file.",
                                    "EndToEndIdPolicy.DROP により参照番号をいずれの顧客コードにも"
                                            + "転送しませんでした。受取人側の照合キーはファイルに"
                                            + "含まれません。")
                            .at(IsoPaths.END_TO_END_ID, "data.customerCode1"));
                }
                values.set("customerCode2", fit(remittance, descriptor, "customerCode2",
                        IsoPaths.REMITTANCE, LossSeverity.MATERIAL));
            }
            default -> throw new IllegalStateException(
                    "unhandled policy " + context.endToEndPolicy());
        }
    }

    /// A base64 金融EDI attachment cannot go into twenty bytes, and pretending
    /// otherwise would put a fragment of an encoding into a field a bank reads.
    private String ediPayload(EdiAttachment attachment) {
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                        attachment.toString(), "",
                        "the remittance information carries a base64 金融EDI attachment of "
                                + attachment.base64().length() + " characters. 顧客コード has "
                                + "twenty bytes at most, so the payload was not carried — a "
                                + "fragment of a base64 encoding is not a shorter version of it.",
                        "送金情報に " + attachment.base64().length()
                                + " 文字の base64 金融EDI情報が含まれています。顧客コードは最大 "
                                + "20 バイトのため転送しませんでした。base64 の断片は短縮版では"
                                + "ないためです。")
                .at(IsoPaths.REMITTANCE, "data.customerCode1"));
        return "";
    }

    // ------------------------------------------------------------------ dates

    /// The year is dropped, and that is not recoverable.
    private MonthDay executionDate(PaymentInstruction instruction) {
        var monthDay = MonthDay.of(instruction.requestedExecutionDate().getMonth(),
                instruction.requestedExecutionDate().getDayOfMonth());
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.INFORMATIONAL,
                        instruction.requestedExecutionDate().toString(), monthDay.toString(),
                        "振込指定日 is MMDD, so the year "
                                + instruction.requestedExecutionDate().getYear()
                                + " was dropped. A reader of the resulting file has to supply one "
                                + "again, and may supply a different one.",
                        "振込指定日は MMDD 形式のため "
                                + instruction.requestedExecutionDate().getYear()
                                + " 年を削除しました。読み取り側は再度年を補う必要があり、"
                                + "異なる年になる可能性があります。")
                .at(IsoPaths.EXECUTION_DATE, "header.valueDate"));
        return monthDay;
    }

    // ----------------------------------------------------------- 預金種目

    /// 預金種目 came from a proprietary code, so it either round-trips exactly or
    /// is not there at all.
    private String accountType(String proprietary, String sourcePath, String targetField) {
        if (proprietary.length() > 1) {
            loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL, proprietary, "1",
                            "預金種目 is one character and " + sourcePath + " carries '"
                                    + proprietary + "'. 普通預金 (1) was assumed. An account type "
                                    + "that is wrong sends the payment to a different account at "
                                    + "the same branch.",
                            "預金種目は 1 文字ですが " + sourcePath + " の値は '" + proprietary
                                    + "' です。普通預金 (1) と仮定しました。預金種目を誤ると"
                                    + "同一支店の別口座に振り込まれます。")
                    .at(sourcePath, targetField));
            return "1";
        }
        if (!proprietary.isBlank()) {
            return proprietary;
        }
        loss.record(LossEntry.of(LossKind.DEFAULTED, LossSeverity.CRITICAL,
                        "", "1",
                        "no 預金種目 in " + sourcePath + ", so 普通預金 (1) was assumed. An "
                                + "account type that is wrong sends the payment to a different "
                                + "account at the same branch.",
                        sourcePath + " に預金種目がないため普通預金 (1) と仮定しました。"
                                + "預金種目を誤ると同一支店の別口座に振り込まれます。")
                .at(sourcePath, targetField));
        return "1";
    }

    // --------------------------------------------------------- transliteration

    /// Full-width to half-width, into the field it has to fit.
    ///
    /// Everything hard about this module is in this method. The engine
    /// decides what a name becomes; what happens here is deciding what to do
    /// when it cannot become anything — which, under the default policies, is to
    /// refuse rather than to write an approximation of somebody's name into a
    /// payment instruction.
    private String narrow(String text, FormatDescriptor descriptor, RecordKind kind,
            String fieldId, String sourcePath, LossSeverity severity) {
        if (text == null || text.isBlank()) {
            return "";
        }
        FieldDescriptor field = descriptor.record(kind).field(fieldId);
        var options = TransliterationOptions.builder()
                .characterClass(field.charClass())
                .charset(context.targetCharset())
                .truncation(context.truncationPolicy())
                .hiragana(context.hiraganaPolicy())
                .unmappable(context.unmappablePolicy())
                .build();

        try {
            Transliteration narrowed =
                    KanaTransliterator.toHalfWidth(text, field.length(), options);
            for (LossEntry entry : narrowed.loss().entries()) {
                loss.record(entry.at(sourcePath, kind.name().toLowerCase(java.util.Locale.ROOT)
                        + "." + fieldId));
            }
            return narrowed.text();
        } catch (UntransliterableCharacterException | FieldTooSmallException
                | ValueTooLongException refused) {
            loss.record(LossEntry.of(LossKind.DROPPED, severity, text, "",
                            "'" + text + "' cannot be written into " + fieldId + ": "
                                    + refused.messageEn()
                                    + " The field was left empty rather than filled with a guess.",
                            "'" + text + "' を " + fieldId + " に書き込めません: "
                                    + refused.messageJa()
                                    + " 推測で埋めず、空欄のままにしました。")
                    .at(sourcePath, kind.name().toLowerCase(java.util.Locale.ROOT)
                            + "." + fieldId));
            return "";
        }
    }

    /// An identifier, or nothing, but never a shortened version of one.
    ///
    /// ISO 20022 gives an account number thirty-four characters and a
    /// clearing-system member id thirty-five; the Zengin fields are seven and
    /// four and three. A sender can legitimately fill them, and until this
    /// existed the encoder threw an untyped `IllegalArgumentException`
    /// from inside the builder — a whole file lost to one payment, in an
    /// exception outside this module's vocabulary.
    ///
    /// **Not truncated.** Half an account number is a different
    /// account, and a file carrying one looks perfectly valid.
    ///
    /// The value is not written, which for a numeric field means the field
    /// takes its padding — zeros, not spaces. That is not a safe outcome either:
    /// `0000000` is a well-formed account number and no validation rule
    /// will object to it. It is survivable only because the entry is
    /// `CRITICAL` and the default threshold therefore stops the conversion
    /// before anybody sees the file. A caller who passes `acceptAnyLoss`
    /// gets a file with a zeroed identifier and a report saying which payment.
    private String identifier(String value, FormatDescriptor descriptor, RecordKind kind,
            String fieldId, String sourcePath) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int limit = descriptor.record(kind).field(fieldId).length();
        byte[] bytes = context.targetCharset().encode(value);
        if (bytes.length <= limit) {
            return value;
        }

        String target = kind.name().toLowerCase(java.util.Locale.ROOT) + "." + fieldId;
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.CRITICAL, value, "",
                        "'" + value + "' is " + bytes.length + " bytes and " + fieldId
                                + " holds " + limit + ". An identifier is not shortened: half an "
                                + "account number is a different account, and a file carrying one "
                                + "looks valid. It was not written at all, so the field holds its "
                                + "padding — which for a numeric field is zeros, and is no safer. "
                                + "This payment cannot be sent.",
                        "'" + value + "' は " + bytes.length + " バイトですが " + fieldId
                                + " は " + limit + " バイトです。識別子は切り詰めません。"
                                + "口座番号の一部は別の口座であり、それを含むファイルは一見"
                                + "正当に見えるためです。値を書き込まなかったため、当該項目は"
                                + "パディング(数値項目ではゼロ)のままです。これも安全ではなく、"
                                + "この明細は送信できません。")
                .at(sourcePath, target));
        return "";
    }

    /// A value that is already writable, cut to the field it goes in.
    ///
    /// References and remittance codes are not names — they are ASCII, and
    /// transliterating them would be wrong. They still have to fit.
    private String fit(String value, FormatDescriptor descriptor, String fieldId,
            String sourcePath, LossSeverity severity) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int limit = descriptor.record(RecordKind.DATA).field(fieldId).length();
        byte[] bytes = context.targetCharset().encode(value);
        if (bytes.length <= limit) {
            return value;
        }

        var kept = new String(bytes, 0, limit, context.targetCharset().charset()).trim();
        loss.record(LossEntry.of(LossKind.TRUNCATED, severity, value, kept,
                        "'" + value + "' is " + bytes.length + " bytes and " + fieldId
                                + " holds " + limit + ". It was cut to '" + kept
                                + "', which no longer matches the value it came from.",
                        "'" + value + "' は " + bytes.length + " バイトですが " + fieldId
                                + " は " + limit + " バイトです。'" + kept
                                + "' に切り詰めた結果、元の値とは一致しなくなりました。")
                .at(sourcePath, "data." + fieldId));
        return kept;
    }

    private EncodingOptions encodingOptions() {
        return EncodingOptions.builder()
                .truncation(context.truncationPolicy())
                .hiragana(context.hiraganaPolicy())
                .unmappable(context.unmappablePolicy())
                .build();
    }
}
