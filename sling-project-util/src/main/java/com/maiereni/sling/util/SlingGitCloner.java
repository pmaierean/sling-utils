/**
 * ================================================================
 *  Copyright (c) 2017-2018 Maiereni Software and Consulting Inc
 * ================================================================
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.maiereni.sling.util;

import java.io.File;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.maiereni.sling.util.bean.Project;
import com.maiereni.sling.util.bean.ProjectLayout;

/**
 * @author Petre Maierean
 *
 */
public class SlingGitCloner {
	private static final Logger logger = LoggerFactory.getLogger(SlingGitCloner.class);
	public static final String GIT_HOME = "user.git.home";
	public static final String USER_HOME = "user.home";
	public static final String GITHUB_URL = "github.url";
	public static final String GITHUB_USER = "github.user";
	public static final String GITHUB_PASSWORD = "github.password";
	public static final String SLING_AGGREGATOR_DEF_URL = "github.sling.aggregator.url";
	
	private File fLocalGitDir;
	private String gitHubUrl;
	private boolean bare;
	private Project aggregator;
	private UsernamePasswordCredentialsProvider credentials;
	
	SlingGitCloner() throws Exception {
		String userDir = System.getProperty(USER_HOME);
		String gitDir = System.getProperty(GIT_HOME, userDir + "/git");
		File fLocalGitDir = new File(gitDir);
		if (!fLocalGitDir.exists())
			if (!fLocalGitDir.mkdirs())
				throw new Exception("Cannot make GIT repository at " + fLocalGitDir.getPath());
		this.fLocalGitDir = fLocalGitDir;
		logger.debug("Using git local repository at " + fLocalGitDir.getPath());
		this.gitHubUrl = System.getProperty(GITHUB_URL, "https://github.com/apache");
		aggregator = new Project();
		aggregator.setName("sling-aggregator");
		aggregator.setPath("sling-aggregator.git");
		aggregator.setGroup("aggregator");
		String userName = System.getProperty(GITHUB_USER);
		if (StringUtils.isBlank(userName))
			throw new Exception("Please provide the userName with the JVM property " + GITHUB_USER);
		String password = System.getProperty(GITHUB_PASSWORD);
		if (StringUtils.isBlank(password))
			throw new Exception("Please provide the password with the JVM property " + GITHUB_PASSWORD);
		credentials = new UsernamePasswordCredentialsProvider(userName, password.toCharArray());
	}
	
	
	public void cloneSling() throws Exception {
		logger.debug("Clone aggregator project");
		File f = cloneProject(aggregator);
		File fProjectDef = new File(f, "default.xml");
		ProjectLayoutLoader loader = new ProjectLayoutLoader();
		logger.debug("Load project definition from "  + fProjectDef.getPath());
		ProjectLayout projectLayout = loader.readProjectLayout(fProjectDef.getPath());
		logger.debug("Have loaded the project definitions from " + fProjectDef.getPath());
		for(Project project: projectLayout.getProjects()) {
			cloneProject(project);
		}
	}
	
	private File cloneProject(@Nonnull final Project project) throws Exception {
		String uri = gitHubUrl + "/" + project.getPath();
		File fDest = new File(fLocalGitDir, project.getName());
		if (!fDest.exists()) {	
			if (!fDest.mkdirs())
				throw new Exception("Could not make directory at " + fDest.getPath());
			logger.debug("Clone project from " + uri);
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(uri).setCredentialsProvider(credentials).setDirectory(fDest).setBranch(Constants.HEAD).setBare(bare);
			clone.call().getRepository().close();
			logger.debug("Done cloning the project from " + uri);
		}
		else
			logger.debug("The project has already been cloned " + fDest.getPath());
		return fDest;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			SlingGitCloner synch = new SlingGitCloner();
			synch.cloneSling();
		}
		catch(Exception e) {
			logger.error("Failed to synchronize", e);
		}
	}

}
