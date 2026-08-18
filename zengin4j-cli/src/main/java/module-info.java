/**
 * Command line interface: {@code validate}, {@code inspect}, {@code convert},
 * {@code dryrun}, {@code generate}, {@code diff} and {@code explain}.
 *
 * <p><strong>An application, not a library.</strong> It is the one module with
 * a third-party runtime dependency — picocli, for argument parsing — and it is
 * deliberately not published: nothing downstream can inherit that dependency
 * because nothing downstream depends on this (ADR-0024). {@code core}'s
 * zero-dependency guarantee is unaffected and separately enforced.
 *
 * @since 0.3.0
 */
module io.zengin4j.cli {

    requires io.zengin4j.core;
    requires io.zengin4j.validation;
    requires io.zengin4j.iso20022;
    requires io.zengin4j.testkit;
    requires info.picocli;

    // picocli reflects over the command classes to bind options to fields.
    opens io.zengin4j.cli to info.picocli;
    opens io.zengin4j.cli.command to info.picocli;

    exports io.zengin4j.cli;
}
