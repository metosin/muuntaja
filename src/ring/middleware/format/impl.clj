(ns ring.middleware.format.impl)

(defn extract-options [args]
  (if (map? (first args))
    (do
      (assert (empty? (rest args)) "When using options map arity, middlewares take only one parameter.")
      (first args))
    (do
      (assert (even? (count args)) "When using keyword args arity, there should be even number of parameters.")
      (apply hash-map args))))
