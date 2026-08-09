# 蓝色大肥鱼桌宠 · Android 版

基于 [deepfish-desktop-pet](https://github.com/Dolphin2026-stl/deepfish-desktop-pet) 移植的 Android 8.0+ 桌宠应用。沿用原项目全部帧动画素材与行为逻辑，使用原生 Kotlin 实现透明悬浮窗桌宠。

> 社区二创，不隶属于 DeepSeek 官方。角色立绘来自原项目素材，如需公开分发请确认你拥有对应素材的使用与再授权权利。

## 功能

- 透明、无边框、置顶悬浮窗桌宠（`TYPE_APPLICATION_OVERLAY`）
- 待机呼吸浮动与不定时整帧眨眼
- 单击、双击、拖拽、快速绕圈拖动反馈
- 生理时钟：睡眠、清晨、专注、午饭、摸鱼、加班、犯困七段状态
- 自动散步、洗碗、加班喝咖啡、抱鲸鱼玩偶、坐下和睡觉场景
- 随机闲置彩蛋与 31 张整帧 PNG 表情、道具和动作帧
- 监听锁屏、解锁事件，解锁后随机触发「伸懒腰」或「惊醒」
- 内置聊天面板，保留最近十轮上下文
- 默认免费 Pollinations 模型，可切换 DeepSeek 或自定义 OpenAI 兼容 API
- API Key 使用 Android Keystore AES 加密后落盘

## 安装

- 环境要求：Android 8.0 (API 26) 或更高
- 从 [Releases](https://github.com/fvfpq/deepfish-desktop-pet-android/releases) 下载 APK
- 首次使用需授予「悬浮窗权限」与「通知权限」

## 交互方式

| 操作 | 反馈 |
|------|------|
| 单击 | 点击台词 + 爱心粒子 |
| 双击 | 打开聊天面板 |
| 按住拖动 | 移动桌宠，松手后拖拽反馈 |
| 快速绕圈拖动 | 触发晕眩、转圈星星与台词 |
| 散步时经过头发 | 随机眯眼卖萌或自信 pose |
| 散步时经过脸 | 捂脸害羞、脸红 |
| 散步时经过腿 | 绊倒；短时间多次绊倒后会拿手帕哭哭 |

## 模型设置

在设置页选择模型服务商：

- **免费模型（默认）**：`https://text.pollinations.ai/openai`，模型 `openai-fast`，无需 Key
- **DeepSeek**：`https://api.deepseek.com/chat/completions`，模型 `deepseek-chat`，需填写自己的 API Key
- **自定义**：任意 OpenAI 兼容 HTTPS 地址

Key 使用 Android Keystore AES 加密保存，不会明文落盘。

## 开发与构建

```bash
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
```

产物位于 `app/build/outputs/apk/debug/`。

## 项目结构

```
deepfish-desktop-pet-android/
├─ app/src/main/
│  ├─ assets/          # 原版整帧 PNG 素材
│  ├─ java/com/deepfish/pet/
│  │  ├─ PetService.kt # 悬浮窗服务、系统事件、触摸交互
│  │  ├─ PetView.kt    # 桌宠渲染、帧动画、粒子、行为调度
│  │  ├─ chat/         # 聊天面板与模型客户端
│  │  ├─ settings/     # 设置页
│  │  └─ model/        # 生理时钟、行为目录、手势检测
│  └─ res/             # 界面资源
```

## 贡献者

- [fvfpq](https://github.com/fvfpq) — Android 移植与维护
- [Dolphin2026-stl](https://github.com/Dolphin2026-stl) — 原始 Windows 版作者

## 许可

- 代码：MIT License（见 `LICENSE`）
- 角色立绘：素材权利归原项目作者，不随源码自动授权
