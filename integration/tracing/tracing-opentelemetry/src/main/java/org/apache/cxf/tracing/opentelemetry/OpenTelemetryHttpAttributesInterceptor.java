/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.tracing.opentelemetry;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;

@NoJSR250Annotations
public class OpenTelemetryHttpAttributesInterceptor extends AbstractOpenTelemetryInterceptor {
    public OpenTelemetryHttpAttributesInterceptor(OpenTelemetry openTelemetry, String instrumentationName) {
        super(Phase.POST_MARSHAL, openTelemetry, instrumentationName);
    }

    public OpenTelemetryHttpAttributesInterceptor(OpenTelemetry openTelemetry, Tracer tracer) {
        super(Phase.POST_MARSHAL, openTelemetry, tracer);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        URI extractedURI = getUri(message);
        Span.current().setAttribute(ServerAttributes.SERVER_ADDRESS, extractedURI.getHost());
        Span.current().setAttribute(ServerAttributes.SERVER_PORT, Long.valueOf(extractedURI.getPort()));
        Span.current().setAttribute(UrlAttributes.URL_PATH, extractedURI.getPath());
        Span.current().setAttribute(HttpAttributes.HTTP_ROUTE, extractedURI.getPath());
        Span.current().setAttribute(UrlAttributes.URL_QUERY, extractedURI.getQuery());
        Span.current().setAttribute(UrlAttributes.URL_SCHEME, extractedURI.getScheme());

        HttpServletRequest request = (HttpServletRequest)message.getContextualProperty("HTTP.REQUEST");
        if (request != null) {
            Span.current().setAttribute(ClientAttributes.CLIENT_ADDRESS, request.getRemoteAddr());
            Span.current().setAttribute(NetworkAttributes.NETWORK_PEER_ADDRESS, request.getRemoteAddr());
            String protocol = request.getProtocol();
            if (protocol != null && protocol.contains("/")) {
                String protocolVersion = protocol.split("/")[1];
                Span.current().setAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, protocolVersion);
            }
            Span.current().setAttribute(UserAgentAttributes.USER_AGENT_ORIGINAL, request.getHeader("User-Agent"));
        }

        HttpServletResponse response = (HttpServletResponse) message.getContextualProperty("HTTP.RESPONSE");
        if (response != null && response.getStatus() >= 400) {
            Span.current().setAttribute(ErrorAttributes.ERROR_TYPE, Integer.toString(response.getStatus()));
        }

        Long resendCount = (Long)message.getContextualProperty("X-Retry-Count");
        if (resendCount != null && resendCount > 0) {
            Span.current().setAttribute(HttpAttributes.HTTP_REQUEST_RESEND_COUNT, resendCount);
        }
    }
}
