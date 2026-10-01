package com.gregtechceu.gtceu.api.machine.issue;

public interface IIssueProvider {

    default void collectIssues(IssueSink sink) {}
}
