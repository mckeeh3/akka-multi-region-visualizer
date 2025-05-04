#!/bin/bash
# Script to extract version from pom.xml and write it to src/main/resources/static-resources/version.txt

set -e

# Get the directory of the script (assume run from project root)
PROJECT_ROOT="$(dirname "$0")"
POM_FILE="$PROJECT_ROOT/pom.xml"
TARGET_DIR="$PROJECT_ROOT/src/main/resources/static-resources"
VERSION_FILE="$TARGET_DIR/version.txt"

# Extract version from pom.xml (second occurrence of <version> under <project>)
VERSION=$(grep '<version>' "$POM_FILE" | sed -n '2p' | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')

# Ensure target directory exists
mkdir -p "$TARGET_DIR"

# Write version to file
echo "$VERSION" > "$VERSION_FILE"

echo "Version $VERSION written to $VERSION_FILE"
