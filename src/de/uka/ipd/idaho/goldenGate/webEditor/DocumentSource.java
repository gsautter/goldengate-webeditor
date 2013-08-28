/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.goldenGate.webEditor;

import java.io.File;
import java.io.IOException;

import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;

/**
 * Instances of this class hide the details of individual sources of documents
 * and document meta data from the online wizard.
 * 
 * @author sautter
 */
public abstract class DocumentSource {
	
	/** the importer's data path */
	protected File dataPath;
	
	private String name;
	
	/** the folder used to cache documents in after import */
	protected File cacheFolder;

	/**
	 * Constructor
	 * @param name the name identifying the document import source (to be
	 *            provided by implementing classes); the name must not be empty,
	 *            must consist of 32 characters at most, and must consist only
	 *            of upper and lower case Latin letters, no whitespaces.
	 */
	protected DocumentSource(String name) {
		this.name = name;
	}
	
	/**
	 * Make the importer know its data path.
	 * @param dataPath the importer's data path
	 */
	public void setDataPath(File dataPath) {
		this.dataPath = dataPath;
		this.cacheFolder = new File(this.dataPath, "cache");
		if (!this.cacheFolder.exists())
			this.cacheFolder.mkdirs();
	}
	
	/**
	 * Initialize the importer. This method is invoked by the parent DIC after
	 * parent and data path are set. This default implementation does nothing,
	 * sub classes are welcome to overwrite it as needed.
	 */
	public void init() {}
	
	/**
	 * Shut down the importer. This method is invoked by the parent DIC right
	 * before shutdown. This default implementation does nothing, sub classes
	 * are welcome to overwrite it as needed.
	 */
	public void exit() {}
	
	/**
	 * Get the importer's name (the one provided to the constructor).
	 * @return the importer's name
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * Search the backing source for bibliographic references.
	 * @param query the reference data to use for the search.
	 * @return the search result
	 * @throws IOException
	 */
	public abstract RefData[] findRefData(RefData query) throws IOException;
	
	/**
	 * Obtain the name of the document format to use for importing a document
	 * described by a reference data set. This method should return null to
	 * indicate that an import source cannot determin the document format to
	 * use.
	 * @param rd the reference data set describing the document to import
	 * @return the name of the document format to use
	 * @throws IOException
	 */
	public abstract String getDocumentFormatName(RefData rd) throws IOException;
	
	/**
	 * Load document import sources from the JAR fiels in a given folder.
	 * @param folder the folder to load the document import sources from
	 * @return the document import sources loaded from the argument folder
	 */
	public static DocumentSource[] getDocumentImportSources(final File folder) {
		Object[] disObjects = GamtaClassLoader.loadComponents(
				folder,
				DocumentSource.class, 
				new ComponentInitializer() {
					public void initialize(Object component, String componentJarName) throws Exception {
						File dataPath = new File(folder, (componentJarName.substring(0, (componentJarName.length() - 4)) + "Data"));
						if (!dataPath.exists()) dataPath.mkdir();
						DocumentSource dis = ((DocumentSource) component);
						dis.setDataPath(dataPath);
						dis.init();
					}
				});
		DocumentSource[] diss = new DocumentSource[disObjects.length];
		for (int s = 0; s < disObjects.length; s++)
			diss[s] = ((DocumentSource) disObjects[s]);
		return diss;
	}
}