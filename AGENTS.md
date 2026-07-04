# 项目工作约定

## 发版流程（每次功能改动/修复验证完成后）

按以下顺序执行，缺一不可：

1. **更新版本号**：在 `app/build.gradle.kts` 中
   - `versionCode` +1
   - `versionName`：按语义化版本升级（修复/补丁 patch +0.0.1；新增功能 minor +0.1.0；破坏性改动 major +1.0.0）
2. **提交代码**：`git add` + `git commit`，commit message 概括本次改动
3. **推送到远程**：`git push origin <当前分支>`
4. **构建 release 包**：`./gradlew :app:assembleRelease`，产物在 `app/build/outputs/apk/release/app-release.apk`

> 注意：版本号必须在构建 release **之前**更新，确保 APK 内嵌的版本号是最新值。验证完功能后再发版，避免把未验证的改动打进 release。
