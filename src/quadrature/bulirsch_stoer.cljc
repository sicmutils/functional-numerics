(ns quadrature.bulirsch-stoer
  (:require [quadrature.interpolate.polynomial :as poly]
            [quadrature.interpolate.rational :as rat]
            [quadrature.common :as qc
             #?@(:cljs [:include-macros true])]
            [quadrature.midpoint :as mid]
            [quadrature.trapezoid :as trap]
            [sicmutils.generic :as g]
            [sicmutils.util :as u]
            [quadrature.util.stream :as us]))

(def bulirsch-stoer-steps
  (interleave
   (us/powers 2 2)
   (us/powers 2 3)))

(defn- slice-width [a b]
  (let [width (- b a)]
    (fn [n] (/ width n))))

(defn- h-sequence
  "Defines the sequence of slice widths, given a sequence of `n` (number of
  slices) in the interval $(a, b)$."
  ([a b] (h-sequence a b bulirsch-stoer-steps))
  ([a b n-seq]
   (map (slice-width a b) n-seq)))

(defn- extrapolator-fn
  "Allows the user to specify polynomial or rational function extrapolation via
  the `:bs-extrapolator` option."
  [opts]
  (if (= :polynomial (:bs-extrapolator opts))
    poly/modified-neville
    rat/modified-bulirsch-stoer))

(defn- bs-sequence-fn
  "Accepts some function (like `mid/midpoint-sequence`) that returns a sequence of
  successively better estimates to the integral, and returns a new function with
  interface `(f a b opts)` that accelerates the sequence with either

  - polynomial extrapolation
  - rational function extrapolation

  By default, The `:n` in `opts` (passed on to `integrator-seq-fn`) is set to
  the sequence of step sizes suggested by Bulirsch-Stoer,
  `bulirsch-stoer-steps`."
  [integrator-seq-fn]
  (fn call
    ([f a b]
     (call f a b {:n bulirsch-stoer-steps}))
    ([f a b opts]
     {:pre [(not (number? (:n opts)))]}
     (let [{:keys [n] :as opts} (-> {:n bulirsch-stoer-steps}
                                    (merge opts))
           extrapolate (extrapolator-fn opts)
           square      (fn [x] (* x x))
           xs          (map square (h-sequence a b n))
           ys          (integrator-seq-fn f a b opts)]
       (-> (map vector xs ys)
           (extrapolate 0))))))

(def ^{:doc "Returns a (lazy) sequence of successively refined estimates of the
  integral of `f` over the closed interval $[a, b]$ by applying rational
  polynomial extrapolation to successive integral estimates from the Midpoint
  rule.

  Returns estimates formed from the same estimates used by the Bulirsch-Stoer
  ODE solver, stored in `bulirsch-stoer-steps`.

  ## Optional arguments:

  `:n`: If supplied, `n` (sequence) overrides the sequence of steps to use.

  `:bs-extrapolator`: Pass `:polynomial` to override the default rational
  function extrapolation and enable polynomial extrapolation using the modified
  Neville's algorithm implemented in `poly/modified-neville`."}
  open-sequence
  (bs-sequence-fn mid/midpoint-sequence))

(def ^{:doc "Returns a (lazy) sequence of successively refined estimates of the
  integral of `f` over the closed interval $[a, b]$ by applying rational
  polynomial extrapolation to successive integral estimates from the Trapezoid
  rule.

  Returns estimates formed from the same estimates used by the Bulirsch-Stoer
  ODE solver, stored in `bulirsch-stoer-steps`.

  ## Optional arguments:

  `:n`: If supplied, `:n` (sequence) overrides the sequence of steps to use.

 `:bs-extrapolator`: Pass `:polynomial` to override the default rational
  function extrapolation and enable polynomial extrapolation using the modified
  Neville's algorithm implemented in `poly/modified-neville`."}
  closed-sequence
  (bs-sequence-fn trap/trapezoid-sequence))

(qc/defintegrator open-integral
  "Returns an estimate of the integral of `f` over the open interval $(a, b)$
  generated by applying rational polynomial extrapolation to successive integral
  estimates from the Midpoint rule.

  Considers successive numbers of windows into $(a, b)$ specified by
  `bulirsch-stoer-steps`.

  Optionally accepts `opts`, a dict of optional arguments. All of these get
  passed on to `us/seq-limit` to configure convergence checking.

  See `open-sequence` for more information about Bulirsch-Stoer quadrature,
  caveats that might apply when using this integration method and information on
  the optional args in `opts` that customize this function's behavior."
  :area-fn mid/single-midpoint
  :seq-fn open-sequence)

(qc/defintegrator closed-integral
  "Returns an estimate of the integral of `f` over the closed interval $[a, b]$
  generated by applying rational polynomial extrapolation to successive integral
  estimates from the Trapezoid rule.

  Considers successive numbers of windows into $[a, b]$ specified by
  `bulirsch-stoer-steps`.

  Optionally accepts `opts`, a dict of optional arguments. All of these get
  passed on to `us/seq-limit` to configure convergence checking.

  See `closed-sequence` for more information about Bulirsch-Stoer quadrature,
  caveats that might apply when using this integration method and information on
  the optional args in `opts` that customize this function's behavior."
  :area-fn trap/single-trapezoid
  :seq-fn closed-sequence)
