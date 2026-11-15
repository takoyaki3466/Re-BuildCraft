package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 設計台・図書館の GUI が使う通信。旧実装はクライアントがタイルエンティティを直接書き換えていたが、
 * ここではクライアントの入力をサーバーへ送り、サーバー側で検証してから反映する。
 */
public final class BuilderPayloads {
    private static final String VERSION = "1";

    private BuilderPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ArchitectName.TYPE, ArchitectName.STREAM_CODEC, BuilderPayloads::handleArchitectName);
        registrar.playToClient(LibraryContents.TYPE, LibraryContents.STREAM_CODEC, BuilderPayloads::handleLibraryContents);
    }

    /** 設計台の名前欄 (旧 GuiTemplate の文字入力)。 */
    public record ArchitectName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<ArchitectName> TYPE = new Type<>(ReBuildcraft.id("architect_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArchitectName> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ArchitectName::pos,
                ByteBufCodecs.stringUtf8(ArchitectBlockEntity.MAX_NAME_LENGTH), ArchitectName::name,
                ArchitectName::new);

        @Override
        public Type<ArchitectName> type() {
            return TYPE;
        }
    }

    /** 図書館の一覧 (旧 TileBlueprintLibrary.getNextPage の元になるプレイヤーの一覧)。 */
    public record LibraryContents(int containerId, List<LibraryEntry> entries) implements CustomPacketPayload {
        public static final Type<LibraryContents> TYPE = new Type<>(ReBuildcraft.id("library_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryContents> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LibraryContents::containerId,
                LibraryEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), LibraryContents::entries,
                LibraryContents::new);

        @Override
        public Type<LibraryContents> type() {
            return TYPE;
        }
    }

    /** 図書館の一覧の 1 行。 */
    public record LibraryEntry(int id, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LibraryEntry::id,
                ByteBufCodecs.STRING_UTF8, LibraryEntry::name,
                LibraryEntry::new);
    }

    /** サーバー: 開いている設計台の名前だけを、使える文字に絞って設定する。 */
    private static void handleArchitectName(ArchitectName payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ArchitectMenu menu && menu.getArchitect().getBlockPos().equals(payload.pos())
                && menu.stillValid(context.player())) {
            menu.getArchitect().setName(ArchitectBlockEntity.sanitizeName(payload.name()));
        }
    }

    /** クライアント: 開いている図書館の一覧を差し替える。 */
    private static void handleLibraryContents(LibraryContents payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof LibraryMenu menu && menu.containerId == payload.containerId()) {
            menu.setEntries(payload.entries());
        }
    }
}
