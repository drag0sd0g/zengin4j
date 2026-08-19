package io.zengin4j.iso20022.pain001;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.iso20022.xml.XmlParser;
import io.zengin4j.iso20022.xml.XmlSerializer;
import org.junit.jupiter.api.Test;

/// The `pain.001` subset: what it writes, and that it reads back what it
/// wrote.
///
/// A hand-written message model earns its keep only if something checks it.
/// Two things do — this, which sends every element through a real parser and
/// back, and the opt-in XSD task for anyone who has the schemas.
class Pain001ModelTest {

    private static Pain001Document document() {
        return new Pain001Document(
                new GroupHeader("MSG-1", OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                        new Party("テストシヨウジ", "9900000001")),
                List.of(new PaymentInstruction(
                        "MSG-1-1",
                        LocalDate.of(2026, 9, 30),
                        Party.named("テストシヨウジ"),
                        new Account("9000001", "1"),
                        new Agent("9999", "998", "テストギンコウ"),
                        List.of(new CreditTransferTransaction(
                                "INV2026000", "",
                                Money.yen(150_000),
                                new Agent("9999", "999", "テストギンコウ"),
                                Party.named("ヤマダ　タロウ"),
                                new Account("9876543", "1"),
                                RemittanceInformation.of("REF-1"))))));
    }

    @Test
    void aDocumentSurvivesBeingWrittenAndReadBack() {
        Pain001Document original = document();

        Pain001Document parsed = Pain001Document.from(
                XmlParser.parse(XmlSerializer.toBytes(original.toXml())));

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void theDocumentDeclaresThePinnedNamespace() {
        assertThat(document().toXml().namespace())
                .isEqualTo("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
    }

    /// The counts are computed, never carried.
    ///
    /// Same reasoning as the Zengin trailer: a header that can disagree with
    /// its own contents is a header somebody has to reconcile.
    @Test
    void countsAndSumsComeFromThePaymentsRatherThanFromTheHeader() {
        Pain001Document document = document();

        assertThat(document.numberOfTransactions()).isEqualTo(1);
        assertThat(document.controlSum()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(document.toXml().textAt("CstmrCdtTrfInitn/GrpHdr/NbOfTxs")).contains("1");
        assertThat(document.toXml().textAt("CstmrCdtTrfInitn/GrpHdr/CtrlSum")).contains("150000");
    }

    @Test
    void everyTransactionIsReachableAcrossInstructions() {
        Pain001Document two = new Pain001Document(document().groupHeader(),
                List.of(document().payments().get(0), document().payments().get(0)));

        assertThat(two.transactions()).hasSize(2);
        assertThat(two.numberOfTransactions()).isEqualTo(2);
        assertThat(two.controlSum()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
    }

    @Test
    void elementsAppearInTheOrderTheMessageDefinitionSequencesThem() {
        String written = XmlSerializer.toText(document().toXml());

        assertThat(written.indexOf("<MsgId>")).isLessThan(written.indexOf("<CreDtTm>"));
        assertThat(written.indexOf("<CreDtTm>")).isLessThan(written.indexOf("<NbOfTxs>"));
        assertThat(written.indexOf("<NbOfTxs>")).isLessThan(written.indexOf("<CtrlSum>"));
        assertThat(written.indexOf("<CtrlSum>")).isLessThan(written.indexOf("<InitgPty>"));
        assertThat(written.indexOf("<PmtInfId>")).isLessThan(written.indexOf("<PmtMtd>"));
        assertThat(written.indexOf("<PmtMtd>")).isLessThan(written.indexOf("<ReqdExctnDt>"));
        assertThat(written.indexOf("<ReqdExctnDt>")).isLessThan(written.indexOf("<Dbtr>"));
        assertThat(written.indexOf("<Dbtr>")).isLessThan(written.indexOf("<DbtrAcct>"));
        assertThat(written.indexOf("<DbtrAcct>")).isLessThan(written.indexOf("<DbtrAgt>"));
        assertThat(written.indexOf("<PmtId>")).isLessThan(written.indexOf("<Amt>"));
        assertThat(written.indexOf("<Amt>")).isLessThan(written.indexOf("<CdtrAgt>"));
        assertThat(written.indexOf("<Cdtr>")).isLessThan(written.indexOf("<CdtrAcct>"));
    }

    @Test
    void aDocumentThatIsNotACreditTransferInitiationIsRefusedByName() {
        XmlElement other = XmlElement.element("Document")
                .namespace("urn:iso:std:iso:20022:tech:xsd:pain.002.001.03")
                .child(XmlElement.element("CstmrPmtStsRpt"))
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Pain001Document.from(other))
                .withMessageContaining("CstmrCdtTrfInitn");
    }

    @Test
    void aDocumentWithNoGroupHeaderIsRefused() {
        XmlElement headerless = XmlElement.element("Document")
                .child(XmlElement.element("CstmrCdtTrfInitn"))
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Pain001Document.from(headerless))
                .withMessageContaining("GrpHdr");
    }

    // --------------------------------------------------------------- elements

    @Test
    void anAbsentPartyWritesNothingRatherThanAnEmptyElement() {
        assertThat(Party.named("").toXml("Cdtr")).isEmpty();
        assertThat(new Account("", "").toXml("CdtrAcct")).isEmpty();
        assertThat(new Agent("", "", "").toXml("CdtrAgt")).isEmpty();
        assertThat(RemittanceInformation.NONE.toXml()).isEmpty();
    }

    /// Within 全銀システム a participant is an office, so the member id is 銀行番号
    /// followed by 支店番号 — seven digits, not four.
    @Test
    void anAgentNamesTheClearingSystemItsMemberIdBelongsTo() {
        XmlElement agent = new Agent("9999", "998", "").toXml("DbtrAgt").orElseThrow();

        assertThat(agent.textAt("FinInstnId/ClrSysMmbId/ClrSysId/Cd")).contains("JPZGN");
        assertThat(agent.textAt("FinInstnId/ClrSysMmbId/MmbId")).contains("9999998");
        assertThat(agent.at("BrnchId"))
                .as("the branch is part of the member id, not a second identifier")
                .isEmpty();
    }

    @Test
    void aSevenDigitMemberIdSplitsBackIntoABankAndABranch() {
        Agent read = Agent.from(new Agent("9999", "998", "ﾃｽﾄ").toXml("DbtrAgt").orElseThrow());

        assertThat(read.bankCode()).isEqualTo("9999");
        assertThat(read.branchCode()).isEqualTo("998");
        assertThat(read.memberId()).isEqualTo("9999998");
        assertThat(read.splitsCleanly()).isTrue();
    }

    /// A member id of another shape is kept whole rather than cut somewhere
    /// arbitrary. The mapper reports it; the model only declines to guess.
    @Test
    void aMemberIdOfAnotherShapeIsKeptWholeRatherThanCutArbitrarily() {
        Agent read = Agent.from(XmlElement.element("CdtrAgt")
                .child(XmlElement.element("FinInstnId")
                        .child(XmlElement.element("ClrSysMmbId")
                                .textChild("MmbId", "SOMEBANKXXX")))
                .build());

        assertThat(read.bankCode()).isEqualTo("SOMEBANKXXX");
        assertThat(read.branchCode()).isEmpty();
        assertThat(read.splitsCleanly()).isFalse();
    }

    /// An absent agent is not a malformed one.
    ///
    /// Reporting "this is not four digits plus three" for an element that was
    /// never there sends a reader looking for a defect in a value that does not
    /// exist.
    @Test
    void anAbsentAgentSplitsCleanlyByVacuity() {
        assertThat(new Agent("", "", "").splitsCleanly()).isTrue();
        assertThat(Agent.from(XmlElement.element("CdtrAgt").build()).splitsCleanly()).isTrue();
        assertThat(new Agent("9999", "998", "").splitsCleanly()).isTrue();
    }

    @Test
    void anAmountCarriesItsCurrencyAsAnAttribute() {
        XmlElement amount = Money.yen(150_000).toXml();

        assertThat(amount.at("InstdAmt").orElseThrow().attribute("Ccy")).contains("JPY");
        assertThat(amount.textAt("InstdAmt")).contains("150000");
    }

    @Test
    void anAmountIsReadBackWithItsCurrency() {
        Money read = Money.from(Money.yen(150_000).toXml()).orElseThrow();

        assertThat(read.isYen()).isTrue();
        assertThat(read.toYen()).isEqualTo(150_000);
        assertThat(read.hasFraction()).isFalse();
    }

    @Test
    void anAmountThatIsNotANumberIsNotAnAmount() {
        XmlElement broken = XmlElement.element("Amt")
                .child(XmlElement.text("InstdAmt", "not a number"))
                .build();

        assertThat(Money.from(broken)).isEmpty();
    }

    /// Thirteen bytes of input, one `OutOfMemoryError`.
    ///
    /// `xs:decimal` admits `1e2000000000`. It parses in
    /// microseconds — `BigDecimal` keeps an unscaled value and a scale —
    /// and exhausts the heap the instant anything renders it in plain notation.
    /// That is a denial of service on a file somebody else wrote, in a module
    /// whose documentation says the input is assumed hostile.
    @Test
    void anAmountTooLargeToRenderIsRefusedBeforeAnythingRendersIt() {
        XmlElement enormous = XmlElement.element("Amt")
                .child(XmlElement.element("InstdAmt")
                        .attribute("Ccy", "JPY")
                        .text("1e2000000000"))
                .build();

        assertThat(Money.from(enormous))
                .as("holding it is cheap; rendering it is not, so it is refused at the boundary")
                .isEmpty();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("1e2000000000"), "JPY"))
                .withMessageContaining("integer digits")
                .satisfies(refused -> assertThat(refused.getMessage())
                        .as("the diagnostic must not render the value it is refusing")
                        .hasSizeLessThan(300));
    }

    @Test
    void anAmountAtTheBoundaryIsStillAnAmount() {
        BigDecimal thirtyDigits = new BigDecimal("1".repeat(Money.MAX_INTEGER_DIGITS));

        assertThat(new Money(thirtyDigits, "JPY").amount()).isEqualByComparingTo(thirtyDigits);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Money(new BigDecimal("1".repeat(Money.MAX_INTEGER_DIGITS + 1)), "JPY"));
    }

    /// An unreadable amount is not an amount of zero.
    ///
    /// ISO 4217 defines `XXX` as "no currency involved", which is
    /// exactly what a figure nobody could read is. Using it means the case
    /// travels the path a foreign currency already travels, and is reported
    /// rather than silently becoming a payment for nothing.
    @Test
    void anUnreadableAmountIsMarkedRatherThanZeroed() {
        XmlElement transaction = XmlElement.element(CreditTransferTransaction.ELEMENT)
                .child(XmlElement.element("PmtId").textChild("EndToEndId", "INV-1"))
                .child(XmlElement.element("Amt")
                        .child(XmlElement.text("InstdAmt", "not a number")))
                .build();

        Money read = CreditTransferTransaction.from(transaction).amount();

        assertThat(read.isUnreadable()).isTrue();
        assertThat(read.currency()).isEqualTo("XXX");
        assertThat(Money.yen(0).isUnreadable())
                .as("a genuine zero is not the same thing")
                .isFalse();
    }

    @Test
    void aCurrencyCodeIsThreeLetters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.ONE, "YEN "))
                .withMessageContaining("ISO 4217");
    }

    @Test
    void aFractionalAmountIsRecognisedAsSuch() {
        Money fractional = new Money(new BigDecimal("1000.50"), "JPY");

        assertThat(fractional.hasFraction()).isTrue();
        assertThat(fractional.toYen()).isEqualTo(1000);
        assertThat(new Money(new BigDecimal("1000.00"), "JPY").hasFraction()).isFalse();
    }

    @Test
    void anAbsentReferenceIsWrittenAsTheValueTheStandardDefinesForIt() {
        CreditTransferTransaction noReference = new CreditTransferTransaction(
                "", "", Money.yen(1), new Agent("9999", "999", ""),
                Party.named("ﾔﾏﾀﾞ"), new Account("9876543", "1"), RemittanceInformation.NONE);

        assertThat(noReference.toXml().textAt("PmtId/EndToEndId"))
                .contains(CreditTransferTransaction.NOT_PROVIDED);
    }

    // ------------------------------------------------------------ attachment

    /// R-I12 — the exact encoding survives.
    ///
    /// Two base64 encodings of the same bytes can differ in where the lines
    /// split and in padding, so re-encoding a decoded payload produces different
    /// XML for identical content. The lines are therefore stored verbatim.
    @Test
    void anAttachmentIsWrittenBackExactlyAsItArrived() {
        List<String> arrived = List.of(
                "MIME-Version: 1.0",
                "Content-Type: text/xml",
                EdiAttachment.TRANSFER_ENCODING,
                "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48VHJhbkluZj48L1RyYW5J",
                "bmY+");

        EdiAttachment attachment = EdiAttachment.parse(arrived).orElseThrow();

        assertThat(attachment.toUnstructured()).isEqualTo(arrived);
        assertThat(attachment.base64Lines())
                .as("the split is where it arrived, not where this library would put it")
                .hasSize(2);
    }

    @Test
    void anAttachmentSurvivesAWholeRemittanceRoundTrip() {
        RemittanceInformation original = RemittanceInformation.of(
                EdiAttachment.of("<TranInf>ordered</TranInf>".getBytes(StandardCharsets.UTF_8)));

        RemittanceInformation parsed =
                RemittanceInformation.from(original.toXml().orElseThrow());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.ediAttachment().orElseThrow().decodedText())
                .isEqualTo("<TranInf>ordered</TranInf>");
    }

    @Test
    void anAttachmentSplitsAtSeventySixCharacters() {
        byte[] payload = "x".repeat(200).getBytes(StandardCharsets.UTF_8);

        EdiAttachment attachment = EdiAttachment.of(payload);

        assertThat(attachment.base64Lines())
                .allSatisfy(line -> assertThat(line.length())
                        .isLessThanOrEqualTo(EdiAttachment.LINE_LENGTH));
        assertThat(attachment.base64Lines().get(0)).hasSize(EdiAttachment.LINE_LENGTH);
        assertThat(attachment.decodedBytes()).isEqualTo(payload);
    }

    /// Recognised by the transfer-encoding header, not by position.
    @Test
    void anAttachmentIsRecognisedEvenWithUnusualHeaders() {
        EdiAttachment attachment = EdiAttachment.parse(List.of(
                "Content-Type: text/xml",
                EdiAttachment.TRANSFER_ENCODING,
                "eA==")).orElseThrow();

        assertThat(attachment.mimeHeaders()).hasSize(2);
        assertThat(attachment.decodedText()).isEqualTo("x");
    }

    @Test
    void ordinaryRemittanceTextIsNotAnAttachment() {
        RemittanceInformation text = new RemittanceInformation(List.of("INVOICE 12345"));

        assertThat(text.ediAttachment()).isEmpty();
        assertThat(text.freeText()).containsExactly("INVOICE 12345");
    }

    @Test
    void anAttachmentThatDoesNotDecodeSaysSoWithoutLosingItsLines() {
        EdiAttachment broken = new EdiAttachment(
                List.of(EdiAttachment.TRANSFER_ENCODING), List.of("!!!not base64!!!"));

        assertThatIllegalStateException()
                .isThrownBy(broken::decodedBytes)
                .withMessageContaining("preserved exactly");
        assertThat(broken.toUnstructured()).contains("!!!not base64!!!");
    }

    @Test
    void anAttachmentSaysHowBigItIs() {
        assertThat(EdiAttachment.of(new byte[] {1, 2, 3}))
                .hasToString("EdiAttachment[4 base64 chars in 1 lines]");
        assertThat(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})).hasSize(4);
    }

    // ------------------------------------------------------------------ nulls

    @Test
    void nullsAreRejectedByName() {
        assertThatNullPointerException().isThrownBy(() -> Party.named(null));
        assertThatNullPointerException().isThrownBy(() -> new Account(null, ""));
        assertThatNullPointerException().isThrownBy(() -> new Agent("", null, ""));
        assertThatNullPointerException().isThrownBy(() -> new Money(null, "JPY"));
        assertThatNullPointerException().isThrownBy(() -> new RemittanceInformation(null));
        assertThatNullPointerException().isThrownBy(() -> new EdiAttachment(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> Pain001Document.from(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new Pain001Document(null, List.of()));
    }

    @Test
    void aMessageIdCannotBeBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new io.zengin4j.iso20022.envelope.MessageId("  "));
    }
}
