# ZstdNet 文档

- English: [README.en-US.md](README.en-US.md)
- 中文: [README.zh-CN.md](README.zh-CN.md)
- Forge 1.7.10 / Java 25: [独立移植模块与安装说明](mods/1.7.10/zstdnet-forge/README.md) · [功能与验证矩阵](docs/1.7.10-parity-matrix.md)
- Forge 1.7.10 移植版通过 [GitHub Releases](https://github.com/TheRealKamisama/zstdnet-cleanroom/releases) 发布；上游现代版本仍请前往 CurseForge 下载。

## 省流版

### 当前支持版本

- Forge 1.7.10（Java 25，需要下方列出的运行时组件）
- Forge 1.20.1
- NeoForge 1.20.1
- NeoForge 1.21.1
- Fabric 1.20.1
- Fabric 1.21.1

### Forge 1.7.10 / Java 25 移植版

1.7.10 移植已完成，独立模块位于 `mods/1.7.10/zstdnet-forge`。支持自动客户端连接与 Zstd TCP 压缩、独立服务器端口接管、局域网端口设置与广播、UDP/语音转发、可信 PROXY v2 与真实 IP 转发、HUD、命令和持久化流量统计 HTML 面板。

该版本需要 Minecraft Forge `10.13.4.1614` 和 Java 25，并配套安装 LWJGL3ify `3.0.33`（含对应 forgePatches）、RetroFuturaBootstrap `1.1.1`、UniMixins `0.3.1`、GTNHLib `0.11.48`、GTNHExtLib `1.0.4` 和 Hodgepodge `2.7.206`。客户端与服务器的安装、启动参数、构建命令和配置请参阅[1.7.10 安装与构建说明](mods/1.7.10/zstdnet-forge/README.md)。

已通过 43 项自动化测试，以及 Windows x64 / Java 25 下的生产 JAR 客户端和独立服务器验证，覆盖登录、重连、LAN、端口命令、报告权限和全部报告时间范围。局域网端口点击复制已验证；报告链接在 Edge 中打开由用户确认。其他平台的 JNI 资源已打包，尚未在对应系统上运行验证。详细证据见[功能与验证矩阵](docs/1.7.10-parity-matrix.md)和[完成审计](docs/validation/1.7.10/completion-audit.md)。

## Velocity版本

https://github.com/wish131400/zstdnet-Velocity
VC版本因为兼容性原因，需要下载velocity插件版本

## spigot的插件版
https://github.com/Meoyuta/ZstdNet-spigot
感谢 https://github.com/Meoyuta 移植的插件版本

### 客户端联机配置

安装好mod后，直接打开局域网即可。游戏端口通常可以留空，ZstdNet 会自动跟随本次实际 LAN 端口；Zstd 端口会优先使用配置里的端口，如果被占用会自动换到可用端口。开放成功后聊天框会提示实际 Zstd 端口，并且端口可以点击复制。如果使用高级联机 mod 完全替换界面导致看不到 Zstd UI，可以用 `/zstdport show` 查看当前端口；只有需要固定公网/隧道端口时，才用 `/zstdport zstd xxxxx` 手动指定。
有正版验证需求，可以额外搭配 [TrueUUID（正版离线共存）](https://www.curseforge.com/minecraft/mc-mods/trueuuid)。使用/zstdhud on指令可以查看压缩状态。

### 服务器配置

安装好 mod 后，最小配置只需要在 `server.properties` 里关闭正版验证：

```properties
online-mode=false
```

然后正常启动服务器即可体验带宽压缩。公网直连、局域网、虚拟局域网、FRP / 反代等详细配置请看 [中文完整文档](README.zh-CN.md)。
有正版验证需求，可以额外搭配 [TrueUUID（正版离线共存）](https://www.curseforge.com/minecraft/mc-mods/trueuuid)。使用/zstdhud on指令可以查看压缩状态。

### 本地流量统计面板

拥有 2 级命令权限的玩家可使用 `/zstdreport today|session|24h|7d|30d` 生成自包含 HTML 面板。面板支持时间筛选、深浅模式、10 套主题色以及 `KiB/s` / `Mbps` 带宽参考；详细说明见 [中文完整文档](README.zh-CN.md#本地流量统计面板) 和 [面板设计文档](docs/design/zstdnet-report-dashboard.md)。

# curseforge

- https://www.curseforge.com/minecraft/mc-mods/zstdnet

# 鸣谢
- [齿轮盛宴官方网站]( https://www.xn--dctt54dhmrbwo.com/ )
- [量子科技官方网站]( https://www.mcplay.cc/ )
- [本项目灵感来源]( https://github.com/MeguminKato )
