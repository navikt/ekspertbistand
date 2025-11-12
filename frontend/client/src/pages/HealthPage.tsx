import useSWR from "swr";
import type { ApiHealth } from "shared";

export default function HealthPage() {
  const { data } = useSWR<ApiHealth>("/internal/isAlive");

  return <p>Health: {data?.status ?? "loading..."}</p>;
}
