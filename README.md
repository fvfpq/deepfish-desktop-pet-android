# 蓝色大肥鱼桌宠 · Android 版

基于 [deepfish-desktop-pet](https://github.com/Dolphin2026-stl/deepfish-desktop-pet) 移植的 Android 8.0+ 桌宠应用。沿用原项目全部帧动画素材与行为逻辑，使用原生 Kotlin 实现透明悬浮窗桌宠，并扩展了聊天、模型接入与无障碍手机操作能力。

> 社区二创，不隶属于 DeepSeek 官方。角色立绘来自原项目素材，如需公开分发请确认你拥有对应素材的使用与再授权权利。

## 功能特性

### 悬浮窗桌宠

- 透明、无边框、置顶悬浮窗（`TYPE_APPLICATION_OVERLAY`），不干扰桌面操作
- 常驻前台服务 + 通知栏快捷操作（聊天 / 防误触 / 隐藏桌宠）
- 支持开机自启（需授予开机广播权限）
- 可调节人物尺寸（60% ~ 115%）与行为强度（50% ~ 180%）

### 动画与行为

- 31 张整帧 PNG 立绘 + 全新 5 种动画（跳舞 / 跳跃 / 欢呼 / 好奇歪头 / 小憩），共 36 种可触发行为
- 待机呼吸浮动、不定时整帧眨眼
- 生理时钟七段状态，不同时段自动切换行为计划
- 随机闲置彩蛋与专属「签名动作」池
- 锁定 / 解锁屏幕监听：解锁后随机触发「伸懒腰」或「惊醒」

### 交互反馈

- 单击：点击台词 + 爱心粒子
- 双击：打开聊天悬浮窗
- 拖拽：移动桌宠，松手后拖拽反馈
- 快速绕圈拖动：触发晕眩（转圈星星 + 台词）
- 拖拽距离判定：短距离松手触发「惊醒」

### 聊天与模型

- 透明悬浮窗聊天面板：可拖动、可调大小、点击输入框自动唤起系统输入法
- 保留最近十轮上下文
- 三种模型服务商：免费 Pollinations（默认）/ DeepSeek / 自定义 OpenAI 兼容 API
- API Key 使用 Android Keystore AES-GCM 加密后落盘，不存明文

### 无障碍手机操作（v1.2.0+）

- 内嵌 openclaw（MIT）无障碍执行层
- 在聊天中输入自然语言指令（「帮我打开微信」「帮我点一下」「回桌面」等），桌宠通过 LLM 理解屏幕节点快照并自动操作手机
- 支持多轮「观察 -> 决策 -> 执行 -> 再观察」闭环，直到任务完成
- 敏感输入框（密码等）自动拒绝写入

## 安装

### 环境要求

- Android 8.0 (API 26) 或更高
- 首次使用需授予「悬浮窗权限」与「通知权限」

### 安装步骤

1. 从 [Releases](https://github.com/fvfpq/deepfish-desktop-pet-android/releases) 下载最新 APK
2. 允许安装来自未知来源的应用（按系统提示操作）
3. 打开应用，点击「授予悬浮窗权限」，按系统指引完成授权
4. 回到应用，点击「启动桌宠」
5. 如需手机操作能力，在设置页点击「无障碍权限」并开启「大肥鱼桌宠」服务

> 提示：不同厂商系统对后台弹窗的限制不同，若桌宠被系统清理，请在系统设置中将本应用加入「后台运行白名单 / 自启动白名单」。

## 交互方式

| 操作 | 反馈 |
|------|------|
| 单击 | 点击台词 + 爱心粒子 |
| 双击 | 打开聊天悬浮窗 |
| 按住拖动 | 移动桌宠；松手后根据拖动距离触发「惊醒」或普通反馈 |
| 快速绕圈拖动 | 触发晕眩、转圈星星与台词 |
| 通知栏「聊天」 | 防误触模式下无需触碰桌宠即可聊天 |

## 行为目录

桌宠会根据生理时钟与随机调度触发以下行为：

| 行为 | 场景 | 说明 |
|------|------|------|
| walk | 散步 | 桌面自动左右走动，带动画帧 |
| wash / work / coffee / toy | 日常 | 洗碗、加班、喝咖啡、抱玩偶，各有循环摆动 |
| sleep / dream / nap | 睡眠 | 睡眠时段自动打盹 |
| hungry / feed | 进食 | 饥饿与投喂反馈 |
| sit / stretch / ciallo | 互动 | 坐下、伸懒腰、打招呼 |
| pat / shy | 触摸 | 摸头、害羞脸红 |
| trip / stranded | 意外 | 绊倒、搁浅 |
| think / smug / price | 情绪 | 思考、得意、涨价 |
| angry / cry / panic / shock / pressure / startle | 情绪 | 愤怒、哭泣、恐慌、震惊、压力、惊吓 |
| fly / dizzy / goAway | 特殊 | 起飞、转晕、赶人 |
| dance / jump / cheer / wonder | 新增 | 跳舞、跳跃、欢呼、好奇歪头 |

每个行为都有对应台词与粒子效果，会在合适的时段随机出现。

## 生理时钟

| 时段 | 时间段 | 状态 |
|------|--------|------|
| 睡眠 | 0:00 - 6:00 | 呼呼大睡 |
| 清晨 | 6:00 - 9:00 | 刚刚醒来 |
| 专注 | 9:00 - 12:00 | 专注营业 |
| 午饭 | 12:00 - 14:00 | 寻找白饭 |
| 摸鱼 | 14:00 - 18:00 | 下午摸鱼 |
| 加班 | 18:00 - 23:00 | 陪你加班 |
| 犯困 | 23:00 - 24:00 | 开始犯困 |

不同时段会调用不同的行为计划，例如清晨偏向散步 / 伸懒腰，睡眠时段则反复打盹。

## 模型设置

在设置页选择模型服务商：

- **免费模型（默认）**：`https://text.pollinations.ai/openai`，模型 `openai-fast`，无需 Key
- **DeepSeek**：`https://api.deepseek.com/chat/completions`，模型 `deepseek-chat`，需填写自己的 API Key
- **自定义**：任意 OpenAI 兼容 HTTPS 地址。只需填写 base URL（如 `https://xxx/v1`），应用会自动补全 `/chat/completions`；直接填完整路径也兼容

Key 使用 Android Keystore AES-GCM 加密保存（每次加密使用随机 IV，密钥不可导出），不会明文落盘。

## 设置项详解

| 设置项 | 说明 |
|--------|------|
| 模型服务商 | 免费 / DeepSeek / 自定义 |
| 悬浮窗置顶 | 是否保持窗口置顶 |
| 音效 | 行为触发时播放提示（如需） |
| 自由散步 | 开启后桌宠会随机左右走动 |
| 互动区域 | 开启后按人物区域触发不同反应 |
| 贴心台词 | 关闭后不再出现调侃类台词 |
| 行为强度 | 50% ~ 180%，控制行为触发频率 |
| 人物尺寸 | 60% ~ 115% 缩放 |
| 防误触 | 开启后触摸穿透到下层应用，不再拦截点击 |

## 手机操作（无障碍）

开启步骤：

1. 设置页点击「无障碍权限」按钮
2. 在系统无障碍设置中开启「大肥鱼桌宠」
3. 回到桌宠聊天，输入操作指令即可

支持指令示例：

```
帮我打开微信
帮我点一下设置
帮我输入"你好"
回桌面
帮我滑动到下一页
帮我看一下当前屏幕
```

技术原理：

1. 抓取当前屏幕的无障碍节点快照（包名、标题、节点树）
2. 将快照压缩为文本喂给 LLM，模型返回 JSON 动作列表
3. 通过无障碍手势执行（点击 / 输入 / 滚动 / 滑动 / 全局操作）
4. 执行后重新读取屏幕确认结果，最多 4 轮、每轮最多 10 个动作，防止失控

## 开发与构建

### 环境要求

- JDK 17+
- Android SDK（API 34）
- Gradle 8.9

### 构建命令

```bash
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
```

产物位于 `app/build/outputs/apk/debug/`。

> 提示：本环境使用 `/opt/gradle/gradle-8.9/bin/gradle` 构建，亦可通过 `./gradlew`（如已配置 wrapper）构建。

## 项目结构

```
deepfish-desktop-pet-android/
├─ app/
│  ├─ build.gradle.kts        # 构建配置（minSdk 26 / targetSdk 34）
│  └─ src/main/
│     ├─ AndroidManifest.xml  # 权限与组件声明
│     ├─ assets/
│     │  ├─ character.png     # 主立绘
│     │  └─ frames/           # 31 张整帧 PNG 动画素材
│     ├─ java/com/deepfish/pet/
│     │  ├─ MainActivity.kt   # 启动页：权限引导
│     │  ├─ PetService.kt     # 悬浮窗服务、系统事件、触摸交互
│     │  ├─ PetView.kt        # 桌宠渲染、帧动画、粒子、行为调度
│     │  ├─ ApiKeyStore.kt    # Keystore AES-GCM 加密存储
│     │  ├─ Prefs.kt          # 设置持久化
│     │  ├─ BootReceiver.kt   # 开机自启
│     │  ├─ accessibility/    # 无障碍手机操作（openclaw 执行层）
│     │  ├─ chat/             # 聊天悬浮窗与模型客户端
│     │  ├─ settings/         # 设置页
│     │  └─ model/            # 生理时钟、行为目录、手势检测
│     └─ res/                 # 布局、主题、资源
```

### 核心模块说明

| 模块 | 职责 |
|------|------|
| `PetView.kt` | 帧动画播放、行为调度、粒子效果、手势识别、闲置调度 |
| `PetService.kt` | 悬浮窗窗口管理、屏幕锁定监听、通知栏、防误触 |
| `chat/ChatOverlay.kt` | 聊天悬浮窗 UI、操作指令识别与分发 |
| `chat/ChatManager.kt` | OpenAI 兼容客户端：上下文截断、错误解析 |
| `accessibility/PhoneOperator.kt` | LLM 决策 + 无障碍执行闭环 |
| `model/Behaviors.kt` | 行为目录、场景计划、台词与帧映射 |
| `model/BodyClock.kt` | 生理时钟七段状态 |
| `model/SpinTracker.kt` | 快速绕圈手势检测 |

## 版本历史

| 版本 | 说明 |
|------|------|
| [v1.2.6](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.6) | 通知栏聊天入口 + 新增 5 种动画（跳舞 / 跳跃 / 欢呼 / 好奇 / 小憩） |
| [v1.2.5](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.5) | 聊天悬浮窗支持输入法唤出与大小调节 |
| [v1.2.4](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.4) | 人形缩小至 120dp，聊天框改为悬浮窗 |
| [v1.2.3](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.3) | 防误触模式（触摸穿透） |
| [v1.2.2](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.2) | 人形进一步缩小 |
| [v1.2.1](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.1) | 修复自定义模型 404（自动补全 /chat/completions） |
| [v1.2.0](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.2.0) | 接入 openclaw 无障碍能力，可指令操作手机 |
| [v1.1.3](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.1.3) | 进一步缩小人形，气泡文字居中 |
| [v1.1.2](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.1.2) | 气泡简化 + 人形缩小 |
| [v1.1.1](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.1.1) | 修复肢体动作不播放 + 绕圈闪退加固 |
| [v1.1.0](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.1.0) | 鲸鱼女仆形象 + 比例修正 + 闪退修复 |
| [v1.0.2](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.0.2) | 气泡样式优化（圆角描边 + 小尾巴） |
| [v1.0.1](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.0.1) | 缩小尺寸、解决遮挡 |
| [v1.0.0](https://github.com/fvfpq/deepfish-desktop-pet-android/releases/tag/v1.0.0) | 首发版 |

各版本更新详情见 [CHANGELOG.md](CHANGELOG.md)。

## 常见问题

**桌宠被系统清理 / 后台被杀？**
在系统设置中将本应用加入自启动白名单与电池优化白名单。

**点击「启动桌宠」没反应？**
确认已授予悬浮窗权限；部分系统需要在「设置 -> 应用 -> 特殊应用权限 -> 显示在其他应用上层」中手动允许。

**聊天一直「没连上模型」？**
免费模型依赖公共接口，可能受网络影响；可切换到自备的 DeepSeek 或自定义 Key 使用。

**手机操作指令没反应？**
确认已在系统无障碍设置中开启「大肥鱼桌宠」服务，且当前屏幕存在可操作节点。

## 贡献者

- [fvfpq](https://github.com/fvfpq) — Android 移植与维护
- [Dolphin2026-stl](https://github.com/Dolphin2026-stl) — 原始 Windows 版作者
- 无障碍执行层基于 [openclaw](https://github.com/openclaw/openclaw)（MIT License）

## 许可

- 代码：MIT License（见 `LICENSE`）
- 角色立绘：素材权利归原项目作者，不随源码自动授权
