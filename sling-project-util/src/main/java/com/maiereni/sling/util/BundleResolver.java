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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.maiereni.sling.util.bean.Bundle;

/**
 * Resolve the bundles
 * @author Petre Maierean
 *
 */
public class BundleResolver {
	private static final Logger logger = LoggerFactory.getLogger(BundleResolver.class);
	private File repositoryRootPath;
	
	public BundleResolver() {
		String s = System.getProperty("user.home");
		repositoryRootPath = new File(s, ".m2/repository");
	}
	
	/**
	 * Given an artifact and its containing feature, resolve the bundle definition
	 * 
	 * @param artifact
	 * @param feature
	 * @return
	 */
	public Bundle getBundle(@Nonnull final Artifact artifact, @Nonnull final Feature feature) {
		String version = artifact.getVersion();
		if (version.startsWith("${")) {
			version = version.substring(2);
			version = version.substring(0, version.length() - 1);
			Iterator<Entry<String,String>> iter = feature.getVariables().iterator();
			while(iter.hasNext()) {
				Entry<String, String> entry = iter.next();
				if (version.equals(entry.getKey())) {
					version = entry.getValue();
					break;
				}
			}
		}
		return getBundle(artifact.getGroupId(), artifact.getArtifactId(), version, feature.getName());
	}
	
	/**
	 * Find a bundle in the M2 repository and describe it
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @param featureName
	 * @return
	 */
	public Bundle getBundle(@Nonnull final String groupId, @Nonnull final String artifactId, @Nonnull final String version, final String featureName) {
		Bundle ret = null;
		String sPackage = groupId.replaceAll("\\.", "/");
		File fPackage = new File(repositoryRootPath, sPackage);
		File fArtifactDir = new File(fPackage, artifactId + "/" + version);
		if (fArtifactDir.isDirectory()) {
			File fArtifact = new File(fArtifactDir, artifactId + "-" + version + ".jar");
			if (fArtifact.isFile()) {
			    try (FileInputStream stream = new FileInputStream(fArtifact);
		    		JarInputStream jarStream = new JarInputStream(stream);) {
			    	Manifest mf = jarStream.getManifest();
			    	ret = new Bundle();
			    	ret.setName(getAttributeValue(mf, "Bundle-SymbolicName"));
			    	ret.setText(getAttributeValue(mf, "Bundle-Name"));
			    	ret.setPkgName(getAttributeValue(mf, "Bundle-Category"));
			    	ret.setVersion(getAttributeValue(mf, "Bundle-Version"));
			    	
			    	ret.setExportPackages(getAttributeValueAsList(mf, "Export-Package"));
			    	ret.setImportPackages(getAttributeValueAsList(mf, "Import-Package"));
			    	
			    	
			    	ret.setLocation(fArtifact.getPath());
			    	ret.setFeatureName(featureName);
			    	ret.setArtifactId(artifactId);
			    	ret.setGroupId(groupId);
				} catch (Exception e) {
					logger.error("Failed to read the bundle due to an exception", e);
				}
			}
		}
		if (ret == null)
			logger.error("Cannot resolve " + groupId + ":" + artifactId + ":" + version);
		
		return ret;
	}
	
	private List<String> getAttributeValueAsList(final Manifest mf, final String key) {
		String value = getAttributeValue(mf, key);
		List<String> ret = new ArrayList<String>();
		if (StringUtils.isNotBlank(value)) {
			String[] toks = value.split(",");
			for(String tok: toks) {
				if ((tok.indexOf(")\"") > 0 || tok.indexOf("]\"") > 0 ) && ret.size() > 0) {
					String actual = ret.remove(ret.size() - 1);
					ret.add(actual + "," + tok);
				}
				else
					ret.add(tok);
			}
		}
		return ret;
	}
	
	private String getAttributeValue(final Manifest mf, final String key) {
		String ret = null;
		Attributes attrib = mf.getMainAttributes();
		ret = attrib.getValue(key);
		
		return ret;
	}
}
