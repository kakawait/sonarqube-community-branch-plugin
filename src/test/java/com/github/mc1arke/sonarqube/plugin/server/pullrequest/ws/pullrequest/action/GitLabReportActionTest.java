package com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.component.BranchDao;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.issue.ImpactDto;
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.permission.ProjectPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.rule.RuleDao;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;

class GitLabReportActionTest {

    private final DbClient dbClient = mock();
    private final ComponentFinder componentFinder = mock();
    private final UserSession userSession = mock();
    private final GitLabReportAction underTest = new GitLabReportAction(dbClient, componentFinder, userSession);

    @Test
    void shouldDefineEndpointWithPullRequestAndPathPrefixParameters() {
        WebService.NewController controller = mock();
        WebService.NewAction action = mock();
        WebService.NewParam projectParam = mock();
        WebService.NewParam pullRequestParam = mock();
        when(controller.createAction(any())).thenReturn(action);
        when(action.setHandler(any())).thenReturn(action);
        when(action.createParam(any())).thenReturn(projectParam, pullRequestParam);

        underTest.define(controller);

        verify(controller).createAction("gitlab_report");
        verify(action).createParam("pullRequest");
        verify(action).createParam("pathPrefix");
        verify(pullRequestParam).setRequired(true);
    }

    @Test
    void shouldWriteOnlyOpenIssuesInGitLabCodeClimateFormatWithoutPathPrefix() {
        assertThat(writeReport(null, "MINOR")).isEqualTo(expectedReport("src/Foo.java", "minor"));
    }

    @Test
    void shouldPrefixPathsWithNormalisedPathPrefix() {
        assertThat(writeReport("/back/", "MINOR")).isEqualTo(expectedReport("back/src/Foo.java", "minor"));
    }

    @Test
    void shouldIgnoreBlankPathPrefix() {
        assertThat(writeReport("/", "MINOR")).isEqualTo(expectedReport("src/Foo.java", "minor"));
    }

    @Test
    void shouldReportCriticalIssueWithOnlyHighMaintainabilityImpactAsMajor() {
        assertThat(writeReport(null, "CRITICAL", new ImpactDto(SoftwareQuality.MAINTAINABILITY, Severity.HIGH)))
                .isEqualTo(expectedReport("src/Foo.java", "major"));
    }

    @Test
    void shouldKeepCriticalSeverityWhenAnotherQualityIsHigh() {
        assertThat(writeReport(null, "CRITICAL", new ImpactDto(SoftwareQuality.MAINTAINABILITY, Severity.HIGH),
                new ImpactDto(SoftwareQuality.SECURITY, Severity.HIGH)))
                .isEqualTo(expectedReport("src/Foo.java", "critical"));
    }

    @Test
    void shouldThrowNotFoundWhenPullRequestDoesNotExist() {
        when(componentFinder.getProjectByKey(any(), eq("projectKey")))
                .thenReturn(new ProjectDto().setKey("projectKey").setUuid("projectUuid"));
        when(userSession.hasEntityPermission(any(ProjectPermission.class), any(ProjectDto.class))).thenReturn(true);

        BranchDao branchDao = mock();
        when(dbClient.branchDao()).thenReturn(branchDao);
        when(branchDao.selectByPullRequestKey(any(), any(), any())).thenReturn(Optional.empty());

        Request request = mock();
        when(request.mandatoryParam("project")).thenReturn("projectKey");
        when(request.mandatoryParam("pullRequest")).thenReturn("123");

        assertThatThrownBy(() -> underTest.handle(request, mock()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pull request '123' is not found for project 'projectKey'");
    }

    private String writeReport(String pathPrefix, String severity, ImpactDto... impacts) {
        when(componentFinder.getProjectByKey(any(), eq("projectKey")))
                .thenReturn(new ProjectDto().setKey("projectKey").setUuid("projectUuid"));
        when(userSession.hasEntityPermission(any(ProjectPermission.class), any(ProjectDto.class))).thenReturn(true);

        BranchDao branchDao = mock();
        when(dbClient.branchDao()).thenReturn(branchDao);
        when(branchDao.selectByPullRequestKey(any(), eq("projectUuid"), eq("123")))
                .thenReturn(Optional.of(new BranchDto().setBranchType(BranchType.PULL_REQUEST).setUuid("prUuid")));

        IssueDao issueDao = mock();
        when(dbClient.issueDao()).thenReturn(issueDao);
        when(issueDao.selectIssueKeysByComponentUuid(any(), eq("prUuid"))).thenReturn(Set.of("open", "closed"));
        IssueDto openIssue = new IssueDto().setKee("open").setRuleKey("java", "S1481").setStatus(IssueStatus.OPEN.name())
                .setSeverity(severity).setMessage("Remove this unused variable").setFilePath("src/Foo.java").setLine(3);
        openIssue.replaceAllImpacts(List.of(impacts));
        IssueDto closedIssue = new IssueDto().setKee("closed").setRuleKey("java", "S1481").setStatus(IssueStatus.FIXED.name())
                .setSeverity("MINOR").setMessage("Fixed").setFilePath("src/Foo.java").setLine(4);
        when(issueDao.selectByKeys(any(), any())).thenReturn(List.of(openIssue, closedIssue));

        RuleDao ruleDao = mock();
        when(dbClient.ruleDao()).thenReturn(ruleDao);
        when(ruleDao.selectByKey(any(), eq(RuleKey.of("java", "S1481")))).thenReturn(Optional.of(new RuleDto().setName("Unused Local Variable!")));

        Request request = mock();
        when(request.mandatoryParam("project")).thenReturn("projectKey");
        when(request.mandatoryParam("pullRequest")).thenReturn("123");
        when(request.param("pathPrefix")).thenReturn(pathPrefix);
        StringWriter body = new StringWriter();
        Response response = mock();
        when(response.newJsonWriter()).thenReturn(JsonWriter.of(body));

        underTest.handle(request, response);

        return body.toString();
    }

    private static String expectedReport(String path, String severity) {
        return "[{\"check_name\":\"unused-local-variable\","
                + "\"fingerprint\":\"" + DigestUtils.md5Hex("open") + "\","
                + "\"description\":\"Remove this unused variable\","
                + "\"severity\":\"" + severity + "\","
                + "\"location\":{\"path\":\"" + path + "\",\"lines\":{\"begin\":3,\"end\":3}}}]";
    }
}
