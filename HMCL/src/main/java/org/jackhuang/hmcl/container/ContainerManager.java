package org.jackhuang.hmcl.container;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.download.LoaderDetector;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import javafx.application.Platform;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ContainerManager {
    private static final ContainerManager INSTANCE = new ContainerManager();
    private static final Path CONTAINERS_DIR = Metadata.HMCL_CURRENT_DIRECTORY.resolve("containers");
    private static final Path CONTAINERS_FILE = CONTAINERS_DIR.resolve("containers.json");

    private final List<Container> containers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, List<ContainerContentEntry>> contentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> containerLocks = new ConcurrentHashMap<>();
    private final Set<UUID> launchingContainers = ConcurrentHashMap.newKeySet();

    ReentrantLock lockFor(Container container) {
        return containerLocks.computeIfAbsent(container.getId(), k -> new ReentrantLock());
    }

    private ContainerManager() {
    }

    public static ContainerManager getInstance() {
        return INSTANCE;
    }

    public List<Container> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public Optional<Container> getContainer(UUID id) {
        return containers.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public void load() {
        try {
            if (Files.exists(CONTAINERS_FILE)) {
                try {
                    ContainerRegistry registry = JsonUtils.fromJsonFile(CONTAINERS_FILE, ContainerRegistry.class);
                    if (registry != null && registry.getContainers() != null) {
                        containers.clear();
                        containers.addAll(registry.getContainers());
                        return;
                    }
                } catch (JsonSyntaxException ignored) {
                }
                List<Container> loaded = JsonUtils.fromJsonFile(CONTAINERS_FILE, new TypeToken<List<Container>>() {});
                if (loaded != null) {
                    containers.clear();
                    containers.addAll(loaded);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.warning("Failed to load containers", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONTAINERS_DIR);
            JsonUtils.writeToJsonFileAtomically(CONTAINERS_FILE,
                    new ContainerRegistry(ContainerRegistry.CURRENT_SCHEMA_VERSION, new ArrayList<>(containers)));
        } catch (IOException e) {
            LOG.warning("Failed to save containers", e);
        }
    }

    public Container createContainer(String name, String linkedVersionId, String description) {
        UUID id = UUID.randomUUID();
        Path containerDir = CONTAINERS_DIR.resolve(id.toString());
        try {
            Files.createDirectories(containerDir);
            Files.createDirectories(containerDir.resolve("mods"));
            Files.createDirectories(containerDir.resolve("saves"));
            Files.createDirectories(containerDir.resolve("resourcepacks"));
            Files.createDirectories(containerDir.resolve("shaderpacks"));
            Files.createDirectories(containerDir.resolve("config"));
        } catch (IOException e) {
            LOG.warning("Failed to create container directory", e);
        }

        Container container = new Container(
                id, name, linkedVersionId,
                containerDir.toAbsolutePath().normalize().toString(),
                Instant.now(),
                description
        );
        containers.add(container);
        save();
        return container;
    }

    public void deleteContainer(UUID id) {
        Container container = getContainer(id).orElse(null);
        if (container == null) return;

        Path containerDir = Path.of(container.getContainerPath());
        ReentrantLock lock = lockFor(container);

        IOException deleteError = null;
        lock.lock();
        try {
            containers.removeIf(c -> c.getId().equals(id));
            contentCache.remove(id);
            containerLocks.remove(id);
            save();
            try {
                FileUtils.deleteDirectory(containerDir.toFile());
                LOG.info("Deleted container directory: " + containerDir);
            } catch (IOException e) {
                LOG.warning("Failed to delete container directory: " + containerDir, e);
                deleteError = e;
            }
        } finally {
            lock.unlock();
        }

        if (deleteError != null) {
            LOG.warning(i18n("container.delete.failed", containerDir));
        }
    }

    // --- Content persistence ---

    private Path getContentFile(Container container) {
        return Path.of(container.getContainerPath()).resolve("content.json");
    }

    public List<ContainerContentEntry> getContent(Container container) {
        UUID id = container.getId();
        List<ContainerContentEntry> cached = contentCache.get(id);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            cached = contentCache.get(id);
            if (cached != null) {
                return new ArrayList<>(cached);
            }

            Path contentFile = getContentFile(container);
            List<ContainerContentEntry> loaded = new ArrayList<>();
            if (Files.exists(contentFile)) {
                List<ContainerContentEntry> fromFile = JsonUtils.fromJsonFile(contentFile, new TypeToken<List<ContainerContentEntry>>() {});
                if (fromFile != null) {
                    loaded = fromFile;
                    // Force enabled=true on all loaded entries (safety migration for old data)
                    for (ContainerContentEntry e : loaded) {
                        e.setEnabled(true);
                    }
                }
            }
            contentCache.put(id, new ArrayList<>(loaded));
            return loaded;
        } catch (IOException e) {
            LOG.warning("Failed to load container content", e);
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    public boolean saveContent(Container container, List<ContainerContentEntry> entries) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            JsonUtils.writeToJsonFileAtomically(getContentFile(container), entries);
            contentCache.put(container.getId(), new ArrayList<>(entries));
            return true;
        } catch (IOException e) {
            LOG.warning("Failed to save container content", e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void mergeContainerContent(Container container, List<ContainerContentEntry> newEntries) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            List<ContainerContentEntry> existing = getContent(container);
            for (ContainerContentEntry newEntry : newEntries) {
                boolean alreadyRegistered = existing.stream().anyMatch(e ->
                        e.getFileName().equals(newEntry.getFileName()) && e.getType() == newEntry.getType());
                if (!alreadyRegistered) {
                    existing.add(newEntry);
                }
            }
            saveContent(container, existing);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Scans the physical files in the container's subdirectories and registers
     * any files not already tracked in content.json.
     * Skips files whose filename already exists in content.json for that type.
     * @return number of newly discovered entries, or -1 if the save failed
     */
    public int scanAndAddNewContent(Container container) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            List<ContainerContentEntry> entries = getContent(container);
            int newCount = 0;

            for (ContainerContentEntry.Type type : ContainerContentEntry.Type.values()) {
                Path dir = targetDirForType(container, type);
                if (!Files.isDirectory(dir)) {
                    LOG.warning("Container directory does not exist: " + dir);
                    continue;
                }

                LOG.info("Scanning " + dir + " for new " + type + " files...");

                Set<String> knownFileNames = entries.stream()
                        .filter(e -> e.getType() == type)
                        .map(ContainerContentEntry::getFileName)
                        .collect(Collectors.toSet());

                try (Stream<Path> stream = Files.list(dir)) {
                    List<Path> files = stream
                            .filter(p -> Files.isRegularFile(p) || Files.isDirectory(p))
                            .filter(p -> !knownFileNames.contains(p.getFileName().toString()))
                            .collect(Collectors.toList());

                    LOG.info("Directory " + dir + ": " + files.size() + " new file(s) out of "
                            + (knownFileNames.size() + files.size()) + " total on disk");

                    for (Path file : files) {
                        Path sourcePath = file.toAbsolutePath().normalize();

                        ContainerContentEntry entry = new ContainerContentEntry(
                                type,
                                sourcePath.toString(),
                                file.getFileName().toString(),
                                Instant.now()
                        );
                        entries.add(entry);
                        newCount++;
                        LOG.info("Found new file: " + file.getFileName() + " (type=" + type + ")");
                    }
                } catch (IOException e) {
                    LOG.warning("Failed to scan container directory: " + dir, e);
                }
            }

            if (newCount > 0) {
                if (!saveContent(container, entries)) {
                    LOG.warning("Scan found " + newCount + " new file(s) but FAILED to save content.json");
                    return -1;
                }
                List<ContainerContentEntry> saved = getContent(container);
                int expectedSize = entries.size();
                int actualSize = saved.size();
                if (actualSize != expectedSize) {
                    LOG.warning("Save verification failed: expected " + expectedSize + " entries, got " + actualSize);
                    return -1;
                }
                LOG.info("Scan saved " + newCount + " new file(s) in container " + container.getName());
            } else {
                LOG.info("Scan found no new files in container " + container.getName());
            }

            return newCount;
        } finally {
            lock.unlock();
        }
    }

    // --- Symlink / copy logic ---

    private static Path targetDirForType(Container container, ContainerContentEntry.Type type) {
        Path containerPath = Path.of(container.getContainerPath());
        switch (type) {
            case MOD: return containerPath.resolve("mods");
            case WORLD: return containerPath.resolve("saves");
            case RESOURCE_PACK: return containerPath.resolve("resourcepacks");
            case SHADER_PACK: return containerPath.resolve("shaderpacks");
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private boolean createLinkOrCopy(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.createSymbolicLink(target, source.toAbsolutePath().normalize());
            LOG.info("Created symlink: " + target + " -> " + source);
            return true;
        } catch (UnsupportedOperationException | IOException e) {
            LOG.warning("Symlink not supported, falling back to copy: " + target, e);
        }
        try {
            if (Files.isDirectory(source)) {
                FileUtils.copyDirectory(source, target);
            } else {
                FileUtils.copyFile(source, target);
            }
            LOG.info("Copied: " + source + " -> " + target);
            return true;
        } catch (IOException e) {
            LOG.warning("Failed to copy: " + source + " -> " + target, e);
            return false;
        }
    }

    // --- Deploy content at launch ---

    public List<String> deployContent(Container container) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            List<ContainerContentEntry> entries = getContent(container);
            LOG.info("Deploying " + entries.size() + " content entries for container " + container.getName());
            List<String> failures = new ArrayList<>();

            // HIGH 2: Pre-filter broken entries and add to failures immediately
            List<ContainerContentEntry> brokenEntries = entries.stream()
                    .filter(ContainerContentEntry::isBroken)
                    .collect(Collectors.toList());
            for (ContainerContentEntry broken : brokenEntries) {
                String tag = broken.getFileName() + " (" + typeDisplayName(broken.getType()) + ")";
                String msg = tag + ": Source file not found";
                LOG.warning(msg);
                failures.add(msg);
            }

            String gameVersion = resolveContainerGameVersion(container.getLinkedVersionId());
            Path containerPath = Path.of(container.getContainerPath());

            List<String> deployedResourcePacks = new ArrayList<>();
            List<String> deployedShaderPacks = new ArrayList<>();
            boolean hasShaderMod = false;

            for (ContainerContentEntry entry : entries) {
                // CRITICAL 1: Skip disabled entries
                if (!entry.isEnabled()) {
                    LOG.info("  Skipping disabled entry: " + entry.getFileName());
                    continue;
                }

                // HIGH 2: Skip broken entries (already reported above)
                if (entry.isBroken()) continue;

                Path sourcePath = Path.of(entry.getSourcePath());
                String tag = entry.getFileName() + " (" + typeDisplayName(entry.getType()) + ")";

                // Detect shader mods among deployed mods (CRITICAL 2 part A)
                if (entry.getType() == ContainerContentEntry.Type.MOD) {
                    String lower = entry.getFileName().toLowerCase(Locale.ROOT);
                    if (lower.contains("optifine") || lower.contains("iris") || lower.contains("oculus")) {
                        hasShaderMod = true;
                    }
                }

                // Validate resource pack at deploy time
                if (entry.getType() == ContainerContentEntry.Type.RESOURCE_PACK && Files.exists(sourcePath)) {
                    ResourcePackValidator.ValidationResult result = ResourcePackValidator.validate(sourcePath, gameVersion);
                    if (result.isError()) {
                        String msg = tag + ": " + i18n(result.getMessageKey(), result.getMessageArgs());
                        LOG.warning(msg);
                        failures.add(msg);
                        continue;
                    } else if (result.isWarning()) {
                        String msg = tag + ": " + i18n(result.getMessageKey(), result.getMessageArgs());
                        LOG.warning(msg);
                        // WARNINGS do NOT block launch — only errors added to failures
                    }
                }

                Path targetDir = targetDirForType(container, entry.getType());
                Path target = targetDir.resolve(entry.getFileName());
                if (Files.exists(target)) {
                    LOG.info("  Content already deployed: " + entry.getFileName());
                    continue;
                }
                LOG.info("  Deploying missing content: " + entry.getFileName() + " -> " + target);
                try {
                    Files.createDirectories(target.getParent());
                } catch (IOException e) {
                    String msg = tag + ": Failed to create target directory";
                    LOG.warning(msg, e);
                    failures.add(msg);
                    continue;
                }
                if (!Files.exists(sourcePath)) {
                    String msg = tag + ": Source file not found";
                    LOG.warning(msg);
                    failures.add(msg);
                    continue;
                }
                if (!createLinkOrCopy(sourcePath, target)) {
                    String msg = tag + ": Failed to copy or symlink file";
                    LOG.warning(msg);
                    failures.add(msg);
                }

                // Track newly deployed content for post-deploy updates
                if (entry.getType() == ContainerContentEntry.Type.RESOURCE_PACK && !Files.exists(target)) {
                    deployedResourcePacks.add(entry.getFileName());
                } else if (entry.getType() == ContainerContentEntry.Type.SHADER_PACK && !Files.exists(target)) {
                    deployedShaderPacks.add(entry.getFileName());
                }
            }

            // CRITICAL 1: Update options.txt with deployed resource packs
            if (!deployedResourcePacks.isEmpty()) {
                updateOptionsResourcePacks(containerPath, deployedResourcePacks);
            }

            // CRITICAL 2 part A: Warn if shaders deployed without shader mod
            if (!deployedShaderPacks.isEmpty() && !hasShaderMod) {
                String msg = "container.deploy.warning.no_shader_mod";
                LOG.warning("Deploying shader pack(s) to container " + container.getId() + " but no shader mod detected");
                failures.add(i18n(msg) + ": " + String.join(", ", deployedShaderPacks));
            }

            // CRITICAL 2 part B: Activate deployed shaders
            if (!deployedShaderPacks.isEmpty()) {
                activateShaderPacks(containerPath, deployedShaderPacks, hasShaderMod);
            }

            return failures;
        } finally {
            lock.unlock();
        }
    }

    private static String typeDisplayName(ContainerContentEntry.Type type) {
        switch (type) {
            case MOD: return "mod";
            case WORLD: return "world";
            case RESOURCE_PACK: return "resourcepack";
            case SHADER_PACK: return "shaderpack";
            default: return "unknown";
        }
    }

    // --- Post-deploy updates ---

    /**
     * Updates options.txt with newly deployed resource pack filenames so they
     * appear as enabled resource packs in-game. (CRITICAL 1)
     */
    private void updateOptionsResourcePacks(Path containerPath, List<String> packNames) {
        Path optionsFile = containerPath.resolve("options.txt");
        List<String> lines;
        try {
            if (Files.exists(optionsFile)) {
                lines = new ArrayList<>(Files.readAllLines(optionsFile, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                lines = new ArrayList<>();
                Files.createDirectories(optionsFile.getParent());
            }
        } catch (IOException e) {
            LOG.warning("Failed to read options.txt for resource pack update", e);
            return;
        }

        // Find the resourcePacks: line index
        int rpIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("resourcePacks:")) {
                rpIndex = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("resourcePacks:[");
        boolean first = true;

        if (rpIndex >= 0) {
            // Parse existing entries from the current line
            String existingLine = lines.get(rpIndex);
            int colonIdx = existingLine.indexOf(':');
            if (colonIdx >= 0 && colonIdx + 1 < existingLine.length()) {
                String jsonArrayStr = existingLine.substring(colonIdx + 1).trim();
                if (jsonArrayStr.startsWith("[") && jsonArrayStr.endsWith("]")) {
                    String inner = jsonArrayStr.substring(1, jsonArrayStr.length() - 1);
                    if (!inner.isEmpty()) {
                        String[] parts = inner.split(",");
                        java.util.Set<String> existingPacks = new java.util.LinkedHashSet<>();
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                existingPacks.add(part);
                                if (!first) sb.append(",");
                                sb.append(part);
                                first = false;
                            }
                        }
                        // Add only packs not already present
                        for (String name : packNames) {
                            String quoted = "\"" + name + "\"";
                            if (!existingPacks.contains(quoted)) {
                                if (!first) sb.append(",");
                                sb.append(quoted);
                                first = false;
                            }
                        }
                    }
                }
            }
            // Remove the old line; we will re-insert it
            lines.remove(rpIndex);
        }

        if (first && !packNames.isEmpty()) {
            // No existing packs, add the new ones
            for (int i = 0; i < packNames.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(packNames.get(i)).append("\"");
            }
        }
        sb.append("]");

        lines.add(sb.toString());

        try {
            Files.write(optionsFile, lines, java.nio.charset.StandardCharsets.UTF_8);
            LOG.info("Updated options.txt with " + packNames.size() + " new resource pack(s)");
        } catch (IOException e) {
            LOG.warning("Failed to write updated options.txt", e);
        }
    }

    /**
     * Activates deployed shader packs by updating the relevant config file
     * (optionsshaders.txt for OptiFine, iris.properties for Iris).
     * (CRITICAL 2 part B)
     */
    private void activateShaderPacks(Path containerPath, List<String> shaderPackNames, boolean hasShaderMod) {
        if (shaderPackNames.isEmpty()) return;
        String firstPack = shaderPackNames.get(0);

        // OptiFine: optionsshaders.txt at container root
        Path optifineConfig = containerPath.resolve("optionsshaders.txt");
        try {
            Files.createDirectories(optifineConfig.getParent());
            List<String> optifineLines = Files.exists(optifineConfig)
                    ? new ArrayList<>(Files.readAllLines(optifineConfig, java.nio.charset.StandardCharsets.UTF_8))
                    : new ArrayList<>();
            int index = -1;
            for (int i = 0; i < optifineLines.size(); i++) {
                if (optifineLines.get(i).startsWith("shaderPack=")) {
                    index = i;
                    break;
                }
            }
            String line = "shaderPack=" + firstPack;
            if (index >= 0) {
                optifineLines.set(index, line);
            } else {
                optifineLines.add(line);
            }
            Files.write(optifineConfig, optifineLines, java.nio.charset.StandardCharsets.UTF_8);
            LOG.info("Activated shader pack in optionsshaders.txt: " + firstPack);
        } catch (IOException e) {
            LOG.warning("Failed to update optionsshaders.txt", e);
        }

        // Iris: config/iris.properties at container root
        Path irisConfig = containerPath.resolve("config").resolve("iris.properties");
        try {
            Files.createDirectories(irisConfig.getParent());
            List<String> irisLines = Files.exists(irisConfig)
                    ? new ArrayList<>(Files.readAllLines(irisConfig, java.nio.charset.StandardCharsets.UTF_8))
                    : new ArrayList<>();
            int index = -1;
            for (int i = 0; i < irisLines.size(); i++) {
                if (irisLines.get(i).startsWith("shaderPack=")) {
                    index = i;
                    break;
                }
            }
            String line = "shaderPack=" + firstPack;
            if (index >= 0) {
                irisLines.set(index, line);
            } else {
                irisLines.add(line);
            }
            Files.write(irisConfig, irisLines, java.nio.charset.StandardCharsets.UTF_8);
            LOG.info("Activated shader pack in iris.properties: " + firstPack);
        } catch (IOException e) {
            LOG.warning("Failed to update iris.properties", e);
        }
    }

    // --- Integrity check ---

    /**
     * Ensures all required subdirectories (mods/, resourcepacks/, shaderpacks/, saves/) exist.
     * Creates any missing directories and logs the action.
     */
    public void ensureContainerDirectories(Container container) {
        for (ContainerContentEntry.Type type : ContainerContentEntry.Type.values()) {
            Path dir = targetDirForType(container, type);
            try {
                if (!Files.isDirectory(dir)) {
                    Files.createDirectories(dir);
                    LOG.info("[Integrity] Created missing directory: " + dir);
                }
            } catch (IOException e) {
                LOG.warning("[Integrity] Failed to create directory: " + dir, e);
            }
        }
    }

    /**
     * Fully rebuilds content.json by scanning all subdirectories on disk from scratch.
     * Unlike scanAndAddNewContent, this purges any stale/orphaned entries and starts fresh.
     * @return number of entries found on disk, or -1 if saving failed
     */
    public int rebuildContentFromDisk(Container container) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            List<ContainerContentEntry> newEntries = new ArrayList<>();
            int totalFound = 0;

            for (ContainerContentEntry.Type type : ContainerContentEntry.Type.values()) {
                Path dir = targetDirForType(container, type);
                if (!Files.isDirectory(dir)) continue;

                try (Stream<Path> stream = Files.list(dir)) {
                    List<Path> files = stream
                            .filter(p -> Files.isRegularFile(p) || Files.isDirectory(p))
                            .collect(Collectors.toList());
                    for (Path file : files) {
                        Path sourcePath = file.toAbsolutePath().normalize();
                        if (type == ContainerContentEntry.Type.RESOURCE_PACK) {
                            String gameVersion = resolveContainerGameVersion(container.getLinkedVersionId());
                            ResourcePackValidator.ValidationResult result = ResourcePackValidator.validate(sourcePath, gameVersion);
                            if (result.isError()) {
                                LOG.warning("[Integrity] Invalid resource pack found on disk, registered anyway: "
                                        + file.getFileName().toString());
                            }
                        }
                        ContainerContentEntry entry = new ContainerContentEntry(
                                type,
                                sourcePath.toString(),
                                file.getFileName().toString(),
                                Instant.now()
                        );
                        newEntries.add(entry);
                        totalFound++;
                    }
                } catch (IOException e) {
                    LOG.warning("[Integrity] Failed to scan directory: " + dir, e);
                }
            }

            if (saveContent(container, newEntries)) {
                LOG.info("[Integrity] Rebuilt content.json for container " + container.getName()
                        + ": " + totalFound + " entries");
                return totalFound;
            } else {
                LOG.warning("[Integrity] Failed to save rebuilt content.json for container " + container.getName());
                return -1;
            }
        } finally {
            lock.unlock();
        }
    }

    // --- Add content ---

    public boolean addContent(Container container, ContainerContentEntry.Type type, Path sourceFile)
            throws ContainerValidationException {
        Path targetDir = targetDirForType(container, type);
        String fileName = sourceFile.getFileName().toString();
        Path target = targetDir.resolve(fileName);

        // File system check — no lock needed
        if (Files.exists(target)) {
            LOG.warning("Content already exists in container: " + fileName);
            throw new ContainerValidationException(
                    i18n("container.add.failed.already_exists", fileName));
        }

        // Validate resource packs — no lock needed
        if (type == ContainerContentEntry.Type.RESOURCE_PACK) {
            String gameVersion = resolveContainerGameVersion(container.getLinkedVersionId());
            ResourcePackValidator.ValidationResult result = ResourcePackValidator.validate(sourceFile, gameVersion);
            if (result.isError()) {
                String message = i18n(result.getMessageKey(), result.getMessageArgs());
                LOG.warning("Resource pack validation failed: " + message + " - " + sourceFile);
                throw new ContainerValidationException(message);
            }
            if (result.isWarning()) {
                String message = i18n(result.getMessageKey(), result.getMessageArgs());
                LOG.warning("Resource pack validation warning: " + message + " - " + sourceFile);
            }
        }

        // Critical section: file copy + content.json read-modify-write (atomic under lock)
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            if (!createLinkOrCopy(sourceFile, target)) {
                return false;
            }

            List<ContainerContentEntry> entries = getContent(container);

            // Re-check duplicates under lock (TOCTOU prevention)
            boolean alreadyAdded = entries.stream()
                    .anyMatch(e -> e.getSourcePath().equals(sourceFile.toAbsolutePath().normalize().toString()));
            if (alreadyAdded) {
                LOG.warning("Content already registered in container manifest: " + fileName);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ex) {
                    LOG.warning("Failed to clean up orphan file after duplicate detection: " + target, ex);
                }
                throw new ContainerValidationException(
                        i18n("container.add.failed.already_exists", fileName));
            }

            ContainerContentEntry entry = new ContainerContentEntry(
                    type,
                    sourceFile.toAbsolutePath().normalize().toString(),
                    fileName,
                    Instant.now()
            );
            entry.setEnabled(true);
            entries.add(entry);
            saveContent(container, entries);
            return true;
        } finally {
            lock.unlock();
        }
    }

    static String resolveContainerGameVersion(String linkedVersionId) {
        if (linkedVersionId == null) return null;
        try {
            Profile profile = Profiles.getSelectedProfile();
            if (profile == null) return null;
            HMCLGameRepository repo = profile.getRepository();
            if (repo == null) return null;
            return repo.getGameVersion(linkedVersionId).orElse(null);
        } catch (Exception e) {
            LOG.warning("Failed to resolve container game version: " + e.getMessage());
            return null;
        }
    }

    // --- Remove content ---

    public boolean removeContent(Container container, ContainerContentEntry entry) {
        ReentrantLock lock = lockFor(container);
        lock.lock();
        try {
            Path target = Path.of(container.getContainerPath())
                    .resolve(targetDirForType(container, entry.getType()).getFileName())
                    .resolve(entry.getFileName());
            try {
                if (Files.isSymbolicLink(target) || Files.exists(target)) {
                    if (Files.isDirectory(target)) {
                        FileUtils.deleteDirectory(target.toFile());
                    } else {
                        Files.delete(target);
                    }
                }
            } catch (IOException e) {
                LOG.warning("Failed to remove content file: " + target, e);
                return false;
            }

            List<ContainerContentEntry> entries = getContent(container);
            entries.removeIf(e -> e.getSourcePath().equals(entry.getSourcePath()) && e.getFileName().equals(entry.getFileName()));
            saveContent(container, entries);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // --- List available global resources ---

    public List<AvailableContent> listAvailableMods() {
        return collectContent("mods", p -> {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".litemod");
        });
    }

    public List<AvailableContent> listAvailableWorlds() {
        return collectContent("saves", Files::isDirectory);
    }

    public List<AvailableContent> listAvailableResourcePacks() {
        return collectContent("resourcepacks", p -> {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".zip");
        });
    }

    public List<AvailableContent> listAvailableShaderPacks() {
        return collectContent("shaderpacks", p -> Files.isRegularFile(p) || Files.isDirectory(p));
    }

    private List<AvailableContent> collectContent(String subdir, Predicate<Path> filter) {
        Set<String> seen = new LinkedHashSet<>();
        List<AvailableContent> result = new ArrayList<>();
        File profileDir = getProfileGameDir();

        for (File base : getContentSourceDirs(profileDir)) {
            Path dir = base.toPath().resolve(subdir);
            if (!Files.isDirectory(dir)) continue;
            String origin = getOriginName(base, profileDir);
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> Files.isRegularFile(p) || Files.isDirectory(p))
                        .filter(filter)
                        .forEach(p -> {
                            String key = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            if (seen.add(key)) {
                                long size = 0;
                                try { size = Files.size(p); } catch (IOException ignored) {}
                                result.add(new AvailableContent(p.toAbsolutePath().normalize(), origin, size));
                            }
                        });
            } catch (IOException e) {
                LOG.warning("Failed to list directory: " + dir, e);
            }
        }

        result.sort(Comparator.comparing(AvailableContent::getFileName));
        return result;
    }

    private static List<File> getContentSourceDirs(File profileDir) {
        List<File> dirs = new ArrayList<>();
        dirs.add(profileDir);
        Path defaultMc = Metadata.MINECRAFT_DIRECTORY.toAbsolutePath().normalize();
        Path profilePath = profileDir.toPath().toAbsolutePath().normalize();
        if (!defaultMc.equals(profilePath)) {
            dirs.add(defaultMc.toFile());
        }
        for (Profile p : Profiles.profilesProperty()) {
            File d = p.getGameDir();
            if (d != null) {
                Path dp = d.toPath().toAbsolutePath().normalize();
                if (!dp.equals(profilePath) && !dp.equals(defaultMc)) {
                    dirs.add(d);
                }
            }
        }
        return dirs;
    }

    private static String getOriginName(File baseDir, File profileDir) {
        Path base = baseDir.toPath().toAbsolutePath().normalize();
        if (base.equals(profileDir.toPath().toAbsolutePath().normalize())) {
            return i18n("container.origin.selected_profile");
        }
        if (base.equals(Metadata.MINECRAFT_DIRECTORY.toAbsolutePath().normalize())) {
            return i18n("container.origin.default_minecraft");
        }
        for (Profile p : Profiles.profilesProperty()) {
            if (p.getGameDir() != null && p.getGameDir().toPath().toAbsolutePath().normalize().equals(base)) {
                return i18n("container.origin.profile", p.getName());
            }
        }
        return base.getFileName().toString();
    }

    private static File getProfileGameDir() {
        Profile profile = Profiles.getSelectedProfile();
        return profile != null ? profile.getGameDir() : new File(".");
    }

    public static class AvailableContent {
        private final Path path;
        private final String origin;
        private final long size;

        public AvailableContent(Path path, String origin, long size) {
            this.path = path;
            this.origin = origin;
            this.size = size;
        }

        public Path getPath() { return path; }
        public String getOrigin() { return origin; }
        public long getSize() { return size; }
        public String getFileName() { return path.getFileName().toString(); }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    // --- Version tracking ---

    public void setContainerVersion(Container container, String versionId) {
        containers.removeIf(c -> c.getId().equals(container.getId()));
        Container updated = new Container(
                container.getId(), container.getName(), versionId,
                container.getContainerPath(), container.getCreatedAt(), container.getDescription()
        );
        containers.add(updated);
        save();
    }

    // --- Update name / description ---

    public void updateContainerName(Container container, String newName) {
        containers.removeIf(c -> c.getId().equals(container.getId()));
        Container updated = new Container(
                container.getId(), newName, container.getLinkedVersionId(),
                container.getContainerPath(), container.getCreatedAt(), container.getDescription()
        );
        containers.add(updated);
        save();
    }

    public void updateContainerDescription(Container container, String newDescription) {
        containers.removeIf(c -> c.getId().equals(container.getId()));
        Container updated = new Container(
                container.getId(), container.getName(), container.getLinkedVersionId(),
                container.getContainerPath(), container.getCreatedAt(), newDescription
        );
        containers.add(updated);
        save();
    }

    // --- Launch Profile ---

    private Path getLaunchProfilePath(Container container) {
        return Path.of(container.getContainerPath()).resolve("launch_profile.json");
    }

    public LaunchProfile loadLaunchProfile(Container container) {
        Path file = getLaunchProfilePath(container);
        try {
            if (Files.exists(file)) {
                return JsonUtils.fromJsonFile(file, LaunchProfile.class);
            }
        } catch (IOException e) {
            LOG.warning("Failed to load launch profile for container " + container.getName(), e);
        }
        return null;
    }

    public void saveLaunchProfile(Container container, LaunchProfile profile) {
        try {
            JsonUtils.writeToJsonFileAtomically(getLaunchProfilePath(container), profile);
        } catch (IOException e) {
            LOG.warning("Failed to save launch profile for container " + container.getName(), e);
        }
    }

    public void deleteLaunchProfile(Container container) {
        try {
            Files.deleteIfExists(getLaunchProfilePath(container));
        } catch (IOException e) {
            LOG.warning("Failed to delete launch profile for container " + container.getName(), e);
        }
    }

    // --- Launch ---

    public void launchContainer(UUID containerId) throws ContainerLaunchException {
        launchContainer(containerId, null, null);
    }

    public void launchContainer(UUID containerId, Runnable onComplete) throws ContainerLaunchException {
        launchContainer(containerId, onComplete, null);
    }

    public void launchContainer(UUID containerId, Runnable onComplete,
                                BiConsumer<List<String>, Runnable> onDeployFailures) throws ContainerLaunchException {
        if (!launchingContainers.add(containerId)) {
            LOG.info("Container " + containerId + " is already being launched, ignoring duplicate request");
            return;
        }

        Container container = getContainer(containerId).orElse(null);
        if (container == null) {
            launchingContainers.remove(containerId);
            LOG.warning("launchContainer: container not found for id " + containerId);
            if (onComplete != null) onComplete.run();
            FXUtils.runInFX(() -> Controllers.dialog(
                    i18n("container.launch.failed.not_found"),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR));
            return;
        }
        if (container.getLinkedVersionId() == null) {
            launchingContainers.remove(containerId);
            LOG.warning("launchContainer: linkedVersionId is null for container " + containerId);
            if (onComplete != null) onComplete.run();
            FXUtils.runInFX(() -> Controllers.dialog(
                    i18n("container.launch.failed.no_version"),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR));
            return;
        }

        Profile profile = Profiles.getSelectedProfile();
        if (profile == null) {
            launchingContainers.remove(containerId);
            LOG.warning("launchContainer: selected profile is null");
            if (onComplete != null) onComplete.run();
            FXUtils.runInFX(() -> Controllers.dialog(
                    i18n("container.launch.failed.no_profile"),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR));
            return;
        }

        String version = container.getLinkedVersionId() != null
                ? container.getLinkedVersionId()
                : profile.getSelectedVersion();
        if (version == null) {
            launchingContainers.remove(containerId);
            LOG.warning("launchContainer: version is null for container " + containerId);
            if (onComplete != null) onComplete.run();
            FXUtils.runInFX(() -> Controllers.dialog(
                    i18n("container.launch.failed.no_version"),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR));
            return;
        }

        Account account = Accounts.getSelectedAccount();
        if (account == null) {
            launchingContainers.remove(containerId);
            if (onComplete != null) onComplete.run();
            throw new ContainerLaunchException(i18n("account.missing"));
        }

        final Container capturedContainer = container;
        final Profile capturedProfile = profile;
        final Account capturedAccount = account;
        final String capturedVersion = version;
        final java.util.concurrent.atomic.AtomicReference<List<String>> failuresRef = new java.util.concurrent.atomic.AtomicReference<>();

        Task.runAsync(() -> {
            failuresRef.set(deployContent(capturedContainer));
        }).whenComplete(Schedulers.javafx(), (v, exc) -> {
            try {
                if (exc != null) {
                    LOG.warning("Container deploy failed", exc);
                    launchingContainers.remove(containerId);
                    if (onComplete != null) Platform.runLater(() -> onComplete.run());
                    return;
                }

                List<String> failures = failuresRef.get();

                if (!java.util.Objects.equals(capturedAccount, Accounts.getSelectedAccount())) {
                    return;
                }

                if (!failures.isEmpty()) {
                    if (onDeployFailures != null) {
                        Runnable continueAction = () -> {
                            try {
                                LaunchOrchestrator.doLaunch(capturedContainer, capturedProfile, capturedAccount, capturedVersion);
                            } catch (ContainerLaunchException e) {
                                LOG.warning("Launcher failed after deploy failures", e);
                                FXUtils.runInFX(() -> Controllers.dialog(e.getMessage(),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR));
                            } finally {
                                launchingContainers.remove(containerId);
                            }
                        };
                        onDeployFailures.accept(failures, continueAction);
                    } else {
                        LOG.warning("Deploy completed with " + failures.size() + " failures for container " + containerId);
                        StringBuilder msg = new StringBuilder(i18n("container.deploy.failures") + ":\n");
                        for (String f : failures) {
                            msg.append("• ").append(f).append("\n");
                        }
                        FXUtils.runInFX(() -> Controllers.confirm(
                                msg.toString(),
                                i18n("container.deploy.warning"),
                                MessageDialogPane.MessageType.WARNING,
                                () -> {
                                    try {
                                        LaunchOrchestrator.doLaunch(capturedContainer, capturedProfile, capturedAccount, capturedVersion);
                                    } catch (ContainerLaunchException e) {
                                        LOG.warning("Launcher failed after deploy failures", e);
                                        FXUtils.runInFX(() -> Controllers.dialog(e.getMessage(),
                                                i18n("message.error"), MessageDialogPane.MessageType.ERROR));
                                    } finally {
                                        launchingContainers.remove(containerId);
                                    }
                                },
                                () -> {
                                    launchingContainers.remove(containerId);
                                    if (onComplete != null) Platform.runLater(() -> onComplete.run());
                                }));
                    }
                } else {
                    try {
                        LaunchOrchestrator.doLaunch(capturedContainer, capturedProfile, capturedAccount, capturedVersion);
                    } catch (ContainerLaunchException e) {
                        LOG.warning("Launcher failed", e);
                        FXUtils.runInFX(() -> Controllers.dialog(e.getMessage(),
                                i18n("message.error"), MessageDialogPane.MessageType.ERROR));
                    } finally {
                        launchingContainers.remove(containerId);
                    }
                }
            } finally {
                if (onComplete != null) onComplete.run();
            }
        }).start();
    }

    public void openContainerFolder(UUID containerId) {
        Container container = getContainer(containerId).orElse(null);
        if (container == null) return;
        File dir = new File(container.getContainerPath());
        if (dir.isDirectory()) {
            FXUtils.openFolder(dir);
        }
    }

    // --- Modpack import / export ---

    public ContainerModpackImportTask importModpack(Container container, Path modpackFile) throws IOException {
        if (container.getLinkedVersionId() == null) {
            throw new IllegalStateException(i18n("container.modpack.import.no_version"));
        }

        return new ContainerModpackImportTask(container, modpackFile);
    }

    public ContainerModpackImportTask importModpackFromVersion(Container container, String versionId) throws IOException {
        if (container.getLinkedVersionId() == null) {
            throw new IllegalStateException(i18n("container.modpack.import.no_version"));
        }

        Profile profile = Profiles.getSelectedProfile();
        if (profile == null) {
            throw new IOException("No active profile");
        }
        HMCLGameRepository repo = profile.getRepository();
        if (repo == null) {
            throw new IOException("No repository available");
        }

        File versionRoot = repo.getVersionRoot(versionId);
        if (versionRoot == null || !versionRoot.isDirectory()) {
            throw new IOException("Version directory not found: " + versionId);
        }

        return new ContainerModpackImportTask(container, versionRoot.toPath(), versionRoot.toPath());
    }

    public ContainerModpackExportTask exportModpack(Container container, Path outputFile,
                                                     String packName, String packVersion) {
        return new ContainerModpackExportTask(
                container, outputFile, packName, packVersion);
    }

    // --- Installed modpack detection ---

    public List<InstalledModpackInfo> getInstalledModpacks() {
        List<InstalledModpackInfo> result = new ArrayList<>();
        Profile profile = Profiles.getSelectedProfile();
        if (profile == null) return result;
        HMCLGameRepository repo = profile.getRepository();
        if (repo == null) return result;

        for (org.jackhuang.hmcl.game.Version v : repo.getVersions()) {
            if (v.isHidden()) continue;
            String versionId = v.getId();
            if (!repo.isModpack(versionId)) continue;

            String mcVersion = repo.getGameVersion(v).orElse(versionId);

            String loaderType = "";
            String loaderVersion = "";
            try {
                LoaderDetector.DetectedLoader dl = LoaderDetector.detect(repo, versionId);
                if (!dl.isVanilla()) {
                    loaderType = dl.getType();
                    loaderVersion = dl.getVersion() != null ? dl.getVersion() : "";
                }
            } catch (Exception ignored) {
            }

            String modpackName = versionId;
            String modpackVersion = "";
            try {
                File cfgFile = repo.getModpackConfiguration(versionId);
                if (cfgFile.exists()) {
                    ModpackConfiguration<?> cfg = JsonUtils.fromJsonFile(cfgFile.toPath(), new TypeToken<ModpackConfiguration<?>>() {});
                    if (cfg != null) {
                        if (cfg.getName() != null && !cfg.getName().isEmpty()) modpackName = cfg.getName();
                        if (cfg.getVersion() != null) modpackVersion = cfg.getVersion();
                    }
                }
            } catch (Exception ignored) {
            }

            result.add(new InstalledModpackInfo(versionId, modpackName, modpackVersion, mcVersion, loaderType, loaderVersion));
        }

        return result;
    }

    public static class InstalledModpackInfo {
        private final String versionId;
        private final String modpackName;
        private final String modpackVersion;
        private final String mcVersion;
        private final String loaderType;
        private final String loaderVersion;

        public InstalledModpackInfo(String versionId, String modpackName, String modpackVersion,
                                    String mcVersion, String loaderType, String loaderVersion) {
            this.versionId = versionId;
            this.modpackName = modpackName;
            this.modpackVersion = modpackVersion;
            this.mcVersion = mcVersion;
            this.loaderType = loaderType;
            this.loaderVersion = loaderVersion;
        }

        public String getVersionId() { return versionId; }
        public String getModpackName() { return modpackName; }
        public String getModpackVersion() { return modpackVersion; }
        public String getMcVersion() { return mcVersion; }
        public String getLoaderType() { return loaderType; }
        public String getLoaderVersion() { return loaderVersion; }
    }
}
