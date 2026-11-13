// aura-keyboard-api.js
// Aura Keyboard OS: Node.js + Express backend (in-memory store).
// Run:  npm i express  &&  node aura-keyboard-api.js
// Base URL: http://localhost:8787/v1

const express = require('express');
const app = express();
app.use(express.json({ limit: '64kb' }));

// CORS so the web app can post telemetry from any origin.
app.use((req, res, next) => {
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Headers', 'Content-Type');
  res.set('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

const LIMITS = {
  kbWidth: [78, 100],
  rowGap: [4, 18],
  keyHeight: [32, 54],
  keyScaleX: [82, 106],
};

const state = {
  startedAt: Date.now(),
  keystrokes: 0,
  chars: [], // timestamps of recent characters, used for WPM
  clips: [
    { id: 1, text: 'On my way, 10 minutes.', pinned: true },
    { id: 2, text: 'Thanks, I will check and get back to you.', pinned: false },
  ],
  nextClipId: 3,
  layout: { kbWidth: 100, rowGap: 7, keyHeight: 44, keyScaleX: 100, dock: 'center' },
  requests: [],
};

const EMOJI = {
  quick: ['❤️', '😂', '😮', '😢', '🙏', '👍', '🔥'],
  categories: {
    reactions: ['❤️', '😂', '😮', '😢', '🙏', '👍', '🔥', '🎉', '😍', '💯'],
    smileys: ['😀', '😃', '😄', '😁', '😆', '😊', '😇', '🙂', '😉', '😎'],
    gestures: ['👍', '👎', '👏', '🙌', '🤝', '🙏', '💪', '👋', '✌️', '👌'],
    stickers: ['🎉', '🎈', '🎁', '🏆', '🎯', '🎵', '📸', '🚀', '💡', '⭐'],
    nature: ['🌸', '🌻', '🌿', '🍀', '🌊', '🔥', '🌙', '🐶', '🦋', '🐌'],
    cafe: ['☕', '🍵', '🥐', '🍕', '🍜', '🍰', '🍩', '🍫', '🥤', '🍎'],
    activities: ['⚽', '🏀', '🎾', '🏏', '🎯', '🎲', '🧩', '🎨', '🏆', '🥇'],
    travel: ['🚗', '🚆', '✈️', '🚀', '⛵', '🏠', '🏰', '🛕', '🗼', '🏖️'],
    objects: ['💡', '📱', '💻', '📷', '⏰', '🔋', '💳', '📦', '🔑', '🛒'],
    symbols: ['❤️', '💔', '✅', '❌', '❓', '❗', '➕', '♻️', '⚠️', '💯'],
    flags: ['🇮🇳', '🇺🇸', '🇬🇧', '🇨🇦', '🇦🇺', '🇩🇪', '🇫🇷', '🇯🇵', '🇧🇷', '🏁'],
  },
};

// Log every request so the telemetry console has something to show.
app.use((req, res, next) => {
  const t0 = process.hrtime.bigint();
  res.on('finish', () => {
    const ms = Number(process.hrtime.bigint() - t0) / 1e6;
    state.requests.push({ at: Date.now(), method: req.method, path: req.path, status: res.statusCode, ms: +ms.toFixed(2) });
    if (state.requests.length > 200) state.requests.shift();
  });
  next();
});

const wpm = () => {
  const cutoff = Date.now() - 10000;
  state.chars = state.chars.filter((t) => t >= cutoff);
  return Math.round((state.chars.length / 5) * 6);
};

app.get('/v1/health', (req, res) => {
  res.json({ ok: true, uptimeSeconds: Math.round((Date.now() - state.startedAt) / 1000) });
});

app.post('/v1/keystroke', (req, res) => {
  const { key } = req.body || {};
  if (typeof key !== 'string' || key.length === 0 || key.length > 8) {
    return res.status(400).json({ error: 'key must be a string of 1 to 8 characters' });
  }
  state.keystrokes += 1;
  if (key.length === 1) state.chars.push(Date.now());
  res.status(202).json({ accepted: true, keystrokes: state.keystrokes });
});

app.get('/v1/telemetry', (req, res) => {
  res.json({
    wpm: wpm(),
    keystrokes: state.keystrokes,
    clips: state.clips.length,
    layout: state.layout,
    recentRequests: state.requests.slice(-20),
  });
});

app.get('/v1/clips', (req, res) => {
  const sorted = [...state.clips].sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.id - a.id);
  res.json(sorted);
});

app.post('/v1/clips', (req, res) => {
  const { text } = req.body || {};
  if (typeof text !== 'string' || !text.trim() || text.length > 2000) {
    return res.status(400).json({ error: 'text must be a non-empty string up to 2000 characters' });
  }
  const existing = state.clips.find((c) => c.text === text);
  if (existing) return res.status(200).json(existing);
  const clip = { id: state.nextClipId++, text, pinned: false };
  state.clips.push(clip);
  res.status(201).json(clip);
});

app.patch('/v1/clips/:id', (req, res) => {
  const clip = state.clips.find((c) => c.id === Number(req.params.id));
  if (!clip) return res.status(404).json({ error: 'clip not found' });
  if (typeof req.body.pinned === 'boolean') clip.pinned = req.body.pinned;
  res.json(clip);
});

app.delete('/v1/clips/:id', (req, res) => {
  const before = state.clips.length;
  state.clips = state.clips.filter((c) => c.id !== Number(req.params.id));
  if (state.clips.length === before) return res.status(404).json({ error: 'clip not found' });
  res.sendStatus(204);
});

app.delete('/v1/clips', (req, res) => {
  if (req.query.unpinned !== 'true') return res.status(400).json({ error: 'pass ?unpinned=true' });
  state.clips = state.clips.filter((c) => c.pinned);
  res.sendStatus(204);
});

app.get('/v1/layout', (req, res) => res.json(state.layout));

app.put('/v1/layout', (req, res) => {
  const next = { ...state.layout, ...(req.body || {}) };
  for (const [field, [lo, hi]] of Object.entries(LIMITS)) {
    const v = next[field];
    if (typeof v !== 'number' || v < lo || v > hi) {
      return res.status(400).json({ error: `${field} must be a number from ${lo} to ${hi}` });
    }
  }
  if (!['left', 'center', 'right'].includes(next.dock)) {
    return res.status(400).json({ error: 'dock must be left, center or right' });
  }
  state.layout = next;
  res.json(state.layout);
});

app.get('/v1/emoji', (req, res) => res.json(EMOJI));

app.use((req, res) => res.status(404).json({ error: 'not found' }));

const PORT = process.env.PORT || 8787;
app.listen(PORT, () => console.log(`Aura Keyboard API listening on http://localhost:${PORT}/v1`));
