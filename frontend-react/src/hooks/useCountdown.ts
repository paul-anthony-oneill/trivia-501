"use client";

import { useState, useEffect } from "react";

export function useCountdown() {
  const [timeUntilReset, setTimeUntilReset] = useState("");
  useEffect(() => {
    const tick = () => {
      const now = new Date();
      const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
      setTimeUntilReset(new Date(next.getTime() - now.getTime()).toISOString().slice(11, 19));
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);
  return timeUntilReset;
}
