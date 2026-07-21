# 云端打包原生 YunPlayer（不装 Android Studio）

## 1. 新建 GitHub 空仓库

例如：`yunplayer-native`（不要勾选 README）

## 2. 推送

```bat
cd /d "K:\Claude Project\YunPlayer\android-native"
git init
git add .
git commit -m "YunPlayer native Android"
git branch -M main
git remote add origin https://github.com/你的用户名/yunplayer-native.git
git push -u origin main
```

Token 需要勾选：`repo` + **`workflow`**

## 3. Actions

仓库 → **Actions** → **Build YunPlayer Native APK** → **Run workflow**

完成后下载 Artifacts：**yunplayer-native-apk**

## 4. 安装

把 `app-debug.apk` 拷到手机安装，或：

```bat
adb install -r app-debug.apk
```
