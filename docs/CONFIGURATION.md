# HCR AAD 配置文档

## 配置来源

HCR AAD 现在把“客户端显示设置”和“地图规则”分开：

| 文件 | 作用 | 所有者 |
| --- | --- | --- |
| `config/espoints-common.toml` | HUD、检测频率、奖励等通用设置 | HCR AAD |
| `config/espoints-client.toml` | 战术地图位置、缩放和颜色 | 每个客户端 |
| `config/hcr_map_player_display.json` | 是否显示玩家位置 | HCR AAD |
| `EsWorld/<地图>/EsConfig/TacticalMap.json` | 当前地图边界、底图和标点显示 | Espetro 地图 |
| `EsWorld/<地图>/EsConfig/CapturePoints.json` | 当前地图据点批次和双方兵力 | Espetro 地图 |
| `config/espoints/exports/*.json` | 管理员手工导出的运行状态 | HCR AAD，只写这里 |
| `config/espoints/presets/*.json` | 旧版管理员预设兼容 | HCR AAD |

不再读取 `config/espoints/teamfight.json`、`data/espoints/tactical_map/default.json` 或旧的
`config/hcr_tactical_map.json`。`/reload` 不会更换活动地图规则。

地图规则由 Espetro 在服务端启动时校验并冻结。地图激活时，HCR AAD 接收内存快照；停止游戏、
结算或切换地图时会清空据点、底图、标点和同步缓存。HCR AAD 不会修改
`EsWorld/<地图>` 中的任何文件。

## 构建与依赖

| `gradle.properties` 字段 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.20.1` | 目标游戏版本 |
| `forge_version` | `47.4.20` | Forge 开发版本 |
| `mod_id` | `espoints` | 模组 ID |
| `mod_version` | `1.1.1c` | HCR AAD 构建版本 |
| `espetro_version` | `1.1.3-j` | Espetro 编译和运行精确版本 |
| `pingwheel_version` | `1.12.1` | 客户端和服务器强制安装的 Ping Wheel 兼容版本 |
| `esradial_version` | `0.3.0` | 战术轮盘精确强制依赖 |
| `apricityui_version` | `1.2.6` | 轮盘渲染前置 |
| `oelib_version` | `0.2.4` | OELib 运行时强制依赖 |
| `tetrachord_version` | `1.20+1.0.3` | 客户端与服务器精确硬前置 |
| `mod_group_id` | `com.example.espoints` | Maven group |

客户端和服务器都需要 Espetro、HCR AAD、Tetrachord Lib 1.20+1.0.3、MUtil、
Ping Wheel 1.12.1、EsRadial 0.3.0、ApricityUI 1.2.6 与 OELib（以及 Espetro 自身的前置）。
这些运行要求均在 `mods.toml` 声明。普通 GUI 使用 MUtil，战术类型轮盘使用 ApricityUI / EsRadial，
世界射线与 3D 标点显示复用 Ping Wheel。

Tetrachord 制品从 `dev/Forge-1.20.X` 的固定提交
`7783bce06bf0d9264df7b1c9edb8fe5aabfa6ea1` 构建；JAR SHA-256 为
`6417a7b620fa46382809214adf4dbe25fa3e9d5f7a361e54783134afd19e5feb`。Gradle 只从该
checkout 的 `build/libs` 或由同一 checkout 发布到 Maven Local 的精确坐标解析；不默认下载
无法证明提交来源的同名远程文件。当前地图热点继续
使用 O(1) map、区块索引和小集合线性扫描，因为尚无主指标改善至少 15% 且内存/延迟不退化的
基准证据，不能仅为“使用前置”强行换成 KDTree。

## `espoints-common.toml`

### HUD Settings

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enableHUD` | `true` | 总体 HUD 开关 |
| `enableCarousel` | `true` | 据点信息轮播开关 |

### Team Settings

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enableTeams` | `true` | 旧配置兼容；实际阵营固定来自 Espetro |
| `enableTeamIndicator` | `true` | 玩家头顶友军/敌军标识 |

### Performance Settings

| 字段 | 默认 | 范围 | 说明 |
| --- | ---: | ---: | --- |
| `checkInterval` | 5 | 1..100 tick | 据点检测间隔；进度按实际 elapsed tick 积分，1/5/40/100 都保持旧 40 tick 规则的墙钟速度 |
| `tacticalMapServerMemoryMiB` | 32 | 8..512 MiB | 服务端编码瓦片 LRU |
| `tacticalMapDiskCacheMiB` | 512 | 64..4096 MiB | `config/espoints/cache/tactical-map/` 磁盘预算 |
| `tacticalMapPlayerTransferKiBps` | 256 | 32..4096 KiB/s | 单玩家瓦片平均传输预算 |
| `tacticalMapGlobalTransferKiBps` | 4096 | 256..65536 KiB/s | 全服瓦片平均传输预算 |

战术地图服务端按玩家轮询发送：每玩家最多等待 64 个 tile key、全服最多 4096 个，队列不持有
PNG 大数组；每玩家和全局 burst 默认不超过对应平均速率的 2 秒额度（256 KiB/s → 512 KiB，
4 MiB/s → 8 MiB）。单张合法 PNG 若大于玩家 burst、但不超过 2 MiB 硬帽，会等到该玩家
burst 桶加满后再发送并抽空该桶，而不是永久拒绝。客户端本地 desired set 上限
256，实际 network outstanding 默认 32，每 tick 最多发 4 个请求。PNG decode 队列上限 32、
等待 GPU upload 上限 24；render thread 每 tick 最多上传 2 张，并在启动下一张前检查 2 ms 软预算。
协议仍为 13，请求包没有 priority/purpose 字段；优先级由客户端按 preview、可见 tile、邻环分批发包表达。

### Reward Settings

| 字段 | 默认 | 范围 | 说明 |
| --- | ---: | ---: | --- |
| `pointRewardInterval` | 60 | 1..3600 | 据点内周期奖励间隔，秒 |
| `pointRewardAmount` | 5 | 1..1000 | 每次据点内奖励 |
| `killRewardAmount` | 50 | 1..1000 | 击杀奖励 |
| `captureRewardAmount` | 100 | 1..1000 | 占领奖励 |
| `capturedRewardInterval` | 60 | 1..3600 | 已占据点持续奖励间隔，秒 |
| `capturedRewardAmount` | 10 | 1..1000 | 持续奖励数值 |
| `capturedRewardDelay` | 5 | 1..3600 | 首次持续奖励延迟，秒 |
| `enableFriendlyFirePenalty` | `true` | Boolean | 是否启用友军击杀惩罚 |
| `friendlyFirePenalty` | 200 | 1..10000 | 友军击杀扣分 |

### Operation Settings

| 字段 | 默认 | 范围 | 说明 |
| --- | --- | --- | --- |
| `enableOperationMode` | `true` | Boolean | 旧配置兼容；Espetro 对局固定使用行动模式 |
| `lowReinforcementThreshold` | `10.0` | 0..100 | 低兵力 BGM 阈值百分比；0 为禁用 |

## `espoints-client.toml`

```toml
[tacticalMap]
displayMode = "TOGGLE_KEY"
miniMapScale = 75
attackerProgressBarColor = "#FF5500"
defenderProgressBarColor = "#0055FF"
tileTextureCacheMiB = 64
mapImageQuality = "BALANCED"
```

| 字段 | 默认 | 可选值/格式 |
| --- | --- | --- |
| `displayMode` | `TOGGLE_KEY` | `TOGGLE_KEY` 或四角常显模式 |
| `miniMapScale` | `75` | 25..100，百分比 |
| `attackerProgressBarColor` | `#FF5500` | `#RRGGBB` |
| `defenderProgressBarColor` | `#0055FF` | `#RRGGBB` |
| `tileTextureCacheMiB` | `64` | 16..512 MiB；高级瓦片纹理 LRU，低级预览跨开关保留 |
| `mapImageQuality` | `BALANCED` | `PERFORMANCE`、`BALANCED` 或 `HIGH`；平衡/高清在视野稳定 250 ms 后分别渐进细化一/两层。低清预览和非整数像素缩放使用平滑采样，精确整数比例使用锐利采样；建议性能较好的客户端选择 `HIGH` |

所有 HCR AAD 配置页和游戏内窗口都以 MUtil 构建。界面更新采用稳定布局刷新，不再依赖逐帧淡入层。

## `hcr_map_player_display.json`

```json
{
  "showPlayerLocations": true
}
```

`showPlayerLocations` 控制服务端是否向战场玩家同步位置。管理员也可用
`/hcrpi mapctrl true|false` 即时修改。

## 地图目录

```text
<服务器根目录>/
└── EsWorld/
    └── my_map/
        ├── level.dat
        ├── region/
        └── EsConfig/
            ├── TacticalMap.json
            ├── CapturePoints.json
            └── map.png              # 可选
```

两份 JSON 都是地图必需文件。任何一份缺失或校验失败时，Espetro 会拒绝整张地图，而不是回退到旧
HCR AAD 配置。

## `TacticalMap.json`

```json
{
  "topLeftX": -512,
  "topLeftZ": -512,
  "bottomRightX": 512,
  "bottomRightZ": 512,
  "initialRange": 512,
  "minimumRange": 64,
  "backgroundImage": "map.png",
  "backgroundImageWidth": 1024,
  "backgroundImageHeight": 1024,
  "showGrid": true,
  "showLabels": true,
  "tacticalMarkerDurationSeconds": 120,
  "tacticalMarkerFadeSeconds": 120
}
```

| 字段 | 规则 |
| --- | --- |
| `topLeftX/topLeftZ` | 地图西北角世界坐标 |
| `bottomRightX/bottomRightZ` | 地图东南角，必须分别大于左上角 |
| `initialRange` | 初始可见范围，必须大于 0 |
| `minimumRange` | 最小可见范围，必须大于 0 且不超过初始范围 |
| `backgroundImage` | 可留空；非空时为同一 `EsConfig/` 内的相对 PNG 路径 |
| `backgroundImageWidth/Height` | 图片尺寸提示；不用底图时可为 0 |
| `showGrid/showLabels` | 网格和名称开关 |
| `tacticalMarkerDurationSeconds` | 标点完整显示时间 |
| `tacticalMarkerFadeSeconds` | 标点淡出时间 |

底图由 Espetro 读取后以只读字节快照分片同步，最大 16 MiB。禁止绝对路径、`..`、盘符、符号链接、
非 PNG 扩展名和伪造 PNG。客户端不再从本地任意文件路径寻找底图。

战术标点与指挥官技能目标在服务端再次校验：玩家必须位于当前活动战场、坐标必须在边界内，并且具有相应权限。

## `CapturePoints.json`

```json
{
  "totalBatches": 2,
  "endBehavior": "terminate",
  "teamReinforcements": {
    "ATTACK": 280,
    "DEFEND": 1200
  },
  "plannedPoints": [
    {
      "name": "A",
      "batch": 1,
      "pos1": {"x": -24, "y": 60, "z": -24},
      "pos2": {"x": 24, "y": 72, "z": 24}
    },
    {
      "name": "B",
      "batch": 2,
      "pos1": {"x": 104, "y": 60, "z": -24},
      "pos2": {"x": 152, "y": 72, "z": 24}
    }
  ]
}
```

| 字段 | 规则 |
| --- | --- |
| `totalBatches` | 至少 1，且不能小于任一据点的 `batch` |
| `endBehavior` | `terminate` 或 `loop` |
| `teamReinforcements.ATTACK/DEFEND` | 双方初始兵力，必须大于 0 |
| `plannedPoints` | 据点数组 |
| `name` | 单个 `A-Z` 字母，不能重复 |
| `batch` | `1..totalBatches`，每批最多 7 个据点 |
| `pos1/pos2` | `{x,y,z}` 两个不同角点，组成占领长方体 |

部署阶段开始时自动建立第一批据点。所有占领计算、位置同步和胜负消息只绑定到当前活动战场。

## 管理员导出与兼容命令

```text
/hcrpi reload
/hcrpi mapctrl <true|false>
/hcrpi teamfight loadconfig
/hcrpi teamfight saveconfig
/hcrpi teamfight start [totalBatches] [terminate|loop]
/hcrpi teamfight stop
/hcrpi teamfight nextbatch
/hcrpi teamfight save <id>
/hcrpi teamfight load <id>
```

- `loadconfig` 只重新应用当前活动地图启动时冻结的 `CapturePoints.json`，不会重新读磁盘。
- `saveconfig` 导出到 `config/espoints/exports/<地图>-CapturePoints.json`，不会覆盖地图模板。
- `reload` 只重载 HCR AAD 自己的 TOML/玩家显示设置并重新应用当前冻结快照。
- 预设命令保留给管理员兼容旧工作流，不会改变 Espetro 的地图源。

## 排错

- 地图不出现：查看 Espetro 启动日志中的地图拒绝原因。
- 地图显示“不可用”：确认客户端和服务器同时安装当前 HCR AAD，并确认活动地图已成功激活。
- 底图不显示：检查相对文件名、PNG 内容、16 MiB 上限以及 `TacticalMap.json` 边界。
- 据点不出现：确认已经进入部署阶段，并检查 `plannedPoints`、批次和坐标。
- 旧地图或标点残留：应通过正常结算或 `/espetro stop` 结束；清理事件会同时清空 HCR AAD 状态。
- JSON 必须为 UTF-8，不支持注释和尾随逗号。修改地图文件后必须完整重启服务器。
