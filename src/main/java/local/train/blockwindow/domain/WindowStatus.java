package local.train.blockwindow.domain;

/** 封锁窗口状态机：DRAFT（未开工）→ IN_PROGRESS（已开工）→ RELEASED（已销记）。 */
public enum WindowStatus {
    DRAFT,
    IN_PROGRESS,
    RELEASED
}
