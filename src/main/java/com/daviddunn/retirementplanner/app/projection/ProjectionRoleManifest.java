package com.daviddunn.retirementplanner.app.projection;

import java.util.*;

/** Roles refer to immutable components. No swap operation is supplied. */
public record ProjectionRoleManifest(int version, UUID owner, Reference current, Reference baseline,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        List<AnalyzerAssociation> associations,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        List<Reference> pinnedProjections) {
    public ProjectionRoleManifest(int version, UUID owner, Reference current, Reference baseline) {
        this(version, owner, current, baseline, List.of(), List.of());
    }
    public ProjectionRoleManifest(int version, UUID owner, Reference current, Reference baseline, List<AnalyzerAssociation> associations) {
        this(version, owner, current, baseline, associations, List.of());
    }
    public ProjectionRoleManifest {
        if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported projection role schema.");
        Objects.requireNonNull(owner);
        associations = associations == null ? List.of() : List.copyOf(associations);
        pinnedProjections = pinnedProjections == null ? List.of() : List.copyOf(pinnedProjections);
        if (pinnedProjections.size() > 30 || version == 1 && !pinnedProjections.isEmpty()
                || pinnedProjections.stream().map(Reference::id).distinct().count() != pinnedProjections.size())
            throw new IllegalArgumentException("Invalid historical projection table.");
        if (associations.size() > 30 || version == 1 && !associations.isEmpty()) throw new IllegalArgumentException("Invalid association bounds/version.");
        var references = new HashMap<UUID, Reference>();
        for (var ref : new Reference[]{ current, baseline }) if (ref != null) {
            var previous = references.putIfAbsent(ref.id(), ref);
            if (previous != null && !previous.equals(ref)) throw new IllegalArgumentException("Inconsistent projection role references.");
        }
        for (var ref : pinnedProjections) {
            var previous = references.putIfAbsent(ref.id(), ref);
            if (previous != null && !previous.equals(ref)) throw new IllegalArgumentException("Inconsistent pinned projection reference.");
            if (associations.stream().noneMatch(a -> a.projectionId().equals(ref.id())))
                throw new IllegalArgumentException("Orphan pinned projection reference.");
        }
        var keys = new HashSet<String>();
        for (var association : associations) {
            if (!owner.equals(association.owner()) || !keys.add(association.projectionId()+":"+association.kind())
                    || (current == null || !current.id().equals(association.projectionId()))
                    && (baseline == null || !baseline.id().equals(association.projectionId()))
                    && pinnedProjections.stream().noneMatch(p -> p.id().equals(association.projectionId())))
                throw new IllegalArgumentException("Invalid association ownership or projection role.");
        }
    }
    public ProjectionRoleManifest withRoles(UUID nextOwner, Reference nextCurrent, Reference nextBaseline) {
        var pins = new LinkedHashMap<UUID, Reference>();
        for (var ref : pinnedProjections) pins.put(ref.id(), ref);
        for (var ref : new Reference[]{ current, baseline }) if (ref != null
                && associations.stream().anyMatch(a -> a.projectionId().equals(ref.id()))) pins.put(ref.id(), ref);
        return new ProjectionRoleManifest(associations.isEmpty() ? 1 : 2, nextOwner, nextCurrent, nextBaseline, associations, List.copyOf(pins.values()));
    }
    public record Reference(UUID id, String digest) {
        public Reference {
            Objects.requireNonNull(id);
            if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid component digest.");
        }
    }
}
