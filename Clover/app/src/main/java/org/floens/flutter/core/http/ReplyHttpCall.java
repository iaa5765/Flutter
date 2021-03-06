/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.flutter.core.http;

import android.text.TextUtils;

import org.floens.flutter.chan.ChanUrls;
import org.floens.flutter.core.model.Reply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReplyHttpCall extends HttpCall {
    private static final String TAG = "ReplyHttpCall";
    private static final Random RANDOM = new Random();
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");

    public boolean posted;
    public String errorMessage;
    public String text;
    public String password;
    public int threadNo = -1;
    public int postNo = -1;

    private final Reply reply;

    public ReplyHttpCall(Reply reply) {
        this.reply = reply;
    }

    @Override
    public Reply setup(Request.Builder requestBuilder) {
        boolean thread = reply.resto >= 0;

        password = Long.toHexString(RANDOM.nextLong());

        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!thread && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("subject", reply.subject);
        } else
            formBuilder.addFormDataPart("subject", "");

        formBuilder.addFormDataPart("body", reply.comment);
        if (reply.file != null) {
            formBuilder.addFormDataPart("file", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }
        formBuilder.addFormDataPart("password", password);
        if (thread) {
            formBuilder.addFormDataPart("thread", String.valueOf(reply.resto));
        }
        formBuilder.addFormDataPart("board", reply.board);
        formBuilder.addFormDataPart("making_a_post", "1");
        formBuilder.addFormDataPart("post", "New Reply");
        formBuilder.addFormDataPart("wantjson", "1");

         if (thread) {
             formBuilder.addFormDataPart("resto", String.valueOf(reply.resto));
         }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }

        requestBuilder.url(ChanUrls.getReplyUrl(reply.board));
        requestBuilder.post(formBuilder.build());
        this.posted = true;
        return reply;


    }

    @Override
    public void process(Response response, String result) throws IOException {
        text = result;

        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (threadNo >= 0 && postNo >= 0) {
                    posted = true;
                }
            }
        }
    }
}
