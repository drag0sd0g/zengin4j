/**
 * Rendering and alignment, with no picocli in sight.
 *
 * <p>Deliberately free of command-line concerns: the field table, the JSON
 * writer and the record alignment are the parts worth testing closely, and they
 * are easiest to test when they take data and return text.
 *
 * <p>Not exported. Nothing outside this module should depend on how the CLI
 * formats a table.
 *
 * @since 0.3.0
 */
package io.zengin4j.cli.internal;
