# YunPlayer Native（Android 原生重写）

**Kotlin + Jetpack Compose + Media3 + OkHttp + Room** 全原生实现，**不是 WebView 套壳**。

相对网页版的关键差异：

| 能力 | 网页 / 套壳 APK | 原生版 |
|------|-----------------|--------|
| WebDAV | 受 **CORS** 限制 | **OkHttp 原生请求，无 CORS** |
| 播放 | HTML Audio / WebView | **Media3 ExoPlayer + 系统媒体会话** |
| 本地曲库 | 浏览器文件选择 | **SAF 持久授权 + MediaMetadata** |
| 数据 | localStorage | **Room 数据库 + DataStore** |
| 后台 | 易被杀 | **MediaSessionService 前台服务** |

## 功能对照

- 播放页：封面、进度、上一首/下一首、播放模式（顺序/列表循环/随机/单曲）
- 封面播放动效：涟漪 / 呼吸 / 脉冲 / 光晕 / 关闭
- 主题：清茶 / 夜墨 / 暖沙 / 紫霞 / 极简
- 歌单：我喜欢的、本地、WebDAV、自建歌单
- 歌曲源：本地导入、WebDAV 连接 / 浏览 / 扫描
- 播放统计（按播放次数）

## 目录

```
android-native/
├── app/src/main/java/com/yunplayer/app/
│   ├── data/          # Room / Prefs / WebDAV / Repository
│   ├── player/        # Media3 PlaybackService
│   └── ui/            # Compose 界面
├── app/src/main/res/
└── README.md
```

## 构建 APK（GitHub Actions，可不装 Android Studio）

1. 新建 GitHub 仓库  
2. 将 **`android-native` 目录内容**推到仓库根目录  
3. 添加工作流（可复制 `../android-app/.github/workflows/build-apk.yml`，把路径改成当前工程）  
4. Actions → 运行 → 下载 APK  

或用 Android Studio：**Open** 本目录 → **Build APK**。

### 本机命令行（需 SDK）

```bat
cd android-native
REM 写入 local.properties: sdk.dir=你的SDK路径
gradlew assembleDebug
```

## 包名

`com.yunplayer.app` · 版本 `2.0.0-native`

## 说明

- 首次打开请授予「音乐与音频」权限（Android 13+）  
- WebDAV 填完整地址，如 `https://nas:5005/dav/`  
- 歌词 LRC 解析 / ID3 封面可在后续迭代补强（当前以系统 Metadata 为主）  
