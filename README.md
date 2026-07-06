# Arcor en Casa — Local Onboarding

Desktop wizard that sets up a local **Arcor en Casa** WordPress environment on top of a
WordPress + MySQL stack you already created (Devilbox, Local, XAMPP, …).

## Requirements
- Java 17+
- git (with your Bitbucket SSH key configured)
- A running, empty WordPress + MySQL stack

## What it does
1. Clones the plugin (`aec-plugin`) and theme (`aec-theme`) into `wp-content/`.
2. Configures `wp-config.php` (DB credentials, `WP_HOME`/`WP_SITEURL` = `http://arcorencasa.local`).
3. Imports the database dump you downloaded from Drive.
4. Creates a WordPress admin user.
5. Activates the plugin and theme.
6. Adds `127.0.0.1 arcorencasa.local` to your hosts file.

## Run
```
java -jar arcor-onboarding.jar
```
Fill the form (defaults match Devilbox), pick your WP root and the dump file, then
**Run onboarding**. When it finishes, open http://arcorencasa.local.

## Config overrides
Repo URLs, branches and defaults live in the embedded `config.properties`. To override
without rebuilding, drop a `config.properties` next to the JAR (same keys).

## Notes
- The hosts entry needs admin rights. If it can't be written, the log prints the exact
  line to add manually.
- Keep the domain `arcorencasa.local` and DB name `arcorencasa` to avoid URL rewrites.
