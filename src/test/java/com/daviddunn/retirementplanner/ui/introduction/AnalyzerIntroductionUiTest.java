package com.daviddunn.retirementplanner.ui.introduction;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.ui.settings.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerIntroductionUiTest {
    @TempDir Path directory;
    @BeforeAll static void start() throws Exception {
        var ready=new CompletableFuture<Void>();Runnable action=()->{Platform.setImplicitExit(false);ready.complete(null);};
        try{Platform.startup(action);}catch(IllegalStateException started){Platform.runLater(action);}ready.get(20,TimeUnit.SECONDS);
    }
    static <T>T fx(Callable<T> work)throws Exception{var task=new FutureTask<>(work);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static Stage owner(int width,int height){var stage=new Stage();stage.setScene(new Scene(new VBox(new Label("Aledrix")),width,height));stage.show();return stage;}
    static DialogPane pane(Window owner){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==owner)
            .map(w->w.getScene().getRoot().lookup(".dialog-pane")).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();}
    static void continued(DialogPane pane){((Button)pane.lookupButton(AnalyzerIntroductionDialog.CONTINUE)).fire();}
    static void canceled(DialogPane pane){((Button)pane.lookupButton(ButtonType.CANCEL)).fire();}
    static void await(java.util.concurrent.Callable<Boolean> condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.call()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertTrue(condition.call());
    }
    static String text(Node node){var value=node instanceof Labeled l?l.getText()+"\n":"";if(node instanceof Parent p)for(var child:p.getChildrenUnmodifiable())value+=text(child);return value;}

    @Test void educationalCopyUsesExactTitlesAndHouseholdSpecificScope() {
        var titles=Map.of(
                AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,"Social Security Only",
                AnalyzerIntroduction.SOCIAL_SECURITY_QUICK,"Deterministic Quick Comparison",
                AnalyzerIntroduction.SOCIAL_SECURITY_EXHAUSTIVE,"Deterministic Exhaustive Search",
                AnalyzerIntroduction.SOCIAL_SECURITY_WEIGHTED,"Longevity-Weighted Search",
                AnalyzerIntroduction.MONTE_CARLO_COMPARISON,"Monte Carlo Strategy Comparison",
                AnalyzerIntroduction.MONTE_CARLO_ANALYSIS,"Monte Carlo Retirement Analysis");
        for(var entry:AnalyzerIntroduction.values())assertEquals(titles.get(entry),AnalyzerIntroductionContent.forEntry(entry,false).title());
        assertEquals("Deterministic Integrated",AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.SOCIAL_SECURITY_EXHAUSTIVE,true).title());
        assertEquals("Longevity-Weighted Integrated",AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.SOCIAL_SECURITY_WEIGHTED,true).title());
        assertTrue(AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,true).limitations().contains("does not model spouse or survivor"));
        assertTrue(AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.SOCIAL_SECURITY_QUICK,false).purpose().contains("compatible Social Security Only result"));
        assertTrue(AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.SOCIAL_SECURITY_WEIGHTED,false).limitations().contains("not a random-market simulation"));
        assertTrue(AnalyzerIntroductionContent.forEntry(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS,false).limitations().contains("not guarantees"));
    }
    @ParameterizedTest @EnumSource(AnalyzerIntroduction.class)
    void allSixFirstUseCancelAndContinueOpenOnlyTheRequestedEntry(AnalyzerIntroduction entry)throws Exception{
        var path=directory.resolve("settings.json");
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))){
            fx(()->{
                var owner=owner(1180,820);var gate=new AnalyzerIntroductionGate(service);var opened=new ArrayList<AnalyzerIntroduction>();
                var finish=new AtomicReference<Runnable>();
                try{
                    gate.request(owner,entry,false,()->true,done->{opened.add(entry);finish.set(done);});
                    var dialog=pane(owner);assertEquals(0,opened.size());assertEquals(Modality.WINDOW_MODAL,((Stage)dialog.getScene().getWindow()).getModality());
                    assertSame(owner,((Stage)dialog.getScene().getWindow()).getOwner());
                    assertTrue(text(dialog).contains(AnalyzerIntroductionContent.DURATION));
                    var suppress=(CheckBox)dialog.lookup("#suppress-analyzer-introduction");assertNotNull(suppress.getAccessibleHelp());suppress.setSelected(true);
                    assertTrue(((Button)dialog.lookupButton(AnalyzerIntroductionDialog.CONTINUE)).isDefaultButton());
                    assertTrue(((Button)dialog.lookupButton(ButtonType.CANCEL)).isCancelButton());
                    suppress.requestFocus();assertSame(suppress,dialog.getScene().getFocusOwner());
                    canceled(dialog);assertTrue(opened.isEmpty());assertTrue(service.snapshot().showsIntroduction(entry));assertFalse(Files.exists(path));
                    gate.request(owner,entry,false,()->true,done->{opened.add(entry);finish.set(done);});
                    var accepted=pane(owner);continued(accepted);assertEquals(List.of(entry),opened);
                    continued(accepted); // A queued duplicate action on a dismissed dialog must do nothing.
                    gate.request(owner,entry,false,()->true,done->{opened.add(entry);done.run();});assertEquals(1,opened.size());
                    finish.get().run();assertTrue(service.snapshot().showsIntroduction(entry));
                }finally{owner.hide();}return null;
            });
        }
    }

    @Test void suppressionPersistsOnlyAfterContinueAndBypassesOnlyThatEntry()throws Exception{
        var path=directory.resolve("settings.json");
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))){
            var opened=new AtomicInteger();var owner=new Stage[1];var gate=new AnalyzerIntroductionGate(service);
            fx(()->{owner[0]=owner(900,650);gate.request(owner[0],AnalyzerIntroduction.MONTE_CARLO_ANALYSIS,false,()->true,done->{opened.incrementAndGet();done.run();});
                var pane=pane(owner[0]);((CheckBox)pane.lookup("#suppress-analyzer-introduction")).setSelected(true);continued(pane);return null;});
            try{
                await(()->opened.get()==1);assertFalse(new JsonApplicationSettingsRepository(path).load().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));
                for(var entry:AnalyzerIntroduction.values())if(entry!=AnalyzerIntroduction.MONTE_CARLO_ANALYSIS)assertTrue(service.snapshot().showsIntroduction(entry));
                fx(()->{gate.request(owner[0],AnalyzerIntroduction.MONTE_CARLO_ANALYSIS,false,()->true,done->{opened.incrementAndGet();done.run();});assertEquals(2,opened.get());return null;});
            }finally{fx(()->{owner[0].hide();return null;});}
        }
        try(var restarted=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))){assertFalse(restarted.snapshot().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));}
    }

    @Test void windowCloseAndInvalidPlanDoNotLaunchOrSave()throws Exception{
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("settings.json")))){
            fx(()->{
                var owner=owner(900,650);var gate=new AnalyzerIntroductionGate(service);var valid=new AtomicBoolean(true);var count=new AtomicInteger();
                try{
                    gate.request(owner,AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,true,valid::get,done->{count.incrementAndGet();done.run();});
                    var pane=pane(owner);((CheckBox)pane.lookup("#suppress-analyzer-introduction")).setSelected(true);
                    pane.getScene().getWindow().fireEvent(new WindowEvent(pane.getScene().getWindow(),WindowEvent.WINDOW_CLOSE_REQUEST));
                    assertEquals(0,count.get());assertTrue(service.snapshot().showsIntroduction(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY));
                    gate.request(owner,AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,true,valid::get,done->{count.incrementAndGet();done.run();});valid.set(false);continued(pane(owner));assertEquals(0,count.get());
                    assertFalse(Files.exists(directory.resolve("settings.json")));
                }finally{owner.hide();}return null;
            });
        }
    }

    @Test void failedSuppressionKeepsDialogAndAllowsRetryWithoutFalseDurability()throws Exception{
        var fail=new AtomicBoolean(true);var saved=new AtomicReference<>(new ApplicationSettings());
        var repository=new ApplicationSettingsRepository(){public ApplicationSettings load(){return saved.get().copy();}public void save(ApplicationSettings next){assertFalse(Platform.isFxApplicationThread());if(fail.get())throw new RuntimeException("Injected failure");saved.set(next.copy());}};
        try(var service=new ApplicationSettingsService(repository)){
            var owner=new Stage[1];var opened=new AtomicInteger();
            fx(()->{owner[0]=owner(900,650);new AnalyzerIntroductionGate(service).request(owner[0],AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,false,()->true,done->{opened.incrementAndGet();done.run();});
                var pane=pane(owner[0]);((CheckBox)pane.lookup("#suppress-analyzer-introduction")).setSelected(true);continued(pane);return null;});
            try{
                await(()->fx(()->((Label)pane(owner[0]).lookup("#analyzer-introduction-notice")).getText().contains("not saved")));
                assertEquals(0,opened.get());assertTrue(service.snapshot().showsIntroduction(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY));fail.set(false);
                fx(()->{continued(pane(owner[0]));return null;});await(()->opened.get()==1);
                assertFalse(service.snapshot().showsIntroduction(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY));
            }finally{fx(()->{owner[0].hide();return null;});}
        }
    }

    @Test void latePreferenceCompletionCannotLaunchIntoReplacedOwnerOrReleaseNewGate()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var settings=new AtomicReference<>(new ApplicationSettings());
        var repository=new ApplicationSettingsRepository(){public ApplicationSettings load(){return settings.get().copy();}public void save(ApplicationSettings next){entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}settings.set(next.copy());}};
        try(var service=new ApplicationSettingsService(repository)){
            var owner=new Stage[1];var opened=new AtomicInteger();var gate=new AnalyzerIntroductionGate(service);
            fx(()->{owner[0]=owner(900,650);gate.request(owner[0],AnalyzerIntroduction.SOCIAL_SECURITY_ONLY,false,()->true,done->{opened.incrementAndGet();done.run();});
                var pane=pane(owner[0]);((CheckBox)pane.lookup("#suppress-analyzer-introduction")).setSelected(true);continued(pane);return null;});
            try{
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                fx(()->{owner[0].hide();owner[0].show();gate.request(owner[0],AnalyzerIntroduction.MONTE_CARLO_ANALYSIS,false,()->true,done->{opened.incrementAndGet();done.run();});return null;});
                release.countDown();service.recordLastPlanAsync("latest.json").toCompletableFuture().get(5,TimeUnit.SECONDS);
                fx(()->{assertEquals(0,opened.get());assertTrue(text(pane(owner[0])).contains("Monte Carlo Retirement Analysis"));continued(pane(owner[0]));assertEquals(1,opened.get());return null;});
            }finally{release.countDown();fx(()->{owner[0].hide();return null;});}
        }
    }

    @Test void settingsResetIsDraftUntilDurableSaveAndPreservesLastPlan()throws Exception{
        var path=directory.resolve("settings.json");
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))){
            for(var entry:AnalyzerIntroduction.values())service.suppressIntroduction(entry).toCompletableFuture().get(5,TimeUnit.SECONDS);
            var manager=new ApplicationThemeManager[1];var owner=new Stage[1];
            var savedDialog=fx(()->{
                manager[0]=ApplicationThemeManager.start(ApplicationTheme.LIGHT);owner[0]=owner(900,650);
                var canceled=new ApplicationSettingsDialog(owner[0],service,manager[0]);canceled.show();((Button)canceled.getDialogPane().lookup("#reset-analyzer-introductions")).fire();
                ((Button)canceled.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();assertEquals(6,service.snapshot().getSuppressedAnalyzerIntroductions().size());
                var dialog=new ApplicationSettingsDialog(owner[0],service,manager[0]);dialog.show();((Button)dialog.getDialogPane().lookup("#reset-analyzer-introductions")).fire();return dialog;
            });
            try{
                service.recordLastPlan("changed-while-settings-open.json");
                fx(()->{((Button)savedDialog.getDialogPane().lookupButton(savedDialog.getDialogPane().getButtonTypes().getFirst())).fire();return null;});await(()->fx(()->!savedDialog.isShowing()));
                assertTrue(new JsonApplicationSettingsRepository(path).load().getSuppressedAnalyzerIntroductions().isEmpty());assertEquals("changed-while-settings-open.json",service.snapshot().getLastOpenedPlan());
            }finally{fx(()->{savedDialog.close();owner[0].hide();manager[0].close();return null;});}
        }
    }

    @Test void actualDialogsAndSettingsWrapInBothThemesAtSupportedOwnerSizes()throws Exception{
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("settings.json")))){
            fx(()->{
                var manager=ApplicationThemeManager.start(ApplicationTheme.LIGHT);var output=Path.of("target/phase13g/previews");Files.createDirectories(output);
                try{
                    for(var theme:ApplicationTheme.values())for(int width:new int[]{1180,900}){
                        manager.apply(theme);var owner=owner(width,width==900?650:820);
                        try{
                            for(var entry:AnalyzerIntroduction.values()){
                                var dialog=new AnalyzerIntroductionDialog(owner,AnalyzerIntroductionContent.forEntry(entry,false),ignored->{});dialog.show();
                                try{
                                    var pane=dialog.getDialogPane();pane.applyCss();pane.layout();
                                    var warning=pane.lookup("#analyzer-introduction-duration");var bounds=warning.localToScene(warning.getBoundsInLocal());
                                    assertTrue(bounds.getMinY()>=0);assertTrue(bounds.getMaxY()<=pane.getScene().getHeight(),"Warning must be visible without scrolling");
                                    assertTrue(((Label)warning).getHeight()+1>=((Label)warning).prefHeight(((Label)warning).getWidth()));
                                    assertTrue(((Label)warning).getTextFill().isOpaque());
                                    save(pane,output.resolve(entry+"-"+theme+"-"+width+".png"));
                                }finally{dialog.close();}
                            }
                            var settingsDialog=new ApplicationSettingsDialog(owner,service,manager);settingsDialog.show();
                            try{assertFalse(settingsDialog.getDialogPane().lookup("#reset-analyzer-introductions").isDisabled());((ComboBox<ApplicationTheme>)settingsDialog.getDialogPane().lookup(".combo-box")).setValue(theme);settingsDialog.getDialogPane().applyCss();settingsDialog.getDialogPane().layout();save(settingsDialog.getDialogPane(),output.resolve("settings-"+theme+"-"+width+".png"));}
                            finally{settingsDialog.close();}
                        }finally{owner.hide();}
                    }
                }finally{manager.close();}return null;
            });
        }
    }
    static void save(Node node,Path file)throws Exception{
        var snapshot=node.snapshot(null,null);var image=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(image,"png",file.toFile());
    }
}
