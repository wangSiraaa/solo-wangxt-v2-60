package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 计划-人员派工关系，带计划上的岗位角色。 */
@Entity
@Table(name = "plan_assignment")
public class PlanAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_window_id", nullable = false)
    private Long blockWindowId;

    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    /** WORK_LEADER / SAFETY_OFFICER / CONTACT / PROTECTOR */
    @Column(name = "role_on_plan", nullable = false)
    private String roleOnPlan;

    protected PlanAssignment() {
    }

    public PlanAssignment(Long blockWindowId, Long personnelId, String roleOnPlan) {
        this.blockWindowId = blockWindowId;
        this.personnelId = personnelId;
        this.roleOnPlan = roleOnPlan;
    }

    public Long getId() {
        return id;
    }

    public Long getBlockWindowId() {
        return blockWindowId;
    }

    public Long getPersonnelId() {
        return personnelId;
    }

    public String getRoleOnPlan() {
        return roleOnPlan;
    }
}
