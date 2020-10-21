;;
;; Copyright © 2017 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.numerical.unimin.bracket-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [same :refer [zeroish? ish? with-comparator]
             #?@(:cljs [:include-macros true])]
            [sicmutils.calculus.derivative :refer [D]]
            [sicmutils.numerical.interpolate.polynomial :as ip]
            [sicmutils.function]
            [sicmutils.generic :as g]
            [sicmutils.value :as v]
            [sicmutils.numerical.unimin.bracket :as b]))

(def ^:private near (v/within 1e-8))

(def point
  ;; Generator of [x, (f x)] points.
  (gen/tuple gen/small-integer gen/small-integer))

(deftest ascending-by-tests
  (checking "ascending-by always returns a sorted pair."
            100
            [a gen/small-integer
             b gen/small-integer]
            (let [[[a fa] [b fb]] (b/ascending-by g/square a b)]
              (is (>= (g/abs b) (g/abs a)))
              (is (>= fb fa))
              (is (= (g/square a) fa))
              (is (= (g/square b) fb)))))

(deftest parabolic-minimum-tests
  (checking "The point generated by `parabolic-step` lives at the minimum of the
            lagrange-interpolating polynomial"
            100
            [[a b c] (gen/list-distinct-by first point {:num-elements 3})]
            (let [[p q] (b/parabolic-pieces a b c)
                  f'    (D (fn [x] (ip/lagrange [a b c] x)))]
              (is (or (zero? q)
                      (near 0.0 (f' (b/parabolic-step a b c)))))))

  (checking "The step implied by `parabolic-pieces` would take x to the
            parabolic minimum."
            100
            [[[xa :as a] [xb :as b] [xc :as c]] (gen/list-distinct-by first point {:num-elements 3})]
            (let [[p q] (b/parabolic-pieces a b c)
                  f' (D (fn [x] (ip/lagrange [a b c] x)))]
              (if (zero? q)
                (is (and (= (f' xa) (f' xb) (f' xc)))
                    "If the step's denominator is 0, the points are colinear.")
                (is (near 0.0 (f' (+ xb (/ p q))))
                    "Otherwise, `parabolic-pieces` returns the offset from the
                    middle point to the minimum.")))))

(defn quadratic-bracket [minimize maximize suffix]
  (checking (str "bracket-{min,max}" suffix " properly brackets a quadratic")
            100
            [lower gen/large-integer
             upper  gen/large-integer
             offset gen/small-integer]
            (let [upper (if (= lower upper) (inc lower) upper)
                  min-f (fn [x] (g/square (- x offset)))]
              (testing "bracket-min"
                (let [{:keys [lo hi fncalls iterations]} (minimize min-f {:xa lower :xb upper})]
                  (is (<= (first lo) offset)
                      (str "bracket-min" suffix " lower bound is <= argmin."))
                  (is (>= (first hi) offset)
                      (str "bracket-min" suffix " upper bound is >= argmin."))))

              (let [max-f (comp g/negate min-f)
                    {:keys [lo hi]} (maximize max-f {:xa lower :xb upper})]
                (is (<= (first lo) offset)
                    (str "bracket-max" suffix " lower bound is <= argmax."))
                (is (>= (first hi) offset)
                    (str "bracket-max" suffix " upper bound is >= argmax."))))))

(deftest bracket-tests
  (quadratic-bracket b/bracket-min b/bracket-max "")

  (testing "cubic-from-java"
    (let [min-f (fn [x]
                  (if (< x -2)
                    -2
                    (* (- x 1)
                       (+ x 2)
                       (+ x 3))))
          max-f (comp g/negate min-f)
          expected {:lo [-2 0]
                    :mid [-1 -4]
                    :hi [0.6180339887498949 -3.6180339887498945]
                    :fncalls 3
                    :iterations 0}]
      (is (ish? expected (b/bracket-min min-f {:xa -2 :xb -1})))
      (is (ish? expected (b/bracket-max max-f {:xa -2 :xb -1}))))))

(deftest scmutils-bracket-tests
  (quadratic-bracket b/bracket-min-scmutils b/bracket-max-scmutils "-scmutils")

  (testing "bracket"
    (is (ish? {:lo [-46 2116]
               :mid [-12 144]
               :hi [43 1849]
               :fncalls 11
               :converged? true
               :iterations 8}
              (b/bracket-min-scmutils g/square {:start -100 :step 1})))))