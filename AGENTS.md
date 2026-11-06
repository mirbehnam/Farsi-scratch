# Android client agent guide

Before changing editor assets, Gradle variants or engine selection, read docs/EDITOR_ASSETS.md and the sibling Scratch Auto Builder documentation at C:/Users/olobu_nanowoh/OneDrive/Desktop/main/scratch/auto-builder/README.md. If paths, flavor, Gradle task, output names or shared-media contracts change, update the Auto Builder implementation, safety checks and documentation. Keep its backup and staging directories outside assets; preserve engine bridges and saved HTML player isolation.

Phone imports from native Home must persist in ProjectRepository and return to
My Projects. Never auto-open an editor on file selection: require the user to
click the card and choose an engine. Preserve the original phone file.

Release editor WebViews through AndroidView.onRelease, detached before destroy.
Clear editor session references and pending chooser/load/input state on exit.
Never use WebView.pauseTimers() for one editor: it pauses all WebViews.

Downloaded backdrops and sounds share the persistent downloaded library contract; see docs/DOWNLOADED_SPRITES.md. Preserve all three host folders when replacing editor assets.
