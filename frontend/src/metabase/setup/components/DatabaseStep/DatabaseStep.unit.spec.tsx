import React from "react";
import { render, screen } from "@testing-library/react";
import { createMockDatabaseInfo } from "metabase-types/store/mocks";
import DatabaseStep, { DatabaseStepProps } from "./DatabaseStep";

const ComponentMock = () => <div />;

jest.mock("metabase/databases/containers/DatabaseForm", () => ComponentMock);

jest.mock(
  "metabase/databases/containers/DatabaseEngineWarning",
  () => ComponentMock,
);

describe("DatabaseStep", () => {
  it("should render in active state", () => {
    const props = getProps({
      isStepActive: true,
      isStepCompleted: false,
    });

    render(<DatabaseStep {...props} />);

    expect(screen.getByText("Add your data"));
  });

  it("should render in completed state", () => {
    const props = getProps({
      database: createMockDatabaseInfo({ name: "Test" }),
      isStepActive: false,
      isStepCompleted: true,
    });

    render(<DatabaseStep {...props} />);

    expect(screen.getByText("Connecting to Test"));
  });

  it("should render a user invite form", () => {
    const props = getProps({
      isStepActive: true,
      isEmailConfigured: true,
    });

    render(<DatabaseStep {...props} />);

    expect(screen.getByText("Need help connecting to your data?"));
  });
});

const getProps = (opts?: Partial<DatabaseStepProps>): DatabaseStepProps => ({
  isEmailConfigured: false,
  isStepActive: false,
  isStepCompleted: false,
  isSetupCompleted: false,
  onEngineChange: jest.fn(),
  onStepSelect: jest.fn(),
  onDatabaseSubmit: jest.fn(),
  onInviteSubmit: jest.fn(),
  onStepCancel: jest.fn(),
  ...opts,
});
