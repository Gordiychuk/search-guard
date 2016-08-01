package com.floragunn.searchguard.utils;


import org.apache.commons.io.IOUtils;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ResourceUtils {
    private static final ESLogger LOGGER = Loggers.getLogger(ResourceUtils.class);


    private final Class<?> resourceClazz;

    public ResourceUtils(Class<?> resourceClazz) {
        this.resourceClazz = resourceClazz;
    }

    public ResourceUtils() {
        this.resourceClazz = this.getClass();
    }


    public final String loadFile(final String file) throws IOException {
        final StringWriter sw = new StringWriter();
        IOUtils.copy(resourceClazz.getResourceAsStream("/" + file), sw, StandardCharsets.UTF_8);
        return sw.toString();
    }

    public BytesReference readYamlContent(final String file) {
        try {
            return readXContent(new StringReader(loadFile(file)), XContentType.YAML);
        } catch (IOException e) {
            return null;
        }
    }

    public BytesReference readXContent(final Reader reader, final XContentType xContentType) throws IOException {
        XContentParser parser = null;
        try {
            parser = XContentFactory.xContent(xContentType).createParser(reader);
            parser.nextToken();
            final XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.copyCurrentStructure(parser);
            return builder.bytes();
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    public File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {
        File file = null;
        final URL fileUrl = resourceClazz.getClassLoader().getResource(fileNameFromClasspath);
        if (fileUrl != null) {
            try {
                file = new File(URLDecoder.decode(fileUrl.getFile(), "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                return null;
            }

            if (file.exists() && file.canRead()) {
                return file;
            } else {
                LOGGER.error("Cannot read from {}, maybe the file does not exists? ", file.getAbsolutePath());
            }

        } else {
            LOGGER.error("Failed to load " + fileNameFromClasspath);
        }
        return null;
    }
}
