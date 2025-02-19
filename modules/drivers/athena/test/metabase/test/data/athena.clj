(ns metabase.test.data.athena
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [metabase.config :as config]
   [metabase.driver :as driver]
   [metabase.driver.ddl.interface :as ddl.i]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
   [metabase.driver.sql.util.unprepare :as unprepare]
   [metabase.test.data.interface :as tx]
   [metabase.test.data.sql :as sql.tx]
   [metabase.test.data.sql-jdbc :as sql-jdbc.tx]
   [metabase.test.data.sql-jdbc.execute :as execute]
   [metabase.test.data.sql-jdbc.load-data :as load-data]
   [metabase.test.data.sql.ddl :as ddl]))

(set! *warn-on-reflection* true)

(sql-jdbc.tx/add-test-extensions! :athena)

;; during unit tests don't treat athena as having FK support
(defmethod driver/supports? [:athena :foreign-keys] [_ _] (not config/is-test?))

;;; ----------------------------------------------- Connection Details -----------------------------------------------

;; Athena doesn't support dashes in Table names... we'll just go ahead and convert them all to underscores, even for DB
;; names.
(defmethod ddl.i/format-name :athena
  [driver table-or-field-name]
  ((get-method ddl.i/format-name :sql-jdbc) driver (str/replace table-or-field-name #"-" "_")))

(defmethod tx/dbdef->connection-details :athena
  [driver _context {:keys [database-name], :as _dbdef}]
  (merge
   {:region                        (tx/db-test-env-var-or-throw :athena :region)
    :access_key                    (tx/db-test-env-var-or-throw :athena :access-key)
    :secret_key                    (tx/db-test-env-var-or-throw :athena :secret-key)
    :s3_staging_dir                (tx/db-test-env-var-or-throw :athena :s3-staging-dir)
    :workgroup                     "primary"
    ;; HACK -- this is here so the Athena driver sync code only syncs the database in question -- see documentation
    ;; for [[metabase.driver.athena/fast-active-tables]] for more information.
    :metabase.driver.athena/schema (some->> database-name (ddl.i/format-name driver))}))

;; TODO: We need a better way to have an isolated test environment for Athena
;; If other tables exist, the tests start to query them for some reason,
;; so we exclude them via an environment variable
(defmethod sql-jdbc.sync/excluded-schemas :athena
  [_driver]
  (let [ignored-schemas (set (str/split (tx/db-test-env-var-or-throw :athena :ignore-dbs "") #","))]
    (log/infof "Excluding schemas: %s" (pr-str ignored-schemas))
    ignored-schemas))

;; Athena requires you identify an object with db-name.table-name
(defmethod sql.tx/qualified-name-components :athena
  ([_ db-name]                       [db-name])
  ([_ db-name table-name]            [db-name table-name])
  ([_ db-name table-name field-name] [db-name table-name field-name]))

;;; Athena requires backtick-escaped database name for some queries
(defmethod sql.tx/drop-db-if-exists-sql :athena
  [driver {:keys [database-name]}]
  (log/warn "Dropping an Athena database does not delete existing data. You may have to delete in manually from S3 and Glue Console.")
  (format "DROP DATABASE IF EXISTS `%s` CASCADE;" (ddl.i/format-name driver database-name)))

(defmethod sql.tx/drop-table-if-exists-sql :athena
  [driver {:keys [database-name]} {:keys [table-name]}]
  (format "DROP TABLE IF EXISTS `%s`.`%s`"
          (ddl.i/format-name driver database-name)
          (ddl.i/format-name driver table-name)))

(defmethod sql.tx/create-db-sql :athena
  [driver {:keys [database-name]}]
  (format "CREATE DATABASE `%s`;" (ddl.i/format-name driver database-name)))

(defn- s3-location-for-table
  [driver database-name table-name]
  (let [s3-prefix (tx/db-test-env-var-or-throw :athena :s3-staging-dir)]
    (str s3-prefix
         (when-not (str/ends-with? s3-prefix "/")
           "/")
         (ddl.i/format-name driver database-name)
         "/"
         (ddl.i/format-name driver table-name)
         "/")))

;; Customize the create table table to include the S3 location
;; TODO: Specify a unique location each time
(defmethod sql.tx/create-table-sql :athena
  [driver {:keys [database-name]} {:keys [table-name], :as tabledef}]
  (let [field-definitions (cons
                           {:field-name "id", :base-type :type/Integer, :semantic-type :type/PK}
                           (:field-definitions tabledef))
        fields            (->> field-definitions
                               (map (fn [{:keys [field-name base-type]}]
                                      (format "`%s` %s"
                                              (ddl.i/format-name driver field-name)
                                              (if (map? base-type)
                                                (:native base-type)
                                                (sql.tx/field-base-type->sql-type driver base-type)))))
                               (interpose ", ")
                               str/join)]
    ;; ICEBERG tables do what we want, and dropping them causes the data to disappear; dropping a normal non-ICEBERG
    ;; table doesn't delete data, so if you recreate it you'll have duplicate rows. 'normal' tables do not support
    ;; `DELETE .. FROM`, either, so there's no way to fix them here.
    ;;
    ;; I contemplated using ICEBERG tables here but they're unusably slow -- we're talking like 10 seconds to run a
    ;; `SELECT count(*)` query on a table with 100 rows. So for the time being, just be careful not to load the same
    ;; data twice. If you do, you'll have to manually delete those folders from the s3 bucket.
    ;;
    ;; -- Cam
    (format #_"CREATE TABLE `%s`.`%s` (%s) LOCATION '%s' TBLPROPERTIES ('table_type'='ICEBERG');"
            "CREATE EXTERNAL TABLE `%s`.`%s` (%s) LOCATION '%s';"
            (ddl.i/format-name driver database-name)
            (ddl.i/format-name driver table-name)
            fields
            (s3-location-for-table driver database-name table-name))))

(comment
  (let [test-data-dbdef (tx/get-dataset-definition @(requiring-resolve 'metabase.test.data.dataset-definitions/test-data))
        venues-tabledef (some (fn [tabledef]
                                (when (= (:table-name tabledef) "venues")
                                  tabledef))
                              (:table-definitions test-data-dbdef))]
    (sql.tx/create-table-sql :athena {:database-name "test-data"} venues-tabledef)))

;; The Athena JDBC driver doesn't support parameterized queries.
;; So go ahead and deparameterize all the statements for now.
(defmethod ddl/insert-rows-ddl-statements :athena
  [driver table-identifier row-or-rows]
  (for [sql+args ((get-method ddl/insert-rows-ddl-statements :sql-jdbc/test-extensions) driver table-identifier row-or-rows)]
    (unprepare/unprepare driver sql+args)))

(doseq [[base-type sql-type] {:type/BigInteger     "BIGINT"
                              :type/Boolean        "BOOLEAN"
                              :type/Date           "TIMESTAMP"
                              :type/DateTime       "TIMESTAMP"
                              :type/DateTimeWithTZ "TIMESTAMP"
                              :type/Decimal        "DECIMAL"
                              :type/Float          "DOUBLE"
                              :type/Integer        "INT"
                              :type/Text           "STRING"
                              :type/Time           "TIMESTAMP"}]
  (defmethod sql.tx/field-base-type->sql-type [:athena base-type] [_ _] sql-type))

;; I'm not sure why `driver/supports?` above doesn't rectify this, but make `add-fk-sql a noop
(defmethod sql.tx/add-fk-sql :athena [& _] nil)

;; Athena can only execute one statement at a time
(defmethod execute/execute-sql! :athena [& args]
  (apply execute/sequentially-execute-sql! args))

;; Might have to figure out autoincrement settings
(defmethod sql.tx/pk-sql-type :athena [_] "INTEGER")

;; Add IDs to the sample data
(defmethod load-data/load-data! :athena
  [& args]
  ;;; 200 is super slow, and the query ends up being too large around 500 rows... for some reason the same dataset
  ;;; orders table (about 17k rows) stalls out at row 10,000 when loading them 200 at a time. It works when you do 400
  ;;; at a time tho. This is just going to have to be ok for now.
  (binding [load-data/*chunk-size* 400]
    (apply load-data/load-data-add-ids-chunked! args)))

(defn- server-connection-details []
  (tx/dbdef->connection-details :athena :server nil))

(defn- server-connection-spec []
  (sql-jdbc.conn/connection-details->spec :athena (server-connection-details)))

(defn- existing-databases
  "Set of databases that already exist in our S3 bucket, so we don't try to create them a second time."
  []
  (jdbc/with-db-connection [conn (server-connection-spec)]
    (let [dbs (into #{} (map :database_name) (jdbc/query conn ["SHOW DATABASES;"]))]
      (log/infof "The following Athena databases have already been created: %s" (pr-str (sort dbs)))
      dbs)))

(defmethod tx/create-db! :athena
  [driver {:keys [database-name], :as db-def} & options]
  (let [database-name (ddl.i/format-name driver database-name)]
    (if (contains? (existing-databases) database-name)
      (log/infof "Athena database %s already exists, skipping creation" (pr-str database-name))
      (do
        (log/infof "Creating Athena database %s" (pr-str database-name))
        ;; call the default impl for SQL JDBC drivers
        (apply (get-method tx/create-db! :sql-jdbc/test-extensions) driver db-def options)))))

(defmethod tx/sorts-nil-first? :athena
  [_driver _base-type]
  false)

(defmethod tx/supports-time-type? :athena
  [_driver]
  false)
