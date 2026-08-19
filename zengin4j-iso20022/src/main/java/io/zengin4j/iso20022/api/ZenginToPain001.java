package io.zengin4j.iso20022.api;

import module java.base;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.KanaTransliterator;
import io.zengin4j.core.kana.Transliteration;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.HeaderRecord;
import io.zengin4j.core.model.TrailerRecord;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.time.MonthDayResolver;
import io.zengin4j.iso20022.envelope.BusinessApplicationHeader;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.iso20022.mapping.MappingRow;
import io.zengin4j.iso20022.pain001.Account;
import io.zengin4j.iso20022.pain001.Agent;
import io.zengin4j.iso20022.pain001.CreditTransferTransaction;
import io.zengin4j.iso20022.pain001.GroupHeader;
import io.zengin4j.iso20022.pain001.Money;
import io.zengin4j.iso20022.pain001.Pain001Document;
import io.zengin4j.iso20022.pain001.PaymentInstruction;
import io.zengin4j.iso20022.pain001.Party;
import io.zengin4j.iso20022.pain001.RemittanceInformation;

/// The upward leg: a Zengin file becomes a `pain.001` document.
///
/// This direction loses less than the other one, and what it loses it loses
/// for a structural reason rather than a capacity one: the fixed-length record
/// has fields ISO 20022 has no element for — 手形交換所番号, 新規コード,
/// 振込指定区分 — and they are dropped rather than smuggled into a proprietary
/// element where nothing would read them.
///
/// The one thing it *adds* is a year. 振込指定日 is `MMDD`, so
/// `ReqdExctnDt` needs a year that is not in the file, and inventing one
/// is a `DEFAULTED` loss even when the answer is obviously right.
final class ZenginToPain001 {

    private final MappingContext context;
    private final ZenginFields fields;
    private final LossCollector loss = new LossCollector();

    /// Counted rather than reported one at a time; see [#reportDroppedReferences()].
    private int droppedReferences;

    ZenginToPain001(MappingContext context, ZenginFields fields) {
        this.context = context;
        this.fields = fields;
    }

    MappingResult<ZediFile> convert(ZenginFile file) {
        List<PaymentInstruction> instructions = new ArrayList<>(file.batches().size());
        for (int i = 0; i < file.batches().size(); i++) {
            instructions.add(instruction(file.batches().get(i), i + 1));
        }

        Pain001Document document = new Pain001Document(groupHeader(file), instructions);
        crossCheckTrailers(file, document);
        reportUnreadableRecords(file);
        reportDroppedReferences();
        reportDisagreeingOriginators(file);
        reportDroppedFields(file);

        BusinessApplicationHeader header = new BusinessApplicationHeader(
                context.originatorCode(), receiver(file),
                context.messageId(), Pain001Document.MESSAGE_ID, context.creationDateTime());

        return new MappingResult<>(
                ZediFile.of(ZediMessage.of(header, document.toXml())),
                io.zengin4j.iso20022.loss.MappingLossReport.of(loss.build()));
    }

    /// Who the message is addressed to.
    ///
    /// `To` is mandatory in `head.001` and no Zengin field says
    /// it outright. It is not unknown, though: a 総合振込 file goes to the
    /// originator's own bank, whose code is 仕向銀行番号 in the header record. So
    /// the default is derived from the file rather than invented, and a file
    /// without one produces a header that says so by omission.
    private String receiver(ZenginFile file) {
        Optional<String> declared = context.receiver();
        if (declared.isPresent()) {
            return declared.get();
        }
        String originBank = file.batches().isEmpty() ? ""
                : fields.text(file.batches().getFirst().header(), RecordKind.HEADER,
                        "originBankCode");
        if (originBank.isBlank()) {
            loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL, "", "",
                    "the business application header names no recipient. head.001 requires To, "
                            + "and neither MappingContext.receiver nor the file's 仕向銀行番号 "
                            + "supplied one, so the element was left out rather than filled with "
                            + "a placeholder.",
                    "ビジネスアプリケーションヘッダーに宛先がありません。head.001 では To は必須"
                            + "ですが、MappingContext.receiver も 仕向銀行番号 も値を与えなかった"
                            + "ため、仮の値を入れず要素を省略しました。"));
        }
        return originBank;
    }

    /// Records the reader could not parse, which are not in the document.
    ///
    /// Lenient mode surfaces a record that does not fit the format as a
    /// [io.zengin4j.core.model.MalformedRecord] rather than failing the
    /// read (R-D8), so one bad record does not hide the other 9,999. The
    /// conversion has nothing to map them to — a record whose fields could not
    /// be located has no beneficiary and no amount — so they do not appear in
    /// the message at all.
    ///
    /// **Reported `CRITICAL`.** A malformed record may
    /// well be a payment, and a payment that silently fails to appear in the
    /// converted message is money that does not move with nothing to show for
    /// it. Under the default threshold this stops the conversion, which is the
    /// right outcome: a file that could not be read whole should not be
    /// converted in part.
    private void reportUnreadableRecords(ZenginFile file) {
        int inBatches = file.batches().stream().mapToInt(batch -> batch.malformed().size()).sum();
        int outside = file.unbatched().size();
        int total = inBatches + outside;
        if (total == 0) {
            return;
        }
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.CRITICAL,
                total + " unreadable record" + (total == 1 ? "" : "s"), "",
                total + " record" + (total == 1 ? " was" : "s were") + " read as malformed and "
                        + "could not be mapped: " + inBatches + " inside a batch, " + outside
                        + " outside one. Any of them may be a payment. The message does not "
                        + "contain them, and nothing downstream will know they existed — read the "
                        + "file in strict mode, or run zengin validate over it, before converting.",
                total + " 件のレコードが不正として読み取られ、マッピングできませんでした"
                        + "(バッチ内 " + inBatches + " 件、バッチ外 " + outside + " 件)。"
                        + "いずれも明細である可能性があります。電文には含まれず、後続の処理は"
                        + "その存在を知りません。変換前に厳格モードで読み取るか、"
                        + "zengin validate で確認してください。"));
    }

    // ------------------------------------------------------------ group header

    private GroupHeader groupHeader(ZenginFile file) {
        String name = file.batches().isEmpty() ? ""
                : widen(fields.text(file.batches().getFirst().header(), RecordKind.HEADER,
                        "originatorName"),
                        "header.originatorName", IsoPaths.INITIATING_PARTY_NAME);
        return new GroupHeader(context.messageId(), context.creationDateTime(),
                new Party(name, context.originatorCode()));
    }

    // ------------------------------------------------------ payment instruction

    private PaymentInstruction instruction(Batch batch, int position) {
        HeaderRecord header = batch.header();

        List<CreditTransferTransaction> transactions = new ArrayList<>(batch.data().size());
        for (DataRecord record : batch.data()) {
            transactions.add(transaction(record));
        }

        return new PaymentInstruction(
                context.messageId() + "-" + position,
                executionDate(header),
                Party.named(widen(fields.text(header, RecordKind.HEADER, "originatorName"),
                        "header.originatorName", IsoPaths.DEBTOR_NAME)),
                new Account(fields.text(header, RecordKind.HEADER, "accountNumber"),
                        fields.text(header, RecordKind.HEADER, "accountType")),
                new Agent(fields.text(header, RecordKind.HEADER, "originBankCode"),
                        fields.text(header, RecordKind.HEADER, "originBranchCode"),
                        widen(fields.text(header, RecordKind.HEADER, "originBankName"),
                                "header.originBankName", IsoPaths.DEBTOR_AGENT_NAME)),
                transactions);
    }

    /// 振込指定日 has no year, so one is supplied and the fact is recorded.
    ///
    /// Reported every time rather than only when the answer looks doubtful.
    /// The resolution is a guess whichever year it lands on — a correct guess is
    /// still a guess, and a report that only mentions the surprising cases
    /// teaches a reader that silence means certainty.
    private LocalDate executionDate(HeaderRecord header) {
        Optional<MonthDay> declared = header.effectiveDate();
        if (declared.isEmpty()) {
            loss.record(LossEntry.of(LossKind.DEFAULTED, LossSeverity.CRITICAL,
                            "", context.referenceDate().toString(),
                            "振込指定日 is absent or unreadable, so the execution date was set to "
                                    + "the reference date. A payment file with no execution date "
                                    + "is one the bank decides the timing of.",
                            "振込指定日が存在しないか読み取れないため、実行日を基準日に設定しました。"
                                    + "実行日のない依頼は銀行側で時期が決まります。")
                    .at("header.valueDate", IsoPaths.EXECUTION_DATE));
            return context.referenceDate();
        }

        MonthDayResolver resolver = MonthDayResolver.forwardLooking(context.referenceDate());
        return resolver.resolve(declared.get()).date()
                .map(resolved -> {
                    loss.record(LossEntry.of(LossKind.DEFAULTED, LossSeverity.MATERIAL,
                                    declared.get().toString(), resolved.toString(),
                                    "振込指定日 carries no year; " + resolved.getYear()
                                            + " was supplied from the reference date.",
                                    "振込指定日は年を持たないため、基準日から "
                                            + resolved.getYear() + " 年を補いました。")
                            .at("header.valueDate", IsoPaths.EXECUTION_DATE));
                    return resolved;
                })
                .orElseGet(() -> {
                    loss.record(LossEntry.of(LossKind.DEFAULTED, LossSeverity.CRITICAL,
                                    declared.get().toString(), context.referenceDate().toString(),
                                    "振込指定日 " + declared.get() + " could not be resolved to a "
                                            + "real date — 0229 in a non-leap year is the usual "
                                            + "cause — so the reference date was used instead.",
                                    "振込指定日 " + declared.get()
                                            + " を実在する日付に解決できませんでした"
                                            + "(平年の 0229 が典型例)。基準日で代用しました。")
                            .at("header.valueDate", IsoPaths.EXECUTION_DATE));
                    return context.referenceDate();
                });
    }

    // ------------------------------------------------------------- transaction

    private CreditTransferTransaction transaction(DataRecord record) {
        String customerCode1 = fields.text(record, RecordKind.DATA, "customerCode1");
        String customerCode2 = fields.text(record, RecordKind.DATA, "customerCode2");

        return new CreditTransferTransaction(
                endToEndId(customerCode1, customerCode2),
                "",
                Money.yen(record.amount()),
                new Agent(fields.text(record, RecordKind.DATA, "beneficiaryBankCode"),
                        fields.text(record, RecordKind.DATA, "beneficiaryBranchCode"),
                        widen(fields.text(record, RecordKind.DATA, "beneficiaryBankName"),
                                "data.beneficiaryBankName",
                                IsoPaths.CREDITOR_AGENT_NAME)),
                Party.named(widen(fields.text(record, RecordKind.DATA, "beneficiaryName"),
                        "data.beneficiaryName", IsoPaths.CREDITOR_NAME)),
                new Account(fields.text(record, RecordKind.DATA, "accountNumber"),
                        fields.text(record, RecordKind.DATA, "accountType")),
                remittance(record, customerCode1, customerCode2));
    }

    /// Where the reference comes from, and what happens when it is not carried.
    ///
    /// The upward leg never truncates an `EndToEndId` — ten bytes go
    /// into thirty-five with room to spare. The loss is on the way back, and the
    /// policy is declared here so both legs agree about which field it lives in.
    private String endToEndId(String customerCode1, String customerCode2) {
        droppedReferences += context.endToEndPolicy() == EndToEndIdPolicy.DROP ? 1 : 0;
        return switch (context.endToEndPolicy()) {
            case CUSTOMER_CODE_1 -> customerCode1;
            case CUSTOMER_CODE_2 -> customerCode2;
            case DROP -> "";
        };
    }

    /// `EndToEndIdPolicy.DROP`, reported once for the file.
    ///
    /// Once per payment would be the same sentence thirty thousand times, and
    /// the fact is a property of the policy rather than of any one payment. The
    /// count is what a reader actually needs.
    private void reportDroppedReferences() {
        if (droppedReferences == 0) {
            return;
        }
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                        droppedReferences + " 顧客コード",
                        CreditTransferTransaction.NOT_PROVIDED,
                        "EndToEndIdPolicy.DROP: no 顧客コード was carried into EndToEndId for any "
                                + "of " + droppedReferences + " payments, which are written as "
                                + "NOTPROVIDED. The creditor has nothing to reconcile them "
                                + "against.",
                        "EndToEndIdPolicy.DROP により、" + droppedReferences
                                + " 件すべてで顧客コードを EndToEndId に転送せず、NOTPROVIDED を"
                                + "書き出しました。受取人側に照合の手がかりがありません。")
                .at("data.customerCode1", IsoPaths.END_TO_END_ID));
    }

    /// One message carries one initiating party, and a file may not.
    ///
    /// Each Zengin batch has its own 委託者名; `GrpHdr/InitgPty` has room
    /// for one. The first batch's wins, which is right when they agree and is
    /// worth saying out loud when they do not — the message would name one
    /// originator and instruct payments from another.
    private void reportDisagreeingOriginators(ZenginFile file) {
        List<String> names = file.batches().stream()
                .map(batch -> fields.text(batch.header(), RecordKind.HEADER, "originatorName"))
                .distinct()
                .toList();
        if (names.size() < 2) {
            return;
        }
        loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                        String.join(", ", names), names.getFirst(),
                        "the file's batches name " + names.size() + " different originators and "
                                + "GrpHdr/InitgPty holds one. The first was used; each batch keeps "
                                + "its own name in PmtInf/Dbtr, so nothing is lost from the "
                                + "payments themselves — but the message as a whole now names one "
                                + "originator.",
                        "ファイル内のバッチが " + names.size()
                                + " 種類の委託者名を持ちますが、GrpHdr/InitgPty は 1 つのみです。"
                                + "先頭を採用しました。各バッチの名称は PmtInf/Dbtr に保持される"
                                + "ため明細の情報は失われませんが、電文全体としては 1 者の委託者を"
                                + "名乗ることになります。")
                .at("header.originatorName", IsoPaths.INITIATING_PARTY_NAME));
    }

    /// The 顧客コード that is not the reference becomes remittance text.
    ///
    /// Which one that is depends on [EndToEndIdPolicy]: under
    /// `CUSTOMER_CODE_1` the reference is 顧客コード1 and 顧客コード2 lands
    /// here, and under `CUSTOMER_CODE_2` it is the other way round. Both
    /// are carried either way — sending a fixed one regardless would drop the
    /// other with nothing to say so.
    ///
    /// Unless 識別表示 says the two are one 金融EDI情報 field, in which case
    /// they are not customer codes at all.
    private RemittanceInformation remittance(DataRecord record, String customerCode1,
            String customerCode2) {
        if (carriesEdiOverlay(record)) {
            String edi = (customerCode1 + customerCode2).trim();
            return RemittanceInformation.of(edi);
        }
        // Whichever 顧客コード is not carrying the reference goes here. Sending
        // the same one regardless would drop the other silently, which is what
        // this did under CUSTOMER_CODE_2 until a coverage gap pointed at it.
        return RemittanceInformation.of(
                context.endToEndPolicy() == EndToEndIdPolicy.CUSTOMER_CODE_2
                        ? customerCode1
                        : customerCode2);
    }

    /// 識別表示 = Y means data fields 12 and 13 are one C(20) 金融EDI情報 field
    /// rather than two customer codes (OQ-8).
    ///
    /// Read as a derived property of the record rather than modelled in the
    /// descriptor, which is the smaller of the two changes OQ-8 weighs and the
    /// one that does not require the descriptor schema to express a condition.
    private boolean carriesEdiOverlay(DataRecord record) {
        return fields.descriptor().record(RecordKind.DATA).find("identification").isPresent()
                && "Y".equalsIgnoreCase(fields.text(record, RecordKind.DATA, "identification"));
    }

    // ------------------------------------------------------------ cross-checks

    /// The trailer said one thing and the payments say another.
    ///
    /// The computed value is written, because it is the one the payments
    /// actually add up to. The disagreement is `CRITICAL`: a file whose
    /// own trailer does not match it cannot be trusted for either number, and
    /// this is precisely what V-301 and V-302 catch on the fixed-length side.
    private void crossCheckTrailers(ZenginFile file, Pain001Document document) {
        for (Batch batch : file.batches()) {
            Optional<TrailerRecord> trailer = batch.trailer();
            if (trailer.isEmpty()) {
                continue;
            }
            long declaredCount = trailer.get().recordCount();
            long declaredTotal = trailer.get().totalAmount();

            if (declaredCount != batch.computedCount()) {
                loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                                String.valueOf(declaredCount),
                                String.valueOf(batch.computedCount()),
                                "the trailer declares " + declaredCount + " payments and the "
                                        + "batch contains " + batch.computedCount()
                                        + "; NbOfTxs was written from the payments.",
                                "トレーラーの件数は " + declaredCount + " ですが明細は "
                                        + batch.computedCount() + " 件です。NbOfTxs には明細から"
                                        + "計算した値を書き出しました。")
                        .at("trailer.recordCount", IsoPaths.NUMBER_OF_TRANSACTIONS));
            }
            if (declaredTotal != batch.computedTotal()) {
                loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.CRITICAL,
                                String.valueOf(declaredTotal),
                                String.valueOf(batch.computedTotal()),
                                "the trailer declares a total of " + declaredTotal
                                        + " and the payments add up to " + batch.computedTotal()
                                        + "; CtrlSum was written from the payments.",
                                "トレーラーの合計金額は " + declaredTotal + " ですが明細の合計は "
                                        + batch.computedTotal() + " です。CtrlSum には明細から"
                                        + "計算した値を書き出しました。")
                        .at("trailer.totalAmount", IsoPaths.CONTROL_SUM));
            }
        }
        assertDocumentAddsUp(document);
    }

    /// A cheap guard against the document disagreeing with itself.
    private void assertDocumentAddsUp(Pain001Document document) {
        BigDecimal fromInstructions = document.payments().stream()
                .map(PaymentInstruction::controlSum)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (document.controlSum().compareTo(fromInstructions) != 0) {
            throw new IllegalStateException("the document's control sum disagrees with its own "
                    + "instructions: " + document.controlSum() + " vs " + fromInstructions);
        }
    }

    // --------------------------------------------------------- dropped fields

    /// Fields the declaration says go nowhere.
    ///
    /// Reported once per file rather than once per record. Thirty thousand
    /// identical "新規コード was dropped" entries would bury the one that matters,
    /// and the fact being reported is a property of the mapping, not of any
    /// particular payment.
    private void reportDroppedFields(ZenginFile file) {
        boolean hasData = !file.allData().isEmpty();
        for (MappingRow row : fields.droppedRows()) {
            boolean applies = row.zenginRecord()
                    .map(record -> !record.equals("data") || hasData)
                    .orElse(true);
            if (!applies) {
                continue;
            }
            loss.record(LossEntry.of(
                            row.lossKind().orElse(LossKind.DROPPED),
                            row.lossSeverity().orElse(LossSeverity.INFORMATIONAL),
                            row.zenginField(), "", row.whyEn(), row.whyJa())
                    .at(row.zenginField(), ""));
        }
    }

    // ------------------------------------------------------- transliteration

    /// Half-width to full-width, recording what it changed.
    ///
    /// Widening is the safe direction — every half-width kana has exactly one
    /// full-width form — so the loss is informational. It is recorded anyway,
    /// because the resulting name is not the string that was in the file, and a
    /// report that only mentions damage cannot be used to explain a difference.
    private String widen(String text, String source, String target) {
        if (text.isEmpty()) {
            return text;
        }
        Transliteration widened = KanaTransliterator.toFullWidth(text);
        for (LossEntry entry : widened.loss().entries()) {
            loss.record(entry.at(source, target));
        }
        if (widened.loss().isLossless() && !widened.text().equals(text)) {
            loss.record(LossEntry.of(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL,
                            text, widened.text(),
                            "half-width katakana was widened for display; every half-width kana "
                                    + "has exactly one full-width form, so this is reversible.",
                            "表示用に半角カナを全角化しました。半角カナには全角形が 1 つずつ"
                                    + "対応するため可逆です。")
                    .at(source, target));
        }
        return widened.text();
    }
}
