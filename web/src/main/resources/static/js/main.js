// Controlador de la aplicación: pantallas, modos de juego y sincronización con la escena 3D.
import { Stage } from './stage.js';
import { api, GameSocket } from './net.js';

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];

const MOVE_ES = { ROCK: 'Piedra', PAPER: 'Papel', SCISSORS: 'Tijera' };
const VERB = {
  ROCK_SCISSORS: 'Piedra rompe Tijera',
  PAPER_ROCK: 'Papel envuelve Piedra',
  SCISSORS_PAPER: 'Tijera corta Papel',
};

const stage = new Stage($('#scene'));

/** Celebraciones disponibles (deben coincidir con el enum Celebration del backend). */
const CELEBRATIONS = [
  { id: 'BOUNCE', emoji: '🦘', label: 'Rebote', desc: 'Salto de alegría' },
  { id: 'PEACE', emoji: '✌️', label: 'Paz', desc: 'Signo de la paz' },
  { id: 'THUMBS_UP', emoji: '👍', label: 'Pulgar arriba', desc: '¡Bien jugado!' },
  { id: 'HORNS', emoji: '🤘', label: 'Rockero', desc: 'Cuernos con headbang' },
  { id: 'FIST_PUMP', emoji: '💪', label: 'Puño al aire', desc: 'Festejo de gol' },
  { id: 'WAVE', emoji: '👋', label: 'Chau chau', desc: 'Saludo burlón' },
  { id: 'DRUM', emoji: '🥁', label: 'Tamborileo', desc: 'Dedos sobre la mesa' },
  { id: 'MIDDLE_FINGER', emoji: '🖕', label: 'Mala seña', desc: 'Censurada 🔲' },
];
const CPU_CELEBRATIONS = ['BOUNCE', 'THUMBS_UP', 'FIST_PUMP', 'WAVE', 'DRUM', 'HORNS'];

function storageGet(key, fallback = null) {
  try { const v = localStorage.getItem(key); return v == null ? fallback : v; } catch { return fallback; }
}
function storageSet(key, value) {
  try { if (value == null) localStorage.removeItem(key); else localStorage.setItem(key, value); } catch { /* modo privado */ }
}

/** Perfil competitivo de este navegador: { playerId, token, name } */
let profile = (() => { try { return JSON.parse(storageGet('rps.profile')); } catch { return null; } })();
let myCelebration = storageGet('rps.celebration', 'BOUNCE');

/** Estado de la partida actual. `gen` invalida respuestas viejas si el jugador abandona. */
const state = {
  mode: null, // 'pve' | 'pvp' | 'ranked'
  gen: 0,
  me: null,
  opp: null,
  pveId: null,
  socket: null,
  code: null,
  myMove: null,
  inGame: false,
  competitive: false,
  targetWins: 0,
  animating: false,
  pendingMatchResult: null,
  queueStart: 0,
};

// ---------- utilidades de UI ----------
function show(screen) {
  $$('.screen').forEach((s) => s.classList.toggle('active', s.id === screen));
}

let toastTimer;
function toast(text, ms = 2800) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), ms);
}

function menuError(text) {
  $('#menu-error').textContent = text || '';
}

function playerName() {
  const name = $('#name').value.trim();
  if (!name) {
    menuError('Ingresá tu nombre para jugar');
    $('#name').focus();
    return null;
  }
  try { localStorage.setItem('rps.name', name); } catch { /* modo privado */ }
  return name;
}

function setMoveButtons(enabled) {
  $$('.move').forEach((b) => { b.disabled = !enabled; b.classList.remove('selected'); });
}

function setStatus(text) {
  $('#status').textContent = text;
}

function beat(word) {
  const c = $('#chant');
  c.textContent = word;
  c.classList.remove('show');
  void c.offsetWidth; // reinicia la animación CSS
  c.classList.add('show');
}

function showResult(kind, title, detail) {
  const r = $('#result');
  r.className = `result ${kind}`;
  r.innerHTML = `<h2>${title}</h2><p>${detail}</p>`;
  requestAnimationFrame(() => r.classList.add('show'));
}

function hideResult() {
  $('#result').classList.remove('show');
}

function explain(myMove, oppMove) {
  if (myMove === oppMove) return `Ambos eligieron ${MOVE_ES[myMove]}`;
  return VERB[`${myMove}_${oppMove}`] || VERB[`${oppMove}_${myMove}`];
}

function paintScore(myScore, oppScore, round, draws) {
  const bump = (el, value) => {
    if (el.textContent !== String(value)) {
      el.textContent = value;
      el.classList.remove('bump'); void el.offsetWidth; el.classList.add('bump');
    }
  };
  bump($('#me-score'), myScore);
  bump($('#opp-score'), oppScore);
  $('#round-label').textContent = `Ronda ${round + 1}`;
  $('#draws-label').textContent = `Empates: ${draws}`;
}

function paintPlayers() {
  $('#me-name').textContent = state.me.name;
  $('#me-shirt').style.background = state.me.colorHex;
  $('#opp-name').textContent = state.opp ? state.opp.name : '—';
  $('#opp-shirt').style.background = state.opp ? state.opp.colorHex : 'transparent';
  $('#room-label').textContent = state.competitive ? 'Clasificatoria · al mejor de 3'
    : (state.code ? `Sala ${state.code}` : '');
}

/** Indicador de partida al mejor de 3 (victorias necesarias de cada lado). */
function paintBestOf(myScore, oppScore) {
  const el = $('#bo3');
  if (!state.competitive) { el.innerHTML = ''; return; }
  const pips = [];
  for (let i = 0; i < state.targetWins; i++) pips.push(`<i class="${i < myScore ? 'me' : ''}"></i>`);
  pips.push('<span class="muted small">vs</span>');
  for (let i = 0; i < state.targetWins; i++) pips.push(`<i class="${i < oppScore ? 'opp' : ''}"></i>`);
  el.innerHTML = pips.join('');
}

// ---------- flujo común ----------
async function enterGame() {
  state.inGame = true;
  paintPlayers();
  paintScore(0, 0, 0, 0);
  paintBestOf(0, 0);
  hideResult();
  setStatus('Elegí tu jugada');
  show('hud');
  setMoveButtons(false);
  await stage.enterGame({ myColor: state.me.colorHex, oppColor: state.opp.colorHex });
  setMoveButtons(true);
}

function oppCelebration() {
  if (state.mode === 'pve') return CPU_CELEBRATIONS[Math.floor(Math.random() * CPU_CELEBRATIONS.length)];
  return (state.opp && state.opp.celebration) || 'BOUNCE';
}

async function resolveRound(gen, myMove, oppMove, outcome, scores, matchOver = false) {
  state.animating = true;
  hideResult();
  await stage.playRound(myMove, oppMove, beat);
  if (gen !== state.gen) return; // el jugador abandonó durante la animación
  paintScore(scores.me, scores.opp, scores.round, scores.draws);
  paintBestOf(scores.me, scores.opp);
  const detail = explain(myMove, oppMove);
  // En competitivo, la celebración elegida se reserva para el final de la partida.
  const celebrationFor = (who) => (state.competitive && !matchOver ? 'BOUNCE'
    : (who === 'me' ? myCelebration : oppCelebration()));
  let celebration = Promise.resolve();
  if (outcome === 'WIN') {
    showResult('win', matchOver ? '¡Ganaste la partida!' : '¡Ganaste la ronda!', detail);
    celebration = stage.celebrate('me', celebrationFor('me'));
    stage.sulk('opp');
  } else if (outcome === 'LOSE') {
    showResult('lose', matchOver ? `${state.opp.name} ganó la partida` : `Ganó ${state.opp.name}`, detail);
    celebration = stage.celebrate('opp', celebrationFor('opp'));
    stage.sulk('me');
  } else {
    showResult('draw', 'Empate', detail);
  }
  await celebration;
  if (gen !== state.gen) return;
  state.animating = false;
  if (state.pendingMatchResult) {
    const m = state.pendingMatchResult;
    state.pendingMatchResult = null;
    showMatchResult(m);
    return;
  }
  setStatus('Elegí tu jugada');
  setMoveButtons(true);
}

async function leaveGame(message) {
  if (state.competitive && state.inGame && !message
      && !window.confirm('Abandonar una partida competitiva cuenta como DERROTA. ¿Abandonar igual?')) {
    return;
  }
  if (state.competitive && state.inGame && !message) message = 'Abandonaste la partida: se registró como derrota';
  state.gen++;
  if (state.socket) { state.socket.close(); state.socket = null; }
  if (state.pveId) { api.endPve(state.pveId); state.pveId = null; }
  const wasInGame = state.inGame;
  Object.assign(state, { mode: null, me: null, opp: null, code: null, myMove: null, inGame: false,
    competitive: false, targetWins: 0, animating: false, pendingMatchResult: null });
  clearInterval(queueTimer);
  refreshProfileLine();
  hideResult();
  show('menu');
  if (message) toast(message);
  if (wasInGame) await stage.exitGame();
}

// ---------- PvE ----------
async function startPve() {
  const name = playerName();
  if (!name) return;
  menuError('');
  try {
    const s = await api.createPve(name);
    state.gen++;
    Object.assign(state, { mode: 'pve', pveId: s.sessionId, me: s.player, opp: s.cpu, code: null });
    await enterGame();
  } catch (e) {
    menuError(e.message);
  }
}

async function playPve(move) {
  const gen = state.gen;
  setMoveButtons(false);
  setStatus(`Elegiste ${MOVE_ES[move]}…`);
  try {
    const r = await api.playPve(state.pveId, move);
    if (gen !== state.gen) return;
    await resolveRound(gen, r.playerMove, r.cpuMove, r.outcome,
      { me: r.score.player, opp: r.score.cpu, round: r.score.rounds, draws: r.score.draws });
  } catch (e) {
    toast(e.message);
    setMoveButtons(true);
  }
}

// ---------- PvP ----------
function pvpHandlers() {
  return {
    JOINED: (m) => {
      state.me = m.you;
      state.code = m.code;
      onRoomState(m.room);
    },
    ROOM_STATE: (m) => onRoomState(m),
    ROUND_RESULT: (m) => onRoundResult(m),
    PLAYER_LEFT: (m) => {
      toast(`${m.name} abandonó la sala`);
    },
    ERROR: (m) => {
      if (state.inGame) { toast(m.message); setMoveButtons(true); return; }
      leaveGame();
      menuError(m.message);
    },
    CLOSED: () => leaveGame('Se perdió la conexión con el servidor'),
    // ---- competitivo ----
    QUEUED: (m) => onQueued(m),
    QUEUE_CANCELLED: () => {},
    MATCH_FOUND: (m) => {
      clearInterval(queueTimer);
      state.competitive = true;
      state.targetWins = m.room.targetWins;
      state.me = m.you;
      state.code = m.code;
      state.opp = null;
      toast('¡Rival encontrado!');
      onRoomState(m.room);
    },
    MATCH_RESULT: (m) => {
      if (state.animating) state.pendingMatchResult = m; // se muestra al terminar la animación
      else showMatchResult(m);
    },
  };
}

async function onRoomState(room) {
  const opp = room.players.find((p) => p.id !== state.me.id) || null;
  if (!opp) {
    // Sin rival: sala de espera (al crear o cuando el rival se fue)
    const hadOpponent = state.inGame;
    state.opp = null;
    $('#room-code').textContent = room.code;
    $('#lobby-players').innerHTML = room.players
      .map((p) => `<li><span class="shirt" style="background:${p.colorHex}"></span>${escapeHtml(p.name)}${p.id === state.me.id ? ' (vos)' : ''}</li>`)
      .join('');
    if (hadOpponent) {
      state.inGame = false;
      await stage.exitGame();
    }
    show('lobby');
    return;
  }
  const isNewOpponent = !state.opp || state.opp.id !== opp.id;
  state.opp = opp;
  if (!state.inGame) {
    await enterGame();
  } else if (isNewOpponent) {
    stage.setOpponentColor(opp.colorHex);
    paintPlayers();
  }
  if (opp.ready && state.myMove == null) setStatus(`${opp.name} ya eligió. ¡Tu turno!`);
}

async function onRoundResult(m) {
  const gen = state.gen;
  const myMove = m.moves[state.me.id];
  const oppMove = m.moves[state.opp.id];
  const outcome = m.winnerId == null ? 'DRAW' : (m.winnerId === state.me.id ? 'WIN' : 'LOSE');
  state.myMove = null;
  await resolveRound(gen, myMove, oppMove, outcome,
    { me: m.scores[state.me.id] ?? 0, opp: m.scores[state.opp.id] ?? 0, round: m.round, draws: m.draws },
    m.matchOver);
}

async function startPvp(kind) {
  const name = playerName();
  if (!name) return;
  let code;
  if (kind === 'JOIN') {
    code = $('#code').value.trim().toUpperCase();
    if (!/^[A-Z]{6}$/.test(code)) { menuError('El código debe tener 6 letras'); return; }
  }
  menuError('');
  state.gen++;
  state.mode = 'pvp';
  const socket = new GameSocket(pvpHandlers());
  state.socket = socket;
  try {
    await socket.connect();
    socket.send(kind === 'JOIN' ? { type: 'JOIN', code, name, celebration: myCelebration }
      : { type: 'CREATE', name, celebration: myCelebration });
  } catch (e) {
    state.socket = null;
    menuError(e.message);
  }
}

function playPvp(move) {
  state.myMove = move;
  setMoveButtons(false);
  $(`.move[data-move="${move}"]`).classList.add('selected');
  setStatus(`Elegiste ${MOVE_ES[move]}. Esperando a ${state.opp.name}…`);
  state.socket.send({ type: 'MOVE', move });
}

// ---------- competitivo ----------
let queueTimer;

/** Garantiza un perfil competitivo para el nombre ingresado (lo crea si hace falta). */
async function ensureProfile(name) {
  if (profile && profile.name.toLowerCase() === name.toLowerCase()) return profile;
  try {
    const p = await api.register(name, myCelebration);
    profile = { playerId: p.playerId, token: p.token, name: p.name };
    storageSet('rps.profile', JSON.stringify(profile));
    return profile;
  } catch (e) {
    if (/perfil competitivo/.test(e.message)) {
      throw new Error(`"${name}" ya tiene un perfil competitivo en otro navegador. Elegí otro nombre.`);
    }
    throw e;
  }
}

async function startRanked() {
  const name = playerName();
  if (!name) return;
  menuError('');
  try {
    await ensureProfile(name);
  } catch (e) {
    menuError(e.message);
    return;
  }
  state.gen++;
  Object.assign(state, { mode: 'ranked', competitive: true });
  if (!state.socket) {
    const socket = new GameSocket(pvpHandlers());
    state.socket = socket;
    try {
      await socket.connect();
    } catch (e) {
      state.socket = null;
      menuError(e.message);
      return;
    }
  }
  joinQueue();
}

function joinQueue() {
  state.queueStart = Date.now();
  $('#queue-time').textContent = '0:00';
  show('queue');
  clearInterval(queueTimer);
  queueTimer = setInterval(() => {
    const s = Math.floor((Date.now() - state.queueStart) / 1000);
    $('#queue-time').textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }, 250);
  state.socket.send({ type: 'QUEUE', playerId: profile.playerId, token: profile.token });
}

function onQueued(m) {
  $('#queue-info').textContent = `Tus victorias competitivas: ${m.wins} · Jugadores en cola: ${m.queueSize}`;
  const t = m.tolerance;
  $('#queue-range').textContent = `Buscando rivales con ${Math.max(0, m.wins - t)}–${m.wins + t} victorias (el rango se amplía con la espera)`;
}

async function cancelQueue() {
  if (state.socket) state.socket.send({ type: 'CANCEL_QUEUE' });
  await leaveGame();
}

async function showMatchResult(m) {
  const won = m.winnerId === state.me.id;
  const title = $('#mr-title');
  title.textContent = won ? '¡Victoria!' : 'Derrota';
  title.className = `mr-title ${won ? 'win' : 'lose'}`;
  const myScore = m.scores[state.me.id] ?? 0;
  const oppScore = state.opp ? (m.scores[state.opp.id] ?? 0) : 0;
  $('#mr-detail').textContent = m.forfeit
    ? (won ? `${state.opp ? state.opp.name : 'Tu rival'} abandonó la partida` : 'Perdiste por abandono')
    : `${myScore} - ${oppScore} contra ${state.opp ? state.opp.name : 'tu rival'}`;
  $('#mr-wins').textContent = m.you ? m.you.wins : '—';
  $('#mr-losses').textContent = m.you ? m.you.losses : '—';
  $('#mr-position').textContent = m.you && m.you.position ? `#${m.you.position}` : '—';
  $('#mr-note').textContent = m.counted ? '' : 'Esta partida no suma al ranking: límite diario de victorias contra el mismo rival (antifraude).';
  if (m.forfeit && won && !state.animating && state.inGame) {
    // El rival se fue: igual festejamos sobre el escritorio
    await stage.celebrate('me', myCelebration);
  }
  state.inGame = false;
  setMoveButtons(false);
  show('match-result');
  stage.exitGame();
}

// ---------- ranking ----------
let leaderboardReturn = 'menu';

async function openLeaderboard() {
  leaderboardReturn = document.querySelector('.screen.active')?.id || 'menu';
  show('leaderboard');
  const body = $('#lb-body');
  const me = $('#lb-me');
  body.innerHTML = '<tr><td colspan="4">Cargando…</td></tr>';
  me.classList.remove('show');
  try {
    const lb = await api.leaderboard(profile && profile.playerId);
    const myId = profile && profile.playerId;
    body.innerHTML = lb.top.length ? lb.top.map((e) => `
      <tr class="${e.playerId === myId ? 'me' : ''} ${e.position <= 3 ? `top${e.position}` : ''}">
        <td>${e.position}</td><td>${escapeHtml(e.name)}${e.playerId === myId ? ' (vos)' : ''}</td>
        <td>${e.wins}</td><td>${e.losses}</td></tr>`).join('')
      : '<tr><td colspan="4">Todavía nadie jugó partidas competitivas. ¡Sé el primero!</td></tr>';
    if (lb.me && !lb.meInTop) {
      // Fuera del Top 100: posición global exacta fija abajo
      me.innerHTML = lb.me.position > 0
        ? `<span class="pos">#${lb.me.position}</span><span>${escapeHtml(lb.me.name)} (vos)</span>
           <span class="stats">${lb.me.wins} V · ${lb.me.losses} D · de ${lb.totalRanked} jugadores</span>`
        : `<span class="pos">—</span><span>${escapeHtml(lb.me.name)}: todavía no jugaste partidas competitivas</span>`;
      me.classList.add('show');
    }
    const row = body.querySelector('tr.me');
    if (row) row.scrollIntoView({ block: 'center' });
  } catch (e) {
    body.innerHTML = `<tr><td colspan="4">${escapeHtml(e.message)}</td></tr>`;
  }
}

function closeLeaderboard() {
  show(leaderboardReturn === 'leaderboard' ? 'menu' : leaderboardReturn);
}

async function refreshProfileLine() {
  const line = $('#profile-line');
  if (!profile) { line.textContent = 'El modo competitivo crea un perfil con tu nombre'; return; }
  try {
    const p = await api.profile(profile.playerId);
    line.innerHTML = `Perfil competitivo: <b>${escapeHtml(p.name)}</b> · ${p.wins} V · ${p.losses} D${p.position ? ` · #${p.position}` : ''}`;
  } catch {
    // El perfil ya no existe en este ambiente (por ejemplo, base nueva): lo olvidamos
    profile = null;
    storageSet('rps.profile', null);
    line.textContent = 'El modo competitivo crea un perfil con tu nombre';
  }
}

// ---------- celebraciones ----------
let pendingCelebration = myCelebration;

function openSettings() {
  pendingCelebration = myCelebration;
  const grid = $('#celebrations');
  grid.innerHTML = CELEBRATIONS.map((c) => `
    <button class="celebration ${c.id === pendingCelebration ? 'selected' : ''}" data-id="${c.id}">
      <span class="emoji">${c.emoji}</span><b>${c.label}</b><small>${c.desc}</small></button>`).join('');
  grid.querySelectorAll('.celebration').forEach((b) => b.addEventListener('click', () => {
    pendingCelebration = b.dataset.id;
    grid.querySelectorAll('.celebration').forEach((x) => x.classList.toggle('selected', x === b));
  }));
  $('#settings-dialog').showModal();
}

async function saveSettings() {
  myCelebration = pendingCelebration;
  storageSet('rps.celebration', myCelebration);
  $('#settings-dialog').close();
  if (profile) {
    try { await api.updateCelebration(profile.playerId, profile.token, myCelebration); } catch { /* se reintenta al jugar */ }
  }
  toast(`Celebración: ${CELEBRATIONS.find((c) => c.id === myCelebration).label}`);
}

async function previewCelebration() {
  $('#settings-dialog').close();
  if (state.inGame) { toast('La vista previa está disponible desde el menú'); return; }
  show('none');
  await stage.previewCelebration(pendingCelebration, '#3e63dd');
  show('menu');
  $('#settings-dialog').showModal();
}

// ---------- historial ----------
async function openHistory() {
  const body = $('#history-body');
  body.innerHTML = '<tr><td colspan="5">Cargando…</td></tr>';
  $('#history-dialog').showModal();
  try {
    const rows = await api.history(20);
    body.innerHTML = rows.length ? rows.map((r) => `<tr>
      <td>${r.mode}${r.roomCode ? ` · ${r.roomCode}` : ''}</td>
      <td>${escapeHtml(r.playerA)} (${MOVE_ES[r.moveA]})</td><td>vs</td>
      <td>${escapeHtml(r.playerB)} (${MOVE_ES[r.moveB]})</td>
      <td>${r.winner ? escapeHtml(r.winner) : 'Empate'}</td></tr>`).join('')
      : '<tr><td colspan="5">Todavía no hay partidas</td></tr>';
  } catch (e) {
    body.innerHTML = `<tr><td colspan="5">${escapeHtml(e.message)}</td></tr>`;
  }
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---------- eventos ----------
$('#btn-pve').addEventListener('click', startPve);
$('#btn-ranked').addEventListener('click', startRanked);
$('#btn-cancel-queue').addEventListener('click', cancelQueue);
$('#btn-requeue').addEventListener('click', () => { state.gen++; state.inGame = false; joinQueue(); });
$('#btn-mr-ranking').addEventListener('click', openLeaderboard);
$('#btn-mr-menu').addEventListener('click', () => leaveGame());
$('#btn-leaderboard').addEventListener('click', openLeaderboard);
$('#btn-lb-close').addEventListener('click', closeLeaderboard);
$('#btn-settings').addEventListener('click', openSettings);
$('#btn-settings-save').addEventListener('click', saveSettings);
$('#btn-preview').addEventListener('click', previewCelebration);
$('#btn-create').addEventListener('click', () => startPvp('CREATE'));
$('#btn-join').addEventListener('click', () => startPvp('JOIN'));
$('#code').addEventListener('input', (e) => { e.target.value = e.target.value.toUpperCase().replace(/[^A-Z]/g, ''); });
$('#code').addEventListener('keydown', (e) => { if (e.key === 'Enter') startPvp('JOIN'); });
$('#name').addEventListener('keydown', (e) => { if (e.key === 'Enter') startPve(); });
$('#btn-history').addEventListener('click', openHistory);
$$('.leave').forEach((b) => b.addEventListener('click', () => leaveGame()));
$('#room-code').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(state.code); toast('Código copiado'); } catch { toast(state.code); }
});
$$('.move').forEach((b) => b.addEventListener('click', () => {
  if (stage.busy) return;
  const move = b.dataset.move;
  if (state.mode === 'pve') playPve(move);
  else if (state.mode === 'pvp' || state.mode === 'ranked') playPvp(move);
}));
window.addEventListener('keydown', (e) => {
  if (!state.inGame || document.activeElement.tagName === 'INPUT') return;
  const key = { '1': 'ROCK', '2': 'PAPER', '3': 'SCISSORS' }[e.key];
  if (key) $(`.move[data-move="${key}"]`).click();
  if (e.key === 'Escape' && !state.competitive) leaveGame();
});

// ---------- arranque ----------
refreshProfileLine();
try { $('#name').value = localStorage.getItem('rps.name') || ''; } catch { /* sin storage */ }
const params = new URLSearchParams(location.search);
if (params.get('code')) $('#code').value = params.get('code').toUpperCase().slice(0, 6);

api.info().then((info) => {
  const badge = $('#env-badge');
  badge.textContent = `${info.env} · ${/^\d/.test(info.version) ? 'v' : ''}${info.version}${info.commit && info.commit !== 'unknown' ? ` · ${info.commit.slice(0, 7)}` : ''}`;
  badge.classList.add(info.env);
  document.title = info.env === 'PROD' ? document.title : `[${info.env}] ${document.title}`;
}).catch(() => {});

stage.onFirstFrame = () => $('#loading').classList.add('hide');

// Exponer para pruebas manuales/automatizadas desde la consola
window.__rps = { stage, state, get profile() { return profile; } };
