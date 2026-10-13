Forge 1.7.10 / Java 25 port of the upstream ZstdNet version identified by this release tag.

Includes Zstd TCP compression, automatic client connections, dedicated-server port takeover, LAN controls, UDP forwarding, trusted PROXY v2, HUD, commands, and local traffic reports.

Requires Forge 10.13.4.1614 and Java 25, with LWJGL3ify 3.0.33 (including matching forgePatches), RetroFuturaBootstrap 1.1.1, UniMixins 0.3.1, GTNHLib 0.11.48, GTNHExtLib 1.0.4, and Hodgepodge 2.7.206. A stock Forge installation cannot run this JAR just by selecting Java 25.

Download the production JAR and optionally verify it against SHA256SUMS.txt. Installation instructions are in `mods/1.7.10/zstdnet-forge/README.md` in the source attached to this release.

The release workflow runs the automated tests and production build on Windows. Earlier production gameplay acceptance is documented under `docs/validation/1.7.10`; it is not rerun by this workflow. Other operating systems have not received runtime validation.
