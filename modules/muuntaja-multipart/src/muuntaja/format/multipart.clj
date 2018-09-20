(ns muuntaja.format.multipart
  (:refer-clojure :exclude [format])
  (:require [muuntaja.format.core :as core]
            [clojure.java.io :as io])
  (:import [java.io PipedInputStream PipedOutputStream]
           [org.synchronoss.cloud.nio.multipart
            Multipart MultipartContext MultipartUtils
            NioMultipartParserListener DefaultPartBodyStreamStorageFactory
            PartBodyStreamStorageFactory]
           [org.synchronoss.cloud.nio.stream.storage
            StreamStorage StreamStorageFactory]))

(defn stream-storage []
  (reify PartBodyStreamStorageFactory
    (newStreamStorageForPartBody [this headers partIndex]
      (let [is (PipedInputStream.)
            os (PipedOutputStream.)]
        (.connect is os)
        (proxy [StreamStorage] []
          (write
            ([b]
             (.write os b))
            ([b off len]
             (.write os b off len)))
          (close []
            (.close os))
          (flush []
            nil)
          (getInputStream []
            is))))))

(defn decoder [{:keys [store fallback-encoding encoding
                       ;; nio-multipart options
                       buffer-size headers-size-limit
                       max-memory-usage-per-body-part
                       limit-nesting-parts-to]
                :as options}]
  (reify
    core/Decode
    (decode [this data charset]
      ;; FIXME: This needs the request map to get proper content-type and length
      (let [store (or store identity)
            context (MultipartContext. "multipart/form-data; boundary=XXXX" 10000 (or encoding
                                                                                      charset
                                                                                      fallback-encoding))
            result (atom {})
            listener (reify NioMultipartParserListener
                       (onPartFinished [this partBodyStreamStorage, headersFromPart]
                         (let [fieldName (MultipartUtils/getFieldName headersFromPart)]
                           (let [data (if (MultipartUtils/isFormField headersFromPart context)
                                        (slurp (.getInputStream partBodyStreamStorage) :encoding (or encoding
                                                                                                     (MultipartUtils/getCharEncoding headersFromPart)
                                                                                                     fallback-encoding))
                                        ;; TODO: If content-type is known by Muuntaja, parse the data.
                                        ;; FIXME: needs the muuntaja instance.
                                        (store {:content-type (MultipartUtils/getContentType headersFromPart)
                                                :filename (MultipartUtils/getFileName headersFromPart)
                                                :stream (.getInputStream partBodyStreamStorage)}))]
                             (swap! result update fieldName (fn [v]
                                                              ;; If result already contains same key,
                                                              ;; append to vec to vector or convert the value to vector.
                                                              (if v
                                                                (if (vector? v)
                                                                  (conj v data)
                                                                  [v data])
                                                                data))))))
                       (onNestedPartStarted [this headersFromParentPart]
                         )
                       (onNestedPartFinished [this]
                         )
                       (onAllPartsFinished [this]
                         )
                       (onError [this message cause]
                         ))
            parser (-> (Multipart/multipart context)
                       (doto (cond->
                               buffer-size (.setBufferSize buffer-size)
                               headers-size-limit (.setHeadersSizeLimit headers-size-limit)
                               max-memory-usage-per-body-part (.setMaxMemoryUsagePerBodyPart max-memory-usage-per-body-part)
                               limit-nesting-parts-to (.setLimitNestingPartsTo limit-nesting-parts-to)
                               store (.usePartBodyStreamStorageFactory (stream-storage))))
                       (.forNIO listener))]
        (io/copy data parser)
        @result))))

(def format
  (core/map->Format
    {:name "multipart/form-data"
     :decoder [decoder]}))
