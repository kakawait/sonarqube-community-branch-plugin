package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.filter;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;
import org.sonar.api.rule.Severity;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SeverityExclusionFilter implements Predicate<PostAnalysisIssueVisitor.ComponentIssue> {
    private final List<String> exclusions;

    public SeverityExclusionFilter(String severityString) {
        this.exclusions = parseString(severityString);
    }

    private List<String> parseString(String severityString) {
        List<String> severityStringList = Arrays.asList(severityString.split(","));
        return severityStringList
                .stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(Severity.ALL::contains)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean test(PostAnalysisIssueVisitor.ComponentIssue componentIssue) {
        return !exclusions.contains(componentIssue.getIssue().severity());
    }
}
