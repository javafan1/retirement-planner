package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloProjectionAssociationTest {
    @TempDir Path directory;
    @BeforeAll static void init() throws Exception {startFx();}
    @ParameterizedTest @EnumSource(value=AnalysisKind.class,names=".*MONTE_CARLO.*",mode=EnumSource.Mode.MATCH_ALL)
    void allEightActualCompletionPathsBindTheirOriginalRequestAndAcceptedProjection(AnalysisKind kind) throws Exception {
        var fixture=new MonteCarloParameterPersistenceTest(); fixture.dir=directory;
        var source=fixture.source(kind,"original",new JsonRetirementPlanRepository());
        fx(()->{assertNotNull(source.getCurrentProjection());source.save();return null;});
        var projection=source.getAcceptedProjectionSnapshot();
        var view=fx(()->new View(source,kind,false));
        try {
            fx(()->{view.configure(kind);view.button("run").fire();return null;}); view.execute(); await(view.label("parameters-status"),"SAVED");
            var refs=source.getAnalyzerAssociations(projection.id()); assertEquals(1,refs.size(),source.getAnalyzerAssociationNotice(kind));
            var ref=refs.getFirst(); assertEquals(kind,ref.kind());
            var completion=source.getAnalysisResults().inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(),kind)).orElseThrow();
            assertEquals(completion.runId(),ref.runId()); assertEquals(completion.inputFingerprint(),ref.inputFingerprint());
            assertEquals(completion.payload(),source.loadAssociatedAnalyzer(ref).toCompletableFuture().get(20,TimeUnit.SECONDS).payload());
        } finally {fx(()->{view.close();return null;});stop(source);}
    }
}
