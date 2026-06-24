# Container UI Redesign - Change Document

## Files Modified

### `HMCL/src/main/java/org/jackhuang/hmcl/ui/container/ContainerListPage.java`
**Complete rewrite** of the container list page. The file was restructured from a single-list layout to a three-panel layout:

- **Left sidebar**: Unchanged — "Acciones" section with "Crear Contenedor" and "Refrescar" buttons
- **Center panel**: New search bar (`JFXTextField`) with 100ms debounce + 2-column `GridPane` of container cards
- **Right panel**: New detail panel (`ScrollPane` containing `VBox`) that appears when a card is clicked

Key architectural changes:
- Container data is cached in `allContainers` and filtered on-the-fly by `applyFilter()`
- Selected container state drives the detail panel visibility and content
- All dialog methods (`pickAndAddContent`, `showInstalledPicker`, `showVersionSelection`) unchanged in logic — they now call `rebuildDetailPanel()` instead of the old `refreshList()` to update just the detail panel after content modification
- `refreshList()` still handles full reload from `ContainerManager` for when containers are created/deleted

### `HMCL/src/main/resources/assets/lang/I18N.properties`
Added 11 new i18n keys for the redesigned UI (English).

### `HMCL/src/main/resources/assets/lang/I18N_es.properties`
Added 11 new i18n keys for the redesigned UI (Spanish).

## New Files Created

None. All changes are contained within existing files.

## New i18n Keys

| Key | English | Spanish |
|-----|---------|---------|
| `container.details.title` | Details of: %s | Detalles de: %s |
| `container.details.minecraft` | Minecraft | Minecraft |
| `container.details.modify_version` | Modify Version | Modificar Versión |
| `container.details.manage` | Manage | Gestionar |
| `container.details.general` | General Details | Detalles Generales |
| `container.details.created` | Created | Fecha de creación |
| `container.details.description` | Description | Descripción |
| `container.details.active` | Active | Activo |
| `container.details.inactive` | Inactive | Inactivo |
| `container.launches` | Launches | Lanzamientos |
| `container.elements` | Elements | Elementos |
| `container.world_thumbnail` | World | Mundo |

## Design Decisions

### 1. Three-panel layout
Used a `HBox` inside the center `StackPane` (from `DecoratorAnimatedPage`) to hold both the card grid and the detail panel side-by-side. The detail panel is initially hidden (`visible=false, managed=false`). When a container is selected, `visible/managed` are set to `true` and the grid shrinks to accommodate the 400px-wide detail panel.

### 2. GridPane for 2-column cards
Chose `GridPane` over `FlowPane` or `TilePane` because it gives explicit control over column widths (50% each via `ColumnConstraints`) and row placement. The grid is rebuilt entirely on each search filter change, which is efficient given small container counts.

### 3. No avatar/thumbnail system for containers
The Container model has no avatar field. Following the launcher's `VersionIconType` pattern would require adding per-container icons. Instead, `SVG.FOLDER` is used as the default container avatar (both in cards and detail panel header). This is consistent with the sidebar icon already used for "Contenedores" in the launcher.

### 4. Launch and settings counters hardcoded to "0"
The Container model does not track launch count or settings changes. These fields would need to be added to `Container.java` and persisted. The counters are shown as `0` with proper i18n labels as placeholders for future implementation. The elements count IS live-computed from `ContainerManager.getInstance().getContent(container).size()`.

### 5. Checkboxes in mod list are visual-only
`ContainerContentEntry` has no `active` field. `JFXCheckBox` is added for the UI layout but defaults to selected and has no backend effect. Adding a real toggle would require schema changes to `ContainerContentEntry` and the JSON persistence layer.

### 6. World thumbnails use generic LANDSCAPE icon
Minecraft world folders don't contain thumbnail images. Rather than adding a complex screenshot/thumbnail pipeline, each world entry shows `SVG.LANDSCAPE` as placeholder. The world folder name is displayed below the icon.

### 7. Footer buttons use SVG icons inline
`SVG.ROCKET_LAUNCH`, `SVG.FOLDER_OPEN`, and `SVG.DELETE` are rendered at 16px inside the footer buttons. This follows the same icon+text pattern used in `ToolbarListPageSkin.createToolbarButton2`.

### 8. Version/Loader resolution in cards
`resolveGameVersion()` and `resolveLoader()` call `Profiles.getSelectedProfile().getRepository()` to detect the game version and mod loader from the linked version ID. Both methods are wrapped in try-catch to handle missing versions gracefully (e.g., version deleted after container was linked).
