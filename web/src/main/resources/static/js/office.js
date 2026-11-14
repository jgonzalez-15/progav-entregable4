// Oficina 3D modelada de forma procedural a partir de la imagen de referencia:
// escritorio gris oscuro, monitor con fondo de dunas, teclado, mouse, auriculares,
// lapicero, notas adhesivas, vaso térmico verde, cuaderno, bowl con manzana,
// ventana con atardecer, estantería con libros y plantas, alfombra geométrica
// y silla de malla. No hay personas en la escena.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

export const ROOM = { halfX: 3.5, halfZ: 3.5, height: 3.0 };
export const DESK = { x: 0, z: -1.6, width: 1.8, depth: 0.85, top: 0.76 };

// ---------- utilidades ----------
function canvasTexture(w, h, draw, repeat) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); }
  return t;
}

function std(color, opts = {}) {
  return new THREE.MeshStandardMaterial({ color, roughness: 0.75, metalness: 0, ...opts });
}

function mesh(geo, mat, x = 0, y = 0, z = 0, parent) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  m.castShadow = true;
  m.receiveShadow = true;
  if (parent) parent.add(m);
  return m;
}

function box(w, h, d, mat, x, y, z, parent) {
  return mesh(new THREE.BoxGeometry(w, h, d), mat, x, y, z, parent);
}

function rbox(w, h, d, r, mat, x, y, z, parent) {
  return mesh(new RoundedBoxGeometry(w, h, d, 3, r), mat, x, y, z, parent);
}

function rand(seed) {
  // PRNG determinista: la oficina se ve igual en cada carga
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

// ---------- texturas ----------
const tex = {
  floor: () => canvasTexture(512, 512, (g, w, h) => {
    const r = rand(7);
    const plank = h / 8;
    for (let i = 0; i < 8; i++) {
      const base = 120 + r() * 30;
      g.fillStyle = `rgb(${base + 40},${base - 10},${base - 55})`;
      g.fillRect(0, i * plank, w, plank);
      for (let k = 0; k < 40; k++) {
        g.strokeStyle = `rgba(70,40,20,${0.05 + r() * 0.08})`;
        g.beginPath();
        const y = i * plank + r() * plank;
        g.moveTo(0, y); g.bezierCurveTo(w * 0.3, y + 3, w * 0.6, y - 3, w, y + r() * 4);
        g.stroke();
      }
      g.fillStyle = 'rgba(40,20,10,0.55)';
      g.fillRect(0, i * plank, w, 2);
      const cut = r() * w;
      g.fillRect(cut, i * plank, 2, plank);
    }
  }, [4, 4]),

  rug: () => canvasTexture(512, 512, (g, w, h) => {
    g.fillStyle = '#e8e2d6'; g.fillRect(0, 0, w, h);
    const n = 8, s = w / n;
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      const cx = i * s + s / 2, cy = j * s + s / 2;
      g.fillStyle = (i + j) % 2 ? '#2f3133' : '#55585a';
      g.beginPath();
      g.moveTo(cx, cy - s * 0.42); g.lineTo(cx + s * 0.42, cy); g.lineTo(cx, cy + s * 0.42); g.lineTo(cx - s * 0.42, cy);
      g.closePath(); g.fill();
      g.fillStyle = '#e8e2d6';
      g.fillRect(cx - s * 0.1, cy - s * 0.1, s * 0.2, s * 0.2);
    }
    g.strokeStyle = '#2f3133'; g.lineWidth = 14; g.strokeRect(10, 10, w - 20, h - 20);
  }),

  dunes: () => canvasTexture(1024, 620, (g, w, h) => {
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#f6c9a0'); sky.addColorStop(0.5, '#f0a46c'); sky.addColorStop(1, '#d9774a');
    g.fillStyle = sky; g.fillRect(0, 0, w, h);
    const layers = ['#e98d58', '#d9733f', '#c4602f', '#a94d24'];
    layers.forEach((col, i) => {
      g.fillStyle = col;
      g.beginPath();
      const base = h * (0.45 + i * 0.13);
      g.moveTo(0, h);
      for (let x = 0; x <= w; x += 8) {
        g.lineTo(x, base + Math.sin(x / (140 + i * 40) + i * 1.7) * 38 + Math.sin(x / 57 + i) * 8);
      }
      g.lineTo(w, h); g.closePath(); g.fill();
    });
    // íconos del escritorio y barra de tareas
    g.fillStyle = 'rgba(255,255,255,0.85)';
    for (let i = 0; i < 5; i++) g.fillRect(24, 28 + i * 52, 30, 30);
    g.fillStyle = 'rgba(20,20,30,0.35)'; g.fillRect(0, h - 34, w, 34);
    g.fillStyle = 'rgba(255,255,255,0.9)';
    for (let i = 0; i < 6; i++) g.fillRect(w / 2 - 90 + i * 30, h - 26, 18, 18);
  }),

  sunset: () => canvasTexture(1024, 768, (g, w, h) => {
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#f7b267'); sky.addColorStop(0.45, '#ffd29a'); sky.addColorStop(0.7, '#ffe7c2'); sky.addColorStop(1, '#f2c48d');
    g.fillStyle = sky; g.fillRect(0, 0, w, h);
    const sun = g.createRadialGradient(w * 0.62, h * 0.62, 10, w * 0.62, h * 0.62, 260);
    sun.addColorStop(0, 'rgba(255,255,240,1)'); sun.addColorStop(0.2, 'rgba(255,240,200,0.8)'); sun.addColorStop(1, 'rgba(255,200,120,0)');
    g.fillStyle = sun; g.fillRect(0, 0, w, h);
    // árboles desenfocados
    const r = rand(3);
    for (let i = 0; i < 26; i++) {
      g.fillStyle = `rgba(${70 + r() * 40},${90 + r() * 40},${50 + r() * 20},${0.35 + r() * 0.3})`;
      g.beginPath();
      g.ellipse(r() * w, h * (0.72 + r() * 0.2), 50 + r() * 80, 70 + r() * 90, 0, 0, Math.PI * 2);
      g.fill();
    }
  }),

  picture: () => canvasTexture(256, 320, (g, w, h) => {
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#9fc4e8'); sky.addColorStop(1, '#f2d9b8');
    g.fillStyle = sky; g.fillRect(0, 0, w, h);
    g.fillStyle = '#4a6f9a';
    g.beginPath(); g.moveTo(0, h * 0.75); g.lineTo(w * 0.35, h * 0.35); g.lineTo(w * 0.6, h * 0.62); g.lineTo(w * 0.8, h * 0.45); g.lineTo(w, h * 0.7); g.lineTo(w, h); g.lineTo(0, h); g.fill();
    g.fillStyle = '#2f4f74';
    g.beginPath(); g.moveTo(0, h * 0.88); g.lineTo(w * 0.5, h * 0.62); g.lineTo(w, h * 0.9); g.lineTo(w, h); g.lineTo(0, h); g.fill();
  }),

  note: (text) => canvasTexture(256, 256, (g, w, h) => {
    g.fillStyle = '#f7e27a'; g.fillRect(0, 0, w, h);
    g.fillStyle = '#5a4a1a'; g.font = 'bold 52px "Comic Sans MS", "Segoe Print", cursive';
    g.textAlign = 'center';
    text.split('\n').forEach((line, i) => g.fillText(line, w / 2, 100 + i * 64));
  }),

  whiteboard: () => canvasTexture(1024, 512, (g, w, h) => {
    g.fillStyle = '#fbfbf8'; g.fillRect(0, 0, w, h);
    g.lineWidth = 6; g.strokeStyle = '#2b59c3'; g.font = 'bold 64px "Segoe Print", cursive'; g.fillStyle = '#2b59c3';
    g.fillText('Pipeline', 60, 100);
    const steps = ['Build', 'Tests', 'UAT', 'OK?', 'PROD'];
    steps.forEach((s, i) => {
      const x = 60 + i * 190;
      g.strokeStyle = i === 3 ? '#d64545' : '#2b59c3';
      g.strokeRect(x, 200, 150, 90);
      g.fillStyle = i === 3 ? '#d64545' : '#2b59c3';
      g.font = 'bold 40px "Segoe Print", cursive';
      g.fillText(s, x + 18, 260);
      if (i < steps.length - 1) { g.beginPath(); g.moveTo(x + 152, 245); g.lineTo(x + 188, 245); g.stroke(); }
    });
    g.fillStyle = '#2f9e44'; g.font = 'bold 48px "Segoe Print", cursive';
    g.fillText('✊ ✋ ✌', 60, 420);
  }),

  meshFabric: () => canvasTexture(128, 128, (g, w, h) => {
    g.fillStyle = '#2a2b2e'; g.fillRect(0, 0, w, h);
    g.strokeStyle = 'rgba(90,92,98,0.9)'; g.lineWidth = 2;
    for (let i = 0; i < w; i += 8) { g.beginPath(); g.moveTo(i, 0); g.lineTo(i, h); g.stroke(); g.beginPath(); g.moveTo(0, i); g.lineTo(w, i); g.stroke(); }
  }, [3, 3]),
};

// ---------- piezas ----------
function plant(scale = 1, seed = 1, potColor = '#f1ece4') {
  const g = new THREE.Group();
  const r = rand(seed);
  const pot = mesh(new THREE.CylinderGeometry(0.11, 0.08, 0.18, 24), std(potColor, { roughness: 0.5 }), 0, 0.09, 0, g);
  pot.castShadow = true;
  mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.01, 24), std('#3b2a1e'), 0, 0.175, 0, g);
  const leafGeo = new THREE.SphereGeometry(0.06, 12, 8);
  leafGeo.scale(1, 0.12, 1.8);
  leafGeo.translate(0, 0, 0.1);
  const greens = ['#3f7d3a', '#4f9447', '#356b31', '#5ea852'];
  for (let i = 0; i < 16; i++) {
    const pivot = new THREE.Group();
    pivot.position.set(0, 0.18 + r() * 0.12, 0);
    pivot.rotation.y = r() * Math.PI * 2;
    pivot.rotation.x = -0.4 - r() * 0.8;
    const leaf = mesh(leafGeo, std(greens[i % greens.length], { roughness: 0.6, side: THREE.DoubleSide }), 0, 0, 0, pivot);
    leaf.scale.setScalar(0.8 + r() * 0.6);
    g.add(pivot);
  }
  g.scale.setScalar(scale);
  return g;
}

function books(parent, x0, x1, y, z, seed) {
  const r = rand(seed);
  const colors = ['#2f4f6f', '#a33b3b', '#d9a441', '#3f6f5a', '#e8dcc5', '#5b4a7a', '#c96f3b', '#1f2b3a'];
  let x = x0;
  while (x < x1 - 0.03) {
    const w = 0.025 + r() * 0.03;
    const h = 0.18 + r() * 0.1;
    const lean = r() < 0.1 ? 0.25 : 0;
    const b = box(w, h, 0.18 + r() * 0.04, std(colors[Math.floor(r() * colors.length)], { roughness: 0.8 }), x + w / 2, y + h / 2, z, parent);
    b.rotation.z = lean;
    x += w + 0.004 + (r() < 0.12 ? 0.08 : 0);
  }
}

function chair() {
  const g = new THREE.Group();
  const dark = std('#1e1f22', { roughness: 0.5, metalness: 0.3 });
  const fabric = new THREE.MeshStandardMaterial({ map: tex.meshFabric(), roughness: 0.9 });
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    const leg = box(0.32, 0.03, 0.04, dark, Math.cos(a) * 0.16, 0.06, Math.sin(a) * 0.16, g);
    leg.rotation.y = -a;
    mesh(new THREE.SphereGeometry(0.025, 10, 8), dark, Math.cos(a) * 0.3, 0.025, Math.sin(a) * 0.3, g);
  }
  mesh(new THREE.CylinderGeometry(0.025, 0.03, 0.36, 12), dark, 0, 0.26, 0, g);
  rbox(0.5, 0.08, 0.48, 0.03, fabric, 0, 0.47, 0, g);
  const back = rbox(0.46, 0.55, 0.05, 0.03, fabric, 0, 0.85, 0.24, g);
  back.rotation.x = -0.12;
  for (const s of [-1, 1]) {
    box(0.04, 0.2, 0.04, dark, s * 0.25, 0.6, 0.02, g);
    rbox(0.07, 0.03, 0.26, 0.012, dark, s * 0.25, 0.71, 0.0, g);
  }
  return g;
}

function headphones() {
  const g = new THREE.Group();
  const m = std('#1c1d20', { roughness: 0.45 });
  const band = mesh(new THREE.TorusGeometry(0.085, 0.012, 10, 32, Math.PI), m, 0, 0.03, 0, g);
  band.rotation.x = -Math.PI / 2;
  for (const s of [-1, 1]) {
    const cup = mesh(new THREE.CylinderGeometry(0.045, 0.045, 0.035, 24), m, s * 0.085, 0.022, 0.02, g);
    cup.rotation.z = Math.PI / 2;
    cup.rotation.y = s * 0.25;
  }
  return g;
}

function keyboard() {
  const g = new THREE.Group();
  rbox(0.44, 0.018, 0.14, 0.006, std('#2a2a2d', { roughness: 0.5 }), 0, 0.009, 0, g);
  const keyGeo = new RoundedBoxGeometry(0.022, 0.01, 0.022, 2, 0.003);
  const keys = new THREE.InstancedMesh(keyGeo, std('#3a3a3e', { roughness: 0.6 }), 15 * 5);
  const m4 = new THREE.Matrix4();
  let i = 0;
  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 15; col++) {
      m4.makeTranslation(-0.196 + col * 0.028, 0.022, -0.052 + row * 0.026);
      keys.setMatrixAt(i++, m4);
    }
  }
  keys.castShadow = true;
  g.add(keys);
  box(0.16, 0.01, 0.02, std('#3a3a3e'), 0, 0.022, 0.078, g); // barra espaciadora
  return g;
}

function monitor() {
  const g = new THREE.Group();
  const black = std('#151517', { roughness: 0.35, metalness: 0.2 });
  rbox(0.24, 0.015, 0.16, 0.006, black, 0, 0.008, 0, g);
  box(0.05, 0.3, 0.03, black, 0, 0.16, 0.0, g);
  rbox(0.64, 0.4, 0.025, 0.008, black, 0, 0.38, 0.02, g);
  const screen = new THREE.Mesh(new THREE.PlaneGeometry(0.61, 0.37), new THREE.MeshBasicMaterial({ map: tex.dunes(), toneMapped: false }));
  screen.position.set(0, 0.38, 0.0335);
  screen.material.color.setScalar(0.92);
  g.add(screen);
  const note = new THREE.Mesh(new THREE.PlaneGeometry(0.08, 0.08), new THREE.MeshStandardMaterial({ map: tex.note('¡Suerte!\n:)') }));
  note.position.set(0.2, 0.23, 0.036);
  note.rotation.z = -0.08;
  g.add(note);
  return g;
}

function tumbler() {
  const g = new THREE.Group();
  mesh(new THREE.CylinderGeometry(0.038, 0.034, 0.15, 24), std('#6f8f5a', { roughness: 0.55 }), 0, 0.075, 0, g);
  mesh(new THREE.CylinderGeometry(0.039, 0.039, 0.035, 24), std('#b08158', { roughness: 0.9 }), 0, 0.12, 0, g);
  mesh(new THREE.CylinderGeometry(0.037, 0.039, 0.02, 24), std('#d9cbb6'), 0, 0.155, 0, g);
  mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.006, 24), std('#a47149', { roughness: 0.95 }), 0, 0.003, 0, g);
  return g;
}

function notebook() {
  const g = new THREE.Group();
  rbox(0.2, 0.02, 0.26, 0.004, std('#efe9df'), 0, 0.01, 0, g);
  box(0.2, 0.004, 0.26, std('#5c5a57'), 0, 0.001, 0, g);
  const ring = new THREE.TorusGeometry(0.008, 0.0018, 6, 12);
  for (let i = 0; i < 12; i++) {
    const t = mesh(ring, std('#888', { metalness: 0.8, roughness: 0.3 }), -0.1, 0.012, -0.11 + i * 0.02, g);
    t.rotation.x = Math.PI / 2;
    t.rotation.y = Math.PI / 2;
  }
  const pen = mesh(new THREE.CylinderGeometry(0.005, 0.005, 0.15, 10), std('#7a4fd0', { roughness: 0.3 }), 0.02, 0.026, 0.0, g);
  pen.rotation.z = Math.PI / 2;
  pen.rotation.y = 0.5;
  return g;
}

function penCup() {
  const g = new THREE.Group();
  mesh(new THREE.CylinderGeometry(0.04, 0.038, 0.1, 20, 1, true), std('#4a4b4f', { side: THREE.DoubleSide }), 0, 0.05, 0, g);
  mesh(new THREE.CylinderGeometry(0.038, 0.038, 0.004, 20), std('#4a4b4f'), 0, 0.002, 0, g);
  const colors = ['#2d6cdf', '#2f9e44', '#e03131', '#f08c00', '#7048e8', '#1098ad'];
  colors.forEach((c, i) => {
    const p = mesh(new THREE.CylinderGeometry(0.005, 0.005, 0.15, 8), std(c, { roughness: 0.3 }), Math.cos(i) * 0.018, 0.1, Math.sin(i) * 0.018, g);
    p.rotation.z = Math.cos(i * 2) * 0.18;
    p.rotation.x = Math.sin(i * 2) * 0.18;
  });
  return g;
}

function bowlWithApple() {
  const g = new THREE.Group();
  const bowl = mesh(new THREE.SphereGeometry(0.075, 24, 12, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), std('#8a5a3b', { side: THREE.DoubleSide, roughness: 0.6 }), 0, 0.07, 0, g);
  bowl.scale.y = 0.75;
  mesh(new THREE.SphereGeometry(0.042, 20, 16), std('#8cc63f', { roughness: 0.4 }), 0, 0.07, 0, g);
  mesh(new THREE.CylinderGeometry(0.003, 0.003, 0.02, 6), std('#5a3b1e'), 0, 0.115, 0, g);
  return g;
}

function deskLampCharger() {
  const g = new THREE.Group();
  rbox(0.12, 0.025, 0.08, 0.01, std('#6b4a35', { roughness: 0.7 }), 0, 0.0125, 0, g);
  rbox(0.07, 0.008, 0.14, 0.006, std('#7fb6c9', { roughness: 0.3 }), 0.13, 0.004, 0.02, g);
  return g;
}

// ---------- escena completa ----------
export function buildOffice(scene) {
  const { halfX, halfZ, height } = ROOM;
  const wallMat = std('#e9dfcf', { roughness: 0.95 });
  const trimMat = std('#f6f1e8', { roughness: 0.6 });

  // Piso, techo y paredes
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(halfX * 2, halfZ * 2), new THREE.MeshStandardMaterial({ map: tex.floor(), roughness: 0.55 }));
  floor.rotation.x = -Math.PI / 2;
  floor.receiveShadow = true;
  scene.add(floor);

  const ceiling = new THREE.Mesh(new THREE.PlaneGeometry(halfX * 2, halfZ * 2), std('#f3eee6'));
  ceiling.rotation.x = Math.PI / 2;
  ceiling.position.y = height;
  scene.add(ceiling);

  const wallT = 0.1;
  // Norte y sur
  box(halfX * 2, height, wallT, wallMat, 0, height / 2, -halfZ - wallT / 2, scene);
  box(halfX * 2, height, wallT, wallMat, 0, height / 2, halfZ + wallT / 2, scene);
  // Este
  box(wallT, height, halfZ * 2, wallMat, halfX + wallT / 2, height / 2, 0, scene);
  // Oeste con ventana (hueco z ∈ [-3.1, -0.3], y ∈ [0.9, 2.6])
  const win = { z0: -3.1, z1: -0.3, y0: 0.9, y1: 2.6 };
  const wx = -halfX - wallT / 2;
  box(wallT, win.y0, halfZ * 2, wallMat, wx, win.y0 / 2, 0, scene);
  box(wallT, height - win.y1, halfZ * 2, wallMat, wx, (height + win.y1) / 2, 0, scene);
  box(wallT, win.y1 - win.y0, win.z0 + halfZ, wallMat, wx, (win.y0 + win.y1) / 2, (-halfZ + win.z0) / 2, scene);
  box(wallT, win.y1 - win.y0, halfZ - win.z1, wallMat, wx, (win.y0 + win.y1) / 2, (halfZ + win.z1) / 2, scene);
  // Vista exterior y marco
  const outside = new THREE.Mesh(new THREE.PlaneGeometry(win.z1 - win.z0 + 0.6, win.y1 - win.y0 + 0.6), new THREE.MeshBasicMaterial({ map: tex.sunset(), toneMapped: false }));
  outside.position.set(-halfX - 0.6, (win.y0 + win.y1) / 2, (win.z0 + win.z1) / 2);
  outside.rotation.y = Math.PI / 2;
  scene.add(outside);
  const frameMat = std('#fbf8f2', { roughness: 0.4 });
  const fx = -halfX + 0.01;
  box(0.06, 0.05, win.z1 - win.z0, frameMat, fx, win.y0, (win.z0 + win.z1) / 2, scene);
  box(0.06, 0.05, win.z1 - win.z0, frameMat, fx, win.y1, (win.z0 + win.z1) / 2, scene);
  box(0.06, 0.04, win.z1 - win.z0, frameMat, fx, (win.y0 + win.y1) / 2 + 0.2, (win.z0 + win.z1) / 2, scene);
  for (const z of [win.z0, (win.z0 + win.z1) / 2, win.z1]) box(0.06, win.y1 - win.y0, 0.05, frameMat, fx, (win.y0 + win.y1) / 2, z, scene);
  box(0.22, 0.04, win.z1 - win.z0 + 0.2, trimMat, -halfX + 0.1, win.y0 - 0.02, (win.z0 + win.z1) / 2, scene); // alféizar
  const sillPlant1 = plant(0.8, 11); sillPlant1.position.set(-halfX + 0.12, win.y0, -2.6); scene.add(sillPlant1);
  const sillPlant2 = plant(0.6, 12, '#d9c7a7'); sillPlant2.position.set(-halfX + 0.12, win.y0, -0.8); scene.add(sillPlant2);

  // Zócalos
  for (const [w, d, x, z] of [[halfX * 2, 0.02, 0, -halfZ + 0.01], [halfX * 2, 0.02, 0, halfZ - 0.01], [0.02, halfZ * 2, halfX - 0.01, 0], [0.02, halfZ * 2, -halfX + 0.01, 0]]) {
    box(w, 0.1, d, trimMat, x, 0.05, z, scene);
  }

  // Alfombra geométrica
  const rug = new THREE.Mesh(new THREE.BoxGeometry(3.0, 0.01, 2.3), new THREE.MeshStandardMaterial({ map: tex.rug(), roughness: 1 }));
  rug.position.set(0.3, 0.005, -0.4);
  rug.receiveShadow = true;
  scene.add(rug);

  // ---------- Escritorio ----------
  const desk = new THREE.Group();
  desk.position.set(DESK.x, 0, DESK.z);
  scene.add(desk);
  rbox(DESK.width, 0.04, DESK.depth, 0.012, std('#56575b', { roughness: 0.55 }), 0, DESK.top - 0.02, 0, desk);
  const legMat = std('#1d1e21', { roughness: 0.4, metalness: 0.5 });
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    box(0.05, DESK.top - 0.04, 0.05, legMat, sx * (DESK.width / 2 - 0.07), (DESK.top - 0.04) / 2, sz * (DESK.depth / 2 - 0.07), desk);
  }
  box(DESK.width - 0.14, 0.05, 0.03, legMat, 0, DESK.top - 0.08, -DESK.depth / 2 + 0.07, desk);

  const onDesk = (obj, x, z, ry = 0) => { obj.position.set(x, DESK.top, z); obj.rotation.y = ry; desk.add(obj); return obj; };
  const mon = onDesk(monitor(), -0.3, -0.22, 0.05);
  onDesk(keyboard(), -0.3, 0.17, 0.03);
  const mouse = mesh(new THREE.SphereGeometry(1, 20, 14), std('#1c1d20', { roughness: 0.35 }), 0.02, DESK.top + 0.012, 0.2, desk);
  mouse.scale.set(0.032, 0.018, 0.052);
  onDesk(headphones(), -0.72, 0.22, 0.5);
  onDesk(penCup(), -0.62, -0.25);
  onDesk(plant(0.75, 21), -0.8, -0.28);
  onDesk(tumbler(), 0.42, -0.2);
  onDesk(bowlWithApple(), 0.62, -0.3);
  onDesk(notebook(), 0.75, 0.05, -0.35);
  onDesk(deskLampCharger(), 0.1, -0.28, 0.1);
  const noteMat = std('#f7e27a', { roughness: 0.9 });
  box(0.07, 0.003, 0.07, noteMat, -0.55, DESK.top + 0.002, 0.25, desk).rotation.y = 0.3;
  box(0.07, 0.003, 0.07, noteMat, -0.05, DESK.top + 0.002, -0.1, desk).rotation.y = -0.2;
  box(0.08, 0.02, 0.025, std('#e03131'), -0.62, DESK.top + 0.01, 0.05, desk).rotation.y = 0.8;
  box(0.08, 0.02, 0.025, std('#2f9e44'), -0.58, DESK.top + 0.01, 0.1, desk).rotation.y = 0.6;

  // Silla
  const ch = chair();
  ch.position.set(0.85, 0, -0.55);
  ch.rotation.y = -0.6;
  scene.add(ch);

  // ---------- Estantería (pared norte) ----------
  const shelf = new THREE.Group();
  shelf.position.set(2.0, 0, -halfZ + 0.2);
  scene.add(shelf);
  const shelfMat = std('#f2efe9', { roughness: 0.6 });
  const shelfW = 2.4, shelfD = 0.35;
  for (const x of [-shelfW / 2, 0, shelfW / 2]) box(0.03, 2.2, shelfD, shelfMat, x, 1.1, 0, shelf);
  [0.05, 0.55, 1.05, 1.55, 2.05].forEach((y) => box(shelfW + 0.03, 0.03, shelfD, shelfMat, 0, y, 0, shelf));
  books(shelf, -1.18, -0.3, 0.565, 0, 1);
  books(shelf, 0.05, 0.9, 1.065, 0, 2);
  books(shelf, -1.15, -0.05, 1.565, 0, 3);
  books(shelf, 0.1, 0.6, 0.065, 0, 4);
  const frame = rbox(0.28, 0.34, 0.03, 0.005, std('#3a2c22'), 0.85, 1.75, 0.02, shelf);
  frame.rotation.x = -0.08;
  const art = new THREE.Mesh(new THREE.PlaneGeometry(0.22, 0.28), new THREE.MeshStandardMaterial({ map: tex.picture() }));
  art.position.set(0, 0, 0.016);
  frame.add(art);
  const p1 = plant(0.8, 31); p1.position.set(0.4, 1.565, 0); shelf.add(p1);
  const p2 = plant(0.9, 32, '#d9c7a7'); p2.position.set(-0.6, 2.065, 0); shelf.add(p2);
  const p3 = plant(0.7, 33); p3.position.set(1.0, 0.565, 0); shelf.add(p3);
  rbox(0.35, 0.2, 0.28, 0.02, std('#c9b79a', { roughness: 0.9 }), -0.75, 0.165, 0, shelf);
  rbox(0.3, 0.22, 0.28, 0.02, std('#9a8f80', { roughness: 0.9 }), 0.85, 0.175, 0, shelf);

  // Planta grande en esquina y reloj de pared
  const big = plant(2.4, 41); big.position.set(-2.9, 0, -3.0); scene.add(big);
  const clock = mesh(new THREE.CylinderGeometry(0.18, 0.18, 0.03, 40), std('#fdfbf7'), -1.0, 2.2, -halfZ + 0.02, scene);
  clock.rotation.x = Math.PI / 2;
  const rim = mesh(new THREE.TorusGeometry(0.18, 0.012, 8, 40), std('#222'), -1.0, 2.2, -halfZ + 0.035, scene);
  const hand1 = box(0.008, 0.12, 0.004, std('#222'), -1.0, 2.25, -halfZ + 0.04, scene);
  const hand2 = box(0.006, 0.15, 0.004, std('#c92a2a'), -1.0, 2.2, -halfZ + 0.042, scene);
  hand2.geometry.translate(0, 0.07, 0);
  hand2.position.y = 2.2;
  rim.castShadow = false;

  // ---------- Pared este: puerta, póster, lámpara ----------
  const doorMat = std('#8b6a4f', { roughness: 0.6 });
  box(0.05, 2.1, 0.95, doorMat, halfX - 0.03, 1.05, 1.2, scene);
  box(0.06, 2.16, 0.06, trimMat, halfX - 0.03, 1.08, 0.7, scene);
  box(0.06, 2.16, 0.06, trimMat, halfX - 0.03, 1.08, 1.7, scene);
  box(0.06, 0.06, 1.06, trimMat, halfX - 0.03, 2.15, 1.2, scene);
  mesh(new THREE.SphereGeometry(0.03, 12, 10), std('#c0a060', { metalness: 0.8, roughness: 0.3 }), halfX - 0.08, 1.0, 0.85, scene);
  const poster = new THREE.Mesh(new THREE.PlaneGeometry(0.7, 0.9), new THREE.MeshStandardMaterial({ map: tex.picture() }));
  poster.position.set(halfX - 0.01, 1.6, -1.0);
  poster.rotation.y = -Math.PI / 2;
  scene.add(poster);
  const lamp = new THREE.Group();
  lamp.position.set(halfX - 0.4, 0, 2.9);
  mesh(new THREE.CylinderGeometry(0.15, 0.17, 0.03, 24), std('#222'), 0, 0.015, 0, lamp);
  mesh(new THREE.CylinderGeometry(0.012, 0.012, 1.55, 8), std('#222'), 0, 0.8, 0, lamp);
  mesh(new THREE.CylinderGeometry(0.12, 0.2, 0.25, 24, 1, true), new THREE.MeshStandardMaterial({ color: '#fff3dc', emissive: '#ffcf8a', emissiveIntensity: 0.9, side: THREE.DoubleSide }), 0, 1.6, 0, lamp);
  scene.add(lamp);

  // ---------- Pared sur: pizarra y sillón ----------
  const wb = new THREE.Mesh(new THREE.PlaneGeometry(1.8, 0.9), new THREE.MeshStandardMaterial({ map: tex.whiteboard(), roughness: 0.3 }));
  wb.position.set(-0.6, 1.6, halfZ - 0.03);
  wb.rotation.y = Math.PI;
  scene.add(wb);
  box(1.86, 0.96, 0.02, std('#b8bcc2', { metalness: 0.6, roughness: 0.3 }), -0.6, 1.6, halfZ - 0.015, scene);
  const sofa = new THREE.Group();
  sofa.position.set(-0.4, 0, halfZ - 0.5);
  const sofaMat = std('#7d8a7a', { roughness: 0.95 });
  rbox(1.9, 0.4, 0.8, 0.08, sofaMat, 0, 0.3, 0, sofa);
  rbox(1.9, 0.5, 0.2, 0.08, sofaMat, 0, 0.7, 0.3, sofa);
  for (const s of [-1, 1]) rbox(0.2, 0.55, 0.8, 0.08, sofaMat, s * 0.95, 0.42, 0, sofa);
  rbox(0.38, 0.32, 0.12, 0.06, std('#d9a441'), -0.55, 0.65, 0.12, sofa).rotation.z = 0.15;
  rbox(0.38, 0.32, 0.12, 0.06, std('#c96f3b'), 0.55, 0.65, 0.12, sofa).rotation.z = -0.12;
  scene.add(sofa);
  const sidePlant = plant(1.8, 51, '#c9b79a'); sidePlant.position.set(1.4, 0, halfZ - 0.4); scene.add(sidePlant);

  // Lámpara colgante sobre el escritorio
  mesh(new THREE.CylinderGeometry(0.004, 0.004, 0.9, 6), std('#111'), 0, height - 0.45, DESK.z, scene);
  mesh(new THREE.ConeGeometry(0.18, 0.18, 32, 1, true), new THREE.MeshStandardMaterial({ color: '#2b2b2b', side: THREE.DoubleSide }), 0, height - 0.95, DESK.z, scene);
  mesh(new THREE.SphereGeometry(0.05, 16, 12), new THREE.MeshStandardMaterial({ color: '#fff', emissive: '#ffd9a0', emissiveIntensity: 2 }), 0, height - 1.02, DESK.z, scene);

  // ---------- Iluminación cálida de atardecer ----------
  scene.add(new THREE.HemisphereLight('#ffe4c4', '#6b4a35', 0.9));
  const sun = new THREE.DirectionalLight('#ffb36b', 3.2);
  sun.position.set(-7, 3.2, -1.2);
  sun.target.position.set(0.5, 0.6, -1.4);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  sun.shadow.camera.left = -4; sun.shadow.camera.right = 4;
  sun.shadow.camera.top = 3; sun.shadow.camera.bottom = -3;
  sun.shadow.camera.near = 1; sun.shadow.camera.far = 16;
  sun.shadow.bias = -0.0004;
  sun.shadow.normalBias = 0.02;
  scene.add(sun, sun.target);
  const pendant = new THREE.PointLight('#ffd29a', 6, 4.5, 1.6);
  pendant.position.set(0, height - 1.1, DESK.z);
  scene.add(pendant);
  const fill = new THREE.PointLight('#ffe8cc', 4, 8, 1.5);
  fill.position.set(1.5, 2.6, 1.5);
  scene.add(fill);
  const lampLight = new THREE.PointLight('#ffcf8a', 3, 4, 1.8);
  lampLight.position.set(halfX - 0.4, 1.6, 2.9);
  scene.add(lampLight);

  return {
    clockHands: [hand1, hand2],
    monitor: mon,
  };
}
