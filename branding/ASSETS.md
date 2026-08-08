# Nomad brand asset drop points (Android)

Replace the files below with your exports. Keep the **exact filenames**.

## 1) Launch icons (home screen / “favicon” for the app)

Android does not use a web favicon. The launcher icon *is* the app icon.

| Variant | Put files here |
|--------|-----------------|
| **Light / default** | `app/src/main/res/mipmap-mdpi/ic_launcher.webp` (+ `ic_launcher_round.webp`) |
| | same names in `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi` |
| **Dark** | `app/src/main/res/mipmap-night-mdpi/ic_launcher.webp` (+ round) |
| | same for `mipmap-night-hdpi` … `mipmap-night-xxxhdpi` |
| Adaptive XML | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| Foreground layer | `app/src/main/res/drawable/ic_launcher_foreground.xml` (or `.png`) |
| Background layer | `app/src/main/res/drawable/ic_launcher_background.xml` |

**Easiest:** Android Studio → `File → New → Image Asset` → Launcher Icons, then copy night variants into `mipmap-night-*`.

Suggested sizes (px): mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192.

## 2) Registration page logos

| Variant | Path |
|--------|------|
| Light | `app/src/main/res/drawable/logo_nomad.xml` **or** `drawable-nodpi/logo_nomad.png` |
| Dark | `app/src/main/res/drawable-night/logo_nomad.xml` **or** `drawable-night-nodpi/logo_nomad.png` |

If you drop PNGs named `logo_nomad.png` into `drawable-nodpi` / `drawable-night-nodpi`, remove the matching vector XML so the PNG wins (or rename vectors).

Registration already loads `@drawable/logo_nomad` and Android picks day/night automatically.

## 3) Web favicon (backend / website only)

Not used by the Android APK. For docs/site:

`backend` static folder, e.g. `static/favicon.ico` (or your web host root).

## Brand tokens (already in theme)

- Deep Navy `#0F172A`
- Midnight Blue `#1E3A8A`
- Emerald `#10B981`
- Sky Blue `#0EA5E9`
- Slate Gray `#64748B`
- Typeface: **Poppins SemiBold** (bundled under `res/font/`)
