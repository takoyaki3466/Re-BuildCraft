package com.takoy3466.re_buildcraft.builders.blueprint;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 設計図の保管庫。元実装: {@code BptRootIndex} (全設計図の番号とファイル) と {@code BptPlayerIndex} (プレイヤーごとの図書館の一覧)。
 * <ul>
 *   <li>旧実装はゲームフォルダの {@code buildcraft/blueprints/} に 1 設計図 1 ファイル (.bpt) で保存していた。
 *       ここではマルチプレイでも扱えるよう、ワールドの保存データ (オーバーワールドの {@code data/re_buildcraft_blueprints.dat}) に保存する。</li>
 *   <li>設計図の番号は 1 から順に振り、アイテムはその番号を持つ (旧 ItemStack のダメージ値)。</li>
 *   <li>名前は保管庫の中で重複しないよう「名前_番号」にする (旧 storeBluePrint)。</li>
 *   <li>図書館の一覧はプレイヤーごとに名前順。初めて使うプレイヤーの一覧には既存の設計図がすべて入る (旧 BptPlayerIndex の初期化)。</li>
 *   <li>ワールドフォルダの {@code buildcraft/blueprints/*.bpt} (旧形式) は、読み込める限り取り込む (旧 importNewFiles)。</li>
 * </ul>
 */
public final class BlueprintStore extends SavedData {
    public static final String DATA_NAME = "re_buildcraft_blueprints";
    /** 旧形式のファイルを置く場所 (ワールドフォルダからの相対パス)。 */
    public static final String LEGACY_FOLDER = "buildcraft/blueprints";
    private static final String LEGACY_EXTENSION = ".bpt";
    private static final String DEFAULT_NAME = "unnamed";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_NEXT_ID = "next_id";
    private static final String TAG_BLUEPRINTS = "blueprints";
    private static final String TAG_ID = "id";
    private static final String TAG_DATA = "data";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_IDS = "ids";
    private static final String TAG_IMPORTED = "imported_legacy_files";

    private final Map<Integer, Blueprint> blueprints = new TreeMap<>();
    private final Map<UUID, Set<Integer>> playerIndexes = new HashMap<>();
    private final Set<String> importedLegacyFiles = new HashSet<>();
    private int nextId = 1;
    /** 旧形式のフォルダをこのサーバーの起動中に確認したか (保存しない)。 */
    private boolean legacyScanned;

    public static SavedData.Factory<BlueprintStore> factory() {
        return new SavedData.Factory<>(BlueprintStore::new, BlueprintStore::load);
    }

    /** サーバーの保管庫。初回に旧形式のファイルを取り込む。 */
    public static BlueprintStore get(MinecraftServer server) {
        BlueprintStore store = server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
        if (!store.legacyScanned) {
            store.legacyScanned = true;
            store.importLegacyFiles(server.getWorldPath(LevelResource.ROOT).resolve(LEGACY_FOLDER), server.registryAccess());
        }
        return store;
    }

    // ---- 設計図 ---------------------------------------------------------------

    @Nullable
    public Blueprint get(int id) {
        return blueprints.get(id);
    }

    /** 新しい番号で保存する (旧 storeBluePrint)。名前は重複しないように付け直す。 */
    public int store(Blueprint blueprint) {
        blueprint.setName(uniqueName(blueprint.name()));
        int id = nextId++;
        blueprints.put(id, blueprint);
        for (Set<Integer> index : playerIndexes.values()) {
            index.add(id);
        }
        setDirty();
        return id;
    }

    private String uniqueName(String requested) {
        String base = requested == null || requested.isBlank() ? DEFAULT_NAME : requested;
        Set<String> names = new HashSet<>();
        blueprints.values().forEach(existing -> names.add(existing.name()));
        if (!names.contains(base)) {
            return base;
        }
        int suffix = 0;
        while (names.contains(base + "_" + suffix)) {
            suffix++;
        }
        return base + "_" + suffix;
    }

    // ---- 図書館 (プレイヤーごとの一覧) -----------------------------------------------

    /** プレイヤーの一覧 (名前順の番号)。初めてなら既存の設計図をすべて入れる。 */
    public List<Integer> playerIndex(UUID player) {
        Set<Integer> index = playerIndexes.computeIfAbsent(player, key -> {
            setDirty();
            return new HashSet<>(blueprints.keySet());
        });
        index.removeIf(id -> !blueprints.containsKey(id));
        List<Integer> sorted = new ArrayList<>(index);
        sorted.sort((a, b) -> blueprints.get(a).name().compareTo(blueprints.get(b).name()));
        return sorted;
    }

    /** 図書館に登録する (旧 BptPlayerIndex.addBlueprint)。 */
    public void addToPlayer(UUID player, int id) {
        if (blueprints.containsKey(id)) {
            playerIndex(player);
            playerIndexes.get(player).add(id);
            setDirty();
        }
    }

    /** 図書館の一覧から外す (旧 deleteBluePrint。設計図そのものは消さない)。 */
    public void removeFromPlayer(UUID player, int id) {
        Set<Integer> index = playerIndexes.get(player);
        if (index != null && index.remove(id)) {
            setDirty();
        }
    }

    // ---- 旧形式の取り込み -----------------------------------------------------------

    private void importLegacyFiles(Path folder, HolderLookup.Provider registries) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) {
            files = listing.filter(path -> path.getFileName().toString().endsWith(LEGACY_EXTENSION)).sorted().toList();
        } catch (IOException e) {
            LOGGER.warn("Could not list legacy blueprints in {}", folder, e);
            return;
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            if (importedLegacyFiles.contains(fileName)) {
                continue;
            }
            importedLegacyFiles.add(fileName);
            setDirty();
            LegacyBlueprintReader.Result result = LegacyBlueprintReader.read(file, registries);
            if (result.blueprint() != null) {
                int id = store(result.blueprint());
                LOGGER.info("Imported legacy blueprint {} as #{} ({})", fileName, id, result.blueprint().name());
            } else {
                // 変換できない設計図は別のブロックに置き換えずに取り込まない。
                LOGGER.warn("Legacy blueprint {} was not imported: {}", fileName, result.error());
            }
        }
    }

    // ---- 保存 -----------------------------------------------------------------

    private static BlueprintStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BlueprintStore store = new BlueprintStore();
        store.nextId = Math.max(1, tag.getInt(TAG_NEXT_ID));
        ListTag list = tag.getList(TAG_BLUEPRINTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Blueprint blueprint = Blueprint.load(entry.getCompound(TAG_DATA), registries);
            if (blueprint != null) {
                store.blueprints.put(entry.getInt(TAG_ID), blueprint);
            } else {
                LOGGER.warn("Blueprint #{} could not be loaded (unknown blocks?) and is skipped", entry.getInt(TAG_ID));
            }
        }
        ListTag players = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            Set<Integer> ids = new HashSet<>();
            for (int id : entry.getIntArray(TAG_IDS)) {
                ids.add(id);
            }
            store.playerIndexes.put(entry.getUUID(TAG_PLAYER), ids);
        }
        ListTag imported = tag.getList(TAG_IMPORTED, Tag.TAG_STRING);
        for (int i = 0; i < imported.size(); i++) {
            store.importedLegacyFiles.add(imported.getString(i));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_NEXT_ID, nextId);
        ListTag list = new ListTag();
        blueprints.forEach((id, blueprint) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_ID, id);
            entry.put(TAG_DATA, blueprint.save());
            list.add(entry);
        });
        tag.put(TAG_BLUEPRINTS, list);
        ListTag players = new ListTag();
        playerIndexes.forEach((player, ids) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_PLAYER, player);
            entry.putIntArray(TAG_IDS, ids.stream().mapToInt(Integer::intValue).toArray());
            players.add(entry);
        });
        tag.put(TAG_PLAYERS, players);
        ListTag imported = new ListTag();
        importedLegacyFiles.forEach(name -> imported.add(StringTag.valueOf(name)));
        tag.put(TAG_IMPORTED, imported);
        return tag;
    }
}
