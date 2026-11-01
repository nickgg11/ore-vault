package com.orevault.orevault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

/** The reload arrives on the config watcher's thread, and the player list and command tree are the server's. */
class ConfigReloadTest {
    private final Queue<Runnable> serverThread = new ArrayDeque<>();
    private final List<String> online = new ArrayList<>();
    private final List<String> sentTo = new ArrayList<>();

    private void reload() {
        ConfigReload.resendOnServerThread(serverThread::add, () -> List.copyOf(online), sentTo::add);
    }

    @Test
    void nothingIsSentBeforeTheServerThreadRunsIt() {
        online.add("alice");
        reload();
        assertEquals(List.of(), sentTo);
    }

    @Test
    void everyoneOnlineIsSentTheTree() {
        online.addAll(List.of("alice", "bob"));
        reload();
        serverThread.remove().run();
        assertEquals(List.of("alice", "bob"), sentTo);
    }

    /** The list itself is server-thread state, so it is read in the task, not on the watcher's thread. */
    @Test
    void thePlayerListIsReadOnTheServerThread() {
        reload();
        online.add("alice");
        serverThread.remove().run();
        assertEquals(List.of("alice"), sentTo);
    }
}
