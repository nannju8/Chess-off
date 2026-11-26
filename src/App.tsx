import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Code2,
  Flag,
  Lightbulb,
  PlusCircle,
  RotateCw,
  Settings,
  SkipBack,
  SkipForward,
  Undo2,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import {
  FideChessGame,
  PIECE_VALUES,
  STARTING_FEN,
  evaluatePositionCp,
  findLocalBestMove,
  findKingSquare,
  isKingInCheck,
  oppositeColor,
  serializeFen,
  squareFile,
  squareRank,
  toSquareIndex,
  type ChessMove,
  type ChessPosition,
  type GameStatus,
  type MoveRecord,
  type Piece,
  type PieceColor,
  type PieceType,
} from './chess/fideRules';
import { playChessSound } from './audio/webSoundEngine';
import { ChessPieceSvg, type PieceStyle } from './components/ChessPiecesSvg';
import { AndroidProjectExplorer } from './components/AndroidProjectExplorer';

type BoardTheme = 'tournament' | 'wood' | 'slate';
type GameMode = 'vs_ai' | 'two_players' | 'analysis';

interface TimeControlOption {
  id: string;
  label: string;
  initialMs: number;
  incrementMs: number;
}

const TIME_CONTROLS: TimeControlOption[] = [
  { id: 'unlimited', label: 'Sans chrono', initialMs: 0, incrementMs: 0 },
  { id: 'bullet_1_0', label: 'Bullet 1+0', initialMs: 60_000, incrementMs: 0 },
  { id: 'blitz_3_2', label: 'Blitz 3+2', initialMs: 180_000, incrementMs: 2_000 },
  { id: 'blitz_5_0', label: 'Blitz 5+0', initialMs: 300_000, incrementMs: 0 },
  { id: 'rapid_10_0', label: 'Rapide 10+0', initialMs: 600_000, incrementMs: 0 },
];

const THEMES: Record<
  BoardTheme,
  {
    name: string;
    light: string;
    dark: string;
    lastLight: string;
    lastDark: string;
    selected: string;
    coordLight: string;
    coordDark: string;
    border: string;
  }
> = {
  tournament: {
    name: 'Vert Tournoi',
    light: '#EEEED2',
    dark: '#769656',
    lastLight: '#F6F680',
    lastDark: '#BACA2B',
    selected: '#5A7F3B',
    coordLight: '#5F7D42',
    coordDark: '#EEEED2',
    border: '#3E552A',
  },
  wood: {
    name: 'Bois Classique',
    light: '#F0D9B5',
    dark: '#B58863',
    lastLight: '#F6F680',
    lastDark: '#BACB44',
    selected: '#829769',
    coordLight: '#9E704D',
    coordDark: '#F0D9B5',
    border: '#5D3A21',
  },
  slate: {
    name: 'Ardoise Sombre',
    light: '#94A3B8',
    dark: '#334155',
    lastLight: '#60A5FA',
    lastDark: '#2563EB',
    selected: '#0EA5E9',
    coordLight: '#1E293B',
    coordDark: '#CBD5E1',
    border: '#1E293B',
  },
};

const STORAGE_KEY = 'chess_pro_offline_preview_v1';

function formatClock(ms: number): string {
  const clamped = Math.max(0, ms);
  const totalSec = Math.floor(clamped / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (clamped > 0 && clamped < 20_000) {
    const tenths = Math.floor((clamped % 1000) / 100);
    return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}.${tenths}`;
  }
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

export default function App() {
  const gameRef = useRef<FideChessGame>(new FideChessGame());
  const boardRef = useRef<HTMLDivElement | null>(null);

  const [activeTab, setActiveTab] = useState<'play' | 'android'>('play');
  const [boardTheme, setBoardTheme] = useState<BoardTheme>('tournament');
  const [pieceStyle, setPieceStyle] = useState<PieceStyle>('staunton');
  const [isMuted, setIsMuted] = useState(false);
  const [autoFlip2P, setAutoFlip2P] = useState(true);
  const [showEvalBar, setShowEvalBar] = useState(true);
  const [showCoords, setShowCoords] = useState(true);

  const [gameMode, setGameMode] = useState<GameMode>('two_players');
  const [humanColor, setHumanColor] = useState<PieceColor>('w');
  const [aiLevel, setAiLevel] = useState(3);
  const [timeControl, setTimeControl] = useState<TimeControlOption>(TIME_CONTROLS[3]);
  const [whiteMs, setWhiteMs] = useState(TIME_CONTROLS[3].initialMs);
  const [blackMs, setBlackMs] = useState(TIME_CONTROLS[3].initialMs);
  const [isFlipped, setIsFlipped] = useState(false);

  const [position, setPosition] = useState<ChessPosition>(gameRef.current.current);
  const [status, setStatus] = useState<GameStatus>(gameRef.current.status());
  const [history, setHistory] = useState<MoveRecord[]>([]);
  const [viewingPly, setViewingPly] = useState(0);
  const [selectedSquare, setSelectedSquare] = useState<number | null>(null);
  const [hintMove, setHintMove] = useState<{ from: number; to: number } | null>(null);
  const [evalCp, setEvalCp] = useState(0);
  const [pendingPromo, setPendingPromo] = useState<{ from: number; to: number; color: PieceColor } | null>(null);

  const [dragState, setDragState] = useState<{
    from: number;
    piece: Piece;
    x: number;
    y: number;
    hoverSq: number | null;
  } | null>(null);

  const [showNewGameModal, setShowNewGameModal] = useState(false);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showFenPgnModal, setShowFenPgnModal] = useState(false);
  const [fenPgnTab, setFenPgnTab] = useState<'fen' | 'pgn'>('fen');
  const [fenInput, setFenInput] = useState(STARTING_FEN);
  const [pgnInput, setPgnInput] = useState('');
  const [modalError, setModalError] = useState<string | null>(null);
  const [showResignConfirm, setShowResignConfirm] = useState(false);

  const [draftMode, setDraftMode] = useState<GameMode>('two_players');
  const [draftColor, setDraftColor] = useState<'w' | 'b' | 'random'>('w');
  const [draftLevel, setDraftLevel] = useState(3);
  const [draftTcId, setDraftTcId] = useState('blitz_5_0');

  // Restauration automatique depuis localStorage
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw);
      if (saved.boardTheme && THEMES[saved.boardTheme as BoardTheme]) setBoardTheme(saved.boardTheme);
      if (saved.pieceStyle === 'staunton' || saved.pieceStyle === 'neo') setPieceStyle(saved.pieceStyle);
      if (typeof saved.isMuted === 'boolean') setIsMuted(saved.isMuted);
      if (typeof saved.autoFlip2P === 'boolean') setAutoFlip2P(saved.autoFlip2P);
      if (typeof saved.showEvalBar === 'boolean') setShowEvalBar(saved.showEvalBar);
      if (typeof saved.showCoords === 'boolean') setShowCoords(saved.showCoords);
      if (saved.gameMode) setGameMode(saved.gameMode);
      if (saved.humanColor === 'w' || saved.humanColor === 'b') setHumanColor(saved.humanColor);
      if (typeof saved.aiLevel === 'number') setAiLevel(saved.aiLevel);
      if (typeof saved.isFlipped === 'boolean') setIsFlipped(saved.isFlipped);
      if (saved.timeControlId) {
        const tc = TIME_CONTROLS.find((t) => t.id === saved.timeControlId);
        if (tc) setTimeControl(tc);
      }
      if (typeof saved.whiteMs === 'number') setWhiteMs(saved.whiteMs);
      if (typeof saved.blackMs === 'number') setBlackMs(saved.blackMs);

      if (saved.initialFen) {
        gameRef.current.reset(saved.initialFen);
        if (Array.isArray(saved.uciMoves)) {
          for (const uci of saved.uciMoves) {
            gameRef.current.playUci(uci);
          }
        }
        const g = gameRef.current;
        setPosition(g.current);
        setStatus(g.status());
        setHistory([...g.history]);
        setViewingPly(g.history.length);
        setEvalCp(evaluatePositionCp(g.current));
      }
    } catch {
      // Ignore les données corrompues éventuelles
    }
  }, []);

  const persistState = useCallback(
    (overrides?: Partial<{ whiteMs: number; blackMs: number; isFlipped: boolean; gameMode: GameMode }>) => {
      try {
        const g = gameRef.current;
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            boardTheme,
            pieceStyle,
            isMuted,
            autoFlip2P,
            showEvalBar,
            showCoords,
            gameMode: overrides?.gameMode ?? gameMode,
            humanColor,
            aiLevel,
            timeControlId: timeControl.id,
            whiteMs: overrides?.whiteMs ?? whiteMs,
            blackMs: overrides?.blackMs ?? blackMs,
            isFlipped: overrides?.isFlipped ?? isFlipped,
            initialFen: g.initialFen,
            uciMoves: g.history.map((h) => `${String.fromCharCode(97 + squareFile(h.move.from))}${squareRank(h.move.from) + 1}${String.fromCharCode(97 + squareFile(h.move.to))}${squareRank(h.move.to) + 1}${h.move.promotion ?? ''}`),
          })
        );
      } catch {
        // Quota localStorage éventuel
      }
    },
    [aiLevel, autoFlip2P, blackMs, boardTheme, gameMode, humanColor, isFlipped, isMuted, pieceStyle, showCoords, showEvalBar, timeControl.id, whiteMs]
  );

  useEffect(() => {
    persistState();
  }, [persistState]);

  const syncFromGame = useCallback(
    (nextWhiteMs: number, nextBlackMs: number, nextFlip: boolean) => {
      const g = gameRef.current;
      const cur = g.current;
      setPosition(cur);
      setStatus(g.status());
      setHistory([...g.history]);
      setViewingPly(g.history.length);
      setSelectedSquare(null);
      setHintMove(null);
      setEvalCp(evaluatePositionCp(cur));
      setWhiteMs(nextWhiteMs);
      setBlackMs(nextBlackMs);
      setIsFlipped(nextFlip);
      persistState({ whiteMs: nextWhiteMs, blackMs: nextBlackMs, isFlipped: nextFlip });
    },
    [persistState]
  );

  // Pendule temps réel
  useEffect(() => {
    if (timeControl.initialMs === 0 || status.kind !== 'ongoing' || gameMode === 'analysis' || history.length === 0) {
      return;
    }
    const id = window.setInterval(() => {
      const turn = gameRef.current.current.sideToMove;
      if (turn === 'w') {
        setWhiteMs((prev) => {
          if (prev <= 100) {
            gameRef.current.forcedStatus = { kind: 'timeout', winner: 'b' };
            setStatus(gameRef.current.status());
            playChessSound('end', isMuted);
            return 0;
          }
          return prev - 100;
        });
      } else {
        setBlackMs((prev) => {
          if (prev <= 100) {
            gameRef.current.forcedStatus = { kind: 'timeout', winner: 'w' };
            setStatus(gameRef.current.status());
            playChessSound('end', isMuted);
            return 0;
          }
          return prev - 100;
        });
      }
    }, 100);
    return () => window.clearInterval(id);
  }, [gameMode, history.length, isMuted, status.kind, timeControl.initialMs]);

  const executeMove = useCallback(
    (from: number, to: number, promo?: Exclude<PieceType, 'p' | 'k'>) => {
      const mover = gameRef.current.current.sideToMove;
      const rec = gameRef.current.playMove(from, to, promo);
      if (!rec) return;

      const nextStatus = gameRef.current.status();
      if (nextStatus.kind !== 'ongoing') playChessSound('end', isMuted);
      else if (rec.isCheckAfter) playChessSound('check', isMuted);
      else if (rec.move.isKingSideCastle || rec.move.isQueenSideCastle) playChessSound('castle', isMuted);
      else if (rec.move.isCapture) playChessSound('capture', isMuted);
      else playChessSound('move', isMuted);

      const addInc = timeControl.initialMs > 0 && gameRef.current.history.length > 1;
      const nW = addInc && mover === 'w' ? whiteMs + timeControl.incrementMs : whiteMs;
      const nB = addInc && mover === 'b' ? blackMs + timeControl.incrementMs : blackMs;
      const nFlip =
        gameMode === 'two_players' && autoFlip2P && nextStatus.kind === 'ongoing'
          ? gameRef.current.current.sideToMove === 'b'
          : isFlipped;

      syncFromGame(nW, nB, nFlip);
    },
    [autoFlip2P, blackMs, gameMode, isFlipped, isMuted, syncFromGame, timeControl.incrementMs, timeControl.initialMs, whiteMs]
  );

  // Coup IA local (aperçu navigateur) lorsque mode VS_AI actif
  useEffect(() => {
    if (
      gameMode !== 'vs_ai' ||
      status.kind !== 'ongoing' ||
      viewingPly !== history.length ||
      position.sideToMove === humanColor
    ) {
      return;
    }
    const timer = window.setTimeout(() => {
      const depth = aiLevel <= 4 ? 1 : aiLevel <= 7 ? 2 : 3;
      const best = findLocalBestMove(gameRef.current.current, depth);
      if (best) {
        executeMove(best.move.from, best.move.to, best.move.promotion);
      }
    }, 320);
    return () => window.clearTimeout(timer);
  }, [aiLevel, executeMove, gameMode, history.length, humanColor, position.sideToMove, status.kind, viewingPly]);

  const legalTargetsForSelected = useMemo(() => {
    if (selectedSquare === null || viewingPly !== history.length) return new Set<number>();
    return new Set(gameRef.current.legalMovesFrom(selectedSquare).map((m) => m.to));
  }, [history.length, selectedSquare, viewingPly]);

  const isViewingLatest = viewingPly === history.length;

  const handleSquareAction = (sq: number) => {
    if (!isViewingLatest) {
      jumpToPly(history.length);
      return;
    }
    if (status.kind !== 'ongoing') return;
    if (gameMode === 'vs_ai' && position.sideToMove !== humanColor) return;

    if (selectedSquare !== null && legalTargetsForSelected.has(sq)) {
      if (gameRef.current.needsPromotion(selectedSquare, sq)) {
        setPendingPromo({ from: selectedSquare, to: sq, color: position.sideToMove });
        setSelectedSquare(null);
      } else {
        executeMove(selectedSquare, sq);
      }
      return;
    }

    const piece = position.board[sq];
    if (piece && piece.color === position.sideToMove) {
      setSelectedSquare(selectedSquare === sq ? null : sq);
    } else {
      setSelectedSquare(null);
    }
  };

  const pointerToSquare = (clientX: number, clientY: number): number | null => {
    const el = boardRef.current;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    if (x < 0 || y < 0 || x >= rect.width || y >= rect.height) return null;
    const col = Math.floor((x / rect.width) * 8);
    const row = Math.floor((y / rect.height) * 8);
    const file = isFlipped ? 7 - col : col;
    const rank = isFlipped ? row : 7 - row;
    return toSquareIndex(file, rank);
  };

  const handlePointerDownBoard = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isViewingLatest || status.kind !== 'ongoing') return;
    if (gameMode === 'vs_ai' && position.sideToMove !== humanColor) return;
    const sq = pointerToSquare(e.clientX, e.clientY);
    if (sq === null) return;
    const piece = position.board[sq];
    if (!piece || piece.color !== position.sideToMove) {
      handleSquareAction(sq);
      return;
    }
    e.currentTarget.setPointerCapture(e.pointerId);
    const startX = e.clientX;
    const startY = e.clientY;
    let dragged = false;

    const onMove = (ev: PointerEvent) => {
      const dist = Math.hypot(ev.clientX - startX, ev.clientY - startY);
      if (!dragged && dist > 6) {
        dragged = true;
        setSelectedSquare(sq);
      }
      if (dragged) {
        setDragState({
          from: sq,
          piece,
          x: ev.clientX,
          y: ev.clientY,
          hoverSq: pointerToSquare(ev.clientX, ev.clientY),
        });
      }
    };

    const onUp = (ev: PointerEvent) => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      if (dragged) {
        const dropSq = pointerToSquare(ev.clientX, ev.clientY);
        setDragState(null);
        if (dropSq !== null && dropSq !== sq) {
          const legal = gameRef.current.legalMovesFrom(sq).some((m) => m.to === dropSq);
          if (legal) {
            if (gameRef.current.needsPromotion(sq, dropSq)) {
              setPendingPromo({ from: sq, to: dropSq, color: position.sideToMove });
              setSelectedSquare(null);
            } else {
              executeMove(sq, dropSq);
            }
          } else {
            setSelectedSquare(null);
          }
        }
      } else {
        handleSquareAction(sq);
      }
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const jumpToPly = (ply: number) => {
    const clamped = Math.max(0, Math.min(ply, history.length));
    const pos = gameRef.current.timeline[clamped];
    setViewingPly(clamped);
    setPosition(pos);
    setSelectedSquare(null);
    setHintMove(null);
    setEvalCp(evaluatePositionCp(pos));
  };

  const handleUndo = () => {
    if (history.length === 0) return;
    const steps = gameMode === 'vs_ai' && position.sideToMove === humanColor && history.length >= 2 ? 2 : 1;
    if (gameRef.current.undo(steps)) {
      syncFromGame(whiteMs, blackMs, isFlipped);
    }
  };

  const handleHint = () => {
    if (status.kind !== 'ongoing') return;
    const res = findLocalBestMove(position, 2);
    if (res) {
      setHintMove({ from: res.move.from, to: res.move.to });
      setEvalCp(res.evalCp);
    }
  };

  const handleStartNewGame = () => {
    const tc = TIME_CONTROLS.find((t) => t.id === draftTcId) ?? TIME_CONTROLS[3];
    const resolvedColor: PieceColor =
      draftColor === 'random' ? (Math.random() < 0.5 ? 'w' : 'b') : draftColor;

    gameRef.current.reset(STARTING_FEN);
    setGameMode(draftMode);
    setHumanColor(resolvedColor);
    setAiLevel(draftLevel);
    setTimeControl(tc);
    const nextFlip = draftMode === 'vs_ai' && resolvedColor === 'b';
    setShowNewGameModal(false);
    syncFromGame(tc.initialMs, tc.initialMs, nextFlip);
  };

  const palette = THEMES[boardTheme];
  const lastMove: ChessMove | null = viewingPly > 0 ? history[viewingPly - 1].move : null;
  const kingCheckSq = isKingInCheck(position, position.sideToMove)
    ? findKingSquare(position, position.sideToMove)
    : null;

  const { capByWhite, capByBlack, whiteAdv, blackAdv } = useMemo(() => {
    const cw: Piece[] = [];
    const cb: Piece[] = [];
    for (let i = 0; i < viewingPly; i++) {
      const c = history[i]?.captured;
      if (!c) continue;
      if (c.color === 'b') cw.push(c);
      else cb.push(c);
    }
    cw.sort((a, b) => PIECE_VALUES[b.type] - PIECE_VALUES[a.type]);
    cb.sort((a, b) => PIECE_VALUES[b.type] - PIECE_VALUES[a.type]);
    const wSum = cw.reduce((s, p) => s + PIECE_VALUES[p.type], 0);
    const bSum = cb.reduce((s, p) => s + PIECE_VALUES[p.type], 0);
    return {
      capByWhite: cw,
      capByBlack: cb,
      whiteAdv: Math.max(0, wSum - bSum),
      blackAdv: Math.max(0, bSum - wSum),
    };
  }, [history, viewingPly]);

  const topColor: PieceColor = isFlipped ? 'w' : 'b';
  const bottomColor: PieceColor = oppositeColor(topColor);

  const playerLabel = (c: PieceColor) => {
    if (gameMode === 'vs_ai') return c === humanColor ? 'Vous' : `IA Aperçu · Niv. ${aiLevel}`;
    if (gameMode === 'analysis') return c === 'w' ? 'Blancs (Analyse)' : 'Noirs (Analyse)';
    return c === 'w' ? 'Joueur 1 (Blancs)' : 'Joueur 2 (Noirs)';
  };

  const evalWhitePct = useMemo(() => {
    const t = Math.tanh(evalCp / 450);
    return Math.round((0.5 + 0.45 * t) * 100);
  }, [evalCp]);

  const statusText = useMemo(() => {
    if (status.kind === 'checkmate') {
      return `Échec et mat · Victoire des ${status.winner === 'w' ? 'Blancs' : 'Noirs'}`;
    }
    if (status.kind === 'stalemate') return 'Pat · Partie nulle';
    if (status.kind === 'draw') return `Partie nulle · ${status.reason}`;
    if (status.kind === 'resigned') {
      return `Abandon · Victoire des ${status.winner === 'w' ? 'Blancs' : 'Noirs'}`;
    }
    if (status.kind === 'timeout') {
      return `Temps écoulé · Victoire des ${status.winner === 'w' ? 'Blancs' : 'Noirs'}`;
    }
    return null;
  }, [status]);

  const renderBanner = (color: PieceColor) => {
    const activeTurn = status.kind === 'ongoing' && position.sideToMove === color;
    const caps = color === 'w' ? capByWhite : capByBlack;
    const adv = color === 'w' ? whiteAdv : blackAdv;
    const ms = color === 'w' ? whiteMs : blackMs;
    const hasClock = timeControl.initialMs > 0 && gameMode !== 'analysis';

    return (
      <div
        className={`flex items-center justify-between rounded-xl border px-3 py-2 transition-colors ${
          activeTurn ? 'border-[#81B64C] bg-[#282C34]' : 'border-slate-800 bg-[#1E2127]'
        }`}
      >
        <div className="flex min-w-0 items-center gap-2.5">
          <span
            className={`h-4 w-4 shrink-0 rounded border border-slate-500 ${
              color === 'w' ? 'bg-slate-100' : 'bg-[#181A1F]'
            }`}
          />
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-slate-100">{playerLabel(color)}</div>
            {(caps.length > 0 || adv > 0) && (
              <div className="flex items-center gap-0.5">
                {caps.slice(0, 10).map((p, idx) => (
                  <div key={idx} className="h-3.5 w-3.5">
                    <ChessPieceSvg piece={p} style={pieceStyle} />
                  </div>
                ))}
                {adv > 0 && (
                  <span className="ml-1 text-xs font-bold text-[#81B64C] tabular-nums">+{adv}</span>
                )}
              </div>
            )}
          </div>
        </div>
        {hasClock && (
          <div
            className={`rounded-lg px-3 py-1 font-mono text-sm font-bold tabular-nums ${
              ms > 0 && ms <= 20_000 && activeTurn
                ? 'bg-red-700 text-white'
                : activeTurn
                  ? 'bg-[#81B64C] text-[#0E1706]'
                  : 'bg-[#14161A] text-slate-400'
            }`}
          >
            {formatClock(ms)}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-[#14161A] text-slate-100">
      {/* Barre supérieure 3 zones */}
      <header className="border-b border-slate-800 bg-[#181B20]">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-2 px-3 py-2.5 sm:gap-4 sm:px-4 sm:py-3">
          <span className="text-sm font-bold tracking-tight text-slate-100 whitespace-nowrap shrink-0 sm:text-base">
            Échecs Pro
          </span>

          <nav className="flex items-center gap-1 rounded-lg bg-[#14161A] p-0.5 sm:p-1" aria-label="Navigation">
            <button
              type="button"
              onClick={() => setActiveTab('play')}
              className={`rounded-md px-2.5 py-1 text-xs font-semibold whitespace-nowrap shrink-0 transition-colors sm:px-3 sm:py-1.5 ${
                activeTab === 'play' ? 'bg-[#81B64C] text-[#0E1706]' : 'text-slate-300 hover:text-white'
              }`}
            >
              Échiquier
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('android')}
              className={`rounded-md px-2.5 py-1 text-xs font-semibold whitespace-nowrap shrink-0 transition-colors sm:px-3 sm:py-1.5 ${
                activeTab === 'android' ? 'bg-[#81B64C] text-[#0E1706]' : 'text-slate-300 hover:text-white'
              }`}
            >
              <span className="sm:hidden">Android</span>
              <span className="hidden sm:inline">Projet Android (/android)</span>
            </button>
          </nav>

          <div className="flex items-center gap-0.5 shrink-0 sm:gap-1">
            <button
              type="button"
              aria-label={isMuted ? 'Activer le son' : 'Couper le son'}
              onClick={() => setIsMuted((m) => !m)}
              className="rounded-lg p-1.5 text-slate-300 hover:bg-slate-800 hover:text-white sm:p-2"
            >
              {isMuted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
            </button>
            <button
              type="button"
              aria-label="Importer ou exporter FEN et PGN"
              onClick={() => {
                setFenInput(serializeFen(gameRef.current.current));
                setPgnInput(gameRef.current.exportPgn(playerLabel('w'), playerLabel('b')));
                setModalError(null);
                setShowFenPgnModal(true);
              }}
              className="rounded-lg p-1.5 text-slate-300 hover:bg-slate-800 hover:text-white sm:p-2"
            >
              <Code2 className="h-4 w-4" />
            </button>
            <button
              type="button"
              aria-label="Ouvrir les paramètres"
              onClick={() => setShowSettingsModal(true)}
              className="rounded-lg p-1.5 text-slate-300 hover:bg-slate-800 hover:text-white sm:p-2"
            >
              <Settings className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {activeTab === 'android' ? (
        <AndroidProjectExplorer />
      ) : (
        <main className="mx-auto flex max-w-[440px] flex-col gap-2.5 px-3 py-3">
          {statusText && (
            <div className="flex items-center justify-between rounded-xl border border-[#81B64C]/40 bg-[#81B64C]/15 px-3 py-2 text-xs font-semibold text-[#A3E635]">
              <span>{statusText}</span>
              <button
                type="button"
                onClick={() => setShowNewGameModal(true)}
                className="ml-2 rounded-md bg-[#81B64C] px-2.5 py-1 text-xs font-bold text-[#0E1706] whitespace-nowrap shrink-0"
              >
                Rejouer
              </button>
            </div>
          )}

          {showEvalBar && (
            <div className="flex items-center gap-2">
              <div className="h-2.5 flex-1 overflow-hidden rounded-full border border-slate-700 bg-[#1F242D]">
                <div
                  className="h-full bg-slate-100 transition-all duration-300"
                  style={{ width: `${evalWhitePct}%` }}
                />
              </div>
              <span className="w-12 text-right font-mono text-xs font-semibold text-slate-400 tabular-nums">
                {evalCp >= 0 ? `+${(evalCp / 100).toFixed(1)}` : (evalCp / 100).toFixed(1)}
              </span>
            </div>
          )}

          {renderBanner(topColor)}

          {/* Échiquier principal */}
          <div
            ref={boardRef}
            data-testid="chess-board"
            onPointerDown={handlePointerDownBoard}
            className="relative aspect-square w-full touch-none select-none overflow-hidden rounded-lg"
            style={{ border: `2px solid ${palette.border}` }}
          >
            <div className="grid h-full w-full grid-cols-8 grid-rows-8">
              {Array.from({ length: 64 }).map((_, cellIdx) => {
                const row = Math.floor(cellIdx / 8);
                const col = cellIdx % 8;
                const file = isFlipped ? 7 - col : col;
                const rank = isFlipped ? row : 7 - row;
                const sq = toSquareIndex(file, rank);
                const isLight = (file + rank) % 2 !== 0;
                const isLast = lastMove && (lastMove.from === sq || lastMove.to === sq);
                const isSelected = selectedSquare === sq;
                const isLegal = legalTargetsForSelected.has(sq);
                const piece = position.board[sq];
                const isCheckSq = kingCheckSq === sq;
                const isDragOrigin = dragState?.from === sq;

                let bg = isLight ? palette.light : palette.dark;
                if (isLast) bg = isLight ? palette.lastLight : palette.lastDark;
                if (isSelected) bg = palette.selected;

                return (
                  <button
                    key={sq}
                    type="button"
                    aria-label={`Case ${String.fromCharCode(97 + file)}${rank + 1}`}
                    onClick={() => {}}
                    className="relative flex items-center justify-center focus:outline-none"
                    style={{ backgroundColor: bg }}
                  >
                    {isCheckSq && (
                      <div
                        className="pointer-events-none absolute inset-0"
                        style={{
                          background: 'radial-gradient(circle, rgba(239,68,68,0.9) 0%, rgba(220,38,38,0.45) 60%, transparent 100%)',
                        }}
                      />
                    )}

                    {showCoords && col === 0 && (
                      <span
                        className="pointer-events-none absolute top-0.5 left-0.5 text-[10px] font-bold leading-none"
                        style={{ color: isLight ? palette.coordLight : palette.coordDark }}
                      >
                        {rank + 1}
                      </span>
                    )}
                    {showCoords && row === 7 && (
                      <span
                        className="pointer-events-none absolute right-0.5 bottom-0.5 text-[10px] font-bold leading-none"
                        style={{ color: isLight ? palette.coordLight : palette.coordDark }}
                      >
                        {String.fromCharCode(97 + file)}
                      </span>
                    )}

                    {piece && !isDragOrigin && (
                      <div className="h-[88%] w-[88%]">
                        <ChessPieceSvg piece={piece} style={pieceStyle} />
                      </div>
                    )}

                    {isLegal && (
                      <div
                        className={`pointer-events-none absolute rounded-full ${
                          piece
                            ? 'h-[86%] w-[86%] border-[3.5px] border-black/30'
                            : 'h-[28%] w-[28%] bg-black/25'
                        }`}
                      />
                    )}
                  </button>
                );
              })}
            </div>

            {/* Flèche d'indice */}
            {hintMove && (
              <svg viewBox="0 0 800 800" className="pointer-events-none absolute inset-0 h-full w-full">
                {(() => {
                  const pt = (sq: number) => {
                    const col = isFlipped ? 7 - squareFile(sq) : squareFile(sq);
                    const row = isFlipped ? squareRank(sq) : 7 - squareRank(sq);
                    return { x: (col + 0.5) * 100, y: (row + 0.5) * 100 };
                  };
                  const a = pt(hintMove.from);
                  const b = pt(hintMove.to);
                  const ang = Math.atan2(b.y - a.y, b.x - a.x);
                  const head = 34;
                  const sx = b.x - Math.cos(ang) * head * 0.7;
                  const sy = b.y - Math.sin(ang) * head * 0.7;
                  const p1x = b.x - head * Math.cos(ang - Math.PI / 6);
                  const p1y = b.y - head * Math.sin(ang - Math.PI / 6);
                  const p2x = b.x - head * Math.cos(ang + Math.PI / 6);
                  const p2y = b.y - head * Math.sin(ang + Math.PI / 6);
                  return (
                    <g opacity="0.88">
                      <line x1={a.x} y1={a.y} x2={sx} y2={sy} stroke="#F59E0B" strokeWidth="18" strokeLinecap="round" />
                      <polygon points={`${b.x},${b.y} ${p1x},${p1y} ${p2x},${p2y}`} fill="#F59E0B" />
                    </g>
                  );
                })()}
              </svg>
            )}

            {/* Pièce en cours de glissement */}
            {dragState && (
              <div
                className="pointer-events-none fixed z-50 h-14 w-14 -translate-x-1/2 -translate-y-1/2"
                style={{ left: dragState.x, top: dragState.y }}
              >
                <ChessPieceSvg piece={dragState.piece} style={pieceStyle} />
              </div>
            )}
          </div>

          {renderBanner(bottomColor)}

          {/* Historique SAN cliquable */}
          <div className="flex items-center gap-1 rounded-xl border border-slate-800 bg-[#1E2127] px-1.5 py-1">
            <button
              type="button"
              aria-label="Début de partie"
              disabled={viewingPly === 0}
              onClick={() => jumpToPly(0)}
              className="rounded p-1.5 text-slate-300 hover:bg-slate-800 disabled:opacity-30"
            >
              <SkipBack className="h-4 w-4" />
            </button>
            <button
              type="button"
              aria-label="Coup précédent"
              disabled={viewingPly === 0}
              onClick={() => jumpToPly(viewingPly - 1)}
              className="rounded p-1.5 text-slate-300 hover:bg-slate-800 disabled:opacity-30"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>

            <div className="flex flex-1 items-center gap-1 overflow-x-auto px-1">
              {history.length === 0 ? (
                <span className="w-full text-center text-xs text-slate-500">Aucun coup joué</span>
              ) : (
                history.map((rec, idx) => {
                  const ply = idx + 1;
                  const active = viewingPly === ply;
                  return (
                    <button
                      key={ply}
                      type="button"
                      onClick={() => jumpToPly(ply)}
                      className={`rounded px-2 py-1 font-mono text-xs whitespace-nowrap shrink-0 ${
                        active
                          ? 'bg-[#81B64C]/25 font-bold text-[#A3E635]'
                          : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
                      }`}
                    >
                      {idx % 2 === 0 ? `${Math.floor(idx / 2) + 1}. ` : ''}
                      {rec.san}
                    </button>
                  );
                })
              )}
            </div>

            <button
              type="button"
              aria-label="Coup suivant"
              disabled={viewingPly >= history.length}
              onClick={() => jumpToPly(viewingPly + 1)}
              className="rounded p-1.5 text-slate-300 hover:bg-slate-800 disabled:opacity-30"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
            <button
              type="button"
              aria-label="Position actuelle"
              disabled={viewingPly >= history.length}
              onClick={() => jumpToPly(history.length)}
              className="rounded p-1.5 text-slate-300 hover:bg-slate-800 disabled:opacity-30"
            >
              <SkipForward className="h-4 w-4" />
            </button>
          </div>

          {/* Dock d'actions tactile */}
          <div className="grid grid-cols-5 gap-1 rounded-2xl border border-slate-800 bg-[#1E2127] p-1.5">
            <button
              type="button"
              disabled={history.length === 0}
              onClick={handleUndo}
              className="flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-35"
            >
              <Undo2 className="h-4 w-4" />
              <span>Annuler</span>
            </button>
            <button
              type="button"
              disabled={status.kind !== 'ongoing'}
              onClick={handleHint}
              className="flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-35"
            >
              <Lightbulb className="h-4 w-4" />
              <span>Indice</span>
            </button>
            <button
              type="button"
              onClick={() => setIsFlipped((f) => !f)}
              className="flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium text-slate-200 hover:bg-slate-800"
            >
              <RotateCw className="h-4 w-4" />
              <span>Retourner</span>
            </button>
            <button
              type="button"
              onClick={() => setShowNewGameModal(true)}
              className="flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium text-slate-200 hover:bg-slate-800"
            >
              <PlusCircle className="h-4 w-4" />
              <span>Nouvelle</span>
            </button>
            <button
              type="button"
              disabled={status.kind !== 'ongoing' || gameMode === 'analysis'}
              onClick={() => setShowResignConfirm(true)}
              className="flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-35"
            >
              <Flag className="h-4 w-4" />
              <span>Abandon</span>
            </button>
          </div>
        </main>
      )}

      {/* Modale Promotion */}
      {pendingPromo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-xs rounded-2xl border border-slate-700 bg-[#1E2127] p-5">
            <h2 className="text-base font-bold text-slate-100">Promotion du pion</h2>
            <div className="mt-4 grid grid-cols-4 gap-2">
              {(['q', 'r', 'b', 'n'] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  aria-label={`Promouvoir en ${t}`}
                  onClick={() => {
                    const p = pendingPromo;
                    setPendingPromo(null);
                    executeMove(p.from, p.to, t);
                  }}
                  className="flex aspect-square items-center justify-center rounded-xl bg-slate-800 p-2 hover:bg-slate-700"
                >
                  <ChessPieceSvg piece={{ type: t, color: pendingPromo.color }} style={pieceStyle} />
                </button>
              ))}
            </div>
            <button
              type="button"
              onClick={() => setPendingPromo(null)}
              className="mt-4 w-full rounded-lg py-2 text-xs font-semibold text-slate-400 hover:bg-slate-800"
            >
              Annuler
            </button>
          </div>
        </div>
      )}

      {/* Modale Nouvelle Partie */}
      {showNewGameModal && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 sm:items-center sm:p-4">
          <div className="w-full max-w-md rounded-t-2xl border border-slate-800 bg-[#1E2127] p-5 sm:rounded-2xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-100">Nouvelle partie</h2>
              <button
                type="button"
                aria-label="Fermer"
                onClick={() => setShowNewGameModal(false)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              <div>
                <div className="mb-1.5 text-xs font-semibold text-slate-400">Mode de jeu</div>
                <div className="grid grid-cols-3 gap-2">
                  {(
                    [
                      ['two_players', '2 Joueurs'],
                      ['vs_ai', 'Contre IA'],
                      ['analysis', 'Analyse'],
                    ] as const
                  ).map(([id, label]) => (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setDraftMode(id)}
                      className={`rounded-lg border py-2 text-xs font-semibold ${
                        draftMode === id
                          ? 'border-[#81B64C] bg-[#81B64C]/20 text-[#A3E635]'
                          : 'border-slate-700 bg-slate-800/50 text-slate-300'
                      }`}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              {draftMode === 'vs_ai' && (
                <>
                  <div>
                    <div className="mb-1.5 text-xs font-semibold text-slate-400">Votre camp</div>
                    <div className="grid grid-cols-3 gap-2">
                      {(
                        [
                          ['w', 'Blancs'],
                          ['b', 'Noirs'],
                          ['random', 'Aléatoire'],
                        ] as const
                      ).map(([id, label]) => (
                        <button
                          key={id}
                          type="button"
                          onClick={() => setDraftColor(id)}
                          className={`rounded-lg border py-2 text-xs font-semibold ${
                            draftColor === id
                              ? 'border-[#81B64C] bg-[#81B64C]/20 text-[#A3E635]'
                              : 'border-slate-700 bg-slate-800/50 text-slate-300'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div>
                    <div className="mb-1 flex justify-between text-xs font-semibold">
                      <span className="text-slate-400">Niveau</span>
                      <span className="text-[#81B64C] tabular-nums">{draftLevel} / 10</span>
                    </div>
                    <input
                      type="range"
                      min={1}
                      max={10}
                      value={draftLevel}
                      onChange={(e) => setDraftLevel(Number(e.target.value))}
                      className="w-full accent-[#81B64C]"
                    />
                  </div>
                </>
              )}

              {draftMode !== 'analysis' && (
                <div>
                  <div className="mb-1.5 text-xs font-semibold text-slate-400">Cadence</div>
                  <div className="flex flex-wrap gap-2">
                    {TIME_CONTROLS.map((tc) => (
                      <button
                        key={tc.id}
                        type="button"
                        onClick={() => setDraftTcId(tc.id)}
                        className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${
                          draftTcId === tc.id
                            ? 'border-[#81B64C] bg-[#81B64C]/20 text-[#A3E635]'
                            : 'border-slate-700 bg-slate-800/50 text-slate-300'
                        }`}
                      >
                        {tc.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <button
                type="button"
                onClick={handleStartNewGame}
                className="w-full rounded-xl bg-[#81B64C] py-2.5 text-sm font-bold text-[#0E1706] hover:brightness-105"
              >
                Lancer la partie
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modale Paramètres */}
      {showSettingsModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-[#1E2127] p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-100">Paramètres</h2>
              <button
                type="button"
                aria-label="Fermer les paramètres"
                onClick={() => setShowSettingsModal(false)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              <div>
                <div className="mb-2 text-xs font-semibold text-slate-400">Thème de l’échiquier</div>
                <div className="grid grid-cols-3 gap-2">
                  {(Object.keys(THEMES) as BoardTheme[]).map((key) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => setBoardTheme(key)}
                      className={`flex flex-col items-center gap-1.5 rounded-xl border p-2.5 text-xs font-semibold ${
                        boardTheme === key ? 'border-[#81B64C] text-[#A3E635]' : 'border-slate-700 text-slate-300'
                      }`}
                    >
                      <div className="flex overflow-hidden rounded">
                        <span className="h-4 w-4" style={{ backgroundColor: THEMES[key].light }} />
                        <span className="h-4 w-4" style={{ backgroundColor: THEMES[key].dark }} />
                      </div>
                      <span>{THEMES[key].name}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <div className="mb-2 text-xs font-semibold text-slate-400">Jeu de pièces</div>
                <div className="grid grid-cols-2 gap-2">
                  {(
                    [
                      ['staunton', 'Staunton'],
                      ['neo', 'Néo Moderne'],
                    ] as const
                  ).map(([id, label]) => (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setPieceStyle(id)}
                      className={`rounded-lg border py-2 text-xs font-semibold ${
                        pieceStyle === id
                          ? 'border-[#81B64C] bg-[#81B64C]/20 text-[#A3E635]'
                          : 'border-slate-700 bg-slate-800/50 text-slate-300'
                      }`}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2 border-t border-slate-800 pt-3 text-sm">
                <label className="flex items-center justify-between">
                  <span className="text-slate-300">Retourner automatiquement (2 joueurs)</span>
                  <input
                    type="checkbox"
                    checked={autoFlip2P}
                    onChange={(e) => setAutoFlip2P(e.target.checked)}
                    className="h-4 w-4 accent-[#81B64C]"
                  />
                </label>
                <label className="flex items-center justify-between">
                  <span className="text-slate-300">Afficher la barre d’évaluation</span>
                  <input
                    type="checkbox"
                    checked={showEvalBar}
                    onChange={(e) => setShowEvalBar(e.target.checked)}
                    className="h-4 w-4 accent-[#81B64C]"
                  />
                </label>
                <label className="flex items-center justify-between">
                  <span className="text-slate-300">Afficher les coordonnées</span>
                  <input
                    type="checkbox"
                    checked={showCoords}
                    onChange={(e) => setShowCoords(e.target.checked)}
                    className="h-4 w-4 accent-[#81B64C]"
                  />
                </label>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modale FEN / PGN */}
      {showFenPgnModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-[#1E2127] p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-100">FEN & PGN Hors Ligne</h2>
              <button
                type="button"
                aria-label="Fermer FEN/PGN"
                onClick={() => setShowFenPgnModal(false)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-3 grid grid-cols-2 gap-2 rounded-lg bg-[#14161A] p-1">
              <button
                type="button"
                onClick={() => {
                  setFenPgnTab('fen');
                  setModalError(null);
                }}
                className={`rounded-md py-1.5 text-xs font-semibold ${
                  fenPgnTab === 'fen' ? 'bg-slate-700 text-white' : 'text-slate-400'
                }`}
              >
                Position FEN
              </button>
              <button
                type="button"
                onClick={() => {
                  setFenPgnTab('pgn');
                  setModalError(null);
                }}
                className={`rounded-md py-1.5 text-xs font-semibold ${
                  fenPgnTab === 'pgn' ? 'bg-slate-700 text-white' : 'text-slate-400'
                }`}
              >
                Partie PGN
              </button>
            </div>

            {fenPgnTab === 'fen' ? (
              <div className="mt-3 space-y-3">
                <textarea
                  aria-label="Chaîne FEN"
                  rows={3}
                  value={fenInput}
                  onChange={(e) => setFenInput(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-[#14161A] p-2.5 font-mono text-xs text-slate-100"
                />
                {modalError && <p className="text-xs text-red-400">{modalError}</p>}
                <button
                  type="button"
                  onClick={() => {
                    try {
                      gameRef.current.reset(fenInput);
                      setShowFenPgnModal(false);
                      syncFromGame(timeControl.initialMs, timeControl.initialMs, isFlipped);
                    } catch (err) {
                      setModalError(err instanceof Error ? err.message : 'FEN invalide');
                    }
                  }}
                  className="w-full rounded-xl bg-[#81B64C] py-2 text-xs font-bold text-[#0E1706]"
                >
                  Charger la position FEN
                </button>
              </div>
            ) : (
              <div className="mt-3 space-y-3">
                <textarea
                  aria-label="Contenu PGN"
                  rows={6}
                  value={pgnInput}
                  onChange={(e) => setPgnInput(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-[#14161A] p-2.5 font-mono text-xs text-slate-100"
                />
                {modalError && <p className="text-xs text-red-400">{modalError}</p>}
                <button
                  type="button"
                  onClick={() => {
                    try {
                      gameRef.current.loadPgn(pgnInput);
                      setShowFenPgnModal(false);
                      syncFromGame(timeControl.initialMs, timeControl.initialMs, isFlipped);
                    } catch (err) {
                      setModalError(err instanceof Error ? err.message : 'PGN invalide');
                    }
                  }}
                  className="w-full rounded-xl bg-[#81B64C] py-2 text-xs font-bold text-[#0E1706]"
                >
                  Importer le PGN
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Confirmation Abandon */}
      {showResignConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-xs rounded-2xl border border-slate-800 bg-[#1E2127] p-5">
            <h2 className="text-base font-bold text-slate-100">Abandonner la partie ?</h2>
            <p className="mt-1 text-xs text-slate-400">
              La partie sera immédiatement enregistrée comme perdue par abandon.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowResignConfirm(false)}
                className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-300 hover:bg-slate-800"
              >
                Continuer
              </button>
              <button
                type="button"
                onClick={() => {
                  const losing = gameMode === 'vs_ai' ? humanColor : gameRef.current.current.sideToMove;
                  gameRef.current.forcedStatus = { kind: 'resigned', winner: oppositeColor(losing) };
                  playChessSound('end', isMuted);
                  setShowResignConfirm(false);
                  syncFromGame(whiteMs, blackMs, isFlipped);
                }}
                className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-bold text-white"
              >
                Abandonner
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
