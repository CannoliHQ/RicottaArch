#!/bin/bash
# Prepare the RetroArch submodule for RicottaArch builds.
# Applies patches, copies native sources, and removes files we replace.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
RA_DIR="$ROOT_DIR/retroarch"
PATCH_DIR="$ROOT_DIR/patches"
RICOTTA_DIR="$ROOT_DIR/ricotta"

cd "$RA_DIR"

# Apply all patches (idempotent)
echo "Applying patches..."
for patch in "$PATCH_DIR"/*.patch; do
    name="$(basename "$patch")"
    if git apply --check "$patch" 2>/dev/null; then
        git apply "$patch"
        echo "  ✓ $name"
    else
        echo "  - $name (already applied)"
    fi
done

# Remove upstream RetroActivityFuture.java — we replace it with Kotlin
rm -f "$RA_DIR/pkg/android/phoenix/src/com/retroarch/browser/retroactivity/RetroActivityFuture.java"
echo "Removed upstream RetroActivityFuture.java (replaced by Kotlin)"

# Copy bridge source into RetroArch's JNI directory
cp "$RICOTTA_DIR/jni/ricotta_bridge.c" "$RA_DIR/pkg/android/phoenix-common/jni/ricotta_bridge.c"
echo "Copied ricotta_bridge.c"

# Ensure Android.mk includes our bridge file and define
ANDROID_MK="$RA_DIR/pkg/android/phoenix-common/jni/Android.mk"
if ! grep -q "HAVE_RICOTTA_IGM" "$ANDROID_MK"; then
    sed -i '' 's|griffin/griffin_cpp.cpp|griffin/griffin_cpp.cpp \\\
							ricotta_bridge.c|' "$ANDROID_MK"
    sed -i '' '/^LOCAL_MODULE := retroarch-activity/a\
\
DEFINES += -DHAVE_RICOTTA_IGM' "$ANDROID_MK"
    echo "Updated Android.mk"
else
    echo "Android.mk already configured."
fi

echo "Ready to build."
