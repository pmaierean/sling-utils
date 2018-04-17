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
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.ArtifactGroup;
import org.apache.sling.provisioning.model.Feature;
import org.apache.sling.provisioning.model.Model;
import org.apache.sling.provisioning.model.RunMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.maiereni.sling.util.bean.Bundle;

/**
 * @author Petre Maierean
 *
 */
public class SlingModelInterpreter extends SlingModelReader {
	private static final Logger logger = LoggerFactory.getLogger(SlingModelInterpreter.class);
	private Map<String, Bundle> installedBundles;
	
	public SlingModelInterpreter() throws Exception {
		super();
		installedBundles = init(false);
	}
	
	public void buildDependencyTree(final String modelDir, final String xmlFile) throws Exception {
		List<Bundle> bundles = listBundles(modelDir);
        List<Bundle> extra = getExtraInstalledBundles(bundles);
        Map<String, Bundle> exportPackages = getExportPackages(bundles, extra);
        Document document = documentBuilder.newDocument();
        Element root = document.createElement("bundles");
        document.appendChild(root);
        Element el = document.createElement("featured");
        root.appendChild(el);
        addBundles(el, bundles, exportPackages);
        el = document.createElement("extra");
        addBundles(el, extra, exportPackages);

        el = document.createElement("exports");
        root.appendChild(el);
        addExports(el,exportPackages);
        
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		//initialize StreamResult with File object to save to file
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(document);
		transformer.transform(source, result);
		String xmlString = result.getWriter().toString();
		FileUtils.writeStringToFile(new File(xmlFile), xmlString, Charset.defaultCharset());		
	}
	
	private void addExports(final Element el, final Map<String, Bundle> exportPackages) {
		Document document = el.getOwnerDocument();
		for(String key: exportPackages.keySet()) {
			Element exp = document.createElement("export");
			exp.setAttribute("key", key);
			exp.setAttribute("name", exportPackages.get(key).getName());
			exp.setAttribute("pos", ""+ exportPackages.get(key).getPos());
			el.appendChild(exp);
		}		
	}
	
	private void addBundles(final Element el, final List<Bundle> bundles, final Map<String, Bundle> exportPackages ) {
		Document document = el.getOwnerDocument();
        List<String> depName = new ArrayList<String>();
        for(Bundle bundle: bundles) {
        	depName.clear();
        	Element sel = document.createElement("bundle");
        	sel.setAttribute("name", bundle.getName());
        	sel.setAttribute("position", "" + bundle.getPos());
        	sel.setAttribute("category", bundle.getPkgName());
        	sel.setAttribute("artifactId", bundle.getArtifactId());
        	sel.setAttribute("groupId", bundle.getGroupId());
        	sel.setAttribute("version", bundle.getVersion());
        	sel.setAttribute("feature", bundle.getFeatureName());
        	Element elDeps = document.createElement("dependents");
        	Element unresolved = document.createElement("uresolved");
        	boolean hasUnresolved = false;
        	for(String importPackage: bundle.getImportPackages()) {
        		String key = getKey(importPackage);
        		Bundle dep = exportPackages.get(key);
        		boolean tenative = false;
        		if (dep == null) {
                	if (bundle.getName().equals("org.apache.felix.configadmin")) {
                		logger.debug("here: " + key);
                	}
        			dep = resolveExportIgnoreVersion(exportPackages, key);
        			tenative = true;
        		}
        		if (dep == null) {        		
    				Element elDep = document.createElement("unresolvedItem");
    				elDep.setTextContent(importPackage);
    				unresolved.appendChild(elDep);
    				hasUnresolved = true;
        		}
        		else {
        			if (!depName.contains(dep.getName())) {
        				Element elDep = document.createElement("dependent");
        				elDep.setAttribute("name", dep.getName());
        				elDep.setAttribute("pos", "" + dep.getPos());
        				if (tenative)
        					elDep.setTextContent("tentative");
        				elDeps.appendChild(elDep);
        				depName.add(dep.getName());
        			}
        		}
        	}
       		sel.appendChild(elDeps);
       		if (hasUnresolved)
       			sel.appendChild(unresolved);
        	el.appendChild(sel);
        }
		
	}
	
	private Bundle resolveExportIgnoreVersion(final Map<String, Bundle> exportPackages, final String key) {
		Bundle ret = null;
		int ix = key.indexOf("version=");
		String s = key;
		if (key.equals("org.apache.felix.cm.file"))
			logger.debug("Here");
		if (ix > 0) {
			s = key.substring(0,  ix);
		} else {
			ix = key.indexOf(":");
			if (ix > 0)
				s = key.substring(0,  ix);
		}
		ix = s.indexOf(";");
		if (ix>0)
			s = s.substring(0,  ix);
		for(String expKeys: exportPackages.keySet()) {
			if (expKeys.startsWith(s + ":") || expKeys.startsWith(s + ";") || expKeys.equals(s)) {
				ret = exportPackages.get(expKeys);
				break;
			}
		}
		return ret;
	}

	private Map<String, Bundle> getExportPackages(final List<Bundle> bundles, final List<Bundle> extra) {
		Map<String, Bundle> ret = new HashMap<String, Bundle>();
		for(Bundle bundle: bundles) {
			for(String exp: bundle.getExportPackages()) {
				String key = getKey(exp);
				if (StringUtils.isNotEmpty(exp)) {
					if (ret.containsKey(key)) {
						Bundle b = ret.get(key);
						logger.warn("Collision " + key + " is exported by 2 bundles " + b.getName() + " and " + bundle.getName());
					}
					else
						ret.put(key, bundle);
				}
			}
		}
		for(Bundle bundle: extra) {
			for(String exp: bundle.getExportPackages()) {
				if (ret.containsKey(exp))
					logger.warn("Collision " + exp + " is exported by 2 bundles " + ret.get(exp).getName() + " and " + bundle.getName());
				else
					ret.put(exp, bundle);
			}
		}
		return ret; 
	} 
	
	private static final String VERSION1 = ";version=";
	private static final String VERSION = VERSION1 + "\"";
	
	private String getKey(final String s) {
		String ret = s.trim();
		if (s.startsWith("\""))
			ret = s.substring(1);
		if (ret.endsWith("\""))
			ret = ret.substring(0, ret.length() - 1);
		int ix = ret.indexOf(VERSION);
		if (ix>0)
			ret = ret.substring(0, ix) + ":" + ret.substring(ix + VERSION.length());
		ix = ret.indexOf(VERSION1);
		if (ix>0)
			ret = ret.substring(0, ix) + ":" + ret.substring(ix + VERSION1.length());
		ix = ret.indexOf(";uses");
		if (ix>0)
			ret = ret.substring(0, ix);
		if (ret.endsWith("\""))
			ret = ret.substring(0, ret.length() - 1);
		return ret;
	}
	
	private List<Bundle> getExtraInstalledBundles(final List<Bundle> bundles) {
		List<Bundle> ret = new ArrayList<Bundle>();
		List<String> names = new ArrayList<String>();
		for(Bundle bundle: bundles) 
			names.add(bundle.getName());
		for(String rem: installedBundles.keySet()) {
			if (!names.contains(rem)) {
				Bundle b = installedBundles.get(rem);
				Bundle actualBundle = null;
				if (b.getName().startsWith("org.apache.felix")) {
					actualBundle = bundleResolver.getBundle("org.apache.felix", b.getName(), b.getVersion(), null);						
				}
				else if (b.getName().startsWith("org.apache.sling")) {
					actualBundle = bundleResolver.getBundle("org.apache.sling", b.getName(), b.getVersion(), null);											
				}
				if (actualBundle != null) {
					actualBundle.setPos(b.getPos());
					ret.add(actualBundle);
				}
				else
					ret.add(b);
			}
		}
		Collections.sort(ret, new BundleComparator());
		logger.debug("Extra size " + ret.size());
		return ret;
	}
	
	private List<Bundle> listBundles(final String modelDir) throws Exception {
		Model model = readModel(modelDir);
		List<Bundle> ret = new ArrayList<Bundle>();
		int count = 0;
		for(Feature feature: model.getFeatures()) {
			for(RunMode rm : feature.getRunModes()) {
				for(ArtifactGroup group : rm.getArtifactGroups()) {
					Iterator<Artifact> iArtifact = group.iterator();
					while(iArtifact.hasNext()) {
						Artifact artifact = iArtifact.next();
						Bundle bundle = bundleResolver.getBundle(artifact, feature);
						if (bundle == null)
							throw new Exception("Cannot find bundle for artifact " + artifact);
						if (installedBundles.containsKey(bundle.getName())) {
							int pos = installedBundles.get(bundle.getName()).getPos();
							bundle.setPos(pos);
							ret.add(bundle);
							count++;
						}
						else
							logger.error("Cannot find bundle: " + bundle.getName());
					}
				}
			}
		}
		Collections.sort(ret, new BundleComparator());
		logger.debug("The count is " + count);
		return ret;
	}
	
	private class BundleComparator implements Comparator<Bundle> {
		@Override
		public int compare(Bundle o1, Bundle o2) {
			int ret = 0;
			if (o1.getPos() < o2.getPos())
				ret = -1;
			else if (o1.getPos() > o2.getPos())
				ret = 1;
			return ret;
		}
	};
	
	public static void main(final String[] args) {
		try {
			SlingModelInterpreter reader = new SlingModelInterpreter();
			reader.buildDependencyTree(args[0], args[1]);
		}
		catch(Exception e) {
			logger.error("The model could not be read", e);
		}
	}
}
