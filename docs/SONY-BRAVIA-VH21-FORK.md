# Sony BRAVIA VH21 launcher customization

This branch is a **personal fork** of [CleanYourTV](https://github.com/Gathin23/cleanyourtv), tested on a Sony BRAVIA VH21 running Android TV 12. It is **not** an official CleanYourTV release or a generally safe debloat profile.

## Launcher changes

- A more atmospheric, full-screen Home presentation, updated typography and controls, and cleaner app focus/dock styling.
- A full-bleed local art-gallery clock screen, removing the decorative picture-frame margins.
- An **Edit apps** menu supporting remote-based reorder, hide and restore. Hiding removes a tile from Home without uninstalling or disabling the application.
- Wallpaper selection/import from the TV file picker and memory-bounded image decoding.
- Sony-specific guarded accessibility behavior that returns from stock Home and can select a live channel in the existing JioTV Go app after launch, avoiding a repeat-autoplay issue. This behavior requires the launcher accessibility service to be enabled and is not a change to the signed JioTV Go APK.

## Verification and boundaries

The launcher release build and 137 JVM unit tests passed during development. App moving and a complete hide/restore cycle were exercised on the Sony TV. Some interactions (physical remote Home/TV keys and Cast/AirPlay playback after the latest styling changes) still warrant manual regression testing.

**Important:** Sony services, Google Cast, AirPlay, the stock Home recovery path, and JioTV Go installation should remain intact. This branch deliberately does not contain TV-specific user backups, account data, device addresses, or uninstall scripts. Do not apply device-specific package removals to other TVs. The upstream project accepts only **disable-based** device debloat examples, not uninstall lists.

## Contributing upstream

Per upstream `CONTRIBUTING.md`, significant visual restyles belong in a **fork and `SHOWCASE.md` entry**, while small generic bug fixes or optional launcher features can be proposed as separate pull requests. Avoid merging this entire Sony-specific branch into upstream `main` as a single feature PR.
