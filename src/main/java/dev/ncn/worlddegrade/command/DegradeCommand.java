package dev.ncn.worlddegrade.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.DegradeResult;
import dev.ncn.worlddegrade.degrade.DegradeService;
import dev.ncn.worlddegrade.item.ModItems;
import dev.ncn.worlddegrade.net.OpenDegradeGuiPayload;
import dev.ncn.worlddegrade.schedule.DegradeSchedule;
import dev.ncn.worlddegrade.schedule.ScheduleResult;
import dev.ncn.worlddegrade.schedule.ScheduleService;
import dev.ncn.worlddegrade.schedule.ScheduledDegradations;
import dev.ncn.worlddegrade.undo.UndoManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class DegradeCommand {

    /**
     * Guards the chunk-rectangle command against a typo allocating billions of entries.
     *
     * <p>It bounds the chunk set, not the undo snapshot. A run this size on a densely built server
     * still accumulates every touched block in memory before one blocking {@code NbtIo} write at the
     * end — the same exposure the whole-dimension GUI mode has always had, but easy to hit
     * deliberately here. Lower it if that write starts showing up as a tick spike.
     */
    public static final int MAX_AREA_CHUNKS = 10_000;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("degrade")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(player, new OpenDegradeGuiPayload());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("undo")
                        .executes(context -> UndoManager.undo(context.getSource())))
                .then(Commands.literal("area")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                                .executes(DegradeCommand::degradeArea)))))
                .then(Commands.literal("chunks")
                        .then(Commands.argument("fromChunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("fromChunkZ", IntegerArgumentType.integer())
                                        .then(Commands.argument("toChunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("toChunkZ", IntegerArgumentType.integer())
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                                                .executes(DegradeCommand::degradeChunks)))))))
                .then(Commands.literal("schedule")
                        .then(Commands.literal("add")
                                .then(Commands.argument("fromChunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("fromChunkZ", IntegerArgumentType.integer())
                                                .then(Commands.argument("toChunkX", IntegerArgumentType.integer())
                                                        .then(Commands.argument("toChunkZ", IntegerArgumentType.integer())
                                                                .executes(DegradeCommand::scheduleArea))))))
                        .then(Commands.literal("list")
                                .executes(DegradeCommand::scheduleList))
                        .then(Commands.literal("cancel")
                                .then(Commands.literal("all")
                                        .executes(DegradeCommand::scheduleCancelAll))
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(DegradeCommand::scheduleCancel))))
                .then(Commands.literal("opac")
                        .then(Commands.literal("simulate")
                                .then(Commands.argument("fromChunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("fromChunkZ", IntegerArgumentType.integer())
                                                .then(Commands.argument("toChunkX", IntegerArgumentType.integer())
                                                        .then(Commands.argument("toChunkZ", IntegerArgumentType.integer())
                                                                .executes(context -> opacSimulate(context, false))
                                                                .then(Commands.argument("expireClaims", BoolArgumentType.bool())
                                                                        .executes(context -> opacSimulate(context,
                                                                                BoolArgumentType.getBool(context, "expireClaims"))))))))))
                .then(Commands.literal("playerblockset")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack wand = new ItemStack(ModItems.MARKER_WAND.get());
                            if (!player.getInventory().add(wand)) {
                                player.drop(wand, false);
                            }
                            player.sendSystemMessage(
                                    Component.translatable("chat.worlddegrade.wand.given"));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private static int degradeArea(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        int levelId = IntegerArgumentType.getInteger(context, "level");
        DegradeArea.Box box = DegradeArea.clampedBox(
                from, to, level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (box == null) {
            source.sendFailure(Component.translatable("chat.worlddegrade.area.out_of_world",
                    level.getMinBuildHeight(), level.getMaxBuildHeight() - 1));
            return 0;
        }
        return runManual(source, box, levelId);
    }

    private static int degradeChunks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int fromX = IntegerArgumentType.getInteger(context, "fromChunkX");
        int fromZ = IntegerArgumentType.getInteger(context, "fromChunkZ");
        int toX = IntegerArgumentType.getInteger(context, "toChunkX");
        int toZ = IntegerArgumentType.getInteger(context, "toChunkZ");
        int levelId = IntegerArgumentType.getInteger(context, "level");
        long requested = DegradeArea.chunkRectangleCount(fromX, fromZ, toX, toZ);
        if (requested > MAX_AREA_CHUNKS) {
            source.sendFailure(Component.translatable("chat.worlddegrade.area.toobig",
                    MAX_AREA_CHUNKS, requested));
            return 0;
        }
        return runManual(source, DegradeArea.chunkRectangle(fromX, fromZ, toX, toZ), levelId);
    }

    /**
     * Shared path for the manual admin commands. Maps the service's rejection reason to a message
     * rather than re-deriving it, and keeps the undo snapshot since these are intentional one-offs.
     */
    private static int runManual(CommandSourceStack source, DegradeArea area, int levelId) {
        DegradeChances chances = DegradeChances.of(DegradeLevel.byId(levelId));
        ServerPlayer player = source.getPlayer();
        UUID operator = player != null ? player.getUUID() : null;
        DegradeResult result = DegradeService.start(source.getLevel(), area, chances, true, operator);
        if (!result.started()) {
            source.sendFailure(Component.translatable(result.messageKey()));
            return 0;
        }
        // A player already got chat.worlddegrade.start from the job itself; only the console, where
        // that operator message is a no-op, needs this synchronous acknowledgement.
        if (player == null) {
            DegradeJob job = result.job();
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.area.started",
                    job.totalChunks(), job.totalCompatTargets(), levelId), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int scheduleArea(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int fromX = IntegerArgumentType.getInteger(context, "fromChunkX");
        int fromZ = IntegerArgumentType.getInteger(context, "fromChunkZ");
        int toX = IntegerArgumentType.getInteger(context, "toChunkX");
        int toZ = IntegerArgumentType.getInteger(context, "toChunkZ");
        long requested = DegradeArea.chunkRectangleCount(fromX, fromZ, toX, toZ);
        if (requested > ScheduleService.MAX_CHUNKS) {
            source.sendFailure(Component.translatable("chat.worlddegrade.area.toobig",
                    ScheduleService.MAX_CHUNKS, requested));
            return 0;
        }
        LongOpenHashSet packed = DegradeArea.chunkRectangle(fromX, fromZ, toX, toZ).packedChunks();
        ScheduleResult result = ScheduleService.schedule(source.getLevel(), packed);
        if (!result.created()) {
            source.sendFailure(Component.translatable(result.messageKey()));
            return 0;
        }
        // The claimed count, not the requested one: chunks already covered by another schedule are
        // dropped, so a rectangle overlapping an existing schedule covers fewer than it asked for.
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.created",
                result.id(), result.chunkCount()), true);
        // Say this up front: without placement tracking there is nothing for a pass to degrade, and the
        // only other sign is a WARN in the server log an hour later when the first pass finds nothing.
        if (!WorldDegradeConfig.placementTrackingEnabled()) {
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.untracked")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int scheduleList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<ScheduledDegradations.Entry> entries = new ArrayList<>(ScheduleService.activeSchedules(level));
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.list.empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        entries.sort(Comparator.comparingInt(ScheduledDegradations.Entry::id));
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.list.header",
                entries.size()), false);
        boolean enabled = WorldDegradeConfig.scheduleEnabled();
        long now = level.getGameTime();
        for (ScheduledDegradations.Entry entry : entries) {
            // Each entry counts down on its own table (OPAC vs global, #6) and against its own
            // inhabited threshold.
            DegradeSchedule schedule = WorldDegradeConfig.schedule(entry.source());
            int threshold = WorldDegradeConfig.threshold(entry.source());
            // While the feature is off the clocks are frozen, so a countdown would be a lie — say so
            // instead of printing a remaining time that is not ticking down.
            boolean paused = !enabled || schedule.isEmpty();
            String nextLevel = "-";
            Component timing;
            if (entry.nextPass() >= schedule.passes().size()) {
                timing = Component.translatable("chat.worlddegrade.schedule.list.nopass");
            } else {
                DegradeSchedule.Pass pass = schedule.passes().get(entry.nextPass());
                nextLevel = String.valueOf(pass.levelId());
                if (paused) {
                    timing = Component.translatable("chat.worlddegrade.schedule.list.paused");
                } else {
                    long ticksLeft = Math.max(0, entry.triggerGameTime() + pass.delayTicks() - now);
                    long minutesLeft = (ticksLeft + DegradeSchedule.MINUTE_TICKS - 1) / DegradeSchedule.MINUTE_TICKS;
                    timing = Component.translatable("chat.worlddegrade.schedule.list.due", minutesLeft);
                }
            }
            // Only worth showing once something has actually been built there, and only when the
            // inhabited check is switched on at all.
            Component uses = threshold > 0 && entry.uses() > 0
                    ? Component.translatable("chat.worlddegrade.schedule.list.uses", entry.uses(), threshold)
                    : Component.empty();
            String passLevel = nextLevel;
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.list.entry",
                    entry.id(), entry.chunkCount(), passLevel, timing, uses), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int scheduleCancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!ScheduleService.cancel(source.getLevel(), id)) {
            source.sendFailure(Component.translatable("chat.worlddegrade.schedule.notfound", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.cancelled", id), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int scheduleCancelAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int removed = ScheduleService.cancelAll(source.getLevel());
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.cancelled.all", removed), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Dev/QA helper: drives the OPAC claim-expiration path on demand. OPAC only expires claims of
     * players inactive for hours, which cannot be reproduced quickly in a test session, so this feeds
     * the selected chunks into the same batcher OPAC's expiration callback uses. An OPAC-sourced
     * schedule then appears within a couple of seconds. It does not fake the OPAC claim data, so the
     * post-pass unclaim only ever drops chunks OPAC itself still marks as expired.
     */
    private static int opacSimulate(CommandContext<CommandSourceStack> context, boolean expireClaims) {
        CommandSourceStack source = context.getSource();
        if (!ScheduleService.opacSimulationAvailable()) {
            source.sendFailure(Component.translatable("chat.worlddegrade.opac.unavailable"));
            return 0;
        }
        int fromX = IntegerArgumentType.getInteger(context, "fromChunkX");
        int fromZ = IntegerArgumentType.getInteger(context, "fromChunkZ");
        int toX = IntegerArgumentType.getInteger(context, "toChunkX");
        int toZ = IntegerArgumentType.getInteger(context, "toChunkZ");
        long requested = DegradeArea.chunkRectangleCount(fromX, fromZ, toX, toZ);
        if (requested > ScheduleService.MAX_CHUNKS) {
            source.sendFailure(Component.translatable("chat.worlddegrade.area.toobig",
                    ScheduleService.MAX_CHUNKS, requested));
            return 0;
        }
        LongOpenHashSet packed = DegradeArea.chunkRectangle(fromX, fromZ, toX, toZ).packedChunks();
        int queued = ScheduleService.simulateOpacExpiration(source.getLevel(), packed, expireClaims);
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.opac.simulated", queued), true);
        // Head off the natural follow-up "why wasn't the claim removed?": by default the claim-removal
        // half is a no-op because no chunk is actually expired. expireClaims = true forces the chunks to
        // OPAC's expired owner so the whole unclaim path is exercised.
        if (expireClaims) {
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.opac.simulated.expire", queued)
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.opac.simulated.note")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (!WorldDegradeConfig.placementTrackingEnabled()) {
            source.sendSuccess(() -> Component.translatable("chat.worlddegrade.schedule.untracked")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private DegradeCommand() {
    }
}
