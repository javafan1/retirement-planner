package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanOperationFeedbackTest {
    @BeforeAll static void startFx() throws Exception { PlanFileOperationsTest.startFx(); }

    @Test void delayedOwnedModalCloseRequestsDoNotCancelAndPhasesStayStable() throws Exception {
        PlanFileOperationsTest.fx(() -> {
            var owner = owner(1180, 820);
            var feedback = new PlanOperationFeedback(owner, "Saving retirement plan…");
            try {
                assertNull(feedback.dialog());
                assertFalse(feedback.view().isVisible());
                feedback.showSubtle();
                var dialog = feedback.dialog();
                assertSame(owner, dialog.getOwner());
                assertEquals(Modality.WINDOW_MODAL, dialog.getModality());
                assertTrue(dialog.isShowing());
                var close = new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST);
                dialog.fireEvent(close);
                assertTrue(dialog.isShowing());
                assertFalse(feedback.view().lookup("#plan-operation-cancel").isVisible());
                var count = new java.util.concurrent.atomic.AtomicInteger();
                feedback.phase("Preparing projection swap…", count::incrementAndGet);
                ((javafx.scene.control.Button)feedback.view().lookup("#plan-operation-cancel")).fire();
                assertEquals(1, count.get());
                double height = dialog.getHeight();
                feedback.phase("Saving retirement plan before swap…", null);
                assertFalse(feedback.view().lookup("#plan-operation-cancel").isVisible());
                assertEquals(height, dialog.getHeight());
                owner.setX(owner.getX()+20); owner.setY(owner.getY()+30); owner.setWidth(900);
                assertEquals(owner.getX()+(owner.getWidth()-dialog.getWidth())/2, dialog.getX(), 1);
                assertEquals(owner.getY()+(owner.getHeight()-dialog.getHeight())/2, dialog.getY(), 1);
                feedback.close(); feedback.showSubtle();
                assertFalse(dialog.isShowing()); assertNull(dialog.getScene());
                assertFalse(feedback.view().isVisible());
            } finally { feedback.close(); owner.hide(); }
            return null;
        });
    }

    @Test void fastCompletionAndOwnerShutdownCannotLeaveOrResurrectDialog() throws Exception {
        PlanFileOperationsTest.fx(() -> {
            var owner = owner(900,650);
            var fast = new PlanOperationFeedback(owner,"Loading retirement plan…");
            fast.close(); fast.showSubtle(); assertNull(fast.dialog());
            var active = new PlanOperationFeedback(owner,"Loading retirement plan…");
            active.showSubtle(); assertTrue(active.dialog().isShowing());
            owner.hide(); assertFalse(active.dialog().isShowing());
            active.showSubtle(); assertFalse(active.dialog().isShowing());
            return null;
        });
    }

    @Test void modalAllowsFxCompletionAndNestedLoopExitIncludingFailure() throws Exception {
        for (boolean fail : new boolean[]{false,true}) {
            PlanFileOperationsTest.fx(() -> {
                var owner = owner(900,650);
                var feedback = new PlanOperationFeedback(owner,"Saving retirement plan…");
                var loop = new Object();
                var completed = new CompletableFuture<String>();
                completed.whenComplete((value,error) -> Platform.runLater(() -> {
                    assertEquals(fail, error != null);
                    assertTrue(feedback.dialog().isShowing());
                    feedback.close(); Platform.exitNestedEventLoop(loop, "completed");
                }));
                Platform.runLater(() -> {
                    feedback.showSubtle();
                    if (fail) completed.completeExceptionally(new java.io.IOException("Injected failure"));
                    else completed.complete("saved");
                });
                try {
                    assertEquals("completed", Platform.enterNestedEventLoop(loop));
                    assertFalse(feedback.dialog().isShowing());
                } finally { feedback.close(); owner.hide(); }
                return null;
            });
        }
    }

    @Test void actualDelayThenCompletionClosesDialog() throws Exception {
        var feedback = new PlanOperationFeedback[1]; var owner = new Stage[1];
        PlanFileOperationsTest.fx(() -> {
            owner[0] = owner(900,650);
            feedback[0] = new PlanOperationFeedback(owner[0],"Loading retirement plan…");
            assertNull(feedback[0].dialog()); return null;
        });
        try {
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            boolean visible = false;
            while (!visible && System.nanoTime()<deadline) {
                Thread.sleep(30);
                visible = PlanFileOperationsTest.fx(() -> feedback[0].dialog()!=null && feedback[0].dialog().isShowing());
            }
            assertTrue(visible);
        } finally {
            PlanFileOperationsTest.fx(() -> { feedback[0].close(); owner[0].hide(); return null; });
        }
    }

    @Test void feedbackPreviewsInBothThemesAndWindowSizes() throws Exception {
        PlanFileOperationsTest.fx(() -> {
            var output = Path.of("target/phase13e/previews"); Files.createDirectories(output);
            var themes = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            try {
                for (var theme : ApplicationTheme.values()) for (int width : new int[]{1180,900}) {
                    themes.apply(theme);
                    var owner = owner(width,width==900?650:820);
                    var feedback = new PlanOperationFeedback(owner,"Saving retirement plan…");
                    try {
                        feedback.showSubtle(); feedback.view().applyCss(); feedback.view().layout();
                        var label = (Label)feedback.view().getChildren().get(1);
                        assertEquals(javafx.scene.paint.Color.web(theme==ApplicationTheme.DARK?"#edf1f7":"#17213d"), label.getTextFill());
                        assertTrue(label.getHeight()>=label.prefHeight(label.getWidth()));
                        var snapshot = feedback.view().snapshot(null,null);
                        var image = new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                        javax.imageio.ImageIO.write(image,"png",output.resolve("progress-"+theme+"-"+width+".png").toFile());
                        themes.apply(theme==ApplicationTheme.DARK?ApplicationTheme.LIGHT:ApplicationTheme.DARK);
                        feedback.view().applyCss(); assertNotEquals(label.getTextFill(), javafx.scene.paint.Color.TRANSPARENT);
                    } finally { feedback.close(); owner.hide(); }
                }
            } finally { themes.close(); }
            return null;
        });
    }

    private static Stage owner(int width,int height) {
        var stage = new Stage(); stage.setScene(new Scene(new StackPane(new Label("Aledrix Retirement Planning Science")),width,height)); stage.show(); return stage;
    }
}
