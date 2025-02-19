/* eslint-disable react/prop-types */
import React from "react";
import _ from "underscore";
import { t } from "ttag";

import SchedulePicker from "metabase/containers/SchedulePicker";

export default function MetadataSyncScheduleWidget({ field }) {
  return (
    <SchedulePicker
      schedule={
        !_.isString(field.value)
          ? field.value
          : {
              schedule_day: "mon",
              schedule_frame: null,
              schedule_hour: 0,
              schedule_type: "daily",
            }
      }
      scheduleOptions={["hourly", "daily"]}
      onScheduleChange={field.onChange}
      textBeforeInterval={t`Scan`}
      minutesOnHourPicker={true}
    />
  );
}
