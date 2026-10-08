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
1. ~~Overview 切换器挡住底栏「概览」~~ 已修（cac41d7）。真实原因不是遮挡：页面内用普通 `navigate()` 跳 tab 路由，污染了起始 tab 的返回栈。**跳 tab 一律用 `core:ui` 的 `NavController.navigateToTab`。**
2. **通知 + widget + integrations 主Agent 的代码已提交，但最终报告没出**（被限流中断）。代码本身编译过了，单元测试也过了，但我没逐一看过这三块的完整实现报告。
3. ~~Release Build 没跑过~~ 已修（e7fc1de）：R8 曾裁掉路由序列化器导致启动崩溃。Release 已在模拟器冒烟（5 个 tab + 搜索 + 新增页 + 设置子页）。
4. **面板 = Panel tab** 长啥样还没验证（只用了 `compil-able`）。
5. **通知在模拟器里还没测**（提醒 + 开机重排） 。
6. UI 层仍有 14 处直接 `LocalDate.now()`（share/overview/analytics/calendar/money）。`SystemTimeProvider` 也用系统时区，运行时结果一致，仅影响可测试性；后续统一改为从 UiState 取 `today`。
7. 待确认：新订阅首付日为今天且未付时，详情页「当前周期」显示为开始日之前的一个周期（如 Sep 8 – Oct 7）。是否符合预期需产品决定。
8. 小问题：自定义概览页冷启动时会闪一下「至少显示一个组件」（初始空状态）；全局搜索页 "Categories" chip 文字折行。

## 使用
- 工具链：`source /Users/exynos/workdir/markbuy/env.sh`，Gradle 8.9 + JDK 17 + Android SDK 35
- 构建：`./gradlew :app:assembleDebug`；APK 在 `app/build/outputs/apk/debug/`
- 模拟器：已建好 `submark_test` (API 35 arm64)，启动命令见 env.sh + `emulator -avd submark_test`
- 设备文件位置：照片 → `filesDir/photos/`，本地图标 → `filesDir/icons/`，备份暂存 → `filesDir/backup-staging/`，分享导出 → `cacheDir/share/`

## 下一步建议
1. 在模拟器里走钱包、通知（提醒 + 开机重排）、小组件、备份、Panel
2. 确认上面第 7 条「当前周期」的业务语义
3. 统一 UI 层 today 来源（第 6 条）

## 提交
所有提交都是中文 Conventional Commit，追了 `Co-Authored-By`。
