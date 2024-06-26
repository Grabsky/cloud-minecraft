//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud.examples.bukkit.builder.feature.minecraft;

import io.leangen.geantyref.TypeToken;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.bukkit.BukkitCommandManager;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.examples.bukkit.ExamplePlugin;
import org.incendo.cloud.examples.bukkit.builder.BuilderFeature;
import org.incendo.cloud.parser.ArgumentParseResult;

import static org.incendo.cloud.bukkit.parser.ItemStackParser.itemStackParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * Example showcasing the use of the native item stack parser.
 */
public final class ItemStackExample implements BuilderFeature {

    @Override
    public void registerFeature(
            final @NonNull ExamplePlugin examplePlugin,
            final @NonNull BukkitCommandManager<CommandSender> manager
    ) {
        manager.command(
                manager.commandBuilder("builder")
                        .literal("gib")
                        .senderType(Player.class)
                        .requiredArgumentPair(
                                "itemstack",
                                TypeToken.get(ItemStack.class),
                                "item", itemStackParser(),
                                "amount", integerParser(),
                                (sender, proto, amount) -> {
                                    try {
                                        return ArgumentParseResult.successFuture(proto.createItemStack(amount, true));
                                    } catch (final IllegalArgumentException ex) {
                                        return ArgumentParseResult.failureFuture(ex);
                                    }
                                },
                                Description.of("The ItemStack to give")
                        )
                        .handler(ctx -> {
                            final ItemStack stack = ctx.get("itemstack");
                            ctx.sender().getInventory().addItem(stack);
                        })
        );
    }
}
