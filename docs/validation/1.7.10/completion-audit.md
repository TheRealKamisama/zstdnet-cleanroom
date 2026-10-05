# Completion audit — Forge 1.7.10 / Java 25

Audited 2026-10-04/05, Asia/Shanghai, against
`docs/1.7.10-java25-migration-plan.md`, including the user's correction of the
download proxy to `http://localhost:7897`.

The scoped migration is complete. The table maps each numbered feature requirement
and delivery gate to evidence inspected at closure. Detailed implementation/source
references remain in `docs/1.7.10-parity-matrix.md`.

| Requirement | Inspected evidence and result |
| --- | --- |
| 1. Automatic client relay, endpoint detection, ordinary servers, status, handshake suffix and saved address | `RelayIntegrationTest` exercises AUTO RAW/ZSTD, status, actual compressed round trips and preserved Forge suffix. `production-reconnect.txt` records two real saved-server reconnects and unchanged persisted addresses. |
| 2. Dedicated manual/auto entry, backend binding, stop/reconnect | `production-server.txt` records Java 25 startup, public-entry takeover, loopback backend, Forge login and clean stop. `manual-status.json` and `manual-raw-login.json` pass. Relay tests cover idle shutdown and port reuse. |
| 3. LAN actual/free ports, advertisement, controls and clickable port | `production-lan.txt` records transformed controls, held-port fallback, all host mutations, real status and multicast. `lan-advertisement.txt` contains the actual public port. Native input separately started LAN with game 62084/Zstd 62085; `native-ui-port-copied.png` verifies the link copied 62085 after a controlled sentinel. |
| 4. Client same-port UDP and server same/separate voice routes | `LocalZstdNetUdpPassthroughTest`, `ServerProxyRuntimeVoiceChatTest` and `RelayIntegrationTest` cover actual datagram forwarding, separate clients, route defaults/cleanup and compressed TCP continuing with occupied UDP. Production logs show the routes binding. |
| 5. Real IP, trusted PROXY v2, bounds, rejection, limits, bans, timeouts, rates/burst | `ServerRealIpHooksTest`, `PacketLimitsTest`, `RelayIntegrationTest` and `TokenBucketLimiterTest` cover malformed literals/markers, oversized packets, absent/untrusted headers, caps, ban recovery, deadlines, idle timeout and shared concurrent rate limits. Production Forge login logs synthetic source `203.0.113.7`. |
| 6. Settings/defaults, levels, flush, persistence and reload | `ClientConfigTest` and `ServerConfigTest` pass for save/reload, defaults, extra keys and restricted binds. `production-reconnect.txt` uses live compression level 7. Production host commands persist and reload the relay; the module guide documents all settings and defaults. |
| 7. HUD fields, status/on/off/toggle and lifecycle | `ClientAcceptance.java` checks each command and persisted state; `production-reports.txt` records success. Native world screenshots show the host HUD; earlier production checks cover both panels. Disconnect/shutdown tests and reconnect logs cover cleanup. |
| 8. All port commands and permissions | `ClientAcceptance.java` checks remote mutation leaves local configuration unchanged. `LanAcceptance.java` and `production-lan.txt` verify game/zstd/voice/zstdvoice mutations, persistence, hot reload and the actual backend remaining fixed until republishing. |
| 9. Every report range, permission level 2, aggregate transport, storage and dashboard | `production-reports.txt` records non-operator denial followed by today/session/24h/7d/30d success. The 7-day fixture produces 663,554-byte HTML over real legacy channels. `ReportTransferTest` covers bounded multi-chunk Unicode, ordering, malformed data, timeout and disconnect. Statistics and generation tests cover TCP/UDP totals, daily/series/run storage, interrupted-run recovery, self-contained HTML and ten archives. `dashboard-checks.json` verifies filters, custom ranges, ten palettes, languages, themes, units and no console errors. |
| 9. Native report opening | Agent input generated `/zstdreport session` and clicked the local file link; `native-ui-html-app-chooser.png` records Windows dispatch. The user selected Edge and confirmed the report loaded, then closed Edge. This browser check is explicitly user-verified; no agent Edge screenshot is claimed. |
| 10. English/Chinese, platform handling and compatibility fallbacks | Both language resources and Android native resources are present. `artifact.json`/the re-run JAR inspector verify 22 packaged native libraries, unrelocated JNI and private Gson/SLF4J. Windows x64 JNI is exercised by real compressed transport and production gameplay. Other platform runtime coverage is limited as stated below. |
| Pinned starter/build and minimal Java 25 runtime | Starter archive hash was rechecked: `0e1bb6482eb16e391927900bca30fd03dbe256845dcd586bc19b61413b22e673`. Settings pin GTNHGradle 2.0.33; module properties pin Forge 10.13.4.1614, mappings 12 and modern syntax. Starter Java-25 run logs and production logs establish the runtime. Plain run tasks' Java-8 behavior is documented; explicit `runClient25`/`runServer25` are provided. |
| Production loader/remapping, modern bytecode and dependency packaging | `build.txt` succeeds with Checkstyle and `reobfJar`. The JAR inspector was run again: main major 69, mixin major 52, early coremod registration, no conflicting TweakClass/MixinConfigs and no acceptance fixture classes. Production client/server runs use Forge/LWJGL3ify/RFB/UniMixins, rather than a development-only loader. |
| Artifact equality and notices | Delivery SHA-256 is `d56dc39f9c971beee783793f71b68096636339c91ba8376e05e5bf331dc29aad`, 9,129,086 bytes. The final native client's installed archive has that exact hash. `runtime-content-equivalence.json` records that the final notice additions preserve all earlier production-tested entries. The inspector checks project and third-party notices. |
| Meaningful automated validation | Parsed the actual current JUnit XML: 11 suites, 43 tests, zero failures/errors/skips. Inspected transport and statistics tests assert observable I/O, bounds, persistence, counters and recovery. Results match `unit-test-summary.json`. No code changed during the final native checks. |
| Reproducible installation/build/delivery docs | Module `README.md` supplies proxied build, Java/runtime versions, client Prism/MultiMC installation, dedicated launcher arguments, offline-backend requirement and configuration/command usage. Root English/Chinese READMEs link the module and matrix. `scripts/build-1.7.10.ps1` and `scripts/inspect-1.7.10-jar.ps1` are present. |
| Proxy and machine-setting constraints | Build/starter logs show HTTP and HTTPS Java proxy properties at 7897; the build script uses explicit proxy arguments, restores process environment and has no direct fallback. `.work/` is ignored. Final native validation reused cached dependency JARs created at 01:16:40, before its 23:01 launch; the dependency-loader banner does not indicate new asset downloads. The user made the Edge HTML association choice. |
| Preserved scope and branch | At validation closure, branch was `codex/1.7.10-java25` with baseline HEAD `c9161fbe7c90cd33f50b73fb4e73d4e9b179dd47`. Git diff against baseline is empty for `mods/1.20.1`, `mods/1.21.1` and `shared`. The unrelated untracked assessment remains present with SHA-256 `24b408d67758db1be2696a3cf350fe61900ef0a008f3024c1143c6026cec53af`, unchanged from before the native checks. Changes were uncommitted during validation. On 2026-10-05 the user subsequently authorized committing and pushing this migration branch; release publication remains outside this request. |
| Final cleanup | The isolated client exited with code 0 after normal window close. `native-ui-resumed.txt` records relay stop, player save and all world dimensions saved. The isolated production/helper process inventory is empty. |

## Explicit limits

Windows x64 Java 25 was executed. Other packaged JNI targets, including Android,
were inspected but not executed on their native platforms. Generic UDP routing
was validated with synthetic datagrams; modern third-party mod binaries are not
claimed compatible with Minecraft 1.7.10. Dashboard history fixtures are synthetic
test data, not performance claims. The existing offline-backend authentication
requirement remains; the port adds neither account verification nor version
translation. Minecraft 1.7.10 has no vanilla packet-compression threshold, so that
modern-only feature is correctly inapplicable.

No required acceptance checks remain open within this scope.
