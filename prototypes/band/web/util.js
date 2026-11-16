/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The few numeric helpers everything shares.

/** 0 below 0, 1 above 1, and a soft S between. */
export const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };

/** A number from 0 to 1 that belongs to [arg], always the same one: the chance behind every seeded choice. */
export const hash01 = (arg) => { const x = Math.sin(arg) * 43758.5453; return x - Math.floor(x); };

/** Smooth noise along [x], 0 to 1: the value [at] gives at every whole number, eased between them. */
export function valueNoise(x, at) {
  const n = Math.floor(x), u = x - n;
  return at(n) + (at(n + 1) - at(n)) * (u * u * (3 - 2 * u));
}
