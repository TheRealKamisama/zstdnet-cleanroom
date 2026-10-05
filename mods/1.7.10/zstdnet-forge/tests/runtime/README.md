# Production API acceptance fixtures

These optional client mods are outside all Gradle production source sets.
The distributable ZstdNet JAR must not contain their classes; the repository's
`scripts/inspect-1.7.10-jar.ps1` checks that boundary.

Use a separate disposable Forge 1.7.10 / Java 25 client and dedicated-server
installation with the runtime stack in the module README. Compile the fixtures
with Java 25 against the Forge universal JAR, then package the resulting class
directory with `jar --create`. They use reflection for Minecraft's MCP/SRG names
and therefore do not require a second reobfuscation pass. Install the fixture
JAR only into the isolated client instance's `mods/` directory.

Set `-Dzstdnet.acceptance.root=<instance>/acceptance` in the client JVM arguments.
The expected game directory is the sibling `<instance>/.minecraft`.

## Dedicated commands and reports

Launch the client with `--server 127.0.0.1 --port <compressed-entry>` using a
non-operator test player and an empty reports directory. `ClientAcceptance`
checks HUD off/on/toggle/status, rejects remote host-port mutation, and requests
an unprivileged report. Read its console denial message and
`permission-denied.txt`. When `ready-for-op.txt` appears, grant the isolated test
player operator permissions on the local server, then create
`<acceptance-root>/continue.txt`. The fixture requests `today`, `session`, `24h`,
`7d`, and `30d`, checks the resulting local HTML and writes individual result
files and `passed.txt`.

Large-report validation used explicitly synthetic history: 18 previous days of
288 five-minute aggregate buckets per day in the isolated server's statistics
directory. The resulting 7-day report crossed many legacy payload chunks and
generated a 663,554-byte self-contained HTML file. Those history figures are
test data, not measured Minecraft traffic or advertised compression gains.

## Integrated server / LAN

Add `-Dzstdnet.acceptance.mode=lan`. `ClientAcceptance` disables itself and
`LanAcceptance` takes over after the initial dedicated connection. It creates
the isolated `zstdnet-acceptance-lan` world with cheats enabled and checks:

- The actual transformed production Open to LAN screen contains both fields.
- Entering ports and invoking its Start LAN handler publishes the world.
- Holding the requested relay port forces free-port fallback.
- The actual backend port is tracked, and public status queries work.
- All four host port commands persist their settings and hot reload the relay.
- The live game backend remains unchanged until republishing.
- A real multicast packet advertises the new public port after hot reload.
- The integrated admin can generate a session report over the game channel.
- Returning to the title screen stops the relay and saves the world.

These are game-API integration tests. They do not inject desktop input. Native
clipboard and OS browser-opening clicks are a separate interactive acceptance
step. Evidence and screenshots are under `docs/validation/1.7.10/`.

## Saved server and same-process reconnect

Set `-Dzstdnet.acceptance.mode=connection` and
`-Dzstdnet.acceptance.port=<entry-port>`. `ConnectionAcceptance` edits the isolated
client compression level to 7 while it is running, creates a saved server entry,
reconnects twice through the actual saved-server constructor, verifies the address
in memory and on disk, then returns to the title screen and checks session cleanup.
It restores the previous compression setting. Its markers include `config-reload`,
`reconnect-1`, `reconnect-2`, and `connection-passed`.
