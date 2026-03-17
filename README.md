# ChargingCalculator — 充电费用计算器

一款简洁的 Android 应用，用于计算新能源汽车或其他设备的充电费用。支持手动输入充电时间，也支持从充电通知截图中一键 OCR 识别开始/结束时间。

---

## 功能特性

- **手动输入**：分别选择充电开始时间与结束时间，自动计算时长与费用
- **截图 OCR 识别**：从相册选取充电通知截图，自动识别「开始充电」和「结束充电」两个时间点，无需手动输入
- **单价设置**：默认电费单价 6.50 元/小时，可随时修改并持久化保存
- **结果展示**：显示充电时长（小时/分钟）及应付费用

## 截图示例

> OCR 识别支持如下格式的充电通知截图：
>
> ```
> 开始充电提醒
> 2026-03-17 09:26:11
>
> 结束充电提醒
> 2026-03-17 16:23:52
> ```

## 技术栈

| 技术 | 版本 |
|------|------|
| Android SDK | compileSdk 34 / minSdk 24 |
| Java | 1.8 |
| Google ML Kit Text Recognition | 16.0.0 |
| ML Kit 中文识别 | 16.0.0 |
| AndroidX AppCompat | 1.6.1 |
| Material Components | 1.11.0 |
| Gradle | 8.2 |
| Android Gradle Plugin | 8.2.0 |

## 构建与运行

### 环境要求

- JDK 17+（推荐使用 Android Studio 内置 JDK）
- Android Studio Hedgehog 或更高版本

### 本地构建

```bash
# Debug APK
./gradlew assembleDebug

# APK 输出路径
app/build/outputs/apk/debug/app-debug.apk
```

### 自动构建

本项目配置了 GitHub Actions CI，每次向 `main` 分支推送代码或提交 PR 时自动构建 Debug APK，构建产物可在 Actions 页面下载。

详见 [.github/workflows/build.yml](.github/workflows/build.yml)

## 项目结构

```
ChargingCalculator/
├── app/
│   └── src/main/
│       ├── java/com/example/chargingcalculator/
│       │   └── MainActivity.java       # 核心逻辑（OCR + 费用计算）
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   └── values/strings.xml
│       └── AndroidManifest.xml
├── .github/
│   └── workflows/
│       └── build.yml                   # GitHub Actions 自动构建
├── build.gradle
├── settings.gradle
└── README.md
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_IMAGES` (Android 13+) | 从相册读取截图 |
| `READ_EXTERNAL_STORAGE` (Android ≤12) | 从相册读取截图（兼容旧版） |

## License

MIT
