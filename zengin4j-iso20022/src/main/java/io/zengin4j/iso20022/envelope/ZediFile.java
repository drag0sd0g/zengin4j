package io.zengin4j.iso20022.envelope;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A ZEDI file: everything before the first XML declaration, then the messages.
 *
 * <p>The preamble is almost always empty. It exists so that
 * {@link ZediEnvelopeWriter} can reproduce a file exactly — a byte order mark
 * or a stray newline ahead of the first declaration is part of what arrived,
 * and a library that quietly drops it cannot claim byte-identical output.
 *
 * @since 0.5.0
 */
public final class ZediFile {

    private final byte[] preamble;
    private final List<ZediMessage> messages;

    /**
     * Creates a file.
     *
     * @param preamble the bytes before the first XML declaration
     * @param messages the messages, in file order
     */
    public ZediFile(byte[] preamble, List<ZediMessage> messages) {
        this.preamble = Objects.requireNonNull(preamble, "preamble").clone();
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    /**
     * A file of messages, with nothing before the first declaration.
     *
     * @param messages the messages, in file order
     * @return the file
     */
    public static ZediFile of(List<ZediMessage> messages) {
        return new ZediFile(new byte[0], messages);
    }

    /**
     * A file of one message.
     *
     * @param message the message
     * @return the file
     */
    public static ZediFile of(ZediMessage message) {
        return of(List.of(message));
    }

    /** @return the bytes before the first XML declaration */
    public byte[] preamble() {
        return preamble.clone();
    }

    /** @return the messages, in file order */
    public List<ZediMessage> messages() {
        return messages;
    }

    /**
     * The single message in this file.
     *
     * @return the message
     * @throws IllegalStateException if the file does not hold exactly one
     */
    public ZediMessage onlyMessage() {
        if (messages.size() != 1) {
            throw new IllegalStateException("expected one message, found " + messages.size()
                    + ". Use messages() — a ZEDI file may carry several groups (R-I7).");
        }
        return messages.get(0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ZediFile file
                && Arrays.equals(preamble, file.preamble)
                && messages.equals(file.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(preamble), messages);
    }

    @Override
    public String toString() {
        return "ZediFile[" + messages.size()
                + (messages.size() == 1 ? " message]" : " messages]");
    }
}
