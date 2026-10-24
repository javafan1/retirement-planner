package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.ProjectionReadiness;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummaryService;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummaryService;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;


public class ApplicationController {

    private long sourcePlanRevision;
    private final java.util.List<Runnable> sourcePlanListeners = new java.util.ArrayList<>();
    private final java.util.List<Runnable> analysisBindingListeners = new java.util.ArrayList<>();

    /** File/UUID changes are distinct from financial revisions, including successful first Save and Save As. */
    public Runnable addAnalysisBindingListener(Runnable listener) {
        analysisBindingListeners.add(java.util.Objects.requireNonNull(listener));
        return () -> analysisBindingListeners.remove(listener);
    }
    private void analysisBindingChanged() {
        analysisParameters.bind(currentPlan, currentFile);
        java.util.List.copyOf(analysisBindingListeners).forEach(Runnable::run);
    }

    public long getSourcePlanRevision() {
        return sourcePlanRevision;
    }

    /** UI-thread notification; the returned removal action belongs to the subscriber. */
    public Runnable addSourcePlanRevisionListener(Runnable listener) {
        sourcePlanListeners.add(java.util.Objects.requireNonNull(listener));
        return () -> sourcePlanListeners.remove(listener);
    }

    private void sourcePlanChanged() {
        sourcePlanRevision++;
        java.util.List.copyOf(sourcePlanListeners).forEach(Runnable::run);
    }

    private RetirementPlan currentPlan;
    private ProjectionSummary currentProjectionSummary;
    private Path currentFile;
    private Projection currentProjection;
    private FundingFailure currentFundingFailure;
    private Projection baselineProjection;
    private ProjectionBaseline cachedBaselineIdentity;
    private BreakEvenPlanSummary baselineProjectionAssumptions;
    private BreakEvenPlanSummary currentProjectionAssumptions;
    private List<NonInvestableAssetProjection> currentNonInvestableAssetProjections;
    private RetirementPlan longevitySettingsOwner;
    private com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings longevitySettings;

    public com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings getLongevitySessionSettings() {
        if (longevitySettingsOwner != currentPlan || longevitySettings == null) {
            longevitySettingsOwner = currentPlan;
            longevitySettings = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings
                    .defaults(currentPlan.getPlanningAssumptions().getProjectionStartDate());
        }
        return longevitySettings;
    }

    public void setLongevitySessionSettings(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings settings) {
        longevitySettingsOwner = currentPlan;
        longevitySettings = java.util.Objects.requireNonNull(settings);
    }

    public BreakEvenContext prepareBreakEvenContext(BreakEvenAnalysisResult result) {
        return new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory()
                .create(result, getLongevitySessionSettings());
    }

    private List<NonInvestableAssetProjection>
            baselineNonInvestableAssetProjections;

    private final BaselineProjectionService
            baselineProjectionService;

    private final ProjectionComparisonService
            projectionComparisonService;

    private final ProjectionEngine projectionEngine;
    private final ProjectionSummaryService projectionSummaryService;
    private final RetirementPlanRepository repository;
    private final com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator analysisParameters;

    public com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator getAnalysisParameters() {
        analysisParameters.bind(currentPlan, currentFile);
        return analysisParameters;
    }
    private final com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore analysisResults;
    private java.util.concurrent.CompletionStage<List<String>> snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of());

    public java.util.concurrent.CompletionStage<List<String>> getSnapshotCopyCompletion() { return snapshotCopy; }

    public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore getAnalysisResults() { return analysisResults; }

    public boolean canPersistAnalysisResults() { return currentFile != null && currentPlan.getPlanId() != null; }
    private RetirementPlan analysisSessionOwner;
    private java.util.UUID legacyAnalysisSession;
    public java.util.UUID getAnalysisSessionId() {
        if (currentPlan.getPlanId() != null) return RetirementPlan.validatedPlanId(currentPlan.getPlanId());
        if (analysisSessionOwner != currentPlan) {
            analysisSessionOwner = currentPlan; legacyAnalysisSession = java.util.UUID.randomUUID();
        }
        return legacyAnalysisSession;
    }
    private boolean modified;
    private final java.util.List<String> persistenceWarnings = new java.util.ArrayList<>();
    private Path failedOpenPath;
    private Path recoveredOriginalPath;
    private Path recoveredBackupPath;

    private final JsonApplicationSettingsRepository
            applicationSettingsRepository;

    private final ApplicationSettings
            applicationSettings;

    private final NonInvestableAssetProjectionService
            nonInvestableAssetProjectionService;

    public ApplicationController() {
        this(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository());
    }

    public ApplicationController(RetirementPlanRepository repository,
            JsonApplicationSettingsRepository settingsRepository) {
        this(repository, settingsRepository,
                com.daviddunn.retirementplanner.app.socialsecurity.results.ApplicationAnalysisResults.store());
    }

    public ApplicationController(RetirementPlanRepository repository,
            JsonApplicationSettingsRepository settingsRepository,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore analysisResults) {

        this.analysisResults = java.util.Objects.requireNonNull(analysisResults);

        projectionEngine =
                new ProjectionEngine();

        projectionSummaryService =
                new ProjectionSummaryService(
                        new ProjectionStatisticsService(),
                        new IncomeSummaryService());

        nonInvestableAssetProjectionService =
                new NonInvestableAssetProjectionService();

        baselineProjectionService =
                new BaselineProjectionService(
                        projectionEngine);

        projectionComparisonService =
                new ProjectionComparisonService();

        this.repository = java.util.Objects.requireNonNull(repository);
        analysisParameters = new com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator(
                repository instanceof JsonRetirementPlanRepository json ? json : null);

        applicationSettingsRepository = java.util.Objects.requireNonNull(settingsRepository);

        applicationSettings =
                applicationSettingsRepository.load();
        persistenceWarnings.addAll(applicationSettingsRepository.takeWarnings());

        newPlan();
    }

    public List<NonInvestableAssetProjection>
    getCurrentNonInvestableAssetProjections() {

        if (currentNonInvestableAssetProjections != null) {
            return currentNonInvestableAssetProjections;
        }

        RetirementPlan plan =
                getCurrentPlan();

        Projection projection =
                getCurrentProjection();

        if (plan == null
                || projection == null) {

            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        currentNonInvestableAssetProjections = nonInvestableAssetProjectionService
                .project(
                        plan.getNonInvestableAssets(),
                        firstYear,
                        lastYear);
        return currentNonInvestableAssetProjections;
    }

    public RetirementPlan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * Existing UI can continue using this.
     */
    public Projection getCurrentProjection() {

        if (!isCurrentPlanReadyForProjection()) {
            return null;
        }

        if (currentFundingFailure != null) {
            return null;
        }

        if (currentProjection == null) {

            var outcome = projectionEngine.projectWithOutcome(currentPlan);
            if (outcome instanceof ProjectionExecutionResult.InsufficientFunds insufficient) {
                currentFundingFailure = insufficient.fundingFailure();
                return null; // Never expose a completed-year prefix as a complete projection.
            }
            currentProjection = ((ProjectionExecutionResult.Completed) outcome).projection();
            currentProjectionAssumptions = BreakEvenPlanSummary.from(currentPlan.getHousehold());
        }

        return currentProjection;
    }

    /** Read-only cache lookup; never starts financial work on the JavaFX thread. */
    public Projection peekCurrentProjection() {
        return currentProjection;
    }

    /** Read-only cached failure, cleared with projection invalidation; never starts another projection. */
    public java.util.Optional<FundingFailure> getCurrentFundingFailure() {
        return java.util.Optional.ofNullable(currentFundingFailure);
    }

    public boolean isCurrentPlanReadyForProjection() {

        return ProjectionReadiness.isReady(
                currentPlan);
    }

    public Projection getBaselineProjection() {

        ProjectionBaseline selectedBaseline = currentPlan == null ? null : currentPlan.getBaseline();
        if (cachedBaselineIdentity != selectedBaseline) {
            baselineProjection = null;
            baselineNonInvestableAssetProjections = null;
            baselineProjectionAssumptions = null;
            cachedBaselineIdentity = selectedBaseline;
        }

        if (baselineProjection == null
                && currentPlan != null
                && currentPlan.getBaseline() != null) {

            baselineProjection =
                    baselineProjectionService
                            .projectBaseline(
                                    currentPlan.getBaseline());
            baselineProjectionAssumptions = BreakEvenPlanSummary.from(
                    selectedBaseline.getSnapshot().getHousehold());
        }

        return baselineProjection;
    }

    public List<NonInvestableAssetProjection>
    getBaselineNonInvestableAssetProjections() {

        Projection projection = getBaselineProjection();

        if (baselineNonInvestableAssetProjections != null) {
            return baselineNonInvestableAssetProjections;
        }

        if (projection == null) {
            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        baselineNonInvestableAssetProjections =
                nonInvestableAssetProjectionService
                        .project(
                                currentPlan
                                        .getBaseline()
                                        .getSnapshot()
                                        .getNonInvestableAssets(),
                                firstYear,
                                lastYear);

        return baselineNonInvestableAssetProjections;
    }

    private BreakEvenAnalysisResult insightSourceResult;
    private com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight cachedBreakEvenInsight;

    /** Reads only populated result caches. Opening an analysis never projects a plan. */
    public BreakEvenAnalysisResult getCachedBreakEvenAnalysis() {
        if (currentPlan == null || currentPlan.getBaseline() == null
                || cachedBaselineIdentity != currentPlan.getBaseline()
                || baselineProjection == null || currentProjection == null
                || baselineProjectionAssumptions == null || currentProjectionAssumptions == null
                || baselineNonInvestableAssetProjections == null || currentNonInvestableAssetProjections == null) {
            return null;
        }
        var result = new BreakEvenAnalyzer().analyze(
                new BreakEvenProjectionSnapshot(baselineProjection.getYears(),
                        baselineNonInvestableAssetProjections, baselineProjectionAssumptions),
                new BreakEvenProjectionSnapshot(currentProjection.getYears(),
                        currentNonInvestableAssetProjections, currentProjectionAssumptions));
        insightSourceResult = result;
        cachedBreakEvenInsight = new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService()
                .prepare(result, baselineProjection.getYears(), currentProjection.getYears());
        return result;
    }

    /** Observes the same populated projection caches; never invokes lazy projection getters. */
    public com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight prepareBreakEvenInsight(BreakEvenAnalysisResult result) {
        return result == insightSourceResult ? cachedBreakEvenInsight
                : new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(result);
    }

    public ProjectionComparison compareProjections() {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        if (currentPlan.getBaseline() == null) {
            throw new IllegalStateException(
                    "No baseline projection exists.");
        }

        Projection baseline =
                getBaselineProjection();

        Projection current =
                getCurrentProjection();

        return projectionComparisonService.compare(
                baseline,
                getBaselineNonInvestableAssetProjections(),
                current,
                getCurrentNonInvestableAssetProjections());
    }

    public void saveCurrentAsBaseline(
            String description) {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        ProjectionBaseline baseline =
                ProjectionBaselineFactory.create(
                        currentPlan,
                        description);

        currentPlan.setBaseline(
                baseline);

        baselineProjection = null;
        baselineNonInvestableAssetProjections = null;

        modified = true;
        sourcePlanChanged();
    }

    /**
     * New UI should use this.
     */
    public ProjectionSummary getCurrentProjectionSummary() {

        if (currentProjectionSummary == null) {

            Projection projection =
                    getCurrentProjection();

            if (projection == null) {
                return null;
            }

            currentProjectionSummary =
                    projectionSummaryService.summarize(
                            currentPlan,
                            projection);
        }

        return currentProjectionSummary;
    }


    public BigDecimal getNonInvestableAssetValue(
            int calendarYear) {

        List<NonInvestableAssetProjection> projections =
                getCurrentNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(NonInvestableAssetProjection::getTotalValue)
                .orElse(BigDecimal.ZERO);
    }


    public void invalidateProjection() {
        projectionChanged();
    }

    public RetirementPlan newPlan() {

        return newPlan(RetirementPlanFactory.createSinglePersonPlan());
    }

    /** Activates an isolated, completed new-plan draft using the existing New lifecycle. */
    public RetirementPlan newPlan(RetirementPlan plan) {

        java.util.Objects.requireNonNull(plan, "New plan is required.").validateHouseholdReferences();
        currentPlan = plan;
        sourcePlanChanged();

        currentFile = null;
        analysisParameters.bind(currentPlan, currentFile);
        recoveredOriginalPath = null;
        recoveredBackupPath = null;

        modified = false;

        projectionChanged();

        return currentPlan;
    }

    public RetirementPlan open(
            Path file)
            throws IOException {

        if (repository instanceof JsonRetirementPlanRepository json) {
            var original = json.originalForBackup(file);
            if (original.isPresent()) {
                openRecoveredPlan(original.orElseThrow());
                return currentPlan;
            }
        }

        currentPlan =
                repository.load(file);
        sourcePlanChanged();
//
//        System.out.println(
//                "Primary Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getPrimaryPerson()
//                        .getBirthDate());
//
//        System.out.println(
//                "Spouse Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getSpouse()
//                        .getBirthDate());

        currentFile =
                file;
        analysisParameters.bind(currentPlan, currentFile);
        recoveredOriginalPath = null;
        recoveredBackupPath = null;
        failedOpenPath = null;

        modified = false;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        saveSettingsQuietly();

        projectionChanged();

        return currentPlan;
    }

    public boolean openLastPlan() {

//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        if (!applicationSettings.isAutomaticallyOpenLastPlan()) {
            return false;
        }

        if (!applicationSettings.hasLastOpenedPlan()) {
            return false;
        }
//
//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        try {

            open(Path.of(
                    applicationSettings.getLastOpenedPlan()));

//            System.out.println(
//                    "Primary Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getPrimaryPerson()
//                            .getBirthDate());
//
//            System.out.println(
//                    "Spouse Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getSpouse()
//                            .getBirthDate());

            return true;

        } catch (IOException | RuntimeException ex) {
            try {
                failedOpenPath = Path.of(applicationSettings.getLastOpenedPlan());
            } catch (RuntimeException invalidPath) {
                failedOpenPath = null;
            }
            persistenceWarnings.add("The last plan could not be opened. Its original file was preserved. Use Open to select a plan or review an available backup.");
            return false;
        }
    }

    public void save()
            throws IOException {

        if (currentFile == null) {

            throw new IllegalStateException(
                    "No file selected.");
        }

        repository.save(
                currentPlan,
                currentFile);
        analysisParameters.saved(currentPlan, currentFile);

        modified = false;

        applicationSettings.setLastOpenedPlan(
                currentFile.toString());

        saveSettingsQuietly();
        collectSaveWarnings();
        analysisBindingChanged();
    }

    public void saveAs(
            Path file)
            throws IOException {

        if (recoveredBackupPath != null
                && recoveredBackupPath.equals(file.toAbsolutePath().normalize())) {
            throw new IOException("Choose a different filename; the recovery backup must be preserved.");
        }

        String originalId = currentPlan.getPlanId();
        Path identitySource = currentFile != null ? currentFile : recoveredOriginalPath;
        boolean fork = identitySource != null && !identitySource.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize());
        java.util.UUID proposed = fork || originalId == null ? java.util.UUID.randomUUID() : RetirementPlan.validatedPlanId(originalId);
        if (repository instanceof JsonRetirementPlanRepository json) {
            json.saveWithIdentity(currentPlan, file, proposed);
        } else {
            // Keep alternate repositories/test seams transactional with respect to active identity too.
            var copy = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(currentPlan);
            copy.assignPlanId(proposed);
            repository.save(copy, file);
            currentPlan.assignPlanId(proposed);
        }

        currentFile =
                file;

        analysisParameters.saved(currentPlan, currentFile);

        modified = false;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        recoveredOriginalPath = null;
        recoveredBackupPath = null;
        saveSettingsQuietly();
        collectSaveWarnings();
        if (fork && originalId != null) {
            try {
                var isolated = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(currentPlan);
                snapshotCopy = analysisResults.cloneSnapshots(RetirementPlan.validatedPlanId(originalId), proposed, file, isolated);
            } catch (RuntimeException failure) {
                var warning = "The new plan was saved, but historical analysis copying could not start.";
                persistenceWarnings.add(warning);
                snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of(warning));
            }
        } else snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of());
        analysisBindingChanged();
    }

    private void saveSettingsQuietly() {
        try {
            applicationSettingsRepository.save(applicationSettings);
            persistenceWarnings.addAll(applicationSettingsRepository.takeWarnings());
        } catch (RuntimeException failure) {
            persistenceWarnings.add("The plan operation succeeded, but application settings could not be saved. The application remains usable.");
        }
    }

    private void collectSaveWarnings() {
        if (repository instanceof JsonRetirementPlanRepository json) {
            persistenceWarnings.addAll(json.takeWarnings());
        }
    }

    public List<String> takePersistenceWarnings() {
        persistenceWarnings.addAll(analysisResults.takeOperationWarnings());
        var result = List.copyOf(persistenceWarnings);
        persistenceWarnings.clear();
        return result;
    }

    public Path getFailedOpenPath() { return failedOpenPath; }
    public Path getRecoveredOriginalPath() { return recoveredOriginalPath; }
    public Path getRecoveredBackupPath() { return recoveredBackupPath; }

    public boolean hasValidBackup(Path original) {
        if (!(repository instanceof JsonRetirementPlanRepository json)) return false;
        try {
            json.loadBackup(original);
            return true;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Explicit UI-approved recovery. Save must use Save As, never the backup source. */
    public void openRecoveredPlan(Path original) throws IOException {
        if (!(repository instanceof JsonRetirementPlanRepository json)) {
            throw new IOException("Backup recovery is unavailable.");
        }
        var recovered = json.loadBackup(original);
        newPlan(recovered);
        recoveredOriginalPath = original.toAbsolutePath().normalize();
        recoveredBackupPath = json.backupPath(original);
        modified = true;
    }

    public boolean hasCurrentFile() {
        return currentFile != null;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    private void projectionChanged() {

        currentProjection = null;
        currentFundingFailure = null;
        currentProjectionSummary = null;
        currentProjectionAssumptions = null;
        currentNonInvestableAssetProjections = null;
    }

    public String getCurrentPlanName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile
                .getFileName()
                .toString();
    }

    public String getCurrentPlanDisplayName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile.getFileName().toString();
    }


    public BigDecimal getBaselineNonInvestableAssetValue(
            int calendarYear) {

        if (currentPlan == null
                || currentPlan.getBaseline() == null) {

            return null;
        }

        List<NonInvestableAssetProjection> projections =
                getBaselineNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(
                        NonInvestableAssetProjection::
                                getTotalValue)
                .orElse(null);
    }


    public void markModified() {

        modified = true;
        sourcePlanChanged();
    }

    public boolean isModified() {

        return modified;
    }



}
