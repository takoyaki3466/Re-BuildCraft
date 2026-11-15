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
| 緑マーカー (パスマーカー) | `TilePathMarker`, `BlockPathMarker`, `WorldIteratorRadius` | `builders.PathMarker{Block, BlockEntity}`, `client.PathMarkerRenderer` | 実装済み | 青マーカーを継承 (旧実装と同じ)。右クリックで半径 64 の立方体の中の最も近い、つながり切っていない緑マーカーと接続 (1 個最大 2 本)、経路のたどり方、壊したときの解除、レーザー表示、レシピ (緑の染料 + レッドストーントーチ)。探索方法の変更点は「ビルダー系の設計」を参照 |
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
| フィラー | `TileFiller`, `BlockFiller`, `CraftingFiller`, `GuiFiller`, `SlotFillerMode`, `FillerRecipe`, `FillerPatternCore`, `ItemFillerModule` | `builders.Filler{Block, BlockEntity, Menu}`, `builders.filler.{FillerPattern, FillerPatterns, FillerRecipes, FillerCursor}`, `client.AreaRenderer`, `client.screen.FillerScreen` | 実装済み | 旧版と同じく、蓄えた MJ が作業量に足りる限り同じ tick の中で作業を繰り返す (「動作速度 (フィラー)」)。隣のマーカーから範囲を受け取りマーカーを撤去、作業台枠 (レンガ/ガラス/水バケツの図案、左右反転可) かモジュールでパターンを決定、材料枠の最初のアイテムで作業、パターンごとの動力 (setPower)、MJ がある限り繰り返す作業、完了判定、On/Off/Loop、上下面の稼働表示と側面のパターン絵柄、範囲のしま模様レーザー、モジュールの作成 (作業台枠を 1 個ずつ消費・容器を返却)、材料枠のパイプ入出力、保存。パターン 23 種 (埋め立て・撤去・整地・箱・ピラミッド・階段・完全消去・壁・1.4.7 型撤去・穴埋め・池・床張替え・タワー・天井張替え・壁張替え・外壁・円壁・円柱消去・水設置・地下埋め・クワ・階段設置・松明設置) をすべて移行。モジュールは 23 個の別アイテム |
| 設計図・テンプレート (データ) | `BptBase`, `BptBlueprint`, `BptTemplate`, `BptSlot`, `BptContext`, `BptRootIndex`, `BptPlayerIndex`, `ItemBptBase`, `ItemBptTemplate`, `ItemBptBluePrint` | `builders.blueprint.{Blueprint, BlueprintStore, BlueprintRef, BlueprintItem}`, `registry.ModDataComponents` | 実装済み | 記録・回転・保存・番号付け・名前の重複回避・プレイヤーごとの一覧・アイテム (白紙/記録済みの見た目、説明文に名前)。保存形式は「設計図の保存形式と互換性」を参照 |
| 旧設計図ファイル (.bpt) の取り込み | `BptBase.loadBluePrint`, `BptBlueprint.loadAttribute`, `BptDataStream`, `BlockSignature` | `builders.blueprint.LegacyBlueprintReader` | 部分実装 | テンプレートは完全に、設計図はバニラのブロックのみ取り込む。Mod のブロック・表に無い ID/メタデータを含む設計図は取り込まない。ブロックエンティティのデータは取り込まない (理由は同節) |
| 設計台 | `TileArchitect`, `BlockArchitect`, `CraftingTemplate*`, `GuiTemplate`, `SlotTemplate` | `builders.Architect{Block, BlockEntity, Menu}`, `client.screen.ArchitectScreen`, `BuilderPayloads.ArchitectName` | 実装済み | マーカーの範囲の取得 (2,097,152 ブロックまで)、40 tick の記録、テンプレートの反転記録 (信号中)、向きの正規化、同じ中身なら同じ番号、名前欄 (英数字と空白、サーバーで検証)、向き変更 (レンチ)、範囲のレーザー、レシピ |
| 設計図ライブラリ | `TileBlueprintLibrary`, `BlockBlueprintLibrary`, `ContainerBlueprintLibrary`, `GuiBlueprintLibrary` | `builders.Library{Block, BlockEntity, Menu}`, `client.screen.LibraryScreen`, `BuilderPayloads.LibraryContents` | 実装済み | 登録 (100 tick)・複製 (100 tick、選んだ設計図または白紙)・一覧の 12 件ずつの表示とページ送り・選択・Del・Lock (持ち主以外は開けない)、レシピ (白紙の設計図 8 + 本棚)。一覧の持ち主の扱いは「ビルダー系の設計」を参照 |
| ビルダー | `TileBuilder`, `BlockBuilder`, `CraftingBuilder`, `GuiBuilder`, `BptBuilderBase`, `BptBuilderTemplate`, `BptBuilderBlueprint`, `SurroundingInventory`, `EntityRobot`, `BptBlock*` | `builders.Builder{Block, BlockEntity, Menu}`, `builders.blueprint.{BuildPlan, BlueprintRules, BuilderMaterials}`, `client.BuilderRenderer`, `client.screen.BuilderScreen` | 実装済み | 向きに合わせた回転、取り除き (上から) → 建築 (下から、立方体 → その他)、材料は自分と隣接インベントリから、材料不足のマスは後回し (建築は終わらない)、ワールドの既定がクリエイティブなら材料不要、25 MJ ごとに 1 マスを建設ロボットへ予約、緑マーカーの経路に沿った繰り返し建築、必要アイテム欄、範囲のレーザーとロボットの描画、保存、レシピ。ブロックごとの規則の置き換えは「ビルダー系の設計」を参照 |
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

### 公開 API の追加 (アドオンのヒートシンク用)
- `api.engine.EngineLogic#dissipateHeat(int)` を追加した。既定は何もしない (熱の概念が無いエンジン)。燃焼エンジン (`CombustionEngineLogic`) だけが
  熱量を指定量だけ下げる (0 未満にはならない)。
- 用途: Rebuild Compact Engine の IC2 連携で移行したヒートシンク (旧 `CEHeatSinkTile`。旧版は `TileEngine.engine` が `EngineIron` の場合に
  `getHeat` / `setHeat` で熱を直接書き換えていた)。アドオンがエンジンの内部 (燃焼エンジンのクラス) に触れずに済むよう、公開 API の操作として用意した。
- 既存のエンジン・機械の動作は変わらない (呼ばれない限り何も起きない)。ビルド・ゲーム内確認はしていない。

## 不具合の調査と修正 (パイプ描画・設計図の名前・JEI・搬送・IC2 接続・クアーリー)
ビルド・コンパイル・テスト・ゲーム起動は行っていない。以下は旧ソース (BC 3.1.5 の逆コンパイル結果) と現行コードの照合による静的な調査・修正。

### 1. エネルギーコンデンサー MK2 の生成物が意図しないチェストに入る (BC 側の原因)
- 実際にアイテムを動かしていた処理: **自動作業台の `craft` → 材料の返却 (`giveBack`)**。
  自動作業台は見本が 1 個しか無い材料を隣接インベントリから 1 個ずつ取る (旧 getNearbyItem)。以前の実装は「取ってから足りないと分かったら戻す」方式で、
  戻し先が搬入を受け付けない場合 (ProjectE のコンデンサーの自動搬出用インベントリは出力枠が取り出し専用) に、戻せなかった材料を
  `InventoryUtils.insertIntoAdjacentInventories` で **自動作業台の別の隣接インベントリ (チェスト)** へ入れていた。
  旧実装 (`resetPointers`) は元のスロットへ直接書き戻していたため、この移動は起きなかった。
- 修正 (`factory/AutoWorkbenchBlockEntity`): レシピと全材料の出どころを先に確かめ (取り出しの試行 = simulate、同じスロットを複数回使う場合は合計個数で確認)、
  そろっている場合だけ実際に取り出す。足りなければどのインベントリにも触れない。万一取り出しの途中で失敗した場合も、戻せない材料は
  その見本スロット → 上方への放出の順で扱い、無関係な隣接インベントリへは入れない。
  あわせて、外部向けハンドラの「試しに取り出す」が材料の有無を見ずに完成品を返していた (実際の取り出しと食い違う) のを、同じ確認処理で判定するよう直した。
- 木のパイプ・パイプの搬送 (`ItemTransport`): コンデンサーの出力を木のパイプが取り出してパイプの先へ運ぶのは通常の動作で、原因ではない。変更していない (下記 7 の修正を除く)。
- CE 側にも原因があった (コンデンサーの自動搬出が、チェスト以外の隣接機械にも入れていた)。CE の MIGRATION.md を参照。

### 2. IC2 の機械が導電パイプにつながらない
- BC 側の判定: 導電パイプは隣のブロックが `BuildCraftCapabilities.POWER_RECEPTOR` を返すとき接続し (`PipeConnectionRules`)、送電も同じケーパビリティ・同じ面で行う
  (`PowerTransport.tick`)。接続表示と実際の送電は同じ条件で決まり、食い違わない。接続の再判定はブロックの形状更新と隣接変化 (`neighborChanged`) で行う。BC 側の不具合は見つからなかった。
- 原因 (IC2 Classical 側の仕様): IC2 Classical の `integration/buildcraft/BuildCraftEnergyBridge` は、設定 `buildcraft.euPerMj` が 0 より大きいときだけ
  ケーパビリティを返す。**既定値は 0 (無効)** で、旧 IC2 にも Re-BuildCraft にも MJ と EU の比率の定めが無いため、利用者が決める設計になっている。
  加えて、受電装置であること (`EnergySink`)、安全な最大入力が分かっていること (`getMaxSafeInput() > 0`)、その面が受電面であること (蓄電装置・変圧器は向きで変わる) が条件。
  発電機は受電装置ではないため接続しない。
- 対応: BC 側は変更していない (全ブロックエンティティを無条件に接続可能にするような変更はしない)。使う場合は IC2 Classical の設定で比率を決める必要がある。
  設定変更後は、接続を取り直すため隣接ブロックの更新 (パイプの置き直しなど) が必要 (IC2 側の設定コメントに記載済み)。
- 補足: **アイテムのパイプ**は、IC2 Classical の機械がアイテムのケーパビリティ (`Capabilities.ItemHandler.BLOCK`) を公開していないため接続しない。
  これは IC2 Classical 側で、機械ごとの搬入・搬出面とスロットを決めて公開する必要がある (BC 側では対応できない)。

### 3. パイプ内のアイテムの位置ずれ / 4. 大きさ
- 原因 (`client/PipeRenderer.renderItems`): 論理上のアイテムの位置はパイプの「床」(ブロックの下から 0.25、旧 `Utils.getPipeFloorOf`) にある。
  旧 `RenderPipe.doRenderItem` は描画時に 0.25 持ち上げてパイプの中心に描いていたが、移行版は床の高さのまま描き、さらに表示用の変形 GROUND
  (落ちているアイテム用で、地面から少し浮かせる移動を含む) を使っていたため、中心から下にずれていた。
  大きさは、GROUND 自体がアイテムを 0.5 倍・ブロックを 0.25 倍に縮めるうえに、さらに 0.5 倍を掛けていたため、旧版の半分だった。
- 修正: 位置は「床 + (0.5 - 床)」で中心線に描く (定数は `ItemTransport.PIPE_FLOOR` から求める。縦のパイプでも論理位置は同じく床の分だけずれているため同じ式)。
  表示用の変形は使わず (`ItemDisplayContext.NONE`: モデルの中心が原点)、大きさを旧版と同じ「立体のモデル 0.25・平らなアイテム 0.5」にする
  (旧版はブロックの描画種別で分けていた。移行版は `BakedModel.isGui3d()` で分ける)。どちらもパイプの芯の幅 0.5 に収まる。
  移動・補間 (`TravelingItem` の位置と速度) は変更していない。通常のワールドのアイテム描画には影響しない (パイプの描画だけの変更)。

### 5. 設計図に名前を付けられない
- 調べた経路: 設計台の名前欄 (`ArchitectScreen` の EditBox) → `BuilderPayloads.ArchitectName` (登録済み、サーバーで開いているメニューと位置を確認し、英数字と空白・32 文字に制限)
  → `ArchitectBlockEntity.setName` → 保存・同期 → 設計図作成時に `Blueprint.setName` → `BlueprintStore` (重複名に連番) → アイテムの `BlueprintRef` (説明文に表示)。
- 原因 1 (サーバー): `createBlueprint` は、直前に作った設計図と内容が同じなら前の設計図を使い回していた。前の設計図の名前のままになるため、
  「作る → 名前を入れる → もう一度作る」では名前が付かなかった。旧 BC の `BptBase.equals` はクラス比較が逆で常に false になり、毎回新しく保存していた (名前は必ず反映された)。
  修正: 使い回すのは「内容が同じ」かつ「名前を指定していない、または前回と同じ名前を指定した」場合だけ。前回指定した名前は `last_blueprint_name` として保存する
  (重複回避で末尾が変わる前の名前で比べる)。既存の設計図の名前は書き換えない (それを指す他のアイテムがあるため)。保存形式は項目の追加のみで、既存データはそのまま読める。
- 原因 2 (画面): EditBox の既定の文字色 (明るい灰色) が設計台の背景に溶け、枠も無いため、入力しても見えなかった。旧 GuiTemplate と同じ `0x404040` で描くようにした。
- 名前は設計図アイテムの説明文に表示される (旧版と同じ)。空の場合は `unnamed`、重複は `_1` などの連番 (旧版と同じ)。

### 6. JEI にレシピが出ない
- 原因: Re-BuildCraft に JEI 連携が無かった。レシピ自体は登録されており、組立台 (`re_buildcraft:assembly`)・精製機 (`re_buildcraft:refinery`) は
  機械が実際に `RecipeManager` からそのレシピ型を照合している (`AssemblyTableBlockEntity`, `RefineryBlockEntity`)。レシピの登録は変更していない。
- 追加 (`integration/jei`): `BuildCraftJeiPlugin` (`@JeiPlugin`)、`AssemblyRecipeCategory` (材料 12 マス・個数付き、完成品、必要なレーザーエネルギー)、
  `RefineryRecipeCategory` (液体の材料 1 - 2 種・生成物、mB、必要 MJ と間隔)。触媒: 組立台とレーザー、精製機。自動作業台はバニラの作業台レシピを使うため、
  JEI の作業台カテゴリに触媒として追加した (カテゴリは重複させない)。表示するのはクライアントへ同期された実レシピだけ (架空のレシピは無い)。検索・逆引きは JEI の入出力の区別で行われる。
- 依存: JEI 19.70.0.469 (IC2 Classical と同じ版) を `compileOnly` (API) + `runtimeOnly`、`neoforge.mods.toml` で optional。他のクラスは JEI のパッケージを参照しないため、
  JEI が無い環境では JEI のクラスは読み込まれない。JEI はプラグインをクライアントでだけ読み込む。
- 表示対象外: エンジンの燃料・クアーリー・ポンプなど (レシピ型を使わず、コードで条件を持つもの)。

### 7. 木のパイプで自動作業台から取り出せないことがある (CE の圧縮木エンジン改の不具合の BC 側の原因)
- 原因 (`transport/behavior/WoodItemExtractionBehavior`): 取り出す個数を「蓄積 MJ の整数部分」で決めていた。自動作業台の完成品枠は完成品をまとめてしか渡せない
  (IItemHandler の決まりで、要求より多くは返せない) ため、蓄積が完成品の個数 (板材なら 4) より少ないと何も取り出せず、しかも MJ は先に消費していた。
  蓄積は毎 tick 失われる (旧 `configurePowerPerdition(64, 1)`) ため、1 tick に完成品の個数以上の MJ が届かないエンジンでは、複数個できるレシピを永遠に取り出せない。
  旧実装は自動作業台のような特殊インベントリ (`ISpecialInventory`) から、蓄積量に関係なく 1 回分をまとめて取り出していた。
- 修正: 指定個数で取り出せず、まとめてなら取り出せる枠は旧 `ISpecialInventory` と同じ扱いにし、1 回分をまとめて取り出す。MJ は取り出しに成功したときだけ消費する。
  通常のインベントリ (指定個数で取り出せるもの) の動作は変わらない。

### 8. クアーリーが広い範囲で途中から掘らなくなる (CE の改良型クアーリーと共通の原因)
- 原因 (`api/quarry/QuarryBlockEntity.tryTarget`, `FrameBlueprint.pollInvalid`): 読み込まれていないチャンクの位置を「掘る物が無い」「作業済み」として飛ばしていた。
  探索は上の層から順に進み、一度下の層へ進むと上の層を探し直さないため、飛ばした部分は二度と掘られない。読み込まれている部分を掘り終えると
  `findTarget` が false を返し、`stopQuarry` で採掘が終了していた。範囲が広いほど、読み込み範囲 (プレイヤーの周囲) の外に出る部分が増えるため起きやすい。
  旧版 (1.2.5) はブロックを調べるだけでチャンクが読み込まれたため起きなかった。整数のあふれ・配列の上限・探索の打ち切りは原因ではなかった (該当する処理が無いことを確認)。
- 修正: 読み込まれていない位置に当たったら、その位置で探索を止めて待つ (採掘を終了しない)。そのチャンクだけを一時チケット (`re_buildcraft_quarry`、300 tick で自動的に外れる、
  半径 1) で読み込ませ、読み込み後に同じ位置から再開する。目標を決めたときも、アームが着くまで目標のチャンクのチケットを延長する。枠組みの設計図も同様に、
  読み込まれていないマスを捨てずに残して待つ。チケットは非同期読み込みで、1 回の探索で頼むのは 1 チャンクだけのため、1 tick の負荷は増えない。
  常時読み込み (チャンクローダー) ではなく、クアーリー本体のチャンクが読み込まれて動いている間だけ働く。
- 派生クラス向けに `isWaitingForChunk()` と `requestWorkChunk(Level, BlockPos)` を追加した (CE の改良型クアーリーの探索で使う)。

### 主なファイル
- 変更: `factory/AutoWorkbenchBlockEntity.java`, `transport/behavior/WoodItemExtractionBehavior.java`, `client/PipeRenderer.java`,
  `builders/ArchitectBlockEntity.java`, `client/screen/ArchitectScreen.java`, `api/quarry/QuarryBlockEntity.java`, `api/quarry/FrameBlueprint.java`,
  `build.gradle`, `gradle.properties`, `neoforge.mods.toml`, `lang/en_us.json`, `lang/ja_jp.json`
- 追加: `integration/jei/{BuildCraftJeiPlugin, AssemblyRecipeCategory, RefineryRecipeCategory}.java`

### 静的に確認したこと / 未確認のこと
- 確認: import の解決 (JEI の import はすべて JEI 19.70.0.469 のソースに存在)、使った API の署名 (`ItemRenderer.render` / `getModel`、`BakedModel.isGui3d`、
  `EditBox.setTextColor`、`TicketType.create(String, Comparator, int)`、`ServerChunkCache.addRegionTicket`、JEI の `AbstractRecipeCategory` / `IRecipeSlotBuilder.setFluidRenderer` /
  `NeoForgeTypes.FLUID_STACK` / `IRecipeExtrasBuilder.addRecipeArrow` / `IPlaceable.setPosition`、NeoForge の `SizedIngredient.getItems` / `SizedFluidIngredient.getFluids`)、
  言語キー (en_us / ja_jp 一致)、自動作業台の呼び出し元 (GUI の完成品枠・シフトクリック) の動作が変わらないこと。
- 未確認 (ビルド・ゲーム起動をしていないため): コンパイル可否、JEI での表示、アイテムの見た目の大きさ・位置、名前入力の見え方、チケットによる読み込みの挙動、各修正の実際の動作。

## 元の仕様から変更した点 (ビルダー系)
- マーカーの組は旧実装のように 1 つの Origin オブジェクトを共有せず、各マーカーが原点の位置、原点が各軸の相手の位置を保存する (チャンクの読み込み状態に依存しない)。
  原点の相手を差し替えたとき、前の相手は組から外す (旧実装では組の一員のまま取り残され得た)。
- マーカーの自動設置は、旧実装ではクリエイティブで置き換え不能なブロックも上書きし、サバイバルでは `onItemUse` の仕様で 1 つ上に置かれ得た。
  ここでは置き換え可能な場所にだけ、計算した座標そのものに置く。旧実装がクライアントのプレイヤーのクリエイティブ判定で決めていた「撤去したマーカーを落とすか」は、サーバーでは判定できないため常に落とす。
- フィラーの `softBlock` 等の判定はブロックステートに置き換えた (`FillerPattern` の説明を参照)。ブロックの設置は偽プレイヤーで行い、ブロックアイテムは計算した座標そのものに置く
  (旧 `onItemUse(x, y, z, 1)` の結果座標は 1.2.5 の `ItemBlock` の実装に依存し、ソースが無く確認できないため)。
  設置できないときに取り除く生き物からプレイヤーを除外した。クリエイティブで材料を減らさない処理 (クライアントのプレイヤーを参照していた) は移行していない。
- 池・水設置で使う「フィラーの上のタンク」は、旧実装の `TileTank` 限定から液体ハンドラを持つブロック全般にした。液体ブロック自体をアイテムとして置く処理 (1.2.5 の液体ブロック ID のアイテム) は 1.21.1 に該当アイテムが無いため無い。
- 「1.4.7 型撤去」のドロップがすぐ消える仕様 (旧 age = 5980) は `ItemEntity.lifespan` を短くして表した。

### 動作速度 (フィラー) — 同一 tick 内の連続作業 (最新)
前回は「1 tick に 1 か所まで」に制限したが、ユーザーの指示により旧 BCx と同じ連続作業方式へ戻した。

| 項目 | 旧 BCx (TileFiller) | 前回の実装 | 今回の実装 |
| --- | --- | --- | --- |
| 1 回の作業 | パターンの iteratePattern 1 回 (置く・壊すのは最大 1 か所) | 同じ | 同じ |
| 同じ tick の繰り返し | doWork の最後で「蓄積 &gt; 作業量」なら自分を呼び直す (回数の上限なし) | `lastWorkTick` で 1 tick 1 回 | `while` で「蓄積 ≥ 作業量」の間繰り返す (回数の上限なし) |
| MJ を使う時点 | 作業の前に作業量分を使う (何も起きなくても使う) | 同じ | 作業量分あることを先に確かめ、作業で何かが起きた後に使う |
| 何も起きなかった作業 | MJ を使い、MJ がある限り同じ対象を試し続ける | 1 tick 1 回試す | MJ を使わず、その tick の作業をやめる |
| 完了後 | 完了していても doWork が MJ を使い続けた | 作業しない | 作業しない (MJ を使わない) |

- 制限していた箇所: `FillerBlockEntity.doWork` の `lastWorkTick` (同じゲーム時刻の 2 回目以降を拒否)。削除し、ループを作り直した。
- 「何かが起きた」の判定: `FillerCursor.acted` (置いた・壊した・消した・液体を置いたことを、ワールドの変更に成功したときだけ記録) と、
  作業位置 (`FillerCursor.progressKey`) が変わったか。全 23 パターンの 1 回の処理は「1 か所を処理して戻る」「位置を進めて戻る」「完了して戻る」
  「何もせずに戻る (材料が無い・置けない・液体が無い)」のいずれかで、最後の場合だけ繰り返しをやめる。
- 終了の保証: 繰り返すたびに作業量 (1 以上の整数) の MJ を使うため、回数は最大で「蓄積量 ÷ 作業量」。蓄積量の上限はパターンごとの作業量 x 回数 (旧 setPower、例: 埋め立て 16 x 512 = 8192 MJ)。
- 同時に直したこと: ブロックを壊す処理で、撤去に失敗した場合はドロップを出さない。液体・砂の処理で、置けなかった場合はタンク・バケツ・砂を減らさない (旧実装は先に減らしていた)。
- 1 回の作業の MJ (パターンの setPower の work) と、受け取り量・蓄電量は旧実装のまま。共通の動力処理 (`PowerProvider`) は変更していない。

#### 超エンジンからフィラーまでの動力の経路 (調査結果)
- 改エンジン・超エンジン (`CompactEngineExBlockEntity.transferContinuously`) は毎 tick、`倍率 / 2.56 x 0.16` MJ (黒猫 512 MJ/t、黒烏 2048 MJ/t) を正面の機械へ直接送る。
  受け手の受け取り上限 (maxEnergyReceived) では絞らず、受け手の `PowerProvider.receiveEnergy` が蓄電量で頭打ちにする。エンジン側のエネルギーは毎 tick 満タンに戻る (旧 CE と同じ)。
- 導電パイプ経由では、1 つの面に 1 tick で 1000 MJ 以上流れるとパイプが爆発する (旧 PipeTransportPower と同じ。変更していない)。黒烏 (2048 MJ/t) を導電パイプにつなぐと爆発する。
- フィラーの蓄電量は作業量 x 回数 (旧 setPower)。例: 埋め立て (16 x 512 = 8192 MJ) に黒猫を直付けすると、毎 tick 512 MJ が入り、同じ tick に最大 32 か所 (512 ÷ 16) を処理する。
  撤去 (32 x 2 = 64 MJ) は蓄電量が 64 MJ のため、どれだけ供給しても 1 tick 最大 2 か所だった (下の「大出力の受け取り」で改善)。
- 数値: 蓄積は float、作業量は int。蓄電量の最大は数万 MJ 程度で、float の精度・オーバーフロー・負数化の問題は生じない (`useEnergy` は蓄積 ≥ 要求量のときだけ減らす)。
- 受け取り量と使える量の差: 蓄電量を超えた分は受け取り時に捨てられる (旧実装と同じ)。超エンジンは毎 tick 満タンに戻るため、エンジン側の損失にはならない。

#### 大出力の受け取り (最新: 蓄電量で捨てていた分を作業に使う)
- 速度を制限していた原因: `PowerProvider.receiveEnergy` が受け取った MJ を蓄電量 (パターンの作業量 x 回数 = 旧 setPower) で切り捨てていた。
  受け取りより消費が少ないパターン (撤去 64 MJ、タワー・円壁作成 256 MJ など) では、大出力のエンジンをつないでも 1 tick に蓄電量 ÷ 作業量の回数しか作業できず、残りは捨てられていた。
  エンジン側 (改エンジン・超エンジンは受け手の上限で絞らずに送る) と導電パイプ側 (流れてきた分をすべて機械に渡す。要求量は分配の比率にだけ使う) は、切り捨てていない。
- 採用した方式: `PowerProvider.configureTickInputBuffer(true)` (フィラーだけが有効にする)。蓄電量の上限を「前回の `update` (= フィラーの tick の先頭) から受け取った量」の分だけ一時的に広げ、
  受け取った MJ を次の作業の機会まで捨てずに持つ。同一 tick 内の連続作業 (`FillerBlockEntity.doWork`) がそれを 1 回ずつ使う。
  - 実際に届いた量から決まるため、エンジンの出力値をどこにも書いていない (別の大出力エンジンが追加されても対応する)。
  - 残量の上限は「蓄電量 + 1 回の更新の間に受け取った量」。材料不足・完了などで使い切れない間に届いた分は、すでに上限を超えている分を増やさないため、残量が増え続けることはない。
  - 1 回の作業量、受け取りの最小量、パターンごとの蓄電量 (作業量 x 回数)、通常のエンジン (ピストンの 1 往復ごとに受け手の受け取り上限まで送る) の動作は変わらない。
  - 共通の `PowerProvider` には既定で無効の設定を足しただけで、ほかの機械 (クアーリー・ポンプ・精製機・ビルダー・CE の機械など) の受け取りは旧実装どおり蓄電量で切り捨てる。
- 旧版との違い: 旧 BCx は蓄電量を超えた分を切り捨てていた。高出力のエンジン (CE の改エンジン・超エンジン) を活かすため、フィラーに限りこの切り捨てをやめた。
- 導電パイプ: 旧 BCx の `PipeTransportPower` も 1 面 1000 MJ 以上で爆発する (`maxEnergy = 1000`) ため、変更していない。パイプ経由では 1 面 1000 MJ/t 未満まで、
  それ以上はエンジンをフィラーに直接つなぐ。パイプ経由で届いた分もフィラーは同じ方式で捨てずに受け取る。

数値上の処理能力 (エンジンを直付けし、材料と作業対象が十分にある場合。1 tick に作業できる回数。導電パイプ経由の 2048 MJ/t はパイプが爆発するため直付けのみ):

| パターン | 作業量 (MJ) | 蓄電量 (MJ) | 従来 512 MJ/t | 従来 2048 MJ/t | 今回 512 MJ/t | 今回 2048 MJ/t |
| --- | --- | --- | --- | --- | --- | --- |
| 埋め立て | 16 | 8192 | 32 | 128 | 32 | 128 |
| 撤去 | 32 | 64 | 2 | 2 | 16 | 64 |
| 整地 | 24 | 1536 | 21 | 64 | 21 | 85 |
| 箱作成 | 16 | 8192 | 32 | 128 | 32 | 128 |
| ピラミッド | 16 | 4096 | 32 | 128 | 32 | 128 |
| 階段作成 | 16 | 4096 | 32 | 128 | 32 | 128 |
| 完全消去 | 24 | 6144 | 21 | 85 | 21 | 85 |
| 壁作成 | 16 | 8192 | 32 | 128 | 32 | 128 |
| 1.4.7型撤去 | 16 | 128 | 8 | 8 | 32 | 128 |
| 穴埋め | 24 | 1536 | 21 | 64 | 21 | 85 |
| 池作成 | 24 | 768 | 21 | 32 | 21 | 85 |
| 床張替え | 32 | 4096 | 16 | 64 | 16 | 64 |
| タワー | 32 | 256 | 8 | 8 | 16 | 64 |
| 天井張替え | 32 | 4096 | 16 | 64 | 16 | 64 |
| 壁張替え | 32 | 4096 | 16 | 64 | 16 | 64 |
| 外壁作成 | 16 | 8192 | 32 | 128 | 32 | 128 |
| 円壁作成 | 32 | 256 | 8 | 8 | 16 | 64 |
| 円柱消去 | 32 | 1024 | 16 | 32 | 16 | 64 |
| 水設置 | 24 | 768 | 21 | 32 | 21 | 85 |
| 地下埋め | 16 | 4096 | 32 | 128 | 32 | 128 |
| クワ | 24 | 768 | 21 | 32 | 21 | 85 |
| 階段設置 | 32 | 1024 | 16 | 32 | 16 | 64 |
| 松明設置 | 32 | 512 | 16 | 16 | 16 | 64 |

- 今回の値は「受け取った量 ÷ 作業量」(端数は次の tick へ繰り越す)。エンジンとフィラーの tick の順序によっては、届いた分を次の tick に使う。
- 未検証: ゲーム内での処理速度、1 tick に多くのブロックを置いたときのサーバー負荷、tick の順序による 1 tick の遅れ。

## ビルダー系の設計 (今回)
### 責務の分け方
- `builders.blueprint.Blueprint`: 設計図/テンプレートのデータ (記録・回転・保存)。`BlueprintStore`: 保管と番号と一覧。`LegacyBlueprintReader`: 旧ファイルの読み込みと変換。
- `BuildPlan`: 「次に何をするか」の判断 (旧 BptBuilderTemplate / BptBuilderBlueprint を 1 つに)。`BlueprintRules`: マスごとの規則 (旧 BptBlock 約 40 種の置き換え)。
  `BuilderMaterials`: 材料の確認・消費 (旧 SurroundingInventory)。
- `ConstructionRobot` (api.quarry) を「マスと処理」の予約 (`Task`) を受け取る形に一般化し、クアーリーの枠組みとビルダーで共用。
- `RotatableMachineBlock` (設計台・ビルダー共通のブロック)、`AreaHolder` + `client.AreaRenderer` (フィラー・設計台・ビルダーの範囲表示を共通化)、
  `FakePlayerPlacement` (偽プレイヤーでの設置。フィラーとテンプレート建築で共用)、`SoftBlocks` (柔らかいブロックの判定を共通化)。
- `QuarryRenderer.renderRobot` を公開してビルダーのロボット描画に使う。
- アドオン向けの差し替え点 (EMC Builder が使用): `BuilderBlockEntity` の型を受け取る protected コンストラクタと `createMaterials` (旧 `TileBuilder.createBptBuilderBase` に相当)、
  `BuilderMenu` のメニュー型を受け取る protected コンストラクタ、`BuilderMaterials` の継承 (`hasAll` / `useAll` の上書き)。既定の動作は変えていない。

### 旧版との差異
- 旧 BptBlock のブロック別処理は、1.21.1 のブロックステートで同じ判断ができるものを `BlueprintRules` の規則にまとめた: 必要アイテムはブロックのアイテム (2 枚重ねのハーフは 2 個、液体の水源はバケツ)、
  建てないもの (流れる液体、アイテムとして入手できないブロック、2 マスのブロックの上半分とベッドの頭側。下半分・足側を置くときに両方置く)、完成の判定はブロックの一致 (旧 isValid の既定)、
  先に建てるのは光を遮る立方体。旧 BptBlock*Mask のメタデータ比較、レバー・ボタン・看板など個別の補正は、向きなどをブロックステートごと保存・回転するため不要になった。
- 旧 postProcessing (設計図の建築後の処理) は、既定実装が空で、個別の BptBlock でも使うブロックが 1.21.1 に無いため持たない。
- ブロックエンティティ: アイテム・液体を持つもの (チェスト・タンクなど) はデータを記録しない (旧 BptBlockInventory は置いた後に中身を空にしていた。複製を防ぐ)。
  それ以外 (看板の文字など) は記録して、置いた後に復元する。
- 保存場所: 旧実装はゲームフォルダ (全ワールド共通) の `buildcraft/blueprints/*.bpt`。ここではワールドの保存データ `data/re_buildcraft_blueprints.dat` (マルチプレイ対応)。
- 図書館の一覧: 旧実装はローカルプレイヤーのもの。サーバーではライブラリの持ち主 (置いたプレイヤー) の一覧を使う。持ち主がいなければ開いたプレイヤー。
- 緑マーカーの探索: 旧実装は 129^3 の座標を距離順に 1 tick 1000 か所ずつ (最大約 107 秒) 調べた。ここでは読み込み済みチャンクのブロックエンティティから候補を集め、同じ順序で選ぶ (即時)。
  旧実装が探索中に読み込んでいた未読み込みチャンクは調べない。
- 緑マーカーの 2 本目のつながりは、旧実装では保存時に 1 本目を上書きして失われていた。両方を保存する。
- ビルダー: 先頭の枠が空のとき旧実装は毎 tick 25 MJ を使って何もしなかった。空なら作業を止める (動力要求も 0 で、旧実装と同じく動力は送られない)。
- テンプレート建築の材料は旧実装と同じく材料枠と隣接インベントリの先頭から 1 個。置けなかった場合も材料は使われる (旧実装と同じ)。

### 設計図の保存形式と互換性
- 新形式 (NBT): 種類・名前・作者・大きさ・基点、テンプレートは 0/1 のバイト列、設計図はブロックステートのパレット (レジストリ名 + プロパティ) とマスごとの番号、ブロックエンティティのデータ。
  読み込み時に登録されていないブロックがあれば、その設計図は読み込まない (空気などに置き換えない)。
- 旧形式 (.bpt) の取り込み (`LegacyBlueprintReader`): ワールドフォルダの `buildcraft/blueprints/*.bpt` を、サーバーの起動後に保管庫を初めて使うときに 1 回だけ取り込む (取り込み済みのファイル名を記録)。
  - テンプレート: `mask:` をそのまま取り込む (互換)。
  - 設計図: `idMap` の署名でブロッククラス名が `*` (1.2.5 のバニラのブロック、ID 122 以下) のものだけを、1.21.1 に含まれる平坦化の対応表 (`BlockStateData`: 1.12 の ID x 16 + メタデータ)
    → データ修正 (`DataFixers`、データバージョン 1451 から現在) で変換する。1.2.5 のバニラの ID は 1.12 まで変わっておらず、メタデータも下位ビットとして同じ意味で引き継がれていることを前提にしている (ブロックごとの検証はしていない)。
  - 取り込まないもの: Mod のブロック (BC・CE を含む。ID は設定で変わるため署名だけでは特定できない)、表に無い ID/メタデータの組み合わせ、形式 3.1.0 (旧実装でも非対応)。いずれかを含む設計図は全体を取り込まず、理由をログに出す。
  - 失う情報: マスのブロックエンティティのデータ (1.2.5 の形式から変換できない)、記録された必要アイテム (現在のブロックから求め直す)、旧 BptDataStream が書き出さなかったバイト配列。
  - 新形式から旧形式への書き出しはしない。

### ビルダー系の処理経路
- 設計台: `serverTick` → `takeAreaFromMarkers` → (入力あり) 40 tick → `createBlueprint` → `Blueprint.capture` → `rotateClockwise` → `BlueprintStore.store` → 出力枠
- 図書館: `serverTick` → 登録 `BlueprintStore.addToPlayer` / 複製 `BlueprintItem.withBlueprint`; 画面 → `handleInventoryButtonClick` → `LibraryMenu.clickMenuButton` (選択は一覧にある番号だけ) → `LibraryContents` で一覧を送り直す
- ビルダー: `serverTick` (初回 `takePathFromMarkers`) → `PowerProvider.update` → `doWork` (25 MJ) → `iterateBlueprint` → `BuildPlan.next` (`BuilderMaterials.hasAll/useAll`) → `ConstructionRobot.schedule` → `robot.update` → `BuildPlan.Step.execute`

### 今回の静的チェック
- import 解決・自クラス参照・リソース参照・登録 ID と言語/ルートテーブル/レシピ: 指摘 2 件 (既知の誤検出: 同パッケージのネスト enum の import)
- チェッカーの改善: ソースの jar に無い外部ライブラリ (DFU、Netty、LWJGL、FML) の import は、NeoForge/Minecraft のソース自身が同じクラスを import している場合だけ通す (手書きの許可リストに頼らない)。
- 新たに使った API をソースで確認: `RegisterPayloadHandlersEvent` / `PayloadRegistrar.playToServer/playToClient` / `PacketDistributor`、`SavedData.Factory` / `DimensionDataStorage.computeIfAbsent`、
  `NbtUtils.readBlockState/writeBlockState`、`BlockStateData.getTag`、`DataFixer.update`、`BlockEntity.saveCustomOnly/loadCustomOnly`、`DataComponentIngredient` (`neoforge:components`, strict)、
  `ItemProperties.register`、`ServerChunkCache.getChunkNow`、`BlockState.rotate`、`EditBox`、`Button.builder`

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
