(ns jackdaw.client
  "Clojure wrapper to kafka consumers/producers"
  (:import [org.apache.kafka.clients.consumer Consumer ConsumerRecord KafkaConsumer]
           [org.apache.kafka.clients.producer Callback KafkaProducer ProducerRecord RecordMetadata Producer]
           org.apache.kafka.common.serialization.Serde
           org.apache.kafka.common.TopicPartition))

(set! *warn-on-reflection* true)

(defn producer-record
  "Creates a kafka ProducerRecord for use with `send!`."
  ([{:keys [jackdaw.topic/topic-name]} value] (ProducerRecord. topic-name value))
  ([{:keys [jackdaw.topic/topic-name]} key value] (ProducerRecord. topic-name key value))
  ([{:keys [jackdaw.topic/topic-name]} partition key value] (ProducerRecord. topic-name partition key value))
  ([{:keys [jackdaw.topic/topic-name]} partition timestamp key value] (ProducerRecord. topic-name partition timestamp key value)))

(defn topic-partition
  "Return a TopicPartition"
  [{:keys [:jackdaw.topic/topic-name] :as topic-config} partition]
  (TopicPartition. topic-name (int partition)))

(defn producer
  "Return a KafkaProducer with the supplied properties"
  ([config]
   (let [props (java.util.Properties.)]
     (.putAll props config)
     (KafkaProducer. props)))

  ([config {:keys [jackdaw.serdes/key-serde jackdaw.serdes/value-serde]}]
   (let [props (java.util.Properties.)]
     (.putAll props config)
     (KafkaProducer. props
                     (.serializer ^Serde key-serde)
                     (.serializer ^Serde value-serde)))))

(defn record-metadata
  "Clojurizes an org.apache.kafka.clients.producer.RecordMetadata."
  [^RecordMetadata record-metadata]
  (when record-metadata
    {:checksum (.checksum record-metadata)
     :offset (.offset record-metadata)
     :partition (.partition record-metadata)
     :serialized-key-size (.serializedKeySize record-metadata)
     :serialized-value-size (.serializedValueSize record-metadata)
     :timestamp (.timestamp record-metadata)
     :topic (.topic record-metadata)}))

(defn callback
  "Build a kafka producer callback function out of a normal clojure one
   The function should expect two parameters, the first being the record
   metadata, the second being an exception if there was one. The function
   should check for an exception and handle it appropriately."
  [on-completion]
  (reify Callback
    (onCompletion [this record-meta exception]
      (on-completion (record-metadata record-meta) exception))))

(defn send!
  "Asynchronously sends a record to a topic, returning a Future. A callback function
  can be optionally provided that should expect two parameters: a map of the
  record metadata, and an exception instance, if an error occurred."
  ([producer record]
   (.send ^Producer producer record))
  ([producer record callback-fn]
   (.send ^Producer producer record (callback callback-fn))))

(defn consumer
  "Return a Consumer with the supplied properties."
  ([config]
   (let [props (java.util.Properties.)]
     (.putAll props config)
     (KafkaConsumer. props)))
  ([config {:keys [jackdaw.serdes/key-serde jackdaw.serdes/value-serde]}]
   (let [props (java.util.Properties.)]
     (.putAll props config)
     (KafkaConsumer. props
                     (when key-serde (.deserializer ^Serde key-serde))
                     (when value-serde (.deserializer ^Serde value-serde))))))

(defn subscribe
  "Subscribe a consumer to topics. Returns the consumer."
  [consumer & topic-configs]
  (.subscribe ^Consumer consumer (mapv :jackdaw.topic/topic-name topic-configs))
  consumer)

(defn subscribed-consumer
  "Returns a consumer that is subscribed to a single topic."
  [config topic-config]
  (-> (consumer config topic-config)
      (subscribe topic-config)))

(defn- consumer-record
  "Clojurize the ConsumerRecord returned from consuming a kafka record"
  [^ConsumerRecord consumer-record]
  (when consumer-record
    {:checksum (.checksum consumer-record)
     :key (.key consumer-record)
     :offset (.offset consumer-record)
     :partition (.partition consumer-record)
     :serializedKeySize (.serializedKeySize consumer-record)
     :serializedValueSize (.serializedValueSize consumer-record)
     :timestamp (.timestamp consumer-record)
     :topic (.topic consumer-record)
     :value (.value consumer-record)}))

(defn poll
  "Polls kafka for new messages."
  [^Consumer consumer timeout]
  (mapv consumer-record (.poll consumer timeout)))
