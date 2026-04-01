# 技术设计文档：App使用控制器

## 概述（Overview）

App使用控制器是一款安卓原生应用，旨在帮助用户管理和限制手机应用的使用时长与使用时间段。系统通过前台服务持续监控目标应用的使用状态，在超出限制时以悬浮窗形式锁定应用，并提供使用统计、密码保护、生物识别验证和强制锁定等功能。

### 技术栈

- 语言：Kotlin
- 最低 SDK：API 26 (Android 8.0)
- 目标 SDK：API 34 (Android 14)
- 架构模式：MVVM + Clean Architecture
- 依赖注入：Hilt
- 本地数据库：Room
- UI 框架：Jetpack Compose + Material Design 3
- 异步处理：Kotlin Coroutines + Flow
- 图表库：Vico (Compose 原生图表库)
- 生物识别：AndroidX Biometric (BiometricPrompt)
- 加密存储：Android Keystore + EncryptedSharedPreferences
- 属性测试：Kotest Property Testing

## 架构（Architecture）

系统采用分层架构，分为表现层、领域层和数据层，通过依赖注入实现松耦合。

```mermaid
graph TB
    subgraph 表现层 Presentation
        UI[Jetpack Compose UI<br/>Material Design 3]
        VM[ViewModels]
    end

    subgraph 领域层 Domain
        UC[Use Cases]
        REPO_IF[Repository Interfaces]
    end

    subgraph 数据层 Data
        REPO[Repository Implementations]
        DB[(Room Database)]
        PREFS[EncryptedSharedPreferences]
        KS[Android Keystore]
    end

    subgraph 系统服务层 System Services
        FGS[前台监控服务 MonitorService]
        OVL[悬浮窗管理 OverlayManager]
        BR[广播接收器 BootReceiver]
        DA[设备管理器 DeviceAdminReceiver]
    end

    UI --> VM
    VM --> UC
    UC --> REPO_IF
    REPO_IF -.-> REPO
    REPO --> DB
    REPO --> PREFS
    REPO --> KS
    FGS --> UC
    OVL --> FGS
    BR --> FGS
```

### 核心监控流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant MS as MonitorService
    participant UC as UseCases
    participant DB as Room DB
    participant OVL as OverlayManager

    MS->>MS: 每秒轮询前台应用
    MS->>UC: 检查当前前台应用
    UC->>DB: 查询应用规则(Usage_Limit, Allowed_Period)
    DB-->>UC: 返回规则
    UC->>DB: 查询当日累计使用时长
    DB-->>UC: 返回使用时长
    UC->>UC: 判断是否需要锁定
    alt 需要锁定
        UC->>OVL: 显示 Lock_Screen
        OVL->>User: 展示锁定界面
    else 不需要锁定
        UC->>DB: 更新累计使用时长(+1秒)
    end
```

