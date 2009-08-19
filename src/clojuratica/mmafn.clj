; ***** BEGIN LICENSE BLOCK *****
; Version: MPL 1.1/GPL 2.0/LGPL 2.1
;
; The contents of this file are subject to the Mozilla Public License Version
; 1.1 (the "License"); you may not use this file except in compliance with
; the License. You may obtain a copy of the License at
; http://www.mozilla.org/MPL/
;
; Software distributed under the License is distributed on an "AS IS" basis,
; WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
; for the specific language governing rights and limitations under the
; License.
;
; The Original Code is the Clojure-Mathematica interface library Clojuratica.
;
; The Initial Developer of the Original Code is Garth Sheldon-Coulson.
; Portions created by the Initial Developer are Copyright (C) 2009
; the Initial Developer. All Rights Reserved.
;
; Contributor(s):
;
; Alternatively, the contents of this file may be used under the terms of
; either the GNU General Public License Version 2 or later (the "GPL"), or
; the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
; in which case the provisions of the GPL or the LGPL are applicable instead
; of those above. If you wish to allow use of your version of this file only
; under the terms of either the GPL or the LGPL, and not to allow others to
; use your version of this file under the terms of the MPL, indicate your
; decision by deleting the provisions above and replace them with the notice
; and other provisions required by the GPL or the LGPL. If you do not delete
; the provisions above, a recipient may use your version of this file under
; the terms of any one of the MPL, the GPL or the LGPL.
;
; ***** END LICENSE BLOCK *****

(ns clojuratica.clojuratica)
(declare get-mmafn get-parser)

(ns clojuratica.mmafn
  (:use [clojuratica.core]
        [clojuratica.clojuratica]
        [clojuratica.parser]
        [clojuratica.lib])
  (:import [clojuratica CExpr]
           [com.wolfram.jlink Expr]))

(defnf mmafn-dispatch [] []
  []
  [& args]
  (let [assignments  (first args)
        expression   (second args)]
    (if-not (vector? assignments)
      (throw (Exception. (str "First argument to mmafn "
                              "must be a vector of assignments"))))
    (cond (string? expression)          :string
          (instance? Expr expression)   :expr
          (instance? CExpr expression)  :cexpr
          true (throw (Exception. (str "Second argument to mmafn must be "
                                       "string, Expr, or CExpr. You passed an object "
                                       "of class " (class expression)))))))

(defmulti mmafn mmafn-dispatch)

(defmethodf mmafn :string [] []
  [_ passthrough-flags]
  [assignments s evaluate]
  (let [kernel-link (evaluate :get-kernel-link)
        expr        (.getExpr (express s kernel-link))]
    (apply mmafn assignments expr evaluate passthrough-flags)))

(defmethodf mmafn :cexpr [] []
  [_ passthrough-flags]
  [assignments cexpr evaluate]
  (let [expr (.getExpr cexpr)]
    (apply mmafn assignments expr evaluate passthrough-flags)))

(defmethodf mmafn :expr [[:parse :no-parse]] [:parse]
  [flags passthrough-flags]
  [assignments expr evaluate]
  (let [head        (.toString (.part expr 0))
        kernel-link (evaluate :get-kernel-link)
        mmafn       (apply get-mmafn evaluate flags)
        parse       (if (flags :parse)
                      (get-parser kernel-link mmafn)
                      identity)
        math        (comp parse evaluate)]
    (if-not (or (= "Set"        head)
                (= "SetDelayed" head)
                (= "Function"   head)
                (= "Symbol"     head))
      (throw (Exception. (str "mmafn must be passed a "
                              "string that contains a pure function "
                              "(head Function), a function definition "
                              "(head Set (=) or SetDelayed (:=)), or "
                              "a symbol (head Symbol)."))))
    (if (or (= "Function" head) (= "Symbol" head))
      (fn [& args]
        (let [expressed-args     (map (fn [x] (.getExpr (convert x))) args)
              expressed-arg-list (add-head "List" expressed-args)
              fn-call            (add-head "Apply" [expr expressed-arg-list])]
          (apply math assignments fn-call passthrough-flags)))
      (fn [& args]
        (let [lhs             (.part expr 1)
              expressed-args  (map (fn [x] (.getExpr (convert x))) args)
              name            (if (zero? (count (.args lhs)))
                                (.toString lhs)
                                (.toString (.head lhs)))
              assignments     (vec (concat assignments [name :undefined]))
              fn-call         (add-head name expressed-args)]
          (apply math assignments expr fn-call passthrough-flags))))))