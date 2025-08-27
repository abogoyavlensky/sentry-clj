(ns sentry-clj.tracing
  (:import
   [io.sentry CustomSamplingContext EventProcessor ITransaction Scope Sentry SpanStatus TransactionOptions]))

(def span-status
  {:ok SpanStatus/OK
   :cancel SpanStatus/CANCELLED
   :internal-error SpanStatus/INTERNAL_ERROR
   :unknown SpanStatus/UNKNOWN
   :unknown-error SpanStatus/UNKNOWN_ERROR
   :invalid-argument SpanStatus/INVALID_ARGUMENT
   :deadline-exceeded SpanStatus/DEADLINE_EXCEEDED
   :not-found SpanStatus/NOT_FOUND
   :already-exists SpanStatus/ALREADY_EXISTS
   :permisson-denied SpanStatus/PERMISSION_DENIED
   :resource-exhaused SpanStatus/RESOURCE_EXHAUSTED
   :fail-precondition SpanStatus/FAILED_PRECONDITION
   :aborted SpanStatus/ABORTED
   :out-of-range SpanStatus/OUT_OF_RANGE
   :unimplemented SpanStatus/UNIMPLEMENTED
   :unavailable SpanStatus/UNAVAILABLE
   :data-loss SpanStatus/DATA_LOSS
   :unauthenticated SpanStatus/UNAUTHENTICATED})

(defn compute-custom-sampling-context
  "Compute a custom sampling context has key and info."
  ^CustomSamplingContext
  [key info]
  (let [csc (CustomSamplingContext.)]
    (.set csc key info)
    csc))

(defn start-transaction
  "Start tracing transactions.
   If a sentry-trace-header is given, connect the existing transaction."
  [name custom-sampling-context sentry-trace-header]
  (let [transaction-options (doto (TransactionOptions.) (.setBindToScope true) (.setCustomSamplingContext ^CustomSamplingContext custom-sampling-context))]
    (if sentry-trace-header
      (let [transactionContext (Sentry/continueTrace (.getValue (io.sentry.SentryTraceHeader. sentry-trace-header)) nil)]
        (-> (Sentry/getCurrentScopes)
            (.startTransaction transactionContext ^TransactionOptions transaction-options)))
      (-> (Sentry/getCurrentScopes)
          (.startTransaction ^String name "http.server" ^TransactionOptions transaction-options)))))

(defn swap-scope-request!
  "Set request info to the scope."
  [^Scope scope req]
  (.setRequest scope req))

(defn add-event-processor
  "Add Event Processor to the scope.
   event-processor is executed when tracing transaction finish or capture error event."
  [^Scope scope ^EventProcessor event-processor]
  (.addEventProcessor scope event-processor))

(defn swap-transaction-status!
  "Set trace transaction status."
  [^ITransaction transaction status]
  (.setStatus transaction status))

(defn finish-transaction!
  "Finish trace transaction and send event to Sentry."
  [^ITransaction transaction]
  (.finish transaction))

(defmacro with-start-child-span
  "Start a child span which has the operation or description
   and finish after evaluating forms."
  [operation description & forms]
  `(if-let [sp# (Sentry/getSpan)]
     (let [inner-sp# (.startChild sp# ~operation ~description)]
       (try
         (let [result# (do ~@forms)]
           (.setStatus inner-sp# SpanStatus/OK)
           result#)
         (catch Throwable e#
           (.setThrowable inner-sp# e#)
           (.setStatus inner-sp# SpanStatus/INTERNAL_ERROR)
           (throw e#))
         (finally
           (.finish inner-sp#))))
     (do ~@forms)))
