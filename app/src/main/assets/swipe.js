/* Shared by the reader and Playground. Units: CSS px and milliseconds. */
(function (root) {
  var defaults = { slop: 6, dominance: 1, distance: 0.214, velocity: 0.4, windowMs: 100 };
  function config(input) {
    var p = {}, ranges = { slop: [2, 30], dominance: [1, 3], distance: [0.05, 0.5], velocity: [0.1, 2], windowMs: [30, 200] };
    Object.keys(defaults).forEach(function (k) {
      var n = input && input[k];
      p[k] = typeof n === 'number' && isFinite(n) ? Math.max(ranges[k][0], Math.min(ranges[k][1], n)) : defaults[k];
    });
    return p;
  }
  function start(x, y, t, params) { return { params: config(params), points: [[x, y, t]], dir: 0, rejected: false }; }
  function move(g, x, y, t) {
    g.points.push([x, y, t]);
    var first = g.points[0], dx = x - first[0], dy = y - first[1];
    if (!g.dir && !g.rejected && Math.max(Math.abs(dx), Math.abs(dy)) >= g.params.slop) {
      if (Math.abs(dy) >= Math.abs(dx) * g.params.dominance) g.dir = dy < 0 ? 1 : -1;
      else if (Math.abs(dx) > Math.abs(dy)) g.rejected = true;
    }
    return g.dir;
  }
  function result(g, height, cancelled) {
    var pts = g.points, first = pts[0], last = pts[pts.length - 1], sample = last;
    for (var i = pts.length - 2; i >= 0; i--) {
      if (last[2] - pts[i][2] > g.params.windowMs) break;
      sample = pts[i];
    }
    var distance = (first[1] - last[1]) * g.dir;
    var velocity = (sample[1] - last[1]) * g.dir / Math.max(1, last[2] - sample[2]);
    var complete = !cancelled && !!g.dir && distance >= g.params.slop &&
      (distance >= height * g.params.distance || velocity > g.params.velocity);
    return { complete: complete, direction: g.dir, distance: distance, velocity: velocity,
      duration: last[2] - first[2], height: height, params: g.params, points: pts,
      reason: cancelled ? 'cancelled' : g.rejected ? 'horizontal' : !g.dir ? 'below slop' : complete ? 'accepted' : 'below distance / speed' };
  }
  root.Swipe = { defaults: defaults, config: config, start: start, move: move, result: result };
})(typeof window === 'undefined' ? globalThis : window);
