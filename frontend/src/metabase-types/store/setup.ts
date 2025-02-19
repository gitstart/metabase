import { DatabaseSchedules } from "metabase-types/api";

export interface Locale {
  name: string;
  code: string;
}

export interface UserInfo {
  first_name: string | null;
  last_name: string | null;
  email: string;
  site_name: string;
  password: string;
  password_confirm: string;
}

export interface InviteInfo {
  first_name: string | null;
  last_name: string | null;
  email: string;
}

export interface DatabaseInfo {
  name: string;
  engine: string | undefined;
  details: Record<string, unknown>;
  schedules: DatabaseSchedules;
  auto_run_queries: boolean;
  refingerprint: boolean;
  is_sample: boolean;
  is_full_sync: boolean;
  is_on_demand: boolean;
}

export interface SubscribeInfo {
  email: string;
}

export interface SetupState {
  step: number;
  locale?: Locale;
  user?: UserInfo;
  databaseEngine?: string;
  database?: DatabaseInfo;
  invite?: InviteInfo;
  isLocaleLoaded: boolean;
  isTrackingAllowed: boolean;
}
