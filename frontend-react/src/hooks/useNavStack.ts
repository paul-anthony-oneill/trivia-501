"use client";

import { useState, useCallback, useEffect } from "react";
import type { NavScreen } from "@/components/game/lobby/types";

export function useNavStack() {
  const [stack, setStack] = useState<NavScreen[]>([{ id: "football" }]);
  const [slideDir, setSlideDir] = useState<1 | -1>(1);
  const [animKey, setAnimKey] = useState(0);

  const push = useCallback((screen: NavScreen) => {
    window.history.pushState(null, "", "");
    setSlideDir(1);
    setAnimKey((k) => k + 1);
    setStack((s) => [...s, screen]);
  }, []);

  // Stable — uses functional updates so the callback identity never changes
  const pop = useCallback(() => {
    setSlideDir(-1);
    setAnimKey((k) => k + 1);
    setStack((s) => (s.length <= 1 ? s : s.slice(0, -1)));
  }, []);

  // Sync browser back button with drill-down stack
  useEffect(() => {
    window.addEventListener("popstate", pop);
    return () => window.removeEventListener("popstate", pop);
  }, [pop]);

  // Breadcrumb label for the back button
  const breadcrumb = stack
    .slice(1)
    .map((s) => {
      if (s.id === "football") return "Football";
      if (s.id === "football-league") return s.league.name;
      if (s.id === "football-club") return s.club.name;
      return "";
    })
    .join(" › ");

  const currentScreen = stack[stack.length - 1];

  return { stack, currentScreen, slideDir, animKey, breadcrumb, push, pop };
}
