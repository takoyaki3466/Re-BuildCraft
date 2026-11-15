package com.takoy3466.re_buildcraft.builders.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * テンプレート・設計図アイテムが指す保管庫の設計図 (旧 ItemStack のダメージ値 = 設計図の番号)。
 * 名前はクライアントで説明文に出すために一緒に持つ。
 *
 * @param id   {@link BlueprintStore} の番号
 * @param name 設計図の名前
 */
public record BlueprintRef(int id, String name) {
    public static final Codec<BlueprintRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(BlueprintRef::id),
            Codec.STRING.fieldOf("name").forGetter(BlueprintRef::name)
    ).apply(instance, BlueprintRef::new));

    public static final StreamCodec<ByteBuf, BlueprintRef> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlueprintRef::id,
            ByteBufCodecs.STRING_UTF8, BlueprintRef::name,
            BlueprintRef::new);
}
