package org.jackhuang.hmcl.container;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ContainerModpackExportTask extends Task<Void> {

    private final Container container;
    private final Path outputFile;
    private final String packName;
    private final String packVersion;

    public ContainerModpackExportTask(Container container, Path outputFile,
                                      String packName, String packVersion) {
        this.container = container;
        this.outputFile = outputFile;
        this.packName = packName;
        this.packVersion = packVersion;

        onDone().register(event -> {
            if (event.isFailed()) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException e) {
                    LOG.warning("Could not clean up failed export: " + outputFile, e);
                }
            }
        });
    }

    @Override
    public void execute() throws Exception {
        Path containerPath = Path.of(container.getContainerPath());
        LOG.info("Exporting container " + container.getName() + " to " + outputFile);

        String[] dirsToExport = {"mods", "resourcepacks", "shaderpacks", "config", "saves"};

        try (Zipper zip = new Zipper(outputFile)) {
            int totalDirs = 0;
            for (String dir : dirsToExport) {
                Path dirPath = containerPath.resolve(dir);
                if (Files.isDirectory(dirPath)) {
                    totalDirs++;
                }
            }
            updateProgress(0, Math.max(totalDirs, 1));

            int completed = 0;
            for (String dir : dirsToExport) {
                Path dirPath = containerPath.resolve(dir);
                if (Files.isDirectory(dirPath)) {
                    LOG.info("Adding directory to export: " + dir);
                    zip.putDirectory(dirPath, dir);
                }
                completed++;
                updateProgress(completed, Math.max(totalDirs, 1));
            }

            Path launchProfileFile = containerPath.resolve("launch_profile.json");
            if (Files.exists(launchProfileFile)) {
                zip.putFile(launchProfileFile, "launch_profile.json");
                LOG.info("Added launch_profile.json to export");
            }

            Path contentFile = containerPath.resolve("content.json");
            if (Files.exists(contentFile)) {
                zip.putFile(contentFile, "content.json");
                LOG.info("Added content.json to export");
            }

            Map<String, String> packInfo = new HashMap<>();
            packInfo.put("name", packName);
            packInfo.put("version", packVersion);
            packInfo.put("mcVersion", container.getLinkedVersionId() != null
                    ? container.getLinkedVersionId() : "unknown");
            packInfo.put("loader", detectLoader());

            String packInfoJson = JsonUtils.GSON.toJson(packInfo);
            zip.putTextFile(packInfoJson, "pack_info.json");
            LOG.info("Added pack_info.json to export");

            updateProgress(1, 1);
        }

        LOG.info("Export completed: " + outputFile);
    }

    private String detectLoader() {
        String linkedVersion = container.getLinkedVersionId();
        if (linkedVersion != null) {
            try {
                Profile profile = Profiles.getSelectedProfile();
                if (profile != null) {
                    HMCLGameRepository repo = profile.getRepository();
                    if (repo != null && repo.isLoaded()) {
                        org.jackhuang.hmcl.game.Version resolved = repo.getResolvedVersion(linkedVersion);
                        if (resolved != null) {
                            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved,
                                    repo.getGameVersion(resolved).orElse(null));
                            if (analyzer.has(LibraryAnalyzer.LibraryType.FABRIC)) return "fabric";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) return "forge";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE)) return "neoforge";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.QUILT)) return "quilt";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.LITELOADER)) return "liteloader";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM)) return "cleanroom";
                            if (analyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE)) return "optifine";
                            return "vanilla";
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("LibraryAnalyzer detection failed, falling back to filename heuristics", e);
            }
        }

        Path modsDir = Path.of(container.getContainerPath()).resolve("mods");
        if (!Files.isDirectory(modsDir)) return "vanilla";
        try {
            return Files.list(modsDir)
                    .filter(Files::isRegularFile)
                    .map(f -> f.getFileName().toString().toLowerCase())
                    .filter(n -> n.contains("fabric") || n.contains("neoforge") || n.contains("forge") || n.contains("quilt"))
                    .findFirst()
                    .map(n -> {
                        if (n.contains("fabric")) return "fabric";
                        if (n.contains("neoforge")) return "neoforge";
                        if (n.contains("forge")) return "forge";
                        if (n.contains("quilt")) return "quilt";
                        return "unknown";
                    })
                    .orElse("vanilla");
        } catch (IOException e) {
            LOG.warning("Could not detect loader from mods directory", e);
            return "unknown";
        }
    }
}
