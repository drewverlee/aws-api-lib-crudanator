(ns drewverlee.aws-api-lib-crudanator
  "The Crudanator handles all your C.R.U.D needs by letting you
  Create
  READ
  UPDATE
  DESTROY (really remove)
  aws-api libs"
  (:require
   [com.cognitect.aws :as-alias aws]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn is-v1-newer-then-v2?
  "takes some dot spatted shanagins and finds out of the first one is newer then the second"
  [v1 v2]
  (loop [[v1 & v1_rest] (str/split v1 #"\.")
         [v2 & v2_rest] (str/split v2 #"\.")]
    (cond
      (not (and v1 v2)) false
      (> (Integer. v1) (Integer. v2)) true
      :else (recur v1_rest v2_rest))))

(defn get-diff [current-libs latest-libs]
  (reduce-kv
   (fn [lib->new+old lib {latest-version :mvn/version}]
     (if-let [{current-version :mvn/version} (lib current-libs)]
       (if (is-v1-newer-then-v2? latest-version current-version)
         (assoc lib->new+old (name lib) {:new latest-version :old current-version})
         lib->new+old)
       lib->new+old))
   {}
   latest-libs))

(defn get-current-aws-lib-versions!
  ([] (get-current-aws-lib-versions! "deps.edn"))
  ([path]
   (->> path
        slurp
        edn/read-string
        :deps
        (reduce-kv
         (fn [aws-lib->v lib v]
           (let [is-aws-lib? #(str/includes? (str %) "com.cognitect.aws")]
             (if (is-aws-lib? lib)
               (assoc aws-lib->v lib v)
               aws-lib->v)))
         {}))))

(defn get-latest-aws-libs-versions!
  []
  (-> "https://raw.githubusercontent.com/cognitect-labs/aws-api/main/latest-releases.edn"
      slurp
      edn/read-string))

(defn update-deps-with-latest-aws-versions!
  [{:keys [from to lib-name->new-version] :or {from "deps.edn" to "deps.edn"}} ]
  ;;TODO maybe don't hard code this
  (let [deps-top (-> from slurp edn/read-string)]
    (spit to (pr-str
                   (assoc deps-top :deps
                          (reduce-kv
                           (fn [new-current-libs lib-str-name {:keys [new]}]
                             (assoc-in new-current-libs [(->> lib-str-name (str "com.cognitect.aws/") symbol) :mvn/version] new))
                           (:deps deps-top)
                           lib-name->new-version))))))


(comment
  ;; to use the lib just
  ;; eval these two vars
  (def latest (get-latest-aws-libs-versions!))
  (def current (get-current-aws-lib-versions! "test/resources/mock-deps.edn"))

  ;; then
  (update-deps-with-latest-aws-versions!
   {:from "test/resources/mock-deps.edn"
    :to "test/resources/mock-deps-update.edn"
    :lib-name->new-version
    ;; then eval and replace this get-diff
    ;; to get this map which you should remove any deps you don't want to update
    (get-diff current latest)})

  nil)
