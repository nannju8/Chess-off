let sharedCtx: AudioContext | null = null;

function getAudioCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!sharedCtx) {
    const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    sharedCtx = new Ctor();
  }
  if (sharedCtx.state === 'suspended') {
    void sharedCtx.resume();
  }
  return sharedCtx;
}

function playTone(freq: number, duration: number, gainPeak: number, delaySec = 0) {
  const ctx = getAudioCtx();
  if (!ctx) return;
  const now = ctx.currentTime + delaySec;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(freq, now);
  osc.frequency.exponentialRampToValueAtTime(Math.max(55, freq * 0.6), now + duration);

  gain.gain.setValueAtTime(0.001, now);
  gain.gain.linearRampToValueAtTime(gainPeak, now + 0.006);
  gain.gain.exponentialRampToValueAtTime(0.001, now + duration);

  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start(now);
  osc.stop(now + duration + 0.01);
}

export function playChessSound(
  kind: 'move' | 'capture' | 'castle' | 'check' | 'end',
  muted: boolean
): void {
  if (muted) return;
  switch (kind) {
    case 'move':
      playTone(210, 0.075, 0.22);
      break;
    case 'capture':
      playTone(300, 0.055, 0.25);
      playTone(155, 0.085, 0.22, 0.02);
      break;
    case 'castle':
      playTone(195, 0.065, 0.2);
      playTone(235, 0.075, 0.22, 0.07);
      break;
    case 'check':
      playTone(620, 0.16, 0.22);
      break;
    case 'end':
      playTone(440, 0.22, 0.2);
      playTone(554.37, 0.28, 0.2, 0.11);
      break;
  }
}
