/* eslint-disable react/prop-types */
import React from "react";
import { t } from "ttag";

import Link from "metabase/core/components/Link";

function MongoConnectionStringToggle({ field: { value, onChange } }) {
  return (
    <div>
      <Link className="link" onClick={() => onChange(!value)}>
        {value === false
          ? t`Paste a connection string`
          : t`Fill out individual fields`}
      </Link>
    </div>
  );
}

export default function getFieldsForMongo(details, defaults, id) {
  const useConnectionString =
    details["use-conn-uri"] == null || details["use-conn-uri"];

  const manualFields = [
    "host",
    "dbname",
    "port",
    "user",
    "pass",
    "authdb",
    "additional-options",
    "use-srv",
    "ssl",
  ];

  const fields = defaults["details-fields"]
    .filter(
      field =>
        !(
          field["name"] === "use-conn-uri" ||
          (useConnectionString && manualFields.includes(field["name"])) ||
          (!useConnectionString && field["name"] === "conn-uri")
        ),
    )
    .map(function (field) {
      if (field["name"] === "conn-uri" && id) {
        field.type = "password";
      }
      return field;
    });

  return {
    "details-fields": [
      {
        name: "use-conn-uri",
        type: MongoConnectionStringToggle,
        hidden: true,
        default: false,
      },
      ...fields,
    ],
  };
}
