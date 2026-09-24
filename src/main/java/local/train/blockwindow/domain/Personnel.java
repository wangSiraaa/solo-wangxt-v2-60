package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 模拟人员档案：岗位、作业资质及资质有效期。 */
@Entity
@Table(name = "personnel")
public class Personnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge", nullable = false, unique = true)
    private String badge;

    @Column(name = "name", nullable = false)
    private String name;

    /** WORK_LEADER / SAFETY_OFFICER / CONTACT / PROTECTOR */
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "qualification", nullable = false)
    private String qualification;

    /** 可从事的作业类型，如 SIGNAL_REPLACEMENT、TRACK_WORK。 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "qualified_work", columnDefinition = "text[]")
    private List<String> qualifiedWork = new ArrayList<>();

    @Column(name = "qual_valid_from", nullable = false)
    private Instant qualValidFrom;

    @Column(name = "qual_valid_until", nullable = false)
    private Instant qualValidUntil;

    protected Personnel() {
    }

    public Personnel(String badge, String name, String role, String qualification,
                     List<String> qualifiedWork, Instant qualValidFrom, Instant qualValidUntil) {
        this.badge = badge;
        this.name = name;
        this.role = role;
        this.qualification = qualification;
        this.qualifiedWork = qualifiedWork;
        this.qualValidFrom = qualValidFrom;
        this.qualValidUntil = qualValidUntil;
    }

    public Long getId() {
        return id;
    }

    public String getBadge() {
        return badge;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getQualification() {
        return qualification;
    }

    public List<String> getQualifiedWork() {
        return qualifiedWork;
    }

    public Instant getQualValidFrom() {
        return qualValidFrom;
    }

    public Instant getQualValidUntil() {
        return qualValidUntil;
    }
}
