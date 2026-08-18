package dev.ncn.worlddegrade.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.DegradeResult;
import dev.ncn.worlddegrade.degrade.DegradeService;
import dev.ncn.worlddegrade.item.ModItems;
import dev.ncn.worlddegrade.net.OpenDegradeGuiPayload;
import dev.ncn.worlddegrade.undo.UndoManager;
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

    private DegradeCommand() {
    }
}
