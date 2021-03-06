package com.sap.hcp.cf.logging.jersey.filter;

import javax.ws.rs.container.ContainerResponseContext;

import com.sap.hcp.cf.logging.common.request.HttpHeader;

public class ContainerResponseContextAdapter implements ResponseContextAdapter {

	private final ContainerResponseContext ctx;

	public ContainerResponseContextAdapter(ContainerResponseContext responseContext) {
		ctx = responseContext;
	}

	@Override
	public String getHeader(HttpHeader header) {
		String headerName = header.getName();
		return ctx.getHeaderString(headerName);
	}

	@Override
	public long getStatus() {
		return ctx.getStatus();
	}

	@Override
	public long getLength() {
		int res = ctx.getLength();
		if (res < 0) {
			if (ctx.hasEntity()) {
				res = ctx.getEntity().toString().length();
			}
		}
		return res;
	}

}
