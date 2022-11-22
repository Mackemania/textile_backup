/*
 * A simple backup mod for Fabric
 * Copyright (C) 2020  Szum123321
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

package net.szum123321.textile_backup.core.create.compressors;

import net.szum123321.textile_backup.TextileBackup;
import net.szum123321.textile_backup.TextileLogger;
import net.szum123321.textile_backup.core.ActionInitiator;
import net.szum123321.textile_backup.core.CompressionStatus;
import net.szum123321.textile_backup.core.NoSpaceLeftOnDeviceException;
import net.szum123321.textile_backup.core.Utilities;
import net.szum123321.textile_backup.core.create.BackupContext;
import net.szum123321.textile_backup.core.create.FileInputStreamSupplier;
import net.szum123321.textile_backup.core.create.InputSupplier;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Basic abstract class representing directory compressor
 */
public abstract class AbstractCompressor {
    private final static TextileLogger log = new TextileLogger(TextileBackup.MOD_NAME);

    public void createArchive(Path inputFile, Path outputFile, BackupContext ctx, int coreLimit) {
        Instant start = Instant.now();

        try (OutputStream outStream = Files.newOutputStream(outputFile);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outStream);
             OutputStream arc = createArchiveOutputStream(bufferedOutputStream, ctx, coreLimit);
             Stream<Path> fileStream = Files.walk(inputFile)) {

            CompressionStatus.Builder statusBuilder = new CompressionStatus.Builder();

            fileStream
                    .filter(path -> !Utilities.isBlacklisted(inputFile.relativize(path)))
                    .filter(Files::isRegularFile).forEach(file -> {
                        try {
                            addEntry(new FileInputStreamSupplier(file, inputFile.relativize(file).toString(), statusBuilder), arc);
                        } catch (IOException e) {
                            log.error("An exception occurred while trying to compress: {}", inputFile.relativize(file).toString(), e);

                            if (ctx.initiator() == ActionInitiator.Player)
                                log.sendError(ctx, "Something went wrong while compressing files!");
                        }
                    });

            //Serialize using gson?
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream o = new ObjectOutputStream(bo);
            o.writeObject(statusBuilder.build());

            addEntry(new StatusFileInputSupplier(bo.toByteArray(), bo.size()), arc);

            finish(arc);
        } catch(NoSpaceLeftOnDeviceException e) {
            log.error("""
            CRITICAL ERROR OCCURRED!
            The backup is corrupt!
            Don't panic! This is a known issue!
            For help see: https://github.com/Szum123321/textile_backup/wiki/ZIP-Problems
            In case this isn't it here's also the exception itself""", e);

            if(ctx.initiator() == ActionInitiator.Player) {
                log.sendError(ctx, "Backup failed. The file is corrupt.");
                log.error("For help see: https://github.com/Szum123321/textile_backup/wiki/ZIP-Problems");
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("An exception occurred!", e);
            if(ctx.initiator() == ActionInitiator.Player)
                log.sendError(ctx, "Something went wrong while compressing files!");
        } finally {
            close();
        }

        //  close();

        log.sendInfoAL(ctx, "Compression took: {} seconds.", Utilities.formatDuration(Duration.between(start, Instant.now())));
    }

    protected abstract OutputStream createArchiveOutputStream(OutputStream stream, BackupContext ctx, int coreLimit) throws IOException;
    protected abstract void addEntry(InputSupplier inputSupplier, OutputStream arc) throws IOException;

    protected void finish(OutputStream arc) throws InterruptedException, ExecutionException, IOException {
        //This function is only needed for the ParallelZipCompressor to write out ParallelScatterZipCreator
    }

    protected void close() {
        //Same as above, just for ParallelGzipCompressor to shut down ExecutorService
    }

    private record StatusFileInputSupplier(byte[] data, int len) implements InputSupplier {
        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(data, 0, len); }

        @Override
        public Path getPath() { return Path.of(CompressionStatus.DATA_FILENAME); }

        @Override
        public String getName() { return CompressionStatus.DATA_FILENAME; }

        @Override
        public InputStream get() { return new ByteArrayInputStream(data, 0, len); }
    }
 }
