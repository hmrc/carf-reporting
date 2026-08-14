# Schemas

CARF (Crypto-Asset Reporting Framework) XSDs. The parser is pointed at
the root schema below and the JDK follows `<xs:import>` references
automatically as long as sibling files stay put.

## What's here

| File | Namespace | Role |
| --- | --- | --- |
| `CARFXML_v1.5.xsd`       | `urn:oecd:ties:carf:v1`         | **Root schema** — defines `<CARF_OECD>`, `<MessageSpec>`, `<CARFBody>`, `<RCASP>`, `<CryptoUsers>`, `<RelevantTransactions>`, etc. |
| `oecdcarftypes_v5.0.xsd` | `urn:oecd:ties:carfstf:v5`      | Common STF types — `DocSpec`, `DocTypeIndic` enum, string-length restrictions. |
| `isocarftypes_v1.1.xsd`  | `urn:oecd:ties:isocarftypes:v1` | ISO 3166-1 alpha-2 country codes and ISO 4217 currency codes. |

## Root schema

The parser defaults to `CARFXML_v1.5.xsd`.

## Source of truth

These are the official OECD schemas — **do not modify them**. The
parser and generator work around any quirks in the schemas (see the
"Things to think about" section of the top-level README).
