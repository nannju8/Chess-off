import test from 'node:test';
import assert from 'node:assert/strict';
import {
  FideChessGame,
  STARTING_FEN,
  algebraicToIndex,
  generateLegalMoves,
  parseFen,
  serializeFen,
} from './fideRules';

test('FEN initial et génération des 20 coups légaux', () => {
  const pos = parseFen(STARTING_FEN);
  assert.equal(serializeFen(pos), STARTING_FEN);
  assert.equal(generateLegalMoves(pos).length, 20);
});

test('prise en passant interdite si clouage horizontal', () => {
  const game = new FideChessGame('8/8/8/K2pP2r/8/8/8/4k3 w - d6 0 1');
  const illegal = game.playMove(algebraicToIndex('e5')!, algebraicToIndex('d6')!);
  assert.equal(illegal, null);
});

test('mat du berger, triple répétition et PGN aller-retour', () => {
  const game = new FideChessGame();
  for (const uci of ['e2e4', 'e7e5', 'd1h5', 'b8c6', 'f1c4', 'g8f6', 'h5f7']) {
    assert.ok(game.playUci(uci));
  }
  assert.deepEqual(game.status(), { kind: 'checkmate', winner: 'w' });

  const pgn = game.exportPgn('Vous', 'Stockfish 19');
  const replayed = new FideChessGame();
  replayed.loadPgn(pgn);
  assert.equal(replayed.history.length, 7);
  assert.deepEqual(replayed.status(), { kind: 'checkmate', winner: 'w' });

  const repGame = new FideChessGame();
  for (let i = 0; i < 2; i++) {
    for (const uci of ['g1f3', 'g8f6', 'f3g1', 'f6g8']) {
      assert.ok(repGame.playUci(uci));
    }
  }
  assert.deepEqual(repGame.status(), { kind: 'draw', reason: 'Triple répétition' });
});
