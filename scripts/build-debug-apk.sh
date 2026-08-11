#!/bin/bash

# Debug APK build and install script
# Builds the debug package and installs it to a connected Android device

set -e  # Exit immediately on error

echo "================================================"
echo "  ACC Settings - Debug APK build and install"
echo "================================================"
echo ""

# Color definitions
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check that adb is available
if ! command -v adb &> /dev/null; then
    echo -e "${RED}Error: adb not found. Ensure Android SDK platform-tools is on PATH${NC}"
    exit 1
fi

# Check for a connected device
echo "Checking device connection..."
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${YELLOW}Warning: no connected device detected${NC}"
    echo "Please ensure:"
    echo "  1. The device is connected via USB"
    echo "  2. USB debugging is enabled on the device"
    echo "  3. This computer is authorized for debugging"
    echo ""
    read -p "Continue building APK (without installing)? (y/n): " CONTINUE_BUILD
    if [[ ! "$CONTINUE_BUILD" =~ ^[Yy]$ ]]; then
        echo "Operation cancelled"
        exit 0
    fi
    INSTALL_FLAG=false
else
    echo -e "${GREEN}Detected $DEVICE_COUNT connected device(s)${NC}"
    adb devices -l
    echo ""
    INSTALL_FLAG=true
fi

# Clean previous build
echo "Cleaning previous build files..."
./gradlew clean

# Build Debug APK
echo ""
echo "Building Debug APK..."
echo "================================================"
./gradlew assembleDebug

# Check whether the build succeeded
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Debug APK built successfully!${NC}"
    echo ""
else
    echo ""
    echo -e "${RED}✗ Debug APK build failed${NC}"
    exit 1
fi

# APK file path
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Check that the APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: APK file not found: $APK_PATH${NC}"
    exit 1
fi

# Display APK info
echo "APK info:"
echo "  Path: $(pwd)/$APK_PATH"
echo "  Size: $(du -h "$APK_PATH" | cut -f1)"
echo "  Modified: $(stat -c %y "$APK_PATH" 2>/dev/null || stat -f "%Sm" "$APK_PATH")"
echo ""

# Install to device
if [ "$INSTALL_FLAG" = true ]; then
    echo "================================================"
    echo "Installing to device..."
    echo "================================================"

    # Uninstall the old version if present
    PACKAGE_NAME="app.owlow.accsettings"
    echo "Checking for an old version..."
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        echo "Old version detected, uninstalling..."
        adb uninstall "$PACKAGE_NAME"
        echo -e "${GREEN}✓ Old version uninstalled${NC}"
    else
        echo "No old version detected"
    fi

    # Install the new version
    echo ""
    echo "Installing new version..."
    adb install "$APK_PATH"

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ APK installed successfully!${NC}"
        echo ""
        echo "Launching app..."
        adb shell am start -n "$PACKAGE_NAME/.SettingsActivity"
        echo ""
        echo -e "${GREEN}================================================${NC}"

        echo -e "${GREEN}✓ All done!${NC}"
        echo -e "${GREEN}================================================${NC}"
    else
        echo ""
        echo -e "${RED}✗ APK installation failed${NC}"
        echo "Possible causes:"
        echo "  - Not enough device storage"
        echo "  - Device permission issues"
        echo "  - APK signature mismatch"
        exit 1
    fi
else
    echo -e "${GREEN}================================================${NC}"
    echo -e "${GREEN}✓ APK build complete!${NC}"
    echo -e "${GREEN}================================================${NC}"
    echo ""
    echo "APK located at: $(pwd)/$APK_PATH"
    echo ""
    echo "Manual install command:"
    echo "  adb install $APK_PATH"
fi
