package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.ui.benefits.ConditionalBenefitPresentation;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Detached quoted-income draft. Does not calculate annuity prices, payments or taxes. */
public final class NonqualifiedAnnuityDialog extends Dialog<ConditionalBenefitDefinition> {
    private final VBox form = new VBox(10);
    private final TextField name = new TextField(), gross = new TextField(), excluded = new TextField(), original = new TextField(), remaining = new TextField();
    private final DatePicker purchase = date(), taxStart = date(), cashStart = date();
    private final ComboBox<PersonReference> annuitant = new ComboBox<>();
    private final CheckBox qualified = new CheckBox("I confirm the documented contract, payment split and supported NIIT assumptions below.");
    private final Label error = text("");
    private final List<RequiredFieldSupport.Attachment> markers = new ArrayList<>();
    private ConditionalBenefitDefinition accepted;

    public NonqualifiedAnnuityDialog(RetirementPlan plan, ConditionalBenefitDefinition existing, boolean duplicate) {
        // A legacy plan gains the original Phase D guarded semantics when an annuity is added.
        // Disclose that prospective policy even before its first annuity has been committed.
        boolean niitMode = plan.getFinancialCalculationMode() != com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC;
        var identity = existing == null || duplicate ? UUID.randomUUID() : existing.id();
        setTitle(existing == null || duplicate ? "Add Nonqualified Income Annuity" : "Edit Nonqualified Income Annuity");
        setResizable(true);
        getDialogPane().setId("nonqualified-annuity-dialog");
        getDialogPane().getButtonTypes().addAll(ConditionalBenefitDialog.SAVE, ButtonType.CANCEL);
        form.setPadding(new Insets(12));
        form.getChildren().add(text("Nonqualified Income Annuity — After-Tax Purchase. Already funded single-life quotation; no liquid contract asset or future purchase is modeled."));
        field("Description",name,"name","A descriptive label. Renaming preserves normalized financial identity.");
        annuitant.getItems().add(PersonReference.PRIMARY);
        if(plan.getHousehold().hasSpouse()) annuitant.getItems().add(PersonReference.SPOUSE);
        annuitant.setConverter(new StringConverter<>() {
            public String toString(PersonReference value) { return value == null ? "" : ConditionalBenefitPresentation.person(value,plan.getHousehold()); }
            public PersonReference fromString(String value) { throw new UnsupportedOperationException(); }
        });
        annuitant.setValue(PersonReference.PRIMARY);
        field("Annuitant and payment recipient",annuitant,"annuitant","Payments stop when this person dies. No survivor, refund or guaranteed period is supported.");
        field("Gross monthly payment ($)",gross,"gross","Insurer-quoted fixed full monthly payment. Exact cents; decimal input without commas or currency symbols.");
        field("First cash payment date",cashStart,"cash-start","First day of a month; independent of purchase and federal tax annuity starting dates. No fractional first payment.");
        field("Completed purchase date",purchase,"purchase","The purchase must be completed before projection opening. Do not also list the purchase premium as a liquid contract asset.");
        field("Documented tax annuity starting date",taxStart,"tax-start","Verify with insurer or tax documentation; this can differ from the first cash payment date. Only post-1986 General Rule contracts.");
        form.getChildren().add(text("Federal method: documented General Rule fixed excluded dollars per full payment. Aledrix does not use the Simplified Method or determine an exclusion ratio."));
        field("Validated excluded amount per monthly payment ($)",excluded,"excluded","Documented nontaxable recovery, at most the gross payment. Recovery ends when remaining investment is exhausted.");
        field("Original adjusted investment at tax annuity start ($)",original,"original","Use documented adjusted investment, which may differ from the purchase premium.");
        field("Remaining recoverable investment at projection opening ($)",remaining,"remaining","Verified unrecovered investment after prior payments. Do not automatically enter the original premium.");
        form.getChildren().add(text("Investment as-of / projection opening: "+plan.getPlanningAssumptions().getProjectionStartDate()+". Opening must be the first day of a month. Changing it requires re-verifying remaining investment."));
        form.getChildren().add(text("Qualification: personal after-tax fixed single-life commercial annuity; annuitant receives payments, age 59½ or later; no variable, joint, refund or period-certain feature; no separately represented contract asset. Insurer/tax documentation validates the split and opening investment. Remaining investment at death is not automatically refunded or deducted."));
        if (niitMode) form.getChildren().add(text("NIIT: domestic individual standard-deduction planning subset, no foreign/CFC/PFIC MAGI adjustments, special elections, investment deductions or loss carryovers. Qualified IRA/Roth distributions can increase MAGI without becoming investment income. Generic pensions, other taxable conditional income and positive brokerage assets are unsupported. Annuity-enabled execution rejects unclassified growth on cash accounts or retained household assets; qualified-account market growth remains supported. Inputs are not automatically changed. Future earnings are not tax-excluded basis recovery."));
        else form.getChildren().add(text("Basic: NIIT Not Modeled. Projected federal taxes omit NIIT. Annuity basis, state tax and contract restrictions still apply; existing account growth conventions are unchanged."));
        if (plan.getFinancialCalculationMode() == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC) qualified.setText("I confirm the documented annuity contract and payment/basis assumptions.");
        qualified.setWrapText(true); field("Required qualification acknowledgment",qualified,"qualification","These are your verified planning assumptions, not a legal determination by Aledrix.");
        form.getChildren().add(text("Gross payment funds household expenses and taxes once; only actual surplus becomes retained household nonqualified assets. Federal NIIT is separate from Medicare premiums."));
        try { NonqualifiedAnnuityAdmission.requireState(plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()); }
        catch(UnsupportedOperationException unsupported) { form.getChildren().add(text("Definition may be saved, but execution is unsupported: "+unsupported.getMessage())); }
        form.getChildren().add(RequiredFieldSupport.createLegend());
        var scroll = new ScrollPane(form); scroll.setId("annuity-form-scroll"); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setPrefViewportHeight(420); scroll.setMinHeight(100);
        error.getStyleClass().add("wizard-field-error"); error.setId("annuity-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty()); error.visibleProperty().bind(error.managedProperty());
        var body=new BorderPane(scroll); body.setTop(error); getDialogPane().setContent(body); getDialogPane().setPrefWidth(720);
        if(existing != null) {
            name.setText(duplicate ? existing.label()+" (copy)" : existing.label()); gross.setText(existing.grossAmount().toPlainString());
            annuitant.setValue(existing.recipient()); cashStart.setValue(existing.trigger().date());
            var terms=Objects.requireNonNull(existing.annuityTerms()); purchase.setValue(terms.acquisitionDate()); taxStart.setValue(terms.taxAnnuityStartingDate());
            excluded.setText(terms.excludedPerPayment().toPlainString()); original.setText(terms.originalInvestment().toPlainString()); remaining.setText(terms.openingInvestment().toPlainString());
            boolean sameOpening=terms.basisAsOf().equals(plan.getPlanningAssumptions().getProjectionStartDate());
            qualified.setSelected(!duplicate && sameOpening && (!niitMode
                    || terms.qualification() == NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1));
            if(!sameOpening) form.getChildren().add(text("Projection opening changed. Previous remaining investment was verified as of "
                    +terms.basisAsOf()+". Reverify the amount for the new opening and confirm qualification; it has not been rolled forward automatically."));
        }
        for(var field:List.of(gross,excluded,original,remaining)) field.textProperty().addListener((o,old,value)->qualified.setSelected(false));
        annuitant.valueProperty().addListener((o,old,value)->qualified.setSelected(false));
        for(var picker:List.of(purchase,taxStart,cashStart)) {
            picker.valueProperty().addListener((o,old,value)->qualified.setSelected(false));
            picker.getEditor().textProperty().addListener((o,old,value)->qualified.setSelected(false));
        }
        getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE).addEventFilter(ActionEvent.ACTION,event->{
            try {
                if(!qualified.isSelected()) throw new IllegalArgumentException(niitMode
                        ? "Confirm the documented annuity and bounded NIIT assumptions."
                        : "Confirm the documented annuity contract, payment split and remaining investment.");
                var terms=new NonqualifiedAnnuityTerms(read(purchase),read(taxStart),number(excluded),number(original),number(remaining),
                        plan.getPlanningAssumptions().getProjectionStartDate(),
                        plan.getFinancialCalculationMode() == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC
                            ? NonqualifiedAnnuityTerms.Qualification.PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2
                            : NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1);
                accepted=new ConditionalBenefitDefinition(identity,name.getText().strip(),Category.NONQUALIFIED_INCOME_ANNUITY,
                        new Trigger(TriggerType.FIXED_DATE,read(cashStart),null,null),Objects.requireNonNull(annuitant.getValue()),
                        PaymentPattern.MONTHLY_RECURRING,number(gross),null,null,null,null,BigDecimal.ZERO,EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                        TaxCharacter.NONQUALIFIED_GENERAL_RULE_FIXED_EXCLUSION,ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH,null,terms);
                accepted.validateFor(plan.getHousehold()); error.setText("");
            } catch(RuntimeException invalid) { accepted=null; error.setText(invalid.getMessage()==null?"Review all required fields.":invalid.getMessage()); event.consume(); }
        });
        setResultConverter(button->button==ConditionalBenefitDialog.SAVE?accepted:null);
        setOnShown(event->Platform.runLater(name::requestFocus));
        setOnHidden(event->markers.forEach(RequiredFieldSupport.Attachment::close));
    }
    private void field(String title,Control control,String id,String help) {
        var label=text(title); control.setId("annuity-"+id); control.setAccessibleText(title); control.setMaxWidth(Double.MAX_VALUE);
        InputHelp.install(control,help); InputHelp.link(label,control); markers.add(RequiredFieldSupport.attach(label,control));
        form.getChildren().add(new VBox(4,label,control));
    }
    private static Label text(String value) { var label=new Label(value); label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE); return label; }
    private static BigDecimal number(TextField field) {
        if(field.getText().isBlank()) throw new IllegalArgumentException(field.getAccessibleText()+" is required.");
        try { return new BigDecimal(field.getText().strip()); }
        catch(NumberFormatException invalid) { throw new IllegalArgumentException("Enter a valid decimal amount for "+field.getAccessibleText()+"."); }
    }
    private static DatePicker date() {
        var picker=new DatePicker(); picker.setConverter(new StringConverter<>() {
            public String toString(LocalDate date) { return date==null?"":date.toString(); }
            public LocalDate fromString(String text) { return text==null||text.isBlank()?null:LocalDate.parse(text.strip()); }
        }); return picker;
    }
    private static LocalDate read(DatePicker picker) {
        if(picker.getEditor().getText().isBlank()) throw new IllegalArgumentException(picker.getAccessibleText()+" is required.");
        try { return LocalDate.parse(picker.getEditor().getText().strip()); }
        catch(java.time.format.DateTimeParseException invalid) { throw new IllegalArgumentException("Enter a valid yyyy-MM-dd date for "+picker.getAccessibleText()+"."); }
    }
}
