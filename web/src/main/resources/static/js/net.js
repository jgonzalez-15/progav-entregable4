// Comunicación con el backend: REST (PvE, historial, info) y WebSocket (PvP).

async function request(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let detail = `Error ${res.status}`;
    try { detail = (await res.json()).detail || detail; } catch { /* sin cuerpo */ }
    throw new Error(detail);
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  info: () => request('GET', '/api/info'),
  history: (limit = 20) => request('GET', `/api/history?limit=${limit}`),
  createPve: (name) => request('POST', '/api/pve/sessions', { name }),
  playPve: (id, move) => request('POST', `/api/pve/sessions/${id}/rounds`, { move }),
  endPve: (id) => request('DELETE', `/api/pve/sessions/${id}`).catch(() => {}),
  // V2
  register: (name, celebration) => request('POST', '/api/players', { name, celebration }),
  leaderboard: (playerId) => request('GET', `/api/leaderboard${playerId ? `?playerId=${encodeURIComponent(playerId)}` : ''}`),
  updateCelebration: async (playerId, token, celebration) => {
    const res = await fetch(`/api/players/${playerId}/celebration`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-Player-Token': token },
      body: JSON.stringify({ celebration }),
    });
    if (!res.ok) throw new Error(`Error ${res.status}`);
    return res.json();
  },
  profile: (playerId) => request('GET', `/api/players/${playerId}`),
};

/** Cliente WebSocket del modo PvP con despacho por tipo de mensaje. */
export class GameSocket {
  constructor(handlers) {
    this.handlers = handlers;
    this.ws = null;
    this.closedByUser = false;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const proto = location.protocol === 'https:' ? 'wss' : 'ws';
      this.ws = new WebSocket(`${proto}://${location.host}/ws/game`);
      this.ws.onopen = () => resolve();
      this.ws.onerror = () => reject(new Error('No se pudo conectar con el servidor'));
      this.ws.onmessage = (e) => {
        const msg = JSON.parse(e.data);
        (this.handlers[msg.type] || (() => {}))(msg);
      };
      this.ws.onclose = () => {
        if (!this.closedByUser && this.handlers.CLOSED) this.handlers.CLOSED();
      };
    });
  }

  send(payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(payload));
  }

  /** Reemplaza los handlers (por ejemplo al pasar de la cola a una partida). */
  setHandlers(handlers) {
    this.handlers = handlers;
  }

  close() {
    this.closedByUser = true;
    if (this.ws) {
      this.send({ type: 'LEAVE' });
      this.ws.close();
    }
  }
}
