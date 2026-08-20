# World Degrade
 
### A mod for turning player made structures into ruins
***

This is a Minecraft Mod that adds one thing

- the /degrade command

But that one command does quite a lot, it converts all player built structures into ruins.
## How it works

From the start of your world every player placed block is tracked and stored then when you run the degrade command the following happens:

- All inventory blocks are scanned and looted leaving a percentage of the original items behind
- All player placed blocks are found and go through a degrade cycle
- dug out tunnels and rooms collapse

You can also run /degrade undo to restore your world.
But thats just for vanilla Minecraft if you have any of the following mods installed there are a few other things that happen; Create, Create aeronautics, Sable, Waystones, Chipped, Exposure, CC:Tweaked and/or Supplementaries

- For Exposure Photos left in frames and chests age and film left in chests develops scratches and chemical/water damage (by far one of the coolest features)
- For waystones all waystones are deleted from players known waystones and the regular waystones develop into mossy wasystones
- In create everything breaks with a percentage chance with the chance for cogs to fall off to the ground
- This mod also has support for degrading sable sub-levels so your create aeronautics ships can degrade just fine
- In CC:Tweaked any files on computers or floppy disks have the chance to get corrupted
- All of Chipped's blocks are supported
- All of Rechiseled's blocks are supported
- Literally the only thing for Supplementaries was making globes degrade into sepia globes

Any blocks not recognised my the mod will just get destroyed at a percentage chance.

## Tags, datapacks and the generate command

You no longer need a dedicated compat module for every mod. Block categorisation and wear chains are layered, so the built-in hardcoded defaults are always the fallback and nothing breaks if you never touch any of this.

### Block tags (which effect applies)

Tag a block with one of the `#worlddegrade:` block tags and it becomes eligible for that effect — no datapack required. If a tagged block has no wear chain, the effect is destruction (block → air), e.g. glass shattering or wood rotting.

- `#worlddegrade:wood_rot`
- `#worlddegrade:glass_break`
- `#worlddegrade:door_break`
- `#worlddegrade:masonry_decay`
- `#worlddegrade:light_snuff`
- `#worlddegrade:exempt` (never degrades)

**Which tags are easy to see:** `wood_rot` and `glass_break` end in destruction, which is also what an *un-categorised* block does by default, so the visible result is the same either way — they mostly change *which effect owns* the block, not the outcome. The clearly observable ones are `exempt` (tag a block that would normally crumble and it now survives untouched) and `masonry_decay` / wear chains (the block steps through intermediate variants instead of vanishing). Use those when you want to confirm the system is doing something.

### Datapack chains

Drop JSON into `data/<namespace>/chains/` in any datapack. Three entry types are understood:

```json
{ "type": "worlddegrade:wear_chain",
  "chain": ["minecraft:copper_block", "minecraft:exposed_copper", "minecraft:weathered_copper", "minecraft:oxidized_copper"] }
```
```json
{ "type": "worlddegrade:chain_entry", "from": "minecraft:waxed_copper_block", "into": "minecraft:copper_block" }
```
```json
{ "type": "worlddegrade:block_category", "category": "wood",
  "add": ["somemod:custom_plank"], "remove": ["minecraft:crimson_planks"] }
```

Overrides are **per-chain**: a datapack chain for `cobblestone` replaces only cobblestone's built-in link; everything it doesn't mention keeps the built-in default. Lookup order per block is **datapack → built-in → tag-based destruction**. Everything under `chains/` (including subfolders) is loaded recursively.

### Loading the bundled examples

The repo carries an example datapack (`copper_block`, `waxed_copper_block`, `exempt_stone_bricks`, `wood_category`). It is a **development/testing aid only — it is not offered to normal players.** The pack finder is skipped in a production (shipped) environment, so players never see it in `/datapack list`; it only appears when running the mod from a dev environment (`runClient`/`runServer`). Even there it is **not active by default** — you switch it on per world:

- **Existing world (dev run):** run `/datapack enable "mod/worlddegrade:example_datapack"`, then `/reload`.
- **New world (dev run):** on the create-world screen open **Data Packs**, drag *World-Degrade Examples* to the left (Selected), then create the world.
- **Check it's on:** `/datapack list` should list it under *Available/Enabled*.

Normal players don't need any of this — the built-in defaults and `#worlddegrade:` tags already work out of the box. If you *do* want these mappings in a shipped world, copy the JSON from `src/main/resources/example_datapack/data/worlddegrade/chains/` into your own world datapack's `chains/` folder.

Once enabled you can watch two clearly visible effects:

- **Waxed copper starts degrading** (`waxed_copper_block → copper_block → exposed → weathered → oxidized_copper`). Waxed copper never weathers on its own — it only moves once this pack (or your own chain for it) is loaded.
- **Stone bricks stop degrading.** Left alone, stone bricks visibly wear `stone_bricks → cracked_stone_bricks → cobblestone → mossy_cobblestone`; the `exempt` example freezes them so you can watch un-exempt stone bricks crack next to exempt ones that stay pristine.

The exempt example targets **stone bricks** on purpose: they have a real, visible wear chain, so exemption is easy to observe. `exempt` now stops wear chains too (not just the slow break-to-air step), so an exempt block genuinely never degrades.

To customise, copy the JSON out of the pack into any world datapack's `chains/` folder and edit it there.

### `/worlddegrade generate`

Run it once after loading your world with your modpack:

1. It scans the whole block registry and applies naming heuristics (`polished_/smooth_/cut_/waxed_ → base`, `chiseled_X → cracked_X`, `X → cracked_X/cobbled_X/mossy_X`, same-namespace only).
2. It writes a datapack to `<world>/datapacks/worlddegrade-generated/`, **one file per namespace subfolder** so a big modpack stays browsable:
   - `data/worlddegrade/chains/<modid>/<block>.json` — active auto-detected modded chains (loaded on the next `/reload` or restart).
   - `data/worlddegrade/chains_reference/<modid>/<block>.json` — the built-in vanilla chains, written for reference only (never loaded). Copy one into `chains/` to tweak it.
3. It logs a summary like `Found 247 wear chains across 12 namespaces`.

**One file vs. a longer chain:** detection gives each block a single target, so the links are stitched into chains. A file is written per *chain head* — a block that nothing else degrades into — named after that head, and its `chain` array is the full walk from the head, following each block's target until the trail ends. So you get a longer chain when the detected links connect transitively (e.g. `chiseled_x → cracked_x → x` becomes one file, `chiseled_x.json`, with all three), and a short two-block file when a head's target has no onward link of its own. A block that is only ever a *target* never gets its own file — it appears inside its predecessor's chain.

Re-running overwrites the whole `worlddegrade-generated/` tree, so back up any manual edits in `chains/` first.

## Progressive degradation (schedule)

Instead of ruining an area in one shot, you can schedule it to fall apart in stages over time — lightly weathered first, then damaged, then collapsed. This is opt-in and off by default.

Turn it on in the server config under `[schedule]`:

```toml
[schedule]
enabled = true
# Delays in real-life MINUTES after the schedule is triggered (1 minute = 1200 ticks of server
# runtime at the normal 20 tps). Paired by index with passLevels.
passDelays = [60, 720, 1440]
passLevels = [1, 3, 5]
# How many blocks must be PLACED inside a scheduled area before the whole schedule is cancelled.
# 0 turns the inhabited check off entirely.
releaseBlockThreshold = 1
# Whether blocks fired by a Create schematicannon count toward releaseBlockThreshold.
schematicannonCountsAsInhabited = true
```

Trigger and manage schedules with:

- `/degrade schedule add <fromChunkX> <fromChunkZ> <toChunkX> <toChunkZ>` — schedule an area of chunks
- `/degrade schedule list` — list active schedules with their next pass and minutes remaining
- `/degrade schedule cancel <id>` — cancel one schedule
- `/degrade schedule cancel all` — cancel every schedule in this dimension

Notes:

- Timing uses server runtime (real-life minutes at 20 tps), so schedules do not advance while the server is offline, they slow if the server is lagging, and they survive restarts.
- **Setting `enabled = false` pauses schedules rather than letting them age.** Their clocks stop, so switching the feature back on resumes every schedule where it left off instead of finding all its passes overdue at once. While paused, `/degrade schedule list` says so instead of showing a countdown that is not ticking.
- If the scheduler still falls behind (a long manual run holding the job slot, chunks left unloaded) so that several passes come due at once, the schedule jumps straight to the highest overdue level in a single pass rather than firing every level within seconds.
- Scheduled passes never capture an undo snapshot (they run unattended over time). `/degrade undo` is only for manual runs.
- **Inhabited areas are spared:** once `releaseBlockThreshold` blocks have been *placed* anywhere inside a scheduled area, the whole schedule is cancelled — something is building there again. Breaking blocks (looting or mining a ruin) never counts. Anything acting through a player counts equally: a player, a fake player, a Create deployer, and each individual block fired by a Create schematicannon (counted against the chunk it is building *into*, not where the cannon stands). Placements with no entity behind them do not count — dispensers, pistons and Create rollers are invisible here, as are mobs. The counter resets after each pass, so it measures activity since the last pass, and `releaseBlockThreshold = 0` switches the check off entirely. Schematicannon fire can be excluded on its own with `schematicannonCountsAsInhabited = false`, which leaves the cannon's blocks tracked for degradation as before.
- Each pass only touches blocks that still exist, so blocks removed by an earlier pass are never re-processed. Scheduled degradation only affects blocks recorded by placement tracking, so it does nothing with `enablePlacementTracking = false` — `/degrade schedule add` warns you about this when you create a schedule, and a pass that finds nothing tracked is logged and skipped rather than silently cancelled.
- Create rollers pave blocks without going through a placement event, so they are invisible to block tracking and do not count as building either.

Other mods can drive this through the `ScheduleService` API (`schedule`, `markInUse`, `isScheduled`, `cancel`) — the schedule system is standalone and does not depend on any claim mod.

## Claim expiration (Open Parties and Claims)

With [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) (OPAC) installed, an abandoned claim can rot on its own. When a player's claim expires, its chunks are handed to the progressive schedule above, so the ruin appears over the same staged passes rather than all at once. This is optional and does nothing without OPAC.

> [!IMPORTANT]
> This only reacts to claims OPAC actually marks as *expired*. OPAC's own `playerClaimsConvertExpiredClaims` must be **off** — if OPAC is set to convert expired claims to server or wilderness claims instead, they never enter the expired state and this integration stays inert. OPAC-triggered runs never capture an undo snapshot.

Configure it under `[opac]`:

```toml
[opac]
# Master switch. When off, claim expirations are ignored.
enabled = true
# Use the OPAC pass table below instead of the shared [schedule] one.
useCustomSchedule = false
# Only used when useCustomSchedule = true. Real-life MINUTES after the claim expired, paired by
# index with customPassLevels — same units and pairing rules as [schedule].
customPassDelays = [7, 30, 60]
customPassLevels = [1, 3, 5]
# When to drop the expired claim so others can loot the ruin:
#   FINAL_PASS - after the last pass finishes (loot only at the very end)
#   FIRST_PASS - after the first pass finishes, then it keeps crumbling while looted (default)
#   SCHEDULE   - immediately when the schedule is created, before any degradation
#   NEVER      - leave the expired claim in place
removeClaimAfter = "FIRST_PASS"
# Overrides [schedule].releaseBlockThreshold for OPAC schedules only. 0 (default) means looters
# placing blocks never cancel the degradation.
releaseBlockThreshold = 0
```

Notes:

- The `[schedule]` feature must be enabled for OPAC schedules to run — the integration only feeds chunks into that system, it does not degrade anything on its own. A warning is logged at startup if OPAC is on while `[schedule]` is off.
- Thousands of chunks expiring at once are batched into a handful of schedules rather than one per chunk, and any leftover batch is flushed on server shutdown.
- The claim is only ever dropped if it is still owned by OPAC's expired-claim owner, so a chunk someone re-claims mid-schedule is left untouched. With `FIRST_PASS` or `SCHEDULE` the claim is released before the schedule could be cancelled, and OPAC does not expose the old owner, so the area simply stays unclaimed rather than being restored.

### When does a claim actually expire?

Expiration is driven entirely by OPAC, not by this mod. OPAC only expires a claim once its **owner has been completely offline/inactive for `playerClaimsExpirationTime` hours** (OPAC config, minimum 1 hour), and it checks for expired claims every `playerClaimsExpirationCheckInterval` minutes (rounded up to a multiple of 10). Crucially, `playerClaimsConvertExpiredClaims` must stay **off** — with it on, OPAC frees claims to wilderness/server instead of marking them expired, and nothing reaches this integration.

Because the owner must be offline, you cannot make a claim expire in single-player, and even on a dedicated server the fastest real path is: set `playerClaimsExpirationTime = 1`, claim some chunks with a test account, log that account out, and wait for the next expiration check.

### Testing without waiting (dev command)

To exercise the integration on demand there is an operator command (permission level 2), available only when OPAC is installed:

```
/degrade opac simulate <fromChunkX> <fromChunkZ> <toChunkX> <toChunkZ> [expireClaims]
```

It feeds the selected chunks into the exact pipeline OPAC's expiration callback uses (debounced batching → an OPAC-sourced schedule → the post-pass unclaim), so an OPAC schedule appears within ~2 seconds and then degrades on the `[opac]` table. By default (`expireClaims` omitted or `false`) it does **not** touch OPAC's claim data, so the unclaim step still only drops chunks OPAC itself marks as expired — running it over live claims is safe and simply leaves them claimed.

**Do I need to claim the chunks first? No.** The command drives the degradation half of the integration directly, so the only thing you need in the selected chunks is some **built, placement-tracked structure** — claims have no bearing on whether it degrades. What you *cannot* observe from the default `simulate` alone is the automatic unclaim, because a chunk can only reach OPAC's "expired" state if it was claimed and then expired by OPAC (expiration replaces an existing claim's owner; it never invents a claim on empty land). So:

- **To see degradation:** just build something in the chunks (with `enablePlacementTracking = true`) and run the command — no claiming needed.
- **To see the automatic unclaim too:** run `/degrade opac simulate <...> true`. The trailing `expireClaims = true` first rewrites the selected chunks to OPAC's expired-claim owner (exactly as a real expiration does), so the post-pass unclaim genuinely removes them. This **overwrites any claim already there**, so it is opt-in and defaults to off.

> Why won't my *own* live claim get removed? Because the unclaim step, by design, only ever drops chunks whose owner is OPAC's `EXPIRED` pseudo-player. A claim you still own is a live claim, not an expired one, so degradation runs but the claim stays — this is the intended safety guard against unclaiming land someone re-claimed mid-schedule. Use the `true` flag (or a genuine OPAC expiration) to reach the expired state the unclaim actually acts on.

## Known Issues

- Assembled create contraptions including trains are extremely buggy as of now the current fix is to disassemble all contraptions before a degrade or sometimes a server restart fixes it

## Note

A Fabric and Forge version will be coming in the future
