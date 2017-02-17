package com.zhukai.spring.integration.common;

import com.zhukai.spring.integration.common.constant.RequestType;
import com.zhukai.spring.integration.server.SpringIntegration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by zhukai on 17-1-17.
 */
public class HttpParser {

    public static HttpRequest parseRequest(SocketChannel channel) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(SpringIntegration.BUFFER_SIZE);
            String line = readLine(channel, buf);
            String[] firstLineArr = line.split(" ");
            if (firstLineArr.length < 3) {
                return null;
            }
            String[] pathArr = firstLineArr[1].split("\\?");
            String path = pathArr[0];
            if (path.equals("/favicon.ico")) {
                return null;
            }

            HttpRequest request = new HttpRequest();
            request.setMethod(firstLineArr[0]);
            request.setProtocol(firstLineArr[2]);
            request.setPath(path);

            if (pathArr.length > 1) {
                String[] pathParameter = pathArr[1].split("&");
                for (String param : pathParameter) {
                    String[] keyValue = param.split("=");
                    request.setParameter(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
                }
            }

            String contextLine = readLine(channel, buf);

            //保存此次请求的headers（包含cookies）
            while (!contextLine.trim().equals("")) {
                if (contextLine.startsWith("Cookie")) {
                    String cookieString = contextLine.substring(contextLine.indexOf(':') + 2);
                    String[] cookieArr = cookieString.split(";");
                    for (String cookie : cookieArr) {
                        String[] keyValue = cookie.split("=");
                        request.setCookie(keyValue[0].trim(), keyValue[1].trim());
                    }
                } else {
                    String headerKey = contextLine.substring(0, contextLine.indexOf(':'));
                    String headerValue = contextLine.substring(contextLine.indexOf(':') + 2);
                    request.setHeader(headerKey, headerValue);
                }
                contextLine = readLine(channel, buf);
            }

            if (request.getMethod().equals(RequestType.POST)) {
                int contentLength = Integer.parseInt(request.getHeader("Content-Length").trim());
                String postString = readInputStreamLimitSize(channel, buf, contentLength);
                if (request.getHeader("Content-Type").startsWith("multipart/form-data")) {
                    //TODO 一般用来上传文件
                } else if (request.getHeader("Content-Type").startsWith("application/x-www-form-urlencoded")) {
                    String[] paramStringArr = postString.split("&");
                    for (String paramString : paramStringArr) {
                        String[] param = paramString.split("=");
                        request.setAttribute(param[0], param[1]);
                    }
                } else if (request.getHeader("Content-Type").startsWith("text/plain") ||
                        request.getHeader("Content-Type").startsWith("application/json")) {
                    request.setRequestContext(postString);
                }
            }
            return request;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String parseHttpString(HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getProtocol()).append(" ")
                .append(response.getStatusCode()).append(" ")
                .append(response.getStatusCodeStr()).append("\r\n")
                .append("Content-Type: ").append(response.getContentType())
                .append("\r\n");
        response.getHeaders().keySet().forEach(key ->
                sb.append(key).append(": ")
                        .append(response.getHeaders().get(key))
                        .append("\r\n")
        );
        response.getCookies().keySet().forEach(key ->
                sb.append("Set-Cookie: ").append(key)
                        .append("=").append(response.getCookies().get(key))
                        .append(";Path=/").append("\r\n")
        );
        sb.append("\r\n");
        return sb.toString();
    }

    private static String readLine(SocketChannel channel, ByteBuffer buf) throws IOException {
        return readInputStreamLimitSize(channel, buf, 0);
    }

    private static String readInputStreamLimitSize(SocketChannel channel, ByteBuffer buf, int size) throws IOException {
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            int total = 0;
            if (size != 0) {
                while (channel.read(buf) != -1 && total < size) {
                    buf.flip();
                    while (buf.hasRemaining() && total < size) {
                        out.write(buf.get());
                        total++;
                    }
                    buf.compact();
                }
            } else {
                int i = 0;
                while (channel.read(buf) != -1 && i != 10) {
                    buf.flip();
                    while (buf.hasRemaining() && (i = buf.get()) != 10) {
                        out.write(i);
                    }
                    buf.compact();
                }
            }
            String result = new String(out.toByteArray(), SpringIntegration.CHARSET);
            if (result.length() > 0 && result.charAt(result.length() - 1) == 13) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (out != null)
                out.close();
        }
    }

    public static String getContentType(String extensionName) {
        String contentType = null;
        if (extensionName.equals("css")) {
            contentType = "text/css";
        } else if (extensionName.equals("png")) {
            contentType = "image/png";
        } else if (extensionName.equals("jpg") || extensionName.equals("jpeg")
                || extensionName.equals("jpe")) {
            contentType = "image/jpeg";
        } else if (extensionName.equals("js")) {
            contentType = "application/x-javascript";
        } else if (extensionName.equals("txt")) {
            contentType = "text/plain";
        } else if (extensionName.equals("html")) {
            contentType = "text/html";
        } else if (extensionName.equals("json")) {
            contentType = "text/json";
        } else if (extensionName.equals("xml")) {
            contentType = "text/xml";
        } else if (extensionName.equals("git")) {
            contentType = "image/gif";
        } else if (extensionName.equals("cgm")) {
            contentType = "image/cgm";
        } else if (extensionName.equals("doc")) {
            contentType = "application/msword";
        } else if (extensionName.equals("dms") || extensionName.equals("lha")
                || extensionName.equals("lzh") || extensionName.equals("exe")
                || extensionName.equals("class")) {
            contentType = "application/octet-stream";
        } else if (extensionName.equals("pdf")) {
            contentType = "application/pdf";
        } else if (extensionName.equals("ai") || extensionName.equals("eps")
                || extensionName.equals("ps")) {
            contentType = "application/postscript";
        } else if (extensionName.equals("ppt")) {
            contentType = "application/powerpoint";
        } else if (extensionName.equals("rtf")) {
            contentType = "application/rtf";
        } else if (extensionName.equals("z")) {
            contentType = "application/x-compress";
        } else if (extensionName.equals("gz")) {
            contentType = "application/x-gzip";
        } else if (extensionName.equals("gtar")) {
            contentType = "application/x-gtar";
        } else if (extensionName.equals("swf")) {
            contentType = "application/x-shockwave-flash";
        } else if (extensionName.equals("tar")) {
            contentType = "application/x-tar";
        } else if (extensionName.equals("zip")) {
            contentType = "application/zip";
        } else if (extensionName.equals("au") || extensionName.equals("snd")) {
            contentType = "audio/basic";
        } else if (extensionName.equals("mpeg") || extensionName.equals("mp2")) {
            contentType = "audio/mpeg";
        } else if (extensionName.equals("mid") || extensionName.equals("midi")
                || extensionName.equals("rmf")) {
            contentType = "audio/x-aiff";
        } else if (extensionName.equals("ram") || extensionName.equals("ra")) {
            contentType = "audio/x-pn-realaudio";
        } else if (extensionName.equals("rpm")) {
            contentType = "audio/x-pn-realaudio-plugin";
        } else if (extensionName.equals("wav")) {
            contentType = "audio/x-wav";
        } else {
            contentType = "application/octet-stream";
        }
        return contentType;
    }
}
