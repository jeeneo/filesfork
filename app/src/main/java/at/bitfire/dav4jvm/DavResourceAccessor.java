/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package at.bitfire.dav4jvm;

import androidx.annotation.NonNull;

import kotlin.jvm.functions.Function0;
import okhttp3.Response;

public class DavResourceAccessor {
    private DavResourceAccessor() {
    }

    public static void checkStatus(@NonNull DavResource davResource, @NonNull Response response) {
        davResource.checkStatus(response);
    }

    public static Response followRedirects(@NonNull DavResource davResource, @NonNull Function0<Response> sendRequest) {
        return davResource.followRedirects$build(sendRequest);
    }
}
