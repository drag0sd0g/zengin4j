/**
 * One class per subcommand, plus the options they share.
 *
 * <p>Each command does its own work and returns an exit status; none of them
 * calls {@code System.exit}, so each is a plain object a test can drive.
 * {@code ReadingOptions} is mixed into every command that opens a file, so
 * {@code --charset} and {@code --format} cannot drift apart between them.
 *
 * <p><strong>Every command that prints record contents masks sensitive fields
 * unless {@code --unsafe-print} is given</strong> (R-CLI4).
 *
 * @since 0.3.0
 */
package io.zengin4j.cli.command;
