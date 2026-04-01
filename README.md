# LessMore - App Usage Controller

一款 Android 应用使用控制工具，帮助用户管理和限制手机应用的使用时长与使用时间段。

## 功能特性

- **📊 目标应用管理** - 选择需要控制的应用，支持白名单功能
- **⏰ 每日时长限制** - 为每个应用设置每日使用上限（1-1440分钟），达到80%时发送提醒通知
- **📅 允许时间段** - 设置允许使用应用的时间窗口（如 08:00-12:00）
- **🔒 锁定界面** - 超出限制时显示锁定界面，说明锁定原因和剩余时间
- **📈 使用统计** - 查看每日/每周使用统计，支持图表展示
- **🔐 密码保护** - 4位以上管理密码保护所有设置
- **👆 生物识别** - 支持指纹/面部识别快速验证
- **🛡️ 强制锁定模式** - 高优先级锁定，防止绕过（需设备管理器权限）

## 截图

*(待添加)*

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 8.0 (API 26)
- **目标 SDK**: Android 14 (API 34)
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **数据库**: Room
- **UI**: Jetpack Compose + Material Design 3
- **图表**: Vico
- **安全存储**: EncryptedSharedPreferences + Android Keystore
- **测试**: Kotest

## 项目结构

```
app/src/main/java/com/appcontrol/
├── data/                  # 数据层
│   ├── db/                # Room 数据库
│   ├── preferences/       # 加密存储
│   └── repository/        # 仓库实现
├── domain/                # 领域层
│   ├── model/             # 领域模型
│   ├── repository/        # 仓库接口
│   └── usecase/           # 用例
├── presentation/          # 表现层
│   ├── theme/             # 主题
│   ├── ui/                # 界面
│   └── viewmodel/         # 视图模型
└── service/               # 系统服务
    ├── monitor/           # 监控服务
    ├── overlay/           # 锁定悬浮窗
    └── receiver/          # 广播接收器
```

## 构建说明

### 环境要求

- Java 17+
- Android SDK (API 34)
- WSL 环境或 Linux/macOS/Windows

### 构建命令

使用项目提供的构建脚本（推荐）：

```bash
cd scripts

# 构建 debug APK
./build.sh

# 构建 release APK
./build.sh -t release

# 运行测试
./build.sh -T

# 清理构建
./build.sh -c

# 查看帮助
./build.sh -h
```

或使用 Gradle 直接构建：

```bash
./gradlew assembleDebug      # Debug 构建
./gradlew assembleRelease    # Release 构建
./gradlew testDebugUnitTest  # 运行单元测试
```

### APK 输出

构建完成后，APK 位于：
```
app/build/outputs/apk/<build_type>/Lessmore-<build_type>.apk
```

## 所需权限

| 权限 | 用途 | 必需 |
|------|------|------|
| `PACKAGE_USAGE_STATS` | 获取前台应用 | 是 |
| `SYSTEM_ALERT_WINDOW` | 显示锁定悬浮窗 | 是 |
| `FOREGROUND_SERVICE` | 后台监控服务 | 是 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 | 是 |
| `USE_BIOMETRIC` | 生物识别验证 | 可选 |
| `POST_NOTIFICATIONS` | 警告通知 | 可选 |
| Device Admin | 强制锁定/防卸载 | 可选 |

## 开发指南

详细的开发指南请参考 [CLAUDE.md](CLAUDE.md)。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

[MIT License](LICENSE)

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Hilt](https://dagger.dev/hilt/)
- [Room](https://developer.android.com/training/data-storage/room)
- [Vico](https://patrykandpatrick.com/vico/wiki)
- [Kotest](https://kotest.io/)
