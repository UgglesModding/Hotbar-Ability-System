package com.abilities.abilitiesplugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans the /mods folder for jar files that contain HCA pack(s),
 * e.g. HCA/HCA_pack.json or HCA/*_hca_pack.json
 *
 * Then queues them into AbilityReceiver BEFORE Hotbar activates,
 * so they apply in deterministic order.
 */
public final class ModPackScanner {

    private ModPackScanner() {}

    public static void scanModsFolderAndQueuePacks(Path modsDir) {
        if (modsDir == null) return;

        if (!Files.exists(modsDir) || !Files.isDirectory(modsDir)) {
            System.out.println("[HCA] ModPackScanner: mods dir not found: " + modsDir);
            return;
        }

        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path p : stream) jars.add(p);
        } catch (Throwable t) {
            System.out.println("[HCA] ModPackScanner: failed listing mods dir: " + t.getMessage());
            return;
        }

        // stable order (so “load order” is deterministic)
        jars.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        int queued = 0;

        for (Path jarPath : jars) {
            String jarName = jarPath.getFileName().toString();

            try (ZipFile zip = new ZipFile(jarPath.toFile())) {

                // Collect candidate packs inside this jar
                List<String> packEntries = new ArrayList<>();

                // 1) standard name
                if (zip.getEntry("HCA/HCA_pack.json") != null) {
                    packEntries.add("HCA/HCA_pack.json");
                }

                // 2) alternative names: HCA/*_hca_pack.json
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (name == null) continue;

                    String n = name.replace("\\", "/");
                    if (!n.startsWith("HCA/")) continue;
                    if (!n.toLowerCase(Locale.ROOT).endsWith("_hca_pack.json")) continue;

                    packEntries.add(n);
                }

                if (packEntries.isEmpty()) continue;

                // Queue each pack we found
                for (String entryName : packEntries) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null) continue;

                    byte[] bytes = readAllBytes(zip.getInputStream(entry));

                    // ResourceAccess that can read any file inside THIS jar.
                    AbilityReceiver.ResourceAccess access = new ZipResourceAccess(jarPath, bytes);

                    // source tag = jar name + entry name (good for debugging)
                    String sourceTag = jarName + "::" + entryName;

                    boolean ok = AbilityReceiver.queuePack(access, bytes, sourceTag);
                    if (ok) queued++;
                }

            } catch (Throwable t) {
                System.out.println("[HCA] ModPackScanner: failed scanning " + jarName + " : " + t.getMessage());
            }
        }

        System.out.println("[HCA] ModPackScanner: queued packs=" + queued);
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) >= 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    /**
     * ResourceAccess implementation that re-opens the jar when it needs to read index/weapon json.
     * NOTE: stores jarPath only; does NOT keep ZipFile open.
     */
    private static final class ZipResourceAccess implements AbilityReceiver.ResourceAccess {
        private final Path jarPath;
        private final byte[] packBytes; // not used for reads, but handy if you want later

        ZipResourceAccess(Path jarPath, byte[] packBytes) {
            this.jarPath = jarPath;
            this.packBytes = packBytes;
        }

        @Override
        public InputStream open(String resourcePath) {
            String p = normalize(resourcePath);
            try {
                ZipFile zip = new ZipFile(jarPath.toFile());
                ZipEntry e = zip.getEntry(p);
                if (e == null) {
                    zip.close();
                    return null;
                }

                // Wrap so closing the stream also closes the ZipFile.
                InputStream raw = zip.getInputStream(e);
                return new FilterInputStream(raw) {
                    @Override public void close() throws IOException {
                        super.close();
                        zip.close();
                    }
                };

            } catch (Throwable t) {
                return null;
            }
        }

        private static String normalize(String p) {
            String s = (p == null) ? "" : p.replace("\\", "/").trim();
            while (s.startsWith("/")) s = s.substring(1);
            return s;
        }
    }
}
