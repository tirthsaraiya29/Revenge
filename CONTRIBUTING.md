# Contributing to Revenge

Thanks for taking the time to improve Revenge.

## Before you start

- Search existing issues before opening a new one.
- Keep pull requests focused on a single change.
- Do not include secrets, signing keys, or local machine configuration.

## Development

1. Install JDK 21 and Android SDK Platform 37.
2. Create `local.properties` with `sdk.dir` pointing at your SDK.
3. Build and test:

   ```bash
   ./gradlew :app:assembleUniversalFossDebug
   ./gradlew test
   ```

4. Format the code before committing:

   ```bash
   ./gradlew spotlessApply
   ```

The repository uses Spotless with ktfmt (Google style). A pre-commit hook is available under `.githooks/`; enable it with:

```bash
git config core.hooksPath .githooks
```

## Code style

- Follow the existing Kotlin and Compose conventions.
- Prefer small, typed functions over large blocks.
- Keep user-visible strings in `res/values/strings.xml` and translate them where possible.
- Add tests for behavior changes in `src/test`.

## Reporting bugs

Include the app version, Android version, device model, reproduction steps, and relevant log output. Remove personal data from logs before posting.
