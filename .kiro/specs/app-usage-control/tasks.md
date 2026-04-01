# 实现计划：App使用控制器

## 概述

基于 MVVM + Clean Architecture 架构，使用 Kotlin + Jetpack Compose + Material Design 3 实现 App 使用控制器。按照数据层 → 领域层 → 系统服务层 → 表现层的顺序逐步构建，确保每一步都可增量验证。

## 任务列表

- [x] 1. 项目初始化与基础架构搭建
  - [x] 1.1 创建 Android 项目并配置 Gradle 依赖
    - 配置 Kotlin、Jetpack Compose、Material Design 3、Hilt、Room、Vico、AndroidX Biometric、EncryptedSharedPreferences、Kotest 等依赖
    - 设置 minSdk=26, targetSdk=34, compileSdk=34
    - 配置 Hilt 插件和 KSP/KAPT 注解处理器
    - _需求: 6.1, 6.3_

  - [x] 1.2 创建项目包结构和基础模块
    - 创建 `data/`（db, repository, preferences）、`domain/`（model, usecase, repository）、`presentation/`（ui, viewmodel, theme）、`service/`（monitor, overlay, receiver）包结构
    - 创建 Hilt Application 类并在 AndroidManifest.xml 中注册
    - 配置 Material Design 3 主题（AppTheme.kt），包含 Dynamic Color 支持
    - _需求: 7.1, 7.2_

- [x] 2. 数据层 - Room 数据库与数据模型
  - [x] 2.1 定义领域模型和 Room Entity
    - 创建 `TargetApp` 实体（packageName, appName, iconUri, isWhitelisted, usageLimitMinutes, createdAt）
    - 创建 `AllowedPeriod` 实体（id, packageName, startTime, endTime）
    - 创建 `UsageRecord` 实体（id, packageName, date, usageDurationSeconds, openCount）
    - 创建 `AppSettings` 实体（passwordHash, biometricEnabled, forcedLockEnabled, lockoutUntil, failedAttempts）
    - _需求: 1.1, 1.4, 2.1, 3.1, 5.1, 7.1, 7.2, 8.1, 8.6_

  - [x] 2.2 创建 Room DAO 接口
    - `TargetAppDao`：增删改查目标应用、白名单查询、按包名查询
    - `AllowedPeriodDao`：按应用增删改查时间段
    - `UsageRecordDao`：按日期和包名查询/更新使用记录、查询周统计、清理30天前数据
    - `AppSettingsDao`：读写应用设置
    - _需求: 1.2, 1.3, 2.1, 3.1, 5.1, 5.4, 7.1, 7.2_

  - [x] 2.3 创建 Room Database 类和数据库迁移配置
    - 创建 `AppControlDatabase` 抽象类，包含所有 DAO
    - 配置 Hilt Module 提供 Database 和 DAO 单例
    - _需求: 7.1, 7.2, 7.3_

  - [ ]* 2.4 编写数据模型属性测试
    - **属性 1: 往返一致性 - TargetApp 实体保存后重新加载应与原始对象等价**
    - **验证: 需求 7.4**

- [x] 3. 数据层 - Repository 实现
  - [x] 3.1 定义 Repository 接口（领域层）
    - `AppRepository`：管理目标应用和白名单的增删改查
    - `RuleRepository`：管理 UsageLimit 和 AllowedPeriod 的增删改查
    - `UsageRepository`：管理使用记录的查询和更新
    - `AuthRepository`：管理密码哈希存储、验证、生物识别开关、锁定状态
    - _需求: 1.1-1.4, 2.1, 3.1, 5.1, 7.1, 8.1-8.6_

  - [x] 3.2 实现 Repository（数据层）
    - `AppRepositoryImpl`：基于 Room DAO 实现，使用 Flow 暴露响应式数据
    - `RuleRepositoryImpl`：基于 Room DAO 实现
    - `UsageRepositoryImpl`：基于 Room DAO 实现，包含30天数据清理逻辑
    - `AuthRepositoryImpl`：基于 EncryptedSharedPreferences + Android Keystore 实现密码哈希存储和生物识别密钥管理
    - 配置 Hilt Module 绑定 Repository 接口到实现
    - _需求: 1.1-1.4, 2.1, 3.1, 5.1, 5.4, 7.1-7.3, 8.1, 8.6, 9.9_

- [x] 4. 领域层 - Use Cases 实现
  - [x] 4.1 实现应用管理 Use Cases
    - `GetInstalledAppsUseCase`：获取设备已安装应用列表（排除系统应用）
    - `AddTargetAppUseCase`：添加目标应用到受控列表
    - `RemoveTargetAppUseCase`：移除目标应用并解除所有限制
    - `ToggleWhitelistUseCase`：切换应用白名单状态
    - _需求: 1.1, 1.2, 1.3, 1.4_

  - [x] 4.2 实现规则管理 Use Cases
    - `SetUsageLimitUseCase`：设置每日使用时长限制（1-1440分钟校验）
    - `ManageAllowedPeriodsUseCase`：增删改允许使用时间段
    - _需求: 2.1, 3.1, 3.5_

  - [x] 4.3 实现监控判定 Use Cases
    - `CheckAppLockStatusUseCase`：综合判断目标应用是否需要锁定（检查白名单、UsageLimit、AllowedPeriod）
    - `UpdateUsageUseCase`：更新应用累计使用时长（+1秒）
    - `ResetDailyUsageUseCase`：重置所有应用当日使用时长（00:00触发）
    - `CheckUsageWarningUseCase`：检查是否达到80%阈值需发送提醒通知
    - _需求: 2.2, 2.3, 2.4, 3.2, 3.3, 3.4, 4.4_

  - [x] 4.4 实现身份验证 Use Cases
    - `SetPasswordUseCase`：设置管理密码（最少4位，SHA-256哈希存储）
    - `VerifyPasswordUseCase`：验证密码，管理连续失败次数和15分钟锁定
    - `ChangePasswordUseCase`：修改密码（需先验证旧密码）
    - `ToggleBiometricUseCase`：启用/禁用生物识别（需先通过身份验证）
    - `CheckBiometricAvailabilityUseCase`：检查设备生物识别能力
    - _需求: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 9.1, 9.6, 9.8_

  - [x] 4.5 实现统计查询 Use Cases
    - `GetDailyStatsUseCase`：获取每日使用统计（时长+打开次数）
    - `GetWeeklyStatsUseCase`：获取过去7天每日总使用时长
    - `CleanOldDataUseCase`：清理超过30天的历史数据
    - _需求: 5.1, 5.2, 5.3, 5.4_

- [x] 5. 检查点 - 数据层和领域层验证
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 6. 系统服务层 - 前台监控服务
  - [x] 6.1 实现 MonitorService 前台服务
    - 创建 `MonitorService` 继承 `LifecycleService`，配置前台通知（Notification Channel）
    - 实现每秒轮询逻辑：通过 `UsageStatsManager` 获取当前前台应用包名
    - 调用 `CheckAppLockStatusUseCase` 判断是否需要锁定
    - 调用 `UpdateUsageUseCase` 更新使用时长
    - 调用 `CheckUsageWarningUseCase` 在达到80%时发送提醒通知
    - 实现午夜重置定时器（AlarmManager 或 WorkManager）
    - 实现服务被杀后自动重启（START_STICKY + onTaskRemoved）
    - _需求: 2.2, 2.3, 2.4, 3.2, 3.3, 3.4, 4.4, 6.3, 10.4_

  - [x] 6.2 实现 BootReceiver 开机自启
    - 创建 `BootReceiver` 监听 `BOOT_COMPLETED` 广播
    - 在 `onReceive` 中启动 `MonitorService`
    - 在 AndroidManifest.xml 中注册 receiver 和权限
    - _需求: 6.4_

- [ ] 7. 系统服务层 - 悬浮窗锁定界面
  - [x] 7.1 实现 LockOverlayManager 悬浮窗管理
    - 使用 `WindowManager` + `TYPE_APPLICATION_OVERLAY` 创建全屏悬浮窗
    - 实现 Compose 锁定界面渲染（Material Design 3 风格）
    - 根据锁定原因显示不同内容：
      - Usage_Limit 触发：显示"今日使用时长已达上限"+ 已使用时长
      - Allowed_Period 触发：显示"当前时间段不允许使用"+ 下一个允许时间段
    - 非强制锁定模式下提供"返回桌面"按钮
    - 强制锁定模式下仅显示锁定原因和剩余锁定时间，无退出按钮
    - _需求: 4.1, 4.2, 4.3, 10.1, 10.7_

  - [ ] 7.2 实现强制锁定模式防绕过机制
    - 悬浮窗设置 `FLAG_NOT_FOCUSABLE` 取消 + `FLAG_LAYOUT_IN_SCREEN` 实现全屏拦截
    - 拦截返回键、Home 键、最近任务键（通过 `onKeyEvent` + `TYPE_APPLICATION_OVERLAY` 最高层级）
    - 监控目标应用进程状态，强制停止后重新打开时立即重新显示锁定界面
    - 服务被杀后自动重启并恢复锁定状态
    - _需求: 10.1, 10.2, 10.3, 10.4_

- [ ] 8. 系统服务层 - 设备管理器与防卸载
  - [~] 8.1 实现 DeviceAdminReceiver
    - 创建 `AppDeviceAdminReceiver` 继承 `DeviceAdminReceiver`
    - 在 `onDisableRequested` 中要求身份验证
    - 创建 `device_admin.xml` 策略文件
    - 在 AndroidManifest.xml 中注册
    - _需求: 8.7, 10.5, 10.6_

- [~] 9. 检查点 - 系统服务层验证
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 10. 表现层 - 权限引导与密码设置流程
  - [~] 10.1 实现权限引导页面（OnboardingScreen）
    - Material Design 3 风格的分步引导界面
    - 检查并引导授予：UsageStats 权限、悬浮窗权限、设备管理器权限
    - 每个权限提供说明文字和跳转系统设置的按钮
    - 权限状态实时检测和 UI 更新
    - _需求: 6.1, 6.2, 10.6_

  - [~] 10.2 实现密码设置页面（SetPasswordScreen）
    - 首次使用时引导设置至少4位管理密码
    - 密码输入框 + 确认输入框 + 强度提示
    - 设置完成后提供启用生物识别的选项（设备支持时）
    - 调用 BiometricPrompt API 注册生物识别
    - _需求: 8.1, 9.1, 9.2_

  - [~] 10.3 实现身份验证对话框（AuthDialog）
    - 可复用的身份验证 Composable 组件
    - 生物识别已启用时优先展示 BiometricPrompt
    - 提供"使用密码验证"备选入口
    - 生物识别失败3次自动切换到密码验证
    - 密码连续错误5次锁定15分钟，显示倒计时
    - 设备不支持或生物识别不可用时隐藏相关选项
    - _需求: 8.2, 8.3, 8.4, 9.3, 9.4, 9.5, 9.6, 9.7_

- [ ] 11. 表现层 - 主界面与应用管理
  - [~] 11.1 实现主界面（MainScreen）与底部导航
    - Material Design 3 NavigationBar 底部导航：应用管理、使用统计、设置
    - 主界面顶部显示权限缺失警告条（如有）
    - 监控服务状态指示器
    - _需求: 6.2_

  - [~] 11.2 实现应用管理页面（AppListScreen）
    - 已安装应用列表（LazyColumn），显示应用图标、名称、包名
    - 搜索过滤功能
    - 每个应用项显示：受控状态开关、白名单标记、已设置的限制摘要
    - 点击应用进入规则设置页面
    - 添加/移除目标应用操作（需身份验证）
    - _需求: 1.1, 1.2, 1.3, 1.4, 8.2_

  - [~] 11.3 实现应用规则设置页面（AppRuleScreen）
    - 使用时长限制设置：Slider + 数字输入（1-1440分钟）
    - 允许时间段管理：时间段列表 + 添加/编辑/删除时间段
    - TimePicker 选择开始和结束时间
    - 实时预览当前规则摘要
    - 修改规则需先通过身份验证
    - _需求: 2.1, 3.1, 3.5, 8.2_

- [ ] 12. 表现层 - 使用统计页面
  - [~] 12.1 实现每日统计视图（DailyStatsScreen）
    - Material Design 3 Card 列表展示每个目标应用当天的使用时长和打开次数
    - 使用时长格式化显示（X小时Y分钟）
    - 按使用时长降序排列
    - 日期选择器切换查看不同日期
    - _需求: 5.1, 5.2_

  - [~] 12.2 实现每周统计视图（WeeklyStatsScreen）
    - 使用 Vico 图表库绘制柱状图，展示过去7天每天的总使用时长
    - Material Design 3 配色方案
    - 点击柱状图可查看当天详细统计
    - _需求: 5.3_

- [ ] 13. 表现层 - 设置页面
  - [~] 13.1 实现设置页面（SettingsScreen）
    - 修改管理密码入口（需验证当前密码）
    - 生物识别开关（设备支持时显示，操作需身份验证）
    - 强制锁定模式开关
    - 设备管理器权限状态和引导
    - 关于页面 / 版本信息
    - _需求: 8.5, 9.8, 10.6_

- [~] 14. 检查点 - 表现层验证
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 15. 集成与整体联调
  - [~] 15.1 连接所有组件并配置导航
    - 配置 Jetpack Navigation Compose 路由（Onboarding → SetPassword → Main → AppRule → Auth 等）
    - 在 MainActivity 中根据首次使用状态决定起始页面
    - 将 MonitorService 的启动/停止与 UI 开关绑定
    - 确保 Hilt 依赖注入在所有层正确连接
    - _需求: 6.3, 7.3, 8.1_

  - [~] 15.2 实现 AndroidManifest.xml 完整配置
    - 声明所有必要权限：FOREGROUND_SERVICE, SYSTEM_ALERT_WINDOW, RECEIVE_BOOT_COMPLETED, PACKAGE_USAGE_STATS, USE_BIOMETRIC 等
    - 注册所有 Service、Receiver、Activity
    - 配置 Hilt Application
    - _需求: 6.1, 6.3, 6.4_

  - [ ]* 15.3 编写关键流程集成测试
    - 测试：添加目标应用 → 设置限制 → 监控服务检测 → 触发锁定的完整流程
    - 测试：密码验证 → 生物识别回退 → 锁定15分钟的完整流程
    - _需求: 2.3, 3.2, 8.2, 8.4, 9.5_

- [~] 16. 最终检查点 - 全部功能验证
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 开发
- 每个任务都引用了对应的需求编号，确保可追溯性
- 检查点用于增量验证，确保每个阶段的功能正确
- 属性测试验证数据模型的通用正确性属性
- UI 全面采用 Material Design 3 规范，支持 Dynamic Color
