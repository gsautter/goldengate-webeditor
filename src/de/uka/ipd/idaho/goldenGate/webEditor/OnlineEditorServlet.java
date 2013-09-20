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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.web.FormDataReceiver;
import de.uka.ipd.idaho.easyIO.web.HtmlServlet;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.easyIO.web.FormDataReceiver.FieldValueInputStream;
import de.uka.ipd.idaho.easyIO.web.WebAppHost.AuthenticationProvider;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.DocumentRoot;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.util.AnnotationFilter;
import de.uka.ipd.idaho.gamta.util.ProgressMonitor;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler.AsynchronousRequest;
import de.uka.ipd.idaho.gamta.util.feedback.html.FeedbackFormBuilder.SubmitMode;
import de.uka.ipd.idaho.gamta.util.feedback.html.renderers.BufferedLineWriter;
import de.uka.ipd.idaho.goldenGate.CustomFunction;
import de.uka.ipd.idaho.goldenGate.GoldenGATE;
import de.uka.ipd.idaho.goldenGate.GoldenGateConfiguration;
import de.uka.ipd.idaho.goldenGate.configuration.ConfigurationUtils;
import de.uka.ipd.idaho.goldenGate.plugins.DocumentFormat;
import de.uka.ipd.idaho.goldenGate.plugins.DocumentFormatProvider;
import de.uka.ipd.idaho.goldenGate.plugins.DocumentProcessor;
import de.uka.ipd.idaho.goldenGate.plugins.GoldenGatePlugin;
import de.uka.ipd.idaho.goldenGate.plugins.MonitorableDocumentFormat;
import de.uka.ipd.idaho.goldenGate.plugins.MonitorableDocumentProcessor;
import de.uka.ipd.idaho.goldenGateServer.dst.DocumentStore;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder.HtmlPageBuilderHost;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefConstants;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefEditorFormHandler;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefTypeSystem;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * This servlet provides functionality to apply document processors from an
 * embedded GoldenGATE instance to documents stored in a centralized location,
 * and to upload documents to this location together with their meta data.
 * 
 * @author sautter
 */
public class OnlineEditorServlet extends HtmlServlet implements BibRefConstants {
	private static final int uploadMaxLength = (4 * 1024 * 1024); // 4MB for starters
	
	private BibRefTypeSystem refTypeSystem = null;
	private String[] refIdTypes;
	
	private DocumentSource[] docSources;
	
	private File uploadCacheFolder = null;
	
	private GoldenGateConfiguration ggConfig = null;
	private GoldenGATE goldenGate = null;
	private DocumentFormat[] loadDocFormats = {};
	
	private OnlineEditorRequestHandler requestHandler = null;
	
	private String servletPath = null;
	
	private static final String MARKUP_PROGRESS_TABLE_NAME = "MarkupProgress";
	private static final String DOCUMENT_ID_COLUMN_NAME = "docId";
	private static final String DOCUMENT_NAME_COLUMN_NAME = "docName";
	private static final String NEXT_FUNCTIONS_COLUMN_NAME = "nextFunctions";
	private static final String USER_NAME_COLUMN_NAME = "userName";
	
	private IoProvider io;
	private DocumentStore documentStore;
	
	/**
	 * Initialize the HTML servlet. This implementation starts the wrapped
	 * GoldenGATE instance and initializes the upload facilities. Sub classes
	 * overwriting this method thus have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	create request handler
		this.requestHandler = new OnlineEditorRequestHandler();
		
		//	set up upload caching
		this.uploadCacheFolder = new File(this.dataFolder, "UploadCache");
		this.uploadCacheFolder.mkdir();
		
		//	load document source connector plug-ins
		this.docSources = DocumentSource.getDocumentImportSources(new File(this.dataFolder, "DocSources"));
		
		//	collect identifier types
		ArrayList idTypes = new ArrayList();
		idTypes.add("Generic");
		for (int i = 0; i < this.docSources.length; i++)
			idTypes.add(this.docSources[i].getName());
		idTypes.addAll(Arrays.asList(this.getSetting("refIdTypes", "").trim().split("\\s+")));
		this.refIdTypes = ((String[]) idTypes.toArray(new String[idTypes.size()]));
		
		//	TODO_later load custom reference type system if configured
		this.refTypeSystem = BibRefTypeSystem.getDefaultInstance();
		
		//	set up local document storage
		if (!"true".equals(this.getSetting("documentStorageOff", "false")))
			this.createDocumentStore();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		
		//	check if any requests running
		if ((this.requestHandler != null) && (this.requestHandler.getRunningRequestCount() != 0))
			throw new ServletException("Unable to reload GoldenGATE, there are request running.");
		
		//	shut down GoldenGATE
		if (this.goldenGate != null) {
			this.goldenGate.exitShutdown();
			this.goldenGate = null;
			this.ggConfig = null;
		}
		
		//	read how to access GoldenGATE config
		String ggConfigName = this.getSetting("GgConfigName");
		String ggConfigHost = this.getSetting("GgConfigHost");
		String ggConfigPath = this.getSetting("GgConfigPath");
		if (ggConfigName == null)
			throw new ServletException("Unable to access GoldenGATE Configuration.");
		
		//	load configuration
		try {
			this.ggConfig = ConfigurationUtils.getConfiguration(ggConfigName, ggConfigPath, ggConfigHost, this.dataFolder);
		}
		catch (IOException ioe) {
			throw new ServletException("Unable to access GoldenGATE Configuration.", ioe);
		}
		
		//	check if we got a configuration from somewhere
		if (this.ggConfig == null)
			throw new ServletException("Unable to access GoldenGATE Configuration.");
		
		//	load GoldenGATE core
		try {
			this.goldenGate = GoldenGATE.openGoldenGATE(this.ggConfig, false, false);
		}
		catch (IOException ioe) {
			throw new ServletException("Unable to load GoldenGATE instance", ioe);
		}
		
		//	get available document formats capable of loading
		DocumentFormatProvider[] docFormatProviders = this.goldenGate.getDocumentFormatProviders();
		ArrayList loadDocFormatList = new ArrayList();
		for (int p = 0; p < docFormatProviders.length; p++) {
			String[] dfns = docFormatProviders[p].getLoadFormatNames();
			for (int f = 0; f < dfns.length; f++) {
				DocumentFormat df = docFormatProviders[p].getFormatForName(dfns[f]);
				if (df != null)
					loadDocFormatList.add(df);
			}
		}
		if (loadDocFormatList.size() != 0)
			this.loadDocFormats = ((DocumentFormat[]) loadDocFormatList.toArray(new DocumentFormat[loadDocFormatList.size()]));
		
		//	TODO use specialized online editor doc formats only, as they handle cutting out excerpts and modifying the document ID internally, before first saving.
		
		//	get custom functions to prefill cache
		this.fetchCustomFunctions();
	}
	
	private void createDocumentStore() {
		
		//	get IoProvider from host
		this.io = WebAppHost.getInstance(this.getServletContext()).getIoProvider();
		if (!this.io.isJdbcAvailable())
			return;
		
		//	create log table (512 bytes per record)
		TableDefinition td = new TableDefinition(MARKUP_PROGRESS_TABLE_NAME);
		td.addColumn(DOCUMENT_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(DOCUMENT_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 64);
		td.addColumn(NEXT_FUNCTIONS_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 352);
		td.addColumn(USER_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 64);
		if (!this.io.ensureTable(td, true))
			return;
		
		//	index log table
		this.io.indexColumn(MARKUP_PROGRESS_TABLE_NAME, DOCUMENT_ID_COLUMN_NAME);
		this.io.indexColumn(MARKUP_PROGRESS_TABLE_NAME, USER_NAME_COLUMN_NAME);
		
		//	set up document storage
		String documentStoragePath = this.getSetting("documentStoragePath");
		File documentFolder = ((documentStoragePath == null) ? new File(this.dataFolder, "Documents") : new File(documentStoragePath));
		documentFolder.mkdir();
		this.documentStore = new DocumentStore(documentFolder);
	}
	
	/**
	 * Test whether or not the local document store is enabled. Sub classes
	 * working with alternative storage locations might not require this and be
	 * configured accordingly.
	 * @return true if the internal storage is avilable, false otherwise
	 */
	protected boolean isLocalDocumentStoreAvailable() {
		return (this.documentStore != null);
	}
	
	private void fetchCustomFunctions() {
		if (this.customFunctions.size() != 0)
			return;
		GoldenGatePlugin[] ggps = this.getGoldenGateInstance().getPlugins();
		for (int p = 0; p < ggps.length; p++)
			if (ggps[p] instanceof CustomFunction.Manager) {
				String[] cfns = ((CustomFunction.Manager) ggps[p]).getResourceNames();
				for (int f = 0; f < cfns.length; f++) {
					CustomFunction cf = ((CustomFunction.Manager) ggps[p]).getCustomFunction(cfns[f]);
					if ((cf != null) && cf.usePanel)
						this.customFunctions.put(cfns[f], cf);
				}
			}
	}
	private TreeMap customFunctions = new TreeMap();
	
	/**
	 * This implementation shuts down the GoldenGATE instance to use. Sub
	 * classes overwriting this method thus have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.WebServlet#exit()
	 */
	protected void exit() {
		
		//	shut down GoldenGATE
		if (this.goldenGate != null) {
			this.goldenGate.exitShutdown();
			this.goldenGate = null;
			this.ggConfig = null;
		}
		
		//	shut down request handler
		if (this.requestHandler != null) {
			this.requestHandler.shutdown();
			this.requestHandler = null;
		}
		
		//	disconnect from database
		if (this.io != null)
			this.io.close();
	}
	
	/**
	 * Retrieve the GoldenGATE configuration the servlet works with.
	 * @return the GoldenGATE gonfiguration
	 */
	public GoldenGateConfiguration getGoldenGateConfiguration() {
		return this.ggConfig;
	}
	
	/**
	 * Retrieve the GoldenGATE instance wrapped in this servlet.
	 * @return the GoldenGATE instance
	 */
	public GoldenGATE getGoldenGateInstance() {
		return this.goldenGate;
	}
	
	/**
	 * Retrieve the asynchronous request handler running in the servlet.
	 * @return the request handler
	 */
	public AsynchronousRequestHandler getRequestHandler() {
		return this.requestHandler;
	}

	/**
	 * Retrieve the path leading to this servlet. This path is set after the
	 * first GET or POST request, i.e., right after the servlet is loaded.
	 * @return the servlet path
	 */
	public String getServletPath() {
		return this.servletPath;
	}
	
	private class OnlineEditorRequestHandler extends AsynchronousRequestHandler {
		OnlineEditorRequestHandler() {
			super(false);
		}
		public AsynchronousRequest buildAsynchronousRequest(HttpServletRequest request) throws IOException {
			return null; // we're creating the requests ourselves
		}
		protected boolean retainAsynchronousRequest(AsynchronousRequest ar, int finishedArCount) {
			
			/* client not yet notified that request is complete, we have to hold
			 * on to this one, unless last status update was more than 10 minutes
			 * ago, which indicates the client side is likely dead */
			if (!ar.isFinishedStatusSent())
				return (System.currentTimeMillis() < (ar.getLastAccessTime() + (1000 * 60 * 10)));
			
			/* client has not yet retrieved result, we have to hold on to this
			 * one, unless last status update was more than 10 minutes ago,
			 * which indicates the client side is likely dead */
			if (!ar.isResultSent())
				return (System.currentTimeMillis() < (ar.getLastAccessTime() + (1000 * 60 * 10)));
			
			/* retain the last 32 requests for a while, so users have the chance
			 * to close the window and do not see start page after a reload */
			if (this.getRequestsCount() < 32)
				return true;
			
			//	no need to retain any requests after client notified that it's finished, as document is stored by now
			return false;
		}
		protected HtmlPageBuilderHost getPageBuilderHost() {
			return OnlineEditorServlet.this;
		}
		protected void sendHtmlPage(HtmlPageBuilder hpb) throws IOException {
			OnlineEditorServlet.this.sendHtmlPage(hpb);
		}
		protected void sendPopupHtmlPage(HtmlPageBuilder hpb) throws IOException {
			OnlineEditorServlet.this.sendPopupHtmlPage(hpb);
		}
		public OnlineEditorRequest getOnlineEditorRequest(String arId) {
			return ((OnlineEditorRequest) this.getAsynchronousRequest(arId));
		}
		protected SubmitMode[] getSubmitModes(ArFeedbackRequest arfr) {
			String[] bs = arfr.fp.getButtons();
			if (bs.length == 0)
				return super.getSubmitModes(arfr);
			SubmitMode[] sms = new SubmitMode[bs.length];
			for (int b = 0; b < bs.length; b++)
				sms[b] = new SubmitMode(bs[b], bs[b], bs[b]);
			return sms;
		}
	}
	
	/**
	 * Asynchronous request running in the online editor servlet.
	 * 
	 * @author sautter
	 */
	protected abstract class OnlineEditorRequest extends AsynchronousRequest implements ProgressMonitor {
		private HttpSession session;
		private final Object sessionLock = new Object();
		private String[] uploadProtocol = null;
		/**
		 * Constructor
		 * @param id the request ID
		 * @param name the name of the request, for display purposes
		 * @param session the HTTP session to start with
		 */
		protected OnlineEditorRequest(String id, String name, HttpSession session) {
			super(id, name);
			this.session = session;
		}
		private int baseProgress = 0;
		private int maxProgress = 100;
		public void setBaseProgress(int baseProgress) {
			this.baseProgress = baseProgress;
		}
		public void setMaxProgress(int maxProgress) {
			this.maxProgress = maxProgress;
		}
		public void setProgress(int progress) {
			this.setPercentFinished(this.baseProgress + (((this.maxProgress - this.baseProgress) * progress) / 100));
		}
		public void setStep(String step) {
			this.setStatus(step);
		}
		public void setInfo(String info) {
			// let's ignore this one for now, we only have one status string
		}
		
		/**
		 * Store the upload protocol after a document has been stored. An HTML
		 * page displaying this upload protocol is sent to the client als the
		 * result of the request.
		 * @param uploadProtocol the upload protocol
		 */
		public void setUploadProtocol(String[] uploadProtocol) {
			this.uploadProtocol = uploadProtocol;
		}
		
		/**
		 * Set or reset the HTTP session this request uses for authentication.
		 * Sub classes should always call this method when touching a request,
		 * so to re-establish authentication should the user owning the request
		 * have logged out since first creating the request.
		 * @param authClient the autheticated client to use
		 */
		public void setSession(HttpSession session) {
			if ((this.session != null) && (webAppHost.getUserName(session) != null))
				return;
			this.session = session;
			synchronized (this.sessionLock) {
				this.sessionLock.notify();
			}
		}
		
		/**
		 * Retrieve the HTTP session authenticating this request. If no session
		 * has been set via the setSession() method, this method blocks until
		 * some other code calls the setSession() method to re-establish
		 * authentication. Sub classes should call this method reight before
		 * each action requiring authentication.
		 * @return the authenticated client
		 */
		protected HttpSession getSession() {
			if ((this.session != null) && (webAppHost.getUserName(session) != null))
				return this.session;
			synchronized (this.sessionLock) {
				this.setStep("Waiting for user to re-authenticate with server ...");
				try {
					this.sessionLock.wait();
				} catch (InterruptedException ie) {}
				return this.session;
			}
		}
		public boolean sendResult(HttpServletRequest request, HttpServletResponse response) throws IOException {
			if (this.uploadProtocol == null)
				return false;
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			HtmlPageBuilder hpb = new HtmlPageBuilder(OnlineEditorServlet.this, request, response) {
				protected void include(String type, String tag) throws IOException {
					if ("includeBody".equals(type))
						this.includeBody();
					else super.include(type, tag);
				}
				private void includeBody() throws IOException {
					
					//	write update result
					this.writeLine("<table class=\"onlineEditorResultTable\">");
					this.writeLine("<tr>");
					this.writeLine("<td class=\"onlineEditorResultTableHead\">Document Updated Successfully</td>");
					this.writeLine("</tr>");
					for (int l = 0; l < uploadProtocol.length; l++) {
						this.writeLine("<tr>");
						this.writeLine("<td class=\"onlineEditorResultTableCell\">" + html.escape(uploadProtocol[l]) + "</td>");
						this.writeLine("</tr>");
					}
					this.writeLine("</table>");
					
					//	write additional information
					this.writeLine("<table class=\"onlineEditorFunctionTable\">");
					this.writeLine("<tr>");
					this.writeLine("<td class=\"onlineEditorFunctionTableCell\">Thank you for contributing. Your changes may take a few minute to show because of caching effects.</td>");
					this.writeLine("</tr>");
					this.writeLine("<tr>");
					this.writeLine("<td class=\"onlineEditorFunctionTableCell\"><a href=\"#\" onclick=\"window.close();\">Close Window</a></td>");
					this.writeLine("</tr>");
					this.writeLine("</table>");
				}
				protected String getPageTitle(String title) {
					return ((name == null) ? super.getPageTitle(title) : name);
				}
			};
			sendPopupHtmlPage(hpb);
			this.resultSent();
			return true;
		}
	}
	
	private class DocumentProcessorOER extends OnlineEditorRequest {
		private DocumentProcessor dp;
		private String docId;
		private String annotationId;
		DocumentProcessorOER(String id, String label, HttpSession session, DocumentProcessor dp, String docId, String annotationId) {
			super(id, label, session);
			this.dp = dp;
			this.docId = docId;
			this.annotationId = annotationId;
		}
		
		protected void process() throws Exception {
			
			//	load document
			this.setStep("Loading document ...");
			this.setBaseProgress(0);
			this.setMaxProgress(10);
			this.setProgress(0);
			MutableAnnotation doc = loadDocument(this.getSession(), this.docId, this, true);
			
			//	find target annotation
			MutableAnnotation data = doc;
			System.out.println(" - got document");
			if (this.annotationId != null) {
				System.out.println(" - got target annotation ID: " + this.annotationId);
				MutableAnnotation[] annots = doc.getMutableAnnotations();
				System.out.println(" - checking " + annots.length + " annotations");
				for (int a = 0; a < annots.length; a++) {
					if (this.annotationId.equals(annots[a].getAnnotationID())) {
						data = annots[a];
						System.out.println(" - found target annotation " + data.toXML());
						break;
					}
				}
			}
			
			//	run document processor
			this.setStep("Processing document ..."); // processor name is too cryptic
			this.setBaseProgress(10);
			this.setMaxProgress(90);
			this.setProgress(0);
			Properties parameters = new Properties();
			parameters.setProperty(DocumentProcessor.INTERACTIVE_PARAMETER, DocumentProcessor.INTERACTIVE_PARAMETER);
			if (this.dp instanceof MonitorableDocumentProcessor)
				((MonitorableDocumentProcessor) this.dp).process(data, parameters, this);
			else this.dp.process(data, parameters);
			documentProcessed(doc, this.getSession(), this.dp.getName());
			
			//	store document
			this.setStep("Storing document ...");
			this.setBaseProgress(90);
			this.setMaxProgress(100);
			this.setProgress(0);
			String[] uploadProtocol = saveDocument(this.getSession(), doc, null, this, false);
			this.setUploadProtocol(uploadProtocol);
			this.setProgress(100);
			this.setStep("Document stored, finished.");
		}
	}
	
	private class DocumentLoaderOER extends OnlineEditorRequest {
		private FormDataReceiver uploadData;
		private File uploadCacheFile;
		private DocumentFormat df;
		DocumentLoaderOER(String id, String title, HttpSession session, FormDataReceiver uploadData, DocumentFormat df) {
			super(id, title, session);
			this.uploadData = uploadData;
			this.df = df;
		}
		protected void process() throws Exception {
			this.setBaseProgress(0);
			this.setMaxProgress(90);
			
			//	prepare document import
			final InputStream docIn;
			final int docByteCount;
			final String docName;
			
			//	cache data from URL
			String uploadUrlString = this.uploadData.getFieldValue("uploadUrl");
			if ((uploadUrlString != null) && (uploadUrlString.trim().length() != 0)) {
				this.uploadCacheFile = new File(uploadCacheFolder, (this.id + ".cache"));
				OutputStream cacheOut = new BufferedOutputStream(new FileOutputStream(this.uploadCacheFile));
				URL uploadUrl = new URL(uploadUrlString);
				InputStream uploadIn = new BufferedInputStream(uploadUrl.openStream());
				byte[] uploadBytes = new byte[2048];
				int read;
				while ((read = uploadIn.read(uploadBytes, 0, uploadBytes.length)) != -1)
					cacheOut.write(uploadBytes, 0, read);
				cacheOut.flush();
				cacheOut.close();
				uploadIn.close();
				docIn = new BufferedInputStream(new FileInputStream(this.uploadCacheFile));
				docByteCount = ((int) this.uploadCacheFile.length());
				while (uploadUrlString.endsWith("/"))
					uploadUrlString = uploadUrlString.substring(0, (uploadUrlString.length() - 1));
				if (uploadUrlString.indexOf('/') == -1)
					docName = uploadUrlString;
				else docName = uploadUrlString.substring(uploadUrlString.lastIndexOf('/') + 1);
			}
			
			//	open uploaded file
			else {
				FieldValueInputStream fvDocIn = this.uploadData.getFieldByteStream("uploadFile");
				docIn = fvDocIn;
				docByteCount = fvDocIn.fieldLength;
				docName = this.uploadData.getSourceFileName("uploadFile");
			}
			
			//	load document
			MutableAnnotation doc;
			if (this.df instanceof MonitorableDocumentFormat)
				doc = ((MonitorableDocumentFormat) this.df).loadDocument(docIn, this);
			else {
				doc = this.df.loadDocument(new FilterInputStream(docIn) {
					int bytesRead = 0;
					public int read() throws IOException {
						int r = super.read();
						if (r != -1) {
							this.bytesRead++;
							setProgress((this.bytesRead * 100) / docByteCount);
						}
						return r;
					}
					public int read(byte[] b) throws IOException {
						return this.read(b, 0, b.length);
					}
					public int read(byte[] b, int off, int len) throws IOException {
						int r = super.read(b, off, len);
						if (r != -1) {
							this.bytesRead += r;
							setProgress((this.bytesRead * 100) / docByteCount);
						}
						return r;
					}
				});
			}
			docIn.close();
			if (!doc.hasAttribute(DOCUMENT_ID_ATTRIBUTE))
				doc.setAttribute(DOCUMENT_ID_ATTRIBUTE, doc.getAnnotationID());
			
			//	add document meta data
			RefData docRefData = this.getRefData();
			BibRefUtils.setDocAttributes(doc, docRefData);
			if (docRefData.hasAttribute(PUBLICATION_TYPE_ATTRIBUTE)) {
				MutableAnnotation mods = SgmlDocumentReader.readDocument(new StringReader(refTypeSystem.toModsXML(docRefData)));
				BibRefUtils.cleanModsXML(mods);
				doc.insertTokensAt(mods, 0);
				MutableAnnotation docMods = doc.addAnnotation("mods:mods", 0, mods.size());
				docMods.copyAttributes(mods);
				Annotation[] modsAnnotations = mods.getAnnotations();
				for (int a = 0; a < modsAnnotations.length; a++) {
					if (DocumentRoot.DOCUMENT_TYPE.equals(modsAnnotations[a].getType()))
						continue;
					if ("mods:mods".equals(modsAnnotations[a].getType()))
						docMods.copyAttributes(modsAnnotations[a]);
					else if (modsAnnotations[a].getType().startsWith("mods:"))
						docMods.addAnnotation(modsAnnotations[a]);
				}
			}
			AnnotationFilter.removeDuplicates(doc);
			
			//	notify sub class
			documentProcessed(doc, this.getSession(), this.df.getName());
			
			//	store document ...
			this.setStep("Storing document ...");
			this.setBaseProgress(90);
			this.setMaxProgress(100);
			String[] uploadProtocol = saveDocument(this.getSession(), doc, docName, this, true);
			this.setUploadProtocol(uploadProtocol);
			this.setProgress(100);
			this.setStep("Document stored, finished.");
		}
		private RefData getRefData() throws IOException {
			RefData rd = new RefData();
			this.addRefDataAttribute(rd, AUTHOR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, YEAR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, TITLE_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, JOURNAL_NAME_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, PUBLISHER_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, LOCATION_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, EDITOR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, VOLUME_TITLE_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, PAGINATION_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, VOLUME_DESIGNATOR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, ISSUE_DESIGNATOR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, NUMERO_DESIGNATOR_ANNOTATION_TYPE);
			this.addRefDataAttribute(rd, PUBLICATION_URL_ANNOTATION_TYPE);
			if (!rd.hasAttribute(PUBLICATION_URL_ANNOTATION_TYPE) && this.uploadData.hasField("uploadUrl"))
				rd.setAttribute(PUBLICATION_URL_ANNOTATION_TYPE, this.uploadData.getFieldValue("uploadUrl"));
			if (!rd.hasAttribute(PUBLICATION_TYPE_ATTRIBUTE)) {
				String type = refTypeSystem.classify(rd);
				if (type != null)
					rd.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
			}
			for (int i = 0; i < refIdTypes.length; i++)
				this.addRefDataAttribute(rd, ("ID-" + refIdTypes[i]));
			return rd;
		}
		private void addRefDataAttribute(RefData rd, String attribute) throws IOException {
			String value = this.uploadData.getFieldValue(attribute);
			if (value == null)
				return;
			value = value.trim();
			if (value.length() == 0)
				return;
			if (AUTHOR_ANNOTATION_TYPE.equals(attribute) || EDITOR_ANNOTATION_TYPE.equals(attribute)) {
				String[] values = value.split("\\s*\\&\\s*");
				for (int v = 0; v < values.length; v++)
					rd.addAttribute(attribute, values[v]);
			}
			else rd.setAttribute(attribute, value);
		}
		protected void cleanup() throws Exception {
			this.uploadData.dispose();
			if (this.uploadCacheFile != null)
				this.uploadCacheFile.delete();
		}
	}
	
	/**
	 * Receive notification that an asynchronous request has completed running
	 * on a document. This method is called right after a document processor is
	 * finished, and in particular before the document is stored in the backing
	 * server. Sub class specific online editor request should do the exact
	 * same. This method may further modify the document. This default
	 * implementation does nothing, sub classes are welcome to overwrite it as
	 * needed.
	 * @param doc the document that just finished processing
	 * @param session the HTTP session authenticating the user who did the
	 *            processing
	 * @param resName the name of the GoldenGATE resource used
	 */
	public void documentProcessed(MutableAnnotation doc, HttpSession session, String resName) {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (this.servletPath == null)
			this.servletPath = request.getServletPath();
		
		//	check if request directed at webapp host
		if (this.webAppHost.handleRequest(request, response))
			return;
		
		//	check authentication
		if (!this.webAppHost.isAuthenticated(request)) {
			this.sendHtmlPage(this.webAppHost.getLoginPageBuilder(this, request, response, "includeBody", null));
			return;
		}
		
		//	request to handler
		if (this.requestHandler.handleRequest(request, response))
			return;
		
		//	get user name
		String userName = this.webAppHost.getUserName(request);
		
		//	call sub class specific handler
		if (this.doGet(request, response, userName))
			return;
		
		//	check type of request
		String pathInfo = request.getPathInfo();
		if (pathInfo == null) {
			this.sendStartPage(request, response, userName);
			return;
		}
		
		//	request for status page of pending request
		if (pathInfo.startsWith("/status/")) {
			String requestId = pathInfo.substring("/status/".length());
			
			//	re-authenticate upload request
			OnlineEditorRequest oer = this.requestHandler.getOnlineEditorRequest(requestId);
			if (oer != null)
				oer.setSession(request.getSession(false));
			
			//	send status page
			this.requestHandler.sendStatusDisplayPage(request, requestId, response);
			return;
		}
		
		//	request to run a document processor
		if (pathInfo.equals("/runDp")) {
			String docId = request.getParameter("docId");
			String annotationId = request.getParameter("annotId");
			String dpName = request.getParameter("dpName");
			String dpLabel = request.getParameter("dpLabel");
			String arId = this.runDocumentProcessor(request.getSession(false), docId, annotationId, dpName, dpLabel);
			if (arId != null) {
				this.requestHandler.sendStatusDisplayPage(request, arId, response);
				return;
			}
		}
		
		//	document download request
		if (pathInfo.startsWith("/download/")) {
			String docId = pathInfo.substring("/download/".length());
			MutableAnnotation doc = this.loadDocument(request.getSession(false), docId, null, false);
			if (doc == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			response.setContentType("text/xml");
			response.setCharacterEncoding("UTF-8");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
			AnnotationUtils.writeXML(doc, bw);
			bw.flush();
			return;
		}
		
		//	deliver form for document deletion
		if ("/delForm".equals(pathInfo)) {
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
			BufferedLineWriter blw = new BufferedLineWriter(out);
			blw.writeLine("<html><body>");
			blw.writeLine("<form id=\"deleteDocForm\" method=\"POST\" action=\"" + request.getContextPath() + this.getServletPath() + "/delete" + "\">");
			blw.writeLine("<input type=\"hidden\" id=\"docId_field\" name=\"docId\" value=\"\">");
			blw.writeLine("</form>");
			blw.writeLine("</body></html>");
			blw.flush();
			out.flush();
			return;
		}
		
		//	deliver (empty) search form for background reference search
		if ("/form".equals(pathInfo)) {
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
			BufferedLineWriter blw = new BufferedLineWriter(out);
			blw.writeLine("<html><body>");
			blw.writeLine("<form id=\"searchForm\" method=\"GET\" action=\"" + request.getContextPath() + this.getServletPath() + "/search" + "\"></form>");
			blw.writeLine("</body></html>");
			blw.flush();
			out.flush();
			return;
		}
		
		//	handle calls for background reference search
		if ("/search".equals(pathInfo)) {
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
			BufferedLineWriter blw = new BufferedLineWriter(out);
			blw.writeLine("<html><body>");
			blw.writeLine("<script type=\"text/javascript\">");
			blw.writeLine("var rs = new Object();");
			blw.writeLine("var r;");
			RefData[] rds = this.searchRefData(request);
			for (int r = 0; r < rds.length; r++) {
				blw.writeLine("r = new Object();");
				String refType = this.refTypeSystem.classify(rds[r]);
				if (refType != null)
					rds[r].setAttribute(PUBLICATION_TYPE_ATTRIBUTE, refType);
				BibRefEditorFormHandler.writeRefDataAsJavaScriptObject(blw, rds[r], "r");
				if (rds[r].hasAttribute("docFormat"))
					blw.writeLine("r['docFormat'] = '" + BibRefEditorFormHandler.escapeForJavaScript(rds[r].getAttribute("docFormat")) + "';");
				blw.writeLine("r['sourceName'] = '" + BibRefEditorFormHandler.escapeForJavaScript(rds[r].getAttribute("sourceName")) + "';");
				blw.writeLine("r['refString'] = '" + BibRefEditorFormHandler.escapeForJavaScript(BibRefUtils.toRefString(rds[r])) + "';");
				blw.writeLine("rs['" + r + "'] = r;");
			}
			blw.writeLine("var references = rs;");
			blw.writeLine("</script>");
			blw.writeLine("</body></html>");
			blw.flush();
			out.flush();
			return;
		}
		
		//	something else, send base page
		this.sendStartPage(request, response, userName);
	}
	private RefData[] searchRefData(HttpServletRequest request) throws IOException {
		RefData queryRd = this.getRefData(request);
		ArrayList refDataList = new ArrayList();
		for (int s = 0; s < this.docSources.length; s++) {
			System.out.println("Searching " + this.docSources[s].getName());
			RefData[] rds = this.docSources[s].findRefData(queryRd);
			if (rds == null) {
				System.out.println(" ==> no references found");
				continue;
			}
			System.out.println(" ==> found " + rds.length + " references");
			String sourceName = this.docSources[s].getName();
			for (int r = 0; r < rds.length; r++) {
				rds[r].setAttribute("sourceName", sourceName);
				String docFormatName = this.docSources[s].getDocumentFormatName(rds[r]);
				if (docFormatName != null)
					rds[r].setAttribute("docFormat", docFormatName);
				refDataList.add(rds[r]);
			}
		}
		return ((RefData[]) refDataList.toArray(new RefData[refDataList.size()]));
		//	TODO (later) if we have more than one source, search in parallel threads
	}
	private RefData getRefData(HttpServletRequest request) throws IOException {
		RefData rd = new RefData();
		this.addRefDataAttribute(rd, request, AUTHOR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, YEAR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, TITLE_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, JOURNAL_NAME_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, PUBLISHER_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, LOCATION_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, EDITOR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, VOLUME_TITLE_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, PAGINATION_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, VOLUME_DESIGNATOR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, ISSUE_DESIGNATOR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, NUMERO_DESIGNATOR_ANNOTATION_TYPE);
		this.addRefDataAttribute(rd, request, PUBLICATION_URL_ANNOTATION_TYPE);
		if (!rd.hasAttribute(PUBLICATION_TYPE_ATTRIBUTE)) {
			String type = this.refTypeSystem.classify(rd);
			if (type != null)
				rd.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
		}
		for (int i = 0; i < this.refIdTypes.length; i++)
			this.addRefDataAttribute(rd, request, ("ID-" + this.refIdTypes[i]));
		return rd;
	}
	private void addRefDataAttribute(RefData rd, HttpServletRequest request, String attribute) throws IOException {
		String value = request.getParameter(attribute);
		if (value == null)
			return;
		value = value.trim();
		if (value.length() == 0)
			return;
		if (AUTHOR_ANNOTATION_TYPE.equals(attribute) || EDITOR_ANNOTATION_TYPE.equals(attribute)) {
			String[] values = value.split("\\s*\\&\\s*");
			for (int v = 0; v < values.length; v++)
				rd.addAttribute(attribute, values[v]);
		}
		else rd.setAttribute(attribute, value);
	}
	
	/**
	 * Handle a GET request in a sub class specific way. This method gives sub
	 * classes the opportunity to extend the servlet's functionality. This
	 * default implementation simply returns false, sub classes are welcome to
	 * overwrite it as needed.
	 * @param request the request to handle
	 * @param response the response to send the result to
	 * @param authClient the authenticated client belonging to the HTTP session
	 *            of the request
	 * @return true if the request was handled, false otherwise
	 */
	protected boolean doGet(HttpServletRequest request, HttpServletResponse response, String userName) throws IOException {
		return false;
	}
	
	/**
	 * Send the default page. This implementation produces a page listing the
	 * pending requests of a user. Sub classes are welcome to overwrite this
	 * method to provide additional functionality. However, they have to include
	 * a call to WebAppHost.writeAccountManagerHtml() somewhere.
	 * @param request the HTTP request to handle
	 * @param response the response to write to
	 * @param authClient the authenticated client belonging to the HTTP session
	 *            of the request
	 */
	protected void sendStartPage(HttpServletRequest request, HttpServletResponse response, final String userName) throws IOException {
		this.sendHtmlPage(new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type)) {
					webAppHost.writeAccountManagerHtml(this, null);
					if ("/upload".equals(this.request.getPathInfo()))
						writeUploadForm(this, null);
					else {
						writeRequestList(this);
						writeDocumentList(this);
					}
				}
				else super.include(type, tag);
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		//	check if request directed at webapp host
		if (this.webAppHost.handleRequest(request, response))
			return;
		
		//	check authentication
		if (!this.webAppHost.isAuthenticated(request)) {
			this.sendHtmlPage(this.webAppHost.getLoginPageBuilder(this, request, response, "includeBody", null));
			return;
		}
		
		//	request to handler
		if (this.requestHandler.handleRequest(request, response))
			return;
		
		//	get user name
		String userName = this.webAppHost.getUserName(request);
		
		//	call sub class specific handler
		if (this.doPost(request, response, userName))
			return;
		
		String pathInfo = request.getPathInfo();
		if (pathInfo == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		//	document deletion request
		if ("/delete".equals(pathInfo)) {
			String docId = request.getParameter("docId");
			AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(request.getSession(false));
			if (ap == null) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			if (this.documentStore == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			
			//	delete database entry
			String result;
			String query = "DELETE FROM " + MARKUP_PROGRESS_TABLE_NAME + 
					" WHERE " + DOCUMENT_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(docId) + "'" + 
					" AND " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(this.webAppHost.getUserName(request.getSession(false)) + "@" + ap.getName()) + "'" + 
					";";
			try {
				int updated = this.io.executeUpdateQuery(query);
				if (updated == 0)
					result = "Could not find document to delete it.";
				else {
					this.documentStore.deleteDocument(docId);
					result = ("Document deleted.");
				}
			}
			catch (SQLException sqle) {
				System.out.println("Exception deleting document '" + docId + "': " + sqle.getMessage());
				System.out.println("  query was " + query);
				result = "Could not delete document due to technical problems.";
			}
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
			BufferedLineWriter blw = new BufferedLineWriter(out);
			blw.writeLine("<html><body>");
			blw.writeLine("<form id=\"deleteDocResultForm\" action=\"#\">");
			blw.writeLine("<input type=\"hidden\" id=\"deleteDocResult\" name=\"ddr\" value=\"" + result + "\">");
			blw.writeLine("</form>");
			blw.writeLine("</body></html>");
			blw.flush();
			out.flush();
			return;
		}
		
		//	get upload ID and check validity
		if (!pathInfo.startsWith("/upload/")) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String uploadId = pathInfo.substring("/upload/".length());
		if (!this.validUploadIDs.contains(uploadId)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		try {
			
			//	accept document upload
			HashSet fileFieldSet = new HashSet(2);
			fileFieldSet.add("uploadFile");
			FormDataReceiver uploadData = FormDataReceiver.receive(request, uploadMaxLength, this.uploadCacheFolder, 1024, fileFieldSet);
			System.out.println("MarkupWizardServlet: document upload data encoding is " + uploadData.getContentType());
			
			//	get forward URL
			String forwardUrl = uploadData.getFieldValue("forwardUrl");
			
			//	get document format
			DocumentFormat df = this.goldenGate.getDocumentFormatForName(uploadData.getFieldValue("uploadDocFormat"));
			if (df == null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			
			//	check upload data and build title for importer job
			String uploadUrlString = uploadData.getFieldValue("uploadUrl");
			String uploadTitle;
			if ((uploadUrlString != null) && (uploadUrlString.trim().length() != 0))
				uploadTitle = ("Importing " + uploadUrlString);
			else if (uploadData.hasField("uploadFile"))
				uploadTitle = ("Importing " + uploadData.getSourceFileName("uploadFile"));
			else {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			
			//	start loading AR
			DocumentLoaderOER ar = new DocumentLoaderOER(uploadId, uploadTitle, request.getSession(false), uploadData, df);
			this.enqueueOnlineEditorRequest(ar);
			
			//	send back base page, status page comes in popup window on loading (have to use redirect, though, to get back to GETting pages)
			if (forwardUrl == null)
				response.sendRedirect(request.getContextPath() + request.getServletPath() + "?" + "uploadId=" + uploadId);
			/*
			 * we have to redirect to the start page instead of sending request it
			 * directly in order to get back to GET, as reloading on POST tends
			 * to cause trouble like re-sending data, respective browser
			 * prompts, etc.
			 */
			
			//	... or forward where the client desires, including signal for opening status window
			else response.sendRedirect(forwardUrl + ((forwardUrl.indexOf('?') == -1) ? "?" : "&") + "uploadId=" + uploadId);
			
			//	we're done with this one ...
			return;
		}
		finally {
			this.validUploadIDs.remove(uploadId);
		}
	}
	
	/**
	 * Handle a POST request in a sub class specific way. This method gives sub
	 * classes the opportunity to extend the servlet's functionality. This
	 * default implementation simply returns false, sub classes are welcome to
	 * overwrite it as needed.
	 * @param request the request to handle
	 * @param response the response to send the result to
	 * @param authClient the authenticated client belonging to the HTTP session
	 *            of the request
	 * @return true if the request was handled, false otherwise
	 */
	protected boolean doPost(HttpServletRequest request, HttpServletResponse response, String userName) throws IOException {
		return false;
	}
	
	/**
	 * Retrieve the IDs of the requests for a given user. This method allows for
	 * listing a user's requests after login.
	 * @param userName the user name
	 * @return an array holding the IDs of the requests belonging to the
	 *         argument user
	 */
	public String[] getRequestIDs(String userName) {
		return this.requestHandler.getRequestIDs(userName);
	}
	
	/**
	 * Write the list of pending requests to some page builder. This method
	 * enables external code to include the list.
	 * @param hpb the HTML page builder to write to
	 * @param authClient the authenticated client for whose user to list the
	 *            requests
	 * @throws IOException
	 */
	public void writeRequestList(HtmlPageBuilder hpb) throws IOException {
		if (this.servletPath == null)
			throw new IOException("Don't got own path as yet.");
		
		String[] requestIDs = this.getRequestIDs(this.webAppHost.getUserName(hpb.request));
		String listPath = (hpb.request.getContextPath() + hpb.request.getServletPath());
		if (hpb.request.getPathInfo() != null)
			listPath += hpb.request.getPathInfo();
		
		hpb.writeLine("<table class=\"onlineEditorRequestTable\" id=\"onlineEditorRequestTable\">");
		
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorRequestTableHeader\">");
		hpb.writeLine("Your Pending Requests (click on them to continue)");
		hpb.writeLine("</td>");
		hpb.writeLine("</tr>");
		
		if (requestIDs.length == 0) {
			hpb.writeLine("<tr>");
			hpb.writeLine("<td class=\"onlineEditorRequestTableCell\">");
			hpb.writeLine("You have no requests pending at the moment.");
			hpb.writeLine("</td>");
			hpb.writeLine("</tr>");
		}
		
		//	list pending requests
		else for (int r = 0; r < requestIDs.length; r++) {
			OnlineEditorRequest oer = this.requestHandler.getOnlineEditorRequest(requestIDs[r]);
			if (oer == null)
				continue;
			if (oer.isFinished())
				continue;
			oer.setSession(hpb.request.getSession(false));
			
			hpb.writeLine("<tr>");
			hpb.writeLine("<td class=\"onlineEditorRequestTableCell\">");
			hpb.write("<a" +
					" href=\"#\"" +
					" onclick=\"openStatusWindow('" + hpb.request.getContextPath() + this.servletPath + "/status/" + requestIDs[r] + "', '" + listPath + "'); return false;\"" +
					">");
			hpb.write(html.escape(oer.name));
			hpb.write("</a>");
			hpb.writeLine(" (" + html.escape(oer.getStatus()) + ")");
			hpb.writeLine("</td>");
			hpb.writeLine("</tr>");
		}
		
		hpb.writeLine("</table>");
		
		//	get upload ID
		String uploadId = null;
		if ("POST".equalsIgnoreCase(hpb.request.getMethod())) {
			String pathInfo = hpb.request.getPathInfo();
			if ((pathInfo != null) && pathInfo.startsWith("/upload/"))
				uploadId = pathInfo.substring("/upload/".length());
		}
		else if ("GET".equalsIgnoreCase(hpb.request.getMethod()))
			uploadId = hpb.request.getParameter("uploadId");
		
		//	we're returning from an upload, open status page
		if (uploadId != null) {
			hpb.writeLine("<script type=\"text/javascript\">");
			hpb.writeLine("  openStatusWindow('" + hpb.request.getContextPath() + this.getServletPath() + "/status/" + uploadId + "', '" + listPath + "');");
			hpb.writeLine("</script>");
		}
	}
	
	/**
	 * Write the list of waiting documents to some page builder. This method
	 * enables external code to include the list. This default implementation
	 * lists the document of the servlet's own internal database. Sub classes
	 * working on a storage location that permits listing documents are welcome
	 * to overwrite it as needed.
	 * @param hpb the HTML page builder to write to
	 * @throws IOException
	 */
	public void writeDocumentList(HtmlPageBuilder hpb) throws IOException {
		if (this.documentStore == null)
			return;
		
		AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(hpb.request.getSession(false));
		if (ap == null)
			return;
		
		String listPath = (hpb.request.getContextPath() + hpb.request.getServletPath());
		if (hpb.request.getPathInfo() != null)
			listPath += hpb.request.getPathInfo();
		
		hpb.writeLine("<table class=\"onlineEditorDocumentTable\" id=\"onlineEditorDocumentTable\">");
		
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorDocumentTableHeader\" colspan=\"2\">");
		hpb.writeLine("Your Pending Documents (click buttons to work on them, or name to download)");
		if (this.loadDocFormats.length != 0)
			hpb.writeLine("<input" +
					" type=\"button\"" +
					" class=\"onlineEditorUploadButton\"" +
					" value=\"Upload Document\"" +
					" title=\"Upload a new document\"" +
					" onclick=\"window.location.href = '" + hpb.request.getContextPath() + hpb.request.getServletPath() + "/upload" + "';\"" +
					">");
		hpb.writeLine("</td>");
		hpb.writeLine("</tr>");
		
		//	load user documents
		String query = "SELECT " + DOCUMENT_ID_COLUMN_NAME + ", " + DOCUMENT_NAME_COLUMN_NAME + ", " + NEXT_FUNCTIONS_COLUMN_NAME +
				" FROM " + MARKUP_PROGRESS_TABLE_NAME + 
				" WHERE " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(this.webAppHost.getUserName(hpb.request.getSession(false)) + "@" + ap.getName()) + "'" + 
					" AND " + NEXT_FUNCTIONS_COLUMN_NAME + " <> ''" + 
				" ORDER BY " + DOCUMENT_NAME_COLUMN_NAME + 
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query, true);
			if (!sqr.next()) {
				hpb.writeLine("<tr>");
				hpb.writeLine("<td class=\"onlineEditorDocumentTableCell\" colspan=\"2\">");
				hpb.writeLine("You do not have any documents at the moment.");
				if (this.loadDocFormats.length != 0)
					hpb.writeLine("<br>Use the 'Upload Document' button above to upload documents.");
				hpb.writeLine("</td>");
				hpb.writeLine("</tr>");
				return;
			}
			
			do {
				String docId = sqr.getString(0);
				String docName = sqr.getString(1);
				String nextFunctionsString = sqr.getString(2);
				String[] nextFunctions = nextFunctionsString.split("\\;");
				
				hpb.writeLine("<tr id=\"docListEntry" + docId + "\">");
				hpb.writeLine("<td class=\"onlineEditorDocumentTableCell\">");
				hpb.writeLine("<input" +
						" type=\"button\"" +
						" class=\"onlineEditorDeleteButton\"" +
						" value=\"X\"" +
						" title=\"Delete this document\"" +
						" onclick=\"deleteDocument('" + docId + "', '" + html.escape(docName) + "');\"" +
						">");
				hpb.write("<a" +
						" href=\"" + hpb.request.getContextPath() + this.getServletPath() + "/download/" + docId + "\"" +
						" target=\"_blank\"" +
						" title=\"Download document.\"" +
						" class=\"onlineEditorDocumentLink\"" +
						">");
				hpb.write(html.escape(docName));
				hpb.writeLine("</a>");
				hpb.writeLine("</td>");
				hpb.writeLine("<td class=\"onlineEditorDocumentTableCell\">");
				for (int f = 0; f < nextFunctions.length; f++) {
					CustomFunction cf = ((CustomFunction) this.customFunctions.get(nextFunctions[f]));
					if (cf == null)
						this.writeDocumentListEntryExtensions(hpb, listPath, docId, nextFunctions[f]);
					else hpb.writeLine("<input" +
							" type=\"button\"" +
							" class=\"onlineEditorFunctionButton\"" +
							" value=\"" + html.escape(cf.label) + "\"" +
							" title=\"" + html.escape(cf.toolTip) + "\"" +
							" onclick=\"openDocumentProcessingWindow('" + hpb.request.getContextPath() + this.getServletPath() + "/runDp?docId=" + URLEncoder.encode(docId, "UTF-8") + "&dpName=" + URLEncoder.encode(cf.getDocumentProcessorName(), "UTF-8") + "&dpLabel=" + URLEncoder.encode(("Running " + cf.label), "UTF-8") + "', 'Running " + cf.label + "', '" + listPath +"');\"" +
							">");
				}
				hpb.writeLine("</td>");
				hpb.writeLine("</tr>");
			}
			while (sqr.next());
		}
		catch (SQLException sqle) {
			System.out.println("Exception getting documents for user '" + this.webAppHost.getUserName(hpb.request) + "': " + sqle.getMessage());
			System.out.println("  query was " + query);
		}
		finally {
			if (sqr != null)
				sqr.close();
			hpb.writeLine("</table>");
			hpb.writeLine("<iframe id=\"deleteDocumentFrame\" style=\"width: 0px; height: 0px; border-width: 0px;\"></iframe>");
		}
	}
	
	/**
	 * Write some sub class specific button for an entry in a document list to
	 * an HTML page builder. This default implementation does nothing. Sub
	 * classes are welcome to overwrite it as needed. Sub classes using
	 * JavaScript to implement the functionality should also overwrite the
	 * writePageHeadExtensions method to generate the required code. However,
	 * sub classes may also use the openDocumentProcessingWindow() JavaScript
	 * method readily provided by this class and overwrite the three-argument
	 * doGet() method to catch respective HTTP requests. The buttons should have
	 * the CSS class 'onlineEditorFunctionButton' to allow for a unified look.
	 * The argument function name always is one of the ones returned by
	 * getApplicableFunctionNames().
	 * @param hpb the HTML page builder to write to
	 * @param listPath the path to the document list the functions belong to
	 * @param docId the ID of the document to provide the functionality for
	 * @param functionName the name of the function
	 * @throws IOException
	 */
	protected void writeDocumentListEntryExtensions(HtmlPageBuilder hpb, String listPath, String docId, String functionName) throws IOException {}
	
	/**
	 * Write the document upload form to some page builder. This method enables
	 * external code to include the upload form. If the argument forward URL is
	 * not null, the browser will be forwarded to this URL after the upload form
	 * is submitted. If the configuration of the encapsulated GoldenGATE
	 * instance does not provide any document formats, this method does nothing.
	 * @param hpb the HTML page builder to write to
	 * @param forwardUrl the URL to forward to after receiving the upload
	 * @throws IOException
	 */
	public void writeUploadForm(HtmlPageBuilder hpb, String forwardUrl) throws IOException {
		if (this.loadDocFormats.length == 0) {
			hpb.writeLine("<table class=\"onlineEditorUploadTable\">");
			hpb.writeLine("<tr>");
			hpb.writeLine("<td class=\"onlineEditorUploadTableHeader\">");
			hpb.writeLine("Uploading new documents is not possible at this time.");
			hpb.writeLine("</td>");
			hpb.writeLine("</tr>");
			hpb.writeLine("</table>");
			return;
		}
		
		//	generate upload ID
		String uploadId = Gamta.getAnnotationID();
		this.validUploadIDs.add(uploadId);
		
		//	start form
		hpb.writeLine("<form" +
				" id=\"uploadForm\"" +
				" method=\"POST\"" +
				" action=\"" + hpb.request.getContextPath() + this.getServletPath() + "/upload/" + uploadId + "\"" +
				" onsubmit=\"return checkUploadData();\"" +
			">");
		
		//	add forward URL (if any)
		if (forwardUrl != null)
			hpb.writeLine("<input type=\"hidden\" name=\"forwardUrl\" value=\"" + forwardUrl + "\">");
		
		//	add fields
		hpb.writeLine("<table class=\"onlineEditorUploadTable\">");
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorUploadTableHeader\">");
		hpb.writeLine("Upload a New Document");
		hpb.writeLine("</td>");
		hpb.writeLine("</tr>");
		hpb.writeLine("</table>");
		
		hpb.writeLine("<div id=\"onlineEditorUploadRefDataFields\">");
		BibRefEditorFormHandler.createHtmlForm(hpb.asWriter(), false, this.refTypeSystem, this.refIdTypes);
		hpb.writeLine("</div>");
		
		hpb.writeLine("<div id=\"onlineEditorUploadDataFields\">");
		hpb.writeLine("<table class=\"bibRefEditorTable\">");
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorUploadFieldLabel\" style=\"text-align: right;\">Document URL:</td>");
		hpb.writeLine("<td colspan=\"3\" class=\"onlineEditorUploadFieldCell\"><input class=\"onlineEditorUploadField\" style=\"width: 100%;\" id=\"uploadUrl_field\" name=\"uploadUrl\"></td>");
		hpb.writeLine("</tr>");
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorUploadFieldLabel\" style=\"text-align: right;\">Document File:</td>");
		hpb.writeLine("<td colspan=\"3\" class=\"onlineEditorUploadFieldCell\"><input type=\"file\" class=\"onlineEditorUploadField\" style=\"width: 100%;\" id=\"uploadFile_field\" name=\"uploadFile\" onchange=\"uploadFileChanged();\"></td>");
		hpb.writeLine("</tr>");
		
		hpb.writeLine("<tr>");
		hpb.writeLine("<td class=\"onlineEditorUploadFieldLabel\" style=\"text-align: right;\">Document Format:</td>");
		hpb.writeLine("<td colspan=\"3\" class=\"onlineEditorUploadFieldCell\">");
		hpb.writeLine("<select class=\"onlineEditorUploadField\" style=\"width: 100%;\" id=\"uploadDocFormat_field\" name=\"uploadDocFormat\">");
		for (int f = 0; f < this.loadDocFormats.length; f++)
			hpb.writeLine("<option value=\"" + this.loadDocFormats[f].getName() + "\">" + this.loadDocFormats[f].getDescription() + "</option>");
		hpb.writeLine("</select>");
		hpb.writeLine("</td>");
		hpb.writeLine("</tr>");
		
		hpb.writeLine("</table>");
		hpb.writeLine("</div>");
		
		hpb.writeLine("<div id=\"onlineEditorUploadButtons\">");
		hpb.writeLine("<input type=\"button\" class=\"onlineEditorUploadButton\" id=\"searchRefs_button\" value=\"Search References\" onclick=\"searchRefs();\">");
		hpb.writeLine("<input type=\"button\" class=\"onlineEditorUploadButton\" id=\"checkRef_button\" value=\"Check Reference\" onclick=\"validateRefData();\">");
		hpb.writeLine("<input type=\"submit\" class=\"onlineEditorUploadButton\" id=\"doUpload_button\" value=\"Import Document\">");
		hpb.writeLine("</div>");
		
		hpb.writeLine("</form>");
		
		hpb.writeLine("<script type=\"text/javascript\">");
		hpb.writeLine("  bibRefEditor_refTypeChanged();");
		hpb.writeLine("</script>");
		
		hpb.writeLine("<iframe id=\"searchRefsFrame\" style=\"width: 0px; height: 0px; border-width: 0px;\"></iframe>");
		
		hpb.writeLine("<div id=\"refSearchResult\" style=\"display: none;\">");
		hpb.writeLine("</div>");
	}
	private HashSet validUploadIDs = new HashSet();
	//	TODO use linked hash map instead, keeping track of timestamps
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#writePageHeadExtensions(de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageHeadExtensions(HtmlPageBuilder out) throws IOException {
		out.writeLine("<script type=\"text/javascript\">");
		out.writeLine("var openingStatusWindow;");
		out.writeLine("function openStatusWindow(path, listPath) {");
		out.writeLine("  if (openingStatusWindow != null) {");
		out.writeLine("    window.setTimeout(('openStatusWindow(\\'' + path + '\\', \\'' + listPath + '\\')'), 250);");
		out.writeLine("    return;");
		out.writeLine("  }");
		out.writeLine("  openingStatusWindow = window.open(path, 'Document Processing Status', 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes');");
		out.writeLine("  if (openingStatusWindow)");
		out.writeLine("    linkStatusWindow(listPath);");
		out.writeLine("  else {");
		out.writeLine("    alert('Please deactivate your popup blocker for this page\\nso JavaScript can open document status windows.');");
		out.writeLine("    openStatusWindow(path, listPath);");
		out.writeLine("  }");
		out.writeLine("}");
		out.writeLine("function linkStatusWindow(listPath) {");
		out.writeLine("  if (openingStatusWindow == null)");
		out.writeLine("    return;");
		out.writeLine("  if (openingStatusWindow.requestFinished) {");
		out.writeLine("    openingStatusWindow.requestFinished = function() {");
		out.writeLine("        location.href = listPath;");
		out.writeLine("      };");
		out.writeLine("    openingStatusWindow = null;");
		out.writeLine("  }");
		out.writeLine("  else window.setTimeout(('linkStatusWindow(\\'' + listPath + '\\')'), 250);");
		out.writeLine("}");
		out.writeLine("</script>");
		
		out.writeLine("<script type=\"text/javascript\">");
		out.writeLine("function deleteDocument(docId, docName) {");
		out.writeLine("  if (confirm('Do you really want to delete ' + docName + '?') == true)");
		out.writeLine("    doDeleteDocument(docId);");
		out.writeLine("}");
		out.writeLine("function doDeleteDocument(docId) {");
		out.writeLine("  var ddfr = $('deleteDocumentFrame');");
		out.writeLine("  var ddf = ddfr.contentWindow.document.getElementById('deleteDocForm');");
		out.writeLine("  if (ddf == null) {");
		out.writeLine("    if (ddfr.src != '" + out.request.getContextPath() + this.getServletPath() + "/delForm" + "')");
		out.writeLine("      ddfr.src = '" + out.request.getContextPath() + this.getServletPath() + "/delForm" + "';");
		out.writeLine("    window.setTimeout(('doDeleteDocument(\\'' + docId + '\\')'), 100);");
		out.writeLine("  }");
		out.writeLine("  else {");
		out.writeLine("    var didf = ddfr.contentWindow.document.getElementById('docId_field');");
		out.writeLine("    didf.value = docId;");
		out.writeLine("    ddf.submit();");
		out.writeLine("    window.setTimeout(('readDeleteDocumentResult(\\'' + docId + '\\')'), 100);");
		out.writeLine("  }");
		out.writeLine(" }");
		out.writeLine("function readDeleteDocumentResult(docId) {");
		out.writeLine("  var ddfr = $('deleteDocumentFrame');");
		out.writeLine("  var ddf = ddfr.contentWindow.document.getElementById('deleteDocResultForm');");
		out.writeLine("  if (ddf == null) {");
		out.writeLine("    window.setTimeout(('readDeleteDocumentResult(\\'' + docId + '\\')'), 100);");
		out.writeLine("    return;");
		out.writeLine("  }");
		out.writeLine("  var dle = $('docListEntry' + docId);");
		out.writeLine("  if ((dle != null) && (dle.parentNode != null))");
		out.writeLine("    dle.parentNode.removeChild(dle);");
		out.writeLine("}");
		out.writeLine("</script>");
		
		if (this.loadDocFormats.length == 0)
			return;
		
		out.writeLine("<script type=\"text/javascript\">");
		out.writeLine("var openingDocumentWindow;");
		out.writeLine("function openDocumentProcessingWindow(path, title, listPath) {");
		out.writeLine("  if (openingDocumentWindow != null) {");
		out.writeLine("    window.setTimeout(('openDocumentProcessingWindow(\\'' + path + '\\', \\'' + title + '\\', \\'' + listPath + '\\')'), 250);");
		out.writeLine("    return;");
		out.writeLine("  }");
		out.writeLine("  openingDocumentWindow = window.open(path, title, 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes');");
		out.writeLine("  if (openingDocumentWindow)");
		out.writeLine("    linkDocumentProcessingWindow(listPath);");
		out.writeLine("  else {");
		out.writeLine("    alert('Please deactivate your popup blocker for this page\\nso JavaScript can open document processing windows.');");
		out.writeLine("    openDocumentProcessingWindow(path, title, listPath);");
		out.writeLine("  }");
		out.writeLine("}");
		out.writeLine("function linkDocumentProcessingWindow(listPath) {");
		out.writeLine("  if (openingDocumentWindow == null)");
		out.writeLine("    return;");
		out.writeLine("  if (openingDocumentWindow.requestFinished) {");
		out.writeLine("    openingDocumentWindow.requestFinished = function() {");
		out.writeLine("        location.href = listPath;");
		out.writeLine("      };");
		out.writeLine("    openingDocumentWindow = null;");
		out.writeLine("    location.href = listPath;");
		out.writeLine("  }");
		out.writeLine("  else window.setTimeout(('linkDocumentProcessingWindow(\\'' + listPath + '\\')'), 250);");
		out.writeLine("}");
		out.writeLine("</script>");
		
		//	write JavaScripts used by reference form
		BibRefEditorFormHandler.writeJavaScripts(out.asWriter(), this.refTypeSystem, this.refIdTypes);
		
		//	write JavaScripts used by reference search
		out.writeLine("<script type=\"text/javascript\">");
		
		out.writeLine("function uploadFileChanged() {");
		out.writeLine("  var uff = $('uploadFile_field');");
		out.writeLine("  if ((uff.value == null) || (uff.value.length == 0))");
		out.writeLine("    return;");
		out.writeLine("  var uuf = $('uploadUrl_field');");
		out.writeLine("  uuf.value = '';");
		out.writeLine("}");
		
		out.writeLine("var searchResult = null;");
		out.writeLine("function searchRefs() {");
		out.writeLine("  var srf = $('searchRefsFrame');");
		out.writeLine("  var sf = srf.contentWindow.document.getElementById('searchForm');");
		out.writeLine("  if (sf == null) {");
		out.writeLine("    if (srf.src != '" + out.request.getContextPath() + this.getServletPath() + "/form" + "')");
		out.writeLine("      srf.src = '" + out.request.getContextPath() + this.getServletPath() + "/form" + "';");
		out.writeLine("    window.setTimeout('searchRefs()', 100);");
		out.writeLine("  }");
		out.writeLine("  else {");
		out.writeLine("    bibRefEditor_addRefAttributeInputs(sf);");
		out.writeLine("    sf.submit();");
		out.writeLine("    window.setTimeout('readSearchResult()', 100);");
		out.writeLine("  }");
		out.writeLine("}");
		out.writeLine("function readSearchResult() {");
		out.writeLine("  var srf = $('searchRefsFrame');");
		out.writeLine("  if (srf.contentWindow.references) {");
		out.writeLine("    searchResult = srf.contentWindow.references;");
		out.writeLine("    displaySearchResult();");
		out.writeLine("  }");
		out.writeLine("  else window.setTimeout('readSearchResult()', 100);");
		out.writeLine("}");
		out.writeLine("function clearSearchResult() {");
		out.writeLine("  var srd = $('refSearchResult');");
		out.writeLine("  while (srd.firstChild)");
		out.writeLine("    srd.removeChild(srd.firstChild);");
		out.writeLine("  srd.style.display = 'none';");
		out.writeLine("}");
		out.writeLine("function displaySearchResult() {");
		out.writeLine("  clearSearchResult();");
		out.writeLine("  var srd = $('refSearchResult');");
		out.writeLine("  for (var r = 0;; r++) {");
		out.writeLine("    var ref = searchResult['' + r];");
		out.writeLine("    if (ref == null) {");
		out.writeLine("      if (r == 1)");
		out.writeLine("        selectSearchResult(0);");
		out.writeLine("      else if (r == 0) {");
		out.writeLine("        var rd = document.createElement('p');");
		out.writeLine("        setAttribute(rd, 'class', 'refSearchResultElement');");
		out.writeLine("        rd.appendChild(document.createTextNode('Your search did not return any references.'));");
		out.writeLine("        srd.appendChild(rd);");
		out.writeLine("      }");
		out.writeLine("      break;");
		out.writeLine("    }");
		out.writeLine("    displaySearchResultRef(ref, r, srd);");
		out.writeLine("  }");
		out.writeLine("  srd.style.display = '';");
		out.writeLine("}");
		out.writeLine("function displaySearchResultRef(ref, r, srd) {");
		out.writeLine("  var rl = document.createElement('a');");
		out.writeLine("  setAttribute(rl, 'href', '#');");
		out.writeLine("  setAttribute(rl, 'onclick', ('return selectSearchResult(' + r + ');'));");
		out.writeLine("  rl.appendChild(document.createTextNode(ref['refString']));");
		out.writeLine("  var rd = document.createElement('p');");
		out.writeLine("  setAttribute(rd, 'class', 'refSearchResultElement');");
		out.writeLine("  rd.appendChild(rl);");
		out.writeLine("  if (ref['sourceName'])");
		out.writeLine("    rd.appendChild(document.createTextNode(' (from ' + ref['sourceName'] + ')'));");
		out.writeLine("  srd.appendChild(rd);");
		out.writeLine("}");
		out.writeLine("function setAttribute(node, name, value) {");
		out.writeLine("  if (!node.setAttributeNode)");
		out.writeLine("    return;");
		out.writeLine("  var attribute = document.createAttribute(name);");
		out.writeLine("  attribute.nodeValue = value;");
		out.writeLine("  node.setAttributeNode(attribute);");
		out.writeLine("}");
		out.writeLine("function selectSearchResult(r) {");
		StringBuffer refIdTypesString = new StringBuffer();
		for (int i = 1; i < this.refIdTypes.length; i++) {
			if (i != 1)
				refIdTypesString.append(';');
			refIdTypesString.append(this.refIdTypes[i]);
		}
		out.writeLine("  if (!searchResult['' + r]['ID-" + this.refIdTypes[0] + "']) {");
		out.writeLine("    for (var attribute in searchResult['' + r]) {");
		out.writeLine("      if (!searchResult['' + r].hasOwnProperty(attribute))");
		out.writeLine("        continue;");
		out.writeLine("      if (attribute.length < 3)");
		out.writeLine("        continue;");
		out.writeLine("      if (attribute.indexOf('ID-') != 0)");
		out.writeLine("        continue;");
		out.writeLine("      if ('" + refIdTypesString + "'.indexOf(attribute.substr(3)) != -1)");
		out.writeLine("        continue;");
		out.writeLine("      searchResult['' + r]['ID-" + this.refIdTypes[0] + "'] = searchResult['' + r][attribute];");
		out.writeLine("      break;");
		out.writeLine("    }");
		out.writeLine("  }");
		out.writeLine("  if (!searchResult['' + r]['ID-" + this.refIdTypes[0] + "']) {");
		out.writeLine("    for (var attribute in searchResult['' + r]) {");
		out.writeLine("      if (!searchResult['' + r].hasOwnProperty(attribute))");
		out.writeLine("        continue;");
		out.writeLine("      if (attribute.length < 3)");
		out.writeLine("        continue;");
		out.writeLine("      if (attribute.indexOf('ID-') != 0)");
		out.writeLine("        continue;");
		out.writeLine("      searchResult['' + r]['ID-" + this.refIdTypes[0] + "'] = searchResult['' + r][attribute];");
		out.writeLine("      break;");
		out.writeLine("    }");
		out.writeLine("  }");
		out.writeLine("  bibRefEditor_setRef(searchResult['' + r]);");
		out.writeLine("  if (searchResult['' + r]['" + PUBLICATION_URL_ANNOTATION_TYPE + "']) {");
		out.writeLine("    var uuf = $('uploadUrl_field');");
		out.writeLine("    var uff = $('uploadFile_field');");
		out.writeLine("    if ((uuf != null) && ((uff == null) || (uff.value == null) || (uff.value == '')))");
		out.writeLine("      uuf.value = searchResult['' + r]['" + PUBLICATION_URL_ANNOTATION_TYPE + "'];");
		out.writeLine("  }");
		out.writeLine("  if (searchResult['' + r]['docFormat']) {");
		out.writeLine("    var udff = $('uploadDocFormat_field');");
		out.writeLine("    if (udff != null)");
		out.writeLine("      udff.value = searchResult['' + r]['docFormat'];");
		out.writeLine("  }");
		out.writeLine("  clearSearchResult();");
		out.writeLine("  searchResult = null;");
		out.writeLine("  return false;");
		out.writeLine("}");
		
		//	write JavaScripts for validating document meta data
		out.writeLine("function validateRefData() {");
		out.writeLine("  var refErrors = bibRefEditor_getRefErrors();");
		out.writeLine("  if (refErrors != null) {");
		out.writeLine("    var em = 'The meta data has the following errors:';");
		out.writeLine("    for (var e = 0;; e++) {");
		out.writeLine("      var refError = refErrors[e];");
		out.writeLine("      if (refError == null)");
		out.writeLine("        break;");
		out.writeLine("      em += '\\n - ';");
		out.writeLine("      em += refError;");
		out.writeLine("    }");
		out.writeLine("    alert(em);");
		out.writeLine("  }");
		out.writeLine("  else alert('The meta data is good for import.');");
		out.writeLine("}");
		
		//	write JavaScripts used by document upload requests
		out.writeLine("function checkUploadData() {");
		out.writeLine("  var refErrors = bibRefEditor_getRefErrors();");
		out.writeLine("  if (refErrors != null) {");
		out.writeLine("    var em = 'The import cannot be processed due to incomplete meta data:';");
		out.writeLine("    for (var e = 0;; e++) {");
		out.writeLine("      var refError = refErrors[e];");
		out.writeLine("      if (refError == null)");
		out.writeLine("        break;");
		out.writeLine("      em += '\\n - ';");
		out.writeLine("      em += refError;");
		out.writeLine("    }");
		out.writeLine("    alert(em);");
		out.writeLine("    return false;");
		out.writeLine("  }");
		out.writeLine("  var uf = $('uploadForm');");
		out.writeLine("  if (uf == null)");
		out.writeLine("    return false;");
		out.writeLine("  var uuf = $('uploadUrl_field');");
		out.writeLine("  var uploadUrl = ((uuf == null) ? '' : uuf.value);");
		out.writeLine("  if (uploadUrl != '') {");
		out.writeLine("    uf.enctype = 'application/x-www-form-urlencoded';");
		out.writeLine("    return true;");
		out.writeLine("  }");
		out.writeLine("  var uff = $('uploadFile_field');");
		out.writeLine("  var uploadFile = ((uff == null) ? '' : uff.value);");
		out.writeLine("  if (uploadFile != '') {");
		out.writeLine("    uf.enctype = 'multipart/form-data';");
		out.writeLine("    return true;");
		out.writeLine("  }");
		out.writeLine("  alert('Please specify a URL to retrieve the document from, or select a file from you computer to upload.');");
		out.writeLine("  return false;");
		out.writeLine("}");
		
		out.writeLine("</script>");
	}
	
	/**
	 * Trigger running a document processor on a document to load from a backing
	 * GoldenGATE DIO. The annotationId argument may be null; if it is not null,
	 * the document processor is applied only to the annotation with the
	 * specified ID rather than to the whole document. The returned string is
	 * the identifier of the created request, to retrieve the processing status
	 * from.
	 * @param authClient the authenticated client to use for accessing the
	 *            backing GoldenGATE DIO
	 * @param docId the ID of the document to process
	 * @param annotationId the ID of the annotation to process
	 * @param dpName the name of the document processor to run
	 * @return the identifier of the created request
	 */
	public String runDocumentProcessor(HttpSession session, String docId, String annotationId, String dpName) {
		return this.runDocumentProcessor(session, docId, annotationId, dpName, null);
	}
	
	/**
	 * Trigger running a document processor on a document to load from a backing
	 * GoldenGATE DIO. The annotationId argument may be null; if it is not null,
	 * the document processor is applied only to the annotation with the
	 * specified ID rather than to the whole document. The returned string is
	 * the identifier of the created request, to retrieve the processing status
	 * from.
	 * @param authClient the authenticated client to use for accessing the
	 *            backing GoldenGATE DIO
	 * @param docId the ID of the document to process
	 * @param annotationId the ID of the annotation to process
	 * @param dpName the name of the document processor to run
	 * @param dpLabel the nice name of the document processor to run, for
	 *            display purposes, defaults to 'Running &lt;dpName&gt;' if null
	 *            or empty.
	 * @return the identifier of the created request
	 */
	public String runDocumentProcessor(HttpSession session, String docId, String annotationId, String dpName, String dpLabel) {
		
		//	check authentication and permission
		if (!this.webAppHost.isAuthenticated(session))
			return null;
		
		//	check document ID
		if (docId == null)
			return null;
		
		//	get document processor
		if (dpName == null)
			return null;
		DocumentProcessor dp = this.goldenGate.getDocumentProcessorForName(dpName);
		if (dp == null)
			return null;
		
		//	generate label if necessary
		if ((dpLabel == null) || (dpLabel.trim().length() == 0))
			dpLabel = ("Running " + dp.getName());
		
		//	create and start request
		DocumentProcessorOER ar = new DocumentProcessorOER(Gamta.getAnnotationID(), dpLabel, session, dp, docId, annotationId);
		this.requestHandler.enqueueRequest(ar, this.webAppHost.getUserName(session));
		
		//	hand back request ID
		return ar.id;
	}
	
	/**
	 * Enqueue a sub class specific request.
	 * @param oer the request to enqueue
	 */
	protected void enqueueOnlineEditorRequest(OnlineEditorRequest oer) {
		this.requestHandler.enqueueRequest(oer, this.webAppHost.getUserName(oer.session));
	}
	
	/**
	 * Retrieve an asynchronous request running in this servlet.
	 * @param id the request ID
	 * @return the request with the speciefied ID
	 */
	protected OnlineEditorRequest getOnlineEditorRequest(String id) {
		return this.requestHandler.getOnlineEditorRequest(id);
	}
	
	/**
	 * Load a document. This implementation works on the servlet's internal
	 * database. Subclasses working with alternative storage locations can
	 * overwrite this method. For each operation on a document, i.e., for each
	 * asynchronous request processing it, this method is called exactly once.
	 * @param session the HTTP session authentication the user checking out the
	 *            document
	 * @param docId the ID of the document
	 * @param pm a progress monitor to observe the loading operation
	 * @param forProcessing will the document be processed and stored back via
	 *            the saveDocument method?
	 * @return the document with the argument ID
	 * @throws IOException
	 */
	protected MutableAnnotation loadDocument(HttpSession session, String docId, ProgressMonitor pm, boolean forProcessing) throws IOException {
		AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(session);
		if (ap == null)
			throw new IOException("Cannot load document without authentication.");
		
		//	no access to DIO, and no local storage ... nothing we could possibley do
		if (this.documentStore == null)
			throw new IOException("Cannot access GoldenGATE Server with current session.");
		
		//	return document only if it belongs to authenticated user
		String query = "SELECT " + DOCUMENT_ID_COLUMN_NAME + 
				" FROM " + MARKUP_PROGRESS_TABLE_NAME + 
				" WHERE " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(this.webAppHost.getUserName(session) + "@" + ap.getName()) + "'" + 
					" AND " + DOCUMENT_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(docId) + "'" +
				" ORDER BY " + DOCUMENT_NAME_COLUMN_NAME + 
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query, true);
			if (sqr.next())
				return this.documentStore.loadDocument(docId);
			else throw new IOException("Document with ID '" + docId + "' not found.");
		}
		catch (SQLException sqle) {
			System.out.println("Exception getting document '" + docId + "': " + sqle.getMessage());
			System.out.println("  query was " + query);
			throw new IOException("Document with ID '" + docId + "' not accessible: " + sqle.getMessage());
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
	}
	
	/**
	 * Retrieve the names of sub class specific functions applicable to a given
	 * document being saved by a given HTTP session. This default implementation
	 * returns null. Sub classes are welcome to overwrite it as needed. Sub
	 * classes that provide function names via this method will be asked to
	 * write respective HTML code via the writeDocumentListEntryExtensions()
	 * method.
	 * @param session the session saving the document
	 * @param doc the document to retrieve the function names for
	 * @return an array holding the function names
	 */
	protected String[] getApplicableFunctionNames(HttpSession session, QueriableAnnotation doc) {
		return null;
	}
	
	/**
	 * Retrieve a label for a function returned from the
	 * getApplicableFunctionNames() method. Sub classes overwriting the latter
	 * are highly recommended to overwrite his method as well.
	 * @param functionName the name of the function
	 * @return a label for the function with the argument name
	 */
	protected String getFunctionLabel(String functionName) {
		return null;
	}
	
	/**
	 * Save a document. This implementation works on the servlet's internal
	 * database. Subclasses working with alternative storage locations can
	 * overwrite this method. For each operation on a document, i.e., for
	 * each upload and each asynchronous request processing it afterward, this
	 * method is called exactly once.
	 * @param session the HTTP session authentication the user updating the
	 *            document
	 * @param doc the document to save
	 * @param pm a progress monitor to observe saving operation
	 * @param isImport was the document loaded via the loadDocument() method, or
	 *            imported from some other source?
	 * @return the protocol reporting details of the saving operation
	 * @throws IOException
	 */
	protected String[] saveDocument(HttpSession session, MutableAnnotation doc, String docName, ProgressMonitor pm, boolean isImport) throws IOException {
		AuthenticationProvider ap = this.webAppHost.getAuthenticationProvider(session);
		if (ap == null)
			throw new IOException("Cannot store document without authentication.");
		
		//	no document store available ... nothing we could possibley do
		if (this.documentStore == null)
			throw new IOException("Cannot access document store.");
		
		//	get document ID
		String docId = ((String) doc.getAttribute(DOCUMENT_ID_ATTRIBUTE));
		
		//	store document in file system
		int version = this.documentStore.storeDocument(doc);
		
		//	get custom functions and check preclusions
		StringVector nextFunctions = new StringVector();
		for (Iterator cfit = this.customFunctions.keySet().iterator(); cfit.hasNext();) {
			String cfn = ((String) cfit.next());
			CustomFunction cf = ((CustomFunction) this.customFunctions.get(cfn));
			if (cf.isApplicableTo(doc))
				nextFunctions.addElement(cfn);
		}
		
		//	get sub class functions
		String[] functionNames = this.getApplicableFunctionNames(session, doc);
		if (functionNames != null)
			nextFunctions.addContentIgnoreDuplicates(functionNames);
		
		//	store document and status in database
		String query = "UPDATE " + MARKUP_PROGRESS_TABLE_NAME + 
				" SET" +
					" " + NEXT_FUNCTIONS_COLUMN_NAME + " = '" + EasyIO.sqlEscape(nextFunctions.concatStrings(";")) + "'" +
				" WHERE " + DOCUMENT_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(docId) + "'" + 
				";";
		try {
			int updated = this.io.executeUpdateQuery(query);
			System.out.println(" - " + updated + " document status records updated.");
			if (updated == 0) {
				query = "INSERT INTO " + MARKUP_PROGRESS_TABLE_NAME + 
						" (" + 
							DOCUMENT_ID_COLUMN_NAME + 
							", " + 
							DOCUMENT_NAME_COLUMN_NAME + 
							", " + 
							NEXT_FUNCTIONS_COLUMN_NAME + 
							", " + 
							USER_NAME_COLUMN_NAME + 
						") VALUES (" +
							"'" + EasyIO.sqlEscape(docId) + "'" + 
							", " + 
							"'" + EasyIO.sqlEscape(docName) + "'" + 
							", " + 
							"'" + EasyIO.sqlEscape(nextFunctions.concatStrings(";")) + "'" + 
							", " + 
							"'" + EasyIO.sqlEscape(this.webAppHost.getUserName(session) + "@" + ap.getName()) + "'" + 
						");";
				updated = this.io.executeUpdateQuery(query);
				System.out.println(" - " + updated + " document status records created.");
			}
			
			//	report what we have
			String[] storageLog;
			if (nextFunctions.isEmpty()) {
				storageLog = new String[1];
				storageLog[0] = ("Document stored as version " + version + ".");
			}
			else {
				StringVector sl = new StringVector();
				sl.addElement("Document stored as version " + version + ".");
				sl.addElement("Here the next possible steps forward:");
				for (int f = 0; f < nextFunctions.size(); f++) {
					CustomFunction cf = ((CustomFunction) this.customFunctions.get(nextFunctions.get(f)));
					if (cf == null) {
						String fl = this.getFunctionLabel(nextFunctions.get(f));
						if (fl != null)
							sl.addElement(" - " + fl);
					}
					else sl.addElement(" - " + cf.label);
				}
				storageLog = sl.toStringArray();
			}
			return storageLog;
		}
		catch (SQLException sqle) {
			System.out.println("Exception storing next processing step for document '" + docId + "' and user '" + this.webAppHost.getUserName(session) + "@" + ap.getName() + "': " + sqle.getMessage());
			System.out.println("  query was " + query);
			throw new IOException(sqle.getMessage());
		}
	}
}
