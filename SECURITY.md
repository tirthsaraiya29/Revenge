# Security policy

## Reporting a vulnerability

Report security problems privately through GitHub Security Advisories:
https://github.com/tirthsaraiya29/Revenge/security/advisories/new

Do not open a public issue for a vulnerability. Include the affected version,
the impact, and a minimal reproduction when you can.

You can expect an acknowledgement within seven days. If the report is valid we
will agree on a disclosure date with you before publishing.

## Supported versions

Security fixes ship for the latest release only. Builds older than the most
recent release are not supported.

## Scope

In scope: the Android application, its Gradle build, CI workflows, and the
project landing page. Out of scope: third-party services Revenge talks to
(YouTube Music, LRCLIB, Last.fm, …) and modified builds distributed by others.

## Handling of credentials

Revenge never ships API keys, signing keys, or tokens in the repository. Keys
are read from `local.properties` or the environment at build time and exposed
through generated `BuildConfig` fields. If you find a committed secret, report
it privately; do not open a public issue.
