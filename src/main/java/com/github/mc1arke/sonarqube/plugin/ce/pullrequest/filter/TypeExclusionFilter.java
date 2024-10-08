package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.filter;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;
import org.sonar.api.rules.RuleType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TypeExclusionFilter implements Predicate<PostAnalysisIssueVisitor.ComponentIssue> {
    private final List<RuleType> exclusions;

    public TypeExclusionFilter(String typeExclusionString) {
        this.exclusions = parseString(typeExclusionString);
    }

    private List<RuleType> parseString(String typeExclusionString) {
        List<String> typeExclusionStringList = Arrays.asList(typeExclusionString.split(","));
        return typeExclusionStringList
                .stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(string -> RuleType.names().contains(string))
                .map(RuleType::valueOf)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean test(PostAnalysisIssueVisitor.ComponentIssue componentIssue) {
        return !exclusions.contains(componentIssue.getIssue().type());
    }
}
