/*
 * Copyright © 2018 VMware, Inc. All Rights Reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */

package com.vmware.connectors.bitbucket.server;

import com.google.common.collect.ImmutableList;
import com.vmware.connectors.bitbucket.server.utils.BitbucketServerAction;
import com.vmware.connectors.test.ControllerTestsBase;
import com.vmware.connectors.test.JsonReplacementsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.AsyncRestTemplate;

import java.io.IOException;
import java.util.List;

import static com.vmware.connectors.bitbucket.server.utils.BitbucketServerConstants.*;
import static com.vmware.connectors.test.JsonSchemaValidator.isValidHeroCardConnectorResponse;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BitbucketServerControllerTest extends ControllerTestsBase {

    private static final String BITBUCKET_SERVER_AUTH_TOKEN = "bitbucket-token";

    private static final String PULL_REQUEST_ID_1 = "236";

    private static final String PULL_REQUEST_ID_2 = "246";

    @Autowired
    private AsyncRestTemplate rest;

    private MockRestServiceServer mockBitbucketServer;

    @Value("classpath:bitbucket/responses/pr_236.json")
    private Resource pr236;

    @Value("classpath:bitbucket/responses/pr_246.json")
    private Resource pr246;

    @Value("classpath:bitbucket/responses/private-repo.json")
    private Resource testRepoPr1;

    @Value("classpath:bitbucket/responses/approved.json")
    private Resource approve;

    @Value("classpath:bitbucket/responses/private-repo-approved.json")
    private Resource testRepoPr1Approved;

    @Value("classpath:bitbucket/responses/comments.json")
    private Resource comments;

    @Value("classpath:bitbucket/responses/private-repo-comments.json")
    private Resource testRepoPr1Comments;

    @Value("classpath:bitbucket/responses/declined.json")
    private Resource declined;

    // TODO - private repo for: declined, merged, and activities

    @Value("classpath:bitbucket/responses/merged.json")
    private Resource merged;

    @Value("classpath:bitbucket/responses/activities_pr_236.json")
    private Resource pr236Activities;

    @Value("classpath:bitbucket/responses/activities_pr_246.json")
    private Resource pr246Activities;

    @Before
    public void setup() throws Exception {
        super.setup();

        mockBitbucketServer = MockRestServiceServer.bindTo(rest)
                .ignoreExpectOrder(true)
                .build();
    }

    @Test
    public void testProtectedResources() throws Exception {
        testProtectedResource(POST, "/cards/requests");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/approve");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/merge");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/comments");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/decline");
    }

    @Test
    public void testDiscovery() throws Exception {
        testConnectorDiscovery();
    }

    @Test
    public void testRegex() throws Exception {
        final List<String> expectedList = ImmutableList.of(
                // Project Name/ Repository Plug/ Pull request id.
                "UFO/app-platform-server - Pull request #244: ",
                "UFO/app-platform-server - Pull request #245: ",
                "UFO/app-platform-server - Pull request #241: ",
                "UFO/app-platform-server - Pull request #239: ",
                "~JBARD/test-repo - Pull request #1: ",
                "UFO/card-connectors - Pull request #9: ");

        testRegex("pr_email_subject", fromFile("/regex/pr-email-subject.txt"), expectedList);
    }

    @Test
    public void getImage() throws Exception {
        perform(get("/images/connector.png"))
                .andExpect(status().isOk())
                .andExpect(header().longValue(CONTENT_LENGTH, 11901))
                .andExpect(header().string(CONTENT_TYPE, IMAGE_PNG_VALUE))
                .andExpect(content().bytes(bytesFromFile("/static/images/connector.png")));
    }

    @Test
    public void testCardRequestWithEmptyIssue() throws Exception {
        testCardRequests("emptyIssue.json", "emptyIssue.json");
    }

    @Test
    public void testCardRequests() throws Exception {
        final String pr236Url = "https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/" + PULL_REQUEST_ID_1;
        final String pr246Url = "https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/" + PULL_REQUEST_ID_2;
        final String notFoundUrl = "https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/NOT-FOUND/pull-requests/999";

        expect(pr236Url).andRespond(withSuccess(pr236, APPLICATION_JSON));
        expect(pr246Url).andRespond(withSuccess(pr246, APPLICATION_JSON));
        expect(notFoundUrl).andRespond(withStatus(HttpStatus.NOT_FOUND));

        expect(pr236Url + "/activities").andRespond(withSuccess(pr236Activities, APPLICATION_JSON));
        expect(pr246Url + "/activities").andRespond(withSuccess(pr246Activities, APPLICATION_JSON));
        expect(notFoundUrl + "/activities").andRespond(withStatus(HttpStatus.NOT_FOUND));

        testCardRequests("request.json", "success.json");

        this.mockBitbucketServer.verify();
    }

    @Test
    public void testCardRequestsPersonalRepo() throws Exception {
        final String pr1Url = "https://stash.air-watch.com/rest/api/1.0/users/JBARD/repos/test-repo/pull-requests/1";

        expect(pr1Url).andRespond(withSuccess(testRepoPr1, APPLICATION_JSON));

        expect(pr1Url + "/activities").andRespond(withSuccess(testRepoPr1Activities, APPLICATION_JSON));

        testCardRequests("private-repo-request.json", "private-repo-success.json");

        this.mockBitbucketServer.verify();
    }

    @Test
    public void testRequestEmptyCards() throws Exception {
        testRequestCardsWithMissingParameter("emptyRequest.json", "emptyRequest.json");
    }

    @Test
    public void testRequestEmptyToken() throws Exception {
        testRequestCardsWithMissingParameter("emptyToken.json", "emptyToken.json");
    }

    @Test
    public void approve() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/approve";

        testBitbucketServerPRAction(url, approve, BitbucketServerAction.APPROVE);
    }

    @Test
    public void decline() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/decline";

        testBitbucketServerPRAction(url, declined, BitbucketServerAction.DECLINE);
    }

    @Test
    public void merge() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/merge";

        testBitbucketServerPRAction(url, merged, BitbucketServerAction.MERGE);
    }

    @Test
    public void comment() throws Exception {
        this.mockBitbucketServer.expect(requestTo("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/236/comments"))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN))
                .andExpect(MockRestRequestMatchers.header(CONTENT_TYPE, APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(comments, APPLICATION_JSON));

        perform(post("/api/v1/UFO/app-platform-server/236/comments")
                .with(token(accessToken()))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .header(AUTH_HEADER, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN)
                .header(BASE_URL_HEADER, "https://stash.air-watch.com")
                .param(COMMENT_PARAM_KEY, "Pull request comment")
                ).andExpect(status().isOk());

        this.mockBitbucketServer.verify();
    }

    @Test
    public void testAuthSuccess() throws Exception {
        expect("https://stash.air-watch.com").andRespond(withStatus(HttpStatus.NOT_FOUND));
        perform(get("/test-auth").with(token(accessToken()))
                .header(AUTH_HEADER, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN)
                .header(BASE_URL_HEADER, "https://stash.air-watch.com"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAuthFail() throws Exception {
        expect("https://stash.air-watch.com").andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        perform(get("/test-auth").with(token(accessToken()))
                .header(AUTH_HEADER, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN)
                .header(BASE_URL_HEADER, "https://stash.air-watch.com"))
                .andExpect(status().isBadRequest());
    }

    private void testBitbucketServerPRAction(final String url,
                                             final Resource resource,
                                             final BitbucketServerAction stashAction) throws Exception {
        expect("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/" + PULL_REQUEST_ID_1).andRespond(withSuccess(pr236, APPLICATION_JSON));

        this.mockBitbucketServer.expect(requestTo("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/236/" + stashAction.getAction() + "?version=10"))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN))
                .andExpect(MockRestRequestMatchers.header(ATLASSIAN_TOKEN, "no-check"))
                .andRespond(withSuccess(resource, APPLICATION_JSON));

        perform(post(url)
                .with(token(accessToken()))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .header(AUTH_HEADER, "Basic " + BITBUCKET_SERVER_AUTH_TOKEN)
                .header(BASE_URL_HEADER, "https://stash.air-watch.com")
                .header(ROUTING_PREFIX, "https://hero/connectors/stash/"))
                .andExpect(status().isOk());

        this.mockBitbucketServer.verify();
    }

    private void testRequestCardsWithMissingParameter(String requestFile, String responseFile) throws Exception {
        MockHttpServletRequestBuilder builder = requestCard(BITBUCKET_SERVER_AUTH_TOKEN, requestFile);

        perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(fromFile("bitbucket/responses/" + responseFile)));
    }

    private void testCardRequests(final String requestFile, final String responseFile) throws Exception {
        final MockHttpServletRequestBuilder builder = requestCard(BITBUCKET_SERVER_AUTH_TOKEN, requestFile);
        perform(builder)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().string(isValidHeroCardConnectorResponse()))
                .andExpect(content().string(JsonReplacementsBuilder.from(
                        fromFile("bitbucket/responses/" + responseFile)).buildForCards()))
                .andReturn();
    }

    private MockHttpServletRequestBuilder requestCard(final String authToken, final String requestFile) throws IOException {
        return post("/cards/requests").with(token(accessToken()))
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .header(AUTH_HEADER, "Basic " + authToken)
                .header(BASE_URL_HEADER, "https://stash.air-watch.com")
                .header(ROUTING_PREFIX, "https://hero/connectors/stash/")
                .content(fromFile("/bitbucket/requests/" + requestFile));
    }

    private ResponseActions expect(final String url) {
        return this.mockBitbucketServer.expect(requestTo(url))
                .andExpect(method(GET))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic bitbucket-token"));
    }
}
