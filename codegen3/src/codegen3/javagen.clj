(ns codegen3.javagen
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as s])
  (:gen-class))

(def BASE-CLASS-PATH "./gen-java")

(def PREAMBLE
  (str " - THIS CLASS IS AUTOMATICALLY GENERATED - \n"
       " - -    PLEASE DO NOT EDIT MANUALLY    - - \n\n"
       " Generated on: " (.toString (java.util.Date.))
       "\n\n"))



;ch.loway.oss.ari4java.generated
(def BASE-GEN-PKG "gg")


(def IMPORTS-INTERFACE [
                        "java.util.Date"
                        "java.util.List"
                        "java.util.Map"
                        "java.util.ArrayList"
                        "ch.loway.oss.ari4java.tools.RestException"
                        "ch.loway.oss.ari4java.tools.AriCallback"
                        "ch.loway.oss.ari4java.tools.tags.*" ])



                                        ;(genclassfile {:package "a.b.c"
                                        ;               :classname "Pippon"
                                        ;               :imports ["java.w"]
                                        ;               :implements ["tool", "tool2"]
                                        ;               :extends "base"
                                        ;               :functions [
                                        ;                  { :method "pippo"
                                        ;                    :returns "void"
                                        ;                    :args [ {:type "int" :name "pippo
                                        ;                            {:type "String" :name "plut"}]
                                        ;                    :notes ["yoo"]}
                                        ;               ]
                                        ;               :stanzas ["m;" ]  } )


(defn camelName
  "Creates a camelName - es getDate by joining 'get' (prefix)
  and 'date' (value)"
  [p v]
  {:pre [(pos? (count v))]}
  (str p
       (s/upper-case (subs v 0 1))
       (subs v 1)))


(defn mkSection
  "Crea una sezione con prefisso e suffisso.
   Items puo' essere:
    - nil o vettore vuoto: non fa nulla
    - uno scalare: aggiunge solo una volta
    - un vettore: aggiunge n volte"
  [itemprefix itempostfix items]
  (let [i2 (cond
              (string? items) [ items ]
              :else           (vec items))]
    (reduce
      #(str %1 itemprefix %2 itempostfix) ""
      i2 )))


(defn mkImports
  "Creates import section."
  [lImports]
  (mkSection "import " ";\n" lImports) )

(defn indent-prefix
  "Adds a given prefix to a block of text (string).
  Returns the string with prefix applied. "
  [prefix text]
  (s/join "\n"
      (map #(str prefix % ) (s/split-lines text))))

(defn indent
  "Indents a text by three spaces"
  [text]
  (indent-prefix "   " text))

(defn mkComment
  "Creates a Java comment.
  Receives one string of (multi-line) text."
  [text]
  (let [STARS "************************************************"]
    (str "/" STARS "\n"
       (indent-prefix " * " text) "\n"
       " " STARS "/" "\n\n"
        )))


(defn genAttr
  "Generate an attribute. Checks that :type and :name are defined."
  [{t :type n :name}]
  {:pre [(string? t) (string? n)]}
  (str t " " n))


(defn genAttrs
  "The attributes for a method.
   data is a list of attributes."
  [data]
  (let [signatures (map genAttr data)]
    (s/join ", " signatures)))


(defn genbody
  "Creates a Java method
      :method
      :returns
      :isPrivate   if undefined, public
      :args        [{:type :name}]
      :notes
      :body
      :isAbstract
  "
  [data]
  (let [{:keys [method returns isPrivate args notes body isAbstract]} data]
  (str
    "\n\n"
    (mkComment notes)
    (if isPrivate "private " "public ")
    returns " "
    method "("
    (genAttrs args)
    ")"
    (if isAbstract
       ";"
       (str
        "{\n"
        (indent body)
        "\n"
        "}"))
    "\n"

   )))


(defn genFilename
  "Returns the relative filename of a package + class"
  [pkgName kName]
  (let [pkg (s/replace pkgName "." "/")]
    (str pkg "/" kName ".java")))



(defn genclassfile
   "Inputs
        :classname    the class name
        :isInterface
        :package      the package name
        :imports      a list of imports
        :implements   a list of implementations
        :extends      the class to extend
        :notes        the class comments (version etc)
        :functions    a list of methods to implement (may be empty)
        :stanzas      a list of text to implement (added after the methods)

    Output
        :body
        :filename
   "
   [data]
   (let [{:keys [classname isInterface package imports implements extends notes functions stanzas]} data]
   { :filename (genFilename package classname)
     :body
       (str
          "package " package ";\n\n"
          (mkImports imports)
          (mkComment (str PREAMBLE notes))
          "public "
               (if isInterface "interface " "class ")
               classname
               (mkSection " extends " "" extends)
               " implements "
               (mkSection "" ", " implements)
               "java.io.Serializable "
               " {\n"

          (mkSection "" "\n\n" stanzas)
          (mkSection "" "\n\n" (map #(indent (genbody %)) functions))

          "}\n\n")
    }
  ) )


(def knownSwaggerTypes
  "These Swagger types match a Java type directly."

  {
   "string" "String",
   "long" "long",
   "int" "int" ,
   "double" "double",
   "date" "Date",
   "object" "String",
   "boolean" "boolean",
   "containers" "Map<String,String>"
   })



(defn swagger->java
  "Turns a Swagger type into a matching Java type.
  We need to know if we are asking for a concrete implementation
  or an interface (as for native objects this is different)
  so we set the API version when we require a concrete
  implementation.
  "
  [sw-type api-version]
  (let [inList (get (re-matches #"^List\[(.+)\]$" sw-type) 1)
        known  (knownSwaggerTypes sw-type)
        concrete? (pos? (count api-version))]
    (cond
       inList (str "List<" (swagger->java inList api-version) ">")
       known  known
       :else  (if concrete?
                (str sw-type "_impl_" api-version)
                sw-type))))

;; DATA CLASSES
;; Only containers for data.



(defn mkGetterVal [field]
  (let [{t :type v :name} field]
  {
      :method     (camelName "get" v)
      :returns    t
      :args       []
      :notes      (str "get " v)
      :body       (str "return this." v ";")
  }))

(defn mkSetterVal [field]
  (let [{t :type v :name} field]

  {
      :method     (camelName "set" v)
      :returns    "void"
      :args       [field]
      :notes      (str "sets " v)
      :body       (str "this." v " = " v ";")
  }))

(defn mkPrivateField [acc field]
  (let [{t :type v :name} field]

  (conj acc
        (str "private " t " " v ";"))
  ))


(defn mkGettersSetters
  "da usare con reduce"
  [funcs field]
  (conj funcs
      (mkGetterVal field)
      (mkSetterVal field))
  )

(defn mkDataClass
  " TO BE TESTED


  (mkDataClass p c n
             [{:type int :val pluto}
              {:type String :val pippo}]
             )"



  [package klass notes lfields]
  {
   :classname    klass
   :package      package
   :notes        notes
   :functions    (reduce mkGettersSetters [] lfields)
   :stanzas      (reduce mkPrivateField   [] lfields)
   })


(defn writeOutKlass
  "Generates and writes a classfile on disk.
   Returns the filename."
  [klass]
  (let [{:keys [filename body]} (genclassfile klass)
        realPath (str BASE-CLASS-PATH "/" filename)]
    (clojure.java.io/make-parents realPath)
    (spit realPath body)
    realPath))



(defn writeInterface
  "Una interfaccia è scritta su disco 
  sulla base del nome del file e dei metodi"
  [file comments meths]
  (let [klass {
               :classname    (camelName "Action" (name file))
               :isInterface  true
               :package      BASE-GEN-PKG
               :imports      IMPORTS-INTERFACE
                                        ;:implements   ""
                                        ;:extends      the class to extend
               :notes        comments
               :functions    meths
                                        ;:stanzas      a list of text to implement (added after the methods)
               }]
    (writeOutKlass klass)  ) )






                                        ;(writeOutKlass
                                        ;   (mkDataClass "p.k.g" "c" "no"
                                        ;      [{:type "int" :name "pluto"} {:type "String" :name "pippno"}]  ))




















;; CREATE FILE
;; (let [file-name "path/to/whatever.txt"]
;;  (make-parents file-name)
;;  (spit file-name "whatever"))
