/*
 * Copyright (C) 2022 Michael Clarke
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
package com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest;

import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action.PullRequestWsAction;
import org.sonar.api.server.ws.WebService;

public class PullRequestWs implements WebService {

    private final PullRequestWsAction[] actions;

    public PullRequestWs(PullRequestWsAction... actions) {
        this.actions = actions;
    }

    @Override
    public void define(Context context) {
        NewController controller = context.createController("api/project_pull_requests");
        for (PullRequestWsAction action : actions) {
            action.define(controller);
        }
        controller.done();
    }

}