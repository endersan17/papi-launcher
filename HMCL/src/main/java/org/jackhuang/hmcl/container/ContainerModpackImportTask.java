package org.jackhuang.hmcl.container;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ContainerModpackImportTask extends Task<Void> {
    private final Container container;
    private final Path containerPath;
    private final Path modpackFile;
    private final Path sourceDirectory;
    private final List<ContainerContentEntry> importedEntries = new ArrayList<>();
    private final List<String> rejectedResourcePacks = new ArrayList<>();
    private final List<String> failedDownloads = new ArrayList<>();
    private int totalFiles = 0;
    private int processedFiles = 0;

    public List<String> getRejectedResourcePacks() {
        return rejectedResourcePacks;
    }

    public ContainerModpackImportTask(Container container, Path modpackFile) {
        this.container = container;
        this.modpackFile = modpackFile;
        this.sourceDirectory = null;
        this.containerPath = Path.of(container.getContainerPath());

        onDone().register(event -> {
            if (event.isFailed()) rollback();
        });
    }

    public ContainerModpackImportTask(Container container, Path modpackFile, Path sourceDirectory) {
        this.container = container;
        this.modpackFile = modpackFile;
        this.sourceDirectory = sourceDirectory;
        this.containerPath = Path.of(container.getContainerPath());

        onDone().register(event -> {
            if (event.isFailed()) rollback();
        });
    }

    private void rollback() {
        LOG.warning("Modpack import failed, rolling back...");
        if (importedEntries.isEmpty()) return;

        for (ContainerContentEntry entry : importedEntries) {
            Path file = Path.of(entry.getSourcePath());
            try {
                Files.deleteIfExists(file);
                Path parent = file.getParent();
                while (parent != null && !parent.equals(containerPath)) {
                    try (java.util.stream.Stream<Path> dirEntries = Files.list(parent)) {
                        if (dirEntries.findAny().isPresent()) break;
                    }
                    Files.delete(parent);
                    parent = parent.getParent();
                }
            } catch (IOException e) {
                LOG.warning("Rollback: could not delete " + file, e);
            }
        }
        LOG.warning("Rollback complete — removed " + importedEntries.size() + " files");
    }

    @Override
    public void execute() throws Exception {
        ReentrantLock lock = ContainerManager.getInstance().lockFor(container);
        lock.lock();
        try {
            if (sourceDirectory != null) {
                importFromDirectory();
            } else {
                importFromZip();
            }
            registerContent();
        } finally {
            lock.unlock();
        }
    }

    private void importFromZip() throws Exception {
        LOG.info("Starting modpack import for container " + container.getName() + " from " + modpackFile);

        if (!Files.exists(modpackFile)) {
            throw new IOException("Modpack file not found: " + modpackFile);
        }

        try (ZipFile zip = new ZipFile(modpackFile.toFile())) {
            // Validate the zip contains a recognizable modpack manifest (HIGH 5).
            boolean hasManifest = false;
            java.util.Enumeration<? extends ZipEntry> manifestScan = zip.entries();
            while (manifestScan.hasMoreElements()) {
                String entryName = manifestScan.nextElement().getName();
                if (entryName.equals("pack_info.json") || entryName.equals("manifest.json") || entryName.equals("modrinth.index.json")) {
                    hasManifest = true;
                    break;
                }
            }
            if (!hasManifest) {
                throw new ContainerModpackImportException(
                        "No valid modpack manifest found in zip (expected pack_info.json, manifest.json, or modrinth.index.json)");
            }

            List<ZipEntry> relevantEntries = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (isRelevantEntry(name)) {
                    relevantEntries.add(entry);
                }
            }

            totalFiles = relevantEntries.size();
            LOG.info("Found " + totalFiles + " relevant entries in modpack");

            for (ZipEntry entry : relevantEntries) {
                String name = entry.getName();
                String targetRelative = resolveTargetPath(name);

                if (targetRelative == null) {
                    LOG.warning("Skipping entry with unresolved path: " + name);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                    continue;
                }

                Path targetPath = containerPath.resolve(targetRelative).normalize();

                if (!targetPath.startsWith(containerPath)) {
                    LOG.warning("Skipping entry outside container: " + name);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                    continue;
                }

                if (Files.exists(targetPath)) {
                    LOG.info("Skipping existing file: " + targetRelative);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                    continue;
                }

                Files.createDirectories(targetPath.getParent());

                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, targetPath);
                }

                ContainerContentEntry.Type type = detectEntryType(targetRelative);
                if (type == null) {
                    // Config files are extracted but not tracked in content.json.
                    LOG.info("Extracted (unregistered): " + targetRelative);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                    continue;
                }
                if (type == ContainerContentEntry.Type.RESOURCE_PACK && !validateResourcePack(targetPath)) {
                    LOG.info("Skipping invalid resource pack: " + targetRelative);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                    continue;
                }
                importedEntries.add(new ContainerContentEntry(
                        type,
                        targetPath.toAbsolutePath().normalize().toString(),
                        targetPath.getFileName().toString(),
                        Instant.now()));

                LOG.info("Extracted: " + targetRelative);
                processedFiles++;
                updateProgress(processedFiles, Math.max(totalFiles, 1));
            }

            downloadExternalMods(zip);
        }

        LOG.info("Zip import completed — " + importedEntries.size() + " new files");
    }

    private void importFromDirectory() throws IOException {
        LOG.info("Importing modpack from installed version directory: " + sourceDirectory);

        if (!Files.isDirectory(sourceDirectory)) {
            throw new IOException("Source directory not found: " + sourceDirectory);
        }

        String[] targetDirs = {"mods", "resourcepacks", "shaderpacks", "config", "saves"};
        List<Path> dirsToCopy = new ArrayList<>();

        for (String dir : targetDirs) {
            Path src = sourceDirectory.resolve(dir);
            if (Files.isDirectory(src)) {
                dirsToCopy.add(src);
            }
        }

        totalFiles = 0;
        for (Path dir : dirsToCopy) {
            try (Stream<Path> walk = Files.walk(dir)) {
                totalFiles += (int) walk.filter(Files::isRegularFile).count();
            }
        }

        LOG.info("Found " + dirsToCopy.size() + " directories with " + totalFiles + " files to copy");

        for (Path srcDir : dirsToCopy) {
            String relativeDir = srcDir.getFileName().toString();
            Path targetDir = containerPath.resolve(relativeDir);
            Files.createDirectories(targetDir);

            try (Stream<Path> files = Files.walk(srcDir)) {
                files.filter(Files::isRegularFile).forEach(srcFile -> {
                    String relative = srcDir.relativize(srcFile).normalize().toString();
                    if (relative.isEmpty()) return;

                    Path targetFile = targetDir.resolve(relative).normalize();
                    if (!targetFile.startsWith(containerPath)) {
                        LOG.warning("Skipping file outside container: " + srcFile);
                        processedFiles++;
                        updateProgress(processedFiles, Math.max(totalFiles, 1));
                        return;
                    }

                    if (Files.exists(targetFile)) {
                        LOG.info("Skipping existing file: " + relativeDir + "/" + relative);
                        processedFiles++;
                        updateProgress(processedFiles, Math.max(totalFiles, 1));
                        return;
                    }

                    try {
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(srcFile, targetFile);
                    } catch (IOException e) {
                        LOG.warning("Failed to copy: " + srcFile, e);
                        processedFiles++;
                        updateProgress(processedFiles, Math.max(totalFiles, 1));
                        return;
                    }

                    ContainerContentEntry.Type type = detectEntryType(relativeDir + "/" + relative);
                    if (type == null) {
                        // Config files are extracted but not tracked in content.json.
                        LOG.info("Copied (unregistered): " + relativeDir + "/" + relative);
                        processedFiles++;
                        updateProgress(processedFiles, Math.max(totalFiles, 1));
                        return;
                    }
                    if (type == ContainerContentEntry.Type.RESOURCE_PACK && !validateResourcePack(targetFile)) {
                        LOG.info("Skipping invalid resource pack: " + relativeDir + "/" + relative);
                        processedFiles++;
                        updateProgress(processedFiles, Math.max(totalFiles, 1));
                        return;
                    }
                    importedEntries.add(new ContainerContentEntry(
                            type,
                            targetFile.toAbsolutePath().normalize().toString(),
                            targetFile.getFileName().toString(),
                            Instant.now()));

                    LOG.info("Copied: " + relativeDir + "/" + relative);
                    processedFiles++;
                    updateProgress(processedFiles, Math.max(totalFiles, 1));
                });
            }
        }

        LOG.info("Directory import completed — " + importedEntries.size() + " new files");
    }

    private boolean isRelevantEntry(String name) {
        return name.startsWith("mods/")
                || name.startsWith("resourcepacks/")
                || name.startsWith("shaderpacks/")
                || name.startsWith("config/")
                || name.startsWith("saves/")
                || name.startsWith("overrides/");
    }

    private String resolveTargetPath(String name) {
        if (name.startsWith("overrides/")) {
            return name.substring("overrides/".length());
        }
        return name;
    }

    private ContainerContentEntry.Type detectEntryType(String relativePath) {
        if (relativePath.startsWith("resourcepacks/")) {
            return ContainerContentEntry.Type.RESOURCE_PACK;
        } else if (relativePath.startsWith("shaderpacks/")) {
            return ContainerContentEntry.Type.SHADER_PACK;
        } else if (relativePath.startsWith("saves/")) {
            return ContainerContentEntry.Type.WORLD;
        } else if (relativePath.startsWith("config/")) {
            // Config files are extracted to the correct directory but not tracked in content.json.
            return null;
        }
        return ContainerContentEntry.Type.MOD;
    }

    private void registerContent() {
        ContainerManager.getInstance().mergeContainerContent(container, importedEntries);
        LOG.info("Registered " + importedEntries.size() + " new content entries");
    }

    private boolean validateResourcePack(Path file) {
        String gameVersion = ContainerManager.resolveContainerGameVersion(container.getLinkedVersionId());
        ResourcePackValidator.ValidationResult result = ResourcePackValidator.validate(file, gameVersion);
        if (result.isError()) {
            String msg = "container.resource_pack.validation.pack_format_mismatch".equals(result.getMessageKey())
                    ? i18n(result.getMessageKey(), result.getMessageArgs())
                    : file.getFileName().toString() + ": " + i18n(result.getMessageKey(), result.getMessageArgs());
            LOG.warning("Resource pack validation failed during import: " + msg);
            rejectedResourcePacks.add(msg);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete invalid resource pack: " + file, e);
            }
            return false;
        }
        return true;
    }

    private void downloadExternalMods(ZipFile zip) {
        // Determine manifest format and parse external file references.
        ZipEntry modrinthEntry = zip.getEntry("modrinth.index.json");
        ZipEntry curseforgeEntry = zip.getEntry("manifest.json");

        try {
            if (modrinthEntry != null) {
                downloadModrinthMods(zip, modrinthEntry);
            } else if (curseforgeEntry != null) {
                // CurseForge manifest.json references files by projectID/fileID, which
                // requires the CurseForge API to resolve. Without API credentials, skip.
                LOG.warning("CurseForge manifest detected but external mod downloads require API access. "
                        + "Only bundled mods were imported.");
            }
        } catch (Exception e) {
            LOG.warning("Failed to download external mods", e);
        }

        if (!failedDownloads.isEmpty()) {
            LOG.warning("Failed to download " + failedDownloads.size() + " external mod(s): " + failedDownloads);
        }
    }

    @SuppressWarnings("unchecked")
    private void downloadModrinthMods(ZipFile zip, ZipEntry manifestEntry) {
        Path modsDir = containerPath.resolve("mods");
        try {
            String jsonText;
            try (InputStream is = zip.getInputStream(manifestEntry)) {
                jsonText = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            java.util.Map<String, Object> manifest = JsonUtils.GSON.fromJson(jsonText, java.util.Map.class);
            if (manifest == null) return;

            Object filesObj = manifest.get("files");
            if (!(filesObj instanceof java.util.List)) return;

            List<java.util.Map<String, Object>> files = (List<java.util.Map<String, Object>>) filesObj;
            int downloadTotal = 0;
            for (java.util.Map<String, Object> fileEntry : files) {
                Object pathObj = fileEntry.get("path");
                Object downloadsObj = fileEntry.get("downloads");
                if (!(pathObj instanceof String) || !(downloadsObj instanceof java.util.List)) continue;

                String relPath = (String) pathObj;
                List<String> downloads = (List<String>) downloadsObj;
                if (downloads.isEmpty()) continue;

                // Only download mod files (under mods/)
                if (!relPath.startsWith("mods/")) continue;

                String fileName = relPath.substring("mods/".length());
                Path targetPath = modsDir.resolve(fileName).normalize();
                if (!targetPath.startsWith(containerPath)) {
                    LOG.warning("Skipping download for path outside container: " + relPath);
                    continue;
                }

                if (Files.exists(targetPath)) {
                    LOG.info("External mod already exists: " + fileName);
                    continue;
                }

                String downloadUrl = downloads.get(0);
                LOG.info("Downloading external mod: " + fileName + " from " + downloadUrl);
                downloadTotal++;

                boolean success = false;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        Files.createDirectories(targetPath.getParent());
                        new FileDownloadTask(downloadUrl, targetPath).run();
                        success = true;
                        break;
                    } catch (Exception e) {
                        LOG.warning("Download attempt " + attempt + "/3 failed for " + fileName, e);
                        if (attempt < 3) {
                            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        }
                    }
                }

                if (success) {
                    importedEntries.add(new ContainerContentEntry(
                            ContainerContentEntry.Type.MOD,
                            targetPath.toAbsolutePath().normalize().toString(),
                            fileName,
                            Instant.now()));
                    LOG.info("Downloaded external mod: " + fileName);
                } else {
                    failedDownloads.add(fileName);
                    LOG.warning("Failed to download external mod after 3 attempts: " + fileName);
                }
            }

            if (downloadTotal > 0) {
                LOG.info("Processed " + downloadTotal + " external mod reference(s), "
                        + (downloadTotal - failedDownloads.size()) + " succeeded, "
                        + failedDownloads.size() + " failed");
            }
        } catch (Exception e) {
            LOG.warning("Failed to parse modrinth.index.json for external mod downloads", e);
        }
    }

    public List<String> getFailedDownloads() {
        return failedDownloads;
    }
}
