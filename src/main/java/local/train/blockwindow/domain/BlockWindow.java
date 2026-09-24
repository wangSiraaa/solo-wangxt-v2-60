package local.train.blockwindow.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 封锁窗口计划。
 *
 * <p>时间使用 UTC {@link Instant}，因此跨午夜窗口只是普通的时间区间，无特殊分支。
 */
@Entity
@Table(name = "block_window")
public class BlockWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_no", nullable = false, unique = true)
    private String planNo;

    @Column(name = "title", nullable = false)
    private String title;

    /** 作业类型，互斥判断的矩阵键之一。 */
    @Column(name = "work_type", nullable = false)
    private String workType;

    @Column(name = "status", nullable = false)
    private String status = WindowStatus.DRAFT.name();

    /** 占用区段（可能是多个相邻区段）。冲突判断必须逐区段求交，禁止只比单个设备编号。 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "section_codes", columnDefinition = "text[]", nullable = false)
    private List<String> sectionCodes = new ArrayList<>();

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    /** 开工幂等键：同键重试返回同一结果，不会产生第二次开工。 */
    @Column(name = "start_idem_key", unique = true)
    private String startIdemKey;

    /** 销记幂等键。 */
    @Column(name = "release_idem_key", unique = true)
    private String releaseIdemKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BlockWindow() {
    }

    public BlockWindow(String planNo, String title, String workType, List<String> sectionCodes,
                       Instant plannedStart, Instant plannedEnd, String zoneId) {
        this.planNo = planNo;
        this.title = title;
        this.workType = workType;
        this.sectionCodes = sectionCodes;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.zoneId = zoneId;
    }

    public WindowStatus getStatus() {
        return WindowStatus.valueOf(status);
    }

    public void setStatus(WindowStatus s) {
        this.status = s.name();
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        // 半开区间 [start, end)：首尾相接不算重叠
        return this.plannedStart.isBefore(otherEnd) && otherStart.isBefore(this.plannedEnd);
    }

    public Long getId() {
        return id;
    }

    public String getPlanNo() {
        return planNo;
    }

    public String getTitle() {
        return title;
    }

    public String getWorkType() {
        return workType;
    }

    public List<String> getSectionCodes() {
        return sectionCodes;
    }

    public Instant getPlannedStart() {
        return plannedStart;
    }

    public Instant getPlannedEnd() {
        return plannedEnd;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getStartIdemKey() {
        return startIdemKey;
    }

    public void setStartIdemKey(String startIdemKey) {
        this.startIdemKey = startIdemKey;
    }

    public String getReleaseIdemKey() {
        return releaseIdemKey;
    }

    public void setReleaseIdemKey(String releaseIdemKey) {
        this.releaseIdemKey = releaseIdemKey;
    }
}
