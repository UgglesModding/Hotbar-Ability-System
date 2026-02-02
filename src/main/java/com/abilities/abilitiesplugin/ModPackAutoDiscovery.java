package com.abilities.abilitiesplugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Locale;

public final class ModPackAutoDiscovery {

    private ModPackAutoDiscovery() {}

    /**
     * Scans the server "mods" folder for JARs containing HCA/HCA_pack.json
     * and queues them into AbilityReceiver.
     */
    public static void scanAndRegisterAllPacks() {
        Path modsDir = findModsDir();
        if (modsDir == null) {
            System.out.println("[HCA] AutoDiscover: could not locate mods folder. Skipping scan.");
            return;
        }

        System.out.println("[HCA] AutoDiscover: scanning mods folder: " + modsDir.toAbsolutePath());

        int jars = 0;
        int packs = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : stream) {
                jars++;

                String fileName = jar.getFileName().toString();
                // Optional: skip our own jar to avoid double-load
                // if (fileName.toLowerCase(Locale.ROOT).contains("hotbar")) continue;

                int added = tryRegisterFromJar(jar);
                packs += added;
            }
        } catch (Throwable t) {
            System.out.println("[HCA] AutoDiscover: failed scanning mods folder: " + t.getMessage());
        }

        System.out.println("[HCA] AutoDiscover: jars=" + jars + " packsQueued=" + packs);
    }

    private static int tryRegisterFromJar(Path jarPath) {
        String sourceTag = jarPath.getFileName().toString();

        try (URLClassLoader jarLoader = new URLClassLoader(
                new URL[]{ jarPath.toUri().toURL() },
                ModPackAutoDiscovery.class.getClassLoader()
        )) {
            String packPath = "HCA/HCA_pack.json";

            InputStream packStream = jarLoader.getResourceAsStream(packPath);
            if (packStream == null) {
                return 0; // no pack in this jar
            }

            byte[] packBytes = readAllBytes(packStream);

            boolean ok = AbilityReceiver.queuePack( // ✅ fixed
                    jarLoader,
                    new ByteArrayInputStream(packBytes),
                    sourceTag
            );

            if (ok) {
                System.out.println("[HCA] AutoDiscover: queued pack from " + sourceTag);
                return 1;
            } else {
                System.out.println("[HCA] AutoDiscover: found pack but failed queuing: " + sourceTag);
                return 0;
            }

        } catch (Throwable t) {
            System.out.println("[HCA] AutoDiscover: error reading jar " + sourceTag + " : " + t.getMessage());
            return 0;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = input.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        }
    }

    private static Path findModsDir() {
        Path p1 = Paths.get("mods");
        if (Files.isDirectory(p1)) return p1;

        Path p2 = Paths.get("Server", "mods");
        if (Files.isDirectory(p2)) return p2;

        Path p3 = Paths.get("game", "mods");
        if (Files.isDirectory(p3)) return p3;

        try {
            Path cwd = Paths.get("").toAbsolutePath();
            Path p4 = cwd.resolve("mods");
            if (Files.isDirectory(p4)) return p4;
        } catch (Throwable ignored) {}

        return null;
    }
}
