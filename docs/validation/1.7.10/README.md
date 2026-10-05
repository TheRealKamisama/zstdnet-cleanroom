# Validation ledger — 2026-10-03–05

Timezone: Asia/Shanghai. These tests used isolated local instances under
`.work/1.7.10/`; no public server or user modpack was changed.

## Completed evidence

- `unit-test-summary.json`: 43 Java 25 tests, zero failures/errors. Includes
  JNI compressed round trips, UDP, trust/headers, parsing bounds, limits,
  timeouts, config persistence/reload, statistics and report framing/generation.
- `build.txt`: successful GTNH build, Checkstyle and production reobfuscation.
- `artifact.json`: delivery path, SHA-256, classfile versions, preserved JNI
  resources, private Gson/SLF4J, early mixin manifest and fixture exclusion.
- `runtime-content-equivalence.json`: the final packaging step adds only license
  notices. Every pre-existing entry is byte-identical to the archive validated
  in the production client/server, including all classes, natives and manifests.
- `production-server.txt`: real Forge production startup and login through the
  trusted local PROXY v2 hop. Minecraft sees synthetic source `203.0.113.7`.
- `production-reconnect.txt`: two real same-process Forge reconnects using a saved
  server, preserved address in memory/on disk, live level 7 reload and cleanup.
- `production-reports.txt`: HUD commands, remote port denial, non-operator report
  denial and all five report ranges after an operator grant.
- `production-lan.txt`: integrated world publication, production screen controls,
  free-port fallback, all host port commands, hot reload, session report and
  clean world shutdown. `lan-advertisement.txt` is the received multicast packet.
- `manual-status.json` and `manual-raw-login.json`: real production backend status
  through manual mode and rejection of raw login at the compressed entry.
- `dashboard-checks.json` and the English/light and Chinese/dark JPEGs: time
  filters, custom range, all ten presets, languages, themes and no browser errors.
- `native-ui-access.json` and `native-ui-port-copied.png`: native LAN publication
  and Zstd port chat link copied actual port 62085. A controlled sentinel was
  verified by paste first, then the copied port was pasted into local chat.
- The native dashboard chat click opened Windows' HTML app chooser. The user
  selected Microsoft Edge and confirmed the report loaded before closing it.
  This browser-opening check is user-verified; the agent did not capture Edge.
- `completion-audit.md`: requirements mapped to current implementation, build,
  production, synthetic and native evidence, including preserved scope.

The report fixture deliberately added 18 days of synthetic aggregate history.
The large totals in dashboard screenshots are **test data, not observed traffic
or a compression-performance claim**. Actual dedicated and integrated sessions
also recorded normal loopback gameplay traffic and clean run shutdowns.

The earlier `production-proxy-before-final.txt` records the same production trust
boundary before the last configuration-reload change; the later server and
reconnect logs are the evidence for the runtime-validated delivery code.

## Completed native acceptance and verification boundaries

Windows desktop access was unavailable during earlier native input validation.
After the user's RDP reconnect, native input successfully loaded the isolated
world, opened the LAN controls and pressed Start LAN World without a fixture.
The game displayed actual game port 62084 and Zstd port 62085 with the host HUD.
A known sentinel was entered, selected and the copy shortcut pressed. Access
was then lost before the port link could be clicked; refreshing the target
failed with `GetCursorPos failed: access denied (0x80070005)`. No further app
input was attempted. On the next user-authorized retry, native input succeeded.
The sentinel was verified by paste, the Zstd port link was clicked and paste
returned exactly 62085, closing the clipboard check.

The local-host `/zstdreport session` command generated a report. Clicking
**Open traffic dashboard** opened Windows' “Choose an app to open this .html
file” dialog. The chooser was visible but absent from both returned window and
app inventories, so it could not be targeted safely. The user selected
Microsoft Edge and **Always**, then confirmed: “It worked, I checked, and I
closed the edge.” This closes the browser-opening integration check using
direct user verification. The HTML association choice was made by the user;
the agent did not change the system association or capture the Edge window.

`native-ui-access.json`, the `native-ui-*.png` screenshots and
`native-ui-resumed.txt` preserve the native input and OS dispatch evidence.
The report opened was
`config/zstdnet/reports/singleplayer-9cc4c929/latest.html` in the isolated client.

All required interactive checks are closed. The final native client loaded
the exact delivery JAR, SHA-256
`d56dc39f9c971beee783793f71b68096636339c91ba8376e05e5bf331dc29aad`.
Normal window close stopped the relay and saved players and all world dimensions;
the final log is `native-ui-resumed.txt`. The dashboard's controls and rendering
were validated independently in the earlier browser tests.

## Platform and integration limits

Windows x64 Java 25 was run. Other packaged JNI targets, including Android, were
inspected but not executed on their native platforms. Generic same/separate-port
UDP forwarding was tested; no compatibility claim is made for modern third-party
mod binaries on Minecraft 1.7.10. The existing offline-backend authentication
requirement remains in effect.
