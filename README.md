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

## Known Issues

- Assembled create contraptions including trains are extremely buggy as of now the current fix is to disassemble all contraptions before a degrade or sometimes a server restart fixes it

## Note

A Fabric and Forge version will be coming in the future
