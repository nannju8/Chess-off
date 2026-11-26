import React from 'react';
import { FolderTree, Cpu, ShieldCheck, Smartphone, Terminal } from 'lucide-react';

const MODULES = [
  {
    name: ':core-chess',
    subtitle: 'Kotlin pur · Règles FIDE strictes',
    files: [
      'ChessModels.kt — Square, Piece, ChessMove, GameStatus, MoveRecord',
      'ClockModels.kt — Cadences Bullet 1+0, Blitz 3+2 / 5+0, Rapide 10+0',
      'MoveGenerator.kt — Coups légaux, roques, en passant, clouages, échecs',
      'DrawDetector.kt — Triple répétition, 50 coups, matériel insuffisant',
      'FenCodec.kt & PgnCodec.kt — Import/export complet FEN & PGN',
      'PerftAndRulesTest.kt — Tests unitaires Perft & règles FIDE',
    ],
  },
  {
    name: ':engine-oex',
    subtitle: 'Open Exchange (Stockfish 19) · UCI',
    files: [
      'AndroidManifest.xml — Balise <queries> pour intent.chess.provider.ENGINE',
      'OexDiscovery.kt — Résolution prioritaire depuis nativeLibraryDir (W^X Android 10–15+)',
      'UciProcessClient.kt — ProcessBuilder + stdin/stdout sur Dispatchers.IO',
      'UciParser.kt — Parseur score cp/mate, multipv, depth, pv & bestmove',
      'DifficultyEngine.kt — 10 niveaux (~400 à Max) avec depth 1-2 + MultiPV pour débutants',
      'StockfishCoordinator.kt — Coups IA, indices Meilleur coup & barre d’évaluation',
    ],
  },
  {
    name: ':app',
    subtitle: 'Jetpack Compose + Material 3',
    files: [
      'ChessBoardView.kt — Drag & drop + clic-puis-clic, flèche indice, roi en échec',
      'ChessViewModel.kt — Pendules coroutine, historique, livre d’ouvertures & Coach',
      'openings/OpeningBookRepository.kt — Indexation O(1) de openings.json & transpositions',
      'coach/CoachAnalyzer.kt & CoachCommentaryPicker.kt — 20 catégories, tons & anti-spam',
      'OpeningBadge.kt & CoachBubble.kt — Badge ECO + bulle Coach avec « Voir le meilleur coup »',
      'LocalSoundPlayer.kt — SoundPool faible latence sur 5 fichiers PCM .wav dans res/raw/',
      'res/drawable/ic_*.xml — 24 VectorDrawables (Staunton & Néo) + icône adaptative',
      'Modales — PromotionDialog, NewGameSheet, FenPgnDialog, SettingsDialog, NoOexEngineDialog',
      '.github/workflows/build-apk.yml — Compilation automatique de l’APK Debug',
    ],
  },
];

export const AndroidProjectExplorer: React.FC = () => {
  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-6">
      <div className="rounded-xl border border-slate-800 bg-[#1E2127] p-5">
        <div className="flex items-start gap-3">
          <Smartphone className="mt-0.5 h-5 w-5 shrink-0 text-[#81B64C]" />
          <div>
            <h2 className="text-lg font-bold text-slate-100">
              Projet Android Natif généré dans <code className="text-[#81B64C]">/android</code>
            </h2>
            <p className="mt-1 text-sm text-slate-400">
              Ouvrez le dossier <code className="text-slate-200">/android</code> dans Android Studio (JDK 17) ou exécutez le workflow GitHub Actions fourni pour générer l’APK sans aucune connexion Internet requise à l’exécution.
            </p>
          </div>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="rounded-lg border border-slate-800 bg-[#14161A] p-3.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
              <ShieldCheck className="h-4 w-4 text-[#81B64C]" />
              Conformité W^X Android 10–15+
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-slate-400">
              <code className="text-slate-300">OexDiscovery.kt</code> privilégie l’exécution directe du binaire <code className="text-slate-300">.so</code> dans le <code className="text-slate-300">nativeLibraryDir</code> de l’APK Stockfish 19 avant toute copie de secours.
            </p>
          </div>
          <div className="rounded-lg border border-slate-800 bg-[#14161A] p-3.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
              <Cpu className="h-4 w-4 text-sky-400" />
              Calibrage 400–1000 Elo (Stockfish 19)
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-slate-400">
              Les niveaux 1 à 4 limitent la recherche à <code className="text-slate-300">depth 1..2</code> avec <code className="text-slate-300">MultiPV 3..5</code> et sélection pondérée pour reproduire un jeu humain accessible.
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {MODULES.map((mod) => (
          <div key={mod.name} className="rounded-xl border border-slate-800 bg-[#1E2127] p-4">
            <div className="flex items-center gap-2">
              <FolderTree className="h-4 w-4 text-[#81B64C]" />
              <h3 className="font-mono text-sm font-bold text-slate-100">{mod.name}</h3>
            </div>
            <p className="mt-0.5 text-xs text-slate-400">{mod.subtitle}</p>
            <ul className="mt-3 space-y-2 border-t border-slate-800 pt-3 text-xs text-slate-300">
              {mod.files.map((file) => (
                <li key={file} className="leading-snug">
                  {file}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="rounded-xl border border-slate-800 bg-[#1E2127] p-4">
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
          <Terminal className="h-4 w-4 text-[#81B64C]" />
          Commandes Gradle (depuis le dossier /android)
        </div>
        <pre className="mt-2 overflow-x-auto rounded-lg bg-[#14161A] p-3 font-mono text-xs text-slate-300">
{`gradle :core-chess:test      # Exécuter les tests FIDE (Perft, roques, en passant, nulles)
gradle :app:assembleDebug    # Compiler l'APK Debug installable sur Android`}
        </pre>
      </div>
    </div>
  );
};
