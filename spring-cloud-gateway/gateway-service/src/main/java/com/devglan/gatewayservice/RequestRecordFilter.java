package com.devglan.gatewayservice;

import io.netty.buffer.ByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

@Component
public class RequestRecordFilter implements GatewayFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestRecordFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI requestUri = request.getURI();
        String schema = requestUri.getScheme();
        if ((!"http".equals(schema) && !"https".equals(schema))){
            return chain.filter(exchange);
        }
        String method = request.getMethodValue();
        String contentType = request.getHeaders().getFirst("Content-Type");
        if ("POST".equals(method) && !contentType.startsWith("multipart/form-data")){
            String bodyStr = resolveBodyFromRequest(exchange);
            DataBuffer bodyDataBuffer = stringBuffer(bodyStr);
            int len = bodyDataBuffer.readableByteCount();
            URI ex = UriComponentsBuilder.fromUri(requestUri).build(true).toUri();
            ServerHttpRequest newRequest = request.mutate().uri(ex).build();
            //Next, the request body is encapsulated and written back to the request and passed to the next level.
            HttpHeaders myHeaders = new HttpHeaders();
            copyMultiValueMap(request.getHeaders(), myHeaders);
            myHeaders.remove(HttpHeaders.CONTENT_LENGTH);
            myHeaders.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(len));

            Flux<DataBuffer> bodyFlux = Flux.just(bodyDataBuffer);
            newRequest = new ServerHttpRequestDecorator(newRequest) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return bodyFlux;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return myHeaders;
                }
            };
            ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
            return chain.filter(newExchange);
        } else {
            return chain.filter(exchange);
        }
    }

    private static <K, V> void copyMultiValueMap(MultiValueMap<K,V> source, MultiValueMap<K,V> target) {
        source.forEach((key, value) -> target.put(key, new LinkedList<>(value)));
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;
    }

    private String resolveBodyFromRequest(ServerWebExchange exchange){

        Flux<DataBuffer> body = exchange.getRequest().getBody();
        StringBuilder sb = new StringBuilder();
        body.map(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            String bodyString = new String(bytes, StandardCharsets.UTF_8);
            sb.append(bodyString);
            return bytes;
        });
        return sb.toString();

    }

    private DataBuffer stringBuffer(String value){
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
        DataBuffer buffer = nettyDataBufferFactory.allocateBuffer(bytes.length);
        buffer.write(bytes);
        return buffer;
    }

}