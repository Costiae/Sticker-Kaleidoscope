# Project conventions

- Client UI uses Kotlin + Compose Multiplatform, with Windows layout first.
- Put user-facing text in Compose Resources, including window titles and accessibility descriptions. Do not hardcode UI copy in Kotlin.
- Maintain English fallback and Chinese translations; use plurals and numbered placeholders where appropriate. See `docs/localization.md`.
- Keep navigation and domain identifiers independent of translated labels.
- Preserve Windows and Android platform boundaries; common code must not import JVM-only APIs.
- Use `scripts/dev.ps1` to select the project JDK without changing global Java settings. See README for the packaging JDK and network options.
- Verify relevant compilation and packaging after client changes. Do not claim visual or platform compatibility checks that were not performed.
- Keep `docs/feature-status.md` current when functionality changes; distinguish implemented capabilities, validated behavior, partial work, and planned features.
