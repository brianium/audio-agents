(ns viewer
  (:require [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]
           [javax.swing JFrame JLabel ImageIcon]
           [java.awt.image BufferedImage]
           [java.awt Dimension]))

(defn show-image-from-classpath
  [image-path]
  (let [image-stream (io/input-stream image-path)
        buffered-image (ImageIO/read image-stream)
        icon (ImageIcon. buffered-image)
        label (JLabel. icon)
        frame (doto (JFrame. "Image Viewer")
                (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
                (.add label)
                (.pack)
                (.setVisible true))]
    (.setPreferredSize frame (Dimension. (.getWidth buffered-image) (.getHeight buffered-image)))))
