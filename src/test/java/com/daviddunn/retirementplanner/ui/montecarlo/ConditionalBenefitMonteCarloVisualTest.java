package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.ConditionalBenefitIntegratedExecutionTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;

class ConditionalBenefitMonteCarloVisualTest {
    @TempDir Path directory;
    @BeforeAll static void init() throws Exception {startFx();}
    @Test void actualCompactAndStandardDialogsKeepFeedbackVisibleAndOriginalAssumptionsReadable() throws Exception {
        Files.createDirectories(ConditionalBenefitMonteCarloHistoryTest.REPORTS);
        for(var kind:List.of(AnalysisKind.COUPLE_MONTE_CARLO_FIXED,AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY)) {
            var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir=directory;
            var source=fixture.source(kind,kind.name(),new JsonRetirementPlanRepository());
            fx(()-> {
                var definitions = ConditionalBenefitIntegratedExecutionTest.plan().getConditionalBenefits();
                var longLabel = "Original conditional survivor benefit with a deliberately long explanation to verify compact wrapping and frozen provenance";
                source.getCurrentPlan().setConditionalBenefits(List.of(BenefitFixtures.change(definitions.getFirst(),"label",longLabel),definitions.get(1)));
                source.markModified();source.invalidateProjection();source.getCurrentProjection();source.save();return null;
            });
            var view=fx(()->new View(source,kind,false));
            try {
                fx(()-> {view.configure(kind);view.button("run").fire();return null;});view.execute();await(view.label("parameters-status"),"SAVED");
                fx(()-> {view.node.getScene().setRoot(new VBox());return null;});
                var dialog=fx(()-> kind.name().contains("PAIRED")
                        ? new MonteCarloStrategyComparisonDialog(null,(MonteCarloStrategyComparisonView)view.node)
                        : new MonteCarloAnalysisDialog(null,(MonteCarloAnalysisView)view.node));
                try {
                    for(var theme:ApplicationTheme.values()) for(int width:new int[]{900,1180}) {
                        var themes=fx(()->ApplicationThemeManager.start(theme));
                        try {
                            fx(()-> {
                                dialog.getDialogPane().setPrefSize(width,width==900?650:820);dialog.show();
                                var pane=dialog.getDialogPane();pane.applyCss();pane.layout();
                                var details=(TitledPane)view.node.lookup(kind.name().contains("PAIRED")?"#mcc-details":"#mc-analysis-details");
                                details.setAnimated(false);details.setExpanded(true);
                                ((javafx.stage.Stage)pane.getScene().getWindow()).sizeToScene();return null;
                            });
                            awaitLayoutPulses(fx(()->dialog.getDialogPane().getScene()));
                            fx(()-> {
                                ((ScrollPane)dialog.getDialogPane().lookup(kind.name().contains("PAIRED")?"#mcc-content-scroll":"#mc-content-scroll")).setVvalue(1);
                                return null;
                            });
                            awaitLayoutPulses(fx(()->dialog.getDialogPane().getScene()));
                            fx(()-> {
                                var pane=dialog.getDialogPane();var details=(TitledPane)view.node.lookup(kind.name().contains("PAIRED")?"#mcc-details":"#mc-analysis-details");
                                assertEquals(width,pane.getScene().getWidth(),1);assertEquals(width==900?650:820,pane.getScene().getHeight(),1);
                                String prefix=kind.name()+"-"+theme+"-"+width;
                                var image=pane.snapshot(null,null);
                                var bitmap=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                for(int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                                javax.imageio.ImageIO.write(bitmap,"png",ConditionalBenefitMonteCarloHistoryTest.REPORTS.resolve(prefix+".png").toFile());
                                var status=(Label)pane.lookup(view.prefix+"status");var bounds=status.localToScene(status.getBoundsInLocal());
                                assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=pane.getScene().getHeight(),"Current run feedback inside dialog");
                                assertFalse(((Button)pane.lookup(view.prefix+"run")).isDisabled());
                                assertTrue(((Button)pane.lookup(view.prefix+"cancel")).isDisabled());
                                String text=labels(details);
                                assertTrue(text.contains("fully taxable ordinary non-pension"));
                                assertTrue(text.contains("Original conditional survivor benefit"));
                                assertTrue(text.contains("400000"));
                                Files.writeString(ConditionalBenefitMonteCarloHistoryTest.REPORTS.resolve(prefix+"-geometry.txt"),
                                        "Scene "+pane.getScene().getWidth()+" x "+pane.getScene().getHeight()+"\nFixed status "+bounds+"\n"+text);
                                return null;
                            });
                        } finally {fx(()-> {themes.close();return null;});}
                    }
                } finally {fx(()-> {dialog.close();return null;});}
            } finally {fx(()-> {view.close();return null;});stop(source);}
        }
    }
    static String labels(javafx.scene.Node node) {
        String result=node instanceof Label label?label.getText()+"\n":"";
        if(node instanceof javafx.scene.Parent parent) for(var child:parent.getChildrenUnmodifiable()) result+=labels(child);
        return result;
    }
}
