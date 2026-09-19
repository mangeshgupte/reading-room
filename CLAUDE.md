# Reading Room

Android app for the reading list. `README.md` says how it behaves, builds and releases;
`STATUS.md` is the dated log of what was built and what is still unverified.

## Git

All work on the app goes through git, as laid out in "Working in git" in `README.md`:
a feature starts on `feature/<slug>` off `main`, is committed as it goes, and merges back
with `--no-ff` once tests pass. Start the branch before the first edit, not after. Releases
are cut with `scripts/release.sh`, which commits and tags the version bump itself.

`origin` is `github.com/mangeshgupte/reading-room`, a public repo. Anything committed is
published on the next push: keep tokens, the keystore and its passwords out of every commit.
