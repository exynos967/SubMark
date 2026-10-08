---
name: submark-handover
description: SubMark 项目交接：架构、状态、已知问题、下一步
---

# SubMark 交接

开源安卓订阅管理 App，clean-room 复刻 iOS "MarkBuy"。
Kotlin 2.0.21 + Compose + Hilt + Room，minSdk 29，多模块。

## 模块

```
app
core: model(实体枚举DataStore序列化) / domain(日期+金额+汇率计算) / database / data(服务+设置+变更总线+启动钩子) / ui(主题+通用组件+图表+路由契约)
feature: subscriptions money overview calendar analytics share notifications backup settings integrations(主+panel) widget
```

## 已完成
- 底层：model(database+domain+data+ui) 全测
- 11 个 feature 模块全部编译通过，全部单元测试**约 341 个全过**
- `app:assembleDebug` 构建通过，APK 已上模拟器跑起来
- 启动引导 / 主界面（概览） / 底栏 / 拦截应用锁 都活
- 修复：概览 `TreeMap.get(null)` 崩溃、通知模块 Hilt 依赖环、种子图标名不对

## 没做完 / 已知问题
1. **Overview 顶部有 "Classic/Modern" 切换按钮挡住了底栏 "概览" 键**（点不了）。`OverviewScreen.kt:182` 的内联布局切换器。底栏兜的 assurance 大概率被顶部的这个 segmented 吃掉了 touch。要么识高在 Overview tab 里，要么 Override 它的 click intercept，要么把切换器改成 url 固定在 Scaffold 里。
2. **通知 + widget + integrations 主Agent 的代码已提交，但最终报告没出**（被限流中断）。代码本身编译过了，单元测试也过了，但我没逐一看过这三块的完整实现报告。先跑模拟器测核心流程（添加订阅 → 提醒 → 付款 → 钱包）再确认。
3. **Release Build**： ProGuard / R8 优化没跑过，大概会变现（dashboard 7。目前装的是 `app-debug.apk`）。
4. **面板 = Panel tab** 长啥样还没验证（只用了 `compil-able`）。
5. **通知在模拟器里还没测**（提醒 + 开机重排） 。

## 使用
- 工具链：`source /Users/exynos/workdir/markbuy/env.sh`，Gradle 8.9 + JDK 17 + Android SDK 35
- 构建：`./gradlew :app:assembleDebug`；APK 在 `app/build/outputs/apk/debug/`
- 模拟器：已建好 `submark_test` (API 35 arm64)，启动命令见 env.sh + `emulator -avd submark_test`
- 设备文件位置：照片 → `filesDir/photos/`，本地图标 → `filesDir/icons/`，备份暂存 → `filesDir/backup-staging/`，分享导出 → `cacheDir/share/`

## 下一步建议
1. 修 overview 顶栏布局切换器遮挡底栏的问题
2. 在模拟器里走一遍核心流程（添加订阅、标记付款、查看历史、钱包）
3. 跑一遍 Release/ProGuard
4. 确认通知、小组件、备份、面板能跑

## 提交
所有提交都是中文 Conventional Commit，追了 `Co-Authored-By`。
