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

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.maiereni.sling.util.bean.Project;
import com.maiereni.sling.util.bean.ProjectLayout;

/**
 * Loads the project definition from https://github.com/apache/sling-aggregator.git (default.xml) 
 * 
 * @author Petre Maierean
 *
 */
class ProjectLayoutLoader {
	private static final DocumentBuilder DOCUMENT_BUILDER;
	/**
	 * Read the project layout from the sourceURL
	 * @param sourceFile
	 * @return
	 * @throws Exception
	 */
	public ProjectLayout readProjectLayout(@Nonnull final String sourceFile) throws Exception {
		ProjectLayout ret = new ProjectLayout();
		try (InputStream is = new FileInputStream(sourceFile)) {
			XPath xpath = XPathFactory.newInstance().newXPath();
			Document document = DOCUMENT_BUILDER.parse(is);
			NodeList nl = (NodeList)xpath.evaluate("//project", document, XPathConstants.NODESET);
			List<Project> projects = new ArrayList<Project>();
			for(int i=0;i<nl.getLength();i++) {
				Element eProject = (Element)nl.item(i);
				Project project = getProject(eProject);
				projects.add(project);
			}
			ret.setProjects(projects);
		}
		return ret;
	}

	protected Project getProject(final Element element) {
		Project project = new Project();
		project.setGroup(element.getAttribute("group"));
		project.setName(element.getAttribute("path"));
		project.setPath(element.getAttribute("name"));
		return project;
	}

	static {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DOCUMENT_BUILDER = factory.newDocumentBuilder();
		}
		catch(Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	
}
