export type Inputs = {
  virksomhet: {
    navn: string;
    virksomhetsnummer: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefon: string;
    };
  };
  ansatt: {
    fodselsnummer: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
  };
  behovForBistand: {
    problemstilling: string;
    bistand: string;
    tiltak: string;
    kostnad: number | string;
    startDato: string | null;
    navKontakt: string;
  };
};

const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const deepClone = <T>(value: T): T => {
  if (Array.isArray(value)) {
    return value.map((item) => deepClone(item)) as unknown as T;
  }
  if (isPlainObject(value)) {
    const clone: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value)) {
      clone[key] = deepClone(val);
    }
    return clone as T;
  }
  return value;
};

const mergeObjects = (target: Record<string, unknown>, source: Record<string, unknown>) => {
  for (const [key, val] of Object.entries(source)) {
    if (val === undefined) continue;
    if (isPlainObject(val)) {
      const base = isPlainObject(target[key]) ? (target[key] as Record<string, unknown>) : {};
      target[key] = mergeObjects({ ...base }, val);
    } else {
      target[key] = deepClone(val);
    }
  }
  return target;
};

export const createEmptyInputs = (): Inputs => ({
  virksomhet: {
    navn: "",
    virksomhetsnummer: "",
    kontaktperson: {
      navn: "",
      epost: "",
      telefon: "",
    },
  },
  ansatt: {
    fodselsnummer: "",
    navn: "",
  },
  ekspert: {
    navn: "",
    virksomhet: "",
    kompetanse: "",
  },
  behovForBistand: {
    problemstilling: "",
    bistand: "",
    tiltak: "",
    kostnad: "",
    startDato: null,
    navKontakt: "",
  },
});

export const mergeInputs = (base: Partial<Inputs> | undefined, update: Partial<Inputs>): Inputs => {
  const result = base ? deepClone(base) : createEmptyInputs();
  return mergeObjects(
    result as unknown as Record<string, unknown>,
    update as unknown as Record<string, unknown>
  ) as Inputs;
};
