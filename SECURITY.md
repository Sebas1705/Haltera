# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |

## Reporting a vulnerability

Open a **private security advisory** on GitHub (Security tab → Advisories → New draft advisory).
Do not open a public issue for security vulnerabilities.

## Data

Haltera stores everything on the device, in the local SQLite database (`haltera.db`).
There is no account, token storage or network layer yet; sync arrives in a later phase (F8),
and this section will describe its storage and transport rules then.
