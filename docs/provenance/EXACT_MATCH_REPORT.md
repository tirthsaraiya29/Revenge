# Exact Match & Diff Forensics Report

**Baseline:** `11ae62071f96db3b70557867ab1254dbcc5b125e` (`1,949` files) vs `Echo-Music` (`815273ddc993dad72c23a37ceff8b76de5f47183`, `2,603` blobs) & `Revenge-source` (`512` files)

## 1. Executive Census of Exact Matches

- **Echo-Music `ExactRawSHA` (identical raw Git blob SHA-1):** **`877`** files (`45.0%` of baseline):
  - **Same relative path (`Echo-Music/<path>`):** **`789`** files.
  - **Moved or renamed path:** **`88`** files (`17` `revenge_cat_icon*` == `echo_cat_icon*`, `3` `ic_revenge_*`/`revenge_equalizer.xml`, `12` `values-*/revenge_strings.xml`, `7` `xml/revenge_*_widget_info.xml`, `48` `core/schemas/*.json`, `1` `docs/.nojekyll` 0-byte empty blob collision with `usbaudio/src/main/res/.gitkeep`).
- **Echo-Music LF-normalized-only matches (differing solely by CRLF/LF):** **`0`** additional files (all exact blob comparisons normalized cleanly; remaining high-overlap Kotlin/XML differences include package renames `com.music.echo` -> `com.revenge.music`).
- **`Revenge-source` exact matches:** **`2`** `ExactRawSHA` + **`134`** `ExactLFSHA` = **`136`** files (`Revenge-source` has no `app/` directory, and `461/512` (90.0%) of its files match `Echo-Music`).

### 1.1 Echo-Music `ExactRawSHA` Breakdown by Module

| Module / Dir | ExactRawSHA Count | Share of Module |
|---|---:|---:|
| `app` | 821 | 54.6% (821/1503) |
| `core` | 48 | 36.4% (48/132) |
| `(root)` | 3 | 20.0% (3/15) |
| `lyrics` | 2 | 7.1% (2/28) |
| `docs` | 1 | 12.5% (1/8) |
| `gradle` | 1 | 25.0% (1/4) |
| `metadata` | 1 | 10.0% (1/10) |
| **TOTAL** | **877** | **45.0% (877/1,949)** |

---

## 2. All `88` Moved / Renamed `ExactRawSHA` Matches vs `Echo-Music`

| Revenge Path (`11ae6207`) | Echo-Music Exact Blob Path (`815273dd`) | Size (B) | SHA-256 (short) | Classification |
|---|---|---:|---|---|
| `app/src/main/res/drawable/ic_revenge_brain.xml` | `app/src/main/res/drawable/ic_echo_brain.xml` | 25,669 | `54e8c7a0abf72bb2` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_revenge_wrapped.xml` | `app/src/main/res/drawable/ic_echo_wrapped.xml` | 1,408 | `566ee0cf0099c51c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/revenge_equalizer.xml` | `app/src/main/res/drawable/echoequlizer.xml` | 860 | `cc993b77b41c8959` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-hdpi/echo_cat_icon.png` | 11,604 | `eef583974ce6b139` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon_foreground.png` | `app/src/main/res/mipmap-hdpi/echo_cat_icon_foreground.png` | 38,989 | `7722f708472472cc` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-hdpi/echo_cat_icon_round.png` | 12,191 | `42295c3d0db3bbc8` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-ldpi/echo_cat_icon.png` | 3,548 | `e2aebbace3e24412` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-ldpi/echo_cat_icon_round.png` | 3,619 | `42e9b4dd13490160` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-mdpi/echo_cat_icon.png` | 5,546 | `699b5c4851bf23f0` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon_foreground.png` | `app/src/main/res/mipmap-mdpi/echo_cat_icon_foreground.png` | 19,950 | `b097d88adbf89539` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-mdpi/echo_cat_icon_round.png` | 5,892 | `db9b2ff09ddd1880` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon.png` | 18,849 | `69f181cce79f4bf3` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon_foreground.png` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon_foreground.png` | 65,473 | `ab33c27654e5dd77` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon_round.png` | 19,575 | `e9e7cc3ee15668e9` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon.png` | 37,212 | `5ecdf4020b0a60bd` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon_foreground.png` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon_foreground.png` | 133,327 | `8099a0403b5b74a7` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon_round.png` | 39,564 | `bc548e17982502b7` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon.png` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon.png` | 61,944 | `80b979ad6696916b` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon_foreground.png` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon_foreground.png` | 232,088 | `df934f11650bf346` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon_round.png` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon_round.png` | 63,997 | `2e3e4d7b24d8b4d7` | `REMOVE` |
| `app/src/main/res/values-as/revenge_strings.xml` | `app/src/main/res/values-as/echo_strings.xml` | 4,069 | `cfa891d24963354b` | `REIMPLEMENT` |
| `app/src/main/res/values-az/revenge_strings.xml` | `app/src/main/res/values-az/echo_strings.xml` | 4,499 | `a81aa17d109a4afa` | `REIMPLEMENT` |
| `app/src/main/res/values-b+sr+Latn/revenge_strings.xml` | `app/src/main/res/values-b+sr+Latn/echo_strings.xml` | 5,958 | `35a515eff2ef5301` | `REIMPLEMENT` |
| `app/src/main/res/values-be/revenge_strings.xml` | `app/src/main/res/values-be/echo_strings.xml` | 4,854 | `94e5d634f8afd05b` | `REIMPLEMENT` |
| `app/src/main/res/values-fil/revenge_strings.xml` | `app/src/main/res/values-fil/echo_strings.xml` | 3,226 | `37f78324a683236e` | `REIMPLEMENT` |
| `app/src/main/res/values-hi/revenge_strings.xml` | `app/src/main/res/values-hi/echo_strings.xml` | 662 | `332bcaa8b2ca4659` | `REIMPLEMENT` |
| `app/src/main/res/values-iw/revenge_strings.xml` | `app/src/main/res/values-iw/echo_strings.xml` | 2,862 | `1309e4d595c31050` | `REIMPLEMENT` |
| `app/src/main/res/values-km/revenge_strings.xml` | `app/src/main/res/values-km/echo_strings.xml` | 1,496 | `5af7a785cbcbacd5` | `REIMPLEMENT` |
| `app/src/main/res/values-ms/revenge_strings.xml` | `app/src/main/res/values-ms/echo_strings.xml` | 5,575 | `a6015c5a4de99f25` | `REIMPLEMENT` |
| `app/src/main/res/values-ta/revenge_strings.xml` | `app/src/main/res/values-ta/echo_strings.xml` | 1,906 | `4947e868dd6dd89a` | `REIMPLEMENT` |
| `app/src/main/res/values-te/revenge_strings.xml` | `app/src/main/res/values-te/echo_strings.xml` | 7,645 | `aa12c6ed70d0f6f5` | `REIMPLEMENT` |
| `app/src/main/res/values-wae/revenge_strings.xml` | `app/src/main/res/values-wae/echo_strings.xml` | 68 | `6bcbc721002cf571` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_adaptive_widget_info.xml` | `app/src/main/res/xml/echo_adaptive_widget_info.xml` | 617 | `9160f05d951c8ffc` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_modern_widget_info.xml` | `app/src/main/res/xml/echo_modern_widget_info.xml` | 605 | `60d9e52d266db759` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_showcase_widget_info.xml` | `app/src/main/res/xml/echo_showcase_widget_info.xml` | 612 | `2773ac0c5ee3a3cc` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_slim_widget_info.xml` | `app/src/main/res/xml/echo_slim_widget_info.xml` | 600 | `966e7bc3ab2d14f1` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_square_widget_info.xml` | `app/src/main/res/xml/echo_square_widget_info.xml` | 608 | `2ddddb5f1a33c4af` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_vintage_widget_info.xml` | `app/src/main/res/xml/echo_vintage_widget_info.xml` | 618 | `5635113b0fdbf12b` | `REIMPLEMENT` |
| `app/src/main/res/xml/revenge_vinyl_widget_info.xml` | `app/src/main/res/xml/echo_vinyl_widget_info.xml` | 607 | `ef6841c21511004c` | `REIMPLEMENT` |
| `core/schemas/com.revenge.music.db.InternalDatabase/1.json` | `core/schemas/com.music.echo.db.InternalDatabase/1.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/1.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/1.json` | 8,802 | `d5e3fcb2df9af1d2` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/10.json` | `core/schemas/com.music.echo.db.InternalDatabase/10.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/10.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/10.json` | 24,353 | `836df933a95ee1a1` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/11.json` | `core/schemas/com.music.echo.db.InternalDatabase/11.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/11.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/11.json` | 23,776 | `3d40d0c3e42f4d1e` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/12.json` | `core/schemas/com.music.echo.db.InternalDatabase/12.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/12.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/12.json` | 24,268 | `f31479116c1ae21b` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/13.json` | `core/schemas/com.music.echo.db.InternalDatabase/13.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/13.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/13.json` | 24,268 | `74be1640dcefafc0` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/14.json` | `core/schemas/com.music.echo.db.InternalDatabase/14.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/14.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/14.json` | 24,657 | `2820f4b3b0c99881` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/15.json` | `core/schemas/com.music.echo.db.InternalDatabase/15.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/15.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/15.json` | 25,594 | `4725c461d561c02b` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/16.json` | `core/schemas/com.music.echo.db.InternalDatabase/16.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/16.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/16.json` | 27,807 | `ef63722c321a6958` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/17.json` | `core/schemas/com.music.echo.db.InternalDatabase/17.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/17.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/17.json` | 28,695 | `26f34aa9d047efcb` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/18.json` | `core/schemas/com.music.echo.db.InternalDatabase/18.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/18.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/18.json` | 29,818 | `b1df722760a9942e` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/19.json` | `core/schemas/com.music.echo.db.InternalDatabase/19.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/19.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/19.json` | 30,180 | `9a57b62ffba772ab` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/2.json` | `core/schemas/com.music.echo.db.InternalDatabase/2.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/2.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/2.json` | 19,693 | `34f3702956b0631e` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/20.json` | `core/schemas/com.music.echo.db.InternalDatabase/20.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/20.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/20.json` | 30,472 | `dc6f099e8770c232` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/21.json` | `core/schemas/com.music.echo.db.InternalDatabase/21.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/21.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/21.json` | 31,216 | `3632f35795eb0976` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/22.json` | `core/schemas/com.music.echo.db.InternalDatabase/22.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/22.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/22.json` | 31,406 | `59c378053a3320c7` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/23.json` | `core/schemas/com.music.echo.db.InternalDatabase/23.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/23.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/23.json` | 30,897 | `a357b864de912717` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/24.json` | `core/schemas/com.music.echo.db.InternalDatabase/24.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/24.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/24.json` | 31,350 | `6e0758bf3922360f` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/25.json` | `core/schemas/com.music.echo.db.InternalDatabase/25.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/25.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/25.json` | 31,534 | `8d6fd11621c798f8` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/26.json` | `core/schemas/com.music.echo.db.InternalDatabase/26.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/26.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/26.json` | 31,377 | `fdeccc3e7a91c7f3` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/27.json` | `core/schemas/com.music.echo.db.InternalDatabase/27.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/27.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/27.json` | 31,864 | `4df7cecf2fa70a1b` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/28.json` | `core/schemas/com.music.echo.db.InternalDatabase/28.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/28.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/28.json` | 32,066 | `30670486b4bbb960` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/29.json` | `core/schemas/com.music.echo.db.InternalDatabase/29.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/29.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/29.json` | 32,315 | `b949b3a5464a8614` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/3.json` | `core/schemas/com.music.echo.db.InternalDatabase/3.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/3.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/3.json` | 21,515 | `e196521291bb295d` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/30.json` | `core/schemas/com.music.echo.db.InternalDatabase/30.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/30.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/30.json` | 32,560 | `d90ea34553c74d49` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/31.json` | `core/schemas/com.music.echo.db.InternalDatabase/31.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/31.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/31.json` | 35,989 | `6a8b548aa4adb655` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/32.json` | `core/schemas/com.music.echo.db.InternalDatabase/32.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/32.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/32.json` | 36,760 | `4b26c8b831f0ef05` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/33.json` | `core/schemas/com.music.echo.db.InternalDatabase/33.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/33.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/33.json` | 38,588 | `29187e5d38ac3e21` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/34.json` | `core/schemas/com.music.echo.db.InternalDatabase/34.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/34.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/34.json` | 38,588 | `1e8985173ac51eba` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/35.json` | `core/schemas/com.music.echo.db.InternalDatabase/35.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/35.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/35.json` | 38,588 | `6de79943da689bac` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/36.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/36.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/36.json` | 38,831 | `5214df3d094a6820` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/37.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/37.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/37.json` | 38,831 | `d7dabbe6e4fb2233` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/38.json` | `core/schemas/com.music.echo.db.InternalDatabase/38.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/38.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/38.json` | 42,733 | `87de7427471b88aa` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/39.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/39.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/39.json` | 42,733 | `653a67d80cd94d9d` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/4.json` | `core/schemas/com.music.echo.db.InternalDatabase/4.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/4.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/4.json` | 22,201 | `3aea7b116eff6432` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/40.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/40.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/40.json` | 43,992 | `61ae9b43e650ee05` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/41.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/41.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/41.json` | 44,327 | `36e4d3ae4893b856` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/42.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/42.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/42.json` | 40,425 | `b9e899636115f833` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/43.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/43.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/43.json` | 40,425 | `c5eababc4bd344d8` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/44.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/44.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/44.json` | 40,754 | `b4884333c923e927` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/45.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/45.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/45.json` | 40,754 | `b8768bb79fc6a997` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/46.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/46.json` | 41,019 | `72316dec0cf97788` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/47.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/47.json` | 44,097 | `eea1b35b4972b402` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/48.json` | `core/schemas/echo.music.iad1tya.db.InternalDatabase/48.json` | 45,577 | `a9f25a5228174863` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/5.json` | `core/schemas/com.music.echo.db.InternalDatabase/5.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/5.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/5.json` | 22,370 | `aeef8f440888f35d` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/6.json` | `core/schemas/com.music.echo.db.InternalDatabase/6.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/6.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/6.json` | 21,233 | `cf2941455dd7ad60` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/7.json` | `core/schemas/com.music.echo.db.InternalDatabase/7.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/7.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/7.json` | 21,420 | `787f188ee64be264` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/8.json` | `core/schemas/com.music.echo.db.InternalDatabase/8.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/8.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/8.json` | 22,761 | `991e7cf7bb3c2b78` | `RETAIN` |
| `core/schemas/com.revenge.music.db.InternalDatabase/9.json` | `core/schemas/com.music.echo.db.InternalDatabase/9.json, core/schemas/echo.music.iad1tya.db.InternalDatabase/9.json, core/schemas/iad1tya.echo.music.db.InternalDatabase/9.json` | 25,046 | `d737821d6da049a1` | `RETAIN` |
| `docs/.nojekyll` | `usbaudio/src/main/res/.gitkeep` | 0 | `e3b0c44298fc1c14` | `RETAIN` |

---

## 3. Complete Index of `789` Same-Path `ExactRawSHA` Matches vs `Echo-Music`

| Revenge & Echo-Music Relative Path | Module | Size (B) | SHA-256 (short) | Classification |
|---|---|---:|---|---|
| `.editorconfig` | `(root)` | 694 | `a414ae2b57e73267` | `RETAIN` |
| `app/.gitignore` | `app` | 65 | `2410ede0943f4d78` | `RETAIN` |
| `app/lint.xml` | `app` | 557 | `bc88ed21236238da` | `REFACTOR` |
| `app/src/foss/AndroidManifest.xml` | `app` | 539 | `d67a31a681b3c874` | `REFACTOR` |
| `app/src/main/assets/beat_this_final0_q8.onnx` | `app` | 23,138,816 | `7801f9c1adfd5ff1` | `BLOCKED` |
| `app/src/main/assets/player_configs.json` | `app` | 31,489 | `b4c668208fbf9ad9` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/assets/player_dates.json` | `app` | 8,599 | `fa5fa745aee0b3b5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/assets/po_token.html` | `app` | 8,610 | `ac8fab74d17416a8` | `REIMPLEMENT` |
| `app/src/main/assets/solver/astring.js` | `app` | 18,221 | `0c479b5d1ad846bd` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/assets/solver/meriyah.js` | `app` | 361,544 | `b5880ded197f47d4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/assets/solver/yt.solver.core.js` | `app` | 18,395 | `9146fa6a655ec350` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/assets/vocals_umxhq_int8.onnx` | `app` | 9,054,206 | `a2be987b55a29bc1` | `BLOCKED` |
| `app/src/main/cpp/vibrafp/include/vibra.h` | `app` | 3,420 | `da87e2da70fdd78f` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/CMakeLists.txt` | `app` | 5,465 | `6d62ca49d24c79b5` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/frequency.cpp` | `app` | 897 | `eb417ab6ee94159d` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/frequency.h` | `app` | 1,071 | `77d95a833ea6957d` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature.cpp` | `app` | 3,087 | `68c771e49b411d34` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature.h` | `app` | 1,928 | `47af7952e665a785` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature_generator.cpp` | `app` | 8,582 | `df1db7ae868923d4` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature_generator.h` | `app` | 1,384 | `59063809b7cd9b8f` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/byte_control.h` | `app` | 1,555 | `62180da2d0c181a6` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/downsampler.cpp` | `app` | 7,615 | `07082ca5ffef69d4` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/downsampler.h` | `app` | 2,511 | `f8b0db44715cd7b2` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/wav.cpp` | `app` | 3,402 | `6e81dc460f49fba3` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/wav.h` | `app` | 2,366 | `70adbb03beaaeade` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/base64.h` | `app` | 1,559 | `5a0fd2c87f93ed9d` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/crc32.h` | `app` | 584 | `62b273c151794742` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/fft.h` | `app` | 2,256 | `4952ed331bc1dea3` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/hanning.h` | `app` | 30,169 | `e1baf5c5c943b534` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/ring_buffer.h` | `app` | 1,499 | `a0b2175e47beee4e` | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/vibra.cpp` | `app` | 2,254 | `8d20ebec566c7082` | `REMOVE` |
| `app/src/main/cpp/vibrafp/third_party/build-fftw-android.sh` | `app` | 7,027 | `6fa274f1b45f774b` | `REMOVE` |
| `app/src/main/cpp/vibrafp/third_party/fftw-3.3.10.tar.gz` | `app` | 4,144,100 | `56c932549852cddc` | `REMOVE` |
| `app/src/main/res/drawable-hdpi/ic_stat_name.png` | `app` | 815 | `290d2df249cc023a` | `REIMPLEMENT` |
| `app/src/main/res/drawable-mdpi/ic_stat_name.png` | `app` | 564 | `df0d9024f3ba89ff` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_background.xml` | `app` | 371 | `7a734b66fc56ca80` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_play_pill_bg.xml` | `app` | 327 | `d45cfd9ba0594477` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_progress_fill.xml` | `app` | 246 | `333d36552dd5bf48` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_progress_track.xml` | `app` | 246 | `a898538418ca6ac0` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_turntable_nav_bg.xml` | `app` | 247 | `484ee7666eb4869d` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_turntable_play_bg.xml` | `app` | 303 | `e3859251270791ee` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_background.xml` | `app` | 398 | `c021d68ae199c97f` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_play_pill_bg.xml` | `app` | 221 | `2433c5a38ef755cc` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_progress_fill.xml` | `app` | 220 | `f8ebb4acdd8aede4` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_progress_track.xml` | `app` | 220 | `43c225cca9cf0dbc` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_turntable_nav_bg.xml` | `app` | 246 | `3ca3192f60d1b51d` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_turntable_play_bg.xml` | `app` | 277 | `bebddf0fc030faad` | `REIMPLEMENT` |
| `app/src/main/res/drawable-nodpi/birthday_image.png` | `app` | 1,757,869 | `87d50cad8863fc9f` | `BLOCKED` |
| `app/src/main/res/drawable-v31/ic_launcher_background_v31.xml` | `app` | 276 | `55768c04b76481d2` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/ic_widget_mic.xml` | `app` | 864 | `1528f11ddf103d22` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_background.xml` | `app` | 365 | `12f0594586586a33` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_button_bg.xml` | `app` | 355 | `5c78d8d1dc192424` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_button_bg_active.xml` | `app` | 358 | `7146327349b632e9` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_1.xml` | `app` | 418 | `a6193e6b7854e899` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_2.xml` | `app` | 418 | `d89fce8c244cfcf4` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_3.xml` | `app` | 416 | `08dfe0113fafda70` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_4.xml` | `app` | 414 | `9b724367197a49e2` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_play_pill_bg.xml` | `app` | 327 | `be87830012b1bf9f` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_progress_fill.xml` | `app` | 246 | `73710d06d2d4e096` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_progress_track.xml` | `app` | 246 | `8b4657153e4c73af` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_turntable_nav_bg.xml` | `app` | 247 | `f43b03cf18b6e3c6` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_turntable_play_bg.xml` | `app` | 303 | `0c8795487b8ae522` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xhdpi/ic_stat_name.png` | `app` | 1,227 | `6b37b61f401e2a46` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xxhdpi/ic_stat_name.png` | `app` | 1,979 | `0838fd85116326be` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xxxhdpi/ic_stat_name.png` | `app` | 2,953 | `1015347cdb132fdb` | `REIMPLEMENT` |
| `app/src/main/res/drawable/account.xml` | `app` | 963 | `0876940adfdf24d1` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/add.xml` | `app` | 399 | `1af4a6e338a9bc14` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/add_circle.xml` | `app` | 662 | `ed3ea8f51367dcf3` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ai_pfp.png` | `app` | 836,320 | `b006e34880b54962` | `BLOCKED` |
| `app/src/main/res/drawable/album.xml` | `app` | 844 | `791067e1e935b234` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/alphabet_cyrillic.xml` | `app` | 670 | `42731ae599db41dc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/apk_install.xml` | `app` | 1,318 | `533f1546661f2d74` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/apple_airpods.png` | `app` | 1,195 | `64efdea038aafc39` | `BLOCKED` |
| `app/src/main/res/drawable/apple_headset.png` | `app` | 1,368 | `955c8ee8ffff7f8b` | `BLOCKED` |
| `app/src/main/res/drawable/apple_lyrics.png` | `app` | 721 | `90e855a40d365e39` | `BLOCKED` |
| `app/src/main/res/drawable/apple_music_me.xml` | `app` | 5,402 | `86e53ed5b3828f52` | `BLOCKED` |
| `app/src/main/res/drawable/apple_queue.xml` | `app` | 939 | `18fa3f0820423f5a` | `BLOCKED` |
| `app/src/main/res/drawable/apple_skip_next.xml` | `app` | 579 | `3d48704b32b9f8b1` | `BLOCKED` |
| `app/src/main/res/drawable/apple_skip_previous.xml` | `app` | 581 | `eada79def3cff8ee` | `BLOCKED` |
| `app/src/main/res/drawable/arrow_back.xml` | `app` | 552 | `1c53788132872a33` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_back_ios.xml` | `app` | 409 | `a6d46cca3784a892` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_downward.xml` | `app` | 375 | `aa3baef6424fb518` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_forward.xml` | `app` | 407 | `01e0c6f06ba78009` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_top_left.xml` | `app` | 375 | `01aacb46b4d8ee3d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_upward.xml` | `app` | 375 | `e1595c530ad958c4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/artist.xml` | `app` | 826 | `21c47eb1870e05c0` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/artist_screen.xml` | `app` | 1,395 | `757ace798aae763e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/auto_play.xml` | `app` | 785 | `ae9c84550b4abfaa` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/automation_slow_connecttion.xml` | `app` | 1,562 | `4cb24357859d027e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/avg_time_oldplayer.xml` | `app` | 1,478 | `2ae172d2e67bfdb4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/backup.xml` | `app` | 702 | `bdbecd680ad18c42` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bedtime.xml` | `app` | 628 | `c1d13ddbe72fdef7` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/biotech.xml` | `app` | 1,272 | `0ae50246a025b8e2` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bluetooth.xml` | `app` | 495 | `6145d10435a6a4d6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bug_report.xml` | `app` | 1,216 | `517479a5c927e840` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/buymeacoffee.xml` | `app` | 6,880 | `73dbd7f509f4e4f4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cached.xml` | `app` | 714 | `26cafeeb47712043` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast.xml` | `app` | 539 | `7867f75e8b9d7f64` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_connected.xml` | `app` | 587 | `6eac822844a9c424` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_speaker.xml` | `app` | 1,613 | `2590e8a1cd592bd5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_speaker_connected.xml` | `app` | 3,916 | `2406ef431937a46c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_tv.xml` | `app` | 903 | `77a921839e96fd5c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_tv_connected.xml` | `app` | 1,882 | `4fc5f99d31e96dcf` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chat_msg.xml` | `app` | 752 | `cfddd4f1e16c723a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/check.xml` | `app` | 336 | `dd622c96eab314fb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chevron_leftpx.xml` | `app` | 410 | `f4b3cf5ee06daf91` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chevron_right_px.xml` | `app` | 410 | `d3f53b271fdb2150` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/clear_all.xml` | `app` | 417 | `6360260566107063` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/close.xml` | `app` | 387 | `541ca4f4897a55eb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cloud.xml` | `app` | 1,038 | `839f8c81def62400` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/coffee.xml` | `app` | 677 | `1d88b5763126dca0` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/collab.jpg` | `app` | 85,433 | `5533457b436de827` | `BLOCKED` |
| `app/src/main/res/drawable/commit.xml` | `app` | 661 | `a69e69d41a8f7a86` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/connect_people.xml` | `app` | 1,111 | `e9a5a011374acb47` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/content_copy.xml` | `app` | 957 | `cd77f4d13841f186` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/contrast.xml` | `app` | 418 | `da5d419eb4415e1c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/crop.xml` | `app` | 378 | `69808c0342f6bf49` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/crown.xml` | `app` | 1,030 | `b836ae40eb16d83b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/currency_rupee_upi.xml` | `app` | 1,169 | `0c0674e86409a11f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/delete.xml` | `app` | 496 | `1e9dca487e653d64` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/delete_history.xml` | `app` | 876 | `f36e3d55bca493cc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/deployed_app_update.xml` | `app` | 1,273 | `3f9f925b41deb1dc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/dev.jpg` | `app` | 151,514 | `56a1c731788823c8` | `BLOCKED` |
| `app/src/main/res/drawable/discover_tune.xml` | `app` | 634 | `9c8ac21ae4a65595` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/dock_to_top.xml` | `app` | 383 | `518cf6a3078b97f5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/done.xml` | `app` | 351 | `8b86f29d37551e5a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/download.xml` | `app` | 584 | `4ceb54c4ec7b6622` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/drag_handle.xml` | `app` | 376 | `c336907a58fdf5a4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/edit.xml` | `app` | 608 | `4c3347a5c804cacb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/equalizer.xml` | `app` | 417 | `f883d27b7a6eb2d0` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/error.xml` | `app` | 792 | `7b846bf9b62c4950` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/expand_less.xml` | `app` | 351 | `d6b348b116fab1fc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/expand_more.xml` | `app` | 351 | `5bdc942ba87fb46d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/explicit.xml` | `app` | 533 | `17ac2b92f552f88d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/explore_outlined.xml` | `app` | 1,034 | `feae6bbfc9b58d6f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/extension.xml` | `app` | 1,224 | `48d32e666f2dd2dd` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fast_forward.xml` | `app` | 466 | `44a7e1517b1a432b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/favorite.xml` | `app` | 465 | `3037c30970d47614` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/favorite_border.xml` | `app` | 645 | `f55e807435c96051` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/file_export.xml` | `app` | 538 | `8a9e7bd397899db6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/folder_managed.xml` | `app` | 480 | `1ea6d2542376790e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fullscreen.xml` | `app` | 418 | `fce87653cbeb77b7` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fullscreen_exit.xml` | `app` | 400 | `5537689c5e3b5a3a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/github.xml` | `app` | 995 | `8008e0c92add3cd8` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/github_main.xml` | `app` | 1,115 | `b009654594c9e89a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe.xml` | `app` | 1,059 | `183f68ecb10306c9` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe_location_pin.xml` | `app` | 1,387 | `d0f137afe0de3cc7` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe_search.xml` | `app` | 1,059 | `183f68ecb10306c9` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/gradient.xml` | `app` | 628 | `0208bdf86a53d253` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/graphic_eq.xml` | `app` | 497 | `edad7a63327e7b8d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/grid_view.xml` | `app` | 786 | `ff81095c91564ca5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group.xml` | `app` | 1,583 | `4dac956b01d36a18` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_add.xml` | `app` | 1,639 | `2cea6a1331a423e8` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_filled.xml` | `app` | 729 | `9ae879eaa6034ba5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_outlined.xml` | `app` | 970 | `2b27d7966fd98a47` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/headset_applemusic.xml` | `app` | 1,540 | `3432c72bd614a1bb` | `BLOCKED` |
| `app/src/main/res/drawable/hide_image.xml` | `app` | 523 | `08c90319ffc55ffa` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/history.xml` | `app` | 623 | `3020601fcfe0d215` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/home_filled.xml` | `app` | 332 | `f042c0f9d2e752bc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/home_outlined.xml` | `app` | 556 | `4768449f66ccc9aa` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_app_settings.png` | `app` | 9,959 | `842d9d0f8b7a09e2` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_apple_lossless.xml` | `app` | 1,733 | `8d8223b0c7c3f7dc` | `BLOCKED` |
| `app/src/main/res/drawable/ic_calendar_month.xml` | `app` | 441 | `6720c880b471298f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_canvas.xml` | `app` | 1,035 | `2ace8ad6fdb9c5b0` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_chart.xml` | `app` | 942 | `8dadc6a15beaf57a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_drive.xml` | `app` | 595 | `2480725f99ada94d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_dynamic_icon.xml` | `app` | 476 | `7a78df9770f3effc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_forward_10.xml` | `app` | 1,159 | `db0552bcb1c37249` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_google.xml` | `app` | 1,008 | `d1704a2fc5fe726b` | `BLOCKED` |
| `app/src/main/res/drawable/ic_heart.xml` | `app` | 463 | `2ad00aab09935d8b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_heart_outline.xml` | `app` | 643 | `e59ec72ba512a19d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_hq.xml` | `app` | 984 | `045436daa5782855` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_instagram_new.xml` | `app` | 806 | `f82192805ceda4b1` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_jellyfin.xml` | `app` | 607 | `5f1a4d120b669b57` | `BLOCKED` |
| `app/src/main/res/drawable/ic_lastfm.xml` | `app` | 1,047 | `b4afa875d671c696` | `BLOCKED` |
| `app/src/main/res/drawable/ic_launcher_background_v31.xml` | `app` | 345 | `9c00b1cccd0fa28f` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_listenbrainz.xml` | `app` | 828 | `9b394ad5c65a9ba9` | `BLOCKED` |
| `app/src/main/res/drawable/ic_monochrome_mode.xml` | `app` | 378 | `a0a0fc2bb88c0bbd` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_nav_chevron_left.xml` | `app` | 308 | `7e534fe88f3b03b6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_nav_chevron_right.xml` | `app` | 310 | `f096b69cb52f1d95` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_navidrome.xml` | `app` | 2,495 | `0b524b5aa2309361` | `BLOCKED` |
| `app/src/main/res/drawable/ic_navidrome_md3.xml` | `app` | 814 | `fda890cf229776cc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_patreon_new.xml` | `app` | 399 | `235d3af1e5028827` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_push_pin.xml` | `app` | 378 | `a1d669d1e86b1769` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_replay_10.xml` | `app` | 1,147 | `c641253e9394565c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_ringtone.png` | `app` | 876 | `6d5211a37c7f2210` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_smart_display.xml` | `app` | 383 | `a43f4eb6d05d0af6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_spotify.xml` | `app` | 1,439 | `0f465f7d2cae546e` | `BLOCKED` |
| `app/src/main/res/drawable/ic_subsonic.xml` | `app` | 1,043 | `8e207277e2e1eddc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_subsonic_md3.xml` | `app` | 905 | `f64f5d172f3082e6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_telegram_new.xml` | `app` | 908 | `396e25773dab1aae` | `BLOCKED` |
| `app/src/main/res/drawable/ic_ten_sided_cookie.xml` | `app` | 1,306 | `19cfe4c59395c9cc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_text_color.xml` | `app` | 546 | `6bdd91f457c6c356` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_widget_cast_white.xml` | `app` | 543 | `222ebcfeb7b51bdd` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_equalizer_dots.xml` | `app` | 1,106 | `3c91b626bf4a6d25` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_clean.xml` | `app` | 682 | `20440037e1725e31` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_filled_clean.xml` | `app` | 486 | `d7fb9ec1a8d00d39` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_nav.xml` | `app` | 475 | `1667ead0bdf40110` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_outline_nav.xml` | `app` | 655 | `64c1e53998233841` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_solid_white.xml` | `app` | 499 | `745fcabe27b08670` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_lyrics_pill.xml` | `app` | 433 | `263ce93ee62f24ca` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_mic.xml` | `app` | 830 | `3548dcd168e893ef` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_more_dots.xml` | `app` | 506 | `555d39c69931ab82` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_music_note.xml` | `app` | 425 | `dcf4ce716d04b1a4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_next_brown.xml` | `app` | 304 | `2bf6f1fbe92d857b` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause.xml` | `app` | 299 | `50b4d861b2399503` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_bold_white.xml` | `app` | 516 | `9f0bdba89157757b` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_brown.xml` | `app` | 468 | `dd18bb6c64b4db8c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_low.xml` | `app` | 326 | `c1e56783df7574bf` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_secondary.xml` | `app` | 326 | `e767855a93e1c11d` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_solid.xml` | `app` | 352 | `b48b2a17ffb7be2c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_phone_device.xml` | `app` | 441 | `211b600d91b47e30` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play.xml` | `app` | 282 | `91d6e664479d516c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_bold_white.xml` | `app` | 413 | `7070df61b16d4837` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_brown.xml` | `app` | 384 | `b5ed77b62e3116d4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_low.xml` | `app` | 309 | `7656bc97318a7415` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_secondary.xml` | `app` | 309 | `2e2c2d771432868f` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_triangle.xml` | `app` | 337 | `28fa9f4c9ff41cbf` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_prev_brown.xml` | `app` | 295 | `796a50b1713f7304` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_queue_library.xml` | `app` | 465 | `52940dc6b3dd7c58` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_all.xml` | `app` | 521 | `72d8a6e4e1481f68` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_off.xml` | `app` | 521 | `b14cafbb13ef5457` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_one.xml` | `app` | 578 | `3f411d7d01d9b62c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_shuffle_off.xml` | `app` | 531 | `5c916b7de15c8f82` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_shuffle_on.xml` | `app` | 531 | `1d07d8962b9da323` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_next.xml` | `app` | 323 | `b5ac716de8727147` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_next_white.xml` | `app` | 359 | `7e638a29b6359960` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_prev_white.xml` | `app` | 359 | `610dbb91c366ca43` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_previous.xml` | `app` | 321 | `824d98c980227c4c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_soundwave_logo.xml` | `app` | 1,190 | `911aae0f9b250730` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_volume_mute.xml` | `app` | 925 | `5ba19aeabaad337a` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_volume_on.xml` | `app` | 531 | `38dc2556e2f0247f` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_x_new.xml` | `app` | 685 | `07cc82fefb141342` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/image.xml` | `app` | 885 | `23cd3ca3e086c233` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/info.xml` | `app` | 729 | `a93a7a5054ed2d64` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/insert_photo.xml` | `app` | 445 | `343bc84293c3236f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/integration.xml` | `app` | 831 | `d07a409c985173c7` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ios_share.xml` | `app` | 778 | `7204ca0ef7e6bbfe` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/key.xml` | `app` | 565 | `4b12bb12a0bd3eec` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language.xml` | `app` | 1,610 | `d73f47a056336b9e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language_japanese_latin.xml` | `app` | 1,764 | `325c50f4bb0c2c46` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language_korean_latin.xml` | `app` | 1,247 | `222b98e36436dbfe` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_add.xml` | `app` | 753 | `0d0d195734a1159a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_add_check.xml` | `app` | 665 | `d3af49b0e5589334` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music.xml` | `app` | 676 | `e7b4b67bda2d9134` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music_filled.xml` | `app` | 669 | `73faee23abf26e74` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music_outlined.xml` | `app` | 676 | `e7b4b67bda2d9134` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/linear_scale.xml` | `app` | 653 | `c1e01d9bad4e28c2` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/link.xml` | `app` | 590 | `98e07c2a375d563e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/list.xml` | `app` | 908 | `d969a9de36c3b97f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/location_on.xml` | `app` | 628 | `271a2ed1ee80313d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/lock.xml` | `app` | 1,158 | `9db84f25360c8757` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/lock_open.xml` | `app` | 1,133 | `d70e7903b59b9f3e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/login.xml` | `app` | 606 | `becf5725673d116d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/logout.xml` | `app` | 461 | `c9ec8568cd30b815` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/lyrics.png` | `app` | 7,785 | `aa31aa18e83d0f2b` | `REIMPLEMENT` |
| `app/src/main/res/drawable/manage_search.xml` | `app` | 758 | `d41f06d2acd6f48a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/mic.xml` | `app` | 493 | `aa81b897bea7b22f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/more_horiz.xml` | `app` | 753 | `6831be7b3eeee126` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/more_vert.xml` | `app` | 753 | `1ba445e1953481c2` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/music_history.xml` | `app` | 968 | `384b0f207281421a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/music_note.xml` | `app` | 475 | `1d8991e4dd445796` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/nav_bar.xml` | `app` | 490 | `06f4205ea73f6489` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/navigate_next.xml` | `app` | 383 | `5013ec6af8002d10` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/network_node.xml` | `app` | 1,305 | `7454d67e6ad2d3e8` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/network_update.xml` | `app` | 1,359 | `2b4d617e33d33bcb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/new_release.xml` | `app` | 1,105 | `1ff7b55c9ad6433a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/no_volume.xml` | `app` | 621 | `a768e782419ec44e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/notification.xml` | `app` | 568 | `55d87374cc42c117` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/offline.xml` | `app` | 683 | `ae5c018d72c77e3a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/palette.xml` | `app` | 1,069 | `f376e9c105e8c979` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/pause.xml` | `app` | 638 | `5ce4d5825146f5ae` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/pause_applemusic.xml` | `app` | 631 | `615d03bb2e1545b8` | `BLOCKED` |
| `app/src/main/res/drawable/paypal.xml` | `app` | 1,353 | `313150762c9f68ed` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/person.xml` | `app` | 621 | `f5619a5f84c759dd` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/play.xml` | `app` | 525 | `ceb197f264a0f70c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/play_applemusic.xml` | `app` | 518 | `f752fb7dfe3b39cc` | `BLOCKED` |
| `app/src/main/res/drawable/playlist_add.xml` | `app` | 522 | `10e6b20cd178a891` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/playlist_play.xml` | `app` | 450 | `c76010a2e9a3cd36` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/preview_artwork.webp` | `app` | 26,686 | `062785038a7cd49a` | `BLOCKED` |
| `app/src/main/res/drawable/queue_music.xml` | `app` | 629 | `2e649d5c22c67c17` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio.xml` | `app` | 858 | `db85f5bb53a9e41d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio_button_checked.xml` | `app` | 1,036 | `e07dc21f66258cb1` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio_button_unchecked.xml` | `app` | 843 | `5c54e60c3c6b15a1` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/refresh.xml` | `app` | 645 | `b4aa764bd65d7ce6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/remove.xml` | `app` | 335 | `008acafa48338686` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat.xml` | `app` | 487 | `2569e0b8b696381f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_on.xml` | `app` | 656 | `de7a5111c9f37a41` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_one.xml` | `app` | 544 | `5d9eab77b1daafab` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_one_on.xml` | `app` | 713 | `609c1c0ca7d6ed28` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/replay.xml` | `app` | 709 | `8865943ea05d2adc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/restore.xml` | `app` | 680 | `ad7d2233d46e42e8` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/screenshot.xml` | `app` | 1,028 | `b6ec069663f49431` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/search.xml` | `app` | 534 | `80e70235b6aa1a7e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/search_off.xml` | `app` | 954 | `bc582d24b9cd4309` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/security.xml` | `app` | 529 | `f2fbf014b7d1ed39` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/select_all.xml` | `app` | 792 | `a9328910f3b71f5b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/send_chat.xml` | `app` | 508 | `44fc4e67b392577b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/settings.xml` | `app` | 2,031 | `e3f0e5a625f25f00` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/share.xml` | `app` | 699 | `3bacf8d51fbe0a43` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shortcut_library.xml` | `app` | 580 | `589f5537efb90bf9` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shortcut_search.xml` | `app` | 669 | `9636bbe72c47df8d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shuffle.xml` | `app` | 497 | `84e0027607a31692` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shuffle_on.xml` | `app` | 666 | `df99cec1a9186cbb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/similar.xml` | `app` | 376 | `c375b236320e9627` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/skip_next.xml` | `app` | 383 | `450bf7a0f5d33236` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/skip_previous.xml` | `app` | 381 | `648418f689460f2b` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sleep_timer.xml` | `app` | 992 | `93757ce57946861f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sliders.xml` | `app` | 538 | `c708d42614c81a12` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/slow_motion_video.xml` | `app` | 841 | `270b39896614661a` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/smalls_icon.xml` | `app` | 740 | `51b18d6873ac0a91` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/snippet_folder.xml` | `app` | 572 | `e56fa31ef14d1ce6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/songdna.png` | `app` | 828,768 | `bcbdbb4b5697c0a8` | `BLOCKED` |
| `app/src/main/res/drawable/sparks.xml` | `app` | 685 | `1227fe7a4e855c8c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/spatial_tracking_apple.xml` | `app` | 1,839 | `314cc85c6b88b4ed` | `BLOCKED` |
| `app/src/main/res/drawable/speaker_apple.xml` | `app` | 1,291 | `9e9ae5c166c65f6b` | `BLOCKED` |
| `app/src/main/res/drawable/speaker_applemusic.png` | `app` | 991 | `1764a1924d18455f` | `BLOCKED` |
| `app/src/main/res/drawable/speed.xml` | `app` | 1,063 | `f8c74afb89821019` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/speed_0_75x.xml` | `app` | 3,872 | `c6df366ef28f6003` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_25x.xml` | `app` | 4,000 | `95f028538ceab393` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_5x.xml` | `app` | 2,548 | `715ca36333e25781` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_75x.xml` | `app` | 3,369 | `d17ff09137572b36` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1x.xml` | `app` | 1,021 | `4cc1ee57e8fc11ec` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_2x.xml` | `app` | 1,277 | `93a336b3fe3572a4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/star.xml` | `app` | 578 | `b8485ab3f551d407` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/stats.xml` | `app` | 708 | `eec421ecf82643ca` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/storage.xml` | `app` | 540 | `d29f0836fc653d32` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/stream_old_player.xml` | `app` | 1,107 | `3d39f36357586027` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/subscribe.xml` | `app` | 746 | `185d136088a91d8e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/subscribed.xml` | `app` | 734 | `ee59a1620e14a9df` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/swipe.xml` | `app` | 836 | `58e0a28eced1784e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/swipe_vertical.xml` | `app` | 1,326 | `c320b53b258cd028` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sync.xml` | `app` | 736 | `fae74a098f152f8c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/tab.xml` | `app` | 797 | `f5bb0755725c82b5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/tast.png` | `app` | 845,677 | `bcd8d87f24acc949` | `BLOCKED` |
| `app/src/main/res/drawable/telegram.xml` | `app` | 662 | `911f4b86f01d43f6` | `BLOCKED` |
| `app/src/main/res/drawable/thumb_down.xml` | `app` | 604 | `9837ac2cd74976fb` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/thumb_down_filled.xml` | `app` | 562 | `30fdba5978168668` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/thumb_up_like.xml` | `app` | 858 | `5a186228f86c8ad5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/timer.xml` | `app` | 594 | `ddfe98376359f610` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/token.xml` | `app` | 682 | `39e7640345977bba` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/translate.xml` | `app` | 618 | `324d4c72959fc7fc` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/trending_down.xml` | `app` | 439 | `ef0e050f838a24d7` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/trending_up.xml` | `app` | 439 | `bf04c5e562cfec4e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/tune.xml` | `app` | 636 | `8f35fcfd104b83a3` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/update.xml` | `app` | 766 | `783eb1cf06c3251e` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/updated.xml` | `app` | 874 | `be4ffbf18640c727` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/upi_new.png` | `app` | 1,351 | `6df67f7bf8333827` | `BLOCKED` |
| `app/src/main/res/drawable/vibration.xml` | `app` | 509 | `2af8c97e7fc68876` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/visibility.xml` | `app` | 555 | `4ae6f250c29a62a5` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/visibility_off.xml` | `app` | 921 | `d72105002d3ac465` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_down.xml` | `app` | 474 | `b067b581ab6df5b2` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_mute.xml` | `app` | 414 | `77d1946a82cb7ce6` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_off.xml` | `app` | 728 | `e67bd110bede4a8c` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_off_pause.xml` | `app` | 1,386 | `cbd1ba4c90f42df4` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_up.xml` | `app` | 598 | `0a77c020fc2d6346` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/warning.xml` | `app` | 559 | `0de339e857fa28ae` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/water_drop.xml` | `app` | 519 | `7aced3c54d1da511` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/web_link.xml` | `app` | 689 | `86a6a84b61bd4e37` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/website.xml` | `app` | 1,593 | `b512e864d703c586` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/weeknd.webp` | `app` | 40,064 | `6b439991c4a912a5` | `BLOCKED` |
| `app/src/main/res/drawable/widget_adaptive_card_bg.xml` | `app` | 340 | `2e728958fd292486` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_adaptive_queue_bg.xml` | `app` | 342 | `8741712d6afe6382` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_artwork_scrim.xml` | `app` | 374 | `3e2675307bdeffbf` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_aurora_card_bg.xml` | `app` | 453 | `a812976f95db742e` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_background.xml` | `app` | 387 | `ac5d62c8c8d8e7f5` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_card_dark_bg.xml` | `app` | 340 | `6e1da35aa62bfb46` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_card_gradient_bg.xml` | `app` | 368 | `5be0d090d885cec0` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_button_bg.xml` | `app` | 293 | `4432dc67bebe3620` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_glow_play_bg.xml` | `app` | 404 | `1de50b1d1cce7b30` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_play_accent_bg.xml` | `app` | 199 | `f97aada993471bae` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_device_badge_bg.xml` | `app` | 221 | `122c6fd822fcb679` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_like_button_bg.xml` | `app` | 259 | `b7767d2cbfc11c2a` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_button_bg.xml` | `app` | 323 | `5249855f96c2cc50` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_button_bg_active.xml` | `app` | 330 | `7d95eb16f5af7143` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_1.xml` | `app` | 428 | `b49174f4298d5ec6` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_2.xml` | `app` | 420 | `ef116723249b6fea` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_3.xml` | `app` | 424 | `3e39184bcedfde0b` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_4.xml` | `app` | 437 | `06435cf41f93eef4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_idle.xml` | `app` | 315 | `73f1a193d9349d7a` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_pill_aurora_blue_bg.xml` | `app` | 300 | `e1f1584024452374` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_play_button_circular.xml` | `app` | 298 | `54fae4fe4dafc8e4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_play_pill_bg.xml` | `app` | 242 | `3656bb68e26dace2` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_downloaded.xml` | `app` | 470 | `5d1ac70144fe1cf8` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_liked.xml` | `app` | 491 | `41d1c635f2f226b4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_playlist.xml` | `app` | 477 | `0bcdb79983aeb31c` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_top.xml` | `app` | 475 | `630dabbcaefecc46` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_clip.xml` | `app` | 229 | `fd484c36df036863` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_clip_modern.xml` | `app` | 236 | `e176f07988fe6826` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill.xml` | `app` | 241 | `4dc74d0255687fd6` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill_modern.xml` | `app` | 241 | `4dc74d0255687fd6` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill_vintage.xml` | `app` | 315 | `331d40f5625991f6` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track.xml` | `app` | 220 | `fbc22630e2b8ab82` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track_modern.xml` | `app` | 247 | `a3e80ca9808f4df7` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track_vintage.xml` | `app` | 222 | `81ce973260b1e032` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_thumbnail_squircle_bg.xml` | `app` | 252 | `c2d82747d9651ff9` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_tonearm.xml` | `app` | 1,515 | `00ce56429a671436` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_tonearm_vintage.xml` | `app` | 1,529 | `242d98a09b0f663a` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_turntable_nav_bg.xml` | `app` | 246 | `3ca3192f60d1b51d` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_turntable_play_bg.xml` | `app` | 298 | `82a6d910c6addf6e` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_vintage_cream_bg.xml` | `app` | 298 | `385915fbf6b46ae4` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_vinyl_glass_bg.xml` | `app` | 349 | `3653e7b7fdc3b0e5` | `REIMPLEMENT` |
| `app/src/main/res/drawable/wifi_proxy.xml` | `app` | 909 | `9dbfb45e14f35b97` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/eko_icon-web.png` | `app` | 303,471 | `81cf946b2ea85b38` | `REMOVE` |
| `app/src/main/res/font/bbh_bartle.xml` | `app` | 416 | `ac0603b1e630121f` | `REIMPLEMENT` |
| `app/src/main/res/font/bbh_bartle_regular.ttf` | `app` | 17,880 | `6240252862fa9dad` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/font/google_sans_flex.ttf` | `app` | 4,607,592 | `9db5a1555133dad2` | `REMOVE` |
| `app/src/main/res/font/outfit.ttf` | `app` | 110,884 | `fc7287273e669297` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/font/plus_jakarta_sans.ttf` | `app` | 176,288 | `89b3fb38aa0d275d` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/font/sans_flex.ttf` | `app` | 3,997,148 | `2510a8b7a24beb1f` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/layout/widget_adaptive_compact.xml` | `app` | 6,627 | `a4c450ae544957e6` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_adaptive_expanded.xml` | `app` | 8,464 | `d75dbd155a98a0fa` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_compact_glow.xml` | `app` | 4,198 | `f899ae0f49bad6c3` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_device_connect.xml` | `app` | 10,900 | `0e3d6b0a15cfe0a3` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_modern_flow.xml` | `app` | 9,551 | `29fd1c2165e1cafa` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_queue_item.xml` | `app` | 1,131 | `6ce7fac6bb33e256` | `REIMPLEMENT` |
| `app/src/main/res/layout/widget_slim_strip.xml` | `app` | 6,444 | `7c66a0ee2536c711` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/billie_eilish_icon.xml` | `app` | 231 | `e30df0e719ec54bd` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/billie_eilish_icon_round.xml` | `app` | 231 | `e30df0e719ec54bd` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/cat_icon.xml` | `app` | 216 | `c1bc3379eee3563e` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/cat_icon_round.xml` | `app` | 216 | `c1bc3379eee3563e` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/crazy_blue_icon.xml` | `app` | 230 | `d4e6d59a0bf78abe` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/crazy_blue_icon_round.xml` | `app` | 230 | `d4e6d59a0bf78abe` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/eko_icon.xml` | `app` | 216 | `f830c6785c089832` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/eko_icon_round.xml` | `app` | 216 | `f830c6785c089832` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_craft.xml` | `app` | 228 | `776ce2f20aa26559` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_craft_round.xml` | `app` | 228 | `776ce2f20aa26559` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_millie.xml` | `app` | 276 | `322a365ec61d7b2b` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_millie_round.xml` | `app` | 276 | `322a365ec61d7b2b` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_neon_glass.xml` | `app` | 284 | `55cc88855c804b89` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_neon_glass_round.xml` | `app` | 284 | `55cc88855c804b89` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sabrina.xml` | `app` | 278 | `0dc15aabc30d05d8` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sabrina2.xml` | `app` | 280 | `1c8bf62324bcd110` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sabrina2_round.xml` | `app` | 280 | `1c8bf62324bcd110` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sabrina_round.xml` | `app` | 278 | `0dc15aabc30d05d8` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sadie.xml` | `app` | 228 | `e6c2e8dc01953b08` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_sadie_round.xml` | `app` | 228 | `e6c2e8dc01953b08` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/legacy_icon.xml` | `app` | 328 | `6aa00b73c903e236` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/legacy_icon_round.xml` | `app` | 328 | `6aa00b73c903e236` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/legacy_pixel_icon.xml` | `app` | 229 | `86f146c7b8123dc9` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/legacy_pixel_icon_round.xml` | `app` | 229 | `86f146c7b8123dc9` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/new_pixel_icon.xml` | `app` | 223 | `6f83da5eff52d60d` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/new_pixel_icon_round.xml` | `app` | 223 | `6f83da5eff52d60d` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/pookie_icon.xml` | `app` | 222 | `97965bd3fed8754d` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/pookie_icon_round.xml` | `app` | 222 | `97965bd3fed8754d` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/sky_icon.xml` | `app` | 216 | `c8678fa41b11eaa1` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/sky_icon_round.xml` | `app` | 216 | `c8678fa41b11eaa1` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/tamil_naidu_icon.xml` | `app` | 227 | `210417519a04870c` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/tamil_naidu_icon_round.xml` | `app` | 227 | `210417519a04870c` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/wierd_cat_icon.xml` | `app` | 228 | `4c793c01cf210d50` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi-v26/wierd_cat_icon_round.xml` | `app` | 228 | `4c793c01cf210d50` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi/ic_launcher_static.xml` | `app` | 276 | `f2d7306cc42809d2` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-anydpi/ic_launcher_static_round.xml` | `app` | 276 | `f2d7306cc42809d2` | `REIMPLEMENT` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon.png` | `app` | 10,939 | `acf5c5293ef2ab53` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon_foreground.png` | `app` | 35,475 | `cf72dcb504c474a7` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon_round.png` | `app` | 11,241 | `1a0417a30898c003` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/cat_icon.png` | `app` | 10,477 | `572d474d89ea1756` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/cat_icon_foreground.png` | `app` | 29,168 | `0c9478d577650826` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/cat_icon_round.png` | `app` | 11,100 | `d82386c180b914be` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon.png` | `app` | 11,529 | `abe42fb0dc157a76` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon_foreground.png` | `app` | 42,949 | `e0968c0b6216655c` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon_round.png` | `app` | 11,917 | `e245c0082a7f5f59` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon.png` | `app` | 9,935 | `77dff998448479ec` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon_foreground.png` | `app` | 34,949 | `2c97c49b4fff386c` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon_round.png` | `app` | 11,116 | `b40f9e9ca77b6eb4` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft.png` | `app` | 7,164 | `5cd84946523a10c3` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft_foreground.png` | `app` | 35,694 | `89355c53af46f552` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft_round.png` | `app` | 7,420 | `c8ac05ae03d95c70` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie.png` | `app` | 11,124 | `fc64a006b40b8299` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie_foreground.png` | `app` | 32,219 | `cb29e5a7715941bb` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie_round.png` | `app` | 11,600 | `dd34da4d5d00e3ea` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass.png` | `app` | 8,521 | `21b66aa70d4ebefe` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_foreground.png` | `app` | 21,280 | `2a8cabffee23fe6d` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_round.png` | `app` | 10,680 | `8ac9f3ca28870e37` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina.png` | `app` | 10,707 | `864cb4f49cce92e5` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2.png` | `app` | 10,650 | `baa4da369270538a` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_foreground.png` | `app` | 56,350 | `93835b8cc405b4c4` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_round.png` | `app` | 11,351 | `9301f6ae4f46ad32` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_foreground.png` | `app` | 40,545 | `278be351b314b736` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_round.png` | `app` | 11,323 | `49a0ad90ff709731` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie.png` | `app` | 10,360 | `a0c17f210663679f` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_foreground.png` | `app` | 34,380 | `f0aa032a2c726784` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_round.png` | `app` | 10,967 | `4ad4dfc8a43697f7` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/legacy_icon.png` | `app` | 6,325 | `080788db84dbab59` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_foreground.png` | `app` | 11,410 | `90e9869c81be75a0` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_monochrome.png` | `app` | 8,120 | `607b3aff750afa94` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_round.png` | `app` | 7,979 | `e2f57ab44dcdf019` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon.png` | `app` | 4,691 | `453ecfc91a0d94d2` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_foreground.png` | `app` | 10,033 | `c3b28b943ec8bd3f` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_round.png` | `app` | 6,685 | `465563ae231f984d` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon.png` | `app` | 8,070 | `3296e2bd78e15e0b` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon_foreground.png` | `app` | 14,815 | `cb992a8a1f66fffd` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon_round.png` | `app` | 9,322 | `e267e081e5df3bfc` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon.png` | `app` | 11,278 | `562a0e0faf64bb03` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon_foreground.png` | `app` | 46,738 | `0e9a0d1f491872f5` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon_round.png` | `app` | 11,960 | `50638b131eeae9c3` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon.png` | `app` | 10,802 | `fd8cf5ad52271a1c` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon_foreground.png` | `app` | 39,913 | `8b12c38c63d15895` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon_round.png` | `app` | 11,350 | `7c059f3a97cb8ed4` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon.png` | `app` | 11,355 | `bd1a211dc07abb9f` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_foreground.png` | `app` | 65,478 | `701ad313121f8ab6` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_round.png` | `app` | 11,592 | `eb8cb824761e9e55` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon.png` | `app` | 11,387 | `339f2b30de4524b9` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon_foreground.png` | `app` | 59,948 | `2f9eb95de0fcee3d` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon_round.png` | `app` | 12,166 | `02f125d9e77f138b` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/billie_eilish_icon.png` | `app` | 3,300 | `79bdcc160f0f3309` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/billie_eilish_icon_round.png` | `app` | 3,329 | `44170811a7bc30ca` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/cat_icon.png` | `app` | 3,191 | `b824825b64a1cb1f` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/cat_icon_round.png` | `app` | 3,297 | `33828df32421c273` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/crazy_blue_icon.png` | `app` | 3,501 | `8da28ac1685f1470` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/crazy_blue_icon_round.png` | `app` | 3,580 | `c055bdd40254232c` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/eko_icon.png` | `app` | 3,130 | `c4cd5e387b60c622` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/eko_icon_round.png` | `app` | 3,257 | `029da29cc5e0e1ce` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_craft.png` | `app` | 2,621 | `68ed309de3865c51` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_craft_round.png` | `app` | 2,589 | `7ab7ffe3739a4644` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_millie.png` | `app` | 3,255 | `46eb0064c4f62b46` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_millie_round.png` | `app` | 3,304 | `eaffd51f3c7d8af7` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass.png` | `app` | 2,719 | `e5b935c8b69d925d` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass_round.png` | `app` | 3,290 | `d54eb87357326303` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina.png` | `app` | 3,255 | `51ca46c5e8f6a389` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2.png` | `app` | 3,137 | `a3e82026af1ee85a` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2_round.png` | `app` | 3,230 | `d1ef91de144b716f` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina_round.png` | `app` | 3,324 | `b320de9aeedbcfba` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sadie.png` | `app` | 3,092 | `5a339332f76375d2` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sadie_round.png` | `app` | 3,218 | `67c97b4ae05297f2` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/legacy_icon.png` | `app` | 2,272 | `ffba14f0e4a86215` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_icon_round.png` | `app` | 2,838 | `fae4ca5379bb9ee9` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_pixel_icon.png` | `app` | 2,010 | `2302fc7ce3a57283` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_pixel_icon_round.png` | `app` | 2,580 | `678ae09aa99b7be3` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/new_pixel_icon.png` | `app` | 2,852 | `eb1486e8f3aaf3cd` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/new_pixel_icon_round.png` | `app` | 3,073 | `29cc71f53f911bd8` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/pookie_icon.png` | `app` | 3,316 | `7493157a82bbade0` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/pookie_icon_round.png` | `app` | 3,392 | `2e9ce2c6c135c726` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/sky_icon.png` | `app` | 3,332 | `060217b9336ed6ff` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/sky_icon_round.png` | `app` | 3,429 | `dec327e88a33064c` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/tamil_naidu_icon.png` | `app` | 3,423 | `98101386bb664bb9` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/tamil_naidu_icon_round.png` | `app` | 3,400 | `6cc777a1626f1b08` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/wierd_cat_icon.png` | `app` | 3,268 | `aed5803b6168c757` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/wierd_cat_icon_round.png` | `app` | 3,386 | `92ae669ab9c7f7fa` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon.png` | `app` | 5,152 | `dc5c72bac5d9db49` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon_foreground.png` | `app` | 17,176 | `aaf74b78c37a79f1` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon_round.png` | `app` | 5,438 | `39c0d7c510d14eaf` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/cat_icon.png` | `app` | 5,030 | `278083b6a05164bc` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/cat_icon_foreground.png` | `app` | 14,041 | `fba110366aadf245` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/cat_icon_round.png` | `app` | 5,407 | `cfe513bfbd42f415` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon.png` | `app` | 5,508 | `9dceadad29d9d359` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon_foreground.png` | `app` | 21,628 | `c4faece01f6574a2` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon_round.png` | `app` | 5,869 | `f2ee644892e2f474` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon.png` | `app` | 4,769 | `37593588247f54b4` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon_foreground.png` | `app` | 16,869 | `a210f716aa3cb7cc` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon_round.png` | `app` | 5,248 | `ba25a80957e2eaf9` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft.png` | `app` | 3,963 | `6c5eadc90defe250` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft_foreground.png` | `app` | 19,256 | `75ccc8e0040f73e0` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft_round.png` | `app` | 4,086 | `b4667ab30d220ed0` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie.png` | `app` | 5,160 | `c80395030ace3e71` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie_foreground.png` | `app` | 15,162 | `558fe946fc439fd7` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie_round.png` | `app` | 5,464 | `f529a8846447e963` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass.png` | `app` | 4,395 | `54825abf137019d5` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_foreground.png` | `app` | 11,889 | `a02f81d2d79a1409` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_round.png` | `app` | 5,351 | `bd61f273b79e2be0` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina.png` | `app` | 5,072 | `92cc31dba06c052f` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2.png` | `app` | 4,971 | `efa6a2f957cd22c9` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_foreground.png` | `app` | 27,122 | `202c3d18e23289de` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_round.png` | `app` | 5,388 | `b1ef2f228aa805eb` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_foreground.png` | `app` | 18,589 | `247fc9e89adccd25` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_round.png` | `app` | 5,474 | `3757f092bb562063` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie.png` | `app` | 4,946 | `510c6f1ada49f1f3` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_foreground.png` | `app` | 16,358 | `f190fde2dfd24d0d` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_round.png` | `app` | 5,257 | `5d7e9c1252a41e37` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/legacy_icon.png` | `app` | 3,395 | `5cff18e73eef07a1` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_foreground.png` | `app` | 7,523 | `dac160d9d99ce9a5` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_monochrome.png` | `app` | 5,337 | `626416873ab2fa53` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_round.png` | `app` | 4,250 | `18123883f5c30571` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon.png` | `app` | 2,428 | `c39089095aea915c` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_foreground.png` | `app` | 6,102 | `6bd3b5928056101f` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_round.png` | `app` | 3,904 | `bad86cdccdff4c69` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon.png` | `app` | 3,771 | `64159133486bc3d6` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon_foreground.png` | `app` | 8,475 | `c77d4e9a98539288` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon_round.png` | `app` | 4,934 | `ac921f558a94a6b6` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon.png` | `app` | 5,246 | `07b314c844a8df2d` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon_foreground.png` | `app` | 22,642 | `f90e88220b7d90d6` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon_round.png` | `app` | 5,682 | `2d2957d12dc91c5e` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon.png` | `app` | 5,240 | `c9c565d0c65634ab` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon_foreground.png` | `app` | 20,060 | `d03d2d68713cc372` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon_round.png` | `app` | 5,620 | `f9f392ede297cf59` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon.png` | `app` | 5,337 | `37179e707878f19e` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_foreground.png` | `app` | 31,792 | `b414d8f34d7bd84e` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_round.png` | `app` | 5,623 | `3daeca137d30ce89` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon.png` | `app` | 5,122 | `889b00b4aebb4210` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon_foreground.png` | `app` | 26,604 | `1405f7d23dc97c57` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon_round.png` | `app` | 5,611 | `e38e5ec1945c7501` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon.png` | `app` | 17,862 | `9398686922bdba91` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_foreground.png` | `app` | 58,986 | `99b21ac0fe3d19fd` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_round.png` | `app` | 18,674 | `253202ffb6111e48` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/cat_icon.png` | `app` | 17,210 | `2c95c7ab8ea497ff` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/cat_icon_foreground.png` | `app` | 50,651 | `80ead1c6119bb375` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/cat_icon_round.png` | `app` | 18,469 | `767b6250edf6508e` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon.png` | `app` | 18,910 | `7dee371428b5da9d` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_foreground.png` | `app` | 75,965 | `691cbba83e8c3888` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_round.png` | `app` | 19,197 | `35e682f12ac5352d` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon.png` | `app` | 16,375 | `6b0c0fbcc9243408` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon_foreground.png` | `app` | 56,458 | `331c014834a02a5d` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon_round.png` | `app` | 18,613 | `d4c175b0829f7e9a` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft.png` | `app` | 10,673 | `a4c6da912437b44c` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_foreground.png` | `app` | 58,487 | `db0a28a88f5ea87c` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_round.png` | `app` | 11,910 | `ad138dfa4380837e` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie.png` | `app` | 18,571 | `baf6a8e565a22c7a` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_foreground.png` | `app` | 55,141 | `7ffe4fe2edadefb1` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_round.png` | `app` | 19,591 | `0e524df8bd51e5af` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass.png` | `app` | 13,943 | `92706a00770e9fe9` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_foreground.png` | `app` | 36,315 | `f0535778875458d9` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_round.png` | `app` | 17,164 | `28bbc830a799d38c` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina.png` | `app` | 17,774 | `fc3d37c21fc4a52a` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2.png` | `app` | 17,804 | `0ee74df9a01a7a63` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_foreground.png` | `app` | 96,217 | `fda8b47dd492bfcb` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_round.png` | `app` | 19,171 | `0d33e2cbf891fd64` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_foreground.png` | `app` | 71,501 | `a33ba7a959d03571` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_round.png` | `app` | 19,132 | `eed8f24a3e96987f` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie.png` | `app` | 17,140 | `0dd3fb1dda1ce8e1` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_foreground.png` | `app` | 58,208 | `9c53d9c0274c96b7` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_round.png` | `app` | 18,292 | `fd88ecbc47ac91c4` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon.png` | `app` | 9,840 | `d40ae9cc179f0718` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_foreground.png` | `app` | 17,592 | `333e0ef5635cf0ae` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_monochrome.png` | `app` | 12,987 | `d0825cdbc03403f0` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_round.png` | `app` | 11,425 | `7e827dbbb83dd4d4` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon.png` | `app` | 7,127 | `859fe1e49cbf078f` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_foreground.png` | `app` | 14,602 | `c860ec3d3b9c6224` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_round.png` | `app` | 9,860 | `a6d804dabc4290e5` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon.png` | `app` | 11,851 | `6255fb5751b1f26a` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon_foreground.png` | `app` | 23,301 | `b7acd27ef94bbec4` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon_round.png` | `app` | 14,608 | `5c261aad3f0b1784` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon.png` | `app` | 19,708 | `7634d5cb4164c308` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon_foreground.png` | `app` | 85,292 | `22dae6541b100bbc` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon_round.png` | `app` | 20,368 | `ed4314c8d98c7e68` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon.png` | `app` | 17,715 | `15063683a9519a94` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon_foreground.png` | `app` | 71,340 | `a602ca00c1ffd42c` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon_round.png` | `app` | 18,228 | `120284f1b63727dc` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon.png` | `app` | 18,503 | `4e22ef01c5b0bb1f` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_foreground.png` | `app` | 109,394 | `e811191dc13a92a7` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_round.png` | `app` | 19,365 | `dd59201fe5969eea` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon.png` | `app` | 19,840 | `e313d5f7c4f3d3eb` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_foreground.png` | `app` | 110,265 | `5d2fe1cba08c2f34` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_round.png` | `app` | 21,337 | `3fe58e2041279615` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon.png` | `app` | 36,864 | `34898c5e6d0667dc` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_foreground.png` | `app` | 125,555 | `77bf7069e687efff` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_round.png` | `app` | 38,834 | `aa89ae184b491807` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon.png` | `app` | 36,033 | `06b63c261951177c` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon_foreground.png` | `app` | 107,257 | `195d62c2a480a7f0` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon_round.png` | `app` | 38,107 | `db9199d6c9d17b1b` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon.png` | `app` | 38,372 | `b0873e2b0ee02de6` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_foreground.png` | `app` | 156,608 | `7241fc80d53f1748` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_round.png` | `app` | 38,670 | `a2444b6242289122` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon.png` | `app` | 33,780 | `7e72518529dd39e8` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon_foreground.png` | `app` | 116,426 | `ac1c89145a7811a7` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon_round.png` | `app` | 38,234 | `668efe153922ec99` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft.png` | `app` | 19,629 | `3370c19c9124fc6e` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_foreground.png` | `app` | 121,597 | `cd4961907f7a9019` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_round.png` | `app` | 22,213 | `285d50cdc08f5097` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie.png` | `app` | 39,320 | `f840f093f9fd6ba5` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_foreground.png` | `app` | 116,787 | `a5d52573788970ce` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_round.png` | `app` | 41,372 | `e90682866abe159c` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass.png` | `app` | 26,814 | `a12d1ba2a87b10dc` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_foreground.png` | `app` | 84,161 | `2b545ce37b450ca1` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_round.png` | `app` | 33,787 | `20b8feae916efdb4` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina.png` | `app` | 37,447 | `5e7d67a9469c2225` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2.png` | `app` | 37,362 | `f59955f85019d00d` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_foreground.png` | `app` | 202,050 | `c1d435ea766ff904` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_round.png` | `app` | 40,395 | `3aed844189f8ddb5` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_foreground.png` | `app` | 152,909 | `25b7ff1173a8dfc7` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_round.png` | `app` | 40,255 | `4ab1c33c5b7fb433` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie.png` | `app` | 35,666 | `cf6024777ff3fa13` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_foreground.png` | `app` | 122,984 | `2fd08f2f1c901e4b` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_round.png` | `app` | 38,364 | `57c4d3947ea7d4da` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon.png` | `app` | 15,209 | `c717e2b070408ed7` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_foreground.png` | `app` | 29,371 | `361afcdddc36d0d7` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_monochrome.png` | `app` | 22,286 | `0e20715ee7e7e8f3` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_round.png` | `app` | 18,508 | `5267cd9056a95c9d` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon.png` | `app` | 10,112 | `3442f7c52c12c1e8` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_foreground.png` | `app` | 26,650 | `e071b64b6540dca5` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_round.png` | `app` | 16,545 | `6541bedbcb90a4f6` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon.png` | `app` | 20,160 | `082cd2ee09e00abe` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_foreground.png` | `app` | 42,795 | `15459d275c391755` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_round.png` | `app` | 26,494 | `ea87a2f53d28b2c0` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon.png` | `app` | 42,068 | `49e1588d3a4cc5ed` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon_foreground.png` | `app` | 176,966 | `ff821734d4180f5c` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon_round.png` | `app` | 42,690 | `1af1fcf7c2985d51` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon.png` | `app` | 35,964 | `43be0b5f0634f779` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon_foreground.png` | `app` | 155,633 | `101a99e88dc19a4f` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon_round.png` | `app` | 36,612 | `a4e22b557a7f7779` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon.png` | `app` | 37,722 | `f2dfe21961065b80` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_foreground.png` | `app` | 228,575 | `f04518406824815c` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_round.png` | `app` | 39,837 | `f34763436beb1dfd` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon.png` | `app` | 44,877 | `58d1324216df2553` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_foreground.png` | `app` | 249,983 | `834c83d052d05d82` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_round.png` | `app` | 49,568 | `895d7b56c7195b55` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon.png` | `app` | 62,089 | `017d9660c9617bad` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_foreground.png` | `app` | 213,885 | `55eeddfe8ec18f44` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_round.png` | `app` | 65,043 | `79112fbb83490d62` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon.png` | `app` | 62,452 | `6f8e60600bbb870b` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon_foreground.png` | `app` | 184,877 | `38a399776f668826` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon_round.png` | `app` | 65,216 | `43a86323215e14d7` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon.png` | `app` | 63,816 | `4a0206943097b130` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_foreground.png` | `app` | 269,268 | `80d4aaf56b1a5a4b` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_round.png` | `app` | 62,666 | `9a090bca8af2ee25` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon.png` | `app` | 55,015 | `1c25d6405d51acf0` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon_foreground.png` | `app` | 184,360 | `2c18e24c5ea2a425` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon_round.png` | `app` | 63,497 | `85e9d3f262b1f177` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft.png` | `app` | 32,134 | `5c918e862f7804d4` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_foreground.png` | `app` | 201,477 | `bd86d87e23eed7bd` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_round.png` | `app` | 35,860 | `d86ea64c809a0952` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie.png` | `app` | 67,268 | `65a2c7ea5a0eb7ad` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_foreground.png` | `app` | 201,058 | `07d360e3efc372a2` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_round.png` | `app` | 70,344 | `5d6d678491f71921` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass.png` | `app` | 45,240 | `decea07f63375d5b` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_foreground.png` | `app` | 130,007 | `1bc0bfba6fb3a698` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_round.png` | `app` | 57,881 | `4d0e6fbb5a6c23a3` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina.png` | `app` | 65,126 | `a3bcced90848994c` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2.png` | `app` | 63,686 | `9cff294cf4bc72eb` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_foreground.png` | `app` | 342,291 | `e7fefcfb7ae6799f` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_round.png` | `app` | 68,575 | `7f9e76266e38c3f3` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_foreground.png` | `app` | 272,727 | `5cfd1ee0f50ab887` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_round.png` | `app` | 69,553 | `97f080db8fff5321` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie.png` | `app` | 60,410 | `8035fd556ae9f84a` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_foreground.png` | `app` | 211,062 | `75e6fc40c6c766ec` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_round.png` | `app` | 64,407 | `245196ea5c6d3574` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon.png` | `app` | 23,814 | `fe06a8c95ed4c7bb` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_foreground.png` | `app` | 44,875 | `864a0815b6b45e23` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_monochrome.png` | `app` | 35,893 | `ea1ff53f1e67e7f7` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_round.png` | `app` | 27,829 | `965730bff073fcb4` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon.png` | `app` | 19,748 | `7707ae16a8da97c1` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_foreground.png` | `app` | 39,895 | `4bc83bdb9837a193` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_round.png` | `app` | 24,785 | `7175fecf17a4fbc8` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon.png` | `app` | 34,922 | `d433670e62e80998` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_foreground.png` | `app` | 68,087 | `245f6660c8430cf2` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_round.png` | `app` | 40,896 | `74fd4dc99982ce91` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon.png` | `app` | 71,340 | `69b28e380559ed1d` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon_foreground.png` | `app` | 296,082 | `57e642b9fbe36c62` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon_round.png` | `app` | 70,276 | `13cc6916bd105ff3` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon.png` | `app` | 60,894 | `91a0309d4a1cb0c5` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon_foreground.png` | `app` | 269,251 | `bb2dc590ea22e25b` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon_round.png` | `app` | 60,872 | `560dc4dbb25bc342` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon.png` | `app` | 63,912 | `b1b16e0b6c81acbe` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_foreground.png` | `app` | 379,010 | `1eb00713de535e53` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_round.png` | `app` | 67,399 | `87a6c42843afc543` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon.png` | `app` | 82,865 | `c873cfbee7c00c56` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_foreground.png` | `app` | 444,748 | `ed743bce08d1864a` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_round.png` | `app` | 88,345 | `f5711bb50d36cbb0` | `REMOVE` |
| `app/src/main/res/raw/setting.json` | `app` | 4,638 | `9fcd39005f93791d` | `BLOCKED` |
| `app/src/main/res/resources.properties` | `app` | 24 | `e5b0dfdb7d3662b1` | `REIMPLEMENT` |
| `app/src/main/res/values-night-v31/strings.xml` | `app` | 173 | `63e40e44168e4cf1` | `REIMPLEMENT` |
| `app/src/main/res/values-night-v31/widget_colors.xml` | `app` | 918 | `3c30f17f0827718c` | `REIMPLEMENT` |
| `app/src/main/res/values-night/colors.xml` | `app` | 1,012 | `e8c6be360339235d` | `REIMPLEMENT` |
| `app/src/main/res/values-night/widget_colors.xml` | `app` | 1,282 | `93ab9b48969c3381` | `REIMPLEMENT` |
| `app/src/main/res/values-v31/strings.xml` | `app` | 173 | `63e40e44168e4cf1` | `REIMPLEMENT` |
| `app/src/main/res/values-v31/widget_colors.xml` | `app` | 908 | `e265f11469ca45d4` | `REIMPLEMENT` |
| `app/src/main/res/values/cat_icon_background.xml` | `app` | 116 | `de31d1283f55e843` | `REIMPLEMENT` |
| `app/src/main/res/values/colors_sabrina.xml` | `app` | 122 | `928caa737e2d1e7a` | `REIMPLEMENT` |
| `app/src/main/res/values/colors_sabrina2.xml` | `app` | 128 | `d49e56159a0000e6` | `REIMPLEMENT` |
| `app/src/main/res/values/crazy_blue_icon_background.xml` | `app` | 123 | `ade68459c6c66bb5` | `REIMPLEMENT` |
| `app/src/main/res/values/eko_icon_background.xml` | `app` | 76 | `910936ddc5338047` | `REIMPLEMENT` |
| `app/src/main/res/values/ic_launcher_craft_background.xml` | `app` | 85 | `5f68505f571e086d` | `REIMPLEMENT` |
| `app/src/main/res/values/ic_launcher_millie_background.xml` | `app` | 126 | `0dc056fa8cbb4537` | `REIMPLEMENT` |
| `app/src/main/res/values/ic_launcher_neon_glass_background.xml` | `app` | 130 | `2f2d088365908838` | `REIMPLEMENT` |
| `app/src/main/res/values/ic_launcher_sadie_background.xml` | `app` | 85 | `7f2dde5e11d7c16d` | `REIMPLEMENT` |
| `app/src/main/res/values/pookie_icon_background.xml` | `app` | 119 | `19508186393caecf` | `REIMPLEMENT` |
| `app/src/main/res/values/sky_icon_background.xml` | `app` | 116 | `d7825ad4adcbed4a` | `REIMPLEMENT` |
| `app/src/main/res/values/values.xml` | `app` | 443 | `2fd5c60548745ce8` | `REIMPLEMENT` |
| `app/src/main/res/values/widget_colors.xml` | `app` | 1,257 | `3b8fbb7d3d74fb3b` | `REIMPLEMENT` |
| `app/src/main/res/values/wierd_cat_icon_background.xml` | `app` | 82 | `b694bd023392a127` | `REIMPLEMENT` |
| `app/src/main/res/wierd_cat_icon-web.png` | `app` | 576,849 | `360c4001c12cf35e` | `REMOVE` |
| `app/src/main/res/xml-v31/music_widget_info.xml` | `app` | 606 | `6c908a588b695d8d` | `REIMPLEMENT` |
| `app/src/main/res/xml-v31/playlist_widget_info.xml` | `app` | 688 | `23e1449ff8e519d8` | `REIMPLEMENT` |
| `app/src/main/res/xml-v31/recognizer_widget_info.xml` | `app` | 615 | `ee49616b5a70d427` | `REIMPLEMENT` |
| `app/src/main/res/xml-v31/turntable_widget_info.xml` | `app` | 612 | `c9418a0939beae76` | `REIMPLEMENT` |
| `app/src/main/res/xml/automotive_app_desc.xml` | `app` | 97 | `49e9e4b14c0e5f92` | `REIMPLEMENT` |
| `app/src/main/res/xml/backup_rules.xml` | `app` | 538 | `354dda60436b2c5e` | `REIMPLEMENT` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `app` | 1,224 | `0950c5effc62572f` | `REIMPLEMENT` |
| `app/src/main/res/xml/music_widget_info.xml` | `app` | 606 | `6c908a588b695d8d` | `REIMPLEMENT` |
| `app/src/main/res/xml/network_security_config.xml` | `app` | 381 | `6bf006ddd2f6c12a` | `REIMPLEMENT` |
| `app/src/main/res/xml/playlist_widget_info.xml` | `app` | 617 | `4007173e6f6ea8dd` | `REIMPLEMENT` |
| `app/src/main/res/xml/provider_paths.xml` | `app` | 256 | `8b46dd02a9d9428f` | `REIMPLEMENT` |
| `app/src/main/res/xml/recognizer_widget_info.xml` | `app` | 615 | `ee49616b5a70d427` | `REIMPLEMENT` |
| `app/src/main/res/xml/turntable_widget_info.xml` | `app` | 612 | `c9418a0939beae76` | `REIMPLEMENT` |
| `gradle/wrapper/gradle-wrapper.jar` | `gradle` | 48,749 | `3b8a25775a69158b` | `RETAIN` |
| `gradlew` | `(root)` | 8,656 | `a5a5c199ba02189a` | `RETAIN` |
| `gradlew.bat` | `(root)` | 4,776 | `31ec098bd961c460` | `RETAIN` |
| `lyrics/src/main/res/values-es/strings.xml` | `lyrics` | 576 | `016b5d11510296d2` | `REIMPLEMENT` |
| `lyrics/src/main/res/values/strings.xml` | `lyrics` | 537 | `8971ce172c6d6a1a` | `REIMPLEMENT` |
| `metadata/build.gradle.kts` | `metadata` | 133 | `d316508d501578b9` | `REFACTOR` |

---

## 4. Six Renamed Echo / Metrolist Kotlin Files Exposed in `:app`

Global cross-file inverted-index scanning and manual R2 code inspection exposed 6 Kotlin files in `:app` whose filenames were changed from `Echo-Music`, bypassing naive same-path comparisons:

| Revenge Path | Echo-Music Original Path | Lines | Token Jaccard | Line Overlap | Shingle Jaccard | Verbatim Evidence & R2 Disposition |
|---|---|---:|---:|---:|---:|---|
| `app/src/main/kotlin/com/revenge/music/ui/component/RevengeLyrics.kt` | `.../ui/component/EchoMusicLyrics.kt` | 377 | 0.994 | 0.924 | 0.976 | Identical lyric line rendering, sync state, and tap-seek logic; symbol rename only -> `REIMPLEMENT` |
| `app/src/main/kotlin/com/revenge/music/widget/RevengeWidgetManager.kt` | `.../widget/EchoMusicWidgetManager.kt` | 1,481 | 0.996 | 0.856 | 0.984 | Line 1 retains `Metrolist Project (C) 2026 Licensed under GPL-3.0`; identical widget push orchestration -> `REIMPLEMENT` |
| `app/src/main/kotlin/com/revenge/music/updates/commitscreen/CommitItem.kt` | `.../echomusic/commitscreen/commiitem.kt` | 126 | 1.000 | 0.856 | 1.000 | 100% token & shingle match; identical commit UI card -> `REIMPLEMENT` |
| `app/src/main/kotlin/com/revenge/music/updates/updater/downloadmanager/DownloadManagerUtils.kt` | `.../echomusic/updater/downloadmanager/downloadmanager.kt` | 99 | 0.980 | 0.973 | 0.919 | Identical Android `DownloadManager` APK download helper -> `REIMPLEMENT` |
| `app/src/main/kotlin/com/revenge/music/updates/updater/updaterscreen.kt` | `.../echomusic/updater/echomusicupdater.kt` | 954 | 0.963 | 0.904 | 0.892 | Identical GitHub release updater screen & state flow -> `REIMPLEMENT` |
| `app/src/main/kotlin/com/revenge/music/ui/screens/SettingsDialog.kt` | `.../ui/screens/SettingDialoge.kt` | 261 | 0.434 | 0.505 | 0.186 | Adapted from Echo `SettingDialoge.kt`: identical `ModalBottomSheet`, `SAPISID` check, `Material3SettingsGroup(..., compact = true)`, `customIcon = { Text("Ai") }` -> `REIMPLEMENT` |

---

## 5. Archived Diffs: `Revenge-Wasted` `18 M` (`revenge_wasted_18M.diff`) & `app/build.gradle.kts` (`app_build_gradle_kts.diff`)

### 5.1 `Revenge/app/build.gradle.kts` Diff (`app_build_gradle_kts.diff`, `+27/-22`)

- **Contents:** Parameterizes `versionCode` via `-PrevengeVersionCode` (default `1`) and `versionName` via `-PrevengeVersionName` (default `"1.0.0"`), and gates split-ABI product flavors (`arm64`, `armeabi`, `x86`, `x86_64`) behind `-PallAbis` so default test/CI runs evaluate `universal` only instead of 10 redundant variants.
- **R2/M3 Verdict:** Valid build-system engineering improvement. **Adopt in Wave 0 (`REFACTOR`).**

### 5.2 `Revenge-Wasted` `18 M` Diff (`revenge_wasted_18M.diff`, `+1,192/-1,056` across 18 files)

#### A. Six CI & Documentation Edits (Operational Findings to Carry Into Wave 0 `REFACTOR`)
| # | File | Diff (+/-) | Key Change & Audit Assessment |
|---:|---|---:|---|
| 1 | `.github/workflows/release.yml` | +13/-2 | Fixes `workflow_dispatch` `inputs.tag` and passes `-PrevengeVersionName` / `-PrevengeVersionCode` -> **Adopt in Wave 0** |
| 2 | `PROGRESS.md` | +10/-2 | Logs checkpoint `11ae620`, `-PallAbis` change, and admits `"Independence rewrite of the highest-overlap behaviour files (in progress)"` |
| 3 | `README.md` | +12/-3 | Corrects USB DAC claim (`"platform mixer, not bit-perfect"`) and updates lyrics/DSP lists -> **Carry honesty fix into Wave 0** |
| 4 | `docs/BUILDING.md` | +5/-4 | Documents `-PallAbis` opt-in for split-ABI builds |
| 5 | `docs/LICENSING.md` | +14/-0 | Adds 6-row bundled asset table flagging ONNX model licenses as unresolved; however misstates `meriyah.js` as MIT (actually ISC), misstates `po_token.html` as project asset, and wrongly implies `vocals_umxhq_int8.onnx` is used at runtime |
| 6 | `docs/index.html` | +2/-1 | Clarifies releases ship FOSS build while GMS is built from source -> **Consistent with GPL-3.0 constraint** |

#### B. Twelve Kotlin Behavior Diffs (REJECTED Under R2 as Superficial Refactorings -> `REIMPLEMENT`)
| # | File | Diff (+/-) | Baseline Echo Match (`11ae6207`) | Superficial Rewrite Technique Observed in `18 M` |
|---:|---|---:|---|---|
| 7 | `audio-dsp/src/main/kotlin/com/revenge/dsp/controller/DspControllerImpl.kt` | +62/-52 | tok=0.774, ln=0.80 | Replaces 2 boolean args with private `EqInstruction` helper; identical EQ/processor orchestration |
| 8 | `innertube/src/main/kotlin/com/revenge/innertube/NetworkConfig.kt` | +29/-35 | tok=0.758, ln=0.78 | Flattens `Configuration` wrapper into 3 fields and renames helper wrappers; identical HTTP/proxy/locale setup |
| 9 | `innertube/src/main/kotlin/com/revenge/innertube/pages/AlbumPage.kt` | +57/-62 | tok=0.759, ln=0.82 | Extracts private helpers (`videoId()`, `songCard()`, etc.); identical InnerTube JSON traversal |
| 10 | `innertube/src/main/kotlin/com/revenge/innertube/pages/ArtistItemsPage.kt` | +112/-90 | tok=0.708, ln=0.76 | Extracts row-mapper lambdas & local variable renames; identical parser behavior |
| 11 | `innertube/src/main/kotlin/com/revenge/innertube/pages/ArtistPage.kt` | +169/-146 | tok=0.713, ln=0.78 | Extracts section helpers & renames locals; identical shelf parsing |
| 12 | `innertube/src/main/kotlin/com/revenge/innertube/pages/HomePage.kt` | +202/-169 | tok=0.692, ln=0.75 | Splits helper functions and reorganizes `when` branches; identical renderer parsing |
| 13 | `innertube/src/main/kotlin/com/revenge/innertube/pages/PageHelper.kt` | +40/-39 | tok=0.625, ln=0.71 | Reorders/renames filter helper functions; identical menu/badge extraction |
| 14 | `innertube/src/main/kotlin/com/revenge/innertube/pages/PlaylistPage.kt` | +45/-46 | tok=0.640, ln=0.72 | Extracts header/track helpers; identical continuation & track extraction |
| 15 | `innertube/src/main/kotlin/com/revenge/innertube/pages/SearchPage.kt` | +187/-170 | tok=0.696, ln=0.76 | Extracts `failureOf()`/shelf helpers and renames variables; identical search parsing |
| 16 | `innertube/src/main/kotlin/com/revenge/innertube/pages/SearchSuggestionPage.kt` | +142/-137 | tok=0.669, ln=0.74 | Extracts suggestion helper functions; identical JSON path traversal |
| 17 | `lyrics/src/main/kotlin/com/revenge/music/api/AiPrompts.kt` | +42/-48 | tok=0.332, ln=0.92 | Rewords prompt section headings & KDoc while keeping identical prompt schema & output format |
| 18 | `playback/src/main/kotlin/com/revenge/music/playback/RoutingAudioSink.kt` | +49/-50 | tok=0.831, ln=0.85 | Extracts `relay()` wrapper & renames sink parameters; identical Media3 `ForwardingAudioSink` routing |

**R2 Conclusion:** Under `ORIGINAL_REQUEST.md` R2 (*"Never disguise origin via superficial rewriting (renaming packages, classes, variables, formatting, comments, or file organisation)"*), all 12 Kotlin diffs are **rejected** as proof of independence and scheduled for genuine clean-room `REIMPLEMENT` from M2 specifications.
