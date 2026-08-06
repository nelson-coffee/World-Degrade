package dev.ncn.worlddegrade.command;

import com.mojang.brigadier.Command;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.net.OpenDegradeGuiPayload;
import dev.ncn.worlddegrade.undo.UndoManager;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class DegradeCommand {

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
                .then(Commands.literal("playerblockset")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack wand = new ItemStack(dev.ncn.worlddegrade.item.ModItems.MARKER_WAND.get());
                            if (!player.getInventory().add(wand)) {
                                player.drop(wand, false);
                            }
                            player.sendSystemMessage(
                                    net.minecraft.network.chat.Component.translatable("chat.worlddegrade.wand.given"));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private DegradeCommand() {
    }
}
