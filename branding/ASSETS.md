# Nomad brand asset drop points (Android)

Replace the files below with your exports. Keep the **exact filenames**.

## 1) Launch screen icons (splash)

Full brand lockup: emblem + **NOMAD** + tagline *Your Journey. Protected.*

| Variant | Path |
|--------|------|
| **Light** | `app/src/main/res/drawable/splash_nomad.png` |
| **Dark** | `app/src/main/res/drawable-night/splash_nomad.png` |

`SplashScreen` loads `@drawable/splash_nomad`; Android picks day/night automatically.

## 2) Home-screen launcher icons

| Variant | Put files here |
|--------|----------------|
| **Light / default** | `app/src/main/res/mipmap-mdpi/ic_launcher.png` (+ `ic_launcher_round.png`) |
| | same names in `mipmap-hdpi` … `mipmap-xxxhdpi` |
| **Dark** | `app/src/main/res/mipmap-night-mdpi/ic_launcher.png` (+ round) |
| | same for `mipmap-night-hdpi` … `mipmap-night-xxxhdpi` |
| Adaptive XML | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| Foreground | `drawable/ic_launcher_foreground.png` / `drawable-night/…` |
| Background | `drawable/ic_launcher_background.png` / `drawable-night/…` |

Suggested legacy sizes (px): mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192.

Light adaptive background: `#F8FAFC`. Dark: `#000000`.

## 3) In-app logos (header / registration)

Emblem + **NOMAD** wordmark (no tagline).

| Variant | Path |
|--------|------|
| Light | `app/src/main/res/drawable/logo_nomad.png` |
| Dark | `app/src/main/res/drawable-night/logo_nomad.png` |

`BrandLogo` / registration load `@drawable/logo_nomad`.

**Note:** Android resource names cannot contain hyphens or capitals
(`lighticon-Photoroom.png` is invalid). Always use `logo_nomad.png` /
`splash_nomad.png` / `ic_launcher.png`.

## 4) Web favicon (backend / website only)

Not used by the Android APK. For docs/site:

`backend` static folder, e.g. `static/favicon.ico` (or your web host root).

## Brand tokens (already in theme)

- Deep Navy `#0F172A`
- Midnight Blue `#1E3A8A`
- Emerald `#10B981`
- Sky Blue `#0EA5E9`
- Slate Gray `#64748B`
- Typeface: **Poppins SemiBold** (bundled under `res/font/`)
