# 战术轮盘构建

战术标点使用 EsRadial 0.2.0 的 ApricityUI 渲染器，与 Espetro 共用轮盘。
保留 Ping Wheel 的标点按键、射线和地图/世界标点；网络消息格式不变。

先将 [EsRadial](https://github.com/RositaOVO/EsRadial/tree/1.20.1) 的 `1.20.1`
分支发布到 Maven Local：在 EsRadial 根目录执行 `./gradlew :forge:publishToMavenLocal`。
然后准备 README 所列的 Espetro、Tetrachord 和其他依赖，在 EsPoints 执行
`./gradlew test build`。不再需要 `-PauratipJar`；Espetro 自身的 AuraTip 前置仍需安装。

客户端和服务器安装外置 EsRadial 0.2.0、ApricityUI 1.2.3.1。
EsRadial 0.2.0 包含游戏内拖动编辑和客户端 JSON 布局配置。

按住标点键约 4 tick 打开；松开或左键确认，右键 / Esc 取消。
中心和两个空槽不执行动作，取消后继续按住不会重新打开。
退出战场或断线会关闭本模组的轮盘；已有 Espetro 轮盘时不会抢占。

开发者可以在 `TacticalMarkRadialController.menuLayout()` 中把 `RadialLayout.squad(...)`
换成显式布局：构造 `RadialLayout(内半径, 外半径, List<Sector>)`，
每个 `Sector(起始角, 扇区角度, 动作序号)` 指定位置和大小，序号 `-1` 留空。角度从正上方起顺时针计算；未覆盖区域自动留空。
玩家打开轮盘后按 F6 进入拖动编辑，Enter 保存、Esc 取消、R 恢复默认（保存后生效）。
空槽也能拖动；拖动分界线调整大小，滚轮旋转整个轮盘。
配置位于客户端 `config/esradial/layouts.json`，每个菜单和每组稳定按钮 ID 分别保存。
手动修改后下次打开轮盘生效；同一个菜单的不同职业权限或补给分页使用不同 profile。
详细格式见 [EsRadial 布局配置](https://github.com/RositaOVO/EsRadial/blob/1.20.1/docs/LAYOUTS.md)。
