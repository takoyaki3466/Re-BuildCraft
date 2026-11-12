package com.takoy3466.re_buildcraft.api;

/**
 * ゲートのアクションで機械に送る運転指示。元実装: {@code buildcraft.core.ActionMachineControl.Mode}。
 */
public enum MachineControl {
    /** 運転する。 */
    ON,
    /** 止める。 */
    OFF,
    /** 作業が終わっても最初から繰り返す。 */
    LOOP
}
