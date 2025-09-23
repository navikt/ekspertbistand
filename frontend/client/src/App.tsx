import { useEffect, useState } from "react";
import type { ApiHealth } from "shared";

export default function App() {
  const [health, setHealth] = useState<ApiHealth | null>(null);

  useEffect(() => {
    fetch("/api/health")
      .then((res) => res.json())
      .then((data: ApiHealth) => setHealth(data));
  }, []);

  return (
    <div>
      <h1>Ekspertbistand</h1>
      <p>Health: {health?.status ?? "loading..."}</p>
    </div>
  );
}
