export type PieceColor = 'w' | 'b';
export type PieceType = 'p' | 'n' | 'b' | 'r' | 'q' | 'k';

export interface Piece {
  type: PieceType;
  color: PieceColor;
}

export interface CastlingRights {
  wK: boolean;
  wQ: boolean;
  bK: boolean;
  bQ: boolean;
}

export interface ChessPosition {
  board: (Piece | null)[];
  sideToMove: PieceColor;
  castling: CastlingRights;
  enPassantTarget: number | null;
  halfmoveClock: number;
  fullmoveNumber: number;
}

export interface ChessMove {
  from: number;
  to: number;
  promotion?: Exclude<PieceType, 'p' | 'k'>;
  isCapture: boolean;
  isEnPassant: boolean;
  isKingSideCastle: boolean;
  isQueenSideCastle: boolean;
}

export type DrawReason =
  | 'Triple répétition'
  | 'Règle des 50 coups'
  | 'Matériel insuffisant';

export type GameStatus =
  | { kind: 'ongoing'; isCheck: boolean }
  | { kind: 'checkmate'; winner: PieceColor }
  | { kind: 'stalemate' }
  | { kind: 'draw'; reason: DrawReason }
  | { kind: 'resigned'; winner: PieceColor }
  | { kind: 'timeout'; winner: PieceColor };

export interface MoveRecord {
  ply: number;
  move: ChessMove;
  san: string;
  fenAfter: string;
  captured: Piece | null;
  isCheckAfter: boolean;
  isCheckmateAfter: boolean;
}

export const STARTING_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

export const PIECE_VALUES: Record<PieceType, number> = {
  p: 1,
  n: 3,
  b: 3,
  r: 5,
  q: 9,
  k: 0,
};

export function oppositeColor(c: PieceColor): PieceColor {
  return c === 'w' ? 'b' : 'w';
}

export function squareFile(idx: number): number {
  return idx % 8;
}

export function squareRank(idx: number): number {
  return Math.floor(idx / 8);
}

export function toSquareIndex(file: number, rank: number): number {
  return rank * 8 + file;
}

export function indexToAlgebraic(idx: number): string {
  return `${String.fromCharCode(97 + squareFile(idx))}${squareRank(idx) + 1}`;
}

export function algebraicToIndex(coord: string): number | null {
  if (coord.length !== 2) return null;
  const f = coord.charCodeAt(0) - 97;
  const r = coord.charCodeAt(1) - 49;
  if (f < 0 || f > 7 || r < 0 || r > 7) return null;
  return toSquareIndex(f, r);
}

export function moveToUci(m: ChessMove): string {
  return `${indexToAlgebraic(m.from)}${indexToAlgebraic(m.to)}${m.promotion ?? ''}`;
}

export function parseFen(fen: string): ChessPosition {
  const parts = fen.trim().split(/\s+/);
  if (parts.length < 4 || parts.length > 6) {
    throw new Error('FEN invalide : 4 à 6 champs requis.');
  }
  const rows = parts[0].split('/');
  if (rows.length !== 8) {
    throw new Error('FEN invalide : 8 rangées requises.');
  }
  const board: (Piece | null)[] = new Array(64).fill(null);
  let wKings = 0;
  let bKings = 0;

  for (let rIdx = 0; rIdx < 8; rIdx++) {
    const rank = 7 - rIdx;
    let file = 0;
    for (const ch of rows[rIdx]) {
      if (ch >= '1' && ch <= '8') {
        file += Number(ch);
      } else {
        if (file > 7) throw new Error('FEN invalide : débordement de colonne.');
        const lower = ch.toLowerCase() as PieceType;
        if (!['p', 'n', 'b', 'r', 'q', 'k'].includes(lower)) {
          throw new Error(`Pièce FEN inconnue : ${ch}`);
        }
        const color: PieceColor = ch === ch.toUpperCase() ? 'w' : 'b';
        if (lower === 'k') {
          if (color === 'w') wKings++;
          else bKings++;
        }
        board[toSquareIndex(file, rank)] = { type: lower, color };
        file++;
      }
    }
    if (file !== 8) throw new Error('FEN invalide : largeur de rangée incorrecte.');
  }

  if (wKings !== 1 || bKings !== 1) {
    throw new Error('FEN invalide : chaque camp doit avoir exactement un roi.');
  }

  const sideToMove = parts[1] as PieceColor;
  if (sideToMove !== 'w' && sideToMove !== 'b') {
    throw new Error('Trait FEN invalide.');
  }

  const castStr = parts[2];
  const castling: CastlingRights = {
    wK: castStr.includes('K'),
    wQ: castStr.includes('Q'),
    bK: castStr.includes('k'),
    bQ: castStr.includes('q'),
  };

  const enPassantTarget = parts[3] === '-' ? null : algebraicToIndex(parts[3]);
  if (parts[3] !== '-' && enPassantTarget === null) {
    throw new Error('Case en passant FEN invalide.');
  }

  const halfmoveClock = parts[4] ? Math.max(0, Number(parts[4]) || 0) : 0;
  const fullmoveNumber = parts[5] ? Math.max(1, Number(parts[5]) || 1) : 1;

  return { board, sideToMove, castling, enPassantTarget, halfmoveClock, fullmoveNumber };
}

export function serializeFen(pos: ChessPosition): string {
  const rows: string[] = [];
  for (let rank = 7; rank >= 0; rank--) {
    let empty = 0;
    let row = '';
    for (let file = 0; file < 8; file++) {
      const p = pos.board[toSquareIndex(file, rank)];
      if (!p) {
        empty++;
      } else {
        if (empty > 0) {
          row += empty;
          empty = 0;
        }
        row += p.color === 'w' ? p.type.toUpperCase() : p.type;
      }
    }
    if (empty > 0) row += empty;
    rows.push(row);
  }
  const cast =
    `${pos.castling.wK ? 'K' : ''}${pos.castling.wQ ? 'Q' : ''}${pos.castling.bK ? 'k' : ''}${pos.castling.bQ ? 'q' : ''}` ||
    '-';
  const ep = pos.enPassantTarget !== null ? indexToAlgebraic(pos.enPassantTarget) : '-';
  return `${rows.join('/')} ${pos.sideToMove} ${cast} ${ep} ${pos.halfmoveClock} ${pos.fullmoveNumber}`;
}

const KNIGHT_OFFSETS = [
  [1, 2], [2, 1], [2, -1], [1, -2],
  [-1, -2], [-2, -1], [-2, 1], [-1, 2],
];
const KING_OFFSETS = [
  [-1, -1], [0, -1], [1, -1],
  [-1, 0], [1, 0],
  [-1, 1], [0, 1], [1, 1],
];
const BISHOP_RAYS = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
const ROOK_RAYS = [[1, 0], [-1, 0], [0, 1], [0, -1]];

export function findKingSquare(pos: ChessPosition, color: PieceColor): number | null {
  for (let i = 0; i < 64; i++) {
    const p = pos.board[i];
    if (p && p.color === color && p.type === 'k') return i;
  }
  return null;
}

export function isSquareAttacked(pos: ChessPosition, target: number, byColor: PieceColor): boolean {
  const tf = squareFile(target);
  const tr = squareRank(target);

  const pawnRank = byColor === 'w' ? tr - 1 : tr + 1;
  if (pawnRank >= 0 && pawnRank <= 7) {
    for (const df of [-1, 1]) {
      const pf = tf + df;
      if (pf >= 0 && pf <= 7) {
        const p = pos.board[toSquareIndex(pf, pawnRank)];
        if (p && p.color === byColor && p.type === 'p') return true;
      }
    }
  }

  for (const [df, dr] of KNIGHT_OFFSETS) {
    const nf = tf + df;
    const nr = tr + dr;
    if (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
      const p = pos.board[toSquareIndex(nf, nr)];
      if (p && p.color === byColor && p.type === 'n') return true;
    }
  }

  for (const [df, dr] of KING_OFFSETS) {
    const kf = tf + df;
    const kr = tr + dr;
    if (kf >= 0 && kf <= 7 && kr >= 0 && kr <= 7) {
      const p = pos.board[toSquareIndex(kf, kr)];
      if (p && p.color === byColor && p.type === 'k') return true;
    }
  }

  for (const [df, dr] of BISHOP_RAYS) {
    let f = tf + df;
    let r = tr + dr;
    while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
      const p = pos.board[toSquareIndex(f, r)];
      if (p) {
        if (p.color === byColor && (p.type === 'b' || p.type === 'q')) return true;
        break;
      }
      f += df;
      r += dr;
    }
  }

  for (const [df, dr] of ROOK_RAYS) {
    let f = tf + df;
    let r = tr + dr;
    while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
      const p = pos.board[toSquareIndex(f, r)];
      if (p) {
        if (p.color === byColor && (p.type === 'r' || p.type === 'q')) return true;
        break;
      }
      f += df;
      r += dr;
    }
  }

  return false;
}

export function isKingInCheck(pos: ChessPosition, color: PieceColor): boolean {
  const k = findKingSquare(pos, color);
  return k !== null && isSquareAttacked(pos, k, oppositeColor(color));
}

export function applyMoveUnchecked(pos: ChessPosition, move: ChessMove): ChessPosition {
  const board = pos.board.slice();
  const moving = board[move.from];
  if (!moving) throw new Error('Aucune pièce au départ');
  const target = board[move.to];

  board[move.from] = null;
  board[move.to] = move.promotion ? { type: move.promotion, color: moving.color } : moving;

  if (move.isEnPassant) {
    board[toSquareIndex(squareFile(move.to), squareRank(move.from))] = null;
  }

  if (move.isKingSideCastle) {
    const r = squareRank(move.from);
    board[toSquareIndex(5, r)] = board[toSquareIndex(7, r)];
    board[toSquareIndex(7, r)] = null;
  } else if (move.isQueenSideCastle) {
    const r = squareRank(move.from);
    board[toSquareIndex(3, r)] = board[toSquareIndex(0, r)];
    board[toSquareIndex(0, r)] = null;
  }

  const castling = { ...pos.castling };
  if (moving.type === 'k') {
    if (moving.color === 'w') {
      castling.wK = false;
      castling.wQ = false;
    } else {
      castling.bK = false;
      castling.bQ = false;
    }
  }
  for (const sq of [move.from, move.to]) {
    if (sq === 0) castling.wQ = false;
    if (sq === 7) castling.wK = false;
    if (sq === 56) castling.bQ = false;
    if (sq === 63) castling.bK = false;
  }

  let enPassantTarget: number | null = null;
  if (moving.type === 'p' && Math.abs(squareRank(move.to) - squareRank(move.from)) === 2) {
    enPassantTarget = toSquareIndex(squareFile(move.from), (squareRank(move.from) + squareRank(move.to)) / 2);
  }

  const resetHalf = moving.type === 'p' || target !== null || move.isEnPassant;
  return {
    board,
    sideToMove: oppositeColor(pos.sideToMove),
    castling,
    enPassantTarget,
    halfmoveClock: resetHalf ? 0 : pos.halfmoveClock + 1,
    fullmoveNumber: pos.sideToMove === 'b' ? pos.fullmoveNumber + 1 : pos.fullmoveNumber,
  };
}

export function generateLegalMoves(pos: ChessPosition): ChessMove[] {
  const pseudo: ChessMove[] = [];
  const color = pos.sideToMove;
  const opp = oppositeColor(color);

  for (let idx = 0; idx < 64; idx++) {
    const p = pos.board[idx];
    if (!p || p.color !== color) continue;
    const ff = squareFile(idx);
    const fr = squareRank(idx);

    if (p.type === 'p') {
      const dir = color === 'w' ? 1 : -1;
      const startRank = color === 'w' ? 1 : 6;
      const promoRank = color === 'w' ? 7 : 0;
      const r1 = fr + dir;
      if (r1 >= 0 && r1 <= 7) {
        const sq1 = toSquareIndex(ff, r1);
        if (!pos.board[sq1]) {
          if (r1 === promoRank) {
            for (const pr of ['q', 'r', 'b', 'n'] as const) {
              pseudo.push({ from: idx, to: sq1, promotion: pr, isCapture: false, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
            }
          } else {
            pseudo.push({ from: idx, to: sq1, isCapture: false, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
            if (fr === startRank) {
              const sq2 = toSquareIndex(ff, fr + 2 * dir);
              if (!pos.board[sq2]) {
                pseudo.push({ from: idx, to: sq2, isCapture: false, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
              }
            }
          }
        }
        for (const df of [-1, 1]) {
          const tf = ff + df;
          if (tf < 0 || tf > 7) continue;
          const capSq = toSquareIndex(tf, r1);
          const capPiece = pos.board[capSq];
          if (capPiece && capPiece.color === opp) {
            if (r1 === promoRank) {
              for (const pr of ['q', 'r', 'b', 'n'] as const) {
                pseudo.push({ from: idx, to: capSq, promotion: pr, isCapture: true, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
              }
            } else {
              pseudo.push({ from: idx, to: capSq, isCapture: true, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
            }
          } else if (pos.enPassantTarget === capSq) {
            const adj = pos.board[toSquareIndex(tf, fr)];
            if (adj && adj.color === opp && adj.type === 'p') {
              pseudo.push({ from: idx, to: capSq, isCapture: true, isEnPassant: true, isKingSideCastle: false, isQueenSideCastle: false });
            }
          }
        }
      }
    } else if (p.type === 'n' || p.type === 'k') {
      const offsets = p.type === 'n' ? KNIGHT_OFFSETS : KING_OFFSETS;
      for (const [df, dr] of offsets) {
        const tf = ff + df;
        const tr = fr + dr;
        if (tf < 0 || tf > 7 || tr < 0 || tr > 7) continue;
        const to = toSquareIndex(tf, tr);
        const t = pos.board[to];
        if (!t || t.color === opp) {
          pseudo.push({ from: idx, to, isCapture: !!t, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
        }
      }
      if (p.type === 'k') {
        const expRank = color === 'w' ? 0 : 7;
        if (ff === 4 && fr === expRank && !isSquareAttacked(pos, idx, opp)) {
          const canK = color === 'w' ? pos.castling.wK : pos.castling.bK;
          const canQ = color === 'w' ? pos.castling.wQ : pos.castling.bQ;
          if (canK) {
            const fSq = toSquareIndex(5, expRank);
            const gSq = toSquareIndex(6, expRank);
            const hSq = toSquareIndex(7, expRank);
            const rPiece = pos.board[hSq];
            if (rPiece?.type === 'r' && rPiece.color === color && !pos.board[fSq] && !pos.board[gSq] && !isSquareAttacked(pos, fSq, opp) && !isSquareAttacked(pos, gSq, opp)) {
              pseudo.push({ from: idx, to: gSq, isCapture: false, isEnPassant: false, isKingSideCastle: true, isQueenSideCastle: false });
            }
          }
          if (canQ) {
            const dSq = toSquareIndex(3, expRank);
            const cSq = toSquareIndex(2, expRank);
            const bSq = toSquareIndex(1, expRank);
            const aSq = toSquareIndex(0, expRank);
            const rPiece = pos.board[aSq];
            if (rPiece?.type === 'r' && rPiece.color === color && !pos.board[dSq] && !pos.board[cSq] && !pos.board[bSq] && !isSquareAttacked(pos, dSq, opp) && !isSquareAttacked(pos, cSq, opp)) {
              pseudo.push({ from: idx, to: cSq, isCapture: false, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: true });
            }
          }
        }
      }
    } else {
      const rays = p.type === 'b' ? BISHOP_RAYS : p.type === 'r' ? ROOK_RAYS : [...BISHOP_RAYS, ...ROOK_RAYS];
      for (const [df, dr] of rays) {
        let f = ff + df;
        let r = fr + dr;
        while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
          const to = toSquareIndex(f, r);
          const t = pos.board[to];
          if (!t) {
            pseudo.push({ from: idx, to, isCapture: false, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
          } else {
            if (t.color === opp) {
              pseudo.push({ from: idx, to, isCapture: true, isEnPassant: false, isKingSideCastle: false, isQueenSideCastle: false });
            }
            break;
          }
          f += df;
          r += dr;
        }
      }
    }
  }

  return pseudo.filter((m) => !isKingInCheck(applyMoveUnchecked(pos, m), color));
}

export function isInsufficientMaterial(pos: ChessPosition): boolean {
  let knights = 0;
  let bLight = 0;
  let bDark = 0;
  for (let i = 0; i < 64; i++) {
    const p = pos.board[i];
    if (!p) continue;
    if (p.type === 'p' || p.type === 'r' || p.type === 'q') return false;
    if (p.type === 'n') knights++;
    if (p.type === 'b') {
      if ((squareFile(i) + squareRank(i)) % 2 !== 0) bLight++;
      else bDark++;
    }
  }
  if (knights + bLight + bDark <= 1) return true;
  if (knights === 0 && (bLight === 0 || bDark === 0)) return true;
  return false;
}

export function toSan(pos: ChessPosition, move: ChessMove, legalMoves: ChessMove[]): string {
  const piece = pos.board[move.from]!;
  let base = '';
  if (move.isKingSideCastle) base = 'O-O';
  else if (move.isQueenSideCastle) base = 'O-O-O';
  else if (piece.type === 'p') {
    if (move.isCapture) base += `${String.fromCharCode(97 + squareFile(move.from))}x`;
    base += indexToAlgebraic(move.to);
    if (move.promotion) base += `=${move.promotion.toUpperCase()}`;
  } else {
    base += piece.type.toUpperCase();
    const amb = legalMoves.filter(
      (m) => m.from !== move.from && m.to === move.to && pos.board[m.from]?.type === piece.type
    );
    if (amb.length > 0) {
      const sameF = amb.some((m) => squareFile(m.from) === squareFile(move.from));
      const sameR = amb.some((m) => squareRank(m.from) === squareRank(move.from));
      if (!sameF) base += String.fromCharCode(97 + squareFile(move.from));
      else if (!sameR) base += `${squareRank(move.from) + 1}`;
      else base += indexToAlgebraic(move.from);
    }
    if (move.isCapture) base += 'x';
    base += indexToAlgebraic(move.to);
  }
  const next = applyMoveUnchecked(pos, move);
  if (isKingInCheck(next, next.sideToMove)) {
    base += generateLegalMoves(next).length === 0 ? '#' : '+';
  }
  return base;
}

export function repetitionKey(pos: ChessPosition): string {
  const full = serializeFen(pos).split(' ');
  const hasEp = pos.enPassantTarget !== null && generateLegalMoves(pos).some((m) => m.isEnPassant);
  return `${full[0]} ${full[1]} ${full[2]} ${hasEp ? full[3] : '-'}`;
}

export class FideChessGame {
  initialFen: string;
  timeline: ChessPosition[] = [];
  history: MoveRecord[] = [];
  repetitions: string[] = [];
  forcedStatus: GameStatus | null = null;

  constructor(fen: string = STARTING_FEN) {
    this.initialFen = fen;
    this.reset(fen);
  }

  reset(fen: string = STARTING_FEN): void {
    const pos = parseFen(fen);
    this.initialFen = serializeFen(pos);
    this.timeline = [pos];
    this.history = [];
    this.repetitions = [repetitionKey(pos)];
    this.forcedStatus = null;
  }

  get current(): ChessPosition {
    return this.timeline[this.timeline.length - 1];
  }

  status(): GameStatus {
    if (this.forcedStatus) return this.forcedStatus;
    const pos = this.current;
    const legal = generateLegalMoves(pos);
    const inCheck = isKingInCheck(pos, pos.sideToMove);
    if (legal.length === 0) {
      return inCheck ? { kind: 'checkmate', winner: oppositeColor(pos.sideToMove) } : { kind: 'stalemate' };
    }
    if (isInsufficientMaterial(pos)) return { kind: 'draw', reason: 'Matériel insuffisant' };
    if (pos.halfmoveClock >= 100) return { kind: 'draw', reason: 'Règle des 50 coups' };
    const lastKey = this.repetitions[this.repetitions.length - 1];
    if (this.repetitions.filter((k) => k === lastKey).length >= 3) {
      return { kind: 'draw', reason: 'Triple répétition' };
    }
    return { kind: 'ongoing', isCheck: inCheck };
  }

  legalMovesFrom(sq: number): ChessMove[] {
    const s = this.status();
    if (s.kind !== 'ongoing') return [];
    return generateLegalMoves(this.current).filter((m) => m.from === sq);
  }

  needsPromotion(from: number, to: number): boolean {
    return this.legalMovesFrom(from).some((m) => m.to === to && !!m.promotion);
  }

  playMove(from: number, to: number, promotion?: Exclude<PieceType, 'p' | 'k'>): MoveRecord | null {
    const candidates = this.legalMovesFrom(from).filter((m) => m.to === to);
    if (candidates.length === 0) return null;
    const chosen = promotion
      ? candidates.find((m) => m.promotion === promotion)
      : candidates.some((m) => m.promotion)
        ? undefined
        : candidates[0];
    if (!chosen) return null;

    const prev = this.current;
    const allLegal = generateLegalMoves(prev);
    const san = toSan(prev, chosen, allLegal);
    const captured = chosen.isEnPassant
      ? prev.board[toSquareIndex(squareFile(chosen.to), squareRank(chosen.from))]
      : prev.board[chosen.to];

    const next = applyMoveUnchecked(prev, chosen);
    const isCheckAfter = isKingInCheck(next, next.sideToMove);
    const isCheckmateAfter = isCheckAfter && generateLegalMoves(next).length === 0;
    const rec: MoveRecord = {
      ply: this.history.length + 1,
      move: chosen,
      san,
      fenAfter: serializeFen(next),
      captured,
      isCheckAfter,
      isCheckmateAfter,
    };
    this.timeline.push(next);
    this.history.push(rec);
    this.repetitions.push(repetitionKey(next));
    return rec;
  }

  playUci(uci: string): MoveRecord | null {
    if (uci.length < 4) return null;
    const from = algebraicToIndex(uci.slice(0, 2));
    const to = algebraicToIndex(uci.slice(2, 4));
    if (from === null || to === null) return null;
    const promo = uci.length >= 5 ? (uci[4] as Exclude<PieceType, 'p' | 'k'>) : undefined;
    return this.playMove(from, to, promo);
  }

  undo(steps = 1): boolean {
    if (this.history.length === 0 || steps <= 0) return false;
    const n = Math.min(steps, this.history.length);
    for (let i = 0; i < n; i++) {
      this.timeline.pop();
      this.history.pop();
      this.repetitions.pop();
    }
    this.forcedStatus = null;
    return true;
  }

  exportPgn(white = 'Blancs', black = 'Noirs'): string {
    const s = this.status();
    const res =
      s.kind === 'checkmate' || s.kind === 'resigned' || s.kind === 'timeout'
        ? s.winner === 'w'
          ? '1-0'
          : '0-1'
        : s.kind === 'stalemate' || s.kind === 'draw'
          ? '1/2-1/2'
          : '*';
    const headers = [
      `[Event "Partie Locale"]`,
      `[Site "Échecs Pro Offline"]`,
      `[White "${white}"]`,
      `[Black "${black}"]`,
      `[Result "${res}"]`,
    ];
    if (this.initialFen !== STARTING_FEN) {
      headers.push(`[SetUp "1"]`, `[FEN "${this.initialFen}"]`);
    }
    const body = this.history
      .map((r, i) => (i % 2 === 0 ? `${Math.floor(i / 2) + 1}. ${r.san}` : r.san))
      .concat(res)
      .join(' ');
    return `${headers.join('\n')}\n\n${body}`;
  }

  loadPgn(pgn: string): void {
    const headers: Record<string, string> = {};
    const bodyLines: string[] = [];
    for (const raw of pgn.split('\n')) {
      const line = raw.trim();
      const hm = line.match(/^\[(\w+)\s+"(.*)"\]$/);
      if (hm) headers[hm[1]] = hm[2];
      else if (!line.startsWith('%')) bodyLines.push(line.split(';')[0]);
    }
    const startFen = headers.FEN || STARTING_FEN;
    this.reset(startFen);
    const cleaned = bodyLines
      .join(' ')
      .replace(/\{[^}]*\}/g, ' ')
      .replace(/\([^)]*\)/g, ' ');
    for (const rawTok of cleaned.trim().split(/\s+/)) {
      if (!rawTok || ['1-0', '0-1', '1/2-1/2', '*'].includes(rawTok)) continue;
      if (/^\d+\.+$/.test(rawTok) || rawTok.startsWith('$')) continue;
      const tok = rawTok.replace(/^\d+\.+/, '').replace(/0-0-0/g, 'O-O-O').replace(/0-0/g, 'O-O').replace(/[+#!?]+$/, '');
      if (!tok) continue;
      const legal = generateLegalMoves(this.current);
      const match = legal.find((m) => toSan(this.current, m, legal).replace(/[+#]+$/, '') === tok);
      if (!match) throw new Error(`Coup PGN illégal : ${tok}`);
      this.playMove(match.from, match.to, match.promotion);
    }
  }
}

/**
 * Évaluateur local léger (Alpha-Beta profondeur 2-3) pour l'aperçu web uniquement
 * (sur Android, Stockfish 19 via OEX remplace cet évaluateur).
 */
export function evaluatePositionCp(pos: ChessPosition): number {
  let score = 0;
  for (let i = 0; i < 64; i++) {
    const p = pos.board[i];
    if (!p) continue;
    const base = PIECE_VALUES[p.type] * 100;
    const f = squareFile(i);
    const r = squareRank(i);
    const centerBonus = (3.5 - Math.abs(f - 3.5)) * 6 + (3.5 - Math.abs(r - 3.5)) * 6;
    const val = base + (p.type === 'k' ? 0 : centerBonus);
    score += p.color === 'w' ? val : -val;
  }
  return Math.round(score);
}

export function findLocalBestMove(pos: ChessPosition, depth = 2): { move: ChessMove; evalCp: number } | null {
  const legal = generateLegalMoves(pos);
  if (legal.length === 0) return null;

  function alphabeta(p: ChessPosition, d: number, alpha: number, beta: number, maximizing: boolean): number {
    if (d === 0) return evaluatePositionCp(p);
    const moves = generateLegalMoves(p);
    if (moves.length === 0) {
      if (isKingInCheck(p, p.sideToMove)) return maximizing ? -90000 - d : 90000 + d;
      return 0;
    }
    // Ordonner captures en premier pour une coupe rapide
    moves.sort((a, b) => Number(b.isCapture) - Number(a.isCapture));
    if (maximizing) {
      let best = -Infinity;
      for (const m of moves) {
        best = Math.max(best, alphabeta(applyMoveUnchecked(p, m), d - 1, alpha, beta, false));
        alpha = Math.max(alpha, best);
        if (beta <= alpha) break;
      }
      return best;
    } else {
      let best = Infinity;
      for (const m of moves) {
        best = Math.min(best, alphabeta(applyMoveUnchecked(p, m), d - 1, alpha, beta, true));
        beta = Math.min(beta, best);
        if (beta <= alpha) break;
      }
      return best;
    }
  }

  const maxRoot = pos.sideToMove === 'w';
  let bestMove = legal[0];
  let bestScore = maxRoot ? -Infinity : Infinity;
  for (const m of legal) {
    const sc = alphabeta(applyMoveUnchecked(pos, m), depth - 1, -Infinity, Infinity, !maxRoot);
    if ((maxRoot && sc > bestScore) || (!maxRoot && sc < bestScore)) {
      bestScore = sc;
      bestMove = m;
    }
  }
  return { move: bestMove, evalCp: bestScore };
}
