"use client";

import { useState } from "react";

const SECTIONS: { title: string; body: React.ReactNode }[] = [
  {
    title: "Starting Score",
    body: (
      <>
        You start with a points target, usually <strong>501</strong>. Each
        correct answer has a score, and that score is subtracted from your
        total. First to reach exactly 0 wins — a <em>checkout</em>.
      </>
    ),
  },
  {
    title: "Scoring",
    body: (
      <>
        Submitting an answer is our equivalent to &ldquo;throwing a
        dart.&rdquo; The score you get for that answer depends on the
        question. For example, if the question is &ldquo;Goals for Liverpool in
        Premier League since 2000&rdquo;, an answer of &ldquo;Luis Suarez&rdquo;
        would be worth 69 points, as he scored 69 goals for Liverpool in the
        Premier League. Valid values range 1–180, except 163, 166, 169, 172,
        173, 175, 176, 178, 179 (impossible with three darts).
      </>
    ),
  },
  {
    title: "Bust Rules",
    body: (
      <>
        You <em>bust</em> if your remaining score after a throw drops below
        -10, or if your dart score exceeds 180. A bust resets your score to
        its value before the throw.
      </>
    ),
  },
  {
    title: "Checkout Range",
    body: (
      <>
        When your score is under 180, you could possibly <em>checkout</em>{" "}
        (win) with your next throw. A checkout is when your score reaches 0.
        In real darts, you have to score enough to finish on exactly 0. But we
        give you a little more leeway, and any answer leaving you between{" "}
        <strong>-10 and 0</strong> will count as a checkout. Extra points for
        the closer you get to 0, though!
      </>
    ),
  },
];

export default function HowToPlayPanel() {
  const [open, setOpen] = useState(false);

  return (
    <div>
      <button
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="btn-ghost w-full px-4 py-2.5 !justify-between"
      >
        <span>How to play</span>
        <span className={`transition-transform duration-200 ${open ? "rotate-180" : ""}`} aria-hidden="true">
          ▾
        </span>
      </button>

      {open && (
        <div className="mt-3 border border-line rounded-md p-4 space-y-4 animate-rise bg-surface">
          {SECTIONS.map((s) => (
            <section key={s.title}>
              <h3 className="font-display font-bold text-base mb-1">{s.title}</h3>
              <p className="text-muted text-[13px] leading-relaxed">{s.body}</p>
            </section>
          ))}

          <section>
            <h3 className="font-display font-bold text-base mb-1">Hints Explained</h3>
            <ul className="text-muted text-[13px] leading-relaxed list-disc pl-4 space-y-1">
              <li>
                <strong>180s Left</strong> — Answers still available for this
                question worth exactly 180 points. Visible when score &gt; 180.
              </li>
              <li>
                <strong>Checkouts</strong> — Answers that would end the game in
                one throw. Visible when score &le; 180.
              </li>
            </ul>
          </section>
        </div>
      )}
    </div>
  );
}
