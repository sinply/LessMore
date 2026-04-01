#!/bin/bash
# =============================================================================
# AppControl Android APK Build Script
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
    echo "  -h, --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                     # Build debug APK"
    echo "  $0 -t release          # Build release APK"
    echo "  $0 -T -c               # Clean build with tests"
    echo "  $0 -t debug -o ~/apk   # Build and copy to ~/apk"
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

    # Check for cmd.exe (Windows) and ensure it's executable in current sandbox
    if command -v cmd.exe &> /dev/null && cmd.exe /c "echo ok" > /dev/null 2>&1; then
        HAS_CMD=true
    else
        HAS_CMD=false
        if [ "$IS_WSL" = true ]; then
            print_warning "cmd.exe unavailable in current environment, fallback to Linux Gradle"
        fi
    fi

    # Check Java
    if command -v java &> /dev/null; then
        local java_output
        java_output=$(java -version 2>&1 | head -n 1 || true)
        print_info "Java version: $java_output"

        if echo "$java_output" | grep -qi "permission denied"; then
            print_warning "System java is not executable in current environment"
        else
            local java_major
            java_major=$(echo "$java_output" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')
            if [ -n "$java_major" ] && [ "$java_major" -lt 17 ]; then
                print_error "Java 17 or higher is required"
                exit 1
            fi
        fi
    else
        print_error "Java not found. Please install Java 17 or higher."
        exit 1
    fi

    # Check if project directory exists
    if [ ! -f "$PROJECT_ROOT/gradlew" ] && [ ! -f "$PROJECT_ROOT/gradlew.bat" ]; then
        print_error "Gradle wrapper not found. Are you in the correct project directory?"
        exit 1
    fi

    print_success "Environment check passed"
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
    if [ -n "${JAVA_HOME:-}" ] && [ ! -x "$JAVA_HOME/bin/java" ]; then
        print_warning "JAVA_HOME is invalid: $JAVA_HOME, unset it for Linux Gradle"
        unset JAVA_HOME
    fi
    chmod +x gradlew
    ./gradlew $build_cmd
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
    discovered=$(find "$PROJECT_ROOT/app/build/outputs/apk/$BUILD_TYPE" -maxdepth 1 -type f -name "*.apk" | head -n 1)
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

    # Build
    print_info "Starting $BUILD_TYPE build..."

    if [ "$IS_WSL" = true ] && [ "$HAS_CMD" = true ]; then
        build_with_windows_gradle
    else
        build_with_linux_gradle
    fi

    if [ $? -eq 0 ]; then
        copy_apk
        print_build_summary
    else
        print_error "Build failed!"
        exit 1
    fi
}

main "$@"
