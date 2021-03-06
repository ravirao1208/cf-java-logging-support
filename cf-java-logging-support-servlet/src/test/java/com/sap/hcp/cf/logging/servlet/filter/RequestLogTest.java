package com.sap.hcp.cf.logging.servlet.filter;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.hcp.cf.logging.common.Fields;
import com.sap.hcp.cf.logging.common.LogContext;
import com.sap.hcp.cf.logging.common.request.HttpHeader;
import com.sap.hcp.cf.logging.common.request.HttpHeaders;

public class RequestLogTest {

	private static final Logger LOG = LoggerFactory.getLogger(RequestLogTest.class);
	private static final String REQUEST_RECEIVED = "Request received";

	@Rule
	public SystemOutRule systemOut = new SystemOutRule();

	private Server server;
	private CloseableHttpClient client;

	@Before
	public void setUp() throws Exception {
		this.server = initJetty();
		this.client = HttpClientBuilder.create().build();

	}

	private Server initJetty() throws Exception {
		Server server = new Server(0);
		ServletContextHandler handler = new ServletContextHandler(server, null);
		handler.addFilter(RequestLoggingFilter.class, "/*", EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST,
				DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC));
		handler.addServlet(TestServlet.class, "/test");
		server.start();
		return server;
	}

	@After
	public void tearDown() throws Exception {
		client.close();
		server.stop();
	}

	@Test
	public void logsCorrelationIdFromRequestHeader() throws Exception {
		String correlationId = UUID.randomUUID().toString();
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		get.setHeader(HttpHeaders.CORRELATION_ID.getName(), correlationId);
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);

			assertNull("No correlation_id should be generated.", getCorrelationIdGenerated());

			assertThat("Application log without correlation id.", getRequestMessage(),
					hasEntry(Fields.CORRELATION_ID, correlationId));
			assertThat("Request log without correlation id.", getRequestLog(),
					hasEntry(Fields.CORRELATION_ID, correlationId));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Test
	public void logsGeneratedCorrelationId() throws Exception {
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);

			String correlationId = getCorrelationIdGenerated();

			assertThat("Application log without correlation id.", getRequestMessage(),
					hasEntry(Fields.CORRELATION_ID, correlationId));
			assertThat("Request log without correlation id.", getRequestLog(),
					hasEntry(Fields.CORRELATION_ID, correlationId));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Test
	public void logsRequestIdFromRequestHeader() throws Exception {
		String requestId = UUID.randomUUID().toString();
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		get.setHeader(HttpHeaders.X_VCAP_REQUEST_ID.getName(), requestId);
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);

			assertThat("Application log without request id.", getRequestMessage(),
					hasEntry(Fields.REQUEST_ID, requestId));
			assertThat("Request log without request id.", getRequestLog(),
					hasEntry(Fields.REQUEST_ID, requestId));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Test
	public void logsTenantIdFromRequestHeader() throws Exception {
		String tenantId = UUID.randomUUID().toString();
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		get.setHeader(HttpHeaders.TENANT_ID.getName(), tenantId);
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);

			assertThat("Application log without tenant id.", getRequestMessage(),
					hasEntry(Fields.TENANT_ID, tenantId));
			assertThat("Request log without tenant id.", getRequestLog(), hasEntry(Fields.TENANT_ID, tenantId));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Test
	public void writesCorrelationIdFromHeadersAsResponseHeader() throws Exception {
		String correlationId = UUID.randomUUID().toString();
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		get.setHeader(HttpHeaders.CORRELATION_ID.getName(), correlationId);
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);
			assertFirstHeaderValue(correlationId, response, HttpHeaders.CORRELATION_ID);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Test
	public void writesGeneratedCorrelationIdAsResponseHeader() throws Exception {
		HttpGet get = new HttpGet(getBaseUrl() + "/test");
		CloseableHttpResponse response = null;
		try {
			response = client.execute(get);

			assertFirstHeaderValue(getCorrelationIdGenerated(), response, HttpHeaders.CORRELATION_ID);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}


	private String getBaseUrl() {
		int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
		return "http://localhost:" + port;
	}

	private String getCorrelationIdGenerated() throws IOException {
		Map<String, Object> generationLog = systemOut.fineLineAsMapWith("logger", LogContext.class.getName());
		if (generationLog == null) {
			return null;
		}
		return generationLog.get(Fields.CORRELATION_ID) == null ? null
				: generationLog.get(Fields.CORRELATION_ID).toString();
	}

	private Map<String, Object> getRequestMessage() throws IOException {
		return systemOut.fineLineAsMapWith("msg", REQUEST_RECEIVED);
	}

	private Map<String, Object> getRequestLog() throws IOException {
		return systemOut.fineLineAsMapWith("layer", "[SERVLET]");
	}

	private static void assertFirstHeaderValue(String expected, CloseableHttpResponse response, HttpHeader header) {
		String headerValue = response.getFirstHeader(header.getName()).getValue();
		assertThat(headerValue, is(equalTo(expected)));
	}

	@SuppressWarnings("serial")
	public static class TestServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			LOG.info(REQUEST_RECEIVED);
		}
	}
}
