# AppControl Android Build Script

Android APK 构建脚本，支持自动化测试和构建流程。

## 环境要求

- **Java 17+** - 确保已安装 Java 17 或更高版本
- **WSL 环境** - 脚本会自动检测 WSL 并使用 Windows Gradle
- **Android SDK** - 已配置 ANDROID_HOME 环境变量

## 快速开始

```bash
# 进入脚本目录
cd scripts

# 构建 debug APK
./build.sh

# 查看帮助
./build.sh -h
```

## 命令选项

| 选项 | 简写 | 说明 | 默认值 |
|------|------|------|--------|
| `--type <type>` | `-t` | 构建类型: debug 或 release | debug |
| `--test` | `-T` | 构建前运行单元测试 | 否 |
| `--clean` | `-c` | 执行清理构建 | 否 |
| `--output <dir>` | `-o` | 构建完成后复制 APK 到指定目录 | - |
| `--help` | `-h` | 显示帮助信息 | - |

## 使用示例

### 基础构建

```bash
# 构建 debug APK
./build.sh

# 构建 release APK
./build.sh -t release
```

### 清理构建

```bash
# 清理后重新构建 debug APK
./build.sh -c

# 清理后构建 release APK
./build.sh -c -t release
```

### 运行测试

```bash
# 运行单元测试并构建
./build.sh -T

# 清理 + 测试 + 构建
./build.sh -c -T
```

### 指定输出目录

```bash
# 构建 APK 并复制到指定目录
./build.sh -o ~/Desktop/apk

# 完整构建流程
./build.sh -c -T -t debug -o ~/Desktop/apk
```

## 输出说明

### APK 位置

构建完成后，APK 文件位于:

```
app/build/outputs/apk/<build_type>/app-<build_type>.apk
```

例如:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### 测试报告

使用 `-T` 选项运行测试后，测试报告位于:

```
app/build/reports/tests/testDebugUnitTest/
```

在浏览器中打开 `index.html` 查看详细测试报告。

## 构建输出示例

```
============================================
    AppControl Android Build Script
============================================

[INFO] Checking build environment...
[INFO] Detected WSL environment
[INFO] Java version: java version "17.0.10"
[SUCCESS] Environment check passed
[INFO] Starting debug build...
[INFO] Running: gradlew.bat assembleDebug

BUILD SUCCESSFUL in 1m 24s

============================================
           BUILD SUCCESSFUL
============================================

APK Details:
  Path:      /path/to/app/build/outputs/apk/debug/app-debug.apk
  Size:      11M
  Built:     2026-03-31 22:40:00
```

## WSL 环境说明

脚本会自动检测运行环境:
- **WSL + Windows Java**: 使用 `gradlew.bat` 通过 `cmd.exe` 构建
- **原生 Linux**: 使用 `./gradlew` 构建

## 常见问题

### 1. Java 版本不匹配

```
[ERROR] Java 17 or higher is required
```

**解决方案**: 安装 Java 17 或更高版本，并确保 `java` 命令可用。

### 2. Gradle wrapper 权限问题

```
./build.sh: line 1: ./gradlew: Permission denied
```

**解决方案**: 赋予执行权限

```bash
chmod +x gradlew
```

### 3. ANDROID_HOME 未配置

构建可能失败或警告缺少 Android SDK。

**解决方案**:

```bash
# 在 ~/.bashrc 或 ~/.zshrc 中添加
export ANDROID_HOME=/mnt/d/ProgramData/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### 4. WSL 中 Gradle 无法启动

确保 Windows Java 路径可访问:

```bash
ls -la "/mnt/c/Program Files/Java/jdk-17/bin/java.exe"
```

## 文件结构

```
scripts/
├── build.sh      # 构建脚本
└── README.md     # 使用说明
```

## 集成到 CI/CD

可以将脚本集成到持续集成流程:

```yaml
# GitHub Actions 示例
- name: Build APK
  run: |
    cd scripts
    ./build.sh -c -T -t debug
```

## 许可证

MIT License
