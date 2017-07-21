package com.zhukai.framework.fast.rest.handle;


import com.zhukai.framework.fast.rest.http.request.HttpRequest;

import java.nio.channels.SelectionKey;

public class ActionHandleNIO extends AbstractActionHandle {

    private SelectionKey key;

    public ActionHandleNIO(HttpRequest request, SelectionKey key) {
        this.request = request;
        this.key = key;
    }

    @Override
    protected void respond() {
        if (response == null) {
            return;
        }
        key.attach(response);
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
