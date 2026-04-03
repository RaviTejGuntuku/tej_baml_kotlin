#!/usr/bin/env bash
# End-to-end test: baml-cli generate with output_type "kotlin"
#
# Usage: ./scripts/test_cli_generate.sh
#
# Prerequisites:
#   cd engine && cargo build -p baml-cli
#
# This script:
#   1. Creates a temporary BAML project
#   2. Runs baml-cli generate
#   3. Verifies all expected files are generated
#   4. Checks generated code contains expected patterns
#   5. Cleans up

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENGINE_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)/engine"
CLI="$ENGINE_DIR/target/debug/baml-cli"

if [ ! -f "$CLI" ]; then
    echo "ERROR: baml-cli not found at $CLI"
    echo "Run: cd engine && cargo build -p baml-cli"
    exit 1
fi

VERSION=$("$CLI" --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "0.219.0")

TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

mkdir -p "$TMPDIR/baml_src"
cat > "$TMPDIR/baml_src/main.baml" << BAML
generator kotlin {
    output_type "kotlin"
    output_dir "../baml_client"
    version "$VERSION"
}

class Person {
    name string
    age int
    email string?
}

enum Sentiment {
    POSITIVE
    NEGATIVE
    NEUTRAL
}

function ExtractPerson(input: string) -> Person {
    client "openai/gpt-4o"
    prompt #"Extract person: {{ input }}"#
}

function ClassifySentiment(text: string) -> Sentiment {
    client "openai/gpt-4o"
    prompt #"Classify: {{ text }}"#
}
BAML

echo "=== Running baml-cli generate ==="
cd "$TMPDIR"
"$CLI" generate --from ./baml_src 2>&1

echo ""
echo "=== Checking generated files ==="
EXPECTED_FILES=(
    "baml_client/baml_client/BamlFunctions.kt"
    "baml_client/baml_client/BamlStreamFunctions.kt"
    "baml_client/baml_client/BamlParseFunctions.kt"
    "baml_client/baml_client/BamlTypeMap.kt"
    "baml_client/baml_client/BamlSourceMap.kt"
    "baml_client/baml_client/BamlRuntimeInit.kt"
    "baml_client/baml_client/types/Classes.kt"
    "baml_client/baml_client/types/Enums.kt"
    "baml_client/baml_client/types/Unions.kt"
    "baml_client/baml_client/types/TypeAliases.kt"
    "baml_client/baml_client/stream_types/Classes.kt"
    "baml_client/baml_client/stream_types/Unions.kt"
    "baml_client/baml_client/stream_types/TypeAliases.kt"
)

PASS=0
FAIL=0
for f in "${EXPECTED_FILES[@]}"; do
    if [ -f "$f" ]; then
        PASS=$((PASS + 1))
    else
        echo "  MISSING: $f"
        FAIL=$((FAIL + 1))
    fi
done

echo "  Files: $PASS present, $FAIL missing"

echo ""
echo "=== Checking generated code patterns ==="
check_pattern() {
    local file="$1" pattern="$2" desc="$3"
    if grep -q "$pattern" "$file" 2>/dev/null; then
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $desc (pattern '$pattern' not found in $file)"
        FAIL=$((FAIL + 1))
    fi
}

check_pattern "baml_client/baml_client/types/Classes.kt" "data class Person" "Person class generated"
check_pattern "baml_client/baml_client/types/Classes.kt" "val email: String?" "Optional field"
check_pattern "baml_client/baml_client/types/Classes.kt" "override fun decode" "decode() method"
check_pattern "baml_client/baml_client/types/Classes.kt" "typeMap: BamlTypeMap" "decode has typeMap param"
check_pattern "baml_client/baml_client/types/Enums.kt" "enum class Sentiment" "Sentiment enum"
check_pattern "baml_client/baml_client/types/Enums.kt" "POSITIVE" "Enum values"
check_pattern "baml_client/baml_client/BamlFunctions.kt" "options: CallOptions? = null" "CallOptions in functions"
check_pattern "baml_client/baml_client/BamlFunctions.kt" "callFunction(\"ExtractPerson\"" "Function call wiring"
check_pattern "baml_client/baml_client/BamlStreamFunctions.kt" "options: CallOptions? = null" "CallOptions in stream"
check_pattern "baml_client/baml_client/BamlParseFunctions.kt" "callFunctionParse" "Parse function call"
check_pattern "baml_client/baml_client/BamlParseFunctions.kt" "\"stream\" to false" "Parse stream=false"
check_pattern "baml_client/baml_client/BamlTypeMap.kt" "\"TYPES\", \"Person\"" "Type map registration"
check_pattern "baml_client/baml_client/types/Classes.kt" "com.boundaryml.baml.cffi.HostValue" "Correct import path (no v1)"

echo ""
if [ $FAIL -eq 0 ]; then
    echo "=== ALL $PASS CHECKS PASSED ==="
else
    echo "=== $FAIL CHECKS FAILED ($PASS passed) ==="
    exit 1
fi
