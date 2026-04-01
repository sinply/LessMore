# 需求文档

## 简介

本项目旨在开发一款安卓应用（App使用控制器），帮助用户管理和限制手机应用的使用时长与使用时间段。通过设定每日使用时长上限和允许使用的时间窗口，引导用户减少不必要的手机使用，养成健康的数字生活习惯。

## 术语表

- **App使用控制器（Controller）**: 本安卓应用的核心系统，负责监控、限制和管理目标应用的使用
- **目标应用（Target_App）**: 用户选择进行使用控制的第三方安卓应用
- **使用时长限制（Usage_Limit）**: 用户为目标应用设定的每日最大可使用时长（以分钟为单位）
- **允许时间段（Allowed_Period）**: 用户设定的允许使用目标应用的时间窗口（如 08:00-12:00）
- **锁定界面（Lock_Screen）**: 当目标应用超出使用限制时，Controller 显示的遮挡界面，阻止用户继续使用
- **使用统计（Usage_Stats）**: Controller 记录的目标应用每日使用时长和使用频次等数据
- **白名单（Whitelist）**: 不受任何使用限制的应用列表（如电话、短信等必要应用）
- **管理密码（Admin_Password）**: 用户设定的密码，用于保护 Controller 的所有设置项，防止未授权修改
- **生物识别验证（Biometric_Auth）**: 通过设备支持的生物识别技术（指纹识别、面部识别等）进行身份验证，作为 Admin_Password 的替代验证方式
- **身份验证（Identity_Verification）**: 用户通过 Admin_Password 或 Biometric_Auth 进行身份确认的统称
- **强制锁定模式（Forced_Lock_Mode）**: 当目标应用触发锁定条件后，Controller 进入的高权限锁定状态，在限制条件解除前阻止一切退出或绕过行为

## 需求

### 需求 1：目标应用选择与管理

**用户故事：** 作为用户，我希望能够选择需要控制的手机应用，以便对特定应用进行使用限制。

#### 验收标准

1. THE Controller SHALL 显示设备上已安装的所有应用列表，供用户选择目标应用
2. WHEN 用户将一个应用添加为目标应用时, THE Controller SHALL 将该应用保存到受控应用列表中
3. WHEN 用户将一个应用从目标应用中移除时, THE Controller SHALL 立即解除该应用的所有使用限制
4. THE Controller SHALL 允许用户将应用添加到白名单，白名单中的应用不受任何使用限制

### 需求 2：每日使用时长限制

**用户故事：** 作为用户，我希望为每个目标应用设定每日使用时长上限，以便控制每天使用该应用的总时间。

#### 验收标准

1. WHEN 用户为目标应用设定 Usage_Limit 时, THE Controller SHALL 以分钟为单位保存该限制值，最小值为 1 分钟，最大值为 1440 分钟
2. WHILE 目标应用正在前台运行时, THE Controller SHALL 每秒更新该应用的当日累计使用时长
3. WHEN 目标应用的当日累计使用时长达到 Usage_Limit 时, THE Controller SHALL 显示 Lock_Screen 并阻止用户继续使用该应用
4. WHEN 新的一天开始（00:00）时, THE Controller SHALL 将所有目标应用的当日累计使用时长重置为零

### 需求 3：允许使用时间段设置

**用户故事：** 作为用户，我希望设定每个目标应用允许使用的时间段，以便在特定时间（如工作时间、睡眠时间）禁止使用某些应用。

#### 验收标准

1. THE Controller SHALL 允许用户为每个目标应用设定一个或多个 Allowed_Period，每个 Allowed_Period 包含开始时间和结束时间
2. WHEN 用户在 Allowed_Period 之外尝试打开目标应用时, THE Controller SHALL 显示 Lock_Screen 并提示下一个允许使用的时间段
3. WHEN 当前时间进入 Allowed_Period 时, THE Controller SHALL 自动允许用户使用对应的目标应用
4. WHEN 当前时间离开 Allowed_Period 时, THE Controller SHALL 显示 Lock_Screen 并阻止用户继续使用对应的目标应用
5. IF 用户未为目标应用设定任何 Allowed_Period, THEN THE Controller SHALL 默认全天允许使用该目标应用（仅受 Usage_Limit 约束）

### 需求 4：锁定界面与提醒

**用户故事：** 作为用户，我希望在应用被锁定时看到清晰的提示信息，以便了解锁定原因和解锁条件。

#### 验收标准

1. WHEN Lock_Screen 因 Usage_Limit 触发时, THE Controller SHALL 在 Lock_Screen 上显示"今日使用时长已达上限"以及已使用的时长
2. WHEN Lock_Screen 因 Allowed_Period 限制触发时, THE Controller SHALL 在 Lock_Screen 上显示"当前时间段不允许使用"以及下一个 Allowed_Period 的开始时间
3. WHILE Forced_Lock_Mode 未激活时, THE Lock_Screen SHALL 提供"返回桌面"按钮，允许用户退出被锁定的应用
4. WHEN 目标应用的当日累计使用时长达到 Usage_Limit 的 80% 时, THE Controller SHALL 发送通知提醒用户剩余可用时长

### 需求 5：使用统计与报告

**用户故事：** 作为用户，我希望查看每个应用的使用统计数据，以便了解自己的手机使用习惯并持续改进。

#### 验收标准

1. THE Controller SHALL 记录每个目标应用每天的使用时长和打开次数
2. THE Controller SHALL 提供每日使用统计视图，以列表形式展示每个目标应用当天的使用时长和打开次数
3. THE Controller SHALL 提供每周使用统计视图，以柱状图形式展示过去 7 天每天的总使用时长
4. THE Controller SHALL 保留至少 30 天的 Usage_Stats 历史数据

### 需求 6：应用权限与后台服务

**用户故事：** 作为用户，我希望应用能够在后台持续运行并正确监控应用使用情况，以便限制功能始终有效。

#### 验收标准

1. WHEN Controller 首次启动时, THE Controller SHALL 引导用户授予"使用情况访问权限"（UsageStatsPermission）和"悬浮窗权限"（SYSTEM_ALERT_WINDOW）
2. IF 必要权限未被授予, THEN THE Controller SHALL 在主界面显示权限缺失提示，并提供跳转到系统设置的入口
3. THE Controller SHALL 以前台服务（Foreground Service）方式运行监控进程，确保在后台持续监控目标应用的使用状态
4. WHEN 设备重启后, THE Controller SHALL 自动启动监控服务，无需用户手动操作

### 需求 7：数据持久化

**用户故事：** 作为用户，我希望我的设置和使用数据在应用关闭或设备重启后不会丢失，以便获得连续的使用控制体验。

#### 验收标准

1. THE Controller SHALL 将所有用户设置（目标应用列表、Usage_Limit、Allowed_Period、白名单）持久化存储在本地数据库中
2. THE Controller SHALL 将所有 Usage_Stats 数据持久化存储在本地数据库中
3. WHEN Controller 启动时, THE Controller SHALL 从本地数据库加载所有用户设置并恢复监控状态
4. FOR ALL 用户设置对象，保存后重新加载 SHALL 产生与原始设置等价的对象（往返一致性）

### 需求 8：密码保护与身份验证

**用户故事：** 作为用户，我希望通过管理密码或生物识别（指纹、面部识别）来保护控制器的所有设置，以便防止他人（或自己在意志薄弱时）随意修改使用限制，同时享受便捷的验证体验。

#### 验收标准

1. WHEN 用户首次使用 Controller 时, THE Controller SHALL 引导用户设定一个至少 4 位的 Admin_Password
2. WHEN 用户尝试进入设置页面（包括修改目标应用列表、Usage_Limit、Allowed_Period、白名单）时, THE Controller SHALL 要求用户通过 Identity_Verification（Admin_Password 或 Biometric_Auth）进行验证
3. WHEN 用户输入的密码与 Admin_Password 不匹配时, THE Controller SHALL 拒绝进入设置页面并显示"密码错误"提示
4. WHEN 用户连续 5 次输入错误密码时, THE Controller SHALL 锁定设置入口 15 分钟，并显示剩余锁定时间
5. THE Controller SHALL 提供修改 Admin_Password 的功能，修改前须验证当前密码
6. THE Controller SHALL 将 Admin_Password 以安全哈希形式存储在本地，不以明文保存
7. WHEN 用户尝试卸载 Controller 应用时, THE Controller SHALL 要求通过 Identity_Verification 进行确认（需启用设备管理器权限）

### 需求 9：生物识别验证

**用户故事：** 作为用户，我希望使用指纹或面部识别来替代密码验证，以便更快捷、更安全地通过身份验证。

#### 验收标准

1. WHEN 设备支持生物识别功能（指纹传感器或面部识别）时, THE Controller SHALL 在密码设定完成后提供启用 Biometric_Auth 的选项
2. WHEN 用户选择启用 Biometric_Auth 时, THE Controller SHALL 调用 Android BiometricPrompt API 引导用户注册生物识别信息
3. WHILE Biometric_Auth 已启用时, THE Controller SHALL 在所有需要 Identity_Verification 的场景中优先展示生物识别验证界面
4. WHILE Biometric_Auth 已启用时, THE Controller SHALL 同时提供"使用密码验证"的备选入口，允许用户切换到 Admin_Password 验证
5. WHEN 生物识别验证失败 3 次时, THE Controller SHALL 自动切换到 Admin_Password 验证方式
6. IF 设备不支持生物识别功能, THEN THE Controller SHALL 隐藏 Biometric_Auth 相关选项，仅使用 Admin_Password 进行验证
7. IF 设备的生物识别硬件不可用或生物识别数据被清除, THEN THE Controller SHALL 自动回退到 Admin_Password 验证方式，并通知用户生物识别不可用
8. THE Controller SHALL 提供在设置中启用或禁用 Biometric_Auth 的开关，操作前须通过 Identity_Verification
9. THE Controller SHALL 使用 Android Keystore 系统存储与生物识别关联的加密密钥，确保生物识别凭据的安全性

### 需求 10：强制锁定与防绕过

**用户故事：** 作为用户，我希望锁定界面具有足够高的权限，在使用时长或时间段限制未解除前，不能通过任何手段退出或绕过锁定，以便确保使用控制真正有效。

#### 验收标准

1. WHILE Forced_Lock_Mode 处于激活状态时, THE Controller SHALL 以最高优先级悬浮窗覆盖目标应用界面，阻止用户与目标应用进行任何交互
2. WHILE Forced_Lock_Mode 处于激活状态时, THE Controller SHALL 拦截系统返回键、Home 键和最近任务键，阻止用户通过导航操作离开 Lock_Screen
3. WHILE Forced_Lock_Mode 处于激活状态时, THE Controller SHALL 监控目标应用的进程状态，若用户通过强制停止方式关闭目标应用后重新打开，Controller SHALL 立即重新显示 Lock_Screen
4. WHEN 用户在 Forced_Lock_Mode 激活期间尝试关闭 Controller 的前台服务时, THE Controller SHALL 自动重启监控服务并恢复锁定状态
5. WHEN 用户在 Forced_Lock_Mode 激活期间尝试撤销 Controller 的悬浮窗权限或使用情况访问权限时, THE Controller SHALL 通过设备管理器权限阻止权限撤销操作
6. THE Controller SHALL 在首次启用强制锁定功能时，引导用户授予设备管理器（Device Admin）权限，以获得防卸载和防权限撤销能力
7. WHILE Forced_Lock_Mode 处于激活状态时, THE Lock_Screen SHALL 仅显示锁定原因和剩余锁定时间，不提供任何退出或关闭按钮
8. WHEN 使用限制条件解除（当日使用时长重置或进入 Allowed_Period）时, THE Controller SHALL 自动退出 Forced_Lock_Mode 并恢复用户对目标应用的正常访问
