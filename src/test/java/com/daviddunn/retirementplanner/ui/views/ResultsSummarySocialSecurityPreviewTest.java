package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ResultsSummarySocialSecurityPreviewTest {
    private static final String[] PRIMARY = {"2800.00","3000.00","3200.00","3466.67","3733.33","4000.00","4320.00","4640.00","4960.00"};
    private static final String[] SPOUSE = {"1400.00","1500.00","1600.00","1733.33","1866.67","2000.00","2160.00","2320.00","2480.00"};
    private static final String[] PRIMARY_TEXT = {"$2,800","$3,000","$3,200","$3,467","$3,733","$4,000","$4,320","$4,640","$4,960"};
    private static final String[] SPOUSE_TEXT = {"$1,400","$1,500","$1,600","$1,733","$1,867","$2,000","$2,160","$2,320","$2,480"};
    @TempDir Path directory;
    private final List<ApplicationController> controllers = new ArrayList<>();

    @BeforeAll static void startFx() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException started) { Platform.runLater(start); }
        ready.get(20,TimeUnit.SECONDS);
    }
    private static <T>T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(30,TimeUnit.SECONDS);
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void field(Object object,String name,Object value) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private ApplicationController controller() throws Exception {
        var c=new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings-"+controllers.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("history-"+controllers.size()))));
        controllers.add(c); return c;
    }
    @AfterEach void stop() throws Exception {
        for(var c:controllers) {
            c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
            c.getPlanPreferenceWrite().toCompletableFuture().get(20,TimeUnit.SECONDS);
            c.getAnalysisParameters().drain(Duration.ofSeconds(10)); c.getAnalysisParameters().shutdown();
            c.getAnalysisResults().drain(Duration.ofSeconds(10)); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
            c.getSettingsService().close();
        }
    }
    private static RetirementPlan plan(boolean couple,int age,String cola,int valuationYear) {
        var primary=new Person("Primary","Synthetic",LocalDate.of(1963,6,4));
        primary.addIncomeSource(new SocialSecurityIncome("SS",AccountOwnership.PRIMARY,primary.getBirthDate().plusYears(age),null,new BigDecimal("4000.00"),age,new BigDecimal("0.99"),valuationYear));
        var spouse=couple?new Person("Spouse","Synthetic",LocalDate.of(1965,2,28)):null;
        if(spouse!=null) spouse.addIncomeSource(new SocialSecurityIncome("SS",AccountOwnership.SPOUSE,spouse.getBirthDate().plusYears(62),null,new BigDecimal("2000.00"),62,new BigDecimal("0.99"),valuationYear+1));
        var portfolio=new AccountPortfolio(); portfolio.addAccount(new BrokerageAccount("Opening",AccountOwnership.PRIMARY,new BigDecimal("1000000.00")));
        var assumptions=new PlanningAssumptions(new EconomicAssumptions(new BigDecimal("0.03"),new BigDecimal("0.025"),new BigDecimal("0.025"),new BigDecimal(cola)),null,null,null,null,null,12,LocalDate.of(2026,1,1),null);
        return new RetirementPlan(new Household(primary,spouse),portfolio,assumptions);
    }
    private static ResultsSummaryView view(ApplicationController c) {
        var view=new ResultsSummaryView(c);
        view.load(c.getCurrentPlan(),c.peekCurrentProjection(),c.getCurrentNonInvestableAssetProjections());
        return view;
    }
    @SuppressWarnings("unchecked") private static void checkRows(ResultsSummaryView view,int year,boolean couple) throws Exception {
        var rows=(List<?>)field(view,"socialSecurityRows"); assertEquals(couple?2:1,rows.size());
        for(int r=0;r<rows.size();r++) {
            var row=rows.get(r);var combo=(ComboBox<Integer>)field(row,"claimingAgeCombo");var label=(Label)field(row,"benefitLabel");
            var person=(Person)field(row,"person"); var source=(SocialSecurityIncome)field(row,"source");
            var exact=r==0?PRIMARY:SPOUSE; var text=r==0?PRIMARY_TEXT:SPOUSE_TEXT;
            assertEquals(text[combo.getValue()-62],label.getText(),"Initial selection must use the same basis");
            for(int age:new int[]{70,69,70,62,63,64,65,66,67,68,69,70}) {
                combo.setValue(age);
                // Constants are independently tabulated FRA-67 election amounts, not computed by the UI.
                assertEquals(new BigDecimal(exact[age-62]),SocialSecurityBenefitCalculator.calculateMonthlyBenefit(source.getFullRetirementMonthlyBenefit(),person.getBirthDate(),age).setScale(2,RoundingMode.HALF_UP));
                assertEquals(text[age-62],label.getText(),"person="+r+" age="+age);
                assertTrue(label.getTooltip().getText().contains((year+r)+" dollars"));
                assertTrue(label.getTooltip().getText().contains("COLA"));
                assertFalse(label.getTooltip().getText().contains("Original accepted income summary"));
                assertTrue(label.getAccessibleText().contains("age "+age));
                assertTrue(label.getAccessibleText().contains((year+r)+" dollars"));
            }
            var grid=(GridPane)combo.getParent(); int displayYear=year+r;
            assertTrue(grid.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast).anyMatch(l->l.getText().contains(displayYear+" dollars")));
        }
    }
    private static void rejectProjection(ApplicationController c) throws Exception {
        field(c,"projectionEngine",new ProjectionEngine() {
            @Override public Projection project(RetirementPlan p) {throw new AssertionError("Preview cannot project");}
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) {throw new AssertionError("Preview cannot project");}
        });
    }

    @ParameterizedTest @ValueSource(strings={"0","0.025","0.10"})
    void allAgesUseFixedYearWithoutChangingAcceptedFinancialValues(String cola) throws Exception {
        var c=controller(); c.newPlan(plan(true,70,cola,2026)); c.getCurrentProjection();
        var accepted=c.getAcceptedProjectionSnapshot();var annual=accepted.annual();var inputs=CompleteProjectionSnapshot.freeze(c.getCurrentPlan());
        var summary=c.getCurrentProjectionSummary();boolean dirty=c.isModified();long revision=c.getSourcePlanRevision();
        rejectProjection(c);
        fx(()->{checkRows(view(c),2026,true);return null;});
        assertEquals(inputs,CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));assertSame(accepted,c.getAcceptedProjectionSnapshot());
        assertEquals(annual,c.getAcceptedProjectionSnapshot().annual());assertSame(summary,c.getCurrentProjectionSummary());
        assertEquals(dirty,c.isModified());assertEquals(revision,c.getSourcePlanRevision());
        var year=c.peekCurrentProjection().getYears().stream().filter(y->y.getCalendarYear()==2034).findFirst().orElseThrow();
        var expected=new BigDecimal("4960.00").multiply(BigDecimal.ONE.add(new BigDecimal(cola)).pow(8)).setScale(2,RoundingMode.HALF_UP).multiply(new BigDecimal("12"));
        assertEquals(0,expected.compareTo(year.getSocialSecurityResult().primaryOwnBenefit()));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void saveReloadAndPlanSwitchPreserveDisplayAndOriginalResults(boolean couple) throws Exception {
        var c=controller();c.newPlan(plan(couple,70,"0.025",2026));c.getCurrentProjection();
        var a=directory.resolve("a.json");c.saveAs(a);var identity=c.getAcceptedProjectionSnapshot().id();var original=c.getAcceptedProjectionSnapshot().annual();
        c.newPlan(plan(couple,69,"0.10",2020));c.getCurrentProjection();var b=directory.resolve("b.json");c.saveAs(b);
        rejectProjection(c);
        for(var file:List.of(a,b,a)) {
            c.open(file);c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
            assertTrue(c.isRestoredProjectionMode());assertTrue(c.currentProjectionInputsMatch());
            boolean dirty=c.isModified();String inputs=CompleteProjectionSnapshot.freeze(c.getCurrentPlan());
            fx(()->{checkRows(view(c),file.equals(a)?2026:2020,couple);return null;});
            assertEquals(inputs,CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));assertEquals(dirty,c.isModified());
            if(file.equals(a)){assertEquals(identity,c.getAcceptedProjectionSnapshot().id());assertEquals(original,c.getAcceptedProjectionSnapshot().annual());}
        }
        c.save();c.open(a);c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
        fx(()->{checkRows(view(c),2026,couple);return null;});
        assertEquals(identity,c.getAcceptedProjectionSnapshot().id());assertEquals(original,c.getAcceptedProjectionSnapshot().annual());
    }

    @Test void sourceRowDoesNotUseAnAggregatedRestoredIncomeSummary() throws Exception {
        var c=controller();var p=plan(true,70,"0.025",2026);var primary=p.getHousehold().getPrimaryPerson();
        primary.addIncomeSource(new SocialSecurityIncome("Additional legacy SS",AccountOwnership.PRIMARY,LocalDate.of(2033,6,4),null,new BigDecimal("1000.00"),70,BigDecimal.ZERO,2026));
        c.newPlan(p);c.getCurrentProjection();c.saveAs(directory.resolve("multiple.json"));c.open(directory.resolve("multiple.json"));c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
        assertEquals(new BigDecimal("6200.00"),c.getCurrentProjectionSummary().getIncomeSummary().getPrimarySocialSecurityMonthly());
        fx(()->{checkRows(view(c),2026,true);return null;});
    }

    @Test void actualSummaryControlsRenderInBothThemesAtSupportedSizes() throws Exception {
        var c=controller();c.newPlan(plan(true,70,"0.025",2026));c.getCurrentProjection();rejectProjection(c);
        fx(()->{
            var themes=ApplicationThemeManager.start(ApplicationTheme.LIGHT);var output=Path.of("target/phase13f-implementation/previews");Files.createDirectories(output);
            try {
                for(var theme:ApplicationTheme.values())for(int width:new int[]{1180,900}) {
                    themes.apply(theme);var view=view(c);var stage=new Stage();stage.setScene(new Scene(view,width,width==900?650:820));stage.show();
                    try {
                        view.applyCss();view.layout();checkRows(view,2026,true);
                        var section=((VBox)field(view,"socialSecurityContainer")).getParent();
                        section.applyCss();section.layout();
                        saveImage(section,output.resolve("social-security-"+theme+"-"+width+".png"));
                        saveImage(view,output.resolve("summary-"+theme+"-"+width+".png"));
                    } finally {stage.hide();}
                }
            } finally {themes.close();}return null;
        });
    }
    private static void saveImage(Node node,Path path)throws Exception {
        var snapshot=node.snapshot(null,null);var image=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(image,"png",path.toFile());
    }
}
