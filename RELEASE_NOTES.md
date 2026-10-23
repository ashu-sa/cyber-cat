# Focanopy 1.12.0 · A grove that grows together

- Replaced the abrupt completed-tree reset with a continuous 2.4-second handoff. The full tree eases back into the grove while the next seedling fades into the foreground.
- Overlapping canopies use graduated scale and opacity for depth. Older trees settle behind newer trees; visible layers stay bounded as the total grows.
- One stable soil island anchors the scene. Water now lands above the active seedling, and the large count label is removed from the artwork; session metrics keep the exact totals.
- The fast-forward preview holds the handoff at real speed before accelerating growth, so the transition is easy to inspect.
- The same native choreography runs on Android, macOS, Windows and Linux. Pausing freezes elapsed-time motion; disabled animations and reduced-motion settings show the settled layout.
- Android includes the polished focus cards, stable navigation and compact-screen controls from 1.11.

Validation: native macOS timing/layout tests, Android unit tests/lint and isolated Pomodoro flow, desktop model/widget tests, and all-platform release builds.
