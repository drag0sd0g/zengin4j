/**
 * The conversion API: {@code Iso20022Mapper} and what it needs and returns.
 *
 * <p>Two rules shape everything here. A conversion always returns its loss
 * report alongside its output, and there is no method that returns the output
 * alone (R-I14). And the downward leg always takes a {@code MappingContext},
 * never a default, because the XML genuinely cannot supply 委託者コード, the
 * target format or the truncation policy (R-I20).
 *
 * @since 0.5.0
 */
package io.zengin4j.iso20022.api;
