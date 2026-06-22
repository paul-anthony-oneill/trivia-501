# Trivia 501 - Game Rules & Mechanics

**Version**: 1.0
**Last Updated**: 2026-01-17
**Status**: Official Rules

---

## Table of Contents

1. [Game Overview](#game-overview)
2. [Scoring System](#scoring-system)
3. [Valid Darts Scores](#valid-darts-scores)
4. [Game Flow](#game-flow)
5. [Turn Rules](#turn-rules)
6. [Winning Conditions](#winning-conditions)
7. [Match Formats](#match-formats)
8. [Question Types](#question-types)
9. [Special Rules](#special-rules)
10. [Examples](#examples)

---

## Game Overview

Trivia 501 is a two-player competitive trivia game that combines football knowledge with darts 501 scoring mechanics.

### Objective

Be the first player to reduce your score from **501 to exactly 0** (or within the checkout range of **-10 to 0**) by correctly naming football players whose statistics match the given question.

### Core Concept

- Both players start with **501 points**
- A single **question** is presented for the entire game
- Players **alternate turns** naming different players that answer the question
- Each player's **statistic value** is deducted from your score
- The game continues until one player **checks out** (reaches -10 to 0 range)

---

## Scoring System

### Basic Scoring

1. **Starting Score**: Both players begin at **501 points**
2. **Deduction**: When a player names a valid answer, the player's statistic is deducted from their score
3. **Score Display**: Scores are updated in real-time after each turn

### Example

**Question**: "Appearances for Manchester City in Premier League"

- Player 1 names **"Kevin De Bruyne"** (200 appearances)
  - Score: 501 - 200 = **301**
- Player 2 names **"Sergio Agüero"** (275 appearances)
  - Score: 501 - 275 = **226**

---

## Valid Darts Scores

Not all numerical scores are valid in traditional 501 darts. Trivia 501 follows the same rules.

### Valid Scores (1-180)

A score is valid if it can be achieved with **3 darts** on a standard dartboard.

**Valid Examples**: 1-180 **EXCEPT** the invalid scores listed below

### Invalid Darts Scores

The following scores between 1-180 are **IMPOSSIBLE** in darts and therefore **invalid**:

```
163, 166, 169, 172, 173, 175, 176, 178, 179
```

### What Happens with Invalid Scores?

If a player's statistic equals an invalid darts score (e.g., 179 appearances):
- The answer is treated as a **bust**
- **No points are deducted**
- The turn is **wasted**
- Play passes to the opponent

**Example**:
- Question: "Appearances for Arsenal in Premier League"
- Answer: Player with **179 appearances**
- Result: **0 points scored** (bust)
- Player's score remains unchanged

---

## Game Flow

### 1. Game Start

1. Question is randomly selected (or predetermined for daily challenge)
2. Question is displayed to both players
3. Turn order is determined (Player 1 goes first)
4. Turn timer starts

### 2. Turn Sequence

```
Player 1's Turn → Player 2's Turn → Player 1's Turn → ...
```

Players alternate until one player checks out.

### 3. Submitting an Answer

On each turn, the active player must:

1. Type a player's name (e.g., "Sergio Aguero")
2. Submit before timer expires
3. Wait for validation

### 4. Answer Validation

The system validates the answer:

1. **Fuzzy Matching**: Handles spelling variations (e.g., "Aguero" = "Agüero")
2. **Question Match**: Ensures the player matches the question criteria
3. **Duplicate Check**: Ensures the answer hasn't been used already this game
4. **Score Validity**: Checks if the score is a valid darts score

### 5. Score Update

Based on validation:

- ✅ **Valid Answer**: Score is deducted, turn ends
- ❌ **Invalid Answer**: Player can retry immediately (timer keeps running)
- ❌ **Duplicate Answer**: Player can retry immediately
- ⚠️ **Bust** (invalid darts score or >180): No score deducted, turn wasted

### 6. Next Turn

After a valid submission or timeout:
- Play passes to the opponent
- Timer resets (unless consecutive timeouts occurred)
- Opponent's turn begins

### 7. Game End

Game ends when:
- A player **checks out** (reaches -10 to 0)
- A player **forfeits**
- Both players agree to **draw** (🔄 IN PROGRESS - not yet implemented)

---

## Turn Rules

### Turn Timer

- **Default**: 45 seconds per turn
- **Configurable**: Can be set at match start (30s, 45s, 60s, etc.)
- Timer starts when it becomes your turn
- Timer pauses if system is validating an answer

### Timeout Consequences

If a player's timer reaches **0:00** before submitting:

| Timeout # | Consequence | Next Turn Timer |
|-----------|-------------|-----------------|
| **1st** (non-consecutive) | Turn forfeited, no score change | 45s |
| **1st consecutive** | Turn forfeited, no score change | 45s |
| **2nd consecutive** | Turn forfeited, no score change | **30s** |
| **3rd consecutive** | Turn forfeited, no score change | **15s** |
| **4th consecutive** | **Match forfeited** (opponent wins) | N/A |

**Note**: A "consecutive timeout" means timing out on back-to-back turns. If you answer successfully, the timeout counter resets.

### Invalid Answer Handling

If a player submits an **invalid answer** (player doesn't exist, wrong team, misspelling not recognized):

- ❌ Instant rejection message shown
- ⏱️ Timer **continues running**
- 🔄 Player can immediately retry
- No penalty for invalid guesses (but wastes time)

---

## Winning Conditions

### Checkout Range

A player **checks out** when their score reaches the range **-10 to 0** (inclusive).

**Valid Checkout Scores**:
```
0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10
```

**Invalid Checkout Scores** (below -10):
```
-11, -12, -13, ... (game continues)
```

### Standard Win

**Player A wins** if:
- Player A reaches checkout range (-10 to 0)
- Player B has NOT yet checked out

**Example**:
- Player A: 12 points remaining
- Player A names a player with 15 appearances
- Player A's score: 12 - 15 = **-3** ✅ **Checked out!**
- Player B: 150 points remaining
- **Result**: Player A wins immediately

### Close Finish Rule

If **Player 1 checks out first**, **Player 2 gets ONE final turn** to attempt a closer checkout.

#### Scenario 1: Player 1 Checks Out, Player 2 Also Checks Out Closer

1. Player 1 checks out at **-9**
2. Player 2 gets one final turn
3. Player 2 names a player that brings score to **-5**
4. **Player 2 wins** (closer to 0)

#### Scenario 2: Player 1 Checks Out, Player 2 Checks Out Further

1. Player 1 checks out at **-3**
2. Player 2 gets one final turn
3. Player 2 names a player that brings score to **-8**
4. **Player 1 wins** (closer to 0)

#### Scenario 3: Player 1 Checks Out, Player 2 Fails to Check Out

1. Player 1 checks out at **-7**
2. Player 2 gets one final turn
3. Player 2 names a player but remains above 0 (e.g., 50 points left)
4. **Player 1 wins**

### Bust on Checkout Attempt

If a player would go **below -10**:

- Score **remains unchanged** (bust)
- Turn is wasted
- Opponent gets their turn
- Player can try again on next turn

**Example**:
- Player A: 5 points remaining
- Player A names a player with 20 appearances
- Calculation: 5 - 20 = **-15** (below -10)
- **Result**: Bust! Score stays at 5

---

## Match Formats

### Best of 1 (Single Game)

- One question, one game
- Winner of the game wins the match

### Best of 3

- First to win **2 games** wins the match
- Each game uses a **different question**
- Possible outcomes: 2-0, 2-1

### Best of 5

- First to win **3 games** wins the match
- Each game uses a **different question**
- Possible outcomes: 3-0, 3-1, 3-2

### Tiebreaker (Best of 3/5)

If a tiebreaker game is needed:
- **Special tiebreaker question** is selected
- **Objective**: Get closest to exactly **50 points remaining**
- Player closest to 50 wins the tiebreaker
- If tied at 50, sudden death continues (alternate turns until someone is closer)

🔄 **IN PROGRESS**: Tiebreaker rules may be refined

---

## Question Types

### Type 1: Team League Appearances

**Format**: "Appearances for [Team] in [League]"

**Example**: "Appearances for Manchester City in Premier League"

**Valid Answers**:
- Any player who has made appearances for Manchester City in the Premier League
- Score = Total appearances in Premier League for Man City

---

### Type 2: Team League Appearances + Goals (Combined)

**Format**: "Appearances + Goals for [Team] in [League]"

**Example**: "Appearances + Goals for Liverpool in Premier League"

**Valid Answers**:
- Any player who has made appearances for Liverpool in the Premier League
- **Score = Appearances + Goals** (summed)

**Example Calculation**:
- Mohamed Salah: 250 appearances, 150 goals
- Score: 250 + 150 = **400 points**

---

### Type 3: Team League Appearances + Clean Sheets (Goalkeepers)

**Format**: "Appearances + Clean Sheets for [Team] in [League]"

**Example**: "Appearances + Clean Sheets for Chelsea in Premier League"

**Valid Answers**:
- Goalkeepers who played for Chelsea in Premier League
- **Score = Appearances + Clean Sheets** (summed)

**Example Calculation**:
- Petr Čech: 333 appearances, 162 clean sheets
- Score: 333 + 162 = **495 points**

---

### Type 4: International Appearances

**Format**: "Appearances for [Country] in [Competition]"

**Example**: "Appearances for Brazil in World Cup"

**Valid Answers**:
- Any player who represented Brazil in World Cup matches
- Score = Total World Cup appearances for Brazil

---

### Type 5: League Appearances by Nationality

**Format**: "Appearances in [League] by players from [Country/Continent]"

**Example**: "Appearances in Premier League by players from Spain"

**Valid Answers**:
- Any Spanish player who has appeared in the Premier League
- Score = Total Premier League appearances (for any team)

---

## Special Rules

### Question Refresh

#### When Available
- **Only at game start** (before any answers are submitted)
- Not available once first answer is given

#### How It Works
1. Either player clicks **"Request New Question"**
2. Opponent sees popup: **"Opponent wants to refresh question. Accept?"**
3. If opponent clicks **"Yes"**: New question is loaded, game starts fresh
4. If opponent clicks **"No"**: Current question remains, game continues

#### Limitations
- Only **one refresh opportunity** per game
- Both players must see and agree to the new question

---

### Answer Reuse Rules

**Within a single game**:
- ❌ **Cannot reuse answers** that have already been named
- ✅ **Can use same player** in different games within a match

**Example**:
- Game 1: Player A names "Agüero" → Valid
- Game 1: Player B names "Agüero" → **Invalid** (already used)
- Game 2 (new question): Player A names "Agüero" → **Valid** (new game)

---

### Disconnection Rules

If a player disconnects during a game:

1. **Grace Period**: 30 seconds to reconnect
2. **During Grace Period**: Timer is paused, game is paused
3. **If Reconnected**: Game resumes from exact state
4. **If Not Reconnected**: Disconnected player forfeits the match

---

### Forfeit

A player can **forfeit** at any time:
- Click "Forfeit Match" button
- Opponent wins immediately
- Forfeit counts as a loss in record

---

## Examples

### Example 1: Standard Game

**Question**: "Appearances for Arsenal in Premier League"

| Turn | Player | Answer | Score Deducted | New Score | Notes |
|------|--------|--------|----------------|-----------|-------|
| 1 | P1 | Thierry Henry | 258 | 243 | Valid |
| 2 | P2 | Dennis Bergkamp | 315 | 186 | Valid |
| 3 | P1 | Patrick Vieira | 279 | -36 | **Bust!** (Over -10) |
| 3 | P1 | (retry) | (score unchanged) | 243 | Bust, score stays |
| 4 | P2 | Tony Adams | 255 | -69 | **Bust!** (Over -10) |
| 5 | P1 | David Seaman | 141 | 102 | Valid |
| 6 | P2 | Ian Wright | 221 | -35 | **Bust!** (Over -10) |
| 7 | P1 | Freddie Ljungberg | 216 | -114 | **Bust!** (Over -10) |
| 7 | P1 | Robert Pires | 189 | -87 | **Bust!** (Over -10) |
| 7 | P1 | Cesc Fàbregas | 212 | -110 | **Bust!** (Over -10) |
| 7 | P1 | Bukayo Saka | 98 | 4 | Valid |
| 8 | P2 | Aaron Ramsey | 262 | -297 | **Bust!** (Over -10) |
| 9 | P1 | Gabriel Jesus | 4 | **0** | ✅ **P1 WINS!** |

---

### Example 2: Close Finish

**Question**: "Goals for Barcelona in La Liga"

| Turn | Player | Answer | Score | Notes |
|------|--------|--------|-------|-------|
| ... | ... | ... | ... | (earlier turns) |
| 12 | P1 | Rivaldo | 130 | Score: 15 |
| 13 | P2 | Luis Suárez | 147 | Score: 22 |
| 14 | P1 | Ronaldinho | 94 | Score: -79 | **Bust!** |
| 15 | P2 | Neymar | 68 | Score: -46 | **Bust!** |
| 16 | P1 | Samuel Eto'o | 108 | Score: -93 | **Bust!** |
| 17 | P2 | Romário | 30 | Score: **-8** | ✅ **P2 checked out!** |
| 18 | P1 | *Final turn* | Carles Puyol (6 goals) | Score: **-85** | Failed to checkout |

**Result**: Player 2 wins (-8 is closer to 0 than P1's -85)

---

### Example 3: Timeout Escalation

**Question**: "Appearances for Real Madrid in La Liga"

| Turn | Player | Event | Timer Next Turn | Notes |
|------|--------|-------|-----------------|-------|
| 1 | P1 | ⏱️ Timeout | 45s | 1st timeout |
| 2 | P2 | Answers | 45s | - |
| 3 | P1 | ⏱️ Timeout | 30s | 2nd consecutive |
| 4 | P2 | Answers | 45s | - |
| 5 | P1 | ⏱️ Timeout | 15s | 3rd consecutive |
| 6 | P2 | Answers | 45s | - |
| 7 | P1 | ⏱️ Timeout | - | **4th consecutive → Match forfeited** |

**Result**: Player 2 wins by forfeit

---

### Example 4: Invalid Darts Score

**Question**: "Appearances for Manchester United in Premier League"

| Turn | Player | Answer | Appearances | Result | Notes |
|------|--------|--------|-------------|--------|-------|
| 1 | P1 | Ryan Giggs | **179** | **BUST** | 179 is invalid darts score |
| 1 | P1 | (no retry needed) | - | Score: 501 | Turn wasted, no points |
| 2 | P2 | Wayne Rooney | 393 | **BUST** | Over 180 |
| 2 | P2 | - | - | Score: 501 | Turn wasted |
| 3 | P1 | David de Gea | 162 | ✅ Valid | Score: 339 |

---

## FAQ

### Can I use the same answer twice in one game?
❌ **No**. Once an answer is used in a game, it cannot be repeated.

### What if I misspell a name?
✅ The system uses **fuzzy matching**. "Aguero" will match "Agüero". However, if the system can't recognize the name, you'll get instant feedback to retry.

### What if two players match closely?
The system prefers the **valid answer** (even if it's a bust) when multiple matches exist.

### Can I go below -10?
❌ **No**. Scores below -10 result in a **bust** (turn wasted, score unchanged).

### What happens if both players time out repeatedly?
Each player's timeout consequences are **independent**. If both players hit 4 consecutive timeouts, both forfeit and the match is a draw (🔄 IN PROGRESS - edge case).

### Can I practice the rules?
✅ Yes! Use **AI Practice Mode** to learn the rules and test strategies.

---

---

## Multiplayer Rules (Reference — Not Yet Implemented)

Multiplayer has been deferred indefinitely (2026-06-08). The rules below are retained for design reference only. No code currently implements them. When multiplayer returns, the schema and engine will be rebuilt from a real product spec.

### Close-Finish Rule
When Player 1 checks out, Player 2 gets one final turn before a winner is declared. If P2 also checks out, the player closer to 0 wins; if equal, it is a draw.

### Turn Alternation
Players alternate turns. A BUST or INVALID move switches the turn without changing either player's score.

### Opponent Timeout Forfeit
Three consecutive timeouts forfeit the game; the opponent is declared the winner. In solo play, three timeouts trigger a bust-out (no winner).

---

## Rule Updates

This document represents the **official** game rules as of **2026-01-17**.

- Rule changes will be versioned and communicated to players
- Major changes will be announced in-app
- Rule clarifications may be added to FAQ section

---

**Last Updated**: 2026-01-17
**Version**: 1.0
**Status**: Official
