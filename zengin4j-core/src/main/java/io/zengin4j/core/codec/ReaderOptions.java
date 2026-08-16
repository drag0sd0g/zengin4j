package io.zengin4j.core.codec;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * How to read a file.
 *
 * <p>Immutable and thread-safe. Build one and share it.
 *
 * @since 0.1.0
 */
public final class ReaderOptions {

    /** Default number of records the read buffer holds (R-MEM1). */
    public static final int DEFAULT_BUFFER_RECORDS = 512;

    private final ZenginCharset charset;
    private final boolean allowUnverifiedFormats;
    private final Optional<FormatId> format;
    private final OptionalInt recordLength;
    private final ParseMode mode;
    private final int bufferRecords;
    private final ByteOrderMarkPolicy byteOrderMark;
    private final CharacterPolicy characterPolicy;
    private final FormatRegistry registry;
    private final Consumer<ZenginWarning> warningListener;

    private ReaderOptions(Builder builder) {
        this.charset = builder.charset;
        this.allowUnverifiedFormats = builder.allowUnverifiedFormats;
        this.format = builder.format;
        this.recordLength = builder.recordLength;
        this.mode = builder.mode;
        this.bufferRecords = builder.bufferRecords;
        this.byteOrderMark = builder.byteOrderMark;
        this.characterPolicy = builder.characterPolicy;
        this.registry = builder.registry == null ? FormatRegistry.defaults() : builder.registry;
        this.warningListener = builder.warningListener;
    }

    /**
     * Returns options with every default in place.
     *
     * <p>Loads a fresh {@link FormatRegistry} on each call. Reading many files
     * means building one registry and passing it to
     * {@link Builder#registry(FormatRegistry)} instead (R-0.10).
     *
     * @return default options
     */
    public static ReaderOptions defaults() {
        return builder().build();
    }

    /**
     * Creates a builder with every default in place.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder initialised from these options.
     *
     * @return a builder that would rebuild these options
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.charset = charset;
        builder.allowUnverifiedFormats = allowUnverifiedFormats;
        builder.format = format;
        builder.recordLength = recordLength;
        builder.mode = mode;
        builder.bufferRecords = bufferRecords;
        builder.byteOrderMark = byteOrderMark;
        builder.characterPolicy = characterPolicy;
        builder.registry = registry;
        builder.warningListener = warningListener;
        return builder;
    }

    /**
     * Returns the encoding text fields are decoded with.
     *
     * @return the charset; {@link ZenginCharset#MS932} by default
     */
    public ZenginCharset charset() {
        return charset;
    }

    /**
     * Reports whether formats with unconfirmed layouts may be used.
     *
     * @return {@code false} by default (R-0.1)
     */
    public boolean allowUnverifiedFormats() {
        return allowUnverifiedFormats;
    }

    /**
     * Returns the format to read the file as, if the caller named one.
     *
     * @return the format id, or empty to detect it from the header
     */
    public Optional<FormatId> format() {
        return format;
    }

    /**
     * Returns the record length override, if the caller set one.
     *
     * @return the override, or empty to use the format's own record length
     */
    public OptionalInt recordLength() {
        return recordLength;
    }

    /**
     * Returns what the reader does when a record does not fit the format.
     *
     * @return the parse mode; {@link ParseMode#STRICT} by default
     */
    public ParseMode mode() {
        return mode;
    }

    /**
     * Returns how many records the read buffer holds.
     *
     * @return the buffer size in records
     */
    public int bufferRecords() {
        return bufferRecords;
    }

    /**
     * Returns what to do about a leading byte order mark.
     *
     * @return the policy; {@link ByteOrderMarkPolicy#REJECT} by default
     */
    public ByteOrderMarkPolicy byteOrderMark() {
        return byteOrderMark;
    }

    /**
     * Returns what the reader does about characters a field may not carry.
     *
     * @return the policy, never {@code null}
     */
    public CharacterPolicy characterPolicy() {
        return characterPolicy;
    }

    /**
     * Returns the registry format descriptors are looked up in.
     *
     * @return the registry
     */
    public FormatRegistry registry() {
        return registry;
    }

    /**
     * Returns the listener warnings are handed to.
     *
     * @return the listener
     */
    public Consumer<ZenginWarning> warningListener() {
        return warningListener;
    }

    /**
     * Collects reader settings.
     *
     * <p>Not thread-safe; the options it builds are.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private ZenginCharset charset = ZenginCharset.defaultCharset();
        private boolean allowUnverifiedFormats;
        private Optional<FormatId> format = Optional.empty();
        private OptionalInt recordLength = OptionalInt.empty();
        private ParseMode mode = ParseMode.STRICT;
        private int bufferRecords = DEFAULT_BUFFER_RECORDS;
        private ByteOrderMarkPolicy byteOrderMark = ByteOrderMarkPolicy.REJECT;
        private CharacterPolicy characterPolicy = CharacterPolicy.IGNORE;
        private FormatRegistry registry;
        private Consumer<ZenginWarning> warningListener = ZenginWarning::log;

        private Builder() {
        }

        /**
         * Sets the encoding text fields are decoded with.
         *
         * @param value the charset
         * @return this builder
         */
        public Builder charset(ZenginCharset value) {
            this.charset = Objects.requireNonNull(value, "charset");
            return this;
        }

        /**
         * Permits formats whose layout has not been confirmed against two
         * independent published sources.
         *
         * <p>Every format shipped in 0.1.0 is unverified, so reading anything
         * at all currently requires this. That is deliberate: the opt-in lives
         * in your source code, where a reviewer can see it (§0.3).
         *
         * @param value whether to permit unverified formats
         * @return this builder
         */
        public Builder allowUnverifiedFormats(boolean value) {
            this.allowUnverifiedFormats = value;
            return this;
        }

        /**
         * Names the format instead of detecting it from the header.
         *
         * @param value the format id
         * @return this builder
         */
        public Builder format(FormatId value) {
            this.format = Optional.of(Objects.requireNonNull(value, "format"));
            return this;
        }

        /**
         * Overrides the record length (R-C5).
         *
         * <p>For institutions that emit records longer than the standard
         * layout, padding the excess.
         *
         * @param value the record length in bytes
         * @return this builder
         * @throws IllegalArgumentException if the value is not positive
         */
        public Builder recordLength(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("record length must be positive, found " + value);
            }
            this.recordLength = OptionalInt.of(value);
            return this;
        }

        /**
         * Sets what the reader does when a record does not fit the format.
         *
         * @param value the parse mode
         * @return this builder
         */
        public Builder mode(ParseMode value) {
            this.mode = Objects.requireNonNull(value, "mode");
            return this;
        }

        /**
         * Sets how many records the read buffer holds.
         *
         * @param value the buffer size in records
         * @return this builder
         * @throws IllegalArgumentException if the value is not positive
         */
        public Builder bufferRecords(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("the buffer must hold at least one record, found " + value);
            }
            this.bufferRecords = value;
            return this;
        }

        /**
         * Sets what the reader does about a byte a field's character class does
         * not permit (R-C13).
         *
         * @param value the policy; {@link CharacterPolicy#IGNORE} by default
         * @return this builder
         */
        public Builder characterPolicy(CharacterPolicy value) {
            this.characterPolicy = Objects.requireNonNull(value, "characterPolicy");
            return this;
        }

        /**
         * Sets what to do about a leading byte order mark.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder byteOrderMark(ByteOrderMarkPolicy value) {
            this.byteOrderMark = Objects.requireNonNull(value, "byteOrderMark");
            return this;
        }

        /**
         * Sets the registry format descriptors are looked up in.
         *
         * @param value the registry
         * @return this builder
         */
        public Builder registry(FormatRegistry value) {
            this.registry = Objects.requireNonNull(value, "registry");
            return this;
        }

        /**
         * Sets the listener warnings are handed to.
         *
         * @param value the listener; use {@code warning -> {}} to silence them,
         *              remembering that the reader still collects them
         * @return this builder
         */
        public Builder warningListener(Consumer<ZenginWarning> value) {
            this.warningListener = Objects.requireNonNull(value, "warningListener");
            return this;
        }

        /**
         * Builds the options.
         *
         * @return immutable options
         */
        public ReaderOptions build() {
            return new ReaderOptions(this);
        }
    }
}
