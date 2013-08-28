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
package de.uka.ipd.idaho.goldenGate.webEditor.docSources;

import java.io.IOException;
import java.util.Properties;

import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.goldenGate.webEditor.DocumentSource;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefDataSource;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.plugins.bibRefs.dataSources.InternetArchiveRefDataSource;

/**
 * Document source fetching meta data from BHL's mirror at Internet Archive.
 * 
 * @author sautter
 */
public class BhlDocumentSource extends DocumentSource {
	private BibRefDataSource bhl;
	
	/**
	 * @param name
	 */
	public BhlDocumentSource() {
		super("BHL");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.DocumentSource#init()
	 */
	public void init() {
		this.bhl = new InternetArchiveRefDataSource("BHL", "biodiversity") {
			{this.label = "Biodiversity Heritage Library";}
			public boolean isSuitableID(String docId) {
				return docId.matches("[a-z0-9]++");
			}
		};
		this.bhl.setDataProvider(new AnalyzerDataProviderFileBased(this.dataPath));
		this.bhl.init();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.markupWizard.online.DocumentSource#findRefData(de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData)
	 */
	public RefData[] findRefData(RefData query) throws IOException {
		Properties search = new Properties();
		String[] qans = query.getAttributeNames();
		for (int a = 0; a < qans.length; a++)
			search.setProperty(qans[a], query.getAttribute(qans[a]).trim());
		return this.bhl.findRefData(search);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.markupWizard.online.DocumentSource#getDocumentFormatName(de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData)
	 */
	public String getDocumentFormatName(RefData rd) throws IOException {
		return "<OCR Image PDF Document Format>";
	}
}
