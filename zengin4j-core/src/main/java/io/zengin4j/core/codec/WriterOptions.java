package io.zengin4j.core.codec;

import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import java.util.Objects;
import java.util.Optional;

/**
 * How to write a file.
 *
 * <p>The default is to <strong>reproduce the framing the file already
 * carries</strong> — its separator convention, whether one followed the last
 * record, its byte order mark, its EOF byte. That is what makes INV-1
 * expressible: a file read and written again comes back byte for byte, rather
 * than reformatted into this library's preferences.
 *
 * <p>Override the framing to impose one instead (R-C9), which is what a caller
 * building a file from scratch usually wants.
 *
 * <p>Immutable and thread-safe.
 *
 * @since 0.1.0
 */
public final class WriterOptions {

    private static final WriterOptions REPRODUCING = new WriterOptions(Optional.empty());

    private final Optional<FileFraming> framing;

    private WriterOptions(Optional<FileFraming> framing) {
        this.framing = framing;
    }

    /**
     * Returns options that reproduce each file's own framing.
     *
     * @return the default options
     */
    public static WriterOptions defaults() {
        return REPRODUCING;
    }

    /**
     * Returns options that impose a framing, ignoring the file's own.
     *
     * @param framing the framing to write
     * @return the options
     */
    public static WriterOptions framing(FileFraming framing) {
        return new WriterOptions(Optional.of(Objects.requireNonNull(framing, "framing")));
    }

    /**
     * Returns options that impose a separator convention, appending one after
     * every record including the last — the framing the published
     * record-length statements describe (OQ-4).
     *
     * @param separator the separator convention
     * @return the options
     */
    public static WriterOptions separator(SeparatorStyle separator) {
        return framing(new FileFraming(false, separator, separator != SeparatorStyle.NONE, false));
    }

    /**
     * Returns the framing override, if one was set.
     *
     * @return the override, or empty to reproduce the file's own framing
     */
    public Optional<FileFraming> framingOverride() {
        return framing;
    }

    /**
     * Returns the framing to write a given file with.
     *
     * @param fileFraming the framing the file carries
     * @return the override if set, otherwise the file's own
     */
    public FileFraming resolve(FileFraming fileFraming) {
        return framing.orElse(fileFraming);
    }
}
