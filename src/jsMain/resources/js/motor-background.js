/**
 * Motor Background — Industrial metallic grid with interactive lighting
 *
 * Procedural normal-mapped metal surface: mosaic of varied rectangular
 * pieces (1x1 to 3x2) with void spaces, beveled edges, machining marks.
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
 * Generate a procedural mosaic normal map.
 *
 * A grid of cells is merged into varied rectangular pieces (1x1, 2x1, 2x2,
 * 3x2, etc.) with some void spaces. Grooves with beveled edges run between
 * pieces. Each piece has its own tilt and machining marks.
 */
function generateMotorNormalMap(width, height) {
  var cnv = document.createElement('canvas');
  cnv.width = width;
  cnv.height = height;
  var ctx = cnv.getContext('2d');
  var imageData = ctx.createImageData(width, height);
  var data = imageData.data;

  // ── Grid layout ──
  var cellSize = 45;
  var gridCols = Math.ceil(width / cellSize) + 1;
  var gridRows = Math.ceil(height / cellSize) + 1;
  var pieceMap = new Int32Array(gridCols * gridRows);
  // 0 = unassigned, -1 = void, positive = piece ID

  function cell(c, r) {
    if (c < 0 || c >= gridCols || r < 0 || r >= gridRows) return -1;
    return pieceMap[r * gridCols + c];
  }

  function canPlace(c, r, w, h) {
    for (var dr = 0; dr < h; dr++)
      for (var dc = 0; dc < w; dc++) {
        if (c + dc >= gridCols || r + dr >= gridRows) return false;
        if (pieceMap[(r + dr) * gridCols + (c + dc)] !== 0) return false;
      }
    return true;
  }

  function place(c, r, w, h, id) {
    for (var dr = 0; dr < h; dr++)
      for (var dc = 0; dc < w; dc++)
        pieceMap[(r + dr) * gridCols + (c + dc)] = id;
  }

  // ── Build mosaic — greedy placement with deterministic hash ──
  var nextId = 1;
  for (var row = 0; row < gridRows; row++) {
    for (var col = 0; col < gridCols; col++) {
      if (pieceMap[row * gridCols + col] !== 0) continue;

      var h = hashCell(col * 3 + 1, row * 3 + 1);

      if (h < 0.06) {
        pieceMap[row * gridCols + col] = -1;               // void
      } else if (h < 0.12 && canPlace(col, row, 3, 2)) {
        place(col, row, 3, 2, nextId++);
      } else if (h < 0.18 && canPlace(col, row, 2, 3)) {
        place(col, row, 2, 3, nextId++);
      } else if (h < 0.30 && canPlace(col, row, 2, 2)) {
        place(col, row, 2, 2, nextId++);
      } else if (h < 0.37 && canPlace(col, row, 3, 1)) {
        place(col, row, 3, 1, nextId++);
      } else if (h < 0.44 && canPlace(col, row, 1, 3)) {
        place(col, row, 1, 3, nextId++);
      } else if (h < 0.60 && canPlace(col, row, 2, 1)) {
        place(col, row, 2, 1, nextId++);
      } else if (h < 0.74 && canPlace(col, row, 1, 2)) {
        place(col, row, 1, 2, nextId++);
      } else {
        pieceMap[row * gridCols + col] = nextId++;          // 1x1
      }
    }
  }

  // ── Rendering parameters ──
  var grooveHalf = 2.0;       // half groove width
  var bevelW = 4.0;           // chamfer transition
  var bevelStrength = 0.70;

  // ── Render pixels ──
  for (var py = 0; py < height; py++) {
    for (var px = 0; px < width; px++) {
      var idx = (py * width + px) * 4;

      var col = Math.floor(px / cellSize);
      var row = Math.floor(py / cellSize);
      var lx = px - col * cellSize;
      var ly = py - row * cellSize;
      var pid = cell(col, row);

      var nx = 0, ny = 0, nz = 1;

      if (pid <= 0) {
        // ── Void: flat recessed surface ──
        var g = (Math.random() - 0.5) * 0.004;
        nx = g; ny = g;

      } else {
        // ── Find distances to piece boundaries ──
        var dxEdge = 9999, dyEdge = 9999;
        var edgeDirX = 0, edgeDirY = 0;

        // Left
        if (cell(col - 1, row) !== pid && lx < dxEdge) {
          dxEdge = lx; edgeDirX = -1;
        }
        // Right
        var distR = cellSize - lx;
        if (cell(col + 1, row) !== pid && distR < dxEdge) {
          dxEdge = distR; edgeDirX = 1;
        }
        // Top
        if (cell(col, row - 1) !== pid && ly < dyEdge) {
          dyEdge = ly; edgeDirY = -1;
        }
        // Bottom
        var distB = cellSize - ly;
        if (cell(col, row + 1) !== pid && distB < dyEdge) {
          dyEdge = distB; edgeDirY = 1;
        }

        if (dxEdge <= grooveHalf || dyEdge <= grooveHalf) {
          // ── In groove channel ──
          nx = 0; ny = 0;

        } else {
          // ── Bevel + panel interior ──
          var bvX = 0, bvY = 0;
          if (dxEdge < grooveHalf + bevelW) {
            var tv = 1 - (dxEdge - grooveHalf) / bevelW;
            bvX = tv * tv * (3 - 2 * tv) * bevelStrength;
          }
          if (dyEdge < grooveHalf + bevelW) {
            var th = 1 - (dyEdge - grooveHalf) / bevelW;
            bvY = th * th * (3 - 2 * th) * bevelStrength;
          }

          // Cap corner bevel
          var bMag = Math.sqrt(bvX * bvX + bvY * bvY);
          if (bMag > bevelStrength) {
            bvX *= bevelStrength / bMag;
            bvY *= bevelStrength / bMag;
          }

          // Per-piece tilt (deterministic from piece ID)
          var ph1 = hashCell(pid, pid * 3 + 7);
          var ph2 = hashCell(pid * 5 + 11, pid);
          var tiltX = (ph1 - 0.5) * 0.10;
          var tiltY = (ph2 - 0.5) * 0.10;

          // Cross-hatch machining marks
          var scratch1 = Math.sin((px - py) * 0.45) * 0.012;
          var scratch2 = Math.sin((px + py) * 0.45) * 0.008;
          var grain = (Math.random() - 0.5) * 0.008;

          // Bevel fades out face detail
          var faceWeight = 1.0 - Math.min(1.0, bMag / bevelStrength);
          nx = edgeDirX * bvX + (tiltX + scratch1 - scratch2 + grain) * faceWeight;
          ny = edgeDirY * bvY + (tiltY - scratch1 - scratch2 + grain) * faceWeight;
        }

        nz = Math.sqrt(Math.max(0.01, 1 - nx * nx - ny * ny));
      }

      data[idx]     = n2c(nx);
      data[idx + 1] = n2c(-ny);   // negate Y: canvas Y-down → WebGL Y-up
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

  // ── Lighting ──

  scene.add(new THREE.AmbientLight(0x191C22, 0.5));

  // Start dark — light turns on when powerOn() is called
  var mainLight = new THREE.PointLight(0x56b6c2, 0, 0, 1.0);
  mainLight.position.set(0, 0, 2.5);
  scene.add(mainLight);

  var fillLight = new THREE.DirectionalLight(0x6677aa, 0);
  fillLight.position.set(-1, -0.5, 0.5);
  scene.add(fillLight);

  var targetFillIntensity = 0;

  // ── Hover color transitions ──

  var defaultColor    = new THREE.Color(0x56b6c2);
  var hoverColor      = new THREE.Color(0x4a7fb5);
  var targetColor     = new THREE.Color(0x56b6c2);
  var defaultIntensity = 1.35;
  var hoverIntensity   = 2.7;
  var targetIntensity  = 0;

  // Power on — called from Kraft when the start button is clicked
  window.motorBackgroundPowerOn = function () {
    targetIntensity = defaultIntensity;
    targetFillIntensity = 0.12;
  };

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
    mainLight.intensity += (targetIntensity - mainLight.intensity) * 0.02;
    fillLight.intensity += (targetFillIntensity - fillLight.intensity) * 0.02;

    renderer.render(scene, camera);
  }

  animate();
}

// Expose globally so Kraft can call it after mounting the canvas
window.initMotorBackground = initMotorBackground;
