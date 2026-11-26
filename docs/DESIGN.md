# Design direction

## Design read

Reading this as: a release-download page for privacy-minded Android listeners,
in an editorial liner-notes style, dial ENERGY 2 / RHYTHM 2 / MOTION 1.

## Why these choices

- **Paper and ink palette (#F4F1EA, #171512, #5B554B)** with one oxblood accent
  (#A3311F): album liner notes are printed on paper, and the accent marks the
  one action that matters, the download. All pairs measure above WCAG AA.
- **System serif display (Iowan Old Style, Palatino, Georgia)** and system sans
  body: a record sleeve voice without fetching a webfont, so the page survives
  offline and paints fast.
- **Numbered sections (01 to 04)** and a thin accent rule: a repeated
  liner-notes motif. The numbers are navigation aids, not decoration.
- **Left-aligned editorial column, no cards or bento**: the content is prose and
  one release block, so a card grid would impose hierarchy that does not exist.
- **No screenshots**: the app has not been visually verified on a device in this
  build session, and an illustrative mockup would be a fabricated product shot.
  The page sells the release artifact and the source, both real.
- **Motion level 1**: only hover, focus, and the countdown state change.
- **Theme toggle**: both light and dark modes are first class; dark defaults to
  the system preference and the choice is remembered locally.

## Claims policy

Every capability listed on the page is implemented in the app source in this
repository. No user counts, ratings, testimonials, or performance numbers are
shown. When a release does not exist, the page says so instead of inventing one.

## Countdown behaviour

The download button starts a visible 30 second countdown and then opens the APK
asset URL. A cancel button and a "download now" link are always present. The
countdown runs only after an explicit press, so a visitor is never surprised by
an automatic file download.
