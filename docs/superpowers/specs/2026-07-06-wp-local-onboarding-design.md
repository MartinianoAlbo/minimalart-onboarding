# Design — WordPress Local Onboarding (Arcor en Casa)

- **Date:** 2026-07-06
- **Status:** Approved (design), pending implementation plan
- **Owner:** alvaro.albornoz@minimalart.co

## 1. Context & problem

Every new developer joining the team must stand up a local **Arcor en Casa (AeC)**
WordPress environment. Today this is manual and error-prone:

1. The dev creates an empty WordPress + MySQL stack with whatever tool they prefer
   (Devilbox, Local, XAMPP, Laragon, MAMP…).
2. We hand them a database dump over a Google Drive link; they download, unzip and
   `mysql < arcorencasa.sql` by hand.
3. They clone the custom plugin and theme from git by hand.
4. In between there are undocumented "glue" steps: creating a WordPress user, the
   `arcorencasa.local` host entry, `wp-config.php` values, activating plugin/theme.

The goal is a **cross-OS desktop application** that automates the AeC-specific steps on
top of the stack the developer already created, removing the drip-feed of manual
instructions.

Fixed conventions that avoid extra work (URL search-replace, renames):

- Domain: **`arcorencasa.local`**
- Database name: **`arcorencasa`**

## 2. Goals / non-goals

**Goals**

- Automate: clone plugin+theme, import DB dump, create admin user, add host entry,
  configure `wp-config.php`, activate plugin+theme.
- Run identically on Windows, macOS and Linux.
- Assume only **Java 17+** and **git** on the host.

**Non-goals (out of scope)**

- Provisioning the WordPress/MySQL stack itself (the dev owns that).
- Downloading the dump automatically from Drive (the dev downloads it manually;
  the app locates it via a file picker).
- Coupling to any specific local stack (no `docker exec`, no assumption that
  `mysql`/`wp-cli` live on the host PATH).

## 3. Key decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Assume the WP+MySQL stack already exists | Respects each dev's tooling; smallest useful scope. |
| 2 | Dump is located by the dev via a file picker button | Keeps the human in the loop for Drive auth; avoids fragile Drive automation. |
| 3 | GUI wizard built with **Swing** | Ships in the JDK, no extra deps, easiest single-JAR cross-OS packaging. |
| 4 | **Self-contained execution** (JDBC + direct file/DB manipulation) + **system git** for clone | Portable: only assumes Java + git. MySQL is reachable on `127.0.0.1:3306` even under Devilbox; `wp-config.php` is a host file. System git reuses the dev's SSH keys for the private repos. |
| 5 | Distributed as an executable **fat JAR** (Maven Shade) | Simplest for an MVP; devs have/install Java easily. |
| 6 | Code structured as a **Step pipeline** with a thin Swing view (Enfoque A) | Single responsibility per step, open/closed for new steps, testable without GUI. |
| 7 | Branding applied via **FlatLaf** + Minimalart tokens/fonts | The `minimalart-brand` skill targets CSS/web; FlatLaf lets us map the same tokens into Swing with one lightweight dependency. |

## 4. Domain model (ubiquitous language)

Identifiers in English (per team convention).

- **`OnboardingContext`** — carries all run parameters: `wpRootPath`, `mysql`
  (`MysqlConnection`: host/port/user/password/`database`=`arcorencasa`), `dumpFile`,
  `adminUser` (`WpAdminUser`: username/password/email), `siteUrl`
  (`http://arcorencasa.local`), repository config.
- **`OnboardingStep`** — interface: `String name()`, `void validate(OnboardingContext)`,
  `void execute(OnboardingContext, ProgressReporter)`.
- **`OnboardingRunner`** — runs steps in order, fail-fast, reports progress.
- **`ProgressReporter`** — callback for percent + log lines; implemented by the UI via
  `SwingWorker.publish`.
- **`StepException`** — thrown by a step with a developer-facing message + cause.

## 5. Architecture (Enfoque A — Step pipeline + thin Swing view)

Package root: `co.minimalart.arcoronboarding`

```
├─ App                      main: install theme + launch wizard
├─ domain/                  OnboardingContext, MysqlConnection, WpAdminUser,
│                           OnboardingStep, OnboardingRunner, ProgressReporter, StepException
├─ steps/                   CloneRepositoriesStep, ConfigureWpConfigStep,
│                           ImportDatabaseDumpStep, CreateAdminUserStep,
│                           ActivatePluginAndThemeStep, RegisterHostEntryStep
├─ infra/                   GitClient (shell-out), MysqlGateway (JDBC),
│                           WpConfigFile (read $table_prefix / edit), HostsFile (OS-aware),
│                           PhpSerializer, WordPressPasswordHasher, ArchiveExtractor
├─ config/                  AppConfig (embedded defaults + optional config.properties)
└─ ui/                      OnboardingWizard, ParametersPanel, ProgressPanel,
                            theme/ (MinimalartTheme, BrandColors, BrandFonts)
```

Each step has one responsibility and depends on a single `infra/` collaborator. Adding a
future step (e.g. `wp search-replace`, cache clear) is a new class in `steps/` with no
changes elsewhere (open/closed). The UI is a thin view: it only collects parameters and
renders progress, so all logic is testable without Swing.

## 6. Pipeline & data flow

1. **ParametersPanel**: form pre-filled with Devilbox defaults; buttons pick the WP root
   folder and the dump file.
2. On "Run", an `OnboardingContext` is built and `OnboardingRunner` executes on a
   `SwingWorker` (never on the EDT):
   - **Validate phase** — every step's `validate()` runs first, before any mutation:
     the folder is a valid WP install, the dump file exists, MySQL responds, `git` is on
     the PATH.
   - **Execute phase** — in order:
     1. `CloneRepositoriesStep`
     2. `ConfigureWpConfigStep` (reads the real `$table_prefix`)
     3. `ImportDatabaseDumpStep`
     4. `CreateAdminUserStep`
     5. `ActivatePluginAndThemeStep`
     6. `RegisterHostEntryStep`
3. **ProgressPanel**: live progress bar + log; on success shows `http://arcorencasa.local`
   and the created user.

**Avoiding search-replace:** `ConfigureWpConfigStep` writes `WP_HOME`/`WP_SITEURL` =
`http://arcorencasa.local` in `wp-config.php`. These constants override whatever URLs the
dump carries, honoring the fixed-naming convention.

## 7. Steps (self-contained behavior)

- **CloneRepositoriesStep** — shell-out to the dev's `git` (reuses their SSH keys):
  plugin `arcorencasa` → `wp-content/plugins/`, theme `arcor` → `wp-content/themes/`.
  If a repo already exists, `pull` (idempotent).
- **ImportDatabaseDumpStep** — if the file is a `.zip`, `ArchiveExtractor` extracts the
  `.sql`; creates the `arcorencasa` DB if missing and imports it via `MysqlGateway` (JDBC).
- **ConfigureWpConfigStep** — `WpConfigFile` verifies/writes DB credentials,
  `WP_HOME`/`WP_SITEURL`, `WP_DEBUG`; reads `$table_prefix` for the DB steps.
- **CreateAdminUserStep** — inserts/updates `{prefix}users` / `{prefix}usermeta` via JDBC;
  `WordPressPasswordHasher` produces a legacy 32-char MD5 hash. WordPress accepts a bare
  MD5 on login and transparently rehashes it on first successful login — universal across
  WP versions and trivially testable with a known vector. The class is isolated, so
  switching to phpass later is a localized change. Administrator role serialized via
  `PhpSerializer`. Idempotent (upsert by `user_login`).
- **ActivatePluginAndThemeStep** — sets `active_plugins` (serialized array including
  `arcorencasa/…`) and `template`/`stylesheet` = `arcor` in `{prefix}_options`.
- **RegisterHostEntryStep** — adds `127.0.0.1 arcorencasa.local` to the OS hosts file
  (`/etc/hosts` or `C:\Windows\System32\drivers\etc\hosts`). Checks before adding
  (idempotent); if permissions are missing, it does not abort the run — it prints the
  line for the dev to add manually.

## 8. Branding in Swing

The `minimalart-brand` skill targets CSS; we translate it to Swing with **FlatLaf** (one
lightweight dependency, bundled in the fat JAR).

- **`MinimalartTheme`** registers the TTFs (`Fraunces_72pt-SemiBold`,
  `ZalandoSans-Regular/Medium/SemiBold`) via `Font.createFont` +
  `GraphicsEnvironment.registerFont`, then applies tokens through FlatLaf:
  - accent = **Celeste `#4B8EEF`**, surface = neutral-10 `#F8FAFC`,
    text = neutral-1 `#0F172A`, border = neutral-8, muted text = neutral-5.
  - UI/body font = **Zalando Sans**; wizard titles = **Fraunces SemiBold**.
  - Rounded buttons/inputs (FlatLaf arc); primary button in the accent color.
- **Header**: celeste band with the negative logo
  (`14_logo_negativo_fondo_transparente.png`); window icon = favicon (`32_…`).
- Brand TTFs and the required logo PNGs are copied into `src/main/resources/brand/`.

`BrandColors` / `BrandFonts` hold the token constants so no hex/font name is hardcoded
across the UI.

## 9. Configuration & defaults (ETC)

`AppConfig` loads embedded defaults plus an optional external `config.properties` (change
without recompiling):

- repository URLs + branch,
- `db.name=arcorencasa`, `site.url=http://arcorencasa.local`,
- Devilbox defaults pre-filled in the form: host `127.0.0.1`, port `3306`, user `root`,
  empty password.

## 10. Error handling & robustness

- Global `validate()` before any mutation; stop at the first error with a clear message +
  underlying cause in the log. Passwords are never logged.
- Steps are **idempotent** so re-running after fixing an issue is safe.
- Hosts elevation: if permission is missing, the step degrades to a manual instruction
  instead of failing the whole run.

## 11. Known technical risk — large SQL import

Importing a large dump via JDBC is the most delicate part: splitting the `.sql` naively on
`;` breaks on semicolons inside data, `DELIMITER` changes, triggers, etc. Mitigation: a
tested SQL statement splitter that respects quoted strings, comments and `DELIMITER`; and
as an optional accelerator, if a `mysql` binary is detected on the PATH, use it for the
import only. This is the area to treat with the most care during implementation.

## 12. Testing strategy

- Each step is testable without the GUI: `WpConfigFile` / `HostsFile` against temp files
  (injectable path), `PhpSerializer` and `WordPressPasswordHasher` against known-good
  values, the SQL splitter against tricky fixtures.
- DB steps: optional integration test against MySQL via Testcontainers (out of MVP).

## 13. Build & distribution

Maven + Shade plugin → a single executable `arcor-onboarding.jar`. Runs with `java -jar`
or double-click. Assumes Java 17+.

## 14. Future (explicitly out of scope now)

- Automatic Drive download.
- Native installers via jpackage (bundled JRE).
- Extra steps (`wp search-replace`, cache clear) — trivially added as new `steps/`.
