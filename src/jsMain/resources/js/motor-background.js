/**
 * Motor Background — Industrial metallic grid with interactive lighting
 *
 * Procedural normal-mapped metal surface: rectangular machined panels,
 * bolt heads at grid intersections, cross-hatch machining marks.
 * Point light tracks the mouse; cool fill light provides contrast.
 */
import * as THREE from './three/three.module.min.js';

/** Convert a normal component [-1..1] to pixel value [0..255] */
function n2c(n) {
  return Math.max(0, Math.min(255, (n + 1.0) * 127.5));
}

/** Deterministic hash for per-cell random tilt */
function hashCell(col, row) {
  var h = (col * 73856093) ^ (row * 19349663);
  h = ((h >> 16) ^ h) * 0x45d9f3b;
  h = ((h >> 16) ^ h) * 0x45d9f3b;
  h = (h >> 16) ^ h;
  return (h & 0xffff) / 0xffff;
}

/**
 * Generate a procedural rectangular-grid normal map.
 * Each panel has beveled edges, per-cell tilt, and subtle machining marks.
 * Bolt heads appear at alternating grid intersections (checkerboard).
 */
function generateMotorNormalMap(width, height) {
  var cnv = document.createElement('canvas');
  cnv.width = width;
  cnv.height = height;
  var ctx = cnv.getContext('2d');
  var imageData = ctx.createImageData(width, height);
  var data = imageData.data;

  // Rectangular grid parameters
  var stride = 36;            // panel + groove period (px)
  var grooveHalf = 1.5;       // half groove width (3px total)
  var bevelW = 3.5;           // chamfer transition width
  var bevelStrength = 0.75;   // max bevel normal deflection

  // Bolt head parameters
  var boltR = 4.5;            // bolt radius
  var boltBevel = 0.65;       // inner flat ratio
  var boltStrength = 0.55;    // bolt bevel normal strength

  for (var y = 0; y < height; y++) {
    for (var x = 0; x < width; x++) {
      var idx = (y * width + x) * 4;

      // Position within grid period
      var mx = ((x % stride) + stride) % stride;
      var my = ((y % stride) + stride) % stride;

      // Distance to nearest groove center line
      var dxG = mx <= stride * 0.5 ? mx : stride - mx;
      var dyG = my <= stride * 0.5 ? my : stride - my;

      // Direction toward nearest groove (for bevel orientation)
      var sgnX = mx <= stride * 0.5 ? -1 : 1;
      var sgnY = my <= stride * 0.5 ? -1 : 1;

      // Nearest grid intersection for bolt check
      var intX = Math.round(x / stride) * stride;
      var intY = Math.round(y / stride) * stride;
      var dxI = x - intX;
      var dyI = y - intY;
      var distI = Math.sqrt(dxI * dxI + dyI * dyI);

      // Bolt at this intersection? Checkerboard pattern
      var iCol = Math.round(x / stride);
      var iRow = Math.round(y / stride);
      var hasBolt = ((iCol + iRow) & 1) === 0;

      // Panel cell for per-panel tilt
      var cCol = Math.floor(x / stride);
      var cRow = Math.floor(y / stride);
      var h1 = hashCell(cCol, cRow);
      var h2 = hashCell(cCol + 997, cRow + 991);
      var tiltX = (h1 - 0.5) * 0.10;
      var tiltY = (h2 - 0.5) * 0.10;

      var nx, ny, nz;

      if (hasBolt && distI < boltR) {
        // ── Bolt head: flat center + beveled rim ──
        var bInner = boltR * boltBevel;
        if (distI <= bInner) {
          var g = (Math.random() - 0.5) * 0.008;
          nx = g;
          ny = g;
        } else {
          var bt = (distI - bInner) / (boltR - bInner);
          var bs = bt * bt * (3 - 2 * bt);
          var bLen = distI || 1;
          nx = (dxI / bLen) * boltStrength * bs;
          ny = (dyI / bLen) * boltStrength * bs;
        }
        nz = Math.sqrt(Math.max(0.01, 1 - nx * nx - ny * ny));

      } else if (dxG <= grooveHalf || dyG <= grooveHalf) {
        // ── Groove channel: flat bottom ──
        nx = 0;
        ny = 0;
        nz = 1;

      } else {
        // ── Panel face + bevel zone ──

        // Bevel factors per axis (independent)
        var bvX = 0, bvY = 0;
        if (dxG < grooveHalf + bevelW) {
          var tv = 1 - (dxG - grooveHalf) / bevelW;
          bvX = tv * tv * (3 - 2 * tv) * bevelStrength;
        }
        if (dyG < grooveHalf + bevelW) {
          var th = 1 - (dyG - grooveHalf) / bevelW;
          bvY = th * th * (3 - 2 * th) * bevelStrength;
        }

        // Cap combined bevel magnitude at corners
        var bMag = Math.sqrt(bvX * bvX + bvY * bvY);
        if (bMag > bevelStrength) {
          bvX *= bevelStrength / bMag;
          bvY *= bevelStrength / bMag;
        }

        // Cross-hatch machining marks (+-45 deg)
        var scratch1 = Math.sin((x - y) * 0.45) * 0.014;
        var scratch2 = Math.sin((x + y) * 0.45) * 0.010;

        // Micro grain
        var grain = (Math.random() - 0.5) * 0.008;

        // Face detail fades out near beveled edges
        var faceWeight = 1.0 - Math.min(1.0, bMag / bevelStrength);

        nx = sgnX * bvX + (tiltX + scratch1 - scratch2 + grain) * faceWeight;
        ny = sgnY * bvY + (tiltY - scratch1 - scratch2 + grain) * faceWeight;
        nz = Math.sqrt(Math.max(0.01, 1 - nx * nx - ny * ny));
      }

      data[idx]     = n2c(nx);
      data[idx + 1] = n2c(ny);
      data[idx + 2] = n2c(nz);
      data[idx + 3] = 255;
    }
  }

  ctx.putImageData(imageData, 0, 0);

  var texture = new THREE.CanvasTexture(cnv);
  texture.wrapS = THREE.ClampToEdgeWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  return texture;
}

// ═══════════════════════════════════════════════════════════════════════════
//  Scene setup
// ═══════════════════════════════════════════════════════════════════════════

function initMotorBackground() {
  var canvas = document.getElementById('motor-bg');
  if (!canvas) return;

  var renderer = new THREE.WebGLRenderer({ canvas: canvas, alpha: false, antialias: false });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);

  var scene = new THREE.Scene();
  scene.background = new THREE.Color(0x191C22);

  var aspect = window.innerWidth / window.innerHeight;
  var camSize = 1;
  var camera = new THREE.OrthographicCamera(
    -camSize * aspect, camSize * aspect,
    camSize, -camSize,
    0.1, 10
  );
  camera.position.z = 1;

  // Normal map matching viewport aspect so panels stay square
  var texH = 2048;
  var texW = Math.ceil(texH * aspect);
  var normalMap = generateMotorNormalMap(texW, texH);

  // Full-viewport plane with machined metal material
  var geometry = new THREE.PlaneGeometry(2 * aspect, 2);
  var material = new THREE.MeshStandardMaterial({
    color: 0x252830,
    roughness: 0.32,
    metalness: 0.92,
    normalMap: normalMap,
    normalScale: new THREE.Vector2(0.9, 0.9),
  });
  var plane = new THREE.Mesh(geometry, material);
  scene.add(plane);

  // ── Lighting (same as karsten-gerber.de) ──

  scene.add(new THREE.AmbientLight(0x191C22, 0.5));

  var mainLight = new THREE.PointLight(0xffd4a8, 1.35, 0, 1.0);
  mainLight.position.set(0, 0, 2.5);
  scene.add(mainLight);

  var fillLight = new THREE.DirectionalLight(0x6677aa, 0.12);
  fillLight.position.set(-1, -0.5, 0.5);
  scene.add(fillLight);

  // ── Hover color transitions ──

  var defaultColor    = new THREE.Color(0xffd4a8);
  var hoverColor      = new THREE.Color(0x4a7fb5);
  var targetColor     = new THREE.Color(0xffd4a8);
  var defaultIntensity = 0.54;
  var hoverIntensity   = 2.7;
  var targetIntensity  = defaultIntensity;

  var selector = 'a, button, .ui.button';
  document.addEventListener('pointerover', function (e) {
    if (e.target && e.target.closest && e.target.closest(selector)) {
      targetColor.copy(hoverColor);
      targetIntensity = hoverIntensity;
    }
  });
  document.addEventListener('pointerout', function (e) {
    if (e.target && e.target.closest && e.target.closest(selector)) {
      targetColor.copy(defaultColor);
      targetIntensity = defaultIntensity;
    }
  });

  // ── Mouse tracking ──

  var mouseX = 0, mouseY = 0;
  var targetX = 0, targetY = 0;

  document.addEventListener('mousemove', function (e) {
    targetX = (e.clientX / window.innerWidth) * 2 - 1;
    targetY = -(e.clientY / window.innerHeight) * 2 + 1;
  });

  if (typeof DeviceOrientationEvent !== 'undefined') {
    window.addEventListener('deviceorientation', function (e) {
      if (e.gamma !== null && e.beta !== null) {
        targetX = e.gamma / 45;
        targetY = (e.beta - 45) / 45;
      }
    });
  }

  // ── Resize ──

  var resizeTimer;
  function onResize() {
    var w = window.innerWidth, h = window.innerHeight;
    aspect = w / h;
    renderer.setSize(w, h);
    camera.left  = -camSize * aspect;
    camera.right =  camSize * aspect;
    camera.updateProjectionMatrix();
    plane.geometry.dispose();
    plane.geometry = new THREE.PlaneGeometry(2 * aspect, 2);
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      var nw = Math.ceil(texH * aspect);
      var nm = generateMotorNormalMap(nw, texH);
      if (material.normalMap) material.normalMap.dispose();
      material.normalMap = nm;
      material.needsUpdate = true;
    }, 300);
  }
  window.addEventListener('resize', onResize);

  // ── Idle wandering animation ──

  var lastInteraction = 0;
  var wasIdle = false;
  var velX = 0, velY = 0;
  var attractX = 0, attractY = 0;
  var gravity = 0.3, maxSpeed = 0.4, damping = 0.98, nearThreshold = 0.2;

  function pickAttractor() {
    var angle  = Math.random() * Math.PI * 2;
    var radius = 0.4 + Math.random() * 0.5;
    attractX = Math.cos(angle) * radius;
    attractY = Math.sin(angle) * radius * 0.7;
  }

  document.addEventListener('mousemove', function () {
    lastInteraction = performance.now();
    wasIdle = false;
  });

  // Tab visibility
  var lastFrameTime = performance.now();
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) {
      lastFrameTime = performance.now();
      lastInteraction = 0;
      wasIdle = false;
      renderer.render(scene, camera);
    }
  });

  // WebGL context recovery
  canvas.addEventListener('webglcontextlost', function (e) { e.preventDefault(); });
  canvas.addEventListener('webglcontextrestored', function () { animate(); });

  // ── Render loop ──

  function animate() {
    requestAnimationFrame(animate);
    var now = performance.now();
    var delta = Math.min(now - lastFrameTime, 100);
    lastFrameTime = now;

    // Idle wandering (physics-based attractor)
    if (now - lastInteraction > 3000) {
      if (!wasIdle) {
        wasIdle = true;
        velX = 0; velY = 0;
        pickAttractor();
      }
      var dt = delta / 1000;
      var dx = attractX - targetX;
      var dy = attractY - targetY;
      var dist = Math.sqrt(dx * dx + dy * dy) || 0.001;
      velX += dx * gravity * dt;
      velY += dy * gravity * dt;
      velX *= damping;
      velY *= damping;
      var speed = Math.sqrt(velX * velX + velY * velY);
      if (speed > maxSpeed) {
        velX = (velX / speed) * maxSpeed;
        velY = (velY / speed) * maxSpeed;
      }
      targetX += velX * dt;
      targetY += velY * dt;
      if (dist < nearThreshold) pickAttractor();
    }

    // Smooth mouse follow
    mouseX += (targetX - mouseX) * 0.18;
    mouseY += (targetY - mouseY) * 0.18;
    mainLight.position.x = mouseX * aspect;
    mainLight.position.y = mouseY;

    // Smooth color / intensity transition
    mainLight.color.lerp(targetColor, 0.05);
    mainLight.intensity += (targetIntensity - mainLight.intensity) * 0.05;

    renderer.render(scene, camera);
  }

  animate();
}

// Expose globally so Kraft can call it after mounting the canvas
window.initMotorBackground = initMotorBackground;
