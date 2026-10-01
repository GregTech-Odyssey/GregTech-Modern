package com.gregtechceu.gtceu.api.machine.issue;

@FunctionalInterface
public interface IssueSink {

    void accept(MachineIssue issue);

    default void accept(IssueType type) {
        accept(type.bare());
    }

    default void accept(IssueType type, long a, long b) {
        accept(MachineIssue.of(type, a, b));
    }
}
