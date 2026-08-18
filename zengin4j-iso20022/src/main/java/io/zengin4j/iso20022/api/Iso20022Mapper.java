package io.zengin4j.iso20022.api;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.iso20022.loss.MappingLossReport;
import io.zengin4j.iso20022.mapping.MappingRegistry;
import io.zengin4j.iso20022.mapping.MappingRow;
import io.zengin4j.iso20022.pain001.Pain001Document;
import java.util.List;
import java.util.Objects;

/**
 * Converts between Zengin files and the ISO 20022 messages of the ZEDI profile.
 *
 * <h2>Every conversion reports what it lost</h2>
 *
 * <p>There is no method here that returns a converted file on its own (R-I14).
 * The two formats are not isomorphic — a 140-character name in any script does
 * not fit in thirty bytes of half-width katakana, a full date does not fit in
 * {@code MMDD}, and JPY is the only currency one side can express — so every
 * conversion loses something, and an API that let a caller forget that would be
 * lying about the thing that matters most.
 *
 * <p>By default a conversion <strong>refuses</strong> when the loss reaches
 * {@link LossSeverity#CRITICAL}: money that could reach the wrong account is
 * not something to return quietly and hope somebody reads the report. Set
 * {@link MappingContext.Builder#acceptAnyLoss()} to get it back.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * MappingContext context = MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
 *         .targetFormat(registry.require(FormatId.of("sougou-furikomi")))
 *         .build();
 *
 * MappingResult<ZediFile> converted = Iso20022Mapper.create().toIso(file, context);
 * ZediEnvelopeWriter.write(converted.output(), Path.of("payments.xml"));
 * System.out.print(converted.loss().toText());
 * }</pre>
 *
 * <p>Immutable and thread-safe.
 *
 * @since 0.5.0
 */
public final class Iso20022Mapper {

    private final MappingRegistry registry;

    private Iso20022Mapper(MappingRegistry registry) {
        this.registry = registry;
    }

    /**
     * A mapper over the bundled mappings.
     *
     * @return the mapper
     */
    public static Iso20022Mapper create() {
        return new Iso20022Mapper(MappingRegistry.defaults());
    }

    /**
     * A mapper over a given registry.
     *
     * @param registry the mappings to use
     * @return the mapper
     */
    public static Iso20022Mapper using(MappingRegistry registry) {
        return new Iso20022Mapper(Objects.requireNonNull(registry, "registry"));
    }

    /**
     * Converts a Zengin file to a {@code pain.001} message in its ZEDI envelope.
     *
     * @param file    the file to convert
     * @param context what the conversion needs that the file does not carry
     * @return the message and what the conversion lost
     * @throws MappingFailedException      if the loss reaches
     *                                     {@link MappingContext#failOnSeverity()}
     * @throws io.zengin4j.iso20022.mapping.UnsupportedMappingException
     *                                     if no mapping is bundled for the
     *                                     file's format
     */
    public MappingResult<ZediFile> toIso(ZenginFile file, MappingContext context) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(context, "context");

        List<MappingRow> rows = registry.requireRowsFor(file.format(),
                Pain001Document.MESSAGE_ID);
        ZenginFields reader =
                new ZenginFields(file.descriptor(), context.targetCharset(), rows);

        return refuseIfTooLossy(new ZenginToPain001(context, reader).convert(file), context);
    }

    /**
     * Converts a ZEDI file back to a Zengin file.
     *
     * <p>The context is required and is not defaulted (R-I20): the XML does not
     * carry 委託者コード, does not say which Zengin format to produce, and does
     * not say what to do when a name will not fit.
     *
     * @param file    the ZEDI file, as read by
     *                {@link io.zengin4j.iso20022.envelope.ZediEnvelopeReader}
     * @param context what the conversion needs that the XML does not carry
     * @return the file and what the conversion lost
     * @throws MappingFailedException if the loss reaches
     *                                {@link MappingContext#failOnSeverity()}
     * @throws IllegalArgumentException if the file carries a message this
     *                                  library does not map
     */
    public MappingResult<ZenginFile> toZengin(ZediFile file, MappingContext context) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(context, "context");

        FormatDescriptor target = context.requireTargetFormat();
        registry.requireRowsFor(target.id(), Pain001Document.MESSAGE_ID);

        ZediMessage message = file.onlyMessage();
        return refuseIfTooLossy(
                new Pain001ToZengin(context).convert(document(file), message.body()), context);
    }

    /**
     * Converts and returns only the loss report, producing no output (R-I17).
     *
     * <p>Serves the question "what would this cost me?", which is worth being
     * able to ask before committing to an answer. It never refuses, whatever
     * {@link MappingContext#failOnSeverity()} says — the whole point is to see
     * the loss, and throwing it away because it is severe would be perverse.
     *
     * @param file    the file to convert
     * @param context what the conversion needs that the file does not carry
     * @return what converting would lose
     */
    public MappingLossReport dryRun(ZenginFile file, MappingContext context) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(context, "context");

        List<MappingRow> rows = registry.requireRowsFor(file.format(),
                Pain001Document.MESSAGE_ID);
        ZenginFields reader =
                new ZenginFields(file.descriptor(), context.targetCharset(), rows);
        return new ZenginToPain001(context.acceptingAnyLoss(), reader).convert(file).loss();
    }

    /**
     * Converts a file to ISO 20022 and back, accumulating the loss from both
     * legs (R-I18).
     *
     * <p>The honest demonstration that conversion is not bijective. Run it on a
     * real file and read what comes back: a name loses its kanji on the way out
     * and does not get them back, a year is invented on the way out and dropped
     * on the way in, a reference that fits in thirty-five characters does not
     * fit in ten.
     *
     * <p>Like {@link #dryRun}, this does not refuse — a round trip that stopped
     * at the first critical loss could not show you the rest of it.
     *
     * @param file    the file to send round
     * @param context what the conversions need that neither format carries
     * @return both legs, and everything they lost
     */
    public RoundTripResult roundTrip(ZenginFile file, MappingContext context) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(context, "context");

        MappingContext permissive = context.acceptingAnyLoss();

        MappingResult<ZediFile> out = toIso(file, permissive);
        MappingResult<ZenginFile> back = toZengin(out.output(), permissive);

        return new RoundTripResult(file, out.output(), back.output(),
                out.loss().and(back.loss()));
    }

    /**
     * The {@code pain.001} document inside a ZEDI file.
     *
     * <p>R-I3 pins the version. A document declaring {@code pain.001.001.09} is
     * refused rather than mapped as though it were the pinned one: the two are
     * different messages, and the elements this mapper reads are not guaranteed
     * to mean the same thing in both.
     */
    private static Pain001Document document(ZediFile file) {
        ZediMessage message = file.onlyMessage();
        MessageId declared = message.messageId().orElseThrow(() ->
                new IllegalArgumentException("the message body declares no ISO 20022 namespace, "
                        + "so there is no way to tell what it is. Expected "
                        + Pain001Document.MESSAGE_ID.namespace()));

        if (!declared.equals(Pain001Document.MESSAGE_ID)) {
            throw new IllegalArgumentException("this file carries " + declared + " and this "
                    + "library maps " + Pain001Document.MESSAGE_ID + " only (R-I3). "
                    + (declared.family().equals(Pain001Document.MESSAGE_ID.family())
                            ? "It is the same message in a different version, which is not the "
                                    + "same thing: the elements read here are not guaranteed to "
                                    + "mean what they mean in " + Pain001Document.MESSAGE_ID + "."
                            : "Inbound messages — pain.002, camt.052, camt.054 — are Epic 8."));
        }
        return Pain001Document.from(message.body());
    }

    private static <T> MappingResult<T> refuseIfTooLossy(MappingResult<T> result,
            MappingContext context) {
        context.failOnSeverity()
                .filter(result.loss()::hasAtLeast)
                .ifPresent(threshold -> {
                    throw new MappingFailedException(threshold, result.loss());
                });
        return result;
    }
}
