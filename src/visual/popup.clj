(ns visual.popup
  (:require [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]
           [javax.swing JFrame JLabel ImageIcon]
           [java.awt Dimension]
           [java.awt.event WindowAdapter]))

(defn show-image
  "Display an image in a jframe"
  [^String image-path]
  (with-open [image-stream (io/input-stream image-path)]
    (let [buffered-image (ImageIO/read image-stream)
          icon (ImageIcon. buffered-image)
          label (JLabel. icon)
          frame (JFrame. "Image Viewer")]
      (doto frame
        (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
        (.addWindowListener
         (proxy [WindowAdapter] []
           (windowClosed [_]
             (future
               (Thread/sleep 100) ;; allow clean dispose
               (System/exit 0)))))
        (.add label)
        (.pack)
        (.setAlwaysOnTop true)
        (.setVisible true)
        (.toFront)
        (.requestFocus)
        (.setAlwaysOnTop false))
      (.setPreferredSize frame (Dimension. (.getWidth buffered-image) (.getHeight buffered-image))))))

(defn -main [& args]
  (if-let [image-path (first args)]
    (show-image image-path)
    (println "Usage: clojure -M -m visual.popup <image-path>")))
