package com.bay1ts.bay;

import com.bay1ts.bay.core.Request;
import com.bay1ts.bay.core.Response;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Created by chenu on 2016/9/3.
 */
public interface Action {
//    public FullHttpResponse handle(FullHttpRequest request, FullHttpResponse response);
    public Response handle(Request request,Response response) throws Exception;
}
