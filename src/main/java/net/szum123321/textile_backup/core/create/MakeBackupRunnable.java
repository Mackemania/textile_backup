/*
 * A simple backup mod for Fabric
 * Copyright (C)  2022   Szum123321
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.szum123321.textile_backup.core.create;

import net.szum123321.textile_backup.Globals;
import net.szum123321.textile_backup.TextileBackup;
import net.szum123321.textile_backup.TextileLogger;
import net.szum123321.textile_backup.config.ConfigHelper;
import net.szum123321.textile_backup.core.ActionInitiator;
import net.szum123321.textile_backup.core.Cleanup;
import net.szum123321.textile_backup.core.Utilities;
import net.szum123321.textile_backup.core.create.compressors.ParallelZipCompressor;
import net.szum123321.textile_backup.core.create.compressors.ZipCompressor;
import net.szum123321.textile_backup.core.create.compressors.tar.AbstractTarArchiver;
import net.szum123321.textile_backup.core.create.compressors.tar.ParallelBZip2Compressor;
import net.szum123321.textile_backup.core.create.compressors.tar.ParallelGzipCompressor;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * The actual object responsible for creating the backup
 */
public class MakeBackupRunnable implements Callable<Void> {
    private final static TextileLogger log = new TextileLogger(TextileBackup.MOD_NAME);
    private final static ConfigHelper config = ConfigHelper.INSTANCE;

    private final BackupContext context;

    public MakeBackupRunnable(BackupContext context) {
        this.context = context;
    }

    @Override
    public Void call() throws IOException, ExecutionException, InterruptedException {
        Path outFile = Utilities.getBackupRootPath(Utilities.getLevelName(context.server())).resolve(getFileName());

        log.trace("Outfile is: {}", outFile);

        try {
            //I think I should synchronise these two next calls...
            Utilities.disableWorldSaving(context.server());
            Globals.INSTANCE.disableWatchdog = true;

            Globals.INSTANCE.updateTMPFSFlag(context.server());

            log.sendInfoAL(context, "Starting backup");

            Path world = Utilities.getWorldFolder(context.server());

            log.trace("Minecraft world is: {}", world);

            Files.createDirectories(outFile.getParent());
            Files.createFile(outFile);

            int coreCount;

            if (config.get().compressionCoreCountLimit <= 0) coreCount = Runtime.getRuntime().availableProcessors();
            else
                coreCount = Math.min(config.get().compressionCoreCountLimit, Runtime.getRuntime().availableProcessors());

            log.trace("Running compression on {} threads. Available cores: {}", coreCount, Runtime.getRuntime().availableProcessors());

            switch (config.get().format) {
                case ZIP -> {
                    if (coreCount > 1 && !Globals.INSTANCE.disableTMPFS()) {
                        log.trace("Using PARALLEL Zip Compressor. Threads: {}", coreCount);
                        ParallelZipCompressor.getInstance().createArchive(world, outFile, context, coreCount);
                    } else {
                        log.trace("Using REGULAR Zip Compressor.");
                        ZipCompressor.getInstance().createArchive(world, outFile, context, coreCount);
                    }
                }
                case BZIP2 -> ParallelBZip2Compressor.getInstance().createArchive(world, outFile, context, coreCount);
                case GZIP -> ParallelGzipCompressor.getInstance().createArchive(world, outFile, context, coreCount);
                case LZMA -> new AbstractTarArchiver() {
                    protected OutputStream getCompressorOutputStream(OutputStream stream, BackupContext ctx, int coreLimit) throws IOException {
                        return new LZMACompressorOutputStream(stream);
                    }
                }.createArchive(world, outFile, context, coreCount);
                case TAR -> new AbstractTarArchiver().createArchive(world, outFile, context, coreCount);
            }

            if(!Globals.INSTANCE.getQueueExecutor().isShutdown())
                Globals.INSTANCE.getQueueExecutor().submit(new Cleanup(context.commandSource(), Utilities.getLevelName(context.server())));

            if (config.get().broadcastBackupDone) Utilities.notifyPlayers(context.server(), "Done!");
            else log.sendInfoAL(context, "Done!");

        } catch (Throwable e) {
            //ExecutorService swallows exception, so I need to catch everything
            log.error("An exception occurred when trying to create new backup file!", e);

            if (ConfigHelper.INSTANCE.get().errorErrorHandlingMode.isStrict()) {
                try {
                    Files.delete(outFile);
                } catch (IOException ex) {
                    log.error("An exception occurred while trying go delete: {}", outFile, ex);
                }
            }

            if (context.initiator() == ActionInitiator.Player)
                log.sendError(context, "An exception occurred when trying to create new backup file!");

            throw e;
        } finally {
            Utilities.enableWorldSaving(context.server());
            Globals.INSTANCE.disableWatchdog = false;
        }

        return null;
    }

    private String getFileName() {
        return Utilities.getDateTimeFormatter().format(context.startDate()) + (context.comment() != null ? "#" + context.comment().replaceAll("[\\\\/:*?\"<>|#]", "") : "") + config.get().format.getCompleteString();
    }
}
