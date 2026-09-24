package com.railwindow.sim.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link MutexRule} 复合主键。
 */
public class MutexRuleId implements Serializable {

    private String workTypeA;
    private String workTypeB;

    public MutexRuleId() {
    }

    public MutexRuleId(String workTypeA, String workTypeB) {
        this.workTypeA = workTypeA;
        this.workTypeB = workTypeB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MutexRuleId that)) {
            return false;
        }
        return Objects.equals(workTypeA, that.workTypeA) && Objects.equals(workTypeB, that.workTypeB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workTypeA, workTypeB);
    }
}
