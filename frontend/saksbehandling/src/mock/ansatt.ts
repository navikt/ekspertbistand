export type AnsattEnhet = {
  id: string;
  nummer: string;
  navn: string;
};

export type InnloggetAnsatt = {
  id: string;
  navn: string;
  epost: string;
  enheter: ReadonlyArray<AnsattEnhet>;
  gjeldendeEnhet: AnsattEnhet;
};

const enheter: AnsattEnhet[] = [
  { id: "f62f3d31-84ca-4406-8a1e-e61a45141a4a", nummer: "2970", navn: "IT-avdelingen" },
  {
    id: "24cdeaa6-0929-4307-bde8-bc513d8d603a",
    nummer: "4710",
    navn: "Nav hjelpemiddelsentral Agder",
  },
  {
    id: "82465442-7f35-41ed-beeb-c7742c8a0015",
    nummer: "4711",
    navn: "Nav hjelpemiddelsentral Rogaland",
  },
  {
    id: "81f28e25-3d5e-4094-8255-f5d40fb0df9d",
    nummer: "4715",
    navn: "Nav hjelpemiddelsentral Møre og Romsdal",
  },
  {
    id: "d546311e-43d2-41c5-9329-282167f6c066",
    nummer: "4716",
    navn: "Nav hjelpemiddelsentral Trøndelag",
  },
];

export const mockInnloggetAnsatt: InnloggetAnsatt = {
  id: "S112233",
  navn: "Silje Saksbehandler",
  epost: "silje.saksbehandler@nav.no",
  enheter,
  gjeldendeEnhet: enheter[0],
};
