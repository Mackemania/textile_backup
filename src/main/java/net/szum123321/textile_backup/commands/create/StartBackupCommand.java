/*
    A simple backup mod for Fabric
    Copyright (C) 2020  Szum123321

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package net.szum123321.textile_backup.commands.create;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.szum123321.textile_backup.Globals;
import net.szum123321.textile_backup.TextileBackup;
import net.szum123321.textile_backup.TextileLogger;
import net.szum123321.textile_backup.core.create.BackupContext;
import net.szum123321.textile_backup.core.create.BackupHelper;

import javax.annotation.Nullable;

public class StartBackupCommand {
    private final static TextileLogger log = new TextileLogger(TextileBackup.MOD_NAME);

    public static LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("start")
                .then(CommandManager.argument("comment", StringArgumentType.string())
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "comment")))
                ).executes(ctx -> execute(ctx.getSource(), null));
    }

    private static int execute(ServerCommandSource source, @Nullable String comment) {
        try {
            Globals.INSTANCE.getQueueExecutor().submit(
                    BackupHelper.create(
                            BackupContext.Builder
                                    .newBackupContextBuilder()
                                    .setCommandSource(source)
                                    .setComment(comment)
                                    .guessInitiator()
                                    .saveServer()
                                    .build()
                    )
            );
        } catch (Exception e) {
            log.error("Something went wrong while executing command!", e);
            throw e;
        }

        return 1;
    }
}
