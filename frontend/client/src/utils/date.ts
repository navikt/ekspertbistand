export const formatDateTime = (value: string): string | null => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  const datePart = new Intl.DateTimeFormat("nb-NO", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date);

  const timePart = new Intl.DateTimeFormat("nb-NO", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);

  if (!datePart) {
    return null;
  }

  const formattedTime = timePart.replace(":", ".").trim();
  return formattedTime ? `${datePart} kl. ${formattedTime}` : datePart;
};
