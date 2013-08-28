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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.easyIO.web.WebAppHost.AuthenticationProvider;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet;
import de.uka.ipd.idaho.goldenGateServer.dio.client.GoldenGateDioClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientServlet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * Online editor servlet that works on a backing GoldenGATE DIO, so document
 * processing can take place in the web front-end of a GoldenGATE Server.
 * 
 * @author sautter
 */
public class DioOnlineEditorServlet extends OnlineEditorServlet {
	private String authServletName = null;
	private AuthenticatedWebClientServlet authServlet = null;
	private WebAppHost webAppHost;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	connect to host for authentication and session handling
		this.webAppHost = WebAppHost.getInstance(this.getServletContext());
		
		//	connect to authenticated web client servlet backend access
		this.authServletName = this.getSetting("AuthServletName");
		if (this.authServletName == null)
			throw new RuntimeException("Unable to link to authentication source.");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		//	ensure authentication is with backing GoldenGATE server if local storage unavailable
		if (!this.isLocalDocumentStoreAvailable() && (this.getAuthClient(request.getSession(false)) == null)) {
			HtmlPageBuilder lpb = this.webAppHost.getLoginPageBuilder(this, request, response, "includeBody", null);
			this.sendHtmlPage(lpb);
			return;
		}
		
		//	if so, let super class do the heavy lifting
		super.doGet(request, response);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.String)
	 */
	protected boolean doGet(HttpServletRequest request, HttpServletResponse response, String userName) throws IOException {
		String pathInfo = request.getPathInfo();
		if (pathInfo == null)
			return false;
		
		//	let super class handle this one
		return super.doGet(request, response, userName);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		//	ensure authentication is with backing GoldenGATE server if local storage unavailable
		if (!this.isLocalDocumentStoreAvailable() && (this.getAuthClient(request.getSession(false)) == null)) {
			HtmlPageBuilder lpb = this.webAppHost.getLoginPageBuilder(this, request, response, "includeBody", null);
			this.sendHtmlPage(lpb);
			return;
		}
		
		//	if so, let super class do the heavy lifting
		super.doPost(request, response);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#writeUploadForm(de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder, java.lang.String)
	 */
	public void writeUploadForm(HtmlPageBuilder hpb, String forwardUrl) throws IOException {
		
		// no local storage, check if backing DIO accessible
		if (!this.isLocalDocumentStoreAvailable()) {
			AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(hpb.request.getSession(false));
			if (!AuthenticatedWebClientServlet.GOLDEN_GATE_SERVER_AUTHENTICATION_PROVIDER_NAME.equals(ap.getName())) {
				hpb.writeLine("<table class=\"onlineEditorUploadTable\">");
				hpb.writeLine("<tr>");
				hpb.writeLine("<td class=\"onlineEditorUploadTableHeader\">");
				hpb.writeLine("Uploading new documents is not possible without authentication via GoldenGATE Server.");
				hpb.writeLine("</td>");
				hpb.writeLine("</tr>");
				hpb.writeLine("</table>");
				return;
			}
		}
		
		//	we're fine, provide form
		super.writeUploadForm(hpb, forwardUrl);
	}
	
	/**
	 * Load a document. This implementation attempts to checkout the document
	 * from the backing DIO.
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#loadDocument(javax.servlet.http.HttpSession, java.lang.String, de.uka.ipd.idaho.gamta.util.ProgressMonitor, boolean)
	 */
	protected MutableAnnotation loadDocument(HttpSession session, String docId, ProgressMonitor pm, boolean forProcessing) throws IOException {
		AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(session);
		if (ap == null)
			throw new IOException("Cannot load document without authentication.");
		
		//	check if we can use DIO
		if (AuthenticatedWebClientServlet.GOLDEN_GATE_SERVER_AUTHENTICATION_PROVIDER_NAME.equals(ap.getName())) {
			GoldenGateDioClient dioClient = new GoldenGateDioClient(this.getAuthClient(session));
			return dioClient.checkoutDocument(docId, pm);
		}
		
		//	let super class handle this one
		return super.loadDocument(session, docId, pm, forProcessing);
	}
	
	/**
	 * Save a document. This implementation attempts to update the document in
	 * the backing DIO. Accordingly, it returns the respective upload protocol,
	 * which may include reports from other server components plugged onto the
	 * DIO.
	 * @see de.uka.ipd.idaho.goldenGate.webEditor.OnlineEditorServlet#saveDocument(javax.servlet.http.HttpSession, de.uka.ipd.idaho.gamta.MutableAnnotation, java.lang.String, de.uka.ipd.idaho.gamta.util.ProgressMonitor, boolean)
	 */
	protected String[] saveDocument(HttpSession session, MutableAnnotation doc, String docName, ProgressMonitor pm, boolean isImport) throws IOException {
		AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(session);
		if (ap == null)
			throw new IOException("Cannot store document without authentication.");
		
		//	check if we can use DIO
		if (AuthenticatedWebClientServlet.GOLDEN_GATE_SERVER_AUTHENTICATION_PROVIDER_NAME.equals(ap.getName())) {
			GoldenGateDioClient dioClient = new GoldenGateDioClient(this.getAuthClient(session));
			if (isImport)
				return dioClient.uploadDocument(doc, docName, pm);
			else {
				String[] up = dioClient.updateDocument(doc, docName, pm);
				dioClient.releaseDocument((String) doc.getAttribute(DOCUMENT_ID_ATTRIBUTE));
				return up;
			}
		}
		
		//	let super class handle this one
		return super.saveDocument(session, doc, docName, pm, isImport);
	}
	
	private AuthenticatedClient getAuthClient(HttpSession session) throws IOException {
		AuthenticatedWebClientServlet authServlet = this.getAuthServlet();
		if (authServlet == null)
			throw new IOException("Cannot access GoldenGATE Server with current session.");
		AuthenticatedClient authClient = authServlet.getAuthenticatedClient(session);
		if (authClient == null)
			throw new IOException("Cannot access GoldenGATE Server with current session.");
		return authClient;
	}
	private AuthenticatedWebClientServlet getAuthServlet() {
		if (this.authServlet != null)
			return this.authServlet;
		this.authServlet = ((AuthenticatedWebClientServlet) this.webAppHost.getServlet(this.authServletName));
		if (this.authServlet != null)
			return this.authServlet;
		else return null;
	}
}