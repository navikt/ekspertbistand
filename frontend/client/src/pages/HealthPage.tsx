import useSWR from "swr";

type ApiHealth = {
  status: "ok";
};

export default function HealthPage() {
  const { data } = useSWR<ApiHealth>("/internal/isAlive");

  return <p>Health: {data?.status ?? "loading..."}</p>;
}
