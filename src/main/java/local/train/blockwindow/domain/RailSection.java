package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 模拟区段字典（含相邻关系）。 */
@Entity
@Table(name = "rail_section")
public class RailSection {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** 相邻区段编码。仅用于校验“计划占用多个相邻区段”的连续性。 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "adjacent_to", columnDefinition = "text[]")
    private List<String> adjacentTo = new ArrayList<>();

    protected RailSection() {
    }

    public RailSection(String code, String name, List<String> adjacentTo) {
        this.code = code;
        this.name = name;
        this.adjacentTo = adjacentTo;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public List<String> getAdjacentTo() {
        return adjacentTo;
    }
}
