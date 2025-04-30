(ns visual.viewer)

(defn launch-image-viewer [image-path]
  (let [process-builder (ProcessBuilder.
                         ["clojure" "-M" "-m" "visual.popup" image-path])]
    (.inheritIO process-builder) ; optional: allows logging from subprocess
    (.start process-builder)))
