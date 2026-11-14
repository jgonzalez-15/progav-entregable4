// Brazo + mano articulada construida con primitivas.
// Marco local de la mano (derecha, palma hacia abajo): +Z = dedos, +Y = dorso, +X = pulgar.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

const FINGERS = [
  // x base, longitudes de falanges, radio
  { x: 0.031, len: [0.032, 0.022, 0.018], r: 0.0098 }, // índice
  { x: 0.010, len: [0.035, 0.024, 0.019], r: 0.0100 }, // mayor
  { x: -0.011, len: [0.032, 0.022, 0.018], r: 0.0096 }, // anular
  { x: -0.031, len: [0.025, 0.018, 0.015], r: 0.0086 }, // meñique
];

const CURLED = [1.55, 1.75, 1.15];
const STRAIGHT = [0.05, 0.04, 0.02];

/**
 * Poses: cada dedo [abducción, flexión1, flexión2, flexión3];
 * pulgar [apertura, flexión base, flexión1, flexión2].
 */
export const POSES = {
  rock: {
    fingers: [[0, ...CURLED], [0, ...CURLED], [0, ...CURLED], [0, 1.6, 1.7, 1.1]],
    thumb: [-1.15, 0.2, 0.35, 0.25],
  },
  paper: {
    fingers: [[0.14, ...STRAIGHT], [0.04, ...STRAIGHT], [-0.07, ...STRAIGHT], [-0.18, 0.08, 0.05, 0.03]],
    thumb: [0.15, -0.42, 0.05, 0.05],
  },
  scissors: {
    fingers: [[0.22, ...STRAIGHT], [-0.13, ...STRAIGHT], [0, ...CURLED], [0, 1.6, 1.7, 1.1]],
    thumb: [-1.1, 0.2, 0.35, 0.25],
  },
  // --- Celebraciones (V2) ---
  peace: {
    fingers: [[0.26, ...STRAIGHT], [-0.18, ...STRAIGHT], [0, ...CURLED], [0, 1.6, 1.7, 1.1]],
    thumb: [-1.1, 0.2, 0.35, 0.25],
  },
  thumbsUp: {
    fingers: [[0, ...CURLED], [0, ...CURLED], [0, ...CURLED], [0, 1.6, 1.7, 1.1]],
    thumb: [1.05, -0.5, -0.05, -0.05],
  },
  horns: {
    fingers: [[0.18, ...STRAIGHT], [0, ...CURLED], [0, ...CURLED], [-0.22, 0.05, 0.04, 0.02]],
    thumb: [-1.0, 0.25, 0.45, 0.3],
  },
  middle: {
    fingers: [[0, ...CURLED], [0, 0.02, 0.02, 0.01], [0, ...CURLED], [0, 1.6, 1.7, 1.1]],
    thumb: [-1.1, 0.2, 0.35, 0.25],
  },
  relaxed: {
    fingers: [[0.1, 0.35, 0.3, 0.15], [0.03, 0.35, 0.3, 0.15], [-0.05, 0.35, 0.3, 0.15], [-0.14, 0.35, 0.3, 0.15]],
    thumb: [0.15, -0.3, 0.1, 0.1],
  },
};

const MOVE_TO_POSE = { ROCK: 'rock', PAPER: 'paper', SCISSORS: 'scissors' };
export const poseForMove = (move) => MOVE_TO_POSE[move] || 'rock';

function clonePose(p) {
  return { fingers: p.fingers.map((f) => [...f]), thumb: [...p.thumb] };
}

/** Material de contorno (casco invertido) que da el borde de dibujo animado. */
/** Mosaico pixelado para censurar la "mala seña". */
function censorSprite() {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const g = c.getContext('2d');
  const n = 8;
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
    const v = 150 + Math.floor(Math.random() * 90);
    g.fillStyle = `rgb(${v},${Math.floor(v * 0.72)},${Math.floor(v * 0.55)})`;
    g.fillRect(i * 8, j * 8, 8, 8);
  }
  g.strokeStyle = 'rgba(0,0,0,0.6)';
  g.lineWidth = 3;
  g.strokeRect(1, 1, 62, 62);
  const t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter;
  t.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, depthTest: false }));
  sprite.renderOrder = 10;
  sprite.scale.set(0.055, 0.075, 1);
  sprite.visible = false;
  return sprite;
}

function outlineMaterial() {
  const m = new THREE.MeshBasicMaterial({ color: '#5a2a14', side: THREE.BackSide });
  m.onBeforeCompile = (shader) => {
    shader.vertexShader = shader.vertexShader.replace(
      '#include <begin_vertex>',
      '#include <begin_vertex>\n transformed += normal * 0.0019;',
    );
  };
  return m;
}

export class Arm {
  /**
   * @param {object} o
   * @param {string} o.color color de la camiseta
   * @param {string} [o.skin] tono de piel
   */
  constructor({ color = '#3e63dd', skin = '#f2a27a' } = {}) {
    this.skinMat = new THREE.MeshStandardMaterial({ color: skin, roughness: 0.55 });
    this.sleeveMat = new THREE.MeshStandardMaterial({ color, roughness: 0.85 });
    this.outlineMat = outlineMaterial();

    this.root = new THREE.Group(); // en el hombro, +Z apunta a la muñeca
    this.limb = new THREE.Group(); // se estira en Z para alcanzar el objetivo
    this.root.add(this.limb);
    this.baseLength = 0.75;

    // Antebrazo y brazo (largo base 0.75 m)
    this.#skinCapsule(this.limb, 0.042, 0.42, 0.25); // brazo
    this.#skinCapsule(this.limb, 0.036, 0.36, 0.58); // antebrazo
    const sleeve = new THREE.Mesh(new THREE.CylinderGeometry(0.068, 0.075, 0.3, 24, 1, true), this.sleeveMat);
    sleeve.material.side = THREE.DoubleSide;
    sleeve.rotation.x = Math.PI / 2;
    sleeve.position.z = 0.12;
    sleeve.castShadow = true;
    this.limb.add(sleeve);
    const cuff = new THREE.Mesh(new THREE.TorusGeometry(0.069, 0.008, 8, 24), this.sleeveMat);
    cuff.position.z = 0.27;
    this.limb.add(cuff);

    // Mano
    this.hand = new THREE.Group();
    this.root.add(this.hand);
    this.hand.scale.setScalar(1.2); // manos algo más grandes: estilo caricatura
    this.handInner = new THREE.Group(); // permite rotaciones extra (celebraciones)
    this.hand.add(this.handInner);
    this.#addSkin(new RoundedBoxGeometry(0.086, 0.034, 0.09, 4, 0.014), this.handInner, 0, 0, 0.045);
    this.fingerJoints = FINGERS.map((f) => this.#buildFinger(f));
    this.thumbJoints = this.#buildThumb();
    this.censor = censorSprite();
    this.censor.position.set(0, 0.01, 0.0);
    this.fingerJoints[1][1].add(this.censor);
    /** Si se define, la palma mira hacia este punto (celebraciones). */
    this.facePoint = null;
    /** true = el dorso (no la palma) mira hacia facePoint. */
    this.faceBack = false;

    this.pose = clonePose(POSES.rock);
    this.fromPose = clonePose(POSES.rock);
    this.toPose = clonePose(POSES.rock);
    this.poseT = 1;
    this.poseDuration = 0.2;

    // Orientación de la muñeca en el mundo (rad)
    this.wrist = { pitch: 0, roll: Math.PI / 2, yaw: 0 };
    this.shoulder = new THREE.Vector3();
    this.target = new THREE.Vector3(0, 0, 1);
    this.applyPose();
  }

  #skinCapsule(parent, radius, length, z) {
    const geo = new THREE.CapsuleGeometry(radius, length, 6, 16);
    geo.rotateX(Math.PI / 2);
    return this.#addSkin(geo, parent, 0, 0, z);
  }

  #addSkin(geo, parent, x, y, z) {
    const m = new THREE.Mesh(geo, this.skinMat);
    m.position.set(x, y, z);
    m.castShadow = true;
    const o = new THREE.Mesh(geo, this.outlineMat);
    m.add(o);
    parent.add(m);
    return m;
  }

  #buildFinger(f) {
    const joints = [];
    let parent = this.handInner;
    let z = 0.086;
    f.len.forEach((len, i) => {
      const j = new THREE.Group();
      j.position.set(i === 0 ? f.x : 0, i === 0 ? 0.002 : 0, z);
      parent.add(j);
      const r = f.r * (1 - i * 0.07);
      const geo = new THREE.CapsuleGeometry(r, Math.max(0.001, len - r), 4, 12);
      geo.rotateX(Math.PI / 2);
      this.#addSkin(geo, j, 0, 0, len / 2);
      joints.push(j);
      parent = j;
      z = len;
    });
    return joints;
  }

  #buildThumb() {
    const base = new THREE.Group();
    base.position.set(0.04, -0.006, 0.022);
    this.handInner.add(base);
    const lens = [0.032, 0.026, 0.021];
    const joints = [base];
    let parent = base;
    lens.forEach((len, i) => {
      const geo = new THREE.CapsuleGeometry(0.0115 - i * 0.0008, len - 0.01, 4, 12);
      geo.rotateX(Math.PI / 2);
      this.#addSkin(geo, parent, 0, 0, len / 2);
      if (i < lens.length - 1) {
        const j = new THREE.Group();
        j.position.z = len;
        parent.add(j);
        joints.push(j);
        parent = j;
      }
    });
    return joints;
  }

  /** Vuelve a la orientación normal después de una celebración. */
  resetExtras() {
    this.facePoint = null;
    this.faceBack = false;
    this.censor.visible = false;
    this.handInner.rotation.set(0, 0, 0);
    this.wrist.yaw = 0;
  }

  setColor(hex) {
    this.sleeveMat.color.set(hex);
  }

  setSkin(hex) {
    this.skinMat.color.set(hex);
  }

  /** Cambia de pose con una transición suave. */
  setPose(name, duration = 0.18) {
    const p = typeof name === 'string' ? POSES[name] : name;
    this.fromPose = clonePose(this.pose);
    this.toPose = clonePose(p);
    this.poseT = duration <= 0 ? 1 : 0;
    this.poseDuration = Math.max(duration, 0.0001);
    if (duration <= 0) { this.pose = clonePose(p); this.applyPose(); }
  }

  applyPose() {
    this.fingerJoints.forEach((joints, i) => {
      const [spread, c1, c2, c3] = this.pose.fingers[i];
      joints[0].rotation.set(c1, spread, 0);
      joints[1].rotation.set(c2, 0, 0);
      joints[2].rotation.set(c3, 0, 0);
    });
    const [open, cb, c1, c2] = this.pose.thumb;
    // Pulgar: apunta hacia adelante-afuera y se cierra sobre los dedos
    this.thumbJoints[0].rotation.set(0.35 + cb, 0.55 + open, -0.6, 'YXZ');
    this.thumbJoints[1].rotation.set(c1, 0, 0);
    this.thumbJoints[2].rotation.set(c2, 0, 0);
  }

  /** Posiciona el brazo: hombro fijo, muñeca en `target`, mano orientada según `wrist`. */
  updateTransform() {
    const dir = new THREE.Vector3().subVectors(this.target, this.shoulder);
    const dist = Math.max(dir.length(), 0.2);
    this.root.position.copy(this.shoulder);
    this.root.lookAt(this.target);
    this.limb.scale.set(1, 1, dist / this.baseLength);
    this.hand.position.set(0, 0, dist);

    let flat = new THREE.Vector3(dir.x, 0, dir.z).normalize();
    if (this.facePoint) {
      flat = new THREE.Vector3(this.facePoint.x - this.target.x, 0, this.facePoint.z - this.target.z).normalize();
      if (this.faceBack) flat.negate();
    }
    const yaw = Math.atan2(flat.x, flat.z) + this.wrist.yaw;
    const desired = new THREE.Quaternion().setFromEuler(new THREE.Euler(this.wrist.pitch, yaw, this.wrist.roll, 'YXZ'));
    this.hand.quaternion.copy(this.root.quaternion).invert().multiply(desired);
  }

  update(dt) {
    if (this.poseT < 1) {
      this.poseT = Math.min(1, this.poseT + dt / this.poseDuration);
      const t = easeInOut(this.poseT);
      this.pose.fingers.forEach((f, i) => f.forEach((_, k) => {
        f[k] = lerp(this.fromPose.fingers[i][k], this.toPose.fingers[i][k], t);
      }));
      this.pose.thumb.forEach((_, k) => {
        this.pose.thumb[k] = lerp(this.fromPose.thumb[k], this.toPose.thumb[k], t);
      });
      this.applyPose();
    }
    this.updateTransform();
  }
}

export const lerp = (a, b, t) => a + (b - a) * t;
export const easeInOut = (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2);
export const easeOut = (t) => 1 - Math.pow(1 - t, 3);
