// Escenario 3D: renderer, cámaras, brazos y secuencias de animación.
import * as THREE from 'three';
import { buildOffice, DESK } from './office.js';
import { Arm, poseForMove, lerp, easeInOut, easeOut } from './arms.js';

const V = (x, y, z) => new THREE.Vector3(x, y, z);

// Puntos clave del duelo sobre el escritorio (coordenadas de mundo, metros)
export const LAYOUT = {
  me: {
    shoulder: V(-0.3, 0.88, -0.52),
    hidden: V(-0.05, 0.62, -0.62),
    ready: V(0.14, 1.0, -1.12),
    desk: V(0.2, DESK.top, -1.3),
  },
  opp: {
    shoulder: V(1.45, 1.2, -1.05),
    hidden: V(1.2, 0.6, -1.25),
    ready: V(0.62, 1.0, -1.32),
    desk: V(0.5, DESK.top, -1.44),
  },
};

// Altura del centro de la mano sobre la mesa según la pose final
const REST_HEIGHT = { rock: 0.047, paper: 0.019, scissors: 0.045 };
const REVEAL_ROLL = { rock: Math.PI / 2, paper: 0, scissors: Math.PI / 2 };

const MENU_EYE = V(0.25, 1.6, 0.7);
export const GAME_EYE = V(-0.02, 1.27, -0.66);
export const GAME_LOOK = V(0.36, 0.9, -1.42);

export class Stage {
  constructor(container) {
    this.container = container;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFShadowMap;
    container.appendChild(this.renderer.domElement);

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color('#d9c2a5');
    this.camera = new THREE.PerspectiveCamera(55, 1, 0.03, 50);
    this.office = buildOffice(this.scene);

    this.me = new Arm({ color: '#3e63dd', skin: '#f2a27a' });
    this.opp = new Arm({ color: '#e5484d', skin: '#c98a62' });
    for (const [arm, l] of [[this.me, LAYOUT.me], [this.opp, LAYOUT.opp]]) {
      arm.shoulder.copy(l.shoulder);
      arm.target.copy(l.hidden);
      arm.root.visible = false;
      this.scene.add(arm.root);
    }

    this.mode = 'menu';
    this.menuYaw = -0.4;
    this.tweens = new Set();
    this.timer = new THREE.Timer();
    this.time = 0;
    this.busy = false;

    this.camera.position.copy(MENU_EYE);
    this.#applyMenuCamera(0);

    window.addEventListener('resize', () => this.resize());
    this.resize();
    this.renderer.setAnimationLoop(() => this.#tick());
  }

  resize() {
    const w = this.container.clientWidth || window.innerWidth;
    const h = this.container.clientHeight || window.innerHeight;
    this.renderer.setSize(w, h);
    this.camera.aspect = w / h;
    // En pantallas verticales (celular) abrimos el campo de visión
    this.camera.fov = this.camera.aspect < 0.9 ? 72 : 55;
    this.camera.updateProjectionMatrix();
  }

  // ---------- motor de tweens ----------
  tween(duration, fn, ease = easeInOut) {
    return new Promise((resolve) => {
      const tw = { t: 0, duration, fn, ease, resolve };
      this.tweens.add(tw);
      if (duration <= 0) { fn(1); this.tweens.delete(tw); resolve(); }
    });
  }

  wait(seconds) {
    return this.tween(seconds, () => {});
  }

  #tick() {
    this.timer.update();
    // Tope de 0.2 s: en equipos lentos la animación dura lo mismo (PvP sincronizado)
    const dt = Math.min(this.timer.getDelta(), 0.2);
    this.time += dt;
    for (const tw of [...this.tweens]) {
      tw.t = Math.min(1, tw.t + dt / tw.duration);
      tw.fn(tw.ease(tw.t));
      if (tw.t >= 1) { this.tweens.delete(tw); tw.resolve(); }
    }
    if (this.mode === 'menu') {
      this.menuYaw += dt * 0.07; // giro lento sobre su eje
      this.#applyMenuCamera(this.time);
    } else if (this.mode === 'game') {
      // leve respiración de cámara
      this.camera.position.copy(GAME_EYE).add(V(Math.sin(this.time * 0.6) * 0.004, Math.sin(this.time * 0.9) * 0.004, 0));
      this.camera.lookAt(GAME_LOOK);
    }
    this.#animateClock();
    this.me.update(dt);
    this.opp.update(dt);
    this.renderer.render(this.scene, this.camera);
    if (!this.firstFrame) { this.firstFrame = true; this.onFirstFrame?.(); }
  }

  #menuQuaternion(time) {
    const pitch = -0.1 + Math.sin(time * 0.15) * 0.05;
    return new THREE.Quaternion().setFromEuler(new THREE.Euler(pitch, this.menuYaw, 0, 'YXZ'));
  }

  #applyMenuCamera(time) {
    this.camera.position.copy(MENU_EYE).add(V(0, Math.sin(time * 0.2) * 0.03, 0));
    this.camera.quaternion.copy(this.#menuQuaternion(time));
  }

  #animateClock() {
    const [h, m] = this.office.clockHands;
    const d = new Date();
    h.rotation.z = -((d.getHours() % 12) + d.getMinutes() / 60) / 12 * Math.PI * 2;
    m.rotation.z = -(d.getMinutes() + d.getSeconds() / 60) / 60 * Math.PI * 2;
  }

  // ---------- transiciones de cámara ----------
  async enterGame({ myColor, oppColor }) {
    this.me.setColor(myColor);
    this.opp.setColor(oppColor);
    const fromPos = this.camera.position.clone();
    const fromQ = this.camera.quaternion.clone();
    const toQ = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().lookAt(GAME_EYE, GAME_LOOK, V(0, 1, 0)));
    this.mode = 'transition';
    await this.tween(1.6, (t) => {
      this.camera.position.lerpVectors(fromPos, GAME_EYE, t);
      this.camera.quaternion.slerpQuaternions(fromQ, toQ, t);
    });
    this.mode = 'game';
    await this.showArms();
  }

  async exitGame() {
    this.tweens.clear();
    this.busy = false;
    this.me.resetExtras();
    this.opp.resetExtras();
    await this.hideArms();
    const fromPos = this.camera.position.clone();
    const fromQ = this.camera.quaternion.clone();
    this.mode = 'transition';
    await this.tween(1.6, (t) => {
      this.camera.position.lerpVectors(fromPos, MENU_EYE, t);
      this.camera.quaternion.slerpQuaternions(fromQ, this.#menuQuaternion(this.time), t);
    });
    this.mode = 'menu';
  }

  setOpponentColor(hex) {
    this.opp.setColor(hex);
  }

  async showArms() {
    for (const arm of [this.me, this.opp]) { arm.root.visible = true; arm.setPose('rock', 0.2); }
    await this.#moveBoth({ me: LAYOUT.me.ready, opp: LAYOUT.opp.ready }, 0.6, { pitch: -0.15, roll: Math.PI / 2 });
  }

  async hideArms() {
    await this.#moveBoth({ me: LAYOUT.me.hidden, opp: LAYOUT.opp.hidden }, 0.5, { pitch: 0.3, roll: Math.PI / 2 });
    this.me.root.visible = false;
    this.opp.root.visible = false;
  }

  /** Oculta solo el brazo rival (por ejemplo cuando abandona la sala). */
  async hideOpponent() {
    const from = this.opp.target.clone();
    await this.tween(0.5, (t) => this.opp.target.lerpVectors(from, LAYOUT.opp.hidden, t));
    this.opp.root.visible = false;
  }

  async showOpponent() {
    this.opp.root.visible = true;
    this.opp.setPose('rock', 0.2);
    const from = this.opp.target.clone();
    await this.tween(0.6, (t) => this.opp.target.lerpVectors(from, LAYOUT.opp.ready, t));
  }

  #moveBoth(targets, duration, wrist) {
    const arms = [[this.me, targets.me], [this.opp, targets.opp]];
    const starts = arms.map(([a]) => ({ p: a.target.clone(), w: { ...a.wrist } }));
    return this.tween(duration, (t) => {
      arms.forEach(([a, to], i) => {
        if (!to) return;
        a.target.lerpVectors(starts[i].p, to, t);
        if (wrist) {
          a.wrist.pitch = lerp(starts[i].w.pitch, wrist.pitch, t);
          a.wrist.roll = lerp(starts[i].w.roll, wrist.roll, t);
          a.wrist.yaw = lerp(starts[i].w.yaw, wrist.yaw ?? 0, t);
        }
      });
    });
  }

  /**
   * Animación completa de una ronda: tres sacudidas de puño al ritmo de
   * "piedra, papel, tijera" y revelación de las jugadas sobre el escritorio.
   * @param {string} myMove ROCK | PAPER | SCISSORS
   * @param {string} oppMove ROCK | PAPER | SCISSORS
   * @param {(word:string)=>void} onBeat callback para mostrar el texto del ritmo
   */
  async playRound(myMove, oppMove, onBeat = () => {}) {
    this.busy = true;
    this.me.resetExtras();
    this.opp.resetExtras();
    this.me.setPose('rock', 0.2);
    this.opp.setPose('rock', 0.2);
    await this.#moveBoth({ me: LAYOUT.me.ready, opp: LAYOUT.opp.ready }, 0.35, { pitch: -0.15, roll: Math.PI / 2 });

    const up = V(0, 0.13, 0);
    const down = V(0, -0.05, 0);
    for (const word of ['¡Piedra!', '¡Papel!', '¡Tijera!']) {
      await this.#moveBoth({ me: LAYOUT.me.ready.clone().add(up), opp: LAYOUT.opp.ready.clone().add(up) }, 0.2,
        { pitch: -0.45, roll: Math.PI / 2 });
      onBeat(word);
      await this.#moveBoth({ me: LAYOUT.me.ready.clone().add(down), opp: LAYOUT.opp.ready.clone().add(down) }, 0.22,
        { pitch: 0.12, roll: Math.PI / 2 });
    }

    // Revelación: las manos bajan al escritorio adoptando la jugada final
    const myPose = poseForMove(myMove);
    const oppPose = poseForMove(oppMove);
    await this.#moveBoth({ me: LAYOUT.me.ready.clone().add(up.clone().multiplyScalar(0.7)), opp: LAYOUT.opp.ready.clone().add(up.clone().multiplyScalar(0.7)) }, 0.18,
      { pitch: -0.3, roll: Math.PI / 2 });
    this.me.setPose(myPose, 0.25);
    this.opp.setPose(oppPose, 0.25);
    onBeat('¡YA!');
    await Promise.all([
      this.#reveal(this.me, LAYOUT.me.desk, myPose),
      this.#reveal(this.opp, LAYOUT.opp.desk, oppPose),
    ]);
    await this.wait(0.15);
    this.busy = false;
  }

  #reveal(arm, deskPoint, pose) {
    const from = arm.target.clone();
    const to = deskPoint.clone();
    to.y += REST_HEIGHT[pose];
    const w0 = { ...arm.wrist };
    return this.tween(0.32, (t) => {
      arm.target.lerpVectors(from, to, t);
      arm.wrist.pitch = lerp(w0.pitch, 0, t);
      arm.wrist.roll = lerp(w0.roll, REVEAL_ROLL[pose], t);
    }, easeOut);
  }

  // ===================== Celebraciones (V2) =====================

  /**
   * Ejecuta la celebración de victoria elegida por el ganador, sobre el escritorio.
   * @param {'me'|'opp'} who mano ganadora
   * @param {string} name id de Celebration (BOUNCE, PEACE, THUMBS_UP, HORNS, FIST_PUMP, WAVE, DRUM, MIDDLE_FINGER)
   */
  async celebrate(who, name = 'BOUNCE') {
    const arm = who === 'me' ? this.me : this.opp;
    const l = who === 'me' ? LAYOUT.me : LAYOUT.opp;
    // Puntos de referencia: hacia el rival (palma "afuera") o hacia uno mismo
    const rival = who === 'me' ? LAYOUT.opp.ready.clone() : this.camera.position.clone();
    const self = who === 'me' ? this.camera.position.clone() : l.shoulder.clone();
    const raised = l.desk.clone().lerp(l.ready, 0.35).add(V(0, 0.2, 0));
    this.busy = true;
    try {
      const anim = CELEBRATIONS[name] || CELEBRATIONS.BOUNCE;
      await anim.call(this, arm, { raised, rival, self, desk: l.desk.clone() });
    } finally {
      this.busy = false;
    }
  }

  /** Lleva la mano a una posición/orientación con una pose, en `d` segundos. */
  _pose(arm, { to, pitch, roll, yaw = 0, pose, face = null, back = false, d = 0.35 }) {
    if (pose) arm.setPose(pose, d * 0.8);
    arm.facePoint = face;
    arm.faceBack = back;
    const from = arm.target.clone();
    const w0 = { ...arm.wrist };
    return this.tween(d, (t) => {
      if (to) arm.target.lerpVectors(from, to, t);
      if (pitch !== undefined) arm.wrist.pitch = lerp(w0.pitch, pitch, t);
      if (roll !== undefined) arm.wrist.roll = lerp(w0.roll, roll, t);
      arm.wrist.yaw = lerp(w0.yaw, yaw, t);
    });
  }

  /** Vista previa desde la configuración: muestra solo mi mano y vuelve al menú. */
  async previewCelebration(name, color) {
    if (this.mode !== 'menu' || this.busy) return;
    this.busy = true;
    this.me.setColor(color || '#3e63dd');
    const fromPos = this.camera.position.clone();
    const fromQ = this.camera.quaternion.clone();
    const toQ = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().lookAt(GAME_EYE, GAME_LOOK, V(0, 1, 0)));
    this.mode = 'transition';
    await this.tween(1.2, (t) => {
      this.camera.position.lerpVectors(fromPos, GAME_EYE, t);
      this.camera.quaternion.slerpQuaternions(fromQ, toQ, t);
    });
    this.mode = 'game';
    this.me.root.visible = true;
    this.me.resetExtras();
    this.me.setPose('rock', 0.2);
    await this._pose(this.me, { to: LAYOUT.me.desk.clone().add(V(0, 0.05, 0)), pitch: 0, roll: Math.PI / 2, d: 0.5 });
    await this.celebrate('me', name);
    await this.wait(0.6);
    this.busy = false;
    this.me.resetExtras();
    const from = this.me.target.clone();
    await this.tween(0.4, (t) => this.me.target.lerpVectors(from, LAYOUT.me.hidden, t));
    this.me.root.visible = false;
    const p0 = this.camera.position.clone();
    const q0 = this.camera.quaternion.clone();
    this.mode = 'transition';
    await this.tween(1.2, (t) => {
      this.camera.position.lerpVectors(p0, MENU_EYE, t);
      this.camera.quaternion.slerpQuaternions(q0, this.#menuQuaternion(this.time), t);
    });
    this.mode = 'menu';
  }

  /** Mano perdedora se "desinfla" levemente. */
  async sulk(who) {
    const arm = who === 'me' ? this.me : this.opp;
    const w0 = arm.wrist.pitch;
    await this.tween(0.4, (t) => { arm.wrist.pitch = w0 + Math.sin(t * Math.PI) * 0.12; });
  }
}

// ---------------------------------------------------------------------------
// Animaciones de celebración. `this` es el Stage; `arm` la mano ganadora.
// ---------------------------------------------------------------------------
const UP = -Math.PI / 2 + 0.15; // dedos hacia arriba (levemente inclinados)

const CELEBRATIONS = {
  /** Rebote: la mano salta dos veces sobre el escritorio. */
  async BOUNCE(arm) {
    const base = arm.target.clone();
    await this.tween(0.8, (t) => {
      arm.target.copy(base).add(V(0, Math.abs(Math.sin(t * Math.PI * 2)) * 0.06, 0));
    }, (t) => t);
  },

  /** Signo de la paz: mano en alto, palma hacia el rival, con un pequeño vaivén. */
  async PEACE(arm, p) {
    await this._pose(arm, { to: p.raised, pitch: UP, roll: 0, pose: 'peace', face: p.rival, d: 0.4 });
    const base = arm.target.clone();
    await this.tween(1.4, (t) => {
      arm.handInner.rotation.y = Math.sin(t * Math.PI * 4) * 0.22;
      arm.target.copy(base).add(V(0, Math.sin(t * Math.PI * 4) * 0.012, 0));
    }, (t) => t);
    arm.handInner.rotation.y = 0;
  },

  /** Pulgar arriba con dos "golpes" hacia adelante. */
  async THUMBS_UP(arm, p) {
    await this._pose(arm, { to: p.raised.clone().add(V(0, -0.05, 0)), pitch: -0.1, roll: Math.PI / 2, pose: 'thumbsUp', face: p.rival, d: 0.4 });
    const base = arm.target.clone();
    const fwd = p.rival.clone().sub(base).setY(0).normalize().multiplyScalar(0.05);
    await this.tween(1.2, (t) => {
      const k = Math.max(0, Math.sin(t * Math.PI * 4));
      arm.target.copy(base).addScaledVector(fwd, k).add(V(0, k * 0.02, 0));
    }, (t) => t);
  },

  /** Cuernos rockeros: agitando la mano al ritmo ("headbang"). */
  async HORNS(arm, p) {
    await this._pose(arm, { to: p.raised.clone().add(V(0, 0.04, 0)), pitch: UP, roll: 0, pose: 'horns', face: p.rival, d: 0.35 });
    const base = arm.target.clone();
    await this.tween(1.5, (t) => {
      const k = Math.sin(t * Math.PI * 8);
      arm.target.copy(base).add(V(0, k * 0.035, 0));
      arm.wrist.pitch = UP + k * 0.25;
    }, (t) => t);
  },

  /** Puño al aire: tres "pumps" rápidos de festejo. */
  async FIST_PUMP(arm, p) {
    const high = p.raised.clone().add(V(0, 0.12, 0));
    await this._pose(arm, { to: high, pitch: -0.9, roll: Math.PI / 2, pose: 'rock', face: p.rival, d: 0.3 });
    const base = arm.target.clone();
    await this.tween(1.3, (t) => {
      const k = Math.abs(Math.sin(t * Math.PI * 3));
      arm.target.copy(base).add(V(0, -k * 0.09, 0));
      arm.wrist.pitch = -0.9 + k * 0.5;
    }, (t) => t);
  },

  /** Saludo burlón: "chau, chau" con la mano abierta. */
  async WAVE(arm, p) {
    await this._pose(arm, { to: p.raised.clone().add(V(0, 0.05, 0)), pitch: UP, roll: 0, pose: 'paper', face: p.rival, d: 0.35 });
    await this.tween(1.6, (t) => {
      arm.handInner.rotation.y = Math.sin(t * Math.PI * 6) * 0.45;
    }, (t) => t);
    arm.handInner.rotation.y = 0;
  },

  /** Tamborileo: dedos golpeando el escritorio del meñique al índice. */
  async DRUM(arm, p) {
    const to = p.desk.clone().add(V(0, 0.032, 0));
    await this._pose(arm, { to, pitch: 0.12, roll: 0, pose: 'relaxed', d: 0.35 });
    const order = [3, 2, 1, 0];
    const base = arm.pose.fingers.map((f) => [...f]);
    await this.tween(1.8, (t) => {
      const cycle = (t * 3) % 1; // 3 pasadas
      order.forEach((finger, i) => {
        const phase = cycle * 4 - i; // cada dedo en su turno
        const lift = phase > 0 && phase < 1 ? Math.sin(phase * Math.PI) : 0;
        arm.pose.fingers[finger][1] = base[finger][1] - lift * 0.55;
        arm.pose.fingers[finger][2] = base[finger][2] - lift * 0.2;
      });
      arm.applyPose();
    }, (t) => t);
  },

  /** Mala seña (dedo medio), censurada con un mosaico pixelado. El dorso mira al rival. */
  async MIDDLE_FINGER(arm, p) {
    await this._pose(arm, { to: p.raised.clone().add(V(0, 0.03, 0)), pitch: UP, roll: 0, pose: 'middle', face: p.rival, back: true, d: 0.4 });
    arm.censor.visible = true;
    const base = arm.target.clone();
    await this.tween(1.4, (t) => {
      arm.target.copy(base).add(V(0, Math.sin(t * Math.PI * 3) * 0.015, 0));
    }, (t) => t);
  },
};
