package io.zengin4j.core.format;

import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.format.generated.BundledFormats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable set of format descriptors and the code lists they reference.
 *
 * <p>Immutable and thread-safe (R-T1): build one and share it. There is
 * deliberately no {@code register} method that mutates an existing registry —
 * {@link #withFormat(FormatDescriptor)} returns a new instance instead, so a
 * descriptor cannot appear or change under a reader that is midway through a
 * file.
 *
 * @since 0.1.0
 */
public final class FormatRegistry {

    private final Map<FormatId, FormatDescriptor> formats;
    private final Map<String, CodeList> codeLists;

    private FormatRegistry(Map<FormatId, FormatDescriptor> formats, Map<String, CodeList> codeLists) {
        this.formats = Collections.unmodifiableMap(new LinkedHashMap<>(formats));
        this.codeLists = Collections.unmodifiableMap(new LinkedHashMap<>(codeLists));
    }

    /**
     * Returns the descriptors bundled with this library.
     *
     * <p>Every bundled descriptor is {@code verified: false} in 0.1.0. Reading
     * a file with one requires
     * {@code ReaderOptions.builder().allowUnverifiedFormats(true)}.
     *
     * <p>Nothing is parsed here. The descriptors are authored as YAML and
     * compiled to Java by the build (ADR-0016), so this reads no files, needs
     * no class-path resources, and cannot fail on malformed input — a
     * descriptor that did not add up would have failed the build instead.
     *
     * <p>Each call constructs a fresh registry; there is no cached instance,
     * because a lazily initialised shared registry is exactly the static
     * mutable state R-T4 forbids. Construct one and inject it (R-0.10).
     *
     * @return a registry holding the bundled formats
     */
    public static FormatRegistry defaults() {
        Map<String, CodeList> lists = BundledFormats.codeLists();
        Builder builder = builder().codeLists(lists);
        for (FormatDescriptor descriptor : BundledFormats.formats(lists)) {
            builder.register(descriptor);
        }
        return builder.build();
    }

    /**
     * Creates an empty builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Looks a format up by id.
     *
     * @param id the format id
     * @return the descriptor, or empty if it is not registered
     */
    public Optional<FormatDescriptor> byId(FormatId id) {
        return Optional.ofNullable(formats.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns every registered format declaring a 種別コード.
     *
     * <p>Returns a list rather than an {@code Optional} because the codes are
     * not unique: 預金口座振替 and 口座振替結果 share {@code 91}. A caller
     * detecting the format from file content must decide what to do when this
     * returns more than one candidate.
     *
     * @param typeCode the two-character business type code
     * @return the matching descriptors, in registration order; empty if none
     */
    public List<FormatDescriptor> byTypeCode(String typeCode) {
        Objects.requireNonNull(typeCode, "typeCode");
        List<FormatDescriptor> matches = new ArrayList<>(1);
        for (FormatDescriptor descriptor : formats.values()) {
            if (descriptor.typeCode().equals(typeCode)) {
                matches.add(descriptor);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Returns every registered format, in registration order.
     *
     * @return an unmodifiable list of descriptors
     */
    public List<FormatDescriptor> all() {
        return List.copyOf(formats.values());
    }

    /**
     * Returns the code lists available to descriptors in this registry.
     *
     * @return an unmodifiable map keyed by code list id
     */
    public Map<String, CodeList> codeLists() {
        return codeLists;
    }

    /**
     * Returns a registry holding this registry's formats plus one more.
     *
     * @param descriptor the descriptor to add
     * @return a new registry; this one is unchanged
     * @throws FormatDescriptorException if a format with the same id is
     *                                   already registered
     */
    public FormatRegistry withFormat(FormatDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Map<FormatId, FormatDescriptor> combined = new LinkedHashMap<>(formats);
        if (combined.putIfAbsent(descriptor.id(), descriptor) != null) {
            throw FormatDescriptorException.forFormat(descriptor.id().value(),
                    "a format with this id is already registered");
        }
        return new FormatRegistry(combined, codeLists);
    }

    /**
     * Returns a comma-separated list of registered type codes, for
     * diagnostics.
     *
     * @return the registered type codes, or {@code "none"} if the registry is
     *         empty
     */
    public String describeTypeCodes() {
        if (formats.isEmpty()) {
            return "none";
        }
        List<String> codes = new ArrayList<>(formats.size());
        for (FormatDescriptor descriptor : formats.values()) {
            codes.add(descriptor.typeCode() + " (" + descriptor.id() + ")");
        }
        return String.join(", ", codes);
    }

    /**
     * Collects descriptors into an immutable registry.
     *
     * <p>Not thread-safe; the registry it builds is.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<FormatId, FormatDescriptor> formats = new LinkedHashMap<>();
        private Map<String, CodeList> codeLists = Map.of();

        private Builder() {
        }

        /**
         * Sets the code lists the registry exposes.
         *
         * @param lists code lists keyed by id
         * @return this builder
         */
        public Builder codeLists(Map<String, CodeList> lists) {
            this.codeLists = new LinkedHashMap<>(Objects.requireNonNull(lists, "lists"));
            return this;
        }

        /**
         * Adds a descriptor.
         *
         * @param descriptor the descriptor to add
         * @return this builder
         * @throws FormatDescriptorException if a format with the same id has
         *                                   already been added
         */
        public Builder register(FormatDescriptor descriptor) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (formats.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw FormatDescriptorException.forFormat(descriptor.id().value(),
                        "a format with this id is already registered");
            }
            return this;
        }

        /**
         * Builds the registry.
         *
         * @return an immutable registry
         */
        public FormatRegistry build() {
            return new FormatRegistry(formats, codeLists);
        }
    }
}
