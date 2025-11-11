import type { MouseEventHandler } from "react";
import { FormProgress } from "@navikt/ds-react";

type Props = {
  activeStep: 1 | 2 | 3;
  onStep1?: MouseEventHandler<HTMLAnchorElement>;
  onStep2?: MouseEventHandler<HTMLAnchorElement>;
  onSummary?: MouseEventHandler<HTMLAnchorElement>;
};

const blockNavigation: MouseEventHandler<HTMLAnchorElement> = (event) => event.preventDefault();

export const SkjemaFormProgress = ({ activeStep, onStep1, onStep2, onSummary }: Props) => (
  <FormProgress activeStep={activeStep} totalSteps={3}>
    <FormProgress.Step href="#" onClick={onStep1 ?? blockNavigation}>
      Deltakere
    </FormProgress.Step>
    <FormProgress.Step href="#" onClick={onStep2 ?? blockNavigation}>
      Behov for bistand
    </FormProgress.Step>
    <FormProgress.Step href="#" onClick={onSummary ?? blockNavigation}>
      Oppsummering
    </FormProgress.Step>
  </FormProgress>
);
