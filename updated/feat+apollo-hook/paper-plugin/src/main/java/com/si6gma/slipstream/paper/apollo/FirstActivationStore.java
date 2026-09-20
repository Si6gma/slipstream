/*
 * Decompiled with CFR 0.152.
 */
package com.si6gma.slipstream.paper.apollo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FirstActivationStore {
    private final Path file;
    private final Set<UUID> seen = new LinkedHashSet<UUID>();
    private boolean dirty;

    public FirstActivationStore(Path file) {
        this.file = file;
        this.load();
    }

    private synchronized void load() {
        List<String> lines;
        if (!Files.isRegularFile(this.file, new LinkOption[0])) {
            return;
        }
        try {
            lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new UncheckedStoreException("Failed to read " + String.valueOf(this.file), ex);
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            try {
                this.seen.add(UUID.fromString(trimmed));
            }
            catch (IllegalArgumentException illegalArgumentException) {}
        }
    }

    public synchronized boolean markIfFirst(UUID playerId) {
        if (!this.seen.add(playerId)) {
            return false;
        }
        this.dirty = true;
        return true;
    }

    public synchronized boolean isDirty() {
        return this.dirty;
    }

    public synchronized void save() throws IOException {
        if (!this.dirty) {
            return;
        }
        ArrayList<String> lines = new ArrayList<String>(this.seen.size());
        for (UUID id : this.seen) {
            lines.add(id.toString());
        }
        Path parent = this.file.getParent();
        if (parent != null) {
            Files.createDirectories(parent, new FileAttribute[0]);
        }
        Path temp = this.file.resolveSibling(String.valueOf(this.file.getFileName()) + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8, new OpenOption[0]);
        try {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
        this.dirty = false;
    }

    public static final class UncheckedStoreException
    extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
