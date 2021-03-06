(ns client
    (:use
      [postal.core]
      [clojure.data.json :only (json-str write-json read-json)]
      [clj-stacktrace.core])
    (:require 
      [net.cgrand.enlive-html :as html]
      [necessary-evil.core :as xml-rpc]
      [clj-http.client :as client])
  (:gen-class))
  

(defn != [a b] (not (= a b)))

(defn href [obj] 
  (let [href (:href (:attrs (first obj)))]
    (if 
      (and 
        (string? href)
        (not (.contains href "http://")))

      (do 
        ;(print (str "http://www.gamespot.com" href))
        (str "http://www.gamespot.com" href))

      (do 
        ;(print href)
        href))))

(defn istype? [obj t]
  (= (type obj) t))

(defn sean-email [body]
  (send-message #^{:host "smtp.gmail.com"
                   :user "sean1@seanneilan.com"
                   :pass "shimon88"
                   :ssl true}
                   {:from "sean1@seanneilan.com"
             :to  "sean@seanneilan.com"
             :subject "Oh no!"
             :body body}))

; renders an html tree from enlive
(defn render [html-tree]
  (cond 
    (istype? html-tree clojure.lang.LazySeq) (apply str (html/emit* (:content (first html-tree)))) 
    true (apply str (html/emit* html-tree))))

(defn render-all [html-tree]
  (cond 
    (istype? html-tree clojure.lang.LazySeq) (apply str (html/emit* (:content html-tree)))
    true (apply str (html/emit* html-tree))))


;ye-ah
(defn get-some [url]
  (try 
    (do
      ;(println url)
      (. Thread (sleep 3000))
      (html/html-resource (java.io.StringReader. (:body (client/get url)))))
    (catch Exception e
      () ; return an empty set if we got a 404 or 500 or whatever
      )))


(defn post-some [url]
  (html/html-resource (java.io.StringReader. (:body (client/post url)))))


(def url "http://www.gamespot.com/final-fantasy-xiii-2/platform/xbox360/")
(def user-review-url-1 "http://www.gamespot.com/final-fantasy-xiii-2/user-reviews/786969/platform/xbox360/")
(def gs-review-url-1 "http://www.gamespot.com/final-fantasy-xiii-2/reviews/final-fantasy-xiii-2-review-6349372/")

(defn sean-replace [string & replacements]
 (loop [p replacements
        s string]
   (if (not-empty p)
     (recur 
       (-> p rest rest)
       (.replace s (first p) (second p)))
     s)))

(comment
 


(def json (read-json (slurp "yar.json"))) 
(def headers (eval (flatten (cons 'hash-map (reduce concat (map #(list (:name %1) (:value %1)) (:headers (:request json)))))))) 


(defn try-loading-it [filename]
  (let [json (read-json (slurp filename))
        url (:url (:request json))
        headers (eval (flatten (cons 'hash-map (reduce concat (map #(list (:name %1) (:value %1)) (filter #(not (= (:name %1) "Content-Length")) (:headers (:request json))))))))
        postData (:text  (:postData (:request json)))]
    ;(debug-repl)
    (client/post url {:body postData :headers headers :accept :json})))

) 


(defn scrape-gamespot-review [url & [just-return-review]]
  (let [data (get-some url)]
    (if just-return-review
      (first (html/select data #{[:#main]})) ; return the review

      ; otherwise scrape the page
      (let [review  (render (html/select data #{[:#main]}))
            page-links (map #(str "http://www.gamespot.com" (:href (:attrs %1))) (html/select data #{[:#main :.pageNav :.pages :li :a]}))
            review-pages  (cons review (doall (map #(-> %1 (scrape-gamespot-review true) render) page-links)))
            gamespot-score (first (:content (first (:content (first (html/select data #{[:#side :li.editor_score :span.data]})))))) 
            gamespot-score-word (first  (:content  (first  (html/select data #{[:#side :li.editor_score :span.scoreword]})))) 
            has-reviews (if (!= (html/text (html/select data #{[:#side :li.review-score :span.more]})) "No Reviews") true false)
            metacritic-score (if has-reviews (-> data
                                               (html/select #{[:#side :li.review_score :span.scoreWrap :a]})
                                               first
                                               :content
                                               first)
                                "No Reviews")
            metacritic-reviews (if has-reviews (-> data 
                                                 (html/select #{[:#side :li.review_score :span.more :span]})
                                                 first
                                                 :content
                                                 first
                                                 )
                                                 
                                  "No Reviews")
            metacritic-reviews-link (if has-reviews (href (html/select data #{[:#side :li.review_score :span.scoreWrap :a]})) "No Reviews")
            comments
              (try 
                (let [comments (doall (map html/text (html/select data #{[:ul#comments_list :li.comment]}))) 
                      num-comment-pages (Integer. (html/text (first (html/select data #{[:#post_comment :.pagination :ul.pages :li.last :a]}))))
                      comment-page-link (html/select data #{[:#post_comment :.pagination :.page_flipper :a]})
                      json (:rel (:attrs (first comment-page-link)))
                      base-url "http://www.gamespot.com/pages/ajax/load_comments.php?page="
                      params (sean-replace json " nofollow" "" 
                                                "}" "" 
                                                "{" "" 
                                                "'" "" 
                                                ":" "="
                                                "," "&")]
                  ; start scraping comments here
                  (loop [page-num (map #(+ 1 %) (rest (range num-comment-pages)))
                       c comments]
                    (if (not-empty page-num)
                      (recur 
                        (rest page-num)
                        (let [data (get-some (str base-url (first page-num) "&" params))]
                          ;(debug-repl)
                          (concat c (doall (map html/text (html/select data #{[:ul#comments_list :li.comment]}))))))
                      c)))
                (catch Exception _  ; if it fucks up, just return the comments on the current page
                  (doall (map html/text (html/select data #{[:ul#comments_list :li.comment]})))))]

          ; return everything
          (hash-map
            :review review-pages
            :comments comments
            :gamespot_score gamespot-score
            :gamespot_score_word gamespot-score-word
            :metacritic_score metacritic-score
            :metacritic_reviews metacritic-reviews
            :metacritic_reviews_link metacritic-reviews-link)))))



(defn download-user-review [url]
  (let [data (get-some url)
        metadata (render (html/select data #{[:#player_review :div.body :div.user_reviews]}))
        score-details (render (html/select data #{[:#player_score_details :div.body :dl.review_details]}))
        body (render (html/select data #{[:#player_review_body]}))]

    (hash-map 
      :meta metadata
      :score_details score-details
      :body body
      :url url)))

;(def reviews-url-1 "http://www.gamespot.com/final-fantasy-xiii-2/reviews/platform/xbox360/") 

; download each user review from the reviews page that contains links to user reviews and the gamespot review
(defn get-reviews [url]
  (let [data (get-some url)
        user-review-links (doall (map #(:href (:attrs %1)) (html/select data #{[:#main :.userReviewsModule :div.reviewAction :a.continue]})))
        user-reviews (doall (map download-user-review user-review-links))
        gamespot-review-link 
          (href 
            (filter #(.contains (html/text %) "GameSpot Review")
              (html/select
                (filter #(.contains (html/text %) "GameSpot Review")
                  (html/select data #{[:.navItem.reviewsNavItem.navItemOpen.navItemActive]}))
                #{[:a.subNavItemAction]}))) 
        gs-array (if (nil? gamespot-review-link) nil (list gamespot-review-link))
        gamespot-review (first (doall (map scrape-gamespot-review gs-array)))
        ]
    (list gamespot-review user-reviews)))


(defn scrape-details-page [url]
  (let [data (get-some url)]

    (render (html/select data #{[:#techInfo :dl.game_info]}))))


(defn scrape-related-games-page [url]
  (let [data (get-some url)
        related-games (doall (map #(.trim ( apply str (html/emit* %1)) ) (html/select data #{[:#main :.listModule.gamesModule :.body :div.games :ol.games :li]}))) 
        same-universe-url  (href (html/select data #{[:#main :div.relatedGamesNav :div.relatedGamesNavWrap :div.navItems :ol.navItems :li.universeNavItem :a]}))
        same-universe-data  (get-some same-universe-url)
        same-universe-games (doall (map #(.trim ( apply str (html/emit* %1))) (html/select same-universe-data #{[:#main :.listModule.gamesModule :.body :div.games :ol.games :li]})))]

    (hash-map 
      :related_games related-games 
      :same_universe same-universe-games)))


(defn scrape-gamespot [url]
  (let [; get the page in tree form
        data (get-some url)

        ; get a list of platforms for the game
        platforms (filter #(!= %1 "All Platforms")
                    (doall (map html/text (html/select data #{[:div#main :ul.platformFilter :li]}))))


        ; get the details about the game
        details-url (href (html/select data 
                      #{[:#mini :.mini_col_wrap :div.contentNav :ul.contentNav :.summaryNavItem :ul.contentSubNav :li.techinfoSubNavItem :div.subNavItemWrap :a]})) 
        
        details (scrape-details-page details-url)  

        ; get the page that contains links to the gamespot and user reviews
        reviews-url (href (html/select data #{[:#mini :.mini_col_wrap :div.contentNav :ul.contentNav :.reviewsNavItem :div.navItemWrap :a]})) 
        ; get all the reviews from this page
        reviews (get-reviews reviews-url) ; returns gamespot then user reviews

        ; get the related games
        related-games-url (href (html/select data #{[:#mini :.mini_col_wrap :div.contentNav :ul.contentNav :.summaryNavItem :ul.contentSubNav :li.relatedSubNavItem :div.subNavItemWrap :a]})) 

        ; obtain related games and merge that with the metadata
        metadata
          (merge
            (hash-map
              :details details
              :platforms platforms)
            (scrape-related-games-page related-games-url))]

    ; return everything
    (cons metadata reviews)))


;(def url (xml-rpc/call "http://research.seanneilan.com/" :get_a_url global_password))

;(defn asdf [a b] (asdf a))

(def platforms-to-keep
  '(:xbox360
    :xbox
    :pc

    :ps
    :ps2
    :ps3
    :vita
    :psp

    :wii
    :wii-u

    :ds
    :3ds

    :iphone
    :android
    
    :genesis
    :master
    :saturn
    :dreamcast
    :segacd

    :gameboy
    :gba
    :gbc

    :neo
    :ngpc ; neo geo pocket color

    :nes
    :snes
    :n64
    :gamecube))

(def host (.trim (slurp "host.txt")))
(def id-filename "clientid.txt")
(def id 
  (if (-> id-filename java.io.File. .isFile)
    (slurp id-filename)
    (do 
      (spit id-filename (.toString (java.util.UUID/randomUUID)))
      (slurp id-filename))))

(def global_password "JalvOcGik")

(fn []
  (let [url  (xml-rpc/call host :get_a_url global_password id)]
    (do
      (println "Getting: " url)
      (try
        (xml-rpc/call host :commit_data global_password id url (json-str (scrape-gamespot url)))
        (catch Exception e
          (sean-email
            (str url "\n\n\n\n" (str (parse-exception e))))))
      (println "Done!"))))

