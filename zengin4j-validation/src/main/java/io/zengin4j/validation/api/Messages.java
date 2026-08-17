package io.zengin4j.validation.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Finding text, from properties files rather than from string literals (R-E4).
 *
 * <p>Two reasons the text lives outside the code. A translation is only
 * reviewable if a translator can see it as a file — nobody reviews Japanese
 * embedded in the middle of a rule's control flow. And a finding carries
 * <em>both</em> languages at once (R-V2), which means the code needs both at
 * the same moment; resolving one against the JVM locale, as a
 * {@link ResourceBundle} normally would, is the wrong shape.
 *
 * <p>So both bundles are loaded explicitly and a message is fetched from each.
 * The JVM locale still decides what {@link ValidationReport#toText()} renders,
 * but it does not decide what a finding contains.
 *
 * <p>A missing key is a build-time defect, not a runtime one: it throws, and
 * {@code MessageBundleTest} asserts that every rule id has an entry in both
 * bundles. Shipping a finding whose Japanese text says
 * {@code !V-301.message!} would be worse than shipping no Japanese at all.
 *
 * @since 0.2.0
 */
public final class Messages {
    private static final ResourceBundle ENGLISH = load("messages.properties");
    private static final ResourceBundle JAPANESE = load("messages_ja.properties");

    /**
     * Loads one bundle by resource name, without locale resolution.
     *
     * <p>{@code ResourceBundle.getBundle(name, ENGLISH)} would not do. Its
     * lookup falls back to the <em>default</em> locale before the base bundle,
     * so on a JVM running in Japanese — which is most of the JVMs this library
     * will run on — asking for English returns Japanese, and every finding
     * would carry the same text twice. The bug would be invisible to anyone
     * developing in a non-Japanese locale.
     *
     * <p>Loading each file directly also matches what is actually wanted here:
     * both languages at once (R-V2), not one chosen by the environment. These
     * are still {@link PropertyResourceBundle}s read from properties files, so
     * R-E4's point — that translations are reviewable as files — holds.
     */
    private static ResourceBundle load(String resource) {
        try (InputStream stream = Messages.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new MissingResourceException(
                        "validation message bundle '" + resource + "' is missing from the artifact",
                        Messages.class.getName(), resource);
            }
            return new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("could not read validation messages from " + resource, e);
        }
    }

    private Messages() {
    }

    /**
     * Looks a message up in both languages and applies its arguments.
     *
     * @param key       the message key, conventionally {@code <ruleId>.message}
     * @param arguments values to substitute, in {@link MessageFormat} order
     * @return the English and Japanese renderings
     * @throws MissingResourceException if either bundle lacks the key
     */
    public static Bilingual format(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        return new Bilingual(
                render(ENGLISH, key, Locale.ENGLISH, arguments),
                render(JAPANESE, key, Locale.JAPANESE, arguments));
    }

    /**
     * Looks up a rule's description, used where a report lists the rules
     * themselves.
     *
     * @param ruleId the rule id
     * @return the English description
     */
    public static String description(String ruleId) {
        return ENGLISH.getString(ruleId + ".description");
    }

    /**
     * Whether a key exists in both bundles.
     *
     * @param key the key to look for
     * @return {@code true} if both bundles have it
     */
    public static boolean has(String key) {
        return ENGLISH.containsKey(key) && JAPANESE.containsKey(key);
    }

    private static String render(ResourceBundle bundle, String key, Locale locale, Object... arguments) {
        String pattern = bundle.getString(key);
        if (arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(arguments);
    }

    /**
     * One message in both languages.
     *
     * @param en English text
     * @param ja Japanese text
     * @since 0.2.0
     */
    public record Bilingual(String en, String ja) {
        /**
         * Validates the components.
         */
        public Bilingual {
            Objects.requireNonNull(en, "en");
            Objects.requireNonNull(ja, "ja");
        }

        /**
         * Applies both messages to a finding under construction.
         *
         * @param builder the builder to set them on
         * @return the same builder
         */
        public Finding.Builder into(Finding.Builder builder) {
            return builder.message(en, ja);
        }
    }
}
