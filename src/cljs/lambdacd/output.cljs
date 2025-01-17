(ns lambdacd.output
  (:require [lambdacd.state :as state]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
            [clojure.string :as s]
            [lambdacd.console-output-processor :as console-output-processor]))

(defn negate [a]
  (fn [& _ ]
    (swap! a not)
    nil))

(defn- raw-step-results-component [visible result]
  (if visible
    [:pre {:class "step-results__raw-step-results"} (js/JSON.stringify (clj->js result) nil 2)]
    [:pre]))

(declare details-component)

(defn- detail-component [detail]
  [:li {:key (:label detail)}
   (cond
     (:href detail) [:a {:href (:href detail)
                         :target "_blank"} (:label detail)]
     (:raw detail) (list
                     [:span (:label detail)]
                     [:pre (:raw detail)])
     :else [:span (:label detail)])
   (if (:details detail)
     (details-component (:details detail)))])

(defn- details-component [details]
  [:ul (map detail-component details)])

(defn- details-wrapper-component [result]
  (if (:details result)
    [:div {:class "details-container"}
     [:h3 "Details"]
     (details-component (:details result))]))

(defn- scroll-to-bottom []
  (js/window.scrollTo 0 js/document.body.scrollHeight ))

(defn- is-scrolled-to-bottom []
  (let [delta (- (- js/document.body.scrollHeight js/document.body.scrollTop) js/document.body.clientHeight)
        is-bottom (< delta 20)]
    is-bottom))

(defn scroll-wrapper [component]
  (let [scrolled-to-bottom-before-update (atom false)
        before-update #(reset! scrolled-to-bottom-before-update (is-scrolled-to-bottom))
        after-update #(if @scrolled-to-bottom-before-update (scroll-to-bottom))
        wrapped-component (with-meta component
                             {:component-will-mount before-update
                              :component-will-update before-update
                              :component-did-update after-update
                              :component-did-mount after-update})]
    [wrapped-component]))

(defn status-to-string [status]
  (s/upper-case status))

(defn- still-active? [status]
  (or (nil? status) (= status "running") (= status "waiting")))

(defn- received-kill? [result]
  (and
    (:received-kill result)
    (not= "killed" (:status result))))

(defn- processed-kill? [result]
  (and
    (:processed-kill result)
    (not= "killed" (:status result))))

(defn- enhanced-output [{status :status output :out :as result}]
  (cond
    (processed-kill? result) (str output "\n\n" "Step received kill and is shutting down...")
    (received-kill? result) (str output "\n\n" "LambdaCD received kill and waiting for build step to react to it...")
    (still-active? status) output
    :else (str output "\n\n" "Step is finished: " (status-to-string status))))

(defn- console-component [output]
  (let [lines (console-output-processor/process-ascii-escape-characters output)]
    [:div {:class "console-output"}
     (for [line lines]
       [:pre {:class "console-output__line"} line])]))

(defn- plain-output-component [step raw-step-results-visible]
  (let [result (:result step)]
    [:div {:class "results"}
     [details-wrapper-component result]
     [:h3 "Complete Step Result"]
     [:button {:on-click #(re-frame/dispatch [::db/toggle-raw-step-results-visible])} (if raw-step-results-visible "hide" "show")]
     [raw-step-results-component raw-step-results-visible result]
     (if (not (nil? (:out result)))
       [:div
        [:h3 "Console Output"]
        (console-component (enhanced-output result))])]))

(defn output-renderer [current-step-result raw-step-results-visible]
  (if current-step-result
    (scroll-wrapper (partial plain-output-component current-step-result raw-step-results-visible))
    [:pre {:key "build-output"} "Click on a build step to display details."]))

(defn output-component []
  (let [current-step-result (re-frame/subscribe [::db/current-step-result])
        raw-step-results-visible (re-frame/subscribe [::db/raw-step-results-visible])]
    (fn []
      [output-renderer @current-step-result @raw-step-results-visible])))