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

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.goldenGateServer.dio.GoldenGateDioConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * This modul integrates markup functionality into the web front-end of a
 * GoldenGATE Server, based on a DIO backed online editor servlet.
 * 
 * @author sautter
 */
public class DioEditorAuthModul extends AuthenticatedWebClientModul implements GoldenGateDioConstants {
	
	private DioOnlineEditorServlet dioEditorServlet = null;
	private String dioEditorServletName = null;
	private String dioEditorServletPath = null;
	
	private DioOnlineEditorServlet getDioEditorServlet() {
		if (this.dioEditorServlet != null)
			return this.dioEditorServlet;
		
		//	link to online editor servlet
		this.dioEditorServlet = ((DioOnlineEditorServlet) WebAppHost.getInstance(this.parent.getServletContext()).getServlet(this.dioEditorServletName));
		
		//	servlet not loaded yet, start wakeup thread, wait, and recurse
		if ((this.dioEditorServlet == null) && (this.dioEditorServletPath != null)) {
			final Exception[] oleException = {null};
			Thread oleWt = new Thread() {
				public void run() {
					try {
						URL oleUrl = new URL(dioEditorServletPath);
						InputStream in = oleUrl.openStream();
						in.read();
						in.close();
					}
					catch (Exception e) {
						e.printStackTrace(System.out);
						oleException[0] = e;
					}
				}
			};
			oleWt.start();
			try {
				oleWt.join();
			} catch (InterruptedException ie) {}
			if (oleException[0] == null)
				return this.getDioEditorServlet();
		}
		
		//	servlet loaded
		return this.dioEditorServlet;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#init()
	 */
	protected void init() {
		Settings set = Settings.loadSettings(new File(this.dataPath, "config.cnfg"));
		this.dioEditorServletName = set.getSetting("dioEditorServletName");
		this.dioEditorServletPath = set.getSetting("dioEditorServletPath");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return (authClient.hasPermission(UPLOAD_DOCUMENT_PERMISSION) && authClient.hasPermission(UPDATE_DOCUMENT_PERMISSION));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "Upload & Markup Documents";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getCssToInclude()
	 */
	public String getCssToInclude() {
		DioOnlineEditorServlet des = this.getDioEditorServlet();
		if (des == null)
			return null;
		String[] ctis = des.getCssStylesheets();
		if ((ctis == null) || (ctis.length == 0))
			return null;
		return ctis[0];
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageHeadExtensions(de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageHeadExtensions(HtmlPageBuilder pageBuilder) throws IOException {
		DioOnlineEditorServlet des = this.getDioEditorServlet();
		if (des != null)
			des.writePageHeadExtensions(pageBuilder);
		else pageBuilder.writeLine("<!-- Unable to get page head extensions from Online Editor Servlet -->");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getJavaScriptLoadCalls()
	 */
	public String[] getJavaScriptLoadCalls() {
		DioOnlineEditorServlet des = this.getDioEditorServlet();
		if (des != null)
			return des.getOnloadCalls();
		else return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getJavaScriptUnloadCalls()
	 */
	public String[] getJavaScriptUnloadCalls() {
		DioOnlineEditorServlet des = this.getDioEditorServlet();
		if (des != null)
			return des.getOnunloadCalls();
		else return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		return null; // no request handling or messages for now, markup wizard servlet does the job
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		DioOnlineEditorServlet des = this.getDioEditorServlet();
		
		//	no means of connecting to servlet, indicate so
		if (des == null) {
			pageBuilder.writeLine("<table class=\"onlineEditorResultTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td class=\"onlineEditorResultTableHead\">Markup Wizard Not Accessible</td>");
			pageBuilder.writeLine("</tr>");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td class=\"onlineEditorResultTableCell\">The online markup wizard is not accessible at this time, please try again later.</td>");
			pageBuilder.writeLine("</tr>");
			pageBuilder.writeLine("</table>");
			return;
		}
		
		//	write navigation links
		pageBuilder.writeLine("<table class=\"onlineEditorFunctionTable\">");
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td class=\"onlineEditorFunctionTableCell\">");
		pageBuilder.writeLine("<a class=\"onlineEditorFunctionLink\" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">My Documents</a>");
		pageBuilder.writeLine("&nbsp;&nbsp;");
		pageBuilder.writeLine("<a class=\"onlineEditorFunctionLink\" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "/upload" + "\">Upload / Import Document</a>");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		pageBuilder.writeLine("</table>");
		
		//	include upload form
		if (pageBuilder.request.getPathInfo().endsWith("/upload"))
			des.writeUploadForm(pageBuilder, (pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName()));
		
		//	inlude list of pending requests and list of documents awaiting further processing
		else {
			des.writeRequestList(pageBuilder);
			des.writeDocumentList(pageBuilder);
		}
	}
}