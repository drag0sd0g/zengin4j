package io.zengin4j.cli.command;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.ParseMode;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.format.FormatId;
import picocli.CommandLine;

/// The options every command that opens a Zengin file shares.
///
/// A mixin rather than a base class: picocli composes these into each
/// command's own option list, so `--charset` means the same thing and
/// carries the same help text everywhere it appears. Three commands drifting
/// apart on what `--format` does is the failure this prevents.
///
/// @since 0.3.0
public final class ReadingOptions {

    @CommandLine.Option(
            names = "--format",
            paramLabel = "ID",
            description = "Format id, e.g. sougou-furikomi. Detected from the 種別コード if omitted.")
    String format;

    @CommandLine.Option(
            names = "--charset",
            paramLabel = "NAME",
            description = "Encoding: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    ZenginCharset charset = ZenginCharset.defaultCharset();

    @CommandLine.Option(
            names = "--allow-unverified",
            description = "Read a format whose byte layout is not confirmed by two published "
                    + "sources. Required for every bundled format today; prints a warning.")
    boolean allowUnverified;

    @CommandLine.Option(
            names = "--lenient",
            description = "Keep reading past a record that does not fit the format, surfacing it "
                    + "as malformed rather than failing.")
    boolean lenient;

    /// Builds reader options, routing the library's warnings to stderr.
    ///
    /// R-CLI6 requires the unverified-format warning to be visible rather
    /// than swallowed, and stderr is where it belongs: a caller redirecting
    /// stdout to a file still sees it, and the file does not gain a line that is
    /// not part of the output.
    ///
    /// @param err where warnings go
    /// @return the reader options
    ReaderOptions toReaderOptions(PrintWriter err) {
        ReaderOptions.Builder builder = ReaderOptions.builder()
                .charset(charset)
                .allowUnverifiedFormats(allowUnverified)
                .mode(lenient ? ParseMode.LENIENT : ParseMode.STRICT)
                .warningListener(warning -> err.println("warning: " + warning.messageEn()));
        if (format != null) {
            builder.format(FormatId.of(format));
        }
        return builder.build();
    }

    /// The format the caller pinned, or `null` to detect it.
    FormatId formatId() {
        return format == null ? null : FormatId.of(format);
    }

    ZenginCharset charset() {
        return charset;
    }
}
