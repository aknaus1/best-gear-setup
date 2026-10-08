# Contributing

Bug reports, Wiki corrections and pull requests are all welcome. For bigger changes, please open an
[issue](https://github.com/aknaus1/best-gear-setup/issues) first so we can agree on the approach before you
write the code.

## Running the plugin from source

You'll need JDK 11 (the [Plugin Hub](https://github.com/runelite/plugin-hub#setting-up-the-development-environment)
recommends Eclipse Temurin 11).

1. Open this folder as a Gradle project in IntelliJ IDEA, or any other IDE with Gradle support.
2. Run the `run` Gradle task (`./gradlew run`, or `gradlew.bat run` on Windows). This starts a RuneLite
   client in developer mode with the plugin loaded. You can also run the `main` method of
   `src/test/java/com/bestgearsetup/BestGearSetupPluginTest.java` with the VM option `-ea`.
3. If you log in with a Jagex account, follow RuneLite's
   [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide.

## Before you open a pull request

The [Build workflow](.github/workflows/build.yml) runs the first two checks below on every pull request. It
also runs `python .github/scripts/check_plugin_manifest.py` (the Plugin Hub's checks on
`runelite-plugin.properties` and `icon.png`) and compiles `src/main` with the Plugin Hub's own standard
build file. It runs weekly against the newest RuneLite release too, because the Plugin Hub rebuilds every
plugin whenever RuneLite updates.

- `./gradlew build` compiles the plugin and runs the Java tests.
- `python -m unittest discover -s tools -p "test_*.py"` runs the data tools' offline tests (Python 3).
- If you change `LICENSE` or `THIRD_PARTY_NOTICES.md`, copy the file to `src/main/resources/META-INF/` as
  well. The Plugin Hub only ships the JAR, so those copies are what users actually get, and
  `DistributionNoticesTest` fails if they don't match.
- If you change calculator code (`src/main/java/com/bestgearsetup/calc`), run
  `python tools/reference_similarity.py` and look over anything it flags. The calculator uses the GPL-3.0
  [OSRS Wiki DPS calculator](https://github.com/weirdgloop/osrs-dps-calc) as a formula reference, but this
  plugin is BSD-2-Clause, so implement mechanics from the game's rules and the Wiki pages rather than
  translating the reference's code. Record any rule known only from the reference as reference-derived;
  attribution alone does not authorize copying its implementation. The checker can merge unrelated
  short matches and cannot prove independence. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
  the [source-provenance review](docs/source-provenance.md).
- Note calculation changes and their sources in [COMBAT_AUDIT.md](COMBAT_AUDIT.md), and add a line to
  [CHANGELOG.md](CHANGELOG.md).
- Please don't add dependencies. The plugin uses the Plugin Hub's `build=standard`, which replaces
  `build.gradle` and only provides the RuneLite client, Lombok and JetBrains annotations. Anything else
  needs a `build=gradle` submission with hash verification and a slower review.
- Keep the Gradle wrapper on 8.x. The Plugin Hub builds with Gradle 8.10, and Gradle 9 needs JDK 17 to run,
  while the hub and this guide use JDK 11. To update it, run
  `./gradlew wrapper --gradle-version <8.x> --distribution-type bin --gradle-distribution-sha256-sum <checksum>`
  with the checksum from [Gradle's release checksums](https://gradle.org/release-checksums/).

## Bundled data

The monster, equipment, status-immunity and wear-requirement tables are generated from the OSRS Wiki by the
scripts in `tools/`. [docs/equipment-requirements.md](docs/equipment-requirements.md) explains what each one
contains and how to regenerate it. Downloads need `BGS_WIKI_CONTACT` set to your email address or
repository URL; the scripts put it in the User-Agent, as the
[MediaWiki API etiquette](https://www.mediawiki.org/wiki/API:Etiquette) asks. `tools/wiki_api.py` also sends
requests one at a time with `maxlag`, follows the server's retry instructions, and stops the run on an API
error rather than overwriting a cache or snapshot. After regenerating, update the retrieval dates and counts
in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (and its `META-INF` copy).

## Releasing

1. Set the same version in `runelite-plugin.properties` (`version=`, which the Plugin Hub shows) and in
   `build.gradle` (the Build workflow fails if they differ), and add the release date to `CHANGELOG.md`.
2. Push, then update `commit=` in this plugin's file in your fork of
   [runelite/plugin-hub](https://github.com/runelite/plugin-hub#updating-a-plugin) and open a pull request.

## Licence

By contributing, you agree that your contributions are licensed under the repository's
[BSD 2-Clause licence](LICENSE).
