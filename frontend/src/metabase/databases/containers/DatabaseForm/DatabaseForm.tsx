import { connect } from "react-redux";
import { getSetting } from "metabase/selectors/settings";
import { State } from "metabase-types/store";
import DatabaseForm, { DatabaseFormProps } from "../../components/DatabaseForm";

type DatabaseFormStateKeys = "engines" | "isHosted";
type DatabaseFormOwnProps = Omit<DatabaseFormProps, DatabaseFormStateKeys>;
type DatabaseFormStateProps = Pick<DatabaseFormProps, DatabaseFormStateKeys>;

const mapStateToProps = (state: State) => ({
  engines: getSetting(state, "engines"),
  isHosted: getSetting(state, "is-hosted?"),
});

export default connect<
  DatabaseFormStateProps,
  unknown,
  DatabaseFormOwnProps,
  State
>(mapStateToProps)(DatabaseForm);
