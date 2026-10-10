# HCR AAD (`espoints`)

HCR AAD 是 Minecraft Forge 1.20.1 的行动攻防、据点和战术地图模组。实际模组 ID 为 `espoints`，发布名称为 `HCR AAD`。

## 核心功能

- 分批次行动攻防与进攻/防守兵力。
- 据点占领、奖励、死亡扣兵力和胜负结算。
- 战术地图、地图背景、玩家位置与缩放控制。
- 战术地图显示 Squad 风格的 Radio、HAB 与 Rally 图标、FOB 建设/排斥半径、共享库存和 Rally 波次。
- 指挥官、小队长、火力组组长和合法载具席位可使用 Ping Wheel 按键打开 ApricityUI / EsRadial 战术轮盘；标点同阵营同步。
- Espetro `155火炮支援` 选点地图，右键将战术地图坐标提交给 Espetro KubeJS 技能回调；实际火力效果由 Espetro 的 `server_scripts` 实现。
- 据点详情、兵力、区域和消息 HUD。
- 从 Espetro 当前地图的只读快照加载战术地图、底图和据点。
- 可将运行中的据点状态导出为 JSON；不会改写 `EsWorld` 地图模板。
- 与 Espetro 的 `ATTACK`/`DEFEND` 阵营、指挥官、小队和兵站状态集成。

## 环境与依赖

| 项目 | 版本 |
| --- | --- |
| Java | 17 |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| MUtil | 6.3.0 |
| Espetro | 1.1.3-j（精确版本） |
| Ping Wheel | 1.12.1 |
| EsRadial / ApricityUI | 0.3.0 / 1.2.6（精确版本）|
| OELib | 0.2.4 或更高 |
| Tetrachord Lib | 1.20+1.0.3（精确版本，双端硬前置） |

客户端和服务器均需安装 HCR AAD、Espetro、Tetrachord Lib 1.20+1.0.3、MUtil、
Ping Wheel、EsRadial、ApricityUI 与 OELib（Espetro 自身的前置仍需安装）。
普通配置、据点和选点窗口使用 MUtil；战术标点轮盘使用 ApricityUI / EsRadial，世界射线与显示复用
Ping Wheel。协议版本为 `13`，客户端与服务器必须成套更新。

## 默认按键

| 按键 | 功能 |
| --- | --- |
| 未绑定 | 请求并打开据点总览（请在按键设置中自行绑定） |
| `V` | 显示/隐藏战术地图 |
| Ping Wheel 的“标点”键 | 战场内按住打开战术标点轮盘；非战场区域保持 Ping Wheel 原功能 |
| `X` | 打开战术地图客户端设置 |
| 鼠标滚轮 | 地图可见时缩放 |
| 未绑定 | 打开 Markdown 阅读器 |
| `R` | 据点详情页请求刷新 |

ESPoints 不会用快捷键覆盖 Espetro 正在显示的阵营、编制、部署或死亡重部署界面。

## 构建

先按 [EsRadial 构建说明](docs/ESRADIAL.md) 将轮盘库发布到 Maven Local，再构建本模组。

```bash
cd /home/shu/IdeaProjects/espetro-HCR
./gradlew build
```

产物位于 `build/libs/espoints-<version>.jar`。

## 文档

- [完整配置与 JSON 文档](docs/CONFIGURATION.md)
- [玩家与管理员使用手册](docs/USER_GUIDE.md)
- [开发、API 与类参考](docs/DEVELOPMENT.md)
- [EsRadial 轮盘构建与布局](docs/ESRADIAL.md)

## 快速部署

1. 将 HCR AAD、Espetro、Tetrachord Lib 1.20+1.0.3、MUtil、Ping Wheel 1.12.1、EsRadial 0.3.0 和 ApricityUI 1.2.6 放入客户端及服务器 `mods/`，并安装 Espetro 自身的其他前置。
2. 在每个 `EsWorld/<地图>/EsConfig/` 中配置 `TacticalMap.json` 和 `CapturePoints.json`。
3. 完整重启服务器，由 Espetro 校验并冻结所有地图配置。
4. 正常开始 Espetro 对局；地图激活时 HCR AAD 自动加载该地图，部署阶段自动启动第一批据点。
5. 对局结束或执行 `/espetro stop` 后，地图、据点、底图和标点状态会一起清空。
