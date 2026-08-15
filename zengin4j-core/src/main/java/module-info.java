/**
 * Reading and writing 全銀協規定形式 fixed-length payment files.
 *
 * <p><strong>Zero runtime dependencies (R-M1).</strong> This module requires
 * nothing beyond {@code java.base}, which is what makes it adoptable in
 * environments with a dependency review process. Adding one — a YAML reader, a
 * JSON library, a logging facade — is a breaking change to the property that
 * matters most about this artifact.
 *
 * <p>Format descriptors are authored as YAML and compiled into this module by
 * the build, so there is no parser and no descriptor resource here to require
 * one (ADR-0016).
 *
 * @since 0.1.0
 */
module io.zengin4j.core {

    exports io.zengin4j.core.annotation;
    exports io.zengin4j.core.charset;
    exports io.zengin4j.core.codec;
    exports io.zengin4j.core.error;
    exports io.zengin4j.core.format;
    exports io.zengin4j.core.format.generated;
    exports io.zengin4j.core.model;
    exports io.zengin4j.core.model.generated;
    exports io.zengin4j.core.time;
}
