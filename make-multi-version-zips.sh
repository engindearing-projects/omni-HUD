#!/bin/bash
# OmniHUD Multi-Version Pipeline Submission Zip Creator
# Creates clean zips for TAK third-party pipeline submission for multiple ATAK versions
# Usage: ./make-multi-version-zips.sh

set -e

echo "=================================================="
echo "OmniHUD Multi-Version Pipeline Submission Creator"
echo "=================================================="
echo ""

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Check if we're in a git repo
if [ ! -d ".git" ]; then
    echo "ERROR: Not in a git repository!"
    exit 1
fi

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo "WARNING: You have uncommitted changes!"
    echo "It's recommended to commit your changes before creating submission zips"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Define versions to build
VERSIONS=("5.3.0" "5.4.0" "5.5.1")
OUTPUT_DIR="$SCRIPT_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "Creating submission zips for ATAK versions: ${VERSIONS[@]}"
echo "Output directory: $OUTPUT_DIR"
echo ""

# Store the current version
CURRENT_VERSION=$(grep "ext.ATAK_VERSION" app/build.gradle | sed 's/.*"\(.*\)".*/\1/')
echo "Current ATAK version in build.gradle: $CURRENT_VERSION"
echo ""

# Function to create zip for a specific version
create_version_zip() {
    local VERSION=$1
    local ZIP_NAME="omni-HUD-ATAK${VERSION}-pipeline-${TIMESTAMP}.zip"
    local DEST_PATH="$OUTPUT_DIR/$ZIP_NAME"

    echo "=================================================="
    echo "Creating zip for ATAK $VERSION"
    echo "=================================================="

    # Create a temporary branch
    local TEMP_BRANCH="temp-build-${VERSION}-${TIMESTAMP}"
    echo "  Creating temporary branch: $TEMP_BRANCH..."
    git checkout -b "$TEMP_BRANCH" HEAD >/dev/null 2>&1

    # Update build.gradle with the target version
    echo "  Setting ATAK_VERSION to $VERSION..."
    sed -i.bak "s/ext.ATAK_VERSION = \"[^\"]*\"/ext.ATAK_VERSION = \"${VERSION}\"/" app/build.gradle
    rm app/build.gradle.bak

    # Commit the change
    git add app/build.gradle
    git commit -m "Set ATAK version to ${VERSION} for pipeline submission" >/dev/null 2>&1

    # Create zip with git archive from current branch
    echo "  Creating archive..."
    git archive --format=zip --prefix=omni-HUD/ --output="$DEST_PATH" HEAD

    # Return to main branch and delete temp branch
    git checkout main >/dev/null 2>&1
    git branch -D "$TEMP_BRANCH" >/dev/null 2>&1

    # Verify the zip
    if [ ! -f "$DEST_PATH" ]; then
        echo "ERROR: Failed to create zip file!"
        return 1
    fi

    # Check that local.properties is NOT in the zip
    if unzip -l "$DEST_PATH" 2>/dev/null | grep -q " local\.properties$"; then
        echo "ERROR: local.properties found in zip!"
        rm "$DEST_PATH"
        return 1
    fi

    # Get file size
    SIZE=$(du -h "$DEST_PATH" | cut -f1)

    echo "  ✅ Created: $ZIP_NAME ($SIZE)"
    echo ""
}

# Create zips for each version
for VERSION in "${VERSIONS[@]}"; do
    create_version_zip "$VERSION"
done

echo "=================================================="
echo "✅ ALL ZIPS CREATED SUCCESSFULLY!"
echo "=================================================="
echo ""
echo "Created files:"
ls -lh omni-HUD-ATAK*-pipeline-${TIMESTAMP}.zip 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
echo ""
echo "Next steps:"
echo "  1. Go to https://tak.gov/third-party-plugins"
echo "  2. Upload each zip file for the corresponding ATAK version"
echo "  3. Wait for build artifacts (~5-10 minutes per version)"
echo ""
echo "Build will use TAK's official signing keystore"
echo ""
