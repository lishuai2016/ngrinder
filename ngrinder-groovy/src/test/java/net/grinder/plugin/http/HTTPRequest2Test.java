/*
 * Copyright (c) 2012-present NAVER Corp.
 *
 * This file is part of The nGrinder software distribution. Refer to
 * the file LICENSE which is part of The nGrinder distribution for
 * licensing details. The nGrinder distribution is available on the
 * Internet at https://naver.github.io/ngrinder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.grinder.plugin.http;

import okhttp3.Headers;
import okhttp3.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HTTPRequest2Test {

	private static final String testUrl = "https://google.com?query=param";
	private static final Headers testHeaders = Headers.of("Content-Type", "application/json");
	private static final String testBody = "{ \"name\" : \"test\", \"age\" : \"21\"";

	private static final String HTTP2 = "h2";

	@Test
	public void testGetMethod() {
		Response response = new HTTPRequest2().GET(testUrl);
		assertNotNull(response);

		response = new HTTPRequest2().GET(testUrl, testHeaders);
		assertNotNull(response);

		assertEquals(response.protocol().toString(), HTTP2);
	}

	@Test
	public void testPostMethod() {
		Response response = new HTTPRequest2().POST(testUrl);
		assertNotNull(response);

		response = new HTTPRequest2().POST(testUrl, testBody.getBytes());
		assertNotNull(response);

		response = new HTTPRequest2().POST(testUrl, testBody.getBytes(), testHeaders);
		assertNotNull(response);

		assertEquals(response.protocol().toString(), HTTP2);
	}
}
