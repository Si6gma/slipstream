package com.si6gma.slipstream.paper.apollo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirstActivationStoreTest {
    @TempDir
    Path dir;

    FirstActivationStoreTest() {
    }

    @Test
    void firstCallIsTrue_laterCallsAreFalse() {
        FirstActivationStore store = new FirstActivationStore(this.dir.resolve("seen.txt"));
        UUID id = UUID.randomUUID();
        Assertions.assertTrue(store.markIfFirst(id));
        Assertions.assertFalse(store.markIfFirst(id));
        Assertions.assertFalse(store.markIfFirst(id));
    }

    @Test
    void differentPlayersAreTrackedIndependently() {
        FirstActivationStore store = new FirstActivationStore(this.dir.resolve("seen.txt"));
        Assertions.assertTrue(store.markIfFirst(UUID.randomUUID()));
        Assertions.assertTrue(store.markIfFirst(UUID.randomUUID()));
    }

    @Test
    void entriesSurviveSaveAndReload() throws IOException {
        Path file = this.dir.resolve("seen.txt");
        UUID id = UUID.randomUUID();
        FirstActivationStore first = new FirstActivationStore(file);
        Assertions.assertTrue(first.markIfFirst(id));
        first.save();
        FirstActivationStore reloaded = new FirstActivationStore(file);
        Assertions.assertFalse(reloaded.markIfFirst(id));
    }

    @Test
    void missingFileStartsEmpty() {
        FirstActivationStore store = new FirstActivationStore(this.dir.resolve("nothing-here.txt"));
        Assertions.assertTrue(store.markIfFirst(UUID.randomUUID()));
    }

    @Test
    void malformedLinesAreSkipped() throws IOException {
        Path file = this.dir.resolve("seen.txt");
        UUID good = UUID.randomUUID();
        Files.write(file, List.of("not-a-uuid", "", "   ", good.toString()), StandardCharsets.UTF_8);
        FirstActivationStore store = new FirstActivationStore(file);
        Assertions.assertFalse(store.markIfFirst(good));
        Assertions.assertTrue(store.markIfFirst(UUID.randomUUID()));
    }

    @Test
    void saveIsANoOpWhenNothingChanged() throws IOException {
        Path file = this.dir.resolve("seen.txt");
        FirstActivationStore store = new FirstActivationStore(file);
        Assertions.assertFalse(store.isDirty());
        store.save();
        Assertions.assertFalse(Files.exists(file));
    }

    @Test
    void saveClearsTheDirtyFlagAndCreatesMissingDirectories() throws IOException {
        Path file = this.dir.resolve("nested").resolve("seen.txt");
        FirstActivationStore store = new FirstActivationStore(file);
        store.markIfFirst(UUID.randomUUID());
        Assertions.assertTrue(store.isDirty());
        store.save();
        Assertions.assertFalse(store.isDirty());
        Assertions.assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
    }

    @Test
    void saveLeavesNoTempFileBehind() throws IOException {
        Path file = this.dir.resolve("seen.txt");
        FirstActivationStore store = new FirstActivationStore(file);
        store.markIfFirst(UUID.randomUUID());
        store.save();
        Assertions.assertFalse(Files.exists(this.dir.resolve("seen.txt.tmp")));
    }
}
