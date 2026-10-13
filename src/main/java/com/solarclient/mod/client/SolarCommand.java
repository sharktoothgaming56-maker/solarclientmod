package com.solarclient.mod.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The in-game {@code /solarclient} command.
 *
 *   /solarclient culling
 *
 * Roles (owner / admin / verify / solar+) are not managed here; they are set
 * on Discord with {@code !solarclient}.
 */
public final class SolarCommand {
    private SolarCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("solarclient");
            root.then(ClientCommandManager.literal("culling").executes(ctx -> {
                com.solarclient.mod.client.render.OcclusionCuller.enabled = !com.solarclient.mod.client.render.OcclusionCuller.enabled;
                feedback("Behind-walls culling: " + (com.solarclient.mod.client.render.OcclusionCuller.enabled ? "ON" : "OFF"), Formatting.WHITE);
                return 1;
            }));
            root.executes(ctx -> {
                feedback("Usage: /solarclient culling", Formatting.GRAY);
                return 1;
            });
            dispatcher.register(root);
        });
    }

    private static void feedback(String message, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(
                Text.literal("[SolarClient] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .append(Text.literal(message).formatted(color)),
                false);
    }
}
