#!/bin/bash
# =============================================================================
# AppControl Android APK Build Script
# =============================================================================
#
# Environment Variables:
# ----------------------
# JAVA_HOME      - Java 17+ installation path (auto-detected if not set)
#                  Linux/WSL: /home/user/.local or /usr/lib/jvm/java-17
#                  Windows:   C:\Program Files\Java\jdk-17
#
# GRADLE_ZIP     - Local Gradle distribution zip file (avoids download)
#                  Example: /mnt/d/ProgramData/gradle-8.5-bin.zip
#                  Or use -z/--gradle-zip option
#
# Recommended Setup (WSL):
# ------------------------
# Add to ~/.bashrc:
#   export GRADLE_ZIP=/mnt/d/ProgramData/gradle-8.5-bin.zip
#
# First time setup:
#   # Download Gradle zip (one-time)
#   curl -L -o /mnt/d/ProgramData/gradle-8.5-bin.zip \
#     https://services.gradle.org/distributions/gradle-8.5-bin.zip
#
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default values
BUILD_TYPE="debug"
RUN_TESTS=false
CLEAN_BUILD=false
OUTPUT_DIR=""
APP_OUTPUT_PREFIX="Lessmore"
GRADLE_ZIP="${GRADLE_ZIP:-}"

# =============================================================================
# Helper Functions
# =============================================================================

print_banner() {
    echo -e "${BLUE}"
    echo "============================================"
    echo "    AppControl Android Build Script"
    echo "============================================"
    echo -e "${NC}"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -t, --type <type>      Build type: debug or release (default: debug)"
    echo "  -T, --test             Run unit tests before building"
    echo "  -c, --clean            Perform clean build"
    echo "  -o, --output <dir>     Copy APK to specified directory after build"
    echo "  -z, --gradle-zip <path> Use local Gradle zip file (avoids download)"
    echo "  -h, --help             Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  JAVA_HOME    Java 17+ installation path (auto-detected if not set)"
    echo "  GRADLE_ZIP   Local Gradle zip file path (avoids network download)"
    echo ""
    echo "Examples:"
    echo "  $0                           # Build debug APK"
    echo "  $0 -t release                # Build release APK"
    echo "  $0 -T -c                     # Clean build with tests"
    echo "  $0 -z /path/to/gradle.zip    # Use local Gradle zip"
    echo "  $0 -t debug -o ~/apk         # Build and copy to ~/apk"
    echo ""
    echo "WSL Setup (add to ~/.bashrc):"
    echo "  export GRADLE_ZIP=/mnt/d/ProgramData/gradle-8.5-bin.zip"
    echo ""
    echo "Download Gradle zip (one-time setup):"
    echo "  curl -L -o /mnt/d/ProgramData/gradle-8.5-bin.zip \\"
    echo "    https://services.gradle.org/distributions/gradle-8.5-bin.zip"
}

# =============================================================================
# Environment Detection
# =============================================================================

detect_java_home() {
    is_java_runnable() {
        local java_bin="$1"
        [ -x "$java_bin" ] || return 1
        "$java_bin" -version > /dev/null 2>&1
    }

    # If JAVA_HOME is set and valid, use it
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME:-}/bin/java" ]; then
        if is_java_runnable "${JAVA_HOME}/bin/java"; then
            print_info "Using JAVA_HOME: $JAVA_HOME"
            return 0
        fi
        print_warning "JAVA_HOME java binary is not runnable: ${JAVA_HOME}/bin/java"
    fi

    # If JAVA_HOME is set but invalid (common in WSL with Windows path)
    if [ -n "${JAVA_HOME:-}" ]; then
        print_warning "JAVA_HOME is set but invalid: $JAVA_HOME"
    fi

    # Try to detect Java from PATH
    local java_path
    java_path=$(which java 2>/dev/null || true)

    if [ -n "$java_path" ]; then
        local detected_home
        detected_home=$(readlink -f "$java_path" 2>/dev/null | sed 's|/bin/java||' || true)

        if [ -n "$detected_home" ] && is_java_runnable "$detected_home/bin/java"; then
            export JAVA_HOME="$detected_home"
            print_info "Auto-detected JAVA_HOME: $JAVA_HOME"
            return 0
        fi
    fi

    # Common Java locations
    local common_paths=(
        "/usr/lib/jvm/java-17-openjdk-amd64"
        "/usr/lib/jvm/java-17"
        "/usr/lib/jvm/default-java"
        "/opt/java/openjdk"
        "/usr/java/default"
    )

    for path in "${common_paths[@]}"; do
        if is_java_runnable "$path/bin/java"; then
            export JAVA_HOME="$path"
            print_info "Found Java at: $JAVA_HOME"
            return 0
        fi
    done

    print_error "Java not found. Please install Java 17+ or set JAVA_HOME"
    return 1
}

check_java_version() {
    if [ ! -x "$JAVA_HOME/bin/java" ]; then
        print_error "Java binary not found: $JAVA_HOME/bin/java"
        return 1
    fi

    local java_output
    if ! java_output=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1); then
        print_error "Failed to run Java from JAVA_HOME: $JAVA_HOME"
        return 1
    fi

    local java_major
    java_major=$(echo "$java_output" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')

    if [ -z "$java_major" ]; then
        print_error "Unable to determine Java version from: $java_output"
        return 1
    fi

    if [ "$java_major" -lt 17 ]; then
        print_error "Java 17 or higher is required. Found version: $java_major"
        return 1
    fi

    print_info "Java version: $java_output"
}

check_environment() {
    print_info "Checking build environment..."

    # Check if we're in WSL
    if grep -qi microsoft /proc/version 2>/dev/null; then
        print_info "Detected WSL environment"
        IS_WSL=true
    else
        IS_WSL=false
    fi

    # Detect Java
    detect_java_home || exit 1
    check_java_version || exit 1

    # Check for cmd.exe (Windows)
    if command -v cmd.exe &> /dev/null && cmd.exe /c "echo ok" > /dev/null 2>&1; then
        HAS_CMD=true
    else
        HAS_CMD=false
    fi

    # Check for local Gradle zip
    if [ -n "$GRADLE_ZIP" ] && [ -f "$GRADLE_ZIP" ]; then
        print_info "Will use local Gradle zip: $GRADLE_ZIP"
        HAS_LOCAL_GRADLE_ZIP=true
    else
        HAS_LOCAL_GRADLE_ZIP=false
        if [ -n "$GRADLE_ZIP" ]; then
            print_warning "GRADLE_ZIP set but file not found: $GRADLE_ZIP"
        fi
    fi

    # Check if project directory exists
    if [ ! -f "$PROJECT_ROOT/gradlew" ] && [ ! -f "$PROJECT_ROOT/gradlew.bat" ]; then
        print_error "Gradle wrapper not found. Are you in the correct project directory?"
        exit 1
    fi

    print_success "Environment check passed"
}

# =============================================================================
# Gradle Cache Management
# =============================================================================

get_gradle_dist_dir() {
    # Get the Gradle distribution cache directory
    local dist_dir

    if [ "$IS_WSL" = true ] && [ "$HAS_CMD" = true ]; then
        # Windows Gradle cache
        local win_user
        win_user=$(cmd.exe /c "echo %USERNAME%" 2>/dev/null | tr -d '\r')
        dist_dir="/mnt/c/Users/${win_user}/.gradle/wrapper/dists"
    else
        # Linux Gradle cache
        dist_dir="${HOME}/.gradle/wrapper/dists"
    fi

    echo "$dist_dir"
}

setup_local_gradle_zip() {
    if [ "$HAS_LOCAL_GRADLE_ZIP" = false ]; then
        return 0
    fi

    print_info "Setting up local Gradle zip..."

    # Get Gradle version from wrapper properties
    local gradle_version
    gradle_version=$(grep "distributionUrl" "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | sed 's/.*gradle-\([0-9.]*\)-bin.zip.*/\1/' || true)

    if [ -z "$gradle_version" ]; then
        gradle_version="8.5"
    fi

    # Get the cache directory
    local dist_dir
    dist_dir=$(get_gradle_dist_dir)

    # The hash is based on the download URL
    local gradle_dist_dir="$dist_dir/gradle-${gradle_version}-bin"

    # Find existing hash directory or create one
    local target_dir=""
    if [ -d "$gradle_dist_dir" ]; then
        # Find the hash subdirectory
        target_dir=$(find "$gradle_dist_dir" -maxdepth 1 -type d -name "*" ! -name "gradle-${gradle_version}-bin" 2>/dev/null | head -n 1)
    fi

    if [ -z "$target_dir" ]; then
        # Calculate hash from URL
        local url="https://services.gradle.org/distributions/gradle-${gradle_version}-bin.zip"
        local hash
        hash=$(echo -n "$url" | md5sum | cut -c1-32)

        # Use existing hash if found
        if [ -d "$gradle_dist_dir" ]; then
            local existing_hash
            existing_hash=$(ls "$gradle_dist_dir" 2>/dev/null | grep -v "gradle-${gradle_version}-bin" | head -n 1 || true)
            if [ -n "$existing_hash" ]; then
                hash="$existing_hash"
            fi
        fi

        target_dir="$gradle_dist_dir/$hash"
        mkdir -p "$target_dir"
    fi

    # Copy the zip if not already there
    local target_zip="$target_dir/gradle-${gradle_version}-bin.zip"
    if [ ! -f "$target_zip" ]; then
        print_info "Copying Gradle zip to cache..."
        cp "$GRADLE_ZIP" "$target_zip"
        print_success "Gradle zip cached at: $target_dir"
    else
        print_info "Gradle zip already cached"
    fi
}

# =============================================================================
# Build Functions
# =============================================================================

build_with_windows_gradle() {
    local build_cmd="assemble${BUILD_TYPE^}"

    if [ "$CLEAN_BUILD" = true ]; then
        build_cmd="clean $build_cmd"
    fi

    if [ "$RUN_TESTS" = true ]; then
        build_cmd="$build_cmd test${BUILD_TYPE^}UnitTest"
    fi

    print_info "Running: gradlew.bat $build_cmd"

    cd "$PROJECT_ROOT"
    cmd.exe /c "gradlew.bat $build_cmd"
    local result=$?
    cd - > /dev/null

    return $result
}

build_with_linux_gradle() {
    local build_cmd="assemble${BUILD_TYPE^}"

    if [ "$CLEAN_BUILD" = true ]; then
        build_cmd="clean $build_cmd"
    fi

    if [ "$RUN_TESTS" = true ]; then
        build_cmd="$build_cmd test${BUILD_TYPE^}UnitTest"
    fi

    print_info "Running: ./gradlew $build_cmd"

    cd "$PROJECT_ROOT"

    # Ensure gradlew is executable
    chmod +x gradlew 2>/dev/null || true

    # Run with explicit JAVA_HOME
    JAVA_HOME="$JAVA_HOME" ./gradlew $build_cmd
    local result=$?

    cd - > /dev/null
    return $result
}

copy_apk() {
    local apk_path
    apk_path=$(resolve_apk_path)

    if [ ! -f "$apk_path" ]; then
        print_error "APK not found at: $apk_path"
        return 1
    fi

    if [ -n "$OUTPUT_DIR" ]; then
        mkdir -p "$OUTPUT_DIR"
        cp "$apk_path" "$OUTPUT_DIR/$(basename "$apk_path")"
        print_success "APK copied to: $OUTPUT_DIR/$(basename "$apk_path")"
    fi
}

print_build_summary() {
    local apk_path
    apk_path=$(resolve_apk_path)

    echo ""
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}           BUILD SUCCESSFUL${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""

    if [ -f "$apk_path" ]; then
        local size=$(du -h "$apk_path" | cut -f1)
        local timestamp=$(stat -c %y "$apk_path" 2>/dev/null | cut -d'.' -f1 || stat -f "%Sm" "$apk_path" 2>/dev/null)

        echo "APK Details:"
        echo "  Path:      $apk_path"
        echo "  Size:      $size"
        echo "  Built:     $timestamp"
        echo ""
    fi

    if [ "$RUN_TESTS" = true ]; then
        echo "Test Reports:"
        echo "  $PROJECT_ROOT/app/build/reports/tests/test${BUILD_TYPE^}UnitTest/"
        echo ""
    fi
}

resolve_apk_path() {
    local primary_path="$PROJECT_ROOT/app/build/outputs/apk/$BUILD_TYPE/${APP_OUTPUT_PREFIX}-${BUILD_TYPE}.apk"
    local legacy_path="$PROJECT_ROOT/app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk"

    if [ -f "$primary_path" ]; then
        echo "$primary_path"
        return
    fi

    if [ -f "$legacy_path" ]; then
        echo "$legacy_path"
        return
    fi

    local discovered
    discovered=$(find "$PROJECT_ROOT/app/build/outputs/apk/$BUILD_TYPE" -maxdepth 1 -type f -name "*.apk" 2>/dev/null | head -n 1)
    if [ -n "$discovered" ]; then
        echo "$discovered"
    else
        echo "$primary_path"
    fi
}

# =============================================================================
# Main
# =============================================================================

main() {
    print_banner

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--type)
                BUILD_TYPE="$2"
                shift 2
                ;;
            -T|--test)
                RUN_TESTS=true
                shift
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -o|--output)
                OUTPUT_DIR="$2"
                shift 2
                ;;
            -z|--gradle-zip)
                GRADLE_ZIP="$2"
                shift 2
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done

    # Validate build type
    if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
        print_error "Invalid build type: $BUILD_TYPE. Must be 'debug' or 'release'."
        exit 1
    fi

    check_environment

    # Setup local Gradle zip if specified
    setup_local_gradle_zip

    # Build
    print_info "Starting $BUILD_TYPE build..."

    local build_result=0

    if [ "$IS_WSL" = true ] && [ "$HAS_CMD" = true ]; then
        build_with_windows_gradle || build_result=$?
    else
        build_with_linux_gradle || build_result=$?
    fi

    if [ $build_result -eq 0 ]; then
        copy_apk
        print_build_summary
    else
        print_error "Build failed!"
        exit 1
    fi
}

main "$@"
