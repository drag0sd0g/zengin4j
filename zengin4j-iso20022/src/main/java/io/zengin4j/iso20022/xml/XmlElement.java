package io.zengin4j.iso20022.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable XML element: the whole document model this module needs.
 *
 * <p>ISO 20022 messages are element trees of a narrow shape — every element
 * either carries text or carries children, never both, and attributes appear
 * on a handful of amount elements. That is small enough to model directly, and
 * modelling it directly is what lets the mapper be driven by declared paths
 * rather than by generated bindings.
 *
 * @since 0.5.0
 */
public final class XmlElement {

    private final String name;
    private final String namespace;
    private final Map<String, String> attributes;
    private final String text;
    private final List<XmlElement> children;

    private XmlElement(String name, String namespace, Map<String, String> attributes,
            String text, List<XmlElement> children) {
        this.name = name;
        this.namespace = namespace;
        this.attributes = Map.copyOf(attributes);
        this.text = text;
        this.children = List.copyOf(children);
    }

    /**
     * Starts building an element.
     *
     * @param name the local name
     * @return a builder
     */
    public static Builder element(String name) {
        return new Builder(name);
    }

    /**
     * An element with text content and nothing else.
     *
     * @param name  the local name
     * @param value the text content
     * @return the element
     */
    public static XmlElement text(String name, String value) {
        return new Builder(name).text(value).build();
    }

    /** @return the local name */
    public String name() {
        return name;
    }

    /** @return the namespace URI, or an empty string when the element has none */
    public String namespace() {
        return namespace;
    }

    /** @return the character content, or an empty string when the element has children */
    public String text() {
        return text;
    }

    /** @return the attributes, in document order */
    public Map<String, String> attributes() {
        return attributes;
    }

    /** @return the child elements, in document order */
    public List<XmlElement> children() {
        return children;
    }

    /**
     * Looks up an attribute.
     *
     * @param attributeName the attribute's name
     * @return its value, or empty
     */
    public Optional<String> attribute(String attributeName) {
        return Optional.ofNullable(attributes.get(attributeName));
    }

    /**
     * Looks up the first child with a name.
     *
     * @param childName the child's local name
     * @return the child, or empty
     */
    public Optional<XmlElement> child(String childName) {
        for (XmlElement child : children) {
            if (child.name.equals(childName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Every child with a name, in document order.
     *
     * @param childName the children's local name
     * @return the children, possibly empty
     */
    public List<XmlElement> childrenNamed(String childName) {
        List<XmlElement> found = new ArrayList<>();
        for (XmlElement child : children) {
            if (child.name.equals(childName)) {
                found.add(child);
            }
        }
        return List.copyOf(found);
    }

    /**
     * Resolves a slash-separated path relative to this element.
     *
     * <p>The path addresses the <em>first</em> match at each step, which is what
     * a mapping row means by a path: the declaration says where a value lives,
     * and repetition is handled by the caller iterating a level explicitly.
     *
     * @param path e.g. {@code GrpHdr/InitgPty/Nm}
     * @return the element, or empty if any step is missing
     */
    public Optional<XmlElement> at(String path) {
        XmlElement current = this;
        for (String step : path.split("/")) {
            if (step.isEmpty()) {
                continue;
            }
            Optional<XmlElement> next = current.child(step);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    /**
     * The text at a path.
     *
     * @param path e.g. {@code GrpHdr/InitgPty/Nm}
     * @return the text, or empty if the path is missing
     */
    public Optional<String> textAt(String path) {
        return at(path).map(XmlElement::text).filter(value -> !value.isEmpty());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof XmlElement element
                && name.equals(element.name)
                && namespace.equals(element.namespace)
                && attributes.equals(element.attributes)
                && text.equals(element.text)
                && children.equals(element.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, namespace, attributes, text, children);
    }

    @Override
    public String toString() {
        return children.isEmpty()
                ? "<" + name + ">" + text + "</" + name + ">"
                : "<" + name + "> (" + children.size() + " children)";
    }

    /** Assembles an element. */
    public static final class Builder {
        private final String name;
        private String namespace = "";
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private String text = "";
        private final List<XmlElement> children = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Declares the element's namespace.
         *
         * @param uri the namespace URI
         * @return this builder
         */
        public Builder namespace(String uri) {
            this.namespace = Objects.requireNonNull(uri, "uri");
            return this;
        }

        /**
         * Sets an attribute.
         *
         * @param attributeName the name
         * @param value         the value
         * @return this builder
         */
        public Builder attribute(String attributeName, String value) {
            attributes.put(Objects.requireNonNull(attributeName, "attributeName"),
                    Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets the character content.
         *
         * @param value the text
         * @return this builder
         */
        public Builder text(String value) {
            this.text = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Appends a child.
         *
         * @param child the child
         * @return this builder
         */
        public Builder child(XmlElement child) {
            children.add(Objects.requireNonNull(child, "child"));
            return this;
        }

        /**
         * Appends a child.
         *
         * @param child the child's builder
         * @return this builder
         */
        public Builder child(Builder child) {
            return child(child.build());
        }

        /**
         * Appends a text child when there is a value to write.
         *
         * <p>An ISO 20022 element that would be empty must be absent instead —
         * the schema's minimum occurrence is what makes {@code <Nm></Nm>} invalid
         * rather than merely unhelpful.
         *
         * @param childName the child's local name
         * @param value     the text, or null or blank to write nothing
         * @return this builder
         */
        public Builder textChild(String childName, String value) {
            if (value != null && !value.isBlank()) {
                children.add(XmlElement.text(childName, value));
            }
            return this;
        }

        /**
         * Appends a child when it is present.
         *
         * @param child the child, or empty to write nothing
         * @return this builder
         */
        public Builder childIfPresent(Optional<XmlElement> child) {
            child.ifPresent(children::add);
            return this;
        }

        /** @return true if nothing has been added */
        public boolean isEmpty() {
            return text.isEmpty() && children.isEmpty() && attributes.isEmpty();
        }

        /**
         * Whether a child has been appended.
         *
         * <p>The parser asks, so that mixed content in <em>input</em> becomes a
         * {@link MalformedXmlException} rather than the
         * {@link IllegalStateException} {@link #build()} raises. Both refuse the
         * same shape, and they are refusing different things: one is a document
         * this library cannot represent, the other is a mapping mistake.
         *
         * @return true if this element has children
         */
        boolean hasChildren() {
            return !children.isEmpty();
        }

        /**
         * Builds the element.
         *
         * @return the element
         * @throws IllegalStateException if it carries both text and children
         */
        public XmlElement build() {
            if (!text.isEmpty() && !children.isEmpty()) {
                throw new IllegalStateException(name + " has both text and child elements. "
                        + "No element in this profile is mixed content, so this is a mapping "
                        + "mistake rather than a document this writer should emit.");
            }
            List<XmlElement> inheriting = namespace.isEmpty()
                    ? children
                    : children.stream().map(child -> child.inNamespace(namespace)).toList();
            return new XmlElement(name, namespace, attributes, text, inheriting);
        }
    }

    /**
     * This element and its descendants, in a namespace they did not declare.
     *
     * <p>A default namespace applies to every element below it, so a child that
     * declares none is genuinely <em>in</em> its parent's namespace — that is
     * what a parser reports, and a tree built by hand has to agree or the two
     * will not compare equal despite serialising to the same bytes.
     */
    private XmlElement inNamespace(String uri) {
        if (!namespace.isEmpty()) {
            return this;
        }
        return new XmlElement(name, uri, attributes, text,
                children.stream().map(child -> child.inNamespace(uri)).toList());
    }
}
