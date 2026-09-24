package local.train.blockwindow.service;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.MutexRule;
import local.train.blockwindow.repo.MutexRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MutexEvaluatorTest {

    static final Instant S = Instant.parse("2026-09-30T10:00:00Z");
    static final Instant E = Instant.parse("2026-09-30T12:00:00Z");

    @Mock MutexRuleRepository repo;

    private BlockWindow window(long id, String type, List<String> secs, Instant s, Instant e) {
        BlockWindow w = new BlockWindow("P" + id, "t", type, secs, s, e, "Z");
        try {
            var f = BlockWindow.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(w, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return w;
    }

    @Test
    void noMatrixEntryMeansCompatibleEvenOnSameSection() {
        when(repo.findAll()).thenReturn(List.of());
        MutexEvaluator eval = new MutexEvaluator(repo);
        BlockWindow a = window(1, "CABLE_TEST", List.of("S03"), S, E);
        BlockWindow b = window(2, "ROUTINE_INSPECTION", List.of("S03"), S, E);
        // 区段号完全相同，但矩阵空白：绝不能仅凭编号判冲突
        assertThat(eval.evaluate(a, b)).isNull();
    }

    @Test
    void matrixHitRequiresOverlapAndSectionIntersection() {
        when(repo.findAll()).thenReturn(List.of(
                new MutexRule("TRACK_WORK", "SIGNAL_REPLACEMENT", "侵界")));
        MutexEvaluator eval = new MutexEvaluator(repo);

        BlockWindow a = window(1, "TRACK_WORK", List.of("S03", "S04"), S, E);
        BlockWindow b = window(2, "SIGNAL_REPLACEMENT", List.of("S02", "S03"), S, E);
        MutexEvaluator.MutexHit hit = eval.evaluate(a, b);
        assertThat(hit).isNotNull();
        assertThat(hit.sharedSections()).containsExactly("S03");

        // 首尾相接（半开区间）不算重叠
        BlockWindow backToBack = window(3, "SIGNAL_REPLACEMENT", List.of("S03"), E, E.plusSeconds(3600));
        assertThat(eval.evaluate(a, backToBack)).isNull();

        // 时间重叠但区段不相交
        BlockWindow far = window(4, "SIGNAL_REPLACEMENT", List.of("S09"), S, E);
        assertThat(eval.evaluate(a, far)).isNull();
    }
}
