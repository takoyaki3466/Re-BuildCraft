# NOTICE (Re-BuildCraft)

## 由来
このプロジェクトは、Minecraft 1.2.5 向けの BuildCraft 3.1.5.x の非公式修正版 **BCx 3.1.5.17**
(配布ファイル `@BuildCraft3.1.5.17.zip`) を Minecraft 1.21.1 / NeoForge 向けに書き直したものです。

- 元の著作権表示 (BCx のソースおよび `BuildCraftCore.COPYLIGHT` より):
  `Copyright (c) SpaceToad, 2011-2012, takanasayo 2013-2014`
- 元のソースヘッダーに記載されたライセンス:
  `BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL.`
  `Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt`

## 含まれる元の資産
- `src/main/resources/assets/re_buildcraft/textures/` の画像は、BCx の `block_textures.png` / `item_textures.png` /
  `net/minecraft/src/buildcraft/**/gui/*.png` から切り出した、または複製したものです。
- `oil_*.png` / `fuel_*.png` のみ、元の手続き生成テクスチャ (TextureOilFX / TextureFuelFX) の色範囲から新たに生成しました。
- Java コードは元のコードの処理を参照して 1.21.1 向けに書き直したもので、元のコードをそのまま含んではいません。

## 未確認事項 (要対応)
- BCx (takanasayo 氏による修正部分) に MMPL と別の条件があるかは、配布物から確認できませんでした。
- `gradle.properties` の `mod_license` は上記の調査結果を表すもので、権利関係を確定したものではありません。
