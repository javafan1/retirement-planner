package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import javafx.application.Platform;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ConditionalBenefitUiTestSupport {
    private ConditionalBenefitUiTestSupport() { }

    public static void startFx() throws Exception {
        var started = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.complete(null); }); }
        catch (IllegalStateException running) { Platform.runLater(() -> { Platform.setImplicitExit(false); started.complete(null); }); }
        started.get(20, TimeUnit.SECONDS);
    }

    public static <T> T fx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    public static RetirementPlan plan(boolean couple) {
        var plan = BenefitFixtures.plan(couple);
        plan.getHousehold().getExpenses().stream().toList().forEach(plan.getHousehold()::removeExpense);
        for (var owner : plan.getHousehold().peopleByOwner().entrySet()) {
            var person = owner.getValue();
            person.getIncomeSources().stream().toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new SocialSecurityIncome("Synthetic zero Social Security", owner.getKey(),
                    person.getBirthDate().plusYears(67), null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2030));
        }
        plan.getAccountPortfolio().getAccounts().stream().toList().forEach(plan.getAccountPortfolio()::removeAccount);
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Synthetic Brokerage", AccountOwnership.PRIMARY, new BigDecimal("500000")));
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(
                new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                old.getTaxAssumptions(), old.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(couple ? DeathScenario.PRIMARY_DIES : DeathScenario.BOTH_SURVIVE, 2031, 67),
                5, LocalDate.of(2030, 1, 1), StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        return plan;
    }

    @SuppressWarnings("unchecked")
    public static <T> ComboBox<T> choice(ConditionalBenefitDialog dialog, String id) {
        return (ComboBox<T>) dialog.getDialogPane().lookup("#cb-" + id);
    }

    public static TextField input(ConditionalBenefitDialog dialog, String id) {
        return (TextField) dialog.getDialogPane().lookup("#cb-" + id);
    }

    public static DatePicker date(ConditionalBenefitDialog dialog, String id) {
        return (DatePicker) dialog.getDialogPane().lookup("#cb-" + id);
    }

    public static void configure(ConditionalBenefitDialog dialog, TriggerType trigger, PaymentPattern pattern) {
        input(dialog, "name").setText("Synthetic " + (pattern == PaymentPattern.MONTHLY_RECURRING ? "Monthly Income" : "Lump Sum"));
        choice(dialog, "category").setValue(Category.CONDITIONAL_INCOME);
        choice(dialog, "pattern").setValue(pattern);
        input(dialog, "amount").setText(pattern == PaymentPattern.MONTHLY_RECURRING ? "2500" : "400000");
        choice(dialog, "trigger").setValue(trigger);
        date(dialog, "trigger-date").setValue(LocalDate.of(2030, 1, 1));
        choice(dialog, "trigger-person").setValue(PersonReference.PRIMARY);
        input(dialog, "age").setText("70");
        choice(dialog, "recipient").setValue(trigger == TriggerType.DEATH ? PersonReference.SPOUSE : PersonReference.PRIMARY);
        input(dialog, "escalation").setText("0");
    }

    public static ConditionalBenefitDefinition create(RetirementPlan plan, TriggerType trigger, PaymentPattern pattern) throws Exception {
        return fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan, null);
            dialog.show();
            try {
                configure(dialog, trigger, pattern);
                ((Button) dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertFalse(dialog.isShowing(), "Valid draft must save");
                assertNotNull(dialog.getResult());
                return dialog.getResult();
            } finally { dialog.close(); }
        });
    }
}
