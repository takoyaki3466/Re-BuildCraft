# Re-BuildCraft 移行記録 (BuildCraft 3.1.5.x / BCx → Minecraft 1.21.1)

## 元資料
- `@BuildCraft3.1.5.17.zip` … BCx (BuildCraft Unofficial Fix) 3.1.5.17。Minecraft 1.2.5 / MinecraftForge 3.4.9.171 / ModLoader 系。
  - `bc3.1.5.x_src/` に Java ソース 212 ファイル (BCx で追加・変更されたクラス中心)。
  - `buildcraft/` 以下のコンパイル済みクラスのうち 136 クラスはソースが無く、調査には逆コンパイル結果を使用した。
  - ライセンス: ソースヘッダーは「Minecraft Mod Public License 1.0 (MMPL)」, Copyright SpaceToad 2011-2012, takanasayo 2013-2014。
    `mod_license` は MMPL 1.0 由来であることを示す表記に変更し、著作権表示と未確認事項を `NOTICE.md` に記載した (MMPL 全文は未同梱・要対応)。

## 開発環境 (既存プロジェクトの設定をそのまま採用)
| 項目 | 値 |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod ローダー | NeoForge 21.1.265 (ModDevGradle 2.0.148) |
| Java | 21 (toolchain) |
| マッピング | Mojang 公式名 + Parchment `1.21` / `2024.11.10` (下記「Parchment と複合ビルド」参照) |
| Mod ID | `re_buildcraft` |

## パッケージ構成
| パッケージ | 役割 |
| --- | --- |
| `api.power` | MJ 動力 API (`IPowerReceptor`, `PowerProvider`, `BuildCraftCapabilities.POWER_RECEPTOR`, `PoweredBlockEntity`) |
| `api.engine` | エンジン基盤 (`EngineBlock`, `EngineBlockEntity`, `EngineLogic`, `RedstoneEngineLogic`, `EnergyStage`, `EngineFuel` データマップ) |
| `api.laser` | レーザー基盤 (`LaserBlock`, `LaserBlockEntity`, `LaserSpec`, `ILaserTarget`) |
| `api.recipe` | 組立台レシピ (`re_buildcraft:assembly`)、精製機レシピ (`re_buildcraft:refinery`) |
| `api.client` | アドオンも使える描画 (`EngineRenderer`, `LaserBeamRenderer`, `BoxRenderer`) |
| `api` / `api.util` | `BuildCraftTags` (レンチ `c:tools/wrench`), `InventoryUtils`, `SafeTimeTracker`, `IMachine`, 運転指示 `MachineControl` / `IActionReceptor` |
| `api.area` | 範囲提供 API (`IAreaProvider`, `AreaProviders.findNearby`)。マーカーが実装し、クアーリー (CE の改良型を含む)・フィラーが使う |
| `api.menu` | GUI 共通部品 (`PlayerInventorySlots`。CE からも使うため `core.menu` から移動) |
| `energy` / `energy.worldgen` / `factory` / `silicon` / `builders` / `builders.filler` / `core` | 本体固有の機械・メニュー・石油の生成 (CE からは参照しない) |
| `registry` | DeferredRegister による登録 |
| `client` | クライアント専用 (画面・ポンプのチューブ描画・液体テクスチャ) |

CE (rebuild_compact_engine) は `api.*` と `ReBuildcraft` 以外を参照しない。
(`api.recipe.AssemblyRecipe` は種別・シリアライザー取得のため `registry.ModRecipes` を参照している。)

## 機能・依存関係マトリクス
状態: 実装済み = 登録・処理・保存・リソースまで静的に接続を確認済み (動作は未検証) / 未対応 = 移行していない

| 機能 | 元の実装 | 移行先 | 状態 | 備考 |
| --- | --- | --- | --- | --- |
| 動力 (MJ) API | `api.PowerProvider`, `PowerFramework`, `IPowerReceptor`, `PneumaticPowerProvider` | `api.power.*` | 実装済み | BCx は常に Pneumatic を使うため PowerFramework 切替は移行せず。latency は Pneumatic と同じく 0 固定 |
| 歯車 5 種 | `BuildCraftCore` | `ModItems` + レシピ | 実装済み | BCx 既定 `changeGearRecipes=true` (全て木の歯車を中心) |
| レンチ | `BuildCraftCore.wrenchItem` | `core.WrenchItem` + タグ `c:tools/wrench` | 実装済み | 判定をタグに変更 |
| 木/石/鉄エンジン | `energy.Engine*`, `TileEngine`, `BlockEngine`, `ItemEngine`, `RenderEngine` | `api.engine.*`, `energy.*`, `api.client.EngineRenderer` | 実装済み | メタデータ→別ブロック。石エンジンの表示名は元の "Steam Engine" |
| エンジン GUI | `GuiSteamEngine`, `GuiCombustionEngine`, `ContainerEngine*` | `energy.EngineMenu`, `client.screen.*` | 実装済み | |
| 鉄エンジン燃料表 | `BuildCraftAPI.ironEngineFuel` | データマップ `re_buildcraft:engine_fuel` | 実装済み | 溶岩 1/20000, オイル 2/10000, 燃料 5/50000 |
| 黒オイル / 金オイル | `BlockOilStill/Flowing`, `ItemBucketOil`, `fuel` | `ModFluids`, `ModItems` | 実装済み | 液体テクスチャは旧 `TextureOilFX` の色範囲で生成した静的ノイズ (元は手続き生成アニメーション) |
| ボーリングマシン / 採掘パイプ | `TileMiningWell`, `BlockMiningWell`, `BlockPlainPipe` | `factory.MiningWell*`, `MiningPipeBlock` | 実装済み | |
| タンク | `TileTank`, `BlockTank` | `factory.Tank*` | 実装済み | 縦連結。空容器での汲み出しも可能 (変更点) |
| ポンプ | `TilePump`, `BlockPump` | `factory.Pump*`, `client.PumpTubeRenderer` | 実装済み | チューブはエンティティ→BER。非稼働時のテクスチャ切替は未移行 |
| レーザー | `TileLaser`, `BlockLaser`, `EntityEnergyLaser`, `RenderLaser` | `api.laser.*`, `api.client.LaserBeamRenderer` | 実装済み | ビームはエンティティ→BER |
| 組立台 | `TileAssemblyTable`, `GuiAssemblyTable`, `AssemblyRecipe` | `silicon.AssemblyTable*`, `api.recipe.*` | 実装済み | レシピ予約はメニューボタンでサーバーへ送信 |
| チップセット 4 種 | `ItemRedstoneChipset` + 組立台レシピ | `ModItems` + `recipe/assembly/*.json` | 実装済み | BCx 既定 `isCostdownAssembly=true` のコスト |
| 設定 | `buildcraft.cfg` (`BuildCraftBase.BCxAddingConfigurations`) | `Config` | 一部 | 移行機能が使う `enginePowerUp`, `consumeWaterSources`, `pipesAlwaysConnect`, `pipeGroupItemsTrigger`, `pipeMaxItems`, `modifyWorld`, `markerMaxRange` (MarkerMaxRange), `markerSignalRange` (BlueMarkerRange), `markerVerticalLine` (BlueMarkerVerticalLine), `fillingLava`, `fillerEndPortalErase`, `fillerTorchInterval` (intervalTorch) のみ |
| クアーリー | `factory.TileQuarry`, `BlockQuarry` | `api.quarry.QuarryBlockEntity`, `QuarryBlock`, `api.client.QuarryRenderer` | 実装済み | 作業範囲 → 枠組み建設 → アーム採掘 → 排出の流れ、動力設定 (建設 25/25/25/7000, 採掘 30/200/50/7000)、アーム速度式、蛇行探索、上方の岩盤・溶岩確認、レンチで再初期化、撤去で上部の枠組み除去、保存を移行。CE が継承する差し替え点を protected で公開 |
| 枠組みの設計図・建設ロボット | `BptBlueprint`, `BptBuilderBlueprint`, `EntityRobot` | `api.quarry.FrameBlueprint`, `ConstructionRobot` | 実装済み | 上層から「設計図と違う柔らかくないブロック」をドロップ無しで撤去 → 下層から枠組み/空気を設置。1 マス 25 MJ、予約上限 20、待ち時間式 `(20 - 予約数 + 2) / 3`。ロボットは BE 内の状態 + BER 描画 (エンティティではない) |
| メカニカルアーム | `EntityMechanicalArm` | `api.quarry.MechanicalArm` | 実装済み | 移動・到達判定・保存を移行。棒 4 本の描画は BER |
| 枠組み (フレーム) | `BlockFrame` | `factory.FrameBlock` | 実装済み | 隣の枠組みと接続する形状。ドロップ無し。アイテムは登録しない (旧実装もクリエイティブ一覧に無い) |
| 作業範囲 (マーカー連携) | `Utils.getNearbyAreaProvider`, `IAreaProvider`, `TileQuarry.setBoundaries` | `api.area.*`, `QuarryBlockEntity.initialArea` | 実装済み | 最初の tick に隣接 (x+1, x-1, z+1, z-1, y+1, y-1 の順) の範囲提供者を探し、X・Z が 3 以上ならその範囲 (高さ最低 5) を使ってマーカーを撤去、そうでなければ既定範囲。CE の改良型クアーリーは `defaultArea` だけを上書きしているため同じ処理を継承する。範囲を決めた後に置いたマーカーは使わない (旧実装と同じ) |
| マーカー (青マーカー) | `builders.TileMarker` (BCx 版), `BlockMarker` | `builders.Marker{Block, BlockEntity}`, `client.MarkerRenderer` | 実装済み | X・Y・Z 方向に `markerMaxRange` 未満の距離のマーカーを近い順 (+, - 交互) に探してつなぐ、3 通りの接続規則、範囲上限の判定、相手を越えた位置へのつなぎ替えと中間マーカーの撤去、原点の見た目切替、赤いレーザーの枠、レッドストーン信号中の目安線 (青 15 / 緑 63 / 黄 127 / 赤 255 と縦線)、マーカーを持ってスニーク右クリックでの自動設置、壊したときの組全体の解除、`removeFromWorld` (組のマーカーを撤去してアイテムを落とす)、保存・同期。置いた面に向けて立つ形状・当たり判定無し・明るさ 7。レシピ (ラピスラズリ + レッドストーントーチ) |
| 緑マーカー (パスマーカー) | `TilePathMarker`, `BlockPathMarker` | — | 未実装 | ビルダーの経路指定用で、ビルダー本体が未移行のため使い道が無い (YAGNI) |
| パイプ共通基盤 | `BlockGenericPipe`, `TileGenericPipe`, `Pipe`, `PipeLogic*`, `Utils.checkPipesConnections` | `transport.PipeBlock`, `DirectionalPipeBlock`, `PipeBlockEntity`, `PipeConnectionRules`, `PipeMaterial`, `api.transport.*` | 実装済み | 接続はブロックステート (6 方向 + 木/鉄の特別な面) で保持し、マルチパートモデルと形状に反映。素材による接続制限 (木-木、石-丸石、黒曜石-黒曜石、金の輸送パイプ同士) と設定 `pipesAlwaysConnect` |
| アイテム輸送 | `PipeTransportItems`, `EntityPassiveItem` | `transport.ItemTransport`, `TravelingItem`, `client.PipeRenderer` | 実装済み | 入口 → 中心 → 行き先決定 → 出口、跳ね返り、1 回待ってから落とす、まとめ (32 個超)・爆発 (100 個超)、保存、クライアント同期 (中心での行き先はサーバーが決めて送る) |
| 木/丸石/石/鉄/金/ダイヤ/黒曜石/虚空の輸送パイプ | `pipes.PipeItems*`, `PipeLogicWood/Iron/Diamond`, `addClass.PipeItemsVoid` | `transport.behavior.*` | 実装済み | 木: MJ で取り出し (1 MJ = 1 個、初速 0.04)、鉄: 出力面のみ (レンチ・信号変化で切替)、金: 信号なしで速度 0.2、ダイヤ: 面ごと 9 個のフィルター (GUI)、黒曜石: 開口部前方 4 ブロックの吸引 (アイテム・矢・チェスト付きトロッコ)、虚空: 中心で消去 |
| 液体輸送 | `PipeTransportLiquids` | `transport.FluidTransport` | 実装済み | 面ごと + 中心のバッファ、入力/出力判定、中心からの均等分配 (2 段階)、跳ね返り・空き時間による入力判定の切替、詰まり時のリセット。travelDelay は全パイプ 1 のためリングバッファは持たない。木の防水パイプは 1 MJ ごとに 1 バケツ分の取り出し枠 |
| 動力輸送 | `PipeTransportPower`, `PipePowerWood` | `transport.PowerTransport`, `behavior.WoodPowerBehavior` | 実装済み | 1 tick 遅れの要求・供給の伝播、要求量比例の分配、1 面 1000 MJ 以上で爆発、木の導電パイプは蓄積量に応じて隣の導電パイプへ送出。旧 powerResitance は処理で使われていなかったため持たない |
| 構造パイプ (砂利パイプ) | `PipeStructureCobblestone`, `PipeTransportStructure` | `transport.StructureTransport` | 実装済み | 構造パイプ同士のみ接続し、ワイヤーの信号を中継 |
| ストライプパイプ | `PipeItemsStripes`, `PipeLogicStripes` | `transport.behavior.StripesBehavior`, `PipeBlockEntity.convertTo` | 実装済み | 動力 1/1/1/1 (損失 1/1)。1 MJ ごとに開口部の前のブロックを壊しドロップをパイプへ取り込む (破壊不能ブロックは除外)。行き先の無いアイテムは開口部の前で偽プレイヤーが「使う」(前が空気ならその下の上面、そうでなければ前のブロックの上面)。他のアイテムパイプが届くと 1 本消費して自分をそのパイプに作り替え、運搬中のアイテム・ワイヤー・ゲートを引き継ぐ。接続規則は黒曜石と同じ (黒曜石・ストライプ同士は不可) |
| 液体虚空パイプ | `PipeLiquidsVoid` | — | 未対応 | クラスはあるが BCx の `BuildCraftTransport` で登録されておらず、テクスチャ (158) も空のため対象外 |
| ワイヤー・信号伝達 | `Pipe.wireSet/signalStrength/updateSignalState` | `transport.gate.PipeSignals` | 実装済み | 4 色、強さ 255 から 1 ずつ減衰、構造パイプ経由の中継、撤去時のドロップ |
| ゲート | `Pipe.GateKind`, `ItemGate`, `Pipe.resolveActions` | `transport.gate.Gate`, `GateKind` | 実装済み | 7 種 (赤石/鉄・金・ダイヤの AND/OR)、10 tick ごとの評価、AND/OR 合成、レッドストーン出力 (接続パイプ以外の方向)、ワイヤー信号出力、スニークで取り外し |
| トリガー/アクション | `Trigger`, `Action`, `TriggerParameter`, `DefaultTriggerProvider`, `DefaultActionProvider`, `PipeTriggerProvider`, 各 Trigger* | `transport.gate.GateStatement`, `GateStatements`, `NeighborTriggers`, `PipeTriggers` | 実装済み | インベントリ 4・液体 4・機械 2・エンジン熱 4・レッドストーン 2・パイプ内容 4・信号 8 のトリガーと、レッドストーン/信号出力アクション |
| 機械の運転指示 (On / Off / Loop) | `ActionMachineControl`, `IActionReceptor`, `IMachine.allowActions`, `Pipe.resolveActions` | `api.MachineControl`, `api.IActionReceptor`, `IMachine.allowActions`, `GateStatement.MachineControlAction` | 実装済み | 隣接機械が `allowActions()` を返すときだけ候補に出し、成立時に隣接する全 `IActionReceptor` へ送る。受け手はフィラー (元の BCx と同じく、クアーリー・ポンプ・精製機は受け付けない) |
| ゲート設定画面 | `GuiGateInterface`, `CraftingGateInterface` | `transport.gate.GateMenu`, `client.screen.GateScreen` | 実装済み | 枠の配置・左/右クリックでの候補送り・パラメーター枠・成立表示を移行。変更はメニューボタンでサーバーへ送り、サーバーで候補を検証 |
| 精製機 (Refinery) | `TileRefinery`, `BlockRefinery`, `GuiRefinery`, `ContainerRefinery`, `RenderRefinery`, `RefineryRecipe` | `factory.Refinery{Block, BlockEntity, Menu}`, `api.recipe.RefineryRecipe(Input)`, `client.RefineryRenderer`, `client.screen.RefineryScreen` | 実装済み | 材料槽 2 + 結果槽 (各 4000 mB)、動力 25/25/25/1000、レシピ照合・精製条件・消費順・アニメーション速度を旧 updateEntity と同順で移行。液体はどの面からも入り (フィルター設定時は一致する槽のみ)、取り出しは結果槽のみ。液体容器で右クリックすると投入、レンチで水平回転、GUI でフィルター設定 (持っている容器の液体、空の手で解除)。レシピはデータパック (`recipe/refinery/fuel.json`: 黒オイル 1 mB + 10 MJ → 金オイル 1 mB、間隔 1 tick)。タンク 3 本は向き別の JSON モデル、磁石と液面は BER |
| 自動作業台 | `TileAutoWorkbench` (BCx 版), `ContainerAutoWorkbench`, `BlockAutoWorkbench` | `factory.AutoWorkbench{Block, BlockEntity, Menu}`, `client.screen.AutoWorkbenchScreen` | 実装済み | 3x3 が見本兼材料置き場。搬入は同じアイテムで最も少ないスロットへ。取り出し (木のパイプ等) で 1 回分クラフトし、1 個しか無い材料は隣接インベントリ (上下北南西東の順) から補充。GUI の完成品枠から取ると最後の 1 個も使う。残りアイテム (空バケツ等) は材料の出どころへ戻し、入らなければ隣接パイプ/上方へ。パイプを持った右クリックでは GUI を開かない |
| フィラー | `TileFiller`, `BlockFiller`, `CraftingFiller`, `GuiFiller`, `SlotFillerMode`, `FillerRecipe`, `FillerPatternCore`, `ItemFillerModule` | `builders.Filler{Block, BlockEntity, Menu}`, `builders.filler.{FillerPattern, FillerPatterns, FillerRecipes, FillerCursor}`, `client.FillerRenderer`, `client.screen.FillerScreen` | 実装済み | 隣のマーカーから範囲を受け取りマーカーを撤去、作業台枠 (レンガ/ガラス/水バケツの図案、左右反転可) かモジュールでパターンを決定、材料枠の最初のアイテムで作業、パターンごとの動力 (setPower)、MJ がある限り繰り返す作業、完了判定、On/Off/Loop、上下面の稼働表示と側面のパターン絵柄、範囲のしま模様レーザー、モジュールの作成 (作業台枠を 1 個ずつ消費・容器を返却)、材料枠のパイプ入出力、保存。パターン 23 種 (埋め立て・撤去・整地・箱・ピラミッド・階段・完全消去・壁・1.4.7 型撤去・穴埋め・池・床張替え・タワー・天井張替え・壁張替え・外壁・円壁・円柱消去・水設置・地下埋め・クワ・階段設置・松明設置) をすべて移行。モジュールは 23 個の別アイテム |
| ビルダー・設計台・設計図ライブラリ・設計図/テンプレート | `TileBuilder`, `TileArchitect`, `TileBlueprintLibrary`, `BptBlueprint`, `BptTemplate`, `BptBuilder*`, `BptBlock*` (約 40 種), `ItemBptTemplate`, `ItemBptBluePrint`, `BptRootIndex`, `BptPlayerIndex`, `mod_BluePrint` | — | 未実装 | 理由と不足条件は「ビルダー系の未実装範囲」を参照 |
| 石油の地形生成 | `OilPopulate`, `BuildCraftEnergy.generateSurface` | `energy.worldgen.OilDepositFeature`, `registry.ModFeatures`, `worldgen/*/oil_deposit.json`, `neoforge/biome_modifier/oil_deposit.json`, タグ `has_desert_oil` | 実装済み | チャンクごとに 1 回: 砂漠 3% で砂の上に半径 3 の油だまり、0.15% で中規模 (半径 4-7)・0.005% で大規模 (半径 8-16) の地下の油の球 (中心 y=20-29) と地表までの柱・噴出 (4 / 30 ブロック)・地表の油だまり。地上 (`#minecraft:is_overworld`) のみ、設定 `modifyWorld` で停止可能。差異は「元の仕様から変更した点」を参照 |
| 検証用エンジン (Cheat Engine) | `addClass.CheatEngine*` | — | 未対応 | |
| CraftGuide 連携 | `addon_craftguide` | — | 未対応 | 1.21.1 に CraftGuide 無し |
| 1.2.5 のブロック/アイテム ID 設定 | 各 `getBlockId` | — | 不要 | レジストリ名に置換 |

## ビルダー系の移行 (マーカー・フィラー)
### 主なファイル
- 範囲 API: `api/area/{IAreaProvider, AreaProviders}.java`。`QuarryBlockEntity.initialArea` (既定範囲の決定を分離し、マーカーの範囲を優先)
- マーカー: `builders/{MarkerBlock, MarkerBlockEntity}.java`、`client/MarkerRenderer.java`
- フィラー: `builders/{FillerBlock, FillerBlockEntity, FillerMenu}.java`、`builders/filler/{FillerCursor, FillerPattern, FillerPatterns, FillerRecipes}.java`、
  `client/FillerRenderer.java`、`client/screen/FillerScreen.java`
- 共通化: `api/client/BoxRenderer.renderLaserBox` (クアーリー・マーカー・フィラーの範囲の枠を同じ処理で描く。`QuarryRenderer` の重複を削除)
- 登録: `ModBlocks` (marker, filler)、`ModItems` (marker, filler, filler_module_* 23 種、`fillerModule` / `isFillerModule`)、`ModBlockEntities` (MARKER, FILLER)、
  `ModMenus` (FILLER)、`ModCapabilities` (フィラーの MJ 受け手と材料枠の ItemHandler)、`ReBuildcraftClient` (BER 2・画面 1)、`Config` (6 項目)
- リソース: マーカー (旧タイル 57 / 45、レーザー 1 / 2 / 10 / 12、端に立つ棒のモデルを end_rod と同じ回転で 6 方向)、
  フィラー (旧 filler_textures の 0 / 1 / 2 / 16-38、ブロックモデル 48 = パターン 24 x 稼働 2、GUI `filler.png`)、レシピ (マーカー・フィラー)、言語、ルートテーブル

### 処理経路
- マーカー: `MarkerBlock.useWithoutItem` → `tryConnection` → `searchAxis` → `checkMarker` → `linkTo` → `updateBox` / `refreshGroup` (原点の見た目・同期) → `MarkerRenderer`;
  `neighborChanged` → `updateSignals`; `onRemove` → `destroy`
- クアーリー: `createUtilsIfNeeded` → `initialArea` → `AreaProviders.findNearby` → `IAreaProvider.getArea` / `removeFromWorld`
- フィラー: `serverTick` (初回 `initialize` → 範囲取得・`computeRecipe` → `FillerRecipes.match` → `FillerPattern.initialize`) → `PowerProvider.update` →
  `doWork` (Off なら停止、作業量分の MJ を使えた限り `FillerPattern.iterate`) → 完了時 Loop なら再開; ゲート → `Gate.sendControl` → `FillerBlockEntity.actionActivated`
- モジュール作成: `FillerMenu.ModuleSlot.onTake` → `FillerBlockEntity.consumeGridForModule` (サーバーが作業台枠から決めたモジュールのみ。クライアントの申告は使わない)

### 元の仕様から変更した点 (ビルダー系)
- マーカーの組は旧実装のように 1 つの Origin オブジェクトを共有せず、各マーカーが原点の位置、原点が各軸の相手の位置を保存する (チャンクの読み込み状態に依存しない)。
  原点の相手を差し替えたとき、前の相手は組から外す (旧実装では組の一員のまま取り残され得た)。
- マーカーの自動設置は、旧実装ではクリエイティブで置き換え不能なブロックも上書きし、サバイバルでは `onItemUse` の仕様で 1 つ上に置かれ得た。
  ここでは置き換え可能な場所にだけ、計算した座標そのものに置く。旧実装がクライアントのプレイヤーのクリエイティブ判定で決めていた「撤去したマーカーを落とすか」は、サーバーでは判定できないため常に落とす。
- フィラーの `softBlock` 等の判定はブロックステートに置き換えた (`FillerPattern` の説明を参照)。ブロックの設置は偽プレイヤーで行い、ブロックアイテムは計算した座標そのものに置く
  (旧 `onItemUse(x, y, z, 1)` の結果座標は 1.2.5 の `ItemBlock` の実装に依存し、ソースが無く確認できないため)。
  設置できないときに取り除く生き物からプレイヤーを除外した。クリエイティブで材料を減らさない処理 (クライアントのプレイヤーを参照していた) は移行していない。
- 池・水設置で使う「フィラーの上のタンク」は、旧実装の `TileTank` 限定から液体ハンドラを持つブロック全般にした。液体ブロック自体をアイテムとして置く処理 (1.2.5 の液体ブロック ID のアイテム) は 1.21.1 に該当アイテムが無いため無い。
- 「1.4.7 型撤去」のドロップがすぐ消える仕様 (旧 age = 5980) は `ItemEntity.lifespan` を短くして表した。

### ビルダー系の未実装範囲
- **ビルダー・設計台・設計図ライブラリ・設計図・テンプレート (未実装)**: 次の理由で今回は移行していない。
  1. 規模: BCx ソース内の関連コードだけで約 4,200 行 (`TileBuilder` 773、`TileArchitect` 428、`TilePathMarker` 282、`BptBuilderBlueprint` 他)。
     さらに保存形式の中心である `BptBase` / `BptTemplate` / `BptBuilderTemplate` / `BptPlayerIndex` / `ItemBpt*` は BCx のソースツリーに無く、コンパイル済みクラスしか無いため、逆コンパイルで形式を確認する必要がある。
  2. 旧設計図との互換: 設計図は 1.2.5 の数値ブロック ID + メタデータで保存されている。バニラのブロック (ID 0-255) については 1.21.1 に
     `net.minecraft.util.datafix.fixes.BlockStateData.getTag(id << 4 | meta)` (1.13 の平坦化用の公式対応表) があり、変換の手掛かりになることを確認した。
     ただし 1.2.5 と 1.12 でメタデータの意味が違うブロック (階段の上下・原木の向きなど) の確認と、BC/他 Mod のブロック ID (設定で変わる) の扱いが必要。
     旧ブロックごとの設置処理 (`BptBlock` のサブクラス約 40 種: 回転・必要アイテム・インベントリ付きブロック) にも 1.21.1 での対応が必要。
  3. 図書館は設計図をワールドフォルダのプレイヤー別ファイルに保存する仕組みで、1.21.1 での保存先・同期方式の設計が必要。
  - 実装に必要な条件: `BptBase` 系の逆コンパイルによる保存形式の確定、バニラ ID 変換表 (`BlockStateData`) の適用範囲の検証、`BptBlock` 各種の 1.21.1 対応表、図書館の保存方式の決定。
- **緑マーカー (未実装)**: ビルダーの経路指定用。ビルダー本体が無いため移行しない。

## 以前の追加作業 (残存機能: ストライプパイプ・石油生成・精製機・自動作業台・運転指示)
### 主なファイル
- ストライプパイプ: `transport/behavior/StripesBehavior.java` (新規)、`PipeType` (STRIPES_TRANSPORT)、`PipeMaterial` (STRIPES)、
  `PipeBehavior.beforeDrop` (落とす直前のフック)、`ItemTransport.dropItem`、`PipeBlockEntity.getOpenSide` / `convertTo`、
  `ObsidianSuctionBehavior` (開口部の判定を `getOpenSide` に共通化)
- 動力の流量 API: `api/power/IPowerConduit.java`、`BuildCraftCapabilities.POWER_CONDUIT`、`PowerTransport implements IPowerConduit` (CE のエネルギーチェッカー・圧縮木エンジンが使用)
- 石油: `energy/worldgen/OilDepositFeature.java`、`registry/ModFeatures.java`、`Config.MODIFY_WORLD`
- 精製機: `factory/{RefineryBlock, RefineryBlockEntity, RefineryMenu}.java`、`api/recipe/{RefineryRecipe, RefineryRecipeInput}.java`、
  `client/RefineryRenderer.java`、`client/screen/RefineryScreen.java`、`ModRecipes` (REFINERY_TYPE / SERIALIZER)
- 自動作業台: `factory/{AutoWorkbenchBlock, AutoWorkbenchBlockEntity, AutoWorkbenchMenu}.java`、`client/screen/AutoWorkbenchScreen.java`
- 運転指示: `api/{MachineControl, IActionReceptor}.java`、`IMachine.allowActions`、`GateStatement.MachineControlAction`、`GateStatements` (MACHINE_ON/OFF/LOOP)、`Gate.sendControl`
- 移動: `core/menu/PlayerInventorySlots.java` → `api/menu/PlayerInventorySlots.java` (CE のガラスチェストが使うため。参照 6 ファイルの import を更新)
- 登録: `ModBlocks` (stripes_transport_pipe は PipeType から自動、refinery, auto_workbench)、`ModItems`、`ModBlockEntities` (REFINERY, AUTO_WORKBENCH)、
  `ModMenus` (REFINERY, AUTO_WORKBENCH)、`ModCapabilities` (精製機の液体ハンドラと MJ、自動作業台の ItemHandler)、`ReBuildcraftClient` (BER・画面)
- リソース: ストライプパイプ (テクスチャ: アトラス 126、モデル・blockstate・レシピ「黒の染料・ガラス・黄の染料」→ 8 本)、
  精製機 (`textures/block/refinery.png` = 旧 refinery.png、`textures/gui/refinery.png` = 旧 refinery_filter.png、タンク 3 本の JSON モデル、レシピ「RTR/TGT」)、
  自動作業台 (アトラス 43/44、レシピ「 g /gwg/ g 」)、石油 (configured/placed feature、biome modifier、バイオームタグ)、言語 (en/ja)、ルートテーブル

### 処理経路 (ソース上で接続を確認したもの)
- ストライプパイプ: `PipeBlockEntity.tick` → `PowerProvider.update` → `StripesBehavior.doWork` → `Block.getDrops` → `ItemTransport.injectFromSide`;
  `ItemTransport.dropItem` → `PipeBehavior.beforeDrop` → `ItemStack.useOn` (偽プレイヤー); `reachCenter` → `consumeAtCenter` → 次の `tick` で `PipeBlockEntity.convertTo`
- 石油: biome modifier (`neoforge:add_features`, step `lakes`) → placed feature (配置修飾子なし = チャンクごとに 1 回) → `OilDepositFeature.place`
- 精製機: `RefineryBlockEntity.serverTick` → `PowerProvider.update` → `tryRefine` (`RecipeManager.getRecipeFor`) → `refine` → 結果槽; 液体パイプ → `RefineryFluidHandler.fill/drain`
- 自動作業台: 木のパイプ → `AutomationHandler.extractItem(9)` → `craft(true, false)` → `takeNearby` / `returnRemainders`; GUI → `ResultSlot.onTake` → `craft(true, true)`
- 運転指示: `Gate.resolveActions` → `MachineControlAction` → `sendControl` → 隣接 `IActionReceptor.actionActivated`

### 今回の静的チェック
- import 解決 (NeoForge 21.1.265 ソース・自プロジェクト)・自クラス参照: 指摘 2 件のみで、いずれも既知の誤検出 (同パッケージのネスト enum の import)
- blockstate → model → texture、登録 ID と言語・ルートテーブル・アイテムモデル、レシピ/ルートテーブルのアイテム ID: 不整合なし
  (チェッカーを拡張: 精製機レシピの結果は液体 ID として照合、CE のガラスチェスト名を switch から収集)
- 新たに使った API の存在と引数をソースで確認: `FakePlayerFactory.getMinecraft`, `UseOnContext(Level, Player, InteractionHand, ItemStack, BlockHitResult)`,
  `Block.getDrops`, `FluidUtil.interactWithFluidHandler` / `getFluidContained`, `SizedFluidIngredient.FLAT_CODEC` / `STREAM_CODEC`, `FluidStack.CODEC`,
  `RecipeManager.getRecipeFor(type, input, level, lastRecipe)`, `RecipeInput.isEmpty` (液体のみの入力のため上書き), `CraftingInput.ofPositioned`,
  `WorldGenRegion.ensureCanWrite` (書き込み半径 1 チャンク), `LevelAccessor.scheduleTick(BlockPos, Fluid, int)`, `ResultContainer`, `IClientFluidTypeExtensions.getStillTexture(FluidStack)`

## 以前の追加作業 (クアーリー・パイプ・ゲート)
### 主なファイル
- クアーリー: `api/quarry/{QuarryArea, MechanicalArm, FrameBlueprint, ConstructionRobot, QuarryBlockEntity, QuarryBlock}.java`,
  `factory/FrameBlock.java`, `core/ConnectedShapes.java`, `api/client/QuarryRenderer.java`
- パイプ: `transport/{PipeType, PipeKind, PipeMaterial, PipeSide, PipeBlock, DirectionalPipeBlock, PipeBlockEntity, PipeConnectionRules,
  PipeTransport, ItemTransport, FluidTransport, PowerTransport, StructureTransport, TravelingItem, DiamondFilterMenu}.java`,
  `transport/behavior/*.java`, `api/transport/{IPipeConnectable, IPipeEntry, PipeConnections}.java`, `api/IMachine.java`,
  `client/PipeRenderer.java`, `client/screen/DiamondFilterScreen.java`
- ゲート: `transport/gate/{GateKind, WireColor, PipeSignals, Gate, GateStatement, GateStatements, NeighborTriggers, PipeTriggers, GateMenu}.java`,
  `client/screen/GateScreen.java`
- 既存クラスの変更: `api/util/InventoryUtils.java` (隣接パイプへの排出 `ejectFromMachine` / `insertIntoAdjacentPipe` を追加)、
  `MiningWellBlockEntity` / `PumpBlockEntity` / `AssemblyTableBlockEntity` / `EngineBlockEntity` に `IMachine` / `IPipeConnectable` を実装、
  `api/client/BoxRenderer.java` (UV・色・ビーム描画を追加し `LaserBeamRenderer` と共用)、`Config.java` (パイプ設定 3 項目)
- 登録: `ModBlocks` (quarry, frame, パイプ 18 種), `ModItems` (防水素材・ワイヤー 4 色・ゲート 7 種・各ブロックアイテム), `ModBlockEntities` (QUARRY, PIPE),
  `ModMenus` (GATE, DIAMOND_PIPE), `ModCapabilities` (パイプの液体ハンドラと MJ 受け手、クアーリーの MJ 受け手)
- リソース: マルチパート blockstate (パイプ・枠組み)、テクスチャ 69 枚 (元アトラスから切り出し)、レシピ (パイプ 18、防水素材、クアーリー、
  組立台: ワイヤー 4・ゲート 7)、ルートテーブル、言語 (トリガー/アクション説明の日本語は新規訳)

### 処理経路 (ソース上で接続を確認したもの)
- クアーリー: `QuarryBlockEntity.tick` → `PowerProvider.update` → `doWork` → `buildFrame` (`FrameBlueprint.nextSlot` → `ConstructionRobot.schedule` → `update` → `FrameBlueprint.apply`)
  → `findTarget` → `MechanicalArm.setTarget` → `moveArm` → `doMove` → `positionReached` → `onTargetReached` → `digBlock` → `popItem`
  → `InventoryUtils.ejectFromMachine` (隣接 `IItemHandler` → `IPipeEntry` (パイプ) → 上方へ放出)
- アイテムパイプ: 木 `WoodItemExtractionBehavior.doWork` / 機械 `IPipeEntry.injectItem` → `ItemTransport.entering` → `tick` → `reachCenter`
  (`PipeBehavior.filterMovements` / `outputOpen`) → `handleTileReached` → 隣のパイプ `entering` / `IItemHandler` へ挿入 / 落下
- 液体パイプ: 木 `WoodFluidExtractionBehavior.tick` / 外部 `SideHandler.fill` → `FluidTransport.tick` → 中心 → 出力面 → 隣の `IFluidHandler`
- 動力パイプ: エンジン → 木の導電パイプ (`POWER_RECEPTOR`) → `WoodPowerBehavior.tick` → `PowerTransport.receiveEnergy` → `distributePower` → 機械の `PowerProvider.receiveEnergy`
- ゲート: `PipeBlockEntity.tick` → `Gate.tick` (10 tick ごと) → `GateStatements.isActive` → AND/OR 合成 → `PipeSignals.setBroadcast` / レッドストーン出力 (`PipeBlock.getSignal`)
- ゲート画面: `GateScreen.mouseClicked` → `handleInventoryButtonClick` → `GateMenu.clickMenuButton` (サーバーで候補を検証) → `PipeBlockEntity.scheduleSync` → 画面は同期データを表示

### 今回の静的チェック
- import 解決・自クラス参照・未使用 import・FQCN の残り・TODO/空 catch・共通コードからのクライアント参照: 指摘なし
  (同じパッケージのネスト enum の import 2 件をスクリプトが誤検出。Java として正しい記述)
- blockstate (variants / multipart) → model → texture、実行時に組み立てるテクスチャ名 (ワイヤー・ゲート・レーザー・GUI): 欠落なし
- 登録 ID と言語・ルートテーブル・アイテムモデル、レシピの材料と出力の登録: 不整合なし
- 新たに使った API (例: `Block.updateFromNeighbourShapes`, `ItemStack.consume`, `AABB.minmax`, `ItemRenderer.renderStatic`,
  `ContainerHelper.saveAllItems`, `Block.getDrops`, `Level.explode`) は NeoForge 21.1.265 のソースで存在と引数を確認

## 元の仕様から変更した点
- メタデータで種類を分けていたブロック (エンジン) を種類ごとの別ブロックにした。
- エンジンの向きを BlockState (`facing`) で保持。
- レーザービーム・ポンプのチューブをエンティティから BlockEntityRenderer に変更。
- 燃料表・組立台レシピを static 登録からデータマップ/データパックレシピに変更。
- 他の機械への「出力先インベントリ」はパイプ未移行のため隣接インベントリ (ItemHandler) と上方への放出のみ。
- ポンプが汲む対象を「液体ブロック (`LiquidBlock`) の水源」に限定 (水没ブロックを壊さないため)。
- ポンプの汲み上げ順で、旧実装は確認する要素 (`getLast`) と取り出す要素 (`pop`) が異なっていた。同じ要素を確認・取り出すよう統一した。
- ボーリングマシンは旧実装が 1 個目のドロップを搬出した時点で処理を終えていた箇所を、全ドロップを搬出するようにした。

- (追加分) クアーリーのアーム・建設ロボット・作業範囲のレーザーもエンティティから BER に変更。
- (追加分) パイプは種類ごとの別ブロックで、接続状態をブロックステートに持つ。パイプは ItemHandler を公開しない (旧実装どおり、機械からの搬入は `IPipeEntry` のみ)。
- (追加分) クアーリーの「掘れない障害物」は岩盤に加えて破壊不能ブロック全般 (硬さ < 0)。柔らかいブロックは「空気または置き換え可能なブロック」で判定。
- (追加分) クアーリーの最下層は旧 y=0 をワールドの最下層 (`getMinBuildHeight`) に対応させた。
- (追加分) パイプからアイテムを落とすときの初速は「進行方向 x 移動速度」(旧 `EntityPassiveItem.toEntityItem` の詳細はソースが無く未確認)。
- (追加分) 動力パイプは流量に応じた太さの棒で描く。ワイヤーの描画位置は独自に決めた (旧描画の詳細は未確認)。
- (残存機能) ストライプパイプの作り替えは同じ輸送方式 (アイテム) のパイプに限定した (旧実装は任意の `ItemPipe` を受け付けたが、輸送方式が変わると運搬中の中身を引き継げないため)。破壊不能ブロック (硬さ < 0) は壊さない。
- (残存機能) 石油: 地物は生成中チャンクの周囲 1 チャンクにしか書き込めないため、大規模油田の地表の油だまり (半径最大 39) のうち範囲外の部分は置かない。
  地表の探索は旧ワールドの天井 y=128 ではなく生成時の高さマップから始める。砂漠の判定はバイオームタグ `re_buildcraft:has_desert_oil` (既定 `minecraft:desert`)。
  油の深さ (中心 y=20-29) と砂漠判定の最低高度 y=65 は旧ワールド (0-127) の絶対座標をそのまま使った。置いた油には液体の tick を予約する (旧 setBlockWithNotify の近傍更新に相当)。
- (残存機能) 精製機のフィルターは旧実装ではクライアントが直接書き換えていた。メニューボタンでサーバーへ送り、サーバーが手に持つ容器の液体を設定する。磁石の位置はサーバーから同期した速度でクライアントが進める。
  レンチの回転方向は旧実装の switch がデコンパイル後の対応表に依存して特定できないため、時計回りとした。
- (残存機能) 自動作業台: 旧 BCAPI の「耐久値を消費するクラフト素材」(`isDamageCraftItem`) と隣のガラスチェスト (`InfinityChestTile`) の特別扱いは、
  1.21.1 のレシピの残りアイテム (`getRemainingItems`) と `IItemHandler` に置き換えた。外部から見える完成品スロットは 9 番で、完成品の個数が取り出し要求数を超える場合は作らない (IItemHandler の契約のため)。

## Parchment と複合ビルド
- **Parchment**: 以前の `1.21.11 / 2025.12.20` は 1.21.1 より大幅に新しいゲームバージョン向けのデータだったため、
  このマシンの Gradle キャッシュに実在し、同じ 1.21.1 / NeoForge 環境の別プロジェクトでも使われている `1.21 / 2024.11.10` に変更した。
  1.21.1 専用版 (例: 他プロジェクトにある `1.21.1 / 2024.11.17`) はこのマシンのキャッシュに無く、実在を確認できなかったため採用していない。
- **複合ビルド**: CE 側の `includeBuild('../Re-BuildCraft')` + `implementation "com.takoy3466:re_buildcraft"` でコンパイル時の依存は解決される想定。
  `runClient` で Re-BuildCraft が Mod として読み込まれるか (FML がクラスパス上の jar の `neoforge.mods.toml` を検出するか) は、ビルドを実行していないため未確認。
  配布時は Re-BuildCraft を別 jar として導入する前提 (CE の `neoforge.mods.toml` で required 依存を宣言済み、CE の jar には同梱しない)。

## 将来の Minecraft 更新で変更が必要になりやすい箇所
- `DirectionProperty` (1.21.2 で `EnumProperty<Direction>` に変更) … `EngineBlock`, `LaserBlock`, `MiningWellBlock`。
- `ItemInteractionResult` / `useItemOn` (1.21.2 で `InteractionResult` に統合) … `EngineBlock`, `TankBlock`。
- `RecipeInput` / `Recipe` / `RecipeManager` の API (1.21.2 で大きく変更) … `api.recipe.*`, `AssemblyTableBlockEntity`。
- `GuiGraphics.blit` の引数 (1.21.2 で RenderType 引数が追加) … `client.screen.*`。
- `BlockEntityType.Builder` (1.21.2 で廃止) … `ModBlockEntities`。
- `getMinBuildHeight` (1.21.2 で `getMinY`) … `MiningWellBlock`, `MiningWellBlockEntity`, `PumpBlockEntity`。
- `EventBusSubscriber.Bus` (NeoForge 21.6 以降で非推奨) … `client.ReBuildcraftClient`。
- `DirectionProperty` / `EnumProperty`、`updateShape` の引数 (1.21.2 で変更) … `QuarryBlock`, `DirectionalPipeBlock`, `PipeBlock`, `FrameBlock`, `RefineryBlock`。
- `RecipeInput` / `CraftingInput` / `RecipeManager.getRecipeFor` (1.21.2 で変更) … `RefineryBlockEntity`, `AutoWorkbenchBlockEntity`, `RefineryScreen`。
- ワールド生成 (`Feature`, `WorldGenLevel.getMaxBuildHeight` → 1.21.2 で `getMaxY`) と biome modifier の JSON パス … `OilDepositFeature`, `data/re_buildcraft/neoforge/biome_modifier`。
- `FakePlayerFactory` / `UseOnContext` … `StripesBehavior`, `FillerPattern`。
- `BlockPlaceContext` / `BlockItem.place`、`IntegerProperty`、`ItemEntity.lifespan` (NeoForge) … `FillerPattern`, `FillerBlock`。
- `NbtUtils.readBlockPos(CompoundTag, String)` (1.20.5 で変更) … `MarkerBlockEntity`。
- `AbstractContainerMenu.clicked` / `clickMenuButton` … `DiamondFilterMenu`, `GateMenu`。

## 静的確認の範囲と制約
- ビルド・コンパイル・ゲーム起動・ゲームテストは一切実行していない。
- 確認した内容: 全 import が NeoForge 21.1.265 のソース (`neoforge-21.1.265-sources.jar`) または自プロジェクトに存在すること、
  主要 API の引数・戻り値をソースで照合、blockstate → model → texture の参照、登録 ID と言語ファイル・ルートテーブル・レシピの対応、JSON 構文、
  共通コードからクライアント専用クラスを参照していないこと。
- 静的確認では保証できないこと: 型推論・ジェネリクス・オーバーロード解決を含むコンパイル可否、描画結果 (UV の向き、エンティティ用テクスチャの貼り方)、
  ネットワーク同期のタイミング、データマップ/レシピの実際の読み込み、ゲームバランス。
