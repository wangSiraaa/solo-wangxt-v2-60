package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 互斥作业矩阵条目。存储无序对，服务层双向查询。 */
@Entity
@Table(name = "mutex_rule")
public class MutexRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_type_a", nullable = false)
    private String workTypeA;

    @Column(name = "work_type_b", nullable = false)
    private String workTypeB;

    @Column(name = "note")
    private String note;

    protected MutexRule() {
    }

    public MutexRule(String workTypeA, String workTypeB, String note) {
        this.workTypeA = workTypeA;
        this.workTypeB = workTypeB;
        this.note = note;
    }

    public boolean matches(String t1, String t2) {
        return (workTypeA.equals(t1) && workTypeB.equals(t2))
                || (workTypeA.equals(t2) && workTypeB.equals(t1));
    }

    public String getWorkTypeA() {
        return workTypeA;
    }

    public String getWorkTypeB() {
        return workTypeB;
    }

    public String getNote() {
        return note;
    }
}
