# 更新记录

## v1.1.0（2026-08-09）— 鲸鱼女仆形象 + 比例修正 + 闪退修复

参考 [codex-deepseek-pet](https://github.com/YunYueSama/codex-deepseek-pet) 全面换用蓝发鲸鱼女仆立绘：

- 角色形象由「蓝色大肥鱼」替换为「蓝发鲸鱼女仆」，12 个动作立绘映射全部行为
- 图片保持原始比例显示（FIT_CENTER），消除旧版 FIT_XY 拉伸变形
- 按动作切换视觉缩放（如思考 1.05、害羞 0.89、跳跃 0.84），各姿势主体大小协调
- 修复快速绕圈触发旋转头晕时偶发闪退：位图解码空值防护、粒子/星星移除竞态加固、晃动动画循环独立计时器
- 位图缓存改为按字节计算（上限 32MB），避免高清立绘内存超限

**下载**：`deepfish-pet-android-1.1.0.apk`

## v1.0.2（2026-08-09）— 气泡样式优化

参考 [Deepseek-DesktopPet](https://github.com/nonearth/Deepseek-DesktopPet) 改进气泡视觉与交互：

- 气泡改为白色圆角 + 蓝色描边，底部带指向角色的菱形小尾巴
- 气泡锚定角色头顶、向上生长，长文本不再遮挡人物
- 支持点击气泡立即关闭
- 文字字号 13sp → 14sp，行距 1.3

**下载**：`deepfish-pet-android-1.0.2.apk`

## v1.0.1（2026-08-09）— 缩小尺寸、解决遮挡

- 悬浮窗默认尺寸从 340×430dp 缩至 280×365dp
- 默认缩放比例由 1.0 降至 0.9
- 角色整体下移，顶部预留气泡区域，文字不再与人物重叠

**下载**：`deepfish-pet-android-1.0.1.apk`

## v1.0.0（2026-08-09）— 首发版

基于 [deepfish-desktop-pet](https://github.com/Dolphin2026-stl/deepfish-desktop-pet) 移植的 Android 8.0+ 桌宠：

- 透明悬浮窗桌宠：单击 / 双击 / 拖拽 / 快速绕圈互动
- 生理时钟七段状态（睡眠 / 清晨 / 专注 / 午饭 / 摸鱼 / 加班 / 犯困）+ 闲置彩蛋
- 31 张整帧 PNG 动画素材
- 内置聊天面板：免费模型 / DeepSeek / 自定义 OpenAI 兼容
- API Key 使用 Android Keystore AES 加密落盘
- 监听锁屏 / 解锁，解锁后随机「伸懒腰」或「惊醒」

**下载**：`deepfish-pet-android-1.0.0.apk`
