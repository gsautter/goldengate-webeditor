/*
 * Copyright (c) 2006-2008, IPD Boehm, Universitaet Karlsruhe (TH)
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
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) AND CONTRIBUTORS 
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

package de.uka.ipd.idaho.goldenGate.webEditor.dio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.util.constants.LiteratureConstants;
import de.uka.ipd.idaho.goldenGate.CustomFunction;
import de.uka.ipd.idaho.goldenGate.plugins.GoldenGatePlugin;
import de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * This linker links an SRS search result to editing facilities that allow
 * authorized users to correct and enhance search results in the browser.
 * 
 * @author sautter
 */
public class DioOnlineEditorLinker extends SearchResultLinker implements LiteratureConstants {
	
	private DioOnlineEditorServlet onlineEditorServlet = null;
	private String onlineEditorServletName = null;
	private String onlineEditorServletPath = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker#getName()
	 */
	public String getName() {
		return "Online Editor Linker";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker#init()
	 */
	protected void init() {
		Settings set = Settings.loadSettings(new File(this.dataPath, "config.cnfg"));
		this.onlineEditorServletName = set.getSetting("onlineEditorServletName");
		this.onlineEditorServletPath = set.getSetting("onlineEditorServletPath");
	}
	
	private DioOnlineEditorServlet getOnlineEditorServlet() {
		if (this.onlineEditorServlet != null)
			return this.onlineEditorServlet;
		
		//	link to online editor servlet
		this.onlineEditorServlet = ((DioOnlineEditorServlet) WebAppHost.getInstance(this.parent.getServletContext()).getServlet(this.onlineEditorServletName));
		
		//	servlet not loaded yet, start wakeup thread, wait, and recurse
		if (this.onlineEditorServlet == null) {
			if (this.onlineEditorServletPath != null) {
				final Exception[] wakeupCallException = {null};
				Thread oleWt = new Thread() {
					public void run() {
						try {
							URL oleUrl = new URL(onlineEditorServletPath);
							InputStream in = oleUrl.openStream();
							in.read();
							in.close();
						}
						catch (Exception e) {
							e.printStackTrace(System.out);
							wakeupCallException[0] = e;
						}
					}
				};
				oleWt.start();
				try {
					oleWt.join();
				} catch (InterruptedException ie) {}
				if (wakeupCallException[0] == null)
					return this.getOnlineEditorServlet();
			}
		}
		
		//	servlet loaded, get custom functions
		return this.onlineEditorServlet;
	}
	
	private CustomFunction[] getCustomFunctions() {
		if (this.customFunctions != null)
			return this.customFunctions;
		
		//	link to online editor servlet
		DioOnlineEditorServlet oes = this.getOnlineEditorServlet();
		
		//	servlet not loaded yet
		if (oes == null)
			return new CustomFunction[0];
		
		//	servlet loaded, get custom functions
		System.out.println("OEL: online editor servlet found");
		GoldenGatePlugin[] ggps = oes.getGoldenGateInstance().getPlugins();
		System.out.println(" - got " + ggps.length + " GG plugins");
		for (int p = 0; p < ggps.length; p++)
			if (ggps[p] instanceof CustomFunction.Manager) {
				System.out.println(" - found custom function manager");
				ArrayList cfList = new ArrayList();
				String[] cfns = ((CustomFunction.Manager) ggps[p]).getResourceNames();
				System.out.println(" - investigating " + cfns.length + " custom functions");
				for (int f = 0; f < cfns.length; f++) {
					CustomFunction cf = ((CustomFunction.Manager) ggps[p]).getCustomFunction(cfns[f]);
					if ((cf != null) && cf.useContextMenu) {
						System.out.println("   - got useful custom function: " + cf.label);
						cfList.add(cf);
					}
				}
				System.out.println(" - got " + cfList.size() + " usable custom functions");
				this.customFunctions = ((CustomFunction[]) cfList.toArray(new CustomFunction[cfList.size()]));
				return this.customFunctions;
			}
		return new CustomFunction[0];
	}
	private CustomFunction[] customFunctions = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker#writePageHeadExtensions(de.uka.ipd.idaho.gamta.MutableAnnotation, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageHeadExtensions(MutableAnnotation doc, HtmlPageBuilder hpb) throws IOException {
		hpb.writeLine("<script type=\"text/javascript\">");
		hpb.writeLine("var openingDocumentWindow;");
		hpb.writeLine("function openDocumentWindow(path, title) {");
		hpb.writeLine("  if (openingDocumentWindow != null) {");
		hpb.writeLine("    window.setTimeout(('openDocumentWindow(\\'' + path + '\\', \\'' + title + '\\')'), 250);");
		hpb.writeLine("    return;");
		hpb.writeLine("  }");
		hpb.writeLine("  openingDocumentWindow = window.open(path, title, 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes');");
		hpb.writeLine("  if (openingDocumentWindow)");
		hpb.writeLine("    linkDocumentWindow();");
		hpb.writeLine("  else {");
		hpb.writeLine("    alert('Please deactivate your popup blocker for this page\\nso JavaScript can open document processing windows.');");
		hpb.writeLine("    openDocumentWindow(path, title);");
		hpb.writeLine("  }");
		hpb.writeLine("}");
		hpb.writeLine("function linkDocumentWindow() {");
		hpb.writeLine("  if (openingDocumentWindow == null)");
		hpb.writeLine("    return;");
		hpb.writeLine("  if (openingDocumentWindow.requestFinished) {");
		hpb.writeLine("    openingDocumentWindow.requestFinished = function() {");
		hpb.writeLine("        if (location.href.indexOf('" + CACHE_CONTROL_PARAMETER + "=" + FORCE_CACHE + "') == -1)");
		hpb.writeLine("          location.href = (location.href + ((location.href.indexOf('?') == -1) ? '?' : '&') + '" + CACHE_CONTROL_PARAMETER + "=" + FORCE_CACHE + "');");
		hpb.writeLine("        else location.reload();");
		hpb.writeLine("      };");
		hpb.writeLine("    openingDocumentWindow = null;");
		hpb.writeLine("  }");
		hpb.writeLine("  else window.setTimeout('linkDocumentWindow()', 250);");
		hpb.writeLine("}");
		hpb.writeLine("</script>");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker#getAnnotationLinks(de.uka.ipd.idaho.gamta.Annotation)
	 */
	public SearchResultLink[] getAnnotationLinks(Annotation annotation) {
		if (annotation instanceof QueriableAnnotation) {
			String docId = annotation.getDocumentProperty(MASTER_DOCUMENT_ID_ATTRIBUTE);
			if (docId == null)
				docId = annotation.getDocumentProperty(DOCUMENT_ID_ATTRIBUTE);
			if (docId == null)
				return new SearchResultLink[0];
			ArrayList cfLinkList = new ArrayList();
			CustomFunction[] cfs = this.getCustomFunctions();
			for (int f = 0; f < cfs.length; f++) {
//				System.out.println("Testing CF " + cfs[f].label + " on detail " + annotation.getType());
				if (cfs[f].displayFor((QueriableAnnotation) annotation)) try {
//					System.out.println(" ==> show");
					cfLinkList.add(new SearchResultLink(FUNCTIONALITY,
						this.getClass().getName(),
						cfs[f].label, 
						null, // TODO: insert name of online editor servlet (maybe GG Logo)
						cfs[f].toolTip,
						"", 
						"openDocumentWindow('" + this.onlineEditorServletPath + "/runDp?docId=" + URLEncoder.encode(docId, "UTF-8") + "&annotId=" + URLEncoder.encode(annotation.getAnnotationID(), "UTF-8") + "&dpName=" + URLEncoder.encode(cfs[f].getDocumentProcessorName(), "UTF-8") + "&dpLabel=" + URLEncoder.encode(("Running " + cfs[f].label), "UTF-8") + "', ('Running " + cfs[f].label + "')); return false;"
					));
				} catch (IOException ioe) { /* only unsupported encoding exceptions can happen, not with UTF-8, but Java don't know ... */ }
//				else System.out.println(" ==> hide");
			}
			return ((SearchResultLink[]) cfLinkList.toArray(new SearchResultLink[cfLinkList.size()]));
		}
		else return new SearchResultLink[0];
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.srs.webPortal.SearchPortalConstants.SearchResultLinker#getDocumentLinks(de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	public SearchResultLink[] getDocumentLinks(MutableAnnotation doc) {
		String docId = doc.getDocumentProperty(MASTER_DOCUMENT_ID_ATTRIBUTE);
		if (docId == null)
			docId = doc.getDocumentProperty(DOCUMENT_ID_ATTRIBUTE);
		if (docId == null)
			return new SearchResultLink[0];
		ArrayList cfLinkList = new ArrayList();
		CustomFunction[] cfs = this.getCustomFunctions();
		for (int f = 0; f < cfs.length; f++) {
//			System.out.println("Testing CF " + cfs[f].label + " on document");
			if (cfs[f].displayFor((QueriableAnnotation) doc)) try {
//				System.out.println(" ==> show");
				cfLinkList.add(new SearchResultLink(FUNCTIONALITY,
					this.getClass().getName(),
					cfs[f].label, 
					null, // TODO: insert name of online editor servlet (maybe GG Logo)
					cfs[f].toolTip,
					"", 
					"openDocumentWindow('" + this.onlineEditorServletPath + "/runDp?docId=" + URLEncoder.encode(docId, "UTF-8") + "&annotId=" + URLEncoder.encode(doc.getAnnotationID(), "UTF-8") + "&dpName=" + URLEncoder.encode(cfs[f].getDocumentProcessorName(), "UTF-8") + "&dpLabel=" + URLEncoder.encode(("Running " + cfs[f].label), "UTF-8") + "', ('Running " + cfs[f].label + "')); return false;"
				));
			} catch (IOException ioe) { /* only unsupported encoding exceptions can happen, not with UTF-8, but Java don't know ... */ }
//			else System.out.println(" ==> hide");
		}
		return ((SearchResultLink[]) cfLinkList.toArray(new SearchResultLink[cfLinkList.size()]));
	}
}