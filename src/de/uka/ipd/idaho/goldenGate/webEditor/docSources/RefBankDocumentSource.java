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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.goldenGate.webEditor.DocumentSource;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefConstants;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.plugins.bibRefs.refBank.RefBankClient;
import de.uka.ipd.idaho.plugins.bibRefs.refBank.RefBankClient.BibRef;
import de.uka.ipd.idaho.plugins.bibRefs.refBank.RefBankClient.BibRefIterator;

/**
 * Document source fetching document meta data from RefBank.
 * 
 * @author sautter
 */
public class RefBankDocumentSource extends DocumentSource implements BibRefConstants {
	
	private RefBankClient rbk;
	
	/**
	 * @param name
	 */
	public RefBankDocumentSource() {
		super("RefBank");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.markupWizard.online.DocumentSource#init()
	 */
	public void init() {
		Settings set = Settings.loadSettings(new File(this.dataPath, "config.RefBank.cnfg"));
		String refBankNodeUrl = set.getSetting("refBankNodeUrl");
		if (refBankNodeUrl == null)
			throw new RuntimeException("RefBank node URL missing.");
		this.rbk = new RefBankClient(refBankNodeUrl);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.markupWizard.online.DocumentSource#findRefData(de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData)
	 */
	public RefData[] findRefData(RefData query) throws IOException {
		
		//	try ID access
		String rbkId = query.getIdentifier(this.getName());
		if (rbkId != null) {
			BibRef br = this.rbk.getRef(rbkId);
			if (br != null) {
				RefData rd = this.getRefData(br);
				if (rd != null) {
					RefData[] rds = {rd};
					return rds;
				}
			}
		}
		
		//	get query data
		String[] extIdTypes = query.getIdentifierTypes();
		String extIdType = (((extIdTypes == null) || (extIdTypes.length == 0)) ? null : extIdTypes[0]);
		String extId = ((extIdType == null) ? null : query.getIdentifier(extIdType));
		int year = -1;
		try {
			year = Integer.parseInt(query.getAttribute(YEAR_ANNOTATION_TYPE));
		} catch (Exception e) {}
		
		//	do search
		BibRefIterator brit = this.rbk.findRefs(null, query.getAttribute(AUTHOR_ANNOTATION_TYPE), query.getAttribute(TITLE_ANNOTATION_TYPE), year, this.getOrigin(query), extId, extIdType, -1, false);
		ArrayList rdList = new ArrayList();
		while (brit.hasNextRef()) {
			BibRef br = brit.getNextRef();
			RefData rd = this.getRefData(br);
			if (rd != null)
				rdList.add(rd);
		}
		return ((RefData[]) rdList.toArray(new RefData[rdList.size()]));
	}
	private String getOrigin(RefData query) {
		String origin = query.getAttribute(JOURNAL_NAME_ANNOTATION_TYPE);
		if (origin != null) {
			String vd = query.getAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE);
			if (vd != null) {
				origin += (" " + vd);
				return origin;
			}
			String id = query.getAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE);
			if (id != null) {
				origin += (" " + id);
				return origin;
			}
			String nd = query.getAttribute(NUMERO_DESIGNATOR_ANNOTATION_TYPE);
			if (nd != null) {
				origin += (" " + nd);
				return origin;
			}
		}
		
		origin = query.getAttribute(VOLUME_TITLE_ANNOTATION_TYPE);
		if (origin != null)
			return origin;
		
		origin = query.getAttribute(PUBLISHER_ANNOTATION_TYPE);
		if (origin != null) {
			String location = query.getAttribute(LOCATION_ANNOTATION_TYPE);
			if (location != null)
				origin = (location + ": " + origin);
			return origin;
		}
		
		origin = query.getAttribute(LOCATION_ANNOTATION_TYPE);
		if (origin != null)
			return origin;
		
		return null;
	}
	private RefData getRefData(BibRef br) throws IOException {
		if (br.getRefParsed() == null)
			return null;
		return BibRefUtils.modsXmlToRefData(SgmlDocumentReader.readDocument(new StringReader(br.getRefParsed())));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.markupWizard.online.DocumentSource#getDocumentFormatName(de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData)
	 */
	public String getDocumentFormatName(RefData rd) throws IOException {
		return null; // we're only catering meta data, no way of determining data format
	}
}
