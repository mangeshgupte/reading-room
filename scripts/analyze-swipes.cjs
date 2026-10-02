#!/usr/bin/env node
// Replay exported, labelled traces through the same detector. No dependencies.
const fs = require('node:fs');
require('../app/src/main/assets/swipe.js');
const files = process.argv.slice(2);
if (!files.length) { console.error('Usage: node scripts/analyze-swipes.cjs feedback.json [...]'); process.exit(1); }
const trials = files.flatMap(f => JSON.parse(fs.readFileSync(f, 'utf8')).trials)
  .filter(t => ['next', 'previous', 'none'].includes(t.expected) && t.points?.length > 1);
if (!trials.length) { console.error('No labelled traces to analyze.'); process.exit(1); }
function score(params) {
  let misses = 0, accidental = 0, correct = 0;
  for (const t of trials) {
    const g = Swipe.start(...t.points[0], params || t.params);
    t.points.slice(1).forEach(p => Swipe.move(g, ...p));
    const r = Swipe.result(g, t.height, t.reason === 'cancelled');
    const blocked = t.reason === 'native scroller' ||
      (r.direction === 1 && t.page >= t.pages - 1) || (r.direction === -1 && t.page === 0);
    const actual = r.complete && !blocked ? (r.direction > 0 ? 'next' : 'previous') : 'none';
    if (actual === t.expected) correct++;
    else if (t.expected === 'none') accidental++;
    else misses++;
  }
  return { correct, misses, accidental, loss: misses + 2 * accidental, params };
}
const candidates = [];
for (const slop of [4, 6, 10, 14])
for (const dominance of [1, 1.3, 1.6])
for (const distance of [.1, .15, .214, .3])
for (const velocity of [.25, .4, .6, .9])
for (const windowMs of [50, 100, 150]) candidates.push(score({slop, dominance, distance, velocity, windowMs}));
candidates.sort((a, b) => a.loss - b.loss);
console.log(JSON.stringify({labelledTrials: trials.length, recordedSettings: score(null), defaults: score(Swipe.defaults), candidates: candidates.slice(0, 5)}, null, 2));
console.error('Candidates fit these traces only. Validate on a fresh session before changing reader defaults. Accidental turns carry twice the cost of misses.');
