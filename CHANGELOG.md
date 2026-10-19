# Changelog

## 1.1.0 — Current checked baseline

- Updated compact bottom navigation for mobile.
- Added creator-style Profile UI and progress backup/restore controls.
- Included PCB TTL placement and 45°/90° trace/layer controls from the checked HTML baseline.
- Restored Blank/Practice canvas fullscreen and made the preselection tray fullscreen-only.
- Packaged an Android WebView wrapper with native file save/restore hooks and Firebase notification/authentication scaffolding.
- Firebase provider and push functions remain dependent on project-specific configuration; no cross-device cloud progress database is included.

## Release process

For future releases, add the user-facing changes under a new version heading, run CI, test an in-place Android upgrade, then tag the exact commit as `vMAJOR.MINOR.PATCH`.
