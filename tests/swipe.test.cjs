const {test} = require('node:test');
const assert = require('node:assert/strict');
require('../app/src/main/assets/swipe.js');
function run(points, params = {}, cancel = false) {
  const g = Swipe.start(...points[0], params);
  points.slice(1).forEach(p => Swipe.move(g, ...p));
  return Swipe.result(g, 600, cancel);
}
test('short upward flick turns next', () => {
  const r = run([[100, 300, 0], [102, 280, 20], [103, 260, 50]]);
  assert.equal(r.complete, true); assert.equal(r.direction, 1);
});
test('slow drag uses distance threshold', () => {
  assert.equal(run([[100, 300, 0], [100, 150, 1000]]).complete, true);
  assert.equal(run([[100, 300, 0], [100, 200, 1000]]).complete, false);
});
test('pause at release does not reuse stale flick velocity', () => {
  assert.equal(run([[100, 300, 0], [100, 270, 20], [100, 270, 400]]).complete, false);
});
test('cancel never commits, even beyond threshold', () => {
  assert.equal(run([[100, 300, 0], [100, 100, 50]], {}, true).complete, false);
});
test('horizontal starts and jitter do not turn', () => {
  assert.equal(run([[100, 300, 0], [140, 280, 20], [140, 100, 50]]).complete, false);
  assert.equal(run([[100, 300, 0], [102, 298, 1]]).complete, false);
});
test('downward swipe and reversed flick', () => {
  assert.equal(run([[100, 100, 0], [100, 260, 500]]).direction, -1);
  assert.equal(run([[100, 300, 0], [100, 270, 50], [100, 310, 90]]).complete, false);
});
test('parameters change classification and are snapshotted', () => {
  const points = [[100, 300, 0], [100, 200, 500]];
  assert.equal(run(points).complete, false);
  assert.equal(run(points, {distance: .1}).complete, true);
  const p = {slop: 10}; const g = Swipe.start(0, 0, 0, p); p.slop = 20;
  assert.equal(g.params.slop, 10);
  assert.equal(Swipe.config({velocity: NaN}).velocity, .4);
});

// Exercise the reader's actual touch listeners as well as the pure classifier.
function readerHarness() {
  const vm = require('node:vm'), fs = require('node:fs');
  const handlers = {}, results = [], finishes = [];
  const ctx = { Swipe, window: {swipePlayground: true}, performance: {now: () => ctx.now}, now: 0,
    viewport: {addEventListener: (name, fn) => handlers[name] = fn},
    A: {onGesture: json => results.push(JSON.parse(json))},
    page: 2, pages: 10, W: 360, H: 600, fold: null, suppressClickUntil: 0,
    beginFold: () => { ctx.fold = {}; return true; }, setTheta: () => {},
    finishFold: complete => { finishes.push(complete); ctx.fold = null; }
  };
  const source = fs.readFileSync(require.resolve('../app/src/main/assets/reader.js'), 'utf8');
  vm.runInNewContext(source.slice(source.indexOf('    var drag = null, swipeParams'), source.indexOf('    // taps:')), ctx);
  function event(type, y, time, fingers = 1) {
    ctx.now = time;
    const point = {clientX: 100, clientY: y};
    handlers[type]({type, touches: Array(fingers).fill(point), changedTouches: [point], target: {closest: () => null}, preventDefault() {}});
  }
  return {event, results, finishes};
}
test('reader touchcancel rolls back an active fold', () => {
  const h = readerHarness();
  h.event('touchstart', 300, 0); h.event('touchmove', 100, 50); h.event('touchcancel', 100, 60);
  assert.deepEqual(h.finishes, [false]); assert.equal(h.results[0].outcome, 'none');
});
test('adding a second finger cancels without leaving a stuck fold', () => {
  const h = readerHarness();
  h.event('touchstart', 300, 0); h.event('touchmove', 200, 50); h.event('touchstart', 200, 60, 2);
  h.event('touchend', 200, 80, 0);
  assert.deepEqual(h.finishes, [false]); assert.equal(h.results.length, 1);
});
test('release-only flicks still turn and report the outcome', () => {
  const h = readerHarness();
  h.event('touchstart', 300, 0); h.event('touchend', 260, 50, 0);
  assert.deepEqual(h.finishes, [true]); assert.equal(h.results[0].outcome, 'next');
});
