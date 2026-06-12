# DECISIONS.md — judgment calls and rationale

A running log, one line of rationale each. Newest entries at the bottom of each section.

## M1 — Engine core

### Architecture & environment
- **Conditional `:app` module** — `settings.gradle.kts` includes `:app` only when an Android SDK is locatable; this dev environment has no SDK and no access to Google's Maven repo, so `:engine` must build/test standalone (it resolves entirely from Maven Central).
- **mavenCentral listed before google()** — engine dependency resolution never has to touch the (possibly unreachable) Google repo.
- **M1 commit includes EventSystem/HeirloomChecker/Automation** — the tick loop integrates them at day boundaries; stubbing them out of M1 would mean fake seams. Their dedicated tests land in M2 with the rest of the prestige systems.
- **JVM target 17 for the engine** — safe bytecode level for consumption by the Android module.

### Time scale (the big one)
- **Default tick rate is 1 game-day per 45 real seconds**, not the suggested 1s = 0.5 day. At 0.5 day/s a whole life lasts ~40 active minutes, so a 10h offline cap is ~93% unusable (offline sim halts at death) and generations burn out in one sitting — contradicting the check-in pacing acceptance criteria (day 1 = generation 1; weeks of play). At 45 s/day: 1 year ≈ 21 real minutes, a natural life ≈ 15 real hours ≈ one real day for a 2–3-check-in player, overnight sleep fits inside the 10h cap, and a 2-minute session advances ~2.7 game-days. The pacing targets are the stated acceptance criteria ("tune via simulation"), so they win. Rate stays configurable in `BalanceConfig`.

### World & calendar
- **The season calendar runs on `totalGameDays` and never resets** — heirs inherit mid-season; the world keeps turning across generations.
- **Tick boundary snapping** — `totalGameDays` is snapped to the exact integer day when a boundary is processed, so floating-point drift can never skip or double a day's event roll/season transition.
- **Level-ups are quantized to step ends** — exact step-size equivalence holds between thresholds; offline (1-day steps) and live (1s steps) can differ by at most a fraction of a day's yield around a level-up. Harmless and cheap.

### Activities
- **Resource assignments where the spec was loose:** Scout Ahead → materials (salvage/timber-finding; Era 1 needs a materials source to afford Era 2), Trap → materials (spec's resource table lists trapping under materials — furs), Plow Fields/Harvest/Raise Livestock → food, Run the Mill/Carpentry → materials, Land Office → money, Teach School/Print Newspaper/Found Institutions → standing.
- **Era 6 has a single activity (Found Institutions, standing)** — the era is an endgame sink; the Monument purchase is the real content.
- **Old-era activities stay available forever** — Era 2 has no money activity by design; Haul Cargo remains the money source until the General Store. Resource juggling with one activity slot is the core check-in decision.
- **Tags drive seasons:** PLANTING gets spring +20%, HARVEST gets fall +50%, OUTDOOR suffers winter −60%; indoor work (Blacksmith, store, banking…) is winter-proof — a real reason to switch work in winter.

### Survival
- **Starvation never kills** — food at 0 halves all yields, permanently forfeits the food-security death-age bonus for that life, and fails the winter (no Spring Bounty). Friendlier for a check-in game than offline death spirals; death comes from age alone.
- **Upkeep scales ×2.5 per era** — advancing is a real decision (bigger household), and food activities stay relevant in late eras.
- **Upkeep counts as a cost** — Thrift and Ledger & Quill reduce it; Mother's Recipes and the Old Almanac apply only to the winter multiplier.
- **Winter survival = food never hit zero between the first and last day of that winter** (`foodFailedThisWinter`), evaluated at the winter→spring boundary.

### Death & lifespan
- **Death age = 60 + 1y/era beyond Trail + 3y food security + 5y/Family Plot tier ± 4y** (random offset rolled once at birth, so a life's end is predictable-ish but not exact).
- **Offline simulation halts at death; remaining away-time is forfeited** — "retire before a long absence" becomes a real strategic choice, and the Legacy screen always gets its moment.

### Engine API
- **`pendingLegacy != null` freezes ticking** — the Legacy screen is a hard stop, not a modal over a running game; prevents posterity-spend racing.
- **Actions are pure functions with `can...()` companions** — UI enables/disables from the same predicate the action `require()`s.

## M1 — balance numbers (first pass; tuned in M2 via HeadlessRunner)
- Activity base yields tiered roughly ×6 per home era (1.2–2.2 → ~1900–2300/day at Era 5).
- Era advancement costs ×50 per era (spec range 40–80): 60M/40$ → 375M M/250M $ + standing gates from Village on.
- Era yield bonus: global +25% per era index (infrastructure), on top of tiered base yields.
- Activity XP: 1/day, next level costs 8 × 1.10^level (+8%/level self-speed per spec).
- Skill XP: 1/day, 5 × 1.12^level (spec curve), ×1.5 affinity when the active work matches the skill's domain (Grit↔outdoor, Husbandry↔food, Craftsmanship↔materials, Thrift↔money, Community↔standing).
- Thrift as a divisor 1/(1 + 0.008·level) — the spec's "soft cap" without a hard knee, can never hit zero.
- Craftsmanship cost reduction multiplicative 0.99^level, applied to the materials share of structured costs only.
- Community gates standing activities at levels 5/10/15 (low because skills reset every life).
- Value-score weights: food 0.3, materials 1.0, money 1.0, standing 5.0 — food is plentiful by design.
- Winter: drain ×3 (×4 hard winter), outdoor −60%, spring bounty +25% for the spring.
- Event chance 1/7 per day (≈1 per season), ×1.5 in winter.

## M2 — Prestige systems (design calls made in M1, implemented/tested in M2)
- **Heirlooms 7–12 (designed, ~12 total per spec):** Hunting Rifle (10K lifetime food → +20% food), Oxen Yoke (Grit 25 → +10% all), Quilt of Many Hands (Community 25 → +25% standing), Ledger & Quill (Thrift 25 → −10% all costs), Pioneer's Journal (age 70 → +10% all), Founder's Gavel (reach Era 6 → +50% posterity earned).
- **Posterity is AdCap-shaped:** one cumulative value score per monument cycle; payout = floor(K·√score) − alreadyEarned. The raw counter tracks the formula; the Gavel's +50% is applied on top of each grant so it is a true +50% (inflating the counter would make it self-cancelling).
- **Era-reached score bonus** added once per life at death: 400 × era^2.5.
- **Ventures (12):** the spec's six plus Preacher's Circuit (+50% standing/rank), Schoolhouse Fund (+25% skill XP/rank), Tool Shed (+2 starting activity levels/tier), Family Doctor (heirs start 2y younger/tier), Homestead Act Filing (era costs −20%), Old Almanac (winter penalties −25%).
- **Monument yield bonus is additive in the factor (1 + 1.0·n)**, not 2^n compounding — keeps late-game tuning sane while still "+100% per Monument".
- **Mechanic unlock schedule:** M1 automation, M2 starting-era bump, M3 second skill (half XP rate), M4 event mitigation (auto "choose wisely": negative events halved), M5 +6h offline cap; finalMonument = 6 (Future City vista).
- **Founding a Monument immediately starts a new line** — posterity was just zeroed so a Legacy screen would be an empty shop; the Monument celebration replaces it. Generation numbering stays monotonic across Monuments (nicer stats).
- **Railroad Arrives fires deterministically on entering Era 5** (once per life), not from the random pool — it's a milestone beat, not weather.
- **Merchant/County Fair windfalls = N days of the active activity's yield converted to money via the value-score weights** — scales with progression without a separate economy table.
- **Automation v1 = priority list + built-in food-reserve failsafe (30 days of upkeep)** — covers the dominant micro pattern (keep food up, otherwise grind the bottleneck) without a rules DSL.
