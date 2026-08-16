#!/usr/bin/env bash
#
# R-L5: fail if anything in the repository looks like a real account identifier.
#
# The rule this enforces is simple enough to explain in one line, which is what
# makes it usable: every synthetic identifier in this repository begins with 9,
# and every zero-padded numeric literal begins with 0. A bare run of seven or
# more digits beginning with anything else is either a real identifier or a
# value that needs a comment explaining why it is not.
#
# Seven digits is the length of a 口座番号, the shortest identifier worth
# protecting.
#
# A digit run that is part of a longer alphanumeric token is not a bare
# identifier and is not flagged: INV20260001 is an invoice reference, and the
# customer-code fields of these formats are full of them.

set -euo pipefail

MIN_DIGITS=7

# Third-party text and build output are not ours to police. The build
# specification is an input to this project, not a product of it.
#
# The conformance corpora under input/ are excluded for a structural reason,
# not a convenience one. In a fixed-length record every field abuts the next
# with no separator, so a digit run spans field boundaries and takes its first
# digit from whatever precedes it — which is a データ区分 or 種別コード
# constant, never the 9 this convention requires. No conformant file of these
# formats can pass this check, whatever its account numbers are.
#
# Nothing is lost by that. Each corpus is committed alongside a rendering that
# prints one field per line, those renderings are not excluded, and every field
# of every record appears in them. A real identifier in a corpus is still
# caught — in the representation where a digit run means a single field.
EXCLUDES=(
    ':(exclude)LICENSE'
    ':(exclude)gradle/wrapper/*'
    ':(exclude)gradlew*'
    ':(exclude)**/build/**'
    ':(exclude)zengin4j-build-specification.md'
    ':(exclude)**/resources/conformance/input/**'
)

violations=0

# --cached --others covers tracked and not-yet-committed files alike, so this
# runs the same way in CI and against a working tree.
while IFS= read -r file; do
    [[ -f "$file" ]] || continue
    grep -Iq . "$file" 2>/dev/null || continue

    while IFS= read -r hit; do
        line="${hit%%:*}"
        run="${hit#*:}"
        first="${run:0:1}"
        if [[ "$first" != "9" && "$first" != "0" ]]; then
            echo "::error file=${file},line=${line}::possible real identifier '${run}'"
            echo "  ${file}:${line}: ${run}"
            violations=$((violations + 1))
        fi
    done < <(perl -nE "while (/(?<![0-9A-Za-z_])([0-9]{${MIN_DIGITS},})(?![0-9A-Za-z_])/g) { say qq{\$.:\$1} }" \
        "$file")
done < <(git ls-files --cached --others --exclude-standard -- . "${EXCLUDES[@]}")

if [[ "$violations" -gt 0 ]]; then
    cat >&2 <<'EOF'

Found bare digit runs that do not follow this repository's synthetic-identifier
convention (R-L5, P1).

Synthetic identifiers here begin with 9: bank 9999, branch 999, accounts
9xxxxxx. Zero-padded field literals begin with 0. If a value above is neither —
a checksum, a version, a timestamp — either reformat it or add an exclude to
this script with a comment saying what it is.

No real account number, bank identifier or payment record may be committed.
EOF
    exit 1
fi

echo "no bare identifiers outside the synthetic ranges"
