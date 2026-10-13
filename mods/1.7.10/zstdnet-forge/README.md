# ZstdNet for Forge 1.7.10 / Java 25

This module ports the existing ZstdNet relay, UDP forwarding, HUD, LAN controls,
statistics and local HTML reports to Minecraft 1.7.10. It does not translate
Minecraft versions. Both endpoints need compatible 1.7.10 Forge/mod protocols.

**Scoped migration and required acceptance are complete.** See [the parity matrix](../../../docs/1.7.10-parity-matrix.md)
for the distinction between compilation, synthetic transport, development
runtime and production gameplay evidence. Do not treat the development JAR as
a distributable release.

## Build

The maintained branch is `1.7.10-java25`. `portVersion` in `gradle.properties`
tracks the checked-in upstream version with a `-forge-1.7.10` suffix.
GitHub Actions builds and tests branch pushes and pull requests. Pushing the
matching `v<portVersion>` tag publishes the production JAR and SHA-256 checksum
to GitHub Releases after the build passes. Manual workflow runs only build.

The foundation is GTNewHorizons ExampleMod1.7.10 starter, SHA-256
`0e1bb6482eb16e391927900bca30fd03dbe256845dcd586bc19b61413b22e673`.
Pinned build: GTNHGradle `2.0.33`, RetroFuturaGradle `2.0.5`, Gradle `9.7.1`,
Forge `10.13.4.1614`, stable MCP mappings `12`.

From the repository root, on Windows:

```powershell
./scripts/build-1.7.10.ps1 -JavaHome 'C:/path/to/jdk-25' `
    -ProxyUri http://localhost:7897 -Tasks @('test', 'build')
```

The proxy URI is an invocation argument. The script sets HTTP and HTTPS proxy
environment variables and Java proxy properties for the wrapper, Gradle,
toolchain provisioning, and child Java processes. It restores the calling
process environment afterward. It does not modify machine-wide proxy or Java
settings. There is no direct-download fallback. Loopback gameplay bypasses
the download proxy. An optional `-AdditionalJavaHomes` array lets Gradle reuse
already-installed helper JDKs; RFG uses Java 17 to decompile and Java 8 to
recompile Minecraft itself.

ZstdNet's main code compiles to Java 25 classfiles. The small vanilla-facing
mixin source set uses Java 8 classfiles because UniMixins 0.3.1's compatibility
level check rejects `JAVA_25` metadata. This does not downgrade the mod or its
stdlib usage. No JVM Downgrader is used for ZstdNet.

The production JAR is under `build/libs/`, with **no `-dev`, `-preshadow`, or
`-sources` suffix**. The GTNH build reobfuscates the shaded JAR. `zstd-jni`
`1.5.7-7` is included without relocating JNI packages or removing native
resources. Gson `2.10.1` and SLF4J `2.0.17` are privately relocated to avoid
conflicts with Minecraft's older libraries.

Development launch commands use the same script with `-Tasks @('runClient25')`
or `-Tasks @('runServer25')`. **With this pinned plugin, plain `runClient` and
`runServer` still select Java 8.** The `25` tasks select a Java 25 JetBrains
runtime and the corresponding GTNH compatibility stack. Check the log's
`Java is ... version ...` line rather than inferring the JVM from `.java-version`.

## Runtime installation

The tested development stack resolves:

| Component | Version |
| --- | --- |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Java | 25.0.4.1 |
| LWJGL3ify, including matching forgePatches | 3.0.33 |
| RetroFuturaBootstrap | 1.1.1 |
| UniMixins (all modules, including GTNHMixins) | 0.3.1 |
| GTNHLib | 0.11.48 |
| GTNHExtLib | 1.0.4 |
| Hodgepodge | 2.7.206 |

Use the matching **production** mod artifacts for a normal installation, not
the `-dev` artifacts used by Gradle. LWJGL3ify's launcher patches and extra
libraries are required; simply choosing Java 25 for an unmodified Forge
installation is insufficient.

Client: follow [LWJGL3ify's installation instructions](https://github.com/GTNewHorizons/lwjgl3ify).
Import its matching MultiMC/Prism instance patch, select Java 25, install the
runtime mods above and the production ZstdNet JAR in `mods/`. Prism reads the
extra JVM arguments from the patch; MultiMC needs LWJGL3ify's published JVM
arguments, including the RFB system classloader, module opens and native access.

Dedicated server: install Forge 1.7.10-10.13.4.1614, the runtime mods, the
matching LWJGL3ify `forgePatches` launcher and its `java9args.txt`. Put ZstdNet
in `mods/`, then launch with Java 25:

```text
java -Xms1G -Xmx4G @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui
```

Review and accept Minecraft's EULA yourself for your installation. Set
`online-mode=false` in the backend's `server.properties`, as required by the
existing ZstdNet deployment model. ZstdNet does not supply premium-account
verification. Do not expose the local backend port. Minecraft 1.7.10 has no
vanilla packet-compression threshold, so no threshold setting or synchronization
packet is added by this port.

## Connections and hosting

The client detects the compressed entry using a capability in its normal status
response, starts a local TCP/UDP relay, and preserves the remote saved address
and handshake suffix. Ordinary compatible servers remain usable in raw mode.
Status pings remain uncompressed. A raw login to a compressed entry is rejected.

Dedicated defaults: `enabled=true`, `auto_takeover=true`. The public entry keeps
`server.properties`'s port; only the in-memory Minecraft bind moves to a free
loopback backend port. Restarting retains the original public port. Manual mode:

```properties
enabled=true
auto_takeover=false
listen=0.0.0.0:35565
target=127.0.0.1:25565
```

For LAN, the Open to LAN screen provides game and Zstd port fields. The relay
tracks the game port actually bound; a configured game-port change applies to
the next publication. The public relay selects another available port on a
conflict. LAN advertisement follows the active public port, including hot
reload. The chat port link copies it using a local command because 1.7.10 has no
native `COPY_TO_CLIPBOARD` chat action. LAN backends use offline authentication,
matching the existing modules.

The client forwards raw UDP on its local TCP port. The server forwards game
UDP on the public relay port and supports a separate voice route. A UDP bind
failure does not prevent TCP operation. Forward both TCP and UDP through any
external tunnel that needs same-port UDP. Generic forwarding does not imply
availability or compatibility of modern third-party voice/game mods on 1.7.10.

## Commands, config and reports

- `/zstdhud [status|on|off|toggle]` controls the persistent client HUD.
- `/zstdport show` displays game/Zstd and voice ports.
- `/zstdport game|zstd|voice|zstdvoice <port>` changes only local integrated-server
  configuration and requires the local server owner with permission level 2.
  Remote dedicated-server configuration is edited on that server.
- `/zstdreport today|session|24h|7d|30d` requests aggregated statistics. The server
  checks permission level 2. Only JSON aggregates cross the game connection;
  responses are chunked below the legacy payload limit, capped at 1 MiB, and
  discarded on invalid sequencing, timeout or disconnect.

Client settings use Forge 1.7.10's `config/zstdnet-client.cfg`: `general.level`
defaults to 3 (1–22) and `general.hud` defaults to true. This intentionally replaces
modern Forge's TOML config API. Edited client compression levels are reloaded for
the next connection; HUD commands preserve the level when saving. Server settings remain
`config/zstdnet-server.properties`, including hot reload and preservation of
unknown custom keys during writes.

| Server setting | Default / meaning |
| --- | --- |
| `enabled`, `auto_takeover` | true / true for a generated dedicated config |
| `listen`, `target` | Resolved automatically in takeover mode; explicit endpoints in manual mode |
| `level` | 9, clamped to 1–22 |
| `flush_interval` | 2ms; 0 flushes each write |
| `max_conn_per_ip` | 9999; <=0 disables |
| `max_conn_total` | 128 |
| `max_req_per_window` | 50 per IP; <=0 disables |
| `max_req_total_per_window` | 600 |
| `max_worker_threads` | 384 |
| `request_window`, `ban_duration` | 10s / 1m |
| `handshake_timeout`, `raw_status_timeout` | 3s / 2s |
| `status_cache_ttl` | 2s |
| `stats_interval`, `idle_timeout` | 0s / 0; disabled |
| `max_rate_per_conn_bps`, `max_rate_global_bps` | 0 / 0; disabled; compressed outbound byte limits |
| `burst_bytes` | 262144 |
| `voice_chat_passthrough` | true |
| `voice_chat_listen` | Public voice UDP endpoint; generated/configured default 24455 |
| `voice_chat_target` | Dedicated default 127.0.0.1:24454; client/LAN config default 127.0.0.1:25565 |
| `trust_proxy_protocol` | false; true requires a valid PROXY v2 header on every public-entry connection |
| `trusted_proxy_ips` | Loopback IP literals; only listed upstream proxy machines may supply source addresses |

Explicit blank LAN voice endpoints retain the existing runtime behavior of
following the active LAN game port. Explicit nonblank endpoints are preserved.

History is under `config/zstdnet/stats/`, with daily totals, five-minute series,
session recovery and separate TCP/UDP, direction and raw/wire counters. Reports
are generated locally under `config/zstdnet/reports/<server-id>/latest.html`,
with ten retained archives per server. The self-contained dashboard has English
and Chinese UI, time filters, custom ranges, themes and binary byte/s plus Mbps
bandwidth references. The client adds a clickable local file link in chat.

## Validation boundaries

Windows x64 Java 25 JNI compression/decompression, synthetic TCP/UDP, status,
PROXY trust, limits, chunk framing and report/statistics tests have run. Production
client/server login, world traffic, real-IP forwarding, dedicated auto/manual
entry, integrated LAN screen handlers, multicast updates, commands, report
permissions and every report range have been exercised. The loaded-world HUD
was visually observed. Native LAN publication and clipboard-copy passed. The
agent observed the report chat click opening Windows' HTML app chooser; the
user selected Microsoft Edge and confirmed the report loaded. Browser opening
is user-verified, with no agent screenshot of Edge claimed.
Other JNI platforms and Android resources are packaged but have not been run
on those operating systems. See the parity matrix for the final acceptance ledger.
