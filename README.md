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
