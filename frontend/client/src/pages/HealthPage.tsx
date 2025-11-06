import { useEffect, useState } from "react";
import type { ApiHealth } from "shared";

export default function HealthPage() {
  const [health, setHealth] = useState<ApiHealth | null>(null);

  useEffect(() => {
    fetch("/internal/isAlive")
      .then((res) => res.json())
      .then((data: ApiHealth) => setHealth(data));
  }, []);

  return <p>Health: {health?.status ?? "loading..."}</p>;
}
