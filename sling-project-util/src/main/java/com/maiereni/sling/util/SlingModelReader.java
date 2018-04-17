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
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.ArtifactGroup;
import org.apache.sling.provisioning.model.Configuration;
import org.apache.sling.provisioning.model.Feature;
import org.apache.sling.provisioning.model.KeyValueMap;
import org.apache.sling.provisioning.model.MergeUtility;
import org.apache.sling.provisioning.model.Model;
import org.apache.sling.provisioning.model.ModelUtility;
import org.apache.sling.provisioning.model.RunMode;
import org.apache.sling.provisioning.model.Section;
import org.apache.sling.provisioning.model.Traceable;
import org.apache.sling.provisioning.model.io.ModelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.maiereni.sling.util.bean.Bundle;

/**
 * Tests the Model Reader
 * @author Petre Maierean
 *
 */
public class SlingModelReader {
	private static final Logger logger = LoggerFactory.getLogger(SlingModelReader.class);
	protected DocumentBuilder documentBuilder;
	private File fLocalGitDir;
	private Map<String, Bundle> bundles;
	protected BundleResolver bundleResolver;
	
	public SlingModelReader() throws Exception {
		String userDir = System.getProperty(SlingGitCloner.USER_HOME);
		String gitDir = System.getProperty(SlingGitCloner.GIT_HOME, userDir + "/git");
		fLocalGitDir = new File(gitDir);
		if (!fLocalGitDir.exists())
			throw new Exception("No Git repository");
		documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		bundleResolver = new BundleResolver();
		this.bundles = init(true);
	}
	
	public Model readModel(@Nonnull final String sModelDir) throws Exception {
		File modelDirectory = new File(sModelDir);
		File[] candidates = modelDirectory.listFiles();
		Model result = new Model();
        for(final File f: candidates) {
        	String name = f.getName();
        	
            logger.debug("Reading model " + name + " in project ");
            try (final FileReader reader = new FileReader(f)) {
                //final File f = new File(modelDirectory, name);
                    final Model current = ModelReader.read(reader, f.getAbsolutePath());
                    final Map<Traceable, String> errors = ModelUtility.validate(current);
                    if (errors != null ) {
                        throw new Exception("Invalid model at " + name + " : " + errors);
                    }
                    MergeUtility.merge(result, current, new MergeUtility.MergeOptions().setHandleRemoveRunMode(false));
            } 
        }
        logger.debug("Done");
        return result;
        
	}
	
	public void printToXML(@Nonnull final Model model, @Nonnull final String xmlFile) throws Exception {
        Document document = documentBuilder.newDocument();
        Element el = document.createElement("model");
        el.setAttribute("location", model.getLocation());
        document.appendChild(el);
        
        List<Feature> features = model.getFeatures();
        for(Feature feature: features) {
        	Element f = createFeature(document, feature);
        	el.appendChild(f);
        }
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
	
	private Element createFeature(final Document document, final Feature feature) {
		Element ret = document.createElement("feature");
		if (StringUtils.isNotEmpty(feature.getName()))
			ret.setAttribute("name", feature.getName());
		if (feature.isSpecial())
			ret.setAttribute("special", "true");
		if (StringUtils.isNotEmpty(feature.getType()))
			ret.setAttribute("type", feature.getType());
		if (StringUtils.isNotEmpty(feature.getVersion()))
			ret.setAttribute("version", feature.getVersion());
		if (StringUtils.isNotEmpty(feature.getComment())) {
			Element c = document.createElement("comment");
			c.setTextContent(feature.getComment());
			//ret.appendChild(c);
		}
		Element variables = createMap(document, feature.getVariables(),"variables", "variable");
		if (variables != null)
			ret.appendChild(variables);

		if (!feature.getRunModes().isEmpty()) {
			Element runModes = document.createElement("runModes");
			ret.appendChild(runModes);
			for(RunMode rm : feature.getRunModes()) {
				Element runMode = createRunMode(document, rm, feature);
				runModes.appendChild(runMode);
			}
		}
		if (!feature.getAdditionalSections().isEmpty()) {
			Element additionalSections = document.createElement("additionalSections");
			ret.appendChild(additionalSections);
			for(Section sec : feature.getAdditionalSections()) {
				Element section = createSection(document, sec);
				additionalSections.appendChild(section);
			}
		}
		return ret;
	}
	
	private Element createMap(final Document document, final KeyValueMap<String> map, final String elementName, final String childName) {
		Element ret = null;
		if (!(map == null || map.isEmpty())) {
			ret = document.createElement(elementName);
			Iterator<Entry<String, String>> iter = map.iterator();
			while(iter.hasNext()) {
				Entry<String, String> ent = iter.next();
				Element variable = document.createElement(childName);
				variable.setAttribute("name", ent.getKey());
				variable.setAttribute("value", ent.getValue());
				ret.appendChild(variable);
			}
		}
		return ret;
	}
	
	private Element createRunMode(final Document document, final RunMode rm, final Feature feature) {
		Element ret = document.createElement("runMode");
		if (rm.getLocation() != null)
			ret.setAttribute("location", rm.getLocation());
		if (!rm.getArtifactGroups().isEmpty()) {
			Element ag = document.createElement("artifactGroups");
			ret.appendChild(ag);
			for(ArtifactGroup a : rm.getArtifactGroups()) {
				Element group = createArtifactGroup(document, a, feature);
				ag.appendChild(group);
			}
		}
		if (!rm.getConfigurations().isEmpty()) {
			Element c = document.createElement("configurations");
			if (StringUtils.isNotEmpty(rm.getConfigurations().getLocation()))
				c.setAttribute("location", rm.getConfigurations().getLocation());
			//if (StringUtils.isNotEmpty(rm.getConfigurations().getComment()))
			//	c.setTextContent(rm.getConfigurations().getComment());
			ret.appendChild(c);
			Iterator<Configuration> iCfg = rm.getConfigurations().iterator();
			while(iCfg.hasNext()) {
				Configuration cfg = iCfg.next();
				Element configuration = createConfiguration(document, cfg);
				c.appendChild(configuration);
			}
		}
		
		if (rm.getNames() != null) {
			Element names = document.createElement("names");
			ret.appendChild(names);
			for(String s : rm.getNames()) {
				Element name = document.createElement("name");
				name.setTextContent(s);
				names.appendChild(name);
			}			
		}
		
		Element settings = createMap(document, rm.getSettings(), "settings", "setting");
		if (settings != null)
			ret.appendChild(settings);
		return ret;
	}
	
	private Element createSection(final Document document, final Section sec) {
		Element ret = document.createElement("section");
		if (sec.getName() != null)
			ret.setAttribute("name", sec.getName());
		if (StringUtils.isNotEmpty(sec.getComment())) {
			Element c = document.createElement("comment");
			c.setTextContent(sec.getComment());
			//ret.appendChild(c);
		}		
		if (StringUtils.isNotEmpty(sec.getContents())) {
			Element c = document.createElement("contents");
			c.setTextContent(sec.getContents());
			ret.appendChild(c);
		}
		if (!sec.getAttributes().isEmpty()) {
			Element attrs = document.createElement("attributes");
			ret.appendChild(attrs);
			for(String key : sec.getAttributes().keySet()) {
				Element attr = document.createElement("attribute");
				attr.setAttribute("key", key);
				attr.setTextContent(sec.getAttributes().get(key));
				attrs.appendChild(attr);
			}
		}
		return ret;
	}

	private Element createArtifactGroup(final Document document, final ArtifactGroup group, final Feature feature) {
		Element ret = document.createElement("artifactGroup");
		ret.setAttribute("level", "" + group.getStartLevel());
		if (StringUtils.isNotBlank(group.getLocation()))
			ret.setAttribute("location", group.getLocation());
		if (group.getComment()!=null) {
			Element c = document.createElement("comment");
			c.setTextContent(group.getComment());
			//ret.appendChild(c);			
		}
		if (!group.isEmpty()) {
			Element c = document.createElement("artifacts");
			ret.appendChild(c);			
			Iterator<Artifact> iArtifact = group.iterator();
			while(iArtifact.hasNext()) {
				Artifact artifact = iArtifact.next();
				Element el = createArtifact(document, artifact, feature);
				c.appendChild(el);
			}
		}
		return ret;
	}
	
	private Element createArtifact(final Document document, final Artifact artifact, final Feature feature) {
		Element ret = document.createElement("artifact");
		ret.setAttribute("artifactId", artifact.getArtifactId());
		ret.setAttribute("groupId", artifact.getGroupId());
		ret.setAttribute("version", artifact.getVersion());
		if (StringUtils.isNotBlank(artifact.getClassifier()))
			ret.setAttribute("classifier", artifact.getClassifier());
		Element loc = document.createElement("reference");
		loc.setAttribute("location", artifact.getLocation());
		loc.setAttribute("repositoryPath", artifact.getRepositoryPath());
		ret.appendChild(loc);
		ret.setAttribute("type", artifact.getType());
		if (artifact.getComment()!=null) {
			Element c = document.createElement("comment");
			c.setTextContent(artifact.getComment());
			//ret.appendChild(c);			
		}
		Bundle bundle = bundleResolver.getBundle(artifact, feature);
		String bundleName = artifact.getArtifactId();
		if (bundle != null) 
			bundleName = bundle.getName();
		if (!appendBundle(ret, bundleName, bundle))
			appendBundle(ret, artifact.getGroupId() + "." +  artifact.getArtifactId(), bundle);
				
		if (!artifact.getMetadata().isEmpty()) {
			Element c = document.createElement("metadata");
			ret.appendChild(c);			
			for(String key: artifact.getMetadata().keySet()) {
				String value = artifact.getMetadata().get(key);
				Element p = document.createElement("property");
				p.setAttribute("key", key);
				p.setAttribute("value", value);
				c.appendChild(p);			
			}
		}
		return ret;		
	}
	
	private boolean appendBundle(final Element el, final String bundleName, final Bundle b) {
		boolean ret = false;
		if (bundles.containsKey(bundleName)) {
			Element bdl = el.getOwnerDocument().createElement("bundle");
			Bundle bundle = bundles.get(bundleName);
			bdl.setAttribute("id", "" + bundle.getPos());
			if (StringUtils.isNotBlank(bundle.getPkgName()))
				bdl.setAttribute("category", bundle.getPkgName());
			bdl.setTextContent(bundle.getText());
			el.appendChild(bdl);
			ret = true;
		}		
		return ret;
	}
	
	private Element createConfiguration(final Document document, final Configuration cfg) {
		Element ret = document.createElement("configuration");
		ret.setAttribute("location", cfg.getLocation());
		ret.setAttribute("pid", cfg.getPid());
		ret.setAttribute("factoryPid", cfg.getFactoryPid());

		if (cfg.getComment()!=null) {
			Element c = document.createElement("comment");
			c.setTextContent(cfg.getComment());
			//ret.appendChild(c);			
		}
		
		if (!cfg.getProperties().isEmpty()) {
			Element c = document.createElement("properties");
			ret.appendChild(c);			
			Enumeration<String> keys = cfg.getProperties().keys();
			if (keys.hasMoreElements()) {
				String key = keys.nextElement();
				Object value = cfg.getProperties().get(key);
				Element p = document.createElement("property");
				p.setAttribute("key", key);
				if (value != null)
					p.setAttribute("value", value.toString());
				c.appendChild(p);			
			}			
		}
		
		return ret;
	}

	
	protected Map<String, Bundle> init(boolean b) {
		Map<String, Bundle> bundles = new HashMap<String, Bundle>();
		String sBundles = System.getProperty("bundles", "./bundles.csv");
		File fBundles = new File(sBundles);
		if (fBundles.exists()) {
			try (FileReader fr = new FileReader(fBundles)) {
				LineNumberReader lnr = new LineNumberReader(fr);
				String s = null;
				while((s = lnr.readLine()) != null) {
					String[] sp = s.split(",");
					Bundle bundle = new Bundle();
					bundle.setPos(Integer.parseInt(sp[0]));
					bundle.setText(sp[1]);
					bundle.setName(sp[2]);
					bundle.setVersion(sp[3]);
					String pkgName = "";
					for(int i=4; i<sp.length; i++) {
						String sAdd = sp[i];
						sAdd = sAdd.replaceAll("'", "");
						if (pkgName.equals(""))
							pkgName = sAdd;
						else
							pkgName = pkgName + "," + sp[i];
					}
					bundle.setPkgName(pkgName);
					bundles.put(sp[2], bundle);
					if (b)
						bundles.put(sp[1], bundle);
				}
				lnr.close();
				logger.debug("Loaded {} bundles", bundles.size());
			}
			catch(Exception e) {
				logger.error("Failed to load from file", e);
			}
		}
		return bundles;
	}
	
	public static void main(final String[] args) {
		try {
			SlingModelReader reader = new SlingModelReader();
			Model model = reader.readModel(args[0]);
			if (args.length > 1) 
				reader.printToXML(model, args[1]);
		}
		catch(Exception e) {
			logger.error("The model could not be read", e);
		}
	}
}
