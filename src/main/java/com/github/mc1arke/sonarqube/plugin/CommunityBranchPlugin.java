/*
 * Copyright (C) 2020-2024 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin;

import org.sonar.api.CoreProperties;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinition.ConfigScope;
import org.sonar.api.rule.Severity;
import org.sonar.core.config.PurgeConstants;
import org.sonar.core.extension.CoreExtension;

import com.github.mc1arke.sonarqube.plugin.almclient.azuredevops.DefaultAzureDevopsClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.bitbucket.DefaultBitbucketClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.bitbucket.HttpClientBuilderFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.github.GithubClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.gitlab.DefaultGitlabClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.gitlab.DefaultLinkHeaderReader;
import com.github.mc1arke.sonarqube.plugin.ce.CommunityReportAnalysisComponentProvider;
import com.github.mc1arke.sonarqube.plugin.scanner.BranchConfigurationFactory;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchConfigurationLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchParamsValidator;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityProjectBranchesLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.ScannerPullRequestPropertySensor;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.AzureDevopsAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.BitbucketPipelinesAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.CirrusCiAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.CodeMagicAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.GithubActionsAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.GitlabCiAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.scanner.autoconfiguration.JenkinsAutoConfigurer;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchFeatureExtension;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchSupportDelegate;
import com.github.mc1arke.sonarqube.plugin.server.MonoRepoFeature;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.AzureDevopsValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.BitbucketValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.GithubValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.GitlabValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.DeleteBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.SetAzureBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.SetBitbucketBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.SetBitbucketCloudBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.SetGithubBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.SetGitlabBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.binding.action.ValidateBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.PullRequestWs;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action.DeleteAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action.GitLabReportAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action.ListAction;

import static org.sonar.api.rules.RuleType.BUG;
import static org.sonar.api.rules.RuleType.CODE_SMELL;
import static org.sonar.api.rules.RuleType.SECURITY_HOTSPOT;
import static org.sonar.api.rules.RuleType.VULNERABILITY;

/**
 * @author Michael Clarke
 */
public class CommunityBranchPlugin implements Plugin, CoreExtension {

    public static final String IMAGE_URL_BASE = "com.github.mc1arke.sonarqube.plugin.branch.image-url-base";
    public static final String PR_SUMMARY_NOTE_EDIT = "com.github.mc1arke.sonarqube.plugin.branch.pullrequest.summary.edit";
    public static final String PR_PUBLISH_CI_STATUS = "com.github.mc1arke.sonarqube.plugin.branch.pullrequest.publish.ci.status";
    public static final String PR_FILTER_TYPE_EXCLUSION = "com.github.mc1arke.sonarqube.plugin.branch.filter.type.exclusions";
    public static final String PR_FILTER_SEVERITY_EXCLUSION = "com.github.mc1arke.sonarqube.plugin.branch.filter.severity.exclusions";
    public static final String PR_FILTER_MAXAMOUNT = "com.github.mc1arke.sonarqube.plugin.branch.filter.maxamount";

    @Override
    public String getName() {
        return "Community Branch Plugin";
    }

    @Override
    public void load(CoreExtension.Context context) {
        if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityReportAnalysisComponentProvider.class);
        } else if (SonarQubeSide.SERVER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityBranchFeatureExtension.class, CommunityBranchSupportDelegate.class,
                                  DeleteBindingAction.class,
                                  SetGithubBindingAction.class,
                                  SetAzureBindingAction.class,
                                  SetBitbucketBindingAction.class,
                                  SetBitbucketCloudBindingAction.class,
                                  SetGitlabBindingAction.class,
                    ValidateBindingAction.class,
                    DeleteAction.class,
                    ListAction.class,
                    GitLabReportAction.class,
                    PullRequestWs.class,

                    GithubValidator.class,
                    GithubClientFactory.class,
                    DefaultLinkHeaderReader.class,
                    HttpClientBuilderFactory.class,
                    DefaultBitbucketClientFactory.class,
                    BitbucketValidator.class,
                    GitlabValidator.class,
                    DefaultGitlabClientFactory.class,
                    DefaultAzureDevopsClientFactory.class,
                    AzureDevopsValidator.class,

                /* org.sonar.db.purge.PurgeConfiguration uses the value for the this property if it's configured, so it only
                needs to be specified here, but doesn't need any additional classes to perform the relevant purge/cleanup
                */
                                  PropertyDefinition
                                          .builder(PurgeConstants.DAYS_BEFORE_DELETING_INACTIVE_BRANCHES_AND_PRS)
                                          .name("Number of days before purging inactive branches and pull requests")
                                          .description(
                                                  "Branches and pull requests are permanently deleted when there has been no analysis for the configured number of days.")
                                          .category(CoreProperties.CATEGORY_HOUSEKEEPING)
                                          .subCategory(CoreProperties.SUBCATEGORY_BRANCHES_AND_PULL_REQUESTS).defaultValue("30")
                                          .type(PropertyType.INTEGER)
                                          .index(1)
                                          .build()
                                  ,

                                  PropertyDefinition
                                          .builder(PurgeConstants.BRANCHES_TO_KEEP_WHEN_INACTIVE)
                                          .name("Branches to keep when inactive")
                                          .description("By default, branches and pull requests are automatically deleted when inactive. This setting allows you "
                                                + "to protect branches (but not pull requests) from this deletion. When a branch is created with a name that "
                                                + "matches any of the regular expressions on the list of values of this setting, the branch will not be deleted "
                                                + "automatically even when it becomes inactive. Example:"
                                                + "<ul><li>develop</li><li>release-.*</li></ul>")
                                          .category(CoreProperties.CATEGORY_HOUSEKEEPING)
                                          .subCategory(CoreProperties.SUBCATEGORY_BRANCHES_AND_PULL_REQUESTS)
                                          .multiValues(true)
                                          .defaultValue("main,master,develop,trunk")
                                          .onConfigScopes(ConfigScope.PROJECT)
                                          .index(2)
                                          .build()

                                 );

        }

        if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide() ||
            SonarQubeSide.SERVER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(PropertyDefinition.builder(IMAGE_URL_BASE)
                                          .category(CoreProperties.CATEGORY_GENERAL)
                                          .subCategory(CoreProperties.SUBCATEGORY_GENERAL)
                                          .onConfigScopes(ConfigScope.APP)
                                          .name("Images base URL")
                                          .description("Base URL used to load the images for the PR comments (please use this only if images are not displayed properly).")
                                          .type(PropertyType.STRING)
                                          .build(),
                MonoRepoFeature.class);

            PropertyDefinition editSummaryProperty = PropertyDefinition
                    .builder(PR_SUMMARY_NOTE_EDIT)
                    .category(getName())
                    .subCategory("GitLab only")
                    .onConfigScopes(ConfigScope.PROJECT)
                    .name("Edit summary note")
                    .description(
                            "Edit summary discussion thread instead of resolving it and creating a new one (Gitlab only).")
                    .type(PropertyType.BOOLEAN)
                    .defaultValue(String.valueOf(false))
                    .build();
            PropertyDefinition publishCiStatusProperty = PropertyDefinition
                    .builder(PR_PUBLISH_CI_STATUS)
                    .category(getName())
                    .subCategory("GitLab only")
                    .onConfigScopes(ConfigScope.PROJECT)
                    .name("Publish CI status")
                    .description("Toggle publishing CI status (Gitlab only).")
                    .type(PropertyType.BOOLEAN)
                    .defaultValue(String.valueOf(true))
                    .build();

            PropertyDefinition typeFilterProperty = PropertyDefinition
                    .builder(PR_FILTER_TYPE_EXCLUSION)
                    .category(getName())
                    .subCategory("Filters")
                    .onConfigScopes(ConfigScope.PROJECT)
                    .name("RuleType Exclusions")
                    .description(
                            "Comma-separated list of ruletypes you want to exclude, possible values: CODE_SMELL, BUG, VULNERABILITY, SECURITY_HOTSPOT")
                    .type(PropertyType.STRING)
                    .options(BUG.name(), CODE_SMELL.name(), VULNERABILITY.name(), SECURITY_HOTSPOT.name())
                    .build();
            PropertyDefinition severityFilterProperty = PropertyDefinition
                    .builder(PR_FILTER_SEVERITY_EXCLUSION)
                    .category(getName())
                    .subCategory("Filters")
                    .onConfigScopes(ConfigScope.PROJECT)
                    .name("Severity Exclusions")
                    .description(
                            "Comma-separated list of severity levels you want to exclude, possible values: INFO, MINOR, MAJOR, CRITICAL, BLOCKER")
                    .type(PropertyType.STRING)
                    .options(Severity.ALL)
                    .build();
            PropertyDefinition maxFilterProperty = PropertyDefinition
                    .builder(PR_FILTER_MAXAMOUNT)
                    .category(getName())
                    .subCategory("Filters")
                    .onConfigScopes(ConfigScope.PROJECT)
                    .name("Max amount")
                    .description("Max amount of comments to be added to the pull request, must be > 0")
                    .type(PropertyType.INTEGER)
                    .build();

            context.addExtensions(editSummaryProperty, publishCiStatusProperty, typeFilterProperty,
                    severityFilterProperty, maxFilterProperty);
        }
    }

    @Override
    public void define(Plugin.Context context) {
        if (SonarQubeSide.SCANNER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityProjectBranchesLoader.class,
                                  CommunityBranchConfigurationLoader.class, CommunityBranchParamsValidator.class,
                                  ScannerPullRequestPropertySensor.class, BranchConfigurationFactory.class,
                                  AzureDevopsAutoConfigurer.class, BitbucketPipelinesAutoConfigurer.class,
                                  CirrusCiAutoConfigurer.class, CodeMagicAutoConfigurer.class,
                                  GithubActionsAutoConfigurer.class, GitlabCiAutoConfigurer.class,
                                  JenkinsAutoConfigurer.class);
        }
    }
}
