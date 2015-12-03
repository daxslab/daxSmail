
package com.daxslab.mail.mail.internet;

import com.daxslab.mail.mail.Body;
import com.daxslab.mail.mail.BodyPart;
import com.daxslab.mail.mail.CompositeBody;
import com.daxslab.mail.mail.MessagingException;
import com.daxslab.mail.mail.Multipart;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;

import org.apache.james.mime4j.util.MimeUtil;

/**
 * TODO this is a close approximation of Message, need to update along with
 * Message.
 */
public class MimeBodyPart extends BodyPart {
    protected MimeHeader mHeader = new MimeHeader();
    protected Body mBody;
    protected int mSize;

    public MimeBodyPart() throws MessagingException {
        this(null);
    }

    public MimeBodyPart(Body body) throws MessagingException {
        this(body, null);
    }

    public MimeBodyPart(Body body, String mimeType) throws MessagingException {
        if (mimeType != null) {
            addHeader(MimeHeader.HEADER_CONTENT_TYPE, mimeType);
        }
        setBody(body);
    }

    protected String getFirstHeader(String name) {
        return mHeader.getFirstHeader(name);
    }

    public void addHeader(String name, String value) throws MessagingException {
        mHeader.addHeader(name, value);
    }

    public void setHeader(String name, String value) {
        mHeader.setHeader(name, value);
    }

    public String[] getHeader(String name) throws MessagingException {
        return mHeader.getHeader(name);
    }

    public void removeHeader(String name) throws MessagingException {
        mHeader.removeHeader(name);
    }

    public Body getBody() {
        return mBody;
    }

    public void setBody(Body body) throws MessagingException {
        this.mBody = body;
        if (body instanceof Multipart) {
            Multipart multipart = ((Multipart)body);
            multipart.setParent(this);
            String type = multipart.getContentType();
            setHeader(MimeHeader.HEADER_CONTENT_TYPE, type);
            if ("multipart/signed".equalsIgnoreCase(type)) {
                setEncoding(MimeUtil.ENC_7BIT);
            } else {
                setEncoding(MimeUtil.ENC_8BIT);
            }
        } else if (body instanceof TextBody) {
            String contentType = String.format("%s;\r\n charset=utf-8", getMimeType());
            String name = MimeUtility.getHeaderParameter(getContentType(), "name");
            if (name != null) {
                contentType += String.format(";\r\n name=\"%s\"", name);
            }
            setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType);
            setEncoding(MimeUtil.ENC_8BIT);
        }
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        if (mBody != null) {
            mBody.setEncoding(encoding);
        }
        setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, encoding);
    }

    public String getContentType() throws MessagingException {
        String contentType = getFirstHeader(MimeHeader.HEADER_CONTENT_TYPE);
        return (contentType == null) ? "text/plain" : contentType;
    }

    public String getDisposition() throws MessagingException {
        return getFirstHeader(MimeHeader.HEADER_CONTENT_DISPOSITION);
    }

    public String getContentId() throws MessagingException {
        String contentId = getFirstHeader(MimeHeader.HEADER_CONTENT_ID);
        if (contentId == null) {
            return null;
        }

        int first = contentId.indexOf('<');
        int last = contentId.lastIndexOf('>');

        return (first != -1 && last != -1) ?
               contentId.substring(first + 1, last) :
               contentId;
    }

    public String getMimeType() throws MessagingException {
        return MimeUtility.getHeaderParameter(getContentType(), null);
    }

    public boolean isMimeType(String mimeType) throws MessagingException {
        return getMimeType().equalsIgnoreCase(mimeType);
    }

    public int getSize() {
        return mSize;
    }

    /**
     * Write the MimeMessage out in MIME format.
     */
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);
        mHeader.writeTo(out);
        writer.write("\r\n");
        writer.flush();
        if (mBody != null) {
            mBody.writeTo(out);
        }
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        String type = getFirstHeader(MimeHeader.HEADER_CONTENT_TYPE);
        /*
         * We don't trust that a multipart/* will properly have an 8bit encoding
         * header if any of its subparts are 8bit, so we automatically recurse
         * (as long as its not multipart/signed).
         */
        if (mBody instanceof CompositeBody
                && !"multipart/signed".equalsIgnoreCase(type)) {
            setEncoding(MimeUtil.ENC_7BIT);
            // recurse
            ((CompositeBody) mBody).setUsing7bitTransport();
        } else if (!MimeUtil.ENC_8BIT
                .equalsIgnoreCase(getFirstHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING))) {
            return;
        } else if (type != null
                && (type.equalsIgnoreCase("multipart/signed") || type
                        .toLowerCase(Locale.US).startsWith("message/"))) {
            /*
             * This shouldn't happen. In any case, it would be wrong to convert
             * them to some other encoding for 7bit transport.
             *
             * RFC 1847 says multipart/signed must be 7bit. It also says their
             * bodies must be treated as opaque, so we must not change the
             * encoding.
             *
             * We've dealt with (CompositeBody) type message/rfc822 above. Here
             * we must deal with all other message/* types. RFC 2045 says
             * message/* can only be 7bit or 8bit. RFC 2046 says unknown
             * message/* types must be treated as application/octet-stream,
             * which means we can't recurse into them. It also says that
             * existing subtypes message/partial and message/external must only
             * be 7bit, and that future subtypes "should be" 7bit.
             */
            throw new MessagingException(
                    "Unable to convert 8bit body part to 7bit");
        } else {
            setEncoding(MimeUtil.ENC_QUOTED_PRINTABLE);
        }
    }
}
