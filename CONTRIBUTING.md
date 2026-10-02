# Contributing

Bug reports, Wiki corrections and pull requests are welcome. Please open an
[issue](https://github.com/aknaus1/best-gear-setup/issues) first for larger changes, so the approach can be
agreed before you write the code.

## Running the plugin from source

Requires JDK 11 (the [Plugin Hub](https://github.com/runelite/plugin-hub#setting-up-the-development-environment)
recommends Eclipse Temurin 11).

1. Open this folder in IntelliJ IDEA (or another IDE with Gradle support) as a Gradle project.
2. Run the `run` Gradle task (`./gradlew run`, or `gradlew.bat run` on Windows). It starts a RuneLite
   client in developer mode with the plugin loaded. Running the `main` method of
   `src/test/java/com/bestgearsetup/BestGearSetupPluginTest.java` with the VM option `-ea` does the same.
3. If you log in with a Jagex account, follow RuneLite's
   [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide.

## Checks before a pull request

- `./gradlew build` compiles the plugin and runs the Java tests.
- `python -m unittest discover -s tools -p "test_*.py"` runs the data tools' offline tests (Python 3).
- If you change `LICENSE` or `THIRD_PARTY_NOTICES.md`, copy it to `src/main/resources/META-INF/` as well.
  The Plugin Hub distributes only the JAR, so these copies are what users receive; `DistributionNoticesTest`
  fails if they differ.
- If you change calculator code (`src/main/java/com/bestgearsetup/calc`), run
  `python tools/reference_similarity.py` and review anything it flags. The calculator follows the GPL-3.0
  [OSRS Wiki DPS calculator](https://github.com/weirdgloop/osrs-dps-calc) as a formula reference, and this
  plugin is BSD-2-Clause, so implement mechanics from the game's rules and the Wiki pages rather than
  translating the reference's code. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Record calculation changes, with their sources, in [COMBAT_AUDIT.md](COMBAT_AUDIT.md), and add a line to
  [CHANGELOG.md](CHANGELOG.md).
- Don't add dependencies. The plugin uses the Plugin Hub's `build=standard`, which replaces `build.gradle` and
  provides only the RuneLite client, Lombok and JetBrains annotations; anything else needs a `build=gradle` submission with hash
  verification and a slower review.

## Bundled data

The monster, equipment, status-immunity and wear-requirement tables are generated from the OSRS Wiki by the
scripts in `tools/`. [docs/equipment-requirements.md](docs/equipment-requirements.md) describes what each
contains and how to regenerate them. Downloads need `BGS_WIKI_CONTACT` set to your email address or repository
URL, which the scripts put in the User-Agent as the
[MediaWiki API etiquette](https://www.mediawiki.org/wiki/API:Etiquette) asks. `tools/wiki_api.py` also makes
serial requests with `maxlag`, follows server-directed retries, and fails the run on API errors instead of
overwriting a cache or snapshot. After regenerating, update the
retrieval dates and counts in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (and its `META-INF` copy).

## Releasing

1. Set the same version in `runelite-plugin.properties` (`version=`, shown on the Plugin Hub) and
   `build.gradle`, and date the release in `CHANGELOG.md`.
2. Push, then update `commit=` in this plugin's file in a fork of
   [runelite/plugin-hub](https://github.com/runelite/plugin-hub#updating-a-plugin) and open a pull request.

## Licence

By contributing you agree that your contributions are licensed under the repository's
[BSD 2-Clause licence](LICENSE).
