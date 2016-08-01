package com.floragunn.searchguard.rest;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;

import java.io.IOException;

public class RestChannelDelegator extends RestChannel {
    private final RestChannel original;

    public RestChannelDelegator(RestChannel original) {
        super(original.request(), original.detailedErrorsEnabled());
        this.original = original;
    }

    @Override
    public void sendResponse(RestResponse response) {
        original.sendResponse(response);
    }

    @Override
    public RestRequest request() {
        return original.request();
    }

    @Override
    public XContentBuilder newErrorBuilder() throws IOException {
        return original.newErrorBuilder();
    }

    @Override
    protected BytesStreamOutput newBytesOutput() {
        return super.newBytesOutput();
    }

    @Override
    public XContentBuilder newBuilder(@Nullable BytesReference autoDetectSource, boolean useFiltering) throws IOException {
        return original.newBuilder(autoDetectSource, useFiltering);
    }

    @Override
    public XContentBuilder newBuilder() throws IOException {
        return original.newBuilder();
    }

    @Override
    public boolean detailedErrorsEnabled() {
        return original.detailedErrorsEnabled();
    }
}
