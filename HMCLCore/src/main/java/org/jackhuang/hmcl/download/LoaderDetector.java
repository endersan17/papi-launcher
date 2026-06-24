package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.game.VersionNotFoundException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LoaderDetector {

    // -----------------------------------------------------------------
    // Loader type name constants
    // -----------------------------------------------------------------
    public static final String NEO_FORGE = "NeoForge";
    public static final String FORGE = "Forge";
    public static final String FABRIC = "Fabric";
    public static final String QUILT = "Quilt";
    public static final String OPTIFINE = "OptiFine";
    public static final String LITELOADER = "LiteLoader";
    public static final String CLEANROOM = "Cleanroom";
    public static final String OTHER = "Other";

    /**
     * All known loader types in detection priority order.
     * Each entry is both a well-known loader identifier and the display name.
     */
    public static final String[] ALL_LOADERS = {
            NEO_FORGE, FORGE, FABRIC, QUILT, OPTIFINE, LITELOADER, CLEANROOM, OTHER
    };

    private LoaderDetector() {
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Detect the mod loader for the given version.
     *
     * @param repo    the game repository
     * @param version the version to analyze
     * @return detected loader info; {@code type == null} means vanilla
     */
    public static DetectedLoader detect(GameRepository repo, Version version) {
        String versionId = version.getId();
        String gameVersion = repo.getGameVersion(version).orElse(versionId);
        return detectResolved(repo, versionId, gameVersion);
    }

    /**
     * Detect the mod loader for the version identified by {@code versionId}.
     *
     * @param repo      the game repository
     * @param versionId the version id to look up and analyze
     * @return detected loader info; {@code type == null} means vanilla
     */
    public static DetectedLoader detect(GameRepository repo, String versionId) {
        try {
            Version version = repo.getVersion(versionId);
            String gameVersion = repo.getGameVersion(version).orElse(versionId);
            return detectResolved(repo, versionId, gameVersion);
        } catch (VersionNotFoundException e) {
            LOG.warning("Cannot detect loader: version '" + versionId + "' not found");
            return new DetectedLoader(null, null);
        }
    }

    // -----------------------------------------------------------------
    // Internal implementation
    // -----------------------------------------------------------------

    /**
     * Core detection logic.
     * <p>
     * Loader detection strategy (by priority):
     * <ol>
     *   <li><b>NeoForge</b> - library group {@code net.neoforged.fancymodloader}</li>
     *   <li><b>Forge</b> - library group {@code net.minecraftforge} (excludes NeoForge)</li>
     *   <li><b>Fabric</b> - library group {@code net.fabricmc}, artifact {@code fabric-loader}</li>
     *   <li><b>Quilt</b> - library group {@code org.quiltmc}, artifact {@code quilt-loader}</li>
     *   <li><b>OptiFine</b> - library group {@code optifine} or {@code net.optifine}</li>
     *   <li><b>LiteLoader</b> - library group {@code com.mumfrey}, artifact {@code liteloader}</li>
     *   <li><b>Cleanroom</b> - library group {@code com.cleanroommc}, artifact {@code cleanroom}</li>
     *   <li><b>Other</b> - custom mainClass detected ({@link LibraryAnalyzer#isModded()}) but no known loader</li>
     *   <li><b>Vanilla</b> - no loader present</li>
     * </ol>
     * <p>
     * The analysis is performed on the <b>resolved</b> version (inheritsFrom chain fully
     * resolved) so that all inherited libraries and patches are visible to the analyzer.
     */
    private static DetectedLoader detectResolved(GameRepository repo, String versionId, String gameVersion) {
        try {
            String resolvedGameVer = repo.getGameVersion(versionId).orElse(gameVersion);
            Version resolved = repo.getResolvedVersion(versionId);

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved, resolvedGameVer);

            // Priority order: NeoForge before Forge (NeoForge uses similar patterns)
            if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE)) {
                return new DetectedLoader(NEO_FORGE, analyzer.getVersion(LibraryAnalyzer.LibraryType.NEO_FORGE).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) {
                return new DetectedLoader(FORGE, analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.FABRIC)) {
                return new DetectedLoader(FABRIC, analyzer.getVersion(LibraryAnalyzer.LibraryType.FABRIC).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.QUILT)) {
                return new DetectedLoader(QUILT, analyzer.getVersion(LibraryAnalyzer.LibraryType.QUILT).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE)) {
                return new DetectedLoader(OPTIFINE, analyzer.getVersion(LibraryAnalyzer.LibraryType.OPTIFINE).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.LITELOADER)) {
                return new DetectedLoader(LITELOADER, analyzer.getVersion(LibraryAnalyzer.LibraryType.LITELOADER).orElse(null));
            }
            if (analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM)) {
                return new DetectedLoader(CLEANROOM, analyzer.getVersion(LibraryAnalyzer.LibraryType.CLEANROOM).orElse(null));
            }

            // Unknown modded version: has a custom mainClass but no known loader library
            if (LibraryAnalyzer.isModded(repo, resolved)) {
                LOG.warning("Unknown modded version detected: " + versionId
                        + ", mainClass=" + resolved.getMainClass());
                return new DetectedLoader(OTHER, null);
            }

            // Vanilla — no loader present
            return new DetectedLoader(null, null);
        } catch (Exception e) {
            LOG.warning("Failed to detect loader for " + versionId + ": " + e);
            return new DetectedLoader(null, null);
        }
    }

    // -----------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------

    /**
     * Describes the result of a loader detection operation.
     * <ul>
     *   <li>{@code type == null} &rarr; vanilla (no mod loader)</li>
     *   <li>{@code type != null} &rarr; a mod loader was detected; see the type constants</li>
     *   <li>{@code version} &rarr; the loader version string, may be {@code null} if unavailable</li>
     * </ul>
     */
    public static final class DetectedLoader {
        private final String type;
        private final String version;

        public DetectedLoader(String type, String version) {
            this.type = type;
            this.version = version;
        }

        public String getType() {
            return type;
        }

        public String getVersion() {
            return version;
        }

        public boolean isVanilla() {
            return type == null;
        }

        public boolean isModded() {
            return type != null;
        }
    }
}
