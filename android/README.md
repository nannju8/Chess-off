# Échecs Pro — Application Android Native (Kotlin + Jetpack Compose + OEX Stockfish 19)

Application d'échecs complète, fluide et **100 % hors ligne**, conçue pour Android Studio et connectée au moteur **Stockfish 19** installé sur le téléphone via le standard **Open Exchange (OEX)**.

## Architecture multi-modules

- `:core-chess` — Moteur de règles FIDE strictes en Kotlin pur (roques, prise en passant, clouages, promotion, triple répétition, 50 coups, matériel insuffisant, codecs FEN et PGN, tests unitaires Perft).
- `:engine-oex` — Découverte OEX (`intent.chess.provider.ENGINE` avec `<queries>` Android 11+), résolution du binaire dans `nativeLibraryDir` (conforme W^X Android 10–15+), dialogue UCI sur `Dispatchers.IO`, et 10 niveaux progressifs (~400 Elo via `depth 1..2` + `MultiPV` jusqu'au niveau Max).
- `:app` — Interface mobile-first Jetpack Compose + Material 3, double saisie (Drag & Drop + Clic-puis-clic), pendules intégrées, barre d'évaluation, flèche d'indice, 3 thèmes d'échiquier, 2 jeux de pièces VectorDrawable (`Staunton` & `Néo`), 5 sons locaux PCM WAV via `SoundPool`, sauvegarde locale automatique.

## Compilation

1. Ouvrez le dossier `android/` dans **Android Studio** (JDK 17 requis).
2. Exécutez les tests FIDE : `./gradlew :core-chess:test` (ou `gradle :core-chess:test`).
3. Compilez l'APK Debug : `./gradlew :app:assembleDebug` (ou utilisez `.github/workflows/build-apk.yml`).
