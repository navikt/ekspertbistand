import type { MouseEventHandler } from "react";
import { BodyShort, FormProgress } from "@navikt/ds-react";

type Props = {
  activeStep: 1 | 2 | 3 | 4;
  onStep1?: MouseEventHandler<HTMLAnchorElement>;
  onStep2?: MouseEventHandler<HTMLAnchorElement>;
  onStep3?: MouseEventHandler<HTMLAnchorElement>;
  onSummary?: MouseEventHandler<HTMLAnchorElement>;
};

const blockNavigation: MouseEventHandler<HTMLAnchorElement> = (event) => event.preventDefault();

export const SkjemaFormProgress = ({ activeStep, onStep1, onStep2, onStep3, onSummary }: Props) => (
  <>
    <FormProgress activeStep={activeStep} totalSteps={4}>
      <FormProgress.Step href="#" onClick={onStep1 ?? blockNavigation}>
        Deltaker
      </FormProgress.Step>
      <FormProgress.Step href="#" onClick={onStep2 ?? blockNavigation}>
        Kontaktperson og ekspert
      </FormProgress.Step>
      <FormProgress.Step href="#" onClick={onStep3 ?? blockNavigation}>
        Behov for bistand
      </FormProgress.Step>
      <FormProgress.Step href="#" onClick={onSummary ?? blockNavigation}>
        Oppsummering
      </FormProgress.Step>
    </FormProgress>
    <BodyShort size="small" spacing>
      Alle steg må fylles ut
    </BodyShort>
  </>
);
